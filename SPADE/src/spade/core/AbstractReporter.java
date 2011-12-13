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

/**
 * This is the base class for reporters.
 * 
 * @author Dawood
 */
public abstract class AbstractReporter {

    private Buffer internalBuffer;
    /**
     * The arguments that a specific reporter instance is initialized with.
     */
    public String arguments;

    /**
     * This method is called by the Kernel for configuration purposes.
     * 
     * @param buffer The buffer to be set for this reporter.
     */
    public final void setBuffer(Buffer buffer) {
        internalBuffer = buffer;
    }

    /**
     * Returns the buffer associated with this reporter.
     * 
     * @return The buffer associated with this reporter.
     */
    public final Buffer getBuffer() {
        return internalBuffer;
    }

    /**
     * This method is called by the reporters to send vertices to the buffer.
     * 
     * @param vertex The vertex to be sent to the buffer.
     * @return True if the buffer accepted the vertex.
     */
    public final boolean putVertex(AbstractVertex vertex) {
        return internalBuffer.putVertex(vertex);
    }

    /**
     * This method is called by the reporters to send edges to the buffer.
     * 
     * @param edge The edge to be sent to the buffer.
     * @return True if the buffer accepted the edge.
     */
    public final boolean putEdge(AbstractEdge edge) {
        return internalBuffer.putEdge(edge);
    }

    /**
     * This method is invoked by the kernel when launching a reporter.
     * 
     * @param arguments The arguments for this reporter.
     * @return True if the reporter launched successfully.
     */
    public abstract boolean launch(String arguments);

    /**
     * This method is invoked by the kernel when shutting down a reporter.
     * 
     * @return True if the reporter was shut down successfully.
     */
    public abstract boolean shutdown();
}
