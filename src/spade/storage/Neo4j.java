/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexCreator;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.IndexType;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.BloomFilter;
import spade.core.Edge;
import spade.core.Settings;
import spade.core.Vertex;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.storage.neo4j.Neo4jInstructionExecutor;
import spade.storage.neo4j.Neo4jQueryEnvironment;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.map.external.cache.LRUCache;

/**
 * Neo4j storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public class Neo4j extends AbstractStorage{

	private static final Logger logger = Logger.getLogger(Neo4j.class.getName());

	private final String
		keyDbHomeDirectoryPath = "dbms.directories.neo4j_home",
		keyDbDataDirectoryName = "database",
		keyDbName = "dbms.default_database",
		keySleepWaitMillis = "sleepWaitMillis", 
		keyWaitForIndexSeconds = "waitForIndexSeconds",
		keyDebug = "debug", 
		keyForceShutdown = "forceShutdown",
		keyReportingIntervalSeconds = "reportingIntervalSeconds",
		keyHashPropertyName = "hashPropertyName", 
		keyEdgeSymbolsPropertyName = "edgeSymbolsPropertyName",
		keyHashKeyCollisionAction = "hashKeyCollisionAction", 
		keyMaxRetries = "maxRetries",
		keyNodePrimaryLabel = "nodePrimaryLabel", 
		keyEdgeRelationshipType = "edgeRelationshipType",
		keyFlushBufferSize = "flushBufferSize", 
		keyFlushAfterSeconds = "flushAfterSeconds",
		keyBufferLimit = "bufferLimit",
		keyNeo4jConfigFilePath = "neo4jConfigFilePath",
		keyVertexBloomFilterFalsePositiveProbability = "vertex.bloomfilter.false_positive_probability",
		keyVertexBloomFilterExpectedElements = "vertex.bloomfilter.expected_elements",
		keyVertexCacheMaxSize = "vertex.cache.max_size",
		keyEdgeBloomFilterFalsePositiveProbability = "edge.bloomfilter.false_positive_probability",
		keyEdgeBloomFilterExpectedElements = "edge.bloomfilter.expected_elements",
		keyEdgeCacheMaxSize = "edge.cache.max_size", 
		keyIndexVertex = "index.vertex", 
		keyIndexEdge = "index.edge";
//		keyIndexVertexUser = "index.vertex.user",
//		keyIndexEdgeUser = "index.edge.user";

	private final String vertexBloomFilterLoadSaveFileName = "spade-bloomfilter-vertex",
			edgeBloomFilterLoadSaveFileName = "spade-bloomfilter-edge";

	private Neo4jInstructionExecutor queryInstructionExecutor = null;
	private Neo4jQueryEnvironment queryEnvironment = null;

	// User defined
	private enum HashKeyCollisionAction{
		DISCARD, REMOVE
	};

	private enum IndexMode{
		ALL, NONE,
	}; // USER };

	private File dbHomeDirectoryFile;
	private String dbDataDirectoryName;
	private String dbName;
	private File finalConstructedDbPath;
	private boolean debug;
	private boolean forceShutdown;
	private long sleepWaitMillis;
	private int waitForIndexSeconds;
	private int reportingIntervalSeconds;
	private String hashPropertyName;
	private String edgeSymbolsPropertyName;
	private HashKeyCollisionAction hashKeyCollisionAction;
	private int maxRetries;
	private String nodePrimaryLabelName;
	private String edgeRelationshipTypeName;
	private int flushBufferSize;
	private int flushAfterSeconds;
	private int bufferLimit;
	private File neo4jConfigFilePath;
	private double vertexBloomFilterFalsePositiveProbability;
	private int vertexBloomFilterExpectedElements;
	private int vertexCacheMaxSize;
	private double edgeBloomFilterFalsePositiveProbability;
	private int edgeBloomFilterExpectedElements;
	private int edgeCacheMaxSize;
	private IndexMode indexVertexMode;
	private IndexMode indexEdgeMode;
//	private String[] indexVertexKeysUser;
//	private String[] indexEdgeKeysUser;

	// Computed
	private final Object shutdownLock = new Object();
	private volatile boolean shutdown = false;
	private final Object mainThreadRunningLock = new Object();
	private volatile boolean mainThreadRunning = false;
	private boolean reportingEnabled;
	private DatabaseManagementService dbManagementService;
	private GraphDatabaseService graphDb;
	private String vertexBloomFilterLoadSavePath;
	private BloomFilter<String> vertexBloomFilter;
	private String edgeBloomFilterLoadSavePath;
	private BloomFilter<String> edgeBloomFilter;
	private LRUCache<String, Long> vertexHashToNodeIdMap;
	private LRUCache<String, Boolean> edgeMap;
	private Label neo4jVertexLabel;
	private RelationshipType neo4jEdgeRelationshipType;
	private Neo4jStats stats;

	private final Set<String> nodePropertyNames = new HashSet<String>();
	private final Set<String> nodePropertyNamesIndexed = new HashSet<String>();
	private final Set<String> relationshipPropertyNames = new HashSet<String>();
	private final Set<String> relationshipPropertyNamesIndexed = new HashSet<String>();

	private final LinkedList<Neo4jDbTask<?>> neo4jDbTasksPending = new LinkedList<Neo4jDbTask<?>>();

	private final <T> List<T> listify(final Iterable<T> iterable){
		final List<T> list = new ArrayList<T>();
		if(iterable != null){
			final Iterator<T> iterator = iterable.iterator();
			while(iterator.hasNext()){
				final T item = iterator.next();
				list.add(item);
			}
		}
		return list;
	}

	private final Set<String> getAllValuesInAllMaps(final List<Map<String, Object>> listOfMaps, final String msgIfNull){
		final Set<String> answer = new HashSet<String>();
		for(final Map<String, Object> map : listOfMaps){
			for(Object o : map.values()){
				if(o == null){
					debug(msgIfNull + ": " + map);
				}else{
					answer.add(o.toString());
				}
			}
		}
		return answer;
	}

	private final Set<String> getAllRelationshipKeys(final Transaction tx) throws Exception{
		final Neo4jDbExecuteQuery queryForProperties = new Neo4jDbExecuteQuery(
				"match ()-[e]->() unwind keys(e) as spade_key return distinct(spade_key);");
		return getAllValuesInAllMaps(queryForProperties.execute(tx), "NULL relationship property key");
	}

	private final Set<String> getAllNodeKeys(final Transaction tx) throws Exception{
		final Neo4jDbExecuteQuery queryForProperties = new Neo4jDbExecuteQuery(
				"match (v) unwind keys(v) as spade_key return distinct(spade_key);");
		return getAllValuesInAllMaps(queryForProperties.execute(tx), "NULL node property key");
	}

	// if forNodes == false then it means get for relationship
	private final Set<String> getAllIndexedKeys(final Transaction tx, final boolean forNodes) throws Exception{
		final Set<String> indexedKeys = new HashSet<String>();
		final Iterable<IndexDefinition> indexDefinitionsList = tx.schema().getIndexes();
		final Iterator<IndexDefinition> indexDefinitions = indexDefinitionsList.iterator();
		while(indexDefinitions.hasNext()){
			final IndexDefinition indexDefinition = indexDefinitions.next();
			if(indexDefinition.isNodeIndex()){
				if(forNodes){
					indexedKeys.addAll(listify(indexDefinition.getPropertyKeys()));
				}
			}
			if(indexDefinition.isRelationshipIndex()){
				if(!forNodes){
					indexedKeys.addAll(listify(indexDefinition.getPropertyKeys()));
				}
			}
		}
		return indexedKeys;
	}

	private final void waitForIndexesToComeOnline(final Transaction tx, final List<IndexDefinition> indexDefinitions)
			throws Exception{
		if(indexDefinitions.size() > 0){
			if(waitForIndexSeconds > 0){
				// cater for the case when the storage shuts down because of max retries
				// exceeded TODO
				while(true){
					try{
						tx.schema().awaitIndexesOnline(waitForIndexSeconds, TimeUnit.SECONDS);
					}catch(Exception e){
						// IllegalStateException with IndexState set to Failed/Population
						for(final IndexDefinition indexDefinition : indexDefinitions){
							System.out.println(indexDefinition + ", " + tx.schema().getIndexState(indexDefinition));
						}
						// throw e;
					}
					boolean allPercentagesOver100 = true;
					for(final IndexDefinition indexDefinition : indexDefinitions){
						float percentage = tx.schema().getIndexPopulationProgress(indexDefinition)
								.getCompletedPercentage();
						logger.log(Level.INFO, String
								.format("'" + indexDefinition.getName() + "' index progress: %1.0f%%", percentage));
						allPercentagesOver100 = allPercentagesOver100 && (percentage >= 100);
					}
					if(allPercentagesOver100){
						break;
					}
				}
			}
		}
	}

	private final void loadGlobalPropertyKeysAndIndexedKeys(final Transaction tx) throws Exception{
		final Set<String> vertexKeys = getAllNodeKeys(tx);
		final Set<String> edgeKeys = getAllRelationshipKeys(tx);
		final Set<String> vertexKeysIndexed = getAllIndexedKeys(tx, true);
		final Set<String> edgeKeysIndexed = getAllIndexedKeys(tx, false);

		synchronized(nodePropertyNames){
			nodePropertyNames.clear();
			nodePropertyNames.addAll(vertexKeys);
			// Add the hash property for nodes
			nodePropertyNames.add(hashPropertyName);
			debug("Node properties: " + nodePropertyNames);
		}

		synchronized(relationshipPropertyNames){
			relationshipPropertyNames.clear();
			relationshipPropertyNames.addAll(edgeKeys);
			// Add the hash property and the edge symbol property for relationships
			relationshipPropertyNames.add(hashPropertyName);
			relationshipPropertyNames.add(edgeSymbolsPropertyName);
			debug("Relationship properties: " + relationshipPropertyNames);
		}

		synchronized(nodePropertyNamesIndexed){
			nodePropertyNamesIndexed.clear();
			nodePropertyNamesIndexed.addAll(vertexKeysIndexed);
			debug("Indexed Node properties: " + nodePropertyNamesIndexed);
		}

		synchronized(relationshipPropertyNamesIndexed){
			relationshipPropertyNamesIndexed.clear();
			relationshipPropertyNamesIndexed.addAll(edgeKeysIndexed);
			debug("Indexed Relationship properties: " + relationshipPropertyNamesIndexed);
		}

		/*
		 * final List<IndexDefinition> indexDefinitions = new
		 * ArrayList<IndexDefinition>();
		 * 
		 * final Set<String> nodeKeysToIndex = new HashSet<String>();
		 * nodeKeysToIndex.addAll(nodePropertyNames); // Add all
		 * nodeKeysToIndex.removeAll(nodePropertyNamesIndexed); // Remove all that are
		 * already indexed for(final String nodeKeyToIndex : nodeKeysToIndex){
		 * indexDefinitions.add(createIndexOnKey(tx, true, nodeKeyToIndex)); }
		 * 
		 * final Set<String> relationshipKeysToIndex = new HashSet<String>();
		 * relationshipKeysToIndex.addAll(relationshipPropertyNames);
		 * relationshipKeysToIndex.removeAll(relationshipPropertyNamesIndexed);
		 * for(final String relationshipKeyToIndex : relationshipKeysToIndex){
		 * indexDefinitions.add(createIndexOnKey(tx, false, relationshipKeyToIndex)); }
		 * 
		 * waitForIndexesToComeOnline(tx, indexDefinitions);
		 */
	}

	private final int getPendingTasksSize(){
		synchronized(neo4jDbTasksPending){
			return neo4jDbTasksPending.size();
		}
	}

	private final Neo4jDbTask<?> removeFirstPendingTask(){
		if(mainThreadRunning){
			synchronized(neo4jDbTasksPending){
				final Neo4jDbTask<?> task = neo4jDbTasksPending.pollFirst();
				if(task != null){
					stats.pendingTasksOutgoing.increment();
				}
				return task;
			}
		}
		return null;
	}

	private final void appendPendingTask(final Neo4jDbTask<?> task){
		if(task != null){
			if(mainThreadRunning){
				enforceBufferLimit();
				synchronized(neo4jDbTasksPending){
					stats.pendingTasksIncoming.increment();
					neo4jDbTasksPending.addLast(task);
				}
			}
		}
	}

	private final void prependPendingTask(final Neo4jDbTask<?> task){
		if(task != null){
			if(mainThreadRunning){
				// Don't enforce limit here because of high priority
				synchronized(neo4jDbTasksPending){
					stats.pendingTasksIncoming.increment();
					neo4jDbTasksPending.addFirst(task);
				}
			}
		}
	}
	
	private final void enforceBufferLimit(){
		if(bufferLimit > -1){
			long waitStartMillis = 0;
			boolean needToWait = getPendingTasksSize() > bufferLimit;
			if(needToWait){
				//debug("Buffer limit reached: " + bufferLimit + ". Current buffer size: " + getPendingTasksSize());
				waitStartMillis = System.currentTimeMillis();
			}
			while(getPendingTasksSize() > bufferLimit){
				if(shutdown || !mainThreadRunning){
					break;
				}
				HelperFunctions.sleepSafe(sleepWaitMillis);
			}
			if(needToWait){
				final long diffMillis = (System.currentTimeMillis() - waitStartMillis);
				if(diffMillis >= 10 * 1000){
					debug("Buffer limit below in: " + diffMillis + " millis.");
				}
			}
		}
	}

	private final void prependCreateIndexPendingTask(final boolean forNodes, final String key) throws Exception{
		IndexMode indexMode;
		if(forNodes){
			indexMode = indexVertexMode;
		}else{
			indexMode = indexEdgeMode;
		}

		switch(indexMode){
		case ALL:
			prependPendingTask(new Neo4jDbCreateIndex(key, forNodes));
			break;
		case NONE:
			break;
		default:
			throw new Exception("Unhandled indexing mode for keys '" + keyIndexVertex + "' and/or '" + keyIndexEdge
					+ "': " + indexMode);
		}
	}

	private final boolean isShutdown(){
		synchronized(shutdownLock){
			return shutdown;
		}
	}

	private final void setShutdown(final boolean shutdown){
		synchronized(shutdownLock){
			this.shutdown = shutdown;
		}
	}

	private final Runnable dbPendingTasksRunner = new Runnable(){
		
		// Globals
		int tasksExecutedSinceLastFlush;
		long timeInMillisOfLastFlush;
		
		private final Transaction getANewTransaction(final GraphDatabaseService graphDb, Transaction tx, final boolean commit){
			if(tx != null){
				try{
					if(commit){
						tx.commit();
					}else{
						tx.rollback();
					}
				}catch(Throwable t){
					final String actionName = commit ? "commit" : "rollback";
					logger.log(Level.WARNING, "Failed to "+actionName+" transaction", t);
					try{ tx.terminate(); }catch(Throwable t2){ }
				}
				try{ tx.close(); }catch(Throwable t){ }
				tx = null;
			}
			tasksExecutedSinceLastFlush = 0;
			timeInMillisOfLastFlush = System.currentTimeMillis();
			return graphDb.beginTx();
		}
		
		@Override
		public void run(){
			boolean runTheMainLoop;
			Transaction tx = null;

			int fatalErrorCount = 0;

			Neo4jDbTask<?> task = null;

			synchronized(mainThreadRunningLock){
				mainThreadRunning = true;
			}

			try(final Transaction tempTx = graphDb.beginTx()){
				loadGlobalPropertyKeysAndIndexedKeys(tempTx); // Only read
				// No error
				runTheMainLoop = true;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to load properties and indexes. Storage not in usable state.", e);
				runTheMainLoop = false;
			}

			if(runTheMainLoop){
				int pendingTasksSize = 0;
				while(!isShutdown() || (pendingTasksSize = getPendingTasksSize()) > 0){
					if(isShutdown() && forceShutdown){
						break;
					}
					stats.print(false, pendingTasksSize);
					try{
						if(tx == null){
							tx = getANewTransaction(graphDb, tx, true);
						}else{
							if(tasksExecutedSinceLastFlush > flushBufferSize || (System.currentTimeMillis()
									- timeInMillisOfLastFlush > flushAfterSeconds * 1000)){
								if(tasksExecutedSinceLastFlush > 0){
									debug(tasksExecutedSinceLastFlush + " tasks flushed after "
											+ (System.currentTimeMillis() - timeInMillisOfLastFlush) + " millis");

									// Not a resource leak. Always closed in the function
									tx = getANewTransaction(graphDb, tx, true);
								}else{
									// tasksExecutedSinceLastFlush is empty so no need to commit anything and then
									// creating a new transaction
								}
							}
						}

						task = removeFirstPendingTask();
						if(task != null){
							try{
								if(task.commitBeforeExecution){
									tx = getANewTransaction(graphDb, tx, true);
								}
								task.execute(tx);
								if(task.commitAfterExecution){
									tx = getANewTransaction(graphDb, tx, true);
								}
							}catch(Throwable t){
								task.setError(t);
								
								tx = getANewTransaction(graphDb, tx, false);
								
								throw t;
							}finally{
								tasksExecutedSinceLastFlush++;
								task.completed();
							}
						}
						fatalErrorCount = 0;
					}catch(Throwable t){
						if(task != null){
							logger.log(Level.SEVERE, "Failed to execute a task: " + task);
						}
						logger.log(Level.SEVERE, "Retry#" + (++fatalErrorCount));
						try{
							logger.log(Level.SEVERE, "Error", t);
						}catch(Exception e){
							
						}
						if(fatalErrorCount >= maxRetries){
							logger.log(Level.SEVERE, "Max retries (" + maxRetries + ") exhausted. " + "Discarding "
									+ getPendingTasksSize() + " tasks and shutting down.");
							synchronized(neo4jDbTasksPending){
								neo4jDbTasksPending.clear();
							}
							break;
						}
					}
					HelperFunctions.sleepSafe(sleepWaitMillis);
				}

				if(tx != null){
					try{
						tx.commit();
					}catch(Throwable t){
						logger.log(Level.SEVERE, "Failed to commit data in the buffer", t);
					}finally{
						try{ tx.close(); }catch(Throwable t){}
						tx = null;
					}
				}
			}

			stats.print(true, getPendingTasksSize());

			synchronized(mainThreadRunningLock){
				mainThreadRunning = false;
			}

			logger.log(Level.INFO, "Exited main DB task executor thread");
		}
	};

	// *** START - PUBLIC

	@Override
	public synchronized final boolean shutdown(){
		if(!isShutdown()){
			setShutdown(true);

			logger.log(Level.INFO,
					"Shutdown called. Waiting for " + getPendingTasksSize() + " pending task(s) to complete");
			// wait for main thread to exit
			while(true){
				if(!mainThreadRunning){
					break;
				}
				HelperFunctions.sleepSafe(sleepWaitMillis);
			}
			logger.log(Level.INFO, "Pending tasks going to be discarded: '" + getPendingTasksSize() + "'. Continuing with shutdown ...");

			synchronized(neo4jDbTasksPending){
				neo4jDbTasksPending.clear();
			}
			if(this.dbManagementService != null){
				try{
					this.dbManagementService.shutdown();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to close database service", e);
				}
				this.dbManagementService = null;
			}
			if(this.graphDb != null){
				this.graphDb = null;
			}
			if(this.vertexBloomFilter != null){
				if(this.vertexBloomFilterLoadSavePath != null){
					saveBloomFilter(this.vertexBloomFilter, this.vertexBloomFilterLoadSavePath);
					this.vertexBloomFilterLoadSavePath = null;
				}
				this.vertexBloomFilter = null;
			}
			if(this.edgeBloomFilter != null){
				if(this.edgeBloomFilterLoadSavePath != null){
					saveBloomFilter(this.edgeBloomFilter, this.edgeBloomFilterLoadSavePath);
					this.edgeBloomFilterLoadSavePath = null;
				}
				this.edgeBloomFilter = null;
			}
			if(vertexHashToNodeIdMap != null){
				vertexHashToNodeIdMap.clear();
				vertexHashToNodeIdMap = null;
			}
			if(edgeMap != null){
				edgeMap.clear();
				edgeMap = null;
			}
		}else{
			logger.log(Level.INFO, "Storage already shutdown");
		}
		return true;
	}

	@Override
	public synchronized final boolean initialize(final String arguments){
		if(!initializeConfig(arguments, Settings.getDefaultConfigFilePath(this.getClass()))){
			return false;
		}

		printConfig();

		try{
			debug("Creating/loading database ...");
			DatabaseManagementServiceBuilder dbServiceBuilder = new DatabaseManagementServiceBuilder(
					new File(this.dbHomeDirectoryFile.getAbsolutePath()));
			try{
				if(this.neo4jConfigFilePath != null){
					dbServiceBuilder = dbServiceBuilder.loadPropertiesFromFile(this.neo4jConfigFilePath.getAbsolutePath());
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to load neo4j config file: " + neo4jConfigFilePath.getAbsolutePath(),
						e);
				return false;
			}
			
			try{
				dbServiceBuilder = dbServiceBuilder.setConfig(
						GraphDatabaseSettings.data_directory, java.nio.file.Paths.get(this.dbDataDirectoryName));
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to set neo4j property '"+GraphDatabaseSettings.data_directory.name()+"': " 
						+ this.dbDataDirectoryName, e);
				return false;
			}
			
			final DatabaseManagementService dbManagementService = dbServiceBuilder.build();

			// For SPADE kill!
			Runtime.getRuntime().addShutdownHook(new Thread(){
				@Override
				public void run(){
					if(dbManagementService != null){
						dbManagementService.shutdown();
					}
				}
			});

			try{
				boolean found = false;
				for(String existingDatabase : dbManagementService.listDatabases()){
					if(existingDatabase.equals(this.dbName)){
						found = true;
						break;
					}
				}
				if(!found){
					// Only allowed for enterprise and not community version of neo4j
					// Error would be thrown if not Enterprise
					dbManagementService.createDatabase(this.dbName);
				}
				final GraphDatabaseService graphDb = dbManagementService.database(this.dbName);

				debug("Creating/loading database ... done");

				this.dbManagementService = dbManagementService;
				this.graphDb = graphDb;

				logger.log(Level.INFO, "Database absolute path: " + this.finalConstructedDbPath.getAbsolutePath());

				this.vertexBloomFilterLoadSavePath = this.finalConstructedDbPath.getAbsolutePath() + File.separatorChar
						+ vertexBloomFilterLoadSaveFileName;
				this.edgeBloomFilterLoadSavePath = this.finalConstructedDbPath.getAbsolutePath() + File.separatorChar
						+ edgeBloomFilterLoadSaveFileName;

				try{
					this.vertexBloomFilter = loadBloomFilter(this.vertexBloomFilterLoadSavePath,
							this.vertexBloomFilterFalsePositiveProbability, this.vertexBloomFilterExpectedElements);
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to load vertex bloomfilter", e);
					shutdown();
					return false;
				}

				try{
					this.edgeBloomFilter = loadBloomFilter(this.edgeBloomFilterLoadSavePath,
							this.edgeBloomFilterFalsePositiveProbability, this.edgeBloomFilterExpectedElements);
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to load edge bloomfilter", e);
					shutdown();
					return false;
				}

				vertexHashToNodeIdMap = new LRUCache<String, Long>(vertexCacheMaxSize);
				edgeMap = new LRUCache<String, Boolean>(edgeCacheMaxSize);

				stats = new Neo4jStats(reportingEnabled, reportingIntervalSeconds);

				try{
					Thread thread = new Thread(dbPendingTasksRunner, "db-pending-task-runner");
					thread.start();

					return true; // GOOD to GO
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to start the main thread", e);
					shutdown();
					return false;
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to create database: " + this.finalConstructedDbPath.getAbsolutePath(), e);
				return false;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE,
					"Failed to create database management service: " + this.finalConstructedDbPath.getAbsolutePath(), e);
			return false;
		}
	}

	// start - public
	@Override
	public synchronized final boolean putVertex(final AbstractVertex vertex){
		if(!shutdown && mainThreadRunning){
			appendPendingTask(new Neo4jDbPutVertex(vertex));
		}else{
			debug("Storage already shutdown. Vertex discarded: " + vertex);
		}
		return true;
	}

	@Override
	public synchronized final boolean putEdge(final AbstractEdge edge){
		if(!shutdown && mainThreadRunning){
			appendPendingTask(new Neo4jDbPutEdge(edge));
		}else{
			debug("Storage already shutdown. Edge discarded: " + edge);
		}
		return true;
	}

	@Override
	public QueryInstructionExecutor getQueryInstructionExecutor(){
		synchronized(this){
			if(queryEnvironment == null){
				queryEnvironment = new Neo4jQueryEnvironment(nodePrimaryLabelName, this, edgeSymbolsPropertyName);
				queryEnvironment.initialize();
			}
			if(queryInstructionExecutor == null){
				queryInstructionExecutor = new Neo4jInstructionExecutor(this, queryEnvironment, hashPropertyName);
			}
		}
		return queryInstructionExecutor;
	}
	
	@Override
	public synchronized final Object executeQuery(final String query){
		return executeQueryForSmallResult(query);
	}
	
	public synchronized final List<Map<String, Object>> executeQueryForSmallResult(final String query){
		final Neo4jDbExecuteQuery queryObject = new Neo4jDbExecuteQuery(query);
		executeQueryAndBlockForResult(queryObject);
		return queryObject.getResult();
	}
	
	public synchronized Map<String, Map<String, String>> readHashToVertexMap(String vertexAliasInQuery, String query){
		final Neo4jDbHashToVertexMap queryObject = new Neo4jDbHashToVertexMap(query, vertexAliasInQuery);
		executeQueryAndBlockForResult(queryObject);
		return queryObject.getResult();
	}
	
	public Set<QueriedEdge> readEdgeSet(String relationshipAliasInQuery, String query){
		final Neo4jDbQueriedEdgeSet queryObject = new Neo4jDbQueriedEdgeSet(query, relationshipAliasInQuery);
		executeQueryAndBlockForResult(queryObject);
		return queryObject.getResult();
	}
	
	private synchronized final void executeQueryAndBlockForResult(Neo4jDbTask<?> queryObject){
		if(queryObject == null){
			throw new RuntimeException("NULL query object");
		}
		
		if(!shutdown && mainThreadRunning){
			prependPendingTask(queryObject);
			// Block until complete
			while(!queryObject.isCompleted()){
				if(!mainThreadRunning){
					throw new RuntimeException("Main task executor thread exited. Failed to execute query: " + queryObject);
				}
				HelperFunctions.sleepSafe(sleepWaitMillis);
			}
			if(queryObject.getError() != null){
				throw new RuntimeException(queryObject.getError().getMessage(), queryObject.getError());
			}
		}else{
			throw new RuntimeException("Storage already shutdown. Query failed: " + queryObject);
		}
	}
	
	// *** END - PUBLIC

	// *** START - DB TASKS

	private final void validateUpdateHashKeyAndKeysInAnnotationMap(final Object vertexOrEdgeObject,
			final String objectName, final Map<String, String> annotationsMap) throws Exception{
		for(String key : Arrays.asList(hashPropertyName, edgeSymbolsPropertyName)){
			if(annotationsMap.containsKey(key)){
				switch(hashKeyCollisionAction){
				case DISCARD:
					throw new Exception(objectName + " contains the reserved hash annotation key '" + key
							+ "'. Discarded: " + vertexOrEdgeObject);
				case REMOVE:
					debug("'" + hashPropertyName + "'='" + annotationsMap.remove(key) + "' removed from " + objectName
							+ ": " + vertexOrEdgeObject);
					break;
				default:
					throw new Exception("Unhandled action in case of hash property name collision. Discarded: "
							+ vertexOrEdgeObject);
				}
			}
		}
	}

	// O = transformed result
	private abstract class Neo4jDbTask<O>{
		private final Object completedLock = new Object();
		private volatile boolean completed = false;
		private final Object errorLock = new Object();
		private Throwable error = null;
		public final boolean commitBeforeExecution;
		public final boolean commitAfterExecution;

		public Neo4jDbTask(final boolean commitBeforeExecution, final boolean commitAfterExecution){
			this.commitBeforeExecution = commitBeforeExecution;
			this.commitAfterExecution = commitAfterExecution;
		}

		public final boolean isCompleted(){
			synchronized(completedLock){
				return this.completed;
			}
		}

		public final void completed(){
			synchronized(completedLock){
				this.completed = true;
			}
		}

		public final Throwable getError(){
			synchronized(errorLock){
				return this.error;
			}
		}

		public final void setError(final Throwable error){
			synchronized(errorLock){
				this.error = error;
			}
		}

		// Execute and set result if necessary
		public abstract O execute(final Transaction tx) throws Exception;
	}
	
	private final class Neo4jDbQueriedEdgeSet extends Neo4jDbTask<Set<QueriedEdge>>{
		private final String query;
		private final String relationshipAliasInQuery;
		private Set<QueriedEdge> result = null;
		
		@Override
		public String toString(){
			return "Neo4jDbQueriedEdgeSet [query=" + query + ", relationshipAliasInQuery=" + relationshipAliasInQuery + "]";
		}
		
		private Neo4jDbQueriedEdgeSet(final String query, final String relationshipAliasInQuery){
			super(true, true);
			this.query = query;
			this.relationshipAliasInQuery = relationshipAliasInQuery;
		}
		
		private final void setResult(final Set<QueriedEdge> result){
			this.result = result;
		}
		
		private final Set<QueriedEdge> getResult(){
			return this.result;
		}
		
		@Override
		public final Set<QueriedEdge> execute(final Transaction tx){
			final Set<QueriedEdge> edgeSet = new HashSet<QueriedEdge>();
			
			final long startTime = System.currentTimeMillis();
			org.neo4j.graphdb.Result result = tx.execute(query);
			final long endTime = System.currentTimeMillis();
			debug((endTime - startTime) + " millis taken to execute query '" + query + "'");
			
			Iterator<Relationship> relationships = result.columnAs(relationshipAliasInQuery);
			while(relationships.hasNext()){
				Relationship relationship = relationships.next();
				Object childVertexHashObject = relationship.getStartNode().getProperty(hashPropertyName);
				String childVertexHashString = childVertexHashObject == null ? null
						: childVertexHashObject.toString();
				Object parentVertexHashObject = relationship.getEndNode().getProperty(hashPropertyName);
				String parentVertexHashString = parentVertexHashObject == null ? null
						: parentVertexHashObject.toString();
				Object edgeHashObject = relationship.getProperty(hashPropertyName);
				String edgeHashString = edgeHashObject == null ? null : edgeHashObject.toString();
				Map<String, String> annotations = new HashMap<String, String>();
				for(String key : relationship.getPropertyKeys()){
					if(!HelperFunctions.isNullOrEmpty(key)){
						if(key.equalsIgnoreCase(hashPropertyName)
								|| key.equalsIgnoreCase(edgeSymbolsPropertyName)){
							// ignore
						}else{
							Object annotationValueObject = relationship.getProperty(key);
							String annotationValueString = annotationValueObject == null ? ""
									: annotationValueObject.toString();
							annotations.put(key, annotationValueString);
						}
					}
				}
				edgeSet.add(new QueriedEdge(edgeHashString, childVertexHashString, parentVertexHashString, annotations));
			}
			
			setResult(edgeSet);
			result.close();
			return edgeSet;
		}
	}
	
	private final class Neo4jDbHashToVertexMap extends Neo4jDbTask<Map<String, Map<String, String>>>{
		private final String query;
		private final String vertexAliasInQuery;
		private Map<String, Map<String, String>> result = null;
		
		@Override
		public String toString(){
			return "Neo4jDbHashToVertexMap [query=" + query + ", vertexAliasInQuery=" + vertexAliasInQuery + "]";
		}
		
		private Neo4jDbHashToVertexMap(final String query, final String vertexAliasInQuery){
			super(true, true);
			this.query = query;
			this.vertexAliasInQuery = vertexAliasInQuery;
		}
		
		private final void setResult(final Map<String, Map<String, String>> result){
			this.result = result;
		}
		
		private final Map<String, Map<String, String>> getResult(){
			return this.result;
		}
		
		@Override
		public final Map<String, Map<String, String>> execute(final Transaction tx){
			Map<String, Map<String, String>> hashToVertexAnnotations = new HashMap<String, Map<String, String>>();
			
			final long startTime = System.currentTimeMillis();
			org.neo4j.graphdb.Result result = tx.execute(query);
			final long endTime = System.currentTimeMillis();
			debug((endTime - startTime) + " millis taken to execute query '" + query + "'");
			
			Iterator<Node> nodes = result.columnAs(vertexAliasInQuery);
			while(nodes.hasNext()){
				Node node = nodes.next();
				String hashAnnotationValue = null;
				Map<String, String> annotations = new HashMap<String, String>();
				for(String key : node.getPropertyKeys()){
					if(!HelperFunctions.isNullOrEmpty(key)){
						String annotationValueString = null;
						Object annotationValueObject = node.getProperty(key);
						if(annotationValueObject == null){
							annotationValueString = "";
						}else{
							annotationValueString = annotationValueObject.toString();
						}
						if(hashPropertyName.equals(key)){
							hashAnnotationValue = annotationValueString;
						}else{
							annotations.put(key, annotationValueString);
						}
					}
				}
				hashToVertexAnnotations.put(hashAnnotationValue, annotations);
			}
			
			setResult(hashToVertexAnnotations);
			result.close();
			return hashToVertexAnnotations;
		}
	}

	private final class Neo4jDbExecuteQuery extends Neo4jDbTask<List<Map<String, Object>>>{
		private final String cypherQuery;
		private List<Map<String, Object>> result = null;

		@Override
		public String toString(){
			return "Neo4jDbExecuteQuery [cypherQuery=" + cypherQuery + "]";
		}

		private Neo4jDbExecuteQuery(final String cypherQuery){
			super(true, true);
			this.cypherQuery = cypherQuery;
		}

		private final void setResult(final List<Map<String, Object>> result){
			this.result = result;
		}

		private final List<Map<String, Object>> getResult(){
			return this.result;
		}

		@Override
		public List<Map<String, Object>> execute(final Transaction tx) throws Exception{
			final List<Map<String, Object>> listOfMaps = new ArrayList<Map<String, Object>>();
			final long startTime = System.currentTimeMillis();
			org.neo4j.graphdb.Result result = tx.execute(cypherQuery);
			final long endTime = System.currentTimeMillis();
			debug((endTime - startTime) + " millis taken to execute query '" + cypherQuery + "'");
			while(result.hasNext()){
				listOfMaps.add(new HashMap<String, Object>(result.next()));
			}
			result.close();
			setResult(listOfMaps);
			return listOfMaps;
		}
	}

	private final class Neo4jDbCreateIndex extends Neo4jDbTask<IndexDefinition>{
		private final String key;
		private final boolean forNodes;

		@Override
		public String toString(){
			return "Neo4jDbCreateIndex [key=" + key + ", forNodes=" + forNodes + "]";
		}

		private Neo4jDbCreateIndex(final String key, final boolean forNodes){
			super(true, true);
			this.key = key;
			this.forNodes = forNodes;
		}

		@Override
		public IndexDefinition execute(final Transaction tx) throws Exception{
			try{
				IndexCreator indexCreator = null;
				if(forNodes){
					indexCreator = tx.schema().indexFor(neo4jVertexLabel);
				}else{
					indexCreator = tx.schema().indexFor(neo4jEdgeRelationshipType).withIndexType(IndexType.FULLTEXT);
				}
				indexCreator = indexCreator.on(key);
				return indexCreator.create();
			}catch(Exception e){
				// remove from the main list if failed
				if(forNodes){
					synchronized(nodePropertyNamesIndexed){
						nodePropertyNamesIndexed.remove(key);
					}
				}else{
					synchronized(relationshipPropertyNamesIndexed){
						relationshipPropertyNamesIndexed.remove(key);
					}
				}
				throw e;
			}
		}
	}

	private final class Neo4jDbPutEdge extends Neo4jDbTask<Object>{
		private final AbstractEdge edge;

		@Override
		public String toString(){
			return "Neo4jDbPutEdge [edge=" + edge + "]";
		}

		private Neo4jDbPutEdge(final AbstractEdge edge){
			super(false, false);
			this.edge = edge;
		}

		private final void storeAnnotations(final Transaction tx, final Relationship relationship,
				final Map<String, String> annotations) throws Exception{
			for(final Map.Entry<String, String> entry : annotations.entrySet()){
				final String key = entry.getKey();
				final String value = entry.getValue();
				if(key == null){
					throw new Exception("NULL key in edge: " + edge);
				}
				relationship.setProperty(key, value);
			}
			synchronized(relationshipPropertyNames){
				relationshipPropertyNames.addAll(relationship.getAllProperties().keySet());
			}
			synchronized(relationshipPropertyNamesIndexed){
				final Set<String> notIndexedKeys = new HashSet<String>();
				notIndexedKeys.addAll(relationship.getAllProperties().keySet());
				notIndexedKeys.removeAll(relationshipPropertyNamesIndexed);
				for(final String notIndexedKey : notIndexedKeys){
					relationshipPropertyNamesIndexed.add(notIndexedKey); // add to the main list
					prependCreateIndexPendingTask(false, notIndexedKey);
				}
			}
		}

		private final void storeEdge(final Transaction tx) throws Exception{
			final String hashCode = edge.bigHashCode();
			final Map<String, String> annotations = edge.getCopyOfAnnotations();

			validateUpdateHashKeyAndKeysInAnnotationMap(edge, "Edge", annotations);

			final Node childNode = new Neo4jDbPutVertex(edge.getChildVertex()).execute(tx);
			final Node parentNode = new Neo4jDbPutVertex(edge.getParentVertex()).execute(tx);
			final Relationship relationship = childNode.createRelationshipTo(parentNode, neo4jEdgeRelationshipType);
			relationship.setProperty(hashPropertyName, hashCode);
			storeAnnotations(tx, relationship, annotations);
		}

		@Override
		public final Object execute(final Transaction tx) throws Exception{
			if(edge == null){
				throw new Exception("NULL edge to put");
			}
			final String hashCode = edge.bigHashCode();
			if(hashCode == null){
				throw new Exception("NULL hash code for edge to put: " + edge);
			}

			if(edgeBloomFilter.contains(hashCode)){ // maybe exists
				if(edgeMap.get(hashCode) == null){ // not in cache
					stats.edgeCacheMiss.increment();
					boolean put = false;
					final Node childNode = new Neo4jDbPutVertex(edge.getChildVertex()).getNodeIfExists(tx);
					if(childNode == null){
						put = true;
					}else{
						final Node parentNode = new Neo4jDbPutVertex(edge.getParentVertex()).getNodeIfExists(tx);
						if(parentNode == null){
							put = true;
						}else{
							Relationship found = null;
							final Iterable<Relationship> relationshipsIterable = childNode
									.getRelationships(Direction.OUTGOING, neo4jEdgeRelationshipType);
							final Iterator<Relationship> relationships = relationshipsIterable.iterator();
							while(relationships.hasNext()){
								final Relationship relationship = relationships.next();
								if(relationship != null){
									if(hashCode.equals(relationship.getProperty(hashPropertyName))){
										found = relationship;
										break;
									}
								}
							}
							if(found == null){
								put = true;
							}else{
								put = false;
							}
						}
					}
					if(put){
						// store edge
						stats.edgeBloomFilterFalsePositive.increment();
						storeEdge(tx);
					}else{
						// not storing because found in db
						stats.edgeDbHit.increment();
					}
					edgeMap.put(hashCode, true);
				}else{ // exists in cache
						// No need to put
					stats.edgeCacheHit.increment();
					return null;
				}
			}else{ // doesn't exist
					// store edge
				stats.edgeCount.increment();
				storeEdge(tx);
				edgeBloomFilter.add(hashCode);
				edgeMap.put(hashCode, true);
				return null;
			}
			return null;
		}
	}

	private final class Neo4jDbPutVertex extends Neo4jDbTask<Node>{
		private final AbstractVertex vertex;

		@Override
		public String toString(){
			return "Neo4jDbPutVertex [vertex=" + vertex + "]";
		}

		private Neo4jDbPutVertex(final AbstractVertex vertex){
			super(false, false);
			this.vertex = vertex;
		}

		private final void storeAnnotations(final Transaction tx, final Node node,
				final Map<String, String> annotations) throws Exception{
			for(final Map.Entry<String, String> entry : annotations.entrySet()){
				final String key = entry.getKey();
				final String value = entry.getValue();
				if(key == null){
					throw new Exception("NULL key in vertex: " + vertex);
				}
				node.setProperty(key, value);
			}
			synchronized(nodePropertyNames){
				nodePropertyNames.addAll(node.getAllProperties().keySet());
			}
			synchronized(nodePropertyNamesIndexed){
				final Set<String> notIndexedKeys = new HashSet<String>();
				notIndexedKeys.addAll(node.getAllProperties().keySet());
				notIndexedKeys.removeAll(nodePropertyNamesIndexed);
				for(final String notIndexedKey : notIndexedKeys){
					nodePropertyNamesIndexed.add(notIndexedKey); // add to the main list
					prependCreateIndexPendingTask(true, notIndexedKey);
				}
			}
		}

		private final Node storeVertex(final Transaction tx, final String hashCode, final AbstractVertex vertex)
				throws Exception{
			final Map<String, String> annotations = vertex.getCopyOfAnnotations();
			validateUpdateHashKeyAndKeysInAnnotationMap(vertex, "Vertex", annotations);

			final Node node = tx.createNode(neo4jVertexLabel);
			node.setProperty(hashPropertyName, hashCode);
			storeAnnotations(tx, node, annotations);
			return node;
		}

		private final boolean updateNodeConditionally(final Transaction tx, final Node node,
				final AbstractVertex vertex) throws Exception{
			final Map<String, String> annotations = vertex.getCopyOfAnnotations();
			validateUpdateHashKeyAndKeysInAnnotationMap(vertex, "Vertex", annotations);
			final Map<String, Object> nodeProperties = node.getAllProperties();
			boolean update;
			if(nodeProperties == null){
				if(annotations.size() > 0){
					update = true;
				}else{
					update = false;
				}
			}else{
				final Set<String> annotationKeys = annotations.keySet();
				annotationKeys.removeAll(nodeProperties.keySet());
				if(annotationKeys.size() > 0){
					update = true;
				}else{
					update = false;
				}
			}
			if(update){
				storeAnnotations(tx, node, annotations);
			}
			return update;
		}

		public final Node getNodeIfExists(final Transaction tx) throws Exception{
			if(vertex == null){
				throw new Exception("NULL vertex");
			}
			final String hashCode = vertex.bigHashCode();
			if(hashCode == null){
				throw new Exception("NULL hash for vertex: " + vertex);
			}
			if(vertexBloomFilter.contains(hashCode)){ // maybe exists
				Node node = null;
				final Long nodeId = vertexHashToNodeIdMap.get(hashCode);
				if(nodeId == null){ // not in cache
					stats.vertexCacheMiss.increment();
					node = tx.findNode(neo4jVertexLabel, hashPropertyName, hashCode);
					if(node == null){ // doesn't exist in db
						stats.vertexBloomFilterFalsePositive.increment();
						return null;
					}else{ // exists in db
						stats.vertexDbHit.increment();
						vertexHashToNodeIdMap.put(hashCode, node.getId());
						return node;
					}
				}else{ // exists in cache
					stats.vertexCacheHit.increment();
					node = tx.getNodeById(nodeId);
					if(node == null){ // Something is wrong. Exists in cache but not in the db!
						debug("Vertex existed in cache with id '" + nodeId + "' but not in db. Vertex: " + vertex);
						return null;
					}else{
						return node;
					}
				}
			}else{
				return null;
			}
		}

		@Override
		public final Node execute(final Transaction tx) throws Exception{
			if(vertex == null){
				throw new Exception("NULL vertex to put");
			}
			final String hashCode = vertex.bigHashCode();
			if(hashCode == null){
				throw new Exception("NULL hash code for vertex to put: " + vertex);
			}
			final boolean isReferenceVertex = vertex.isReferenceVertex();
			Node node = null;
			if(vertexBloomFilter.contains(hashCode)){ // maybe exists
				final Long nodeId = vertexHashToNodeIdMap.get(hashCode);
				if(nodeId == null){ // not in cache
					stats.vertexCacheMiss.increment();
					node = tx.findNode(neo4jVertexLabel, hashPropertyName, hashCode);
					if(node == null){ // doesn't exist in db
						stats.vertexBloomFilterFalsePositive.increment();
						node = storeVertex(tx, hashCode, vertex);
					}else{ // exists in db
						stats.vertexDbHit.increment();
						if(!isReferenceVertex){
							updateNodeConditionally(tx, node, vertex);
						}
					}
					vertexHashToNodeIdMap.put(hashCode, node.getId());
				}else{ // exists in cache
					stats.vertexCacheHit.increment();
					node = tx.getNodeById(nodeId);
					if(node == null){ // Something is wrong. Exists in cache but not in the db!
						debug("Vertex existed in cache with id '" + nodeId + "' but not in db. Vertex: " + vertex);
						node = storeVertex(tx, hashCode, vertex);
						vertexHashToNodeIdMap.put(hashCode, node.getId());
					}else{
						if(!isReferenceVertex){
							updateNodeConditionally(tx, node, vertex);
						}
					}
				}
			}else{ // definitely doesn't exist
				stats.vertexCount.increment();
				node = storeVertex(tx, hashCode, vertex);
				vertexBloomFilter.add(hashCode);
				vertexHashToNodeIdMap.put(hashCode, node.getId());
			}
			return node;
		}
	}

	// *** END - DB TASKS

	// *** START - configuration setup

	private final void debug(String msg){
		if(debug){
			logger.log(Level.WARNING, "DEBUG::" + msg);
		}
	}

	private final Result<File> parseOptionalReadableFile(final Map<String, String> map, final String key){
		final String pathString = map.remove(key);
		if(HelperFunctions.isNullOrEmpty(pathString)){
			return Result.successful(null);
		}else{
			final File file = new File(pathString);
			try{
				if(file.exists()){
					if(!file.isFile()){
						return Result.failed(
								"The path for key '" + key + "' is not a file: '" + file.getAbsolutePath() + "'");
					}else{
						if(!file.canRead()){
							return Result.failed("The path for key '" + key + "' is not a readable file: '"
									+ file.getAbsolutePath() + "'");
						}
					}
				}else{
					return Result
							.failed("The path for key '" + key + "' does not exist: '" + file.getAbsolutePath() + "'");
				}
			}catch(Exception e){
				return Result.failed(
						"Failed to validate file path for key '" + key + "': '" + file.getAbsolutePath() + "'", e,
						null);
			}
			return Result.successful(file);
		}
	}

	private final void printConfig(){
		String configString = 
				"Arguments: " 
				+ keySleepWaitMillis + "=" + sleepWaitMillis
				+ ", " + keyWaitForIndexSeconds + "=" + waitForIndexSeconds
				+ ", " + keyDebug + "=" + debug
				+ ", " + keyReportingIntervalSeconds + "=" + reportingIntervalSeconds
				+ ", " + keyHashPropertyName + "=" + hashPropertyName
				+ ", " + keyEdgeSymbolsPropertyName + "=" + edgeSymbolsPropertyName
				+ ", " + keyHashKeyCollisionAction + "=" + hashKeyCollisionAction
				+ ", " + keyMaxRetries + "=" + maxRetries
				+ ", " + keyNodePrimaryLabel + "=" + nodePrimaryLabelName
				+ ", " + keyEdgeRelationshipType + "=" + edgeRelationshipTypeName
				+ ", " + keyFlushBufferSize + "=" + flushBufferSize
				+ ", " + keyFlushAfterSeconds + "=" + flushAfterSeconds
				+ ", " + keyBufferLimit + "=" + bufferLimit + "(buffering:" + ((bufferLimit < 0) ? ("disabled") : ("enabled") )+ ")"
				+ ", " + keyNeo4jConfigFilePath + "=" + (neo4jConfigFilePath == null ? "null" : neo4jConfigFilePath.getAbsolutePath())
				+ ", " + keyVertexBloomFilterFalsePositiveProbability + "=" + String.format("%f", vertexBloomFilterFalsePositiveProbability)
				+ ", " + keyVertexBloomFilterExpectedElements + "=" + vertexBloomFilterExpectedElements
				+ ", " + keyVertexCacheMaxSize + "=" + vertexCacheMaxSize
				+ ", " + keyEdgeBloomFilterFalsePositiveProbability + "=" + String.format("%f", edgeBloomFilterFalsePositiveProbability)
				+ ", " + keyEdgeBloomFilterExpectedElements + "=" + edgeBloomFilterExpectedElements
				+ ", " + keyEdgeCacheMaxSize + "=" + edgeCacheMaxSize
				+ ", " + keyIndexVertex + "=" + indexVertexMode
				+ ", " + keyIndexEdge + "=" + indexEdgeMode
				+ ", " + keyDbHomeDirectoryPath + "=" + dbHomeDirectoryFile.getAbsolutePath()
				+ ", " + keyDbDataDirectoryName + "=" + dbDataDirectoryName
				+ ", " + keyDbName + "=" + dbName;
		logger.log(Level.INFO, configString);
	}

	private final boolean initializeConfig(final String arguments, final String configFilePath){
		// NOTE: Remove keys from map because at the end a warning is printed if the map
		// wasn't empty

		final Map<String, String> map = new HashMap<String, String>();
		try{
			final Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
			map.putAll(configMap);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file: " + configFilePath, e);
			return false;
		}
		map.putAll(HelperFunctions.parseKeyValPairs(arguments));

		final String sleepWaitMillisString = map.remove(keySleepWaitMillis);
		final Result<Long> sleepWaitMillisResult = HelperFunctions.parseLong(sleepWaitMillisString, 10, 1,
				Long.MAX_VALUE);
		if(sleepWaitMillisResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keySleepWaitMillis + "': " + sleepWaitMillisResult.toErrorString());
			return false;
		}
		this.sleepWaitMillis = sleepWaitMillisResult.result;

		final String waitForIndexSecondsString = map.remove(keyWaitForIndexSeconds);
		final Result<Long> waitForIndexSecondsResult = HelperFunctions.parseLong(waitForIndexSecondsString, 10, 0,
				Integer.MAX_VALUE);
		if(waitForIndexSecondsResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyWaitForIndexSeconds + "': " + waitForIndexSecondsResult.toErrorString());
			return false;
		}
		this.waitForIndexSeconds = waitForIndexSecondsResult.result.intValue();

		final String debugString = map.remove(keyDebug);
		final Result<Boolean> debugResult = HelperFunctions.parseBoolean(debugString);
		if(debugResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyDebug + "': " + debugResult.toErrorString());
			return false;
		}
		this.debug = debugResult.result;
		
		final String forceShutdownString = map.remove(keyForceShutdown);
		final Result<Boolean> forceShutdownResult = HelperFunctions.parseBoolean(forceShutdownString);
		if(forceShutdownResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyForceShutdown + "': " + forceShutdownResult.toErrorString());
			return false;
		}
		this.forceShutdown = forceShutdownResult.result;
		

		final String reportingIntervalSecondsString = map.remove(keyReportingIntervalSeconds);
		final Result<Long> reportingIntervalSecondsResult = HelperFunctions.parseLong(reportingIntervalSecondsString,
				10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(reportingIntervalSecondsResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyReportingIntervalSeconds + "': "
					+ reportingIntervalSecondsResult.toErrorString());
			return false;
		}
		this.reportingIntervalSeconds = reportingIntervalSecondsResult.result.intValue();
		if(this.reportingIntervalSeconds > 0){
			this.reportingEnabled = true;
		}else{
			this.reportingEnabled = false;
		}

		final String hashPropertyNameString = map.remove(keyHashPropertyName);
		if(HelperFunctions.isNullOrEmpty(hashPropertyNameString)){
			logger.log(Level.SEVERE, "NULL/Empty value for '" + keyHashPropertyName + "': " + hashPropertyNameString);
			return false;
		}
		this.hashPropertyName = hashPropertyNameString.trim();

		final String edgeSymbolsPropertyNameString = map.remove(keyEdgeSymbolsPropertyName);
		if(HelperFunctions.isNullOrEmpty(edgeSymbolsPropertyNameString)){
			logger.log(Level.SEVERE,
					"NULL/Empty value for '" + keyEdgeSymbolsPropertyName + "': " + edgeSymbolsPropertyNameString);
			return false;
		}
		this.edgeSymbolsPropertyName = edgeSymbolsPropertyNameString.trim();

		final String hashKeyCollisionActionString = map.remove(keyHashKeyCollisionAction);
		final Result<HashKeyCollisionAction> hashKeyCollisionActionResult = HelperFunctions
				.parseEnumValue(HashKeyCollisionAction.class, hashKeyCollisionActionString, true);
		if(hashKeyCollisionActionResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyHashKeyCollisionAction + "': " + hashKeyCollisionActionString);
			return false;
		}
		this.hashKeyCollisionAction = hashKeyCollisionActionResult.result;

		final String maxRetriesString = map.remove(keyMaxRetries);
		final Result<Long> maxRetriesResult = HelperFunctions.parseLong(maxRetriesString, 10, 0, Integer.MAX_VALUE);
		if(maxRetriesResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyMaxRetries + "': " + maxRetriesResult.toErrorString());
			return false;
		}
		this.maxRetries = maxRetriesResult.result.intValue();

		final String nodePrimaryLabelNameString = map.remove(keyNodePrimaryLabel);
		if(HelperFunctions.isNullOrEmpty(nodePrimaryLabelNameString)){
			logger.log(Level.SEVERE,
					"NULL/Empty value for '" + keyNodePrimaryLabel + "': " + nodePrimaryLabelNameString);
			return false;
		}
		this.nodePrimaryLabelName = nodePrimaryLabelNameString.trim();
		this.neo4jVertexLabel = Label.label(this.nodePrimaryLabelName);

		final String edgeRelationshipTypeString = map.remove(keyEdgeRelationshipType);
		if(HelperFunctions.isNullOrEmpty(edgeRelationshipTypeString)){
			logger.log(Level.SEVERE,
					"NULL/Empty value for '" + keyEdgeRelationshipType + "': " + edgeRelationshipTypeString);
			return false;
		}
		this.edgeRelationshipTypeName = edgeRelationshipTypeString.trim();
		this.neo4jEdgeRelationshipType = RelationshipType.withName(this.edgeRelationshipTypeName);

		final String flushBufferSizeString = map.remove(keyFlushBufferSize);
		final Result<Long> flushBufferSizeResult = HelperFunctions.parseLong(flushBufferSizeString, 10, 0,
				Integer.MAX_VALUE);
		if(flushBufferSizeResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyFlushBufferSize + "': " + flushBufferSizeResult.toErrorString());
			return false;
		}
		this.flushBufferSize = flushBufferSizeResult.result.intValue();

		final String flushAfterSecondsString = map.remove(keyFlushAfterSeconds);
		final Result<Long> flushAfterSecondsResult = HelperFunctions.parseLong(flushAfterSecondsString, 10, 0,
				Integer.MAX_VALUE);
		if(flushAfterSecondsResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyFlushAfterSeconds + "': " + flushAfterSecondsResult.toErrorString());
			return false;
		}
		this.flushAfterSeconds = flushAfterSecondsResult.result.intValue();

		final String bufferLimitString = map.remove(keyBufferLimit);
		final Result<Long> bufferLimitResult = HelperFunctions.parseLong(bufferLimitString, 10, Integer.MIN_VALUE,
				Integer.MAX_VALUE);
		if(bufferLimitResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyBufferLimit + "': " + bufferLimitResult.toErrorString());
			return false;
		}
		this.bufferLimit = bufferLimitResult.result.intValue();

		final String dbNameString = map.remove(keyDbName);
		if(HelperFunctions.isNullOrEmpty(dbNameString)){
			logger.log(Level.INFO, "NULL/Empty value for '" + keyDbName + "': " + dbNameString + ".");
			return false;
		}
		this.dbName = dbNameString.trim();
		
		final String dbDataDirectoryNameString = map.remove(keyDbDataDirectoryName);
		if(HelperFunctions.isNullOrEmpty(dbDataDirectoryNameString)){
//			logger.log(Level.INFO, "NULL/Empty value for '" + keyDbDataDirectoryName + "': " + dbDataDirectoryNameString + ".");
//			return false;
			this.dbDataDirectoryName = GraphDatabaseSettings.DEFAULT_DATA_DIR_NAME;
		}else{
			this.dbDataDirectoryName = dbDataDirectoryNameString.trim();
		}

		final String dbHomeDirectoryPathString = map.remove(keyDbHomeDirectoryPath);
		if(HelperFunctions.isNullOrEmpty(dbHomeDirectoryPathString)){
//			logger.log(Level.SEVERE, "NULL/Empty value for '" + keyDbHomeDirectoryPath + "': " + dbHomeDirectoryPathString + ".");
//			return false;
			this.dbHomeDirectoryFile = new File("");
		}else{
			this.dbHomeDirectoryFile = new File(dbHomeDirectoryPathString.trim());
		}
		try{
			if(this.dbHomeDirectoryFile.exists()){
				if(!this.dbHomeDirectoryFile.isDirectory()){
					logger.log(Level.SEVERE, "Path for key '" + keyDbHomeDirectoryPath
							+ "' exists but is not a directory: '" + this.dbHomeDirectoryFile.getAbsolutePath() + "'");
					return false;
				}else{
					if(!this.dbHomeDirectoryFile.canWrite()){
						logger.log(Level.SEVERE,
								"Path for key '" + keyDbHomeDirectoryPath + "' must be a writable directory: '"
										+ this.dbHomeDirectoryFile.getAbsolutePath() + "'");
						return false;
					}
					if(!this.dbHomeDirectoryFile.canRead()){
						logger.log(Level.SEVERE,
								"Path for key '" + keyDbHomeDirectoryPath + "' must be a readable directory: '"
										+ this.dbHomeDirectoryFile.getAbsolutePath() + "'");
						return false;
					}
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to validate directory path for key '" + keyDbHomeDirectoryPath + "': '"
					+ this.dbHomeDirectoryFile.getAbsolutePath() + "'", e);
			return false;
		}
		
		try{
			this.finalConstructedDbPath = new File(this.dbHomeDirectoryFile.getAbsolutePath()
					+ File.separatorChar + this.dbDataDirectoryName
					+ File.separatorChar + "databases"
					+ File.separatorChar + this.dbName);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to construct database path", e);
			return false;
		}

		final Result<File> neo4jConfigFilePathResult = parseOptionalReadableFile(map, keyNeo4jConfigFilePath);
		if(neo4jConfigFilePathResult.error){
			logger.log(Level.SEVERE, neo4jConfigFilePathResult.toErrorString());
			return false;
		}
		this.neo4jConfigFilePath = neo4jConfigFilePathResult.result;

		final String vertexBloomFilterFalsePositiveProbabilityString = map
				.remove(keyVertexBloomFilterFalsePositiveProbability);
		final Result<Double> vertexBloomFilterFalsePositiveProbabilityResult = HelperFunctions
				.parseDouble(vertexBloomFilterFalsePositiveProbabilityString, 0, 1);
		if(vertexBloomFilterFalsePositiveProbabilityResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyVertexBloomFilterFalsePositiveProbability + "': "
					+ vertexBloomFilterFalsePositiveProbabilityResult.toErrorString());
			return false;
		}
		this.vertexBloomFilterFalsePositiveProbability = vertexBloomFilterFalsePositiveProbabilityResult.result;

		final String vertexBloomFilterExpectedElementsString = map.remove(keyVertexBloomFilterExpectedElements);
		final Result<Long> vertexBloomFilterExpectedElementsResult = HelperFunctions
				.parseLong(vertexBloomFilterExpectedElementsString, 10, 1, Integer.MAX_VALUE);
		if(vertexBloomFilterExpectedElementsResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyVertexBloomFilterExpectedElements + "': "
					+ vertexBloomFilterExpectedElementsResult.toErrorString());
			return false;
		}
		this.vertexBloomFilterExpectedElements = vertexBloomFilterExpectedElementsResult.result.intValue();

		final String vertexCacheMaxSizeString = map.remove(keyVertexCacheMaxSize);
		final Result<Long> vertexCacheMaxSizeResult = HelperFunctions.parseLong(vertexCacheMaxSizeString, 10, 0,
				Integer.MAX_VALUE);
		if(vertexCacheMaxSizeResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyVertexCacheMaxSize + "': " + vertexCacheMaxSizeResult.toErrorString());
			return false;
		}
		this.vertexCacheMaxSize = vertexCacheMaxSizeResult.result.intValue();

		final String edgeBloomFilterFalsePositiveProbabilityString = map
				.remove(keyEdgeBloomFilterFalsePositiveProbability);
		final Result<Double> edgeBloomFilterFalsePositiveProbabilityResult = HelperFunctions
				.parseDouble(edgeBloomFilterFalsePositiveProbabilityString, 0, 1);
		if(edgeBloomFilterFalsePositiveProbabilityResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyEdgeBloomFilterFalsePositiveProbability + "': "
					+ edgeBloomFilterFalsePositiveProbabilityResult.toErrorString());
			return false;
		}
		this.edgeBloomFilterFalsePositiveProbability = edgeBloomFilterFalsePositiveProbabilityResult.result;

		final String edgeBloomFilterExpectedElementsString = map.remove(keyEdgeBloomFilterExpectedElements);
		final Result<Long> edgeBloomFilterExpectedElementsResult = HelperFunctions
				.parseLong(edgeBloomFilterExpectedElementsString, 10, 1, Integer.MAX_VALUE);
		if(edgeBloomFilterExpectedElementsResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyEdgeBloomFilterExpectedElements + "': "
					+ edgeBloomFilterExpectedElementsResult.toErrorString());
			return false;
		}
		this.edgeBloomFilterExpectedElements = edgeBloomFilterExpectedElementsResult.result.intValue();

		final String edgeCacheMaxSizeString = map.remove(keyEdgeCacheMaxSize);
		final Result<Long> edgeCacheMaxSizeResult = HelperFunctions.parseLong(edgeCacheMaxSizeString, 10, 0,
				Integer.MAX_VALUE);
		if(edgeCacheMaxSizeResult.error){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyEdgeCacheMaxSize + "': " + edgeCacheMaxSizeResult.toErrorString());
			return false;
		}
		this.edgeCacheMaxSize = edgeCacheMaxSizeResult.result.intValue();

		final String indexVertexModeString = map.remove(keyIndexVertex);
		final Result<IndexMode> indexVertexModeResult = HelperFunctions.parseEnumValue(IndexMode.class,
				indexVertexModeString, true);
		if(indexVertexModeResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyIndexVertex + "': " + indexVertexModeString);
			return false;
		}
		this.indexVertexMode = indexVertexModeResult.result;

		final String indexEdgeModeString = map.remove(keyIndexEdge);
		final Result<IndexMode> indexEdgeModeResult = HelperFunctions.parseEnumValue(IndexMode.class,
				indexEdgeModeString, true);
		if(indexEdgeModeResult.error){
			logger.log(Level.SEVERE, "Invalid value for '" + keyIndexEdge + "': " + indexEdgeModeString);
			return false;
		}
		this.indexEdgeMode = indexEdgeModeResult.result;

		/*
		 * final String indexVertexKeysUserString = map.remove(keyIndexVertexUser);
		 * if(this.indexVertexMode.equals(IndexMode.USER)){
		 * if(HelperFunctions.isNullOrEmpty(indexVertexKeysUserString)){
		 * this.indexVertexKeysUser = null; }else{ this.indexVertexKeysUser =
		 * indexVertexKeysUserString.split(","); for(int i = 0; i <
		 * indexVertexKeysUser.length; i++){ indexVertexKeysUser[i] =
		 * indexVertexKeysUser[i].trim(); if(indexVertexKeysUser[i].isBlank()){
		 * logger.log(Level.SEVERE,
		 * "Blank values not allowed for key '"+keyIndexVertexUser+"': " +
		 * indexVertexKeysUserString + "'"); return false; } } } }
		 * 
		 * final String indexEdgeKeysUserString = map.remove(keyIndexEdgeUser);
		 * if(this.indexEdgeMode.equals(IndexMode.USER)){
		 * if(HelperFunctions.isNullOrEmpty(indexEdgeKeysUserString)){
		 * this.indexEdgeKeysUser = null; }else{ this.indexEdgeKeysUser =
		 * indexEdgeKeysUserString.split(","); for(int i = 0; i <
		 * indexEdgeKeysUser.length; i++){ indexEdgeKeysUser[i] =
		 * indexEdgeKeysUser[i].trim(); if(indexEdgeKeysUser[i].isBlank()){
		 * logger.log(Level.SEVERE,
		 * "Blank values not allowed for key '"+keyIndexEdgeUser+"': " +
		 * indexEdgeKeysUserString + "'"); return false; } } } }
		 * 
		 * if(this.indexVertexMode.equals(IndexMode.USER) && (this.indexVertexKeysUser
		 * == null || this.indexVertexKeysUser.length == 0)){ logger.log(Level.SEVERE,
		 * "Must specify non-null/empty '"+keyIndexVertexUser+"' value if '"
		 * +keyIndexVertex+"'='"+this.indexVertexMode+"'"); return false; }
		 * 
		 * if(this.indexEdgeMode.equals(IndexMode.USER) && (this.indexEdgeKeysUser ==
		 * null || this.indexEdgeKeysUser.length == 0)){ logger.log(Level.SEVERE,
		 * "Must specify non-null/empty '"+keyIndexEdgeUser+"' value if '"+keyIndexEdge+
		 * "'='"+this.indexEdgeMode+"'"); return false; }
		 */

		if(map.size() > 0){
			logger.log(Level.WARNING, "Ignored unexpected argument(s): " + map);
		}

		return true;
	}

	private final void saveBloomFilter(final BloomFilter<String> bloomFilter, final String bloomFilterPath){
		try(final ObjectOutputStream objectOutputStream = new ObjectOutputStream(
				new FileOutputStream(bloomFilterPath))){
			objectOutputStream.writeObject(bloomFilter);
			logger.log(Level.INFO, "Bloomfilter saved to path: " + bloomFilterPath);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to save bloomfilter at path: " + bloomFilterPath, e);
		}
	}

	private final BloomFilter<String> loadBloomFilter(final String bloomFilterPath,
			final double bloomFilterFalsePositiveProbability, final int bloomfilterExpectedElements) throws Exception{
		if(bloomFilterPath == null){
			throw new Exception("NULL path");
		}else{
			// 1) Try loading from the file
			// 2) Error if the path exists but is not readable or writable
			// 3) If path does not exist then create from arguments
			// 4) Try loading bloomfilter from file
			// 5) If successful in loading from file then all well and good
			// 6) If null object read from the file then load from arguments
			boolean loadFromFile = false;
			final File file = new File(bloomFilterPath);
			try{
				if(!file.exists()){
					loadFromFile = false;
				}else{
					if(!file.canRead()){
						throw new Exception("Not readable");
					}
					if(!file.canWrite()){
						throw new Exception("Not writable");
					}
					loadFromFile = true;
				}
			}catch(Exception e){
				throw new Exception("Failed to validate path: " + bloomFilterPath, e);
			}
			if(loadFromFile){
				try(final ObjectInputStream objectInputStream = new ObjectInputStream(
						new FileInputStream(file.getAbsolutePath()))){
					debug("Reading bloomfilter from file " + file.getAbsolutePath() + " ...");
					@SuppressWarnings("unchecked")
					final BloomFilter<String> bloomFilter = (BloomFilter<String>)objectInputStream.readObject();
					if(bloomFilter != null){
						logger.log(Level.INFO,
								"Bloomfilter initialized from file: " + bloomFilterPath + "[falsePositiveProbability="
										+ bloomFilter.getFalsePositiveProbability() + ", " + "expectedElements="
										+ bloomFilter.getExpectedBitsPerElement() + "]");
						return bloomFilter;
					}else{
						// Fall back to creating from arguments
					}
				}catch(Exception e){
					throw new Exception("Invalid bloomfilter file format: " + bloomFilterPath, e);
				}
			}
			final BloomFilter<String> bloomFilter = new BloomFilter<String>(bloomFilterFalsePositiveProbability,
					bloomfilterExpectedElements);
			logger.log(Level.INFO,
					"Bloomfilter initialized from arguments: " + "[falsePositiveProbability="
							+ String.format("%f", bloomFilter.getFalsePositiveProbability()) + ", "
							+ "expectedElements=" + bloomFilter.getExpectedNumberOfElements() + "]");
			return bloomFilter;
		}
	}

	// *** END - configuration setup
	
	public static void main(String[] args) throws Exception{
		Neo4j s = new Neo4j();
		s.initialize(null);

		final List<AbstractVertex> vertices = new ArrayList<AbstractVertex>();
		for(int i = 0; i < 500; i++){
			final String hash = "vhash:" + String.valueOf(i);
			final AbstractVertex v = new Vertex(hash);
			vertices.add(v);
			s.putVertex(v);
			HelperFunctions.sleepSafe(500);
		}

		try{
			System.out.println(s.executeQuery("match (v) return count(v);"));
		}catch(Exception e){
			e.printStackTrace();
		}
		try{
			System.out.println(s.executeQuery("match ()-[e]->() return count(e);"));
		}catch(Exception e){
			e.printStackTrace();
		}

		for(int i = 0; i < 500 - 1; i++){
			final String hash = "ehash:" + String.valueOf(i);
			AbstractEdge edge = new Edge(vertices.get(i), vertices.get(i + 1));
			edge.addAnnotation("abc", hash);
			s.putEdge(edge);
			HelperFunctions.sleepSafe(500);
		}
		HelperFunctions.sleepSafe(5000);

		try{
			System.out.println(s.executeQuery("match (v) return count(v);"));
		}catch(Exception e){
			e.printStackTrace();
		}

		try{
			System.out.println(s.executeQuery("match ()-[e]->() return count(e);"));
		}catch(Exception e){
			e.printStackTrace();
		}

		try{
			System.out.println(s.executeQuery("call db.indexes;"));
		}catch(Exception e){
			e.printStackTrace();
		}

		s.shutdown();

		System.exit(0);

		// Stats
		
		/*
		 * with index (vertex) put time = 255 + 237 + 253 ms commit time = 241 + 279
		 * 227 ms Count = [{count(v)=10000}]
		 */

		/*
		 * without index (vertex) put time = 185 + 238 + 241 ms commit time = 172 + 158
		 * + 159 ms Count = [{count(v)=10000}]
		 */

	}
}

