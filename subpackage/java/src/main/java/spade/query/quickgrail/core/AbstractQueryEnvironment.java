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
package spade.query.quickgrail.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.entities.GraphPredicate;
import spade.query.quickgrail.utility.QuickGrailPredicateTree;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.utility.AggregationState;
import spade.utility.HelperFunctions;

public abstract class AbstractQueryEnvironment extends TreeStringSerializable{
	
	public final Logger logger = Logger.getLogger(this.getClass().getName());

	private final String prefixGraphName = "spade_graph_", prefixMetadataName = "spade_meta_";
	private final String prefixGraphSymbol = "$", prefixMetadataSymbol = "@", prefixPredicateSymbol = "%";
	
	private int idCounter = -1;
	private final Map<String, Graph> symbolsGraph = new HashMap<String, Graph>();
	private final Map<String, GraphMetadata> symbolsMetadata = new HashMap<String, GraphMetadata>();
	private final Map<String, GraphPredicate> symbolsPredicate = new HashMap<String, GraphPredicate>();

	private final String baseGraphSymbol = prefixGraphSymbol+"base";
	private final Graph baseGraph;

	private final EnvironmentVariableManager envVarManager = new EnvironmentVariableManager();
	// START - Differential privacy related state
	private final AggregationState aggregationState = new AggregationState();
	public final AggregationState getAggregationState(){
		return aggregationState;
	}
	// END - Differential privacy related state

	// Step 1
	public AbstractQueryEnvironment(final String baseGraphName){
		if(HelperFunctions.isNullOrEmpty(baseGraphName)){
			throw new RuntimeException("NULL/Empty base graph name: '"+baseGraphName+"'");
		}
		this.baseGraph = new Graph(baseGraphName);
	}

	public final EnvironmentVariableManager getEnvVarManager(){
		return envVarManager;
	}

	public final void initialize(){
		initialize(false);
	}

	// Step 2
	private final void initialize(boolean reset){
		envVarManager.initialize();

		if(reset){
			try{
				deleteSymbolStorageIfPresent();
			}catch(Throwable t){
				throw new RuntimeException("Failed to reset symbol storage", t);
			}
		}

		try{
			createSymbolStorageIfNotPresent();
		}catch(Throwable t){
			throw new RuntimeException("Failed to initialize symbol storage", t);
		}
		
		this.idCounter = -1;
		symbolsGraph.clear();
		symbolsMetadata.clear();
		symbolsPredicate.clear();
		
		int idCounter;
		try{
			idCounter = readIdCount();
		}catch(Throwable t){
			throw new RuntimeException("Failed to read symbol id counter", t);
		}
		if(idCounter < 0){
			throw new RuntimeException("Invalid id counter. Must be non-negative: " + idCounter);
		}
		this.idCounter = idCounter;

		readRemoteSymbols(this.baseGraph);

		Map<String, Graph> graphSymbols;
		try{
			graphSymbols = readGraphSymbols();
		}catch(Throwable t){
			throw new RuntimeException("Failed to read graph symbols", t);
		}
		if(graphSymbols != null && graphSymbols.size() > 0){
			for(Map.Entry<String, Graph> entry : graphSymbols.entrySet()){
				String key = entry.getKey();
				Graph value = entry.getValue();
				
				symbolTypeMustBeGraph(key);
				nameTypeMustBeGraph(value.name);
				
				if(isBaseGraphSymbol(key)){
					throw new RuntimeException("Cannot use reserved graph symbol: '"+getBaseGraphSymbol()+"'");
				}

				if(this.symbolsGraph.containsKey(key)){
					throw new RuntimeException("Duplicate graph symbol: '"+key+"'");
				}
				
				this.symbolsGraph.put(key, value);
				readRemoteSymbols(value);
			}
		}
		
		Map<String, String> metadataSymbols;
		try{
			metadataSymbols = readMetadataSymbols();
		}catch(Throwable t){
			throw new RuntimeException("Failed to read metadata symbols", t);
		}
		if(metadataSymbols != null && metadataSymbols.size() > 0){
			for(Map.Entry<String, String> entry : metadataSymbols.entrySet()){
				String key = entry.getKey();
				String value = entry.getValue();
				
				symbolTypeMustBeMetadata(key);
				nameTypeMustBeMetadata(value);
				
				if(this.symbolsMetadata.containsKey(key)){
					throw new RuntimeException("Duplicate metadata symbol: '"+key+"'");
				}
				
				this.symbolsMetadata.put(key, new GraphMetadata(value));
			}
		}
		
		Map<String, String> predicateSymbols;
		try{
			predicateSymbols = readPredicateSymbols();
		}catch(Throwable t){
			throw new RuntimeException("Failed to read predicate symbols", t);
		}
		if(predicateSymbols != null && predicateSymbols.size() > 0){
			for(Map.Entry<String, String> entry : predicateSymbols.entrySet()){
				String key = entry.getKey();
				String value = entry.getValue();
				
				symbolTypeMustBePredicate(key);
				
				PredicateNode predicateNode = null;
				try{
					predicateNode = QuickGrailPredicateTree.deserializePredicateNodeFromStorage(value);
					if(predicateNode == null){
						throw new RuntimeException("NULL predicate");
					}
				}catch(Throwable t){
					throw new RuntimeException("Failed to create predicate from string: '"+value+"'", t);
				}
				
				if(this.symbolsPredicate.containsKey(key)){
					throw new RuntimeException("Duplicate predicate symbol: '"+key+"'");
				}
				
				this.symbolsPredicate.put(key, new GraphPredicate(predicateNode));
			}
		}
	}
	
