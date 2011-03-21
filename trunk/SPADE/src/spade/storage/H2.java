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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.hsqldb.*;
import spade.core.AbstractStorage;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Lineage;
import spade.opm.edge.Used;
import spade.opm.edge.WasControlledBy;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.vertex.Agent;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Process;

public class H2 extends AbstractStorage {

    private HashMap<AbstractVertex, String> vertexCache;
    private Connection sqlConnection;
    private HashSet<String> columnNamesCache;
    private double vertexCacheSize;
    private HashMap<String, Boolean> edgeTypeCache;
    public double tempVCount;
    public double tempECount;

    //Tables names: Process, Agent, File, AbstractVertex, AbstractEdge
    //all column names are preceeded by c_ to prevent keywords being used as columns
    //edges are id,type,source id, destination id, and annotations
    @Override
    public boolean initialize(String arguments) {
        tempVCount = tempECount = 0;
        columnNamesCache = new HashSet<String>();
        vertexCache = new HashMap<AbstractVertex, String>();
        vertexCacheSize = 10000;
        edgeTypeCache = new HashMap<String, Boolean>();
        //what information needs to be passed in here??
        Properties props = new Properties();
        props.put("user", "spade");
        sqlConnection = this.dbConnect("jdbc:h2:" + arguments, props);
        if (sqlConnection == null) {
            System.out.println("Error connecting to database.");
            return false;
        }
        String tableSchema = "PUBLIC";
        this.checkSchema(tableSchema);
        return true;
    }

