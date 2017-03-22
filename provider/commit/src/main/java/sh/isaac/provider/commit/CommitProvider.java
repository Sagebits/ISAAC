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



/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
 */
package sh.isaac.provider.commit;

//~--- JDK imports ------------------------------------------------------------

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

//~--- non-JDK imports --------------------------------------------------------

import javafx.collections.ObservableList;

import javafx.concurrent.Task;

//~--- JDK imports ------------------------------------------------------------

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

//~--- non-JDK imports --------------------------------------------------------

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.mahout.math.map.OpenIntIntHashMap;

import org.glassfish.hk2.runlevel.RunLevel;

import org.jvnet.hk2.annotations.Service;

import sh.isaac.api.ConfigurationService;
import sh.isaac.api.Get;
import sh.isaac.api.LookupService;
import sh.isaac.api.SystemStatusService;
import sh.isaac.api.chronicle.ObjectChronology;
import sh.isaac.api.chronicle.ObjectChronologyType;
import sh.isaac.api.collections.ConceptSequenceSet;
import sh.isaac.api.collections.SememeSequenceSet;
import sh.isaac.api.collections.StampSequenceSet;
import sh.isaac.api.collections.UuidIntMapMap;
import sh.isaac.api.commit.Alert;
import sh.isaac.api.commit.AlertType;
import sh.isaac.api.commit.ChangeChecker;
import sh.isaac.api.commit.CheckPhase;
import sh.isaac.api.commit.ChronologyChangeListener;
import sh.isaac.api.commit.CommitRecord;
import sh.isaac.api.commit.CommitService;
import sh.isaac.api.commit.UncommittedStamp;
import sh.isaac.api.component.concept.ConceptChronology;
import sh.isaac.api.component.sememe.SememeChronology;
import sh.isaac.api.component.sememe.SememeType;
import sh.isaac.api.coordinate.EditCoordinate;
import sh.isaac.api.externalizable.OchreExternalizable;
import sh.isaac.api.externalizable.StampAlias;
import sh.isaac.api.externalizable.StampComment;
import sh.isaac.api.task.SequentialAggregateTask;
import sh.isaac.api.task.TimedTask;
import sh.isaac.model.ObjectChronologyImpl;
import sh.isaac.model.ObjectVersionImpl;

//~--- classes ----------------------------------------------------------------

/**
 * The Class CommitProvider.
 *
 * @author kec
 */
