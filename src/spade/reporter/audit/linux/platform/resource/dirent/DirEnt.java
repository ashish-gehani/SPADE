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
package spade.reporter.audit.linux.platform.resource.dirent;

import spade.reporter.audit.linux.platform.resource.Resource;
import spade.reporter.audit.linux.platform.resource.device.Device;
import spade.reporter.audit.linux.platform.type.fs.Inode;

public abstract class DirEnt extends Resource {

	private final Type dirEntType;
	private final Device device;
	private final Inode inode;
	private final String path;

	public DirEnt(
		final Type dirEntType,
		final Device device,
		final Inode inode,
		final String path
	){
		super(spade.reporter.audit.linux.platform.resource.Type.DIRENT);
		if(dirEntType == null){
			throw new IllegalArgumentException("dirEntType cannot be NULL");
		}
		if(device == null){
			throw new IllegalArgumentException("device cannot be NULL");
		}
		if(inode == null){
			throw new IllegalArgumentException("inode cannot be NULL");
		}
		if(path == null){
			throw new IllegalArgumentException("path cannot be NULL");
		}
		this.dirEntType = dirEntType;
		this.device = device;
		this.inode = inode;
		this.path = path;
	}

	public Type getDirEntType(){
		return dirEntType;
	}

	public Device getDevice(){
		return device;
	}

	public Inode getInode(){
		return inode;
	}

	public String getPath(){
		return path;
	}

}
