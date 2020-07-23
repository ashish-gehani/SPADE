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
import java.util.TreeMap;

import spade.core.AbstractStorage;
import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.core.GraphDescription;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DescribeGraph;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetMatch;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSimplePath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.GetWhereAnnotationsExist;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
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
	public void insertLiteralEdge(InsertLiteralEdge instruction){
		List<String> hashes = instruction.getEdges();
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
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
	 		storage.executeQuery(query);
		}
	}

	@Override
	public void insertLiteralVertex(InsertLiteralVertex instruction){
		List<String> hashes = instruction.getVertices();
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
			query += " set v:" + instruction.targetGraph.name + ";";
	 		storage.executeQuery(query);
		}
	}

	@Override
	public void createEmptyGraph(CreateEmptyGraph instruction){
		neo4jQueryEnvironment.dropVertexLabels(instruction.graph.name);
		neo4jQueryEnvironment.dropEdgeSymbol(instruction.graph.name);
	}

	@Override
	public void distinctifyGraph(DistinctifyGraph instruction){
		unionGraph(new UnionGraph(instruction.targetGraph, instruction.sourceGraph));
	}
	
	@Override
	public void getWhereAnnotationsExist(final GetWhereAnnotationsExist instruction){
		String query = "";
		query += "match (v:"+instruction.subjectGraph.name+") where ";
		final ArrayList<String> annotationKeys = instruction.getAnnotationKeys();
		
		for(int i = 0; i < annotationKeys.size(); i++){
			final String annotationKey = annotationKeys.get(i);
			query += "exists(v.`" + annotationKey + "`)";
			if(i == annotationKeys.size() - 1){ // is last
				// don't append the 'and'
			}else{
				query += " and ";
			}
		}
		query += " set v:" + instruction.targetGraph.name + ";";
 		storage.executeQuery(query);
	}

	@Override
	public void getMatch(final GetMatch instruction){
		final Graph g1 = createNewGraph();
		getWhereAnnotationsExist(new GetWhereAnnotationsExist(g1, instruction.graph1, instruction.getAnnotationKeys()));
		final Graph g2 = createNewGraph();
		getWhereAnnotationsExist(new GetWhereAnnotationsExist(g2, instruction.graph2 , instruction.getAnnotationKeys()));

		String query = "";
		query += "match (a:" + g1.name + "), (b:" + g2.name + ") where ";

		ArrayList<String> annotationKeys = instruction.getAnnotationKeys();

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
		query += " set a:" + instruction.targetGraph.name;
		query += " set b:" + instruction.targetGraph.name + ";";
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
	public void getVertex(GetVertex instruction){
		String query = "";
		query += "match (v:" + instruction.subjectGraph.name + ")";
		if(instruction.hasArguments()){
			if(instruction.annotationKey.equals("*")){
				query += " where " + buildWildCardComparison("v", instruction.operator, instruction.annotationValue);	
			}else{
				query += " where v." + buildComparison(instruction.annotationKey, instruction.operator, instruction.annotationValue);
			}
		}
		query += " set v:" + instruction.targetGraph.name + ";";
 		storage.executeQuery(query);
	}

	@Override
	public ResultTable evaluateQuery(EvaluateQuery instruction){
		List<Map<String, Object>> result = storage.executeQueryForSmallResult(instruction.nativeQuery);
		
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
	public void collapseEdge(CollapseEdge instruction){		
		String fieldsString = "";
		int xxx = 0;
		for(String field : instruction.getFields()){
			fieldsString += "e0.`" + field + "` as x" + (xxx++) + " , ";
		}
		
		String vertexQuery = "match (v:" + instruction.sourceGraph.name + ") set v:" + instruction.targetGraph.name + ";";
		
		final String edgeProperty = "e0.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match (x)-[e0]->(y) ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.sourceGraph)){
			query += "where " + edgeProperty + " contains ',"+instruction.sourceGraph.name+",'";
		}
		query += " with distinct x as src, y as dst, " + fieldsString + " min(e0) as e ";
		query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
				
		storage.executeQuery(vertexQuery);
		storage.executeQuery(query);
	}
	
	@Override
	public void getEdge(GetEdge instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "";
		query += "match ()-[e]->() ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += "where " + edgeProperty + " contains ',"+instruction.subjectGraph.name+",' ";
			if(instruction.hasArguments()){
				if(instruction.annotationKey.equals("*")){
					query += " where " + buildWildCardComparison("v", instruction.operator, instruction.annotationValue);	
				}else{
					query += "and e." + buildComparison(instruction.annotationKey, instruction.operator, instruction.annotationValue);
				}
			}
		}else{
			if(instruction.hasArguments()){
				if(instruction.annotationKey.equals("*")){
					query += " where " + buildWildCardComparison("v", instruction.operator, instruction.annotationValue);	
				}else{
					query += "where e." + buildComparison(instruction.annotationKey, instruction.operator, instruction.annotationValue);
				}
			}
		}
		query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
		
		storage.executeQuery(query);
	}

	@Override
	public void getEdgeEndpoint(GetEdgeEndpoint instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "";
		query += "match (a)-[e]->(b) ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += "where " + edgeProperty + " contains ',"+instruction.subjectGraph.name+",' ";
		}
		if(instruction.component.equals(GetEdgeEndpoint.Component.kSource)
				|| instruction.component.equals(GetEdgeEndpoint.Component.kBoth)){
			query += "set a:" + instruction.targetGraph.name + " ";
		}
		if(instruction.component.equals(GetEdgeEndpoint.Component.kDestination)
				|| instruction.component.equals(GetEdgeEndpoint.Component.kBoth)){
			query += "set b:" + instruction.targetGraph.name + " ";
		}
		
		query += ";";
		
		storage.executeQuery(query);
	}

	@Override
	public void intersectGraph(IntersectGraph instruction){
		String vertexQuery = "";
		vertexQuery += "match (x:" + instruction.lhsGraph.name + ":" + instruction.rhsGraph.name + ") ";
		vertexQuery += "set x:" + instruction.outputGraph.name + ";";
		
		storage.executeQuery(vertexQuery);
		
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String edgeQuery = "";
		edgeQuery = "match ()-[e]->()";
		if(neo4jQueryEnvironment.isBaseGraph(instruction.lhsGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.rhsGraph)){
			// no where needed
		}else if(!neo4jQueryEnvironment.isBaseGraph(instruction.lhsGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.rhsGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.lhsGraph.name + ",'";
		}else if(neo4jQueryEnvironment.isBaseGraph(instruction.lhsGraph) && !neo4jQueryEnvironment.isBaseGraph(instruction.rhsGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.rhsGraph.name + ",'";
		}else{
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.lhsGraph.name + ",'";
			edgeQuery += " and " + edgeProperty + " contains '," + instruction.rhsGraph.name + ",'";
		}
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.outputGraph.name);
		
		storage.executeQuery(edgeQuery);
	}

	@Override
	public void limitGraph(LimitGraph instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String vertexQuery = 
				"match (v:" + instruction.sourceGraph.name + ") with v order by id(v) asc limit " 
				+ instruction.limit + " set v:" + instruction.targetGraph.name + ";";
		String edgeQuery = "match ()-[e]->()";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.sourceGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.sourceGraph.name + ",'";
		}
		edgeQuery += " with e order by id(e) asc limit " + instruction.limit;
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
		storage.executeQuery(vertexQuery);
		storage.executeQuery(edgeQuery);
	}
	
	@Override
	public GraphStats statGraph(StatGraph instruction){
		long vertices = 0;
		long edges = 0;
		List<Map<String, Object>> result = storage
				.executeQueryForSmallResult("match (v:" + instruction.targetGraph.name + ") return count(v) as vcount;");
		if(result.size() > 0){
			vertices = Long.parseLong(String.valueOf(result.get(0).get("vcount")));
		}
		if(neo4jQueryEnvironment.isBaseGraph(instruction.targetGraph)){
			result = storage.executeQueryForSmallResult("match ()-[e]->() return count(e) as ecount;");
		}else{
			final String edgeProperty = "e.`" + neo4jQueryEnvironment.edgeLabelsPropertyName + "`";
			result = storage.executeQueryForSmallResult("match ()-[e]->() " + "where " + edgeProperty + " contains ',"
					+ instruction.targetGraph.name + ",' " + "return count(e) as ecount;");
		}
		if(result.size() > 0){
			edges = Long.parseLong(String.valueOf(result.get(0).get("ecount")));
		}
		return new GraphStats(vertices, edges);
	}

	@Override
	public void subtractGraph(SubtractGraph instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		if(neo4jQueryEnvironment.isBaseGraph(instruction.minuendGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.subtrahendGraph)){
			// no resulting vertices and edge since both are base
		}else if(!neo4jQueryEnvironment.isBaseGraph(instruction.minuendGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.subtrahendGraph)){
			// no resulting vertices and edges since the subtrahend is base
		}else if(neo4jQueryEnvironment.isBaseGraph(instruction.minuendGraph) && !neo4jQueryEnvironment.isBaseGraph(instruction.subtrahendGraph)){
			String vertexQuery = "match (n:" + instruction.minuendGraph.name + ") where not '" + instruction.subtrahendGraph.name + 
					"' in labels(n) set n:" + instruction.outputGraph.name + ";";
			String edgeQuery = "match ()-[e]->()";
			edgeQuery += " where not " + edgeProperty + " contains '," + instruction.subtrahendGraph.name + ",'";
			edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.outputGraph.name);
			if(instruction.component == null || instruction.component == Graph.Component.kVertex){
				storage.executeQuery(vertexQuery);
			}
			if(instruction.component == null || instruction.component == Graph.Component.kEdge){
				storage.executeQuery(edgeQuery);
			}
		}else{
			String vertexQuery = "match (n:" + instruction.minuendGraph.name + ") where not '" + instruction.subtrahendGraph.name + 
					"' in labels(n) set n:" + instruction.outputGraph.name + ";";
			
			String edgeQuery = "match ()-[e]->()";
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.minuendGraph.name + ",'";
			edgeQuery += " and not " + edgeProperty + " contains '," + instruction.subtrahendGraph.name + ",'";
			edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.outputGraph.name);
			if(instruction.component == null || instruction.component == Graph.Component.kVertex){
				storage.executeQuery(vertexQuery);
			}
			if(instruction.component == null || instruction.component == Graph.Component.kEdge){
				storage.executeQuery(edgeQuery);
			}
		}
	}

	@Override
	public void unionGraph(UnionGraph instruction){
		String edgeQuery = "match ()-[e]->()";
		String vertexQuery = "match (v:" + instruction.sourceGraph.name + ") set v:" + instruction.targetGraph.name + ";";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.sourceGraph)){
			final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.sourceGraph.name + ",'";
		}
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name);
		
		storage.executeQuery(edgeQuery);
		storage.executeQuery(vertexQuery);
	}

	@Override
	public void getAdjacentVertex(GetAdjacentVertex instruction){ // TODO rename to get adjacent graph
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		if(instruction.direction.equals(GetLineage.Direction.kAncestor) || instruction.direction.equals(GetLineage.Direction.kBoth)){
			String query = "match (a:"+instruction.sourceGraph.name+":"+instruction.subjectGraph.name+")-[e]->"
					+ "(b:"+instruction.subjectGraph.name+")";
			if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
				query += " where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",'";
			}
			query += " set a:" + instruction.targetGraph.name;
			query += " set b:" + instruction.targetGraph.name;
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
			storage.executeQuery(query);
		}
		
		if(instruction.direction.equals(GetLineage.Direction.kDescendant) || instruction.direction.equals(GetLineage.Direction.kBoth)){
			String query = "match (a:"+instruction.subjectGraph.name+")-[e]->"
					+ "(b:"+instruction.sourceGraph.name+":"+instruction.subjectGraph.name+")";
			if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
				query += " where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",'";
			}
			query += " set a:" + instruction.targetGraph.name;
			query += " set b:" + instruction.targetGraph.name;
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
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
							minValue = AbstractQueryEnvironment.environmentVariableValueUNSET;
						}
						if(maxObject != null){
							maxValue = String.valueOf(maxObject);
						}else{
							maxValue = AbstractQueryEnvironment.environmentVariableValueUNSET;
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
	public Map<String, Map<String, String>> exportVertices(ExportGraph instruction){
		String nodesQuery = "match (v:" + instruction.targetGraph.name + ") return v;";
		return storage.readHashToVertexMap("v", nodesQuery);
	}
	
	@Override
	public Set<QueriedEdge> exportEdges(ExportGraph instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String edgeQuery = "match ()-[e]->()";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.targetGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.targetGraph.name + ",'";
		}
		edgeQuery += " return e;";
		
		return storage.readEdgeSet("e", edgeQuery);
	}
	
	@Override
	public void getLineage(GetLineage instruction){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		if(instruction.direction.equals(GetLineage.Direction.kAncestor) || instruction.direction.equals(GetLineage.Direction.kBoth)){
			String query = "match ";
			query += "p=(a:"+instruction.startGraph.name+":"+instruction.subjectGraph.name+")"
					+ "-[e0*0.."+instruction.depth+"]->"
					+ "(b:"+instruction.subjectGraph.name+") ";
			if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
				query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",') ";
			}
			query += " foreach (n in nodes(p) | set n:" + instruction.targetGraph.name + ") ";
			query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ");";
			storage.executeQuery(query);
		}
		
		if(instruction.direction.equals(GetLineage.Direction.kDescendant) || instruction.direction.equals(GetLineage.Direction.kBoth)){
			String query = "match ";
			query += "p=(a:"+instruction.subjectGraph.name+")"
					+ "-[e0*0.."+instruction.depth+"]->"
					+ "(b:"+instruction.startGraph.name+":"+instruction.subjectGraph.name+") ";
			if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
				query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",') ";
			}
			query += " foreach (n in nodes(p) | set n:" + instruction.targetGraph.name + ") ";
			query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ");";
			storage.executeQuery(query);
		}
	}

	@Override
	public void getPath(GetSimplePath instruction){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match ";
		query += "p=(a:"+instruction.srcGraph.name+":"+instruction.subjectGraph.name+")"
				+ "-[e0*0.."+instruction.maxDepth+"]->"
				+ "(b:"+instruction.dstGraph.name+":"+instruction.subjectGraph.name+") ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + instruction.targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ");";
		storage.executeQuery(query);
	}
	
	@Override
	public void getLink(GetLink instruction){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match ";
		query += "p=(a:"+instruction.srcGraph.name+":"+instruction.subjectGraph.name+")"
				+ "-[e0*0.."+instruction.maxDepth+"]->"
				+ "(b:"+instruction.dstGraph.name+":"+instruction.subjectGraph.name+") ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + instruction.targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ");";
		storage.executeQuery(query);
		
		query = "match ";
		query += "p=(a:"+instruction.dstGraph.name+":"+instruction.subjectGraph.name+")"
				+ "-[e0*0.."+instruction.maxDepth+"]->"
				+ "(b:"+instruction.srcGraph.name+":"+instruction.subjectGraph.name+") ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + instruction.targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ");";
		storage.executeQuery(query);
	}

	@Override
	public void getShortestPath(GetShortestPath instruction){
		final String edgeProperty = "e1.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String query = "match ";
		query += "p=shortestPath((a:"+instruction.srcGraph.name+":"+instruction.subjectGraph.name+")"
				+ "-[e0*0.."+instruction.maxDepth+"]->"
				+ "(b:"+instruction.dstGraph.name+":"+instruction.subjectGraph.name+")) ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += " where all(e1 in relationships(p) where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",') ";
		}
		query += " foreach (n in nodes(p) | set n:" + instruction.targetGraph.name + ") ";
		query += " foreach (e in relationships(p) | " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ");";
		storage.executeQuery(query);
	}

	@Override
	public void getSubgraph(GetSubgraph instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeLabelsPropertyName+"`";
		String vertexQuery = 
				"match (n:" + instruction.skeletonGraph.name + ":" + instruction.subjectGraph.name + ") "
				+ "set n:" + instruction.targetGraph.name + ";";
		
		String edgeQuery0 = "";
		edgeQuery0 += "match ()-[e]->(b:"+instruction.subjectGraph.name+")";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.skeletonGraph)){
			edgeQuery0 += " where " + edgeProperty + " contains '," + instruction.skeletonGraph.name + ",'";
		}
		edgeQuery0 += " set b:" + instruction.targetGraph.name + ";";
		
		String edgeQuery1 = "";
		edgeQuery1 += "match (a:"+instruction.subjectGraph.name+")-[e]->()";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.skeletonGraph)){
			edgeQuery1 += " where " + edgeProperty + " contains '," + instruction.skeletonGraph.name + ",'";
		}
		edgeQuery1 += " set a:" + instruction.targetGraph.name + ";";
		
		String edgeQuery2 = "";
		edgeQuery2 += "match (a:"+instruction.targetGraph.name+")-[e]->(b:"+instruction.targetGraph.name+")";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			edgeQuery2 += " where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",'";
		}
		edgeQuery2 += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
		
		// order matters
		storage.executeQuery(vertexQuery);
		storage.executeQuery(edgeQuery0);
		storage.executeQuery(edgeQuery1);
		storage.executeQuery(edgeQuery2);
	}
	
	@Override public void createEmptyGraphMetadata(CreateEmptyGraphMetadata instruction){}
	@Override public void overwriteGraphMetadata(OverwriteGraphMetadata instruction){}
	@Override public void setGraphMetadata(SetGraphMetadata instruction){}

}
