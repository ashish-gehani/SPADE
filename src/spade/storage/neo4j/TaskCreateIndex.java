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

import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexCreator;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.IndexType;

import spade.storage.Neo4j;

public class TaskCreateIndex extends StorageTask<IndexDefinition>{

	private final String key;
	private final boolean forNodes;

	@Override
	public String toString(){
		return "TaskCreateIndex [key=" + key + ", forNodes=" + forNodes + "]";
	}

	public TaskCreateIndex(final String key, final boolean forNodes){
		super(true, true);
		this.key = key;
		this.forNodes = forNodes;
	}

	@Override
	public final IndexDefinition execute(final Neo4j storage, final Transaction tx) throws Exception{
		try{
			IndexCreator indexCreator = null;
			if(forNodes){
				indexCreator = tx.schema().indexFor(storage.getConfiguration().neo4jVertexLabel);
			}else{
				indexCreator = tx.schema().indexFor(storage.getConfiguration().neo4jEdgeRelationshipType).withIndexType(IndexType.FULLTEXT);
			}
			indexCreator = indexCreator.on(key);
			final IndexDefinition indexDefinition = indexCreator.create();
			setResult(indexDefinition);
			return indexDefinition;
		}catch(Exception e){
			// remove from the main list if failed
			if(forNodes){
				storage.removeIndexedNodePropertyName(key);
			}else{
				storage.removeIndexedRelationshipPropertyName(key);
			}
			throw e;
		}
	}
}
