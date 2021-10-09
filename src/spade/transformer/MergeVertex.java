package spade.transformer;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import spade.core.AbstractEdge;
import spade.core.Edge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.HelperFunctions;
import spade.reporter.audit.OPMConstants;

public class MergeVertex extends AbstractTransformer{

    private final static Logger logger = Logger.getLogger(MergeVertex.class.getName());
    private String[] mergeBaseKeys = null;

    //arguments will be the attribute based on which a vertex will be marked as equal to other, it can be a single key or comma separated keys
    public boolean initialize(String arguments){
        if(arguments == null || arguments.length() == 0){
            logger.log(Level.SEVERE, "No key is provided.");
            return false;
        }else{
            mergeBaseKeys = arguments.split(",");
        }

        return true;
    }

    @Override
    public LinkedHashSet<ArgumentName> getArgumentNames(){
        return new LinkedHashSet<ArgumentName>();
    }

    @Override
    public Graph transform(Graph graph, ExecutionContext context){
        
        
        // vertex frequency of vertices with similar hashes
        Map<String, Integer> vertexFrequencies = new HashMap<String, Integer>();
        // corresponding vertices map
        Map<String, AbstractVertex> mergedVertices = new HashMap<String, AbstractVertex>();

        /*
         * creating merge vertices
         */
        for(AbstractVertex v : graph.vertexSet()){
            
            //AbstractVertex vertex = v.copyAsVertex();
            String hash = vertexHashForKeys(v);
	        if(hash == null || hash.length() == 0) { continue;}

            if (vertexFrequencies.get(hash) == null || vertexFrequencies.get(hash)  == 0) {

                AbstractVertex vertex = v.copyAsVertex();
                vertexFrequencies.put(hash, 1);
                mergedVertices.put(hash, vertex);
                
            } else {

                vertexFrequencies.put(hash, vertexFrequencies.get(hash) + 1);
                AbstractVertex mergedVertex = mergedVertices.get(hash);
                
                for (String annotationKey : v.getAnnotationKeys()) {

                    String newAnnotation = v.getAnnotation(annotationKey);
                    String mergedAnnotation = mergedVertex.getAnnotation(annotationKey);
        
                    if (newAnnotation != null && mergedAnnotation != null && !mergedAnnotation.contains(newAnnotation)){

                        mergedVertex.removeAnnotation(annotationKey);
                        mergedVertex.addAnnotation(annotationKey, mergedAnnotation + "," + newAnnotation);
                    } //new annotation to add
                    else if (mergedAnnotation == null && newAnnotation != null){

                        mergedVertex.addAnnotation(annotationKey, newAnnotation);
                    }
                
                }
            
                mergedVertices.put(hash, mergedVertex);//merged vertex   
            }

        }

        Graph resultGraph = new Graph();

        for(Map.Entry<String, AbstractVertex> set : mergedVertices.entrySet()){
            AbstractVertex mergedVertex = set.getValue();
            String mergedVertexHash = set.getKey();
            resultGraph.putVertex(mergedVertex);
            for (AbstractEdge edge: graph.edgeSet()){

                AbstractVertex newChild = edge.getChildVertex().copyAsVertex();
		        AbstractVertex newParent = edge.getParentVertex().copyAsVertex();
		        AbstractEdge newEdge = new Edge(newChild, newParent);

                if(edge.getChildVertex() != null) {
                    String hash = vertexHashForKeys(edge.getChildVertex());
                    if(hash != null && hash.equals(mergedVertexHash)) {
                        newEdge.setChildVertex(mergedVertex);
                    }
                    
                }
                if(edge.getParentVertex() != null) {
                    String hash = vertexHashForKeys(edge.getParentVertex());
                    if(hash != null && hash.equals(mergedVertexHash)) {
                        newEdge.setParentVertex(mergedVertex);
                    }
                }
                resultGraph.putEdge(newEdge);
            }

        }

        return resultGraph;
        
    }
    private String vertexHashForKeys(AbstractVertex vertex) {

	String hash = new String();
        for (String key : mergeBaseKeys) {
	    String annotation = vertex.getAnnotation(key);
	    if(annotation != null && annotation.length() > 0) 
            hash += annotation + ",";
        }
        return hash;
    }
}
