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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.client.QueryParameters;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.reporter.audit.OPMConstants;

public class NoEphemeralWrites extends AbstractTransformer {

	public Graph putGraph(Graph graph, QueryParameters digQueryParams){
		
		Map<AbstractVertex, Set<String>> fileReadBy = new HashMap<AbstractVertex, Set<String>>();
		
		for(AbstractEdge edge : graph.edgeSet()){
			
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
		
			if(OPMConstants.isPathBasedArtifact(newEdge.getSourceVertex())
					|| OPMConstants.isPathBasedArtifact(newEdge.getDestinationVertex())){
				String operation = getAnnotationSafe(newEdge, OPMConstants.EDGE_OPERATION);
				if(OPMConstants.isIncomingDataOperation(operation)){
					if(fileReadBy.get(newEdge.getDestinationVertex()) == null){
						fileReadBy.put(newEdge.getDestinationVertex(), new HashSet<String>());
					}
					fileReadBy.get(newEdge.getDestinationVertex()).add(
							getAnnotationSafe(newEdge.getSourceVertex(), OPMConstants.PROCESS_PID));
				}
			}			
		}
	
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(OPMConstants.isPathBasedArtifact(newEdge.getSourceVertex()) &&
					OPMConstants.isOutgoingDataOperation(getAnnotationSafe(newEdge, OPMConstants.EDGE_OPERATION))){
				AbstractVertex vertex = newEdge.getSourceVertex();
				if((fileReadBy.get(vertex) == null) || (fileReadBy.get(vertex).size() == 1 
						&& fileReadBy.get(vertex).toArray()[0].equals(
								getAnnotationSafe(newEdge.getDestinationVertex(), OPMConstants.PROCESS_PID)))){
					continue; 
				}
			}		
			resultGraph.putVertex(newEdge.getSourceVertex());
			resultGraph.putVertex(newEdge.getDestinationVertex());
			resultGraph.putEdge(newEdge);
		}
		
		return resultGraph;
	}
	
}
