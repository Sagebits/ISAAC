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
package sh.isaac.provider.stamp;

//~--- JDK imports ------------------------------------------------------------
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

//~--- non-JDK imports --------------------------------------------------------
import javafx.concurrent.Task;

//~--- JDK imports ------------------------------------------------------------
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

//~--- non-JDK imports --------------------------------------------------------
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.glassfish.hk2.runlevel.RunLevel;

import org.jvnet.hk2.annotations.Service;

import sh.isaac.api.ConfigurationService;
import sh.isaac.api.Get;
import sh.isaac.api.LookupService;
import sh.isaac.api.State;
import sh.isaac.api.SystemStatusService;
import sh.isaac.api.bootstrap.TermAux;
import sh.isaac.api.chronicle.LatestVersion;
import sh.isaac.api.collections.ConcurrentObjectIntMap;
import sh.isaac.api.collections.ConcurrentSequenceSerializedObjectMap;
import sh.isaac.api.commit.Stamp;
import sh.isaac.api.commit.StampService;
import sh.isaac.api.commit.UncommittedStamp;
import sh.isaac.api.component.semantic.version.DescriptionVersion;
import sh.isaac.api.coordinate.ManifoldCoordinate;
import sh.isaac.api.task.TimedTask;

//~--- classes ----------------------------------------------------------------
/**
 * Created by kec on 1/2/16.
 */
