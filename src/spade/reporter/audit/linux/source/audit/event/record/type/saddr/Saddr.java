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
package spade.reporter.audit.linux.source.audit.event.record.type.saddr;

/**
 * Hex-encoded binary representation of a struct sockaddr_storage,
 * as serialized by the kernel module via bin2hex.
 *
 * Family is determined by the leading byte:
 *   01 = AF_UNIX, 02 = AF_INET (IPv4), 0A = AF_INET6 (IPv6), 10 = AF_NETLINK.
 *
 * Use {@link #parse(String)} to obtain the appropriate subclass.
 */
public abstract class Saddr{

	public enum Family{ IPV4, IPV6, UNIX, NETLINK, UNKNOWN }

	static final String PREFIX_IPV4    = "02";
	static final String PREFIX_IPV6    = "0A";
	static final String PREFIX_UNIX    = "01";
	static final String PREFIX_NETLINK = "10";

	private final String hex;
	private final Family family;

	Saddr(final String hex, final Family family){
		this.hex = hex;
		this.family = family;
	}

	public static Saddr parse(final String hex){
		if(hex == null){
			throw new IllegalArgumentException("hex cannot be null");
		}
		if(hex.startsWith(PREFIX_IPV4)){
			return IPv4Saddr.create(hex);
		}else if(hex.startsWith(PREFIX_IPV6)){
			return IPv6Saddr.create(hex);
		}else if(hex.startsWith(PREFIX_UNIX)){
			return UnixSaddr.create(hex);
		}else if(hex.startsWith(PREFIX_NETLINK)){
			return new NetlinkSaddr(hex);
		}else{
			return new UnknownSaddr(hex);
		}
	}

	public String getHex(){
		return hex;
	}

	public Family getFamily(){
		return family;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Saddr)) return false;
		return this.hex.equals(((Saddr) obj).hex);
	}

	@Override
	public int hashCode(){
		return hex.hashCode();
	}
}
