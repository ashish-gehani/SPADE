/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2010 SRI International

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

public class CycleAvoidanceFilter implements Filter {

    private HashSet<Vertex> set;
    private HashMap ancestors;
    private StorageInterface storage;

    public CycleAvoidanceFilter(StorageInterface inputStorage) {
        storage = inputStorage;
        set = new HashSet<Vertex>();
        ancestors = new HashMap();
    }

    @Override
    public void putVertex(Agent a) {
        storage.putVertex(a);
    }

    @Override
    public void putVertex(Process p) {
        storage.putVertex(p);
    }

    @Override
    public void putVertex(Artifact a) {
        storage.putVertex(a);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, Used u) {
//        AddVertex(u.getProcess());
//        AddVertex(u.getArtifact());
        AddEdge(u.getProcess(), u.getArtifact(), u);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasControlledBy wcb) {
//        AddVertex(wcb.getProcess());
//        AddVertex(wcb.getAgent());
        AddEdge(wcb.getProcess(), wcb.getAgent(), wcb);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasDerivedFrom wdf) {
//        AddVertex(wdf.getArtifact2());
//        AddVertex(wdf.getArtifact1());
        AddEdge(wdf.getArtifact2(), wdf.getArtifact1(), wdf);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasGeneratedBy wgb) {
//        AddVertex(wgb.getProcess());
//        AddVertex(wgb.getArtifact());
        AddEdge(wgb.getArtifact(), wgb.getProcess(), wgb);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasTriggeredBy wtb) {
//        AddVertex(wtb.getProcess1());
//        AddVertex(wtb.getProcess2());
        AddEdge(wtb.getProcess1(), wtb.getProcess2(), wtb);
    }

    private void AddVertex(Vertex v) {
        if (set.contains(v) == false) {
            storage.putVertex(v);
            set.add(v);
        }
    }

    private void AddEdge(Vertex v1, Vertex v2, Edge e) {
        if (ancestors.containsKey(v2)) {
            HashSet tempSet = (HashSet)ancestors.get(v2);
            if (tempSet.contains(v1) == false) {
                tempSet.add(v1);
                storage.putEdge(v1, v2, e);
            }
        } else {
            HashSet tempSet = new HashSet();
            tempSet.add(v1);
            ancestors.put(v2, tempSet);
            storage.putEdge(v1, v2, e);
        }
    }

}

