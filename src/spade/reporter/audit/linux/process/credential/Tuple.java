/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.linux.process.credential;

public class Tuple{

	private Process process;
	private User user;
	private Group group;

	public Tuple(final Process process, final User user, final Group group){
		setProcess(process);
		setUser(user);
		setGroup(group);
	}

	public Process getProcess(){ return process; }
	public User getUser(){ return user; }
	public Group getGroup(){ return group; }

	public void setProcess(final Process process){
		if(process == null){
			throw new IllegalArgumentException("process cannot be NULL");
		}
		this.process = process;
	}

	public void setUser(final User user){
		if(user == null){
			throw new IllegalArgumentException("user cannot be NULL");
		}
		this.user = user;
	}

	public void setGroup(final Group group){
		if(group == null){
			throw new IllegalArgumentException("group cannot be NULL");
		}
		this.group = group;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Tuple)) return false;
		final Tuple other = (Tuple) obj;
		return this.process.equals(other.process)
				&& this.user.equals(other.user)
				&& this.group.equals(other.group);
	}

	@Override
	public int hashCode(){
		int result = process.hashCode();
		result = 31 * result + user.hashCode();
		result = 31 * result + group.hashCode();
		return result;
	}

}
