/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;

public class GraphFinesse extends AbstractFilter {

    private HashMap<AbstractVertex, HashSet<AbstractVertex>> edges;

    public GraphFinesse() {
        edges = new HashMap<AbstractVertex, HashSet<AbstractVertex>>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        putInNextFilter(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        AbstractVertex sourceVertex = incomingEdge.getSourceVertex();
        AbstractVertex destinationVertex = incomingEdge.getDestinationVertex();
        if (edges.containsKey(sourceVertex)) {
            HashSet<AbstractVertex> checkSet = edges.get(sourceVertex);
            if (checkSet.contains(destinationVertex)) {
                return;
            }
        }

        if (edges.get(destinationVertex) == null) {
            HashSet<AbstractVertex> tempSet = new HashSet<AbstractVertex>();
            tempSet.add(sourceVertex);
            edges.put(destinationVertex, tempSet);
            if (edges.containsKey(sourceVertex)) {
                HashSet<AbstractVertex> copytempSet = edges.get(sourceVertex);
                Iterator iterator = copytempSet.iterator();
                while (iterator.hasNext()) {
                    tempSet.add((AbstractVertex) iterator.next());
                }
            }
            putInNextFilter(incomingEdge);
        } else {
            HashSet<AbstractVertex> tempSet = edges.get(destinationVertex);
            if (tempSet.add(sourceVertex)) {
                if (edges.containsKey(sourceVertex)) {
                    HashSet<AbstractVertex> copytempSet = edges.get(sourceVertex);
                    Iterator iterator = copytempSet.iterator();
                    while (iterator.hasNext()) {
                        tempSet.add((AbstractVertex) iterator.next());
                    }
                }
                putInNextFilter(incomingEdge);
            }
        }
    }
}
