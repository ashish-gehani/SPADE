package storage;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * This class is used to measure and analyze storages
 * for insertion and retrieval purposes.
 * @author Raza Ahmad
 */
public class StorageProfiler
{
    private static final spade.storage.BerkeleyDB BerkeleyDBInstance = new spade.storage.BerkeleyDB();
    private static List<AbstractVertex> vertexList = new LinkedList<>();
    private static Map<String, String> vertexHashes = new HashMap<>();
    private static Map<String, String> edgeHashes = new HashMap<>();
    private static long serialID = 1;
    private static final long totalRecords = 1000000000;    // 1B

    private static String getSerialId()
    {
        return Long.toString(serialID++);
    }

    private static void computeRetrievalStats(long start_time, String file_name, long checkpoint)
    {
        long elapsed_time = System.nanoTime() - start_time;

        long getVertex_start_time = System.nanoTime();
        for(Map.Entry<String, String> entry: vertexHashes.entrySet())
        {
            entry.getKey();
            BerkeleyDBInstance.getVertex(entry.getValue());
        }
        long getVertex_elapsed_time = System.nanoTime() - getVertex_start_time;

        long getEdge_start_time = System.nanoTime();
        for(Map.Entry<String, String> entry: edgeHashes.entrySet())
        {
            BerkeleyDBInstance.getEdge(entry.getKey(), entry.getValue());
        }
        long getEdge_elapsed_time = System.nanoTime() - getEdge_start_time;

        long getChildren_start_time = System.nanoTime();
        for(Map.Entry<String, String> entry: vertexHashes.entrySet())
        {
            BerkeleyDBInstance.getChildren(entry.getValue());
        }
        long getChildren_elapsed_time = System.nanoTime() - getChildren_start_time;

        long getParents_start_time = System.nanoTime();
        for(Map.Entry<String, String> entry: vertexHashes.entrySet())
        {
            BerkeleyDBInstance.getParents(entry.getValue());
        }
        long getParents_elapsed_time = System.nanoTime() - getParents_start_time;

        try(FileWriter fw = new FileWriter(file_name, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw))
        {
            out.println("Number of Records: " + checkpoint);
            out.println("Timings are in seconds(s).");
            out.println("Cached Vertices: " + vertexHashes.size());
            out.println("Cached Edges: " + edgeHashes.size());
            out.println("Vertex/Edge Net Insertion Time: " + elapsed_time/1000000000.0);
            out.println("getVertex Net Time: " + getVertex_elapsed_time/1000000000.0);
            out.println("getEdge Net Time: " + getEdge_elapsed_time/1000000000.0);
            out.println("getChildren Net Time: " + getChildren_elapsed_time/1000000000.0);
            out.println("getParents Net Time: " + getParents_elapsed_time/1000000000.0);
            out.println("*******************************************");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        Random random = new Random();
        boolean cacheVertex = false;
        String dbPath = "/tmp/spade_test.graph_db";
//        String dbPath = "org.postgresql.Driver jdbc:postgresql://localhost/spade_test sa null";
        BerkeleyDBInstance.initialize(dbPath);
        int[] checkpoints = {10000, 100000, 1000000, 10000000, 100000000, 1000000000};
//        int[] checkpoints = {100, 1000};
        int checkpointIdx = 0;
        int vertexCacheFrequency = 1000;
        String stats_file_name = "Neo4j_stats_with_index.txt";

        long start_time = System.nanoTime();
        while(serialID <= totalRecords)
        {
            if(serialID % vertexCacheFrequency == 0)
                cacheVertex = true;
            AbstractVertex vertex = new Vertex();
            vertex.addAnnotation("count", getSerialId());
            BerkeleyDBInstance.putVertex(vertex);
            // cache a vertex with 25% probability for edge creation
            if(random.nextInt(100) > 75)
                vertexList.add(vertex);

            // 25% probability of an edge creation
            if(random.nextInt(100) > 75 && vertexList.size() >= 2)
            {
                AbstractVertex childVertex = vertexList.get(random.nextInt(vertexList.size()));
                AbstractVertex parentVertex = vertexList.get(random.nextInt(vertexList.size()));
                if(!childVertex.bigHashCode().equals(parentVertex.bigHashCode()))
                {
                    AbstractEdge edge = new Edge(childVertex, parentVertex);
                    edge.addAnnotation("count", getSerialId());
                    BerkeleyDBInstance.putEdge(edge);
                    // cache a vertex hash for retrieval purposes after every thousand
                    if(cacheVertex)
                    {
                        vertexHashes.put(childVertex.getAnnotation("count"), childVertex.bigHashCode());
                        vertexHashes.put(parentVertex.getAnnotation("count"), parentVertex.bigHashCode());
                        edgeHashes.put(childVertex.bigHashCode(), parentVertex.bigHashCode());
                        cacheVertex = false;
                    }
                }
            }
            if(checkpointIdx < checkpoints.length && (serialID % checkpoints[checkpointIdx] == 0 ||
                    serialID > totalRecords))
            {
//                BerkeleyDBInstance.globalTxCheckin(true);
                computeRetrievalStats(start_time, stats_file_name, checkpoints[checkpointIdx]);
                checkpointIdx++;
                vertexCacheFrequency *= 10;
                start_time = System.nanoTime();
            }

        }
    }
}
