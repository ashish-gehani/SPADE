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
package spade.sketch;

import spade.core.AbstractEdge;
import spade.core.AbstractSketch;
import spade.core.AbstractVertex;

public class SampleSketch extends AbstractSketch {

    // The 'storage' variable is a reference to the storage to which this sketch
    // is attached. The reference can be used to call query methods, etc.
    public SampleSketch() {
        // Initialize any necessary data structures here (HashMaps, Sets, etc.)
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