@Service(name = "Stamp Provider")
@RunLevel(value = 1)
public class StampProvider
        implements StampService {
   private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG);

   /**
    * The Constant LOG.
    */
   private static final Logger LOG = LogManager.getLogger();

   /**
    * The Constant STAMP_MANAGER_DATA_FILENAME.
    */
   private static final String STAMP_MANAGER_DATA_FILENAME = "stamp-manager.data";

   /**
    * The Constant DEFAULT_STAMP_MANAGER_FOLDER.
    */
   public static final String DEFAULT_STAMP_MANAGER_FOLDER = "stamp-manager";

   /**
    * TODO: persist across restarts.
    */
   private static final AtomicReference<ConcurrentHashMap<UncommittedStamp, Integer>> UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP
           = new AtomicReference(new ConcurrentHashMap<>());

   // TODO persist dataStoreId.
   private final UUID dataStoreId = UUID.randomUUID();

   @Override
   public UUID getDataStoreId() {
      return dataStoreId;
   }

   //~--- fields --------------------------------------------------------------
   /**
    * The stamp lock.
    */
   private final ReentrantLock stampLock = new ReentrantLock();

   /**
    * The next stamp sequence.
    */
   private final AtomicInteger nextStampSequence = new AtomicInteger(FIRST_STAMP_SEQUENCE);

   /**
    * Persistent map of stamp sequences to a Stamp object.
    */
   private final ConcurrentObjectIntMap<Stamp> stampMap = new ConcurrentObjectIntMap<>();

   /**
    * The load required.
    */
   private final AtomicBoolean loadRequired = new AtomicBoolean();

   /**
    * The database validity.
    */
   private DatabaseValidity databaseValidity = DatabaseValidity.NOT_SET;

   /**
    * The stamp sequence path sequence map.
    */
   ConcurrentHashMap<Integer, Integer> stampSequence_PathNid_Map = new ConcurrentHashMap();

   /**
    * The db folder path.
    */
   private final Path dbFolderPath;

   /**
    * The stamp manager folder.
    */
   private final Path stampManagerFolder;

   /**
    * Persistent as a result of reading and writing the stampMap.
    */
   private final ConcurrentSequenceSerializedObjectMap<Stamp> inverseStampMap;

   //~--- constructors --------------------------------------------------------
   /**
    * Instantiates a new stamp provider.
    *
    * @throws IOException Signals that an I/O exception has occurred.
    */
   public StampProvider()
           throws IOException {
      this.dbFolderPath = LookupService.getService(ConfigurationService.class)
              .getChronicleFolderPath()
              .resolve("stamp-provider");
      this.loadRequired.set(Files.exists(this.dbFolderPath));
      Files.createDirectories(this.dbFolderPath);
      this.inverseStampMap = new ConcurrentSequenceSerializedObjectMap<>(new StampSerializer(),
              this.dbFolderPath,
              null,
              null);
      this.stampManagerFolder = this.dbFolderPath.resolve(DEFAULT_STAMP_MANAGER_FOLDER);

      if (!Files.exists(this.stampManagerFolder)) {
         this.databaseValidity = DatabaseValidity.MISSING_DIRECTORY;
      }

      Files.createDirectories(this.stampManagerFolder);
   }

   //~--- methods -------------------------------------------------------------
   /**
    * Adds the stamp.
    *
    * @param stamp the stamp
    * @param stampSequence the stamp sequence
    */
   @Override
   public void addStamp(Stamp stamp, int stampSequence) {
      this.stampMap.put(stamp, stampSequence);
      this.inverseStampMap.put(stampSequence, stamp);
   }

   /**
    * Cancel.
    *
    * @param authorSequence the author sequence
    * @return the task
    */
   @Override
   public synchronized Task<Void> cancel(int authorSequence) {
      Map<UncommittedStamp, Integer> map = UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get();
      map.forEach((uncommittedStamp, stampSequence) -> {
         // for each uncommitted stamp matching the author, remove the uncommitted stamp
         // and replace with a canceled stamp.
         if (uncommittedStamp.authorNid == authorSequence) {
            final Stamp stamp = new Stamp(uncommittedStamp.status,
                    Long.MIN_VALUE,
                    uncommittedStamp.authorNid,
                    uncommittedStamp.moduleNid,
                    uncommittedStamp.pathNid);

            addStamp(stamp, stampSequence);
            map.remove(uncommittedStamp);
         }
      });

      // TODO make asynchronous with a actual task.
      final Task<Void> task = new TimedTask() {
         @Override
         protected Object call()
                 throws Exception {
            Get.activeTasks()
                    .remove(this);
            return null;
         }
      };

      Get.activeTasks()
              .add(task);
      Get.workExecutors()
              .getExecutor()
              .execute(task);
      return task;
   }

   /**
    * Clear database validity value.
    */
   @Override
   public void clearDatabaseValidityValue() {
      // Reset to enforce analysis
      this.databaseValidity = DatabaseValidity.NOT_SET;
   }

   @Override
   public String describeStampSequenceForTooltip(int stampSequence, ManifoldCoordinate manifoldCoordinate) {
      if (stampSequence == -1) {
         return "CANCELED";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append("S: ");
      final State status = getStatusForStamp(stampSequence);

      sb.append(status).append("\nT: ");

      final long time = getTimeForStamp(stampSequence);

      // Cannot change to case statement, since case supports int not long...
      if (time == Long.MAX_VALUE) {
         sb.append("UNCOMMITTED");
      } else if (time == Long.MIN_VALUE) {
         sb.append("CANCELED");
      } else {
         ZonedDateTime stampTime = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC);
         sb.append(stampTime.format(FORMATTER));
      }
      LatestVersion<DescriptionVersion> authorDescription = manifoldCoordinate.getPreferredDescription(getAuthorNidForStamp(stampSequence));
      if (authorDescription.isPresent()) {
         sb.append("\nA: ").append(authorDescription.get().getText());
      } else {
         sb.append("\nA: unretrievable");
      }
      
      LatestVersion<DescriptionVersion> moduleDescription = manifoldCoordinate.getPreferredDescription(getModuleNidForStamp(stampSequence));
      if (moduleDescription.isPresent()) {
         sb.append("\nM: ").append(moduleDescription.get().getText());
      } else {
         sb.append("\nM: unretrievable");
      }
      
      LatestVersion<DescriptionVersion> pathDescription = manifoldCoordinate.getPreferredDescription(getPathNidForStamp(stampSequence));
      if (pathDescription.isPresent()) {
         sb.append("\nP: ").append(pathDescription.get().getText());
      } else {
         sb.append("\nP: unretrievable");
      }
      
      Optional<String> optionalComment = Get.commitService().getComment(stampSequence);
      if (optionalComment.isPresent()) {
         sb.append("\n\ncomment: ");
         sb.append(optionalComment.get());
      }
      return sb.toString();
   }

   /**
    * Describe stamp sequence.
    *
    * @param stampSequence the stamp sequence
    * @return the string
    */
   @Override
   public String describeStampSequence(int stampSequence) {
      if (stampSequence == -1) {
         return "{Stamp≤CANCELED≥}";
      }
      final StringBuilder sb = new StringBuilder();

      sb.append("{Stamp≤");
      sb.append(stampSequence);
      sb.append("::");

      try {
         final State status = getStatusForStamp(stampSequence);
         
         sb.append(status);
         
         sb.append(" ");
         
         final long time = getTimeForStamp(stampSequence);
         
         if (time == Long.MAX_VALUE) {
            sb.append("UNCOMMITTED:");
         } else if (time == Long.MIN_VALUE) {
            sb.append("CANCELED:  ");
         } else {
            sb.append(Instant.ofEpochMilli(time));
         }
         
         sb.append(" a:");
         sb.append(Get.conceptDescriptionText(getAuthorNidForStamp(stampSequence)));
         sb.append(" m:");
         sb.append(Get.conceptDescriptionText(getModuleNidForStamp(stampSequence)));
         sb.append(" p: ");
         sb.append(Get.conceptDescriptionText(getPathNidForStamp(stampSequence)));
      } catch (Exception e) {
         sb.append(e.getMessage());
      }
      sb.append("≥}");
      return sb.toString();
   }

   /**
    * Stamp sequences equal except author and time.
    *
    * @param stampSequence1 the stamp sequence 1
    * @param stampSequence2 the stamp sequence 2
    * @return true, if successful
    */
   @Override
   public boolean stampSequencesEqualExceptAuthorAndTime(int stampSequence1, int stampSequence2) {
      if (getModuleNidForStamp(stampSequence1) != getModuleNidForStamp(stampSequence2)) {
         return false;
      }

      if (getPathNidForStamp(stampSequence1) != getPathNidForStamp(stampSequence2)) {
         return false;
      }

      return getStatusForStamp(stampSequence1) == getStatusForStamp(stampSequence2);
   }

   /**
    * Start me.
    */
   @PostConstruct
   private void startMe() {
      try {
         LOG.info("Starting StampProvider post-construct");

         if (this.loadRequired.get()) {
            LOG.info("Reading existing commit manager data. ");
            LOG.info("Reading " + STAMP_MANAGER_DATA_FILENAME);

            try (DataInputStream in = new DataInputStream(new FileInputStream(new File(this.stampManagerFolder.toFile(),
                    STAMP_MANAGER_DATA_FILENAME)))) {
               this.nextStampSequence.set(in.readInt());

               final int stampMapSize = in.readInt();

               for (int i = 0; i < stampMapSize; i++) {
                  final int stampSequence = in.readInt();
                  final Stamp stamp = new Stamp(in);

                  this.stampMap.put(stamp, stampSequence);
                  this.inverseStampMap.put(stampSequence, stamp);
               }

               final int uncommittedSize = in.readInt();

               for (int i = 0; i < uncommittedSize; i++) {
                  UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().put(new UncommittedStamp(in), in.readInt());
               }
            }

            this.databaseValidity = DatabaseValidity.POPULATED_DIRECTORY;
         }
      } catch (final IOException e) {
         LookupService.getService(SystemStatusService.class)
                 .notifyServiceConfigurationFailure("Stamp Provider", e);
         throw new RuntimeException(e);
      }
   }

   /**
    * Stop me.
    */
   @PreDestroy
   private void stopMe() {
      LOG.info("Stopping StampProvider pre-destroy. ");

      try (DataOutputStream out = new DataOutputStream(new FileOutputStream(new File(this.stampManagerFolder.toFile(),
              STAMP_MANAGER_DATA_FILENAME)))) {
         out.writeInt(this.nextStampSequence.get());
         out.writeInt(this.stampMap.size());
         this.stampMap.forEachPair((Stamp stamp,
                 int stampSequence) -> {
            try {
               out.writeInt(stampSequence);
               stamp.write(out);
            } catch (final IOException ex) {
               throw new RuntimeException(ex);
            }
         });

         final int size = UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().size();

         out.writeInt(size);

         for (final Map.Entry<UncommittedStamp, Integer> entry : UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().entrySet()) {
            entry.getKey()
                    .write(out);
            out.writeInt(entry.getValue());
         }
      } catch (final IOException e) {
         throw new RuntimeException(e);
      }
   }

   //~--- get methods ---------------------------------------------------------
   /**
    * Gets the activated stamp sequence.
    *
    * @param stampSequence the stamp sequence
    * @return the activated stamp sequence
    */
   @Override
   public int getActivatedStampSequence(int stampSequence) {
      return getStampSequence(State.ACTIVE,
              getTimeForStamp(stampSequence),
              getAuthorNidForStamp(stampSequence),
              getModuleNidForStamp(stampSequence),
              getPathNidForStamp(stampSequence));
   }

   /**
    * Gets the author nid for stamp.
    *
    * @param stampSequence the stamp sequence
    * @return the author nid for stamp
    */
   @Override
   public int getAuthorNidForStamp(int stampSequence) {
      if (stampSequence < 0) {
         return TermAux.USER.getNid();
      }

      final Optional<Stamp> s = this.inverseStampMap.get(stampSequence);

      if (s.isPresent()) {
         return s.get()
                 .getAuthorNid();
      }
      for (Map.Entry<UncommittedStamp, Integer> entry: UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().entrySet()) {
         if (entry.getValue() == stampSequence) {
            return entry.getKey().authorNid;
         }
      }

      throw new NoSuchElementException("No stampSequence found: " + stampSequence);
   }

   /**
    * Gets the database folder.
    *
    * @return the database folder
    */
   @Override
   public Path getDatabaseFolder() {
      return this.stampManagerFolder;
   }

   /**
    * Gets the database validity status.
    *
    * @return the database validity status
    */
   @Override
   public DatabaseValidity getDatabaseValidityStatus() {
      return this.databaseValidity;
   }

   /**
    * Gets the module nid for stamp.
    *
    * @param stampSequence the stamp sequence
    * @return the module nid for stamp
    */
   @Override
   public int getModuleNidForStamp(int stampSequence) {
      if (stampSequence < 0) {
         return TermAux.UNSPECIFIED_MODULE.getNid();
      }

      final Optional<Stamp> s = this.inverseStampMap.get(stampSequence);

      if (s.isPresent()) {
         return s.get().getModuleNid();
      }
      for (Map.Entry<UncommittedStamp, Integer> entry: UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().entrySet()) {
         if (entry.getValue() == stampSequence) {
            return entry.getKey().moduleNid;
         }
      }

      throw new NoSuchElementException("No stampSequence found: " + stampSequence);
   }

   /**
    * Checks if not canceled.
    *
    * @param stamp the stamp
    * @return true, if not canceled
    */
   @Override
   public boolean isNotCanceled(int stamp) {
      if (stamp < 0) {
         return false;
      }

      return getTimeForStamp(stamp) != Long.MIN_VALUE;
   }

   /**
    * Gets the path sequence for stamp.
    *
    * @param stampSequence the stamp sequence
    * @return the path sequence for stamp
    */
   @Override
   public int getPathNidForStamp(int stampSequence) {
      if (stampSequence < 0) {
         return TermAux.DEVELOPMENT_PATH.getNid();
      }

      if (this.stampSequence_PathNid_Map.containsKey(stampSequence)) {
         return this.stampSequence_PathNid_Map.get(stampSequence);
      }

      final Optional<Stamp> s = this.inverseStampMap.get(stampSequence);

      if (s.isPresent()) {
         this.stampSequence_PathNid_Map.put(stampSequence,
                 s.get().getPathNid());
         return this.stampSequence_PathNid_Map.get(stampSequence);
      }
      for (Map.Entry<UncommittedStamp, Integer> entry: UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().entrySet()) {
         if (entry.getValue() == stampSequence) {
            return entry.getKey().pathNid;
         }
      }

      throw new NoSuchElementException("No stampSequence found: " + stampSequence);
   }

   /**
    * Gets the pending stamps for commit.
    *
    * @return the pending stamps for commit
    */
   @Override
   public ConcurrentHashMap<UncommittedStamp, Integer> getPendingStampsForCommit() {
      ConcurrentHashMap<UncommittedStamp, Integer> pendingStampsForCommit
              = UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get();
      
      while (!UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.compareAndSet(pendingStampsForCommit, new ConcurrentHashMap<>())) {
         pendingStampsForCommit
              = UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get();
      }
      
      return pendingStampsForCommit;
   }

   //~--- set methods ---------------------------------------------------------
   /**
    * Set pending stamps for commit.
    *
    * @param pendingStamps the pending stamps
    */
   @Override
   synchronized public void addPendingStampsForCommit(Map<UncommittedStamp, Integer> pendingStamps) {
      UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().putAll(pendingStamps);
   }

   //~--- get methods ---------------------------------------------------------
   /**
    * Gets the retired stamp sequence.
    *
    * @param stampSequence the stamp sequence
    * @return the retired stamp sequence
    */
   @Override
   public int getRetiredStampSequence(int stampSequence) {
      return getStampSequence(State.INACTIVE,
              getTimeForStamp(stampSequence),
              getAuthorNidForStamp(stampSequence),
              getModuleNidForStamp(stampSequence),
              getPathNidForStamp(stampSequence));
   }

   /**
    * Gets the stamp sequence.
    *
    * @param status the status
    * @param time the time
    * @param authorSequence the author sequence
    * @param moduleSequence the module sequence
    * @param pathSequence the path sequence
    * @return the stamp sequence
    */
   @Override
   public int getStampSequence(State status, long time, int authorSequence, int moduleSequence, int pathSequence) {
      final Stamp stampKey = new Stamp(status, time, authorSequence, moduleSequence, pathSequence);

      if (time == Long.MAX_VALUE) {
         final UncommittedStamp usp = new UncommittedStamp(status, authorSequence, moduleSequence, pathSequence);
         final Integer temp = UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().get(usp);

         if (temp != null) {
            return temp;
         } else {
            this.stampLock.lock();

            try {
               if (UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().containsKey(usp)) {
                  return UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().get(usp);
               }

               final int stampSequence = this.nextStampSequence.getAndIncrement();

               UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().put(usp, stampSequence);
               this.inverseStampMap.put(stampSequence, stampKey);
               return stampSequence;
            } finally {
               this.stampLock.unlock();
            }
         }
      }

      OptionalInt stampValue = this.stampMap.get(stampKey);

      if (!stampValue.isPresent()) {
         // maybe have a few available in an atomic queue, and put back
         // if not used? Maybe in a thread-local?
         // Have different sequences, and have the increments be equal to the
         // number of sequences?
         this.stampLock.lock();

         try {
            stampValue = this.stampMap.get(stampKey);

            if (!stampValue.isPresent()) {
               stampValue = OptionalInt.of(this.nextStampSequence.getAndIncrement());
               this.inverseStampMap.put(stampValue.getAsInt(), stampKey);
               this.stampMap.put(stampKey, stampValue.getAsInt());
            }
         } finally {
            this.stampLock.unlock();
         }
      }

      return stampValue.getAsInt();
   }

   /**
    * Gets the stamp sequences.
    *
    * @return the stamp sequences
    */
   @Override
   public IntStream getStampSequences() {
      return IntStream.rangeClosed(FIRST_STAMP_SEQUENCE, this.nextStampSequence.get())
              .filter((stampSequence) -> this.inverseStampMap.containsKey(stampSequence));
   }

   /**
    * Gets the status for stamp.
    *
    * @param stampSequence the stamp sequence
    * @return the status for stamp
    */
   @Override
   public State getStatusForStamp(int stampSequence) {
      if (stampSequence < 0) {
         return State.CANCELED;
      }

      final Optional<Stamp> s = this.inverseStampMap.get(stampSequence);

      if (s.isPresent()) {
         return s.get()
                 .getStatus();
      }
      
      for (Map.Entry<UncommittedStamp, Integer> entry: UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().entrySet()) {
         if (entry.getValue() == stampSequence) {
            return entry.getKey().status;
         }
      }
      throw new NoSuchElementException("No stampSequence found: " + stampSequence);
   }

   /**
    * Gets the time for stamp.
    *
    * @param stampSequence the stamp sequence
    * @return the time for stamp
    */
   @Override
   public long getTimeForStamp(int stampSequence) {
      if (stampSequence < 0) {
         return Long.MIN_VALUE;
      }

      final Optional<Stamp> s = this.inverseStampMap.get(stampSequence);

      if (s.isPresent()) {
         return s.get()
                 .getTime();
      }
      if (UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.get().containsValue(stampSequence)) {
         return Long.MAX_VALUE;
      }

      throw new NoSuchElementException("No stampSequence found: " + stampSequence + " map size: "
              + this.stampMap.size() + " inverse map size: " + this.inverseStampMap.size());
   }

   /**
    * Checks if uncommitted.
    *
    * @param stampSequence the stamp sequence
    * @return true, if uncommitted
    */
   @Override
   public boolean isUncommitted(int stampSequence) {
      return getTimeForStamp(stampSequence) == Long.MAX_VALUE;
   }
}
