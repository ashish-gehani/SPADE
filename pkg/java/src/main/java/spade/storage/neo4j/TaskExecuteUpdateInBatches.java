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

import spade.storage.Neo4j;

/**
 * An update of the vertices or edges of a graph, which may be the whole database: QuickGrail marks a graph's
 * vertices with a label and its edges in a property. It runs as
 * "&lt;match&gt; call { with &lt;variables&gt; &lt;update&gt; } in transactions of ROWS rows", so Neo4j commits
 * every ROWS rows and neither the heap nor the transaction timeout bounds the size of the graph. Such a query
 * needs an implicit transaction of its own, which it runs in after the storage commits its open transaction.
 */
public class TaskExecuteUpdateInBatches extends StorageTask<Void>{

	public static final int ROWS = 10000;

	private final String cypherQuery;

	/**
	 * @param match the rows to update, such as "match (v:g)"
	 * @param variables the variables of the rows that the update uses, such as "v"
	 * @param update the update of one row, such as "set v:h"
	 */
	public TaskExecuteUpdateInBatches(final String match, final String variables, final String update){
		super(true, true);
		this.cypherQuery = query(match, variables, update, ROWS);
	}

	public static String query(final String match, final String variables, final String update, final int rows){
		return match + " call { with " + variables + " " + update + " } in transactions of " + rows + " rows";
	}

	@Override
	public String toString(){
		return "TaskExecuteUpdateInBatches [cypherQuery=" + cypherQuery + "]";
	}

	@Override
	public final Void execute(final Neo4j storage, final Transaction tx) throws Exception{
		final long startTime = System.currentTimeMillis();
		storage.getDatabaseManager().executeInImplicitTransaction(cypherQuery);
		storage.debug((System.currentTimeMillis() - startTime) + " millis taken to execute query '" + cypherQuery + "'");
		return null;
	}
}