class Neo4jStat{
	private final String name;
	private long valueSinceEpoch = 0;
	private long valueSinceLastInterval = 0;

	Neo4jStat(final String name){
		this.name = name;
	}

	synchronized final void increment(){
		this.valueSinceLastInterval++;
		this.valueSinceEpoch++;
	}

	synchronized final void newInterval(){
		this.valueSinceLastInterval = 0;
	}

	synchronized final double currentIntervalRatePerMin(long elapsedTimeMillis){
		return getRatePerMin(this.valueSinceLastInterval, elapsedTimeMillis);
	}

	synchronized final double currentOverallRatePerMin(long elapsedTimeMillis){
		return getRatePerMin(this.valueSinceEpoch, elapsedTimeMillis);
	}

	synchronized String format(final long elapsedTimeSinceStartMillis, final long elapsedTimeSinceIntervalMillis){
		return String.format("Rate of %s per minute [Overall=%.3f; Interval=%.3f]", this.name,
				currentOverallRatePerMin(elapsedTimeSinceStartMillis),
				currentIntervalRatePerMin(elapsedTimeSinceIntervalMillis));
	}

	synchronized final double getRatePerMin(long value, long elapsedTimeMillis){
		final double minutes = ((((double)elapsedTimeMillis) / 1000.0) / 60.0);
		if(minutes <= 0){
			return Double.NaN;
		}else{
			return ((double)value) / minutes;
		}
	}
}

