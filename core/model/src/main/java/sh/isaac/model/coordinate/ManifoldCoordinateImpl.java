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



package sh.isaac.model.coordinate;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.xml.bind.annotation.XmlElement;
import sh.isaac.api.ConfigurationService;
import sh.isaac.api.Get;
import sh.isaac.api.Status;
import sh.isaac.api.component.concept.ConceptSpecification;
import sh.isaac.api.coordinate.LanguageCoordinate;
import sh.isaac.api.coordinate.LogicCoordinate;
import sh.isaac.api.coordinate.ManifoldCoordinate;
import sh.isaac.api.coordinate.PremiseType;
import sh.isaac.api.coordinate.StampCoordinate;

/**
 * The Class ManifoldCoordinateImpl.
 *
 * @author kec
 */
public class ManifoldCoordinateImpl
         implements ManifoldCoordinate {

   /** The taxonomy type. */
   PremiseType taxonomyPremiseType;

   /** The stamp coordinate. */
   StampCoordinate stampCoordinate;

   /** The language coordinate. */
   LanguageCoordinate languageCoordinate;

   /** The logic coordinate. */
   LogicCoordinate logicCoordinate;

   /**
    * Instantiates a new taxonomy coordinate impl.
    */
   private ManifoldCoordinateImpl() {
      // for jaxb
   }

   /**
    * Instantiates a new taxonomy coordinate impl.
    *
    * @param taxonomyType the taxonomy type
    * @param stampCoordinate the stamp coordinate
    * @param languageCoordinate the language coordinate
    * @param logicCoordinate the logic coordinate
    */
   public ManifoldCoordinateImpl(PremiseType taxonomyType,
                                 StampCoordinate stampCoordinate,
                                 LanguageCoordinate languageCoordinate,
                                 LogicCoordinate logicCoordinate) {
      this.taxonomyPremiseType       = taxonomyType;
      this.stampCoordinate    = stampCoordinate;
      this.languageCoordinate = languageCoordinate;
      this.logicCoordinate    = logicCoordinate;
      //this.uuid               //lazy load
   }
   
   /**
    * Instantiates a new taxonomy coordinate impl.  Calls {@link #ManifoldCoordinateImpl(PremiseType, StampCoordinate, LanguageCoordinate, LogicCoordinate)}
    * with a {@link PremiseType#STATED} and the default Logic Coordinate from {@link ConfigurationService}
    *
    * @param stampCoordinate the stamp coordinate
    * @param languageCoordinate - optional - uses default if not provided.  the language coordinate
    */
   public ManifoldCoordinateImpl(StampCoordinate stampCoordinate,
                                 LanguageCoordinate languageCoordinate) {
      this(PremiseType.STATED, stampCoordinate, 
          languageCoordinate == null ? Get.configurationService().getUserConfiguration(Optional.empty()).getLanguageCoordinate() : languageCoordinate,
          Get.configurationService().getUserConfiguration(Optional.empty()).getLogicCoordinate());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   @XmlElement
   public UUID getManifoldCoordinateUuid() {
      return ManifoldCoordinate.super.getManifoldCoordinateUuid(); //To change body of generated methods, choose Tools | Templates.
   }
   
   private void setManifoldCoordinateUuid(UUID uuid) {
        // noop for jaxb
   }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        
        if (getClass() != obj.getClass()) {
            return false;
        }
        
        final ManifoldCoordinateImpl other = (ManifoldCoordinateImpl) obj;
        
        if (this.taxonomyPremiseType != other.taxonomyPremiseType) {
            return false;
        }
        
        if (!Objects.equals(this.stampCoordinate, other.stampCoordinate)) {
            return false;
        }
        
        if (!Objects.equals(this.logicCoordinate, other.logicCoordinate)) {
            return false;
        }
        
        return Objects.equals(this.languageCoordinate, other.languageCoordinate);
    }

   /**
    * {@inheritDoc}
    */
   @Override
   public int hashCode() {
      int hash = 3;

      hash = 53 * hash + Objects.hashCode(this.taxonomyPremiseType);
      hash = 53 * hash + Objects.hashCode(this.stampCoordinate);
      hash = 53 * hash + Objects.hashCode(this.languageCoordinate);
      return hash;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ManifoldCoordinateImpl makeCoordinateAnalog(long stampPositionTime) {
      return new ManifoldCoordinateImpl(this.taxonomyPremiseType,
                                        this.stampCoordinate.makeCoordinateAnalog(stampPositionTime),
                                        this.languageCoordinate,
                                        this.logicCoordinate);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ManifoldCoordinateImpl makeCoordinateAnalog(PremiseType taxonomyType) {
      return new ManifoldCoordinateImpl(taxonomyType,
                                        this.stampCoordinate,
                                        this.languageCoordinate,
                                        this.logicCoordinate);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ManifoldCoordinateImpl makeCoordinateAnalog(Status... state) {
      return new ManifoldCoordinateImpl(this.taxonomyPremiseType,
                                        this.stampCoordinate.makeCoordinateAnalog(state),
                                        this.languageCoordinate,
                                        this.logicCoordinate);
   }

   /**
    * {@inheritDoc}
    * @see sh.isaac.api.coordinate.StampCoordinate#makeModuleAnalog(int[], boolean)
    */
   @Override
   public ManifoldCoordinateImpl makeModuleAnalog(Collection<ConceptSpecification> modules, boolean add) {
      return new ManifoldCoordinateImpl(this.taxonomyPremiseType, 
            this.stampCoordinate.makeModuleAnalog(modules, add), 
            this.languageCoordinate, 
            this.logicCoordinate);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String toString() {
      return "ManifoldCoordinateImpl{" + this.taxonomyPremiseType + ",\n" + this.stampCoordinate + ", \n" +
             this.languageCoordinate + ", \n" + this.logicCoordinate + ", uuid=" + getCoordinateUuid() + '}';
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public LanguageCoordinate getLanguageCoordinate() {
      return this.languageCoordinate;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public LogicCoordinate getLogicCoordinate() {
      return this.logicCoordinate;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public StampCoordinate getStampCoordinate() {
      return this.stampCoordinate;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public PremiseType getTaxonomyPremiseType() {
      return this.taxonomyPremiseType;
   }
   public void setTaxonomyPremiseType(PremiseType taxonomyPremiseType) {
       this.taxonomyPremiseType = taxonomyPremiseType;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ManifoldCoordinateImpl deepClone() {
      ManifoldCoordinateImpl newCoordinate = new ManifoldCoordinateImpl(taxonomyPremiseType,
                                 stampCoordinate.deepClone(),
                                 languageCoordinate.deepClone(),
                                 logicCoordinate.deepClone());
      return newCoordinate;
   }
}