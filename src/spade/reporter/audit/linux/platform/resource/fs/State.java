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
package spade.reporter.audit.linux.platform.resource.fs;

import spade.reporter.audit.core.platform.util.datastore.DataStore;
import spade.reporter.audit.linux.type.fs.Permission;

public class State extends spade.reporter.audit.linux.platform.resource.State{

	private Permission permission;

	public State(
		final VersionedID id,
		final DataStore dataStore,
		final Permission permission
	){
		super(id, dataStore);
		this.permission = permission;
	}

	public State(final State other){
		this(
			new VersionedID((VersionedID) other.getId()),
			new DataStore(other.getDataStore()),
			other.permission
		);
	}

	@Override
	public State copyWithVersionId(final spade.reporter.audit.linux.platform.resource.VersionedID newId){
		if(newId == null){
			throw new IllegalArgumentException("newId cannot be NULL");
		}
		return new State(
			(VersionedID) newId,
			new DataStore(this.getDataStore()),
			this.permission
		);
	}

	public boolean hasPermission(){
		return permission != null;
	}

	public void setPermission(final Permission permission){
		this.permission = permission;
	}

	public Permission getPermission(){
		return permission;
	}

}
