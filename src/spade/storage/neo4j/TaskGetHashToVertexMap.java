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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import spade.storage.Neo4j;
import spade.utility.HelperFunctions;

public class TaskGetHashToVertexMap extends StorageTask<Map<String, Map<String, String>>>{

	private final String query;
	private final String vertexAliasInQuery;
	
	@Override
	public String toString(){
		return "TaskGetHashToVertexMap [query=" + query + ", vertexAliasInQuery=" + vertexAliasInQuery + "]";
	}
	
	public TaskGetHashToVertexMap(final String query, final String vertexAliasInQuery){
		super(true, true);
		this.query = query;
		this.vertexAliasInQuery = vertexAliasInQuery;
	}

	@Override
	public final Map<String, Map<String, String>> execute(final Neo4j storage, final Transaction tx){
		Map<String, Map<String, String>> hashToVertexAnnotations = new HashMap<String, Map<String, String>>();
		
		final long startTime = System.currentTimeMillis();
		org.neo4j.graphdb.Result result = tx.execute(query);
		final long endTime = System.currentTimeMillis();
		storage.debug((endTime - startTime) + " millis taken to execute query '" + query + "'");
		
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
					if(storage.getConfiguration().hashPropertyName.equals(key)){
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
