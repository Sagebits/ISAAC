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
package sh.isaac.api.util;

import sh.isaac.api.ConceptProxy;
import sh.isaac.api.component.concept.ConceptSpecification;

/**
 *
 * @author kec
 */
public class ArrayUtil {
    public static ConceptSpecification[] toSpecificationArray(int[] nidArray) {
        if (nidArray == null) {
            return null;
        }
        ConceptSpecification[] specArray = new ConceptSpecification[nidArray.length];
        for (int i = 0; i < specArray.length; i++) {
            specArray[i] = new ConceptProxy(nidArray[i]);
        }
        return specArray;
    }
    public static int[] toNidArray(ConceptSpecification[] specArray) {
        if (specArray == null) {
           return null;
        }
        int[] nidArray = new int[specArray.length];
        for (int i = 0; i < specArray.length; i++) {
            nidArray[i] = specArray[i].getNid();
        }
        return nidArray;
    }
}
