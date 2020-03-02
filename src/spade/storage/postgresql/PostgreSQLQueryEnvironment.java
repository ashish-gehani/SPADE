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
package spade.storage.postgresql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.storage.PostgreSQL;

public class PostgreSQLQueryEnvironment extends TreeStringSerializable implements QueryEnvironment{

	private final String tableNameGraphSymbols = "graph_symbols";
	private final String tableNameGraphMetadataSymbols = "meta_symbols";
	private final String columnNameIdCounter = "id_counter";
	
	public static final String baseString = "base";
	public static final String baseVariable = "$" + baseString;
	public static final Graph kBaseGraph = new Graph(createGraphName(baseString));

	private Integer idCounter = null;
	private final HashMap<String, Graph> graphSymbols = new HashMap<String, Graph>();
	private final HashMap<String, GraphMetadata> graphMetadataSymbols = new HashMap<String, GraphMetadata>();

	private final PostgreSQL storage;

	public PostgreSQLQueryEnvironment(PostgreSQL storage){
		this.storage = storage;
		initialize(false);
	}
	
	@Override
	public String getBaseSymbolName(){
		return baseVariable;
	}
	
	@Override
	public Graph getBaseGraph(){
		return kBaseGraph;
	}
	
	@Override
	public Set<String> getAllGraphSymbolNames(){
		return graphSymbols.keySet();
	}
	
	private List<List<String>> executeQueryForResult(String query, boolean addColumnNames){
		return storage.executeQueryForResult(query, addColumnNames);
	}
	
	private List<String> getAllTableNames(){
		String query = "SELECT table_name FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema='public';";
		
		List<List<String>> allTableNames = executeQueryForResult(query, false);
		List<String> result = new ArrayList<String>();
		for(List<String> subList : allTableNames){
			result.add(subList.get(0));
		}
		return result;
	}
	
	private synchronized void initialize(boolean deleteFirst){
		if(deleteFirst){
			executeQueryForResult("DROP TABLE IF EXISTS " + tableNameGraphSymbols, false);
			executeQueryForResult("DROP TABLE IF EXISTS " + tableNameGraphMetadataSymbols, false);
			idCounter = null;
			graphSymbols.clear();
			graphMetadataSymbols.clear();
		}
		
		List<String> allTableNames = getAllTableNames();
		
		if(!allTableNames.contains(tableNameGraphSymbols)){
			String createTableQuery = "CREATE TABLE "+tableNameGraphSymbols+" (name VARCHAR(128), value VARCHAR(128));";
			executeQueryForResult(createTableQuery, false);
		}
		
		if(!allTableNames.contains(tableNameGraphMetadataSymbols)){
			String createTableQuery = "CREATE TABLE "+tableNameGraphMetadataSymbols+" (name VARCHAR(128), value VARCHAR(128));";
			executeQueryForResult(createTableQuery, false);
		}

		// Initialize the symbols buffer.
		List<List<String>> graphSymbolsTable = executeQueryForResult("SELECT * FROM " + tableNameGraphSymbols, false);
		for(List<String> row : graphSymbolsTable){
			if(row.size() == 2){
				String name = row.get(0);
				if(name.equals(columnNameIdCounter)){
					idCounter = Integer.parseInt(row.get(1));
				}else{
					graphSymbols.put(row.get(0), new Graph(row.get(1)));
				}
			}
		}
		
		if(idCounter == null){
			idCounter = 1;
			executeQueryForResult("INSERT INTO "+tableNameGraphSymbols+" VALUES('" 
					+ columnNameIdCounter + "', '" + String.valueOf(idCounter) + "');", false);
		}
		
		List<List<String>> graphMetadataSymbolsTable = executeQueryForResult("SELECT * FROM " + tableNameGraphMetadataSymbols, false);
		for(List<String> row : graphMetadataSymbolsTable){
			if(row.size() == 2){
				graphMetadataSymbols.put(row.get(0), new GraphMetadata(row.get(1)));
			}
		}
	}
	/**
	 * Remove all symbols (user defined and internal)
	 */
	@Override
	public void clear(){
		initialize(true);
		gc();
	}

	//////////
	private static String createGraphName(String suffix){
		return "graph_" + suffix;
	}
	
	private String createGraphMetadataName(String suffix){
		return "meta_" + suffix;
	}
	
	private boolean isGraphOrGraphMetadataName(String name){
		return name.startsWith("graph_") || name.startsWith("meta_");
	}
	//////////
	
	private synchronized String incrementIdCounter(){
		idCounter++;
		String result = String.valueOf(idCounter);
		executeQueryForResult("UPDATE "+tableNameGraphSymbols+" SET value = '" 
				+ result + "' WHERE name = '" + columnNameIdCounter + "';", false);
		return result;
	}
	@Override
	public Graph allocateGraph(){
		return new Graph(createGraphName(incrementIdCounter()));
	}
	
