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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.*;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

/**
 *
 * @author dawood
 */
public class SQL extends AbstractStorage {

    private Connection dbConnection;

    @Override
    public boolean initialize(String arguments) {
        try {
            Class.forName("org.h2.Driver");
            dbConnection = DriverManager.getConnection("jdbc:h2:~/testH2", "sa", "");

            Statement dbStatement = dbConnection.createStatement();

            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS VERTEX ("
                    + "vertexId INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(16) NOT NULL, "
                    + "hash INT NOT NULL"
                    + ")";
            dbStatement.execute(createVertexTable);

            // Create edge table if it does not already exist
            String createEdgeTable = "CREATE TABLE IF NOT EXISTS EDGE ("
                    + "edgeId INT PRIMARY KEY AUTO_INCREMENT, "
                    + "type VARCHAR(16) NOT NULL ,"
                    + "hash INT NOT NULL, "
                    + "fromVertexId INT NOT NULL, "
                    + "toVertexId INT NOT NULL"
                    + ")";
            dbStatement.execute(createEdgeTable);

            // For performance optimization, create and store procedure to add
            // columns to tables since this method will be called frequently
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
            dbConnection.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private int getVertexId(AbstractVertex vertex) {
        try {
            Statement selectStatement = dbConnection.createStatement();
            ResultSet result = selectStatement.executeQuery("SELECT vertexId FROM VERTEX WHERE hash = " + vertex.hashCode());
            result.next();
            return result.getInt("vertexId");
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
    }

    private boolean addColumn(String table, String column) {
        try {
            /*
            CallableStatement callStatement = dbConnection.prepareCall("{CALL addColumn(?, ?)}");
            callStatement.setString(1, table);
            callStatement.setString(2, column);
            callStatement.execute();
            callStatement.close();
             * 
             */
            Statement columnStatement = dbConnection.createStatement();
            columnStatement.execute("ALTER TABLE " + table + " ADD IF NOT EXISTS " + column + " BLOB");
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO VERTEX (type, hash, ");

        for (String annotationKey : incomingVertex.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }
            addColumn("VERTEX", annotationKey);

            insertStringBuilder.append(annotationKey);
            insertStringBuilder.append(", ");
        }

        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES (");

        insertStringBuilder.append(
                "\"" + incomingVertex.type() + "\", "
                + "\"" + incomingVertex.hashCode() + "\", ");

        for (String annotationKey : incomingVertex.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            insertStringBuilder.append("\"" + incomingVertex.getAnnotation(annotationKey).replace("\"", "'") + "\", ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        try {
            Statement insertStatement = dbConnection.createStatement();
            insertStatement.executeUpdate(insertString);
            insertStatement.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        int fromVertexId = getVertexId(incomingEdge.getSourceVertex());
        int toVertexId = getVertexId(incomingEdge.getDestinationVertex());
        if ((fromVertexId == 0) || (toVertexId == 0)) {
            return false;
        }

        StringBuilder insertStringBuilder = new StringBuilder("INSERT INTO EDGE (type, hash, fromVertexId, toVertexId, ");

        for (String annotationKey : incomingEdge.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }
            addColumn("EDGE", annotationKey);

            insertStringBuilder.append(annotationKey);
            insertStringBuilder.append(", ");
        }

        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES (");

        insertStringBuilder.append(
                "\"" + incomingEdge.type() + "\", "
                + "\"" + incomingEdge.hashCode() + "\", "
                + "\"" + fromVertexId + "\", "
                + "\"" + toVertexId + "\", ");

        for (String annotationKey : incomingEdge.getAnnotations().keySet()) {
            if (annotationKey.equalsIgnoreCase("type")) {
                continue;
            }

            insertStringBuilder.append("\"" + incomingEdge.getAnnotation(annotationKey).replace("\"", "'") + "\", ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        try {
            Statement insertStatement = dbConnection.createStatement();
            insertStatement.executeUpdate(insertString);
            insertStatement.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SQL.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
}
