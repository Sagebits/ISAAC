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
package sh.komet.gui.cell.treetable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.PropertySheet;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeTableRow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import sh.isaac.api.Get;
import sh.isaac.api.commit.CommitRecord;
import sh.isaac.api.commit.CommitTask;
import sh.isaac.api.component.semantic.version.LogicGraphVersion;
import sh.isaac.api.observable.ObservableCategorizedVersion;
import sh.isaac.api.observable.ObservableVersion;
import sh.isaac.komet.iconography.Iconography;
import sh.komet.gui.cell.CellFunctions;
import sh.komet.gui.cell.CellHelper;
import sh.komet.gui.control.FixedSizePane;
import sh.komet.gui.control.PropertyToPropertySheetItem;
import sh.komet.gui.control.property.PropertyEditorFactory;
import sh.komet.gui.manifold.Manifold;
import sh.komet.gui.style.StyleClasses;
import sh.komet.gui.util.FxGet;

//~--- classes ----------------------------------------------------------------
/**
 *
 * @author kec
 */
public class TreeTableGeneralCell
        extends KometTreeTableCell<ObservableCategorizedVersion> implements CellFunctions {

    private static final Logger LOG = LogManager.getLogger();

    //~--- fields --------------------------------------------------------------
    private final Manifold manifold;
    private final Button editButton = new Button("", Iconography.EDIT_PENCIL.getIconographic());
    private final GridPane textAndEditGrid = new GridPane();
    private final BorderPane editPanel = new BorderPane();
    private ObservableVersion version;
    private final FixedSizePane paneForText = new FixedSizePane();
    private final ToolBar toolBar;
    private ObservableVersion mutableVersion;
    private final CellHelper cellHelper = new CellHelper(this);

    //~--- constructors --------------------------------------------------------
    public TreeTableGeneralCell(Manifold manifold) {
        this.manifold = manifold;
        getStyleClass().add("komet-version-general-cell");
        getStyleClass().add("isaac-version");
        editButton.getStyleClass()
                .setAll(StyleClasses.EDIT_COMPONENT_BUTTON.toString());
        editButton.setOnAction(this::toggleEdit);
        textAndEditGrid.getChildren().addAll(paneForText, editButton, editPanel);
        setContextMenu(cellHelper.makeContextMenu());
        // setConstraints(Node child, int columnIndex, int rowIndex, int columnspan, int rowspan, HPos halignment, VPos valignment, Priority hgrow, Priority vgrow)
        GridPane.setConstraints(paneForText, 0, 0, 1, 2, HPos.LEFT, VPos.TOP, Priority.ALWAYS, Priority.NEVER);
        GridPane.setConstraints(editButton, 2, 0, 1, 1, HPos.RIGHT, VPos.TOP, Priority.NEVER, Priority.NEVER);
        GridPane.setConstraints(editPanel, 0, 2, 3, 1, HPos.LEFT, VPos.TOP, Priority.ALWAYS, Priority.ALWAYS);
        final Pane leftSpacer = new Pane();
        HBox.setHgrow(
                leftSpacer,
                Priority.SOMETIMES
        );
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(this::toggleEdit);
        Button commitButton = new Button("Commit");
        commitButton.setOnAction(this::commitEdit);
        toolBar = new ToolBar(
                leftSpacer,
                cancelButton,
                new Separator(),
                commitButton
        );

    }

    //~--- methods -------------------------------------------------------------


    public GridPane getTextAndEditGrid() {
        return textAndEditGrid;
    }

    public FixedSizePane getPaneForText() {
        return paneForText;
    }

    @Override
    public Manifold getManifold() {
        return manifold;
    }

    public void initializeConceptBuilder() {
        this.cellHelper.initializeConceptBuilder(version);
    }

    public void search() {
        this.cellHelper.search(this.version);
    }

    public void addDefToCell(LogicGraphVersion logicGraphVersion) {
        cellHelper.addDefToCell(logicGraphVersion);
    }

    public void addTextToCell(Text... text) {
        cellHelper.addTextToCell(text);
    }

    private void commitEdit(ActionEvent event) {
        CommitTask commitTask = Get.commitService().commit(
                FxGet.editCoordinate(),
                "No comment",
                this.mutableVersion);
        Get.executor().execute(() -> {
            try {
                Optional<CommitRecord> commitRecord = commitTask.get();
                if (commitRecord.isPresent()) {
                    Platform.runLater(() -> {
                        editPanel.getChildren().clear();
                        editButton.setVisible(true);
                    });
                } else {
                    // TODO show errors. 
                    commitTask.getAlerts();
                }
            } catch (InterruptedException | ExecutionException ex) {
                LOG.error("Error committing change.", ex);
            } finally {
            }
        });
    }

    private void toggleEdit(ActionEvent event) {

        if (editPanel.getChildren().isEmpty()) {
            if (this.version != null) {
                if (this.version instanceof ObservableVersion) {
                    ObservableVersion currentVersion = (ObservableVersion) this.version;
                    mutableVersion = currentVersion.makeAutonomousAnalog(FxGet.editCoordinate());

                    List<Property<?>> propertiesToEdit = mutableVersion.getEditableProperties();
                    PropertySheet propertySheet = new PropertySheet();
                    propertySheet.setMode(PropertySheet.Mode.NAME);
                    propertySheet.setSearchBoxVisible(false);
                    propertySheet.setModeSwitcherVisible(false);
                    propertySheet.setPropertyEditorFactory(new PropertyEditorFactory(this.manifold));
                    propertySheet.getItems().addAll(PropertyToPropertySheetItem.getItems(propertiesToEdit, this.manifold));

                    editPanel.setTop(toolBar);
                    editPanel.setCenter(propertySheet);
                    editButton.setVisible(false);
                }
            }
        } else {
            editPanel.getChildren().clear();
            editButton.setVisible(true);
        }
    }

    @Override
    protected void updateItem(TreeTableRow<ObservableCategorizedVersion> row, ObservableCategorizedVersion version) {
        cellHelper.updateItem(version, this, this.getTableColumn());
    }
}
