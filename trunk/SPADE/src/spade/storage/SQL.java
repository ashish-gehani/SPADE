/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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
 * @author Dawood
 */
public class SQL extends AbstractStorage {

    private Connection dbConnection;
    private HashSet<String> vertexAnnotations;
    private HashSet<String> edgeAnnotations;
    private HashSet<Integer> receivedVertices;
    private HashSet<Integer> receivedEdges;
    private final String VERTEX_TABLE = "VERTEX";
    private final String EDGE_TABLE = "EDGE";

    @Override
    public boolean initialize(String arguments) {
        vertexAnnotations = new HashSet<String>();
        edgeAnnotations = new HashSet<String>();
        receivedVertices = new HashSet<Integer>();
        receivedEdges = new HashSet<Integer>();

        try {
            Class.forName("org.h2.Driver");
            dbConnection = DriverManager.getConnection("jdbc:h2:" + arguments, "sa", "");

            Statement dbStatement = dbConnection.createStatement();

            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS " + VERTEX_TABLE + " ("
                    + "vertexId INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(16) NOT NULL, "
                    + "hash INT NOT NULL"
                    + ")";
            dbStatement.execute(createVertexTable);

            // Create edge table if it does not already exist
            String createEdgeTable = "CREATE TABLE IF NOT EXISTS " + EDGE_TABLE + " ("
                    + "edgeId INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(16) NOT NULL ,"
                    + "hash INT NOT NULL, "
                    + "fromVertexId INT NOT NULL, "
                    + "toVertexId INT NOT NULL"
                    + ")";
            dbStatement.execute(createEdgeTable);

            // For performance optimization, create and store procedure to add
            // columns to tables since this method will be called frequently
            // ---- PROCEDURES UNSUPPORTED IN H2 ----
            /*
            String procedureStatement = "DROP PROCEDURE IF EXISTS addColumn;\n"
            + "CREATE PROCEDURE addColumn(IN myTable VARCHAR(16), IN myColumn VARCHAR(64))\n"
            + "BEGIN\n"
            + "   SET @newStatement = CONCAT('ALTER TABLE ', myTable, ' ADD COLUMN ', myColumn, ' BLOB');\n"
            + "   PREPARE STMT FROM @newStatement;\n"
            + "   EXECUTE STMT;\n"
            + "END;";
            dbStatement.execute(procedureStatement);
             */

            dbStatement.close();
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

    private boolean addColumn(String table, String column) {
        // If this column has already been added before for this table, then return
        if ((table.equalsIgnoreCase(VERTEX_TABLE)) && vertexAnnotations.contains(column)) {
            return true;
        } else if ((table.equalsIgnoreCase(EDGE_TABLE)) && edgeAnnotations.contains(column)) {
            return true;
        }

        try {
            // ---- PROCEDURES UNSUPPORTED IN H2 ----            
            /*
            CallableStatement callStatement = dbConnection.prepareCall("{CALL addColumn(?, ?)}");
            callStatement.setString(1, table);
            callStatement.setString(2, column);
            callStatement.execute();
            callStatement.close();
             * 
             */
            // Add column of type VARCHAR
            Statement columnStatement = dbConnection.createStatement();
            columnStatement.execute("ALTER TABLE " + table + " ADD IF NOT EXISTS " + column + " VARCHAR");

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
        // If this vertex has already been received before, return false
        if (receivedVertices.contains(incomingVertex.hashCode())) {
            return false;
        }

        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO " + VERTEX_TABLE + " (type, hash, ");
        for (String annotationKey : incomingVertex.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            // As the annotation keys are being iterated, add them as new columns
            // to the table if they do not already exist
            addColumn(VERTEX_TABLE, annotationKey);

            insertStringBuilder.append(annotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin adding values
        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES (");

        // Add the type and hash code
        insertStringBuilder.append(
                "'" + incomingVertex.type() + "', "
                + incomingVertex.hashCode() + ", ");

        // Add the annotation values
        for (String annotationKey : incomingVertex.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            insertStringBuilder.append("'" + incomingVertex.getAnnotation(annotationKey) + "', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        // Execute statement and add this vertex to the set that has been received
        // so as to prevent duplicates
        try {
            Statement insertStatement = dbConnection.createStatement();
            insertStatement.executeUpdate(insertString);
            insertStatement.close();
            receivedVertices.add(incomingVertex.hashCode());
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        // If this edge has already been received before, return false
        if (receivedEdges.contains(incomingEdge.hashCode())) {
            return false;
        }

        // Retrieve the vertex Ids of the source and destination vertices
        // from the VERTEX table
        int fromVertexId = getVertexId(incomingEdge.getSourceVertex());
        int toVertexId = getVertexId(incomingEdge.getDestinationVertex());

        // If either the source or destination vertex do not exist, then return false
        if ((fromVertexId == 0) || (toVertexId == 0)) {
            return false;
        }

        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO " + EDGE_TABLE + " (type, hash, fromVertexId, toVertexId, ");
        for (String annotationKey : incomingEdge.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }
            // As the annotation keys are being iterated, add them as new columns
            // to the table if they do not already exist
            addColumn(EDGE_TABLE, annotationKey);

            insertStringBuilder.append(annotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin adding values
        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES (");

        // Add the type, hash code, and source and destination vertex Ids
        insertStringBuilder.append(
                "'" + incomingEdge.type() + "', "
                + incomingEdge.hashCode() + ", "
                + fromVertexId + ", "
                + toVertexId + ", ");

        // Add the annotation values
        for (String annotationKey : incomingEdge.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            insertStringBuilder.append("'" + incomingEdge.getAnnotation(annotationKey) + "', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        // Execute statement and add this edge to the set that has been received
        // so as to prevent duplicates
        try {
            Statement insertStatement = dbConnection.createStatement();
            insertStatement.executeUpdate(insertString);
            insertStatement.close();
            receivedEdges.add(incomingEdge.hashCode());
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
}
