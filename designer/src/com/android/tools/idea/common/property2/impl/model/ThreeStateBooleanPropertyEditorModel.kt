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
package com.android.tools.idea.common.property2.impl.model

import com.android.SdkConstants.VALUE_TRUE
import com.android.SdkConstants.VALUE_FALSE
import com.android.tools.idea.common.property2.api.PropertyItem

/**
 * Model for a 3 state boolean property: on/off/unset.
 */
class ThreeStateBooleanPropertyEditorModel(property: PropertyItem) : BasePropertyEditorModel(property) {

  override var value: String
    get() = property.resolvedValue.orEmpty()
    set(value) { super.value = value }

  override fun toggleValue() {
    value = when (value) {
      VALUE_TRUE -> VALUE_FALSE
      VALUE_FALSE -> ""
      else -> VALUE_TRUE
    }
  }
}
