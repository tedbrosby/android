 /*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.common.analytics;

 import com.android.ide.common.rendering.api.ResourceNamespace;
 import com.android.ide.common.rendering.api.ResourceReference;
 import com.android.tools.idea.common.model.NlComponent;
 import com.android.tools.idea.common.model.NlLayoutType;
 import com.android.tools.idea.common.model.NlModel;
 import com.android.tools.idea.common.property.NlProperty;
 import com.android.tools.idea.common.property.PropertiesManager;
 import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
 import com.android.tools.idea.uibuilder.palette.NlPaletteModel;
 import com.android.tools.idea.uibuilder.palette.Palette;
 import com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PropertiesViewMode;
 import com.android.tools.idea.uibuilder.property.NlPropertyItem;
 import com.google.wireless.android.sdk.stats.LayoutAttributeChangeEvent;
 import com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewGroup;
 import com.google.wireless.android.sdk.stats.SearchOption;
 import com.intellij.openapi.Disposable;
 import com.intellij.openapi.project.Project;
 import com.intellij.openapi.util.Disposer;
 import com.intellij.openapi.util.text.StringUtil;
 import com.intellij.util.xml.XmlName;
 import org.intellij.lang.annotations.Language;
 import org.jetbrains.android.AndroidTestCase;
 import org.jetbrains.android.dom.attrs.AttributeDefinition;
 import org.jetbrains.android.dom.attrs.AttributeDefinitions;
 import org.jetbrains.android.dom.attrs.StyleableDefinition;
 import org.jetbrains.android.resourceManagers.FrameworkResourceManager;
 import org.jetbrains.android.resourceManagers.LocalResourceManager;
 import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
 import org.jetbrains.annotations.NotNull;
 import org.jetbrains.annotations.Nullable;
 import org.picocontainer.MutablePicoContainer;

 import java.io.InputStreamReader;
 import java.io.Reader;
 import java.nio.charset.StandardCharsets;
 import java.util.Collections;
 import java.util.HashMap;
 import java.util.Map;
 import java.util.Set;

 import static com.android.SdkConstants.*;
 import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.*;
 import static com.google.common.truth.Truth.assertThat;
 import static com.google.wireless.android.sdk.stats.AndroidAttribute.AttributeNamespace.*;
 import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.*;
 import static org.mockito.Mockito.mock;
 import static org.mockito.Mockito.when;

public class UsageTrackerUtilTest extends AndroidTestCase {
  private static final String SDK_VERSION = ":" + HIGHEST_KNOWN_API + ".0.1";
  private static final String DESIGN_COORDINATE = DESIGN_LIB_ARTIFACT + SDK_VERSION;
  private static final String GRID_LAYOUT_COORDINATE = GRID_LAYOUT_LIB_ARTIFACT + SDK_VERSION;
  private static final String CONSTRAINT_LAYOUT_COORDINATE = CONSTRAINT_LAYOUT_LIB_ARTIFACT + ":" + LATEST_CONSTRAINT_LAYOUT_VERSION;
  private static final String MAPS_COORDINATE = MAPS_ARTIFACT + ":2.0.0";
  private static final String LEANBACK_V17_COORDINATE = LEANBACK_V17_ARTIFACT + ":7.0.0";
  private static final String ACME_LIB_COORDINATE = "com.acme:my-layout:1.0.0";
  private static final String ATTR_ACME_LAYOUT_MARGIN = "layout_my_custom_right_margin";
  @Language("XML")
  private static final String DISCRETE_SEEK_BAR_XML = "<SeekBar\n" +
                                                      "    style=\"@style/Widget.AppCompat.SeekBar.Discrete\"\n" +
                                                      "    android:layout_width=\"wrap_content\"\n" +
                                                      "    android:layout_height=\"wrap_content\"\n" +
                                                      "    android:max=\"10\"\n" +
                                                      "    android:progress=\"3\"\n" +
                                                      "/>";

  private NlModel myModel;

  public void testConvertAttribute() {
    setUpApplicationAttributes();

    NlProperty textProperty = createProperty(ATTR_TEXT, ANDROID_URI, null);
    assertThat(convertAttribute(textProperty).getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(convertAttribute(textProperty).getAttributeNamespace()).isEqualTo(ANDROID);

    NlProperty collapseProperty = createProperty(ATTR_LAYOUT_COLLAPSE_MODE, AUTO_URI, DESIGN_COORDINATE);
    assertThat(convertAttribute(collapseProperty).getAttributeName()).isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttribute(collapseProperty).getAttributeNamespace()).isEqualTo(APPLICATION);

    NlProperty customProperty = createProperty(ATTR_ACME_LAYOUT_MARGIN, AUTO_URI, ACME_LIB_COORDINATE);
    assertThat(convertAttribute(customProperty).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttribute(customProperty).getAttributeNamespace()).isEqualTo(APPLICATION);
  }

  public void testConvertToolsAttribute() {
    setUpApplicationAttributes();

    NlProperty textProperty = createProperty(ATTR_TEXT, ANDROID_URI, null).getDesignTimeProperty();
    assertThat(convertAttribute(textProperty).getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(convertAttribute(textProperty).getAttributeNamespace()).isEqualTo(TOOLS);

    NlProperty collapseProperty = createProperty(ATTR_LAYOUT_COLLAPSE_MODE, AUTO_URI, DESIGN_COORDINATE).getDesignTimeProperty();
    assertThat(convertAttribute(collapseProperty).getAttributeName()).isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttribute(collapseProperty).getAttributeNamespace()).isEqualTo(TOOLS);

    NlProperty customProperty = createProperty(ATTR_ACME_LAYOUT_MARGIN, AUTO_URI, ACME_LIB_COORDINATE).getDesignTimeProperty();
    assertThat(convertAttribute(customProperty).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttribute(customProperty).getAttributeNamespace()).isEqualTo(TOOLS);
  }

  public void testConvertAttributeByName() {
    setUpApplicationAttributes();

    assertThat(convertAttribute(ATTR_TEXT, myFacet).getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(convertAttribute(ATTR_TEXT, myFacet).getAttributeNamespace()).isEqualTo(ANDROID);

    assertThat(convertAttribute(ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeName()).isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttribute(ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeNamespace()).isEqualTo(APPLICATION);

    assertThat(convertAttribute(ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttribute(ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeNamespace()).isEqualTo(APPLICATION);
  }

  public void testConvertToolsAttributeByName() {
    setUpApplicationAttributes();

    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_TEXT, myFacet).getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_TEXT, myFacet).getAttributeNamespace()).isEqualTo(TOOLS);

    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeName())
      .isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeNamespace()).isEqualTo(TOOLS);

    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeNamespace()).isEqualTo(TOOLS);
  }

  public void testConvertNamespace() {
    assertThat(convertNamespace(null)).isEqualTo(ANDROID);
    assertThat(convertNamespace("")).isEqualTo(ANDROID);
    assertThat(convertNamespace(TOOLS_URI)).isEqualTo(TOOLS);
    assertThat(convertNamespace(ANDROID_URI)).isEqualTo(ANDROID);
    assertThat(convertNamespace(AUTO_URI)).isEqualTo(APPLICATION);
    assertThat(convertNamespace("unknown")).isEqualTo(APPLICATION);
  }

  public void testConvertGroupName() throws Exception {
    assertThat(convertGroupName("All")).isEqualTo(ViewGroup.ALL_GROUPS);
    assertThat(convertGroupName("Widgets")).isEqualTo(ViewGroup.WIDGETS);
    assertThat(convertGroupName("Text")).isEqualTo(ViewGroup.TEXT);
    assertThat(convertGroupName("Layouts")).isEqualTo(ViewGroup.LAYOUTS);
    assertThat(convertGroupName("Containers")).isEqualTo(ViewGroup.CONTAINERS);
    assertThat(convertGroupName("Images")).isEqualTo(ViewGroup.IMAGES);
    assertThat(convertGroupName("Date")).isEqualTo(ViewGroup.DATES);
    assertThat(convertGroupName("Transitions")).isEqualTo(ViewGroup.TRANSITIONS);
    assertThat(convertGroupName("Advanced")).isEqualTo(ViewGroup.ADVANCED);
    assertThat(convertGroupName("Design")).isEqualTo(ViewGroup.DESIGN);
    assertThat(convertGroupName("AppCompat")).isEqualTo(ViewGroup.APP_COMPAT);
    assertThat(convertGroupName("MyGroup")).isEqualTo(ViewGroup.CUSTOM);
  }

  public void testAllGroupsOnPaletteAreRecognized() throws Exception {
    Palette palette = getPalette();
    palette.accept(new Palette.Visitor() {
      @Override
      public void visit(@NotNull Palette.Item item) {
      }

      @Override
      public void visit(@NotNull Palette.Group group) {
        assertThat(convertGroupName(group.getName())).isNotEqualTo(ViewGroup.CUSTOM);
      }
    });
  }

  public void testConvertViewOption() {
    assertThat(convertViewOption(PROGRESS_BAR, "<ProgressBar/>")).isEqualTo(NORMAL);
    assertThat(convertViewOption(PROGRESS_BAR, "<ProgressBar style=\"?android:attr/progressBarStyleHorizontal\"/>"))
      .isEqualTo(HORIZONTAL_PROGRESS_BAR);
    assertThat(convertViewOption(PROGRESS_BAR, "<ProgressBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(SEEK_BAR, "<SeekBar/>")).isEqualTo(NORMAL);
    assertThat(convertViewOption(SEEK_BAR, DISCRETE_SEEK_BAR_XML)).isEqualTo(DISCRETE_SEEK_BAR);
    assertThat(convertViewOption(SEEK_BAR, "<SeekBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(EDIT_TEXT, "<EditText/>")).isEqualTo(NORMAL);
    assertThat(convertViewOption(EDIT_TEXT, "<EditText android:inputType=\"textPassword\"/>")).isEqualTo(PASSWORD);
    assertThat(convertViewOption(EDIT_TEXT, "<EditText android:inputType=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(LINEAR_LAYOUT, "<LinearLayout/>")).isEqualTo(HORIZONTAL_LINEAR_LAYOUT);
    assertThat(convertViewOption(LINEAR_LAYOUT, "<LinearLayout android:orientation=\"vertical\"/>")).isEqualTo(VERTICAL_LINEAR_LAYOUT);
    assertThat(convertViewOption(LINEAR_LAYOUT, "<LinearLayout android:orientation=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(TEXT_VIEW, "<TextView/>")).isEqualTo(NORMAL);
  }

  public void testConvertProgressBarViewOption() {
    assertThat(convertProgressBarViewOption("<ProgressBar/>")).isEqualTo(NORMAL);
    assertThat(convertProgressBarViewOption("<ProgressBar style=\"?android:attr/progressBarStyle\"/>")).isEqualTo(NORMAL);
    assertThat(convertProgressBarViewOption("<ProgressBar style=\"?android:attr/progressBarStyleHorizontal\"/>"))
      .isEqualTo(HORIZONTAL_PROGRESS_BAR);
    assertThat(convertProgressBarViewOption("<ProgressBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertSeekBarViewOption() {
    assertThat(convertSeekBarViewOption("<SeekBar/>")).isEqualTo(NORMAL);
    assertThat(convertSeekBarViewOption(DISCRETE_SEEK_BAR_XML)).isEqualTo(DISCRETE_SEEK_BAR);
    assertThat(convertSeekBarViewOption("<SeekBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertEditTextViewOption() {
    assertThat(convertEditTextViewOption("<EditText/>")).isEqualTo(NORMAL);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textPassword\"/>")).isEqualTo(PASSWORD);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"numberPassword\"/>")).isEqualTo(PASSWORD_NUMERIC);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textEmailAddress\"/>")).isEqualTo(EMAIL);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"phone\"/>")).isEqualTo(PHONE);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textPostalAddress\"/>")).isEqualTo(POSTAL_ADDRESS);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textMultiLine\"/>")).isEqualTo(MULTILINE_TEXT);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"time\"/>")).isEqualTo(TIME_EDITOR);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"date\"/>")).isEqualTo(DATE_EDITOR);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"number\"/>")).isEqualTo(NUMBER);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"numberSigned\"/>")).isEqualTo(SIGNED_NUMBER);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"numberDecimal\"/>")).isEqualTo(DECIMAL_NUMBER);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertLinearLayoutViewOption() {
    assertThat(convertLinearLayoutViewOption("<LinearLayout/>")).isEqualTo(HORIZONTAL_LINEAR_LAYOUT);
    assertThat(convertLinearLayoutViewOption("<LinearLayout android:orientation=\"horizontal\"/>")).isEqualTo(HORIZONTAL_LINEAR_LAYOUT);
    assertThat(convertLinearLayoutViewOption("<LinearLayout android:orientation=\"vertical\"/>")).isEqualTo(VERTICAL_LINEAR_LAYOUT);
    assertThat(convertLinearLayoutViewOption("<LinearLayout android:orientation=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertPropertiesMode() {
    assertThat(convertPropertiesMode(PropertiesViewMode.INSPECTOR)).isEqualTo(LayoutAttributeChangeEvent.ViewType.INSPECTOR);
    assertThat(convertPropertiesMode(PropertiesViewMode.TABLE)).isEqualTo(LayoutAttributeChangeEvent.ViewType.PROPERTY_TABLE);
  }

  public void testConvertFilterMatches() {
    assertThat(convertFilterMatches(-1)).isEqualTo(SearchOption.NONE);
    assertThat(convertFilterMatches(0)).isEqualTo(SearchOption.NONE);
    assertThat(convertFilterMatches(1)).isEqualTo(SearchOption.SINGLE_MATCH);
    assertThat(convertFilterMatches(2)).isEqualTo(SearchOption.MULTIPLE_MATCHES);
    assertThat(convertFilterMatches(117)).isEqualTo(SearchOption.MULTIPLE_MATCHES);
  }

  public void testConvertAttributeName() {
    setUpApplicationAttributes();

    assertThat(convertAttributeName(ATTR_TEXT, ANDROID, null, myFacet)).isEqualTo(ATTR_TEXT);

    assertThat(convertAttributeName(ATTR_LAYOUT_COLLAPSE_MODE, APPLICATION, DESIGN_COORDINATE, myFacet))
      .isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttributeName(ATTR_TEXT, APPLICATION, null, myFacet)).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttributeName(ATTR_ACME_LAYOUT_MARGIN, APPLICATION, ACME_LIB_COORDINATE, myFacet)).isEqualTo(CUSTOM_NAME);

    assertThat(convertAttributeName(ATTR_TEXT, TOOLS, null, myFacet)).isEqualTo(ATTR_TEXT);
    assertThat(convertAttributeName(ATTR_LAYOUT_COLLAPSE_MODE, TOOLS, null, myFacet)).isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttributeName(ATTR_ACME_LAYOUT_MARGIN, TOOLS, null, myFacet)).isEqualTo(CUSTOM_NAME);
  }

  public void testConvertTagName() {
    assertThat(convertTagName(TEXT_VIEW).getTagName()).isEqualTo(TEXT_VIEW);
    assertThat(convertTagName(COORDINATOR_LAYOUT.defaultName()).getTagName()).isEqualTo(StringUtil.getShortName(COORDINATOR_LAYOUT.defaultName()));
    assertThat(convertTagName(CONSTRAINT_LAYOUT.defaultName()).getTagName()).isEqualTo(StringUtil.getShortName(CONSTRAINT_LAYOUT.defaultName()));
    assertThat(convertTagName(AD_VIEW).getTagName()).isEqualTo(StringUtil.getShortName(AD_VIEW));
    assertThat(convertTagName("com.acme.MyClass").getTagName()).isEqualTo(CUSTOM_NAME);
  }

  public void testGetStyleValueFromSeekBar() {
    assertThat(getStyleValue("<SeekBar/>")).isNull();
    assertThat(getStyleValue(DISCRETE_SEEK_BAR_XML)).isEqualTo("@style/Widget.AppCompat.SeekBar.Discrete");
  }

  public void testAcceptedGoogleLibraryNamespace() {
    assertThat(acceptedGoogleLibraryNamespace(DESIGN_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(CONSTRAINT_LAYOUT_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(GRID_LAYOUT_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(MAPS_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(LEANBACK_V17_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(ACME_LIB_COORDINATE)).isFalse();

    assertThat(acceptedGoogleLibraryNamespace("constraint-layout-1.0.0-beta3")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("constraint-layout-2.0.0")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("design-25.0.1")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("cardview-v7-25.0.1")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("acme-layout")).isFalse();
  }

  public void testAcceptedGoogleTagNamespace() {
    assertThat(acceptedGoogleTagNamespace(TEXT_VIEW)).isTrue();
    assertThat(acceptedGoogleTagNamespace(COORDINATOR_LAYOUT.defaultName())).isTrue();
    assertThat(acceptedGoogleTagNamespace(CONSTRAINT_LAYOUT.defaultName())).isTrue();
    assertThat(acceptedGoogleTagNamespace(AD_VIEW)).isTrue();
    assertThat(acceptedGoogleTagNamespace("com.acme.MyClass")).isFalse();
  }

  public void testLookupAttributeResource() {
    setUpApplicationAttributes();

    assertThat(lookupAttributeResource(myFacet, ATTR_TEXT).getNamespace()).isEqualTo(ANDROID);
    assertThat(lookupAttributeResource(myFacet, ATTR_TEXT).getLibraryName()).isNull();

    assertThat(lookupAttributeResource(myFacet, ATTR_LAYOUT_COLLAPSE_MODE).getNamespace()).isEqualTo(APPLICATION);
    assertThat(lookupAttributeResource(myFacet, ATTR_LAYOUT_COLLAPSE_MODE).getLibraryName()).isEqualTo(DESIGN_COORDINATE);

    assertThat(lookupAttributeResource(myFacet, ATTR_ACME_LAYOUT_MARGIN).getNamespace()).isEqualTo(APPLICATION);
    assertThat(lookupAttributeResource(myFacet, ATTR_ACME_LAYOUT_MARGIN).getLibraryName()).isEqualTo(ACME_LIB_COORDINATE);
  }

  private static Palette getPalette() throws Exception {
    Project project = mock(Project.class);
    try (Reader reader = new InputStreamReader(NlPaletteModel.class.getResourceAsStream(NlLayoutType.LAYOUT.getPaletteFileName()),
                                               StandardCharsets.UTF_8)) {
      return Palette.parse(reader, new ViewHandlerManager(project));
    }
  }

  private NlProperty createProperty(@NotNull String propertyName, @NotNull String namespace, @Nullable String libraryName) {
    NlComponent component = mock(NlComponent.class);
    when(component.getModel()).thenReturn(myModel);

    PropertiesManager propertiesManager = mock(PropertiesManager.class);
    return NlPropertyItem.create(new XmlName(propertyName, namespace),
                                 new AttributeDefinition(ResourceNamespace.RES_AUTO, propertyName, libraryName, null),
                                 Collections.singletonList(component),
                                 propertiesManager);
  }

  private void setUpApplicationAttributes() {
    Attributes frameworkAttributes = new Attributes();
    frameworkAttributes.add(new AttributeDefinition(ResourceNamespace.ANDROID, ATTR_TEXT));

    Attributes localAttributes = new Attributes();
    localAttributes.add(new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_LAYOUT_COLLAPSE_MODE, DESIGN_COORDINATE, null));
    localAttributes.add(new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_ACME_LAYOUT_MARGIN, ACME_LIB_COORDINATE, null));

    FrameworkResourceManager frameworkResourceManager = mock(FrameworkResourceManager.class);
    when(frameworkResourceManager.getAttributeDefinitions()).thenReturn(frameworkAttributes);

    LocalResourceManager localResourceManager = mock(LocalResourceManager.class);
    when(localResourceManager.getAttributeDefinitions()).thenReturn(localAttributes);

    ModuleResourceManagers resourceManagers = mock(ModuleResourceManagers.class);
    when(resourceManagers.getFrameworkResourceManager()).thenReturn(frameworkResourceManager);
    when(resourceManagers.getLocalResourceManager()).thenReturn(localResourceManager);

    registerComponentInstance((MutablePicoContainer)myFacet.getModule().getPicoContainer(),
                              ModuleResourceManagers.class,
                              resourceManagers,
                              getTestRootDisposable());

    myModel = mock(NlModel.class);
    when(myModel.getFacet()).thenReturn(myFacet);
  }

  static <T> void registerComponentInstance(MutablePicoContainer container, Class<T> key, T implementation, Disposable parentDisposable) {
    Object old = container.getComponentInstance(key);
    container.unregisterComponent(key.getName());
    container.registerComponentInstance(key.getName(), implementation);
    Disposer.register(
      parentDisposable,
      () -> {
        container.unregisterComponent(key.getName());
        if (old != null) {
          container.registerComponentInstance(key.getName(), old);
        }
      });
  }

  private static class Attributes implements AttributeDefinitions {
    private Map<ResourceReference, AttributeDefinition> myDefinitions = new HashMap<>();

    private void add(@NotNull AttributeDefinition definition) {
      myDefinitions.put(definition.getResourceReference(), definition);
    }

    @Nullable
    @Override
    public StyleableDefinition getStyleableDefinition(@NotNull ResourceReference styleable) {
      throw new UnsupportedOperationException();
    }

    @Deprecated
    @Nullable
    @Override
    public StyleableDefinition getStyleableByName(@NotNull String name) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<ResourceReference> getAttrs() {
      return myDefinitions.keySet();
    }

    @Nullable
    @Override
    public AttributeDefinition getAttrDefinition(@NotNull ResourceReference attr) {
      return myDefinitions.get(attr);
    }

    @Deprecated
    @Nullable
    @Override
    public AttributeDefinition getAttrDefByName(@NotNull String name) {
      AttributeDefinition attribute = myDefinitions.get(ResourceReference.attr(ResourceNamespace.RES_AUTO, name));
      if (attribute == null) {
        attribute = myDefinitions.get(ResourceReference.attr(ResourceNamespace.ANDROID, name));
      }
      return attribute;
    }

    @Nullable
    @Override
    public String getAttrGroup(@NotNull ResourceReference attr) {
      throw new UnsupportedOperationException();
    }
  }
}
