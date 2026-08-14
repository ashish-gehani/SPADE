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
package spade.storage.quickstep;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import spade.core.AbstractStorage;
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
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.SetGraphMetadata.Component;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;
import spade.storage.Quickstep;
import spade.utility.HelperFunctions;

/**
 * 
 * @author Jianqiao
 * 
 * Source: https://github.com/jianqiao/SPADE/tree/grail-dev/src/spade/query/quickgrail/execution
 *
 */
public class QuickstepInstructionExecutor extends QueryInstructionExecutor{

	private final Quickstep qs;
	private final QuickstepQueryEnvironment queryEnvironment;
	
	private final String vertexTableName, edgeTableName, vertexAnnotationsTableName, edgeAnnotationTableName;

	public QuickstepInstructionExecutor(Quickstep qs, QuickstepQueryEnvironment queryEnvironment,
			String vertexTableName, String vertexAnnotationsTableName, String edgeTableName, String edgeAnnotationTableName){
		this.qs = qs;
		this.queryEnvironment = queryEnvironment;
		this.vertexTableName = vertexTableName;
		this.vertexAnnotationsTableName = vertexAnnotationsTableName;
		this.edgeTableName = edgeTableName;
		this.edgeAnnotationTableName = edgeAnnotationTableName;
		if(this.queryEnvironment == null){
			throw new IllegalArgumentException("NULL Query Environment");
		}
		if(this.qs == null){
			throw new IllegalArgumentException("NULL query executor");
		}
		if(HelperFunctions.isNullOrEmpty(vertexTableName)){
			throw new IllegalArgumentException("NULL/Empty vertex table name");
		}
		if(HelperFunctions.isNullOrEmpty(vertexAnnotationsTableName)){
			throw new IllegalArgumentException("NULL/Empty vertex annotations table name");
		}
		if(HelperFunctions.isNullOrEmpty(edgeTableName)){
			throw new IllegalArgumentException("NULL/Empty edge table name");
		}
		if(HelperFunctions.isNullOrEmpty(edgeAnnotationTableName)){
			throw new IllegalArgumentException("NULL/Empty edge annotations table name");
		}
	}

	@Override
	public QuickstepQueryEnvironment getQueryEnvironment(){
		return queryEnvironment;
	}
	
	@Override
	public AbstractStorage getStorage(){
		return qs;
	}

	@Override
	public void insertLiteralEdge(Graph targetGraph, ArrayList<String> edges){
		if(!edges.isEmpty()){
			String insertSubpart = "";
			for(String edge : edges){
				if(edge.length() <= 32){
					insertSubpart += "('" + edge + "'), ";
				}
			}
			
			if(!insertSubpart.isEmpty()){
				final String tempEdgeTable = "m_edgehash";
				qs.executeQuery("drop table " + tempEdgeTable + ";\n");
				qs.executeQuery("create table " + tempEdgeTable + " (md5 char(32));\n");
				insertSubpart = insertSubpart.substring(0, insertSubpart.length() - 2);
				qs.executeQuery("insert into " + tempEdgeTable + " values " + insertSubpart + ";\n");
				
				qs.executeQuery("insert into " + getEdgeTableName(targetGraph)
						+ " select id from " + edgeTableName + " where md5 in (select md5 from "+tempEdgeTable+" group by md5);\n");
				
				qs.executeQuery("drop table " + tempEdgeTable + ";\n");
			}
		}
	}

	@Override
	public void insertLiteralVertex(Graph targetGraph, ArrayList<String> vertices){
		if(!vertices.isEmpty()){
			String insertSubpart = "";
			for(String vertex : vertices){
				if(vertex.length() <= 32){
					insertSubpart += "('" + vertex + "'), ";
				}
			}
			
			if(!insertSubpart.isEmpty()){
				final String tempVertexTable = "m_vertexhash";
				qs.executeQuery("drop table " + tempVertexTable + ";\n");
				qs.executeQuery("create table " + tempVertexTable + " (md5 char(32));\n");
				insertSubpart = insertSubpart.substring(0, insertSubpart.length() - 2);
				qs.executeQuery("insert into " + tempVertexTable + " values " + insertSubpart + ";\n");
				
				qs.executeQuery("insert into " + getVertexTableName(targetGraph)
						+ " select id from " + vertexTableName + " where md5 in (select md5 from "+tempVertexTable+" group by md5);\n");
				
				qs.executeQuery("drop table " + tempVertexTable + ";\n");
			}
		}
	}

	private String formatString(String str){
		StringBuilder sb = new StringBuilder();
		boolean escaped = false;
		for(int i = 0; i < str.length(); ++i){
			char c = str.charAt(i);
			if(c < 32){
				switch(c){
				case '\b':
					sb.append("\\b");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					sb.append("\\x" + Integer.toHexString(c));
					break;
				}
				escaped = true;
			}else{
				if(c == '\\'){
					sb.append('\\');
					escaped = true;
				}
				sb.append(c);
			}
		}
		return (escaped ? "e" : "") + "'" + sb + "'";
	}

	@Override
	public void createEmptyGraph(Graph graph){
		QuickstepUtil.CreateEmptyGraph(qs, queryEnvironment, graph);
	}

	@Override
	public void distinctifyGraph(Graph targetGraph, Graph sourceGraph){
		String sourceVertexTable = getVertexTableName(sourceGraph);
		String sourceEdgeTable = getEdgeTableName(sourceGraph);
		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);

