/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.reporter.audit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

/**
 * All constants and convenience functions for OPM related to Audit data.
 */
public class OPMConstants {

	public static final String 
	
			OPM = "opm",
			// General annotations
			TYPE = "type",
			AGENT = "Agent",
			PROCESS = "Process",
			ARTIFACT = "Artifact",
			WAS_CONTROLLED_BY = "WasControlledBy",
			WAS_TRIGGERED_BY = "WasTriggeredBy",
			WAS_GENERATED_BY = "WasGeneratedBy",
			WAS_DERIVED_FROM = "WasDerivedFrom",
			USED = "Used",
			SOURCE = "source",
			
			// Allowed source annotation values
			SOURCE_AUDIT_SYSCALL = "syscall",
			SOURCE_PROCFS = "/proc",
			SOURCE_BEEP = "beep",
			SOURCE_AUDIT_NETFILTER = "netfilter",
			
			// Agent specific annotations
			AGENT_EGID = "egid",
			AGENT_EUID = "euid",
			AGENT_FSGID = "fsgid",
			AGENT_FSUID = "fsuid",
			AGENT_GID = "gid",
			AGENT_SGID = "sgid",
			AGENT_SUID = "suid",
			AGENT_UID = "uid",

			// Process specific annotations
			PROCESS_COMMAND_LINE = "command line",
			PROCESS_COUNT = "count",
			PROCESS_CWD = "cwd",
			PROCESS_ITERATION = "iteration",
			PROCESS_NAME = "name",
			PROCESS_PID = "pid",
			PROCESS_PPID = "ppid",
			PROCESS_SEEN_TIME = "seen time",
			PROCESS_START_TIME = "start time",
			PROCESS_UNIT = "unit",
			
			// Artifact specific annotations
			ARTIFACT_REMOTE_ADDRESS = "remote address",
			ARTIFACT_REMOTE_PORT = "remote port",
			ARTIFACT_EPOCH = "epoch",
			ARTIFACT_FD = "fd",
			ARTIFACT_MEMORY_ADDRESS = "memory address",
			ARTIFACT_PATH = "path",
			ARTIFACT_PERMISSIONS = "permissions", 
			ARTIFACT_PID = PROCESS_PID,
			ARTIFACT_PROTOCOL = "protocol",
			ARTIFACT_READ_FD = "read fd",
			ARTIFACT_SIZE = "size",
			ARTIFACT_LOCAL_ADDRESS = "local address",
			ARTIFACT_LOCAL_PORT = "local port",
			ARTIFACT_SUBTYPE = "subtype",
			ARTIFACT_TGID = "tgid",
			ARTIFACT_VERSION = "version",
			ARTIFACT_WRITE_FD = "write fd",
			ARTIFACT_HOST_TYPE = "host type",
			ARTIFACT_HOST_TYPE_DESKTOP = "desktop",
			ARTIFACT_HOST_NETWORK_NAME = "host name",
			ARTIFACT_HOST_OPERATING_SYSTEM = "host operating system",
			ARTIFACT_HOST_SERIAL_NUMBER = "serial number",
			ARTIFACT_HOST_INTERFACES_COUNT = "interface count",
			ARTIFACT_HOST_INTERFACE_NAME_PREFIX = "interface name",
			ARTIFACT_HOST_INTERFACE_MAC_ADDRESS_PREFIX = "interface mac address",
			ARTIFACT_HOST_INTERFACE_IP_ADDRESSES_PREFIX = "interface ip addresses",

			// Allowed subtype annotation values
			SUBTYPE_FILE = "file",
			SUBTYPE_DIRECTORY = "directory",
			SUBTYPE_BLOCK_DEVICE = "block device",
			SUBTYPE_CHARACTER_DEVICE = "character device",
			SUBTYPE_LINK = "link",
			SUBTYPE_MEMORY_ADDRESS = "memory",
			SUBTYPE_NAMED_PIPE = "named pipe",
			SUBTYPE_NETWORK_SOCKET = "network socket",
			SUBTYPE_UNIX_SOCKET = "unix socket",
			SUBTYPE_UNKNOWN = "unknown",
			SUBTYPE_UNNAMED_PIPE = "unnamed pipe",
			
			// General edge annotations
			EDGE_EVENT_ID = "event id",
			EDGE_FLAGS = "flags",
			EDGE_MODE = "mode",
			EDGE_OFFSET = "offset",
			EDGE_OPERATION = "operation",
			EDGE_PID = PROCESS_PID,
			EDGE_PROTECTION = "protection",
			EDGE_SIZE = ARTIFACT_SIZE,
			EDGE_TIME = "time",
			
