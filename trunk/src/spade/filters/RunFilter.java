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

import spade.core.AbstractFilter;
import spade.opm.edge.Edge;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.vertex.Vertex;
import java.util.HashMap;
import java.util.HashSet;

public class RunFilter extends AbstractFilter {

    private HashMap writes;
    private HashMap reads;

    public RunFilter() {
        writes = new HashMap();
        reads = new HashMap();
    }

    @Override
    public void putVertex(Vertex v) {
        getNextFilter().putVertex(v);
    }

    @Override
    public void putEdge(Edge e) {
        if (e instanceof Used) {
            Used u = (Used) e;
            int filename = u.getArtifact().hashCode();
            int pidname = u.getProcess().hashCode();
            if (reads.containsKey(filename) == false) {
                HashSet tempSet = new HashSet();
                tempSet.add(pidname);
                reads.put(filename, tempSet);
            } else {
                HashSet tempSet = (HashSet) reads.get(filename);
                if (tempSet.contains(pidname)) {
                    return;
                } else {
                    tempSet.add(pidname);
                }
            }
            getNextFilter().putEdge(u);
            if (writes.containsKey(filename)) {
                HashSet tempSet = (HashSet) writes.get(filename);
                tempSet.remove(pidname);
            }
        } else if (e instanceof WasGeneratedBy) {
            WasGeneratedBy wgb = (WasGeneratedBy) e;
            int filename = wgb.getArtifact().hashCode();
            int pidname = wgb.getProcess().hashCode();
            if (writes.containsKey(filename) == false) {
                HashSet tempSet = new HashSet();
                tempSet.add(pidname);
                writes.put(filename, tempSet);
            } else {
                HashSet tempSet = (HashSet) writes.get(filename);
                if (tempSet.contains(pidname)) {
                    return;
                } else {
                    tempSet.add(pidname);
                }
            }
            getNextFilter().putEdge(wgb);
            if (reads.containsKey(filename)) {
                HashSet tempSet = (HashSet) reads.get(filename);
                tempSet.remove(pidname);
            }
        } else {
            getNextFilter().putEdge(e);
        }
    }
}
