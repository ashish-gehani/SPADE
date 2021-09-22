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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import spade.core.AbstractStorage;
import spade.query.quickgrail.core.EnvironmentVariableManager;
import spade.query.quickgrail.core.GraphDescription;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.GraphStatistic.Interval;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.instruction.DescribeGraph;
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetEdgeEndpoint.Component;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;
import spade.storage.Neo4j;
import spade.utility.HelperFunctions;

public class Neo4jInstructionExecutor extends QueryInstructionExecutor{

	private final Neo4j storage;
	private final Neo4jQueryEnvironment neo4jQueryEnvironment;
	private final String hashKey;
	
	public Neo4jInstructionExecutor(Neo4j storage, Neo4jQueryEnvironment neo4jQueryEnvironment,
			String hashKey){
		this.storage = storage;
		this.neo4jQueryEnvironment = neo4jQueryEnvironment;
		this.hashKey = hashKey;
		if(this.neo4jQueryEnvironment == null){
			throw new IllegalArgumentException("NULL Query Environment");
		}
		if(this.storage == null){
			throw new IllegalArgumentException("NULL storage");
		}
		if(HelperFunctions.isNullOrEmpty(this.hashKey)){
			throw new IllegalArgumentException("NULL/Empty hash key: '"+this.hashKey+"'");
		}
	}
	
	public final Neo4jQueryEnvironment getQueryEnvironment(){
		return neo4jQueryEnvironment;
	}

	@Override
	public AbstractStorage getStorage(){
		return storage;
	}

	@Override
	public void insertLiteralEdge(Graph targetGraph, ArrayList<String> edges){
		List<String> hashes = edges;
		if(hashes == null || hashes.isEmpty()){
			// Empty graph already
		}else{
			String query = "match ()-[e]->() where ";
			String whereClause = "";
			for(String hash : hashes){
				whereClause += "e.`"+hashKey+"`='" + hash + "' or ";
			}
			whereClause = whereClause.substring(0, whereClause.length() - 3);
			query += whereClause;
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ";";
	 		storage.executeQuery(query);
		}
	}

	@Override
	public void insertLiteralVertex(Graph targetGraph, ArrayList<String> vertices){
		List<String> hashes = vertices;
		if(hashes == null || hashes.isEmpty()){
			// Empty graph already
		}else{
			String query = "match (v) where ";
			String whereClause = "";
			for(String hash : hashes){
				whereClause += "v.`"+hashKey+"`='" + hash + "' or ";
			}
			whereClause = whereClause.substring(0, whereClause.length() - 3);
			query += whereClause;
			query += " set v:" + targetGraph.name + ";";
	 		storage.executeQuery(query);
		}
	}

	@Override
	public void createEmptyGraph(Graph graph){
		neo4jQueryEnvironment.dropVertexLabels(graph.name);
		neo4jQueryEnvironment.dropEdgeSymbol(graph.name);
	}

	@Override
	public void distinctifyGraph(Graph targetGraph, Graph sourceGraph){
		unionGraph(targetGraph, sourceGraph);
	}
	
	@Override
	public void getWhereAnnotationsExist(final Graph targetGraph, final Graph subjectGraph,
			final ArrayList<String> annotationNames){
		String query = "";
		query += "match (v:"+subjectGraph.name+") where ";
		final ArrayList<String> annotationKeys = annotationNames;
		
		for(int i = 0; i < annotationKeys.size(); i++){
			final String annotationKey = annotationKeys.get(i);
			query += "exists(v.`" + annotationKey + "`)";
			if(i == annotationKeys.size() - 1){ // is last
				// don't append the 'and'
			}else{
				query += " and ";
			}
		}
		query += " set v:" + targetGraph.name + ";";
 		storage.executeQuery(query);
	}

	@Override
	public void getMatch(final Graph targetGraph, final Graph graph1, final Graph graph2,
			final ArrayList<String> annotationKeys){
		final Graph g1 = createNewGraph();
		getWhereAnnotationsExist(g1, graph1, annotationKeys);
		final Graph g2 = createNewGraph();
		getWhereAnnotationsExist(g2, graph2 , annotationKeys);

		String query = "";
		query += "match (a:" + g1.name + "), (b:" + g2.name + ") where ";

		for(int i = 0; i < annotationKeys.size(); i++){
			final String annotationKey = annotationKeys.get(i);
			query += "(";
			query += "(a.`" + annotationKey + "` = b.`" + annotationKey + "`)";
			//query += " or (not exists(a.`" + annotationKey + "`) and not exists(b.`" + annotationKey + "`))";
			query += ")";
			if(i == annotationKeys.size() - 1){ // is last
				// don't append the 'and'
			}else{
				query += " and ";
			}
		}
		query += " set a:" + targetGraph.name;
		query += " set b:" + targetGraph.name + ";";
 		storage.executeQuery(query);
	}