    private boolean checkDatabaseForAbstractVertex(AbstractVertex source) {
        boolean vertexExistsInDatabase = false;
        String OPMObjectName = source.getVertexType();
        String tableName = "";
        if (OPMObjectName.equalsIgnoreCase("Process")) {
            tableName = "PROCESS";
        } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
            tableName = "ARTIFACT";
        } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
            tableName = "AGENT";
        } else {
            return false;
        }
        String constraints = "";
        Iterator<String> annotationsKeysIterator = source.getAnnotations().keySet().iterator();
        while (annotationsKeysIterator.hasNext()) {
            String key = annotationsKeysIterator.next();
            String column = "C_" + key;
            String value = source.getAnnotation(key);
            if (value == null) {
                constraints = constraints + " AND " + column + " IS NULL";
            } else {
                constraints = constraints + " AND " + column + "='" + value + "'";
            }
        }
        if (constraints.length() > 3) {
            constraints = constraints.substring(4);
        } else {
            constraints = "false";
        }
        try {

            //System.out.println(constraints);
            //get the vertex id for the vertex
            Statement stmt = sqlConnection.createStatement();
            String query = "SELECT ID FROM " + tableName + " WHERE " + constraints;
            ResultSet result = stmt.executeQuery(query);
            //System.out.println(constraints);
            if (result.next()) {
                vertexExistsInDatabase = true;
            } else {
                //vertex isn't there already
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return vertexExistsInDatabase;
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {


        //ensure the schema can accommodate this vertex
        this.checkSchemaForAbstractVertex(incomingVertex);
        //ensure this is not a duplicate
        if (this.checkDatabaseForAbstractVertex(incomingVertex)) {
            return false;
        }

        //put vertex into the database
        String vertexType = incomingVertex.getVertexType();
        String tableName = this.getAbstractVertexTable(incomingVertex);
        if (tableName == null) {
            return false;
        }

        Statement stmt;
        try {
            stmt = sqlConnection.createStatement();
            //System.out.println(v.getAnnotations().keySet());
            //Set<String> columNames = v.getAnnotations().keySet();
            //System.out.println(columnNames.size());
            Iterator<String> it = incomingVertex.getAnnotations().keySet().iterator();
            String columnList = "";
            String valueList = "";

            //System.out.println(v.getAnnotations().keySet());

            String temp;
            while (it.hasNext()) {
                String key = it.next();
                columnList = columnList + "C_" + key + ",";
                temp = incomingVertex.getAnnotation(key);
                if (temp.startsWith("\"") && temp.endsWith("\"")) {
                    temp = temp.substring(1, temp.length() - 1);
                }
                valueList = valueList + "'" + temp + "',";

            }
            columnList = columnList.substring(0, columnList.length() - 1);
            valueList = valueList.substring(0, valueList.length() - 1);

            String check = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + valueList + ")";

            boolean checkResult = stmt.execute(check);

            String vertexId = "";
            check = "SELECT * FROM " + tableName;
            ResultSet result = stmt.executeQuery(check);
            result.last();
            int vertexIdVal = result.getRow();
            vertexId = "" + vertexIdVal;

            check = "INSERT INTO VERTEX (VERTEXTYPE,LINKEDVERTEXID) VALUES ('" + vertexType + "'," + vertexId + ")";

            //System.out.println("Link:"+vertexId);

            checkResult = stmt.execute(check);


            check = "SELECT VERTEXID FROM VERTEX WHERE VERTEXTYPE='" + vertexType + "' AND LINKEDVERTEXID='" + vertexId + "'";
            result = stmt.executeQuery(check);
            result.last();
            vertexIdVal = result.getInt("VERTEXID");
            vertexId = "" + vertexIdVal;
            //System.out.println("Q:"+check);
            //System.out.println("AbstractVertexForLink:"+vertexId);


            if (vertexCache.size() > vertexCacheSize) {

                vertexCache.clear();
            }

            vertexCache.put(incomingVertex, vertexId);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }



    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {

        AbstractVertex vsrc = incomingEdge.getSrcVertex();
        AbstractVertex vdst = incomingEdge.getDstVertex();

        double time1 = System.currentTimeMillis();

        if (!vertexCache.containsKey(vsrc)) {
            this.checkSchemaForAbstractVertex(vsrc);
        }
        if (!vertexCache.containsKey(vdst)) {
            this.checkSchemaForAbstractVertex(vdst);
        }
        if (!edgeTypeCache.containsKey(incomingEdge.getEdgeType())) {
            this.checkSchemaForAbstractEdge(incomingEdge);
            edgeTypeCache.put(incomingEdge.getEdgeType(), true);
            //System.out.println("New AbstractEdge");
        }

        double time2 = System.currentTimeMillis();
        //System.out.println("Time taken:"+(time2-time1));


        Statement stmt;
        try {
            stmt = sqlConnection.createStatement();
            String vertex1Id = "";
            String vertex2Id = "";
            //find vertex id of first vertex
            if (vertexCache.containsKey(vsrc)) {
                vertex1Id = vertexCache.get(vsrc);
                //System.out.println("Hit1");
            } else {

                String vertex1Table = getAbstractVertexTable(vsrc);




                //first make the search query for this vertex by making a query for all key,value annotations in the vertex

                Iterator<String> it = vsrc.getAnnotations().keySet().iterator();
                String constraints = "";



                String temp;
                while (it.hasNext()) {
                    String key = it.next();
                    temp = vsrc.getAnnotation(key);
                    if (temp.startsWith("\"") && temp.endsWith("\"")) {
                        temp = temp.substring(1, temp.length() - 1);
                    }

                    constraints = constraints + "C_" + key + " = '" + temp + "' AND ";

                }
                if (constraints.length() > 5) {
                    constraints = constraints.substring(0, constraints.length() - 5);
                }


                String sourceIdQuery = "SELECT VERTEXID FROM VERTEX," + vertex1Table + " WHERE " + vertex1Table + ".ID = LINKEDVERTEXID "
                        + "AND " + constraints;

                ResultSet vertex1Set = stmt.executeQuery(sourceIdQuery);

                if (vertex1Set.next()) {

                    vertex1Id = new Integer(vertex1Set.getInt("VERTEXID")).toString();
                    vertexCache.put(vsrc, vertex1Id);

                } else {
                    return false;
                }

            }


            //find vertex id of second vertex



            if (vertexCache.containsKey(vdst)) {
                vertex2Id = vertexCache.get(vdst);
                //System.out.println("Hit2");
            } else {

                String vertex2Table = getAbstractVertexTable(vdst);
                //find vertex id of first vertex



                //first make the search query for this vertex by making a query for all key,value annotations in the vertex

                Iterator<String> it2 = vdst.getAnnotations().keySet().iterator();
                String constraints2 = "";

                //System.out.println(v.getAnnotations().keySet());

                String temp2;
                while (it2.hasNext()) {
                    String key = it2.next();
                    temp2 = vdst.getAnnotation(key);
                    if (temp2.startsWith("\"") && temp2.endsWith("\"")) {
                        temp2 = temp2.substring(1, temp2.length() - 1);
                    }

                    constraints2 = constraints2 + "C_" + key + " = '" + temp2 + "' AND ";

                }
                if (constraints2.length() > 5) {
                    constraints2 = constraints2.substring(0, constraints2.length() - 5);
                }


                String sourceIdQuery2 = "SELECT VERTEXID FROM VERTEX," + vertex2Table + " WHERE " + vertex2Table + ".ID = LINKEDVERTEXID "
                        + "AND " + constraints2;

                ResultSet vertex2Set = stmt.executeQuery(sourceIdQuery2);

                if (vertex2Set.next()) {

                    vertex2Id = new Integer(vertex2Set.getInt("VERTEXID")).toString();
                    vertexCache.put(vsrc, vertex1Id);

                } else {

                    //System.out.println(sourceIdQuery2);

                    return false;
                }

            }

            //we have vertex1Id and vertex2Id

            //System.out.println(System.out.println("AbstractVertex");vertex1Id+" "+vertex2Id);

            //put in the edge in the database

            //first get the annotations for the edge type to put into the database
            Iterator<String> it3 = incomingEdge.getAnnotations().keySet().iterator();
            String columnList = "";
            String valueList = "";

            //System.out.println(v.getAnnotations().keySet());

            String temp = "";
            while (it3.hasNext()) {
                String key = it3.next();
                columnList = columnList + "C_" + key + ",";
                temp = incomingEdge.getAnnotation(key);
                if (temp.startsWith("\"") && temp.endsWith("\"")) {
                    temp = temp.substring(1, temp.length() - 1);
                }
                valueList = valueList + "'" + temp + "',";

            }
            String edgeQuery = "";
            //make sure you are not sending empty strings to be cleaned up
            if (columnList.length() > 1 && valueList.length() > 1) {
                columnList = columnList.substring(0, columnList.length() - 1);
                valueList = valueList.substring(0, valueList.length() - 1);
            } else {
                edgeQuery = "INSERT INTO EDGES (EDGETYPE,SOURCEID,DESTINATIONID) VALUES ('" + incomingEdge.getEdgeType() + "'," + vertex1Id + "," + vertex2Id + ")";

            }


            if (vertexCache.size() > vertexCacheSize) {
                vertexCache.clear();
            }



            boolean result = stmt.execute(edgeQuery);
            return result;
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            return false;
        }


    }

    @Override
    public boolean shutdown() {
        try {
            sqlConnection.close();
            return true;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }

    }

    private Connection dbConnect(String db_connect_string, Properties props) {
        try {

            Class.forName("org.hsqldb.jdbc.JDBCDriver").newInstance();
            Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:testdb", "SA", "");

//            Class.forName("org.h2.Driver").newInstance();
//            Connection conn = DriverManager.getConnection(db_connect_string, props);

            return conn;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean checkandAddColumn(String columnName, String tableName) {
        boolean flag = false;
        if (columnNamesCache.contains(columnName + tableName)) {
            flag = true;
            return flag;
        }
        try {

            Statement stmt = sqlConnection.createStatement();
            String check = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "'AND COLUMN_NAME = 'C_" + columnName.toUpperCase() + "'";
            ResultSet checkResult = stmt.executeQuery(check);

            if (checkResult.next() == false) {
                String processQuery = "ALTER TABLE " + tableName + " ADD C_" + columnName + " VARCHAR(64)";
                boolean processResult = stmt.execute(processQuery);
                columnNamesCache.add(columnName + tableName);
                flag = true;
            } else {
                columnNamesCache.add(columnName + tableName);
                flag = true;
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.out.println("Column caused error:" + columnName);
        }


        return flag;
    }

    private void checkSchema(String tableSchema) {

        try {

            //make vertex table
            String tableName = "VERTEX";
            Statement stmt = sqlConnection.createStatement();
            String check = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='" + tableSchema + "' AND TABLE_NAME = '" + tableName + "'";

            ResultSet checkResult = stmt.executeQuery(check);

            if (checkResult.next() == false) {
                String vertexQuery = "CREATE TABLE " + tableName + " (VERTEXID INT PRIMARY KEY IDENTITY, VERTEXTYPE VARCHAR(50) NOT NULL, LINKEDVERTEXID INT NOT NULL)";
                boolean vertexResult = stmt.execute(vertexQuery);
            } else {
                //System.out.println("table already there");
            }

            //make process table
            tableName = "PROCESS";
            check = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='" + tableSchema + "' AND TABLE_NAME = '" + tableName + "'";
            checkResult = stmt.executeQuery(check);

            if (checkResult.next() == false) {
                String processQuery = "CREATE TABLE " + tableName + "(ID INT PRIMARY KEY IDENTITY)";
                boolean processResult = stmt.execute(processQuery);
            } else {
                //System.out.println("table already there");
            }

            //make agent table
            tableName = "AGENT";
            check = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='" + tableSchema + "' AND TABLE_NAME = '" + tableName + "'";
            checkResult = stmt.executeQuery(check);

            if (checkResult.next() == false) {
                String agentQuery = "CREATE TABLE " + tableName + "(ID INT PRIMARY KEY IDENTITY)";
                boolean agentResult = stmt.execute(agentQuery);
            }


            //make file table
            tableName = "ARTIFACT";
            check = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='" + tableSchema + "' AND TABLE_NAME = '" + tableName + "'";
            checkResult = stmt.executeQuery(check);

            if (checkResult.next() == false) {
                String fileQuery = "CREATE TABLE " + tableName + "(ID INT PRIMARY KEY IDENTITY)";
                boolean fileResult = stmt.execute(fileQuery);
            }


            //make edge table

            tableName = "EDGES";
            check = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='" + tableSchema + "' AND TABLE_NAME = '" + tableName + "'";
            checkResult = stmt.executeQuery(check);

            if (checkResult.next() == false) {
                String edgeQuery = "CREATE TABLE " + tableName + "(EDGEID INT PRIMARY KEY IDENTITY, SOURCEID INT NOT NULL, DESTINATIONID INT NOT NULL, EDGETYPE VARCHAR(50) NOT NULL, FOREIGN KEY (SOURCEID) REFERENCES VERTEX(VERTEXID),FOREIGN KEY (DESTINATIONID) REFERENCES VERTEX(VERTEXID))";
                boolean edgeResult = stmt.execute(edgeQuery);
            }


        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.out.println(e.getErrorCode());
        }






    }

    private void checkSchemaForAbstractVertex(AbstractVertex v) {
        String vertexType = v.getVertexType();
        if (vertexType.equalsIgnoreCase("Process")) {
            Set<String> cloumnNames = v.getAnnotations().keySet();
            Iterator<String> it = cloumnNames.iterator();

            while (it.hasNext()) {

                this.checkandAddColumn(it.next(), "PROCESS");

            }

        } else if (vertexType.equalsIgnoreCase("Agent")) {
            Set<String> cloumnNames = v.getAnnotations().keySet();
            Iterator<String> it = cloumnNames.iterator();

            while (it.hasNext()) {

                this.checkandAddColumn(it.next(), "AGENT");

            }

        } else if (vertexType.equalsIgnoreCase("Artifact")) {
            Set<String> cloumnNames = v.getAnnotations().keySet();
            Iterator<String> it = cloumnNames.iterator();

            while (it.hasNext()) {

                this.checkandAddColumn(it.next(), "ARTIFACT");

            }

        }
    }

    private void checkSchemaForAbstractEdge(AbstractEdge e) {



        Set<String> cloumnNames = e.getAnnotations().keySet();
        Iterator<String> it = cloumnNames.iterator();

        while (it.hasNext()) {

            this.checkandAddColumn(it.next(), "EDGES");

        }

        //this.checkandAddColumn("edgeType","AbstractEdges");



    }

    private String getAbstractVertexTable(AbstractVertex v) {
        String vertexType = v.getVertexType();
        String tableName = "";
        if (vertexType.equalsIgnoreCase("Process")) {
            tableName = "PROCESS";
        } else if (vertexType.equalsIgnoreCase("Artifact")) {
            tableName = "ARTIFACT";
        } else if (vertexType.equalsIgnoreCase("Agent")) {
            tableName = "AGENT";
        }

        return tableName;
    }

    // ============================================================================================
    //
    // BEGIN QUERY METHODS
    //
    // ============================================================================================
    @Override
    public Set<AbstractVertex> getVertices(String expression) {
        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        Set<AbstractVertex> processes = this.getVertices(expression, "Process");
        Set<AbstractVertex> agents = this.getVertices(expression, "Agent");
        Set<AbstractVertex> artifacts = this.getVertices(expression, "Artifact");

        if (processes != null) {
            Iterator<AbstractVertex> processVertices = processes.iterator();
            while (processVertices.hasNext()) {
                vertices.add(processVertices.next());
                //vertices.clear();
            }
        }
        if (agents != null) {

            Iterator<AbstractVertex> agentVertices = agents.iterator();
            while (agentVertices.hasNext()) {
                vertices.add(agentVertices.next());
                //agentVertices.next();
            }


        }
        if (artifacts != null) {

            Iterator<AbstractVertex> artifactVertices = artifacts.iterator();
            while (artifactVertices.hasNext()) {
                vertices.add(artifactVertices.next());
                //artifactVertices.next();
            }

        }

        //System.out.println(vertices.size());
        //System.out.println(processes.size()+agents.size()+artifacts.size());

        return vertices;
    }

    private Set<AbstractVertex> getVertices(String expression, String OPMObjectName) {

        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        //set the sql table name depedenent on the OPM object type
        String tableName = "";
        int vertexType = -1;
        if (OPMObjectName.equalsIgnoreCase("Process")) {
            tableName = "PROCESS";
            vertexType = 0;
        } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
            tableName = "ARTIFACT";
            vertexType = 1;
        } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
            tableName = "AGENT";
            vertexType = 2;
        } else {
            return null;
        }

        try {
            //first change the given boolean expression into SQL understandable form
            String processedExpression = expression;
            if (processedExpression == null) {
                return null;
            }
            //now make the query to the database
            Statement stmt = sqlConnection.createStatement();
            String query = "SELECT * FROM " + tableName + " WHERE " + processedExpression;
            ResultSet result = stmt.executeQuery(query);
            //get the column meta data for creating queries
            ResultSetMetaData columnData = result.getMetaData();
            //store the colums names in a set for they get be easily accessed
            Set<String> keySet = new HashSet<String>();

            for (int i = 1; i < columnData.getColumnCount(); i++) {
                String column = columnData.getColumnName(i);
                ;
                if (column.startsWith("C_")) {
                    keySet.add(column.substring(2));
                }
            }


            //process through all the rows of the result set and create the required vertices
            while (result.next()) {

                //depending on vertex type, create the appropriate vertex object objects, create its annotations, and add it to Set we will return
                if (vertexType == 0) {
                    Process p = new Process();
                    HashMap<String, String> annotations = new HashMap<String, String>();

                    //iterate over the column names and add their values to the annotations
                    Iterator<String> keyIterator = keySet.iterator();

                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        annotations.put(key, result.getString("C_" + key));
                    }

                    p.setAnnotations(annotations);
                    vertices.add(p);
                } //same for artifact as process above
                else if (vertexType == 1) {
                    Artifact a = new Artifact();
                    HashMap<String, String> annotations = new HashMap<String, String>();

                    //iterate over the column names and add their values to the annotations
                    Iterator<String> keyIterator = keySet.iterator();

                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        annotations.put(key, result.getString("C_" + key));
                    }
                    a.setAnnotations(annotations);
                    //System.out.println(annotations);
                    vertices.add(a);


                } //and again for agent
                else if (vertexType == 2) {

                    Agent ag = new Agent();
                    HashMap<String, String> annotations = new HashMap<String, String>();

                    //iterate over the column names and add their values to the annotations
                    Iterator<String> keyIterator = keySet.iterator();

                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        annotations.put(key, result.getString("C_" + key));
                    }
                    ag.setAnnotations(annotations);
                    vertices.add(ag);
                }

            }

        } catch (SQLException e) {
            //e.printStackTrace();
            return null;
        }

        return vertices;
    }

    @Override
    public Set<AbstractEdge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        Set<AbstractVertex> sourceSet = null;
        Set<AbstractVertex> destinationSet = null;
        if (sourceExpression != null) {
            sourceSet = getVertices(sourceExpression);
        }
        if (destinationExpression != null) {
            destinationSet = getVertices(destinationExpression);
        }

        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();
        edges.addAll(this.getEdgesOfType(edgeExpression, "Used"));
        edges.addAll(this.getEdgesOfType(edgeExpression, "WasControlledBy"));
        edges.addAll(this.getEdgesOfType(edgeExpression, "WasDerivedFrom"));
        edges.addAll(this.getEdgesOfType(edgeExpression, "WasGeneratedBy"));
        edges.addAll(this.getEdgesOfType(edgeExpression, "WasTriggeredBy"));

        Iterator iterator = edges.iterator();
        while (iterator.hasNext()) {
            AbstractEdge tempEdge = (AbstractEdge) iterator.next();
            if ((sourceExpression != null) && (destinationExpression != null)) {
                if ((sourceSet.contains(tempEdge.getSrcVertex()) && destinationSet.contains(tempEdge.getDstVertex())) == false) {
                    iterator.remove();
                }
            } else if ((sourceExpression != null) && (destinationExpression == null)) {
                if (sourceSet.contains(tempEdge.getSrcVertex()) == false) {
                    iterator.remove();
                }
            } else if ((sourceExpression == null) && (destinationExpression != null)) {
                if (destinationSet.contains(tempEdge.getDstVertex()) == false) {
                    iterator.remove();
                }
            }
        }

        return edges;
    }

    //this function gets edges of a particular type. this is made seperately to hide the messy details of the SQL tables
    private Set<AbstractEdge> getEdgesOfType(String expression, String OPMObjectType) {

        //create a set to hold the result
        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();
        //convert user given expression into SQL understandable form
        String processedExpression = expression;
        if (processedExpression == null) {
            System.out.println("I messed up:1");
            return null;
        }
        //get the two tables that need to be read for this particular edge type
        String sourceTable;
        String destinationTable;

        //get the two tables
        if (OPMObjectType.equalsIgnoreCase("Used")) {
            sourceTable = "PROCESS";
            destinationTable = "ARTIFACT";
        } else if (OPMObjectType.equalsIgnoreCase("WasControlledBy")) {
            sourceTable = "PROCESS";
            destinationTable = "AGENT";
        } else if (OPMObjectType.equalsIgnoreCase("WasDerivedFrom")) {
            sourceTable = "ARTIFACT";
            destinationTable = "ARTIFACT";
        } else if (OPMObjectType.equalsIgnoreCase("WasGeneratedBy")) {
            sourceTable = "ARTIFACT";
            destinationTable = "PROCESS";
        } else if (OPMObjectType.equalsIgnoreCase("WasTriggeredBy")) {
            sourceTable = "PROCESS";
            destinationTable = "PROCESS";
        } else {
            System.out.println("I messed up:2");
            return null;
        }

        //get edges(1 query). for each edge get the two vertex ids. for both vertex ids get the linked vertex ids(2 queries) and hence exact vertex info (2 queries). 5 queries
        try {

            Statement stmt = sqlConnection.createStatement();
            String edgeQuery = "SELECT * FROM EDGES WHERE " + processedExpression + " AND EDGETYPE='" + OPMObjectType + "'";
            ResultSet edgeQueryResult = stmt.executeQuery(edgeQuery);//(Query#1)


            ResultSetMetaData columnData = edgeQueryResult.getMetaData();
            //store the colums names in a set for they get be easily accessed
            Set<String> keySet = new HashSet<String>();

            for (int i = 1; i < columnData.getColumnCount(); i++) {
                String column = columnData.getColumnName(i);
                if (column.startsWith("C_")) {
                    keySet.add(column.substring(2));
                }
            }

            //now get two vertex ids
            int sourceVID;
            int destinationVID;
            int linkedSourceVID;
            int linkedDestinationVID;

            while (edgeQueryResult.next()) {
                sourceVID = edgeQueryResult.getInt("SOURCEID");
                destinationVID = edgeQueryResult.getInt("DESTINATIONID");


                //make the annotations for the edge
                HashMap<String, String> edgeAnnotations = new HashMap<String, String>();

                //iterate over the column names and add their values to the edge annotations
                Iterator<String> keyIterator = keySet.iterator();

                while (keyIterator.hasNext()) {
                    String key = keyIterator.next();
                    edgeAnnotations.put(key, edgeQueryResult.getString(key));
                }

                //now the edgeAnnotations has the data for the edge

                //now get the linked vertex IDs for both these vertices
                Statement stmt2 = sqlConnection.createStatement();
                String sourceVertexQuery = "SELECT LINKEDVERTEXID FROM VERTEX WHERE VERTEXID=" + sourceVID;
                ResultSet sourceVertexQueryResult = stmt2.executeQuery(sourceVertexQuery);
                if (sourceVertexQueryResult.next()) {
                    linkedSourceVID = sourceVertexQueryResult.getInt("LINKEDVERTEXID");
                } else {
                    System.out.println("I messed up:3");
                    return null;
                }




                Statement stmt3 = sqlConnection.createStatement();
                String destinationVertexQuery = "SELECT LINKEDVERTEXID FROM VERTEX WHERE VERTEXID=" + destinationVID;
                ResultSet destinationVertexQueryResult = stmt3.executeQuery(destinationVertexQuery);
                if (destinationVertexQueryResult.next()) {
                    linkedDestinationVID = destinationVertexQueryResult.getInt("LINKEDVERTEXID");
                } else {
                    System.out.println("I messed up:4");
                    return null;
                }

                //now we have ids for both vertices as well as their table. now to look them up.

                //make the source vertex
                HashMap<String, String> sourceAnnotations = new HashMap<String, String>();
                Statement stmt4 = sqlConnection.createStatement();
                String sourceQuery = "SELECT * FROM " + sourceTable + " WHERE ID=" + linkedSourceVID;

                Set<AbstractVertex> sourceVertexSet = this.getVertices("ID=" + linkedSourceVID, sourceTable);
                Set<AbstractVertex> destinationVertexSet = this.getVertices("ID=" + linkedDestinationVID, destinationTable);

                if (!sourceVertexSet.isEmpty() && !destinationVertexSet.isEmpty()) {

                    //now make the final edge to be returned

                    AbstractEdge u = null;
                    if (OPMObjectType.equalsIgnoreCase("Used")) {
                        u = new Used((Process) sourceVertexSet.iterator().next(), (Artifact) destinationVertexSet.iterator().next());
                    } else if (OPMObjectType.equalsIgnoreCase("WasControlledBy")) {
                        u = new WasControlledBy((Process) sourceVertexSet.iterator().next(), (Agent) destinationVertexSet.iterator().next());
                    } else if (OPMObjectType.equalsIgnoreCase("WasDerivedFrom")) {
                        u = new WasDerivedFrom((Artifact) sourceVertexSet.iterator().next(), (Artifact) destinationVertexSet.iterator().next());
                    } else if (OPMObjectType.equalsIgnoreCase("WasGeneratedBy")) {
                        u = new WasGeneratedBy((Artifact) sourceVertexSet.iterator().next(), (Process) destinationVertexSet.iterator().next());
                    } else if (OPMObjectType.equalsIgnoreCase("WasTriggeredBy")) {
                        u = new WasTriggeredBy((Process) sourceVertexSet.iterator().next(), (Process) destinationVertexSet.iterator().next());
                    } else {
                        System.out.println("I messed up:5");
                        return null;
                    }

                    if (u != null) {
                        u.setAnnotations(edgeAnnotations);
                        edges.add(u);

                    }

                }
            }

            return edges;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public Set<String> getKeySet(String OPMObjectName) {
        String tableName = "";
        if (OPMObjectName.equalsIgnoreCase("Process")) {
            tableName = "PROCESS";
        } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
            tableName = "ARTIFACT";
        } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
            tableName = "AGENT";
        } else {
            tableName = "EDGES";
        }



        Set<String> keySet = new HashSet<String>();
        try {
            Statement stmt = sqlConnection.createStatement();
            String query = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "'";
            ResultSet result = stmt.executeQuery(query);

            while (result.next()) {
                String column = result.getString("COLUMN_NAME");
                if (column.startsWith("C_")) {
                    keySet.add(column.substring(2));
                } else if (column.equals("EDGETYPE")) {
                    keySet.add(column);
                }


            }



        } catch (SQLException e) {
            e.printStackTrace();
        }



        return keySet;
    }

    public Lineage getLineage(AbstractVertex source, String pruneExpression, int direction, boolean includeTerminatingNodes) {

        DefaultDirectedGraph<Integer, String> lineageDAG = this.buildLineage(source, pruneExpression, direction, includeTerminatingNodes);
        //System.out.println(lineageDAG);

        //now we have the graph in terms of vertex Ids. Build an identical graph with real information from the database
        int sourceId = Integer.parseInt(source.getAnnotation("storageId"));
        source.getAnnotations().remove("storageId");

        //this is the graph that we will put into the lineage object
        Lineage lineageDAGWithInfo = new Lineage();

        //make hash maps to hold the vertices
        HashMap<Integer, Process> processes = new HashMap<Integer, Process>();
        HashMap<Integer, Agent> agents = new HashMap<Integer, Agent>();
        HashMap<Integer, Artifact> artifacts = new HashMap<Integer, Artifact>();

        //now get all the vertex ids from the DAG and fill up this hash map.

        Iterator<Integer> vertexIdIterator = lineageDAG.vertexSet().iterator();

        //System.out.println("Real size:"+lineageDAG.vertexSet().size());

        while (vertexIdIterator.hasNext()) {
            int currentVertexId = vertexIdIterator.next();
            int currentLinkedId = -1;
            int vertexType = -1;
            String OPMObjectName = "";
            String tableName = "";
            try {
                //make a query to vertex table to get linkedvert			System.out.println(result3.getString("EDGETYPE"));

                Statement stmt = sqlConnection.createStatement();
                String query = "SELECT LINKEDVERTEXID, VERTEXTYPE FROM VERTEX WHERE VERTEXID=" + currentVertexId;
                ResultSet result = stmt.executeQuery(query);
                if (result.next()) {
                    currentLinkedId = result.getInt("LINKEDVERTEXID");
                    OPMObjectName = result.getString("VERTEXTYPE");
                }


                //get the table name
                if (OPMObjectName.equalsIgnoreCase("Process")) {
                    tableName = "PROCESS";
                    vertexType = 0;
                } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
                    tableName = "ARTIFACT";
                    vertexType = 1;
                } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
                    tableName = "AGENT";
                    vertexType = 2;
                }


                //get the key set for the table
                Set<String> vertexKeySet = this.getKeySet(OPMObjectName);

                //make a query to the vertex-type table

                Statement stmt2 = sqlConnection.createStatement();
                String query2 = "SELECT * FROM " + tableName + " WHERE ID= " + currentLinkedId;
                ResultSet result2 = stmt2.executeQuery(query2);


                //get and add the info about the vertex to annotations hashmap
                HashMap<String, String> annotations = new HashMap<String, String>();
                if (result2.next()) {

                    Iterator<String> keyIterator = vertexKeySet.iterator();

                    while (keyIterator.hasNext()) {

                        String key = keyIterator.next();
                        annotations.put(key, result2.getString("C_" + key.toUpperCase()));

                    }



                }

                //make the appropriate vertex and put it into appropriate hash map


                if (vertexType == 0) {
                    processes.put(currentVertexId, new Process(annotations));
                }
                if (vertexType == 1) {
                    artifacts.put(currentVertexId, new Artifact(annotations));
                } else if (vertexType == 2) {
                    agents.put(currentVertexId, new Agent(annotations));
                }



            } catch (SQLException e) {
            }
        }

        //now we have all the vertices. Add all of them to the graph

        //add the processes
        Iterator<Integer> processIterator = processes.keySet().iterator();
        while (processIterator.hasNext()) {
            //add each vertex object to the graph we are going to return
            lineageDAGWithInfo.putVertex(processes.get(processIterator.next()));

        }

        //add the agents
        Iterator<Integer> agentIterator = agents.keySet().iterator();
        while (agentIterator.hasNext()) {
            //add each vertex object to the graph we are going to return
            lineageDAGWithInfo.putVertex(agents.get(agentIterator.next()));

        }

        //add the artifacts
        Iterator<Integer> artifactIterator = artifacts.keySet().iterator();
        while (artifactIterator.hasNext()) {
            //add each vertex object to the graph we are going to return
            lineageDAGWithInfo.putVertex(artifacts.get(artifactIterator.next()));

        }

        //now our graph has all the vertices. now to get each edge and add it to the graph

        //get the edge set iterator
        Iterator<String> edgesIterator = lineageDAG.edgeSet().iterator();
        //System.out.println("Real Vertex Size:"+lineageDAG.vertexSet().size());
        //System.out.println("Real Edge Size:"+lineageDAG.edgeSet().size());

        //get the keyset for the edges table
        Set<String> edgeKeySet = this.getKeySet("edges");

        while (edgesIterator.hasNext()) {
            int currentSourceId = -1;
            int currentDestinationId = -1;
            //get the current source and destination from the current Edge
            String currentEdge = edgesIterator.next();
            currentSourceId = (Integer) lineageDAG.getEdgeSource(currentEdge);
            currentDestinationId = (Integer) lineageDAG.getEdgeTarget(currentEdge);

            HashMap<String, String> currentEdgeAnnotations = new HashMap<String, String>();


            try {
                //query to get the annotations for the edge
                Statement stmt3 = sqlConnection.createStatement();
                String query3 = "SELECT * FROM EDGES WHERE SOURCEID=" + currentSourceId + " AND DESTINATIONID=" + currentDestinationId;
                ResultSet result3 = stmt3.executeQuery(query3);
                String edgeType = "";

                if (result3.next()) {
                    //get the annotations for the edge here using the keyset
                    Iterator<String> edgeKeySetIterator = edgeKeySet.iterator();
                    while (edgeKeySetIterator.hasNext()) {
                        String key = edgeKeySetIterator.next();
                        if (key.equals("EDGETYPE")) {
                            edgeType = result3.getString("EDGETYPE");
                        } else {
                            currentEdgeAnnotations.put(key, result3.getString("C_" + key.toUpperCase()));
                        }


                    }
                }
                boolean result;
                //now we have the edgetype and annotations. time to make the edge itself depending on its type
                if (edgeType.equalsIgnoreCase("Used")) {
                    Used u = new Used(processes.get(currentSourceId), artifacts.get(currentDestinationId));
                    u.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(u);
                } else if (edgeType.equalsIgnoreCase("WasControlledBy")) {
                    WasControlledBy wcb = new WasControlledBy(processes.get(currentSourceId), agents.get(currentDestinationId));
                    wcb.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wcb);
                } else if (edgeType.equalsIgnoreCase("WasDerivedFrom")) {
                    WasDerivedFrom wdf = new WasDerivedFrom(artifacts.get(currentSourceId), artifacts.get(currentDestinationId));
                    wdf.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wdf);
                } else if (edgeType.equalsIgnoreCase("WasGeneratedBy")) {
                    WasGeneratedBy wgb = new WasGeneratedBy(artifacts.get(currentSourceId), processes.get(currentDestinationId));
                    wgb.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wgb);
                } else if (edgeType.equalsIgnoreCase("WasTriggeredBy")) {
                    WasTriggeredBy wtb = new WasTriggeredBy(processes.get(currentSourceId), processes.get(currentDestinationId));
                    wtb.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wtb);
                } else {
                    result = true;
                }

                //we have put the edges into the graph.




            } catch (SQLException e) {
            }
        }

        //now all edges along with their info have been put in.

        return lineageDAGWithInfo;

    }

    public Lineage getLineage(AbstractVertex source, int depth, int direction) {
        // TODO Auto-generated method stub


        DefaultDirectedGraph<Integer, String> lineageDAG = this.buildLineage(source, depth, direction);
        //System.out.println(lineageDAG);

        //now we have the graph in terms of vertex Ids. Build an identical graph with real information from the database
        int sourceId = Integer.parseInt(source.getAnnotation("storageId"));
        source.getAnnotations().remove("storageId");

        //this is the graph that we will put into the lineage object
        Lineage lineageDAGWithInfo = new Lineage();

        //make hash maps to hold the vertices
        HashMap<Integer, Process> processes = new HashMap<Integer, Process>();
        HashMap<Integer, Agent> agents = new HashMap<Integer, Agent>();
        HashMap<Integer, Artifact> artifacts = new HashMap<Integer, Artifact>();

        //now get all the vertex ids from the DAG and fill up this hash map.

        Iterator<Integer> vertexIdIterator = lineageDAG.vertexSet().iterator();

        //System.out.println("Real size:"+lineageDAG.vertexSet().size());

        while (vertexIdIterator.hasNext()) {
            int currentVertexId = vertexIdIterator.next();
            int currentLinkedId = -1;
            int vertexType = -1;
            String OPMObjectName = "";
            String tableName = "";
            try {
                //make a query to vertex table to get linkedvert			System.out.println(result3.getString("EDGETYPE"));

                Statement stmt = sqlConnection.createStatement();
                String query = "SELECT LINKEDVERTEXID, VERTEXTYPE FROM VERTEX WHERE VERTEXID=" + currentVertexId;
                ResultSet result = stmt.executeQuery(query);
                if (result.next()) {
                    currentLinkedId = result.getInt("LINKEDVERTEXID");
                    OPMObjectName = result.getString("VERTEXTYPE");
                }


                //get the table name
                if (OPMObjectName.equalsIgnoreCase("Process")) {
                    tableName = "PROCESS";
                    vertexType = 0;
                } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
                    tableName = "ARTIFACT";
                    vertexType = 1;
                } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
                    tableName = "AGENT";
                    vertexType = 2;
                }


                //get the key set for the table
                Set<String> vertexKeySet = this.getKeySet(OPMObjectName);

                //make a query to the vertex-type table

                Statement stmt2 = sqlConnection.createStatement();
                String query2 = "SELECT * FROM " + tableName + " WHERE ID = " + currentLinkedId;
                ResultSet result2 = stmt2.executeQuery(query2);


                //get and add the info about the vertex to annotations hashmap
                HashMap<String, String> annotations = new HashMap<String, String>();
                if (result2.next()) {

                    Iterator<String> keyIterator = vertexKeySet.iterator();

                    while (keyIterator.hasNext()) {

                        String key = keyIterator.next();
                        annotations.put(key, result2.getString("C_" + key.toUpperCase()));

                    }



                }

                //make the appropriate vertex and put it into appropriate hash map


                if (vertexType == 0) {
                    processes.put(currentVertexId, new Process(annotations));
                }
                if (vertexType == 1) {
                    artifacts.put(currentVertexId, new Artifact(annotations));
                } else if (vertexType == 2) {
                    agents.put(currentVertexId, new Agent(annotations));
                }



            } catch (SQLException e) {
            }
        }

        //now we have all the vertices. Add all of them to the graph

        //add the processes
        Iterator<Integer> processIterator = processes.keySet().iterator();
        while (processIterator.hasNext()) {
            //add each vertex object to the graph we are going to return
            lineageDAGWithInfo.putVertex(processes.get(processIterator.next()));

        }

        //add the agents
        Iterator<Integer> agentIterator = agents.keySet().iterator();
        while (agentIterator.hasNext()) {
            //add each vertex object to the graph we are going to return
            lineageDAGWithInfo.putVertex(agents.get(agentIterator.next()));

        }

        //add the artifacts
        Iterator<Integer> artifactIterator = artifacts.keySet().iterator();
        while (artifactIterator.hasNext()) {
            //add each vertex object to the graph we are going to return
            lineageDAGWithInfo.putVertex(artifacts.get(artifactIterator.next()));

        }

        //now our graph has all the vertices. now to get each edge and add it to the graph





        //get the edge set iterator
        Iterator<String> edgesIterator = lineageDAG.edgeSet().iterator();
        //System.out.println("Real Vertex Size:"+lineageDAG.vertexSet().size());
        //System.out.println("Real Edge Size:"+lineageDAG.edgeSet().size());

        //get the keyset for the edges table
        Set<String> edgeKeySet = this.getKeySet("edges");

        while (edgesIterator.hasNext()) {
            int currentSourceId = -1;
            int currentDestinationId = -1;
            //get the current source and destination from the current Edge
            String currentEdge = edgesIterator.next();
            currentSourceId = (Integer) lineageDAG.getEdgeSource(currentEdge);
            currentDestinationId = (Integer) lineageDAG.getEdgeTarget(currentEdge);

            HashMap<String, String> currentEdgeAnnotations = new HashMap<String, String>();


            try {
                //query to get the annotations for the edge
                Statement stmt3 = sqlConnection.createStatement();
                String query3 = "SELECT * FROM EDGES WHERE SOURCEID=" + currentSourceId + " AND DESTINATIONID=" + currentDestinationId;
                ResultSet result3 = stmt3.executeQuery(query3);
                String edgeType = "";

                if (result3.next()) {
                    //get the annotations for the edge here using the keyset
                    Iterator<String> edgeKeySetIterator = edgeKeySet.iterator();
                    while (edgeKeySetIterator.hasNext()) {
                        String key = edgeKeySetIterator.next();
                        if (key.equals("EDGETYPE")) {
                            edgeType = result3.getString("EDGETYPE");
                        } else {
                            currentEdgeAnnotations.put(key, result3.getString("C_" + key.toUpperCase()));
                        }


                    }
                }
                boolean result;
                //now we have the edgetype and annotations. time to make the edge itself depending on its type
                if (edgeType.equalsIgnoreCase("Used")) {
                    Used u = new Used(processes.get(currentSourceId), artifacts.get(currentDestinationId));
                    u.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(u);
                } else if (edgeType.equalsIgnoreCase("WasControlledBy")) {
                    WasControlledBy wcb = new WasControlledBy(processes.get(currentSourceId), agents.get(currentDestinationId));
                    wcb.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wcb);
                } else if (edgeType.equalsIgnoreCase("WasDerivedFrom")) {
                    WasDerivedFrom wdf = new WasDerivedFrom(artifacts.get(currentSourceId), artifacts.get(currentDestinationId));
                    wdf.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wdf);
                } else if (edgeType.equalsIgnoreCase("WasGeneratedBy")) {
                    WasGeneratedBy wgb = new WasGeneratedBy(artifacts.get(currentSourceId), processes.get(currentDestinationId));
                    wgb.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wgb);
                } else if (edgeType.equalsIgnoreCase("WasTriggeredBy")) {
                    WasTriggeredBy wtb = new WasTriggeredBy(processes.get(currentSourceId), processes.get(currentDestinationId));
                    wtb.setAnnotations(currentEdgeAnnotations);
                    result = lineageDAGWithInfo.putEdge(wtb);
                } else {
                    result = true;
                }

                //we have put the edges into the graph.




            } catch (SQLException e) {
            }
        }

        //now all edges along with their info have been put in.

        //now to just build a lineage object and return it!
        return lineageDAGWithInfo;
    }

    //function to convert user given expression into an expression SQL can understand
    private String processExpression(String inputExpression) {
        String processedExpression = inputExpression;

        //do processing on expression here


        return processedExpression;
    }

    private DefaultDirectedGraph<Integer, String> buildLineage(AbstractVertex source, int depth, int direction) {
        //0 is ancestors
        //1 is decsendents

        //check if parameters are correct
        if (depth < 0 || direction < 0 || direction > 1) {
            return null;
        }

        Queue<Integer> vertexQueue = new LinkedList<Integer>();
        DefaultDirectedGraph<Integer, String> lineageDAG = new DefaultDirectedGraph<Integer, String>(String.class);
        //get ID for first vertex
        int vertexId = -1;
        String OPMObjectName = source.getVertexType();
        String tableName = "";

        if (OPMObjectName.equalsIgnoreCase("Process")) {
            tableName = "PROCESS";
        } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
            tableName = "ARTIFACT";
        } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
            tableName = "AGENT";
        } else {
            return null;
        }

        String constraints = "";



        Iterator<String> annotationsKeysIterator = source.getAnnotations().keySet().iterator();
        while (annotationsKeysIterator.hasNext()) {
            String key = annotationsKeysIterator.next();
            String column = "C_" + key;
            String value = source.getAnnotation(key);
            if (value == null) {
                constraints = constraints + " AND " + column + " IS NULL";
            } else {
                constraints = constraints + " AND " + column + "='" + value + "'";
            }
        }
        if (constraints.length() > 3) {
            constraints = constraints.substring(4);
        } else {
            constraints = "false";
        }
        try {

            //System.out.println(constraints);
            //get the vertex id for the vertex
            Statement stmt = sqlConnection.createStatement();

            String query = "SELECT ID FROM " + tableName + " WHERE " + constraints;

            ResultSet result = stmt.executeQuery(query);
            vertexId = -1;
            int Id = -1;

            //System.out.println(constraints);
            if (result.next()) {
                Id = result.getInt("ID");
                //System.out.println(constraints);

            } else {
                //no such vertex exists in the database
                return null;
            }


            Statement stmt2 = sqlConnection.createStatement();

            String query2 = "SELECT * FROM VERTEX WHERE LINKEDVERTEXID=" + Id + " AND VERTEXTYPE='" + source.getVertexType() + "'";

            ResultSet result2 = stmt.executeQuery(query2);

            if (result2.next()) {
                vertexId = result2.getInt("VERTEXID");
                //System.out.println(vertexId+" link: "+result2.getInt("linkedVertexId")+" type: "+result2.getString("vertexType"));
                //System.out.println(vertexId+"type "+result2.getString("vertexType"));
                source.addAnnotation("storageId", new Integer(vertexId).toString());
            } else {
                //no such vertex exists in the database
                return null;
            }






        } catch (SQLException e) {
            e.printStackTrace();
        }
        //now we have the vertex Id for the source so this is a starting point. now we have to build Lineage DAG

        //start by populating root of lineageDAG and first level of vertexQueue
        vertexQueue.add(vertexId);
        lineageDAG.addVertex(vertexId);

        //now build the rest of the tree iteratively

        for (int i = 0; i < depth; i++) {
            //System.out.println(vertexQueue);

            Queue<Integer> nextLevel = new LinkedList<Integer>();

            //for each vertex in the queue we have to get edges and next vertices to fillId the queue
            Iterator<Integer> levelIterator = vertexQueue.iterator();

            //iterate over all vertices
            while (levelIterator.hasNext()) {
                //get current vertex
                Integer currentVertex = levelIterator.next();

                //get the vertices it is connected to. this is the set to hold that
                Set<Integer> currentVertexNeighbours = new HashSet<Integer>();

                //now for SQL Magic
                try {
                    //get all neighbours
                    if (direction == 0) {
                        Statement stmt2 = sqlConnection.createStatement();
                        String query = "SELECT DESTINATIONID FROM EDGES WHERE SOURCEID=" + currentVertex;
                        ResultSet neighbourRows = stmt2.executeQuery(query);

                        while (neighbourRows.next()) {
                            currentVertexNeighbours.add(neighbourRows.getInt("DESTINATIONID"));
                        }
                    } else {
                        Statement stmt2 = sqlConnection.createStatement();
                        String query = "SELECT SOURCEID FROM EDGES WHERE DESTINATIONID=" + currentVertex;
                        ResultSet neighbourRows = stmt2.executeQuery(query);

                        while (neighbourRows.next()) {
                            currentVertexNeighbours.add(neighbourRows.getInt("SOURCEID"));
                        }


                    }

                    //end of SQL magic
                } catch (SQLException e) {
                }

                //now we have all the current vertex neighbours

                //add these to next level queue(if not there already) and to lineage dag(if not there already) also put edge between current vertex and these vertices.

                Iterator<Integer> neighbourIterator = currentVertexNeighbours.iterator();
                while (neighbourIterator.hasNext()) {
                    Integer neighbour = neighbourIterator.next();
                    if (!nextLevel.contains(neighbour)) {
                        nextLevel.add(neighbour);
                    }
                    if (!lineageDAG.containsVertex(neighbour)) {
                        lineageDAG.addVertex(neighbour);
                    }
                    if (direction == 0) {
                        lineageDAG.addEdge(currentVertex, neighbour, currentVertex + "," + neighbour);
                    } else {
                        lineageDAG.addEdge(neighbour, currentVertex, neighbour + "," + currentVertex);

                    }
                }
                //all neighbours of current vertex has been added to next level queue and the lineage tree with edges.


                //work with current vertex is now finished

            }

            //current level has been exhausted. clear the vertexQueue and copy nextLevel list into it

            vertexQueue.clear();
            vertexQueue.addAll(nextLevel);

            if (vertexQueue.isEmpty()) {
                return lineageDAG;
            }

        }



        return lineageDAG;

    }

    private String processPruneExpression(String inputExpression) {

        //implement this later

        return inputExpression;
    }

    private Set<Integer> getVertexHandles(String pruneExpression) {
        Set<Integer> vertexHandles = new HashSet<Integer>();

        Set<Integer> processes = this.getVertexHandles(pruneExpression, "Process");
        Set<Integer> agents = this.getVertexHandles(pruneExpression, "Agent");
        Set<Integer> artifacts = this.getVertexHandles(pruneExpression, "Artifact");

        if (processes != null) {
            Iterator<Integer> processVertices = processes.iterator();
            while (processVertices.hasNext()) {
                vertexHandles.add(processVertices.next());
            }
        }
        if (agents != null) {

            Iterator<Integer> agentVertices = agents.iterator();
            while (agentVertices.hasNext()) {
                vertexHandles.add(agentVertices.next());
            }


        }
        if (artifacts != null) {

            Iterator<Integer> artifactVertices = artifacts.iterator();
            while (artifactVertices.hasNext()) {
                vertexHandles.add(artifactVertices.next());
            }

        }


        return vertexHandles;

    }

    private Set<Integer> getVertexHandles(String pruneExpression, String OPMObjectName) {
        Set<Integer> vertexHandles = new HashSet<Integer>();

        String processedPruneExpression = this.processPruneExpression(pruneExpression);

        String tableName = "";
        int vertexType = -1;
        if (OPMObjectName.equalsIgnoreCase("Process")) {
            tableName = "PROCESS";
            vertexType = 0;
        } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
            tableName = "ARTIFACT";
            vertexType = 1;
        } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
            tableName = "AGENT";
            vertexType = 2;
        } else {
            return null;
        }

        try {
            if (processedPruneExpression == null) {
                return null;
            }
            //now make the query to the database
            Statement stmt = sqlConnection.createStatement();
            String query = "SELECT ID FROM " + tableName + " WHERE " + processedPruneExpression;
            ResultSet result = stmt.executeQuery(query);

            //process through all the rows of the result set and create the required vertices
            while (result.next()) {

                int currentLinkedId = result.getInt("Id");

                Statement stmt2 = sqlConnection.createStatement();
                String query2 = "SELECT VERTEXID FROM VERTEX WHERE LINKEDVERTEXID=" + currentLinkedId + " AND VERTEXTYPE ='" + OPMObjectName + "'";
                ResultSet result2 = stmt2.executeQuery(query2);

                if (result2.next()) {
                    int vertexId = result2.getInt("VERTEXID");
                    vertexHandles.add(vertexId);
                }


            }

        } catch (SQLException e) {
            //e.printStackTrace();
            return null;
        }


        return vertexHandles;
    }

    private DefaultDirectedGraph<Integer, String> buildLineage(AbstractVertex source, String pruneExpression, int direction, boolean includeTerminatingNodes) {
        //0 is ancestors
        //1 is decsendents
        int depth = Integer.MAX_VALUE;
        //check if parameters are correct
        if (depth < 0 || direction < 0 || direction > 1) {
            return null;
        }

        Set<Integer> terminatingNodes = this.getVertexHandles(pruneExpression);

        //System.out.println(terminatingNodes);

        Queue<Integer> vertexQueue = new LinkedList<Integer>();
        DefaultDirectedGraph<Integer, String> lineageDAG = new DefaultDirectedGraph<Integer, String>(String.class);
        //get ID for first vertex
        int vertexId = -1;
        String OPMObjectName = source.getVertexType();
        String tableName = "";

        if (OPMObjectName.equalsIgnoreCase("Process")) {
            tableName = "PROCESS";
        } else if (OPMObjectName.equalsIgnoreCase("Artifact")) {
            tableName = "ARTIFACT";
        } else if (OPMObjectName.equalsIgnoreCase("Agent")) {
            tableName = "AGENT";
        } else {
            return null;
        }

        String constraints = "";



        Iterator<String> annotationsKeysIterator = source.getAnnotations().keySet().iterator();
        while (annotationsKeysIterator.hasNext()) {
            String key = annotationsKeysIterator.next();
            String column = "C_" + key;
            String value = source.getAnnotation(key);
            if (value == null) {
                constraints = constraints + " AND " + column + " IS NULL";
            } else {
                constraints = constraints + " AND " + column + "='" + value + "'";
            }
        }
        if (constraints.length() > 3) {
            constraints = constraints.substring(4);
        } else {
            constraints = "false";
        }
        try {

            //System.out.println(constraints);
            //get the vertex id for the vertex
            Statement stmt = sqlConnection.createStatement();

            String query = "SELECT ID FROM " + tableName + " WHERE " + constraints;

            ResultSet result = stmt.executeQuery(query);
            vertexId = -1;
            int Id = -1;

            //System.out.println(constraints);
            if (result.next()) {
                Id = result.getInt("ID");
                //System.out.println(constraints);

            } else {
                //no such vertex exists in the database
                return null;
            }


            Statement stmt2 = sqlConnection.createStatement();

            String query2 = "SELECT * FROM VERTEX WHERE LINKEDVERTEXID=" + Id + " AND VERTEXTYPE='" + source.getVertexType() + "'";

            ResultSet result2 = stmt.executeQuery(query2);

            if (result2.next()) {
                vertexId = result2.getInt("VERTEXID");
                //System.out.println(vertexId+" link: "+result2.getInt("linkedVertexId")+" type: "+result2.getString("vertexType"));
                //System.out.println(vertexId+"type "+result2.getString("vertexType"));
                source.addAnnotation("storageId", new Integer(vertexId).toString());
            } else {
                //no such vertex exists in the database
                return null;
            }






        } catch (SQLException e) {
            e.printStackTrace();
        }
        //now we have the vertex Id for the source so this is a starting point. now we have to build Lineage DAG

        //start by populating root of lineageDAG and first level of vertexQueue
        vertexQueue.add(vertexId);
        lineageDAG.addVertex(vertexId);

        //now build the rest of the tree iteratively

        for (int i = 0; i < depth; i++) {
            //System.out.println(vertexQueue);

            Queue<Integer> nextLevel = new LinkedList<Integer>();

            //for each vertex in the queue we have to get edges and next vertices to fillId the queue
            Iterator<Integer> levelIterator = vertexQueue.iterator();

            //iterate over all vertices
            while (levelIterator.hasNext()) {
                //get current vertex
                Integer currentVertex = levelIterator.next();

                //get the vertices it is connected to. this is the set to hold that
                Set<Integer> currentVertexNeighbours = new HashSet<Integer>();

                //now for SQL Magic
                try {
                    //get all neighbours
                    if (direction == 0) {
                        Statement stmt2 = sqlConnection.createStatement();
                        String query = "SELECT DESTINATIONID FROM EDGES WHERE SOURCEID=" + currentVertex;
                        ResultSet neighbourRows = stmt2.executeQuery(query);

                        while (neighbourRows.next()) {
                            currentVertexNeighbours.add(neighbourRows.getInt("DESTINATIONID"));
                        }
                    } else {
                        Statement stmt2 = sqlConnection.createStatement();
                        String query = "SELECT SOURCEID FROM EDGES WHERE DESTINATIONID=" + currentVertex;
                        ResultSet neighbourRows = stmt2.executeQuery(query);

                        while (neighbourRows.next()) {
                            currentVertexNeighbours.add(neighbourRows.getInt("SOURCEID"));
                        }


                    }

                    //end of SQL magic
                } catch (SQLException e) {
                }



                //add these to next level queue(if not there already) and to lineage dag(if not there already) also put edge between current vertex and these vertices.

                Iterator<Integer> neighbourIterator = currentVertexNeighbours.iterator();
                while (neighbourIterator.hasNext()) {
                    Integer neighbour = neighbourIterator.next();

                    //check if the neighbour is a vertex that needs to be pruned
                    boolean pruneNeighbour = false;
                    if (terminatingNodes.contains(neighbour)) {
                        pruneNeighbour = true;
                    }
                    boolean isInGraphAlready = lineageDAG.containsVertex(neighbour);
                    boolean isInNextLevel = nextLevel.contains(neighbour);






                    if (!pruneNeighbour) {
                        if (!isInGraphAlready) {
                            lineageDAG.addVertex(neighbour);
                        }
                        if (isInNextLevel) {
                            nextLevel.add(neighbour);
                        }

                        if (direction == 0) {
                            lineageDAG.addEdge(currentVertex, neighbour, currentVertex + "," + neighbour);
                        } else {
                            lineageDAG.addEdge(neighbour, currentVertex, neighbour + "," + currentVertex);
                        }


                    } else if (pruneNeighbour && includeTerminatingNodes) {
                        if (!isInGraphAlready) {
                            lineageDAG.addVertex(neighbour);
                        }

                        if (direction == 0) {
                            lineageDAG.addEdge(currentVertex, neighbour, currentVertex + "," + neighbour);
                        } else {
                            lineageDAG.addEdge(neighbour, currentVertex, neighbour + "," + currentVertex);
                        }

                    }


                }
                //all neighbours of current vertex has been added to vertex queue and the lineage tree with edges.

                //work with current vertex is now finished

            }

            vertexQueue.clear();
            //System.out.println("next:"+nextLevel);
            vertexQueue.addAll(nextLevel);
            if (vertexQueue.isEmpty()) {
                return lineageDAG;
            }

        }



        return lineageDAG;

    }
}
