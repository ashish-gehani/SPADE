package spade.transformer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.DigQueryParams;
import spade.core.Graph;

public class OPM2Prov extends AbstractTransformer{
	
	private final static Logger logger = Logger.getLogger(OPM2Prov.class.getName());
	
	private static final Map<String, String> opm2ProvEdgeMappings = new HashMap<String, String>();
	
	private static final Map<String, String> opm2ProvVertexMappings = new HashMap<String, String>();
	
	static{
		opm2ProvVertexMappings.put("Agent","Agent");
		opm2ProvVertexMappings.put("Process","Activity");
		opm2ProvVertexMappings.put("Artifact","Entity");
		
		opm2ProvEdgeMappings.put("Used","Used");
		opm2ProvEdgeMappings.put("WasControlledBy","WasAssociatedWith");
		opm2ProvEdgeMappings.put("WasDerivedFrom","WasDerivedFrom");
		opm2ProvEdgeMappings.put("WasGeneratedBy","WasGeneratedBy");
		opm2ProvEdgeMappings.put("WasTriggeredBy","WasInformedBy");
	}

	public Graph putGraph(Graph graph, DigQueryParams digQueryParams){
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet()){
			if(edge != null && edge.getSourceVertex() != null && edge.getDestinationVertex() != null){
				String edgeType = getAnnotationSafe(edge, "type");
				String srcType = getAnnotationSafe(edge.getSourceVertex(), "type");
				String dstType = getAnnotationSafe(edge.getDestinationVertex(), "type");
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				newEdge.addAnnotation("type", getProvEdgeTypeEquivalentToOPMEdgeType(edgeType));
				newEdge.getSourceVertex().addAnnotation("type", getProvVertexTypeEquivalentToOPMVertexType(srcType));
				newEdge.getDestinationVertex().addAnnotation("type", getProvVertexTypeEquivalentToOPMVertexType(dstType));
				resultGraph.putVertex(newEdge.getSourceVertex());
				resultGraph.putVertex(newEdge.getDestinationVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		
		return resultGraph;
	}
	
	private String getProvEdgeTypeEquivalentToOPMEdgeType(String opmEdgeType){
		if(opm2ProvEdgeMappings.containsKey(opmEdgeType)){
			return opm2ProvEdgeMappings.get(opmEdgeType);
		}else{
			logger.log(Level.SEVERE, "No prov equivalent edge type for opm edge type: " + opmEdgeType);
			return opmEdgeType;
		}
	}
	
	private String getProvVertexTypeEquivalentToOPMVertexType(String opmVertexType){
		if(opm2ProvVertexMappings.containsKey(opmVertexType)){
			return opm2ProvVertexMappings.get(opmVertexType);
		}else{
			logger.log(Level.SEVERE, "No prov equivalent vertex type for opm vertex type: " + opmVertexType);
			return opmVertexType;
		}
	}
	
}
