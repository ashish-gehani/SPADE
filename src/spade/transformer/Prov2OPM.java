package spade.transformer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.DigQueryParams;
import spade.core.Graph;

public class Prov2OPM extends AbstractTransformer{

	private final static Logger logger = Logger.getLogger(Prov2OPM.class.getName());
	
	private static final Map<String, String> prov2OPMEdgeMappings = new HashMap<String, String>();
	
	private static final Map<String, String> prov2OPMVertexMappings = new HashMap<String, String>();
	
	static{
		prov2OPMVertexMappings.put("Agent","Agent");
		prov2OPMVertexMappings.put("Activity","Process");
		prov2OPMVertexMappings.put("Entity","Artifact");
		
		prov2OPMEdgeMappings.put("Used","Used");
		prov2OPMEdgeMappings.put("WasAssociatedWith","WasControlledBy");
		prov2OPMEdgeMappings.put("WasDerivedFrom","WasDerivedFrom");
		prov2OPMEdgeMappings.put("WasGeneratedBy","WasGeneratedBy");
		prov2OPMEdgeMappings.put("WasInformedBy","WasTriggeredBy");
	}

	public Graph putGraph(Graph graph, DigQueryParams digQueryParams){
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet()){
			if(edge != null && edge.getSourceVertex() != null && edge.getDestinationVertex() != null){
				String edgeType = getAnnotationSafe(edge, "type");
				String srcType = getAnnotationSafe(edge.getSourceVertex(), "type");
				String dstType = getAnnotationSafe(edge.getDestinationVertex(), "type");
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				newEdge.addAnnotation("type", getOPMEdgeTypeEquivalentToProvEdgeType(edgeType));
				newEdge.getSourceVertex().addAnnotation("type", getOPMVertexTypeEquivalentToProvVertexType(srcType));
				newEdge.getDestinationVertex().addAnnotation("type", getOPMVertexTypeEquivalentToProvVertexType(dstType));
				resultGraph.putVertex(newEdge.getSourceVertex());
				resultGraph.putVertex(newEdge.getDestinationVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		
		return resultGraph;
	}
	
	private String getOPMEdgeTypeEquivalentToProvEdgeType(String provEdgeType){
		if(prov2OPMEdgeMappings.containsKey(provEdgeType)){
			return prov2OPMEdgeMappings.get(provEdgeType);
		}else{
			logger.log(Level.SEVERE, "No opm equivalent edge type for prov edge type: " + provEdgeType);
			return provEdgeType;
		}
	}
	
	private String getOPMVertexTypeEquivalentToProvVertexType(String provVertexType){
		if(prov2OPMVertexMappings.containsKey(provVertexType)){
			return prov2OPMVertexMappings.get(provVertexType);
		}else{
			logger.log(Level.SEVERE, "No opm equivalent vertex type for prov vertex type: " + provVertexType);
			return provVertexType;
		}
	}
	
}
