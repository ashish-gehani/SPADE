package spade.storage;

import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.AbstractEdge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.core.Edge;
import spade.storage.SQL;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by raza on 12/6/16.
 */
class SQLTest {
    private static final SQL testSQLObject = new SQL();
    private static Graph graph = new Graph();


    /**
     * This function executes before any unit test.
     * It creates the environment required to perform the test.
     */
    @org.junit.jupiter.api.BeforeEach
    void setUp()
    {
        // Creating test data to work with.
        // Sample data is taken from the example mentioned in:
        // https://github.com/ashish-gehani/SPADE/wiki/Example%20illustrating%20provenance%20querying

        AbstractVertex v1 = new Vertex();
        v1.addAnnotation("storageID", "1");
        v1.removeAnnotation("type");
        v1.addAnnotation("type", "Process");
        v1.addAnnotation("name", "root process");
        graph.putVertex(v1);

        AbstractVertex v2 = new Vertex();
        v2.addAnnotation("storageID", "2");
        v2.removeAnnotation("type");
        v2.addAnnotation("type", "Process");
        v2.addAnnotation("name", "child process");
        graph.putVertex(v2);

        AbstractEdge e1 = new Edge(v2, v1);
        e1.addAnnotation("storageID", "1");
        e1.removeAnnotation("type");
        e1.addAnnotation("type", "WasTriggeredBy");
        e1.addAnnotation("time", "5:56 PM");
        graph.putEdge(e1);

        AbstractVertex v3 = new Vertex();
        v3.addAnnotation("storageID", "3");
        v3.removeAnnotation("type");
        v3.addAnnotation("type", "Artifact");
        v3.addAnnotation("file_name", "output.tmp");
        graph.putVertex(v3);

        AbstractVertex v4 = new Vertex();
        v4.addAnnotation("storageID", "4");
        v4.removeAnnotation("type");
        v4.addAnnotation("type", "Artifact");
        v4.addAnnotation("file_name", "output.o");
        graph.putVertex(v4);

        AbstractEdge e2 = new Edge(v2, v3);
        e2.addAnnotation("storageID", "2");
        e2.removeAnnotation("type");
        e2.addAnnotation("type", "Used");
        e2.addAnnotation("IO_time", "12 ms");
        graph.putEdge(e2);

        AbstractEdge e3 = new Edge(v4, v2);
        e3.addAnnotation("storageID", "3");
        e3.removeAnnotation("type");
        e3.addAnnotation("type", "WasGeneratedBy");
        e3.addAnnotation("IO_time", "11 ms");
        graph.putEdge(e3);

        AbstractEdge e4 = new Edge(v4, v3);
        e4.addAnnotation("storageID", "4");
        e4.removeAnnotation("type");
        e4.addAnnotation("type", "WasDerivedFrom");
        graph.putEdge(e4);

        AbstractVertex v5 = new Vertex();
        v5.addAnnotation("storageID", "5");
        v5.removeAnnotation("type");
        v5.addAnnotation("type", "Agent");
        v5.addAnnotation("uid", "10");
        v5.addAnnotation("name", "John");
        graph.putVertex(v5);

        AbstractEdge e5 = new Edge(v1, v5);
        e5.addAnnotation("storageID", "5");
        e5.removeAnnotation("type");
        e5.addAnnotation("type", "WasControlledBy");
        graph.putEdge(e5);

        AbstractEdge e6 = new Edge(v2, v5);
        e6.addAnnotation("storageID", "6");
        e6.removeAnnotation("type");
        e6.addAnnotation("type", "WasControlledBy");
        graph.putEdge(e6);

        SQL.TEST_ENV = true;
        SQL.TEST_GRAPH = Graph.union(new Graph(), graph);
    }

    /**
     * This function executes after the execution of each unit test.
     * It clears up the environment setup for the test case execution.
     */
    @org.junit.jupiter.api.AfterEach
    void tearDown()
    {
        SQL.TEST_ENV = false;
        SQL.TEST_GRAPH = null;
    }

    /**
     * This function tests the functionality of getAllPaths_new function in spade.storage.SQL.java file
     */
    @Test
    void getAllPaths_new()
    {
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

        Graph actualOutcomeCase1 = testSQLObject.getAllPaths_new(4, 3, 10);
        assertEquals(expectedOutcomeCase1.vertexSet().size(), actualOutcomeCase1.vertexSet().size());
        assertEquals(expectedOutcomeCase1.edgeSet().size(), actualOutcomeCase1.edgeSet().size());
        assertFalse(Graph.remove(expectedOutcomeCase1, actualOutcomeCase1).isEmpty() && Graph.remove(actualOutcomeCase1, expectedOutcomeCase1).isEmpty());

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

        Graph actualOutomeCase2 = testSQLObject.getAllPaths_new(4, 5, 10);
        assertEquals(expectedOutcomeCase2.vertexSet().size(), actualOutomeCase2.vertexSet().size());
        assertEquals(expectedOutcomeCase2.edgeSet().size(), actualOutomeCase2.edgeSet().size());
        assertFalse(Graph.remove(expectedOutcomeCase2, actualOutomeCase2).isEmpty() && Graph.remove(actualOutomeCase2, expectedOutcomeCase2).isEmpty());

    }
}