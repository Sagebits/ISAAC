/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
package sh.isaac.convert.mojo.turtle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.FileUtils;
import javafx.util.Pair;
import sh.isaac.MetaData;
import sh.isaac.api.Get;
import sh.isaac.api.LanguageCode;
import sh.isaac.api.LookupService;
import sh.isaac.api.Status;
import sh.isaac.api.bootstrap.TermAux;
import sh.isaac.api.component.semantic.version.dynamic.DynamicData;
import sh.isaac.api.constants.DatabaseInitialization;
import sh.isaac.api.datastore.DataStore;
import sh.isaac.api.externalizable.IsaacObjectType;
import sh.isaac.api.index.IndexBuilderService;
import sh.isaac.api.util.UuidFactory;
import sh.isaac.api.util.UuidT5Generator;
import sh.isaac.convert.directUtils.DataWriteListenerImpl;
import sh.isaac.convert.directUtils.DirectWriteHelper;
import sh.isaac.convert.directUtils.LoggingConfig;
import sh.isaac.converters.sharedUtils.ConverterBaseMojo;
import sh.isaac.converters.sharedUtils.stats.ConverterUUID;
import sh.isaac.model.semantic.types.DynamicIntegerImpl;
import sh.isaac.utility.LanguageMap;

/**
 * 
 * {@link TurtleImportMojo}
 * 
 * Goal which converts CPT data into the workbench jbin format
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
@Mojo(name = "convert-turtle-to-ibdf", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class TurtleImportMojo extends ConverterBaseMojo
{
	private Logger log = LogManager.getLogger();
	
	private InputStream inputStream;

	private ConverterUUID converterUUID;
	private DirectWriteHelper dwh;

	private int moduleNid;
	private int authorNid = 0;
	private UUID rootConcept;
	private UUID rootConceptId2;
	private UUID coreGroupConcept;
	
	private long releaseTime;

	private HashMap<String, List<Statement>> allStatements;
	private HashMap<UUID, Resource> conceptsToBeBuilt = new HashMap<>();
	private HashSet<UUID> conceptsToHangFromCore = new HashSet<>();
	
	private HashSet<String> observedAnonNodes = new HashSet<>();
	private HashSet<String> processedAnonNodes = new HashSet<>();
	
	private final UUID fsn = MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getPrimordialUuid();
	private final UUID regularName = MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getPrimordialUuid();
	private final UUID definition = MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getPrimordialUuid();
	private final UUID insensitive = MetaData.DESCRIPTION_NOT_CASE_SENSITIVE____SOLOR.getPrimordialUuid();
	private final UUID preferred = MetaData.PREFERRED____SOLOR.getPrimordialUuid();
	private final UUID acceptable = MetaData.ACCEPTABLE____SOLOR.getPrimordialUuid();
	
	private HashMap<String, String> possibleAssociations = new HashMap<>();
	private HashMap<String, String> possibleRefSets = new HashMap<>();
	private HashMap<String, String> possibleRelationships = new HashMap<>();
	private HashMap<String, Pair<String, UUID>> possibleDescriptionTypes = new HashMap<>();
	private HashMap<String, String> possibleSingleValueTypedSemantics = new HashMap<>();
	private HashMap<String, DynamicSemanticHelper> possibleDynamicAttributes = new HashMap<>();
	private AnonymousNodeUtil anu;

	/**
	 * Constructor for maven
	 */
	public TurtleImportMojo()
	{
		// This constructor is for maven
	}

	/**
	 * Constructor for runtime use
	 * 
	 * @param outputDirectory {@link #outputDirectory} - optional for runtime use, if provided, UUID debug file will be written here.
	 * @param ttlFile {@link #inputFileLocation}
	 * @param converterSourceArtifactVersion {@link #converterSourceArtifactVersion}
	 */
	public TurtleImportMojo(File outputDirectory, InputStream ttlFile, String converterSourceArtifactVersion)
	{
		this();
		this.outputDirectory = outputDirectory;
		this.inputStream = ttlFile;
		this.converterSourceArtifactVersion = converterSourceArtifactVersion;
		converterUUID = new ConverterUUID(UuidT5Generator.PATH_ID_FROM_FS_DESC, false);
		init();
	}
	
	private void init()
	{
		//Each of these will only get created as a metadata concept if it is present in the dataset.
		possibleAssociations.put("http://www.w3.org/2000/01/rdf-schema#seeAlso", "see also");
		possibleAssociations.put("http://www.w3.org/2000/01/rdf-schema#isDefinedBy", "is defined by");
		possibleAssociations.put("http://www.w3.org/2002/07/owl#disjointWith", "disjoint with");
		possibleAssociations.put("http://www.w3.org/2002/07/owl#sameAs", "same as");
		possibleAssociations.put("http://www.w3.org/2000/01/rdf-schema#domain", "domain");
		possibleAssociations.put("http://www.w3.org/2000/01/rdf-schema#range", "range");
		possibleAssociations.put("http://purl.org/dc/terms/hasFormat", "has format");
		possibleAssociations.put("http://www.w3.org/2002/07/owl#inverseOf", "inverse of");
		possibleAssociations.put("http://www.w3.org/2000/01/rdf-schema#subPropertyOf", "sub property of");
		possibleAssociations.put("http://rdfs.co/bevon/container","container");
		possibleAssociations.put("http://rdfs.co/bevon/origin","origin");
		possibleAssociations.put("http://creativecommons.org/ns#license", "license");
		possibleAssociations.put("http://purl.org/dc/terms/creator", "creator");
		possibleAssociations.put("http://xmlns.com/foaf/0.1/homepage", "homepage");
		possibleAssociations.put("http://rdfs.co/bevon/fermentation_base", "fermentation base");
		possibleAssociations.put("http://rdfs.co/bevon/brewery", "brewery");
		possibleAssociations.put("http://rdfs.co/bevon/similar", "similar");
		possibleAssociations.put("http://purl.org/dc/terms/type", "type");
		possibleAssociations.put("http://www.w3.org/ns/adms#status", "status");
		possibleAssociations.put("http://purl.org/dc/terms/publisher", "publisher");
		possibleAssociations.put("http://purl.org/dc/terms/isVersionOf", "is version of");
		possibleAssociations.put("http://purl.org/vocab/vann/termGroup", "term group");
		
		possibleRelationships.put("http://www.w3.org/2000/01/rdf-schema#subClassOf", "sub class of");
		possibleRelationships.put("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "type");
		
		possibleDescriptionTypes.put("http://www.w3.org/2000/01/rdf-schema#label", new Pair<String, UUID>("label", regularName));
		possibleDescriptionTypes.put("http://www.w3.org/2004/02/skos/core#prefLabel", new Pair<String, UUID>("pref label", regularName));
		possibleDescriptionTypes.put("http://www.w3.org/2004/02/skos/core#definition", new Pair<String, UUID>("definition", definition));
		possibleDescriptionTypes.put("http://purl.org/dc/terms/title", new Pair<String, UUID>("title", regularName));
		possibleDescriptionTypes.put("http://www.w3.org/2004/02/skos/core#altLabel", new Pair<String, UUID>("alt label", regularName));
		possibleDescriptionTypes.put("http://rdfs.co/bevon/name", new Pair<String, UUID>("name", regularName));
		possibleDescriptionTypes.put("http://rdfs.co/bevon/description", new Pair<String, UUID>("description", definition));
		possibleDescriptionTypes.put("http://purl.org/dc/terms/description", new Pair<String, UUID>("description", definition));
		possibleDescriptionTypes.put("http://xmlns.com/foaf/0.1/name", new Pair<String, UUID>("name", regularName));
		
		possibleSingleValueTypedSemantics.put("http://rdfs.co/bevon/ibu", "ibu");
		possibleSingleValueTypedSemantics.put("http://rdfs.co/bevon/abv", "abv");
		possibleSingleValueTypedSemantics.put("http://rdfs.co/bevon/launch", "launch");
		possibleSingleValueTypedSemantics.put("http://www.w3.org/2003/06/sw-vocab-status/ns#term_status", "term status");
		possibleSingleValueTypedSemantics.put("http://purl.org/dc/terms/issued", "issued");
		possibleSingleValueTypedSemantics.put("http://purl.org/dc/terms/modified", "modified");
		possibleSingleValueTypedSemantics.put("http://purl.org/dc/terms/identifier", "identifier");
		possibleSingleValueTypedSemantics.put("http://xmlns.com/foaf/0.1/mbox_sha1sum", "mbox sha1sum");
		possibleSingleValueTypedSemantics.put("http://rdfs.co/bevon/srm", "srm");
		possibleSingleValueTypedSemantics.put("http://rdfs.co/bevon/proof", "proof");
		possibleSingleValueTypedSemantics.put("http://purl.org/dc/terms/rights", "rights");
		possibleSingleValueTypedSemantics.put("http://purl.org/vocab/vann/preferredNamespacePrefix", "preferred namespace prefix");
		possibleSingleValueTypedSemantics.put("http://www.w3.org/2002/07/owl#versionInfo", "version info");
		possibleSingleValueTypedSemantics.put("http://purl.org/vocab/vann/preferredNamespaceUri", "preferred namespace uri");
		possibleSingleValueTypedSemantics.put("http://rdfs.co/bevon/manufacturer", "manufacturer");  //This is inconsistent, sometimes defined as anonymous
		possibleSingleValueTypedSemantics.put("http://purl.org/dc/terms/replaces", "replaces");
	}

	/**
	 * This is the execution path when maven is running.
	 * {@inheritDoc}
	 */
	@Override
	public void execute() throws MojoExecutionException
	{
		try
		{
			File ttlFile = null;
			for (File f : inputFileLocation.listFiles())
			{
				if (f.getName().toLowerCase().endsWith("ttl"))
				{
					ttlFile = f;
					break;
				}
			}
	
			getLog().info("Reading " + ttlFile.getCanonicalPath());
			
			this.inputStream = new FileInputStream(ttlFile);
			
			LoggingConfig.configureLogging(outputDirectory, converterOutputArtifactClassifier);

			converterUUID = Get.service(ConverterUUID.class);
			converterUUID.configureNamespace(UuidT5Generator.PATH_ID_FROM_FS_DESC);

			String outputName = converterOutputArtifactId
					+ (StringUtils.isBlank(converterOutputArtifactClassifier) ? "" : "-" + converterOutputArtifactClassifier) + "-"
					+ converterOutputArtifactVersion;
			Path ibdfFileToWrite = new File(outputDirectory, outputName + ".ibdf").toPath();
			ibdfFileToWrite.toFile().delete();

			log.info("Writing IBDF to " + ibdfFileToWrite.toFile().getCanonicalPath());

			File file = new File(outputDirectory, "isaac-db");
			// make sure this is empty
			FileUtils.deleteDirectory(file);

			Get.configurationService().setDataStoreFolderPath(file.toPath());

			LookupService.startupPreferenceProvider();

			Get.configurationService().setDatabaseInitializationMode(DatabaseInitialization.LOAD_METADATA);

			LookupService.startupIsaac();

			// Don't need to build indexes
			for (IndexBuilderService ibs : LookupService.getServices(IndexBuilderService.class))
			{
				ibs.setEnabled(false);
			}

			DataWriteListenerImpl listener = new DataWriteListenerImpl(ibdfFileToWrite, null);

			// we register this after the metadata has already been written.
			LookupService.get().getService(DataStore.class).registerDataWriteListener(listener);
			
			init();

			processTurtle();
			
			LookupService.shutdownSystem();
			
			listener.close();
		}
		catch (Exception ex)
		{
			throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
		}
	}

	/**
	 * This is the execution path when maven is running, or, when you want to run it directly.
	 * @throws IOException
	 */
	public void processTurtle() throws IOException
	{
		try
		{
			Model model = ModelFactory.createDefaultModel();
			model.read(inputStream, "", "TURTLE");
	
			log.info("The read TURTLE graph contains {} objects", model.getGraph().size());
	
			allStatements = new HashMap<>();
			HashSet<String> allPredicates = new HashSet<>();
			
			HashMap<String, String> allAnonPointers = new HashMap<>();
	
			model.listStatements().forEachRemaining(statement -> {
				String key = statement.getSubject().getURI() == null ? statement.getSubject().getId().getLabelString() : statement.getSubject().getURI();
	
				List<Statement> statements = allStatements.get(key);
				if (statements == null)
				{
					statements = new ArrayList<Statement>();
					allStatements.put(key, statements);
				}
				statements.add(statement);
				allPredicates.add(statement.getPredicate().getURI());
				if (statement.getObject().isAnon())
				{
					allAnonPointers.put(statement.getPredicate().asResource().getURI(), statement.getObject().asResource().getId().toString());
				}
			});
	
			log.info("The read TURTLE file contains {} subjects", allStatements.size());
			
			HashSet<String> processedSubjects = new HashSet<>();

//For debug...
//			for (String s : allStatements.keySet())
//			{
//				System.out.println("Subject: " + s + " :" + allStatements.get(s).size());
//				for (Statement st : allStatements.get(s))
//				{
//					System.out.println("  " + st.toString());
//				}
//			}
			
			authorNid = TermAux.USER.getNid();
			
			UUID parentModule = null;
			releaseTime = 0;
			ResIterator typeIterator = model.listSubjectsWithProperty(model.getProperty("http://purl.org/dc/terms/type"));
			while (typeIterator.hasNext())
			{
				if (parentModule != null)
				{
					throw new RuntimeException("Not set up to handle multiple ontologies in a file yet");
				}
				String subject = typeIterator.next().getURI();
				List<Statement> statements = allStatements.get(subject);
				if (statements == null)
				{
					throw new RuntimeException("Can't find statements that define the ontology");
				}
				
				//We found the one that defines the what we will treat as the root of the ontology.
				//Need to create a Module group concept (if not present) and a module version present
				//Finally, create the concept for this set of statements, and hang it under solor_root
				
				releaseTime = findTime(statements).getAsLong();
				String preferredNamespaceUri = findPredicateValue("http://purl.org/vocab/vann/preferredNamespaceUri", statements).asLiteral().getString();  //http://rdfs.co/bevon/
				String title = findPredicateValue("http://purl.org/dc/terms/title", statements).asLiteral().getString(); //"BEVON: Beverage Ontology"
				String identifier = findPredicateValue("http://purl.org/dc/terms/identifier", statements).asLiteral().getString();  //"http://rdfs.co/bevon/0.8"
				String version = findPredicateValue("http://www.w3.org/2002/07/owl#versionInfo", statements).asLiteral().getString();  //"0.8"
				
				if (!subject.equals(identifier))
				{
					throw new RuntimeException("Was expecting these to be the same: " + subject + ", " + identifier);
				}
	
				parentModule = getConceptUUID(preferredNamespaceUri + " modules");
				
				if (!Get.conceptService().hasConcept(Get.identifierService().getNidForUuids(parentModule)))
				{
					//We don't have a module of our own yet, so put the "grouping" concept on the solor module.
					moduleNid = MetaData.MODULE____SOLOR.getNid();
					dwh = new DirectWriteHelper(MetaData.USER____SOLOR.getNid(), moduleNid, MetaData.DEVELOPMENT_PATH____SOLOR.getNid(), converterUUID);
					dwh.makeConcept(parentModule, Status.ACTIVE, releaseTime);
					
					dwh.makeDescriptionEn(parentModule, preferredNamespaceUri + " modules", fsn, 
							insensitive, 
							Status.ACTIVE, releaseTime, preferred);
					
					dwh.makeDescriptionEn(parentModule, title + " modules", regularName, 
							insensitive, 
							Status.ACTIVE, releaseTime, preferred);
					dwh.makeParentGraph(parentModule, Arrays.asList(new UUID[] {MetaData.MODULE____SOLOR.getPrimordialUuid()}), Status.ACTIVE, releaseTime);
				}
				
				//We have now created a Bevon modules grouping concept.  Configure to that module for further work...
				converterUUID.configureNamespace(parentModule);  //UUID generation always using the parent grouping namespace, not a version-specific module.
				moduleNid = Get.identifierService().getNidForUuids(parentModule);
				
				//See if we can find a better author
				for (List<Statement> groupedStatements : allStatements.values())
				{
					if (!groupedStatements.get(0).getSubject().isAnon())
					{
						for (Statement s : groupedStatements)
						{
							if (s.getPredicate().getURI().equals("http://purl.org/dc/terms/creator"))
							{
								if (authorNid != TermAux.USER.getNid())
								{
									throw new RuntimeException("Not written to handle multiple authors");
								}
								UUID temp = getConceptUUID(s.getObject().asNode().getURI());
								authorNid = Get.identifierService().assignNid(temp);
							}
						}
					}
				}
				
				//Switch the direct write helper to the bevon module for the 'version specific' module...
				if (dwh == null)
				{
					dwh = new DirectWriteHelper(authorNid, moduleNid, MetaData.DEVELOPMENT_PATH____SOLOR.getNid(), converterUUID);
				}
				else
				{
					dwh.changeModule(moduleNid);
					dwh.changeAuthor(authorNid);
				}
				
				//Create a module for this version.
				UUID versionModule = getConceptUUID(identifier + " module");
				moduleNid = Get.identifierService().assignNid(versionModule);
				dwh.changeModule(moduleNid);  //change to the version specific module for all future work.
	
				dwh.makeConcept(versionModule, Status.ACTIVE, releaseTime);
				dwh.makeDescriptionEn(versionModule, identifier + " module", fsn, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				dwh.makeDescriptionEn(versionModule, title + " " + version + " module", regularName, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				dwh.makeParentGraph(versionModule, Arrays.asList(new UUID[] {parentModule}), Status.ACTIVE, releaseTime);
				
				dwh.makeTerminologyMetadataAnnotations(versionModule, converterSourceArtifactVersion, Optional.of(new Date(releaseTime).toString()), 
						Optional.ofNullable(converterOutputArtifactVersion), Optional.ofNullable(converterOutputArtifactClassifier), releaseTime);
	
				for (Statement s : allStatements.get("http://rdfs.co/bevon/CoreGroup"))
				{
					if (s.getPredicate().asResource().getURI().startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#_"))
					{
						conceptsToHangFromCore.add(getConceptUUID(s.getObject().asResource().getURI()));
					}
				}
				
				//Set up our metadata hierarchy
				dwh.makeMetadataHierarchy(title, true, true, false, true, true, true, releaseTime);
				
				//Need to make the root concept, and its rel, prior to adding its descriptions - also the coregroup concept
				rootConcept = getConceptUUID(preferredNamespaceUri, subject);  //make sure our nid is assigned to the combination of both nids.
				//But also give it two UUIDs, so everything attached ends up in the right spot.
				rootConceptId2 = getConceptUUID(subject);
				dwh.makeConcept(rootConcept, Status.ACTIVE, releaseTime, new UUID[] {rootConceptId2});
				dwh.makeParentGraph(rootConcept, Arrays.asList(new UUID[] {MetaData.SOLOR_CONCEPT____SOLOR.getPrimordialUuid()}), Status.ACTIVE, releaseTime);
				
				coreGroupConcept = getConceptUUID("http://rdfs.co/bevon/CoreGroup");
				dwh.makeConcept(coreGroupConcept, Status.ACTIVE, releaseTime);
				dwh.makeParentGraph(coreGroupConcept, Arrays.asList(new UUID[] {rootConcept}), Status.ACTIVE, releaseTime);
				
				//Create some types...
				anu = new AnonymousNodeUtil(string -> getConceptUUID(string), resource -> conceptsToBeBuilt.put(getConceptUUID(resource.getURI()), resource));
				
				//Need to scan all anonymous statements to configure our dynamic semantic mapping rules
				for (Entry<String, String> pointer : allAnonPointers.entrySet())
				{
					Optional<RDFNode> singleValuedExample = Optional.empty();
					if (possibleSingleValueTypedSemantics.containsKey(pointer.getKey()))
					{
						singleValuedExample = findFirstPredicateValue(pointer.getKey());
						possibleSingleValueTypedSemantics.remove(pointer.getKey());
					}
					anu.init(pointer.getKey(), allStatements.get(pointer.getValue()), singleValuedExample.orElse(null));
					if (null != possibleDynamicAttributes.put(pointer.getKey(), new DynamicSemanticHelper(pointer.getKey())))
					{
						throw new RuntimeException("Duplicate definition for " + pointer.getKey());
					}
				}
				
				for (Entry<String, String> s : possibleSingleValueTypedSemantics.entrySet())
				{
					Optional<RDFNode> value = findFirstPredicateValue(s.getKey());
					if (value.isPresent())
					{
						anu.initSingleValuedType(s.getKey(), value.get());
						if (null != possibleDynamicAttributes.put(s.getKey(), new DynamicSemanticHelper(s.getValue(), IsaacObjectType.CONCEPT, null)))
						{
							throw new RuntimeException("Duplicate definition for " + s.getKey());
						}
					}
				}
				
				for (Entry<String, String> entry : possibleRelationships.entrySet())
				{
					if (allPredicates.contains(entry.getKey()))
					{
						UUID stringUUID = getConceptUUID(entry.getKey());
						dwh.makeConcept(stringUUID, Status.ACTIVE, releaseTime);
						dwh.makeDescriptionEn(stringUUID, entry.getKey(), fsn, insensitive, Status.ACTIVE, releaseTime, preferred);
						dwh.makeDescriptionEn(stringUUID, entry.getValue(), regularName, insensitive, Status.ACTIVE, releaseTime, preferred);
						dwh.makeParentGraph(stringUUID, dwh.getRelationTypesNode().get(), Status.ACTIVE, releaseTime);
					}
				}
				
				for (Entry<String, String> entry : possibleRefSets.entrySet())
				{
					if (allPredicates.contains(entry.getKey()))
					{
						UUID refsetId = getConceptUUID(entry.getKey());
						dwh.makeConcept(refsetId, Status.ACTIVE, releaseTime);
						dwh.makeDescriptionEn(refsetId, entry.getKey(), fsn, insensitive, Status.ACTIVE, releaseTime, preferred);
						dwh.makeDescriptionEn(refsetId, entry.getValue(), regularName, insensitive, Status.ACTIVE, releaseTime, preferred);
						dwh.makeParentGraph(refsetId, dwh.getRefsetTypesNode().get(), Status.ACTIVE, releaseTime);
						dwh.configureConceptAsDynamicAssemblage(refsetId, "Refset", null, null, null, releaseTime);
					}
				}
				
				dwh.processTaxonomyUpdates();  //process our metadata, so the taxonomy works properly for other things during load
				Get.taxonomyService().notifyTaxonomyListenersToRefresh();
				
				for (Entry<String, String> entry : possibleAssociations.entrySet())
				{
					if (allPredicates.contains(entry.getKey()))
					{
						UUID associationUUID = getConceptUUID(entry.getKey());
						dwh.makeConcept(associationUUID, Status.ACTIVE, releaseTime);
						if (allStatements.containsKey(entry.getKey()))
						{
							//If it is further defined in this file, just make the relationships at this point, and the dynamic marker info
							ArrayList<Statement> parentStatements = new ArrayList<>();
							for (Statement s : allStatements.get(entry.getKey()))
							{
								if (possibleRelationships.containsKey(s.getPredicate().asResource().getURI()))
								{
									parentStatements.add(s);
								}
							}
							writeParentGraph(entry.getKey(), associationUUID, parentStatements, new UUID[] {dwh.getAssociationTypesNode().get()}, releaseTime);
						}
						else
						{
							dwh.makeDescriptionEn(associationUUID, entry.getKey(), fsn, insensitive, Status.ACTIVE, releaseTime, preferred);
							dwh.makeDescriptionEn(associationUUID, entry.getValue(), regularName, insensitive, Status.ACTIVE, releaseTime, preferred);
							dwh.makeParentGraph(associationUUID, dwh.getAssociationTypesNode().get(), Status.ACTIVE, releaseTime);
						}
						dwh.configureConceptAsAssociation(associationUUID, entry.getKey(), null, IsaacObjectType.CONCEPT, null, releaseTime);
					}
				}
				
				for (Entry<String, DynamicSemanticHelper> entry : possibleDynamicAttributes.entrySet())
				{
					if (allPredicates.contains(entry.getKey()))
					{
						UUID dynamicSemenatic = getConceptUUID(entry.getKey());
						dwh.makeConcept(dynamicSemenatic, Status.ACTIVE, releaseTime);
						
						if (allStatements.containsKey(entry.getKey()))
						{
							//If it is further defined in this file, just make the relationships at this point, and the dynamic marker info
							ArrayList<Statement> parentStatements = new ArrayList<>();
							for (Statement s : allStatements.get(entry.getKey()))
							{
								if (possibleRelationships.containsKey(s.getPredicate().asResource().getURI()))
								{
									parentStatements.add(s);
								}
							}
							writeParentGraph(entry.getKey(), dynamicSemenatic, parentStatements, new UUID[] {dwh.getAttributeTypesNode().get()}, releaseTime);
						}
						else
						{
							dwh.makeDescriptionEn(dynamicSemenatic, entry.getKey(), fsn, insensitive, Status.ACTIVE, releaseTime, preferred);
							if (StringUtils.isNotBlank(entry.getValue().getNiceName()))
							{
								dwh.makeDescriptionEn(dynamicSemenatic, entry.getValue().getNiceName(), regularName, insensitive, Status.ACTIVE, releaseTime, preferred);
							}
							dwh.makeParentGraph(dynamicSemenatic, dwh.getAttributeTypesNode().get(), Status.ACTIVE, releaseTime);
						}
						
						dwh.configureConceptAsDynamicAssemblage(dynamicSemenatic, "Stores anonymous RDF node data", 
								anu.getColumnConstructionInfo(entry.getKey()), entry.getValue().getReferencedComponentTypeRestriction(), 
								entry.getValue().getReferencedComponentTypeSubRestriction(), releaseTime);
					}
				}
				
				for (Entry<String, Pair<String, UUID>> entry : possibleDescriptionTypes.entrySet())
				{
					if (allPredicates.contains(entry.getKey()))
					{
						UUID uuid = getConceptUUID(entry.getKey());
						
						dwh.makeConcept(uuid, Status.ACTIVE, releaseTime);
						if (allStatements.containsKey(entry.getKey()))
						{
							//If it is further defined in this file, just make the relationships at this point
							ArrayList<Statement> parentStatements = new ArrayList<>();
							for (Statement s : allStatements.get(entry.getKey()))
							{
								if (possibleRelationships.containsKey(s.getPredicate().asResource().getURI()))
								{
									parentStatements.add(s);
								}
							}
							writeParentGraph(entry.getKey(), uuid, parentStatements, new UUID[] {dwh.getDescriptionTypesNode().get()}, releaseTime);
						}
						else 
						{
							dwh.makeDescriptionEn(uuid, entry.getKey(), fsn, insensitive, Status.ACTIVE, releaseTime, preferred);
							dwh.makeDescriptionEn(uuid, entry.getValue().getKey(), regularName, insensitive, Status.ACTIVE, releaseTime, preferred);
							dwh.makeParentGraph(uuid, dwh.getDescriptionTypesNode().get(), Status.ACTIVE, releaseTime);
						}
					}
				}
				
				generatePlaceholdersForMissing(releaseTime);  //Some type rels have extended types that point to concepts we don't have - must create now
				//for the next step to work properly
				dwh.processTaxonomyUpdates();  //process our metadata, so the taxonomy works properly for other things during load
				Get.taxonomyService().notifyTaxonomyListenersToRefresh();
				
				process(statements, false);  //Don't add any isA's to the parent, because this will cause cycles, due to how we are generating 
				//unresolved references
				processedSubjects.add(subject);
				processedSubjects.add(preferredNamespaceUri);

				//Technically, we could break here, but would rather validate that this isn't a second entry that fits the criteria - exception will be thrown if we 
				//loop through this code a second time.
			}
	
			for (Entry<String, List<Statement>> entries : allStatements.entrySet())
			{
				if (processedSubjects.contains(entries.getKey()))
				{
					continue;
				}
				else
				{
					try
					{
						process(entries.getValue(), true);
					}
					catch (Exception e)
					{
						log.error("Failed on {}",entries.getKey());
						throw e;
					}
					processedSubjects.add(entries.getKey());
				}
			}
			
			generatePlaceholdersForMissing(releaseTime);
			
			if (conceptsToBeBuilt.size() > 0)
			{
				throw new RuntimeException("Didn't build all concepts that we should have: " + conceptsToBeBuilt.size() + " remaining");
			}
			
			observedAnonNodes.removeAll(processedAnonNodes);
			if (observedAnonNodes.size() > 0)
			{
				log.warn("Some anonymous nodes were not processed!");
				for (String s : observedAnonNodes)
				{
					log.info("Unprocessed anonymous node: " + s);
				}
			}
			
			dwh.processTaxonomyUpdates();
			
		}
		finally
		{
			Get.taxonomyService().notifyTaxonomyListenersToRefresh();
	
			if (converterUUID != null && this.outputDirectory != null)
			{
				log.info("Dumping UUID Debug File");
				converterUUID.dump(this.outputDirectory, "TurtleUUID");
				converterUUID.clearCache();
			}
			
			if (dwh != null)
			{
				log.info("Load Stats:");
				for (final String s : this.dwh.getLoadStats().getSummary())
				{
					log.info("  " + s);
				}
			}
			inputStream.close();
		}
	}
	
	private void generatePlaceholdersForMissing(long releaseTime)
	{
		Iterator<UUID> it = conceptsToBeBuilt.keySet().iterator();
		while (it.hasNext())
		{
			if (Get.conceptService().hasConcept(Get.identifierService().getNidForUuids(it.next())))
			{
				it.remove();
			}
		}
		
		if (conceptsToBeBuilt.size() > 0)
		{
			//There were target objects that pointed to a resource we haven't resolved.  Just make placeholders, and hang them under our tree
			//so we can navigate things.
			UUID unresolvedConcepts = getConceptUUID("Unresolved External References");
			
			//Allow this to be called more than once....
			if (!Get.conceptService().hasConcept(Get.identifierService().assignNid(unresolvedConcepts)))
			{
				dwh.makeConcept(unresolvedConcepts, Status.ACTIVE, releaseTime, (UUID[])null);
				dwh.makeDescriptionEn(unresolvedConcepts, "Unresolved External References", fsn, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				dwh.makeDescriptionEn(unresolvedConcepts, "These are external ontology references that were not fully resolved while processing this file.  They are created here"
						+ " as placeholders, so they could be manually resolved and also to make the hierarchy navigable.", 
						definition, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				dwh.makeParentGraph(unresolvedConcepts, Arrays.asList(new UUID[] {rootConcept}), Status.ACTIVE, releaseTime);
			}
			
			Iterator<Entry<UUID, Resource>> i = conceptsToBeBuilt.entrySet().iterator();
			while (i.hasNext())
			{
				Entry<UUID, Resource> entry = i.next();
				if (entry.getValue().isResource() && allStatements.containsKey(entry.getValue().asResource().getURI()))
				{
					continue;  //we will make this one later, when we process the statements
				}
				dwh.makeConcept(entry.getKey(), Status.ACTIVE, releaseTime, (UUID[])null);
				i.remove();
				dwh.makeDescriptionEn(entry.getKey(), entry.getValue().getURI(), fsn, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				if (StringUtils.isNotBlank(entry.getValue().getLocalName()))
				{
					dwh.makeDescriptionEn(entry.getKey(), entry.getValue().getLocalName(), regularName, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				}
				dwh.makeParentGraph(entry.getKey(), Arrays.asList(new UUID[] {unresolvedConcepts}), Status.ACTIVE, releaseTime);
			}
		}
	}
	
	/**
	 * Same as {@link #findPredicateValues(String, List)}, but only expects to find one entry.
	 * @param predicate
	 * @param statements
	 * @return
	 */
	private RDFNode findPredicateValue(String predicate, List<Statement> statements)
	{
		List<RDFNode> values = findPredicateValues(predicate, false, statements);
		if (values.size() != 1)
		{
			throw new RuntimeException("There are " + values.size() + " for " + predicate + " when one was expected");
		}
		return values.get(0);
	}
	
	private Optional<RDFNode> findFirstPredicateValue(String predicate)
	{
		for (List<Statement> sl : allStatements.values())
		{
			for (Statement s : sl)
			{
				if (s.getPredicate().isResource() && s.getPredicate().asResource().getURI().equals(predicate) && !s.getObject().isAnon())
				{
					return Optional.of(s.getObject());
				}
			}
		}
		return Optional.empty();
	}
	
	private List<RDFNode> findPredicateValues(String predicate, boolean prefixMatch, List<Statement> statements)
	{
		List<RDFNode> values = new ArrayList<>();
		for (Statement s : statements)
		{
			if (s.getPredicate().isResource() && 
					(prefixMatch ?  s.getPredicate().asResource().getURI().startsWith(predicate) : s.getPredicate().asResource().getURI().equals(predicate)))
			{
				values.add(s.getObject());
			}
		}
		return values;
	}

	/**
	 * @param statements
	 * @return
	 */
	private OptionalLong findTime(List<Statement> statements)
	{
		List<RDFNode> times = findPredicateValues("http://purl.org/dc/terms/modified", false, statements);
		if (times.size() > 1)
		{
			throw new RuntimeException("Multiple modified times?");
		}
		//try another pattern
		else if (times.size() == 0)
		{
			times = findPredicateValues("http://purl.org/dc/terms/issued", false, statements);
		}
		
		if (times.size() > 1)
		{
			throw new RuntimeException("Multiple issued times?");
		}
		//try another pattern
		else if (times.size() == 0)
		{
			times = findPredicateValues("http://purl.org/dc/terms/date", false, statements);
		}
		
		if (times.size() == 0)
		{
			return OptionalLong.empty();
		}
		
		if (times.size() > 1)
		{
			throw new RuntimeException("Multiple date times?");
		}
		
		return OptionalLong.of(((XSDDateTime) times.get(0).asLiteral().getValue()).asCalendar().getTimeInMillis());
	}

	/**
	 * @param subjectStatements a list of statements which all share the same subject
	 */
	private UUID process(List<Statement> subjectStatements, boolean writeParentGraphs)
	{

		String subjectFQN = subjectStatements.get(0).getSubject().getURI();
		if (subjectFQN == null)
		{
			if (!processedAnonNodes.remove(subjectStatements.get(0).getSubject().getId().getLabelString()))
			{
				//If we haven't yet processed this, stash for later sanity checking
				observedAnonNodes.add(subjectStatements.get(0).getSubject().getId().getLabelString());
			}
			return null;
		}
		else
		{
			ArrayList<Statement> properties = new ArrayList<>(subjectStatements.size());
			ArrayList<Statement> parentStatements = new ArrayList<>(subjectStatements.size());
			long time = 0;
			Statement timeStatementStash = null;
			Status status = Status.ACTIVE;  //haven't yet seen an attribute that would qualify to inactivate something
			String title = null;
			
			String collectionType = "Unspecified collection";
			
			for (Statement s : subjectStatements)
			{
				if (s.getPredicate().isURIResource())
				{
					if (s.getPredicate().getURI().equals("http://purl.org/dc/terms/modified"))
					{
						time = ((XSDDateTime) s.getObject().asLiteral().getValue()).asCalendar().getTimeInMillis();

						if (timeStatementStash != null)
						{
							properties.add(timeStatementStash);
							timeStatementStash = null;
						}
					}
					else if (time == 0 && s.getPredicate().getURI().equals("http://purl.org/dc/terms/issued"))
					{
						((XSDDateTime) s.getObject().asLiteral().getValue()).asCalendar().getTimeInMillis();
						timeStatementStash = s;
					}
					// it may not be correct to treat both of these as isA, but will for now....
					else if (possibleRelationships.containsKey(s.getPredicate().getURI()))
					{
						parentStatements.add(s);
						if (s.getObject().asResource().getURI().startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#"))
						{
							collectionType = s.getObject().asResource().getURI().substring("http://www.w3.org/1999/02/22-rdf-syntax-ns#".length()); 
						}
					}
					else if (s.getPredicate().getURI().equals("http://purl.org/dc/terms/title"))
					{
						title = s.getObject().asLiteral().getString();
					}
					else
					{
						properties.add(s);
					}
				}
				else
				{
					throw new RuntimeException("Unhandled Predicate: " + s.getPredicate());
				}
			}
			
			if (time == 0)
			{
				time = releaseTime;
			}

			UUID concept = getConceptUUID(subjectFQN);
			
			UUID additional = null;
			if (title != null)
			{
				additional = getConceptUUID(title);
			}
			
			if (possibleAssociations.containsKey(subjectFQN) || possibleDynamicAttributes.containsKey(subjectFQN) || possibleDescriptionTypes.containsKey(subjectFQN)
					|| possibleRefSets.containsKey(subjectFQN) || possibleRelationships.containsKey(subjectFQN) || possibleSingleValueTypedSemantics.containsKey(subjectFQN)
					|| anu.knownTypes.containsKey(subjectFQN)
					|| concept.equals(rootConcept) || concept.equals(rootConceptId2) || concept.equals(coreGroupConcept))
			{
				//We already made this concept, and its parent graphs
				writeParentGraphs = false;
				if (concept.equals(rootConceptId2))
				{
					concept = rootConcept;  //this is so our dynamic UUID generation is consistent, and uses the UUID of the non-versioned URI for the referenced component
				}
			}
			else
			{
				dwh.makeConcept(concept, status, time, new UUID[] {additional});
				conceptsToBeBuilt.remove(concept);
			}

			UUID language = MetaData.ENGLISH_LANGUAGE____SOLOR.getPrimordialUuid();
			UUID dialect = MetaData.US_ENGLISH_DIALECT____SOLOR.getPrimordialUuid();
			UUID description = dwh.makeDescription(concept, (title == null ? subjectFQN : title), fsn, language,
					insensitive, status, time, dialect,
					preferred);
			if (title != null)
			{
				dwh.makeExtendedDescriptionTypeAnnotation(description, getConceptUUID("http://purl.org/dc/terms/title"), time);
			}
			
			
			String subjectRegularName = subjectStatements.get(0).getSubject().getLocalName();
			if (StringUtils.isNotBlank(subjectRegularName))
			{
				dwh.makeDescription(concept, subjectRegularName, regularName, language,
					insensitive, status, time, dialect,
					preferred);
			}
			
			if (title != null)
			{
				dwh.makeDescription(concept, subjectFQN, fsn, language,
						insensitive, status, time, dialect,
						acceptable);
			}

			boolean madeRefset = handleProperties(concept,time, subjectRegularName, properties, collectionType);

			if (writeParentGraphs) 
			{
				writeParentGraph(subjectFQN, concept, parentStatements, (madeRefset ? new UUID[] {dwh.getRefsetTypesNode().get()} : null), time);
			}
			return concept;
		}
	}
	
	private UUID writeParentGraph(String subjectFQN, UUID concept, ArrayList<Statement> parentStatements, UUID[] additionalParents, long time)
	{
		ArrayList<UUID> parents = new ArrayList<>();
		for (Statement s : parentStatements)
		{
			UUID uuid = getConceptUUID(s.getObject().asResource().getURI());
			parents.add(uuid);
			if (!Get.conceptService().hasConcept(Get.identifierService().getNidForUuids(uuid)))
			{
				conceptsToBeBuilt.put(uuid, s.getObject().asResource());
			}
		}

		if (subjectFQN.equals("http://rdfs.co/bevon/CoreGroup"))
		{
			parents.add(rootConcept);
		}
		//This is a refset, in OWL, but it makes it much more browseable as a taxononomy, if we also make it a isA...
		if (conceptsToHangFromCore.contains(concept))
		{
			parents.add(coreGroupConcept);
		}
		
		if (Get.identifierService().getNidForUuids(concept) == authorNid)
		{
			parents.add(MetaData.USER____SOLOR.getPrimordialUuid());
		}
		
		if (additionalParents != null)
		{
			for (UUID parent : additionalParents) 
			{
				parents.add(parent);
			}
		}
		
		if (parents.size() == 0)
		{
			UUID orphanParent = getConceptUUID("Unlinked Concepts");
			
			//Allow this to be called more than once....
			if (!Get.conceptService().hasConcept(Get.identifierService().assignNid(orphanParent)))
			{
				dwh.makeConcept(orphanParent, Status.ACTIVE, releaseTime, (UUID[])null);
				dwh.makeDescriptionEn(orphanParent, "Unlinked Concepts", fsn, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				dwh.makeDescriptionEn(orphanParent, "These are ontology references that do not have any parents in the supplied ontology graph.", 
						definition, 
						insensitive, 
						Status.ACTIVE, releaseTime, preferred);
				dwh.makeParentGraph(orphanParent, Arrays.asList(new UUID[] {rootConcept}), Status.ACTIVE, releaseTime);
			}
			parents.add(orphanParent);
		}

		UUID logicGraph = dwh.makeParentGraph(concept, parents, Status.ACTIVE, time);
		HashSet<String> uniquePredicates = new HashSet<>();
		for (Statement s : parentStatements)
		{
			uniquePredicates.add(s.getPredicate().getURI());
		}
		for (String s : uniquePredicates)
		{
			dwh.makeExtendedRelationshipTypeAnnotation(logicGraph, getConceptUUID(s), time);
		}
		return logicGraph;
	}

	/**
	 * @param propStatements
	 * @return true, if this concept was turned into a refset definition by the properties we processed
	 */
	private boolean handleProperties(UUID concept, long time, String localName, ArrayList<Statement> propStatements, String collectionType)
	{
		boolean madeRefset = false;
		if (findPredicateValues("http://www.w3.org/1999/02/22-rdf-syntax-ns#_", true, propStatements).size() > 0)
		{
			dwh.configureConceptAsDynamicAssemblage(concept, collectionType, null, null, null, time);
			madeRefset = true;
		}

		for (Statement s : propStatements)
		{
			if (s.getPredicate().asResource().getURI().startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#_"))
			{
				//collection entry (bag or otherwise)
				dwh.makeDynamicRefsetMember(concept, getConceptUUID(s.getObject().asResource().getURI()), time);
			}
			else if (possibleDynamicAttributes.containsKey(s.getPredicate().asResource().getURI()) && s.getObject().isAnon())
			{
				List<Statement> anonNodeStatements = allStatements.get(s.getObject().asResource().getId().toString());
				OptionalLong semanticTime = findTime(anonNodeStatements);
				
				processAnonStatements(anonNodeStatements, semanticTime.orElse(time), s.getPredicate().asResource().getURI(), concept, 
						s.getObject().asResource().getId().toString());
			}
			else if (possibleDynamicAttributes.containsKey(s.getPredicate().asResource().getURI()))
			{
				dwh.makeDynamicSemantic(getConceptUUID(s.getPredicate().asResource().getURI()), concept, 
						anu.getDataColumns(s.getPredicate().asResource().getURI(), Arrays.asList(new Statement[] {s})), time);
			}
			else if (possibleDescriptionTypes.containsKey(s.getPredicate().asResource().getURI()))
			{
				if (s.getObject().asLiteral().getString().equals(localName))
				{
					//skip, otherwise, we make a dupe description
					continue;
				}
				
				Pair<String, UUID> descInfo = possibleDescriptionTypes.get(s.getPredicate().asResource().getURI());
				
				LanguageCode lc = LanguageCode.getLangCode(s.getObject().asLiteral().getLanguage());
				UUID description = dwh.makeDescription(concept, s.getObject().asLiteral().getString(), descInfo.getValue(), 
						LanguageMap.getConceptForLanguageCode(lc).getPrimordialUuid(), 
						insensitive, Status.ACTIVE, time, 
						LanguageMap.getConceptDialectForLanguageCode(lc).getPrimordialUuid(), 
						acceptable);
				dwh.makeExtendedDescriptionTypeAnnotation(description, getConceptUUID(s.getPredicate().asResource().getURI()), time);
			}
			else if (dwh.isAssociation(getConceptUUID(s.getPredicate().asResource().getURI())) && !s.getObject().isAnon())
			{
				dwh.makeAssociation(getConceptUUID(s.getPredicate().asResource().getURI()), concept, getConceptUUID(s.getObject().asResource().getURI()), time);
				conceptsToBeBuilt.put(getConceptUUID(s.getObject().asResource().getURI()), s.getObject().asResource());
			}
			else if (possibleRelationships.containsKey(s.getPredicate().asResource().getURI()))
			{
				//NOOP, isA rels are handled in the calling method
			}
			else if (s.getPredicate().asResource().getURI().equals("http://www.w3.org/2000/01/rdf-schema#comment"))
			{
				dwh.makeComment(concept, s.getObject().asLiteral().getString(), null, time);
			}
			else if (possibleRefSets.containsKey(s.getPredicate().asResource().getURI()))
			{
				dwh.makeBrittleRefsetMember(getConceptUUID(s.getPredicate().asResource().getURI()), concept, time);
			}
			else
			{
				log.warn("property type not yet handled: " + s.getPredicate().asResource().getURI());
			}
		}
		return madeRefset;
	}
	
	private void processAnonStatements(List<Statement> anonNodeStatements, long time, String predicateURI, UUID attachTo, String anonId)
	{
		OptionalLong semanticTime = findTime(anonNodeStatements);
		
		if (!possibleDynamicAttributes.containsKey(predicateURI))
		{
			log.warn("Property type not yet handled: " + predicateURI);
			return;
		}
		
		DynamicData[] data = anu.getDataColumns(predicateURI, anonNodeStatements);
		DynamicData[] tempDataForUUIDGen = Arrays.copyOf(data, data.length + 1);
		tempDataForUUIDGen[data.length] = new DynamicIntegerImpl(hashNestedData(predicateURI, anonNodeStatements));
		
		//Have to calculate my own UUID, because the data on these ones that mostly contain nested stuff, isn't unique.
		UUID semantic = UuidFactory.getUuidForDynamic(converterUUID.getNamespace(), getConceptUUID(predicateURI), attachTo, tempDataForUUIDGen,
				((input, uuid) -> converterUUID.addMapping(input, uuid)));
		
		dwh.makeDynamicSemantic(getConceptUUID(predicateURI), attachTo, data, semanticTime.orElse(time), semantic);
		
		if (!observedAnonNodes.remove(anonId))
		{
			//If we processed it before we observed it, stash this for later sanity checking.
			processedAnonNodes.add(anonId);
		}
		
		for (Pair<String, String> nestedAnonIds : anu.getNestedAnons(anonNodeStatements))
		{
			processAnonStatements(allStatements.get(nestedAnonIds.getValue()), time, nestedAnonIds.getKey(), semantic, nestedAnonIds.getValue());
		}
	}
	
	//This hack won't work, if you use the dynamic data type NID anywhere...
	private int hashNestedData(String predicateURI, List<Statement> anonNodeStatements)
	{
		int hashCode = 0;
		for (Pair<String, String> nestedAnonIds : anu.getNestedAnons(anonNodeStatements))
		{
			if (!possibleDynamicAttributes.containsKey(nestedAnonIds.getKey()))
			{
				log.warn("Property type not yet handled: " + nestedAnonIds.getKey());
				hashCode +=1;
			}
			else
			{
				DynamicData[] nestedData = anu.getDataColumns(nestedAnonIds.getKey(), allStatements.get(nestedAnonIds.getValue()));
				for (DynamicData dd : nestedData)
				{
					hashCode += dd.getData().hashCode();
				}
				
				for (Pair<String, String> nested : anu.getNestedAnons(allStatements.get(nestedAnonIds.getValue())))
				{
					hashCode += hashNestedData(nested.getKey(), allStatements.get(nestedAnonIds.getValue()));
				}
			}
		}
		return hashCode;
	}
	

	/**
	 * Get a UUID from a string WITHOUT doing duplicate generation checking. You should only use this method
	 * if you are sure you aren't creating a duplicate entry...
	 * 
	 * @param input - one or more strings to map to UUIDs.  All UUIDs will be treated together, in assignment of the associated nid...
	 * @return
	 */
	private UUID getConceptUUID(String ... input)
	{
		UUID[] allUUIDs = new UUID[input.length];
		int i = 0;
		for (String in : input)
		{
			UUID temp = converterUUID.createNamespaceUUIDFromString(in, true);
			allUUIDs[i++] = temp;
		}
		Get.identifierService().assignNid(allUUIDs);
		return allUUIDs[0];
	}
}