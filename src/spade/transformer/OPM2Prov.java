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

public class OPM2Prov extends AbstractTransformer
{
	
	private final static Logger logger = Logger.getLogger(OPM2Prov.class.getName());
	
	private static final Map<String, String> opm2ProvEdgeMappings = new HashMap<>();
	
	private static final Map<String, String> opm2ProvVertexMappings = new HashMap<>();
	
	static
	{
		opm2ProvVertexMappings.put(OPMConstants.AGENT,"Agent");
		opm2ProvVertexMappings.put(OPMConstants.PROCESS,"Activity");
		opm2ProvVertexMappings.put(OPMConstants.ARTIFACT,"Entity");
		
		opm2ProvEdgeMappings.put(OPMConstants.USED,"Used");
		opm2ProvEdgeMappings.put(OPMConstants.WAS_CONTROLLED_BY,"WasAssociatedWith");
		opm2ProvEdgeMappings.put(OPMConstants.WAS_DERIVED_FROM,"WasDerivedFrom");
		opm2ProvEdgeMappings.put(OPMConstants.WAS_GENERATED_BY,"WasGeneratedBy");
		opm2ProvEdgeMappings.put(OPMConstants.WAS_TRIGGERED_BY,"WasInformedBy");
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
				newEdge.addAnnotation("type", getProvEdgeTypeEquivalentToOPMEdgeType(edgeType));
				newEdge.getChildVertex().addAnnotation("type", getProvVertexTypeEquivalentToOPMVertexType(srcType));
				newEdge.getParentVertex().addAnnotation("type", getProvVertexTypeEquivalentToOPMVertexType(dstType));
				resultGraph.putVertex(newEdge.getChildVertex());
				resultGraph.putVertex(newEdge.getParentVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		return resultGraph;
	}
	
	private String getProvEdgeTypeEquivalentToOPMEdgeType(String opmEdgeType)
	{
		if(opm2ProvEdgeMappings.containsKey(opmEdgeType))
		{
			return opm2ProvEdgeMappings.get(opmEdgeType);
		}
		else
		{
			logger.log(Level.SEVERE, "No prov equivalent edge type for opm edge type: " + opmEdgeType);

			return opmEdgeType;
		}
	}
	
	private String getProvVertexTypeEquivalentToOPMVertexType(String opmVertexType)
	{
		if(opm2ProvVertexMappings.containsKey(opmVertexType))
		{
			return opm2ProvVertexMappings.get(opmVertexType);
		}
		else
		{
			logger.log(Level.SEVERE, "No prov equivalent vertex type for opm vertex type: " + opmVertexType);

				return opmVertexType;
		}
	}
}
