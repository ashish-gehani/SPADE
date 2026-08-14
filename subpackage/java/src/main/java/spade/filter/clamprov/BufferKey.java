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

public class BufferKey{

	public final int pid;
	public final String syscall;

	public BufferKey(int pid, String syscall){
		this.pid = pid;
		this.syscall = syscall;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + pid;
		result = prime * result + ((syscall == null) ? 0 : syscall.hashCode());
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
		BufferKey other = (BufferKey)obj;
		if(pid != other.pid)
			return false;
		if(syscall == null){
			if(other.syscall != null)
				return false;
		}else if(!syscall.equals(other.syscall))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "BufferKey [pid=" + pid + ", syscall=" + syscall + "]";
	}
}