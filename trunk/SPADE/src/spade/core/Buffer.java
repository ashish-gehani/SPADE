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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the buffer class which is used by reporters to send provenance
 * elements to.
 *
 * @author Dawood
 */
public class Buffer {

    private Queue<Object> queue;

    /**
     * Empty constructor for this class.
     *
     */
    public Buffer() {
        queue = new ConcurrentLinkedQueue<Object>();
    }

    /**
     * This method is called by the reporter to send vertices to this buffer.
     *
     * @param incomingVertex The vertex to be received by this buffer.
     * @return True if the buffer was successfully added to the buffer.
     */
    public boolean putVertex(AbstractVertex incomingVertex) {
        if (incomingVertex == null) {
            return false;
        } else {
            return queue.add(incomingVertex);
        }
    }

    /**
     * This method is called by the reporter to send edges to this buffer.
     *
     * @param incomingEdge The edge to be received by this buffer.
     * @return True if the edge was successfully added to the buffer.
     */
    public boolean putEdge(AbstractEdge incomingEdge) {
        if ((incomingEdge == null)
                || (incomingEdge.getSourceVertex() == null)
                || (incomingEdge.getDestinationVertex() == null)
                // Thread unsafe: || (incomingEdge.getSourceVertex().hashCode() == incomingEdge.getDestinationVertex().hashCode())) {
                || (incomingEdge.getSourceVertex() == incomingEdge.getDestinationVertex())) {
            if (incomingEdge.getSourceVertex() == null) {
                Logger.getLogger(Buffer.class.getName()).log(Level.WARNING, "Not putting edge. Source is null");
            }
            if (incomingEdge.getDestinationVertex() == null) {
                Logger.getLogger(Buffer.class.getName()).log(Level.WARNING, "Not putting edge. Dest is null");
            } else {
                Logger.getLogger(Buffer.class.getName()).log(Level.WARNING, "Not putting edge. End vertices are equal");
            }
            return false;
        } else {
            return queue.add(incomingEdge);
        }
    }

    /**
     * This method is used to extract provenance elements from the buffer.
     *
     * @return The provenance element from the head of the queue.
     */
    public Object getBufferElement() {
        return queue.poll();
    }

    /**
     * This method is used to determine whether the buffer is empty or not.
     *
     * @return True if the buffer is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
