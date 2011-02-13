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

public class CycleAvoidanceFilter extends AbstractFilter {

    private HashMap ancestors;

    public CycleAvoidanceFilter() {
        ancestors = new HashMap();
    }

    @Override
    public void putVertex(Vertex v) {
        getNextFilter().putVertex(v);
    }

    @Override
    public void putEdge(Edge e) {
        Vertex v1 = e.getSrcVertex();
        Vertex v2 = e.getDstVertex();
        if (ancestors.containsKey(v2)) {
            HashSet tempSet = (HashSet) ancestors.get(v2);
            if (tempSet.contains(v1) == false) {
                tempSet.add(v1);
                getNextFilter().putEdge(e);
            }
        } else {
            HashSet tempSet = new HashSet();
            tempSet.add(v1);
            ancestors.put(v2, tempSet);
            getNextFilter().putEdge(e);
        }
    }
}
