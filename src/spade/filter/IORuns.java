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
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.vertex.opm.Artifact;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class IORuns extends AbstractFilter {

    private final String artifactKey = "location";
    private final Map<String, HashSet<String>> writes;
    private final Map<String, HashSet<String>> reads;
    private final Queue<AbstractVertex> vertexBuffer;

    public IORuns() {
        writes = new HashMap<>();
        reads = new HashMap<>();
        vertexBuffer = new LinkedList<>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        if ((incomingVertex instanceof Artifact) && (incomingVertex.getAnnotation(artifactKey) != null)) {
            vertexBuffer.add(incomingVertex);
        } else {
            putInNextFilter(incomingVertex);
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        if ((incomingEdge instanceof Used) && (incomingEdge.getParentVertex().getAnnotation(artifactKey) != null)) {
            Used usedEdge = (Used) incomingEdge;
            String fileVertexHash = usedEdge.getParentVertex().getAnnotation(artifactKey);
            String processVertexHash = Integer.toString(usedEdge.getChildVertex().hashCode());
            if (!reads.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = new HashSet<>();
                tempSet.add(processVertexHash);
                reads.put(fileVertexHash, tempSet);
            } else {
                HashSet<String> tempSet = reads.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    vertexBuffer.remove(usedEdge.getParentVertex());
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
            vertexBuffer.remove(usedEdge.getParentVertex());
            putInNextFilter(usedEdge.getParentVertex());
            putInNextFilter(usedEdge);
            if (writes.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = writes.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else if ((incomingEdge instanceof WasGeneratedBy) && (incomingEdge.getChildVertex().getAnnotation(artifactKey) != null)) {
            WasGeneratedBy wgb = (WasGeneratedBy) incomingEdge;
            String fileVertexHash = wgb.getChildVertex().getAnnotation(artifactKey);
            String processVertexHash = Integer.toString(wgb.getParentVertex().hashCode());
            if (!writes.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = new HashSet<>();
                tempSet.add(processVertexHash);
                writes.put(fileVertexHash, tempSet);
            } else {
                HashSet<String> tempSet = writes.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    vertexBuffer.remove(wgb.getChildVertex());
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
            vertexBuffer.remove(wgb.getChildVertex());
            putInNextFilter(wgb.getChildVertex());
            putInNextFilter(wgb);
            if (reads.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = reads.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else {
            putInNextFilter(incomingEdge);
        }
    }

    @Override
    public boolean shutdown() {
        return true;
    }
}
