package spade.transformer;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.DigQueryParams;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.FileUtility;

public class ProvTC2OPM extends Prov2OPM{
	
	private static final Logger logger = Logger.getLogger(ProvTC2OPM.class.getName());
	
	private Map<String, String> provTC2OpmMapping = null;
	
	public boolean initialize(String arguments){
		try{
			provTC2OpmMapping = FileUtility.readOPM2ProvTCFileReversed(Settings.getProperty("opm&provtc_mapping_filepath"));
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read the file: " + Settings.getProperty("opm&provtc_mapping_filepath"), e);
			return false;
		}
	}

	public Graph putGraph(Graph graph, DigQueryParams digQueryParams){
		graph = super.putGraph(graph, digQueryParams);
		graph.commitIndex();
		
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet()){
			if(edge != null && edge.getSourceVertex() != null && edge.getDestinationVertex() != null){
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				replaceAnnotations(newEdge.getAnnotations(), provTC2OpmMapping);
				replaceAnnotations(newEdge.getSourceVertex().getAnnotations(), provTC2OpmMapping);
				replaceAnnotations(newEdge.getDestinationVertex().getAnnotations(), provTC2OpmMapping);
				resultGraph.putVertex(newEdge.getSourceVertex());
				resultGraph.putVertex(newEdge.getDestinationVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		
		return resultGraph;
	}
	
	private void replaceAnnotations(Map<String, String> annotations, Map<String, String> newMapping){
		for(String annotation : annotations.keySet()){
			if(newMapping.get(annotation) != null){
				annotations.put(newMapping.get(annotation), annotations.get(annotation));
			}
		}
	}
	
}
