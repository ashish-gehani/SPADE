/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.query.quickgrail.core;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

public class RemoteGraph extends spade.core.Graph{

	private static final long serialVersionUID = 8086586645137800461L;

	public final String localName;

	public RemoteGraph(final String localName){
		this.localName = localName;
	}

	public void put(final String remoteUri, final spade.core.Graph graph){
		final String keyRemoteUri = "SPADE URI";
		for(final AbstractVertex vertex : graph.vertexSet()){
			vertex.addAnnotation(keyRemoteUri, remoteUri);
			super.putVertex(vertex);
		}
		for(final AbstractEdge edge : graph.edgeSet()){
			edge.addAnnotation(keyRemoteUri, remoteUri);
			super.putEdge(edge);
		}
	}
}
