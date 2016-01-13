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

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.DigQueryParams;
import spade.core.Graph;

//remove units and edges to units
public class NoUnits extends AbstractTransformer{

	public Graph putGraph(Graph graph, DigQueryParams digQueryParams){
		Graph resultGraph = new Graph();
		for(AbstractEdge edge : graph.edgeSet()){
			if(getAnnotationSafe(edge, "operation").equals("unit")){
				continue;
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge, "unit");
			if(newEdge != null && newEdge.getSourceVertex() != null && newEdge.getDestinationVertex() != null){
				resultGraph.putVertex(newEdge.getSourceVertex());
				resultGraph.putVertex(newEdge.getDestinationVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		return resultGraph;
	}
	
}
