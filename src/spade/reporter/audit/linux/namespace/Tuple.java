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
package spade.reporter.audit.linux.namespace;

public class Tuple implements Comparable<Tuple>{

	private final String mount;
	private final String user;
	private final String net;
	private final String pid;
	private final String pidChildren;
	private final String ipc;
	private final String cgroup;

	public Tuple(final String mount, final String user, final String net, final String pid,
			final String pidChildren, final String ipc, final String cgroup){
		if(mount == null){
			throw new IllegalArgumentException("mount cannot be NULL");
		}
		if(user == null){
			throw new IllegalArgumentException("user cannot be NULL");
		}
		if(net == null){
			throw new IllegalArgumentException("net cannot be NULL");
		}
		if(pid == null){
			throw new IllegalArgumentException("pid cannot be NULL");
		}
		if(pidChildren == null){
			throw new IllegalArgumentException("pidChildren cannot be NULL");
		}
		if(ipc == null){
			throw new IllegalArgumentException("ipc cannot be NULL");
		}
		if(cgroup == null){
			throw new IllegalArgumentException("cgroup cannot be NULL");
		}
		this.mount = mount;
		this.user = user;
		this.net = net;
		this.pid = pid;
		this.pidChildren = pidChildren;
		this.ipc = ipc;
		this.cgroup = cgroup;
	}

	public String getMount(){ return mount; }
	public String getUser(){ return user; }
	public String getNet(){ return net; }
	public String getPid(){ return pid; }
	public String getPidChildren(){ return pidChildren; }
	public String getIpc(){ return ipc; }
	public String getCgroup(){ return cgroup; }

	@Override
	public int compareTo(final Tuple other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		int cmp;
		cmp = this.mount.compareTo(other.mount); if(cmp != 0) return cmp;
		cmp = this.user.compareTo(other.user);   if(cmp != 0) return cmp;
		cmp = this.net.compareTo(other.net);     if(cmp != 0) return cmp;
		cmp = this.pid.compareTo(other.pid);     if(cmp != 0) return cmp;
		cmp = this.pidChildren.compareTo(other.pidChildren); if(cmp != 0) return cmp;
		cmp = this.ipc.compareTo(other.ipc);     if(cmp != 0) return cmp;
		return this.cgroup.compareTo(other.cgroup);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Tuple)) return false;
		final Tuple other = (Tuple) obj;
		return this.mount.equals(other.mount)
				&& this.user.equals(other.user)
				&& this.net.equals(other.net)
				&& this.pid.equals(other.pid)
				&& this.pidChildren.equals(other.pidChildren)
				&& this.ipc.equals(other.ipc)
				&& this.cgroup.equals(other.cgroup);
	}

	@Override
	public int hashCode(){
		int result = mount.hashCode();
		result = 31 * result + user.hashCode();
		result = 31 * result + net.hashCode();
		result = 31 * result + pid.hashCode();
		result = 31 * result + pidChildren.hashCode();
		result = 31 * result + ipc.hashCode();
		result = 31 * result + cgroup.hashCode();
		return result;
	}

}
