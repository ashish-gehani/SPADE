/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.storage.neo4j;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;

import spade.storage.Neo4j;
import spade.utility.HelperFunctions;

public class DatabaseManager{

	private final Neo4j storage;

	private volatile boolean shudownHookAdded = false;
	private final Thread databaseShutdownThreadForJVMShutdown = new Thread(){
		@Override
		public void run(){
			synchronized(dbManagementServiceLock){
				final DatabaseManagementService dbManagementService = getDatabaseManagementService();
				if(dbManagementService != null){
					try{
						dbManagementService.shutdown();
					}catch(Exception e){
						// Ignore.
						// logger.log(Level.WARNING, "Failed to shutdown database using the shutdown hook", e);
					}
					setDatabaseManagementService(null);
				}
			}
		}
	};

	private final Object dbManagementServiceLock = new Object();
	private DatabaseManagementService dbManagementService;
	
	private final Object graphDatabaseServiceLock = new Object();
	private GraphDatabaseService graphDatabaseService; 
	
	private final Object usableLock = new Object();
	private volatile boolean usable = false;

	public DatabaseManager(final Neo4j storage){
		this.storage = storage;
	}

	private final DatabaseManagementService getDatabaseManagementService(){
		synchronized(dbManagementServiceLock){
			return dbManagementService;	
		}
	}
	
	private final void setDatabaseManagementService(final DatabaseManagementService dbManagementService){
		synchronized(dbManagementServiceLock){
			this.dbManagementService = dbManagementService;
		}
	}
	
	private final GraphDatabaseService getGraphDatabaseService(){
		synchronized(graphDatabaseServiceLock){
			return graphDatabaseService;	
		}
	}
	
	private final void setGraphDatabaseService(final GraphDatabaseService graphDatabaseService){
		synchronized(graphDatabaseServiceLock){
			this.graphDatabaseService = graphDatabaseService;
		}
	}
	
	private final void addShutdownHook(){
		synchronized(databaseShutdownThreadForJVMShutdown){
			if(shudownHookAdded){
				return;
			}
			try{
				Runtime.getRuntime().addShutdownHook(databaseShutdownThreadForJVMShutdown);
				shudownHookAdded = true;
			}catch(Exception e){
				// ignore
			}
		}
	}
	
	private final void removeShutdownHook(){
		synchronized(databaseShutdownThreadForJVMShutdown){
			if(!shudownHookAdded){
				return;
			}
			try{
				Runtime.getRuntime().removeShutdownHook(databaseShutdownThreadForJVMShutdown);
			}catch(Exception e){
				// ignore
			}
			shudownHookAdded = false;
		}
	}
	
	private final void setUsable(final boolean usable){
		synchronized(usableLock){
			this.usable = usable;
		}
	}
	
	public final boolean isUsable(){
		synchronized(usableLock){
			return this.usable;
		}
	}

	public final void initialize() throws Exception{
		if(isUsable()){
			throw new RuntimeException("Database already initialized");
		}
		
		boolean success = false;
		
		try{
			DatabaseManagementServiceBuilder dbServiceBuilder = new DatabaseManagementServiceBuilder(
					new File(storage.getConfiguration().dbHomeDirectoryFile.getAbsolutePath()));

			if(storage.getConfiguration().neo4jConfigFilePath != null){
				dbServiceBuilder = dbServiceBuilder.loadPropertiesFromFile(storage.getConfiguration().neo4jConfigFilePath.getAbsolutePath());
			}

			dbServiceBuilder = dbServiceBuilder.setConfig(
					GraphDatabaseSettings.data_directory, java.nio.file.Paths.get(storage.getConfiguration().dbDataDirectoryName));

			final DatabaseManagementService dbManagementService = dbServiceBuilder.build();
			setDatabaseManagementService(dbManagementService);

			// For SPADE kill!
			addShutdownHook();
			
			boolean found = false;
			for(String existingDatabase : dbManagementService.listDatabases()){
				if(existingDatabase.equals(storage.getConfiguration().dbName)){
					found = true;
					break;
				}
			}
			if(!found){
				// Only allowed for enterprise and not community version of neo4j
				// Error would be thrown if not Enterprise
				dbManagementService.createDatabase(storage.getConfiguration().dbName);
			}
			final GraphDatabaseService graphDb = dbManagementService.database(storage.getConfiguration().dbName);

			setGraphDatabaseService(graphDb);

			setUsable(true);
			
			success = true;
			
		}finally{
			if(!success){
				shutdown();
			}
		}
	}
	
	public final void timedCommit(final Transaction tx){
		if(tx != null){
			try{
				storage.getStorageStats().startActionTimer("TX-COMMIT");
				tx.commit();
			}finally{
				storage.getStorageStats().stopActionTimer("TX-COMMIT");
			}
		}
	}
	
