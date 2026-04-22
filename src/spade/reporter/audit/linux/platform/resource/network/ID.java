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

import spade.reporter.audit.linux.platform.process.State;
import spade.reporter.audit.linux.platform.util.network.ip.V4;
import spade.reporter.audit.linux.platform.util.network.ip.V6;
import spade.reporter.audit.linux.platform.util.network.transport.Address;
import spade.reporter.audit.linux.platform.util.network.transport.Protocol;

public class ID extends spade.reporter.audit.linux.platform.resource.ID{

	public ID(final Network network, final State processState){
		super(network, processState);
	}

	public Network getNetwork(){
		return (Network) getResource();
	}

	private spade.reporter.audit.linux.platform.resource.Type type(){
		return getResource().getType();
	}

	private Address src(){
		return getNetwork().getSrc();
	}

	private Address dst(){
		return getNetwork().getDst();
	}

	private Protocol protocol(){
		return getNetwork().getProtocol();
	}

	private spade.reporter.audit.linux.platform.util.namespace.ID netns(){
		return getProcessState().getNamespace().getNet();
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

	private static int compareNetns(
		final spade.reporter.audit.linux.platform.util.namespace.ID a,
		final spade.reporter.audit.linux.platform.util.namespace.ID b
	){
		int c = a.getType().compareTo(b.getType());
		if(c != 0) return c;
		return Long.compare(a.getInode().getValue(), b.getInode().getValue());
	}

	@Override
	public int compareTo(final spade.reporter.audit.linux.platform.resource.ID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		if(this == other) return 0;
		if(!(other instanceof ID)){
			return Integer.compare(
				this.getResource().getType().ordinal(),
				other.getResource().getType().ordinal()
			);
		}
		final ID o = (ID) other;
		int c = this.type().compareTo(o.type());
		if(c != 0) return c;
		c = this.protocol().compareTo(o.protocol());
		if(c != 0) return c;
		c = compareAddress(this.src(), o.src());
		if(c != 0) return c;
		c = compareAddress(this.dst(), o.dst());
		if(c != 0) return c;
		return compareNetns(this.netns(), o.netns());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return this.type() == other.type()
			&& this.protocol() == other.protocol()
			&& this.src().equals(other.src())
			&& this.dst().equals(other.dst())
			&& this.netns().equals(other.netns());
	}

	@Override
	public int hashCode(){
		int result = type().hashCode();
		result = 31 * result + protocol().hashCode();
		result = 31 * result + src().hashCode();
		result = 31 * result + dst().hashCode();
		result = 31 * result + netns().hashCode();
		return result;
	}

}
