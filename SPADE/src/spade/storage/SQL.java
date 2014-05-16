/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2014 SRI International

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
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;

/**
 * Basic SQL storage implementation.
 *
 * @author Dawood Tariq
 */
public class SQL extends AbstractStorage {

    private Connection dbConnection;
    private HashSet<String> vertexAnnotations;
    private HashSet<String> edgeAnnotations;
    private final String VERTEX_TABLE = "VERTEX";
    private final String EDGE_TABLE = "EDGE";
    private final boolean ENABLE_SANITAZATION = true;
    private final int BATCH_SIZE = 1;
    private int statement_count = 0;
    private static final String ID_STRING = Settings.getProperty("storage_identifier");
    private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");

    // private Statement batch_statement;
    @Override
    public boolean initialize(String arguments) {
        vertexAnnotations = new HashSet<String>();
        edgeAnnotations = new HashSet<String>();

        // Arguments consist of 4 space-separated tokens: 'driver URL username
        // password'
        try {
            String[] tokens = arguments.split("\\s+");
            String driver = (tokens[0].equalsIgnoreCase("default")) ? "org.apache.derby.jdbc.EmbeddedDriver" : tokens[0];
            String databaseURL = (tokens[1].equalsIgnoreCase("default")) ? "jdbc:derby:/Users/dawood/Desktop/testSPADE/SPADE/bin/derbyDB;create=true" : tokens[1];
            // String username = tokens[2];
            // String password = tokens[3];
            // username = (username.equalsIgnoreCase("null")) ? "" : username;
            // password = (password.equalsIgnoreCase("null")) ? "" : password;

            Class.forName(driver).newInstance();
            dbConnection = DriverManager.getConnection(databaseURL);
            // dbConnection = DriverManager.getConnection(databaseURL, username,
            // password);
            dbConnection.setAutoCommit(false);

            Statement dbStatement = dbConnection.createStatement();

            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE " + VERTEX_TABLE + " (" + "vertexId INT GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), " + "type VARCHAR(32) NOT NULL, "
                    + "hash INT NOT NULL" + ")";
            // String createVertexTable = "CREATE TABLE IF NOT EXISTS " +
            // VERTEX_TABLE + " ("
            // + "vertexId INT PRIMARY KEY AUTO_INCREMENT, "
            // + "type VARCHAR(32) NOT NULL, "
            // + "hash INT NOT NULL"
            // + ")";
            dbStatement.execute(createVertexTable);

            // Create edge table if it does not already exist
            String createEdgeTable = "CREATE TABLE " + EDGE_TABLE + " (" + "edgeId INT GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), " + "type VARCHAR(32) NOT NULL ,"
                    + "hash INT NOT NULL, " + "srcVertexHash INT NOT NULL, " + "dstVertexHash INT NOT NULL" + ")";
            // String createEdgeTable = "CREATE TABLE IF NOT EXISTS " +
            // EDGE_TABLE + " ("
            // + "edgeId INT PRIMARY KEY AUTO_INCREMENT, "
            // + "type VARCHAR(32) NOT NULL ,"
            // + "hash INT NOT NULL, "
            // + "srcVertexHash INT NOT NULL, "
            // + "dstVertexHash INT NOT NULL"
            // + ")";
            dbStatement.execute(createEdgeTable);
            dbStatement.close();

            // For performance optimization, create and store procedure to add
            // columns to tables since this method may be called frequently
            // ---- PROCEDURES UNSUPPORTED IN H2 ----
            // String procedureStatement =
            // "DROP PROCEDURE IF EXISTS addColumn;\n"
            // +
            // "CREATE PROCEDURE addColumn(IN myTable VARCHAR(16), IN myColumn VARCHAR(64))\n"
            // + "BEGIN\n"
            // +
            // " SET @newStatement = CONCAT('ALTER TABLE ', myTable, ' ADD COLUMN ', myColumn, ' VARCHAR');\n"
            // + " PREPARE STMT FROM @newStatement;\n"
            // + " EXECUTE STMT;\n" + "END;";
            // dbStatement.execute(procedureStatement);
            // batch_statement = dbConnection.createStatement();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        try {
            // Close the connection to the database
            // if (++statement_count % BATCH_SIZE > 0) {
            // batch_statement.executeBatch();
            // }
            // batch_statement.close();
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
            dbConnection.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private int getVertexId(AbstractVertex vertex) {
        try {
            // Get the vertexId from the VERTEX table based on the hash value
            Statement selectStatement = dbConnection.createStatement();
            ResultSet result = selectStatement.executeQuery("SELECT vertexId FROM " + VERTEX_TABLE + " WHERE hash = " + vertex.hashCode());
            result.next();
            return result.getInt("vertexId");
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
    }

    private String sanitizeColumn(String column) {
        if (ENABLE_SANITAZATION) {
            column = column.replaceAll("[^a-zA-Z0-9]+", "");
        }
        return column;
    }

    private boolean addColumn(String table, String column) {
        // If this column has already been added before for this table, then
        // return
        if ((table.equalsIgnoreCase(VERTEX_TABLE)) && vertexAnnotations.contains(column)) {
            return true;
        } else if ((table.equalsIgnoreCase(EDGE_TABLE)) && edgeAnnotations.contains(column)) {
            return true;
        }

        try {
            // ---- PROCEDURES UNSUPPORTED IN H2 ----
            // CallableStatement callStatement =
            // dbConnection.prepareCall("{CALL addColumn(?, ?)}");
            // callStatement.setString(1, table);
            // callStatement.setString(2, column);
            // callStatement.execute();
            // callStatement.close();
            // Add column of type VARCHAR
            // Statement columnStatement = dbConnection.createStatement();
            // String statement = "ALTER TABLE " + table +
            // " ADD IF NOT EXISTS `" + column + "` VARCHAR";
            Statement s = dbConnection.createStatement();
            String statement = "ALTER TABLE " + table + " ADD " + column + " VARCHAR(128)";
            s.execute(statement);
            s.close();
            // runStatement(statement);
            // columnStatement.execute();

            if (table.equalsIgnoreCase(VERTEX_TABLE)) {
                vertexAnnotations.add(column);
            } else if (table.equalsIgnoreCase(EDGE_TABLE)) {
                edgeAnnotations.add(column);
            }

            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
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
            // columns
            // to the table if they do not already exist
            addColumn(VERTEX_TABLE, newAnnotationKey);

            insertStringBuilder.append("");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin
        // adding values
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        // runStatement(insertString);
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
            // columns
            // to the table if they do not already exist
            addColumn(EDGE_TABLE, newAnnotationKey);

            insertStringBuilder.append("");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin
        // adding values
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
            e.printStackTrace();
        }
        // runStatement(insertString);
        return true;
    }

    // private void runStatement(String statement) {
    // try {
    // batch_statement.addBatch(statement);
    // if (++statement_count % BATCH_SIZE == 0) {
    // batch_statement.executeBatch();
    // batch_statement.close();
    // batch_statement = dbConnection.createStatement();
    // }
    // } catch (Exception ex) {
    // Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
    // }
    // }
    // private void flushStatements() {
    // try {
    // batch_statement.executeBatch();
    // batch_statement.close();
    // batch_statement = dbConnection.createStatement();
    // } catch (Exception ex) {
    // Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
    // }
    // }
    @Override
    public Graph getVertices(String expression) {
        try {
            // flushStatements();
            Graph graph = new Graph();
            String query = "SELECT * FROM VERTEX WHERE " + expression;
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<Integer, String>();
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

    @Override
    public Graph getLineage(int vertexId, int depth, String direction, String terminatingExpression) {
        // flushStatements();
        Graph graph = new Graph();
        int vertexColumnCount;
        int edgeColumnCount;
        Map<Integer, AbstractVertex> vertexLookup = new HashMap<Integer, AbstractVertex>();
        Map<Integer, String> vertexColumnLabels = new HashMap<Integer, String>();
        Map<Integer, String> edgeColumnLabels = new HashMap<Integer, String>();
        Set<Integer> doneSet = new HashSet<Integer>();
        Set<Integer> tempSet = new HashSet<Integer>();

        // Get the source vertex
        try {
            String query = "SELECT * FROM VERTEX WHERE vertexId = " + vertexId;
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            vertexColumnCount = metadata.getColumnCount();

            for (int i = 1; i <= vertexColumnCount; i++) {
                vertexColumnLabels.put(i, metadata.getColumnName(i));
            }

            result.next();
            AbstractVertex vertex = new Vertex();
            vertex.removeAnnotation("type");
            int id = result.getInt(1);
            int hash = result.getInt(3);
            vertex.addAnnotation(vertexColumnLabels.get(1), Integer.toString(id));
            vertex.addAnnotation("type", result.getString(2));
            vertex.addAnnotation(vertexColumnLabels.get(3), Integer.toString(hash));
            for (int i = 4; i <= vertexColumnCount; i++) {
                String value = result.getString(i);
                if ((value != null) && !value.isEmpty()) {
                    vertex.addAnnotation(vertexColumnLabels.get(i), result.getString(i));
                }
            }
            graph.putVertex(vertex);
            vertexLookup.put(hash, vertex);
            tempSet.add(hash);
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        // Get the vertex hashes for the terminating set
        if ((terminatingExpression != null) && (terminatingExpression.trim().equalsIgnoreCase("null"))) {
            terminatingExpression = null;
        }

        Set<Integer> terminatingSet = null;
        if (terminatingExpression != null) {
            terminatingSet = new HashSet<Integer>();
            try {
                String query = "SELECT hash FROM VERTEX WHERE " + vertexId;
                Statement vertexStatement = dbConnection.createStatement();
                ResultSet result = vertexStatement.executeQuery(query);
                while (result.next()) {
                    terminatingSet.add(result.getInt(1));
                }
            } catch (Exception ex) {
                Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        }

        String dir;
        if (DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
            dir = "a";
        } else if (DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
            dir = "d";
        } else {
            return null;
        }

        while (true) {
            if ((tempSet.isEmpty()) || (depth == 0)) {
                break;
            }
            doneSet.addAll(tempSet);
            Set<Integer> newTempSet = new HashSet<Integer>();
            for (Integer tempVertexHash : tempSet) {
                // Get edges for this vertex
                try {
                    // Construct the required query
                    String query = "SELECT * FROM EDGE WHERE ";
                    query += dir.equals("a") ? "srcVertexHash = " : "dstVertexHash = ";
                    query += tempVertexHash;
                    Statement edgeStatement = dbConnection.createStatement();
                    ResultSet result = edgeStatement.executeQuery(query);

                    // Get column labels for edge table
                    ResultSetMetaData metadata = result.getMetaData();
                    edgeColumnCount = metadata.getColumnCount();

                    for (int i = 1; i <= edgeColumnCount; i++) {
                        edgeColumnLabels.put(i, metadata.getColumnName(i));
                    }

                    while (result.next()) {
                        int hashToCheck = dir.equals("a") ? result.getInt(4) : result.getInt(5);
                        if ((terminatingExpression != null) && (terminatingSet.contains(hashToCheck))) {
                            continue;
                        }
                        if (!doneSet.contains(hashToCheck)) {
                            newTempSet.add(hashToCheck);
                        }
                        AbstractVertex otherVertex = getVertexFromHash(hashToCheck, vertexColumnCount, vertexColumnLabels);
                        graph.putVertex(otherVertex);
                        vertexLookup.put(hashToCheck, otherVertex);
                        AbstractVertex srcVertex = dir.equals("a") ? vertexLookup.get(tempVertexHash) : vertexLookup.get(hashToCheck);
                        AbstractVertex dstVertex = dir.equals("a") ? vertexLookup.get(hashToCheck) : vertexLookup.get(tempVertexHash);
                        AbstractEdge edge = new spade.core.Edge(srcVertex, dstVertex);
                        edge.removeAnnotation("type");

                        edge.addAnnotation(edgeColumnLabels.get(1), Integer.toString(result.getInt(1)));
                        edge.addAnnotation("type", result.getString(2));
                        edge.addAnnotation(edgeColumnLabels.get(3), Integer.toString(result.getInt(3)));
                        for (int i = 5; i <= edgeColumnCount; i++) {
                            String value = result.getString(i);
                            if ((value != null) && !value.isEmpty()) {
                                edge.addAnnotation(edgeColumnLabels.get(i), result.getString(i));
                            }
                        }
                        putEdge(edge);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
                    return null;
                }
            }
            tempSet.clear();
            tempSet.addAll(newTempSet);
            depth--;
        }

        graph.commitIndex();
        return graph;
    }

    private AbstractVertex getVertexFromHash(int hash, int columnCount, Map<Integer, String> columnLabels) {
        try {
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
