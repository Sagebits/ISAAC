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
package sh.isaac.komet.gui.treeview;

import javax.inject.Singleton;
import org.jvnet.hk2.annotations.Service;
import javafx.scene.Node;
import sh.isaac.MetaData;
import sh.isaac.api.component.concept.ConceptSpecification;
import sh.isaac.api.preferences.IsaacPreferences;
import sh.isaac.komet.iconography.Iconography;
import sh.komet.gui.contract.ExplorationNodeFactory;
import sh.komet.gui.interfaces.ExplorationNode;
import sh.komet.gui.manifold.Manifold;
import sh.komet.gui.manifold.Manifold.ManifoldGroup;

/**
 *
 * @author kec
 */
@Service(name = "Multi-Parent Tree View Provider")
@Singleton

public class TreeViewExplorationNodeFactory 
        implements ExplorationNodeFactory {
   public static final String MENU_TEXT  = "Taxonomy";

   @Override
   public ExplorationNode createNode(Manifold manifold, IsaacPreferences preferencesNode) {
      MultiParentTreeView multiParentTreeView = new MultiParentTreeView(manifold, MetaData.SOLOR_CONCEPT____SOLOR);
      return multiParentTreeView;
   }

   @Override
   public String getMenuText() {
      return MENU_TEXT;
   }

   @Override
   public Node getMenuIcon() {
      return Iconography.TAXONOMY_ICON.getIconographic();
   }
   
   @Override
   public boolean isEnabled() {
      return true;
   }

   /** 
    * {@inheritDoc}
    */
   @Override
   public ManifoldGroup[] getDefaultManifoldGroups() {
      return new ManifoldGroup[] {ManifoldGroup.TAXONOMY};
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public PanelPlacement getPanelPlacement() {
      return PanelPlacement.LEFT;
   }

   @Override
   public ConceptSpecification getPanelType() {
      return MetaData.TAXONOMY_PANEL____SOLOR;
   }
}
