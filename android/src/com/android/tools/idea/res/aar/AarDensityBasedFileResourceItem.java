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
package com.android.tools.idea.res.aar;

import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing a density-specific file resource inside an AAR, e.g. a drawable or a layout.
 */
final class AarDensityBasedFileResourceItem extends AarFileResourceItem implements DensityBasedResourceValue {
  @NotNull private final Density myDensity;

  /**
   * Initializes a file resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param relativePath the path of the resource relative to the res folder, or path of a zip entry inside res.apk
   * @param density the screen density this resource is associated with
   */
  public AarDensityBasedFileResourceItem(@NotNull ResourceType type,
                                         @NotNull String name,
                                         @NotNull AarConfiguration configuration,
                                         @NotNull ResourceVisibility visibility,
                                         @NotNull String relativePath,
                                         @NotNull Density density) {
    super(type, name, configuration, visibility, relativePath);
    myDensity = density;
  }

  @Override
  @NotNull
  public Density getResourceDensity() {
    return myDensity;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarDensityBasedFileResourceItem other = (AarDensityBasedFileResourceItem) obj;
    return myDensity == other.myDensity;
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), myDensity.hashCode());
  }

  @Override
  @NotNull
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("name", getName())
                      .add("namespace", getNamespace())
                      .add("type", getResourceType())
                      .add("source", getSource())
                      .add("density", getResourceDensity())
                      .toString();
  }
}