	private String buildComparison(
			String annotationKey, PredicateOperator operator,
			String annotationValue){
		String query = "`" + annotationKey + "` ";
		switch(operator){
			case EQUAL: query += "="; break;
			case GREATER: query += ">"; break;
			case GREATER_EQUAL: query += ">="; break;
			case LESSER: query += "<"; break;
			case LESSER_EQUAL: query += "<="; break;
			case NOT_EQUAL: query += "<>"; break;
			case REGEX: query += "=~"; break;
			case LIKE:{
				query += "=~";
				annotationValue = annotationValue.replace("%", ".*");
			}
			break;
			default: throw new RuntimeException("Unexpected comparison operator");
		}
		annotationValue = annotationValue.replace("'", "\\'");
		query += " '" + annotationValue + "'";
		return query;
	}
	
	private String buildWildCardComparison(
			String objectAlias, PredicateOperator operator,
			String annotationValue){
		String query = " any(k in keys("+objectAlias+") where " + objectAlias + "[k] ";
		switch(operator){
			case EQUAL: query += "="; break;
			case GREATER: query += ">"; break;
			case GREATER_EQUAL: query += ">="; break;
			case LESSER: query += "<"; break;
			case LESSER_EQUAL: query += "<="; break;
			case NOT_EQUAL: query += "<>"; break;
			case REGEX: query += "=~"; break;
			case LIKE:{
				query += "=~";
				annotationValue = annotationValue.replace("%", ".*");
			}
			break;
			default: throw new RuntimeException("Unexpected comparison operator");
		}
		annotationValue = annotationValue.replace("'", "\\'");
		query += " '" + annotationValue + "')";
		return query;
	}
	
	@Override
	public void getVertex(Graph targetGraph, Graph subjectGraph, String annotationKey, PredicateOperator operator,
			String annotationValue, final boolean hasArguments){
		String query = "";
		query += "match (v:" + subjectGraph.name + ")";
		if(hasArguments){
			if(annotationKey.equals("*")){
				query += " where " + buildWildCardComparison("v", operator, annotationValue);	
			}else{
				query += " where v." + buildComparison(annotationKey, operator, annotationValue);
			}
		}
		query += " set v:" + targetGraph.name + ";";
 		storage.executeQuery(query);
	}

	@Override
	public ResultTable evaluateQuery(String nativeQuery){
		List<Map<String, Object>> result = storage.executeQueryForSmallResult(nativeQuery);
		
		int cellCount = 0;
		
		ResultTable table = new ResultTable();
	
		Map<String, Object> treeMapForKeys = new TreeMap<String, Object>();
		for(Map<String, Object> map : result){
			Map<String, Object> treeMap = new TreeMap<String, Object>(map);
			ResultTable.Row row = new ResultTable.Row();
			for(String key : treeMap.keySet()){
				treeMapForKeys.put(key, null);
				row.add(treeMap.get(key));
				cellCount++;
			}
			table.addRow(row);
		}
		
		Schema schema = new Schema();
		if(cellCount == 0){
			schema.addColumn("NO RESULT!", StringType.GetInstance());
		}else{
			for(String key : treeMapForKeys.keySet()){
				schema.addColumn(key, StringType.GetInstance());
			}
		}
		
		table.setSchema(schema);
		return table;
	}

	private String buildSubqueryForUpdatingEdgeSymbols(String edgeAlias, String targetGraphName){
		final String edgeProperty = edgeAlias + ".`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		targetGraphName = "'," + targetGraphName + ",'";
		String query = "set " + edgeProperty + " = "
				+ "case "
				+ "when not exists(" + edgeProperty + ") then " + targetGraphName + " " // set
				+ "when " + edgeProperty + " contains " + targetGraphName + " then " + edgeProperty + " " // leave as is
				+ "else " + edgeProperty + " + " + targetGraphName + " end"; // append
		return query;
	}
	
	@Override
	public void collapseEdge(Graph targetGraph, Graph sourceGraph, ArrayList<String> fields){		
		String fieldsString = "";
		int xxx = 0;
		for(String field : fields){
			fieldsString += "e0.`" + field + "` as x" + (xxx++) + " , ";
		}
		
		String vertexQuery = "match (v:" + sourceGraph.name + ") set v:" + targetGraph.name + ";";
		
		final String edgeProperty = "e0.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match (x)-[e0]->(y) ";
		if(!neo4jQueryEnvironment.isBaseGraph(sourceGraph)){
			query += "where " + edgeProperty + " contains ',"+sourceGraph.name+",'";
		}
		query += " with distinct x as src, y as dst, " + fieldsString + " min(e0) as e ";
		query += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ";";
				
		storage.executeQuery(vertexQuery);
		storage.executeQuery(query);
	}
	
