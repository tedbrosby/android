/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.dynamicapp;

import static com.android.tools.idea.npw.model.NewProjectModel.toPackagePart;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_FUSING;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_ON_DEMAND;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_TITLE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_IS_INSTANT_MODULE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_DYNAMIC_FEATURE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_PROJECT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MAKE_IGNORE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_SIMPLE_NAME;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class DynamicFeatureModel extends WizardModel {
  @NotNull private final Project myProject;
  @NotNull private final TemplateHandle myTemplateHandle;
  @NotNull private final ProjectSyncInvoker myProjectSyncInvoker;

  @NotNull private final StringProperty myModuleName = new StringValueProperty("dynamic_feature");
  @NotNull private final StringProperty myFeatureTitle = new StringValueProperty("Module Title");
  @NotNull private final StringProperty myPackageName = new StringValueProperty();
  @NotNull private final OptionalProperty<AndroidVersionsInfo.VersionItem> myAndroidSdkInfo = new OptionalValueProperty<>();
  @NotNull private final OptionalProperty<Module> myBaseApplication = new OptionalValueProperty<>();
  @NotNull private final BoolProperty myFeatureOnDemand = new BoolValueProperty(true);
  @NotNull private final BoolProperty myFeatureFusing = new BoolValueProperty(true);
  @NotNull private final BoolProperty myInstantModule = new BoolValueProperty(false);

  public DynamicFeatureModel(@NotNull Project project,
                             @NotNull TemplateHandle templateHandle,
                             @NotNull ProjectSyncInvoker projectSyncInvoker) {
    myProject = project;
    myTemplateHandle = templateHandle;
    myProjectSyncInvoker = projectSyncInvoker;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public TemplateHandle getTemplateHandle() {
    return myTemplateHandle;
  }

  @NotNull
  public StringProperty moduleName() {
    return myModuleName;
  }

  @NotNull
  public StringProperty featureTitle() {
    return myFeatureTitle;
  }

  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  public OptionalProperty<Module> baseApplication() {
    return myBaseApplication;
  }

  public OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo() {
    return myAndroidSdkInfo;
  }

  public BoolProperty featureOnDemand() {
    return myFeatureOnDemand;
  }

  public BoolProperty featureFusing() {
    return myFeatureFusing;
  }

  public BoolProperty instantModule() { return myInstantModule; }

  @Override
  protected void handleFinished() {
    File moduleRoot = new File(myProject.getBasePath(), moduleName().get());
    Map<String, Object> myTemplateValues = Maps.newHashMap();

    new TemplateValueInjector(myTemplateValues)
      .setModuleRoots(GradleAndroidModuleTemplate.createDefaultTemplateAt(moduleRoot).getPaths(), packageName().get())
      .setBuildVersion(androidSdkInfo().getValue(), myProject)
      .setBaseFeature(baseApplication().getValue());

    myTemplateValues.put(ATTR_IS_DYNAMIC_FEATURE, true);
    myTemplateValues.put(ATTR_MODULE_SIMPLE_NAME, toPackagePart(moduleName().get()));
    myTemplateValues.put(ATTR_DYNAMIC_FEATURE_TITLE, featureTitle().get());
    myTemplateValues.put(ATTR_DYNAMIC_FEATURE_ON_DEMAND, featureOnDemand().get());
    myTemplateValues.put(ATTR_DYNAMIC_FEATURE_FUSING, featureFusing().get());
    myTemplateValues.put(ATTR_MAKE_IGNORE, true);
    myTemplateValues.put(ATTR_IS_NEW_PROJECT, true);
    myTemplateValues.put(ATTR_IS_LIBRARY_MODULE, false);
    myTemplateValues.put(ATTR_DYNAMIC_IS_INSTANT_MODULE, instantModule().get());

    if (doDryRun(moduleRoot, myTemplateValues)) {
      render(moduleRoot, myTemplateValues);
    }
  }

  private boolean doDryRun(@NotNull File moduleRoot, @NotNull Map<String, Object> templateValues) {
    return renderTemplate(true, myProject, moduleRoot, templateValues);
  }

  private void render(@NotNull File moduleRoot, @NotNull Map<String, Object> templateValues) {
    renderTemplate(false, myProject, moduleRoot, templateValues);
    myProjectSyncInvoker.syncProject(myProject);
  }

  private boolean renderTemplate(boolean dryRun,
                                 @NotNull Project project,
                                 @NotNull File moduleRoot,
                                 @NotNull Map<String, Object> templateValues) {
    Template template = myTemplateHandle.getTemplate();

    // @formatter:off
    final RenderingContext context = RenderingContext.Builder.newContext(template, project)
      .withCommandName(message("android.wizard.module.new.module.menu.description"))
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(moduleRoot)
      .withParams(templateValues)
      .build();
    // @formatter:on

    return template.render(context, dryRun);
  }
}
