/*
 * Copyright 2017 Organizations participating in ISAAC, ISAAC's KOMET, and SOLOR development include the 
         US Veterans Health Administration, OSHERA, and the Health Services Platform Consortium..
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
package sh.isaac.komet.iconography;

import de.jensd.fx.glyphs.GlyphIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIconView;
import de.jensd.fx.glyphs.materialicons.MaterialIconView;
import javafx.scene.Node;
import static sh.isaac.komet.iconography.Iconography.IconSource.FONT_AWSOME;
import static sh.isaac.komet.iconography.Iconography.IconSource.MATERIAL_DESIGNS_ICON;
import static sh.isaac.komet.iconography.Iconography.IconSource.MATERIAL_DESIGNS_WEBFONT;
import static sh.isaac.komet.iconography.Iconography.IconSource.SVG;

/**
 *
 * @author kec
 */
public enum Iconography {
//TODO make Iconagraphy a service/provider 
   TAXONOMY_ICON(MATERIAL_DESIGNS_WEBFONT, "taxonomy-icon"),
   TAXONOMY_ROOT_ICON(MATERIAL_DESIGNS_WEBFONT, "taxonomy-root-icon"),
   TAXONOMY_DEFINED_MULTIPARENT_OPEN(MATERIAL_DESIGNS_WEBFONT, "taxonomy-defined-multiparent-open-icon"),
   TAXONOMY_DEFINED_MULTIPARENT_CLOSED(MATERIAL_DESIGNS_WEBFONT, "taxonomy-defined-multiparent-closed-icon"),
   TAXONOMY_PRIMITIVE_MULTIPARENT_OPEN(MATERIAL_DESIGNS_WEBFONT, "taxonomy-primitive-multiparent-open-icon"),
   TAXONOMY_PRIMITIVE_MULTIPARENT_CLOSED(MATERIAL_DESIGNS_WEBFONT, "taxonomy-primitive-multiparent-closed-icon"),
   TAXONOMY_PRIMITIVE_SINGLE_PARENT(MATERIAL_DESIGNS_WEBFONT, "taxonomy-primitive-singleparent-icon"),
   TAXONOMY_DEFINED_SINGLE_PARENT(MATERIAL_DESIGNS_WEBFONT, "taxonomy-defined-singleparent-icon"),
   TAXONOMY_CLOSED(FONT_AWSOME, "taxonomy-closed-icon"),
   TAXONOMY_OPEN(FONT_AWSOME, "taxonomy-open-icon"),
   STATED_VIEW(SVG, "stated-view"),
   INFERRED_VIEW(SVG, "inferred-view"),
   SHORT_TEXT(MATERIAL_DESIGNS_ICON, "short-text"),
   LONG_TEXT(MATERIAL_DESIGNS_ICON, "long-text"),
   VANITY_BOX(SVG, "vanity-box"),
   ;

   String cssClass;
   IconSource source;

   private Iconography(IconSource source, String cssClass) {
      this.source = source;
      this.cssClass = cssClass;
   }

   public Node getIconographic() {
      switch (source) {
         case MATERIAL_DESIGNS_WEBFONT:
            GlyphIcon mdiv = new MaterialDesignIconView().setStyleClass(cssClass);
            //mdiv.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
            return mdiv;
         case FONT_AWSOME:
            GlyphIcon faiv = new FontAwesomeIconView().setStyleClass(cssClass);
            //faiv.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
            return faiv;
         case SVG:
            return new SvgIconographic().setStyleClass(cssClass);
         case MATERIAL_DESIGNS_ICON:
            GlyphIcon miv = new MaterialIconView().setStyleClass(cssClass);
            //miv.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
            return miv;
         default:
            throw new UnsupportedOperationException("Can't handle: " + source);
      }
   }


   enum IconSource {
      MATERIAL_DESIGNS_WEBFONT, MATERIAL_DESIGNS_ICON, FONT_AWSOME, SVG
   };
   
   public static String getStyleSheetStringUrl() {
      return Iconography.class.getResource("/sh/isaac/komet/iconography/Iconography.css").toString();
   }
}
