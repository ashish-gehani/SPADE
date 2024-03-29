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
package spade.storage.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;

public abstract class SQLQueryEnvironment extends AbstractQueryEnvironment{

	private final String prefixSPADETempTableName = "spade_temp_";
	
	private final String symbolTableName = "spade_query_symbols";
	private final String keyColumnName = "name"; // the symbol name
	private final String valueColumnName = "value"; // value
	private final String typeColumnName = "type";
	private final String typeValueGraph = "graph";
	private final String typeValueMetadata = "metadata";
	private final String typeValuePredicate = "predicate";
	private final String typeValueIdCount = "idcounter";

	private final String remoteSymbolTableName = "spade_query_remote_symbols";

	public SQLQueryEnvironment(String baseGraphName){
		super(baseGraphName);
	}

	public abstract void executeSQLQuery(String... queries);
	public abstract Set<String> getAllTableNames();
	public abstract List<List<String>> readNColumnsAndMultipleRows(String selectQuery, final int n);;

	@Override
	public final void createSymbolStorageIfNotPresent(){
		Set<String> allTablesNames = getAllTableNames();
		if(!allTablesNames.contains(symbolTableName)){
			executeSQLQuery(
					"create table " + symbolTableName
					+ "("
					+ keyColumnName + " varchar(256), "
					+ valueColumnName + " varchar(1024), "
					+ typeColumnName + " varchar(32)"
					+ ")"
					);
			// insert default 0 value
			executeSQLQuery(
					"insert into " + symbolTableName
					+ " values('', '0', '" + typeValueIdCount + "')"
					);
		}
		if(!allTablesNames.contains(remoteSymbolTableName)){
			executeSQLQuery(
					"create table " + remoteSymbolTableName
					+ "("
					+ "name varchar(256), "
					+ "host varchar(128), "
					+ "port varchar(32), "
					+ "remote_symbol varchar(256)"
					+ ")"
					);
		}
	}

	@Override
	public final void deleteSymbolStorageIfPresent(){
		Set<String> allTablesNames = getAllTableNames();
		if(allTablesNames.contains(symbolTableName)){
			executeSQLQuery("drop table " + symbolTableName + "");
		}
		if(allTablesNames.contains(remoteSymbolTableName)){
			executeSQLQuery("drop table " + remoteSymbolTableName + "");
		}
	}

	@Override
	public final void saveIdCounter(int idCounter){
		executeSQLQuery("update " + symbolTableName + " set " + valueColumnName + " = '" + String.valueOf(idCounter)
				+ "' where " + typeColumnName + " = '" + typeValueIdCount + "'");
	}

	private final void saveSymbol(String name, String value, String type, boolean symbolNameWasPresent){
		if(symbolNameWasPresent){
			executeSQLQuery("update " + symbolTableName + " set " + valueColumnName + " = '" + value + "' where "
					+ keyColumnName + " = '" + name + "' and " + typeColumnName + " = '" + type + "'");
		}else{
			executeSQLQuery(
					"insert into " + symbolTableName + " values('" + name + "', '" + value + "', '" + type + "')");
		}
	}

	@Override
	public final void saveGraphSymbol(String symbol, String graphName, boolean symbolNameWasPresent){
		saveSymbol(symbol, graphName, typeValueGraph, symbolNameWasPresent);
	}

	@Override
	public final void saveRemoteSymbol(final Graph graph, final Graph.Remote remote){
		executeSQLQuery(
				"insert into " + remoteSymbolTableName + " "
				+ "values"
				+ "("
				+ "'" + graph.name + "', "
				+ "'" + remote.host + "', "
				+ "'" + remote.port + "', "
				+ "'" + remote.symbol + "'"
				+ ")"
				);
	}

	@Override
	public final void saveMetadataSymbol(String symbol, String metadataName, boolean symbolNameWasPresent){
		saveSymbol(symbol, metadataName, typeValueMetadata, symbolNameWasPresent);
	}

	@Override
	public final void savePredicateSymbol(String symbol, String predicate, boolean symbolNameWasPresent){
		saveSymbol(symbol, predicate, typeValuePredicate, symbolNameWasPresent);
	}

