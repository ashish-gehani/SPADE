/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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
package spade.storage;

import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.storage.postgresql.Configuration;
import spade.storage.postgresql.PostgreSQLInstructionExecutor;
import spade.storage.postgresql.PostgreSQLQueryEnvironment;
import spade.utility.GraphBuffer;
import spade.utility.GraphBuffer.GraphSnapshot;

/**
 * Basic PostgreSQL storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public class PostgreSQL extends SQL{

	private PostgreSQLInstructionExecutor queryInstructionExecutor = null;
	private PostgreSQLQueryEnvironment queryEnvironment = null;

	private final String baseGraphName = "spade_base_graph";
	private final String tableNameBaseVertex = PostgreSQLQueryEnvironment.getVertexTableName(baseGraphName);
	private final String tableNameBaseEdge = PostgreSQLQueryEnvironment.getEdgeTableName(baseGraphName);

	/*
	 * Using LinkedHashSet to keep unique elements in their insertion order
	 */
	private final Set<String> vertexColumnNames = new LinkedHashSet<>();
	private final Set<String> edgeColumnNames = new LinkedHashSet<>();

	private final Configuration configuration = new Configuration();
	private final GraphBuffer graphBuffer = new GraphBuffer();

	private Connection connection = null;

	@Override
	public boolean initialize(String arguments){
		try{
			final String configPath = Settings.getDefaultConfigFilePath(this.getClass());
			this.configuration.load(configPath);

			graphBuffer.setMaxSize(this.configuration.getBufferSize());

			final String connectionURL = configuration.getConnectionURL();
			final Connection connection = DriverManager.getConnection(
					connectionURL, configuration.getDbUser(), configuration.getDbPassword());
			setConnection(connection);

			if(configuration.isReset()){
				resetDatabase(connection);
			}

			setupDatabase(connection, configuration.isSecondaryIndexes());

			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize PostgreSQL storage", e);
			return false;
		}
	}

	@Override
	public boolean shutdown(){
		flush();
		try{
			closeConnection();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close connection", e);
		}
		return true;
	}

	private void setConnection(final Connection connection){
		this.connection = connection;
	}

	private CopyManager createCopyManager() throws Exception{
		return new CopyManager((BaseConnection)connection);
	}

	private Statement createStatement() throws Exception{
		return connection.createStatement();
	}

	private void closeConnection() throws Exception{
		this.connection.close();
	}

	private final String getBaseGraphName(){
		return baseGraphName;
	}

	private final String getVertexTableName(){
		return VERTEX_TABLE;
	}

	private final String getEdgeTableName(){
		return EDGE_TABLE;
	}

	private final String getBaseVertexTableName(){
		return tableNameBaseVertex;
	}

	private final String getBaseEdgeTableName(){
		return tableNameBaseEdge;
	}

	private final String getPrimaryKeyName(){
		return PRIMARY_KEY;
	}

	private final String getChildVertexKeyName(){
		return CHILD_VERTEX_KEY;
	}

	private final String getParentVertexKeyName(){
		return PARENT_VERTEX_KEY;
	}

	private Set<String> getVertexColumnNames(){
		return vertexColumnNames;
	}

	private Set<String> getEdgeColumnNames(){
		return edgeColumnNames;
	}

	private void dropTable(final Statement statement, final String tableName) throws Exception{
		statement.execute("drop table if exists " + tableName);
	}

	private void resetDatabase(final Connection connection) throws Exception{
		try(final Statement statement = connection.createStatement()){
			for(final String tableName : new String[]{
					getVertexTableName()
					, getEdgeTableName()
					, getBaseVertexTableName()
					, getBaseEdgeTableName()
					}){
				dropTable(statement, tableName);
			}
		}catch(Exception e){
			throw new Exception("Failed to reset database", e);
		}
	}

	private String formatColumnName(final String columnName){
		return '"' + columnName + '"';
	}

	private String getQueryCreateVertexTable(){
		final String query = 
				"create table if not exists " + getVertexTableName()
				+ "(" 
				+ formatColumnName(getPrimaryKeyName()) + " UUID"
				+ ", " + formatColumnName("type") + " VARCHAR(32) not null"
				+ ")";
		return query;
	}

	private String getQueryCreateEdgeTable(){
		final String query = 
				"create table if not exists " + getEdgeTableName()
				+ "(" 
				+ formatColumnName(getPrimaryKeyName()) + " UUID"
				+ ", " + formatColumnName("type") + " VARCHAR(32) not null"
				+ ", " + formatColumnName(getChildVertexKeyName()) + " UUID not null"
				+ ", " + formatColumnName(getParentVertexKeyName()) + " UUID not null"
				+ ")";
		return query;
	}

	private String getQueryCreateVertexBaseTable(){
		final String query = 
				"create table if not exists " + getBaseVertexTableName()
				+ "(" 
				+ formatColumnName(getPrimaryKeyName()) + " UUID"
				+ ")";
		return query;
	}

	private String getQueryCreateEdgeBaseTable(){
		final String query = 
				"create table if not exists " + getBaseEdgeTableName()
				+ "(" 
				+ formatColumnName(getPrimaryKeyName()) + " UUID"
				+ ")";
		return query;
	}

	private List<String> getColumnNamesInTable(final Statement statement, final String tableName) throws Exception{
		try{
			final List<String> columnNames = new ArrayList<>();
			final String query = "select * from " + tableName + " where false;";
			final ResultSet result = statement.executeQuery(query);
			final ResultSetMetaData metadata = result.getMetaData();
			final int columnCount = metadata.getColumnCount();
			for(int i = 1; i <= columnCount; i++){
				final String columnName = metadata.getColumnLabel(i);
				columnNames.add(columnName);
			}
			return columnNames;
		}catch(Exception e){
			throw new Exception("Failed to get column names for table '" + tableName + "'", e);
		}
	}

	private void addToVertexColumn(final String columnName){
		getVertexColumnNames().add(columnName);
	}

	private void addToVertexColumn(final Set<String> columnNames){
		for(final String columnName : columnNames){
			addToVertexColumn(columnName);
		}
	}

	private void addToEdgeColumn(final String columnName){
		getEdgeColumnNames().add(columnName);
	}
	
	private void addToEdgeColumn(final Set<String> columnNames){
		for(final String columnName : columnNames){
			addToEdgeColumn(columnName);
		}
	}

	private void populateVertexColumnNames(final Statement statement) throws Exception{
		try{
			final List<String> columnNames = getColumnNamesInTable(statement, getVertexTableName());
			for(final String columnName : columnNames){
				addToVertexColumn(columnName);
			}
		}catch(Exception e){
			throw e;
		}
	}

	private void populateEdgeColumnNames(final Statement statement) throws Exception{
		try{
			final List<String> columnNames = getColumnNamesInTable(statement, getEdgeTableName());
			for(final String columnName : columnNames){
				addToEdgeColumn(columnName);
			}
		}catch(Exception e){
			throw e;
		}
	}

	private void createSecondaryIndexes(final Statement statement) throws Exception{
		try{
			final String createVertexHashIndex = 
					"create index if not exists " + getPrimaryKeyName() + "_index on "
					+ getVertexTableName() + " using hash(" + formatColumnName(getPrimaryKeyName()) + ")";
			final String createEdgeParentHashIndex = 
					"create index if not exists " + getParentVertexKeyName() + "_index on "
					+ getEdgeTableName() + " using hash(" + formatColumnName(getParentVertexKeyName()) + ")";
			final String createEdgeChildHashIndex = 
					"create index if not exists " + getChildVertexKeyName() + "_index on "
					+ getEdgeTableName() + " using hash(" + formatColumnName(getChildVertexKeyName()) + ")";

			statement.execute(createVertexHashIndex);
			statement.execute(createEdgeParentHashIndex);
			statement.execute(createEdgeChildHashIndex);
		}catch(Exception e){
			throw new Exception("Failed to build secondary indexes", e);
		}
	}

	private void setupDatabase(final Connection connection, final boolean secondaryIndexes) throws Exception{
		try(final Statement statement = connection.createStatement()){
			statement.execute(getQueryCreateVertexTable());
			populateVertexColumnNames(statement);
			statement.execute(getQueryCreateEdgeTable());
			populateEdgeColumnNames(statement);
			statement.execute(getQueryCreateVertexBaseTable());
			statement.execute(getQueryCreateEdgeBaseTable());
			if(secondaryIndexes){
				createSecondaryIndexes(statement);
			}
		}catch(Exception e){
			throw new Exception("Failed to setup database", e);
		}
	}

	private Set<String> getNewVertexColumns(final Set<String> columnNames){
		final Set<String> newColumnNames = new HashSet<String>(columnNames);
		newColumnNames.removeAll(getVertexColumnNames());
		return newColumnNames;
	}

	private Set<String> getNewEdgeColumns(final Set<String> columnNames){
		final Set<String> newColumnNames = new HashSet<String>(columnNames);
		newColumnNames.removeAll(getEdgeColumnNames());
		return newColumnNames;
	}

	private void updateTableColumns(
			final Set<String> newColumnNames, final String tableName) throws Exception{
		try(final Statement statement = createStatement()){
			String query = "";
			for(final String newColumnName : newColumnNames){
				query += "alter table " + tableName + " add column " + formatColumnName(newColumnName) + " varchar;";
			}
			statement.execute(query);
		}catch(Exception e0){
			throw new Exception("Failed to add columns to " + tableName + " table", e0);
		}
	}

	@Override
	public boolean storeVertex(final AbstractVertex vertex){
		if(vertex == null){
			return false;
		}
		final Set<String> newColumnNames = getNewVertexColumns(vertex.getAnnotationKeys());
		if(!newColumnNames.isEmpty()){
			// Flush existing data because schema needs to be updated
			flush();
			try{
				updateTableColumns(newColumnNames, getVertexTableName());
				addToVertexColumn(newColumnNames);
			}catch(Exception e){
				logger.log(Level.WARNING, "Vertex discarded because of PostgreSQL error", e);
				return false;
			}
		}
		addToBuffer(vertex);
		return true;
	}

	@Override
	public boolean storeEdge(final AbstractEdge edge){
		if(edge == null || edge.getChildVertex() == null || edge.getParentVertex() == null){
			return false;
		}
		final Set<String> newColumnNames = getNewEdgeColumns(edge.getAnnotationKeys());
		if(!newColumnNames.isEmpty()){
			// Flush existing data because schema needs to be updated
			flush();
			try{
				updateTableColumns(newColumnNames, getEdgeTableName());
				addToEdgeColumn(newColumnNames);
			}catch(Exception e){
				logger.log(Level.WARNING, "Edge discarded because of PostgreSQL error", e);
				return false;
			}
		}
		addToBuffer(edge);
		return true;
	}

	private void addToBuffer(final AbstractVertex vertex){
		graphBuffer.add(vertex);
		if(graphBuffer.full()){
			flush();
		}
	}

	private void addToBuffer(final AbstractEdge edge){
		graphBuffer.add(edge);
		if(graphBuffer.full()){
			flush();
		}
	}

	private void persist(final GraphSnapshot graph){
		if(graph.vertexSize() > 0){
			final int vertexBufferSize = graph.vertexSize();
			try{
				final StringBuffer csvHashes = new StringBuffer();
				csvHashes.append(getCSVLine(Arrays.asList(getPrimaryKeyName())));
	
				final StringBuffer csvVertices = new StringBuffer();
				csvVertices.append(getCSVLine(getVertexColumnNames()));
	
				final List<String> vertexValues = new ArrayList<>();
				final Iterator<AbstractVertex> vertices = graph.vertices();
				while(vertices.hasNext()){
					final AbstractVertex vertex = vertices.next();
					for(final String vertexColumnName : getVertexColumnNames()){
						final String vertexValue;
						switch(vertexColumnName){
							case PRIMARY_KEY:{
								vertexValue = vertex.bigHashCode();
								csvHashes.append(getCSVLine(Arrays.asList(vertexValue)));
								break;
							}
							default: vertexValue = vertex.getAnnotation(vertexColumnName); break;
						}
						vertexValues.add(vertexValue);
					}
					csvVertices.append(getCSVLine(vertexValues));
					vertexValues.clear();
				}
	
				final CopyManager copyManager = createCopyManager();
				copyManager.copyIn(
						"copy " + getVertexTableName() + " from stdin (format csv, header)", 
						new BufferedReader(new StringReader(csvVertices.toString()))
						);
	
				copyManager.copyIn(
						"copy " + getBaseVertexTableName() + " from stdin (format csv, header)", 
						new BufferedReader(new StringReader(csvHashes.toString()))
						);
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to persist " + vertexBufferSize + " vertices", e);
			}
		}
		
		if(graph.edgeSize() > 0){
			final int edgeBufferSize = graph.edgeSize();
			try{
				final StringBuffer csvHashes = new StringBuffer();
				csvHashes.append(getCSVLine(Arrays.asList(getPrimaryKeyName())));
	
				final StringBuffer csvData = new StringBuffer();
				csvData.append(getCSVLine(getEdgeColumnNames()));
	
				final List<String> edgesValues = new ArrayList<>();
				final Iterator<AbstractEdge> edges = graph.edges();
				while(edges.hasNext()){
					final AbstractEdge edge = edges.next();
					for(final String edgeColumnName : getEdgeColumnNames()){
						final String edgeValue;
						switch(edgeColumnName){
							case PRIMARY_KEY:{
								edgeValue = edge.bigHashCode();
								csvHashes.append(getCSVLine(Arrays.asList(edgeValue)));
								break;
							}
							case CHILD_VERTEX_KEY: edgeValue = edge.getChildVertex().bigHashCode(); break;
							case PARENT_VERTEX_KEY: edgeValue = edge.getParentVertex().bigHashCode(); break;
							default: edgeValue = edge.getAnnotation(edgeColumnName); break;
						}
						edgesValues.add(edgeValue);
					}
					csvData.append(getCSVLine(edgesValues));
					edgesValues.clear();
				}
	
				final CopyManager copyManager = createCopyManager();
				copyManager.copyIn(
						"copy " + getEdgeTableName() + " from stdin (format csv, header)", 
						new BufferedReader(new StringReader(csvData.toString()))
						);
	
				copyManager.copyIn(
						"copy " + getBaseEdgeTableName() + " from stdin (format csv, header)", 
						new BufferedReader(new StringReader(csvHashes.toString()))
						);
	
				if(BUILD_SCAFFOLD){
					try{
						final Iterator<AbstractEdge> edgesForScaffold = graph.edges();
						while(edgesForScaffold.hasNext()){
							final AbstractEdge edge = edgesForScaffold.next();
							insertScaffoldEntry(edge);
						}
					}catch(Exception e){
						logger.log(Level.WARNING, "Failed to update scaffold", e);
					}
				}
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to persist " + edgeBufferSize + " edges", e);
			}
		}
		graph.clear();
	}

	private String getCSVLine(final Iterable<String> iterable){
		final StringBuffer str = new StringBuffer();
		for(String item : iterable){
			item = formatToCSV(item);
			str.append(item).append(",");
		}
		if(str.length() > 0){
			str.setCharAt(str.length() - 1, '\n');
		}
		return str.toString();
	}

	private String formatToCSV(String item){
		if(item == null){
			return ""; // NULL
		}
		item = item.replace('"', '\"');
		item = '"' + item + '"';
		return item;
	}

	@Override
	public ResultSet executeQuery(String query){
		flush();

		ResultSet result = null;
		try(final Statement queryStatement = createStatement()){
			if(configuration.useFetchSize()){
				queryStatement.setFetchSize(configuration.getFetchSize());
			}
			result = queryStatement.executeQuery(query);
		}catch(Exception ex){
			logger.log(Level.SEVERE, "PostgreSQL query execution not successful!", ex);
		}

		return result;
	}

	private void flush(){
		persist(graphBuffer.flush());
	}

	public List<List<String>> executeQueryForResult(String query, boolean addColumnNames){
		flush();

		try(final Statement queryStatement = createStatement()){
			if(configuration.useFetchSize()){
				queryStatement.setFetchSize(configuration.getFetchSize());
			}
			boolean resultIsResultSet = queryStatement.execute(query);
			if(resultIsResultSet){
				ResultSet resultSet = queryStatement.getResultSet();
				if(resultSet != null){
					List<List<String>> listOfList = new ArrayList<List<String>>();
					if(resultSet != null){
						int columnCount = resultSet.getMetaData().getColumnCount();
						if(addColumnNames){
							List<String> heading = new ArrayList<String>();
							listOfList.add(heading);
							for(int i = 0; i < columnCount; i++){
								heading.add(resultSet.getMetaData().getColumnLabel(i + 1));
							}
						}
						while(resultSet.next()){
							List<String> sublist = new ArrayList<String>();
							listOfList.add(sublist);
							for(int i = 0; i < columnCount; i++){
								Object cellObject = resultSet.getObject(i + 1);
								if(resultSet.wasNull()){
									sublist.add(null);
								}else{
									sublist.add(String.valueOf(cellObject));
								}
							}
						}
					}
					return listOfList;
				}
			}
			// Check if update count
			List<List<String>> listOfList = new ArrayList<List<String>>();
			List<String> sublist0 = new ArrayList<String>();
			sublist0.add("count");
			listOfList.add(sublist0);
			List<String> sublist1 = new ArrayList<String>();
			sublist1.add(String.valueOf(queryStatement.getUpdateCount()));
			listOfList.add(sublist1);
			return listOfList;
		}catch(Exception ex){
			logger.log(Level.SEVERE, "PostgreSQL query execution not successful!", ex);
			throw new RuntimeException("Query failed: " + query, ex);
		}
	}

	@Override
	public synchronized QueryInstructionExecutor getQueryInstructionExecutor(){
		if(queryEnvironment == null){
			queryEnvironment = new PostgreSQLQueryEnvironment(getBaseGraphName(), this);
			if(configuration.isReset()){
				queryEnvironment.resetWorkspace();
			}else{
				queryEnvironment.initialize();
			}
		}
		if(queryInstructionExecutor == null){
			queryInstructionExecutor = new PostgreSQLInstructionExecutor(
					this, queryEnvironment, 
					getPrimaryKeyName(),
					getChildVertexKeyName(), getParentVertexKeyName(), 
					getVertexTableName(), getEdgeTableName());
		}
		return queryInstructionExecutor;
	}

	@Override
	protected boolean addColumn(String table_name, String column_name){
		// TODO Auto-generated method stub
		return false;
	}
}
