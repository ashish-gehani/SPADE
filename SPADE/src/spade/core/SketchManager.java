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
package spade.core;

import java.util.HashMap;

public class SketchManager {

    private Sketch localSketch;
    private HashMap<String, Sketch> remoteSketches;

    public SketchManager() {
        remoteSketches = new HashMap<String, Sketch>();
    }

    public void putVertex(AbstractVertex incomingVertex) {
        
    }

    public void putEdge(AbstractEdge incomingEdge) {

    }

    public Sketch getLocalSketch() {
        return localSketch;
    }

    public void updateRemoteSketch(String host, Sketch sketch) {
        remoteSketches.put(host, sketch);
    }

    public Sketch getRemoteSketch(String host) {
        return remoteSketches.get(host);
    }

    public void getPath(String srcVertexId, String dstVertexId) {

    }
    
}
