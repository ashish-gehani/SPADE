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
package spade.filter.clamprov;

import java.util.List;
import java.util.Objects;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.reporter.audit.OPMConstants;

public class AuditEvent{

	public final long milliseconds;
	public final int pid;
	public final String syscallName;
	private final AbstractEdge edge;

	public final BufferKey bufferKey;

	public AuditEvent(long milliseconds, int pid, String syscallName, AbstractEdge edge){
		this.milliseconds = milliseconds;
		this.pid = pid;
		this.syscallName = syscallName;
		this.edge = edge;
		this.bufferKey = new BufferKey(pid, syscallName);
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((edge == null) ? 0 : edge.hashCode());
		result = prime * result + ((syscallName == null) ? 0 : syscallName.hashCode());
		result = prime * result + (int)(milliseconds ^ (milliseconds >>> 32));
		result = prime * result + pid;
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
		AuditEvent other = (AuditEvent)obj;
		if(edge == null){
			if(other.edge != null)
				return false;
		}else if(!edge.equals(other.edge))
			return false;
		if(syscallName == null){
			if(other.syscallName != null)
				return false;
		}else if(!syscallName.equals(other.syscallName))
			return false;
		if(milliseconds != other.milliseconds)
			return false;
		if(pid != other.pid)
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "AuditEvent [milliseconds=" + milliseconds + ", pid=" + pid + ", syscallName=" + syscallName
				+ ", edge=" + edge + "]";
	}

	public static AuditEvent create(final AbstractEdge edge) throws Exception{
		final String secondsString = edge.getAnnotation(OPMConstants.EDGE_TIME);
		if(secondsString == null){
			return null;
		}
		final double secondsDouble;
		try{
			secondsDouble = Double.parseDouble(secondsString);
		}catch(Exception e){
			throw new Exception("Invalid value for time annotation '" + OPMConstants.EDGE_TIME + "'", e);
		}
		final long milliseconds =  (long)(secondsDouble * 1000.0);
		final AbstractVertex processVertex;
		final String edgeType = edge.type();
		Objects.requireNonNull(edgeType, "Edge type must be not null: " + edge);
		switch(edgeType){
			case Used.typeValue: processVertex = edge.getChildVertex(); break;
			case WasGeneratedBy.typeValue: processVertex = edge.getParentVertex(); break;
			default: return null; // Irrelevant type
		}
		Objects.requireNonNull(processVertex, "Process vertex must be not null: " + edge);
		final String pidString = processVertex.getAnnotation(OPMConstants.PROCESS_PID);
		Objects.requireNonNull(pidString, 
				"Process vertex must have a pid annotation '" + OPMConstants.PROCESS_PID + "': " + processVertex);
		final int pid;
		try{
			pid = Integer.parseInt(pidString);
		}catch(Exception e){
			throw new Exception("Invalid value for pid annotation '" + OPMConstants.PROCESS_PID + "'", e);
		}
		final String operationAnnotation = edge.getAnnotation(OPMConstants.EDGE_OPERATION);
		final List<String> operationTokens = OPMConstants.parseOperation(operationAnnotation);
		Objects.requireNonNull(operationTokens, 
				"Edge must have a syscall name annotation '" + OPMConstants.EDGE_OPERATION + "': " + edge);
		// Audit being run with simplify=false i.e. operation is the syscall name
		final String syscallName = operationTokens.get(0);
		return new AuditEvent(milliseconds, pid, syscallName, edge);
	}
}
