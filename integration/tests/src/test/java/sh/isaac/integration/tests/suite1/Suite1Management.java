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



package sh.isaac.integration.tests.suite1;

import java.io.File;

//~--- non-JDK imports --------------------------------------------------------

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.jvnet.testing.hk2testng.HK2;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import sh.isaac.api.Get;
import sh.isaac.api.LookupService;
import sh.isaac.api.memory.HeapUseTicker;
import sh.isaac.api.progress.ActiveTasksTicker;
import sh.isaac.api.util.RecursiveDelete;

//~--- classes ----------------------------------------------------------------

/**
 * The Class IntegrationSuiteManagement.
 *
 * @author kec
 */

//https://www.jfokus.se/jfokus08/pres/jf08-HundredKilobytesKernelHK2.pdf
//https://github.com/saden1/hk2-testng
@HK2("integration")
@Test(suiteName="suite1")
public class Suite1Management {
   /** The Constant LOG. */
   private static final Logger LOG = LogManager.getLogger();

   //~--- methods -------------------------------------------------------------

   /**
    * Tear down suite.
    *
    * @throws Exception the exception
    */
   @AfterSuite
   public void tearDownSuite()
            throws Exception {
   	LOG.info("Suite 1 teardown");
      LookupService.shutdownSystem();
      ActiveTasksTicker.stop();
      HeapUseTicker.stop();
   }

   //~--- set methods ---------------------------------------------------------

   /**
    * Set up suite.
    *
    * @throws Exception the exception
    */
   @BeforeSuite
   public void setUpSuite()
            throws Exception {
      LOG.info("Suite 1 setup");
      RecursiveDelete.delete(new File("target/suite1"));
      Get.configurationService().setDataStoreFolderPath(new File("target/suite1").toPath());
      LookupService.startupPreferenceProvider();

      LOG.info("termstore folder path exists: " + Get.configurationService().getDataStoreFolderPath().toFile().exists());

      LookupService.startupIsaac();
      ActiveTasksTicker.start(10);
      HeapUseTicker.start(10);
   }
}

