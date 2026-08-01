/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.utility.graph.convert.json;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;

public class Helper {

	private static final ObjectMapper mapper = new ObjectMapper();

	public static ObjectNode toJSON(final Schema schema) throws Exception{
		if(schema == null){
			throw new Exception("NULL schema");
		}

		final ObjectNode root = mapper.createObjectNode();

		root.set("metadata", toJSON(schema.getMetadata()));
		root.set("properties", toJSON(schema.getProperties()));
		root.set("data", toDataJSON(schema.getGraph()));

		return root;
	}

	private static ObjectNode toDataJSON(final Graph graph) throws Exception{
		final ObjectNode dataNode = mapper.createObjectNode();
		dataNode.set("graph", toJSON(graph));
		dataNode.putObject("compression");
		return dataNode;
	}

	private static ObjectNode toJSON(final Metadata metadata) throws Exception{
		if(metadata == null){
			throw new Exception("NULL metadata");
		}

		final ObjectNode metadataNode = mapper.createObjectNode();
		metadataNode.put("version", metadata.getVersion());
		metadataNode.put("versionDescription", metadata.getVersionDescription());
		return metadataNode;
	}

	private static ObjectNode toJSON(final Properties properties) throws Exception{
		if(properties == null){
			throw new Exception("NULL properties");
		}

		final ObjectNode propertiesNode = mapper.createObjectNode();
		propertiesNode.put("isCompressed", properties.isCompressed());
		return propertiesNode;
	}

	private static ObjectNode toJSON(final Map<String, String> annotations) throws Exception{
		if(annotations == null){
			throw new Exception("NULL annotations");
		}

		final ObjectNode annotationsNode = mapper.createObjectNode();
		for(final Map.Entry<String, String> annotation : annotations.entrySet()){
			annotationsNode.put(annotation.getKey(), annotation.getValue());
		}
		return annotationsNode;
	}

	private static ObjectNode toJSON(final AbstractVertex vertex) throws Exception{
		if(vertex == null){
			throw new Exception("NULL vertex");
		}

		final ObjectNode vertexNode = mapper.createObjectNode();
		vertexNode.put(AbstractVertex.idKey, vertex.getIdentifierForExport());
		vertexNode.put(AbstractVertex.typeKey, vertex.type());
		vertexNode.set(AbstractVertex.annotationsKey, toJSON(vertex.getCopyOfAnnotations()));
		return vertexNode;
	}

	private static ObjectNode toJSON(final AbstractEdge edge) throws Exception{
		if(edge == null){
			throw new Exception("NULL edge");
		}else if(edge.getChildVertex() == null){
			throw new Exception("NULL child vertex in edge: " + edge);
		}else if(edge.getParentVertex() == null){
			throw new Exception("NULL parent vertex in edge: " + edge);
		}

		final ObjectNode edgeNode = mapper.createObjectNode();
		edgeNode.put(AbstractEdge.fromIdKey, edge.getChildVertex().getIdentifierForExport());
		edgeNode.put(AbstractEdge.toIdKey, edge.getParentVertex().getIdentifierForExport());
		edgeNode.put(AbstractEdge.typeKey, edge.type());
		edgeNode.set(AbstractEdge.annotationsKey, toJSON(edge.getCopyOfAnnotations()));
		return edgeNode;
	}

	private static ObjectNode toJSON(final Graph graph) throws Exception{
		if(graph == null){
			throw new Exception("NULL graph");
		}

		final ObjectNode root = mapper.createObjectNode();

		final ArrayNode vertices = root.putArray("vertices");
		for(final AbstractVertex vertex : graph.vertexSet()){
			vertices.add(toJSON(vertex));
		}

		final ArrayNode edges = root.putArray("edges");
		for(final AbstractEdge edge : graph.edgeSet()){
			edges.add(toJSON(edge));
		}

		return root;
	}

}
