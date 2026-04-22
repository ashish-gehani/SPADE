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

import spade.reporter.audit.linux.platform.resource.Resource;
import spade.reporter.audit.linux.platform.util.device.Device;
import spade.reporter.audit.linux.platform.util.fs.Inode;

public abstract class Path extends Resource {

	private final Type pathType;
	private final Device device;
	private final Inode inode;
	private final spade.reporter.audit.linux.platform.util.fs.Path path;

	public Path(
		final Type pathType,
		final Device device,
		final Inode inode,
		final spade.reporter.audit.linux.platform.util.fs.Path path
	){
		super(spade.reporter.audit.linux.platform.resource.Type.FS);
		if(pathType == null){
			throw new IllegalArgumentException("pathType cannot be NULL");
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
		this.pathType = pathType;
		this.device = device;
		this.inode = inode;
		this.path = path;
	}

	public Type getPathType(){
		return pathType;
	}

	public Device getDevice(){
		return device;
	}

	public Inode getInode(){
		return inode;
	}

	public spade.reporter.audit.linux.platform.util.fs.Path getPath(){
		return path;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Path)) return false;
		final Path other = (Path) obj;
		return this.pathType == other.pathType
				&& this.device.equals(other.device)
				&& this.inode.getValue() == other.inode.getValue()
				&& this.path.getResolvedPath().equals(other.path.getResolvedPath());
	}

	@Override
	public int hashCode(){
		int result = pathType.hashCode();
		result = 31 * result + device.hashCode();
		result = 31 * result + Long.hashCode(inode.getValue());
		result = 31 * result + path.getResolvedPath().hashCode();
		return result;
	}

}