	@Override
	public void getEdge(Graph targetGraph, Graph subjectGraph, String annotationKey, PredicateOperator operator,
			String annotationValue, final boolean hasArguments){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "";
		query += "match ()-[e]->() ";
		if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
			query += "where " + edgeProperty + " contains ',"+subjectGraph.name+",' ";
			if(hasArguments){
				if(annotationKey.equals("*")){
					query += " where " + buildWildCardComparison("v", operator, annotationValue);	
				}else{
					query += "and e." + buildComparison(annotationKey, operator, annotationValue);
				}
			}
		}else{
			if(hasArguments){
				if(annotationKey.equals("*")){
					query += " where " + buildWildCardComparison("v", operator, annotationValue);	
				}else{
					query += "where e." + buildComparison(annotationKey, operator, annotationValue);
				}
			}
		}
		query += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ";";
		
		storage.executeQuery(query);
	}

	@Override
	public void getEdgeEndpoint(Graph targetGraph, Graph subjectGraph, Component component){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "";
		query += "match (a)-[e]->(b) ";
		if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
			query += "where " + edgeProperty + " contains ',"+subjectGraph.name+",' ";
		}
		if(component.equals(GetEdgeEndpoint.Component.kSource)
				|| component.equals(GetEdgeEndpoint.Component.kBoth)){
			query += "set a:" + targetGraph.name + " ";
		}
		if(component.equals(GetEdgeEndpoint.Component.kDestination)
				|| component.equals(GetEdgeEndpoint.Component.kBoth)){
			query += "set b:" + targetGraph.name + " ";
		}
		
		query += ";";
		
		storage.executeQuery(query);
	}

	@Override
	public void intersectGraph(Graph outputGraph, Graph lhsGraph, Graph rhsGraph){
		String vertexQuery = "";
		vertexQuery += "match (x:" + lhsGraph.name + ":" + rhsGraph.name + ") ";
		vertexQuery += "set x:" + outputGraph.name + ";";
		
		storage.executeQuery(vertexQuery);
		
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String edgeQuery = "";
		edgeQuery = "match ()-[e]->()";
		if(neo4jQueryEnvironment.isBaseGraph(lhsGraph) && neo4jQueryEnvironment.isBaseGraph(rhsGraph)){
			// no where needed
		}else if(!neo4jQueryEnvironment.isBaseGraph(lhsGraph) && neo4jQueryEnvironment.isBaseGraph(rhsGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + lhsGraph.name + ",'";
		}else if(neo4jQueryEnvironment.isBaseGraph(lhsGraph) && !neo4jQueryEnvironment.isBaseGraph(rhsGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + rhsGraph.name + ",'";
		}else{
			edgeQuery += " where " + edgeProperty + " contains '," + lhsGraph.name + ",'";
			edgeQuery += " and " + edgeProperty + " contains '," + rhsGraph.name + ",'";
		}
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", outputGraph.name);
		
		storage.executeQuery(edgeQuery);
	}

	@Override
	public void limitGraph(Graph targetGraph, Graph sourceGraph, int limit){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String vertexQuery = 
				"match (v:" + sourceGraph.name + ") with v order by id(v) asc limit " 
				+ limit + " set v:" + targetGraph.name + ";";
		String edgeQuery = "match ()-[e]->()";
		if(!neo4jQueryEnvironment.isBaseGraph(sourceGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + sourceGraph.name + ",'";
		}
		edgeQuery += " with e order by id(e) asc limit " + limit;
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ";";
		storage.executeQuery(vertexQuery);
		storage.executeQuery(edgeQuery);
	}
	
	@Override
	public GraphStatistic.Count getGraphCount(final Graph graph){
		final long vertices;
		final long edges;
		List<Map<String, Object>> result = storage
				.executeQueryForSmallResult("match (v:" + graph.name + ") return count(v) as vcount;");
		if(result.size() > 0){
			vertices = Long.parseLong(String.valueOf(result.get(0).get("vcount")));
		}else{
			vertices = 0;
		}
		if(neo4jQueryEnvironment.isBaseGraph(graph)){
			result = storage.executeQueryForSmallResult("match ()-[e]->() return count(e) as ecount;");
		}else{
			final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
			result = storage.executeQueryForSmallResult("match ()-[e]->() " + "where " + edgeProperty + " contains ',"
					+ graph.name + ",' " + "return count(e) as ecount;");
		}
		if(result.size() > 0){
			edges = Long.parseLong(String.valueOf(result.get(0).get("ecount")));
		}else{
			edges = 0;
		}
		return new GraphStatistic.Count(vertices, edges);
	}

	@Override
	public long getGraphStatisticSize(final Graph graph, final ElementType elementType, final String annotationKey){
		switch(elementType){
			case VERTEX:{
				final List<Map<String, Object>> result = storage.executeQueryForSmallResult(
						"match (v:" + graph.name + ") where v.`" + annotationKey + "` is not null return count(v) as vcount;"
						);
				if(result.size() > 0){
					return Long.parseLong(String.valueOf(result.get(0).get("vcount")));
				}else{
					return 0;
				}
			}
			case EDGE:{
				final List<Map<String, Object>> result;
				if(neo4jQueryEnvironment.isBaseGraph(graph)){
					result = storage.executeQueryForSmallResult(
							"match ()-[e]->() where e.`" + annotationKey + "` is not null return count(e) as ecount;"
							);
				}else{
					final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
					result = storage.executeQueryForSmallResult(
							"match ()-[e]->() " 
							+ "where " + edgeProperty + " contains '," + graph.name + ",' "
							+ "and e.`" + annotationKey + "` is not null "
							+ "return count(e) as ecount;");
				}
				if(result.size() > 0){
					return Long.parseLong(String.valueOf(result.get(0).get("ecount")));
				}else{
					return 0;
				}
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}
	}

	@Override
	public GraphStatistic.Distribution getGraphDistribution(final Graph graph, final ElementType elementType,
			final String annotationKey, final Integer binCount){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.Distribution();
		}

		final String finalAnnotationKey;
		final String minMaxQuery;
		switch(elementType){
			case VERTEX:{
				finalAnnotationKey = "toInteger(v." + annotationKey + ")";
				minMaxQuery = "MATCH (v:" + graph.name + ") RETURN"
						+ " MIN(" + finalAnnotationKey + ") AS min,"
						+ " MAX(" + finalAnnotationKey + ") AS max;";
				break;
			}
			case EDGE:{
				finalAnnotationKey = "toInteger(e." + annotationKey + ")";
				final String returnStatement = " RETURN"
						+ " MIN(" + finalAnnotationKey + ") AS min,"
						+ " MAX(" + finalAnnotationKey + ") AS max;";
				if(neo4jQueryEnvironment.isBaseGraph(graph)){
					minMaxQuery = "MATCH ()-[e]->() " + returnStatement;
				}else{
					final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
					minMaxQuery = "match ()-[e]->()" + " where "
							+ edgeProperty + " contains '," + graph.name + ",' "
							+ returnStatement;
				}
				break;
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}

		final List<Map<String, Object>> minMaxResult = storage.executeQueryForSmallResult(minMaxQuery);
		if(minMaxResult.size() == 0){
			throw new RuntimeException("Failed to get min and max for: '" + annotationKey + "'");
		}

		final Double min = Double.parseDouble(String.valueOf(minMaxResult.get(0).get("min")));
		final Double max = Double.parseDouble(String.valueOf(minMaxResult.get(0).get("max")));

		final double range = max - min + 1;
		final double step = range / binCount;
		String query;
		switch(elementType){
			case VERTEX:{
				query = "match (v:" + graph.name + ")";
				break;
			}
			case EDGE:{
				if(neo4jQueryEnvironment.isBaseGraph(graph)){
					query = "match ()-[e]->()";
				}else{
					final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
					query = "match ()-[e]->()" + "where " + edgeProperty + " contains '," + graph.name + ",'";
				}
				break;
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}

		final Map<String, Interval> nameToInterval = new TreeMap<>();

		query += " RETURN ";
		final String countQuery = "SUM(CASE WHEN "
				+ finalAnnotationKey + " >= %s AND "
				+ finalAnnotationKey + " < %s "
				+ "THEN 1 ELSE 0 END) AS `%s`,";
		double begin = min;
		while(begin + step < max){
			final String columnName = String.valueOf(nameToInterval.size());
			query += String.format(countQuery, 
					begin, begin + step, 
					columnName);
			nameToInterval.put(columnName, new Interval(begin, begin +  step));
			begin += step;
		}
		final String finalColumnName = String.valueOf(nameToInterval.size());
		final String finalCountQuery = "SUM(CASE WHEN "
				+ finalAnnotationKey + " >= %s AND "
				+ finalAnnotationKey + " <= %s "
				+ "THEN 1 ELSE 0 END) AS `%s`";
		query += String.format(finalCountQuery, 
				begin, max, 
				finalColumnName);
		nameToInterval.put(finalColumnName, new Interval(begin, max));

		final List<Map<String, Object>> result = storage.executeQueryForSmallResult(query);
		final SortedMap<Interval, Double> distribution = new TreeMap<>();
		if(result.size() > 0){
			final Map<String, Object> data = result.get(0);
			for(final String key : data.keySet()){
				final Object valueObject = data.get(key);
				if(valueObject != null){
					final String value = String.valueOf(valueObject);
					final Interval distributionKey = nameToInterval.get(key);
					final Double distributionValue = Double.parseDouble(value);
					distribution.put(distributionKey, distributionValue);
				}
			}
		}

		final GraphStatistic.Distribution graphDistribution = new GraphStatistic.Distribution(distribution);
		return graphDistribution;
	}

	@Override
	public GraphStatistic.StandardDeviation getGraphStandardDeviation(final Graph graph, final ElementType elementType,
			final String annotationKey){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.StandardDeviation();
		}

		final String query;
		switch(elementType){
			case VERTEX:{
				query = "match (v:" + graph.name + ") return stDev(toInteger(v." + annotationKey + ")) as std;";
				break;
			}
			case EDGE:{
				if(neo4jQueryEnvironment.isBaseGraph(graph)){
					query = "match ()-[e]->() return stDev(toInteger(e." + annotationKey + ")) as std;";
				}else{
					final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
					query = "match ()-[e]->()"
							+ "where " + edgeProperty + " contains '," + graph.name + ",' "
							+ "return stDev(toInteger(e." + annotationKey + ")) as std;";
				}
				break;
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}

		final List<Map<String, Object>> result = storage.executeQueryForSmallResult(query);
		final double stdDev;
		if(result.size() > 0){
			stdDev = Double.parseDouble(String.valueOf(result.get(0).get("std")));
		}else{
			stdDev = 0;
		}

		final GraphStatistic.StandardDeviation graphStdDev = new GraphStatistic.StandardDeviation(stdDev);
		return graphStdDev;
	}

	@Override
	public GraphStatistic.Mean getGraphMean(final Graph graph, final ElementType elementType, final String annotationKey){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.Mean();
		}

		final String query;
		switch(elementType){
			case VERTEX:{
				query = "match (v:" + graph.name + ") return AVG(toInteger(v." + annotationKey + ")) as mean;";
				break;
			}
			case EDGE:{
				if(neo4jQueryEnvironment.isBaseGraph(graph)){
					query = "match ()-[e]->() return AVG(toInteger(e." + annotationKey + ")) as mean;";
				}else{
					final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
					query = "match ()-[e]->()" 
							+ "where " + edgeProperty + " contains '," + graph.name + ",' "
							+ "return AVG(toInteger(e." + annotationKey + ")) as mean;";
				}
				break;
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}

		final List<Map<String, Object>> result = storage.executeQueryForSmallResult(query);
		final Double mean;
		if(result.size() > 0){
			mean = Double.parseDouble(String.valueOf(result.get(0).get("mean")));
		}else{
			mean = 0.0;
		}

		final GraphStatistic.Mean graphMean = new GraphStatistic.Mean(mean);
		return graphMean;
	}

	@Override
	public GraphStatistic.Histogram getGraphHistogram(final Graph graph, final ElementType elementType, final String annotationKey){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.Histogram();
		}

		final String query;
		switch(elementType){
			case VERTEX:{
				query = "match (v:" + graph.name + ") return v." + annotationKey + " as ann, count(*) as cnt;";
				break;
			}
			case EDGE:{
				if(neo4jQueryEnvironment.isBaseGraph(graph)){
					query = "match ()-[e]->() return e." + annotationKey + " as ann, count(*) as cnt;";
				}else{
					final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
					query = "match ()-[e]->()" 
							+ "where " + edgeProperty + " contains '," + graph.name + ",' " 
							+ "return e." + annotationKey + " as ann, count(*) as cnt;";
				}
				break;
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}

		final List<Map<String, Object>> result = storage.executeQueryForSmallResult(query);
		final SortedMap<String, Double> histogram = new TreeMap<>();
		if(result.size() > 0){
			for(final Map<String, Object> map : result){
				final Object valueObject = map.get("ann");
				final Object countObject = map.get("cnt");
				if(valueObject != null && countObject != null){
					final String value = String.valueOf(valueObject);
					final Double count = Double.parseDouble(String.valueOf(countObject));
					histogram.put(value, count);
				}
			}
		}

		GraphStatistic.Histogram graphHistogram = new GraphStatistic.Histogram(histogram);
		return graphHistogram;
	}

	@Override
	public void subtractGraph(Graph outputGraph, Graph minuendGraph, Graph subtrahendGraph, Graph.Component component){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		if(neo4jQueryEnvironment.isBaseGraph(minuendGraph) && neo4jQueryEnvironment.isBaseGraph(subtrahendGraph)){
			// no resulting vertices and edge since both are base
		}else if(!neo4jQueryEnvironment.isBaseGraph(minuendGraph) && neo4jQueryEnvironment.isBaseGraph(subtrahendGraph)){
			// no resulting vertices and edges since the subtrahend is base
		}else if(neo4jQueryEnvironment.isBaseGraph(minuendGraph) && !neo4jQueryEnvironment.isBaseGraph(subtrahendGraph)){
			String vertexQuery = "match (n:" + minuendGraph.name + ") where not '" + subtrahendGraph.name + 
					"' in labels(n) set n:" + outputGraph.name + ";";
			String edgeQuery = "match ()-[e]->()";
			edgeQuery += " where not " + edgeProperty + " contains '," + subtrahendGraph.name + ",'";
			edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", outputGraph.name);
			if(component == null || component == Graph.Component.kVertex){
				storage.executeQuery(vertexQuery);
			}
			if(component == null || component == Graph.Component.kEdge){
				storage.executeQuery(edgeQuery);
			}
		}else{
			String vertexQuery = "match (n:" + minuendGraph.name + ") where not '" + subtrahendGraph.name + 
					"' in labels(n) set n:" + outputGraph.name + ";";
			
			String edgeQuery = "match ()-[e]->()";
			edgeQuery += " where " + edgeProperty + " contains '," + minuendGraph.name + ",'";
			edgeQuery += " and not " + edgeProperty + " contains '," + subtrahendGraph.name + ",'";
			edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", outputGraph.name);
			if(component == null || component == Graph.Component.kVertex){
				storage.executeQuery(vertexQuery);
			}
			if(component == null || component == Graph.Component.kEdge){
				storage.executeQuery(edgeQuery);
			}
		}
	}

	@Override
	public void unionGraph(Graph targetGraph, Graph sourceGraph){
		String edgeQuery = "match ()-[e]->()";
		String vertexQuery = "match (v:" + sourceGraph.name + ") set v:" + targetGraph.name + ";";
		if(!neo4jQueryEnvironment.isBaseGraph(sourceGraph)){
			final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
			edgeQuery += " where " + edgeProperty + " contains '," + sourceGraph.name + ",'";
		}
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name);
		
		storage.executeQuery(edgeQuery);
		storage.executeQuery(vertexQuery);
	}

	@Override
	public void getAdjacentVertex(Graph targetGraph, Graph subjectGraph, Graph sourceGraph, GetLineage.Direction direction){
		// TODO rename to get adjacent graph
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		if(direction.equals(GetLineage.Direction.kAncestor) || direction.equals(GetLineage.Direction.kBoth)){
			String query = "match (a:"+sourceGraph.name+":"+subjectGraph.name+")-[e]->"
					+ "(b:"+subjectGraph.name+")";
			if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
				query += " where " + edgeProperty + " contains '," + subjectGraph.name + ",'";
			}
			query += " set a:" + targetGraph.name;
			query += " set b:" + targetGraph.name;
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ";";
			storage.executeQuery(query);
		}
		
		if(direction.equals(GetLineage.Direction.kDescendant) || direction.equals(GetLineage.Direction.kBoth)){
			String query = "match (a:"+subjectGraph.name+")-[e]->"
					+ "(b:"+sourceGraph.name+":"+subjectGraph.name+")";
			if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
				query += " where " + edgeProperty + " contains '," + subjectGraph.name + ",'";
			}
			query += " set a:" + targetGraph.name;
			query += " set b:" + targetGraph.name;
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ";";
			storage.executeQuery(query);
		}
	}
	
	@Override
	public GraphDescription describeGraph(DescribeGraph instruction){
		if(instruction.graph == null){
			throw new RuntimeException("NULL graph");
		}
		if(instruction.elementType == null){
			throw new RuntimeException("NULL element type");
		}
		switch(instruction.elementType){
			case VERTEX:
			case EDGE:
				break;
			default: throw new RuntimeException("Unhandled element type: " + instruction.elementType);
		}
		
		if(instruction.all){
			String query;
			switch(instruction.elementType){
				case VERTEX:
					query = "match (v:" + instruction.graph.name + ") with keys(v) as keys unwind keys as rowsofkeys return distinct rowsofkeys";
					break;
				case EDGE:
					final String relationshipProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
					query = "match ()-[e]->()";
					if(!neo4jQueryEnvironment.isBaseGraph(instruction.graph)){
						query += " where " + relationshipProperty + " contains '," + instruction.graph.name + ",'";
					}
					query += " with keys(e) as keys unwind keys as rowsofkeys return distinct rowsofkeys";
					break;
				default: throw new RuntimeException("Unhandled element type: " + instruction.elementType);
			}
			
			query += " order by rowsofkeys";
			if(instruction.limit != null){
				query += " limit " + instruction.limit;
			}
			
			final Set<String> annotations = new HashSet<String>();
			final List<Map<String, Object>> queryResult = storage.executeQueryForSmallResult(query);
			for(Map<String, Object> map : queryResult){
				final Object value = map.get("rowsofkeys");
				if(value != null){
					final String str = String.valueOf(value); 
					if(!str.equals(neo4jQueryEnvironment.edgeLabelsPropertyName)){
						annotations.add(str);
					}
				}
			}
			
			final GraphDescription desc = new GraphDescription(instruction.elementType);
			desc.addAnnotations(annotations);
			return desc;
		}else{
			if(instruction.annotationName == null){
				throw new RuntimeException("NULL annotation name");
			}
			
			if(instruction.descriptionType == null){
				throw new RuntimeException("NULL annotation description type");
			}
			
			final String alias = "x";
			String query;
			switch(instruction.elementType){
				case VERTEX:
					query = "match ("+alias+":" + instruction.graph.name + ") where exists("+alias+".`" + instruction.annotationName + "`) ";
					break;
				case EDGE:
					final String relationshipProperty = alias + ".`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
					query = "match ()-["+alias+"]->() where exists("+alias+".`" + instruction.annotationName + "`) ";
					if(!neo4jQueryEnvironment.isBaseGraph(instruction.graph)){
						query += "and " + relationshipProperty + " contains '," + instruction.graph.name + ",' ";
					}
					break;
				default: throw new RuntimeException("Unhandled element type: " + instruction.elementType);
			}
			
			switch(instruction.descriptionType){
				case COUNT:{
					query += "with " + alias + ".`"+instruction.annotationName+"` as spadevalues unwind spadevalues as rows "
							+ "return distinct rows as spadevalue, count(rows) as spadevaluecount";
					query += " order by spadevalue";
					if(instruction.limit != null){
						query += " limit " + instruction.limit;
					}
					final List<Map<String, Object>> result = storage.executeQueryForSmallResult(query);
					final GraphDescription desc = new GraphDescription(instruction.elementType, instruction.annotationName, 
							instruction.descriptionType);
					for(Map<String, Object> map : result){
						final Object valueObject = map.get("spadevalue");
						final Object valueCountObject = map.get("spadevaluecount");
						if(valueObject != null && valueCountObject != null){
							desc.putValueToCount(String.valueOf(valueObject), (long)valueCountObject);
						}
					}
					return desc;
				}
				case MINMAX:{
					query += "return min(" + alias + ".`" + instruction.annotationName + "`) as spademin, "
							+ "max(" + alias + ".`" + instruction.annotationName + "`) as spademax;";
					final List<Map<String, Object>> result = storage.executeQueryForSmallResult(query);
					String minValue = null, maxValue = null;
					if(result.size() > 0){
						final Object minObject = result.get(0).get("spademin");
						final Object maxObject = result.get(0).get("spademax");
						if(minObject != null){
							minValue = String.valueOf(minObject);
						}else{
							minValue = EnvironmentVariableManager.getUndefinedConstant();
						}
						if(maxObject != null){
							maxValue = String.valueOf(maxObject);
						}else{
							maxValue = EnvironmentVariableManager.getUndefinedConstant();
						}
					}
					final GraphDescription desc = new GraphDescription(instruction.elementType, instruction.annotationName, 
							instruction.descriptionType);
					desc.setMinMax(minValue, maxValue);
					return desc;
				}
				default: throw new RuntimeException("Unhandled description type: " + instruction.descriptionType);
			}
		}
	}
	
	@Override
	public Map<String, Map<String, String>> exportVertices(final Graph targetGraph){
		String nodesQuery = "match (v:" + targetGraph.name + ") return v;";
		return storage.readHashToVertexMap("v", nodesQuery);
	}
	
	@Override
	public Set<QueriedEdge> exportEdges(final Graph targetGraph){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String edgeQuery = "match ()-[e]->()";
		if(!neo4jQueryEnvironment.isBaseGraph(targetGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + targetGraph.name + ",'";
		}
		edgeQuery += " return e;";
		
		return storage.readEdgeSet("e", edgeQuery);
	}
	
	@Override
	public void getLineage(Graph targetGraph, Graph subjectGraph, Graph startGraph, int depth, Direction direction){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		if(direction.equals(GetLineage.Direction.kAncestor) || direction.equals(GetLineage.Direction.kBoth)){
			String query = "match ";
			query += "p=(a:"+startGraph.name+":"+subjectGraph.name+")"
					+ "-[e0*0.."+depth+"]->"
					+ "(b:"+subjectGraph.name+") ";
			if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
				query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + subjectGraph.name + ",') ";
			}
			query += " foreach (n in nodes(p) | set n:" + targetGraph.name + ") ";
			query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ");";
			storage.executeQuery(query);
		}
		
		if(direction.equals(GetLineage.Direction.kDescendant) || direction.equals(GetLineage.Direction.kBoth)){
			String query = "match ";
			query += "p=(a:"+subjectGraph.name+")"
					+ "-[e0*0.."+depth+"]->"
					+ "(b:"+startGraph.name+":"+subjectGraph.name+") ";
			if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
				query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + subjectGraph.name + ",') ";
			}
			query += " foreach (n in nodes(p) | set n:" + targetGraph.name + ") ";
			query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ");";
			storage.executeQuery(query);
		}
	}

	@Override
	public void getSimplePath(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match ";
		query += "p=(a:"+srcGraph.name+":"+subjectGraph.name+")"
				+ "-[e0*0.."+maxDepth+"]->"
				+ "(b:"+dstGraph.name+":"+subjectGraph.name+") ";
		if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ");";
		storage.executeQuery(query);
	}
	
	@Override
	public void getLink(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match ";
		query += "p=(a:"+srcGraph.name+":"+subjectGraph.name+")"
				+ "-[e0*0.."+maxDepth+"]->"
				+ "(b:"+dstGraph.name+":"+subjectGraph.name+") ";
		if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ");";
		storage.executeQuery(query);
		
		query = "match ";
		query += "p=(a:"+dstGraph.name+":"+subjectGraph.name+")"
				+ "-[e0*0.."+maxDepth+"]->"
				+ "(b:"+srcGraph.name+":"+subjectGraph.name+") ";
		if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ");";
		storage.executeQuery(query);
	}

	@Override
	public void getShortestPath(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match ";
		query += "p=shortestPath((a:"+srcGraph.name+":"+subjectGraph.name+")"
				+ "-[e0*0.."+maxDepth+"]->"
				+ "(b:"+dstGraph.name+":"+subjectGraph.name+")) ";
		if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ");";
		storage.executeQuery(query);
	}

	@Override
	public void getSubgraph(Graph targetGraph, Graph subjectGraph, Graph skeletonGraph){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String vertexQuery = 
				"match (n:" + skeletonGraph.name + ":" + subjectGraph.name + ") "
				+ "set n:" + targetGraph.name + ";";
		
		String edgeQuery0 = "";
		edgeQuery0 += "match ()-[e]->(b:"+subjectGraph.name+")";
		if(!neo4jQueryEnvironment.isBaseGraph(skeletonGraph)){
			edgeQuery0 += " where " + edgeProperty + " contains '," + skeletonGraph.name + ",'";
		}
		edgeQuery0 += " set b:" + targetGraph.name + ";";
		
		String edgeQuery1 = "";
		edgeQuery1 += "match (a:"+subjectGraph.name+")-[e]->()";
		if(!neo4jQueryEnvironment.isBaseGraph(skeletonGraph)){
			edgeQuery1 += " where " + edgeProperty + " contains '," + skeletonGraph.name + ",'";
		}
		edgeQuery1 += " set a:" + targetGraph.name + ";";
		
		String edgeQuery2 = "";
		edgeQuery2 += "match (a:"+targetGraph.name+")-[e]->(b:"+targetGraph.name+")";
		if(!neo4jQueryEnvironment.isBaseGraph(subjectGraph)){
			edgeQuery2 += " where " + edgeProperty + " contains '," + subjectGraph.name + ",'";
		}
		edgeQuery2 += " " + buildSubqueryForUpdatingEdgeSymbols("e", targetGraph.name) + ";";
		
		// order matters
		storage.executeQuery(vertexQuery);
		storage.executeQuery(edgeQuery0);
		storage.executeQuery(edgeQuery1);
		storage.executeQuery(edgeQuery2);
	}

	@Override
	public void createEmptyGraphMetadata(GraphMetadata metadata){
	}

	@Override
	public void overwriteGraphMetadata(GraphMetadata targetMetadata, GraphMetadata lhsMetadata,
			GraphMetadata rhsMetadata){
	}

	@Override
	public void setGraphMetadata(GraphMetadata targetMetadata, SetGraphMetadata.Component component, Graph sourceGraph, String name,
			String value){
	}

}
