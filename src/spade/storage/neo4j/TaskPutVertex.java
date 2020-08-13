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
import org.neo4j.graphdb.Transaction;

import spade.core.AbstractVertex;
import spade.storage.Neo4j;
import spade.storage.neo4j.Configuration.VertexCacheMode;

public class TaskPutVertex extends StorageTask<Node>{
	private final AbstractVertex vertex;

	@Override
	public String toString(){
		return "TaskPutVertex [vertex=" + vertex + "]";
	}

	public TaskPutVertex(final AbstractVertex vertex){
		super(false, false);
		this.vertex = vertex;
	}

	private final void storeAnnotations(
			final Neo4j storage, final Transaction tx, final Node node, final Map<String, String> annotations) throws Exception{

		for(final Map.Entry<String, String> entry : annotations.entrySet()){
			final String key = entry.getKey();
			final String value = entry.getValue();
			if(key == null){
				throw new Exception("NULL key in vertex: " + vertex);
			}
			node.setProperty(key, value);
		}
		
		storage.updateNodePropertyNames(node.getAllProperties().keySet());
	}

	private final Node storeVertex(final Neo4j storage, final Transaction tx, final String hashCode, final AbstractVertex vertex) throws Exception{
		final Map<String, String> annotations = vertex.getCopyOfAnnotations();
		storage.validateUpdateHashKeyAndKeysInAnnotationMap(vertex, "Vertex", annotations);

		storage.getStorageStats().startActionTimer("NODE-CREATE");
		final Node node = tx.createNode(storage.getConfiguration().neo4jVertexLabel);
		node.setProperty(storage.getConfiguration().hashPropertyName, hashCode);
		storeAnnotations(storage, tx, node, annotations);
		storage.getStorageStats().stopActionTimer("NODE-CREATE");
		return node;
	}

	@Override
	public final Node execute(final Neo4j storage, final Transaction tx) throws Exception{
		if(vertex == null){
			throw new Exception("NULL vertex to put");
		}
		final String hashCode = vertex.bigHashCode();
		if(hashCode == null){
			throw new Exception("NULL hash code for vertex to put: " + vertex);
		}
		//final boolean isReferenceVertex = vertex.isReferenceVertex();
		if(VertexCacheMode.ID.equals(storage.getConfiguration().vertexCacheMode)){
			Node node = null;
			final Long nodeId = storage.getCacheManager().vertexCacheGetNodeId(hashCode, tx);
			if(nodeId == null){
				// Need to put
				node = storeVertex(storage, tx, hashCode, vertex);
				storage.getCacheManager().vertexCachePutNodeId(hashCode, node.getId());
				storage.getStorageStats().vertexCount.increment();
			}else{
				node = tx.getNodeById(nodeId);
			}
			setResult(node);
			return node;
		}else if(VertexCacheMode.NODE.equals(storage.getConfiguration().vertexCacheMode)){
			Node node = storage.getCacheManager().vertexCacheGetNode(hashCode, tx);
			if(node == null){
				node = storeVertex(storage, tx, hashCode, vertex);
				storage.getCacheManager().vertexCachePutNode(hashCode, node);
				storage.getStorageStats().vertexCount.increment();
			}
			setResult(node);
			return node;
		}else{
			throw new RuntimeException(
					"Failed to find node. Unhandled vertex cache mode: " + storage.getConfiguration().vertexCacheMode);
		}
	}
}
