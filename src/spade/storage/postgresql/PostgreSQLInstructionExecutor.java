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
import java.util.Map;
import java.util.Set;

import spade.core.AbstractStorage;
import spade.query.quickgrail.core.GraphDescription;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
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
import spade.query.quickgrail.instruction.GetLineage.Direction;
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
import spade.query.quickgrail.instruction.SetGraphMetadata.Component;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;
import spade.storage.PostgreSQL;
import spade.utility.HelperFunctions;

/**
 * @author Raza
 */
public class PostgreSQLInstructionExecutor extends QueryInstructionExecutor{

	private final PostgreSQL storage;
	private final PostgreSQLQueryEnvironment queryEnvironment;
	
	private final String idColumnName;
	private final String idChildVertexColumnName;
	private final String idParentVertexColumnName;
	private final String vertexAnnotationTableName;
	private final String edgeAnnotationTableName;

	public PostgreSQLInstructionExecutor(PostgreSQL storage, PostgreSQLQueryEnvironment queryEnvironment,
			String idColumnName, String idChildVertexColumnName, String idParentVertexColumnName,
			String vertexAnnotationTableName, String edgeAnnotationTableName){
		this.storage = storage;
		this.queryEnvironment = queryEnvironment;
		this.idColumnName = idColumnName;
		this.idChildVertexColumnName = idChildVertexColumnName;
		this.idParentVertexColumnName = idParentVertexColumnName;
		this.vertexAnnotationTableName = vertexAnnotationTableName;
		this.edgeAnnotationTableName = edgeAnnotationTableName;
		if(this.queryEnvironment == null){
			throw new IllegalArgumentException("NULL Query Environment");
		}
		if(this.storage == null){
			throw new IllegalArgumentException("NULL query executor");
		}
		if(HelperFunctions.isNullOrEmpty(this.idColumnName)){
			throw new IllegalArgumentException("NULL/Empty id column name: " + this.idColumnName);
		}
		if(HelperFunctions.isNullOrEmpty(this.idChildVertexColumnName)){
			throw new IllegalArgumentException("NULL/Empty child vertex id column name: " + this.idChildVertexColumnName);
		}
		if(HelperFunctions.isNullOrEmpty(this.idParentVertexColumnName)){
			throw new IllegalArgumentException("NULL/Empty parent vertex id column name: " + this.idParentVertexColumnName);
		}
		if(HelperFunctions.isNullOrEmpty(this.vertexAnnotationTableName)){
			throw new IllegalArgumentException("NULL/Empty vertex table name: " + this.vertexAnnotationTableName);
		}
		if(HelperFunctions.isNullOrEmpty(this.edgeAnnotationTableName)){
			throw new IllegalArgumentException("NULL/Empty edge table name: " + this.edgeAnnotationTableName);
		}
	}

	@Override
	public AbstractStorage getStorage(){
		return storage;
	}

	private String getIdColumnName(){
		return idColumnName;
	}
	
	private String getIdColumnNameChildVertex(){
		return idChildVertexColumnName;
	}
	
	private String getIdColumnNameParentVertex(){
		return idParentVertexColumnName;
	}
	
	private String getVertexAnnotationTableName(){
		return vertexAnnotationTableName;
	}
	
	private String getEdgeAnnotationTableName(){
		return edgeAnnotationTableName;
	}
	
	private String getVertexTableName(Graph graph){
		return queryEnvironment.getGraphVertexTableName(graph);
	}
	
	private String getEdgeTableName(Graph graph){
		return queryEnvironment.getGraphEdgeTableName(graph);
	}
	
	private Set<String> getColumnNamesOfVertexAnnotationTable(){
		Set<String> columnNames = new HashSet<String>();
		columnNames.addAll(
				executeQueryForResult("select * from " + getVertexAnnotationTableName() + " where false", true).get(0)
				);
		return columnNames;
	}
	
	private Set<String> getColumnNamesOfEdgeAnnotationTable(){
		Set<String> columnNames = new HashSet<String>();
		columnNames.addAll(
				executeQueryForResult("select * from " + getEdgeAnnotationTableName() + " where false", true).get(0)
				);
		return columnNames;
	}

	@Override
	public PostgreSQLQueryEnvironment getQueryEnvironment(){
		return queryEnvironment;
	}

	private String getMetadataVertexTableName(GraphMetadata graphMetadata){
		return queryEnvironment.getMetadataVertexTableName(graphMetadata);
	}

	private String getMetadataEdgeTableName(GraphMetadata graphMetadata){
		return queryEnvironment.getMetadataEdgeTableName(graphMetadata);
	}

	private List<List<String>> executeQueryForResult(String query, boolean addColumnNames){
		return storage.executeQueryForResult(query, addColumnNames);
	}

	@Override
	public void insertLiteralEdge(InsertLiteralEdge instruction){
		if(!instruction.getEdges().isEmpty()){
			String insertSubpart = "";
			for(String edge : instruction.getEdges()){
				if(edge.length() <= 32){
					insertSubpart += "('" + edge + "'), ";
				}
			}

			if(!insertSubpart.isEmpty()){
				final String tempEdgeTable = "m_edgehash";
				executeQueryForResult("drop table if exists " + tempEdgeTable + ";\n", false);
				executeQueryForResult("create table " + tempEdgeTable + " (" + getIdColumnName() + " uuid);\n", false);
				insertSubpart = insertSubpart.substring(0, insertSubpart.length() - 2);
				executeQueryForResult("insert into " + tempEdgeTable + " values " + insertSubpart + ";\n", false);

				executeQueryForResult("insert into " + getEdgeTableName(instruction.targetGraph) + " select "
						+ getIdColumnName() + " from " + getEdgeAnnotationTableName() + " where " + getIdColumnName()
						+ " in (select " + getIdColumnName() + " from " + tempEdgeTable + " group by "
						+ getIdColumnName() + ");\n", false);

				executeQueryForResult("drop table " + tempEdgeTable + ";\n", false);
			}
		}
	}

	@Override
	public void insertLiteralVertex(InsertLiteralVertex instruction){
		if(!instruction.getVertices().isEmpty()){
			String insertSubpart = "";
			for(String vertex : instruction.getVertices()){
				if(vertex.length() <= 32){
					insertSubpart += "('" + vertex + "'), ";
				}
			}

			if(!insertSubpart.isEmpty()){
				final String tempVertexTable = "m_vertexhash";
				executeQueryForResult("drop table if exists " + tempVertexTable + ";\n", false);
				executeQueryForResult("create table " + tempVertexTable + " (" + getIdColumnName() + " uuid);\n", false);
				insertSubpart = insertSubpart.substring(0, insertSubpart.length() - 2);
				executeQueryForResult("insert into " + tempVertexTable + " values " + insertSubpart + ";\n", false);

				executeQueryForResult("insert into " + getVertexTableName(instruction.targetGraph) + " select "
						+ getIdColumnName() + " from " + getVertexAnnotationTableName() + " where " + getIdColumnName()
						+ " in (select " + getIdColumnName() + " from " + tempVertexTable + " group by "
						+ getIdColumnName() + ");\n", false);

				executeQueryForResult("drop table " + tempVertexTable + ";\n", false);
			}
		}
	}

	private void createUUIDTable(String tableName, boolean deleteFirst){
		if(deleteFirst){
			dropTable(tableName);
		}
		String createQuery = "create table if not exists " + tableName + "(" + getIdColumnName() + " uuid" + ")";
		executeQueryForResult(createQuery, false);
	}
	
	private void createUUIDShortestPathTable(String tableName, boolean deleteFirst){
		if(deleteFirst){
			dropTable(tableName);
		}
		String createQuery = "create table if not exists " + tableName + "(" + getIdColumnName() + " uuid" + ", reaching int)";
		executeQueryForResult(createQuery, false);
	}
	
	private void createChildParentTable(String tableName, boolean deleteFirst){
		if(deleteFirst){
			dropTable(tableName);
		}
		String createQuery = "create table if not exists " + tableName + "(\"" + getIdColumnNameChildVertex() + "\" uuid" + ","
				+ "\""+getIdColumnNameParentVertex()+"\" uuid)";
		executeQueryForResult(createQuery, false);
	}
	
