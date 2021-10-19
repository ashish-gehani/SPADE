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

public class ProcessIdentifier implements Comparable<ProcessIdentifier>{

	public final String pid;

	private ProcessIdentifier(final String pid){
		this.pid = pid;
	}

	public static ProcessIdentifier get(final AbstractVertex vertex) throws Exception{
		if(vertex == null){
			throw new Exception("NULL vertex to get process identifier for");
		}
		final String pid = vertex.getAnnotation(ProvenanceConstant.PROCESS_PID);
		if(pid == null){
			throw new Exception("NULL pid to create process identifier from in vertex: " + vertex);
		}
		return new ProcessIdentifier(pid);
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
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
		ProcessIdentifier other = (ProcessIdentifier)obj;
		if(pid == null){
			if(other.pid != null)
				return false;
		}else if(!pid.equals(other.pid))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "ProcessIdentifier [pid=" + pid + "]";
	}

	@Override
	public int compareTo(final ProcessIdentifier o){
		if(o == null){
			return 1;
		}
		return this.pid.compareTo(o.pid);
	}
}
