/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2014 SRI International

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
import java.util.Map;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class ThreadAggregator extends AbstractFilter {

    Set<AbstractVertex> processes = new HashSet<AbstractVertex>();
    Map<String, AbstractVertex> aggregates = new HashMap<String, AbstractVertex>();

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        putInNextFilter(incomingVertex);
        if (incomingVertex.type().equalsIgnoreCase("Process")) {
            String tgid = incomingVertex.getAnnotation("tgid");
            if (!processes.contains(incomingVertex)) {
                if (!aggregates.containsKey(tgid)) {
                    Artifact aggregate = new Artifact();
                    aggregate.addAnnotation("location", "threadgroup:[" + tgid + "]");
                    putInNextFilter(aggregate);
                    aggregates.put(tgid, aggregate);
                }
                Artifact aggregate = (Artifact) aggregates.get(tgid);
                AbstractEdge r = new Used((Process) incomingVertex, aggregate);
                AbstractEdge e = new WasGeneratedBy(aggregate, (Process) incomingVertex);
                putInNextFilter(r);
                putInNextFilter(e);
                processes.add(incomingVertex);
            }
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        putInNextFilter(incomingEdge);
    }

}
