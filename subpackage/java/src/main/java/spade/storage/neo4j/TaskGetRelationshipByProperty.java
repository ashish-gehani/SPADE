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

import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import spade.storage.Neo4j;
import spade.storage.neo4j.Configuration.EdgeCacheFindMode;

public class TaskGetRelationshipByProperty extends StorageTask<Boolean>{

	private final RelationshipType relationshipType;
	private final String propertyName, propertyValue;
	
	@Override
	public final String toString(){
		return "TaskGetRelationshipByProperty [relationshipType="+relationshipType+", propertyName="+propertyName+", "
				+ "propertyValue="+propertyValue+"]";
	}
	
	public TaskGetRelationshipByProperty(final RelationshipType relationshipType,
			final String propertyName, final String propertyValue){
		super(false, false);
		this.relationshipType = relationshipType;
		this.propertyName = propertyName;
		this.propertyValue = propertyValue;
	}
	
	public final Boolean execute(final Neo4j storage, final Transaction tx){
		try{
			storage.getStorageStats().startActionTimer(this.getClass().getSimpleName());
			if(storage.getConfiguration().edgeCacheFindMode.equals(EdgeCacheFindMode.ITERATE)){
				final ResourceIterable<Relationship> relationshipIterable = tx.getAllRelationships();
				final ResourceIterator<Relationship> relationshipIterator = relationshipIterable.iterator();
				try{
					while(relationshipIterator.hasNext()){
						final Relationship relationship = relationshipIterator.next();
						if(relationship.isType(relationshipType) 
								&& propertyValue.equals(String.valueOf(relationship.getProperty(propertyName)))){
							setResult(true);
							return true;
						}
					}
					return false;
				}finally{
					relationshipIterator.close();
				}
			}else if(storage.getConfiguration().edgeCacheFindMode.equals(EdgeCacheFindMode.NONE)){
				return false;
			}else if(storage.getConfiguration().edgeCacheFindMode.equals(EdgeCacheFindMode.CYPHER)){
				boolean found = false;
				final Result result = tx.execute("match ()-[e]->() where e.`"+propertyName+"` = '"+propertyValue+"' return e;");
				if(result.hasNext()){
					found = true;
				}
				result.close();
				setResult(found);
				return found;
			}else{
				throw new RuntimeException(
						"Failed to find relationship. Unhandled edge cache find mode: " + storage.getConfiguration().edgeCacheFindMode);
			}
		}finally{
			storage.getStorageStats().stopActionTimer(this.getClass().getSimpleName());
		}
	}
	
}