	@Override
	public GraphMetadata allocateGraphMetadata(){
		return new GraphMetadata(createGraphMetadataName(incrementIdCounter()));
	}
	
	@Override
	public Graph lookupGraphSymbol(String symbol){
		switch(symbol){
			case baseVariable: return kBaseGraph;
		}
		return graphSymbols.get(symbol);
	}
	
	@Override
	public GraphMetadata lookupGraphMetadataSymbol(String symbol){
		// @<name>
		return graphMetadataSymbols.get(symbol);
	}
	
	@Override
	public void setGraphSymbol(String symbol, Graph graph){
		switch(symbol){
			case baseVariable: throw new RuntimeException("Cannot reassign reserved variables.");
		}
		if(graphSymbols.containsKey(symbol)){
			executeQueryForResult("UPDATE " + tableNameGraphSymbols 
					+ " SET value = '" + graph.name + "' WHERE name = '" + symbol + "';", false);
		}else{
			executeQueryForResult("INSERT INTO " + tableNameGraphSymbols 
					+ " VALUES('" + symbol + "', '" + graph.name + "');", false);
		}
		graphSymbols.put(symbol, graph);
	}
	
	@Override
	public void setGraphMetadataSymbol(String symbol, GraphMetadata graphMetadata){
		if(graphMetadataSymbols.containsKey(symbol)){
			executeQueryForResult("UPDATE " + tableNameGraphMetadataSymbols 
					+ " SET value = '" + graphMetadata.name + "' WHERE name = '" + symbol + "';", false);
		}else{
			executeQueryForResult("INSERT INTO " + tableNameGraphMetadataSymbols 
					+ " VALUES('" + symbol + "', '" + graphMetadata.name + "');", false);
		}
		graphMetadataSymbols.put(symbol, graphMetadata);
	}
	
	@Override
	public void eraseGraphSymbol(String symbol){
		switch(symbol){
			case baseVariable: throw new RuntimeException("Cannot erase reserved variables.");
		}
		if(graphSymbols.containsKey(symbol)){
			graphSymbols.remove(symbol);
			executeQueryForResult("DELETE FROM " + tableNameGraphSymbols 
					+ " WHERE name = '" + symbol + "';", false);
		}
	}
	
	@Override
	public void eraseGraphMetadataSymbol(String symbol){
		if(graphMetadataSymbols.containsKey(symbol)){
			graphMetadataSymbols.remove(symbol);
			executeQueryForResult("DELETE FROM " + tableNameGraphMetadataSymbols 
					+ " WHERE name = '" + symbol + "';", false);
		}
	}

	private boolean isGarbageTable(HashSet<String> referencedTables, String table){
		if(table.startsWith("m_")){ return true; }
		if(isGraphOrGraphMetadataName(table)){ return !referencedTables.contains(table); }
		return false;
	}

	public void gc(){
		HashSet<String> referencedTables = new HashSet<String>();
		referencedTables.add(getVertexTableName(kBaseGraph));
		referencedTables.add(getEdgeTableName(kBaseGraph));
		referencedTables.add(tableNameGraphSymbols);
		referencedTables.add(tableNameGraphMetadataSymbols);
		for(Graph graph : graphSymbols.values()){
			referencedTables.add(getVertexTableName(graph));
			referencedTables.add(getEdgeTableName(graph));
		}
		
		List<String> allTables = getAllTableNames();
		StringBuilder dropQuery = new StringBuilder();
		for(String table : allTables){
			if(isGarbageTable(referencedTables, table)){
				dropQuery.append("DROP TABLE " + table + ";\n");
			}
		}
		if(dropQuery.length() > 0){
			executeQueryForResult(dropQuery.toString(), false);
		}
	}
	
	@Override
	public boolean isBaseGraph(Graph graph){
		return graph.equals(kBaseGraph);
	}
	
	public static String getVertexTableName(Graph graph){
		return graph.name + "_vertex";
	}

	public static String getEdgeTableName(Graph graph){
		return graph.name + "_edge";
	}
	
	public String getMetadataVertexTableName(GraphMetadata graphMetadata){
		return graphMetadata.name + "_vertex";
	}
	
	public String getMetadataEdgeTableName(GraphMetadata graphMetadata){
		return graphMetadata.name + "_edge";
	}

	@Override
	public String getLabel(){
		return "PostgreSQLQueryEnvironment";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		for(Entry<String, Graph> entry : graphSymbols.entrySet()){
			inline_field_names.add(entry.getKey());
			inline_field_values.add(entry.getValue().name);
		}
		for(Entry<String, GraphMetadata> entry : graphMetadataSymbols.entrySet()){
			inline_field_names.add(entry.getKey());
			inline_field_values.add(entry.getValue().name);
		}
	}

}
