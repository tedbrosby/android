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
package com.android.tools.idea.common.editor;

import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.PanZoomListener;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.util.Collections;
import java.util.List;

/**
 * The actions toolbar updates dynamically based on the component selection, their
 * parents (and if no selection, the root layout)
 */
public final class ActionsToolbar implements DesignSurfaceListener, Disposable, PanZoomListener, ConfigurationListener,
                                             ModelListener {

  private static final int CONFIGURATION_UPDATE_FLAGS = ConfigurationListener.CFG_TARGET |
                                                        ConfigurationListener.CFG_DEVICE;

  private final DesignSurface mySurface;
  private final JComponent myToolbarComponent;
  private ActionToolbar myNorthToolbar;
  private ActionToolbar myNorthEastToolbar;
  private ActionToolbarImpl myCenterToolbar;
  private ActionToolbar myEastToolbar;
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();
  private Configuration myConfiguration;
  private NlLayoutType myLayoutType;
  private ToolbarActionGroups myToolbarActionGroups;

  public ActionsToolbar(@NotNull Disposable parent, @NotNull DesignSurface surface) {
    Disposer.register(parent, this);
    mySurface = surface;
    mySurface.addListener(this);
    mySurface.addPanZoomListener(this);
    if (myConfiguration == null) {
      myConfiguration = mySurface.getConfiguration();
      if (myConfiguration != null) {
        myConfiguration.addListener(this);
      }
    }
    myToolbarComponent = createToolbarComponent();
    updateActionGroups(surface.getLayoutType());
    updateActions();
  }

  @Override
  public void dispose() {
    mySurface.removePanZoomListener(this);
    mySurface.removeListener(this);
    if (myConfiguration != null) {
      myConfiguration.removeListener(this);
    }
    if (mySurface.getModel() != null) {
      mySurface.getModel().removeListener(this);
    }
  }

  @NotNull
  public JComponent getToolbarComponent() {
    return myToolbarComponent;
  }

  @TestOnly
  ActionToolbarImpl getCenterToolbar() {
    return myCenterToolbar;
  }

  @NotNull
  private static JComponent createToolbarComponent() {
    JComponent panel = new AdtPrimaryPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StudioColorsKt.getBorder()));
    return panel;
  }

  private void updateActionGroups(@NotNull NlLayoutType layoutType) {
   myToolbarComponent.removeAll();
    if (myToolbarActionGroups != null) {
      Disposer.dispose(myToolbarActionGroups);
    }
    myToolbarActionGroups = layoutType.getToolbarActionGroups(mySurface);
    Disposer.register(this, myToolbarActionGroups);

    myNorthToolbar = createActionToolbar("NlConfigToolbar", myToolbarActionGroups.getNorthGroup());
    myNorthToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);

    JComponent northToolbarComponent = myNorthToolbar.getComponent();
    northToolbarComponent.setName("NlConfigToolbar");

    myNorthEastToolbar = createActionToolbar("NlRhsConfigToolbar", myToolbarActionGroups.getNorthEastGroup());

    JComponent northEastToolbarComponent = myNorthEastToolbar.getComponent();
    northEastToolbarComponent.setName("NlRhsConfigToolbar");

    myCenterToolbar = createActionToolbar("NlLayoutToolbar", myDynamicGroup);

    JComponent centerToolbarComponent = myCenterToolbar.getComponent();
    centerToolbarComponent.setName("NlLayoutToolbar");
    // Wrap the component inside a fixed height component so it doesn't disappear
    JPanel centerToolbarComponentWrapper = new AdtPrimaryPanel(new BorderLayout());
    centerToolbarComponentWrapper.add(centerToolbarComponent);

    myEastToolbar = createActionToolbar("NlRhsToolbar", myToolbarActionGroups.getEastGroup());

    JComponent eastToolbarComponent = myEastToolbar.getComponent();
    eastToolbarComponent.setName("NlRhsToolbar");

    if (northToolbarComponent.isVisible()) {
      JComponent northPanel = new AdtPrimaryPanel(new BorderLayout());
      northPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StudioColorsKt.getBorder()));
      northPanel.add(northToolbarComponent, BorderLayout.CENTER);
      northPanel.add(northEastToolbarComponent, BorderLayout.EAST);
      myToolbarComponent.add(northPanel, BorderLayout.NORTH);
    }

    myToolbarComponent.add(centerToolbarComponentWrapper, BorderLayout.CENTER);
    myToolbarComponent.add(eastToolbarComponent, BorderLayout.EAST);
  }

  @NotNull
  private static ActionToolbarImpl createActionToolbar(@NotNull String place, @NotNull ActionGroup group) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(place, group, true);
    toolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    if (group == ActionGroup.EMPTY_GROUP) {
      toolbar.getComponent().setVisible(false);
    }
    return (ActionToolbarImpl)toolbar;
  }

  public void updateActions() {
    SceneView view = mySurface.getCurrentSceneView();
    if (view != null) {
      SelectionModel selectionModel = view.getSelectionModel();
      List<NlComponent> selection = selectionModel.getSelection();
      if (selection.isEmpty()) {
        List<NlComponent> roots = view.getModel().getComponents();
        if (roots.size() == 1) {
          selection = Collections.singletonList(roots.get(0));
        }
        else {
          // Model not yet rendered: when it's done, update. Listener is removed as soon as palette fires from listener callback.
          view.getModel().addListener(this);
          updateBottomActionBarBorder();
          return;
        }
      }
      updateActions(selection);
    }
  }

  private void updateActions(@NotNull List<NlComponent> newSelection) {
    SceneView screenView = mySurface.getCurrentSceneView();
    if (screenView == null) {
      return;
    }

    // TODO: Perform caching
    myDynamicGroup.removeAll();

    mySurface.getActionManager().addActions(myDynamicGroup, null, newSelection, true);
    updateBottomActionBarBorder();
    myCenterToolbar.clearPresentationCache();
  }

  // ---- Implements DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
    assert surface == mySurface;
    if (!newSelection.isEmpty()) {
      updateActions(newSelection);
    }
    else {
      updateActions();
    }
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    myNorthToolbar.updateActionsImmediately();
    Configuration surfaceConfiguration = surface.getConfiguration();
    if (surfaceConfiguration != myConfiguration) {
      if (myConfiguration != null) {
        myConfiguration.removeListener(this);
      }
      myConfiguration = surfaceConfiguration;
      if (myConfiguration != null) {
        myConfiguration.addListener(this);
      }
    }
    NlLayoutType surfaceLayoutType = surface.getLayoutType();
    if (surfaceLayoutType != myLayoutType) {
      myLayoutType = surfaceLayoutType;
      updateActionGroups(myLayoutType);
    }
    updateActions();
  }

  // Hide the bottom border on the main toolbar when the toolbar is empty.
  // This eliminates the double border from the toolbar when the north toolbar is visible.
  private void updateBottomActionBarBorder() {
    boolean hasBottomActionBar = myEastToolbar.getComponent().isVisible() || myDynamicGroup.getChildrenCount() > 0;
    int bottom = hasBottomActionBar ? 1 : 0;
    myToolbarComponent.setBorder(BorderFactory.createMatteBorder(0, 0, bottom, 0, StudioColorsKt.getBorder()));
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    return false;
  }

  // ---- Implements ModelListener ----

  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    if (model.getComponents().size() == 1) {
      updateActions();
      model.removeListener(this);
    }
  }

  @Override
  public void zoomChanged(@NotNull DesignSurface surface) {
    myNorthEastToolbar.updateActionsImmediately();
  }

  @Override
  public void panningChanged(@NotNull AdjustmentEvent event) {
    myNorthEastToolbar.updateActionsImmediately();
  }

  @Override
  public boolean changed(int flags) {
    if ((flags & CONFIGURATION_UPDATE_FLAGS) > 0) {
      if (myNorthToolbar != null) {
        // the North toolbar is the one holding the Configuration Actions
        myNorthToolbar.updateActionsImmediately();
      }
    }
    return true;
  }
}
