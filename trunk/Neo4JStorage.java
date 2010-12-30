/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2010 SRI International

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

import java.io.*;
import java.util.*;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.remote.transports.LocalGraphDatabase;
import org.neo4j.remote.transports.RmiTransport;

public class Neo4JStorage implements StorageInterface {

    private final boolean ENABLE_DOT_OUTPUT = true;
    private final boolean ENABLE_JSON_OUTPUT = false;
    private GraphDatabaseService graphDb;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction tx;
    private int txcount;
    private HashMap<Vertex, Long> VertexTable;
    private HashSet<String> EdgeSet;
    private final String remote_uri = "rmi://localhost/neo4jdb";

    public enum MyRelationshipTypes implements RelationshipType {

        EDGE
    }

    public void initialize(String path) {

        graphDb = new EmbeddedGraphDatabase(path);
        registerShutdownHook(graphDb);
        tx = graphDb.beginTx();
        txcount = 0;
        vertexIndex = graphDb.index().forNodes("vertexIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
        edgeIndex = graphDb.index().forRelationships("edgeIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
        VertexTable = new HashMap<Vertex, Long>();
        EdgeSet = new HashSet<String>();

        /*
        try {
        // rmiregistry -J-Djava.class.path=lib/neo4j-remote-graphdb-0.8-1.2.M04.jar
        java.rmi.registry.LocateRegistry.createRegistry(1099);
        RmiTransport.register(new LocalGraphDatabase(graphDb), remote_uri);
        } catch (Exception e) {
        e.printStackTrace();
        }
         */

        if (ENABLE_DOT_OUTPUT) {
            try {
                FileWriter out = new FileWriter("graph.dot", false);
                out.write("digraph dot_output {\ngraph [rankdir = \"RL\"];\nnode [fontname=\"Helvetica\" fontsize=\"10\" shape=\"Mrecord\"];\nedge [fontname=\"Helvetica\" fontsize=\"10\"];\n");
                out.close();
            } catch (Exception e) {
            }
        }
    }

    public void commit() {
        txcount++;
        if (txcount == 1000) {
            txcount = 0;
            try {
                tx.success();
                tx.finish();
                tx = graphDb.beginTx();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void putVertex(Vertex v) {
        if (VertexTable.get(v) != null) {
            return;
        }
        Node newVertex = graphDb.createNode();
        String vertexstring = "";
        HashMap<String, String> annotations = v.getAnnotations();
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            vertexstring = vertexstring + name + ": " + value + "|";
            newVertex.setProperty(name, value);
            vertexIndex.add(newVertex, name, value);
        }
        vertexstring = vertexstring.substring(0, vertexstring.length() - 2);
        Long newId = newVertex.getId();
        VertexTable.put(v, newId);
        commit();

        if (ENABLE_JSON_OUTPUT) {
            try {
                String vname;
                if (annotations.containsKey("PIDNAME")) {
                    vname = (String) annotations.get("PIDNAME");
                } else {
                    vname = (String) annotations.get("FILENAME");
                }
                String vid = Long.toString(newId);
                String json_path;
                String boottime;
                String beginjson;
                String description = "";
                for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
                    String name = (String) it.next();
                    String value = (String) annotations.get(name);
                    description = description + "<b>" + name + "</b>: " + value + "<br />";
                }
                if (vname.equals("System")) {
                    json_path = "/home/dawood/Desktop/SharedFolder/spadetree/json/json_root.txt";
                    boottime = (String) newVertex.getProperty("BOOTTIME", "");
                    beginjson = "{id:\"" + vid + "\", name:\"" + vname + "\", data:{type:\"process\", description:\"<b>BOOTTIME</b>: " + boottime + "\"}, children:[";
                } else {
                    if (vname.length() > 15) {
                        vname = vname.substring(0, 15);
                    }
                    json_path = "/home/dawood/Desktop/SharedFolder/spadetree/json/json_" + vid + ".txt";
                    beginjson = "{id:\"" + vid + "\", name:\"" + vname + "\", data:{description:\"" + description + "\"}, children:[  ";
                }

                FileWriter out = new FileWriter(json_path, false);
                out.write(beginjson);
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (ENABLE_DOT_OUTPUT) {
            try {
                FileWriter out = new FileWriter("graph.dot", true);
                String shape;
                if (v.getVertexType().equals("Process")) {
                    shape = "rect";
                } else {
                    shape = "ellipse";
                }
                out.write("\"" + Long.toString(newVertex.getId()) + "\" [label=\"<f0> " + vertexstring + "\"];\n");
                out.close();
            } catch (Exception e) {
            }
        }
    }

    public void putEdge(Vertex vsrc, Vertex vdst, Edge e) {
        Long srcNodeId = (Long) VertexTable.get(vsrc);
        Long dstNodeId = (Long) VertexTable.get(vdst);
        Node srcNode = graphDb.getNodeById(srcNodeId);
        Node dstNode = graphDb.getNodeById(dstNodeId);

        HashMap<String, String> annotations = e.getAnnotations();
        Relationship newEdge = srcNode.createRelationshipTo(dstNode, MyRelationshipTypes.EDGE);
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            newEdge.setProperty(name, value);
            edgeIndex.add(newEdge, name, value);
        }
        commit();

        if (ENABLE_JSON_OUTPUT) {
            String edgetype = (String) annotations.get("TYPE");
            if (edgetype.equals("used")) {
                srcNodeId = (Long) VertexTable.get(vdst);
                dstNodeId = (Long) VertexTable.get(vsrc);
                srcNode = graphDb.getNodeById(dstNodeId);
            }
            if (EdgeSet.add(Long.toString(dstNodeId) + "-" + Long.toString(srcNodeId))) {
                try {
                    String json_path = "/home/dawood/Desktop/SharedFolder/spadetree/json/json_" + Long.toString(dstNodeId) + ".txt";
                    if (vdst.getAnnotationValue("PID").equals("0")) {
                        json_path = "/home/dawood/Desktop/SharedFolder/spadetree/json/json_root.txt";
                    }
                    String tmpid = Long.toString(srcNodeId);
                    String tmpname = (String) srcNode.getProperty("PIDNAME", "");
                    if (tmpname.equals("")) {
                        tmpname = (String) srcNode.getProperty("FILENAME", "");
                    }
                    if (tmpname.length() > 15) {
                        tmpname = tmpname.substring(0, 15);
                    }
                    String tmptype = "process";
                    if (edgetype.equals("WasGeneratedBy") || edgetype.equals("WasDerivedFrom")) {
                        tmptype = "writefile";
                    } else if (edgetype.equals("Used")) {
                        tmptype = "readfile";
                    }
                    String childrendata = "{id:\"" + tmpid + "\", name:\"" + tmpname + "\", data:{type:\"" + tmptype + "\"}}, ";
                    FileWriter out = new FileWriter(json_path, true);
                    out.write(childrendata);
                    out.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        if (ENABLE_DOT_OUTPUT) {
            try {
                if (EdgeSet.add(Long.toString(dstNodeId) + "-" + Long.toString(srcNodeId))) {
                    String edgestring = "\"" + Long.toString(srcNodeId) + "\":f0 -> \"" + Long.toString(dstNodeId) + "\":f0 [id=" + newEdge.getId() + " label=\"" + e.getEdgeType() + "\"];\n";
                    FileWriter out = new FileWriter("graph.dot", true);
                    out.write(edgestring);
                    out.close();
                }
            } catch (Exception ex) {
            }
        }
    }

    public void shutdown() {
        tx.success();
        tx.finish();
        graphDb.shutdown();
    }

    private void registerShutdownHook(final GraphDatabaseService graphDb) {
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                System.out.println("Shutting down...");
                graphDb.shutdown();
            }
        });
    }
}
