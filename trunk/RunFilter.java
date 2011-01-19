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

public class RunFilter implements FilterInterface {

    private StorageInterface storage;
    private HashMap writes;
    private HashMap reads;

    public RunFilter(StorageInterface inputStorage) {
        storage = inputStorage;
        writes = new HashMap();
        reads = new HashMap();
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
        String filename = u.getArtifact().getAnnotationValue("filename");
        String pidname = u.getProcess().getAnnotationValue("pidname");
        if (reads.containsKey(filename) == false) {
            HashSet tempSet = new HashSet();
            tempSet.add(pidname);
            reads.put(filename, tempSet);
        } else {
            HashSet tempSet = (HashSet)reads.get(filename);
            if (tempSet.contains(pidname)) return false;
            else tempSet.add(pidname);
        }
        storage.putEdge(u);
        if (writes.containsKey(filename)) {
            HashSet tempSet = (HashSet)writes.get(filename);
            tempSet.remove(pidname);
        }
        return true;
    }

    @Override
    public boolean putEdge(WasControlledBy wcb) {
        return storage.putEdge(wcb);
    }

    @Override
    public boolean putEdge(WasDerivedFrom wdf) {
        return storage.putEdge(wdf);
    }

    @Override
    public boolean putEdge(WasGeneratedBy wgb) {
        String filename = wgb.getArtifact().getAnnotationValue("filename");
        String pidname = wgb.getProcess().getAnnotationValue("pidname");
        if (writes.containsKey(filename) == false) {
            HashSet tempSet = new HashSet();
            tempSet.add(pidname);
            writes.put(filename, tempSet);
        } else {
            HashSet tempSet = (HashSet)writes.get(filename);
            if (tempSet.contains(pidname)) return false;
            else tempSet.add(pidname);
        }
        storage.putEdge(wgb);
        if (reads.containsKey(filename)) {
            HashSet tempSet = (HashSet)reads.get(filename);
            tempSet.remove(pidname);
        }
        return true;
    }

    @Override
    public boolean putEdge(WasTriggeredBy wtb) {
        return storage.putEdge(wtb);
    }
}
