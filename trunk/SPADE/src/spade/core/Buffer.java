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

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Buffer {

    // The buffer is essentially a queue to which vertices and edges are
    // added by the reporters and removed by the Kernel. The objects are deep
    // copied into the buffer on putEdge() and putVertex().

    private Queue<Object> queue;

    public Buffer() {
        queue = new ConcurrentLinkedQueue<Object>();
    }

    public boolean putVertex(AbstractVertex incomingVertex) {
        Vertex copyVertex = new Vertex();
        Map<String, String> annotations = incomingVertex.getAnnotations();
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = ((String) iterator.next()).trim();
            String value = ((String) annotations.get(key)).trim();
            copyVertex.addAnnotation(key, value);
        }
        return queue.add(copyVertex);
    }

    public boolean putEdge(AbstractEdge incomingEdge) {
        Vertex srcVertex = new Vertex();
        Map<String, String> srcAnnotations = incomingEdge.getSrcVertex().getAnnotations();
        for (Iterator iterator = srcAnnotations.keySet().iterator(); iterator.hasNext();) {
            String key = ((String) iterator.next()).trim();
            String value = ((String) srcAnnotations.get(key)).trim();
            srcVertex.addAnnotation(key, value);
        }
        Vertex dstVertex = new Vertex();
        Map<String, String> dstAnnotations = incomingEdge.getDstVertex().getAnnotations();
        for (Iterator iterator = dstAnnotations.keySet().iterator(); iterator.hasNext();) {
            String key = ((String) iterator.next()).trim();
            String value = ((String) dstAnnotations.get(key)).trim();
            dstVertex.addAnnotation(key, value);
        }
        Edge copyEdge = new Edge(srcVertex, dstVertex);
        Map<String, String> annotations = incomingEdge.getAnnotations();
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = ((String) iterator.next()).trim();
            String value = ((String) annotations.get(key)).trim();
            copyEdge.addAnnotation(key, value);
        }
        return queue.add(copyEdge);
    }

    public Object getBufferElement() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
