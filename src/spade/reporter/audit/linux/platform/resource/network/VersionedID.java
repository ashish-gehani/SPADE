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
package spade.reporter.audit.linux.platform.resource.network;

import spade.reporter.audit.linux.type.namespace.ID;
import spade.reporter.audit.linux.type.network.ip.V4;
import spade.reporter.audit.linux.type.network.ip.V6;
import spade.reporter.audit.linux.type.network.transport.Address;

public class VersionedID extends spade.reporter.audit.linux.platform.resource.VersionedID{

	private final ID netns;

	public VersionedID(
		final Network network,
		final ID netns,
		final long version
	){
		super(network, version);
		if(netns == null){
			throw new IllegalArgumentException("netns cannot be NULL");
		}
		this.netns = netns;
	}

	public VersionedID(
		final Network network,
		final ID netns
	){
		super(network);
		if(netns == null){
			throw new IllegalArgumentException("netns cannot be NULL");
		}
		this.netns = netns;
	}

	public VersionedID(final VersionedID other){
		this(new Network(other.getNetwork()), new ID(other.netns), other.getVersion());
	}

	public Network getNetwork(){
		return (Network) getResource();
	}

	public ID getNetns(){
		return netns;
	}

	@Override
	public VersionedID nextVersion(){
		return new VersionedID(new Network(getNetwork()), new ID(netns), getVersion() + 1);
	}

	private static int compareAddress(final Address a, final Address b){
		int c = a.getIP().getIPType().compareTo(b.getIP().getIPType());
		if(c != 0) return c;
		String aHost = (a.getIP() instanceof V4)
			? ((V4) a.getIP()).getAddress().getHostAddress()
			: ((V6) a.getIP()).getAddress().getHostAddress();
		String bHost = (b.getIP() instanceof V4)
			? ((V4) b.getIP()).getAddress().getHostAddress()
			: ((V6) b.getIP()).getAddress().getHostAddress();
		c = aHost.compareTo(bHost);
		if(c != 0) return c;
		return Integer.compare(a.getPort(), b.getPort());
	}

	@Override
	public int compareTo(final spade.reporter.audit.linux.platform.resource.VersionedID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		if(this == other) return 0;
		if(!(other instanceof VersionedID)){
			return super.compareTo(other);
		}
		final VersionedID o = (VersionedID) other;
		int c = this.getNetwork().getProtocol().compareTo(o.getNetwork().getProtocol());
		if(c != 0) return c;
		c = compareAddress(this.getNetwork().getSrc(), o.getNetwork().getSrc());
		if(c != 0) return c;
		c = compareAddress(this.getNetwork().getDst(), o.getNetwork().getDst());
		if(c != 0) return c;
		c = this.netns.getType().compareTo(o.netns.getType());
		if(c != 0) return c;
		c = Long.compare(this.netns.getInode().getValue(), o.netns.getInode().getValue());
		if(c != 0) return c;
		return Long.compare(this.getVersion(), o.getVersion());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof VersionedID)) return false;
		final VersionedID other = (VersionedID) obj;
		return this.getNetwork().equals(other.getNetwork())
			&& this.netns.equals(other.netns)
			&& this.getVersion() == other.getVersion();
	}

	@Override
	public int hashCode(){
		int result = getNetwork().hashCode();
		result = 31 * result + netns.hashCode();
		result = 31 * result + Long.hashCode(getVersion());
		return result;
	}

}
