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

public class NullFilter implements Filter {

    private StorageInterface storage;

    public NullFilter(StorageInterface inputStorage) {
        storage = inputStorage;
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
        storage.putEdge(u.getProcess(), u.getArtifact(), u);
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
        storage.putEdge(wgb.getArtifact(), wgb.getProcess(), wgb);
    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasTriggeredBy wtb) {
        storage.putEdge(wtb.getProcess1(), wtb.getProcess2(), wtb);
    }
}
