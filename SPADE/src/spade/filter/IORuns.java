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
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;

public class IORuns extends AbstractFilter {

    private HashMap<Integer, HashSet<Integer>> writes;
    private HashMap<Integer, HashSet<Integer>> reads;

    public IORuns() {
        writes = new HashMap<Integer, HashSet<Integer>>();
        reads = new HashMap<Integer, HashSet<Integer>>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        putInNextFilter(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        if (incomingEdge instanceof Used) {
            Used usedEdge = (Used) incomingEdge;
            int fileVertexHash = usedEdge.getDestinationVertex().hashCode();
            int processVertexHash = usedEdge.getSourceVertex().hashCode();
            if (reads.containsKey(fileVertexHash) == false) {
                HashSet<Integer> tempSet = new HashSet<Integer>();
                tempSet.add(processVertexHash);
                reads.put(fileVertexHash, tempSet);
            } else {
                HashSet<Integer> tempSet = reads.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
            putInNextFilter(usedEdge);
            if (writes.containsKey(fileVertexHash)) {
                HashSet<Integer> tempSet = writes.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else if (incomingEdge instanceof WasGeneratedBy) {
            WasGeneratedBy wgb = (WasGeneratedBy) incomingEdge;
            int fileVertexHash = wgb.getSourceVertex().hashCode();
            int processVertexHash = wgb.getDestinationVertex().hashCode();
            if (writes.containsKey(fileVertexHash) == false) {
                HashSet<Integer> tempSet = new HashSet<Integer>();
                tempSet.add(processVertexHash);
                writes.put(fileVertexHash, tempSet);
            } else {
                HashSet<Integer> tempSet = writes.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
            putInNextFilter(wgb);
            if (reads.containsKey(fileVertexHash)) {
                HashSet<Integer> tempSet = reads.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else {
            putInNextFilter(incomingEdge);
        }
    }
}
