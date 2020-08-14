package spade.storage.neo4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.storage.Neo4j;
import spade.utility.HelperFunctions;

public class Neo4jQueryEnvironment extends AbstractQueryEnvironment{

	private final String symbolNodeLabel;
	private final String propertyNameIdCounter = "id_counter";
	private final String propertyNameGraphSymbols = "graphs";
	private final String propertyNameMetadataSymbols = "metadatas";
	private final String propertyNamePredicateSymbols = "predicates";

	private final Neo4j storage;
	
	public final String edgeLabelsPropertyName;

	public Neo4jQueryEnvironment(final String baseGraphName, final Neo4j storage, final String edgeLabelsPropertyName,
			final String symbolNodeLabel){
		super(baseGraphName);
		this.storage = storage;
		this.edgeLabelsPropertyName = edgeLabelsPropertyName;
		this.symbolNodeLabel = symbolNodeLabel;
		if(this.storage == null){
			throw new RuntimeException("NULL storage");
		}
		if(HelperFunctions.isNullOrEmpty(this.edgeLabelsPropertyName)){
			throw new RuntimeException("NULL/Empty property name for edge labels: '"+this.edgeLabelsPropertyName+"'.");
		}
		if(HelperFunctions.isNullOrEmpty(this.symbolNodeLabel)){
			throw new RuntimeException("NULL/Empty query node label: '"+this.symbolNodeLabel+"'.");
		}
	}

	////////////////////
	// Setup
	////////////////////

	@Override
	public void createSymbolStorageIfNotPresent(){
		List<Map<String, Object>> result = storage
				.executeQueryForSmallResult("match (v:" + symbolNodeLabel + ") return v limit 1;");

		if(result.size() == 1){
			// Already present
		}else if(result.size() == 0){
			storage.executeQueryForSmallResult("create (x:" + symbolNodeLabel + "{" + propertyNameIdCounter + ":'0', "
					+ propertyNameGraphSymbols + ":'', " + propertyNameMetadataSymbols + ":'', "
					+ propertyNamePredicateSymbols + ":''});");
		}else{
			// More than one node with this label. Error
			throw new RuntimeException("Query storage in undefined state. " + "Expected only one node with label: '"
					+ symbolNodeLabel + "'.");
		}
	}

	@Override
	public void deleteSymbolStorageIfPresent(){
		storage.executeQueryForSmallResult("match (x:" + symbolNodeLabel + ") delete x;");
	}

	private String readSymbolNodePropertyAndValidate(String propertyName){
		List<Map<String, Object>> result = storage.executeQueryForSmallResult(
				"match (v:" + symbolNodeLabel + ") return v." + propertyName + " as " + propertyName + ";");
		if(result.size() == 1){
			Object o = result.get(0).get(propertyName);
			if(o == null){
				throw new RuntimeException("Query symbol storage in undefined state. " + "Expected property '"
						+ propertyName + "' to exist on node with label: '" + symbolNodeLabel + "'.");
			}else{
				return o.toString();
			}
		}else if(result.size() == 0){
			throw new RuntimeException("Query symbol storage in undefined state. " + "Expected property '"
					+ propertyName + "' to exist on node with label: '" + symbolNodeLabel + "'.");
		}else{ // > 1
			throw new RuntimeException("Query symbol storage in undefined state. "
					+ "Expected only one node with label: '" + symbolNodeLabel + "'.");
		}
	}

	private void writeSymbolNodeProperty(String propertyName, String propertyValue){
		storage.executeQuery(
				"match (x:" + symbolNodeLabel + ") " + "set x." + propertyName + "='" + propertyValue + "';");
	}

	private Map<String, String> inflateMapFromString(String propertyName, String str, String listSplit, String keyValueSplit){
		Map<String, String> map = new HashMap<String, String>();
		if(!HelperFunctions.isNullOrEmpty(str)){
			String[] tokens = str.split(listSplit);
			for(String token : tokens){
				String[] keyValue = token.split(keyValueSplit);
				if(keyValue.length == 2){
					String key = keyValue[0].trim();
					String value = keyValue[1].trim();
					if(map.containsKey(key)){
						throw new RuntimeException("Duplicate '"+propertyName+"' symbol key: '"+key+"'");
					}
					map.put(key, value);
				}
			}
		}
		return map;
	}

