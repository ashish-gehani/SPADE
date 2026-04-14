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
package spade.reporter.audit.linux.platform.process.fd;

public class Validate{

	public static spade.reporter.audit.linux.platform.resource.Type expectedResourceType(final Type descriptorType){
		switch(descriptorType){
			case BLOCK_DEVICE:
			case CHARACTER_DEVICE:
			case DIRECTORY:
			case FILE:
			case LINK:
			case NAMED_PIPE:
			case POSIX_MESSAGE_QUEUE:
			case UNIX_SOCKET:
				return spade.reporter.audit.linux.platform.resource.Type.FS;
			case MEMORY:
				return spade.reporter.audit.linux.platform.resource.Type.MEMORY;
			case NETWORK_SOCKET:
				return spade.reporter.audit.linux.platform.resource.Type.NETWORK;
			case NETWORK_SOCKET_PAIR:
			case UNIX_SOCKET_PAIR:
			case UNNAMED_PIPE:
				return spade.reporter.audit.linux.platform.resource.Type.FD_PAIR;
			case SYSV_MESSAGE_QUEUE:
			case SYSV_SHARED_MEMORY:
				return spade.reporter.audit.linux.platform.resource.Type.SYSTEMV;
			case UNKNOWN:
				return spade.reporter.audit.linux.platform.resource.Type.UNKNOWN;
			default:
				throw new IllegalArgumentException("Unhandled descriptor type: " + descriptorType);
		}
	}

	public static spade.reporter.audit.linux.platform.resource.fs.Type expectedFsType(final Type descriptorType){
		switch(descriptorType){
			case BLOCK_DEVICE: return spade.reporter.audit.linux.platform.resource.fs.Type.BLOCK_DEVICE;
			case CHARACTER_DEVICE: return spade.reporter.audit.linux.platform.resource.fs.Type.CHARACTER_DEVICE;
			case DIRECTORY: return spade.reporter.audit.linux.platform.resource.fs.Type.DIRECTORY;
			case FILE: return spade.reporter.audit.linux.platform.resource.fs.Type.FILE;
			case LINK: return spade.reporter.audit.linux.platform.resource.fs.Type.LINK;
			case NAMED_PIPE: return spade.reporter.audit.linux.platform.resource.fs.Type.NAMED_PIPE;
			case POSIX_MESSAGE_QUEUE: return spade.reporter.audit.linux.platform.resource.fs.Type.POSIX_MESSAGE_QUEUE;
			case UNIX_SOCKET: return spade.reporter.audit.linux.platform.resource.fs.Type.UNIX_SOCKET;
			default: throw new IllegalArgumentException("Descriptor type does not map to an FS subtype: " + descriptorType);
		}
	}

	public static spade.reporter.audit.linux.platform.resource.fdpair.Type expectedFdPairType(final Type descriptorType){
		switch(descriptorType){
			case NETWORK_SOCKET_PAIR: return spade.reporter.audit.linux.platform.resource.fdpair.Type.NETWORK_SOCKET_PAIR;
			case UNIX_SOCKET_PAIR: return spade.reporter.audit.linux.platform.resource.fdpair.Type.UNIX_SOCKET_PAIR;
			case UNNAMED_PIPE: return spade.reporter.audit.linux.platform.resource.fdpair.Type.UNNAMED_PIPE;
			default: throw new IllegalArgumentException("Descriptor type does not map to an FD_PAIR subtype: " + descriptorType);
		}
	}

	public static spade.reporter.audit.linux.platform.resource.systemv.Type expectedSystemVType(final Type descriptorType){
		switch(descriptorType){
			case SYSV_MESSAGE_QUEUE: return spade.reporter.audit.linux.platform.resource.systemv.Type.SYSTEMV_MESSAGE_QUEUE;
			case SYSV_SHARED_MEMORY: return spade.reporter.audit.linux.platform.resource.systemv.Type.SYSTEMV_SHARED_MEMORY;
			default: throw new IllegalArgumentException("Descriptor type does not map to a SYSTEMV subtype: " + descriptorType);
		}
	}

	public static void validate(final Type descriptorType, final spade.reporter.audit.linux.platform.resource.Resource resource){
		final spade.reporter.audit.linux.platform.resource.Type expectedResourceType = expectedResourceType(descriptorType);
		if(resource.getType() != expectedResourceType){
			throw new IllegalArgumentException(
				"Expected resource type " + expectedResourceType + " for descriptor type " + descriptorType
				+ " but got " + resource.getType()
			);
		}
		switch(expectedResourceType){
			case FS:{
				final spade.reporter.audit.linux.platform.resource.fs.Type expectedFsType = expectedFsType(descriptorType);
				final spade.reporter.audit.linux.platform.resource.fs.Path fsResource =
					(spade.reporter.audit.linux.platform.resource.fs.Path) resource;
				if(fsResource.getPathType() != expectedFsType){
					throw new IllegalArgumentException(
						"Expected FS subtype " + expectedFsType + " for descriptor type " + descriptorType
						+ " but got " + fsResource.getPathType()
					);
				}
				break;
			}
			case FD_PAIR:{
				final spade.reporter.audit.linux.platform.resource.fdpair.Type expectedFdPairType = expectedFdPairType(descriptorType);
				final spade.reporter.audit.linux.platform.resource.fdpair.FDPair fdPairResource =
					(spade.reporter.audit.linux.platform.resource.fdpair.FDPair) resource;
				if(fdPairResource.getFdPairType() != expectedFdPairType){
					throw new IllegalArgumentException(
						"Expected FD_PAIR subtype " + expectedFdPairType + " for descriptor type " + descriptorType
						+ " but got " + fdPairResource.getFdPairType()
					);
				}
				break;
			}
			case SYSTEMV:{
				final spade.reporter.audit.linux.platform.resource.systemv.Type expectedSystemVType = expectedSystemVType(descriptorType);
				final spade.reporter.audit.linux.platform.resource.systemv.SystemV systemVResource =
					(spade.reporter.audit.linux.platform.resource.systemv.SystemV) resource;
				if(systemVResource.getSystemVType() != expectedSystemVType){
					throw new IllegalArgumentException(
						"Expected SYSTEMV subtype " + expectedSystemVType + " for descriptor type " + descriptorType
						+ " but got " + systemVResource.getSystemVType()
					);
				}
				break;
			}
			default:
				break;
		}
	}

}