class Neo4jStats{

	final Logger logger = Logger.getLogger(Neo4jStats.class.getName());

	final Neo4jStat vertexCount = new Neo4jStat("Vertices");
	final Neo4jStat vertexBloomFilterFalsePositive = new Neo4jStat("Vertex BloomFilter False Positive");
	final Neo4jStat vertexCacheMiss = new Neo4jStat("Vertex Cache Miss");
	final Neo4jStat vertexCacheHit = new Neo4jStat("Vertex Cache Hit");
	final Neo4jStat vertexDbHit = new Neo4jStat("Vertex DB Hit");

	final Neo4jStat edgeCount = new Neo4jStat("Edges");
	final Neo4jStat edgeBloomFilterFalsePositive = new Neo4jStat("Edge BloomFilter False Positive");
	final Neo4jStat edgeCacheMiss = new Neo4jStat("Edge Cache Miss");
	final Neo4jStat edgeCacheHit = new Neo4jStat("Edge Cache Hit");
	final Neo4jStat edgeDbHit = new Neo4jStat("Edge DB Hit");

	final Neo4jStat pendingTasksIncoming = new Neo4jStat("Pending Tasks Incoming");
	final Neo4jStat pendingTasksOutgoing = new Neo4jStat("Pending Tasks Outgoing");

