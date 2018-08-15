/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International

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

package storage;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.query.scaffold.Scaffold;
import spade.query.scaffold.ScaffoldFactory;
import spade.storage.PostgreSQL;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class is used to test the functions of spade.storage.PostgreSQL.java
 * @author Raza Ahmad
 */
class SQLTest {
    private static final PostgreSQL testSQLObject = new PostgreSQL();
    private static Graph graph = new Graph();


    /**
     * This function executes before any unit test.
     * It creates the environment required to perform the test.
     */
    @org.junit.jupiter.api.BeforeEach
    void setUp() throws SQLException {
        // Creating test data to work with.
        // Sample data is taken from the example mentioned in:
        // https://github.com/ashish-gehani/SPADE/wiki/Example%20illustrating%20provenance%20querying

        AbstractVertex v1 = new Vertex();
        v1.removeAnnotation("type");
        v1.addAnnotation("type", "Process");
        v1.addAnnotation("name", "root process");
        v1.addAnnotation("pid", "10");
        v1.addAnnotation("hash", Integer.toString(v1.hashCode()));
        v1.addAnnotation("vertexid", "1");
        graph.putVertex(v1);

        AbstractVertex v2 = new Vertex();
        v2.removeAnnotation("type");
        v2.addAnnotation("type", "Process");
        v2.addAnnotation("name", "child process");
        v2.addAnnotation("pid", "32");
        v2.addAnnotation("hash", Integer.toString(v2.hashCode()));
        v2.addAnnotation("vertexid", "2");
        graph.putVertex(v2);

        AbstractEdge e1 = new Edge(v2, v1);
        e1.removeAnnotation("type");
        e1.addAnnotation("type", "WasTriggeredBy");
        e1.addAnnotation("time", "5:56 PM");
        e1.addAnnotation("childVertexhash", v2.getAnnotation("hash"));
        e1.addAnnotation("parentVertexhash", v1.getAnnotation("hash"));
        e1.addAnnotation("edgeid", "11");
        graph.putEdge(e1);

        AbstractVertex v3 = new Vertex();
        v3.removeAnnotation("type");
        v3.addAnnotation("type", "Artifact");
        v3.addAnnotation("filename", "output.tmp");
        v3.addAnnotation("hash", Integer.toString(v3.hashCode()));
        v3.addAnnotation("vertexid", "3");
        graph.putVertex(v3);

        AbstractVertex v4 = new Vertex();
        v4.removeAnnotation("type");
        v4.addAnnotation("type", "Artifact");
        v4.addAnnotation("filename", "output.o");
        v4.addAnnotation("hash", Integer.toString(v4.hashCode()));
        v4.addAnnotation("vertexid", "4");
        graph.putVertex(v4);

        AbstractEdge e2 = new Edge(v2, v3);
        e2.removeAnnotation("type");
        e2.addAnnotation("type", "Used");
        e2.addAnnotation("iotime", "12 ms");
        e2.addAnnotation("hash", Integer.toString(e2.hashCode()));
        e2.addAnnotation("childVertexhash", v2.getAnnotation("hash"));
        e2.addAnnotation("parentVertexhash", v3.getAnnotation("hash"));
        e2.addAnnotation("edgeid", "2");
        graph.putEdge(e2);

        AbstractEdge e3 = new Edge(v4, v2);
        e3.removeAnnotation("type");
        e3.addAnnotation("type", "WasGeneratedBy");
        e3.addAnnotation("iotime", "11 ms");
        e3.addAnnotation("hash", Integer.toString(e3.hashCode()));
        e3.addAnnotation("childVertexhash", v4.getAnnotation("hash"));
        e3.addAnnotation("parentVertexhash", v2.getAnnotation("hash"));
        e3.addAnnotation("edgeid", "3");
        graph.putEdge(e3);

        AbstractEdge e4 = new Edge(v4, v3);
        e4.removeAnnotation("type");
        e4.addAnnotation("type", "WasDerivedFrom");
        e4.addAnnotation("hash", Integer.toString(e4.hashCode()));
        e4.addAnnotation("childVertexhash", v4.getAnnotation("hash"));
        e4.addAnnotation("parentVertexhash", v3.getAnnotation("hash"));
        e4.addAnnotation("edgeid", "4");
        graph.putEdge(e4);

        AbstractVertex v5 = new Vertex();
        v5.removeAnnotation("type");
        v5.addAnnotation("type", "Agent");
        v5.addAnnotation("uid", "10");
        v5.addAnnotation("gid", "10");
        v5.addAnnotation("name", "john");
        v5.addAnnotation("hash", Integer.toString(v5.hashCode()));
        v5.addAnnotation("vertexid", "5");
        graph.putVertex(v5);

        AbstractEdge e5 = new Edge(v1, v5);
        e5.removeAnnotation("type");
        e5.addAnnotation("type", "WasControlledBy");
        e5.addAnnotation("hash", Integer.toString(e5.hashCode()));
        e5.addAnnotation("childVertexhash", v1.getAnnotation("hash"));
        e5.addAnnotation("parentVertexhash", v5.getAnnotation("hash"));
        e5.addAnnotation("edgeid", "5");
        graph.putEdge(e5);

        AbstractEdge e6 = new Edge(v2, v5);
        e6.removeAnnotation("type");
        e6.addAnnotation("type", "WasControlledBy");
        e6.addAnnotation("hash", Integer.toString(e6.hashCode()));
        e6.addAnnotation("childVertexhash", v2.getAnnotation("hash"));
        e6.addAnnotation("parentVertexHhash", v5.getAnnotation("hash"));
        e6.addAnnotation("edgeid", "6");
        graph.putEdge(e6);

        String connectionString = "org.postgresql.Driver jdbc:postgresql://localhost/spade_pg sa null";
        if(!testSQLObject.initialize(connectionString))
            throw new SQLException();

        Scaffold scaffold = ScaffoldFactory.createDefaultScaffold();
        scaffold.initialize("/tmp");
        AbstractStorage.setScaffold(scaffold);
        testSQLObject.putEdge(e1);
        testSQLObject.putEdge(e2);
    }

