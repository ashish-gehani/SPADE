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

import java.util.Map;
import java.util.TreeMap;

public final class QueriedEdge{

	public final String childHash;
	public final String parentHash;
	public final String edgeHash;
	private final Map<String, String> annotations = new TreeMap<String, String>();
	
	public QueriedEdge(String edgeHash, String childHash, String parentHash, 
			Map<String, String> annotations){
		this.edgeHash = edgeHash;
		this.childHash = childHash;
		this.parentHash = parentHash;
		if(annotations != null){
			this.annotations.putAll(annotations);
		}
	}
	
	public final Map<String, String> getCopyOfAnnotations(){
		return new TreeMap<String, String>(this.annotations); 
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((annotations == null) ? 0 : annotations.hashCode());
		result = prime * result + ((childHash == null) ? 0 : childHash.hashCode());
		result = prime * result + ((edgeHash == null) ? 0 : edgeHash.hashCode());
		result = prime * result + ((parentHash == null) ? 0 : parentHash.hashCode());
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
		QueriedEdge other = (QueriedEdge)obj;
		if(annotations == null){
			if(other.annotations != null)
				return false;
		}else if(!annotations.equals(other.annotations))
			return false;
		if(childHash == null){
			if(other.childHash != null)
				return false;
		}else if(!childHash.equals(other.childHash))
			return false;
		if(edgeHash == null){
			if(other.edgeHash != null)
				return false;
		}else if(!edgeHash.equals(other.edgeHash))
			return false;
		if(parentHash == null){
			if(other.parentHash != null)
				return false;
		}else if(!parentHash.equals(other.parentHash))
			return false;
		return true;
	}
}