	public final void resetWorkspace(){
		initialize(true);
		doGarbageCollection();
	}
	
	public abstract void doGarbageCollection();
	
	public abstract void createSymbolStorageIfNotPresent();
	public abstract void deleteSymbolStorageIfPresent(); // delete everything
	public abstract int readIdCount();
	public abstract Map<String, Graph> readGraphSymbols();
	public abstract void readRemoteSymbols(final Graph graph);
	public abstract Map<String, String> readMetadataSymbols();
	public abstract Map<String, String> readPredicateSymbols();

	public final boolean isGraphSymbol(final String symbol){
		return symbol != null && symbol.startsWith(prefixGraphSymbol);
	}

	private void symbolTypeMustBeGraph(String symbol){
		if(symbol == null){
			throw new RuntimeException("NULL symbol. Expected 'Graph'");
		}
		if(!symbol.startsWith(prefixGraphSymbol)){
			throw new RuntimeException("Unexpected start of 'Graph' symbol. Expected to start with '"+prefixGraphSymbol+"' instead of '"+symbol+"'");
		}
	}
	
	private void nameTypeMustBeGraph(String name){
		if(name == null){
			throw new RuntimeException("NULL name. Expected 'Graph'");
		}
		if(!name.startsWith(prefixGraphName)){
			throw new RuntimeException("Unexpected start of 'Graph' name. Expected to start with '"+prefixGraphName+"' instead of '"+name+"'");
		}
	}
	
	private void symbolTypeMustBeMetadata(String symbol){
		if(symbol == null){
			throw new RuntimeException("NULL symbol. Expected 'GraphMetadata'");
		}
		if(!symbol.startsWith(prefixMetadataSymbol)){
			throw new RuntimeException("Unexpected start of 'GraphMetadata' symbol. Expected to start with '"+prefixMetadataSymbol+"' instead of '"+symbol+"'");
		}
	}
	
	private void nameTypeMustBeMetadata(String name){
		if(name == null){
			throw new RuntimeException("NULL name. Expected 'GraphMetadata'");
		}
		if(!name.startsWith(prefixMetadataName)){
			throw new RuntimeException("Unexpected start of 'GraphMetadata' name. Expected to start with '"+prefixMetadataName+"' instead of '"+name+"'");
		}
	}
	
	private void symbolTypeMustBePredicate(String symbol){
		if(symbol == null){
			throw new RuntimeException("NULL symbol. Expected 'GraphPredicate'");
		}
		if(!symbol.startsWith(prefixPredicateSymbol)){
			throw new RuntimeException("Unexpected start of 'GraphPredicate' symbol. Expected to start with '"+prefixPredicateSymbol+"' instead of '"+symbol+"'");
		}
	}
	
	//////////////////
	// BASE
	public final String getBaseGraphSymbol(){
		return baseGraphSymbol;
	}

	public final Graph getBaseGraph(){
		return baseGraph;
	}

	public final boolean isBaseGraphSymbol(String symbol){
		return symbol.equals(getBaseGraphSymbol());
	}

	public final boolean isBaseGraph(Graph graph){
		return graph.equals(getBaseGraph());
	}
	//////////////////

	//////////////////
	// SYMBOL GETTERS
	public final Graph getGraphSymbol(String symbol){ // TODO rename to getGraph
		if(isBaseGraphSymbol(symbol)){
			return getBaseGraph();
		}else{
			return symbolsGraph.get(symbol);
		}
	}

	public final GraphMetadata getMetadataSymbol(String symbol){
		return symbolsMetadata.get(symbol);
	}

	public final GraphPredicate getPredicateSymbol(String symbol){
		return symbolsPredicate.get(symbol);
	}
	
	//////////////////

	//////////////////
	// SYMBOL Generators
	private final String createGraphName(String suffix){
		return prefixGraphName + suffix;
	}

	private final String createMetadataName(String suffix){
		return prefixMetadataName + suffix;
	}

	public abstract void saveIdCounter(int idCounter);
	
