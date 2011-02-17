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

public abstract class AbstractProducer {

    private Buffer internalBuffer;

    public void setBuffer(Buffer buffer) {
        internalBuffer = buffer;
    }

    public boolean putVertex(AbstractVertex vertex) {
        return internalBuffer.putVertex(vertex);
    }
    
    public boolean putEdge(AbstractEdge edge) {
        return internalBuffer.putEdge(edge);
    }

    public boolean launch(String arguments) {
        return false;
    }

    public boolean shutdown() {
        return false;
    }
}