		qs.executeQuery("\\analyzerange " + sourceVertexTable + " " + sourceEdgeTable + "\n");
		qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id FROM " + sourceVertexTable + " GROUP BY id;");
		qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT id FROM " + sourceEdgeTable + " GROUP BY id;");
	}

	@Override
	public void getWhereAnnotationsExist(final Graph targetGraph, final Graph subjectGraph,
			final ArrayList<String> annotationNames){
		
		final ArrayList<String> annotationKeys = annotationNames;
		
		qs.executeQuery("\\analyzerange " + getVertexTableName(subjectGraph) + "\n ");
		
		String query = "";
		query += "insert into " + getVertexTableName(targetGraph) + " "
				+ "select v.id from " + vertexAnnotationsTableName + " v "
				+ "where v.id in (select id from "+getVertexTableName(subjectGraph)+") and ";
		
		for(int i = 0; i < annotationKeys.size(); i++){
			final String annotationKey = annotationKeys.get(i);
			query += "v.field = '" + annotationKey + "'";
			if(i == annotationKeys.size() - 1){ // is last
				// don't append the 'and'
			}else{
				query += " and ";
			}
		}
		query += ";";
		qs.executeQuery(query);
	}

	private String getMatchCreateSubsetTable(final Graph srcGraph){
		final int maxVertexKeyLength = qs.getMaxVertexKeyLength();
		final int maxVertexValueLength = qs.getMaxVertexValueLength();
		final String srcGraphVertexTable = getVertexTableName(srcGraph);
		final String subsetTable = "m_" + srcGraphVertexTable + "_vertex_anno_subset";
		qs.executeQuery("drop table " + subsetTable + ";\n");
//		qs.executeQuery("create table " + subsetTable
//				+ "(id int, field varchar(" + maxVertexKeyLength + "), value varchar(" + maxVertexValueLength
//				+ ")) WITH BLOCKPROPERTIES (TYPE compressed_columnstore, SORT id, COMPRESS (id, field, value));\n");
		// BLOCKPROPERTIES cannot be reused! don't know why. The DB crashes.
		qs.executeQuery("create table " + subsetTable
				+ "(id int, field varchar(" + maxVertexKeyLength + "), value varchar(" + maxVertexValueLength
				+ "));\n");
		return subsetTable;
	}

	private String getMatchGetWhereClause(final String vertexAnnoAlias, final ArrayList<String> annotationKeys){
		String whereFieldClause = "";
		for(int i = 0; i < annotationKeys.size(); i++){
			final String annotationKey = annotationKeys.get(i);
			whereFieldClause += vertexAnnoAlias + ".field = '" + annotationKey + "'";
			if(i != annotationKeys.size() - 1){
				whereFieldClause += " or ";
			}
		}
		return whereFieldClause;
	}

	private void getMatchDoSubsetJoinWithMain(final String subsetTable, final Graph srcGraph,
			final String vertexAnnoAlias,
			final String whereClause){
		final String srcGraphVertexTable = getVertexTableName(srcGraph);
//		final String query = "insert into " + subsetTable + " select " + vertexAnnoAlias + ".id, " + vertexAnnoAlias
//				+ ".field, " + vertexAnnoAlias + ".value " + "from " + vertexAnnotationsTableName + " "
//				+ vertexAnnoAlias + ", " + srcGraphVertexTable + " vo " + "where (" + vertexAnnoAlias
//				+ ".id = vo.id) and " + "(" + whereClause + ");\n";
		// avoid join
		final String query = "insert into " + subsetTable + " select " + vertexAnnoAlias + ".id, " + vertexAnnoAlias
				+ ".field, " + vertexAnnoAlias + ".value " + "from " + vertexAnnotationsTableName + " "
				+ vertexAnnoAlias + " where " + vertexAnnoAlias + ".id in (select id from " + srcGraphVertexTable + ") "
				+ "and " + "(" + whereClause + ")"
				+ ";\n";
		qs.executeQuery(query);
	}

	private void getMatchDoSubsetJoinWithSubset(final Graph targetGraph, final String subsetTable1,
			final String subsetTable2, final int annotationKeysCount){
		final String targetVertexTable = getVertexTableName(targetGraph);
		final String joinResultTable = "m_" + targetVertexTable;
		qs.executeQuery("drop table " + joinResultTable + ";\n");
		qs.executeQuery("create table " + joinResultTable + "(mcount int, id1 int, id2 int);\n");
		
		final String subsetTable1Alias = "t1";
		final String subsetTable2Alias = "t2";
		final String query = "insert into " + joinResultTable + " select count(*), " + 
				subsetTable1Alias + ".id, " + subsetTable2Alias + ".id from " +
				subsetTable1 + " " + subsetTable1Alias + ", " + subsetTable2 + " " + subsetTable2Alias + " where " +
				subsetTable1Alias + ".field = " + subsetTable2Alias + ".field and " + 
				subsetTable1Alias + ".value = " + subsetTable2Alias + ".value group by " + 
				subsetTable1Alias + ".id, " + subsetTable2Alias + ".id having " +
				"count(*) = " + annotationKeysCount + ";\n";

		qs.executeQuery(query);
		
		final String insertSubsetTable1Query = "insert into " + targetVertexTable + " select id1 from "
				+ joinResultTable + ";\n";
		final String insertSubsetTable2Query = "insert into " + targetVertexTable + " select id2 from "
				+ joinResultTable + ";\n";

		qs.executeQuery(insertSubsetTable1Query);
		qs.executeQuery(insertSubsetTable2Query);
		
		qs.executeQuery("drop table " + joinResultTable + ";\n");
	}

	private long getMatchGetCountOfAnnotationKeysInGraph(final Graph graph, final String vertexAnnoAlias,
			final String whereClause){
		final String graphVertexTable = getVertexTableName(graph);
		final String query = "copy select count(*) from " + vertexAnnotationsTableName + " " + vertexAnnoAlias
				+ " where "
				+ vertexAnnoAlias
				+ ".id in (select id from " + graphVertexTable + ") " + "and " + "(" + whereClause + ")"
				+ " to stdout;";
		return qs.executeQueryForLongResult(query);
	}

	@Override
	public void getMatch(final Graph targetGraph, final Graph graph1, final Graph graph2,
			final ArrayList<String> annotationKeys){
		/*
		 * OLD join based approach in commit:
		 * https://github.com/ashish-gehani/SPADE/commit/05bfe7501f4f1a6f8e77c709c528085e3207fe2d
		 * 
		 * Current Approach:
		 * Special cases:
		 * 1. Empty result, if no annotation keys given
		 * 2. Empty result, if the two graph variables are equal AND if all of the given annotation keys don't exist
		 * 3. Result same as one of the graph variables, if both graph variables are equal and any of the given annotation keys exist
		 * 
		 * General case:
		 * Collect all specified annotations for each variable in their own tables tables (see caveats below)
		 * Caveats: repetition of BLOCKPROPERTIES breaks the db, and join worse than nested query
		 * Join the new tables of both variable, collect all rows where the field and value are the same in both tables
		 * Group the result by id of both tables so that count can be computed on each group 
		 * If the size of the group is equal to the number of annotation keys then that means all of the annotation keys matched
		 * Now we have the ids of the vertices with all matching annotation keys
		 * Put the columns (id columns) in the result table one by one
		 * 
		 * TODO support left-match and right-match for finer grained analysis?
		 */
		qs.executeQuery("\\analyzerange " + getVertexTableName(graph1) + "\n");
		qs.executeQuery("\\analyzerange " + getVertexTableName(graph2) + "\n");


		final String vertexAnnoAlias = "va";
		final String whereFieldClause = getMatchGetWhereClause(vertexAnnoAlias, annotationKeys);
		final int annotationKeysCount = annotationKeys.size();
		if(annotationKeysCount == 0){
			return;
		}
		if(graph1.equals(graph2)){
			if(getMatchGetCountOfAnnotationKeysInGraph(graph1, vertexAnnoAlias, whereFieldClause) == 0){
				return;
			}else{
				final String graph1VertexTable = getVertexTableName(graph1);
				final String targetVertexTable = getVertexTableName(targetGraph);
				qs.executeQuery("insert into " + targetVertexTable + " select id from " + graph1VertexTable + ";\n");
				return;
			}
		}

		final String subsetTable1 = getMatchCreateSubsetTable(graph1);
		final String subsetTable2 = getMatchCreateSubsetTable(graph2);

		getMatchDoSubsetJoinWithMain(subsetTable1, graph1, vertexAnnoAlias, whereFieldClause);
		getMatchDoSubsetJoinWithMain(subsetTable2, graph2, vertexAnnoAlias, whereFieldClause);

		getMatchDoSubsetJoinWithSubset(targetGraph, subsetTable1, subsetTable2, annotationKeysCount);

		qs.executeQuery("drop table " + subsetTable1 + ";\n drop table " + subsetTable2 + ";\n");
	}

	@Override
	public void getVertex(Graph targetGraph, Graph subjectGraph, String annotationKey, PredicateOperator operator,
			String annotationValue, final boolean hasArguments){
		if(!hasArguments){
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + getVertexTableName(targetGraph) + " SELECT id FROM "
					+ vertexAnnotationsTableName);
			if(!queryEnvironment.isBaseGraph(subjectGraph)){
				String analyzeQuery = "\\analyzerange " + getVertexTableName(subjectGraph) + "\n";
				qs.executeQuery(analyzeQuery);
				sqlQuery.append(" WHERE id IN (SELECT id FROM " + getVertexTableName(subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			qs.executeQuery(sqlQuery.toString());
		}else{
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + getVertexTableName(targetGraph) + " SELECT id FROM "
					+ vertexAnnotationsTableName + " WHERE");
			if(!annotationKey.equals("*")){
				sqlQuery.append(" field = " + formatString(annotationKey) + " AND");
			}
			sqlQuery.append(" value " + resolveOperator(operator) + " "
					+ formatString(annotationValue));
			if(!queryEnvironment.isBaseGraph(subjectGraph)){
				String analyzeQuery = "\\analyzerange " + getVertexTableName(subjectGraph) + "\n";
				qs.executeQuery(analyzeQuery);
				sqlQuery.append(" AND id IN (SELECT id FROM " + getVertexTableName(subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			qs.executeQuery(sqlQuery.toString());
		}
	}
	/*
	private final Set<String> executeAndGetSingleColumnResult(final String query){
		final Set<String> set = new HashSet<String>();
		final String result = qs.executeQuery(query);
		final String[] resultLines = result.split("\n");
		for(int i = 0; i < resultLines.length; i++){
			final String resultLine = resultLines[i];
			set.add(resultLine);
		}
		return set;
	}
	*/
	@Override
	public GraphDescription describeGraph(DescribeGraph instruction){
		//final Graph graph = instruction.graph;
		
		/*
		String vertexQuery = "copy select field from " + vertexAnnotationsTableName;
		String edgeQuery = "copy select field from " + edgeAnnotationTableName;
		
		if(!queryEnvironment.isBaseGraph(graph)){
			final String vertexAnalyzeQuery = "\\analyzerange " + getVertexTableName(graph) + "\n";
			final String edgeAnalyzeQuery = "\\analyzerange " + getEdgeTableName(graph) + "\n";
			qs.executeQuery(vertexAnalyzeQuery);
			qs.executeQuery(edgeAnalyzeQuery);
			
			vertexQuery += " where id in (select id from " + getVertexTableName(graph) + ")";
			edgeQuery += " where id in (select id from " + getEdgeTableName(graph) + ")";
		}
		
		vertexQuery += " group by field to stdout;\n";
		edgeQuery += " group by field to stdout;\n";
		
		final Set<String> vertexAnnotationsSet = executeAndGetSingleColumnResult(vertexQuery);
		final Set<String> edgeAnnotationsSet = executeAndGetSingleColumnResult(edgeQuery);
		
		GraphDescription desc = new GraphDescription();
		desc.addVertexAnnotations(vertexAnnotationsSet);
		desc.addEdgeAnnotations(edgeAnnotationsSet);
		return desc;
		*/
		
		throw new RuntimeException("Unsupported for Quickstep");
	}

	@Override
	public ResultTable evaluateQuery(final String nativeQuery){
		List<String> queries = new ArrayList<String>();
		boolean insideQuotes = false;
		String currentQuery = "";
		for(int i = 0; i < nativeQuery.length(); i++){
			char c = nativeQuery.charAt(i);
			if(c == ';'){
				if(i == 0){
					continue;
				}else{
					if(insideQuotes == false){
						if(nativeQuery.charAt(i-1) != '\\'){
							if(!currentQuery.isBlank()){
								queries.add(currentQuery.trim());
							}
							currentQuery = "";
							continue;
						}
					}
				}
			}else if(c == '\''){
				if(c == 0){
					insideQuotes = !insideQuotes;
				}else{
					if(nativeQuery.charAt(i-1) != '\\'){
						insideQuotes = !insideQuotes;
					}
				}
			}
			currentQuery += c;
		}
		if(!currentQuery.isBlank()){
			queries.add(currentQuery.trim());
		}
		
		if(queries.size() > 1){
			throw new RuntimeException("Only one native query allowed!");
		}else if(queries.size() == 0){
			throw new RuntimeException("Empty query");
		}

		if(queries.get(0).startsWith("select ")){
			String finalQuery = "copy " + queries.get(0) + " to stdout with (delimiter ',');";
			String result = qs.executeQuery(finalQuery);
			return ResultTable.FromText(result, ',');
		}else{
			String finalQuery = queries.get(0);
			String result = qs.executeQuery(finalQuery);
			ResultTable table = new ResultTable();
			ResultTable.Row row = new ResultTable.Row();
			row.add(result);
			table.addRow(row);
			Schema schema = new Schema();
			schema.addColumn("Output", StringType.GetInstance());
			table.setSchema(schema);
			
			return table;
		}
	}

	private String resolveOperator(PredicateOperator operator){
		switch(operator){
		case EQUAL:
			return "=";
		case GREATER:
			return ">";
		case GREATER_EQUAL:
			return ">=";
		case LESSER:
			return "<";
		case LESSER_EQUAL:
			return "<=";
		case LIKE:
			return "LIKE";
		case NOT_EQUAL:
			return "<>";
		case REGEX:
			return "REGEXP";
		default:
			throw new RuntimeException("Unexpected operator: " + operator);
		}
	}

	@Override
	public void getEdge(Graph targetGraph, Graph subjectGraph, String annotationKey, PredicateOperator operator,
			String annotationValue, final boolean hasArguments){
		if(!hasArguments){
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + getEdgeTableName(targetGraph) + " SELECT id FROM "
					+ edgeAnnotationTableName);
			if(!queryEnvironment.isBaseGraph(subjectGraph)){
				String analyzeQuery = "\\analyzerange " + getEdgeTableName(subjectGraph) + "\n";
				qs.executeQuery(analyzeQuery);
				sqlQuery.append(" WHERE id IN (SELECT id FROM " + getEdgeTableName(subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			qs.executeQuery(sqlQuery.toString());
		}else{
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + getEdgeTableName(targetGraph) + " SELECT id FROM "
					+ edgeAnnotationTableName + " WHERE");
			if(!annotationKey.equals("*")){
				sqlQuery.append(" field = " + formatString(annotationKey) + " AND");
			}
			sqlQuery.append(" value " + resolveOperator(operator) + " "
					+ formatString(annotationValue));
			if(!queryEnvironment.isBaseGraph(subjectGraph)){
				String analyzeQuery = "\\analyzerange " + getEdgeTableName(subjectGraph) + "\n";
				qs.executeQuery(analyzeQuery);
				sqlQuery.append(" AND id IN (SELECT id FROM " + getEdgeTableName(subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			qs.executeQuery(sqlQuery.toString());
		}
	}

	@Override
	public void getEdgeEndpoint(Graph targetGraph, Graph subjectGraph, GetEdgeEndpoint.Component component){
		String targetVertexTable = getVertexTableName(targetGraph);
		String subjectEdgeTable = getEdgeTableName(subjectGraph);
		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n" + "\\analyzerange "
				+ subjectEdgeTable + "\n");
		if(component == GetEdgeEndpoint.Component.kSource
				|| component == GetEdgeEndpoint.Component.kBoth){
			qs.executeQuery("INSERT INTO m_answer SELECT src FROM edge" + " WHERE id IN (SELECT id FROM "
					+ subjectEdgeTable + ");");
		}
		if(component == GetEdgeEndpoint.Component.kDestination
				|| component == GetEdgeEndpoint.Component.kBoth){
			qs.executeQuery("INSERT INTO m_answer SELECT dst FROM edge" + " WHERE id IN (SELECT id FROM "
					+ subjectEdgeTable + ");");
		}
		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable
				+ " SELECT id FROM m_answer GROUP BY id;\n" + "DROP TABLE m_answer;");
	}

	@Override
	public void intersectGraph(Graph outputGraph, Graph lhsGraph, Graph rhsGraph){
		String outputVertexTable = getVertexTableName(outputGraph);
		String outputEdgeTable = getEdgeTableName(outputGraph);
		String lhsVertexTable = getVertexTableName(lhsGraph);
		String lhsEdgeTable = getEdgeTableName(lhsGraph);
		String rhsVertexTable = getVertexTableName(rhsGraph);
		String rhsEdgeTable = getEdgeTableName(rhsGraph);

		qs.executeQuery("\\analyzerange " + rhsVertexTable + " " + rhsEdgeTable + "\n");
		qs.executeQuery("INSERT INTO " + outputVertexTable + " SELECT id FROM " + lhsVertexTable
				+ " WHERE id IN (SELECT id FROM " + rhsVertexTable + ");");
		qs.executeQuery("INSERT INTO " + outputEdgeTable + " SELECT id FROM " + lhsEdgeTable
				+ " WHERE id IN (SELECT id FROM " + rhsEdgeTable + ");");
	}

	@Override
	public void limitGraph(Graph targetGraph, Graph sourceGraph, int limit){
		String sourceVertexTable = getVertexTableName(sourceGraph);
		String sourceEdgeTable = getEdgeTableName(sourceGraph);

		long numVertices = qs
				.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + sourceVertexTable + " TO stdout;");
		long numEdges = qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + sourceEdgeTable + " TO stdout;");

		if(numVertices > 0){
			qs.executeQuery("\\analyzerange " + sourceVertexTable + "\n" + "INSERT INTO "
					+ getVertexTableName(targetGraph) + " SELECT id FROM " + sourceVertexTable
					+ " GROUP BY id" + " ORDER BY id LIMIT " + limit + ";");

		}
		if(numEdges > 0){
			qs.executeQuery("\\analyzerange " + sourceEdgeTable + "\n" + "INSERT INTO "
					+ getEdgeTableName(targetGraph) + " SELECT id FROM " + sourceEdgeTable + " GROUP BY id"
					+ " ORDER BY id LIMIT " + limit + ";");
		}
	}

	private static List<String[]> getResultAsTable(final String result){
		return HelperFunctions.parseAsList(result, "\\n", ",");
	}

	private static int getIteratorBatchSize(){
		return 1000;
	}

	@Override
	public long getGraphStatisticSize(final Graph graph, final ElementType elementType, final String annotationKey){
		final String annotationTable;
		final String targetTable;
		switch(elementType){
			case VERTEX:{
				annotationTable = vertexAnnotationsTableName;
				targetTable = getVertexTableName(graph);
				break;
			}
			case EDGE:{
				annotationTable = edgeAnnotationTableName;
				targetTable = getEdgeTableName(graph);
				break;
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}

		final String countQuery = 
				"copy select count(*) from " + annotationTable
				+ " where id in (select id from " + targetTable + ")"
				+ " and field=" + formatString(annotationKey) + " to stdout;\n";

		final long size = qs.executeQueryForLongResult(countQuery);
		return size;
	}

	@Override
	public GraphStatistic.Count getGraphCount(final Graph graph){
		final String targetVertexTable = getVertexTableName(graph);
		final String targetEdgeTable = getEdgeTableName(graph);
		final long numVertices = 
				qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetVertexTable + " TO stdout;");
		final long numEdges = 
				qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetEdgeTable + " TO stdout;");
		return new GraphStatistic.Count(numVertices, numEdges);
	}

	@Override
	public GraphStatistic.Histogram getGraphHistogram(final Graph graph, final ElementType elementType, final String annotationKey){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.Histogram();
		}

		final String targetVertexTable = getVertexTableName(graph);
		final String targetEdgeTable = getEdgeTableName(graph);
		
		final String targetTable;
		final String annotationTable;
		
		switch(elementType){
			case VERTEX:
				targetTable = targetVertexTable;
				annotationTable = vertexAnnotationsTableName;
				break;
			case EDGE:
				targetTable = targetEdgeTable;
				annotationTable = edgeAnnotationTableName;
				break;
			default:
				throw new RuntimeException("Unknown element type");
		}
		
		final String query = 
				"copy select value, count(*) from " + annotationTable 
				+ " where id" + " in (select id from " + targetTable + ")"
				+ " and field=" + formatString(annotationKey)
				+ " group by value order by count(*)"
				+ " to stdout with (delimiter ',');";

		final String result = qs.executeQuery(query);
		final List<String[]> resultTable = getResultAsTable(result);
		final SortedMap<String, Double> histogram = new TreeMap<>();
		for(final String[] row : resultTable){
			final String value = row[0];
			final Double count = Double.parseDouble(row[1]);
			histogram.put(value, count);
		}
		final GraphStatistic.Histogram histogramStats = new GraphStatistic.Histogram(histogram);
		return histogramStats;
	}

	@Override
	public GraphStatistic.Mean getGraphMean(final Graph graph, final ElementType elementType, final String annotationKey){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.Mean();
		}

		final String resultTable = createResultIteratorTable(graph, elementType, annotationKey);
		final int batchSize = getIteratorBatchSize();
		final QuickstepBatchIterator qit = new QuickstepBatchIterator(resultTable, batchSize, elementType, qs);
		double sum = 0;
		double count = 0;
		while(qit.hasNextBatch()){
			final String batchResult = qit.nextBatch();
			final List<String[]> list = getResultAsTable(batchResult);
			for(final String[] row : list){
				final double value = Double.parseDouble(row[1]);
				sum += value;
				count++;
			}
		}
		final double mean = sum / count;
		final GraphStatistic.Mean meanStats = new GraphStatistic.Mean(mean);
		return meanStats;
	}

	@Override
	public GraphStatistic.StandardDeviation getGraphStandardDeviation(
			final Graph graph, final ElementType elementType, final String annotationKey){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.StandardDeviation();
		}

		final GraphStatistic.Mean graphMean = getGraphMean(graph, elementType, annotationKey);
		final double mean = graphMean.getMean();

		final String resultTable = createResultIteratorTable(graph, elementType, annotationKey);
		final int batchSize = getIteratorBatchSize();
		final QuickstepBatchIterator qit = new QuickstepBatchIterator(resultTable, batchSize, elementType, qs);
		double squaredDifferenceSum = 0;
		double count = 0;
		while(qit.hasNextBatch()){
			final String batchResult = qit.nextBatch();
			final List<String[]> list = getResultAsTable(batchResult);
			for(final String[] row : list){
				final double value = Double.parseDouble(row[1]);
				final double squaredDifference = Math.pow(value - mean, 2);
				squaredDifferenceSum += squaredDifference;
				count++;
			}
		}
		final double stdDev = Math.sqrt(squaredDifferenceSum / count);

		final GraphStatistic.StandardDeviation graphStdDev = new GraphStatistic.StandardDeviation(stdDev);
		return graphStdDev;
	}

	@Override
	public GraphStatistic.Distribution getGraphDistribution(final Graph graph, final ElementType elementType, final String annotationKey,
			final Integer binCount){

		if(getGraphStatisticSize(graph, elementType, annotationKey) <= 0){
			return new GraphStatistic.Distribution();
		}

		final String resultTable = createResultIteratorTable(graph, elementType, annotationKey);
		final int batchSize = getIteratorBatchSize();
		final QuickstepBatchIterator qit = new QuickstepBatchIterator(resultTable, batchSize, elementType, qs);

		// find max and min of the table to find range
		double max = Double.NEGATIVE_INFINITY;
		double min = Double.POSITIVE_INFINITY;
		while(qit.hasNextBatch()){
			final String batchResult = qit.nextBatch();
			final List<String[]> list = getResultAsTable(batchResult);
			for(final String[] row : list){
				final double value = Double.parseDouble(row[1]);
				max = Math.max(max, value);
				min = Math.min(min, value);
			}
		}

		final double range = max - min + 1;
		final double step = range / binCount;
		final SortedMap<Interval, Double> distribution = new TreeMap<>();

		double begin = min;
		final List<Double> ranges = new ArrayList<>();
		while(begin + step < max){
			final Interval interval = new Interval(
					begin, 
					begin + step);
			distribution.put(interval, 0.0);
			begin += step;
			ranges.add(begin);
		}
		ranges.add(max + 1);
		distribution.put(
				new Interval(begin, max), 
				0.0);

		createResultIteratorTable(graph, elementType, annotationKey);
		// find distribution for each bin
		// run through the result table again in batches
		// keep count of frequencies in the map
		while(qit.hasNextBatch()){
			final String batchResult = qit.nextBatch();
			final List<String[]> list = getResultAsTable(batchResult);
			for(final String[] row : list){
				final double value = Double.parseDouble(row[1]);
				int i = -1;
				for(final double r : ranges){
					i++;
					if(value < r){
						final Interval interval;
						if(i == ranges.size() - 1){
							interval = new Interval(r - step, r - 1);
						}else{
							interval = new Interval(r - step, r);
						}
						if(distribution.get(interval) != null){
							final double newCount = distribution.get(interval) + 1;
							distribution.put(interval, newCount);
						}
						break;
					}
				}
			}
		}
		final GraphStatistic.Distribution distributionStats = new GraphStatistic.Distribution(distribution);
		return distributionStats;
	}

	private String createResultIteratorTable(final Graph graph, final ElementType elementType,
			final String annotationKey){
		final String resultTable;
		final int maxValueLength;
		final String annotationTable;
		final String targetTable;
		switch(elementType){
			case VERTEX:{
				targetTable = getVertexTableName(graph);
				resultTable = "m_result_" + targetTable;
				maxValueLength = qs.getMaxVertexValueLength();
				annotationTable = vertexAnnotationsTableName;
				break;
			}
			case EDGE:{
				targetTable = getEdgeTableName(graph);
				resultTable = "m_result_" + targetTable;
				maxValueLength = qs.getMaxEdgeValueLength();
				annotationTable = edgeAnnotationTableName;
				break;
			}
			default:{
				throw new RuntimeException("Unknown element type");
			}
		}

		final String createQuery = 
				"drop table " + resultTable + ";\n"
				+ "create table " + resultTable
				+ "(id INT, value VARCHAR (" + maxValueLength + "));\n";

		final String insertQuery = 
				"insert into " + resultTable
				+ " select id, value from " + annotationTable
				+ " where id in (select id from " + targetTable + ")"
				+ " and field=" + formatString(annotationKey) + ";\n";

		qs.executeQuery(createQuery);
		qs.executeQuery(insertQuery);

		return resultTable;
	}

	@Override
	public void subtractGraph(Graph outputGraph, Graph minuendGraph, Graph subtrahendGraph, Graph.Component component){
		String outputVertexTable = getVertexTableName(outputGraph);
		String outputEdgeTable = getEdgeTableName(outputGraph);
		String minuendVertexTable = getVertexTableName(minuendGraph);
		String minuendEdgeTable = getEdgeTableName(minuendGraph);
		String subtrahendVertexTable = getVertexTableName(subtrahendGraph);
		String subtrahendEdgeTable = getEdgeTableName(subtrahendGraph);

		if(component == null || component == Graph.Component.kVertex){
			qs.executeQuery("\\analyzerange " + subtrahendVertexTable + "\n");
			qs.executeQuery("INSERT INTO " + outputVertexTable + " SELECT id FROM " + minuendVertexTable
					+ " WHERE id NOT IN (SELECT id FROM " + subtrahendVertexTable + ");");
		}
		if(component == null || component == Graph.Component.kEdge){
			qs.executeQuery("\\analyzerange " + subtrahendEdgeTable + "\n");
			qs.executeQuery("INSERT INTO " + outputEdgeTable + " SELECT id FROM " + minuendEdgeTable
					+ " WHERE id NOT IN (SELECT id FROM " + subtrahendEdgeTable + ");");
		}
	}

	@Override
	public void unionGraph(Graph targetGraph, Graph sourceGraph){
		String sourceVertexTable = getVertexTableName(sourceGraph);
		String sourceEdgeTable = getEdgeTableName(sourceGraph);
		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);

		qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id FROM " + sourceVertexTable + ";");
		qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT id FROM " + sourceEdgeTable + ";");
	}

	@Override
	public void getAdjacentVertex(Graph targetGraph, Graph subjectGraph, Graph sourceGraph, GetLineage.Direction direction){
		List<Direction> oneDirs = new ArrayList<Direction>();
		if(direction == Direction.kBoth){
			oneDirs.add(Direction.kAncestor);
			oneDirs.add(Direction.kDescendant);
		}else{
			oneDirs.add(direction);
		}

		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);

		String subjectEdgeTable = getEdgeTableName(subjectGraph);
		String filter = "";
		if(!isBaseGraph(subjectGraph)){
			qs.executeQuery("\\analyzerange " + subjectEdgeTable + "\n");
			filter = " AND edge.id IN (SELECT id FROM " + subjectEdgeTable + ")";
		}

		for(Direction oneDir : oneDirs){
			executeOneDirection(oneDir, filter, 1, sourceGraph);
			qs.executeQuery("\\analyzerange m_answer m_answer_edge\n" + "INSERT INTO " + targetVertexTable
					+ " SELECT id FROM m_answer;\n" + "INSERT INTO " + targetEdgeTable
					+ " SELECT id FROM m_answer_edge GROUP BY id;");
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "DROP TABLE m_answer_edge;");
	}

	private Map<String, String> _getIdToHashOfVertices(String targetVertexTable){
		long numVertices = qs
				.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetVertexTable + " TO stdout;");
		if(numVertices == 0){
			return new HashMap<String, String>();
		}
		
		qs.executeQuery("\\analyzerange " + targetVertexTable + "\n");

		Map<String, String> idToHash = new HashMap<String, String>();
		
		String vertexHashStr = qs.executeQuery("COPY SELECT * FROM vertex WHERE id IN (SELECT id FROM "
				+ targetVertexTable + ") TO stdout WITH (DELIMITER e'\\n');");
		String[] vertexHashLines = vertexHashStr.split("\n");
		vertexHashStr = null;

		if(vertexHashLines.length % 2 != 0){
			throw new RuntimeException("Unexpected export vertex annotations query output");
		}
		for(int i = 0; i < vertexHashLines.length; i += 2){
			// TODO: accelerate with cache.
			String id = vertexHashLines[i];
			String hash = vertexHashLines[i+1];
			idToHash.put(id, hash);
		}
		
		return idToHash;
	}
	
	private Map<String, Map<String, String>> _exportVertices(String targetVertexTable){
		Map<String, String> idToHash = _getIdToHashOfVertices(targetVertexTable);
		if(idToHash.size() == 0){
			return new HashMap<String, Map<String, String>>();
		}
		
		final String vertexAnnoQuery = "COPY SELECT * FROM vertex_anno WHERE id IN (SELECT id FROM " + targetVertexTable
				+ ") TO stdout WITH (DELIMITER e'\\n');";
		final Map<String, Map<String, String>> idToAnnos = parseTableAsIdToAnnotations(vertexAnnoQuery, "vertex");
		
		////////////
		
		Set<String> allIds = new HashSet<String>();
		allIds.addAll(idToHash.keySet());
		allIds.addAll(idToAnnos.keySet());
		
		Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();
		
		for(String id : allIds){
			String hash = idToHash.get(id);
			Map<String, String> annos = idToAnnos.get(id);
			result.put(hash, annos);
		}
		
		return result;
	}

	private Map<String, Map<String, String>> parseTableAsIdToAnnotations(final String query, final String tableType){
		String annoTableResultStr = qs.executeQuery(query);

		final Map<String, Map<String, String>> idToAnnos = new HashMap<String, Map<String, String>>();

		try(final Scanner scanner = new Scanner(annoTableResultStr)){
			while(scanner.hasNextLine()){
				final String idLine = scanner.nextLine();
				// Skip last newline.
				if(idLine.isEmpty() && !scanner.hasNextLine()){
					break;
				}
				// There must be a second line
				if(!scanner.hasNextLine()){
					throw new RuntimeException("Unexpected export " + tableType + " annotations query output");
				}
				final String keyLine = scanner.nextLine();
				// There must be a third line
				if(!scanner.hasNextLine()){
					throw new RuntimeException("Unexpected export " + tableType + " annotations query output");
				}
				final String valueLine = scanner.nextLine();
				Map<String, String> annos = idToAnnos.get(idLine);
				if(annos == null){
					annos = new HashMap<String, String>();
					idToAnnos.put(idLine, annos);
				}
				annos.put(keyLine, valueLine);
			}
		}

		return idToAnnos;
	}

	private Map<String, String> _getIdToHashOfSrcDstVertices(String targetEdgeTable){
		qs.executeQuery("drop table m_export_edge;\n" + "create table m_export_edge (id INT);\n");
		
		qs.executeQuery("insert into m_export_edge select src from edge e where e.id in (select id from "+targetEdgeTable+");\n");
		qs.executeQuery("insert into m_export_edge select dst from edge e where e.id in (select id from "+targetEdgeTable+");\n");
		
		Map<String, String> idToHash = _getIdToHashOfVertices("m_export_edge");

		qs.executeQuery("drop table m_export_edge;\n");
		
		return idToHash;
	}

	private Set<QueriedEdge> _exportEdges(String targetVertexTable, String targetEdgeTable){
		Set<QueriedEdge> edges = new HashSet<QueriedEdge>();

		long numEdges = qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetEdgeTable + " TO stdout;");
		if(numEdges == 0){
			return edges;
		}

		qs.executeQuery("\\analyzerange " + targetEdgeTable + "\n");
		
		Map<String, String> edgeIdToHash = new HashMap<String, String>();
		Map<String, SimpleEntry<String, String>> edgeIdToSrcDstIds = new HashMap<String, SimpleEntry<String, String>>();

		String edgeIdSrcDstIdStr = qs.executeQuery("COPY SELECT * FROM "+edgeTableName+" WHERE id IN (SELECT id FROM "
				+ targetEdgeTable + ") TO stdout WITH (DELIMITER e'\\n');");
		String[] edgeIdSrcDstId = edgeIdSrcDstIdStr.split("\n");
		edgeIdSrcDstIdStr = null;

		if(edgeIdSrcDstId.length % 4 != 0){
			throw new RuntimeException("Unexpected export edge query output");
		}
		for(int i = 0; i < edgeIdSrcDstId.length; i += 4){
			// TODO: accelerate with cache.
			String eid = edgeIdSrcDstId[i];
			String sid = edgeIdSrcDstId[i + 1];
			String did = edgeIdSrcDstId[i + 2];
			String edgeHash = edgeIdSrcDstId[i + 3];
			edgeIdToSrcDstIds.put(eid, new SimpleEntry<String, String>(sid, did));
			edgeIdToHash.put(eid, edgeHash);
		}
		
		//////
		
		Map<String, String> vertexIdToHash = _getIdToHashOfSrcDstVertices(targetEdgeTable);
		
		//////

		final String edgeAnnoQuery = "COPY SELECT * FROM edge_anno WHERE id IN (SELECT id FROM " + targetEdgeTable
				+ ") TO stdout WITH (DELIMITER e'\\n');";
		final Map<String, Map<String, String>> edgeIdToAnnos = parseTableAsIdToAnnotations(edgeAnnoQuery, "edge");

		Set<String> edgeIds = new HashSet<String>();
		edgeIds.addAll(edgeIdToSrcDstIds.keySet());
		edgeIds.addAll(edgeIdToAnnos.keySet());
		
		for(String edgeId : edgeIds){
			SimpleEntry<String, String> srcDst = edgeIdToSrcDstIds.get(edgeId);
			String childHash = null; String parentHash = null;
			if(srcDst != null){
				childHash = vertexIdToHash.get(srcDst.getKey());
				parentHash = vertexIdToHash.get(srcDst.getValue());
			}
			edges.add(new QueriedEdge(edgeIdToHash.get(edgeId), childHash, parentHash, edgeIdToAnnos.get(edgeId)));
		}
		
		return edges;
	}

	@Override
	public Map<String, Map<String, String>> exportVertices(final Graph targetGraph){
		String targetVertexTable = queryEnvironment.getGraphVertexTableName(targetGraph);

		Map<String, Map<String, String>> result = _exportVertices(targetVertexTable);

		return result;
	}
	
	@Override
	public Set<QueriedEdge> exportEdges(final Graph targetGraph){
		String targetVertexTable = queryEnvironment.getGraphVertexTableName(targetGraph);
		String targetEdgeTable = queryEnvironment.getGraphEdgeTableName(targetGraph);
		
		Set<QueriedEdge> edges = _exportEdges(targetVertexTable, targetEdgeTable);

		return edges;
	}

	@Override
	public void getLink(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth){
		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "CREATE TABLE m_next (id INT);\n"
				+ "CREATE TABLE m_answer (id INT);");

		String filter;
		if(isBaseGraph(subjectGraph)){
			filter = "";
		}else{
			filter = " AND edge.id IN (SELECT id FROM " + getEdgeTableName(subjectGraph) + ")";
		}

		// Create subgraph edges table.
		qs.executeQuery("DROP TABLE m_sgconn;\n" + "CREATE TABLE m_sgconn (src INT, dst INT, depth INT);");

		qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + getVertexTableName(dstGraph) + ";\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;\n" + "\\analyzerange edge\n");

		String loopStmts = "\\analyzerange m_cur\n" + "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge"
				+ " WHERE dst IN (SELECT id FROM m_cur)" + filter + ";\n"
				+ "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge" + " WHERE src IN (SELECT id FROM m_cur)"
				+ filter + ";\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id INT);\n"
				+ "INSERT INTO m_next SELECT src FROM edge" + " WHERE dst IN (SELECT id FROM m_cur)" + filter
				+ " GROUP BY src;\n" + "INSERT INTO m_next SELECT dst FROM edge"
				+ " WHERE src IN (SELECT id FROM m_cur)" + filter + " GROUP BY dst;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i + 1)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_cur (id INT);\n"
				+ "CREATE TABLE m_next (id INT);");

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO m_cur SELECT id FROM "
				+ getVertexTableName(srcGraph) + " WHERE id IN (SELECT id FROM m_answer);\n");

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;" + "\\analyzerange m_answer m_sgconn\n");

		loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id int);\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_next SELECT dst FROM m_sgconn" + " WHERE src IN (SELECT id FROM m_cur)"
				+ " AND depth + $depth <= " + String.valueOf(maxDepth) + " GROUP BY dst;\n"
				+ "INSERT INTO m_next SELECT src FROM m_sgconn" + " WHERE dst IN (SELECT id FROM m_cur)"
				+ " AND depth + $depth <= " + String.valueOf(maxDepth) + " GROUP BY src;\n"
				+ "DROP TABLE m_cur;\n" + "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;\n"
				+ "INSERT INTO " + targetEdgeTable + " SELECT id FROM edge" + " WHERE src IN (SELECT id FROM m_answer)"
				+ " AND dst IN (SELECT id FROM m_answer)" + filter + ";");

		qs.executeQuery(
				"DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n" + "DROP TABLE m_sgconn;");
	}

	@Override
	public void getShortestPath(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth){
		String filter;
		qs.executeQuery("DROP TABLE m_conn;\n" + "CREATE TABLE m_conn (src INT, dst INT);");
		if(isBaseGraph(subjectGraph)){
			filter = "";
			qs.executeQuery(
					"\\analyzecount edge\n" + "INSERT INTO m_conn SELECT src, dst FROM edge GROUP BY src, dst;");
		}else{
			String subjectEdgeTable = getEdgeTableName(subjectGraph);
			filter = " AND edge.id IN (SELECT id FROM " + subjectEdgeTable + ")";
			qs.executeQuery("DROP TABLE m_sgedge;\n" + "CREATE TABLE m_sgedge (src INT, dst INT);\n" + "\\analyzerange "
					+ subjectEdgeTable + "\n" + "INSERT INTO m_sgedge SELECT src, dst FROM edge"
					+ " WHERE id IN (SELECT id FROM " + subjectEdgeTable + ");\n" + "\\analyzecount m_sgedge\n"
					+ "INSERT INTO m_conn SELECT src, dst FROM m_sgedge GROUP BY src, dst;\n" + "DROP TABLE m_sgedge;");
		}
		qs.executeQuery("\\analyze m_conn\n");

		// Create subgraph edges table.
		qs.executeQuery(
				"DROP TABLE m_sgconn;\n" + "CREATE TABLE m_sgconn (src INT, dst INT, reaching INT, depth INT);");

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "CREATE TABLE m_cur (id INT, reaching INT);\n" + "CREATE TABLE m_next (id INT, reaching INT);\n"
				+ "CREATE TABLE m_answer (id INT);");

		qs.executeQuery("INSERT INTO m_cur SELECT id, id FROM " + getVertexTableName(dstGraph) + ";\n"
				+ "\\analyzerange m_cur\n" + "INSERT INTO m_answer SELECT id FROM m_cur GROUP BY id;");

		String loopStmts = "\\analyzecount m_cur\n" + "INSERT INTO m_sgconn SELECT src, dst, reaching, $depth"
				+ " FROM m_cur, m_conn WHERE id = dst;\n" + "DROP TABLE m_next;\n"
				+ "CREATE TABLE m_next (id INT, reaching INT);\n" + "INSERT INTO m_next SELECT src, reaching"
				+ " FROM m_cur, m_conn WHERE id = dst;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT, reaching INT);\n" + "\\analyzerange m_answer\n"
				+ "\\analyzecount m_next\n" + "INSERT INTO m_cur SELECT id, reaching FROM m_next"
				+ " WHERE id NOT IN (SELECT id FROM m_answer) GROUP BY id, reaching;\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur GROUP BY id;";
		for(int i = 0; i < maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i + 1)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_cur (id INT);\n"
				+ "CREATE TABLE m_next (id INT);");

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO m_cur SELECT id FROM "
				+ getVertexTableName(srcGraph) + " WHERE id IN (SELECT id FROM m_answer);\n");

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;");

		qs.executeQuery("\\analyzerange m_answer\n" + "\\analyze m_sgconn\n");

		loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next(id INT);\n" + "INSERT INTO m_next SELECT MIN(dst)"
				+ " FROM m_cur, m_sgconn WHERE id = src" + " AND depth + $depth <= "
				+ String.valueOf(maxDepth) + " GROUP BY src, reaching;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur(id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;\n"
				+ "INSERT INTO " + targetEdgeTable + " SELECT id FROM edge" + " WHERE src IN (SELECT id FROM m_answer)"
				+ " AND dst IN (SELECT id FROM m_answer)" + filter + ";");

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "DROP TABLE m_conn;\n" + "DROP TABLE m_sgconn;");
	}

	@Override
	public void getSubgraph(Graph targetGraph, Graph subjectGraph, Graph skeletonGraph){
		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);
		String subjectVertexTable = getVertexTableName(subjectGraph);
		String subjectEdgeTable = getEdgeTableName(subjectGraph);
		String skeletonVertexTable = getVertexTableName(skeletonGraph);
		String skeletonEdgeTable = getEdgeTableName(skeletonGraph);

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);");

		// Get vertices.
		qs.executeQuery("\\analyzerange " + subjectVertexTable + "\n" + "INSERT INTO m_answer SELECT id FROM "
				+ skeletonVertexTable + " WHERE id IN (SELECT id FROM " + subjectVertexTable + ");\n"
				+ "INSERT INTO m_answer SELECT src FROM edge " + " WHERE id IN (SELECT id FROM " + skeletonEdgeTable
				+ ")" + " AND src IN (SELECT id FROM " + subjectVertexTable + ");\n"
				+ "INSERT INTO m_answer SELECT dst FROM edge" + " WHERE id IN (SELECT id FROM " + skeletonEdgeTable
				+ ")" + " AND dst IN (SELECT id FROM " + subjectVertexTable + ");\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer GROUP BY id;\n");

		// Get edges.
		qs.executeQuery(
				"\\analyzerange " + subjectEdgeTable + "\n" + "INSERT INTO " + targetEdgeTable + " SELECT s.id FROM "
						+ subjectEdgeTable + " s, edge e" + " WHERE s.id = e.id AND e.src IN (SELECT id FROM m_answer)"
						+ " AND e.dst IN (SELECT id FROM m_answer) GROUP BY s.id;");

		qs.executeQuery("DROP TABLE m_answer;");
	}

	@Override
	public void getLineage(Graph targetGraph, Graph subjectGraph, Graph startGraph, int depth, Direction directionArg){
		List<Direction> oneDirs = new ArrayList<Direction>();
		if(directionArg == Direction.kBoth){
			oneDirs.add(Direction.kAncestor);
			oneDirs.add(Direction.kDescendant);
		}else{
			oneDirs.add(directionArg);
		}

		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);

		String subjectEdgeTable = getEdgeTableName(subjectGraph);
		String filter = "";
		if(!isBaseGraph(subjectGraph)){
			qs.executeQuery("\\analyzerange " + subjectEdgeTable + "\n");
			filter = " AND edge.id IN (SELECT id FROM " + subjectEdgeTable + ")";
		}

		for(Direction oneDir : oneDirs){
			executeOneDirection(oneDir, filter, depth, startGraph);
			qs.executeQuery("\\analyzerange m_answer m_answer_edge\n" + "INSERT INTO " + targetVertexTable
					+ " SELECT id FROM m_answer;\n" + "INSERT INTO " + targetEdgeTable
					+ " SELECT id FROM m_answer_edge GROUP BY id;");
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "DROP TABLE m_answer_edge;");
	}

	private void executeOneDirection(Direction dir, String filter, int depth, Graph startGraph){
		String src, dst;
		if(dir == Direction.kAncestor){
			src = "src";
			dst = "dst";
		}else{
			if(dir != Direction.kDescendant){
				throw new RuntimeException("Unexpected direction: " + dir);
			}
			src = "dst";
			dst = "src";
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "DROP TABLE m_answer_edge;\n" + "CREATE TABLE m_cur (id INT);\n" + "CREATE TABLE m_next (id INT);\n"
				+ "CREATE TABLE m_answer (id INT);\n" + "CREATE TABLE m_answer_edge (id LONG);");

		String startVertexTable = getVertexTableName(startGraph);
		qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + startVertexTable + ";\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;");

		String loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id INT);\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_next SELECT " + dst + " FROM edge" + " WHERE " + src + " IN (SELECT id FROM m_cur)"
				+ filter + " GROUP BY " + dst + ";\n" + "INSERT INTO m_answer_edge SELECT id FROM edge" + " WHERE "
				+ src + " IN (SELECT id FROM m_cur)" + filter + ";\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < depth; ++i){
			qs.executeQuery(loopStmts);

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}
	}

	@Override
	public void getSimplePath(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth){
		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);
		String subjectEdgeTable = getEdgeTableName(subjectGraph);
		String dstVertexTable = getVertexTableName(dstGraph);
		String srcVertexTable = getVertexTableName(srcGraph);
		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "CREATE TABLE m_next (id INT);\n"
				+ "CREATE TABLE m_answer (id INT);");

		String filter;
		if(isBaseGraph(subjectGraph)){
			filter = "";
		}else{
			filter = " AND edge.id IN (SELECT id FROM " + subjectEdgeTable + ")";
		}

		// Create subgraph edges table.
		qs.executeQuery("DROP TABLE m_sgconn;\n" + "CREATE TABLE m_sgconn (src INT, dst INT, depth INT);");

		qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + dstVertexTable + ";\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;\n" + "\\analyzerange edge\n");

		String loopStmts = "\\analyzerange m_cur\n" + "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge"
				+ " WHERE dst IN (SELECT id FROM m_cur)" + filter + ";\n" + "DROP TABLE m_next;\n"
				+ "CREATE TABLE m_next (id INT);\n" + "INSERT INTO m_next SELECT src FROM edge"
				+ " WHERE dst IN (SELECT id FROM m_cur)" + filter + " GROUP BY src;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i + 1)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_cur (id INT);\n"
				+ "CREATE TABLE m_next (id INT);");

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO m_cur SELECT id FROM " + srcVertexTable
				+ " WHERE id IN (SELECT id FROM m_answer);\n");

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;" + "\\analyzerange m_answer m_sgconn\n");

		loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id int);\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_next SELECT dst FROM m_sgconn" + " WHERE src IN (SELECT id FROM m_cur)"
				+ " AND depth + $depth <= " + String.valueOf(maxDepth) + " GROUP BY dst;\n"
				+ "\\analyzerange m_next\n" + "INSERT INTO " + targetEdgeTable + " SELECT id FROM edge"
				+ " WHERE src IN (SELECT id FROM m_cur)" + " AND dst IN (SELECT id FROM m_next)" + filter + ";\n"
				+ "DROP TABLE m_cur;\n" + "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;");

		qs.executeQuery(
				"DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n" + "DROP TABLE m_sgconn;");
	}

	@Override
	public void collapseEdge(Graph targetGraph, Graph sourceGraph, ArrayList<String> fields){
		String sourceVertexTable = getVertexTableName(sourceGraph);
		String sourceEdgeTable = getEdgeTableName(sourceGraph);
		String targetVertexTable = getVertexTableName(targetGraph);
		String targetEdgeTable = getEdgeTableName(targetGraph);

		qs.executeQuery("\\analyzerange " + sourceVertexTable + " " + sourceEdgeTable + "\n");
		qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id FROM " + sourceVertexTable + ";");

		StringBuilder tables = new StringBuilder();
		StringBuilder predicates = new StringBuilder();
		StringBuilder groups = new StringBuilder();

		for(int i = 0; i < fields.size(); ++i){
			String edgeAnnoName = "ea" + i;
			tables.append(", edge_anno " + edgeAnnoName);
			predicates.append(" AND e.id = " + edgeAnnoName + ".id" + " AND " + edgeAnnoName + ".field = '"
					+ fields.get(i) + "'");
			groups.append(", " + edgeAnnoName + ".value");
		}

		qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT MIN(e.id) FROM edge e" + tables.toString()
				+ " WHERE e.id IN (SELECT id FROM " + sourceEdgeTable + ")" + predicates.toString()
				+ " GROUP BY src, dst" + groups.toString() + ";");
	}

	private void getSubsetBasedOnType(final String sourceTable, final String targetTable, final String intermTable,
			final String intermTableIdType, final String graphElementTableName, final long fromInclusive,
			final long toExclusive){
		final long range = toExclusive - fromInclusive;
		final String md5ColumnName = "md5";
		final String idColumnName = "id";

		qs.executeQuery("DROP TABLE " + intermTable + ";\n");
		qs.executeQuery(
				"CREATE TABLE " + intermTable + " (" + idColumnName + " " + intermTableIdType + ", " + md5ColumnName
						+ " CHAR(32));\n");

		qs.executeQuery("\\analyzerange " + sourceTable + "\n");

		qs.executeQuery("insert into " + intermTable + " select " + idColumnName + ", " + md5ColumnName + " from "
				+ graphElementTableName + " where " + idColumnName + " in " + "(select " + idColumnName + " from "
				+ sourceTable + ")" + " order by " + md5ColumnName
				+ " asc limit " + toExclusive + ";\n");
		qs.executeQuery("insert into " + targetTable + " select " + idColumnName + " from " + intermTable + " order by "
				+ md5ColumnName + " desc limit " + range + ";");

		qs.executeQuery("DROP TABLE " + intermTable + ";\n");
	}

	@Override
	public void getSubsetVertex(final Graph targetGraph, final Graph sourceGraph, final long fromInclusive,
			final long toExclusive){
		final String sourceTable = getVertexTableName(sourceGraph);
		final String targetTable = getVertexTableName(targetGraph);
		final String intermTable = "m_interm_" + targetTable;
		final String intermTableIdType = "INT";
		
		getSubsetBasedOnType(sourceTable, targetTable, intermTable, intermTableIdType, vertexTableName, fromInclusive,
				toExclusive);
	}

	@Override
	public void getSubsetEdge(final Graph targetGraph, final Graph sourceGraph, final long fromInclusive,
			final long toExclusive){
		final String sourceTable = getEdgeTableName(sourceGraph);
		final String targetTable = getEdgeTableName(targetGraph);
		final String intermTable = "m_interm_" + targetTable;
		final String intermTableIdType = "LONG";

		getSubsetBasedOnType(sourceTable, targetTable, intermTable, intermTableIdType, edgeTableName, fromInclusive,
				toExclusive);
	}

	private String getVertexTableName(Graph graph){
		return queryEnvironment.getGraphVertexTableName(graph);
	}

	private String getEdgeTableName(Graph graph){
		return queryEnvironment.getGraphEdgeTableName(graph);
	}

	private boolean isBaseGraph(Graph graph){
		return queryEnvironment.isBaseGraph(graph);
	}
	
	private String FormatStringLiteral(String input){
		final String kDigits = "0123456789ABCDEF";
		StringBuilder sb = new StringBuilder();
		sb.append("e'");
		for(int i = 0; i < input.length(); ++i){
			char c = input.charAt(i);
			if(c >= 32){
				if(c == '\\' || c == '\''){
					sb.append(c);
				}
				sb.append(c);
				continue;
			}
			switch(c){
			case '\b':
				sb.append("\\b");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			default:
				// Use hexidecimal representation.
				sb.append("\\x");
				sb.append(kDigits.charAt(c >> 4));
				sb.append(kDigits.charAt(c & 0xF));
				break;
			}
		}
		sb.append("'");
		return sb.toString();
	}

	@Override
	public void setGraphMetadata(GraphMetadata targetMetadata, SetGraphMetadata.Component component, Graph sourceGraph, String name,
			String value){
		String targetVertexTable = queryEnvironment.getMetadataVertexTableName(targetMetadata);
		String targetEdgeTable = queryEnvironment.getMetadataEdgeTableName(targetMetadata);
		String sourceVertexTable = queryEnvironment.getGraphVertexTableName(sourceGraph);
		String sourceEdgeTable = queryEnvironment.getGraphEdgeTableName(sourceGraph);

		qs.executeQuery("\\analyzerange " + sourceVertexTable + " " + sourceEdgeTable + "\n");

		if(component == Component.kVertex || component == Component.kBoth){
			qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id, " + FormatStringLiteral(name)
					+ ", " + FormatStringLiteral(value) + " FROM " + sourceVertexTable + " GROUP BY id;");
		}

		if(component == Component.kEdge || component == Component.kBoth){
			qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT id, " + FormatStringLiteral(name)
					+ ", " + FormatStringLiteral(value) + " FROM " + sourceEdgeTable + " GROUP BY id;");
		}
	}
	
	@Override
	public void createEmptyGraphMetadata(GraphMetadata metadata){
		QuickstepUtil.CreateEmptyGraphMetadata(qs, queryEnvironment, metadata);
	}

	@Override
	public void overwriteGraphMetadata(GraphMetadata targetMetadata, GraphMetadata lhsMetadata, GraphMetadata rhsMetadata){
		String targetVertexTable = queryEnvironment.getMetadataVertexTableName(targetMetadata);
		String targetEdgeTable = queryEnvironment.getMetadataEdgeTableName(targetMetadata);
		String lhsVertexTable = queryEnvironment.getMetadataVertexTableName(lhsMetadata);
		String lhsEdgeTable = queryEnvironment.getMetadataEdgeTableName(lhsMetadata);
		String rhsVertexTable = queryEnvironment.getMetadataVertexTableName(rhsMetadata);
		String rhsEdgeTable = queryEnvironment.getMetadataEdgeTableName(rhsMetadata);

		qs.executeQuery("\\analyzerange " + rhsVertexTable + " " + rhsEdgeTable + "\n" + "INSERT INTO "
				+ targetVertexTable + " SELECT id, name, value FROM " + lhsVertexTable + " l"
				+ " WHERE NOT EXISTS (SELECT * FROM " + rhsVertexTable + " r"
				+ " WHERE l.id = r.id AND l.name = r.name);\n" + "INSERT INTO " + targetEdgeTable
				+ " SELECT id, name, value FROM " + lhsEdgeTable + " l" + " WHERE NOT EXISTS (SELECT * FROM "
				+ rhsEdgeTable + " r" + " WHERE l.id = r.id AND l.name = r.name);\n" + "INSERT INTO "
				+ targetVertexTable + " SELECT id, name, value FROM " + rhsVertexTable + ";\n" + "INSERT INTO "
				+ targetEdgeTable + " SELECT id, name, value FROM " + rhsEdgeTable + ";");
	}
}