	private final synchronized String getNextId(){
		saveIdCounter(idCounter+1);
		idCounter = idCounter + 1;
		return String.valueOf(idCounter);
	}

	public final Graph allocateGraph(){
		Graph graph = new Graph(createGraphName(getNextId()));
		if(isBaseGraph(graph)){
			throw new RuntimeException("Allocated graph name is reserved for base graph: '" + graph + "'.");
		}
		return graph;
	}

	public final GraphMetadata allocateMetadata(){
		return new GraphMetadata(createMetadataName(getNextId()));
	}
	
	//////////////////
	// Name GC
	
	// TODO check for base?
	public final boolean isSPADEGraphOrSPADEMetadataName(String name){
		return name.startsWith(prefixGraphName) || name.startsWith(prefixMetadataName);
	}
	
	// TODO check for base?
//	public final boolean isGraphNameReferenced(String name){
//		for(Graph graph : symbolsGraph.values()){
//			if(name.startsWith(graph.name)){
//				return true;
//			}
//		}
//		return false;
//	}
//	
//	public final boolean isMetadataNameReferenced(String name){
//		for(GraphMetadata metadata : symbolsMetadata.values()){
//			if(name.startsWith(metadata.name)){
//				return true;
//			}
//		}
//		return false;
//	}

	//////////////////
	// SYMBOL SETTERS
	public abstract void saveGraphSymbol(String symbol, String graphName, boolean symbolNameWasPresent);

	public final void setGraphSymbol(String symbol, Graph graph){
		if(isBaseGraphSymbol(symbol)){
			throw new RuntimeException("Cannot reassign reserved symbols: '" + symbol + "'.");
		}
		symbolTypeMustBeGraph(symbol);
		
		boolean set = false; // To know if the symbol has to be set or not
		boolean symbolNameWasPresent = false; // To tell the child whether symbol existed before or not
		Graph existingGraph = symbolsGraph.get(symbol);
		if(existingGraph != null){
			if(graph.equals(existingGraph)){
				set = false;
				symbolNameWasPresent = false;
			}else{
				set = true;
				symbolNameWasPresent = true;
			}
		}else{
			set = true;
			symbolNameWasPresent = false;
		}

		if(set){
			symbolsGraph.put(symbol, graph);
			saveGraphSymbol(symbol, graph.name, symbolNameWasPresent);
		}
	}

	public abstract void saveRemoteSymbol(final Graph graph, final Graph.Remote remote);

	public final void setRemoteSymbol(final Graph graph, final Graph.Remote remote){
		if(!graph.containsRemote(remote)){
			graph.addRemote(remote);
			saveRemoteSymbol(graph, remote);
		}
	}

	public final void copyRemoteSymbols(final Graph dstGraph, final Graph srcGraph){
		for(final Graph.Remote remote : srcGraph.getRemotes()){
			setRemoteSymbol(dstGraph, remote);
		}
	}

	public final void intersectRemoteSymbols(final Graph resultGraph, final Graph lhsGraph, final Graph rhsGraph){
		final Set<Graph.Remote> commonRemotes = new HashSet<Graph.Remote>(lhsGraph.getRemotes());
		commonRemotes.retainAll(new HashSet<Graph.Remote>(rhsGraph.getRemotes()));
		for(final Graph.Remote commonRemote : commonRemotes){
			setRemoteSymbol(resultGraph, commonRemote);
		}
	}

	public final void subtractRemoteSymbols(final Graph resultGraph, final Graph lhsGraph, final Graph rhsGraph){
		final Set<Graph.Remote> onlyLHSRemotes = new HashSet<Graph.Remote>(lhsGraph.getRemotes());
		onlyLHSRemotes.removeAll(new HashSet<Graph.Remote>(rhsGraph.getRemotes()));
		for(final Graph.Remote onlyLHSRemote : onlyLHSRemotes){
			setRemoteSymbol(resultGraph, onlyLHSRemote);
		}
	}

	public abstract void saveMetadataSymbol(String symbol, String metadataName, boolean symbolNameWasPresent);

	public final void setMetadataSymbol(String symbol, GraphMetadata metadata){
		symbolTypeMustBeMetadata(symbol);
		boolean set = false; // To know if the symbol has to be set or not
		boolean symbolNameWasPresent = false; // To tell the child whether symbol existed before or not
		GraphMetadata existingMetadata = symbolsMetadata.get(symbol);
		if(existingMetadata != null){
			if(metadata.equals(existingMetadata)){
				set = false;
				symbolNameWasPresent = false;
			}else{
				set = true;
				symbolNameWasPresent = true;
			}
		}else{
			set = true;
			symbolNameWasPresent = false;
		}

		if(set){
			symbolsMetadata.put(symbol, metadata);
			saveMetadataSymbol(symbol, metadata.name, symbolNameWasPresent);
		}
	}

