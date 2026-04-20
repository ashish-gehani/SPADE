/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.core.provenance;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

public final class ProvenanceElement{

	private final AbstractVertex vertex;
	private final AbstractEdge edge;

	private ProvenanceElement(final AbstractVertex vertex, final AbstractEdge edge){
		this.vertex = vertex;
		this.edge = edge;
	}

	public static ProvenanceElement of(final AbstractVertex vertex){
		if(vertex == null){
			throw new IllegalArgumentException("vertex cannot be NULL");
		}
		return new ProvenanceElement(vertex, null);
	}

	public static ProvenanceElement of(final AbstractEdge edge){
		if(edge == null){
			throw new IllegalArgumentException("edge cannot be NULL");
		}
		return new ProvenanceElement(null, edge);
	}

	public boolean isVertex(){
		return vertex != null;
	}

	public boolean isEdge(){
		return edge != null;
	}

	public AbstractVertex asVertex(){
		if(!isVertex()){
			throw new IllegalStateException("not a vertex");
		}
		return vertex;
	}

	public AbstractEdge asEdge(){
		if(!isEdge()){
			throw new IllegalStateException("not an edge");
		}
		return edge;
	}

}
