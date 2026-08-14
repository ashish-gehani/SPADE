/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2022 SRI International

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
package spade.utility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SkeletonGraph{

	private final Map<String, Vertex> vertices = new HashMap<>();

	public void putVertex(final String hash){
		if(hash == null) {
			return;
		}
		if(vertices.get(hash) == null){
			vertices.put(hash, new Vertex(hash));
		}
	}

	// child (from) -> parent (to)
	public boolean putEdge(final String from, final String to){
		putVertex(from);
		putVertex(to);
		final Vertex fromVertex = vertices.get(from);
		final Vertex toVertex = vertices.get(to);
		fromVertex.addOutgoing(toVertex);
		toVertex.addIncoming(fromVertex);
		return true;
	}

	public boolean hasPath(final String from, final String to){
		if(from == null || to == null){
			return false;
		}
		final Vertex fromVertex = vertices.get(from);
		final Vertex toVertex = vertices.get(to);
		if(fromVertex == null || toVertex == null){
			return false;
		}
		final Set<Vertex> visited = new HashSet<>();
		final LinkedList<Vertex> remaining = new LinkedList<>();
		remaining.addAll(fromVertex.getOutgoing());
		while(!remaining.isEmpty()){
			final Vertex currentVertex = remaining.pop();
			if(currentVertex.hasTheSameHash(to)){
				return true;
			}
			if(visited.contains(currentVertex)){
				continue;
			}
			visited.add(currentVertex);
			remaining.addAll(currentVertex.getOutgoing());
		}
		return false;
	}

	public boolean willCreateCycle(final String from, final String to){
		if(Objects.equals(from, to)){
			return true;
		}
		return hasPath(to, from);
	}

	private static class Vertex{
		private final String hash;
		private final Set<Vertex> outgoing = new HashSet<>(), incoming = new HashSet<>();

		private Vertex(final String hash){
			this.hash = hash;
		}

		private boolean hasTheSameHash(final String otherHash){
			return Objects.equals(this.hash, otherHash);
		}

		private void addOutgoing(final Vertex vertex){
			outgoing.add(vertex);
		}

		private Set<Vertex> getOutgoing(){
			return this.outgoing;
		}

		private void addIncoming(final Vertex child){
			incoming.add(child);
		}

		private Set<Vertex> getIncoming(){
			return this.incoming;
		}

		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + Objects.hash(hash);
			return result;
		}

		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			Vertex other = (Vertex)obj;
			return Objects.equals(hash, other.hash);
		}

		@Override
		public String toString(){
			return hash;
		}
	}

}
