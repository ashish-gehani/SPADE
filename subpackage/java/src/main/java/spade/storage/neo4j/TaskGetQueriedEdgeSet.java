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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import spade.query.quickgrail.core.QueriedEdge;
import spade.storage.Neo4j;
import spade.utility.HelperFunctions;

public class TaskGetQueriedEdgeSet extends StorageTask<Set<QueriedEdge>>{

	private final String query;
	private final String relationshipAliasInQuery;
	
	@Override
	public String toString(){
		return "TaskGetQueriedEdgeSet [query=" + query + ", relationshipAliasInQuery=" + relationshipAliasInQuery + "]";
	}
	
	public TaskGetQueriedEdgeSet(final String query, final String relationshipAliasInQuery){
		super(true, true);
		this.query = query;
		this.relationshipAliasInQuery = relationshipAliasInQuery;
	}

	@Override
	public final Set<QueriedEdge> execute(final Neo4j storage, final Transaction tx){
		final Set<QueriedEdge> edgeSet = new HashSet<QueriedEdge>();
		
		final long startTime = System.currentTimeMillis();
		org.neo4j.graphdb.Result result = tx.execute(query);
		final long endTime = System.currentTimeMillis();
		storage.debug((endTime - startTime) + " millis taken to execute query '" + query + "'");
		
		Iterator<Relationship> relationships = result.columnAs(relationshipAliasInQuery);
		while(relationships.hasNext()){
			Relationship relationship = relationships.next();
			Object childVertexHashObject = relationship.getStartNode().getProperty(storage.getConfiguration().hashPropertyName);
			String childVertexHashString = childVertexHashObject == null ? null
					: childVertexHashObject.toString();
			Object parentVertexHashObject = relationship.getEndNode().getProperty(storage.getConfiguration().hashPropertyName);
			String parentVertexHashString = parentVertexHashObject == null ? null
					: parentVertexHashObject.toString();
			Object edgeHashObject = relationship.getProperty(storage.getConfiguration().hashPropertyName);
			String edgeHashString = edgeHashObject == null ? null : edgeHashObject.toString();
			Map<String, String> annotations = new HashMap<String, String>();
			for(String key : relationship.getPropertyKeys()){
				if(!HelperFunctions.isNullOrEmpty(key)){
					if(key.equalsIgnoreCase(storage.getConfiguration().hashPropertyName)
							|| key.equalsIgnoreCase(storage.getConfiguration().edgeSymbolsPropertyName)){
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
