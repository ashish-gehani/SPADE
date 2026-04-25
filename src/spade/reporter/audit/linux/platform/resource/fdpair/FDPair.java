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
package spade.reporter.audit.linux.platform.resource.fdpair;

import spade.reporter.audit.linux.platform.resource.Resource;
import spade.reporter.audit.linux.platform.resource.Type;
import spade.reporter.audit.linux.type.fd.Num;

public abstract class FDPair extends Resource{

	private final Num fd0;
	private final Num fd1;

	protected FDPair(
		final Type type,
		final Num fd0,
		final Num fd1
	){
		super(type);
		if(fd0 == null){
			throw new IllegalArgumentException("fd0 cannot be NULL");
		}
		if(fd1 == null){
			throw new IllegalArgumentException("fd1 cannot be NULL");
		}
		this.fd0 = fd0;
		this.fd1 = fd1;
	}

	protected FDPair(final FDPair other){
		this(other.getType(), new Num(other.fd0), new Num(other.fd1));
	}

	public Num getFd0(){
		return fd0;
	}

	public Num getFd1(){
		return fd1;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof FDPair)) return false;
		final FDPair other = (FDPair) obj;
		return this.getType() == other.getType()
			&& this.fd0.equals(other.fd0)
			&& this.fd1.equals(other.fd1);
	}

	@Override
	public int hashCode(){
		int result = getType().hashCode();
		result = 31 * result + fd0.hashCode();
		result = 31 * result + fd1.hashCode();
		return result;
	}

}
