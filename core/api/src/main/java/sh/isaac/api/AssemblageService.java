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



package sh.isaac.api;

//~--- JDK imports ------------------------------------------------------------

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.Set;

//~--- non-JDK imports --------------------------------------------------------

import org.jvnet.hk2.annotations.Contract;
import sh.isaac.api.bootstrap.TermAux;
import sh.isaac.api.chronicle.Chronology;
import sh.isaac.api.chronicle.LatestVersion;
import sh.isaac.api.chronicle.VersionType;
import sh.isaac.api.collections.NidSet;

import sh.isaac.api.component.concept.ConceptSpecification;
import sh.isaac.api.coordinate.StampCoordinate;
import sh.isaac.api.component.semantic.SemanticChronology;
import sh.isaac.api.component.semantic.SemanticSnapshotService;
import sh.isaac.api.component.semantic.version.ComponentNidVersion;
import sh.isaac.api.component.semantic.version.SemanticVersion;
import sh.isaac.api.component.semantic.version.brittle.Nid1_Int2_Version;
import sh.isaac.api.externalizable.IsaacObjectType;
import sh.isaac.api.observable.ObservableVersion;

//~--- interfaces -------------------------------------------------------------

/**
 * The Interface AssemblageService.
 *
 * @author kec
 */
@Contract
public interface AssemblageService
        extends DatastoreServices {

   /**
    * Write a SemanticChronology to the assemblage service. Will not overwrite a SemanticChronology if one already exists, rather it will
    * merge the written SemanticChronology with the provided semantic.
    *
    *
    * The persistence of the concept is dependent on the persistence
    * of the underlying service.
    *
    * @param semanticChronicle the SemanticChronology 
    */
   void writeSemanticChronology(SemanticChronology semanticChronicle);

   //~--- get methods ---------------------------------------------------------

   /**
    * Gets the descriptions for component.
    *
    * @param componentNid the component nid
    * @return the descriptions for component
    * @TODO needs to integrate the language at some point...
    */
   List<SemanticChronology> getDescriptionsForComponent(int componentNid);

   /**
    * Gets the optional semantic chronology.
    *
    * @param semanticNid  nid for a semantic chronology
    * @return the identified {@code SemanticChronology}
    */
   Optional<? extends SemanticChronology> getOptionalSemanticChronology(int semanticNid);

   /**
    * Gets the SemanticChronology.
    *
    * @param semanticNid  nid for a SemanticChronology
    * @return the identified {@code SemanticChronology}
    */
   SemanticChronology getSemanticChronology(int semanticNid);

   /**
    * Gets the SemanticChronology stream.
    *
    * @return the SemanticChronology stream
    */
   Stream<SemanticChronology> getSemanticChronologyStream();

   /**
    * Gets the SemanticChronology key stream.
    *
    * @return the SemanticChronology key stream
    */
   IntStream getSemanticNidStream();

   /**
    * 
    * @return count of all the semantic chronologies, active, or inactive. 
    */
   int getSemanticCount();

   /**
    * @param assemblageConceptNid The nid for the assemblage to count elements from
    * @return count of all the semantic chronologies in the assemblage, active, or inactive. 
    */
   int getSemanticCount(int assemblageConceptNid);

   
   /**
    * 
    * @param assemblageConceptNid
    * @return the type of object contained within the assemblage. 
    */
   IsaacObjectType getObjectTypeForAssemblage(int assemblageConceptNid);
   
   VersionType getVersionTypeForAssemblage(int assemblageConceptNid);

   /**
    * Gets the SemanticChronology key stream.
    *
    * @param assemblageConceptNid The nid for the assemblage to select the nids from
    * @return the SemanticChronology key stream
    */
   IntStream getSemanticNidStream(int assemblageConceptNid);

   /**
    * Gets the SemanticChronology nids for component.
    *
    * @param componentNid the component nid
    * @return the SemanticChronology nids for component
    */
   NidSet getSemanticNidsForComponent(int componentNid);

   /**
    * Gets the SemanticChronology nids for component from assemblage.
    *
    * @param componentNid the component nid
    * @param assemblageConceptNids The (optional) set of assemblage types to limit the return to.  If empty or null, no assemblage filter is applied.
    * @return the SemanticChronology nids for component from assemblage
    */
   NidSet getSemanticNidsForComponentFromAssemblages(int componentNid, Set<Integer> assemblageConceptNids);
   
   /**
    * Gets the SemanticChronology nids for component from assemblage.
    *
    * @param componentNid the component nid
    * @param assemblageConceptNid the assemblage nid
    * @return the SemanticChronology nids for component from assemblage
    */
   NidSet getSemanticNidsForComponentFromAssemblage(int componentNid, int assemblageConceptNid);


   /**
    * Gets the SemanticChronology nids from assemblage.
    *
    * @param assemblageConceptNid the assemblage nid
    * @return the SemanticChronology nids from assemblage
    */
   NidSet getSemanticNidsFromAssemblage(int assemblageConceptNid);

   /**
    * Gets the SemanticChronology for component.
    *
    * @param <C>
    * @param componentNid the component nid
    * @return the SemanticChronology for component
    */
   <C extends SemanticChronology> Stream<C> getSemanticChronologyStreamForComponent(int componentNid);

   /**
    * Gets the SemanticChronology for component from assemblage.
    *
    * @param <C>
    * @param componentNid the component nid
    * @param assemblageConceptNids The (optional) set of assemblage types to limit the return to.  If empty or null, no assemblage filter is applied.
    * @return the SemanticChronologies for component from assemblage
    */
   <C extends SemanticChronology> Stream<C> getSemanticChronologyStreamForComponentFromAssemblages(int componentNid,
         Set<Integer> assemblageConceptNids);
   
   /**
    * Gets the SemanticChronology for component from assemblage.
    *
    * @param <C>
    * @param componentNid the component nid
    * @param assemblageConceptNid the assemblage concept nid
    * @return the SemanticChronologies for component from assemblage
    */
   <C extends SemanticChronology> Stream<C> getSemanticChronologyStreamForComponentFromAssemblage(int componentNid,
         int assemblageConceptNid);

   /**
    * Gets the SemanticChronologies from assemblage.
    *
    * @param <C>
    * @param assemblageConceptNid the assemblage concept nid
    * @return the SemanticChronologies from assemblage
    */
   <C extends SemanticChronology> Stream<C> getSemanticChronologyStream(int assemblageConceptNid);

   /**
    * Gets the SemanticChronologies from assemblage.
    *
    * @param <C>
    * @param assemblageConceptNid the assemblage concept nic
    * @return the SemanticChronologies from assemblage
    */
   <C extends Chronology> Stream<C> getChronologyStream(int assemblageConceptNid);

   /**
    * Gets the referenced component nids from assemblage.
    *
    * @param conceptSpecification the assemblage concept specification
    * @return the referenced component nids as an IntStream
    */
   default IntStream getReferencedComponentNidStreamFromAssemblage(ConceptSpecification conceptSpecification) {
      return getReferencedComponentNidStreamFromAssemblage(conceptSpecification.getNid());
   }
   
   /**
    * Gets the referenced component nids from assemblage.
    *
    * @param assemblageConceptNid the assemblage concept nid
    * @return the referenced component nids as an IntStream
    */
   default IntStream getReferencedComponentNidStreamFromAssemblage(int assemblageConceptNid) {
      return getSemanticChronologyStream(assemblageConceptNid).mapToInt((semantic) -> semantic.getReferencedComponentNid());
   }
   
   /**
    * @return an array of nids for the concepts that define assemblages. 
    */
   int[] getAssemblageConceptNids(); 
   
   /**
    * Gets the snapshot.
    *
    * @param <V> the value type
    * @param versionType the version type
    * @param stampCoordinate the stamp coordinate
    * @return the snapshot
    */
   <V extends SemanticVersion> SemanticSnapshotService<V> getSnapshot(Class<V> versionType,
         StampCoordinate stampCoordinate);

   /**
    * Gets the snapshot.
    *
    * @param <V> the value type
    * @param assemblageConceptNid
    * @param versionType the version type
    * @param stampCoordinate the stamp coordinate
    * @return the snapshot
    */
   <V extends SemanticVersion> SingleAssemblageSnapshot<V> getSingleAssemblageSnapshot(int assemblageConceptNid,
           Class<V> versionType,
           StampCoordinate stampCoordinate);

   default <V extends SemanticVersion> SingleAssemblageSnapshot<V> getSingleAssemblageSnapshot(
           ConceptSpecification assemblageConcept,
           Class<V> versionType,
           StampCoordinate stampCoordinate) {
       return getSingleAssemblageSnapshot(assemblageConcept.getNid(),
           versionType,
           stampCoordinate);
   }

   /**
    * Use in circumstances when not all semantics may have been loaded to find out if a semantic is present,
    * without incurring the overhead of reading back the object. 
    * @param semanticId nid or semantic instance
    * @return true if present, false otherwise
    */
   boolean hasSemantic(int semanticId);

   /**
    * 
    * @param assemblageConceptNid
    * @return memory used in bytes
    */
    int getAssemblageMemoryInUse(int assemblageConceptNid);
    /**
     * 
     * @param assemblageConceptNid
     * @return disk space used in bytes
     */
    int getAssemblageSizeOnDisk(int assemblageConceptNid);
    
    /**
     * 
     * @param assemblageConcept
     * @param stampCoordinate
     * @return the OptionalInt for the nid of the concepts that defines the semantics of this assemblage. 
     */
    default OptionalInt getSemanticTypeConceptForAssemblage(ConceptSpecification assemblageConcept, StampCoordinate stampCoordinate) {
        return getSemanticTypeConceptForAssemblage(assemblageConcept.getNid(), stampCoordinate);
    }
    
    /**
     * 
     * @param assemblageConceptNid
     * @param stampCoordiante
    * @return the OptionalInt for the nid of the concepts that defines the semantics of this assemblage. 
     */
    default OptionalInt getSemanticTypeConceptForAssemblage(int assemblageConceptNid, StampCoordinate stampCoordiante) {
        NidSet assemblageSemanticType = getSemanticNidsForComponentFromAssemblage(assemblageConceptNid, TermAux.SEMANTIC_TYPE.getNid());
        if (assemblageSemanticType.isEmpty()) {
            return OptionalInt.empty();
        }
        SemanticChronology typeSemantic = getSemanticChronology(assemblageSemanticType.asArray()[0]);
        LatestVersion<ComponentNidVersion> latestVersion = typeSemantic.getLatestVersion(stampCoordiante);
        if (latestVersion.isPresent()) {
            return OptionalInt.of(latestVersion.get().getComponentNid());
        }
        return OptionalInt.empty();
    }
    
    default OptionalInt getPropertyIndexForSemanticField(int semanticFieldConceptNid, int assemblageConceptNid, StampCoordinate stampCoordiante) {
        NidSet propertyIndexes = getSemanticNidsForComponentFromAssemblage(assemblageConceptNid, TermAux.ASSEMBLAGE_SEMANTIC_FIELDS.getNid());
        if (propertyIndexes.isEmpty()) {
            return OptionalInt.empty();
        }
        for (int semanticNid: propertyIndexes.asArray()) {
            SemanticChronology typeSemanticChronology = getSemanticChronology(semanticNid);
            LatestVersion<Nid1_Int2_Version> latestVersion = typeSemanticChronology.getLatestVersion(stampCoordiante);
            if (latestVersion.isPresent() && latestVersion.get().getNid1() == semanticFieldConceptNid) {
                return OptionalInt.of(latestVersion.get().getInt2() + ObservableVersion.PROPERTY_INDEX.SEMANTIC_FIELD_START.getIndex());
            }
        }
        return OptionalInt.empty();
    }
}

