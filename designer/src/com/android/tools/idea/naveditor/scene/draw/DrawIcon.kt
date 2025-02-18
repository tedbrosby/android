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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommandBase
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.common.util.iconToImage
import com.android.tools.idea.naveditor.scene.DRAW_ICON_LEVEL
import com.android.tools.idea.naveditor.scene.setRenderingHints
import icons.StudioIcons.NavEditor.Surface
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.Rectangle2D

/**
 * [DrawIcon] is a DrawCommand that draws an icon
 * in the specified rectangle.
 */
class DrawIcon(@SwingCoordinate private val rectangle: Rectangle2D.Float, private val iconType: IconType) : DrawCommandBase() {
  enum class IconType {
    START_DESTINATION,
    DEEPLINK
  }

  private val image: Image

  init {
    val icon = when (iconType) {
      DrawIcon.IconType.START_DESTINATION -> Surface.START_DESTINATION
      DrawIcon.IconType.DEEPLINK -> Surface.DEEPLINK
    }

    image = iconToImage(icon).getScaledInstance(rectangle.width.toInt(), rectangle.height.toInt(), Image.SCALE_SMOOTH)
  }

  private constructor(sp: Array<String>) : this(stringToRect2D(sp[0]), IconType.valueOf(sp[1]))

  constructor(s: String) : this(parse(s, 2))

  override fun getLevel(): Int {
    return DRAW_ICON_LEVEL
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName, rect2DToString(rectangle), iconType)
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    setRenderingHints(g)
    g.drawImage(image, rectangle.x.toInt(), rectangle.y.toInt(), null)
  }
}