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
package spade.reporter.procmon.feature;

import spade.core.AbstractVertex;
import spade.reporter.procmon.ProvenanceConstant;

public class ArtifactIdentifier implements Comparable<ArtifactIdentifier>{

	public final String path;

	private ArtifactIdentifier(final String path){
		this.path = path;
	}

	public static ArtifactIdentifier get(final AbstractVertex vertex) throws Exception{
		if(vertex == null){
			throw new Exception("NULL vertex to get artifact identifier for");
		}
		final String path = vertex.getAnnotation(ProvenanceConstant.ARTIFACT_PATH);
		if(path == null){
			throw new Exception("NULL path to create artifact identifier from in vertex: " + vertex);
		}
		return new ArtifactIdentifier(path);
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
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
		ArtifactIdentifier other = (ArtifactIdentifier)obj;
		if(path == null){
			if(other.path != null)
				return false;
		}else if(!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "ArtifactIdentifier [path=" + path + "]";
	}

	@Override
	public int compareTo(final ArtifactIdentifier o){
		if(o == null){
			return 1;
		}
		return this.path.compareTo(o.path);
	}
}
