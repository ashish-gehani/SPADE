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

public abstract class AbstractFilter {

    private AbstractFilter nextFilter;

    public void setNextFilter(AbstractFilter next) {
        nextFilter = next;
    }

    public void putInNextFilter(AbstractVertex vertex) {
        nextFilter.putVertex(vertex);
    }

    public void putInNextFilter(AbstractEdge edge) {
        nextFilter.putEdge(edge);
    }

    public void putVertex(AbstractVertex incomingVertex) {
        return;
    }

    public void putEdge(AbstractEdge incomingEdge) {
        return;
    }
}
