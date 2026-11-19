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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.IndexType;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.storage.neo4j.CacheManager;
import spade.storage.neo4j.Configuration;
import spade.storage.neo4j.Configuration.IndexMode;
import spade.storage.neo4j.Configuration.VertexCacheMode;
import spade.storage.neo4j.DatabaseManager;
import spade.storage.neo4j.Neo4jInstructionExecutor;
import spade.storage.neo4j.Neo4jQueryEnvironment;
import spade.storage.neo4j.StorageStats;
import spade.storage.neo4j.StorageTask;
import spade.storage.neo4j.StorageTest;
import spade.storage.neo4j.TaskCreateIndex;
import spade.storage.neo4j.TaskExecuteQuery;
import spade.storage.neo4j.TaskExecuteUpdateInBatches;
import spade.storage.neo4j.TaskGetHashToVertexMap;
import spade.storage.neo4j.TaskGetQueriedEdgeSet;
import spade.storage.neo4j.TaskPutEdge;
import spade.storage.neo4j.TaskPutVertex;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * Neo4j storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public class Neo4j extends AbstractStorage{

	private static final Logger logger = Logger.getLogger(Neo4j.class.getName());

	private DatabaseManager databaseManager;
	private Configuration configuration;
	private StorageStats neo4jStats;
	private CacheManager cacheManager;
	private Neo4jInstructionExecutor queryInstructionExecutor;
	private Neo4jQueryEnvironment queryEnvironment;
	
	private final Object shutdownLock = new Object();
	private volatile boolean shutdown = false;
	private final Object mainThreadRunningLock = new Object();
	private volatile boolean mainThreadRunning = false;
	
	public final DatabaseManager getDatabaseManager(){
		return databaseManager;
	}
	
	public final Configuration getConfiguration(){
		return configuration;
	}
	
	public final StorageStats getStorageStats(){
		return neo4jStats;
	}
	
	public final CacheManager getCacheManager(){
		return cacheManager;
	}
	
	public final boolean isShutdown(){
		synchronized(shutdownLock){
			return shutdown;
		}
	}
	
	public final void setShutdown(final boolean shutdown){
		synchronized(shutdownLock){
			this.shutdown = shutdown;
		}
	}
	
	public final boolean isMainThreadRunning(){
		synchronized(mainThreadRunningLock){
			return mainThreadRunning;
		}
	}
	
	public final void setMainThreadRunning(final boolean mainThreadRunning){
		synchronized(mainThreadRunningLock){
			this.mainThreadRunning = mainThreadRunning;
		}
	}

	@Override
	public final QueryInstructionExecutor getQueryInstructionExecutor(){
		return queryInstructionExecutor;
	}
	
	public final void debug(String msg){
		if(getConfiguration().debug){
			logger.log(Level.WARNING, "DEBUG::" + msg);
		}
	}
	
	///////////////////////
	
	private final Set<String> nodePropertyNames = new HashSet<String>();
	private final Set<String> nodePropertyNamesIndexed = new HashSet<String>();
	private final Set<String> relationshipPropertyNames = new HashSet<String>();
	private final Set<String> relationshipPropertyNamesIndexed = new HashSet<String>();
	
	public final void removeIndexedNodePropertyName(final String key){
		synchronized(nodePropertyNamesIndexed){
			nodePropertyNamesIndexed.remove(key);
		}
	}
	
	public final void removeIndexedRelationshipPropertyName(final String key){
		synchronized(relationshipPropertyNamesIndexed){
			relationshipPropertyNamesIndexed.remove(key);
		}
	}

	public final void updateNodePropertyNames(final Set<String> names){
		synchronized(nodePropertyNames){
			nodePropertyNames.addAll(names);
		}
		synchronized(nodePropertyNamesIndexed){
			final Set<String> notIndexedKeys = new HashSet<String>();
			notIndexedKeys.addAll(names);
			notIndexedKeys.removeAll(nodePropertyNamesIndexed);
			for(final String notIndexedKey : notIndexedKeys){
				nodePropertyNamesIndexed.add(notIndexedKey); // add to the main list
				prependCreateIndexPendingTask(true, notIndexedKey);
			}
		}
	}
	
	public final void updateRelationshipPropertyNames(final Set<String> names){
		synchronized(relationshipPropertyNames){
			relationshipPropertyNames.addAll(names);
		}
		synchronized(relationshipPropertyNamesIndexed){
			final Set<String> notIndexedKeys = new HashSet<String>();
			notIndexedKeys.addAll(names);
			notIndexedKeys.removeAll(relationshipPropertyNamesIndexed);
			for(final String notIndexedKey : notIndexedKeys){
				relationshipPropertyNamesIndexed.add(notIndexedKey); // add to the main list
				prependCreateIndexPendingTask(false, notIndexedKey);
			}
		}
	}
	
	public final void validateUpdateHashKeyAndKeysInAnnotationMap(final Object vertexOrEdgeObject,
			final String objectName, final Map<String, String> annotationsMap) throws Exception{
		for(String key : Arrays.asList(getConfiguration().hashPropertyName, getConfiguration().edgeSymbolsPropertyName)){
			if(annotationsMap.containsKey(key)){
				switch(getConfiguration().hashKeyCollisionAction){
				case DISCARD:
					throw new Exception(objectName + " contains the reserved hash annotation key '" + key
							+ "'. Discarded: " + vertexOrEdgeObject);
				case REMOVE:
					debug("'" + getConfiguration().hashPropertyName + "'='" + annotationsMap.remove(key) + "' removed from " + objectName
							+ ": " + vertexOrEdgeObject);
					break;
				default:
					throw new Exception("Unhandled action in case of hash property name collision. Discarded: "
							+ vertexOrEdgeObject);
				}
			}
		}
	}
	
	///////////////////////


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
		final TaskExecuteQuery queryForProperties = new TaskExecuteQuery(
				"match ()-[e]->() unwind keys(e) as spade_key return distinct(spade_key);");
		return getAllValuesInAllMaps(queryForProperties.execute(this, tx), "NULL relationship property key");
	}

	private final Set<String> getAllNodeKeys(final Transaction tx) throws Exception{
		final TaskExecuteQuery queryForProperties = new TaskExecuteQuery(
				"match (v) unwind keys(v) as spade_key return distinct(spade_key);");
		return getAllValuesInAllMaps(queryForProperties.execute(this, tx), "NULL node property key");
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
					indexedKeys.addAll(HelperFunctions.listify(indexDefinition.getPropertyKeys()));
				}
			}
			if(indexDefinition.isRelationshipIndex()){
				if(!forNodes){
					indexedKeys.addAll(HelperFunctions.listify(indexDefinition.getPropertyKeys()));
				}
			}
		}
		return indexedKeys;
	}

	private final void waitForIndexesToComeOnline(final Transaction tx, final List<IndexDefinition> indexDefinitions)
			throws Exception{
		if(indexDefinitions.size() > 0){
			if(configuration.waitForIndexSeconds > 0){
				// cater for the case when the storage shuts down because of max retries
				// exceeded TODO
				while(true){
					try{
						tx.schema().awaitIndexesOnline(getConfiguration().waitForIndexSeconds, TimeUnit.SECONDS);
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

	/**
	 * Creates the range indexes on the hash property of vertices and edges if they are missing.
	 * QuickGrail looks vertices and edges up by hash (Neo4jInstructionExecutor.insertLiteralVertex
	 * and insertLiteralEdge, which QueryInstructionExecutor.putGraph calls for every vertex and edge
	 * it puts); without these indexes each lookup scans the database. The per-key indexes may be
	 * turned off (index.vertex, index.edge), and those on edges are full-text, which an equality
	 * lookup cannot use.
	 *
	 * @return true if an index was created
	 */
	private final boolean createHashRangeIndexes(final Transaction tx){
		final String hashKey = configuration.hashPropertyName;
		boolean vertexIndexed = false, edgeIndexed = false;
		for(final IndexDefinition index : tx.schema().getIndexes()){
			if(index.getIndexType() != IndexType.RANGE
					|| !Arrays.asList(hashKey).equals(HelperFunctions.listify(index.getPropertyKeys()))){
				continue;
			}
			if(index.isNodeIndex()
					&& HelperFunctions.listify(index.getLabels()).contains(configuration.neo4jVertexLabel)){
				vertexIndexed = true;
			}
			if(index.isRelationshipIndex()
					&& HelperFunctions.listify(index.getRelationshipTypes()).contains(configuration.neo4jEdgeRelationshipType)){
				edgeIndexed = true;
			}
		}
		if(!vertexIndexed){
			tx.schema().indexFor(configuration.neo4jVertexLabel).on(hashKey).withIndexType(IndexType.RANGE)
					.withName(configuration.indexNamePrefix + "vertex_hash_range").create();
			synchronized(nodePropertyNamesIndexed){
				nodePropertyNamesIndexed.add(hashKey); // so that no other index is made on it
			}
		}
		if(!edgeIndexed){
			tx.schema().indexFor(configuration.neo4jEdgeRelationshipType).on(hashKey).withIndexType(IndexType.RANGE)
					.withName(configuration.indexNamePrefix + "edge_hash_range").create();
		}
		return !vertexIndexed || !edgeIndexed;
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
			nodePropertyNames.add(configuration.hashPropertyName);
			debug("Node properties: " + nodePropertyNames);
		}

		synchronized(relationshipPropertyNames){
			relationshipPropertyNames.clear();
			relationshipPropertyNames.addAll(edgeKeys);
			// Add the hash property and the edge symbol property for relationships
			relationshipPropertyNames.add(configuration.hashPropertyName);
			relationshipPropertyNames.add(configuration.edgeSymbolsPropertyName);
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
	}

	//////////////////////////////////

	private final LinkedList<StorageTask<?>> neo4jDbTasksPending = new LinkedList<StorageTask<?>>();

	/**
	 * Waits until every task queued so far has run and been committed. A query task goes to the
	 * front of the queue, so without this a query right after puts can run before them
	 * (QueryInstructionExecutor.putGraph).
	 */
	public final void waitForQueuedTasks(){
		if(isShutdown() || !isMainThreadRunning()){
			return;
		}
		final StorageTask<Void> marker = new StorageTask<Void>(false, true){
			@Override
			public Void execute(final Neo4j storage, final Transaction tx){
				return null;
			}
		};
		appendPendingTask(marker);
		while(!marker.isCompleted()){
			if(!isMainThreadRunning()){
				throw new RuntimeException("Task execution thread exited while waiting for queued tasks");
			}
			HelperFunctions.sleepSafe(configuration.sleepWaitMillis);
		}
	}

	public final int getPendingTasksSize(){
		synchronized(neo4jDbTasksPending){
			return neo4jDbTasksPending.size();
		}
	}

	private final StorageTask<?> removeFirstPendingTask(){
		if(isMainThreadRunning()){
			synchronized(neo4jDbTasksPending){
				final StorageTask<?> task = neo4jDbTasksPending.pollFirst();
				if(task != null){
					getStorageStats().pendingTasksOutgoing.increment();
				}
				return task;
			}
		}
		return null;
	}

	private final void appendPendingTask(final StorageTask<?> task){
		if(task != null){
			if(isMainThreadRunning()){
				enforceBufferLimit();
				synchronized(neo4jDbTasksPending){
					getStorageStats().pendingTasksIncoming.increment();
					neo4jDbTasksPending.addLast(task);
					neo4jDbTasksPending.notifyAll(); // wakes the runner waiting in awaitPendingTask
				}
			}
		}
	}

	private final void prependPendingTask(final StorageTask<?> task){
		if(task != null){
			if(isMainThreadRunning()){
				// Don't enforce limit here because of high priority
				synchronized(neo4jDbTasksPending){
					getStorageStats().pendingTasksIncoming.increment();
					neo4jDbTasksPending.addFirst(task);
					neo4jDbTasksPending.notifyAll();
				}
			}
		}
	}

	/**
	 * Waits, at most waitMillis, for a task to be pending. Without it the runner polled the queue
	 * without pause when mainThreadSleepWaitMillis is 0 (the default), and used a whole CPU while
	 * SPADE was idle.
	 */
	private final void awaitPendingTask(final long waitMillis){
		if(waitMillis <= 0){
			return;
		}
		synchronized(neo4jDbTasksPending){
			if(neo4jDbTasksPending.isEmpty() && !isShutdown()){
				try{
					neo4jDbTasksPending.wait(waitMillis);
				}catch(InterruptedException e){
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private final void prependCreateIndexPendingTask(final boolean forNodes, final String key){
		final IndexMode indexMode;
		if(forNodes){
			indexMode = getConfiguration().indexVertexMode;
		}else{
			indexMode = getConfiguration().indexEdgeMode;
		}

		switch(indexMode){
			case ALL:
				prependPendingTask(new TaskCreateIndex(key, forNodes));
				break;
			case NONE:
				break;
			default:
				throw new RuntimeException(
					"Unhandled indexing mode for keys '" 
					+ Configuration.keyIndexVertex + "' and/or '"
					+ Configuration.keyIndexEdge
					+ "': " + indexMode);
		}
	}

	private final void enforceBufferLimit(){
		if(getConfiguration().bufferLimit > -1){
			long waitStartMillis = 0;
			boolean needToWait = getPendingTasksSize() > getConfiguration().bufferLimit;
			if(needToWait){
				//debug("Buffer limit reached: " + bufferLimit + ". Current buffer size: " + getPendingTasksSize());
				waitStartMillis = System.currentTimeMillis();
			}
			while(getPendingTasksSize() > getConfiguration().bufferLimit){
				if(isShutdown() || !isMainThreadRunning()){
					break;
				}
				HelperFunctions.sleepSafe(getConfiguration().sleepWaitMillis);
			}
			if(needToWait){
				final long diffMillis = (System.currentTimeMillis() - waitStartMillis);
				if(diffMillis >= 10 * 1000){
					debug("Buffer limit below in: " + diffMillis + " millis.");
				}
			}
		}
	}
	
	private final Runnable dbPendingTasksRunner = new Runnable(){
		// Globals
		int tasksExecutedSinceLastFlush;
		long timeInMillisOfLastFlush;

		private final Transaction getANewTransaction(Transaction tx, final boolean commit) throws Exception{
			if(tx != null){
				try{
					if(commit){
						getDatabaseManager().timedCommit(tx);
					}else{
						tx.rollback();
					}
				}catch(Throwable t){
					logger.log(Level.WARNING, "Failed to commit/rollback transaction", t);
					try{ tx.terminate(); }catch(Throwable subT){ }
				}
				try{ tx.close(); }catch(Throwable t){ }
				tx = null;
				
				if(VertexCacheMode.NODE.equals(getConfiguration().vertexCacheMode)){
					// Clear out the cache because the transaction has been closed and the any nodes in the cache are not usable
					getCacheManager().vertexCacheReset();
				}
			}
			tasksExecutedSinceLastFlush = 0;
			timeInMillisOfLastFlush = System.currentTimeMillis();
			return getDatabaseManager().beginANewTransaction();
		}

		@Override
		public void run(){
			boolean runTheMainLoop;
			Transaction tx = null;

			int fatalErrorCount = 0;

			StorageTask<?> task = null;

			setMainThreadRunning(true);

			try(final Transaction tempTx = getDatabaseManager().beginANewTransaction()){
				loadGlobalPropertyKeysAndIndexedKeys(tempTx); // Only read
				// No error
				runTheMainLoop = true;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to load properties and indexes. Storage not in usable state.", e);
				runTheMainLoop = false;
			}

			if(runTheMainLoop){
				try(final Transaction indexTx = getDatabaseManager().beginANewTransaction()){
					if(createHashRangeIndexes(indexTx)){
						indexTx.commit();
					}
				}catch(Exception e){
					logger.log(Level.WARNING, "Failed to create the range indexes on '"
							+ configuration.hashPropertyName + "'. Lookups by hash scan the database.", e);
				}
			}

			if(runTheMainLoop){
				while(true){
					if(isShutdown()){
						if(getPendingTasksSize() == 0){
							break;
						}else{
							if(getConfiguration().forceShutdown){
								break;
							}
						}
					}

					getStorageStats().print(logger, false);
					try{
						if(tx == null){
							tx = getANewTransaction(tx, true);
						}else{
							if(tasksExecutedSinceLastFlush > getConfiguration().flushBufferSize || 
									(System.currentTimeMillis() - timeInMillisOfLastFlush > (getConfiguration().flushAfterSeconds * 1000))){
								if(tasksExecutedSinceLastFlush > 0){
									debug(tasksExecutedSinceLastFlush + " tasks flushed after "
											+ (System.currentTimeMillis() - timeInMillisOfLastFlush) + " millis");

									// Not a resource leak. Always closed in the function
									tx = getANewTransaction(tx, true);
								}else{
									// tasksExecutedSinceLastFlush is empty so no need to commit anything and then
									// creating a new transaction
								}
							}
						}

						task = removeFirstPendingTask();
						if(task != null){
							Timer timer = null;
							try{
								if(task.commitBeforeExecution){
									tx = getANewTransaction(tx, true);
									
									if(task.isTransactionTimeoutable()){
										final Transaction txCopy = tx;
										timer = new Timer();
										timer.schedule(new TimerTask(){
											@Override
											public void run(){
												try{
													txCopy.terminate();
												}catch(Throwable t){
													// ignore
												}
											}
										}, (task.getTransactionTimeoutInSeconds() + 1) * 1000);
									}
									
								}
								getStorageStats().startActionTimer("EXECUTE-" + task.getClass().getSimpleName());
								task.execute(Neo4j.this, tx);
								if(task.commitAfterExecution){
									tx = getANewTransaction(tx, true);
								}
							}catch(Throwable t){
								task.setError(t);
								
								tx = getANewTransaction(tx, false);
								
								throw t;
							}finally{
								if(timer != null){
									try{
										timer.cancel();
									}catch(Throwable t){
										// ignore
									}
								}
								getStorageStats().stopActionTimer("EXECUTE-" + task.getClass().getSimpleName());
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
						if(fatalErrorCount >= getConfiguration().maxRetries){
							logger.log(Level.SEVERE, "Max retries (" + getConfiguration().maxRetries + ") exhausted. " + "Discarding "
									+ getPendingTasksSize() + " tasks and shutting down.");
							synchronized(neo4jDbTasksPending){
								neo4jDbTasksPending.clear();
							}
							break;
						}
					}
					if(getConfiguration().mainThreadSleepWaitMillis > 0){
						HelperFunctions.sleepSafe(getConfiguration().mainThreadSleepWaitMillis);
					}
					if(task == null){
						// Nothing to do: wait for a task, but not past the next flush, nor longer than a
						// second, so that the flush and a shutdown still happen in time.
						long waitMillis = 1000;
						if(tx != null && tasksExecutedSinceLastFlush > 0){
							final long untilFlush = getConfiguration().flushAfterSeconds * 1000L
									- (System.currentTimeMillis() - timeInMillisOfLastFlush);
							waitMillis = Math.min(waitMillis, Math.max(1, untilFlush));
						}
						awaitPendingTask(waitMillis);
					}
					task = null;
				}

				if(tx != null){
					try{
						getDatabaseManager().timedCommit(tx);
					}catch(Throwable t){
						logger.log(Level.SEVERE, "Failed to commit data in the buffer", t);
					}finally{
						try{ tx.close(); }catch(Throwable t){}
						tx = null;
					}
				}
			}

			getStorageStats().print(logger, true);

			setMainThreadRunning(false);

			logger.log(Level.INFO, "Exited main DB task executor thread");
		}
	};

	// *** START - PUBLIC

	@Override
	public synchronized final boolean shutdown(){
		if(!isShutdown()){
			setShutdown(true);
			synchronized(neo4jDbTasksPending){
				neo4jDbTasksPending.notifyAll(); // the runner may be waiting for a task
			}

			logger.log(Level.INFO,
					"Shutdown called. Waiting for " + getPendingTasksSize() + " pending task(s) to complete");
			// wait for main thread to exit
			while(true){
				if(!isMainThreadRunning()){
					break;
				}
				HelperFunctions.sleepSafe(getConfiguration().sleepWaitMillis);
			}
			logger.log(Level.INFO, "Pending tasks going to be discarded: '" + getPendingTasksSize() + "'. Continuing with shutdown ...");

			synchronized(neo4jDbTasksPending){
				neo4jDbTasksPending.clear();
			}
			
			try{
				getDatabaseManager().shutdown();
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to gracefully shutdown storage", e);
			}

			getStorageStats().print(logger, true);
		}else{
			logger.log(Level.INFO, "Storage already shutdown");
		}
		return true;
	}

	@Override
	public final boolean initialize(final String arguments){
		
		final Result<Configuration> configurationResult = Configuration.initialize(arguments, 
				Settings.getDefaultConfigFilePath(this.getClass()));
		if(configurationResult.error){
			logger.log(Level.SEVERE, "Failed to initialize Neo4j storage");
			logger.log(Level.SEVERE, configurationResult.toErrorString());
			return false;
		}

		try{
			
			this.configuration = configurationResult.result;

			logger.log(Level.INFO, configuration.toString());

			this.neo4jStats = new StorageStats(
					configuration.reportingEnabled, configuration.reportingIntervalSeconds, configuration.timeMe);

			this.databaseManager = new DatabaseManager(this);
			
			this.databaseManager.initialize();
			
			this.cacheManager = new CacheManager(this);

			logger.log(Level.INFO, "Database absolute path: " + configuration.finalConstructedDbPath.getAbsolutePath());
			
			if(configuration.reset){
				final String resetTimerKey = "DATABASE-RESET";
				try{
					neo4jStats.startActionTimer(resetTimerKey);
					databaseManager.resetDatabase();
				}catch(Exception e){
					throw new Exception("Failed to reset database", e);
				}finally{
					neo4jStats.stopActionTimer(resetTimerKey);
				}
			}
			
			final Thread thread = new Thread(dbPendingTasksRunner, "db-pending-task-runner");
			thread.start();

			// Wait for the main thread to reach a stable state
			while(!isMainThreadRunning()){
				if(isShutdown()){
					throw new RuntimeException("Failed to start the main thread successfully");
				}
				HelperFunctions.sleepSafe(getConfiguration().sleepWaitMillis);
			}
			
			if(configuration.test){
				final StorageTest storageTest = new StorageTest();
				storageTest.test(this);
				//throw new RuntimeException("Shutting down after completing the test!");
			}
			
			this.queryEnvironment = new Neo4jQueryEnvironment(configuration.nodePrimaryLabelName, this, configuration.edgeSymbolsPropertyName,
					configuration.querySymbolsNodeLabelName);
			queryEnvironment.initialize();
			this.queryInstructionExecutor = new Neo4jInstructionExecutor(this, queryEnvironment, configuration.hashPropertyName);

			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize storage", e);
			shutdown();
			return false;
		}
	}

	// start - public
	@Override
	public final boolean storeVertex(final AbstractVertex vertex){
		if(!isShutdown() && isMainThreadRunning()){
			appendPendingTask(new TaskPutVertex(vertex));
		}else{
			debug("Storage already shutdown. Vertex discarded: " + vertex);
		}
		return true;
	}

	@Override
	public final boolean storeEdge(final AbstractEdge edge){
		if(!isShutdown() && isMainThreadRunning()){
			appendPendingTask(new TaskPutEdge(edge));
		}else{
			debug("Storage already shutdown. Edge discarded: " + edge);
		}
		return true;
	}

	@Override
	public final Object executeQuery(final String query){
		return executeQueryForSmallResult(query);
	}

	public final List<Map<String, Object>> executeQueryForSmallResult(final String query){
		final TaskExecuteQuery queryObject = new TaskExecuteQuery(query);
		executeQueryAndBlockForResult(queryObject);
		return queryObject.getResult();
	}

	/**
	 * Runs an update of a graph's vertices or edges in batches (TaskExecuteUpdateInBatches): "&lt;match&gt; call {
	 * with &lt;variables&gt; &lt;update&gt; } in transactions".
	 */
	public final void executeUpdateInBatches(final String match, final String variables, final String update){
		executeQueryAndBlockForResult(new TaskExecuteUpdateInBatches(match, variables, update));
	}

	public final Map<String, Map<String, String>> readHashToVertexMap(String vertexAliasInQuery, String query){
		final TaskGetHashToVertexMap queryObject = new TaskGetHashToVertexMap(query, vertexAliasInQuery);
		executeQueryAndBlockForResult(queryObject);
		return queryObject.getResult();
	}
	
	public final Set<QueriedEdge> readEdgeSet(String relationshipAliasInQuery, String query){
		final TaskGetQueriedEdgeSet queryObject = new TaskGetQueriedEdgeSet(query, relationshipAliasInQuery);
		executeQueryAndBlockForResult(queryObject);
		return queryObject.getResult();
	}
	
	// All outside queries routed through here
	public final <X> X executeQueryAndBlockForResult(final StorageTask<X> queryObject){
		if(queryObject == null){
			throw new RuntimeException("NULL query object");
		}

		if(!isShutdown() && isMainThreadRunning()){
			if(!(queryObject instanceof TaskExecuteUpdateInBatches)){
				// The timeout ends the task's transaction; an update in batches commits transactions of its own
				queryObject.setTransactionTimeoutInSeconds(getConfiguration().transactionTimeoutInSeconds);
			}
			prependPendingTask(queryObject);
			// Block until complete
			while(!queryObject.isCompleted()){
				if(!isMainThreadRunning()){
					throw new RuntimeException("Task execution thread exited. Failed to execute query: " + queryObject);
				}
				HelperFunctions.sleepSafe(configuration.sleepWaitMillis);
			}
			if(queryObject.getError() != null){
				throw new RuntimeException(queryObject.getError().getMessage(), queryObject.getError());
			}
			return queryObject.getResult();
		}else{
			throw new RuntimeException("Storage already shutdown. Query failed: " + queryObject);
		}
	}
}