			// All operations
			OPERATION_ACCEPT = "accept",
			OPERATION_BIND = "bind",
			OPERATION_CHMOD = "chmod",
			OPERATION_CLONE = "clone",
			OPERATION_CLOSE =  "close",
			OPERATION_CONNECT = "connect",
			OPERATION_CREATE = "create",
			OPERATION_DUP = "dup",
			OPERATION_EXECVE = "execve",
			OPERATION_EXIT = "exit",
			OPERATION_FCNTL = "fcntl",
			OPERATION_FORK = "fork",
			OPERATION_LINK = "link",
			OPERATION_LOAD = "load",
			OPERATION_MKNOD = "mknod",
			OPERATION_MMAP = "mmap",
			OPERATION_MPROTECT = "mprotect",
			OPERATION_OPEN = "open",
			OPERATION_PIPE = "pipe",
			OPERATION_READ = "read",
			OPERATION_RECV = "recv",
			OPERATION_RENAME = "rename",
			OPERATION_SEND = "send",
			OPERATION_SETGID = "setgid",
			OPERATION_SETUID = "setuid",
			OPERATION_TRUNCATE = "truncate",
			OPERATION_UNIT = "unit",
			OPERATION_UNIT_DEPENDENCY = "unit dependency",
			OPERATION_UNKNOWN = "unknown",
			OPERATION_UNLINK = "unlink",
			OPERATION_UPDATE = "update",
			OPERATION_WRITE = "write",
			OPERATION_TEE = "tee",
			OPERATION_SPLICE = "splice",
			OPERATION_VMSPLICE = "vmsplice",
			OPERATION_INIT_MODULE = "init_module",
			OPERATION_FINIT_MODULE = "finit_module";
		
	private static final Logger logger = Logger.getLogger(OPMConstants.class.getName());
	
	// A map from syscall to operation for easy lookup
	private static final Map<SYSCALL, String> syscallToOperation = 
			new HashMap<SYSCALL, String>();
	
	static{
		addSyscallsToOperations(OPERATION_ACCEPT, SYSCALL.ACCEPT, SYSCALL.ACCEPT4);
		addSyscallsToOperations(OPERATION_BIND, SYSCALL.BIND);
		addSyscallsToOperations(OPERATION_CHMOD, SYSCALL.CHMOD, SYSCALL.FCHMOD, SYSCALL.FCHMODAT);
		addSyscallsToOperations(OPERATION_CLONE, SYSCALL.CLONE);
		addSyscallsToOperations(OPERATION_CLOSE, SYSCALL.CLOSE);
		addSyscallsToOperations(OPERATION_CONNECT, SYSCALL.CONNECT);
		addSyscallsToOperations(OPERATION_CREATE, SYSCALL.CREATE, SYSCALL.CREAT);
		addSyscallsToOperations(OPERATION_DUP, SYSCALL.DUP, SYSCALL.DUP2, SYSCALL.DUP3);
		addSyscallsToOperations(OPERATION_EXECVE, SYSCALL.EXECVE);
		addSyscallsToOperations(OPERATION_EXIT, SYSCALL.EXIT, SYSCALL.EXIT_GROUP);
		addSyscallsToOperations(OPERATION_FCNTL, SYSCALL.FCNTL);
		addSyscallsToOperations(OPERATION_FORK, SYSCALL.FORK, SYSCALL.VFORK);
		addSyscallsToOperations(OPERATION_INIT_MODULE, SYSCALL.INIT_MODULE);
		addSyscallsToOperations(OPERATION_FINIT_MODULE, SYSCALL.FINIT_MODULE);
		addSyscallsToOperations(OPERATION_LINK, SYSCALL.LINK, SYSCALL.LINKAT, SYSCALL.SYMLINK, SYSCALL.SYMLINKAT);
		addSyscallsToOperations(OPERATION_LOAD, SYSCALL.LOAD);
		addSyscallsToOperations(OPERATION_MKNOD, SYSCALL.MKNOD, SYSCALL.MKNODAT);
		addSyscallsToOperations(OPERATION_MMAP, SYSCALL.MMAP, SYSCALL.MMAP2);
		addSyscallsToOperations(OPERATION_MPROTECT, SYSCALL.MPROTECT);
		addSyscallsToOperations(OPERATION_OPEN, SYSCALL.OPEN, SYSCALL.OPENAT);
		addSyscallsToOperations(OPERATION_PIPE, SYSCALL.PIPE, SYSCALL.PIPE2);
		addSyscallsToOperations(OPERATION_READ, SYSCALL.READ, SYSCALL.READV, SYSCALL.PREAD, SYSCALL.PREADV);
		addSyscallsToOperations(OPERATION_RECV, SYSCALL.RECV, SYSCALL.RECVFROM, SYSCALL.RECVMSG);
		addSyscallsToOperations(OPERATION_RENAME, SYSCALL.RENAME, SYSCALL.RENAMEAT);
		addSyscallsToOperations(OPERATION_SEND, SYSCALL.SEND, SYSCALL.SENDMSG, SYSCALL.SENDTO);
		addSyscallsToOperations(OPERATION_SETGID, SYSCALL.SETGID, SYSCALL.SETREGID, SYSCALL.SETRESGID, SYSCALL.SETFSGID);
		addSyscallsToOperations(OPERATION_SETUID, SYSCALL.SETUID, SYSCALL.SETREUID, SYSCALL.SETRESUID, SYSCALL.SETFSUID);
		addSyscallsToOperations(OPERATION_SPLICE, SYSCALL.SPLICE);
		addSyscallsToOperations(OPERATION_TEE, SYSCALL.TEE);
		addSyscallsToOperations(OPERATION_TRUNCATE, SYSCALL.TRUNCATE, SYSCALL.FTRUNCATE);
		addSyscallsToOperations(OPERATION_UNIT, SYSCALL.UNIT);
		addSyscallsToOperations(OPERATION_UNKNOWN, SYSCALL.UNKNOWN);
		addSyscallsToOperations(OPERATION_UNLINK, SYSCALL.UNLINK, SYSCALL.UNLINKAT);
		addSyscallsToOperations(OPERATION_UPDATE, SYSCALL.UPDATE);
		addSyscallsToOperations(OPERATION_VMSPLICE, SYSCALL.VMSPLICE);
		addSyscallsToOperations(OPERATION_WRITE, SYSCALL.WRITE, SYSCALL.WRITEV, SYSCALL.PWRITE, SYSCALL.PWRITEV);
	}
	
