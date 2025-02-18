/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture.ComponentVisibility;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture.Kind;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.actions.CreateFileFromTemplateDialog.Visibility;
import com.android.tools.idea.actions.CreateNewClassDialogValidatorExImpl;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.query.ComponentVisibleQuery;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRemoteRunner.class)
public class CreateNewClassDialogGuiTest {
  private static final String PROVIDED_ACTIVITY = "app/src/main/java/google/simpleapplication/MyActivity.java";
  private static final String THING_NAME = "TestThing";
  private static final String PACKAGE_NAME_0 = "google.simpleapplication";
  private static final String PACKAGE_NAME_1 = PACKAGE_NAME_0 + ".sub.pkg";
  private static final String THING_FILE_PATH_0 = "app/src/main/java/google/simpleapplication/TestThing.java";
  private static final String THING_FILE_PATH_1 = "app/src/main/java/google/simpleapplication/sub/pkg/TestThing.java";
  private static final String PUBLIC_DECLARATION = "public %s TestThing {";
  private static final String PACKAGE_PRIVATE_DECLARATION = "%s TestThing {";
  private static final String SUPERCLASS_AND_INTERFACE_DECLARATION = "public %s TestThing extends Super0 implements Interface0 {";
  private static final String CLASS_IMPLEMENTING_ONE_INTERFACE_DECLARATION = "public %s TestThing implements Interface0 {";
  private static final String CLASS_IMPLEMENTING_TWO_INTERFACES_DECLARATION = "public %s TestThing implements Interface0, Interface1 {";
  private static final String CLASS_IMPLEMENTING_INTERFACE_THAT_NEEDS_IMPORT_DECLARATION = "public %s TestThing implements InterfaceX {";
  private static final String INTERFACE_EXTENDING_ONE_INTERFACE_DECLARATION = "public %s TestThing extends Interface0 {";
  private static final String INTERFACE_EXTENDING_TWO_INTERFACES_DECLARATION = "public %s TestThing extends Interface0, Interface1 {";
  private static final String INTERFACE_EXTENDING_INTERFACE_THAT_NEEDS_IMPORT_DECLARATION = "public %s TestThing extends InterfaceX {";
  private static final String SUPERCLASS_0 = "Super0";
  private static final String INVALID_NAME = "Invalid-Class Name";
  private static final String INTERFACE_0 = "Interface0";
  private static final String INTERFACE_1 = "Interface1";
  private static final String FULLY_QUALIFIED_INTERFACE = "com.example.foo.InterfaceX";
  private static final String INTERFACE_IMPORT = "import " + FULLY_QUALIFIED_INTERFACE + ";";
  private static final String JAVA_UTIL_MAP_ENTRY = "java.util.Map.Entry";
  private static final String JAVA_UTIL_MAP_IMPORT = "import java.util.Map;";
  private static final String JAVA_UTIL_MAP_ENTRY_DECLARATION = "public %s TestThing implements Map.Entry {";

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  private EditorFixture myEditor;

