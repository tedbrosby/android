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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.*
import com.android.tools.idea.naveditor.model.*
import com.android.tools.idea.naveditor.scene.*
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.ui.scale.JBUIScale
import java.awt.Font


// Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
@NavCoordinate
private val NAVIGATION_ARC_SIZE = JBUIScale.scale(12f)

/**
 * [SceneDecorator] for the whole of a navigation flow (that is, the root component).
 */
object NavigationDecorator : NavBaseDecorator() {

  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    if (isDisplayRoot(sceneContext, component)) {
      return
    }

    val sceneView = sceneContext.surface?.currentSceneView ?: return

    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))
    @SwingCoordinate val borderRectangle = convertToRoundRect(sceneView, drawRectangle, NAVIGATION_ARC_SIZE)
    list.add(DrawFilledRoundRectangle(DRAW_FRAME_LEVEL, borderRectangle, sceneContext.colorSet.componentBackground))
    list.add(DrawRoundRectangle(DRAW_FRAME_LEVEL, borderRectangle, frameColor(sceneContext, component), frameThickness(component)))

    val text = component.nlComponent.includeFileName ?: "Nested Graph"

    val font = regularFont(sceneContext, Font.BOLD)
    list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, text, drawRectangle,
                               textColor(sceneContext, component), font, true))
  }

  override fun buildListChildren(list: DisplayList,
                                 time: Long,
                                 sceneContext: SceneContext,
                                 component: SceneComponent) {
    if (isDisplayRoot(sceneContext, component)) {
      if (component.childCount > 0) {
        val unClip = list.addClip(sceneContext, component.fillRect(null))
        buildRootList(list, time, sceneContext, component)
        list.add(unClip)
      }
    }
    else {
      // TODO: Either set an appropriate clip here, or make this the default behavior in the base class
      for (child in component.children) {
        child.buildDisplayList(time, list, sceneContext)
      }
    }
  }

  /**
   * Build the displaylist for the root component, ensuring that the right components are on top. Specifically:
   * - If a destination is selected or considered to be selected, it is drawn on top of destinations that aren't selected.
   * - If an action is selected or considered to be selected, it as well as its source and destination are drawn on top of destinations
   *   that aren't selected.
   * A destination is considered to be selected if it is in fact selected, or if an action to or from it is in fact selected.
   * An action is considered to be selected if it is in fact selected, or if its source or target are in fact selected.
   */
  private fun buildRootList(list: DisplayList,
                            time: Long,
                            sceneContext: SceneContext,
                            component: SceneComponent) {
    val selectedComponents = mutableSetOf<SceneComponent>()

    // Find all actions that should be considered to be selected, and mark them as well as their source and target as selected.
    // This should find everything considered to be selected (except destinations that are in fact selected with no actions, which are
    // already in the list anyway).
    for (child in component.children) {
      val childNlComponent = child.nlComponent
      if (childNlComponent.isAction) {
        val destination = childNlComponent.effectiveDestination?.let { component.getSceneComponent(it) }
        val source = childNlComponent.getEffectiveSource(component.nlComponent)?.let { component.getSceneComponent(it) }
        if (child.isSelected || destination?.isSelected == true || source?.isSelected == true) {
          selectedComponents.add(child)
          source?.let { selectedComponents.add(it) }
          destination?.let { selectedComponents.add(it) }
        }
      }
    }

    for (child in component.children) {
      val childList = DisplayList()
      child.buildDisplayList(time, childList, sceneContext)
      val actionOffset = if (child.nlComponent.isAction) -1 else 0
      val level = if (child.isDragging) {
        DrawCommand.TOP_LEVEL + actionOffset
      }
      else if (selectedComponents.contains(child) || child.flatten().any { it.isSelected }) {
        DrawCommand.COMPONENT_SELECTED_LEVEL + actionOffset
      }
      else {
        DrawCommand.COMPONENT_LEVEL + actionOffset
      }
      list.add(childList.getCommand(level))
    }
  }

  private fun isDisplayRoot(sceneContext: SceneContext, sceneComponent: SceneComponent): Boolean {
    return (sceneContext.surface as NavDesignSurface?)?.currentNavigation == sceneComponent.nlComponent
  }
}
