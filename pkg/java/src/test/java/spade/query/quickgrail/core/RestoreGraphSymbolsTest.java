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
package spade.query.quickgrail.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import spade.query.quickgrail.entities.Graph;

/** A query that fails puts its variables back (QuickGrailExecutor.execute). */
class RestoreGraphSymbolsTest{

	private final AbstractQueryEnvironment environment = new AbstractQueryEnvironment("spade_base_graph"){
		@Override public void doGarbageCollection(){}
		@Override public void createSymbolStorageIfNotPresent(){}
		@Override public void deleteSymbolStorageIfPresent(){}
		@Override public int readIdCount(){ return 0; }
		@Override public Map<String, Graph> readGraphSymbols(){ return new HashMap<String, Graph>(); }
		@Override public void readRemoteSymbols(Graph graph){}
		@Override public Map<String, String> readMetadataSymbols(){ return new HashMap<String, String>(); }
		@Override public Map<String, String> readPredicateSymbols(){ return new HashMap<String, String>(); }
		@Override public void saveIdCounter(int idCounter){}
		@Override public void saveGraphSymbol(String symbol, String graphName, boolean symbolNameWasPresent){}
		@Override public void saveRemoteSymbol(Graph graph, Graph.Remote remote){}
		@Override public void saveMetadataSymbol(String symbol, String metadataName, boolean symbolNameWasPresent){}
		@Override public void savePredicateSymbol(String symbol, String predicate, boolean symbolNameWasPresent){}
		@Override public void deleteGraphSymbol(String symbol){}
		@Override public void deleteRemoteSymbol(Graph graph, Graph.Remote remote){}
		@Override public void deleteRemoteSymbols(Graph graph){}
		@Override public void deleteMetadataSymbol(String symbol){}
		@Override public void deletePredicateSymbol(String symbol){}
	};

	@Test
	void aFailedQuerysVariablesArePutBack(){
		final Graph old = new Graph("spade_graph_1");
		environment.setGraphSymbol("$x", old);
		final Map<String, Graph> before = environment.getGraphSymbols();
		// the failed query: $x = ..., $y = ...
		environment.setGraphSymbol("$x", new Graph("spade_graph_2"));
		environment.setGraphSymbol("$y", new Graph("spade_graph_3"));
		environment.restoreGraphSymbols(before);
		assertEquals(old, environment.getGraphSymbol("$x"));
		assertFalse(environment.getGraphSymbols().containsKey("$y"));
	}
}
