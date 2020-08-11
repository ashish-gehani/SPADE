/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2014 2015 International

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
package spade.filter;

import java.util.HashSet;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractSketch;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

public class FinalCommitFilter extends AbstractFilter{

	// Reference to the set of storages maintained by the Kernel.
	public Set<AbstractStorage> storages = new HashSet<>();
	public Set<AbstractSketch> sketches = new HashSet<>();

	// This filter is the last filter in the list so any vertices or edges
	// received by it need to be passed to the storages. On receiving any
	// provenance elements, it is passed to all storages.
	@Override
	public void putVertex(AbstractVertex incomingVertex){
		for(final AbstractStorage storage : storages){
			if(storage.putVertex(incomingVertex)){
				incrementStorageVertexCount(storage);
			}
		}
		for(AbstractSketch sketch : sketches){
			sketch.putVertex(incomingVertex);
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge){
		for(AbstractStorage storage : storages){
			if(storage.putEdge(incomingEdge)){
				incrementStorageEdgeCount(storage);
			}
		}
		for(AbstractSketch sketch : sketches){
			sketch.putEdge(incomingEdge);
		}
	}
}