	public final void resetDatabase() throws Exception{
		if(!isUsable()){
			throw new RuntimeException("Database not initialized or already shutdown");
		}
		
		final Label nodeLabel = storage.getConfiguration().neo4jVertexLabel;
		final RelationshipType relationshipType = storage.getConfiguration().neo4jEdgeRelationshipType;
		final Label queryNodeLabel = storage.getConfiguration().neo4jQuerySymbolsNodeLabel;

		Transaction tx = null;
		try{
			tx = getGraphDatabaseService().beginTx();
			
			final Schema schema = tx.schema();
			final Iterable<IndexDefinition> indexDefinitionIterable = schema.getIndexes();
			final Iterator<IndexDefinition> indexDefinitionIterator = indexDefinitionIterable.iterator();
			while(indexDefinitionIterator.hasNext()){
				final IndexDefinition indexDefinition = indexDefinitionIterator.next();
				if(indexDefinition.isNodeIndex()){
					final List<Label> labels = HelperFunctions.listify(indexDefinition.getLabels());
					if(labels.size() == 1){
						if(labels.get(0).equals(nodeLabel) || labels.get(0).equals(queryNodeLabel)){
							indexDefinition.drop();
						}
					}
				}else if(indexDefinition.isRelationshipIndex()){
					final List<RelationshipType> relationshipTypes = HelperFunctions.listify(indexDefinition.getRelationshipTypes());
					if(relationshipTypes.size() == 1){
						if(relationshipTypes.get(0).equals(relationshipType)){
							indexDefinition.drop();
						}
					}
				}
			}

			timedCommit(tx);
		}catch(Exception deleteException){
			if(tx != null){
				try{
					tx.rollback();
				}catch(Exception rollbackException){
					// ignore
				}
			}

			throw deleteException;
		}finally{
			if(tx != null){
				try{
					tx.close();
				}catch(Exception closeException){
					// ignore
				}
			}
		}
		
		try{
			tx = getGraphDatabaseService().beginTx();
			long operationsCountInTransaction = 0;
			
			while(true){
				final ResourceIterable<Relationship> relationshipIterable = tx.getAllRelationships();
				final ResourceIterator<Relationship> relationshipIterator = relationshipIterable.iterator();
				while(relationshipIterator.hasNext()){
					final Relationship relationship = relationshipIterator.next();
					if(relationship.isType(relationshipType)){
						relationship.delete();
						operationsCountInTransaction++;
						if(operationsCountInTransaction >= storage.getConfiguration().flushBufferSize){
							break;
						}
					}
				}
				relationshipIterator.close();
				timedCommit(tx);
				tx.close();
				tx = null;
				if(operationsCountInTransaction == 0){
					break; // finished deleting all
				}else{
					tx = getGraphDatabaseService().beginTx();
					operationsCountInTransaction = 0;
				}
			}
		}finally{
			if(tx != null){
				try{
					tx.close();
				}catch(Exception closeException){
					// ignore
				}
			}
		}
		
		try{
			tx = getGraphDatabaseService().beginTx();
			long operationsCountInTransaction = 0;
			
			while(true){
				final ResourceIterator<Node> spadeQueryNodeIterator = tx.findNodes(queryNodeLabel);
				while(spadeQueryNodeIterator.hasNext()){
					final Node node = spadeQueryNodeIterator.next();
					node.delete();
					operationsCountInTransaction++;
					if(operationsCountInTransaction >= storage.getConfiguration().flushBufferSize){
						break;
					}
				}

				spadeQueryNodeIterator.close();
				timedCommit(tx);
				tx.close();
				tx = null;
				if(operationsCountInTransaction == 0){
					break; // finished deleting all
				}else{
					tx = getGraphDatabaseService().beginTx();
					operationsCountInTransaction = 0;
				}
			}
		}finally{
			if(tx != null){
				try{
					tx.close();
				}catch(Exception closeException){
					// ignore
				}
			}
		}

		try{
			tx = getGraphDatabaseService().beginTx();
			long operationsCountInTransaction = 0;
			
			while(true){
				final ResourceIterator<Node> nodeIterator = tx.findNodes(nodeLabel);
				while(nodeIterator.hasNext()){
					final Node node = nodeIterator.next();
					node.delete();
					operationsCountInTransaction++;
					if(operationsCountInTransaction >= storage.getConfiguration().flushBufferSize){
						break;
					}
				}

				nodeIterator.close();
				timedCommit(tx);
				tx.close();
				tx = null;
				if(operationsCountInTransaction == 0){
					break; // finished deleting all
				}else{
					tx = getGraphDatabaseService().beginTx();
					operationsCountInTransaction = 0;
				}
			}
		}finally{
			if(tx != null){
				try{
					tx.close();
				}catch(Exception closeException){
					// ignore
				}
			}
		}
	}
	
	public final Transaction beginANewTransaction(){
		if(!isUsable()){
			throw new RuntimeException("Database not initialized or already shutdown");
		}
		return getGraphDatabaseService().beginTx();
	}
	
	public final Set<String> getAllLabels(){
		try(final Transaction tx = beginANewTransaction()){
			final Iterable<Label> labelIterable = tx.getAllLabelsInUse();
			final List<Label> labelList = HelperFunctions.listify(labelIterable);
			final Set<String> labelNameList = new HashSet<String>();
			labelList.forEach(label -> {labelNameList.add(label.name());});
			return labelNameList;
		}
	}

	public final void shutdown() throws Exception{
		setUsable(false);
		setGraphDatabaseService(null);
		removeShutdownHook();
		synchronized(dbManagementServiceLock){
			final DatabaseManagementService dbManagementService = getDatabaseManagementService();
			if(dbManagementService != null){
				try{
					dbManagementService.shutdown();
				}catch(Exception e){
					throw e;
				}finally{
					setDatabaseManagementService(null);
				}
			}
		}
	}
}