	private void dropTable(String tableName){
		String dropQuery = "drop table if exists " + tableName;
		executeQueryForResult(dropQuery, false);
	}

	@Override
	public void createEmptyGraph(CreateEmptyGraph instruction){
		String vertexTable = getVertexTableName(instruction.graph);
		String edgeTable = getEdgeTableName(instruction.graph);

		createUUIDTable(vertexTable, true);
		createUUIDTable(edgeTable, true);
	}

	@Override
	public void distinctifyGraph(DistinctifyGraph instruction){
		String sourceVertexTable = getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = getEdgeTableName(instruction.sourceGraph);
		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = getEdgeTableName(instruction.targetGraph);

		executeQueryForResult("insert into " + targetVertexTable + " select " + getIdColumnName() + " from "
				+ sourceVertexTable + " group by " + getIdColumnName() + ";", false);
		executeQueryForResult("insert into " + targetEdgeTable + " select " + getIdColumnName() + " from "
				+ sourceEdgeTable + " group by " + getIdColumnName() + ";", false);
	}

	private String buildComparison(String columnName, PredicateOperator operator, String value){
		String query = "";
		if(columnName.equals(getIdColumnName()) ||
				columnName.equals(getIdColumnNameChildVertex()) ||
				columnName.equals(getIdColumnNameParentVertex())){
			query += "\"" + columnName + "\"::text ";
		}else{
			query += "\"" + columnName + "\" ";
		}
		switch(operator){
			case EQUAL: query += "="; break;
			case GREATER: query += ">"; break;
			case GREATER_EQUAL: query += ">="; break;
			case LESSER: query += "<"; break;
			case LESSER_EQUAL: query += "<="; break;
			case NOT_EQUAL: query += "<>"; break;
			case REGEX: query += "~"; break;
			case LIKE: query += "like"; break;
			default: throw new RuntimeException("Unexpected comparison operator");
		}
		value = value.replace("'", "''");
		query += " '" + value + "'";
		return query;
	}
	
	@Override
	public void getWhereAnnotationsExist(final GetWhereAnnotationsExist instruction){
		final Graph targetGraph = instruction.targetGraph;
		final Graph subjectGraph = instruction.subjectGraph;
		final ArrayList<String> annotationKeys = instruction.getAnnotationKeys();
		
		final Set<String> existingColumnNames = getColumnNamesOfVertexAnnotationTable();
		final Set<String> requestedColumnNames = new HashSet<String>(annotationKeys);
		requestedColumnNames.removeAll(existingColumnNames);
		if(requestedColumnNames.size() > 0){
			// User specified column names which do not exist for vertices
			return;
		}
		
		String query = "";
		query += "insert into " + getVertexTableName(targetGraph) + " "
				+ "select v.\""+getIdColumnName()+"\" from " + getVertexAnnotationTableName() + " v "
				+ "where v.\""+getIdColumnName()+"\" in (select \""+getIdColumnName()+"\" from "
				+getVertexTableName(subjectGraph)+") and ";
		
		for(int i = 0; i < annotationKeys.size(); i++){
			final String annotationKey = annotationKeys.get(i);
			query += "v.\""+annotationKey+"\" is not null";
			if(i == annotationKeys.size() - 1){ // is last
				// don't append the 'and'
			}else{
				query += " and ";
			}
		}
		query += ";";
		executeQueryForResult(query, false);
	}

	@Override
	public void getMatch(final GetMatch instruction){
		final Graph g1 = createNewGraph();
		getWhereAnnotationsExist(new GetWhereAnnotationsExist(g1, instruction.graph1, instruction.getAnnotationKeys()));
		final Graph g2 = createNewGraph();
		getWhereAnnotationsExist(new GetWhereAnnotationsExist(g2, instruction.graph2 , instruction.getAnnotationKeys()));

		final ArrayList<String> annotationKeys = instruction.getAnnotationKeys();

		final Set<String> existingColumnNames = getColumnNamesOfVertexAnnotationTable();
		final Set<String> requestedColumnNames = new HashSet<String>(annotationKeys);
		requestedColumnNames.removeAll(existingColumnNames);
		if(requestedColumnNames.size() > 0){
			// User specified column names which do not exist for vertices
			return;
		}

		final String vertexAnnotationsTableName = getVertexAnnotationTableName();

		executeQueryForResult("drop table if exists m_answer_x", false);
		executeQueryForResult("create table m_answer_x (id1 uuid, id2 uuid)", false);

		String query = "insert into m_answer_x "
				+ "select ga1.\""+getIdColumnName()+"\", ga2.\""+getIdColumnName()+"\" from " 
				+ getVertexTableName(g1) + " gv1, " + vertexAnnotationsTableName + " ga1, "
				+ getVertexTableName(g2) + " gv2, " + vertexAnnotationsTableName + " ga2 "
				+ "where gv1.\""+getIdColumnName()+"\" = ga1.\""+getIdColumnName()+"\" "
				+ "and gv2.\""+getIdColumnName()+"\" = ga2.\""+getIdColumnName()+"\" and ";
		
		for(int i = 0; i < annotationKeys.size(); i++){
			String annotationKey = annotationKeys.get(i);
			if(annotationKey.equals(getIdColumnName()) 
					|| annotationKey.equals(getIdColumnNameChildVertex())
					|| annotationKey.equals(getIdColumnNameParentVertex())){
				annotationKey = "\"" + annotationKey + "\"::text";
			}else{
				annotationKey = "\"" + annotationKey + "\"";
			}
			query += "( " 
					+ "ga1." + annotationKey + " = ga2." + annotationKey
					+ "and ga1."+annotationKey+" is not null and ga2." + annotationKey + " is not null "
					+ ")";
			if(i == annotationKeys.size() - 1){
				// is last so don't append 'and'
			}else{
				query += " and ";
			}
		}
		
		executeQueryForResult(query, false);
		
		executeQueryForResult("drop table if exists m_answer_y", false);
		executeQueryForResult("create table m_answer_y (id uuid)", false);
		executeQueryForResult("insert into m_answer_y select id1 from m_answer_x group by id1", false);
		executeQueryForResult("insert into m_answer_y select id2 from m_answer_x group by id2", false);
		
		executeQueryForResult("insert into " + getVertexTableName(instruction.targetGraph) 
			+ " select id from m_answer_y group by id;\n", false);
		
		executeQueryForResult("drop table if exists m_answer_y", false);
		executeQueryForResult("drop table if exists m_answer_x", false);
	}
	
	@Override
	public final GraphDescription describeGraph(final DescribeGraph instruction){
		final Graph graph = instruction.graph;
		/*
		if(queryEnvironment.isBaseGraph(graph)){
			final String vertexQuery = "select * from " + vertexAnnotationTableName + " where false";
			final List<List<String>> vertexResult = executeQueryForResult(vertexQuery, true);
			final List<String> vertexHeader = new ArrayList<String>();
			if(vertexResult.isEmpty()){
				throw new RuntimeException("Expected at least first row from query: " + vertexQuery);
			}else{
				vertexHeader.addAll(vertexResult.get(0));
				vertexHeader.remove(idColumnName); // Remove the hash
			}
			
			final String edgeQuery = "select * from " + edgeAnnotationTableName + " where false";
			final List<List<String>> edgeResult = executeQueryForResult(edgeQuery, true);
			final List<String> edgeHeader = new ArrayList<String>();
			if(edgeResult.isEmpty()){
				throw new RuntimeException("Expected at least first row from query: " + edgeQuery);
			}else{
				edgeHeader.addAll(edgeResult.get(0));
				edgeHeader.remove(idColumnName); // Remove the hash
				edgeHeader.remove(idChildVertexColumnName); // Remove the child hash
				edgeHeader.remove(idParentVertexColumnName); // Remove the parent hash
			}
			
			final GraphDescription desc = new GraphDescription();
			desc.addVertexAnnotations(vertexHeader);
			desc.addEdgeAnnotations(edgeHeader);
			return desc;
		}else{
			final GraphDescription baseDesc = describeGraph(new DescribeGraph(queryEnvironment.getBaseGraph()));
			
			final Set<String> resultVertexAnnotations = new HashSet<String>();
			
			final Set<String> baseDescVertexAnnotations = baseDesc.getVertexAnnotations();
			for(final String baseDescVertexAnnotation : baseDescVertexAnnotations){
				final String query = "select count(*) from " + getVertexAnnotationTableName()
					+ " where \"" + idColumnName + "\" in (select \""+idColumnName+"\" from "+getVertexTableName(graph)+")"
					+ " and \"" + baseDescVertexAnnotation + "\" is not null;";
				final long count = Long.parseLong(executeQueryForResult(query, false).get(0).get(0));
				if(count > 0){
					resultVertexAnnotations.add(baseDescVertexAnnotation);
				}
			}
			
			final Set<String> resultEdgeAnnotations = new HashSet<String>();
			
			final Set<String> baseDescEdgeAnnotations = baseDesc.getEdgeAnnotations();
			for(final String baseDescEdgeAnnotation : baseDescEdgeAnnotations){
				final String query = "select count(*) from " + getEdgeAnnotationTableName()
					+ " where \"" + idColumnName + "\" in (select \""+idColumnName+"\" from "+getEdgeTableName(graph)+")"
					+ " and \"" + baseDescEdgeAnnotation + "\" is not null;";
				final long count = Long.parseLong(executeQueryForResult(query, false).get(0).get(0));
				if(count > 0){
					resultEdgeAnnotations.add(baseDescEdgeAnnotation);
				}
			}
			 
			final GraphDescription desc = new GraphDescription();
			desc.addVertexAnnotations(resultVertexAnnotations);
			desc.addEdgeAnnotations(resultEdgeAnnotations);
			return desc;
		}
		*/
		throw new RuntimeException("Unsupported for PostgreSQL");
	}
	