	private String deflateMapToString(Map<String, String> map, String listJoiner, String keyValueJoiner){
		String str = "";
		for(Map.Entry<String, String> entry : map.entrySet()){
			str += entry.getKey() + keyValueJoiner + entry.getValue() + listJoiner;
		}
		if(!str.isEmpty()){
			str = str.substring(0, str.length() - listJoiner.length());
		}
		return str;
	}

	@Override
	public int readIdCount(){
		String idCountString = readSymbolNodePropertyAndValidate(propertyNameIdCounter);
		return Integer.parseInt(idCountString);
	}

	@Override
	public Map<String, String> readGraphSymbols(){
		String graphSymbolsString = readSymbolNodePropertyAndValidate(propertyNameGraphSymbols);
		return inflateMapFromString(propertyNameGraphSymbols, graphSymbolsString, ",", "=");
	}

	@Override
	public Map<String, String> readMetadataSymbols(){
		String metadataSymbolsString = readSymbolNodePropertyAndValidate(propertyNameMetadataSymbols);
		return inflateMapFromString(propertyNameMetadataSymbols, metadataSymbolsString, ",", "=");
	}

	@Override
	public Map<String, String> readPredicateSymbols(){
		String predicateSymbolsString = readSymbolNodePropertyAndValidate(propertyNamePredicateSymbols);
		return inflateMapFromString(propertyNamePredicateSymbols, predicateSymbolsString, ",", "#");
	}

	@Override
	public void saveIdCounter(int idCounter){
		writeSymbolNodeProperty(propertyNameIdCounter, String.valueOf(idCounter));
	}

	@Override
	public void saveGraphSymbol(String symbol, String graphName, boolean symbolNameWasPresent){
		updateGraphSymbols();
	}

	@Override
	public void saveMetadataSymbol(String symbol, String metadataName, boolean symbolNameWasPresent){
		updateMetadataSymbols();
	}

	@Override
	public void savePredicateSymbol(String symbol, String predicate, boolean symbolNameWasPresent){
		updatePredicateSymbols();
	}

	@Override
	public void deleteGraphSymbol(String symbol){
		updateGraphSymbols();
	}

	@Override
	public void deleteMetadataSymbol(String symbol){
		updateMetadataSymbols();
	}

	@Override
	public void deletePredicateSymbol(String symbol){
		updatePredicateSymbols();
	}

	private void updateGraphSymbols(){
		Map<String, String> map = getCurrentGraphSymbolsStringMap();
		String value = deflateMapToString(map, ",", "=");
		storage.executeQuery(
				"match (x:" + symbolNodeLabel + ") " + "set x." + propertyNameGraphSymbols + "='" + value + "';");
	}

	private void updateMetadataSymbols(){
		Map<String, String> map = getCurrentMetadataSymbolsStringMap();
		String value = deflateMapToString(map, ",", "=");
		storage.executeQuery(
				"match (x:" + symbolNodeLabel + ") " + "set x." + propertyNameMetadataSymbols + "='" + value + "';");
	}

	private void updatePredicateSymbols(){
		Map<String, String> map = getCurrentPredicateSymbolsStringMap();
		String value = deflateMapToString(map, ",", "#");
		storage.executeQuery(
				"match (x:" + symbolNodeLabel + ") " + "set x." + propertyNamePredicateSymbols + "='" + value + "';");
	}
	
	//////////////////////////////////
	
	private Set<String> getAllVertexLabels(){
		return storage.getDatabaseManager().getAllLabels();
	}
	
