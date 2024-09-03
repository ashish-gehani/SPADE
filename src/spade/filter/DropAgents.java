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
package spade.filter;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;

public class DropAgents extends AbstractFilter {
    private final String cdmAgentType = "Principal";
    private final String generalAgentType = "Agent";
    private final String agentEdgeType = "Edge";

    @Override
	public void putVertex(AbstractVertex vertex){
        final String vertexType = vertex.getAnnotation("type");
		if(vertex == null || cdmAgentType.equals(vertexType) || generalAgentType.equals(vertexType)){
			return;
        }
		putInNextFilter(vertex);
	}
	
	@Override
	public void putEdge(AbstractEdge edge){
		// If an edge is connected to a dropped vertex drop the edge as well
		if(edge == null || edge.getChildVertex() == null || edge.getParentVertex() == null){
			return;
		}
		if(agentEdgeType.equals(edge.getAnnotation("type"))){
			return;
		}
        putInNextFilter(edge);
    }
}
