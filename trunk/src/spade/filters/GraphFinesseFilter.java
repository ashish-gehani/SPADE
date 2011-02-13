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

package spade.filters;

import spade.AbstractFilter;
import spade.opm.edge.Edge;
import spade.opm.vertex.Vertex;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class GraphFinesseFilter extends AbstractFilter {

    private HashMap edges;

    public GraphFinesseFilter() {
        edges = new HashMap();
    }

    @Override
    public void putVertex(Vertex v) {
        getNextFilter().putVertex(v);
    }

    @Override
    public void putEdge(Edge e) {
        Vertex v1 = e.getSrcVertex();
        Vertex v2 = e.getDstVertex();
        if (edges.containsKey(v1)) {
            HashSet checkSet = (HashSet) edges.get(v1);
            if (checkSet.contains(v2)) {
                return;
            }
        }

        if (edges.get(v2) == null) {
            HashSet tempSet = new HashSet();
            tempSet.add(v1);
            edges.put(v2, tempSet);
            if (edges.containsKey(v1)) {
                HashSet copytempSet = (HashSet) edges.get(v1);
                Iterator it = copytempSet.iterator();
                while (it.hasNext()) {
                    tempSet.add(it.next());
                }
            }
            getNextFilter().putEdge(e);
        } else {
            HashSet tempSet = (HashSet) edges.get(v2);
            if (tempSet.add(v1)) {
                if (edges.containsKey(v1)) {
                    HashSet copytempSet = (HashSet) edges.get(v1);
                    Iterator it = copytempSet.iterator();
                    while (it.hasNext()) {
                        tempSet.add(it.next());
                    }
                }
                getNextFilter().putEdge(e);
            }
        }
    }
}
