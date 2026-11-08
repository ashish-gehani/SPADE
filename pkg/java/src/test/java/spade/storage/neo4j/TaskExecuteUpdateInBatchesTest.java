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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

/**
 * The updates QuickGrail runs in batches (TaskExecuteUpdateInBatches), on an embedded database and with batches
 * smaller than the graph: a graph is the nodes with its label and the edges with its name in the edge symbols.
 */
class TaskExecuteUpdateInBatchesTest{

	private static final int N = 2500; // edges, each between two nodes
	private static final int ROWS = 1000; // so each update takes three batches

	@TempDir
	static Path directory;
	private static DatabaseManagementService service;
	private static GraphDatabaseService db;

	@BeforeAll
	static void start(){
		service = new DatabaseManagementServiceBuilder(directory)
				.setConfig(GraphDatabaseSettings.pagecache_memory, 8L * 1024 * 1024).build();
		db = service.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
	}

	@AfterAll
	static void stop(){
		if(service != null){
			service.shutdown();
		}
	}

	/** Graph g: N edges (a)-[e]->(b), with a in g and every edge in g. */
	@BeforeEach
	void graph(){
		db.executeTransactionally("match (n) detach delete n");
		db.executeTransactionally("unwind range(1, " + N + ") as i create (a:VERTEX:g {hash: 'a' + i})"
				+ "-[:EDGE {hash: 'e' + i, spade_edge_symbols: ',g,'}]->(b:VERTEX {hash: 'b' + i})");
	}

	private static long count(final String query){
		return db.executeTransactionally(query, Map.of(), result -> (Long)result.next().get("c"));
	}

	private static void update(final String match, final String variables, final String update){
		db.executeTransactionally(TaskExecuteUpdateInBatches.query(match, variables, update, ROWS));
	}

	@Test
	void query_runsTheUpdateOfEachRowInASubquery(){
		assertEquals("match (v:g) call { with v set v:h } in transactions of 1000 rows",
				TaskExecuteUpdateInBatches.query("match (v:g)", "v", "set v:h", 1000));
	}

	@Test
	void anExplicitTransaction_cannotRunIt(){
		try(final Transaction tx = db.beginTx()){
			assertThrows(Exception.class,
					() -> tx.execute(TaskExecuteUpdateInBatches.query("match (v:g)", "v", "set v:h", ROWS)));
		}
	}

	@Test
	void union_labelsEveryVertex(){
		update("match (v:g)", "v", "set v:h");
		assertEquals(N, count("match (v:h) return count(v) as c"));
	}

	@Test
	void removingTheLabelItMatchesOn_removesItFromEveryVertex(){ // emptying a graph, and the garbage collection
		update("match (n:g)", "n", "remove n:g");
		assertEquals(0, count("match (n:g) return count(n) as c"));
		assertEquals(2 * N, count("match (n:VERTEX) return count(n) as c"));
	}

	@Test
	void edgeSymbols_areAddedAndDroppedOnEveryEdge(){
		update("match ()-[e]->() where e.spade_edge_symbols contains ',g,'", "e",
				"set e.spade_edge_symbols = e.spade_edge_symbols + ',h,'");
		assertEquals(N, count("match ()-[e]->() where e.spade_edge_symbols contains ',h,' return count(e) as c"));
		update("match ()-[e]->() where e.spade_edge_symbols contains ',g,'", "e",
				"set e.spade_edge_symbols = replace(e.spade_edge_symbols, ',g,', '')");
		assertEquals(0, count("match ()-[e]->() where e.spade_edge_symbols contains ',g,' return count(e) as c"));
		assertEquals(N, count("match ()-[e]->() where e.spade_edge_symbols contains ',h,' return count(e) as c"));
	}

	@Test
	void endpoints_andPaths_labelEveryNodeOnThem(){
		update("match (a:g)-[e]->(b)", "a, b", "set a:s set b:s");
		assertEquals(2 * N, count("match (n:s) return count(n) as c"));
		update("match p=(a:g)-[*0..1]->(b)", "p", "foreach (n in nodes(p) | set n:t)");
		assertEquals(2 * N, count("match (n:t) return count(n) as c"));
	}

	@Test
	void aSliceOfAnOrderedGraph_isLabeled(){ // getSubsetVertex: collect, slice and unwind before the batches
		update("match (v:g) with v order by v.hash asc with collect(v)[0..1500] as vlist unwind vlist as vref",
				"vref", "set vref:u");
		assertEquals(1500, count("match (n:u) return count(n) as c"));
	}
}
