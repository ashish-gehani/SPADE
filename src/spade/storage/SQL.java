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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Set;
import java.util.Stack;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.core.Edge;


/**
 * Basic SQL storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public class SQL extends AbstractStorage {

    private Connection dbConnection;
    private HashSet<String> vertexAnnotations;
    private HashSet<String> edgeAnnotations;
    private final String VERTEX_TABLE = "VERTEX";
    private final String EDGE_TABLE = "EDGE";
    private final boolean ENABLE_SANITAZATION = true;
    private static final String VERTEX_ID = "vertexid";
    private static final String EDGE_ID = "edgeid";
    private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");
    private static String databaseDriver;
    private static int duplicateColumnErrorCode;
    public static boolean TEST_ENV = false;
    public static Graph TEST_GRAPH ;

    /**
     *  initializes the SQL database and creates the necessary tables
     * if not already present. The necessary tables include VERTEX and EDGE tables
     * to store provenance metadata.
     *
     * @param arguments A string of 4 space-separated tokens used for making a successful
     *                  connection to the database, of the following format:
     *                  'driver_name database_URL username password'
     *
     *                  Example argument strings are as follows:
     *                  *H2*
     *                  org.h2.Driver jdbc:h2:/tmp/spade.sql sa null
     *                  *PostgreSQL*
     *                  org.postgresql.Driver jdbc:postgres://localhost/5432/spade_pg.sql root 12345
     *
     *                  Points to note:
     *                  1. The database driver jar should be present in lib/ in the project's root.
     *                  2. For external databases like MySQL or PostgreSQL, a stand-alone database
     *                  version needs to be installed and executed in parallel, and independent of the
     *                  SPADE kernel.
     *
     * @return  returns true if the connection to database has been successful.
     */
    @Override
    public boolean initialize(String arguments) {
        vertexAnnotations = new HashSet<>();
        edgeAnnotations = new HashSet<>();

        // Arguments consist of 4 space-separated tokens: 'driver URL username password'
        try {
            String[] tokens = arguments.split("\\s+");
            String driver = tokens[0].equalsIgnoreCase("default") ? "org.h2.Driver" : tokens[0];
            // for postgres, it is jdbc:postgres://localhost/5432/database_name
            String databaseURL = tokens[1].equalsIgnoreCase("default") ? "jdbc:h2:/tmp/spade.sql" : tokens[1];
            String username = tokens[2].equalsIgnoreCase("null") ? "" : tokens[2];
            String password = tokens[3].equalsIgnoreCase("null") ? "" : tokens[3];

            Class.forName(driver).newInstance();
            dbConnection = DriverManager.getConnection(databaseURL, username, password);
            dbConnection.setAutoCommit(false);

            Statement dbStatement = dbConnection.createStatement();
            String key_syntax = driver.equalsIgnoreCase("org.postgresql.Driver")? " SERIAL PRIMARY KEY, " : " INT PRIMARY KEY AUTO_INCREMENT, ";
            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS "
                    + VERTEX_TABLE
                    + "(" + VERTEX_ID + key_syntax
                    + "type VARCHAR(32) NOT NULL, "
                    + "hash INT NOT NULL"
                    + ")";
            dbStatement.execute(createVertexTable);
            String createEdgeTable = "CREATE TABLE IF NOT EXISTS "
                    + EDGE_TABLE
                    + " (" + EDGE_ID + key_syntax
                    + "type VARCHAR(32) NOT NULL ,"
                    + "hash INT NOT NULL, "
                    + "sourceVertexHash INT NOT NULL, "
                    + "destinationVertexHash INT NOT NULL"
                    + ")";
            dbStatement.execute(createEdgeTable);
            dbStatement.close();

            databaseDriver = driver;
            switch(databaseDriver)
            {
                case "org.h2.Driver":
                    duplicateColumnErrorCode = 42121;
                    break;
                case "org.mysql.Driver":
                    duplicateColumnErrorCode = 1060;
                    break;
                case "org.postgresql.Driver":
                    duplicateColumnErrorCode = 42701;
                    break;
                default:
                    duplicateColumnErrorCode = 42121;
            }

            return true;

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    /**
     *  closes the connection to the open SQL database
     * after committing all pending transactions.
     *
     * @return  returns true if the database connection is successfully closed.
     */
    @Override
    public boolean shutdown() {
        try {
            dbConnection.commit();
            dbConnection.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
    /**
     *  cleans the given column name string for all characters
     * other than digits and alphabets.
     *
     * @param column The name of column to sanitize.
     *
     * @return  returns the sanitized column name string.
     */
    private String sanitizeColumn(String column) {
        if (ENABLE_SANITAZATION) {
            column = column.replaceAll("[^a-zA-Z0-9]+", "");
        }
        return column;
    }

    /**
     *  adds a new column in the database table,
     * if it is not already present.
     *
     * @param table The table in database to add column to.
     * @param column The name of column to add in the table.
     *
     * @return  returns true if column creation in the database has been successful.
     */
    private boolean addColumn(String table, String column) {
        // If this column has already been added before for this table, then return
        if ((table.equalsIgnoreCase(VERTEX_TABLE)) && vertexAnnotations.contains(column)) {
            return true;
        } else if ((table.equalsIgnoreCase(EDGE_TABLE)) && edgeAnnotations.contains(column)) {
            return true;
        }

        try {
            Statement columnStatement = dbConnection.createStatement();
            String statement = "ALTER TABLE `" + table
                        + "` ADD COLUMN `"
                        + column
                        + "` VARCHAR(256);";
            columnStatement.execute(statement);
            dbConnection.commit();
            columnStatement.close();

            if (table.equalsIgnoreCase(VERTEX_TABLE)) {
                vertexAnnotations.add(column);
            } else if (table.equalsIgnoreCase(EDGE_TABLE)) {
                edgeAnnotations.add(column);
            }

            return true;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == duplicateColumnErrorCode) {
                if (table.equalsIgnoreCase(VERTEX_TABLE)) {
                    vertexAnnotations.add(column);
                } else if (table.equalsIgnoreCase(EDGE_TABLE)) {
                    edgeAnnotations.add(column);
                }
                return true;
            }
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return false;
    }

    /**
     *  inserts a vertex into the underlying SQL database.
     *
     * @param incomingVertex The temporary vertex object destined to insert in the database.
     *
     * @return  returns true if vertex insertion in database is successful.
     */

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO " + VERTEX_TABLE + " (type, hash, ");
        for (String annotationKey : incomingVertex.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            // Sanitize column name to remove special characters
            String newAnnotationKey = sanitizeColumn(annotationKey);

            // As the annotation keys are being iterated, add them as new
            // columns to the table if they do not already exist
            addColumn(VERTEX_TABLE, newAnnotationKey);

            insertStringBuilder.append("");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin adding values
        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES (");

        // Add the type and hash code
        insertStringBuilder.append("'");
        insertStringBuilder.append(incomingVertex.type());
        insertStringBuilder.append("', ");
        insertStringBuilder.append(incomingVertex.hashCode());
        insertStringBuilder.append(", ");

        // Add the annotation values
        for (String annotationKey : incomingVertex.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            String value = (ENABLE_SANITAZATION) ? incomingVertex.getAnnotation(annotationKey).replace("'", "\"") : incomingVertex.getAnnotation(annotationKey);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        try {
            Statement s = dbConnection.createStatement();
            s.execute(insertString);
            s.close();
            // s.closeOnCompletion();
        } catch (Exception e) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, e);
        }
        return true;
    }

    /**
     *  inserts an edge into the underlying SQL database.
     *
     * @param incomingEdge The temporary edge object destined to insert in the database.
     *
     * @return  returns true if edge insertion in database is successful.
     */
    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        int sourceVertexHash = incomingEdge.getSourceVertex().hashCode();
        int destinationVertexHash = incomingEdge.getDestinationVertex().hashCode();

        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO " + EDGE_TABLE + " (type, hash, sourceVertexHash, destinationVertexHash, ");
        for (String annotationKey : incomingEdge.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }
            // Sanitize column name to remove special characters
            String newAnnotationKey = sanitizeColumn(annotationKey);

            // As the annotation keys are being iterated, add them as new
            // columns to the table if they do not already exist
            addColumn(EDGE_TABLE, newAnnotationKey);

            insertStringBuilder.append("");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin adding values
        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES (");

        // Add the type, hash code, and source and destination vertex Ids
        insertStringBuilder.append("'");
        insertStringBuilder.append(incomingEdge.type());
        insertStringBuilder.append("', ");
        insertStringBuilder.append(incomingEdge.hashCode());
        insertStringBuilder.append(", ");
        insertStringBuilder.append(sourceVertexHash);
        insertStringBuilder.append(", ");
        insertStringBuilder.append(destinationVertexHash);
        insertStringBuilder.append(", ");

        // Add the annotation values
        for (String annotationKey : incomingEdge.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            String value = (ENABLE_SANITAZATION) ? incomingEdge.getAnnotation(annotationKey).replace("'", "\"") : incomingEdge.getAnnotation(annotationKey);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        try {
            Statement s = dbConnection.createStatement();
            s.execute(insertString);
            s.close();
        } catch (Exception e) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, e);
        }
        return true;
    }

    /**
     *  returns vertices satisfying given expression for the vertex.
     *
     * @param expression The expression containing attribute name and value for vertices.
     *
     * @return  returns a graph object containing relevant vertices.
     */
    @Override
    public Graph getVertices(String expression) {
        try {
            dbConnection.commit();
            Graph graph = new Graph();
            // assuming that expression is single key value only
            String query = "SELECT * FROM VERTEX WHERE " + expression.replace(":","=");
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            while (result.next()) {
                AbstractVertex vertex = new Vertex();
                vertex.removeAnnotation("type");
                vertex.addAnnotation(columnLabels.get(1), Integer.toString(result.getInt(1)));
                vertex.addAnnotation("type", result.getString(2));
                vertex.addAnnotation(columnLabels.get(3), Integer.toString(result.getInt(3)));
                for (int i = 4; i <= columnCount; i++) {
                    String value = result.getString(i);
                    if ((value != null) && !value.isEmpty()) {
                        vertex.addAnnotation(columnLabels.get(i), result.getString(i));
                    }
                }
                graph.putVertex(vertex);
            }

            graph.commitIndex();
            return graph;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    /**
     *  returns edges satisfying given expressions for
     * the source vertex and the destination vertex.
     *
     * @param sourceVertexAnnotationKey The attribute name for source vertices.
     * @param sourceVertexAnnotationValue The attribute value for source vertices.
     * @param destinationVertexAnnotationKey The attribute name for destination vertices.
     * @param destinationVertexAnnotationValue The attribute value for destination vertices.
     *
     * @return  returns a graph object containing relevant edges.
     */
    public Graph getEdges(String sourceVertexAnnotationKey, String sourceVertexAnnotationValue, String destinationVertexAnnotationKey, String destinationVertexAnnotationValue) {
        try {

            dbConnection.commit();
            Graph resultGraph = new Graph();

            Graph sourceVertexGraph = getVertices(sourceVertexAnnotationKey + ":" + sourceVertexAnnotationValue);
            Graph destinationVertexGraph = getVertices(destinationVertexAnnotationKey + ":" + destinationVertexAnnotationValue);

            Iterator<AbstractVertex> iterator = sourceVertexGraph.vertexSet().iterator();
            AbstractVertex sourceVertex = iterator.next();
            iterator = destinationVertexGraph.vertexSet().iterator();
            AbstractVertex destinationVertex = iterator.next();

            resultGraph.putVertex(sourceVertex);
            resultGraph.putVertex(destinationVertex);

            String query = "SELECT * FROM EDGE WHERE sourceVertexHash = " + sourceVertex.getAnnotation("hash") + " AND destinationVertexHash = " + destinationVertex.getAnnotation("hash");
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            while (result.next()) {
                AbstractEdge edge = new Edge(sourceVertex, destinationVertex);
                edge.removeAnnotation("type");
                for (int i=1; i <= columnCount; i++) {
                    String colName = columnLabels.get(i);
                    if (colName != null) {
                        if (colName.equals(VERTEX_ID) || colName.equals("hash") || colName.equals("sourceVertexHash") || colName.equals("destinationVertexHash")) {
                            edge.addAnnotation(colName, Integer.toString(result.getInt(i)));
                        } else {
                            edge.addAnnotation(colName, result.getString(i));
                        }
                    }
                    String h = edge.getAnnotation("hash");
                    int x = edge.hashCode();

                }
                resultGraph.putEdge(edge);
            }

            resultGraph.commitIndex();
            return resultGraph;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    /**
     * Finds edges satisfying given ids for
     * the source vertex and the destination vertex. It calls
     * the generic getEdges implementation and returns its results.
     *
     * @param sourceVertexId The id of source vertex.
     * @param destinationVertexId The id of destination vertex.
     *
     * @return  returns a graph object containing relevant edges.
     */
    @Override
    public Graph getEdges(int sourceVertexId, int destinationVertexId) {
        return getEdges(VERTEX_ID, ""+sourceVertexId, VERTEX_ID, ""+destinationVertexId);
    }

    /**
     * Finds IDs of all neighboring vertices of the given vertex
     *
     * @param vertexId ID of the vertex whose neighbors are to find
     * @param direction Direction of the neighbor from the given vertex
     *
     * @return Returns list of IDs of neighboring vertices
     */
    private Set<Integer> getNeighborVertexIds(int vertexId, String direction)
    {
        Set<Integer> neighborvertexIds = new HashSet<>();
        try {
            dbConnection.commit();
            String sourceVertex = null;
            String destinationVertex = null;
            if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
                sourceVertex = "sourceVertexHash";
                destinationVertex = "destinationVertexHash";
            }
            else if(DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()))
            {
                sourceVertex = "destinationVertexHash";
                destinationVertex = "sourceVertexHash";
            }
            String query = "SELECT vertexId FROM vertex WHERE hash IN (SELECT " + destinationVertex + " FROM edge WHERE ";
            query +=   sourceVertex + " = " + getVertices(VERTEX_ID + ":" + vertexId).vertexSet().iterator().next().getAnnotation("hash") + ")";
            Statement statement = dbConnection.createStatement();
            ResultSet result = statement.executeQuery(query);
            while (result.next())
            {
                neighborvertexIds.add(result.getInt(1));
            }

        } catch (SQLException ex)
        {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        return neighborvertexIds;
    }


    /**
     * Finds paths between the given source and destination vertex.
     *
     * @param sourceVertexId ID of the current node during traversal
     * @param destinationVertexId ID of the destination node
     * @param maxPathLength Maximum length of any path to find
     * @param visitedNodes List of nodes visited during traversal
     * @param currentPath Set of vertices explored currently
     * @param allPaths Set of all paths between source and destination
     *
     * @return nothing
     */
    private void findPath(int sourceVertexId, int destinationVertexId, int maxPathLength, Set<Integer> visitedNodes, Stack<AbstractVertex> currentPath, Set<Graph> allPaths)
    {
        if (currentPath.size() > maxPathLength)
            return;

        visitedNodes.add(sourceVertexId);
        currentPath.push(getVertices(VERTEX_ID + ":" + sourceVertexId).vertexSet().iterator().next());

        if (sourceVertexId == destinationVertexId)
        {
            Graph pathGraph = new Graph();
            Iterator<AbstractVertex> iter = currentPath.iterator();
            AbstractVertex previous = iter.next();
            pathGraph.putVertex(previous);
            while(iter.hasNext())
            {
                AbstractVertex curr = iter.next();
                pathGraph.putVertex(curr);
                // find the relevant edges between previous and current vertex
                Graph edges;
                edges = getEdges(VERTEX_ID, previous.getAnnotation(VERTEX_ID), VERTEX_ID, curr.getAnnotation(VERTEX_ID));
                pathGraph = Graph.union(pathGraph, edges);
                previous = curr;
            }
            allPaths.add(pathGraph);
        }
        else
        {
            for(int neighborId: getNeighborVertexIds(sourceVertexId, DIRECTION_ANCESTORS))
            {
                if(!visitedNodes.contains(neighborId))
                {
                    findPath(neighborId, destinationVertexId, maxPathLength, visitedNodes, currentPath, allPaths);
                }
            }
        }

        currentPath.pop();
        visitedNodes.remove(sourceVertexId);

    }


    /**
     * Finds all possible paths between source and destination vertices.
     *
     * @param sourceVertexId ID of the source vertex
     * @param destinationvertexId ID of the destination vertex
     * @param maxPathLength Maximum length of any path to find
     *
     * @return Returns graph containing all paths between the given source and destination vertex
     */
    public Graph findPaths(int sourceVertexId, int destinationvertexId, int maxPathLength)
    {
        Graph resultGraph = new Graph();
        Set<Integer> visitedNodes = new HashSet<>();
        Stack<AbstractVertex> currentPath = new Stack<>();
        Set<Graph> allPaths = new HashSet<>();
        try
        {
            // Find path between source and destination vertices
            findPath(sourceVertexId, destinationvertexId, maxPathLength, visitedNodes, currentPath, allPaths);
            for (Graph path: allPaths)
            {
                resultGraph = Graph.union(resultGraph, path);
            }
        }
        catch (Exception ex)
        {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }


        return resultGraph;
    }

    /**
     * Finds paths between the source and destination vertices.
     *
     * @param sourceVertexId ID of the source vertex
     * @param maxDepth Maximum depth from source vertex to traverse
     * @param direction Direction of traversal from source vertex
     * @param terminatingVertexId ID of the terminating vertex for the algorithm
     *
     * @return Returns graph containing all paths between all source and destination vertices.
     */
    public Graph getLineage(int sourceVertexId, int maxDepth , String direction, int terminatingVertexId)
    {
        Graph resultGraph = new Graph();
        AbstractVertex sourceVertex = getVertices(VERTEX_ID + ":" + sourceVertexId).vertexSet().iterator().next();
        if(maxDepth == 0)
        {
            resultGraph.putVertex(sourceVertex);
            return resultGraph;
        }
        Queue<AbstractVertex> queue = new LinkedList<>();
        queue.add(sourceVertex);
        for(int depth = 0 ; depth < maxDepth && !queue.isEmpty() ; depth++)
        {
            AbstractVertex node = queue.remove();
            int nodeId = Integer.parseInt(node.getAnnotation(VERTEX_ID));
            resultGraph.putVertex(node);
            try {
                for (int nId : getNeighborVertexIds(nodeId, direction)) {
                    if (nId == terminatingVertexId)
                        continue;
                    AbstractVertex neighbor = getVertices(VERTEX_ID + ":" + nId).vertexSet().iterator().next();
                    boolean notVisited = resultGraph.putVertex(neighbor);
                    Graph edges = getEdges(VERTEX_ID,
                            DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()) ? Integer.toString(nodeId) : Integer.toString(nId),
                            VERTEX_ID,
                            DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()) ? Integer.toString(nodeId) : Integer.toString(nId));
                    resultGraph = Graph.union(resultGraph, edges);
                    if (notVisited)
                        queue.add(neighbor);
                }
            }
            catch (Exception ex)
            {
                Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        }
        return resultGraph;
    }

    /**
     * Performs a lookup for a vertex in the table using its hash.
     *
     * @param hash Hash of the vertex
     * @param columnCount Number of columns set for that vertex in the table
     * @param columnLabels Labels of columns set for that vertex in the table
     *
     * @return Returns the relevant vertex matching the given hash
     */
    private AbstractVertex getVertexFromHash(int hash, int columnCount, Map<Integer, String> columnLabels)
    {
        try {
            dbConnection.commit();
            String query = "SELECT * FROM VERTEX WHERE hash = " + hash;
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
            result.next();
            AbstractVertex vertex = new Vertex();
            vertex.removeAnnotation("type");
            vertex.addAnnotation(columnLabels.get(1), Integer.toString(result.getInt(1)));
            vertex.addAnnotation("type", result.getString(2));
            vertex.addAnnotation(columnLabels.get(3), Integer.toString(result.getInt(3)));
            for (int i = 4; i <= columnCount; i++) {
                String value = result.getString(i);
                if ((value != null) && !value.isEmpty()) {
                    vertex.addAnnotation(columnLabels.get(i), result.getString(i));
                }
            }
            return vertex;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
