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

public class ClamProvEvent{

	public final long milliseconds;
	public final int pid;
	public final long exit;
	public final String functionName;
	public final long callSiteId;

	public final BufferKey bufferKey;

	public ClamProvEvent(long milliseconds, int pid, long callSiteId, long exit, String functionName){
		this.milliseconds = milliseconds;
		this.pid = pid;
		this.callSiteId = callSiteId;
		this.exit = exit;
		this.functionName = functionName;
		this.bufferKey = new BufferKey(pid, functionName);
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + (int)(callSiteId ^ (callSiteId >>> 32));
		result = prime * result + (int)(exit ^ (exit >>> 32));
		result = prime * result + ((functionName == null) ? 0 : functionName.hashCode());
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
		ClamProvEvent other = (ClamProvEvent)obj;
		if(callSiteId != other.callSiteId)
			return false;
		if(exit != other.exit)
			return false;
		if(functionName == null){
			if(other.functionName != null)
				return false;
		}else if(!functionName.equals(other.functionName))
			return false;
		if(milliseconds != other.milliseconds)
			return false;
		if(pid != other.pid)
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "Event [milliseconds=" + milliseconds + ", pid=" + pid + ", callSiteId=" + callSiteId + ", exit=" + exit
				+ ", functionName=" + functionName + "]";
	}

}
