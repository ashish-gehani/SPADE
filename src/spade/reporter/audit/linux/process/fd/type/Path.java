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

public abstract class Path extends Descriptor{

	public final String path;
	public final String rootFSPath;
	public final String combinedPath;
	public final String inode;

	protected Path(
		final Type type,
		final String path,
		final String rootFSPath,
		final String combinedPath,
		final String inode
	){
		super(type);
		if(path == null){
			throw new IllegalArgumentException("path cannot be NULL");
		}
		if(rootFSPath == null){
			throw new IllegalArgumentException("rootFSPath cannot be NULL");
		}
		if(combinedPath == null){
			throw new IllegalArgumentException("combinedPath cannot be NULL");
		}
		if(inode == null){
			throw new IllegalArgumentException("inode cannot be NULL");
		}
		this.path = path;
		this.rootFSPath = rootFSPath;
		this.combinedPath = combinedPath;
		this.inode = inode;
	}

}
