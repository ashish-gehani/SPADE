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

public abstract class SystemV extends Descriptor{

	public final String id;
	public final String ouid;
	public final String ogid;
	public final String ipcNamespace;

	protected SystemV(
		final Type type,
		final String id,
		final String ouid,
		final String ogid,
		final String ipcNamespace
	){
		super(type);
		if(id == null){
			throw new IllegalArgumentException("id cannot be NULL");
		}
		if(ouid == null){
			throw new IllegalArgumentException("ouid cannot be NULL");
		}
		if(ogid == null){
			throw new IllegalArgumentException("ogid cannot be NULL");
		}
		if(ipcNamespace == null){
			throw new IllegalArgumentException("ipcNamespace cannot be NULL");
		}
		this.id = id;
		this.ouid = ouid;
		this.ogid = ogid;
		this.ipcNamespace = ipcNamespace;
	}

}