	private final String buildNColumnSelectQuery(final String[] columns, final String table,
			final String whereColumn, final String whereValue){
		if(columns.length == 0){
			throw new RuntimeException("Columns to project must not be empty");
		}
		String selectColumns = "";
		for(final String column : columns){
			selectColumns += column + ",";
		}
		selectColumns = selectColumns.substring(0, selectColumns.length() - 1);
		// No semi-colon or new line at the end!
		return "select " + selectColumns + " from " + table + " where " + whereColumn + " = '" + whereValue + "'";
	}

	@Override
	public final int readIdCount(){
		String query = buildNColumnSelectQuery(new String[]{valueColumnName, typeColumnName}, symbolTableName, typeColumnName, typeValueIdCount);
		List<List<String>> rows = readNColumnsAndMultipleRows(query, 2);
		return Integer.parseInt(rows.get(0).get(0));
	}
	
	private final Map<String, String> readSymbolsMap(String symbolType, String query){
		List<List<String>> rows = readNColumnsAndMultipleRows(query, 2);
		Map<String, String> map = new HashMap<String, String>();
		for(List<String> row : rows){
			String key = row.get(0);
			String value = row.get(1);
			if(map.containsKey(key)){
				throw new RuntimeException("Duplicate '"+symbolType+"' symbol key: '"+key+"'");
			}
			map.put(key, value);
		}
		return map;
	}

	@Override
	public final Map<String, Graph> readGraphSymbols(){
		final String query = buildNColumnSelectQuery(new String[]{keyColumnName, valueColumnName}, symbolTableName, typeColumnName, typeValueGraph);
		final Map<String, String> symbolToGraphName = readSymbolsMap(typeValueGraph, query);
		final Map<String, Graph> symbolToGraph = new HashMap<>();
		for(final Map.Entry<String, String> entry : symbolToGraphName.entrySet()){
			symbolToGraph.put(entry.getKey(), new Graph(entry.getValue()));
		}
		return symbolToGraph;
	}

	@Override
	public final void readRemoteSymbols(final Graph graph){
		final String query = buildNColumnSelectQuery(
				new String[]{"name", "host", "port", "remote_symbol"}, remoteSymbolTableName, "name", graph.name);
		final LinkedHashSet<Graph.Remote> remotes = new LinkedHashSet<Graph.Remote>();
		final List<List<String>> rows = readNColumnsAndMultipleRows(query, 4);
		for(final List<String> row : rows){
			final String name = row.get(0);
			final String host = row.get(1);
			final String portStr = row.get(2);
			final String remoteSymbol = row.get(3);
			final int port;
			try{
				port = Integer.parseInt(portStr);
			}catch(Exception e){
				throw new RuntimeException("Non-numeric port for graph '" + name + "' connection: '" + portStr + "'");
			}
			final Graph.Remote remote = new Graph.Remote(host, port, remoteSymbol);
			remotes.add(remote);
		}
		for(final Graph.Remote remote : remotes){
			graph.addRemote(remote);
		}
	}

	@Override
	public final Map<String, String> readMetadataSymbols(){
		String query = buildNColumnSelectQuery(new String[]{keyColumnName, valueColumnName}, symbolTableName, typeColumnName, typeValueMetadata);
		return readSymbolsMap(typeValueMetadata, query);
	}

	@Override
	public final Map<String, String> readPredicateSymbols(){
		String query = buildNColumnSelectQuery(new String[]{keyColumnName, valueColumnName}, symbolTableName, typeColumnName, typeValuePredicate);
		return readSymbolsMap(typeValuePredicate, query);
	}
	
	private final void deleteSymbol(String name, String type){
		executeSQLQuery("delete from " + symbolTableName + " where " + keyColumnName + " = '" + name + "' and "
				+ typeColumnName + " = '" + type + "'");
	}

	@Override
	public final void deleteGraphSymbol(String symbol){
		deleteSymbol(symbol, typeValueGraph);
	}

