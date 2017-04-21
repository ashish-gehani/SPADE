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
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;

public class CycleAvoidance extends AbstractFilter {

    private final HashMap<AbstractVertex, HashSet<AbstractVertex>> ancestors;

    public CycleAvoidance() {
        ancestors = new HashMap<>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        putInNextFilter(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        AbstractVertex childVertex = incomingEdge.getChildVertex();
        AbstractVertex parentVertex = incomingEdge.getParentVertex();
        if (ancestors.containsKey(parentVertex)) {
            HashSet<AbstractVertex> tempSet = ancestors.get(parentVertex);
            if (tempSet.contains(childVertex) == false) {
                tempSet.add(childVertex);
                putInNextFilter(incomingEdge);
            }
        } else {
            HashSet<AbstractVertex> tempSet = new HashSet<>();
            tempSet.add(childVertex);
            ancestors.put(parentVertex, tempSet);
            putInNextFilter(incomingEdge);
        }
    }
}
