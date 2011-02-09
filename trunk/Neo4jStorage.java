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
import org.neo4j.index.impl.lucene.ValueContext;
//import org.neo4j.remote.transports.LocalGraphDatabase;
//import org.neo4j.remote.transports.RmiTransport;

public class Neo4jStorage implements ConsumerInterface {

    private GraphDatabaseService graphDb;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction tx;
    private int txcount;
    private HashMap<Vertex, Long> VertexTable;
//    private final String remote_uri = "rmi://localhost/neo4jdb";

    public enum MyRelationshipTypes implements RelationshipType {

        EDGE
    }

    public boolean initialize(String path) {

        graphDb = new EmbeddedGraphDatabase(path);
        registerShutdownHook(graphDb);
        txcount = 0;
        vertexIndex = graphDb.index().forNodes("vertexIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
        edgeIndex = graphDb.index().forRelationships("edgeIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
        VertexTable = new HashMap<Vertex, Long>();

        /*
        try {
        // rmiregistry -J-Djava.class.path=lib/neo4j-remote-graphdb-0.8-1.2.M04.jar
        java.rmi.registry.LocateRegistry.createRegistry(1099);
        RmiTransport.register(new LocalGraphDatabase(graphDb), remote_uri);
        } catch (Exception e) {
        e.printStackTrace();
        }
         */

        return true;

    }

    public void commit() {
        txcount++;
        if (txcount == 25000) {
            txcount = 0;
            try {
                tx.success();
                tx.finish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Vertex fixVertex(Vertex v) {
        if (v instanceof Artifact) {
            v.addAnnotation("type", "Artifact");
        } else if (v instanceof Process) {
            v.addAnnotation("type", "Process");
        } else if (v instanceof Agent) {
            v.addAnnotation("type", "Agent");
        }
        return v;
    }

    public Edge fixEdge(Edge e) {
        if (e instanceof Used) {
            e.addAnnotation("type", "Used");
        } else if (e instanceof WasControlledBy) {
            e.addAnnotation("type", "WasControlledBy");
        } else if (e instanceof WasDerivedFrom) {
            e.addAnnotation("type", "WasDerivedFrom");
        } else if (e instanceof WasGeneratedBy) {
            e.addAnnotation("type", "WasGeneratedBy");
        } else if (e instanceof WasTriggeredBy) {
            e.addAnnotation("type", "WasTriggeredBy");
        }
        return e;
    }

    public boolean putVertex(Vertex v) {
        if (txcount == 0) {
            tx = graphDb.beginTx();
        }
        if (VertexTable.get(v) != null) {
            return false;
        }
        v = fixVertex(v);
        Node newVertex = graphDb.createNode();
        Map<String, String> annotations = v.getAnnotations();
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            try {
                Long val = Long.parseLong(value);
                newVertex.setProperty(name, val);
                vertexIndex.add(newVertex, name, new ValueContext(val).indexNumeric());
            } catch (Exception e) {
                newVertex.setProperty(name, value);
                vertexIndex.add(newVertex, name, value);
            }
        }
        VertexTable.put(v, newVertex.getId());
        commit();
        return true;
    }

    public boolean putEdge(Edge e) {
        if (txcount == 0) {
            tx = graphDb.beginTx();
        }
        Vertex vsrc = fixVertex(e.getSrcVertex());
        Vertex vdst = fixVertex(e.getDstVertex());
        e = fixEdge(e);
        Long srcNodeId = (Long) VertexTable.get(vsrc);
        Long dstNodeId = (Long) VertexTable.get(vdst);
        Node srcNode = graphDb.getNodeById(srcNodeId);
        Node dstNode = graphDb.getNodeById(dstNodeId);

        Map<String, String> annotations = e.getAnnotations();
        Relationship newEdge = srcNode.createRelationshipTo(dstNode, MyRelationshipTypes.EDGE);
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            newEdge.setProperty(name, value);
            edgeIndex.add(newEdge, name, value);
        }
        commit();
        return true;
    }

    public boolean shutdown() {
        tx.success();
        tx.finish();
        graphDb.shutdown();
        return true;
    }

    private void registerShutdownHook(final GraphDatabaseService graphDb) {
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                tx.success();
                tx.finish();
                graphDb.shutdown();
            }
        });
    }
}
