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

public class Memory extends Descriptor{

	public final String memoryAddress;
	public final String size;
	public final String tgid;

	public Memory(
		final String memoryAddress,
		final String size,
		final String tgid
	){
		super(Type.MEMORY);
		if(memoryAddress == null){
			throw new IllegalArgumentException("memoryAddress cannot be NULL");
		}
		if(size == null){
			throw new IllegalArgumentException("size cannot be NULL");
		}
		if(tgid == null){
			throw new IllegalArgumentException("tgid cannot be NULL");
		}
		this.memoryAddress = memoryAddress;
		this.size = size;
		this.tgid = tgid;
	}

}