  @Before
  public void setUp() throws IOException {
    guiTest.importSimpleApplication();
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);
  }

  private CreateFileFromTemplateDialogFixture invokeNewFileDialog() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Java Class");
    return CreateFileFromTemplateDialogFixture.find(guiTest.robot());
  }

  private void assertPackageName(@NotNull String filePath, @NotNull String packageName) {
    myEditor.open(filePath);
    myEditor.moveBetween("package ", packageName);
    assertThat(myEditor.getCurrentLine().trim()).isEqualTo("package " + packageName + ";");
  }

  private void assertDeclaration(@NotNull String filePath, @NotNull String expectedDeclaration, @NotNull Kind kind) {
    myEditor.open(filePath);
    myEditor.moveBetween(kind + " " + THING_NAME, "");
    assertThat(myEditor.getCurrentLine().trim()).isEqualTo(String.format(expectedDeclaration, kind));
  }

  private void assertImport(@NotNull String filePath, @NotNull String expectedImport) {
    myEditor.open(filePath);
    myEditor.moveBetween(expectedImport, "");
    assertThat(myEditor.getCurrentLine().trim()).isEqualTo(expectedImport);
  }

  private void createPackagePrivate(Kind kind) throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(kind);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PACKAGE_PRIVATE);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, PACKAGE_PRIVATE_DECLARATION, kind);
  }

  private void createWithOneInterface(Kind kind) throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(kind);
    dialog.setInterface(INTERFACE_0);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    String declaration = kind.equals(Kind.INTERFACE) || kind.equals(Kind.ANNOTATION)
                         ? INTERFACE_EXTENDING_ONE_INTERFACE_DECLARATION
                         : CLASS_IMPLEMENTING_ONE_INTERFACE_DECLARATION;

    assertDeclaration(THING_FILE_PATH_0, declaration, kind);
  }

  private void createWithTwoInterfaces(Kind kind) throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(kind);
    dialog.setInterface(INTERFACE_0 + "," + INTERFACE_1);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    String declaration = kind.equals(Kind.INTERFACE) || kind.equals(Kind.ANNOTATION)
                         ? INTERFACE_EXTENDING_TWO_INTERFACES_DECLARATION
                         : CLASS_IMPLEMENTING_TWO_INTERFACES_DECLARATION;
    assertDeclaration(THING_FILE_PATH_0, declaration, kind);
  }

  private void createWithAnImport(Kind kind) throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(kind);
    dialog.setInterface(FULLY_QUALIFIED_INTERFACE);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    String declaration = kind.equals(Kind.INTERFACE) || kind.equals(Kind.ANNOTATION)
                         ? INTERFACE_EXTENDING_INTERFACE_THAT_NEEDS_IMPORT_DECLARATION
                         : CLASS_IMPLEMENTING_INTERFACE_THAT_NEEDS_IMPORT_DECLARATION;

    assertImport(THING_FILE_PATH_0, INTERFACE_IMPORT);
    assertDeclaration(THING_FILE_PATH_0, declaration, kind);
  }

  // Create in package tests.
  @Test
  public void createClassInCurrentPackage() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, PUBLIC_DECLARATION, Kind.CLASS);
    assertThat(myEditor.getCurrentFileContents()).doesNotContain("import ");
  }

  @Test
  public void createClassInNewSubPackage() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setPackage(PACKAGE_NAME_1);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_1, PACKAGE_NAME_1);
    assertDeclaration(THING_FILE_PATH_1, PUBLIC_DECLARATION, Kind.CLASS);
  }

  // New class file template tests.
  @Test
  public void createClassPackagePrivate() throws IOException {
    createPackagePrivate(Kind.CLASS);
  }

  @Test
  public void createClassWithOneInterface() throws IOException {
    createWithOneInterface(Kind.CLASS);
  }

  @Test
  public void createClassWithSuperclassAndInterface() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setSuperclass(SUPERCLASS_0);
    dialog.setInterface(INTERFACE_0);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, SUPERCLASS_AND_INTERFACE_DECLARATION, Kind.CLASS);
  }

  @Test
  public void createClassWithNestedInterfaceImport() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setInterface(JAVA_UTIL_MAP_ENTRY);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertImport(THING_FILE_PATH_0, JAVA_UTIL_MAP_IMPORT);
    assertDeclaration(THING_FILE_PATH_0, JAVA_UTIL_MAP_ENTRY_DECLARATION, Kind.CLASS);
  }

  // New enum file template tests.
  @Test
  public void createEnumWithTwoInterfaces() throws IOException {
    createWithTwoInterfaces(Kind.ENUM);
  }

  // New interface file template tests.
  @Test
  public void createInterfaceWithInterfaceImport() throws IOException {
    createWithAnImport(Kind.INTERFACE);
  }

  // Invalid field entries tests. These tests ensure the UI reacts appropriately to invalid input. They do not test
  // that all forms of invalid input produce errors. NewClassDialogOptionsValidatorTest does that.
  @Test
  public void invalidName() throws IOException, InterruptedException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(INVALID_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();
    dialog.waitForErrorMessageToAppear(CreateNewClassDialogValidatorExImpl.INVALID_QUALIFIED_NAME_FOR_NEW_NAME);
    dialog.clickCancel();
  }

  // (Un)hiding fields tests.
  @Test
  public void hidingComponents() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.selectKind(Kind.CLASS);
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("none_radio_button", JRadioButton.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("abstract_radio_button", JRadioButton.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("final_radio_button", JRadioButton.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("modifiers_label", JLabel.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("overrides_separator", JSeparator.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("overrides_check_box", JCheckBox.class, ComponentVisibility.VISIBLE)));

    dialog.selectKind(Kind.INTERFACE);
    assertFalse(ComponentVisibleQuery.isVisible(dialog.find("none_radio_button", JRadioButton.class, ComponentVisibility.NOT_VISIBLE)));
    assertFalse(ComponentVisibleQuery.isVisible(dialog.find("abstract_radio_button", JRadioButton.class, ComponentVisibility.NOT_VISIBLE)));
    assertFalse(ComponentVisibleQuery.isVisible(dialog.find("final_radio_button", JRadioButton.class, ComponentVisibility.NOT_VISIBLE)));
    assertFalse(ComponentVisibleQuery.isVisible(dialog.find("modifiers_label", JLabel.class, ComponentVisibility.NOT_VISIBLE)));
    assertFalse(ComponentVisibleQuery.isVisible(dialog.find("overrides_separator", JSeparator.class, ComponentVisibility.NOT_VISIBLE)));
    assertFalse(ComponentVisibleQuery.isVisible(dialog.find("overrides_check_box", JCheckBox.class, ComponentVisibility.NOT_VISIBLE)));

    dialog.selectKind(Kind.CLASS);
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("none_radio_button", JRadioButton.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("abstract_radio_button", JRadioButton.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("final_radio_button", JRadioButton.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("modifiers_label", JLabel.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("overrides_separator", JSeparator.class, ComponentVisibility.VISIBLE)));
    assertTrue(ComponentVisibleQuery.isVisible(dialog.find("overrides_check_box", JCheckBox.class, ComponentVisibility.VISIBLE)));
    dialog.clickCancel();
  }

  // Interfaces vs. classes.
  @Test
  public void implementAClass() throws IOException {
    String objectClass = "java.lang.Object";
    CreateFileFromTemplateDialogFixture dialog = invokeNewFileDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setInterface(objectClass);
    dialog.clickOk();
    dialog.waitForErrorMessageToAppear(CreateNewClassDialogValidatorExImpl.NOT_A_VALID_INTERFACE + objectClass);
    dialog.clickCancel();
  }

}
