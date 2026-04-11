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
package spade.reporter.audit.linux.platform.namespace;

import spade.reporter.audit.linux.platform.type.fs.Inode;

public class Tuple implements Comparable<Tuple>{

	private final Inode mount;
	private final Inode user;
	private final Inode net;
	private final Inode pid;
	private final Inode pidChildren;
	private final Inode ipc;
	private final Inode cgroup;

	public Tuple(final Inode mount, final Inode user, final Inode net, final Inode pid,
			final Inode pidChildren, final Inode ipc, final Inode cgroup){
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

	public Inode getMount(){ return mount; }
	public Inode getUser(){ return user; }
	public Inode getNet(){ return net; }
	public Inode getPid(){ return pid; }
	public Inode getPidChildren(){ return pidChildren; }
	public Inode getIpc(){ return ipc; }
	public Inode getCgroup(){ return cgroup; }

	@Override
	public int compareTo(final Tuple other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		int cmp;
		cmp = Long.compare(this.mount.getValue(), other.mount.getValue());             if(cmp != 0) return cmp;
		cmp = Long.compare(this.user.getValue(), other.user.getValue());               if(cmp != 0) return cmp;
		cmp = Long.compare(this.net.getValue(), other.net.getValue());                 if(cmp != 0) return cmp;
		cmp = Long.compare(this.pid.getValue(), other.pid.getValue());                 if(cmp != 0) return cmp;
		cmp = Long.compare(this.pidChildren.getValue(), other.pidChildren.getValue()); if(cmp != 0) return cmp;
		cmp = Long.compare(this.ipc.getValue(), other.ipc.getValue());                 if(cmp != 0) return cmp;
		return Long.compare(this.cgroup.getValue(), other.cgroup.getValue());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Tuple)) return false;
		final Tuple other = (Tuple) obj;
		return this.mount.getValue() == other.mount.getValue()
				&& this.user.getValue() == other.user.getValue()
				&& this.net.getValue() == other.net.getValue()
				&& this.pid.getValue() == other.pid.getValue()
				&& this.pidChildren.getValue() == other.pidChildren.getValue()
				&& this.ipc.getValue() == other.ipc.getValue()
				&& this.cgroup.getValue() == other.cgroup.getValue();
	}

	@Override
	public int hashCode(){
		int result = Long.hashCode(mount.getValue());
		result = 31 * result + Long.hashCode(user.getValue());
		result = 31 * result + Long.hashCode(net.getValue());
		result = 31 * result + Long.hashCode(pid.getValue());
		result = 31 * result + Long.hashCode(pidChildren.getValue());
		result = 31 * result + Long.hashCode(ipc.getValue());
		result = 31 * result + Long.hashCode(cgroup.getValue());
		return result;
	}

}
