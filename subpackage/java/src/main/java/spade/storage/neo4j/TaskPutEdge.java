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

import java.util.Map;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.storage.Neo4j;
import spade.storage.neo4j.Configuration.VertexCacheMode;

public class TaskPutEdge extends StorageTask<Relationship>{

	private final AbstractEdge edge;

	@Override
	public String toString(){
		return "TaskPutEdge [edge=" + edge + "]";
	}

	public TaskPutEdge(final AbstractEdge edge){
		super(false, false);
		this.edge = edge;
	}

	private final void storeAnnotations(final Neo4j storage, final Transaction tx, final Relationship relationship,
			final Map<String, String> annotations) throws Exception{

		for(final Map.Entry<String, String> entry : annotations.entrySet()){
			final String key = entry.getKey();
			final String value = entry.getValue();
			if(key == null){
				throw new Exception("NULL key in edge: " + edge);
			}
			relationship.setProperty(key, value);
		}

		storage.updateRelationshipPropertyNames(relationship.getAllProperties().keySet());
	}

	private final Relationship storeEdge(final Neo4j storage, final Transaction tx,
			final AbstractEdge edge, final Node childNode, final Node parentNode) throws Exception{
		final String hashCode = edge.bigHashCode();
		final Map<String, String> annotations = edge.getCopyOfAnnotations();
		storage.validateUpdateHashKeyAndKeysInAnnotationMap(edge, "Edge", annotations);

		storage.getStorageStats().startActionTimer("RELATIONSHIP-CREATE");
		final Relationship relationship = childNode.createRelationshipTo(parentNode, storage.getConfiguration().neo4jEdgeRelationshipType);
		relationship.setProperty(storage.getConfiguration().hashPropertyName, hashCode);
		storeAnnotations(storage, tx, relationship, annotations);
		storage.getStorageStats().stopActionTimer("RELATIONSHIP-CREATE");
		return relationship;
	}

	@Override
	public final Relationship execute(final Neo4j storage, final Transaction tx) throws Exception{
		if(edge == null){
			throw new Exception("NULL edge to put");
		}
		final String hashCode = edge.bigHashCode();
		if(hashCode == null){
			throw new Exception("NULL hash code for edge to put: " + edge);
		}

		if(storage.getCacheManager().edgeCacheGet(hashCode, tx) == null){
			final AbstractVertex childVertex = edge.getChildVertex();
			final AbstractVertex parentVertex = edge.getParentVertex();
			
			if(childVertex == null){
				throw new RuntimeException("Child vertex is NULL. Failed to put edge: " + edge);
			}
			
			if(parentVertex == null){
				throw new RuntimeException("Parent vertex is NULL. Failed to put edge: " + edge);
			}
			
			Node childNode = null;
			Node parentNode = null;
			
			if(storage.getConfiguration().vertexCacheMode.equals(VertexCacheMode.ID)){
				final Long childNodeId = storage.getCacheManager().vertexCacheGetNodeId(childVertex.bigHashCode(), tx);
				if(childNodeId == null){
					final TaskPutVertex task = new TaskPutVertex(childVertex);
					childNode = task.execute(storage, tx);
				}

				final Long parentNodeId = storage.getCacheManager().vertexCacheGetNodeId(parentVertex.bigHashCode(), tx);
				if(parentNodeId == null){
					final TaskPutVertex task = new TaskPutVertex(parentVertex);
					parentNode = task.execute(storage, tx);
				}
			}else if(storage.getConfiguration().vertexCacheMode.equals(VertexCacheMode.NODE)){
				childNode = storage.getCacheManager().vertexCacheGetNode(childVertex.bigHashCode(), tx);
				if(childNode == null){
					final TaskPutVertex task = new TaskPutVertex(childVertex);
					childNode = task.execute(storage, tx);
				}
				
				parentNode = storage.getCacheManager().vertexCacheGetNode(parentVertex.bigHashCode(), tx);
				if(parentNode == null){
					final TaskPutVertex task = new TaskPutVertex(parentVertex);
					parentNode = task.execute(storage, tx);
				}
			}else{
				throw new RuntimeException("Failed to put edge. Unhandled vertex cache mode: " 
						+ storage.getConfiguration().vertexCacheMode);
			}

			if(childNode == null){
				throw new RuntimeException("Child node is NULL. Failed to put edge: " + edge);
			}

			if(parentNode == null){
				throw new RuntimeException("Parent node is NULL. Failed to put edge: " + edge);
			}

			storage.getStorageStats().edgeCount.increment();
			final Relationship relationship = storeEdge(storage, tx, edge, childNode, parentNode);
			storage.getCacheManager().edgeCachePut(hashCode, true);
			setResult(relationship);
			return relationship;
		}
		return null;
	}
}
