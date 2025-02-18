// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors

import com.android.tools.idea.gradle.structure.configurables.ContainerConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.ChildModelConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.*
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.productflavors.ProductFlavorConfigPanel
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsFlavorDimension
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent
import javax.swing.JPanel

class ProductFlavorConfigurable(private val productFlavor: PsProductFlavor)
  : ChildModelConfigurable<PsProductFlavor, ProductFlavorConfigPanel>(
  productFlavor) {
  override fun getBannerSlogan() = "Product Flavor '${productFlavor.name}'"
  override fun createPanel(): ProductFlavorConfigPanel = ProductFlavorConfigPanel(productFlavor)
}

class FlavorDimensionConfigurable(
    private val module: PsAndroidModule,
    val flavorDimension: PsFlavorDimension
) : NamedConfigurable<PsFlavorDimension>(), ContainerConfigurable<PsProductFlavor> {
  override fun getEditableObject(): PsFlavorDimension = flavorDimension
  override fun getBannerSlogan(): String = "Dimension '$flavorDimension'"
  override fun isModified(): Boolean = false
  override fun getDisplayName(): String = flavorDimension.name
  override fun apply() = Unit
  override fun setDisplayName(name: String?) = throw UnsupportedOperationException()
  override fun getChildrenModels(): Collection<PsProductFlavor> =
    module.productFlavors.filter { it.dimension.maybeValue == flavorDimension.name }
  override fun createChildConfigurable(model: PsProductFlavor): NamedConfigurable<PsProductFlavor> =
    ProductFlavorConfigurable(model).also { Disposer.register(this, it) }
  override fun onChange(disposable: Disposable, listener: () -> Unit) = module.productFlavors.onChange(disposable, listener)
  override fun dispose() = Unit

  private val component = JPanel()
  override fun createOptionsPanel(): JComponent = component
}

fun productFlavorPropertiesModel(isLibrary: Boolean) =
  PropertiesUiModel(
    listOfNotNull(
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.dimension, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.minSdkVersion, ::simplePropertyEditor),
      if (!isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.applicationId, ::simplePropertyEditor) else null,
      if (!isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.applicationIdSuffix, ::simplePropertyEditor) else null,
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.targetSdkVersion, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.maxSdkVersion, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.signingConfig, ::simplePropertyEditor),
      if (isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.consumerProGuardFiles, ::listPropertyEditor) else null,
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.proGuardFiles, ::listPropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.manifestPlaceholders, ::mapPropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.multiDexEnabled, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.resConfigs, ::listPropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunner, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments, ::mapPropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testApplicationId, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionCode, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionName, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionNameSuffix, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks, ::listPropertyEditor)))

