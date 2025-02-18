/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all dependencies such as
 * <ol>
 * <li>Artifact dependencies: e.g. compile com.google:guava:1.5</li>
 * <li>Module dependencies: e.g. compile project("a:b")</li>
 * <li>File dependencies: e.g. compile files("a.jar")</li>
 * </ol>
 */
public abstract class DependencyModelImpl implements DependencyModel {
  @NotNull
  protected abstract GradleDslElement getDslElement();

  @Override
  @NotNull
  public abstract String configurationName();

  @Override
  @Nullable
  public PsiElement getPsiElement() { return getDslElement().getPsiElement(); }
}
