/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

package spade.transformer;

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.reporter.audit.OPMConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Prov2OPM extends AbstractTransformer
{

	private final static Logger logger = Logger.getLogger(Prov2OPM.class.getName());
	
	private static final Map<String, String> prov2OPMEdgeMappings = new HashMap<>();
	
	private static final Map<String, String> prov2OPMVertexMappings = new HashMap<>();
	
	static{
		prov2OPMVertexMappings.put("Agent",OPMConstants.AGENT);
		prov2OPMVertexMappings.put("Activity",OPMConstants.PROCESS);
		prov2OPMVertexMappings.put("Entity",OPMConstants.ARTIFACT);
		
		prov2OPMEdgeMappings.put("Used",OPMConstants.USED);
		prov2OPMEdgeMappings.put("WasAssociatedWith",OPMConstants.WAS_CONTROLLED_BY);
		prov2OPMEdgeMappings.put("WasDerivedFrom",OPMConstants.WAS_DERIVED_FROM);
		prov2OPMEdgeMappings.put("WasGeneratedBy",OPMConstants.WAS_GENERATED_BY);
		prov2OPMEdgeMappings.put("WasInformedBy",OPMConstants.WAS_TRIGGERED_BY);
	}

	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet())
		{
			if(edge != null && edge.getChildVertex() != null && edge.getParentVertex() != null)
			{
				String edgeType = getAnnotationSafe(edge, "type");
				String srcType = getAnnotationSafe(edge.getChildVertex(), "type");
				String dstType = getAnnotationSafe(edge.getParentVertex(), "type");
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				newEdge.addAnnotation("type", getOPMEdgeTypeEquivalentToProvEdgeType(edgeType));
				newEdge.getChildVertex().addAnnotation("type", getOPMVertexTypeEquivalentToProvVertexType(srcType));
				newEdge.getParentVertex().addAnnotation("type", getOPMVertexTypeEquivalentToProvVertexType(dstType));
				resultGraph.putVertex(newEdge.getChildVertex());
				resultGraph.putVertex(newEdge.getParentVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		
		return resultGraph;
	}
	
	private String getOPMEdgeTypeEquivalentToProvEdgeType(String provEdgeType)
	{
		if(prov2OPMEdgeMappings.containsKey(provEdgeType))
		{
			return prov2OPMEdgeMappings.get(provEdgeType);
		}
		else
		{
			logger.log(Level.SEVERE, "No opm equivalent edge type for prov edge type: " + provEdgeType);

			return provEdgeType;
		}
	}
	
	private String getOPMVertexTypeEquivalentToProvVertexType(String provVertexType)
	{
		if(prov2OPMVertexMappings.containsKey(provVertexType))
		{
			return prov2OPMVertexMappings.get(provVertexType);
		}
		else
		{
			logger.log(Level.SEVERE, "No opm equivalent vertex type for prov vertex type: " + provVertexType);

			return provVertexType;
		}
	}
}
