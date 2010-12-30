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

public class RunFilter implements Filter {

    private StorageInterface storage;
    private HashMap writes;
    private HashMap reads;

    public RunFilter(StorageInterface inputStorage) {
        storage = inputStorage;
        writes = new HashMap();
        reads = new HashMap();
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
        String filename = v2.getAnnotationValue("filename");
        String pidname = v1.getAnnotationValue("pidname");
        if (reads.containsKey(filename) == false) {
            HashSet tempSet = new HashSet();
            tempSet.add(pidname);
            reads.put(filename, tempSet);
        } else {
            HashSet tempSet = (HashSet)reads.get(filename);
            if (tempSet.contains(pidname)) return;
            else tempSet.add(pidname);
        }
        storage.putEdge(u.getProcess(), u.getArtifact(), u);
        if (writes.containsKey(filename)) {
            HashSet tempSet = (HashSet)writes.get(filename);
            tempSet.remove(pidname);
        }
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasControlledBy wcb) {
        storage.putEdge(wcb.getProcess(), wcb.getAgent(), wcb);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasDerivedFrom wdf) {
        storage.putEdge(wdf.getArtifact2(), wdf.getArtifact1(), wdf);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasGeneratedBy wgb) {
        String filename = v1.getAnnotationValue("filename");
        String pidname = v2.getAnnotationValue("pidname");
        if (writes.containsKey(filename) == false) {
            HashSet tempSet = new HashSet();
            tempSet.add(pidname);
            writes.put(filename, tempSet);
        } else {
            HashSet tempSet = (HashSet)writes.get(filename);
            if (tempSet.contains(pidname)) return;
            else tempSet.add(pidname);
        }
        storage.putEdge(wgb.getArtifact(), wgb.getProcess(), wgb);
        if (reads.containsKey(filename)) {
            HashSet tempSet = (HashSet)reads.get(filename);
            tempSet.remove(pidname);
        }
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasTriggeredBy wtb) {
        storage.putEdge(wtb.getProcess1(), wtb.getProcess2(), wtb);
    }
}
