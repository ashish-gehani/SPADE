import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.storage.CompressedStorage;
import spade.storage.TextFile;
import spade.storage.TextFileRenamed;

public class Debug {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		CompressedStorage storage = new CompressedStorage();
		storage.initialize("/Users/melanie/Documents/benchmarks2");
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
        System.out.println(storage.uncompressAncestorsSuccessorsWithLayer(12, true, true).second().second().toString());
        try {
			System.out.println(storage.getTime(39363, 39011));
		} catch (UnsupportedEncodingException | DataFormatException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        System.out.println("shutdown");
        storage.shutdown();
        
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

}
