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



package sh.isaac.api.memory;

//~--- JDK imports ------------------------------------------------------------

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

import java.util.Formatter;
import java.util.List;
import java.util.Locale;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
import javax.management.Notification;
import javax.management.NotificationEmitter;

//~--- classes ----------------------------------------------------------------

/**
 * Created by kec on 4/8/15.
 */
public class MemoryUtil {
   /** The Constant memorymbean. */

   // private static final Logger log = LogManager.getLogger();
   private static final MemoryMXBean memorymbean = ManagementFactory.getMemoryMXBean();

   //~--- methods -------------------------------------------------------------

   /**
    * Dump memory info.
    */
   public static void dumpMemoryInfo() {
      try {
         System.out.println("\nDUMPING MEMORY INFO\n");

         // Read MemoryMXBean
         final MemoryMXBean memorymbean = ManagementFactory.getMemoryMXBean();

         System.out.println("Heap Memory Usage: " + memorymbean.getHeapMemoryUsage());
         System.out.println("Non-Heap Memory Usage: " + memorymbean.getNonHeapMemoryUsage());

         // Read Garbage Collection information
         final List<GarbageCollectorMXBean> gcmbeans = ManagementFactory.getGarbageCollectorMXBeans();

         for (final GarbageCollectorMXBean gcmbean: gcmbeans) {
            System.out.println("\nName: " + gcmbean.getName());
            System.out.println("Collection count: " + gcmbean.getCollectionCount());
            System.out.println("Collection time: " + gcmbean.getCollectionTime());
            System.out.println("Memory Pools: ");

            final String[] memoryPoolNames = gcmbean.getMemoryPoolNames();

            for (String memoryPoolName : memoryPoolNames) {
               System.out.println("\t" + memoryPoolName);
            }
         }

         // Read Memory Pool Information
         System.out.println("Memory Pools Info");

         final List<MemoryPoolMXBean> mempoolsmbeans = ManagementFactory.getMemoryPoolMXBeans();

         for (final MemoryPoolMXBean mempoolmbean: mempoolsmbeans) {
            System.out.println("\nName: " + mempoolmbean.getName());
            System.out.println("Usage: " + mempoolmbean.getUsage());
            System.out.println("Collection Usage: " + mempoolmbean.getCollectionUsage());
            System.out.println("Peak Usage: " + mempoolmbean.getPeakUsage());
            System.out.println("Type: " + mempoolmbean.getType());
            System.out.println("Memory Manager Names: ");

            final String[] memManagerNames = mempoolmbean.getMemoryManagerNames();

            for (String memManagerName : memManagerNames) {
               System.out.println("\t" + memManagerName);
            }

            System.out.println("\n");
         }
      } catch (final java.lang.Exception e) {
         e.printStackTrace();
      }
   }

   /**
    * Start listener.
    */
   public static void startListener() {
      final MemoryMXBean        mbean    = ManagementFactory.getMemoryMXBean();
      final NotificationEmitter emitter  = (NotificationEmitter) mbean;
      final MyListener          listener = new MyListener();

      emitter.addNotificationListener(listener, null, null);
   }

   //~--- get methods ---------------------------------------------------------

   /**
    * Gets the heap percent use.
    *
    * @return the heap percent use
    */
   public static String getHeapPercentUse() {
      final MemoryUsage   usage         = memorymbean.getHeapMemoryUsage();
      final double        heapCommitted = usage.getCommitted();
      final double        maxHeap       = usage.getMax();
      final double        percentUsed   = 100.0d * usage.getUsed() / heapCommitted;
      final double        sizeUsedInGB  = 1.0d * usage.getUsed() / 1000000000;
      final double        committedInGB = heapCommitted / 1000000000;
      final double        maxInGB       = maxHeap / 1000000000;
      final StringBuilder sb            = new StringBuilder();
      try (Formatter formatter = new Formatter(sb, Locale.US)) {
         formatter.format(" Heap used: %1$,3.2f/%2$,3.2f GB (%3$,3.1f%%) %4$,3.2f GB requested max",
                 sizeUsedInGB,
                 committedInGB,
                 percentUsed,
                 maxInGB);
      }
      return sb.toString();
   }

   //~--- inner classes -------------------------------------------------------

   /**
    * The listener interface for receiving my events.
    * The class that is interested in processing a my
    * event implements this interface, and the object created
    * with that class is registered with a component using the
    * component's <code>addMyListener<code> method. When
    * the my event occurs, that object's appropriate
    * method is invoked.
    *
    * @see MyEvent
    */
   static class MyListener
            implements javax.management.NotificationListener {
      /**
       * Handle notification.
       *
       * @param notif the notif
       * @param handback the handback
       */
      @Override
      public void handleNotification(Notification notif, Object handback) {
         // handle notification
         System.out.println(" Memory Notification: " + notif);
      }
   }
}

