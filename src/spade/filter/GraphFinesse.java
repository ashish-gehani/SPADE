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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;

public class GraphFinesse extends AbstractFilter {

    private final HashMap<AbstractVertex, HashSet<AbstractVertex>> edges;

    public GraphFinesse() {
        edges = new HashMap<>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        putInNextFilter(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        AbstractVertex childVertex = incomingEdge.getChildVertex();
        AbstractVertex parentVertex = incomingEdge.getParentVertex();
        if (edges.containsKey(childVertex)) {
            HashSet<AbstractVertex> checkSet = edges.get(childVertex);
            if (checkSet.contains(parentVertex)) {
                return;
            }
        }

        if (edges.get(parentVertex) == null) {
            HashSet<AbstractVertex> tempSet = new HashSet<>();
            tempSet.add(childVertex);
            edges.put(parentVertex, tempSet);
            if (edges.containsKey(childVertex)) {
                HashSet<AbstractVertex> copytempSet = edges.get(childVertex);
                Iterator iterator = copytempSet.iterator();
                while (iterator.hasNext()) {
                    tempSet.add((AbstractVertex) iterator.next());
                }
            }
            putInNextFilter(incomingEdge);
        } else {
            HashSet<AbstractVertex> tempSet = edges.get(parentVertex);
            if (tempSet.add(childVertex)) {
                if (edges.containsKey(childVertex)) {
                    HashSet<AbstractVertex> copytempSet = edges.get(childVertex);
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
