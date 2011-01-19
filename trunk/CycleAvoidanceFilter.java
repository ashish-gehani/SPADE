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

public class CycleAvoidanceFilter implements FilterInterface {

    private HashSet<Vertex> set;
    private HashMap ancestors;
    private StorageInterface storage;

    public CycleAvoidanceFilter(StorageInterface inputStorage) {
        storage = inputStorage;
        set = new HashSet<Vertex>();
        ancestors = new HashMap();
    }

    @Override
    public boolean putVertex(Agent a) {
        return storage.putVertex(a);
    }

    @Override
    public boolean putVertex(Process p) {
        return storage.putVertex(p);
    }

    @Override
    public boolean putVertex(Artifact a) {
        return storage.putVertex(a);
    }

    @Override
    public boolean putEdge(Used u) {
        AddEdge(u);
        return true;
    }

    @Override
    public boolean putEdge(WasControlledBy wcb) {
        AddEdge(wcb);
        return true;
    }

    @Override
    public boolean putEdge(WasDerivedFrom wdf) {
        AddEdge(wdf);
        return true;
    }

    @Override
    public boolean putEdge(WasGeneratedBy wgb) {
        AddEdge(wgb);
        return true;
    }

    @Override
    public boolean putEdge(WasTriggeredBy wtb) {
        AddEdge(wtb);
        return true;
    }

    private void AddVertex(Vertex v) {
        if (set.contains(v) == false) {
            set.add(v);
            storage.putVertex(v);
        }
    }

    private void AddEdge(Edge e) {
        Vertex v1 = e.getSrcVertex();
        Vertex v2 = e.getDstVertex();
        if (ancestors.containsKey(v2)) {
            HashSet tempSet = (HashSet)ancestors.get(v2);
            if (tempSet.contains(v1) == false) {
                tempSet.add(v1);
                storage.putEdge(e);
            }
        } else {
            HashSet tempSet = new HashSet();
            tempSet.add(v1);
            ancestors.put(v2, tempSet);
            storage.putEdge(e);
        }
    }

}