	/**
	 * Adds the given operation against each of the syscalls in the array
	 * 
	 * If a syscall tried to overwrite an existing entry OR null syscall/operation
	 * then an exception is written to log. And must fix this issue and restart spade
	 * in that case
	 * 
	 * @param operation
	 * @param syscalls
	 */
	private static void addSyscallsToOperations(String operation, SYSCALL... syscalls){
		String error = null;
		if(!(operation == null || syscalls == null || syscalls.length == 0)){
			for(SYSCALL syscall : syscalls){
				if(syscall == null){
					error = "Null syscall cannot be mapped to an operation";
					break;
				}else{
					if(syscallToOperation.get(syscall) != null){
						error = "Operation for syscall " + syscall + " already exists";
						break;
					}else{
						syscallToOperation.put(syscall, operation);
					}
				}
			}
		}else{
			error = "Must specify correct arguments for mapping syscalls to operations";
		}
		if(error != null){
			logger.log(Level.SEVERE, null, new Exception(error));
		}
	}
	
	/**
	 * Returns true if the vertex is an Artifact and has the annotation path
	 * 
	 * @param vertex vertex to check
	 * @return true/false
	 */
	public static boolean isPathBasedArtifact(AbstractVertex vertex){
		if(vertex != null){
			if(ARTIFACT.equals(vertex.getAnnotation(TYPE))){
				if(vertex.getAnnotation(ARTIFACT_PATH) != null){
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Finds the operation given the syscall from the internal map.
	 * 
	 * Builds the operation as defined and returns it.
	 * 
	 * If unable to find or build the operation then null is returned
	 * 
	 * @param primary main system call
	 * @param secondary supporting system call
	 * @param simplify SHOULD BE TRUE ALWAYS FOR NOW
	 * @return operation
	 */
	public static String getOperation(SYSCALL primary, SYSCALL secondary, boolean simplify){
		String primaryOperation = simplify ? syscallToOperation.get(primary) : primary.toString().toLowerCase();
		String secondaryOperation = null;
		if(simplify){
			secondaryOperation = syscallToOperation.get(secondary);
		}else{
			if(secondary != null){
				secondaryOperation = secondary.toString().toLowerCase();
			}
		}
		
		if(primaryOperation == null || (secondaryOperation == null && secondary != null)){
			logger.log(Level.WARNING, "Unmapped syscall to operation. Primary: " + primary + ", Secondary: " + secondary);
			return null;
		}else{
			return buildOperation(primaryOperation, secondaryOperation);
		}
	}
	
	/**
	 * Parses the operation (if a compound one)
	 * 
	 * If this function is updated then update the function {@link #buildOperation(String, String)
	 *  buildOperation} too.
	 * 
	 * @param operation operation to parse
	 * @return list of found operations. NULL list if the format of the operation is unexpected
	 */
	private static List<String> parseOperation(String operation){
		if(operation == null || operation.trim().isEmpty()){
			return null;
		}else{
			String[] toks = operation.split("\\(");
			if(toks.length == 1){
				return Arrays.asList(toks[0].trim());
			}else if(toks.length == 2){
				return Arrays.asList(toks[0].trim(), toks[1].replaceAll("\\)", "").trim());
			}else{
				return null;
			}
		}
	}
	
	/**
	 * Builds a combined operation. If this function is updated then update
	 * the function {@link #parseOperation(String) parseOperation} too
	 * @param primary main operation
	 * @param secondary null or the secondary operation
	 * @return combined operation
	 */
	public static String buildOperation(String primary, String secondary){
		if(secondary == null){
			return primary;
		}else{
			return primary + " (" + secondary + ")"; 
		}
	}
	
	/**
	 * Returns true if the operation is mmap/link/rename read
	 * 
	 * @param operation one of the Operation as defined this class
	 * @return true/false
	 */
	public static boolean isMmapRenameLinkRead(String operation){
		List<String> operations = parseOperation(operation);
		return operations != null && operations.contains(OPERATION_READ) &&
				(operations.contains(OPERATION_MMAP) || 
				operations.contains(OPERATION_RENAME) ||
				operations.contains(OPERATION_LINK));
	}
	
	/**
	 * Returns true if the operation mmap/link/rename write
	 * 
	 * @param operation one of the Operation as defined this class
	 * @return true/false
	 */
	public static boolean isMmapRenameLinkWrite(String operation){
		List<String> operations = parseOperation(operation);
		return operations != null && operations.contains(OPERATION_WRITE) &&
				(operations.contains(OPERATION_MMAP) || 
				operations.contains(OPERATION_RENAME) ||
				operations.contains(OPERATION_LINK));
	}
	
	/**
	 * Returns true if the operation is mmap/link/rename EXACTLY
	 * 
	 * @param operation one of the Operation as defined this class
	 * @return true/false
	 */
	public static boolean isMmapRenameLink(String operation){
		return operation != null && (operation.equals(OPERATION_MMAP) || 
				operation.equals(OPERATION_RENAME) ||
				operation.equals(OPERATION_LINK));
	}
	
	/**
	 * Returns true if the operation is read, recv or mmap/link/rename read
	 * 
	 * @param operation one of the Operation as defined this class
	 * @return true/false
	 */
	public static boolean isIncomingDataOperation(String operation){
		List<String> operations = parseOperation(operation);
		return operations != null && 
				(operations.contains(OPERATION_READ) 
						|| operations.contains(OPERATION_RECV)
						|| isMmapRenameLinkRead(operation));
	}
	
	/**
	 * Returns true if the operation is write, send or mmap/link/rename write
	 * 
	 * @param operation one of the Operation as defined this class
	 * @return true/false
	 */
	public static boolean isOutgoingDataOperation(String operation){
		List<String> operations = parseOperation(operation);
		return operations != null && 
				(operations.contains(OPERATION_WRITE) 
						|| operations.contains(OPERATION_SEND)
						|| isMmapRenameLinkWrite(operation));
	}
		
	public static boolean isNetworkArtifact(AbstractVertex vertex){
		if(vertex != null){
			return SUBTYPE_NETWORK_SOCKET.equals(vertex.getAnnotation(ARTIFACT_SUBTYPE));
		}
		return false;
	}
	
	public static boolean edgeContainsNetworkArtifact(AbstractEdge edge){
		if(edge != null){
			return isNetworkArtifact(edge.getChildVertex())
					|| isNetworkArtifact(edge.getParentVertex());
		}
		return false;
	}
	
	public static String buildHostNetworkInterfaceNameKey(int i){
		return ARTIFACT_HOST_INTERFACE_NAME_PREFIX + " " + i;
	}
	
	public static String buildHostNetworkInterfaceMacAddressKey(int i){
		return ARTIFACT_HOST_INTERFACE_MAC_ADDRESS_PREFIX + " " + i;
	}
	
	public static String buildHostNetworkInterfaceIpAddressesKey(int i){
		return ARTIFACT_HOST_INTERFACE_IP_ADDRESSES_PREFIX + " " + i;
	}
	
	public static String buildHostNetworkInterfaceIpAddressesValue(List<String> ipAddresses){
		if(ipAddresses != null){
			StringBuilder ipAddressesString = new StringBuilder();
			for(String ipAddress : ipAddresses){
				ipAddressesString.append(ipAddress).append(",");
			}
			if(ipAddressesString.length() > 0){
				ipAddressesString.deleteCharAt(ipAddressesString.length()-1);
			}
			return ipAddressesString.toString();
		}else{
			return null;
		}
	}
	
	public static List<CharSequence> parseHostNetworkInterfaceIpAddressesValue(String ipAddresses){
		if(ipAddresses != null){
			String tokens[] = ipAddresses.split(",");
			return Arrays.asList(tokens);
		}else{
			return null;
		}
	}
	
	
}
