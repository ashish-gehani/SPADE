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
package spade.reporter.audit.process;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.OPMConstants;
import spade.reporter.audit.VertexIdentifier;

public class NamespaceIdentifier implements VertexIdentifier{

	private static final long serialVersionUID = -2857253082117578023L;
	
	public final String mount, user, net, pid, pid_children, ipc;
	
	public NamespaceIdentifier(String mount, String user, String net, String pid, String pid_children, String ipc){
		this.mount = mount;
		this.user = user;
		this.net = net;
		this.pid = pid;
		this.pid_children = pid_children;
		this.ipc = ipc;
	}
	
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> map = new HashMap<String, String>();
		if(mount != null){
			map.put(OPMConstants.PROCESS_MOUNT_NAMESPACE, mount);
		}
		if(user != null){
			map.put(OPMConstants.PROCESS_USER_NAMESPACE, user);
		}
		if(net != null){
			map.put(OPMConstants.PROCESS_NET_NAMESPACE, net);
		}
		if(pid != null){
			map.put(OPMConstants.PROCESS_PID_NAMESPACE, pid);
		}
		if(pid_children != null){
			map.put(OPMConstants.PROCESS_PID_CHILDREN_NAMESPACE, pid_children);
		}
		if(ipc != null){
			map.put(OPMConstants.PROCESS_IPC_NAMESPACE, ipc);
		}
		return map;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mount == null) ? 0 : mount.hashCode());
		result = prime * result + ((net == null) ? 0 : net.hashCode());
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
		result = prime * result + ((user == null) ? 0 : user.hashCode());
		result = prime * result + ((pid_children == null) ? 0 : pid_children.hashCode());
		result = prime * result + ((ipc == null) ? 0 : ipc.hashCode());
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
		NamespaceIdentifier other = (NamespaceIdentifier)obj;
		if(mount == null){
			if(other.mount != null)
				return false;
		}else if(!mount.equals(other.mount))
			return false;
		if(net == null){
			if(other.net != null)
				return false;
		}else if(!net.equals(other.net))
			return false;
		if(pid == null){
			if(other.pid != null)
				return false;
		}else if(!pid.equals(other.pid))
			return false;
		if(user == null){
			if(other.user != null)
				return false;
		}else if(!user.equals(other.user))
			return false;
		if(pid_children == null){
			if(other.pid_children != null)
				return false;
		}else if(!pid_children.equals(other.pid_children))
			return false;
		if(ipc == null){
			if(other.ipc != null)
				return false;
		}else if(!ipc.equals(other.ipc))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "NamespaceIdentifier [mount=" + mount + ", user=" + user + ", net=" + net + ", pid=" + pid + ", pid_children="+pid_children+", ipc=" + ipc + "]";
	}
}
