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

import java.util.*;

public class CycleAvoidanceFilter implements FilterInterface {

    private HashMap ancestors;
    private FilterInterface next;

    public CycleAvoidanceFilter() {
        ancestors = new HashMap();
    }

    public void setNextFilter(FilterInterface n) {
        next = n;
    }

    public void putVertex(Vertex v) {
        next.putVertex(v);
    }

    public void putEdge(Edge e) {
        Vertex v1 = e.getSrcVertex();
        Vertex v2 = e.getDstVertex();
        if (ancestors.containsKey(v2)) {
            HashSet tempSet = (HashSet) ancestors.get(v2);
            if (tempSet.contains(v1) == false) {
                tempSet.add(v1);
                next.putEdge(e);
            }
        } else {
            HashSet tempSet = new HashSet();
            tempSet.add(v1);
            ancestors.put(v2, tempSet);
            next.putEdge(e);
        }
    }
}
