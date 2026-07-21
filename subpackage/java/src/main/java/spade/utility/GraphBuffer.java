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
package spade.utility;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

public class GraphBuffer{

	private int maxSize;

	private final Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
	private final Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

	public void setMaxSize(final int maxSize){
		this.maxSize = maxSize;
	}

	public void add(final AbstractVertex vertex){
		synchronized(vertices){
			vertices.add(vertex);
		}
	}

	public void add(final AbstractEdge edge){
		synchronized(edges){
			edges.add(edge);
		}
	}

	public int size(){
		int size = 0;
		synchronized(vertices){
			size += vertices.size();
		}
		synchronized(edges){
			size += edges.size();
		}
		return size;
	}

	public boolean empty(){
		return size() == 0;
	}

	public boolean full(){
		return size() > maxSize;
	}

	public GraphSnapshot flush(){
		final GraphSnapshot result = new GraphSnapshot();
		if(!empty()){
			synchronized(vertices){
				result.vertices.addAll(vertices);
				vertices.clear();
			}
			synchronized(edges){
				result.edges.addAll(edges);
				edges.clear();
			}
		}
		return result;
	}

	public static class GraphSnapshot{
		private final Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
		private final Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

		public int vertexSize(){
			return vertices.size();
		}

		public int edgeSize(){
			return edges.size();
		}

		public int size(){
			return vertexSize() + edgeSize();
		}

		public void clear(){
			vertices.clear();
			edges.clear();
		}

		public Iterator<AbstractVertex> vertices(){
			return vertices.iterator();
		}

		public Iterator<AbstractEdge> edges(){
			return edges.iterator();
		}
	}
}