@Service(name = "Commit Provider")
@RunLevel(value = 1)
public class CommitProvider
         implements CommitService {
   /** The Constant LOG. */
   private static final Logger LOG = LogManager.getLogger();

   /** The Constant DEFAULT_CRADLE_COMMIT_MANAGER_FOLDER. */
   public static final String DEFAULT_CRADLE_COMMIT_MANAGER_FOLDER = "commit-manager";

   /** The Constant COMMIT_MANAGER_DATA_FILENAME. */
   private static final String COMMIT_MANAGER_DATA_FILENAME = "commit-manager.data";

   /** The Constant STAMP_ALIAS_MAP_FILENAME. */
   private static final String STAMP_ALIAS_MAP_FILENAME = "stamp-alias.map";

   /** The Constant STAMP_COMMENT_MAP_FILENAME. */
   private static final String STAMP_COMMENT_MAP_FILENAME = "stamp-comment.map";

   /** The Constant WRITE_POOL_SIZE. */
   private static final int WRITE_POOL_SIZE = 40;

   //~--- fields --------------------------------------------------------------

   /** The uncommitted sequence lock. */
   private final ReentrantLock uncommittedSequenceLock = new ReentrantLock();

   /** The write permit reference. */
   private final AtomicReference<Semaphore> writePermitReference =
      new AtomicReference<>(new Semaphore(WRITE_POOL_SIZE));

   /** The write completion service. */
   private final WriteCompletionService writeCompletionService = new WriteCompletionService();

   /** The change listeners. */
   ConcurrentSkipListSet<WeakReference<ChronologyChangeListener>> changeListeners = new ConcurrentSkipListSet<>();

   /** The checkers. */
   private final ConcurrentSkipListSet<ChangeChecker> checkers = new ConcurrentSkipListSet<>();

   /** The last commit. */
   private long lastCommit = Long.MIN_VALUE;

   /** The load required. */
   private final AtomicBoolean loadRequired = new AtomicBoolean();

   /** The deferred import no check nids. */
   ThreadLocal<Set<Integer>> deferredImportNoCheckNids = new ThreadLocal<>();

   /**
    * TODO recreate alert collection at restart for uncommitted components.
    */
   private final ConcurrentSkipListSet<Alert> alertCollection = new ConcurrentSkipListSet<>();

   /** Persistent map of a stamp aliases to a sequence. */
   private final StampAliasMap stampAliasMap = new StampAliasMap();

   /**
    * Persistent map of comments to a stamp.
    */
   private final StampCommentMap stampCommentMap = new StampCommentMap();

   /** Persistent sequence of database commit actions. */
   private final AtomicLong databaseSequence = new AtomicLong();

   /** Persistent stamp sequence. */
   private final ConceptSequenceSet uncommittedConceptsWithChecksSequenceSet = ConceptSequenceSet.concurrent();

   /** The uncommitted concepts no checks sequence set. */
   private final ConceptSequenceSet uncommittedConceptsNoChecksSequenceSet = ConceptSequenceSet.concurrent();

   /** The uncommitted sememes with checks sequence set. */
   private final SememeSequenceSet uncommittedSememesWithChecksSequenceSet = SememeSequenceSet.concurrent();

   /** The uncommitted sememes no checks sequence set. */
   private final SememeSequenceSet uncommittedSememesNoChecksSequenceSet = SememeSequenceSet.concurrent();

   /** The database validity. */
   private DatabaseValidity databaseValidity = DatabaseValidity.NOT_SET;

   /** The db folder path. */
   private final Path dbFolderPath;

   /** The commit manager folder. */
   private final Path commitManagerFolder;

   //~--- constructors --------------------------------------------------------

   /**
    * Instantiates a new commit provider.
    *
    * @throws IOException Signals that an I/O exception has occurred.
    */
   private CommitProvider()
            throws IOException {
      try {
         this.dbFolderPath = LookupService.getService(ConfigurationService.class)
                                          .getChronicleFolderPath()
                                          .resolve("commit-provider");
         this.loadRequired.set(Files.exists(this.dbFolderPath));
         Files.createDirectories(this.dbFolderPath);
         this.commitManagerFolder = this.dbFolderPath.resolve(DEFAULT_CRADLE_COMMIT_MANAGER_FOLDER);

         if (!Files.exists(this.commitManagerFolder)) {
            this.databaseValidity = DatabaseValidity.MISSING_DIRECTORY;
         }

         Files.createDirectories(this.commitManagerFolder);
      } catch (final Exception e) {
         LookupService.getService(SystemStatusService.class)
                      .notifyServiceConfigurationFailure("Cradle Commit Provider", e);
         throw e;
      }
   }

   //~--- methods -------------------------------------------------------------

   /**
    * Adds the alias.
    *
    * @param stampSequence the stamp sequence
    * @param stampAlias the stamp alias
    * @param aliasCommitComment the alias commit comment
    */
   @Override
   public void addAlias(int stampSequence, int stampAlias, String aliasCommitComment) {
      this.stampAliasMap.addAlias(stampSequence, stampAlias);

      if (aliasCommitComment != null) {
         this.stampCommentMap.addComment(stampAlias, aliasCommitComment);
      }
   }

   /**
    * Adds the change checker.
    *
    * @param checker the checker
    */
   @Override
   public void addChangeChecker(ChangeChecker checker) {
      this.checkers.add(checker);
   }

   /**
    * Due to the use of Weak References in the implementation, you MUST maintain a reference to the change listener that is passed in here,
    * otherwise, it will be rapidly garbage collected, and you will randomly stop getting change notifications!.
    *
    * @param changeListener the change listener
    * @see sh.isaac.api.commit.CommitService#addChangeListener(sh.isaac.api.commit.ChronologyChangeListener)
    */
   @Override
   public void addChangeListener(ChronologyChangeListener changeListener) {
      this.changeListeners.add(new ChangeListenerReference(changeListener));
   }

   /**
    * Adds the uncommitted.
    *
    * @param cc the cc
    * @return the task
    */
   @Override
   public Task<Void> addUncommitted(ConceptChronology cc) {
      return checkAndWrite(cc,
                           this.checkers,
                           this.alertCollection,
                           this.writePermitReference.get(),
                           this.changeListeners);
   }

   /**
    * Adds the uncommitted.
    *
    * @param sc the sc
    * @return the task
    */
   @Override
   public Task<Void> addUncommitted(SememeChronology sc) {
      return checkAndWrite(sc,
                           this.checkers,
                           this.alertCollection,
                           this.writePermitReference.get(),
                           this.changeListeners);
   }

   /**
    * Adds the uncommitted no checks.
    *
    * @param cc the cc
    * @return the task
    */
   @Override
   public Task<Void> addUncommittedNoChecks(ConceptChronology cc) {
      return write(cc, this.writePermitReference.get(), this.changeListeners);
   }

   /**
    * Adds the uncommitted no checks.
    *
    * @param sc the sc
    * @return the task
    */
   @Override
   public Task<Void> addUncommittedNoChecks(SememeChronology sc) {
      return write(sc, this.writePermitReference.get(), this.changeListeners);
   }

   /**
    * Cancel.
    *
    * @return the task
    */
   @Override
   public Task<Void> cancel() {
      return cancel(Get.configurationService()
                       .getDefaultEditCoordinate());
   }

   /**
    * Cancel.
    *
    * @param cc the cc
    * @return the task
    */
   @Override
   public Task<Void> cancel(ConceptChronology cc) {
      return cancel(cc, Get.configurationService()
                           .getDefaultEditCoordinate());
   }

   /**
    * Cancel.
    *
    * @param editCoordinate the edit coordinate
    * @return the task
    */
   @Override
   public Task<Void> cancel(EditCoordinate editCoordinate) {
      return Get.stampService()
                .cancel(editCoordinate.getAuthorSequence());
   }

   /**
    * Cancel.
    *
    * @param sememeChronicle the sememe chronicle
    * @return the task
    */
   @Override
   public Task<Void> cancel(SememeChronology sememeChronicle) {
      return cancel(sememeChronicle, Get.configurationService()
                                        .getDefaultEditCoordinate());
   }

   /**
    * Cancel.
    *
    * @param chronicle the chronicle
    * @param editCoordinate the edit coordinate
    * @return the task
    */
   @Override
   public Task<Void> cancel(ObjectChronology<?> chronicle, EditCoordinate editCoordinate) {
      final ObjectChronologyImpl    chronicleImpl = (ObjectChronologyImpl) chronicle;
      final List<ObjectVersionImpl> versionList   = chronicleImpl.getVersionList();

      for (final ObjectVersionImpl version: versionList) {
         if (version.isUncommitted()) {
            if (version.getAuthorSequence() == editCoordinate.getAuthorSequence()) {
               version.cancel();
            }
         }
      }

      chronicleImpl.setVersions(versionList);  // see if id is in uncommitted with checks, and without checks...

      final Collection<Task<?>> subTasks = new ArrayList<>();

      if (chronicle instanceof ConceptChronology) {
         final ConceptChronology conceptChronology = (ConceptChronology) chronicle;

         if (this.uncommittedConceptsNoChecksSequenceSet.contains(conceptChronology.getConceptSequence())) {
            subTasks.add(addUncommittedNoChecks(conceptChronology));
         }

         if (this.uncommittedConceptsWithChecksSequenceSet.contains(conceptChronology.getConceptSequence())) {
            subTasks.add(addUncommitted(conceptChronology));
         }
      } else if (chronicle instanceof SememeChronology) {
         final SememeChronology sememeChronology = (SememeChronology) chronicle;

         if (this.uncommittedSememesNoChecksSequenceSet.contains(sememeChronology.getSememeSequence())) {
            subTasks.add(addUncommittedNoChecks(sememeChronology));
         }

         if (this.uncommittedSememesWithChecksSequenceSet.contains(sememeChronology.getSememeSequence())) {
            subTasks.add(addUncommitted(sememeChronology));
         }
      } else {
         throw new RuntimeException("Unsupported chronology type: " + chronicle);
      }

      return new SequentialAggregateTask<>("Canceling change", subTasks);
   }

   /**
    * Clear database validity value.
    */
   @Override
   public void clearDatabaseValidityValue() {
      // Reset to enforce analysis
      this.databaseValidity = DatabaseValidity.NOT_SET;
   }

   /**
    * Perform a global commit. The caller may chose to block on the returned
    * task if synchronous operation is desired.
    *
    * @param commitComment the commit comment
    * @return a task that is already submitted to an executor.
    */
   @Override
   public synchronized Task<Optional<CommitRecord>> commit(String commitComment) {
      // return commit(Get.configurationService().getDefaultEditCoordinate(), commitComment);
      final Semaphore pendingWrites = this.writePermitReference.getAndSet(new Semaphore(WRITE_POOL_SIZE));

      pendingWrites.acquireUninterruptibly(WRITE_POOL_SIZE);
      this.alertCollection.clear();
      this.lastCommit = this.databaseSequence.incrementAndGet();

      final Map<UncommittedStamp, Integer> pendingStampsForCommit = Get.stampService()
                                                                       .getPendingStampsForCommit();

      try {
         this.uncommittedSequenceLock.lock();

         final CommitTask task = CommitTask.get(commitComment,
                                                this.uncommittedConceptsWithChecksSequenceSet,
                                                this.uncommittedConceptsNoChecksSequenceSet,
                                                this.uncommittedSememesWithChecksSequenceSet,
                                                this.uncommittedSememesNoChecksSequenceSet,
                                                this.lastCommit,
                                                this.checkers,
                                                this.alertCollection,
                                                pendingStampsForCommit,
                                                this);

         return task;
      } finally {
         this.uncommittedSequenceLock.unlock();
      }
   }

   /**
    * Commit.
    *
    * @param cc the cc
    * @param commitComment the commit comment
    * @return the task
    */
   @Override
   public Task<Optional<CommitRecord>> commit(ConceptChronology cc, String commitComment) {
      return commit(cc, Get.configurationService()
                           .getDefaultEditCoordinate(), commitComment);
   }

   /**
    * Commit.
    *
    * @param editCoordinate the edit coordinate
    * @param commitComment the commit comment
    * @return the task
    */
   @Override
   public Task<Optional<CommitRecord>> commit(EditCoordinate editCoordinate, String commitComment) {
      // TODO, make this only commit those components with changes from the provided edit coordinate.
      throw new UnsupportedOperationException("This implementation is broken");

      // TODO this needs repair... pendingStampsForCommit, for example, is never populated.
      // Also do we need to lock around the CommitTask creation to makre sure the uncommitted lists are consistent during copy?
      // Semaphore pendingWrites = writePermitReference.getAndSet(new Semaphore(WRITE_POOL_SIZE));
      // pendingWrites.acquireUninterruptibly(WRITE_POOL_SIZE);
      // alertCollection.clear();
      // lastCommit = databaseSequence.incrementAndGet();
      //
      // Map<UncommittedStamp, Integer> pendingStampsForCommit = new HashMap<>();
      // UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.forEach((uncommittedStamp, stampSequence) -> {
      // if (uncommittedStamp.authorSequence == editCoordinate.getAuthorSequence()) {
      // Stamp stamp = new Stamp(Status.getStatusFromState(uncommittedStamp.status),
      // Long.MIN_VALUE,
      // Get.identifierService().getConceptNid(uncommittedStamp.authorSequence),
      // Get.identifierService().getConceptNid(uncommittedStamp.moduleSequence),
      // Get.identifierService().getConceptNid(uncommittedStamp.pathSequence));
      // addStamp(stamp, stampSequence);
      // UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.remove(uncommittedStamp);
      // }
      // });
      // UNCOMMITTED_STAMP_TO_STAMP_SEQUENCE_MAP.clear();
      //
      // CommitTask task = CommitTask.get(commitComment,
      // uncommittedConceptsWithChecksSequenceSet,
      // uncommittedConceptsNoChecksSequenceSet,
      // uncommittedSememesWithChecksSequenceSet,
      // uncommittedSememesNoChecksSequenceSet,
      // lastCommit,
      // checkers,
      // alertCollection,
      // pendingStampsForCommit,
      // this);
      // return task;
   }

   /**
    * Commit.
    *
    * @param cc the cc
    * @param commitComment the commit comment
    * @return the task
    */
   @Override
   public Task<Optional<CommitRecord>> commit(SememeChronology cc, String commitComment) {
      return commit(cc, Get.configurationService()
                           .getDefaultEditCoordinate(), commitComment);
   }

   /**
    * Commit.
    *
    * @param chronicle the chronicle
    * @param editCoordinate the edit coordinate
    * @param commitComment the commit comment
    * @return the task
    */
   @Override
   public synchronized Task<Optional<CommitRecord>> commit(ObjectChronology<?> chronicle,
         EditCoordinate editCoordinate,
         String commitComment) {
      // TODO make asynchronous with a actual task.
      // TODO there are numerous inconsistencies with this impl, and the global commit. Need to understand:
      // global seq number, should a write be done on the provider?
      // This also doesn't safely copy the uncommitted lists before using them.
      // TODO I think this needs to be rewritten to use the CommitTask - but need to understand these issues first.
      // This also doesn't update the stamp provider...
      CommitRecord     commitRecord = null;
      final Set<Alert> alerts       = new HashSet<>();

      if (chronicle instanceof ConceptChronology) {
         final ConceptChronology conceptChronology = (ConceptChronology) chronicle;

         if (this.uncommittedConceptsWithChecksSequenceSet.contains(conceptChronology.getConceptSequence())) {
            this.checkers.stream().forEach((check) -> {
                                     check.check(conceptChronology, alerts, CheckPhase.COMMIT);
                                  });
         }
      } else if (chronicle instanceof SememeChronology) {
         final SememeChronology sememeChronology = (SememeChronology) chronicle;

         if (this.uncommittedSememesWithChecksSequenceSet.contains(sememeChronology.getSememeSequence())) {
            this.checkers.stream().forEach((check) -> {
                                     check.check(sememeChronology, alerts, CheckPhase.COMMIT);
                                  });
         }
      } else {
         throw new RuntimeException("Unsupported chronology type: " + chronicle);
      }

      if (alerts.stream()
                .anyMatch((alert) -> (alert.getAlertType() == AlertType.ERROR))) {
         this.alertCollection.addAll(alerts);
      } else {
         final long commitTime = System.currentTimeMillis();

         // TODO have it only commit the versions on the sememe consistent with the edit coordinate.
         // successful check, commit and remove uncommitted sequences...
         final StampSequenceSet   stampsInCommit   = new StampSequenceSet();
         final OpenIntIntHashMap  stampAliases     = new OpenIntIntHashMap();
         final ConceptSequenceSet conceptsInCommit = new ConceptSequenceSet();
         final SememeSequenceSet  sememesInCommit  = new SememeSequenceSet();

         chronicle.getVersionList().forEach((version) -> {
                              if (((ObjectVersionImpl) version).isUncommitted() &&
                                  ((ObjectVersionImpl) version).getAuthorSequence() ==
                                  editCoordinate.getAuthorSequence()) {
                                 ((ObjectVersionImpl) version).setTime(commitTime);
                                 stampsInCommit.add(((ObjectVersionImpl) version).getStampSequence());
                              }
                           });

         if (chronicle instanceof ConceptChronology) {
            final ConceptChronology conceptChronology = (ConceptChronology) chronicle;

            conceptsInCommit.add(conceptChronology.getConceptSequence());
            this.uncommittedConceptsWithChecksSequenceSet.remove(conceptChronology.getConceptSequence());
            this.uncommittedConceptsNoChecksSequenceSet.remove(conceptChronology.getConceptSequence());
            Get.conceptService()
               .writeConcept(conceptChronology);
         } else {
            final SememeChronology sememeChronology = (SememeChronology) chronicle;

            sememesInCommit.add(sememeChronology.getSememeSequence());
            this.uncommittedSememesWithChecksSequenceSet.remove(sememeChronology.getSememeSequence());
            this.uncommittedSememesNoChecksSequenceSet.remove(sememeChronology.getSememeSequence());
            Get.sememeService()
               .writeSememe(sememeChronology);
         }

         commitRecord = new CommitRecord(Instant.ofEpochMilli(commitTime),
                                         stampsInCommit,
                                         stampAliases,
                                         conceptsInCommit,
                                         sememesInCommit,
                                         commitComment);
      }

      CommitProvider.this.handleCommitNotification(commitRecord);

      final Optional<CommitRecord>       optionalRecord = Optional.ofNullable(commitRecord);
      final Task<Optional<CommitRecord>> task           = new TimedTask() {
         @Override
         protected Optional<CommitRecord> call()
                  throws Exception {
            Get.activeTasks()
               .remove(this);
            return optionalRecord;
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
    * Import no checks.
    *
    * @param ochreExternalizable the ochre externalizable
    */
   @Override
   public void importNoChecks(OchreExternalizable ochreExternalizable) {
      switch (ochreExternalizable.getOchreObjectType()) {
      case CONCEPT:
         final ConceptChronology conceptChronology = (ConceptChronology) ochreExternalizable;

         Get.conceptService()
            .writeConcept(conceptChronology);
         break;

      case SEMEME:
         final SememeChronology sememeChronology = (SememeChronology) ochreExternalizable;

         Get.sememeService()
            .writeSememe(sememeChronology);

         if (sememeChronology.getSememeType() == SememeType.LOGIC_GRAPH) {
            deferNidAction(sememeChronology.getNid());
         }

         break;

      case STAMP_ALIAS:
         final StampAlias stampAlias = (StampAlias) ochreExternalizable;

         this.stampAliasMap.addAlias(stampAlias.getStampSequence(), stampAlias.getStampAlias());
         break;

      case STAMP_COMMENT:
         final StampComment stampComment = (StampComment) ochreExternalizable;

         this.stampCommentMap.addComment(stampComment.getStampSequence(), stampComment.getComment());
         break;

      default:
         throw new UnsupportedOperationException("Can't handle: " + ochreExternalizable);
      }
   }

   /**
    * Increment and get sequence.
    *
    * @return the long
    */
   @Override
   public long incrementAndGetSequence() {
      return this.databaseSequence.incrementAndGet();
   }

   /**
    * Post process import no checks.
    */
   @Override
   public void postProcessImportNoChecks() {
      final Set<Integer> nids = this.deferredImportNoCheckNids.get();

      this.deferredImportNoCheckNids.remove();

      if (nids != null) {
         for (final int nid: nids) {
            if (ObjectChronologyType.SEMEME.equals(Get.identifierService()
                  .getChronologyTypeForNid(nid))) {
               final SememeChronology sc = Get.sememeService()
                                              .getSememe(nid);

               if (sc.getSememeType() == SememeType.LOGIC_GRAPH) {
                  Get.taxonomyService()
                     .updateTaxonomy(sc);
               } else {
                  throw new UnsupportedOperationException("Unexpected nid in deferred set: " + nid);
               }
            } else {
               throw new UnsupportedOperationException("Unexpected nid in deferred set: " + nid);
            }
         }
      }
   }

   /**
    * Removes the change checker.
    *
    * @param checker the checker
    */
   @Override
   public void removeChangeChecker(ChangeChecker checker) {
      this.checkers.remove(checker);
   }

   /**
    * Removes the change listener.
    *
    * @param changeListener the change listener
    */
   @Override
   public void removeChangeListener(ChronologyChangeListener changeListener) {
      this.changeListeners.remove(new ChangeListenerReference(changeListener));
   }

   /**
    * Adds the comment.
    *
    * @param stamp the stamp
    * @param commitComment the commit comment
    */
   protected void addComment(int stamp, String commitComment) {
      this.stampCommentMap.addComment(stamp, commitComment);
   }

   /**
    * Handle commit notification.
    *
    * @param commitRecord the commit record
    */
   protected void handleCommitNotification(CommitRecord commitRecord) {
      this.changeListeners.forEach((listenerRef) -> {
                                      final ChronologyChangeListener listener = listenerRef.get();

                                      if (listener == null) {
                                         this.changeListeners.remove(listenerRef);
                                      } else {
                                         listener.handleCommit(commitRecord);
                                      }
                                   });
   }

   /**
    * Revert commit.
    *
    * @param conceptsToCommit the concepts to commit
    * @param conceptsToCheck the concepts to check
    * @param sememesToCommit the sememes to commit
    * @param sememesToCheck the sememes to check
    * @param pendingStampsForCommit the pending stamps for commit
    */
   protected void revertCommit(ConceptSequenceSet conceptsToCommit,
                               ConceptSequenceSet conceptsToCheck,
                               SememeSequenceSet sememesToCommit,
                               SememeSequenceSet sememesToCheck,
                               Map<UncommittedStamp, Integer> pendingStampsForCommit) {
      Get.stampService()
         .setPendingStampsForCommit(pendingStampsForCommit);
      this.uncommittedSequenceLock.lock();

      try {
         this.uncommittedConceptsWithChecksSequenceSet.or(conceptsToCheck);
         this.uncommittedConceptsNoChecksSequenceSet.or(conceptsToCommit);
         this.uncommittedConceptsNoChecksSequenceSet.andNot(conceptsToCheck);
         this.uncommittedSememesWithChecksSequenceSet.or(sememesToCheck);
         this.uncommittedSememesNoChecksSequenceSet.or(sememesToCommit);
         this.uncommittedSememesNoChecksSequenceSet.andNot(sememesToCheck);
      } finally {
         this.uncommittedSequenceLock.unlock();
      }
   }

   /**
    * Check and write.
    *
    * @param cc the cc
    * @param checkers the checkers
    * @param alertCollection the alert collection
    * @param writeSemaphore the write semaphore
    * @param changeListeners the change listeners
    * @return the task
    */
   private Task<Void> checkAndWrite(ConceptChronology cc,
                                    ConcurrentSkipListSet<ChangeChecker> checkers,
                                    ConcurrentSkipListSet<Alert> alertCollection,
                                    Semaphore writeSemaphore,
                                    ConcurrentSkipListSet<WeakReference<ChronologyChangeListener>> changeListeners) {
      writeSemaphore.acquireUninterruptibly();

      final WriteAndCheckConceptChronicle task = new WriteAndCheckConceptChronicle(cc,
                                                                                   checkers,
                                                                                   alertCollection,
                                                                                   writeSemaphore,
                                                                                   changeListeners,
                                                                                   (sememeOrConceptChronicle,
                                                                                    changeCheckerActive) -> handleUncommittedSequenceSet(
                                                                                       sememeOrConceptChronicle,
                                                                                             changeCheckerActive));

      this.writeCompletionService.submit(task);
      return task;
   }

   /**
    * Check and write.
    *
    * @param sc the sc
    * @param checkers the checkers
    * @param alertCollection the alert collection
    * @param writeSemaphore the write semaphore
    * @param changeListeners the change listeners
    * @return the task
    */
   private Task<Void> checkAndWrite(SememeChronology sc,
                                    ConcurrentSkipListSet<ChangeChecker> checkers,
                                    ConcurrentSkipListSet<Alert> alertCollection,
                                    Semaphore writeSemaphore,
                                    ConcurrentSkipListSet<WeakReference<ChronologyChangeListener>> changeListeners) {
      writeSemaphore.acquireUninterruptibly();

      final WriteAndCheckSememeChronicle task = new WriteAndCheckSememeChronicle(sc,
                                                                                 checkers,
                                                                                 alertCollection,
                                                                                 writeSemaphore,
                                                                                 changeListeners,
                                                                                 (sememeOrConceptChronicle,
                                                                                  changeCheckerActive) -> handleUncommittedSequenceSet(
                                                                                     sememeOrConceptChronicle,
                                                                                           changeCheckerActive));

      this.writeCompletionService.submit(task);
      return task;
   }

   /**
    * Defer nid action.
    *
    * @param nid the nid
    */
   private void deferNidAction(int nid) {
      Set<Integer> nids = this.deferredImportNoCheckNids.get();

      if (nids == null) {
         nids = new HashSet<>();
         this.deferredImportNoCheckNids.set(nids);
      }

      nids.add(nid);
   }

   /**
    * Handle uncommitted sequence set.
    *
    * @param sememeOrConceptChronicle the sememe or concept chronicle
    * @param changeCheckerActive the change checker active
    */
   private void handleUncommittedSequenceSet(ObjectChronology sememeOrConceptChronicle, boolean changeCheckerActive) {
      try {
         this.uncommittedSequenceLock.lock();

         switch (sememeOrConceptChronicle.getOchreObjectType()) {
         case CONCEPT: {
            final int sequence           = Get.identifierService()
                                              .getConceptSequence(sememeOrConceptChronicle.getNid());
            final ConceptSequenceSet set = changeCheckerActive ? this.uncommittedConceptsWithChecksSequenceSet
                  : this.uncommittedConceptsNoChecksSequenceSet;

            if (sememeOrConceptChronicle.isUncommitted()) {
               set.add(sequence);
            } else {
               set.remove(sequence);
            }

            break;
         }

         case SEMEME: {
            final int sequence          = Get.identifierService()
                                             .getSememeSequence(sememeOrConceptChronicle.getNid());
            final SememeSequenceSet set = changeCheckerActive ? this.uncommittedSememesWithChecksSequenceSet
                  : this.uncommittedSememesNoChecksSequenceSet;

            if (sememeOrConceptChronicle.isUncommitted()) {
               set.add(sequence);
            } else {
               set.remove(sequence);
            }

            break;
         }

         default:
            throw new RuntimeException("Only Concepts or Sememes should be passed");
         }
      } finally {
         this.uncommittedSequenceLock.unlock();
      }
   }

   /**
    * Start me.
    */
   @PostConstruct
   private void startMe() {
      try {
         LOG.info("Starting CommitProvider post-construct");
         this.writeCompletionService.start();

         if (this.loadRequired.get()) {
            LOG.info("Reading existing commit manager data. ");
            LOG.info("Reading " + COMMIT_MANAGER_DATA_FILENAME);

            try (DataInputStream in =
                  new DataInputStream(new FileInputStream(new File(this.commitManagerFolder.toFile(),
                                                                   COMMIT_MANAGER_DATA_FILENAME)))) {
               this.databaseSequence.set(in.readLong());
               UuidIntMapMap.getNextNidProvider()
                            .set(in.readInt());
               this.uncommittedConceptsWithChecksSequenceSet.read(in);
               this.uncommittedConceptsNoChecksSequenceSet.read(in);
               this.uncommittedSememesWithChecksSequenceSet.read(in);
               this.uncommittedSememesNoChecksSequenceSet.read(in);
            }

            LOG.info("Reading: " + STAMP_ALIAS_MAP_FILENAME);
            this.stampAliasMap.read(new File(this.commitManagerFolder.toFile(), STAMP_ALIAS_MAP_FILENAME));
            LOG.info("Reading: " + STAMP_COMMENT_MAP_FILENAME);
            this.stampCommentMap.read(new File(this.commitManagerFolder.toFile(), STAMP_COMMENT_MAP_FILENAME));
            this.databaseValidity = DatabaseValidity.POPULATED_DIRECTORY;
         }
      } catch (final Exception e) {
         LookupService.getService(SystemStatusService.class)
                      .notifyServiceConfigurationFailure("Cradle Commit Provider", e);
         throw new RuntimeException(e);
      }
   }

   /**
    * Stop me.
    */
   @PreDestroy
   private void stopMe() {
      LOG.info("Stopping CommitProvider pre-destroy. ");

      try {
         this.writeCompletionService.stop();
         this.stampAliasMap.write(new File(this.commitManagerFolder.toFile(), STAMP_ALIAS_MAP_FILENAME));
         this.stampCommentMap.write(new File(this.commitManagerFolder.toFile(), STAMP_COMMENT_MAP_FILENAME));

         try (DataOutputStream out =
               new DataOutputStream(new FileOutputStream(new File(this.commitManagerFolder.toFile(),
                                                                  COMMIT_MANAGER_DATA_FILENAME)))) {
            out.writeLong(this.databaseSequence.get());
            out.writeInt(UuidIntMapMap.getNextNidProvider()
                                      .get());
            this.uncommittedConceptsWithChecksSequenceSet.write(out);
            this.uncommittedConceptsNoChecksSequenceSet.write(out);
            this.uncommittedSememesWithChecksSequenceSet.write(out);
            this.uncommittedSememesNoChecksSequenceSet.write(out);
         }
      } catch (final IOException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Write.
    *
    * @param cc the cc
    * @param writeSemaphore the write semaphore
    * @param changeListeners the change listeners
    * @return the task
    */
   private Task<Void> write(ConceptChronology cc,
                            Semaphore writeSemaphore,
                            ConcurrentSkipListSet<WeakReference<ChronologyChangeListener>> changeListeners) {
      writeSemaphore.acquireUninterruptibly();

      final WriteConceptChronicle task = new WriteConceptChronicle(cc,
                                                                   writeSemaphore,
                                                                   changeListeners,
                                                                   (sememeOrConceptChronicle,
                                                                    changeCheckerActive) -> handleUncommittedSequenceSet(
                                                                       sememeOrConceptChronicle,
                                                                             changeCheckerActive));

      this.writeCompletionService.submit(task);
      return task;
   }

   /**
    * Write.
    *
    * @param sc the sc
    * @param writeSemaphore the write semaphore
    * @param changeListeners the change listeners
    * @return the task
    */
   private Task<Void> write(SememeChronology sc,
                            Semaphore writeSemaphore,
                            ConcurrentSkipListSet<WeakReference<ChronologyChangeListener>> changeListeners) {
      writeSemaphore.acquireUninterruptibly();

      final WriteSememeChronicle task = new WriteSememeChronicle(sc,
                                                                 writeSemaphore,
                                                                 changeListeners,
                                                                 (sememeOrConceptChronicle,
                                                                  changeCheckerActive) -> handleUncommittedSequenceSet(
                                                                     sememeOrConceptChronicle,
                                                                           changeCheckerActive));

      this.writeCompletionService.submit(task);
      return task;
   }

   //~--- get methods ---------------------------------------------------------

   /**
    * Gets the alert list.
    *
    * @return the alert list
    */
   @Override
   public ObservableList<Alert> getAlertList() {
      throw new UnsupportedOperationException(
          "Not supported yet.");  // To change body of generated methods, choose Tools | Templates.
   }

   /**
    * Gets the aliases.
    *
    * @param stampSequence the stamp sequence
    * @return the aliases
    */
   @Override
   public int[] getAliases(int stampSequence) {
      return this.stampAliasMap.getAliases(stampSequence);
   }

   /**
    * Gets the comment.
    *
    * @param stampSequence the stamp sequence
    * @return the comment
    */
   @Override
   public Optional<String> getComment(int stampSequence) {
      return this.stampCommentMap.getComment(stampSequence);
   }

   //~--- set methods ---------------------------------------------------------

   /**
    * Set comment.
    *
    * @param stampSequence the stamp sequence
    * @param comment the comment
    */
   @Override
   public void setComment(int stampSequence, String comment) {
      this.stampCommentMap.addComment(stampSequence, comment);
   }

   //~--- get methods ---------------------------------------------------------

   /**
    * Gets the commit manager sequence.
    *
    * @return the commit manager sequence
    */
   @Override
   public long getCommitManagerSequence() {
      return this.databaseSequence.get();
   }

   /**
    * Gets the database folder.
    *
    * @return the database folder
    */
   @Override
   public Path getDatabaseFolder() {
      return this.commitManagerFolder;
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
    * Gets the stamp alias stream.
    *
    * @return the stamp alias stream
    */
   @Override
   public Stream<StampAlias> getStampAliasStream() {
      return this.stampAliasMap.getStampAliasStream();
   }

   /**
    * Gets the stamp comment stream.
    *
    * @return the stamp comment stream
    */
   @Override
   public Stream<StampComment> getStampCommentStream() {
      return this.stampCommentMap.getStampCommentStream();
   }

   /**
    * Gets the uncommitted component text summary.
    *
    * @return the uncommitted component text summary
    */
   @Override
   public String getUncommittedComponentTextSummary() {
      final StringBuilder builder = new StringBuilder("CommitProvider summary: ");

      builder.append("\nuncommitted concepts with checks: ")
             .append(this.uncommittedConceptsWithChecksSequenceSet);
      builder.append("\nuncommitted concepts no checks: ")
             .append(this.uncommittedConceptsNoChecksSequenceSet);
      builder.append("\nuncommitted sememes with checks: ")
             .append(this.uncommittedSememesWithChecksSequenceSet);
      builder.append("\nuncommitted sememes no checks: ")
             .append(this.uncommittedSememesNoChecksSequenceSet);
      return builder.toString();
   }

   /**
    * Gets the uncommitted concept nids.
    *
    * @return the uncommitted concept nids
    */
   @Override
   public ObservableList<Integer> getUncommittedConceptNids() {
      // need to create a list that can be backed with a set...
      throw new UnsupportedOperationException(
          "Not supported yet.");  // To change body of generated methods, choose Tools | Templates.
   }

   //~--- inner classes -------------------------------------------------------

   /**
    * The Class ChangeListenerReference.
    */
   private static class ChangeListenerReference
           extends WeakReference<ChronologyChangeListener>
            implements Comparable<ChangeListenerReference> {
      /** The listener uuid. */
      UUID listenerUuid;

      //~--- constructors -----------------------------------------------------

      /**
       * Instantiates a new change listener reference.
       *
       * @param referent the referent
       */
      public ChangeListenerReference(ChronologyChangeListener referent) {
         super(referent);
         this.listenerUuid = referent.getListenerUuid();
      }

      /**
       * Instantiates a new change listener reference.
       *
       * @param referent the referent
       * @param q the q
       */
      public ChangeListenerReference(ChronologyChangeListener referent,
                                     ReferenceQueue<? super ChronologyChangeListener> q) {
         super(referent, q);
         this.listenerUuid = referent.getListenerUuid();
      }

      //~--- methods ----------------------------------------------------------

      /**
       * Compare to.
       *
       * @param o the o
       * @return the int
       */
      @Override
      public int compareTo(ChangeListenerReference o) {
         return this.listenerUuid.compareTo(o.listenerUuid);
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

         final ChangeListenerReference other = (ChangeListenerReference) obj;

         return Objects.equals(this.listenerUuid, other.listenerUuid);
      }

      /**
       * Hash code.
       *
       * @return the int
       */
      @Override
      public int hashCode() {
         int hash = 3;

         hash = 67 * hash + Objects.hashCode(this.listenerUuid);
         return hash;
      }
   }
}
