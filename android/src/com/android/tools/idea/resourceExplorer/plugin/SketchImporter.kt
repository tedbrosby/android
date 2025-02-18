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
package com.android.tools.idea.resourceExplorer.plugin

import com.android.tools.idea.resourceExplorer.importer.DesignAssetImporter
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.SketchParser
import com.android.tools.idea.resourceExplorer.sketchImporter.ui.IMPORT_DIALOG_TITLE
import com.android.tools.idea.resourceExplorer.sketchImporter.ui.SketchImporterPresenter
import com.android.tools.idea.resourceExplorer.sketchImporter.ui.SketchImporterView
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.io.FilenameUtils
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JPanel


private val supportedFileTypes = setOf("sketch")
private const val oldestSupportedSketchVersion = 50.0
private const val invalidSketchFileId = "Invalid Sketch file"

/**
 * [ResourceImporter] for Sketch files
 */
class SketchImporter : ResourceImporter {
  override fun getPresentableName() = "Sketch Importer"

  override fun getConfigurationPanel(facet: AndroidFacet, callback: ConfigurationDoneCallback): JPanel? {
    val sketchFilePath = getFilePath(facet.module.project)
    if (sketchFilePath != null) {
      val sketchFile = SketchParser.read(sketchFilePath)
      if (sketchFile == null || sketchFile.meta.appVersion < oldestSupportedSketchVersion) {
        showInvalidSketchFileNotification(sketchFilePath, sketchFile?.meta?.appVersion, facet.module.project)
      }
      else {
        val view = SketchImporterView()
        view.presenter = SketchImporterPresenter(view, sketchFile, DesignAssetImporter(), facet)
        showImportDialog(facet.module.project, view)
      }
    }

    callback.configurationDone()
    return null
  }

  /**
   * Create a dialog allowing the user to preview and choose which assets they would like to import from the sketch file.
   */
  private fun showImportDialog(project: Project,
                               view: SketchImporterView) {
    DialogBuilder(project).apply {
      setCenterPanel(view)
      addDisposable(view)
      setOkOperation {
        view.presenter.importFilesIntoProject()
        dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
      }
      setTitle(IMPORT_DIALOG_TITLE)
    }.showModal(true)
  }

  override fun userCanEditQualifiers() = true

  override fun getSupportedFileTypes() = supportedFileTypes

  override fun getSourcePreview(asset: DesignAsset): DesignAssetRenderer? =
    DesignAssetRendererManager.getInstance().getViewer(SVGAssetRenderer::class.java)

  override fun getImportPreview(asset: DesignAsset): DesignAssetRenderer? = getSourcePreview(asset)

  /**
   * Prompts user to choose a file.
   *
   * @return filePath or null if user cancels the operation
   */
  private fun getFilePath(project: Project): String? {
    val fileDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(supportedFileTypes.first())
    val files = FileChooser.chooseFiles(fileDescriptor, project, null)
    if (files.isEmpty()) return null
    return FileUtil.toSystemDependentName(files[0].path)
  }

  /**
   * Show a notification containing information about the [version] that was used to create the file at [path]. If [version] is null,
   * that means that either the file is not valid or it has been saved using a version of Sketch older than 43.0 (which is when the open
   * file format - the zip archive format that the Sketch Importer plugin is based upon - was introduced.
   */
  private fun showInvalidSketchFileNotification(path: String, version: Double?, project: Project) {
    val fileName = FilenameUtils.getName(path)
    val generalInfo = "Please make sure you use Sketch 50.0 or higher to save your sketch file."
    val versionInfo = if (version == null) {
      "$fileName seems to not be a valid Sketch file or has been saved with a version of Sketch older than 43.0."
    }
    else {
      "$fileName seems to have been saved using Sketch $version."
    }
    val notificationContent = "$generalInfo<br/>$versionInfo"
    val notificationTitle = "Invalid sketch file"

    Notification(invalidSketchFileId, null, notificationTitle, fileName, notificationContent, NotificationType.ERROR, null)
      .notify(project)
  }
}
