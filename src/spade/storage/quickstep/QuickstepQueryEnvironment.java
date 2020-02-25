/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.storage.quickstep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.core.QueryInstructionExecutor.GraphStats;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.utility.QuickstepUtil;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * QuickGrail compile-time environment (also used in runtime) mainly for
 * managing symbols (e.g. mapping from graph variables to underlying Quickstep
 * tables).
 */
public class QuickstepQueryEnvironment extends TreeStringSerializable implements QueryEnvironment{

	private final String tableNameGraphSymbols = "graph_symbols";
	private final String tableNameGraphMetadataSymbols = "graph_metadata_symbols";
	private final String columnNameIdCounter = "id_counter";
	
	private final String baseString = "base";
	private final String baseVariable = "$" + baseString;
	public final Graph kBaseGraph = new Graph(createGraphName(baseString));

	private Integer idCounter = null;
	private final HashMap<String, Graph> graphSymbols = new HashMap<String, Graph>();
	private final HashMap<String, GraphMetadata> graphMetadataSymbols = new HashMap<String, GraphMetadata>();
	
	private QuickstepExecutor qs;

	public QuickstepQueryEnvironment(QuickstepExecutor qs){
		this.qs = qs;
		initialize(false);
	}

	private String executeQuery(String query){
		return qs.executeQuery(query);
	}
	
	private ArrayList<String> getAllTableNames(){
		return QuickstepUtil.GetAllTableNames(qs);
	}
	
	private synchronized void initialize(boolean deleteFirst){
		if(deleteFirst){
			executeQuery("DROP TABLE IF EXISTS " + tableNameGraphSymbols);
			executeQuery("DROP TABLE IF EXISTS " + tableNameGraphMetadataSymbols);
			idCounter = null;
			graphSymbols.clear();
			graphMetadataSymbols.clear();
		}
		
		ArrayList<String> allTableNames = getAllTableNames();
		
		if(!allTableNames.contains(tableNameGraphSymbols)){
			String createTableQuery = "CREATE TABLE "+tableNameGraphSymbols+" (name VARCHAR(128), value VARCHAR(128));";
			executeQuery(createTableQuery);
		}
		
		if(!allTableNames.contains(tableNameGraphMetadataSymbols)){
			String createTableQuery = "CREATE TABLE "+tableNameGraphMetadataSymbols+" (name VARCHAR(128), value VARCHAR(128));";
			executeQuery(createTableQuery);
		}

		String lines = null;
		
		// Initialize the symbols buffer.
		lines = executeQuery("COPY SELECT * FROM "+tableNameGraphSymbols+" TO stdout WITH (DELIMITER ',');");
		for(String line : lines.split("\n")){
			String[] items = line.split(",");
			if(items.length == 2){
				String name = items[0];
				if(name.equals(columnNameIdCounter)){
					idCounter = Integer.parseInt(items[1]);
				}else{
					graphSymbols.put(items[0], new Graph(items[1]));
				}
			}
		}
		
		if(idCounter == null){
			idCounter = 1;
			executeQuery("INSERT INTO "+tableNameGraphSymbols+" VALUES('" 
					+ columnNameIdCounter + "', '" + String.valueOf(idCounter) + "');");
		}
		
		lines = executeQuery("COPY SELECT * FROM "+tableNameGraphMetadataSymbols+" TO stdout WITH (DELIMITER ',');");
		for(String line : lines.split("\n")){
			String[] items = line.split(",");
			if(items.length == 2){
				graphMetadataSymbols.put(items[0], new GraphMetadata(items[1]));
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
	private String createGraphName(String suffix){
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
		executeQuery("UPDATE "+tableNameGraphSymbols+" SET value = '" 
				+ result + "' WHERE name = '" + columnNameIdCounter + "';");
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
			executeQuery("UPDATE " + tableNameGraphSymbols 
					+ " SET value = '" + graph.name + "' WHERE name = '" + symbol + "';");
		}else{
			executeQuery("INSERT INTO " + tableNameGraphSymbols 
					+ " VALUES('" + symbol + "', '" + graph.name + "');");
		}
		graphSymbols.put(symbol, graph);
	}
	
	@Override
	public void setGraphMetadataSymbol(String symbol, GraphMetadata graphMetadata){
		if(graphMetadataSymbols.containsKey(symbol)){
			executeQuery("UPDATE " + tableNameGraphMetadataSymbols 
					+ " SET value = '" + graphMetadata.name + "' WHERE name = '" + symbol + "';");
		}else{
			executeQuery("INSERT INTO " + tableNameGraphMetadataSymbols 
					+ " VALUES('" + symbol + "', '" + graphMetadata.name + "');");
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
			executeQuery("DELETE FROM " + tableNameGraphSymbols 
					+ " WHERE name = '" + symbol + "';");
		}
	}
	
	@Override
	public void eraseGraphMetadataSymbol(String symbol){
		if(graphMetadataSymbols.containsKey(symbol)){
			graphMetadataSymbols.remove(symbol);
			executeQuery("DELETE FROM " + tableNameGraphMetadataSymbols 
					+ " WHERE name = '" + symbol + "';");
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
		for(Graph graph : graphSymbols.values()){
			referencedTables.add(getVertexTableName(graph));
			referencedTables.add(getEdgeTableName(graph));
		}
		
		ArrayList<String> allTables = getAllTableNames();
		StringBuilder dropQuery = new StringBuilder();
		for(String table : allTables){
			if(isGarbageTable(referencedTables, table)){
				dropQuery.append("DROP TABLE " + table + ";\n");
			}
		}
		if(dropQuery.length() > 0){
			executeQuery(dropQuery.toString());
		}
	}
	
	@Override
	public boolean isBaseGraph(Graph graph){
		return graph.equals(kBaseGraph);
	}

	public String GetBaseVertexTableName(){
		return "vertex";
	}

	public String GetBaseVertexAnnotationTableName(){
		return "vertex_anno";
	}

	public String GetBaseEdgeTableName(){
		return "edge";
	}

	public String GetBaseEdgeAnnotationTableName(){
		return "edge_anno";
	}

	public String GetBaseTableName(Graph.Component component){
		return component == Graph.Component.kVertex ? GetBaseVertexTableName() : GetBaseEdgeTableName();
	}

	public String GetBaseAnnotationTableName(Graph.Component component){
		return component == Graph.Component.kVertex ? GetBaseVertexAnnotationTableName()
				: GetBaseEdgeAnnotationTableName();
	}

	public String getVertexTableName(Graph graph){
		return graph.name + "_vertex";
	}

	public String getEdgeTableName(Graph graph){
		return graph.name + "_edge";
	}

	public String getTableName(Graph.Component component, Graph graph){
		return component == Graph.Component.kVertex ? getVertexTableName(graph) : getEdgeTableName(graph);
	}
	
	
	public String getMetadataVertexTableName(GraphMetadata graphMetadata){
		return graphMetadata.name + "_vertex";
	}
	
	public String getMetadataEdgeTableName(GraphMetadata graphMetadata){
		return graphMetadata.name + "_edge";
	}

	@Override
	public String getLabel(){
		return "Environment";
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

	@Override
	public GraphStats getGraphStats(Graph graph){
		// TODO Auto-generated method stub
		return null;
	}
	
}
