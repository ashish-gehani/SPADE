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
package spade.reporter.audit.linux.process.file.descriptor.type;

public abstract class FdPair extends Descriptor{

	public final String tgid;
	public final String fd0;
	public final String fd1;

	protected FdPair(
		final Type type,
		final String tgid,
		final String fd0,
		final String fd1
	){
		super(type);
		if(tgid == null){
			throw new IllegalArgumentException("tgid cannot be NULL");
		}
		if(fd0 == null){
			throw new IllegalArgumentException("fd0 cannot be NULL");
		}
		if(fd1 == null){
			throw new IllegalArgumentException("fd1 cannot be NULL");
		}
		this.tgid = tgid;
		this.fd0 = fd0;
		this.fd1 = fd1;
	}

}
