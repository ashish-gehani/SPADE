/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
import java.sql.Statement;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

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
    private final int BATCH_SIZE = 1000;
    private int statement_count = 0;
    private Statement batch_statement;

    @Override
    public boolean initialize(String arguments) {
        vertexAnnotations = new HashSet<String>();
        edgeAnnotations = new HashSet<String>();

        // Arguments consist of 4 space-separated tokens: 'driver URL username password'

        try {
            String[] tokens = arguments.split("\\s+");
            String driver = tokens[0];
            String databaseURL = tokens[1];
            String username = tokens[2];
            String password = tokens[3];
            username = (username.equalsIgnoreCase("null")) ? "" : username;
            password = (password.equalsIgnoreCase("null")) ? "" : password;

            Class.forName(driver);
            dbConnection = DriverManager.getConnection(databaseURL, username, password);

            Statement dbStatement = dbConnection.createStatement();

            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS " + VERTEX_TABLE + " ("
                    + "vertexId INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(32) NOT NULL, "
                    + "hash INT NOT NULL"
                    + ")";
            dbStatement.execute(createVertexTable);

            // Create edge table if it does not already exist
            String createEdgeTable = "CREATE TABLE IF NOT EXISTS " + EDGE_TABLE + " ("
                    + "edgeId INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(32) NOT NULL ,"
                    + "hash INT NOT NULL, "
                    + "srcVertexHash INT NOT NULL, "
                    + "dstVertexHash INT NOT NULL"
                    + ")";
            dbStatement.execute(createEdgeTable);
            dbStatement.close();

            // For performance optimization, create and store procedure to add
            // columns to tables since this method may be called frequently
            // ---- PROCEDURES UNSUPPORTED IN H2 ----
//            String procedureStatement = "DROP PROCEDURE IF EXISTS addColumn;\n"
//                    + "CREATE PROCEDURE addColumn(IN myTable VARCHAR(16), IN myColumn VARCHAR(64))\n"
//                    + "BEGIN\n"
//                    + " SET @newStatement = CONCAT('ALTER TABLE ', myTable, ' ADD COLUMN ', myColumn, ' VARCHAR');\n"
//                    + " PREPARE STMT FROM @newStatement;\n"
//                    + " EXECUTE STMT;\n" + "END;";
//            dbStatement.execute(procedureStatement);

            batch_statement = dbConnection.createStatement();
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
            if (++statement_count % BATCH_SIZE > 0) {
                batch_statement.executeBatch();
            }
            batch_statement.close();
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
        // If this column has already been added before for this table, then return
        if ((table.equalsIgnoreCase(VERTEX_TABLE)) && vertexAnnotations.contains(column)) {
            return true;
        } else if ((table.equalsIgnoreCase(EDGE_TABLE)) && edgeAnnotations.contains(column)) {
            return true;
        }

        try {
            // ---- PROCEDURES UNSUPPORTED IN H2 ----
//            CallableStatement callStatement = dbConnection.prepareCall("{CALL addColumn(?, ?)}");
//            callStatement.setString(1, table);
//            callStatement.setString(2, column);
//            callStatement.execute();
//            callStatement.close();
            // Add column of type VARCHAR
//            Statement columnStatement = dbConnection.createStatement();
            String statement = "ALTER TABLE " + table + " ADD IF NOT EXISTS `" + column + "` VARCHAR";
            runStatement(statement);
//            columnStatement.execute();

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
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO " + VERTEX_TABLE + " (`type`, `hash`, ");
        for (String annotationKey : incomingVertex.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            // Sanitize column name to remove special characters
            String newAnnotationKey = sanitizeColumn(annotationKey);

            // As the annotation keys are being iterated, add them as new columns
            // to the table if they do not already exist
            addColumn(VERTEX_TABLE, newAnnotationKey);

            insertStringBuilder.append("`");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append("`, ");
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

            String value = (ENABLE_SANITAZATION) ? incomingVertex.getAnnotation(annotationKey).replace("'", "\"")
                    : incomingVertex.getAnnotation(annotationKey);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        runStatement(insertString);
        return true;
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        int srcVertexHash = incomingEdge.getSourceVertex().hashCode();
        int dstVertexHash = incomingEdge.getDestinationVertex().hashCode();

        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO " + EDGE_TABLE + " (`type`, `hash`, `srcVertexHash`, `dstVertexHash`, ");
        for (String annotationKey : incomingEdge.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }
            // Sanitize column name to remove special characters
            String newAnnotationKey = sanitizeColumn(annotationKey);

            // As the annotation keys are being iterated, add them as new columns
            // to the table if they do not already exist
            addColumn(EDGE_TABLE, newAnnotationKey);

            insertStringBuilder.append("`");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append("`, ");
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

            String value = (ENABLE_SANITAZATION) ? incomingEdge.getAnnotation(annotationKey).replace("'", "\"")
                    : incomingEdge.getAnnotation(annotationKey);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        runStatement(insertString);
        return true;
    }

    private void runStatement(String statement) {
        try {
            batch_statement.addBatch(statement);
            if (++statement_count % BATCH_SIZE == 0) {
                batch_statement.executeBatch();
                batch_statement.close();
                batch_statement = dbConnection.createStatement();
            }
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
