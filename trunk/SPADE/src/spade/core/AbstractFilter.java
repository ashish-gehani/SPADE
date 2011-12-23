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
 * This is the base class for filters.
 * 
 * @author Dawood
 */
public abstract class AbstractFilter {

    private AbstractFilter nextFilter;
    /**
     * The arguments that a specific filter instance is initialized with.
     */
    public String arguments;

    /**
     * This method is invoked by the kernel when initializing a filter.
     * 
     * @param arguments The arguments for this reporter.
     * @return True if the reporter launched successfully.
     */
    public boolean initialize(String arguments) {
        return true;
    }

    /**
     * This method is invoked by the kernel when shutting down a filter.
     * 
     * @return True if the reporter was shut down successfully.
     */
    public boolean shutdown() {
        return true;
    }

    /**
     * This method is used by the Kernel for configuring the filter list.
     * 
     * @param next The next filter to which elements are passed.
     */
    public final void setNextFilter(AbstractFilter next) {
        nextFilter = next;
    }

    /**
     * This method is called by the filters to send elements to the next filter.
     * 
     * @param vertex The vertex to be sent to the next filter.
     */
    public final void putInNextFilter(AbstractVertex vertex) {
        nextFilter.putVertex(vertex);
    }

    /**
     * This method is called by the filters to send elements to the next filter.
     * 
     * @param edge The edge to be sent to the next filter.
     */
    public final void putInNextFilter(AbstractEdge edge) {
        nextFilter.putEdge(edge);
    }

    /**
     * This method is called when the filter receives a vertex.
     * 
     * @param incomingVertex The vertex received by this filter.
     */
    public abstract void putVertex(AbstractVertex incomingVertex);

    /**
     * This method is called when the filter receives an edge.
     * 
     * @param incomingEdge The edge received by this filter.
     */
    public abstract void putEdge(AbstractEdge incomingEdge);
}
