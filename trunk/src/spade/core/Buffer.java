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

import spade.opm.edge.Edge;
import spade.opm.vertex.Vertex;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Buffer {

    private ConcurrentLinkedQueue queue;

    public Buffer() {
        queue = new ConcurrentLinkedQueue();
    }

    public boolean putVertex(Vertex v) {
        return queue.add(v);
    }

    public boolean putEdge(Edge e) {
        return queue.add(e);
    }

    public Object getBufferElement() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