	final boolean reportingEnabled;
	final long reportingStartedAtMillis;
	final long reportingIntervalMillis;
	long lastReportedAtMillis;
	
	long intervalNumber = 0;

	Neo4jStats(final boolean reportingEnabled, final int reportingIntervalSeconds){
		this.reportingEnabled = reportingEnabled;
		this.reportingIntervalMillis = reportingIntervalSeconds * 1000;
		this.reportingStartedAtMillis = System.currentTimeMillis();
		this.lastReportedAtMillis = System.currentTimeMillis();
	}

	final void print(final boolean force, final int pendingTasks){
		if(reportingEnabled || force){
			final long elapsedTimeSinceStartMillis = System.currentTimeMillis() - reportingStartedAtMillis;
			final long elapsedTimeSinceIntervalMillis = System.currentTimeMillis() - lastReportedAtMillis;
			if(elapsedTimeSinceIntervalMillis >= reportingIntervalMillis || force){
				intervalNumber++;
				logger.log(Level.INFO, "Neo4j STATS Start [interval="+intervalNumber+"]");
				
				logger.log(Level.INFO, vertexCount.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO, vertexBloomFilterFalsePositive.format(elapsedTimeSinceStartMillis,
						elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						vertexCacheMiss.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						vertexCacheHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO, vertexDbHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));

				logger.log(Level.INFO, edgeCount.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO, edgeBloomFilterFalsePositive.format(elapsedTimeSinceStartMillis,
						elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						edgeCacheMiss.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						edgeCacheHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO, edgeDbHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));

				logger.log(Level.INFO,
						pendingTasksIncoming.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						pendingTasksOutgoing.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));

				logger.log(Level.INFO,
						String.format("JVM Heap Size In Use: %.3f GB",
								((double)(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))
										/ (1024.0 * 1024.0 * 1024.0)));

				logger.log(Level.INFO, "Pending Tasks: " + pendingTasks);
				
				logger.log(Level.INFO, "Neo4j STATS End [interval="+intervalNumber+"]");

				vertexCount.newInterval();
				vertexBloomFilterFalsePositive.newInterval();
				vertexCacheMiss.newInterval();
				vertexCacheHit.newInterval();
				vertexDbHit.newInterval();

				edgeCount.newInterval();
				edgeBloomFilterFalsePositive.newInterval();
				edgeCacheMiss.newInterval();
				edgeCacheHit.newInterval();
				edgeDbHit.newInterval();

				pendingTasksIncoming.newInterval();
				pendingTasksOutgoing.newInterval();
				
				this.lastReportedAtMillis = System.currentTimeMillis();
			}
		}
	}
}
