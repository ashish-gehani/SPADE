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

//remove units and edges to units
public class NoUnits extends AbstractTransformer
{

	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		Graph resultGraph = new Graph();
		for(AbstractEdge edge : graph.edgeSet())
		{
			if(getAnnotationSafe(edge, OPMConstants.EDGE_OPERATION).equals(OPMConstants.OPERATION_UNIT))
			{
				continue;
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge, OPMConstants.PROCESS_UNIT);
			if(newEdge != null && newEdge.getChildVertex() != null && newEdge.getParentVertex() != null)
			{
				resultGraph.putVertex(newEdge.getChildVertex());
				resultGraph.putVertex(newEdge.getParentVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		return resultGraph;
	}
}
