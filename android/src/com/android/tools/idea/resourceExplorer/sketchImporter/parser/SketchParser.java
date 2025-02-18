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
package com.android.tools.idea.resourceExplorer.sketchImporter.parser;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.*;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchDocument;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.meta.SketchMeta;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPoint2D;
import com.android.tools.idea.resourceExplorer.sketchImporter.ui.SketchFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Parses sketch files.
 */
public class SketchParser {
  private static final Logger LOG = Logger.getInstance(SketchParser.class);
  // Tells GSON which deserializers to use for special classes
  private static final Gson gson = new GsonBuilder()
    .registerTypeAdapter(SketchDocument.class, new SketchDocumentDeserializer())
    .registerTypeAdapter(SketchMeta.class, new SketchMetaDeserializer())
    .registerTypeAdapter(SketchLayer.class, new SketchLayerDeserializer())
    .registerTypeAdapter(Color.class, new ColorDeserializer())
    .registerTypeAdapter(SketchPoint2D.class, new PointDeserializer())
    .registerTypeAdapter(ResizingConstraint.class, new ConstraintDeserializer())
    .create();

  /**
   * Read data from the .sketch file (which is actually a zip archive) and turn it into an instance of {@code SketchFile}.
   *
   * @param path filepath to the .sketch file
   * @return {@link SketchFile} or {@code null} if the file could not be processed
   */
  @Nullable
  public static SketchFile read(@NotNull String path) {
    try (ZipFile zip = new ZipFile(path)) {

      SketchFile sketchFile = new SketchFile();

      for (Enumeration e = zip.entries(); e.hasMoreElements(); ) {
        ZipEntry entry = (ZipEntry)e.nextElement();

        String entryName = entry.getName();

        if (FilenameUtils.getExtension(entryName).equals("json")) {
          switch (entryName) {
            case "document.json":
              SketchDocument document = parseJson(zip.getInputStream(entry), SketchDocument.class);
              if (document != null) {
                sketchFile.setDocument(document);
              }
              break;
            case "meta.json":
              SketchMeta meta = parseJson(zip.getInputStream(entry), SketchMeta.class);
              if (meta != null) {
                sketchFile.setMeta(meta);
              }
              break;
            case "user.json":
              // TODO when needed
              break;
            default:
              SketchPage page = parseJson(zip.getInputStream(entry), SketchPage.class);
              if (page != null) {
                sketchFile.addPage(page);
              }
          }
        }
      }

      return sketchFile;
    }
    catch (Exception e) {
      LOG.warn("Failed to read from sketch file: " + path + ".", e);
    }

    return null;
  }

  /**
   * Read data (represented as JSON) from an input stream into a container of type {@code typeOfT}.
   *
   * @return an object of type {@code typeOfT} or {@code null} if the parsing failed
   */
  @Nullable
  public static <T> T parseJson(@NotNull InputStream in, @NotNull Type typeOfT) {
    try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      return gson.fromJson(reader, typeOfT);
    }
    catch (Exception e) {
      LOG.warn("Could not read JSON from input stream.", e);
    }

    return null;
  }
}