	public abstract void savePredicateSymbol(String symbol, String predicate, boolean symbolNameWasPresent);

	public final void setPredicateSymbol(String symbol, GraphPredicate predicate){
		symbolTypeMustBePredicate(symbol);
		boolean set = false; // To know if the symbol has to be set or not
		boolean symbolNameWasPresent = false; // To tell the child whether symbol existed before or not
		GraphPredicate existingPredicate = symbolsPredicate.get(symbol);
		if(existingPredicate != null){
			if(predicate.equals(existingPredicate)){
				set = false;
				symbolNameWasPresent = false;
			}else{
				set = true;
				symbolNameWasPresent = true;
			}
		}else{
			set = true;
			symbolNameWasPresent = false;
		}

		if(set){
			symbolsPredicate.put(symbol, predicate);
			String predicateAsString = QuickGrailPredicateTree.serializePredicateNodeForStorage(predicate.predicateRoot);
			savePredicateSymbol(symbol, predicateAsString, symbolNameWasPresent);
		}
	}
	//////////////////
	
	//////////////////
	// SYMBOL Removers
	public final void removeSymbol(String symbol){
		if(symbol.startsWith(prefixGraphSymbol)){
			removeGraphSymbol(symbol);
		}else if(symbol.startsWith(prefixMetadataSymbol)){
			removeMetadataSymbol(symbol);
		}else if(symbol.startsWith(prefixPredicateSymbol)){
			removePredicateSymbol(symbol);
		}else{
			throw new RuntimeException("Unexpected symbol name: '"+symbol+"'. "
					+ "Allowed '"+prefixGraphSymbol+"', '"+prefixMetadataSymbol+"', and '"+prefixPredicateSymbol+"'.");
		}
	}

	public abstract void deleteGraphSymbol(String symbol);

	public final void removeGraphSymbol(String symbol){
		if(symbol.equals(getBaseGraphSymbol())){
			throw new RuntimeException("Cannot erase reserved variables.");
		}
		if(symbolsGraph.containsKey(symbol)){
			final Graph graph = symbolsGraph.remove(symbol);
			deleteGraphSymbol(symbol);
			deleteRemoteSymbols(graph);
		}
	}

	public abstract void deleteRemoteSymbol(final Graph graph, final Graph.Remote remote);
	public abstract void deleteRemoteSymbols(final Graph graph);

	public final void removeRemoteSymbol(final Graph graph, final Graph.Remote remote){
		deleteRemoteSymbol(graph, remote);
		graph.removeRemote(remote);
	}

	public final void removeRemoteSymbols(final Graph graph){
		deleteRemoteSymbols(graph);
		graph.clearRemotes();
	}

	public abstract void deleteMetadataSymbol(String symbol);
	
	public final void removeMetadataSymbol(String symbol){
		if(symbolsMetadata.containsKey(symbol)){
			symbolsMetadata.remove(symbol);
			deleteMetadataSymbol(symbol);
		}
	}
	
	public abstract void deletePredicateSymbol(String symbol);
	
	public final void removePredicateSymbol(String symbol){
		if(symbolsPredicate.containsKey(symbol)){
			symbolsPredicate.remove(symbol);
			deletePredicateSymbol(symbol);
		}
	}

	//////////////////

	public Map<String, String> getCurrentGraphSymbolsStringMap(){
		final Map<String, String> map = new HashMap<String, String>();
		symbolsGraph.forEach((k,v) -> map.put(k, v.name));
		return map;
	}
	
	public Map<String, String> getCurrentMetadataSymbolsStringMap(){
		final Map<String, String> map = new HashMap<String, String>();
		symbolsMetadata.forEach((k,v) -> map.put(k, v.name));
		return map;
	}
	
	public Map<String, String> getCurrentPredicateSymbolsStringMap(){
		final Map<String, String> map = new HashMap<String, String>();
		symbolsPredicate.forEach((k,v) -> map.put(k, QuickGrailPredicateTree.serializePredicateNodeForStorage(v.predicateRoot)));
		return map;
	}
	
	@Override
	public String getLabel(){
		return this.getClass().getSimpleName();
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add(baseGraphSymbol);
		inline_field_values.add(baseGraph.name);
		for(Entry<String, Graph> entry : symbolsGraph.entrySet()){
			inline_field_names.add(entry.getKey());
			inline_field_values.add(entry.getValue().name);
		}
		for(Entry<String, GraphMetadata> entry : symbolsMetadata.entrySet()){
			inline_field_names.add(entry.getKey());
			inline_field_values.add(entry.getValue().name);
		}
		for(Entry<String, GraphPredicate> entry : symbolsPredicate.entrySet()){
			inline_field_names.add(entry.getKey());
			inline_field_values.add(entry.getValue().predicateRoot.toString());
		}
	}
}