	private Set<String> getAllEdgeLabels(){
		final Set<String> allEdgeSymbols = new HashSet<String>();
		final List<Map<String, Object>> edgeSymbolsPropertyValues = storage.executeQueryForSmallResult(
				"match ()-[e]->() with distinct e."
						+ edgeLabelsPropertyName + " as "
						+ edgeLabelsPropertyName + " return "
						+ edgeLabelsPropertyName
				);
		for(final Map<String, Object> map : edgeSymbolsPropertyValues){
			final Object object = map.get(edgeLabelsPropertyName); // format of value = '<,a,>(repeat)'
			if(object != null){ // ignore the null i.e. only exists in base
				final String edgeSymbolsList = String.valueOf(object);
				final String[] edgeSymbolsTokens = edgeSymbolsList.split(",");
				for(final String edgeSymbolsToken : edgeSymbolsTokens){
					if(!edgeSymbolsToken.isEmpty()){
						allEdgeSymbols.add(edgeSymbolsToken);
					}
				}
			}
		}
		return allEdgeSymbols;
	}

	@Override
	public final void doGarbageCollection(){
		// Get all non-garbage labels
		final Set<String> referencedGraphNames = new HashSet<String>();
		referencedGraphNames.addAll(getCurrentGraphSymbolsStringMap().values());

		final Set<String> nonGarbageNames = new HashSet<String>();
		nonGarbageNames.addAll(referencedGraphNames); // Don't delete a label if it is reference in the symbol graph map
		nonGarbageNames.add(getBaseGraph().name); // Don't delete the base vertex label i.e. VERTEX but depends on config
		nonGarbageNames.add(symbolNodeLabel); // Don't delete the symbol node label used to store symbol info

		// Get all labels from the storage
		final Set<String> allLabels = getAllVertexLabels();

		// Collect garbage labels
		final Set<String> garbageLabels = new HashSet<String>();
		for(String label : allLabels){ // Collect all labels which have been generated by us
			if(isSPADEGraphOrSPADEMetadataName(label)){
				garbageLabels.add(label);
			}
		}
		garbageLabels.removeAll(nonGarbageNames); // Must never remove the non garbage labels

		try{
			dropVertexLabels(garbageLabels.toArray(new String[]{}));
		}catch(Throwable t){
			logger.log(Level.WARNING, "Failed to delete garbage labels from nodes: " + garbageLabels, t);
		}
		
		// Garbage labels dropped from nodes by now

		// Get all symbols from the property
		final Set<String> allEdgeSymbols = getAllEdgeLabels();

		final Set<String> garbageEdgeSymbols = new HashSet<String>();
		for(String allEdgeSymbol : allEdgeSymbols){
			if(isSPADEGraphOrSPADEMetadataName(allEdgeSymbol)){
				garbageEdgeSymbols.add(allEdgeSymbol);
			}
		}
		garbageEdgeSymbols.removeAll(referencedGraphNames);

		if(!garbageEdgeSymbols.isEmpty()){
			for(String garbageEdgeSymbol : garbageEdgeSymbols){
				try{
					dropEdgeSymbol(garbageEdgeSymbol);
				}catch(Throwable t){
					logger.log(Level.WARNING, "Failed to delete garbage value in '"+edgeLabelsPropertyName+"' key of edges: '" + garbageEdgeSymbol + "'.", t);
				}
			}
		}
	}
	
	public final void dropVertexLabels(String... labels){
		if(labels != null){
			String queryLabels = "";
			for(String label : labels){
				if(!HelperFunctions.isNullOrEmpty(label)){
					queryLabels += ":" + label.trim();
				}
			}
			queryLabels = queryLabels.trim(); // format = ":<a>:<b>"
			
			if(!queryLabels.isEmpty()){
				storage.executeQueryForSmallResult("match(n) remove n" + queryLabels + " ;");
			}
		}
	}
	
	public final void dropEdgeSymbol(String symbol){
		if(!HelperFunctions.isNullOrEmpty(symbol)){
			symbol = symbol.trim();
			String matchClause = "match ()-[e]->()";
			String whereClause = "where e." + edgeLabelsPropertyName + " contains ',"+symbol+",'";
			String setClause = "set e." + edgeLabelsPropertyName + " = replace(e." + edgeLabelsPropertyName+ ", "
					+ "',"+symbol+",', '');";
			String query = matchClause + " " + whereClause + " " + setClause;
			storage.executeQuery(query);
		}
	}
}
