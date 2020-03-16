/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International
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

import java.io.Serializable;

public class GraphStats implements Serializable{
	
	private static final long serialVersionUID = -5687449560082206524L;
	public final long vertices, edges;

	public GraphStats(long vertices, long edges){
		this.vertices = vertices;
		this.edges = edges;
	}

	public boolean isEmpty(){
		return vertices == 0 && edges == 0;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + (int)(edges ^ (edges >>> 32));
		result = prime * result + (int)(vertices ^ (vertices >>> 32));
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
		GraphStats other = (GraphStats)obj;
		if(edges != other.edges)
			return false;
		if(vertices != other.vertices)
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "GraphStats [vertices=" + vertices + ", edges=" + edges + "]";
	}
}