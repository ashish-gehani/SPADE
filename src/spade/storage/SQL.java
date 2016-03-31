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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Iterator;
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
 * @author Dawood Tariq and Hasanat Kazmi
 */
public class SQL extends AbstractStorage {

    private Connection dbConnection;
    private HashSet<String> vertexAnnotations;
    private HashSet<String> edgeAnnotations;
    private final String VERTEX_TABLE = "VERTEX";
    private final String EDGE_TABLE = "EDGE";
    private final boolean ENABLE_SANITAZATION = true;
    private static final String ID_STRING = Settings.getProperty("storage_identifier");
    private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");

    // private Statement batch_statement;
    @Override
    public boolean initialize(String arguments) {
        vertexAnnotations = new HashSet<>();
        edgeAnnotations = new HashSet<>();

        // Arguments consist of 4 space-separated tokens: 'driver URL username password'
        try {
            String[] tokens = arguments.split("\\s+");
            String driver = tokens[0].equalsIgnoreCase("default") ? "org.h2.Driver" : tokens[0];
            String databaseURL = tokens[1].equalsIgnoreCase("default") ? "jdbc:h2:/tmp/spade.sql" : tokens[1];
            String username = tokens[2].equalsIgnoreCase("null") ? "" : tokens[2];
            String password = tokens[3].equalsIgnoreCase("null") ? "" : tokens[3];

            Class.forName(driver).newInstance();
            dbConnection = DriverManager.getConnection(databaseURL, username, password);
            dbConnection.setAutoCommit(false);

            Statement dbStatement = dbConnection.createStatement();
            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS "
                    + VERTEX_TABLE
                    + " (" + ID_STRING + " INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(32) NOT NULL, "
                    + "hash INT NOT NULL"
                    + ")";
            dbStatement.execute(createVertexTable);
            String createEdgeTable = "CREATE TABLE IF NOT EXISTS "
                    + EDGE_TABLE
                    + " (" + ID_STRING + " INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(32) NOT NULL ,"
                    + "hash INT NOT NULL, "
                    + "srcVertexHash INT NOT NULL, "
                    + "dstVertexHash INT NOT NULL"
                    + ")";
            dbStatement.execute(createEdgeTable);
            dbStatement.close();

            return true;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

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

    private String sanitizeColumn(String column) {
        if (ENABLE_SANITAZATION) {
            column = column.replaceAll("[^a-zA-Z0-9]+", "");
        }
        return column;
    }

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
            columnStatement.close();

            if (table.equalsIgnoreCase(VERTEX_TABLE)) {
                vertexAnnotations.add(column);
            } else if (table.equalsIgnoreCase(EDGE_TABLE)) {
                edgeAnnotations.add(column);
            }

            return true;
        } catch (SQLException ex) {
            // column duplicate already present error codes 
            // MySQL = 1060 
            // H2 = 42121
            if (ex.getErrorCode() == 1060 || ex.getErrorCode() == 42121) { 
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

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        int srcVertexHash = incomingEdge.getSourceVertex().hashCode();
        int dstVertexHash = incomingEdge.getDestinationVertex().hashCode();

        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO " + EDGE_TABLE + " (type, hash, srcVertexHash, dstVertexHash, ");
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
        insertStringBuilder.append(srcVertexHash);
        insertStringBuilder.append(", ");
        insertStringBuilder.append(dstVertexHash);
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

    public Graph getEdges(String srcVertexAnnotationKey, String srcVertexAnnotationValue, String dstVertexAnnotationKey, String dstVertexAnnotationValue) {
        try {

            dbConnection.commit();
            Graph resultGraph = new Graph();

            Graph srcVertexGraph = getVertices(srcVertexAnnotationKey + ":" + srcVertexAnnotationValue);
            Graph dstVertexGraph = getVertices(dstVertexAnnotationKey + ":" + dstVertexAnnotationValue);

            Iterator<AbstractVertex> iterator = srcVertexGraph.vertexSet().iterator();
            AbstractVertex srcVertex = iterator.next();
            iterator = dstVertexGraph.vertexSet().iterator();
            AbstractVertex dstVertex = iterator.next();

            resultGraph.putVertex(srcVertex);
            resultGraph.putVertex(dstVertex);

            String query = "SELECT * FROM EDGE WHERE srcVertexHash = " + srcVertex.getAnnotation("hash") + " AND dstVertexHash = " + dstVertex.getAnnotation("hash");
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            while (result.next()) {
                AbstractEdge edge = new Edge(srcVertex, dstVertex);
                edge.removeAnnotation("type");
                for (int i=0; i< columnLabels.size(); i++) {
                    String colName = columnLabels.get(i);
                    if (colName != null) {
                        if (colName.equals(ID_STRING) || colName.equals("hash") || colName.equals("srcVertexHash") || colName.equals("dstVertexHash")) {
                            edge.addAnnotation(colName, Integer.toString(result.getInt(i)));
                        } else {
                            edge.addAnnotation(colName, result.getString(i));
                        }
                    }
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

    @Override
    public Graph getEdges(int srcVertexId, int dstVertexId) {
        return getEdges(ID_STRING, ""+srcVertexId, ID_STRING, ""+dstVertexId);
    }


    private Set<String> getNeighbourVertexIdes(String vertexHash, String direction) {
        try {
            dbConnection.commit();

            String normalizedSrcVertexHash;
            String normalizedDstVertexHash;

            if (DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
                normalizedSrcVertexHash = "dstVertexHash";
                normalizedDstVertexHash = "srcVertexHash";
            } else if (DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
                normalizedSrcVertexHash = "srcVertexHash";
                normalizedDstVertexHash = "dstVertexHash";
            } else {
                return null;
            }

            String query = "SELECT "+ normalizedDstVertexHash +" FROM EDGE WHERE "+ normalizedSrcVertexHash +" = " + vertexHash;
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
            Set<String> toReturn = new HashSet<>();

            while (result.next()) {
                toReturn.add( Integer.toString(result.getInt(normalizedDstVertexHash)) );
            }

            return toReturn;

        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    @Override
    public Graph getLineage(int vertexId, int depth, String direction, String terminatingExpression) {
        // TODO: implement terminatingExpression
        
        Set<String> srcVertexHashes = new HashSet<>();
        Set<String> visitedNodesHashes = new HashSet<>();

        Graph vertexGraph = getVertices(ID_STRING + ":" + vertexId);
        Iterator<AbstractVertex> iterator = vertexGraph.vertexSet().iterator();
        AbstractVertex vertex = iterator.next();
        srcVertexHashes.add(vertex.getAnnotation("hash"));


        Graph toReturn = new Graph();

        for (int iter=0; iter < depth; iter++) {
            for (String srcVertexHash: srcVertexHashes) {
                if (visitedNodesHashes.contains(srcVertexHash)) {
                    continue;
                }
                Set<String> dstVertexHashes = getNeighbourVertexIdes(srcVertexHash, direction);
                for (String dstVertexHash : dstVertexHashes) {
                    Graph neighbour;
                    if (DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
                        neighbour = getEdges("hash", srcVertexHash, "hash", dstVertexHash);
                    } else if (DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
                        neighbour = getEdges("hash", dstVertexHash, "hash", srcVertexHash);
                    } else {
                        return null;
                    }
                    
                    toReturn = Graph.union(toReturn, neighbour);
                }
                srcVertexHashes = dstVertexHashes;
                visitedNodesHashes.addAll(srcVertexHashes);
            }
        }

        toReturn.commitIndex();
        return toReturn;
    }

    private Set<Graph> getPathsStep(int srcVertexId, int dstVertexId, int maxLength, Graph currentPath, Set<String> visitedNodesHashes) {

        if (visitedNodesHashes.contains(srcVertexId) || maxLength == -1) {
            return null;
        }

        visitedNodesHashes.add(srcVertexId+"");

        if ((srcVertexId+"").equals(dstVertexId+"")) {
            Set<Graph> currentPathSet = new HashSet<>();
            currentPathSet.add(currentPath);
            return currentPathSet;
        }

        Graph srcVertexGraph = getVertices(ID_STRING + ":" + srcVertexId);
        Iterator<AbstractVertex> iterator = srcVertexGraph.vertexSet().iterator();
        AbstractVertex srcVertex = iterator.next();
       
        Graph localsubgraph = getLineage(srcVertexId, 1, "d", null);

        Set<Graph> toReturn = new HashSet<>();
        for (AbstractVertex dstVertex : localsubgraph.vertexSet()) {
            Graph pathCopy = Graph.union(currentPath, new Graph());

            for (AbstractVertex vertexToPut : localsubgraph.vertexSet() ) {
                if (vertexToPut.getAnnotation(ID_STRING).equals(dstVertex.getAnnotation(ID_STRING))) {
                    pathCopy.putVertex(vertexToPut);
                    break;
                }
            }

            for (AbstractEdge edgeToPut : localsubgraph.edgeSet() ) {
                if (edgeToPut.getSourceVertex().equals(srcVertex) && edgeToPut.getDestinationVertex().equals(dstVertex)) {
                    pathCopy.putEdge(edgeToPut);
                    break;
                }
            }

            Set<Graph> candidatePaths = getPathsStep(Integer.parseInt( dstVertex.getAnnotation(ID_STRING) ), dstVertexId, maxLength-1, pathCopy, visitedNodesHashes);
            if (candidatePaths!=null) {
                toReturn.addAll(candidatePaths);
            }
        }

        return toReturn;
    }

    @Override
    public Graph getPaths(int srcVertexId, int dstVertexId, int maxLength) { 
        Set<String> visitedNodesHashes = new HashSet<>();
        Set<Graph> allPaths = getPathsStep(srcVertexId, dstVertexId, maxLength, new Graph(), visitedNodesHashes);
        Graph toReturn = new Graph();
        for (Graph path: allPaths) {
            toReturn = Graph.union(toReturn, path);
        }
        return toReturn;
    }

    @Override
    public Graph getPaths(String srcVertexExpression, String dstVertexExpression, int maxLength) {

        Graph srcVertexGraph = getVertices(srcVertexExpression);
        Iterator<AbstractVertex> iterator = srcVertexGraph.vertexSet().iterator();
        AbstractVertex srcVertex = iterator.next();

        Graph dstVertexGraph = getVertices(dstVertexExpression);
        iterator = dstVertexGraph.vertexSet().iterator();
        AbstractVertex dstVertex = iterator.next();
        
        return getPaths(srcVertex.getAnnotation(ID_STRING), dstVertex.getAnnotation(ID_STRING), maxLength);
    }

    private AbstractVertex getVertexFromHash(int hash, int columnCount, Map<Integer, String> columnLabels) {
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
