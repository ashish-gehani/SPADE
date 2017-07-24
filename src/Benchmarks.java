import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Scanner;
import java.util.zip.DataFormatException;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.storage.CompressedStorage;
import spade.storage.TextFile;

public class Benchmarks {

	public static void main(String[] args) {
		
		//benchmarksCompressedStorage();


        
       /*TextFileRenamed storage = new TextFileRenamed();
		storage.initialize("/Users/melanie/Documents/benchmarks2/textfile.txt");
        Graph graph = Graph.importGraph("/Users/melanie/Documents/benchmarks/workload.dot");
        System.out.println("starting putVertex");
        //int count = 0;
        for (AbstractVertex v : graph.vertexSet()) {
            storage.putVertex(v);
            //System.out.print(count + " ");
            //count ++;
        }
        //count = 0;
        System.out.println("starting putEdge");
        for (AbstractEdge e : graph.edgeSet()) {
            storage.putEdge(e);
            //System.out.print(count + " ");
            //count ++;
        }
        System.out.println("shutdown");
        storage.shutdown(); */
	}

	
	public static void benchmarksCompressedStorage() {
		CompressedStorage storage = new CompressedStorage();
		storage.initialize("/Users/melanie/Documents/benchmarks");
        Graph graph = Graph.importGraph("/Users/melanie/Documents/benchmarks/workload.dot");
        System.out.println("starting putVertex");
        //int count = 0;
        long aux = System.currentTimeMillis();
        for (AbstractVertex v : graph.vertexSet()) {
            storage.putVertex(v);
            //System.out.print(count + " ");
            //count ++;
        }
        storage.benchmarks.println("Time to put all vertices in the annotations Database (ms): " + (System.currentTimeMillis() - aux));
        System.out.println("Time to put all vertices in the annotations Database (ms): " + (System.currentTimeMillis() - aux));
        //count = 0;
        System.out.println("starting putEdge");
        aux = System.currentTimeMillis();
        
        for (AbstractEdge e : graph.edgeSet()) {
            storage.putEdge(e);
            //System.out.print(count + " ");
            //count ++;
        }
        System.out.println(storage.uncompressAncestorsSuccessorsWithLayer(12, true, true).second().second().toString());
        /*try {
			System.out.println(storage.getTime(39363, 39011));
		} catch (UnsupportedEncodingException | DataFormatException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}*/
       
        System.out.println("start querying");
        File file = new File("/Users/melanie/Documents/benchmarks/hashes.scaffold");
        
        try {
        	PrintWriter query = new PrintWriter("/Users/melanie/Documents/benchmarks/query_time.txt");
        	long query_time = 0;
			Scanner sc = new Scanner(file);
			int countLines = 0;
			while(sc.hasNextLine()) {
				countLines ++;
				String hash = sc.nextLine();
				long aux_query1 = System.nanoTime();
				storage.getLineageMap(hash, "desc", 5);
				long aux_query2 = System.nanoTime();
				query.println(aux_query2 - aux_query1);
				query_time += aux_query2 - aux_query1;
			}
			System.out.println("Average query time (ns) : " + (query_time/countLines));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        //System.out.println(storage.uncompressAncestorsSuccessorsWithLayer(12, true, true).second().second().toString());
        //System.out.println(storage.uncompressAncestorsSuccessorsWithLayer(44139, true, true).second().second().toString());
        System.out.println("shutdown");
        storage.shutdown();
	}
	
	public static void benchmarksTextfile() {
		TextFile storage = new TextFile();
		storage.initialize("/Users/melanie/Documents/benchmarks/TextFile/storage.txt");
        Graph graph = Graph.importGraph("/Users/melanie/Documents/benchmarks/workload.dot");
        System.out.println("starting putVertex");
        //int count = 0;
        long aux = System.currentTimeMillis();
        for (AbstractVertex v : graph.vertexSet()) {
            storage.putVertex(v);
            //System.out.print(count + " ");
            //count ++;
        }
        System.out.println("Time to put all vertices in the database (ms): " + (System.currentTimeMillis() - aux));
        //count = 0;
        aux = System.currentTimeMillis();
        
        for (AbstractEdge e : graph.edgeSet()) {
            storage.putEdge(e);
            //System.out.print(count + " ");
            //count ++;
        }
        System.out.println("Time to put all edges in the database (ms): " + (System.currentTimeMillis() - aux));
       
        System.out.println("start querying");
        File file = new File("/Users/melanie/Documents/benchmarks/hashes.scaffold");
        
        try {
        	PrintWriter query = new PrintWriter("/Users/melanie/Documents/benchmarks/query_time.txt");
        	long query_time = 0;
			Scanner sc = new Scanner(file);
			int countLines = 0;
			while(sc.hasNextLine()) {
				countLines ++;
				String hash = sc.nextLine();
				long aux_query1 = System.nanoTime();
				storage.getLineage(hash, "desc", 5);
				long aux_query2 = System.nanoTime();
				query.println(aux_query2 - aux_query1);
				query_time += aux_query2 - aux_query1;
			}
			System.out.println("Average query time (ns) : " + (query_time/countLines));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        //System.out.println(storage.uncompressAncestorsSuccessorsWithLayer(12, true, true).second().second().toString());
        //System.out.println(storage.uncompressAncestorsSuccessorsWithLayer(44139, true, true).second().second().toString());
        System.out.println("shutdown");
        storage.shutdown();
	}
}
