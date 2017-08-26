/* 
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributions from 2013-2017 where performed either by US government 
 * employees, or under US Veterans Health Administration contracts. 
 *
 * US Veterans Health Administration contributions by government employees
 * are work of the U.S. Government and are not subject to copyright
 * protection in the United States. Portions contributed by government 
 * employees are USGovWork (17USC §105). Not subject to copyright. 
 * 
 * Contribution by contractors to the US Veterans Health Administration
 * during this period are contractually contributed under the
 * Apache License, Version 2.0.
 *
 * See: https://www.usa.gov/government-works
 * 
 * Contributions prior to 2013:
 *
 * Copyright (C) International Health Terminology Standards Development Organisation.
 * Licensed under the Apache License, Version 2.0.
 *
 */
package sh.komet.gui.drag.drop;

//~--- non-JDK imports --------------------------------------------------------
import javafx.event.EventHandler;

import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;

import sh.isaac.api.identity.IdentifiedObject;

import sh.komet.gui.interfaces.DraggableWithImage;

//~--- classes ----------------------------------------------------------------
/**
 * {@link DragDetectedCellEventHandler}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
public class DragDetectedCellEventHandler
        implements EventHandler<MouseEvent> {

   public DragDetectedCellEventHandler() {
   }

   //~--- methods -------------------------------------------------------------
   /**
    * @param event
    * @see javafx.event.EventHandler#handle(javafx.event.Event)
    */
   @Override
   public void handle(MouseEvent event) {
      /* drag was detected, start a drag-and-drop gesture */
 /* allow any transfer mode */
      Node eventNode = null;
      IdentifiedObject identifiedObject = null;

      if (event.getSource() instanceof TreeCell) {
         eventNode = (TreeCell<IdentifiedObject>) event.getSource();
         identifiedObject = ((TreeCell<IdentifiedObject>) event.getSource()).getItem();
      } else if (event.getSource() instanceof TableCell) {
         eventNode = (TableCell) event.getSource();
         Object item = ((TableCell) eventNode).getItem();
         if (item instanceof String) {
            identifiedObject = (IdentifiedObject) ((TableCell) eventNode).getTableRow().getItem();
         }
      } else if (event.getSource() instanceof TableView) {
         TableView<IdentifiedObject> tableView = (TableView) event.getSource();

         identifiedObject = tableView.getSelectionModel()
                 .getSelectedItem();
         eventNode = event.getPickResult()
                 .getIntersectedNode();
         eventNode = eventNode.getParent();

      }

      if (eventNode != null) {
         System.out.println(event);

         Dragboard db = eventNode.startDragAndDrop(TransferMode.COPY);

         if (eventNode instanceof DraggableWithImage) {
            DraggableWithImage draggableWithImageNode = (DraggableWithImage) eventNode;
            Image dragImage = draggableWithImageNode.getDragImage();
            double xOffset = ((dragImage.getWidth() / 2) + draggableWithImageNode.getDragViewOffsetX()) - event.getX();
            double yOffset = event.getY() - (dragImage.getHeight() / 2);

            db.setDragView(dragImage, xOffset, yOffset);
         } else {
            DragImageMaker dragImageMaker = new DragImageMaker(eventNode);
            Image dragImage = dragImageMaker.getDragImage();
            double xOffset = ((dragImage.getWidth() / 2) + dragImageMaker.getDragViewOffsetX()) - event.getX();
            double yOffset = event.getY() - (dragImage.getHeight() / 2);

            db.setDragView(dragImage, xOffset, yOffset);
         }

         /* Put a string on a dragboard */
         if (identifiedObject != null) {
            String drag = identifiedObject.getPrimordialUuid()
                    .toString();

            if ((drag != null) && (drag.length() > 0)) {
               IsaacClipboard content = new IsaacClipboard(identifiedObject);

               db.setContent(content);
               DragRegistry.dragStart();
               event.consume();
            }
         }
      }
   }
}