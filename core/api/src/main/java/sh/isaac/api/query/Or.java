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



package sh.isaac.api.query;

//~--- JDK imports ------------------------------------------------------------

import javax.xml.bind.annotation.XmlRootElement;

//~--- non-JDK imports --------------------------------------------------------

import sh.isaac.api.collections.NidSet;

//~--- classes ----------------------------------------------------------------

/**
 * Clause that computes the union of the results of the enclosed <code>Clauses</code>.
 *
 * @author dylangrald
 */
@XmlRootElement()
public class Or
        extends ParentClause {
   /**
    * Default no arg constructor for Jaxb.
    */
   protected Or() {
      super();
   }

   /**
    * Instantiates a new or.
    *
    * @param enclosingQuery the enclosing query
    * @param clauses the clauses
    */
   public Or(Query enclosingQuery, Clause... clauses) {
      super(enclosingQuery, clauses);
   }

   //~--- methods -------------------------------------------------------------

   /**
    * Compute components.
    *
    * @param incomingComponents the incoming components
    * @return the nid set
    */
   @Override
   public NidSet computeComponents(NidSet incomingComponents) {
      final NidSet results = new NidSet();

      getChildren().stream().forEach((clause) -> {
                               results.or(clause.computeComponents(incomingComponents));
                            });
      return results;
   }

   /**
    * Compute possible components.
    *
    * @param searchSpace the search space
    * @return the nid set
    */
   @Override
   public NidSet computePossibleComponents(NidSet searchSpace) {
      final NidSet results = new NidSet();

      getChildren().stream().forEach((clause) -> {
                               results.or(clause.computePossibleComponents(searchSpace));
                            });
      return results;
   }

   //~--- get methods ---------------------------------------------------------

   /**
    * Gets the where clause.
    *
    * @return the where clause
    */
   @Override
   public WhereClause getWhereClause() {
      final WhereClause whereClause = new WhereClause();

      whereClause.setSemantic(ClauseSemantic.OR);
      getChildren().stream().forEach((clause) -> {
                               whereClause.getChildren()
                                          .add(clause.getWhereClause());
                            });
      return whereClause;
   }
}
