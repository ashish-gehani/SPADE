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
import spade.core.Edge;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.type.Provenanceable;

public class Edgifier{

	private final Vertexifier vertexifier = new Vertexifier();

	public AbstractEdge edgify(final Event event, final Provenanceable child, final Provenanceable parent){
		if(event == null){
			throw new IllegalArgumentException("event cannot be NULL");
		}
		if(child == null){
			throw new IllegalArgumentException("child cannot be NULL");
		}
		if(parent == null){
			throw new IllegalArgumentException("parent cannot be NULL");
		}
		final AbstractVertex childVertex = vertexifier.vertexify(child);
		final AbstractVertex parentVertex = vertexifier.vertexify(parent);
		final Edge edge = new Edge(childVertex, parentVertex);
		edge.addAnnotations(event.getKeyAnnotations());
		edge.addAnnotations(event.getExtraAnnotations());
		return edge;
	}

}
