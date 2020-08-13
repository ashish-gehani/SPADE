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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.Transaction;

import spade.storage.Neo4j;

public class TaskExecuteQuery extends StorageTask<List<Map<String, Object>>>{

	private final String cypherQuery;

	@Override
	public String toString(){
		return "TaskExecuteQuery [cypherQuery=" + cypherQuery + "]";
	}

	public TaskExecuteQuery(final String cypherQuery){
		super(true, true);
		this.cypherQuery = cypherQuery;
	}

	@Override
	public final List<Map<String, Object>> execute(final Neo4j storage, final Transaction tx) throws Exception{
		final List<Map<String, Object>> listOfMaps = new ArrayList<Map<String, Object>>();
		final long startTime = System.currentTimeMillis();
		org.neo4j.graphdb.Result result = tx.execute(cypherQuery);
		final long endTime = System.currentTimeMillis();
		storage.debug((endTime - startTime) + " millis taken to execute query '" + cypherQuery + "'");
		while(result.hasNext()){
			listOfMaps.add(new HashMap<String, Object>(result.next()));
		}
		result.close();
		setResult(listOfMaps);
		return listOfMaps;
	}
}