	@Override
	public final void deleteRemoteSymbol(final Graph graph, final Graph.Remote remote){
		executeSQLQuery(
				"delete from " + remoteSymbolTableName + " where "
				+ "name = '" + graph.name + "' and "
				+ "host = '" + remote.host + "' and "
				+ "port = '" + remote.port + "' and "
				+ "remote_symbol = '" + remote.symbol + "'"
				);
	}

	@Override
	public final void deleteRemoteSymbols(final Graph graph){
		executeSQLQuery("delete from " + remoteSymbolTableName + " where name = '" + graph.name + "'");
	}

	@Override
	public final void deleteMetadataSymbol(String symbol){
		deleteSymbol(symbol, typeValueMetadata);
	}

	@Override
	public final void deletePredicateSymbol(String symbol){
		deleteSymbol(symbol, typeValuePredicate);
	}

	@Override
	public final void doGarbageCollection(){
		final HashSet<String> referencedTables = new HashSet<String>();
		referencedTables.add(getGraphVertexTableName(getBaseGraph()));
		referencedTables.add(getGraphEdgeTableName(getBaseGraph()));
		referencedTables.add(symbolTableName);
		referencedTables.add(remoteSymbolTableName);
		for(String name : getCurrentGraphSymbolsStringMap().values()){
			referencedTables.add(getVertexTableName(name));
			referencedTables.add(getEdgeTableName(name));
		}
		for(String name : getCurrentMetadataSymbolsStringMap().values()){
			referencedTables.add(getVertexTableName(name));
			referencedTables.add(getEdgeTableName(name));
		}
		
		Set<String> allTables = getAllTableNames();
		List<String> dropQueriesList = new ArrayList<String>();
		for(String table : allTables){
			boolean drop = false;
			if(table.startsWith(prefixSPADETempTableName) || table.startsWith("m_")){ // drop right away if temp table
				drop = true;
			}else{
				if(isSPADEGraphOrSPADEMetadataName(table)){
					if(!referencedTables.contains(table)){ // i.e. no longer relevant
						drop = true;
					}
				}
			}
			if(drop){
				dropQueriesList.add("drop table " + table + "");
			}
		}
		if(dropQueriesList.size() > 0){
			executeSQLQuery(dropQueriesList.toArray(new String[]{}));
		}

		final String query = buildNColumnSelectQuery(new String[]{keyColumnName, valueColumnName}, symbolTableName, typeColumnName, typeValueGraph);
		final Map<String, String> symbolToGraphName = readSymbolsMap(typeValueGraph, query);
		String graphNamesAsString = "";
		for(final Map.Entry<String, String> entry : symbolToGraphName.entrySet()){
			graphNamesAsString += "'" + entry.getValue() + "',";
		}
		if(graphNamesAsString.length() > 0){
			graphNamesAsString = graphNamesAsString.substring(0, graphNamesAsString.length() - 1);
			executeSQLQuery(
					"delete from " + remoteSymbolTableName
					+ " where name not in"
					+ " ("
					+ graphNamesAsString
					+ ")"
					);
		}
		/*
		 * Works for PostgreSQL but not for Quickstep.
		 * Quickstep doesn't allow nested queries.
		executeSQLQuery(
				"delete from " + remoteSymbolTableName
				+ " where name not in"
				+ " ("
				+ "select " + valueColumnName + " from " + symbolTableName
				+ " where " + typeColumnName + "='" + typeValueGraph + "'"
				+ ")"
				);
		*/
	}
	
	public static final String getVertexTableName(String name){
		return name + "_vertex";
	}
	
	public static final String getEdgeTableName(String name){
		return name + "_edge";
	}
	
	public final String getGraphVertexTableName(Graph graph){
		return getVertexTableName(graph.name);
	}

	public final String getGraphEdgeTableName(Graph graph){
		return getEdgeTableName(graph.name);
	}
	
	public final String getMetadataVertexTableName(GraphMetadata metadata){
		return getVertexTableName(metadata.name);
	}
	
	public final String getMetadataEdgeTableName(GraphMetadata metadata){
		return getEdgeTableName(metadata.name);
	}
	
	public final String getTempTableName(String suffix){
		return prefixSPADETempTableName + suffix;
	}

}