	@Override
	public void getVertex(GetVertex instruction){
		if(!instruction.hasArguments()){
			String sqlQuery = 
					"insert into " + getVertexTableName(instruction.targetGraph) 
					+ " select " + getIdColumnName() + " from "
					+ getVertexTableName(instruction.subjectGraph)
					+ " group by " + getIdColumnName();
			executeQueryForResult(sqlQuery, false);
		}else{ // has arguments
			final String wildCard = "*";
			final Set<String> existingColumnNames = getColumnNamesOfVertexAnnotationTable();
			final String annotationKey = instruction.annotationKey;
			final PredicateOperator operator = instruction.operator;
			
			if(!annotationKey.equals(wildCard) && !existingColumnNames.contains(annotationKey)){
				if(!operator.equals(PredicateOperator.NOT_EQUAL)){
					// Don't insert anything since the value is null and it cannot match anything
				}else{
					// insert everything because the column is null so it would never equal the value passed
					String sqlQuery = "insert into " + getVertexTableName(instruction.targetGraph)
						+ " select " + getIdColumnName() + " from "
						+ getVertexAnnotationTableName();
					
					if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
						sqlQuery += " where  " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+getVertexTableName(instruction.subjectGraph)+")";
					}
					
					sqlQuery += " group by " + getIdColumnName() + ";";
					executeQueryForResult(sqlQuery, false);
				}
			}else{
				String sqlQuery = 
						"insert into " + getVertexTableName(instruction.targetGraph) 
						+ " select " + getIdColumnName() + " from "
						+ getVertexAnnotationTableName();
				
				Set<String> columnNames = new HashSet<String>();
				
				if("*".equals(instruction.annotationKey)){
					columnNames.addAll(existingColumnNames);
				}else{
					columnNames.add(instruction.annotationKey);
				}
				
				sqlQuery += " where (";
				
				for(String columnName : columnNames){
					sqlQuery += buildComparison(columnName, operator, instruction.annotationValue) + " or ";	
				}
				
				sqlQuery = sqlQuery.substring(0, sqlQuery.length() - 3); // remove the last 'or '
				sqlQuery += ")";
				
				if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
					sqlQuery += " and " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+getVertexTableName(instruction.subjectGraph)+")";
				}
				
				sqlQuery += " group by " + getIdColumnName() + ";";
				executeQueryForResult(sqlQuery, false);
			}
		}
	}

	@Override
	public ResultTable evaluateQuery(EvaluateQuery instruction){
		ResultTable table = new ResultTable();

		List<List<String>> listOfLists = executeQueryForResult(instruction.nativeQuery, true);
		if(listOfLists.size() > 0){
			List<String> headings = listOfLists.remove(0);
			for(List<String> row : listOfLists){
				ResultTable.Row tablerow = new ResultTable.Row();
				for(String col : row){
					tablerow.add(String.valueOf(col));
				}
				table.addRow(tablerow);
			}
			Schema schema = new Schema();
			for(String heading : headings){
				schema.addColumn(heading, StringType.GetInstance());
			}
			table.setSchema(schema);
		}

		return table;
	}

	@Override
	public void getEdge(GetEdge instruction){
		if(!instruction.hasArguments()){
			String sqlQuery = 
					"insert into " + getEdgeTableName(instruction.targetGraph) 
					+ " select " + getIdColumnName() + " from "
					+ getEdgeTableName(instruction.subjectGraph)
					+ " group by " + getIdColumnName();
			executeQueryForResult(sqlQuery, false);
		}else{ // has arguments
			final String wildCard = "*";
			final Set<String> existingColumnNames = getColumnNamesOfEdgeAnnotationTable();
			final String annotationKey = instruction.annotationKey;
			final PredicateOperator operator = instruction.operator;
			
			if(!annotationKey.equals(wildCard) && !existingColumnNames.contains(annotationKey)){
				if(!operator.equals(PredicateOperator.NOT_EQUAL)){
					// Don't insert anything since the value is null and it cannot match anything
				}else{
					// insert everything because the column is null so it would never equal the value passed
					String sqlQuery = "insert into " + getEdgeTableName(instruction.targetGraph)
						+ " select " + getIdColumnName() + " from "
						+ getEdgeAnnotationTableName();
					
					if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
						sqlQuery += " where " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+getEdgeTableName(instruction.subjectGraph)+")";
					}
					
					sqlQuery += " group by " + getIdColumnName() + ";";
					executeQueryForResult(sqlQuery, false);
				}
			}else{
			
				String sqlQuery = 
						"insert into " + getEdgeTableName(instruction.targetGraph) 
						+ " select " + getIdColumnName() + " from "
						+ getEdgeAnnotationTableName();
				
				Set<String> columnNames = new HashSet<String>();
				
				if("*".equals(instruction.annotationKey)){
					columnNames.addAll(existingColumnNames);
				}else{
					columnNames.add(annotationKey);
				}
				
				sqlQuery += " where (";
				
				for(String columnName : columnNames){
					sqlQuery += buildComparison(columnName, operator, instruction.annotationValue) + " or ";	
				}
				
				sqlQuery = sqlQuery.substring(0, sqlQuery.length() - 3); // remove the last 'or '
				sqlQuery += ")";
				
				if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
					sqlQuery += " and " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+getEdgeTableName(instruction.subjectGraph)+")";
				}
				
				sqlQuery += " group by " + getIdColumnName() + ";";
				executeQueryForResult(sqlQuery, false);
			}
		}
	}

	@Override
	public void getEdgeEndpoint(GetEdgeEndpoint instruction){
		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		String subjectEdgeTable = getEdgeTableName(instruction.subjectGraph);

		String answerTable = "m_answer";

		createUUIDTable(answerTable, true);

		if(instruction.component == GetEdgeEndpoint.Component.kSource
				|| instruction.component == GetEdgeEndpoint.Component.kBoth){
			executeQueryForResult("insert into " + answerTable + " select \"" + getIdColumnNameChildVertex()
					+ "\" from " + getEdgeAnnotationTableName() + " where " + getIdColumnName() + " in (select "
					+ getIdColumnName() + " from " + subjectEdgeTable + ");", false);
		}
		if(instruction.component == GetEdgeEndpoint.Component.kDestination
				|| instruction.component == GetEdgeEndpoint.Component.kBoth){
			executeQueryForResult("insert into " + answerTable + " select \"" + getIdColumnNameParentVertex()
					+ "\" from " + getEdgeAnnotationTableName() + " where " + getIdColumnName() + " in (select "
					+ getIdColumnName() + " from " + subjectEdgeTable + ");", false);
		}
		executeQueryForResult("insert into " + targetVertexTable + " select " + getIdColumnName() + " from "
				+ answerTable + " group by "+getIdColumnName(), false);

		dropTable(answerTable);
	}

	@Override
	public void limitGraph(LimitGraph instruction){
		String sourceVertexTable = getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = getEdgeTableName(instruction.sourceGraph);
		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = getEdgeTableName(instruction.targetGraph);

		GraphStats graphStats = statGraph(new StatGraph(instruction.sourceGraph));

		if(graphStats.vertices > 0){
			executeQueryForResult("insert into " + targetVertexTable + " select " + getIdColumnName() + " from "
					+ sourceVertexTable + " group by " + getIdColumnName() + " order by " + getIdColumnName()
					+ " limit " + instruction.limit + ";", false);

		}
		if(graphStats.edges > 0){
			executeQueryForResult("insert into " + targetEdgeTable + " select " + getIdColumnName() + " from "
					+ sourceEdgeTable + " group by " + getIdColumnName() + " order by " + getIdColumnName() + " limit "
					+ instruction.limit + ";", false);
		}
	}

	@Override
	public void intersectGraph(IntersectGraph instruction){
		String outputVertexTable = getVertexTableName(instruction.outputGraph);
		String outputEdgeTable = getEdgeTableName(instruction.outputGraph);
		String lhsVertexTable = getVertexTableName(instruction.lhsGraph);
		String lhsEdgeTable = getEdgeTableName(instruction.lhsGraph);
		String rhsVertexTable = getVertexTableName(instruction.rhsGraph);
		String rhsEdgeTable = getEdgeTableName(instruction.rhsGraph);

		executeQueryForResult("insert into " + outputVertexTable + " select " + getIdColumnName() + " from "
				+ lhsVertexTable + " where " + getIdColumnName() + " in (select " + getIdColumnName() + " from "
				+ rhsVertexTable + ");", false);
		executeQueryForResult(
				"insert into " + outputEdgeTable + " select " + getIdColumnName() + " from " + lhsEdgeTable + " where "
				+ getIdColumnName() + " in (select " + getIdColumnName() + " from " + rhsEdgeTable + ");",
				false);
	}
	
	@Override
	public GraphStats statGraph(StatGraph instruction){
		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = getEdgeTableName(instruction.targetGraph);
		long numVertices = Long.parseLong(
				executeQueryForResult("select count(*) from " + targetVertexTable, false).get(0).get(0)
				);
		long numEdges = Long.parseLong(
				executeQueryForResult("select count(*) from " + targetEdgeTable, false).get(0).get(0)
				);
		return new GraphStats(numVertices, numEdges);
	}

	@Override
	public void subtractGraph(SubtractGraph instruction){
		String outputVertexTable = getVertexTableName(instruction.outputGraph);
		String outputEdgeTable = getEdgeTableName(instruction.outputGraph);
		String minuendVertexTable = getVertexTableName(instruction.minuendGraph);
		String minuendEdgeTable = getEdgeTableName(instruction.minuendGraph);
		String subtrahendVertexTable = getVertexTableName(instruction.subtrahendGraph);
		String subtrahendEdgeTable = getEdgeTableName(instruction.subtrahendGraph);

		if(instruction.component == null || instruction.component == Graph.Component.kVertex){
			executeQueryForResult("insert into " + outputVertexTable + " select " + getIdColumnName() + " from "
					+ minuendVertexTable + " where " + getIdColumnName() + " not in (select " + getIdColumnName()
					+ " from " + subtrahendVertexTable + ");", false);
		}
		if(instruction.component == null || instruction.component == Graph.Component.kEdge){
			executeQueryForResult("insert into " + outputEdgeTable + " select " + getIdColumnName() + " from "
					+ minuendEdgeTable + " where " + getIdColumnName() + " not in (select " + getIdColumnName()
					+ " from " + subtrahendEdgeTable + ");", false);
		}
	}

	@Override
	public void unionGraph(UnionGraph instruction){
		String sourceVertexTable = getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = getEdgeTableName(instruction.sourceGraph);
		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = getEdgeTableName(instruction.targetGraph);

		executeQueryForResult("insert into " + targetVertexTable + " select "
				+getIdColumnName()+" from " + sourceVertexTable + ";", false);
		executeQueryForResult("insert into " + targetEdgeTable + " select "
				+getIdColumnName()+" from " + sourceEdgeTable + ";", false);
	}

	@Override
	public void getAdjacentVertex(GetAdjacentVertex instruction){
		final List<Direction> directions = new ArrayList<Direction>();
		if(instruction.direction == Direction.kBoth){
			directions.add(Direction.kAncestor);
			directions.add(Direction.kDescendant);
		}else{
			directions.add(instruction.direction);
		}

		final String targetVertexTable = getVertexTableName(instruction.targetGraph);
		final String targetEdgeTable = getEdgeTableName(instruction.targetGraph);
		final String subjectEdgeTable = getEdgeTableName(instruction.subjectGraph);
		final String cursorTable = "m_cur";
		final String nextTable = "m_next";
		final String answerTable = "m_answer";
		final String answerEdgeTable = "m_answer_edge";
		
		final String filter = queryEnvironment.isBaseGraph(instruction.subjectGraph) ? ""
				: " and " + getEdgeAnnotationTableName() + "." + getIdColumnName() 
				+ " in (select "+getIdColumnName()+" from "+subjectEdgeTable+")";

		for(final Direction direction : directions){
			if(direction != Direction.kAncestor && direction != Direction.kDescendant){
				throw new RuntimeException("Unexpected direction: " + direction);
			}
			final String startVertexTable = getVertexTableName(instruction.sourceGraph);
			final String src = direction == Direction.kAncestor ? getIdColumnNameChildVertex() : getIdColumnNameParentVertex();
			final String dst = direction == Direction.kAncestor ? getIdColumnNameParentVertex() : getIdColumnNameChildVertex();
			
			createUUIDTable(cursorTable, true);
			createUUIDTable(nextTable, true);
			createUUIDTable(answerTable, true);
			createUUIDTable(answerEdgeTable, true);

			executeQueryForResult("insert into "+cursorTable+" select "+getIdColumnName()+" from " + startVertexTable + ";", false);
			executeQueryForResult("insert into " + answerTable + " select " + getIdColumnName() + " from " + cursorTable + ";", false);

			for(int i = 0; i < 1; ++i){
				createUUIDTable(nextTable, true);
				executeQueryForResult("insert into " + nextTable + " select \"" + dst + "\" from " + getEdgeAnnotationTableName()
					+ " where \"" + src + "\" in (select "+getIdColumnName()+" from "+cursorTable+")"
					+ " " + filter + " group by \"" + dst + "\";", false);
				executeQueryForResult("insert into " + answerEdgeTable + " select " + getIdColumnName() + " from " + getEdgeAnnotationTableName()
					+ " where \"" + src + "\" in (select "+getIdColumnName()+" from "+cursorTable+") " + filter + ";", false);
				createUUIDTable(cursorTable, true);
				executeQueryForResult("insert into " + cursorTable + " select " + getIdColumnName() + " from " + nextTable
						+ " where " + getIdColumnName() + " not in (select "+getIdColumnName()+" from "+answerTable+");", false);
				executeQueryForResult("insert into " + answerTable + " select " + getIdColumnName() + " from " + cursorTable + ";", 
						false);

				List<List<String>> countResult = executeQueryForResult("select count(*) from "+cursorTable+";", false);
				long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
				if(cursorTableCount == 0){
					break;
				}
			}

			executeQueryForResult("insert into " + targetVertexTable 
					+ " select " + getIdColumnName() + " from " + answerTable + ";", false);
			executeQueryForResult("insert into " + targetEdgeTable 
					+ " select " + getIdColumnName() + " from " + answerEdgeTable + " group by " + getIdColumnName() + ";", false);
		}

		dropTable(cursorTable);
		dropTable(nextTable);
		dropTable(answerEdgeTable);
		dropTable(answerTable);
	}

	@Override
	public Map<String, Map<String, String>> exportVertices(ExportGraph instruction){
		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		
		List<List<String>> verticesListOfList = executeQueryForResult("select * from " + getVertexAnnotationTableName() 
				+ " where " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+targetVertexTable+")", true);
		
		List<String> vertexHeader = verticesListOfList.remove(0); // remove the header
		
		Map<String, Map<String, String>> hashToVertexMap = new HashMap<String, Map<String, String>>();
		
		for(List<String> vertexList : verticesListOfList){
			String hash = null;
			Map<String, String> annotations = new HashMap<String, String>();
			for(int i = 0; i < vertexHeader.size(); i++){
				String annotationKey = vertexHeader.get(i);
				String annotationValue = vertexList.get(i);
				if(annotationKey.equals(getIdColumnName())){
					hash = annotationValue.replaceAll("\\-", "");
				}else{
					if(annotationValue != null){
						annotations.put(annotationKey, annotationValue);
					}
				}
			}
			hashToVertexMap.put(hash, annotations);
		}
		return hashToVertexMap;
	}
	
	@Override
	public Set<QueriedEdge> exportEdges(ExportGraph instruction){
		String targetEdgeTable = getEdgeTableName(instruction.targetGraph);
		
		List<List<String>> edgesListOfList = executeQueryForResult("select * from " + getEdgeAnnotationTableName() 
		+ " where " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+targetEdgeTable+")", true);

		List<String> edgeHeader = edgesListOfList.remove(0); // remove the header
		
		Set<QueriedEdge> edgeSet = new HashSet<QueriedEdge>();
		
		for(List<String> edgeList : edgesListOfList){
			String hash = null;
			String childHash = null;
			String parentHash = null;
			Map<String, String> annotations = new HashMap<String, String>();
			for(int i = 0; i < edgeHeader.size(); i++){
				String annotationKey = edgeHeader.get(i);
				String annotationValue = edgeList.get(i);
				if(annotationKey.equals(getIdColumnName())){
					hash = annotationValue.replaceAll("\\-", "");
				}else if(annotationKey.equals(getIdColumnNameChildVertex())){
					childHash = annotationValue.replaceAll("\\-", "");
				}else if(annotationKey.equals(getIdColumnNameParentVertex())){
					parentHash = annotationValue.replaceAll("\\-", "");
				}else{
					if(annotationValue != null){
						annotations.put(annotationKey, annotationValue);
					}
				}
			}
			edgeSet.add(new QueriedEdge(hash, childHash, parentHash, annotations));
		}
		
		return edgeSet;
	}

	@Override
	public void collapseEdge(CollapseEdge instruction){
		String sourceVertexTable = getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = getEdgeTableName(instruction.sourceGraph);
		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = getEdgeTableName(instruction.targetGraph);

		String groupByClause = "group by ";
		groupByClause += "\"" + getIdColumnNameChildVertex() + "\", ";
		groupByClause += "\"" + getIdColumnNameParentVertex() + "\", ";

		for(String annotationKey : instruction.getFields()){
			groupByClause += "\"" + annotationKey + "\", ";
		}

		groupByClause = groupByClause.substring(0, groupByClause.length() - 2); // remove the trailing ', '

		executeQueryForResult("insert into " + targetVertexTable + " select " + getIdColumnName() + " from "
				+ sourceVertexTable + ";", false);
		executeQueryForResult("insert into " + targetEdgeTable + " select min(e." + getIdColumnName() + "::text)::uuid from "
				+ getEdgeAnnotationTableName() + " e where e." + getIdColumnName() + " in (select " + getIdColumnName()
				+ " from " + sourceEdgeTable + ") " + groupByClause + ";", false);
	}

	@Override
	public void getSubgraph(GetSubgraph instruction){
		final String targetVertexTable = getVertexTableName(instruction.targetGraph);
		final String targetEdgeTable = getEdgeTableName(instruction.targetGraph);
		final String subjectVertexTable = getVertexTableName(instruction.subjectGraph);
		final String subjectEdgeTable = getEdgeTableName(instruction.subjectGraph);
		final String skeletonVertexTable = getVertexTableName(instruction.skeletonGraph);
		final String skeletonEdgeTable = getEdgeTableName(instruction.skeletonGraph);

		final String answerTable = "m_answer";
		
		createUUIDTable(answerTable, true);

		// Get vertices.
		executeQueryForResult("insert into "+answerTable+" select "+getIdColumnName()+" from " + skeletonVertexTable 
				+ " where "+getIdColumnName()+" in (select "+getIdColumnName()+" from " + subjectVertexTable + ");", false);
		executeQueryForResult("insert into "+answerTable+" select \""+getIdColumnNameChildVertex()+"\" from "+getEdgeAnnotationTableName()
				+ " where "+getIdColumnName()+" in (select "+getIdColumnName()+" from " + skeletonEdgeTable + ")"
				+ " and \""+getIdColumnNameChildVertex()+"\" in (select "+getIdColumnName()+" from " + subjectVertexTable + ");", false);
		executeQueryForResult("insert into "+answerTable+" select \""+getIdColumnNameParentVertex()+"\" from "+getEdgeAnnotationTableName()
				+ " where "+getIdColumnName()+" in (select "+getIdColumnName()+" from " + skeletonEdgeTable + ")"
				+ " and \""+getIdColumnNameParentVertex()+"\" in (select "+getIdColumnName()+" from " + subjectVertexTable + ");", false);
		executeQueryForResult("insert into " + targetVertexTable + " select "+getIdColumnName()+" from "+answerTable+" group by "+getIdColumnName()+";", false);
		
		// Get edges.
		executeQueryForResult("insert into " + targetEdgeTable + " select s."+getIdColumnName()
				+ " from " + subjectEdgeTable + " s, "+getEdgeAnnotationTableName()+" e" 
				+ " where s."+getIdColumnName()+" = e."+getIdColumnName()
				+ " and e.\""+getIdColumnNameChildVertex()+"\" in (select "+getIdColumnName()+" from "+answerTable+")"
				+ " and e.\""+getIdColumnNameParentVertex()+"\" in (select "+getIdColumnName()+" from "+answerTable+")"
				+ " group by s."+getIdColumnName()+";", false);

		dropTable(answerTable);
	}
	
	private void noResultExecuteQuery(String query){
		executeQueryForResult(query, false);
	}
	
	@Override
	public void getShortestPath(GetShortestPath instruction){
		String filter;
		dropTable("m_conn");
		noResultExecuteQuery("create table m_conn ("+getIdColumnNameChildVertex()+" uuid, "+getIdColumnNameParentVertex()+" uuid)");
		if(queryEnvironment.isBaseGraph(instruction.subjectGraph)){
			filter = "";
			noResultExecuteQuery("insert into m_conn select \"" + getIdColumnNameChildVertex() + "\", \"" + getIdColumnNameParentVertex() + "\" "
					+ "from " + getEdgeAnnotationTableName() + " group by \"" + getIdColumnNameChildVertex() + "\", \"" + getIdColumnNameParentVertex() + "\"");
		}else{
			String subjectEdgeTable = getEdgeTableName(instruction.subjectGraph);
			filter = " and "+getEdgeAnnotationTableName()+".\""+getIdColumnName()+"\" in (select "+getIdColumnName()+" from " + subjectEdgeTable + ")";
			dropTable("m_sgedge");
			noResultExecuteQuery("create table m_sgedge ("+getIdColumnNameChildVertex()+" uuid, "+getIdColumnNameParentVertex()+" uuid)");
			noResultExecuteQuery("insert into m_sgedge select \"" + getIdColumnNameChildVertex() + "\", \"" + getIdColumnNameParentVertex() + "\" "
					+ "from " + getEdgeAnnotationTableName() + " where " + getIdColumnName() + " in (select "+getIdColumnName()+" from "
					+ subjectEdgeTable + ")");
			noResultExecuteQuery("insert into m_conn select "+getIdColumnNameChildVertex()+", "+getIdColumnNameParentVertex()+" from "
					+ "m_sgedge group by " + getIdColumnNameChildVertex() + ", " + getIdColumnNameParentVertex());
			dropTable("m_sgedge");
		}
		// Create subgraph edges table.
		dropTable("m_sgconn");
		noResultExecuteQuery("create table m_sgconn ("+getIdColumnNameChildVertex()+" uuid, "+getIdColumnNameParentVertex()+" uuid,"
				+ "reaching uuid, depth int)");
		
		dropTable("m_cur"); dropTable("m_next"); dropTable("m_answer");
		noResultExecuteQuery("create table m_cur ("+getIdColumnName()+" uuid, reaching uuid)");
		noResultExecuteQuery("create table m_next ("+getIdColumnName()+" uuid, reaching uuid)");
		noResultExecuteQuery("create table m_answer ("+getIdColumnName()+" uuid)");

		noResultExecuteQuery("insert into m_cur select "+getIdColumnName()+", " + getIdColumnName() + " from " 
				+ getVertexTableName(instruction.dstGraph));
		noResultExecuteQuery("insert into m_answer select " + getIdColumnName() + " from m_cur group by " + getIdColumnName());
		
		for(int i = 0; i < instruction.maxDepth; ++i){
			noResultExecuteQuery(
					"insert into m_sgconn select " + getIdColumnNameChildVertex() + ", " + getIdColumnNameParentVertex() + ", reaching, "+String.valueOf(i + 1)+" "
					+ "from m_cur, m_conn where " + getIdColumnName() + " = " + getIdColumnNameParentVertex());
			dropTable("m_next");
			noResultExecuteQuery("create table m_next ("+getIdColumnName()+" uuid, reaching uuid)");
			noResultExecuteQuery("insert into m_next select " + getIdColumnNameChildVertex() + ", reaching from "
				+ "m_cur, m_conn where "+getIdColumnName()+" = " + getIdColumnNameParentVertex());
			dropTable("m_cur");
			noResultExecuteQuery("create table m_cur ("+getIdColumnName()+" uuid, reaching uuid)");
			noResultExecuteQuery("insert into m_cur select " + getIdColumnName() + ", reaching from m_next where "
				+ getIdColumnName() + " not in (select "+getIdColumnName()+" from m_answer) group by " + getIdColumnName() + ", reaching");
			noResultExecuteQuery("insert into m_answer select " + getIdColumnName() + " from m_cur group by " + getIdColumnName());

			List<List<String>> countResult = executeQueryForResult("select count(*) from m_cur", false);
			long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
			if(cursorTableCount == 0){
				break;
			}
		}

		dropTable("m_cur"); dropTable("m_next");
		noResultExecuteQuery("create table m_cur ("+getIdColumnName()+" uuid)");
		noResultExecuteQuery("create table m_next ("+getIdColumnName()+" uuid)");

		noResultExecuteQuery("insert into m_cur select " + getIdColumnName() + " from " + getVertexTableName(instruction.srcGraph)
				+ " where " + getIdColumnName() + " in (select "+getIdColumnName()+" from m_answer)");

		dropTable("m_answer");
		noResultExecuteQuery("create table m_answer ("+getIdColumnName()+" uuid)");
		noResultExecuteQuery("insert into m_answer select " + getIdColumnName() + " from m_cur");

		for(int i = 0; i < instruction.maxDepth; ++i){
			dropTable("m_next");
			noResultExecuteQuery("create table m_next ("+getIdColumnName()+" uuid)");
			noResultExecuteQuery("insert into m_next select min(" + getIdColumnNameParentVertex() + "::text)::uuid" // TODO as?
					+ " from m_cur, m_sgconn where " + getIdColumnName() + " = " + getIdColumnNameChildVertex() + " and depth + " + String.valueOf(i) 
					+ " <= " + String.valueOf(instruction.maxDepth) + " group by " + getIdColumnNameChildVertex() + ", reaching");
			dropTable("m_cur");
			noResultExecuteQuery("create table m_cur ("+getIdColumnName()+" uuid)");
			noResultExecuteQuery("insert into m_cur select "+getIdColumnName()+" from m_next where "
					+getIdColumnName()+" not in (select "+getIdColumnName()+" from m_answer)");
			noResultExecuteQuery("insert into m_answer select "+getIdColumnName()+" from m_cur");
			
			List<List<String>> countResult = executeQueryForResult("select count(*) from m_cur", false);
			long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
			if(cursorTableCount == 0){
				break;
			}
		}

		String targetVertexTable = getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = getEdgeTableName(instruction.targetGraph);

		noResultExecuteQuery("insert into " + targetVertexTable + " select " + getIdColumnName() + " from m_answer");
		noResultExecuteQuery("insert into " + targetEdgeTable + " select \"" + getIdColumnName() + "\" from " + getEdgeAnnotationTableName()
			+ " where \"" + getIdColumnNameChildVertex() + "\" in (select "+getIdColumnName()+" from m_answer)"
			+ " and \"" + getIdColumnNameParentVertex() + "\" in (select "+getIdColumnName()+" from m_answer) " + filter);
		dropTable("m_cur");dropTable("m_next");dropTable("m_answer");dropTable("m_conn");dropTable("m_sgconn");
	}
	
	@Override
	public void getLineage(GetLineage instruction){
		final List<Direction> directions = new ArrayList<Direction>();
		if(instruction.direction == Direction.kBoth){
			directions.add(Direction.kAncestor);
			directions.add(Direction.kDescendant);
		}else{
			directions.add(instruction.direction);
		}

		final String targetVertexTable = getVertexTableName(instruction.targetGraph);
		final String targetEdgeTable = getEdgeTableName(instruction.targetGraph);
		final String subjectEdgeTable = getEdgeTableName(instruction.subjectGraph);
		final String currentTable = "m_cur";
		final String nextTable = "m_next";
		final String answerTable = "m_answer";
		final String answerEdgeTable = "m_answer_edge";
		
		final String filter = queryEnvironment.isBaseGraph(instruction.subjectGraph) ? ""
				: " and " + getEdgeAnnotationTableName() + "." + getIdColumnName() 
				+ " in (select "+getIdColumnName()+" from "+subjectEdgeTable+")";

		for(final Direction direction : directions){
			if(direction != Direction.kAncestor && direction != Direction.kDescendant){
				throw new RuntimeException("Unexpected direction: " + direction);
			}
			final String startVertexTable = getVertexTableName(instruction.startGraph);
			final String src = direction == Direction.kAncestor ? getIdColumnNameChildVertex() : getIdColumnNameParentVertex();
			final String dst = direction == Direction.kAncestor ? getIdColumnNameParentVertex() : getIdColumnNameChildVertex();
			
			createUUIDTable(currentTable, true);
			createUUIDTable(nextTable, true);
			createUUIDTable(answerTable, true);
			createUUIDTable(answerEdgeTable, true);

			executeQueryForResult("insert into "+currentTable+" select "+getIdColumnName()+" from " + startVertexTable + ";", false);
			executeQueryForResult("insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable + ";", false);

			for(int i = 0; i < instruction.depth; ++i){
				createUUIDTable(nextTable, true);
				executeQueryForResult("insert into " + nextTable + " select \"" + dst + "\" from " + getEdgeAnnotationTableName()
					+ " where \"" + src + "\" in (select "+getIdColumnName()+" from "+currentTable+")"
					+ " " + filter + " group by \"" + dst + "\";", false);
				executeQueryForResult("insert into " + answerEdgeTable + " select " + getIdColumnName() + " from " + getEdgeAnnotationTableName()
					+ " where \"" + src + "\" in (select "+getIdColumnName()+" from "+currentTable+") " + filter + ";", false);
				createUUIDTable(currentTable, true);
				executeQueryForResult("insert into " + currentTable + " select " + getIdColumnName() + " from " + nextTable
						+ " where " + getIdColumnName() + " not in (select "+getIdColumnName()+" from "+answerTable+");", false);
				executeQueryForResult("insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable + ";", 
						false);

				List<List<String>> countResult = executeQueryForResult("select count(*) from "+currentTable+";", false);
				long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
				if(cursorTableCount == 0){
					break;
				}
			}

			executeQueryForResult("insert into " + targetVertexTable 
					+ " select " + getIdColumnName() + " from " + answerTable + ";", false);
			executeQueryForResult("insert into " + targetEdgeTable 
					+ " select " + getIdColumnName() + " from " + answerEdgeTable + " group by " + getIdColumnName() + ";", false);
		}

		dropTable(currentTable);
		dropTable(nextTable);
		dropTable(answerEdgeTable);
		dropTable(answerTable);
	}
	
	@Override
	public void getPath(GetSimplePath instruction){
		final int maxDepth = instruction.maxDepth;
		
		final String depthColumnName = "depth";
		final String currentTable = "m_cur";
		final String nextTable = "m_next";
		final String answerTable = "m_answer";
		final String currentSubgraphTable = "m_sgconn";
		
		final String targetVertexTable = getVertexTableName(instruction.targetGraph);
		final String targetEdgeTable = getEdgeTableName(instruction.targetGraph);
		final String subjectEdgeTable = getEdgeTableName(instruction.subjectGraph);
		final String dstVertexTable = getVertexTableName(instruction.dstGraph);
		final String srcVertexTable = getVertexTableName(instruction.srcGraph);
		
		createUUIDTable(currentTable, true);
		createUUIDTable(nextTable, true);
		createUUIDTable(answerTable, true);
		
		dropTable(currentSubgraphTable);
		executeQueryForResult("create table " + currentSubgraphTable + "("
				+ "\""+getIdColumnNameChildVertex()+"\" uuid, "
				+ "\""+getIdColumnNameParentVertex()+"\" uuid, "
				+ depthColumnName + " int);", false);

		executeQueryForResult("insert into "+currentTable+" select "+getIdColumnName()+" from " + dstVertexTable, false);
		executeQueryForResult("insert into "+answerTable+" select "+getIdColumnName()+" from " + currentTable, false);
		
		final String filter = queryEnvironment.isBaseGraph(instruction.subjectGraph) 
				? "" : " and "+getEdgeAnnotationTableName()+"."+getIdColumnName()+" in (select "+getIdColumnName()+" from " + subjectEdgeTable + ")";
		
		final String q0 = "insert into " + currentSubgraphTable + " select \"" + getIdColumnNameChildVertex() + "\", \""
				+ getIdColumnNameParentVertex() + "\", %s from " + getEdgeAnnotationTableName() + " where \""
				+ getIdColumnNameParentVertex() + "\" in (select " + getIdColumnName() + " from " + currentTable + ")"
				+ " " + filter + ";";
		final String q1 = ""; // createUUIDTable(nextTable, true);
		final String q2 = "insert into " + nextTable + " select \"" + getIdColumnNameChildVertex() + "\" from "
				+ getEdgeAnnotationTableName() + " where \"" + getIdColumnNameParentVertex() + "\" in (select "
				+ getIdColumnName() + " from " + currentTable + ")" + " " + filter + " group by \""
				+ getIdColumnNameChildVertex() + "\";";
		final String q3 = ""; // createUUIDTable(cursorTable, true);
		final String q4 = "insert into " + currentTable + " select " + getIdColumnName() + " from " + nextTable
				+ " where " + getIdColumnName() + " not in (select " + getIdColumnName() + " from " + answerTable
				+ ");";
		final String q5 = "insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable + ";";
		
		for(int i = 0; i < maxDepth; ++i){
			final String formattedQ0 = String.format(q0, i+1);
			
			executeQueryForResult(formattedQ0, false);
			createUUIDTable(nextTable, true);
			executeQueryForResult(q2, false);
			createUUIDTable(currentTable, true);
			executeQueryForResult(q4, false);
			executeQueryForResult(q5, false);

			List<List<String>> countResult = executeQueryForResult("select count(*) from "+currentTable+";", false);
			long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
			if(cursorTableCount == 0){
				break;
			}
		}

		createUUIDTable(currentTable, true);
		createUUIDTable(nextTable, true);
		
		executeQueryForResult("insert into " + currentTable + " select " + getIdColumnName() + " from " + srcVertexTable
				+ " where " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+answerTable+");", false);

		createUUIDTable(answerTable, true);
		
		executeQueryForResult("insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable, false);

		final String qq0 = ""; // createUUIDTable(nextTable, true);
		final String qq1 = "insert into " + nextTable + " select \"" + getIdColumnNameParentVertex() + "\" from " + currentSubgraphTable
				+ " where \"" + getIdColumnNameChildVertex() + "\" in (select "+getIdColumnName()+" from "+currentTable+")"
				+ " and " + depthColumnName + " + %s <= " + instruction.maxDepth + " group by \""+getIdColumnNameParentVertex()+"\";";
		final String qq2 = "insert into " + targetEdgeTable + " select " + getIdColumnName() + " from " + getEdgeAnnotationTableName()
				+ " where \""+getIdColumnNameChildVertex()+"\" in (select " + getIdColumnName() + " from " + currentTable + ")"
				+ " and \""+getIdColumnNameParentVertex()+"\" in (select "+getIdColumnName()+" from "+nextTable+") " + filter + ";";
		final String qq3 = ""; // createUUIDTable(cursorTable, true);
		final String qq4 = "insert into " + currentTable + " select " + getIdColumnName() + " from " + nextTable 
				+ " where " + getIdColumnName() + " not in (select "+getIdColumnName()+" from "+answerTable+");";
		final String qq5 = "insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable + ";";

		for(int i = 0; i < maxDepth; ++i){
			createUUIDTable(nextTable, true);
			final String formattedQq1 = String.format(qq1, i);
			executeQueryForResult(formattedQq1, false);
			executeQueryForResult(qq2, false);
			createUUIDTable(currentTable, true);
			executeQueryForResult(qq4, false);
			executeQueryForResult(qq5, false);
			
			List<List<String>> countResult = executeQueryForResult("select count(*) from "+currentTable+";", false);
			long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
			if(cursorTableCount == 0){
				break;
			}
		}

		executeQueryForResult("insert into " + targetVertexTable + " select " + getIdColumnName() + " from " + answerTable, false);
		dropTable(currentSubgraphTable);
		dropTable(currentTable);
		dropTable(nextTable);
		dropTable(answerTable);
	}
	
	@Override
	public void getLink(GetLink instruction){
		if(instruction.maxDepth <= 0){
			return;
		}
		
		final int maxDepth = instruction.maxDepth - 1;
		
		final String depthColumnName = "depth";
		final String currentTable = "m_cur";
		final String nextTable = "m_next";
		final String answerTable = "m_answer";
		final String currentSubgraphTable = "m_sgconn";
		
		final String dstVertexTable = getVertexTableName(instruction.dstGraph);
		final String srcVertexTable = getVertexTableName(instruction.srcGraph);
		final String subjectEdgeTable = getEdgeTableName(instruction.subjectGraph);
		final String targetVertexTable = getVertexTableName(instruction.targetGraph);
		final String targetEdgeTable = getEdgeTableName(instruction.targetGraph);
		
		createUUIDTable(currentTable, true);
		createUUIDTable(nextTable, true);
		createUUIDTable(answerTable, true);
		
		dropTable(currentSubgraphTable);
		executeQueryForResult("create table " + currentSubgraphTable + "("
				+ "\""+getIdColumnNameChildVertex()+"\" uuid, "
				+ "\""+getIdColumnNameParentVertex()+"\" uuid, "
				+ depthColumnName + " int);", false);
		
		executeQueryForResult("insert into "+currentTable+" select "+getIdColumnName()+" from " + dstVertexTable, false);
		executeQueryForResult("insert into "+answerTable+" select "+getIdColumnName()+" from " + currentTable, false);
		
		
		final String filter = queryEnvironment.isBaseGraph(instruction.subjectGraph) 
				? "" : " and "+getEdgeAnnotationTableName()+"."+getIdColumnName()+" in (select "+getIdColumnName()+" from " + subjectEdgeTable + ")";
		final String q0 = "insert into "+currentSubgraphTable+" select \""+getIdColumnNameChildVertex()+"\", \""+getIdColumnNameParentVertex()+"\", %s from " + getEdgeAnnotationTableName()
				+ " where \""+getIdColumnNameParentVertex()+"\" in (select "+getIdColumnName()+" from " + currentTable + ")"
				+ " " + filter + ";";
		final String q1 = "insert into " + currentSubgraphTable + " select \"" + getIdColumnNameChildVertex() + "\", \"" +getIdColumnNameParentVertex() + "\", %s from " + getEdgeAnnotationTableName()
				+ " where \"" + getIdColumnNameChildVertex() + "\" in (select "+getIdColumnName()+" from "+currentTable+")"
				+ " " + filter + ";";
		final String q2 = ""; // createUUIDTable(nextTable, true);
		final String q3 = "insert into " + nextTable + " select \"" + getIdColumnNameChildVertex() + "\" from " + getEdgeAnnotationTableName()
				+ " where \"" + getIdColumnNameParentVertex() + "\" in (select "+getIdColumnName()+" from "+currentTable+")"
				+ " " + filter + " group by \""+getIdColumnNameChildVertex()+"\";";
		final String q4 = "insert into " +nextTable + " select \"" + getIdColumnNameParentVertex() + "\" from " + getEdgeAnnotationTableName()
				+ " where \""+getIdColumnNameChildVertex()+"\" in (select "+getIdColumnName()+" from "+currentTable+")"
				+ " " + filter + " group by \""+getIdColumnNameParentVertex()+"\";";
		final String q5 = ""; // createUUIDTable(cursorTable, true);
		final String q6 = "insert into " + currentTable + " select " + getIdColumnName() + " from " +nextTable
				+ " where " + getIdColumnName() + " not in (select "+getIdColumnName()+" from "+answerTable+");";
		final String q7 = "insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable + ";";
		
		for(int i = 0; i < maxDepth; ++i){
			final String formattedQ0 = String.format(q0, i+1);
			final String formattedQ1 = String.format(q1, i+1);
			
			executeQueryForResult(formattedQ0, false);
			executeQueryForResult(formattedQ1, false);
			createUUIDTable(nextTable, true);
			executeQueryForResult(q3, false);
			executeQueryForResult(q4, false);
			createUUIDTable(currentTable, true);
			executeQueryForResult(q6, false);
			executeQueryForResult(q7, false);

			List<List<String>> countResult = executeQueryForResult("select count(*) from "+currentTable+";", false);
			long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
			if(cursorTableCount == 0){
				break;
			}
		}
		
		createUUIDTable(currentTable, true);
		createUUIDTable(nextTable, true);
		
		executeQueryForResult("insert into " + currentTable + " select " + getIdColumnName() + " from " + srcVertexTable
				+ " where " + getIdColumnName() + " in (select "+getIdColumnName()+" from "+answerTable+");", false);

		createUUIDTable(answerTable, true);
		
		executeQueryForResult("insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable, false);

		final String qq0 = ""; // createUUIDTable(nextTable, true);
		final String qq1 = "insert into " + nextTable + " select \"" + getIdColumnNameParentVertex() + "\" from " + currentSubgraphTable
				+ " where \"" + getIdColumnNameChildVertex() + "\" in (select "+getIdColumnName()+" from "+currentTable+")"
				+ " and " + depthColumnName + " + %s <= " + instruction.maxDepth + " group by \""+getIdColumnNameParentVertex()+"\";";
		final String qq2 = "insert into " + nextTable + " select \"" + getIdColumnNameChildVertex() + "\" from " + currentSubgraphTable
				+ " where \"" + getIdColumnNameParentVertex() + "\" in (select "+getIdColumnName()+" from "+currentTable+")"
				+ " and " + depthColumnName + " + %s <= " + instruction.maxDepth + " group by \""+getIdColumnNameChildVertex()+"\";";
		final String qq3 = ""; // createUUIDTable(cursorTable, true);
		final String qq4 = "insert into " + currentTable + " select " + getIdColumnName() + " from " + nextTable 
				+ " where " + getIdColumnName() + " not in (select "+getIdColumnName()+" from "+answerTable+");";
		final String qq5 = "insert into " + answerTable + " select " + getIdColumnName() + " from " + currentTable + ";";
		
		for(int i = 0; i < maxDepth; ++i){
			createUUIDTable(nextTable, true);
			final String formattedQq1 = String.format(qq1, i);
			final String formattedQq2 = String.format(qq2, i);
			executeQueryForResult(formattedQq1, false);
			executeQueryForResult(formattedQq2, false);
			createUUIDTable(currentTable, true);
			executeQueryForResult(qq4, false);
			executeQueryForResult(qq5, false);
			
			List<List<String>> countResult = executeQueryForResult("select count(*) from "+currentTable+";", false);
			long cursorTableCount = Long.parseLong(countResult.get(0).get(0));
			if(cursorTableCount == 0){
				break;
			}
		}
		
		executeQueryForResult("insert into " + targetVertexTable + " select "+getIdColumnName()+" from "+answerTable+";", false);
		executeQueryForResult("insert into " + targetEdgeTable + " select " +getIdColumnName() + " from " + getEdgeAnnotationTableName()
				+ " where \""+getIdColumnNameChildVertex()+"\" in (select "+getIdColumnName()+" from "+answerTable+")"
				+ " and \""+getIdColumnNameParentVertex()+"\" in (select "+getIdColumnName()+" from "+answerTable+")"
				+ " " + filter + ";", false);

		dropTable(currentSubgraphTable);
		dropTable(currentTable);
		dropTable(nextTable);
		dropTable(answerTable);
	}
	
	@Override
	public void createEmptyGraphMetadata(CreateEmptyGraphMetadata instruction){
		String vertexTable = getMetadataVertexTableName(instruction.metadata);
		String edgeTable = getMetadataEdgeTableName(instruction.metadata);

		StringBuilder sb = new StringBuilder();
		sb.append("DROP TABLE " + vertexTable + ";\n");
		sb.append("DROP TABLE " + edgeTable + ";\n");
		sb.append("CREATE TABLE " + vertexTable + " (id INT, name VARCHAR(64), value VARCHAR(256));");
		sb.append("CREATE TABLE " + edgeTable + " (id LONG, name VARCHAR(64), value VARCHAR(256));");
		executeQueryForResult(sb.toString(), false);
	}

	@Override
	public void overwriteGraphMetadata(OverwriteGraphMetadata instruction){
		String targetVertexTable = getMetadataVertexTableName(instruction.targetMetadata);
		String targetEdgeTable = getMetadataEdgeTableName(instruction.targetMetadata);
		String lhsVertexTable = getMetadataVertexTableName(instruction.lhsMetadata);
		String lhsEdgeTable = getMetadataEdgeTableName(instruction.lhsMetadata);
		String rhsVertexTable = getMetadataVertexTableName(instruction.rhsMetadata);
		String rhsEdgeTable = getMetadataEdgeTableName(instruction.rhsMetadata);

		executeQueryForResult("INSERT INTO " + targetVertexTable + " SELECT id, name, value FROM " + lhsVertexTable
				+ " l" + " WHERE NOT EXISTS (SELECT * FROM " + rhsVertexTable + " r"
				+ " WHERE l.id = r.id AND l.name = r.name);\n" + "INSERT INTO " + targetEdgeTable
				+ " SELECT id, name, value FROM " + lhsEdgeTable + " l" + " WHERE NOT EXISTS (SELECT * FROM "
				+ rhsEdgeTable + " r" + " WHERE l.id = r.id AND l.name = r.name);\n" + "INSERT INTO "
				+ targetVertexTable + " SELECT id, name, value FROM " + rhsVertexTable + ";\n" + "INSERT INTO "
				+ targetEdgeTable + " SELECT id, name, value FROM " + rhsEdgeTable + ";", false);
	}

	@Override
	public void setGraphMetadata(SetGraphMetadata instruction){
		String targetVertexTable = getMetadataVertexTableName(instruction.targetMetadata);
		String targetEdgeTable = getMetadataEdgeTableName(instruction.targetMetadata);
		String sourceVertexTable = getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = getEdgeTableName(instruction.sourceGraph);

		if(instruction.component == Component.kVertex || instruction.component == Component.kBoth){
//			executeQueryForResult("INSERT INTO " + targetVertexTable + " SELECT id, " + FormatStringLiteral(instruction.name)
//					+ ", " + FormatStringLiteral(instruction.value) + " FROM " + sourceVertexTable + " GROUP BY id;");
		}

		if(instruction.component == Component.kEdge || instruction.component == Component.kBoth){
//			executeQueryForResult("INSERT INTO " + targetEdgeTable + " SELECT id, " + FormatStringLiteral(instruction.name)
//					+ ", " + FormatStringLiteral(instruction.value) + " FROM " + sourceEdgeTable + " GROUP BY id;");
		}
	}
}