    /**
     * This function executes after the execution of each unit test.
     * It clears up the environment setup for the test case execution.
     */
    @org.junit.jupiter.api.AfterEach
    void tearDown() {}


    /**
     * This function tests the functionality of findPaths function in spade.storage.PostgreSQL.java
     */
    @Test
    void findPaths()
    {
        System.out.println(Hex.encodeHexString(DigestUtils.sha256("abcdefghijklmnopqrstuvwxyz")));
        System.out.println(Hex.encodeHexString(DigestUtils.sha256("abcdefghijklmnopqrstuvwxyz")).substring(0, 32));

        //Test Case 1:
        // Creating graph for the expected outcome.
        // The following sample subgraph contains 3 vertices and 3 edges.
        Graph expectedOutcomeCase1 = new Graph();
        int i = 1;
        for(AbstractVertex v: graph.vertexSet())
        {
            if (i == 2 || i == 3 || i == 4)
                expectedOutcomeCase1.putVertex(v);
            i++;
        }
        i = 1;
        for(AbstractEdge e: graph.edgeSet())
        {
            if (i == 2 || i == 3 || i == 4)
                expectedOutcomeCase1.putEdge(e);
            i++;
        }

        Graph actualOutcomeCase1 = null; //testSQLObject.findPaths(4, 3, 10);
        assertTrue(expectedOutcomeCase1.equals(actualOutcomeCase1));

        // Test Case 2:
        // Creating graph for the expected outcome.
        // The following sample subgraph contains 4 vertices and 4 edges.
        Graph expectedOutcomeCase2 = new Graph();
        i = 1;
        for(AbstractVertex v: graph.vertexSet())
        {
            if(i != 3)
                expectedOutcomeCase2.putVertex(v);
            i++;
        }
        i = 1;
        for(AbstractEdge e: graph.edgeSet())
        {
            if (i != 2 && i != 4)
                expectedOutcomeCase2.putEdge(e);
            i++;
        }

        Graph actualOutcomeCase2 = null; //testSQLObject.findPaths(4, 5, 10);
        assertTrue(expectedOutcomeCase2.equals(actualOutcomeCase2));

    }

    /*
    * This function tests the functionality of getLineage function in spade.storage.PostgreSQL.java
    * */
    @Test
    void getLineage()
    {
        //Test Case 1:
        // Creating graph for the expected outcome.
        // The following sample subgraph contains 4 vertices and 4 edges.
        Graph expectedOutcomeCase1 = new Graph();
        int i = 1;
        for(AbstractVertex v: graph.vertexSet())
        {
            if(i != 3)
                expectedOutcomeCase1.putVertex(v);
            i++;
        }
        i = 1;
        for(AbstractEdge e: graph.edgeSet())
        {
            if(i != 2 && i != 4)
                expectedOutcomeCase1.putEdge(e);
            i++;
        }

        Graph actualOutcomeCase1 = null; //testSQLObject.getLineage(4, 5, "a", 3);
        assertTrue(expectedOutcomeCase1.equals(actualOutcomeCase1));

        //Test Case 2:
        // Creating graph for the expected outcome.
        // The following sample subgraph contains 3 vertices and 3 edges.
        Graph expectedOutcomeCase2 = new Graph();
        i = 1;
        for(AbstractVertex v: graph.vertexSet())
        {
            if(i != 1 && i != 5)
                expectedOutcomeCase2.putVertex(v);
            i++;
        }
        i = 1;
        for(AbstractEdge e: graph.edgeSet())
        {
            if(i == 2 || i == 3 || i == 4)
                expectedOutcomeCase2.putEdge(e);
            i++;
        }

        Graph actualOutcomeCase2 = null; //testSQLObject.getLineage(3, 3, "d", 1);
        assertTrue(expectedOutcomeCase2.equals(actualOutcomeCase2));
    }
}