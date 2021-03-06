/*
 * Copyright 2018 Organizations participating in ISAAC, ISAAC's KOMET, and SOLOR development include the
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
package sh.komet.gui.contract.preferences;

import javafx.collections.ObservableList;
import javafx.stage.Stage;
import org.jvnet.hk2.annotations.Contract;
import sh.isaac.api.preferences.IsaacPreferences;
import sh.komet.gui.manifold.Manifold;

/**
 *
 * @author kec
 */
@Contract
public interface KometPreferences {
    Stage showPreferences(Manifold manifold);
    void loadPreferences(Manifold manifold);
    void reloadPreferences();
    void resetUserPreferences();
    void closePreferences();

    ObservableList<AttachmentItem> getAttachmentItems();
    ObservableList<ConfigurationPreference> getConfigurationPreferences();
    ObservableList<LogicItem> getLogicItems();
    ObservableList<SynchronizationItem> getSynchronizationItems();
    ObservableList<TaxonomyItem> getTaxonomyItems();
    ObservableList<UserPreferenceItems> getUserPreferences();
    ObservableList<WindowPreferenceItems> getWindowPreferences();

}
