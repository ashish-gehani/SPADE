/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

package spade.client;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import spade.core.AbstractVertex;
import spade.query.quickgrail.instruction.GetLineage;

public class QueryMetaData implements Serializable{

	private static final long serialVersionUID = -6071212141422611083L;
	
	private Set<AbstractVertex> rootVertices = new HashSet<>();
	private Integer maxLength;
	private GetLineage.Direction direction;

	public Set<AbstractVertex> getRootVertices(){
		return rootVertices;
	}

	public Integer getMaxLength(){
		return maxLength;
	}

	public GetLineage.Direction getDirection(){
		return direction;
	}

	public void addRootVertices(Set<AbstractVertex> rootVertices){
		if(rootVertices != null){
			this.rootVertices.addAll(rootVertices);
		}
	}

	public void setMaxLength(Integer maxLength){
		this.maxLength = maxLength;
	}

	public void setDirection(GetLineage.Direction direction){
		this.direction = direction;
	}

	@Override
	public String toString(){
		return "QueryMetaData [rootVertices=" + rootVertices + ", maxLength=" + maxLength + ", direction=" + direction
				+ "]";
	}
}
