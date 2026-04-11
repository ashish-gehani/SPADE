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
package spade.reporter.audit.linux.platform.process.fd.descriptor;

import spade.reporter.audit.linux.platform.process.fd.Num;
import spade.reporter.audit.linux.platform.resource.dirent.DirEnt;

public abstract class Path extends Descriptor {

	private final DirEnt dirEnt;

	protected Path(
		final Type type,
		final Num num,
		final spade.reporter.audit.linux.platform.resource.dirent.Type expectedDirEntType,
		final DirEnt dirEnt
	){
		super(type, num);
		if(dirEnt == null){
			throw new IllegalArgumentException("dirEnt cannot be NULL");
		}
		if(dirEnt.getDirEntType() != expectedDirEntType){
			throw new IllegalArgumentException(
				"Expected dirEnt type " + expectedDirEntType + " but got " + dirEnt.getDirEntType()
			);
		}
		this.dirEnt = dirEnt;
	}

	public DirEnt getDirEnt(){
		return dirEnt;
	}

}
