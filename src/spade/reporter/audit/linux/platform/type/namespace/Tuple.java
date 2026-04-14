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
package spade.reporter.audit.linux.platform.type.namespace;

public class Tuple {

	private final ID mount;
	private final ID user;
	private final ID net;
	private final ID pid;
	private final ID pidChildren;
	private final ID ipc;
	private final ID cgroup;

	public Tuple(final ID mount, final ID user, final ID net, final ID pid,
			final ID pidChildren, final ID ipc, final ID cgroup){
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

	public ID getMount(){ return mount; }
	public ID getUser(){ return user; }
	public ID getNet(){ return net; }
	public ID getPid(){ return pid; }
	public ID getPidChildren(){ return pidChildren; }
	public ID getIpc(){ return ipc; }
	public ID getCgroup(){ return cgroup; }

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
