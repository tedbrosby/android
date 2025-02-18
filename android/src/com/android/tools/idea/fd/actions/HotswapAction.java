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
package com.android.tools.idea.fd.actions;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.fd.gradle.InstantRunGradleSupport;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.actions.AndroidStudioGradleAction;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.SUPPORTED;

public class HotswapAction extends AndroidStudioGradleAction implements AnAction.TransparentUpdate {

  private static final CustomShortcutSet SHORTCUT_SET = CustomShortcutSet.fromString(SystemInfo.isMac ? "control meta R" : "control F10");

  public HotswapAction() {
    super("Apply Changes", "Apply Changes", StudioIcons.Shell.Toolbar.INSTANT_RUN);
    setShortcutSet(SHORTCUT_SET);
    if (StudioFlags.JVMTI_REFRESH.get()) {
      throw new IllegalStateException("Non JVMTI swap action should not be created");
    }
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);

    if (!InstantRunSettings.isInstantRunEnabled()) {
      presentation.setText("Apply Changes: Instant Run has been disabled");
      return;
    }

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings == null) {
      presentation.setText("Apply Changes: No run configuration selected");
      return;
    }

    AndroidSessionInfo session = getAndroidSessionInfo(project, settings);
    if (session == null || session.getDevices().isEmpty()) {
      presentation.setText(String.format("Apply Changes: No active '%1$s' launch", settings.getName()));
      return;
    }

    ProcessHandler processHandler = getActiveProcessHandler(project, settings);
    if (processHandler == null) {
      presentation.setText(String.format("Apply Changes: No active '%1$s' launch", settings.getName()));
      return;
    }

    RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof ModuleBasedConfiguration)) {
      presentation.setText(String.format("Apply Changes: '%1$s' is not a module based configuration", settings.getName()));
      return;
    }

    Module module = ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule();
    if (module == null) {
      presentation.setText(String.format("Apply Changes: No module specified in '%1$s'", settings.getName()));
      return;
    }

    if (!(configuration instanceof AndroidRunConfigurationBase)) {
      presentation.setText(String.format("Apply Changes: '%1$s' is not an Android launch configuration", settings.getName()));
      return;
    }

    if (!((AndroidRunConfigurationBase)configuration).supportsInstantRun()) {
      presentation.setText(String.format("Apply Changes: Configuration '%1$s' does not support instant run", settings.getName()));
      return;
    }

    AndroidVersion androidVersion = InstantRunManager.getMinDeviceApiLevel(processHandler);
    if (androidVersion == null) {
      presentation.setText(String.format("Apply Changes: Cannot locate device from '%1$s'", settings.getName()));
      return;
    }

    if (!InstantRunManager.isInstantRunCapableDeviceVersion(androidVersion)) {
      presentation.setText(String.format("Apply Changes: Target device API level (%1$s) too low for Instant Run", androidVersion));
      return;
    }

    InstantRunGradleSupport status = InstantRunGradleUtils.getIrSupportStatus(InstantRunGradleUtils.getAppModel(module), androidVersion);
    if (status != SUPPORTED) {
      String notification = status.getUserNotification();
      if (notification == null) {
        notification = status.toString();
      }
      presentation.setText("Apply Changes: " + notification);
      return;
    }

    if (!InstantRunGradleUtils.appHasCode(AndroidFacet.getInstance(module))) {
      return;
    }

    presentation.setText("Apply Changes");
    presentation.setEnabled(true);
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings == null) {
      InstantRunManager.LOG.warn("Hotswap Action could not locate current run config settings");
      return;
    }

    Executor executor;
    if (StudioFlags.JVMTI_REFRESH.get()) {
      // TODO: Figure out the debugger flow. For now always use the Run executor.
      executor = getExecutor(DefaultRunExecutor.EXECUTOR_ID);
    } else {
      AndroidSessionInfo session = getAndroidSessionInfo(project, settings);
      if (session == null) {
        InstantRunManager.LOG.warn("Hotswap Action could not locate an existing session for selected run config.");
        return;
      }
      executor = getExecutor(session.getExecutorId());
    }
    if (executor == null) {
      InstantRunManager.LOG.warn("Hotswap Action could not identify executor");
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      InstantRunManager.LOG.warn("Hotswap Action could not construct an env");
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    InstantRunUtils.setInvokedViaHotswapAction(env, true);
    InstantRunManager.LOG.info("Invoking hotswap launch");
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Nullable
  private static ProcessHandler getActiveProcessHandler(@Nullable Project project, @Nullable RunnerAndConfigurationSettings settings) {
    if (project == null || settings == null) {
      return null;
    }

    AndroidSessionInfo session = getAndroidSessionInfo(project, settings);
    if (session == null) {
      return null;
    }

    ProcessHandler processHandler = session.getProcessHandler();
    if (processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
      return null;
    }

    return processHandler;
  }

  @Nullable
  private static AndroidSessionInfo getAndroidSessionInfo(Project project, RunnerAndConfigurationSettings settings) {
    AndroidSessionInfo session = AndroidSessionInfo.findOldSession(project, null, settings.getConfiguration().getUniqueID());
    if (session == null) {
      return null;
    }

    return session;
  }

  @Nullable
  private static Executor getExecutor(@NotNull String executorId) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
      if (executorId.equals(executor.getId())) {
        return executor;
      }
    }

    return null;
  }
}
