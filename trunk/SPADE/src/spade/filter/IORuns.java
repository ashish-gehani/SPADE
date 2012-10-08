/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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

import java.util.*;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.vertex.opm.Artifact;

public class IORuns extends AbstractFilter {

    private final int BUFFER_SIZE = 2000;
//    private final String processKey = "pid";
    private final String artifactKey = "location";
    private Map<String, HashSet<String>> writes;
    private Map<String, HashSet<String>> reads;
    private Queue<AbstractVertex> vertexBuffer;
//    private Map<String, AbstractVertex> vertexMap;

    public IORuns() {
        writes = new HashMap<String, HashSet<String>>();
        reads = new HashMap<String, HashSet<String>>();
        vertexBuffer = new LinkedList<AbstractVertex>();
//        vertexMap = new HashMap<String, AbstractVertex>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        if (incomingVertex instanceof Artifact) {
            vertexBuffer.add(incomingVertex);
//            vertexMap.put(incomingVertex.getAnnotation(artifactKey), incomingVertex);
//        } else if (incomingVertex instanceof Process) {
//            vertexMap.put(Integer.toString(incomingVertex.hashCode()), incomingVertex);
        } else {
            putInNextFilter(incomingVertex);
            return;
        }
        if (vertexBuffer.size() > BUFFER_SIZE) {
            AbstractVertex removed = vertexBuffer.remove();
            Logger.getLogger("IORuns").warning("*** Vertex Buffer full. Dropping! )))");
//            if (removed instanceof Artifact) {
//                vertexMap.remove(removed.getAnnotation(artifactKey));
//            } else if (removed instanceof Process) {
//                vertexMap.remove(Integer.toString(removed.hashCode()));
//            }
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        if (incomingEdge instanceof Used) {
            Used usedEdge = (Used) incomingEdge;
            String fileVertexHash = usedEdge.getDestinationVertex().getAnnotation(artifactKey);
            String processVertexHash = Integer.toString(usedEdge.getSourceVertex().hashCode());
            if (!reads.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = new HashSet<String>();
                tempSet.add(processVertexHash);
                reads.put(fileVertexHash, tempSet);
            } else {
                HashSet<String> tempSet = reads.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    vertexBuffer.remove(usedEdge.getDestinationVertex());
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
//            vertexBuffer.remove(usedEdge.getSourceVertex());
            vertexBuffer.remove(usedEdge.getDestinationVertex());
//            putInNextFilter(usedEdge.getSourceVertex());
            putInNextFilter(usedEdge.getDestinationVertex());
            putInNextFilter(usedEdge);
            if (writes.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = writes.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else if (incomingEdge instanceof WasGeneratedBy) {
            WasGeneratedBy wgb = (WasGeneratedBy) incomingEdge;
            String fileVertexHash = wgb.getSourceVertex().getAnnotation(artifactKey);
            String processVertexHash = Integer.toString(wgb.getDestinationVertex().hashCode());
            if (!writes.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = new HashSet<String>();
                tempSet.add(processVertexHash);
                writes.put(fileVertexHash, tempSet);
            } else {
                HashSet<String> tempSet = writes.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    vertexBuffer.remove(wgb.getSourceVertex());
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
            vertexBuffer.remove(wgb.getSourceVertex());
//            vertexBuffer.remove(wgb.getDestinationVertex());
            putInNextFilter(wgb.getSourceVertex());
//            putInNextFilter(wgb.getDestinationVertex());
            putInNextFilter(wgb);
            if (reads.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = reads.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else {
//            vertexBuffer.remove(incomingEdge.getSourceVertex());
//            vertexBuffer.remove(incomingEdge.getDestinationVertex());
//            putInNextFilter(incomingEdge.getSourceVertex());
//            putInNextFilter(incomingEdge.getDestinationVertex());
            putInNextFilter(incomingEdge);
        }
    }

    @Override
    public boolean shutdown() {

        return true;
    }
}
