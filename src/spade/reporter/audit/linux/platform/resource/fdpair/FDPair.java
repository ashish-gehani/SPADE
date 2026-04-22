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
import spade.reporter.audit.linux.platform.util.fd.Num;

public abstract class FDPair extends Resource{

	private final Type fdPairType;
	private final Num fd0;
	private final Num fd1;

	protected FDPair(
		final Type fdPairType,
		final Num fd0,
		final Num fd1
	){
		super(spade.reporter.audit.linux.platform.resource.Type.FD_PAIR);
		if(fdPairType == null){
			throw new IllegalArgumentException("fdPairType cannot be NULL");
		}
		if(fd0 == null){
			throw new IllegalArgumentException("fd0 cannot be NULL");
		}
		if(fd1 == null){
			throw new IllegalArgumentException("fd1 cannot be NULL");
		}
		this.fdPairType = fdPairType;
		this.fd0 = fd0;
		this.fd1 = fd1;
	}

	public Type getFdPairType(){
		return fdPairType;
	}

	public Num getFd0(){
		return fd0;
	}

	public Num getFd1(){
		return fd1;
	}

}
