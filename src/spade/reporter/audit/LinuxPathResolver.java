/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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

import java.util.logging.Level;

import spade.reporter.Audit;
import spade.reporter.audit.artifact.ArtifactIdentifier;
import spade.reporter.audit.artifact.ArtifactManager;
import spade.reporter.audit.artifact.BlockDeviceIdentifier;
import spade.reporter.audit.artifact.CharacterDeviceIdentifier;
import spade.reporter.audit.artifact.DirectoryIdentifier;
import spade.reporter.audit.artifact.FileIdentifier;
import spade.reporter.audit.artifact.LinkIdentifier;
import spade.reporter.audit.artifact.NamedPipeIdentifier;
import spade.reporter.audit.artifact.PathIdentifier;
import spade.reporter.audit.artifact.UnixSocketIdentifier;
import spade.reporter.audit.process.FileDescriptor;
import spade.reporter.audit.process.ProcessManager;

import spade.utility.HelperFunctions;

public class LinuxPathResolver{

//	File system root
	public static final String FS_ROOT = "/";
	
	public static final String PATH_SEPARATOR = "/";
	public static final String PATH_SYMBOL_PREVIOUS_DIR = "..";
	public static final String PATH_SYMBOL_CURRENT_DIR = ".";
	
//	Following constant values are taken from:
//	http://lxr.free-electrons.com/source/include/uapi/linux/fcntl.h#L56
	public static final int AT_FDCWD = -100;
	
//	Following constant values are taken from:
//	http://lxr.free-electrons.com/source/include/uapi/linux/stat.h#L14
	public static final int S_IFIFO = 0010000, S_IFREG = 0100000, S_IFSOCK = 0140000,
						S_IFLNK = 0120000, S_IFBLK = 0060000, S_IFDIR = 0040000,
						S_IFCHR = 0020000, S_IFMT = 00170000;
	
	public static boolean isAbsolutePath(String path){
		if(path != null){
			return path.startsWith(FS_ROOT);
		}
		return false;
	}
	
	/**
	 * Removes special path symbols '..' and '.'
	 * 
	 * @param path file system path
	 * @return file system path or null if not a valid path
	 */
	private static String removeSpecialPathSymbols(String path){
		if(path == null){
			return null;
		}
		String finalPath = "";
		path = path.trim();
		if(path.isEmpty()){
			return null;
		}else{
			int doubleDirCount = 0;
			String[] parts = path.split(PATH_SEPARATOR);
			for(int a = parts.length-1; a>-1; a--){
				if(parts[a].equals(PATH_SYMBOL_PREVIOUS_DIR)){
					doubleDirCount++;
					continue;
				}else if(parts[a].equals(PATH_SYMBOL_CURRENT_DIR)){
					continue;
				}else if(parts[a].trim().isEmpty()){
					/*
					 * Cases: 
					 * 1) Start of path (/path/to/something)
					 * 2) End of path (path/to/something/)
					 * 3) Double path separator (/path//to////something) 
					 */
					// Continue
				}else{
					if(doubleDirCount > 0){
						doubleDirCount--;
					}else{
						finalPath = parts[a] + PATH_SEPARATOR + finalPath;
					}
				}
			}
			// Adding the slash in the end if the given path had a slash in the end
			if(!path.endsWith(PATH_SEPARATOR) && finalPath.endsWith(PATH_SEPARATOR)){
				finalPath = finalPath.substring(0, finalPath.length() - 1);
			}
			// Adding the slash in the beginning if the given path had a slash in the beginning
			if(path.startsWith(PATH_SEPARATOR) && !finalPath.startsWith(PATH_SEPARATOR)){
				finalPath = PATH_SEPARATOR + finalPath;
			}
			return finalPath;
		}
	}
	
	/**
	 * Resolves symbolic links in case reading from an audit log
	 * 
	 * Add other cases later, so far can only think of /proc/self
	 * 
	 * @param path path to resolve
	 * @param pid process id
	 * @return resolved path or null if invalid arguments
	 */
	private static String resolvePathStatically(String path, String pid){
		if(path == null){
			return null;
		}
		if(path.startsWith("/proc/self")){
			if(pid == null){
				return path;
			}else{
				StringBuilder string = new StringBuilder();
				string.append(path);
				string.delete(6, 10); // index of self in /proc/self is 6 and ends at 10
				string.insert(6, pid); // replacing with pid
				return string.toString();
			}
		}else{ // No symbolic link to replace
			return path;
		}
	}
	
	/**
	 * Concatenates given paths using the following logic:
	 * 
	 * If path is absolute then return path
	 * If path is not absolute then concatenates parentPath with path and returns that
	 * 
	 * @param path path relative to parentPath (can be absolute)
	 * @param parentPath parent path for the given path
	 * @return concatenated path or null
	 */
	private static String concatenatePaths(String path, String parentPath){
		if(path != null){
			path = path.trim();
			if(path.isEmpty()){
				return null;
			}else{
				if(isAbsolutePath(path)){ //is absolute
					return path;
				}else{
					if(parentPath != null){
						parentPath = parentPath.trim();
						if(parentPath.isEmpty() || !isAbsolutePath(parentPath)){
							return null;
						}else{
							return parentPath + PATH_SEPARATOR + path;
						}
					}
				}	   
			}
		}
		return null;
	}
	
	/**
	 * Constructs path by concatenating the two paths, then removes symbols such as '.' and 
	 * '..' and then finally resolves symbolic links like /proc/self.
	 * 
	 * Null returned if unable to construct absolute path
	 * 
	 * @param path path relative to parentPath
	 * @param parentPath parent path of the path
	 * @return constructed path or null
	 */
	public static String constructAbsolutePath(String path, String parentPath, String pid){
		path = concatenatePaths(path, parentPath);
		if(path != null){
			return cleanupPath(path, pid);
		}
		return null;
	}
	
	public static String joinPaths(String path, String parentPath, String pid){
		String newPath = (parentPath == null ? "" : parentPath) 
						+ PATH_SEPARATOR
						+ (path == null ? "" : path);
		return cleanupPath(newPath, pid);
	}
	
	public static String cleanupPath(String path, String pid){
		path = removeSpecialPathSymbols(path);
		if(path != null){
			return resolvePathStatically(path, pid);
		}
		return null;
	}

	/**
	 * Returns the corresponding artifact identifier type based on the pathModeString value
	 * 
	 * Returns null if an unexpected mode value found
	 * 
	 * @param path file system path
	 * @param pathType mode value for the path in the audit log
	 * @return ArtifactIdentifier subclass or null
	 */
	private static PathIdentifier getArtifactIdentifierFromPathMode(
			String path, String rootFSPath,
			int pathType, 
			String time, String eventId, SYSCALL syscall,
			Audit audit){
		final int type = pathType & S_IFMT;
		switch(type){
			case S_IFREG: return new FileIdentifier(path, rootFSPath);
			case S_IFDIR: return new DirectoryIdentifier(path, rootFSPath);
			case S_IFCHR: return new CharacterDeviceIdentifier(path, rootFSPath);
			case S_IFBLK: return new BlockDeviceIdentifier(path, rootFSPath);
			case S_IFLNK: return new LinkIdentifier(path, rootFSPath);
			case S_IFIFO: return new NamedPipeIdentifier(path, rootFSPath);
			case S_IFSOCK: return new UnixSocketIdentifier(path, rootFSPath);
			default:
				audit.log(Level.INFO, "Unknown file type: "+pathType+". Defaulted to 'File'", null, time, eventId, syscall);
				return new FileIdentifier(path, rootFSPath);
		}
	}

	public static PathIdentifier resolvePath(PathRecord pathRecord, String cwdAuditRecord, 
			String pid, String atSyscallFdKey, String fdString, boolean isAnAtSyscall,
			String eventTime, String eventId, SYSCALL syscall,
			Audit audit, ProcessManager processManager, ArtifactManager artifactManager,
			boolean HANDLE_CHDIR){
		
		if(pathRecord == null){
			audit.log(Level.INFO, "NULL path record", null, eventTime, eventId, syscall);
			return null;
		}
		
		final String path = pathRecord.getPath();
		
		String parentPath = null;
		String rootFSPath = null;
		
		if(isAbsolutePath(path)){
			parentPath = null;
			rootFSPath = processManager.getRoot(pid);
		}else{ // is relative
			boolean getParentPathFromCwd = false;
			if(isAnAtSyscall){
				if(fdString == null){
					audit.log(Level.INFO, "Missing directory FD for key '"+atSyscallFdKey+"'", 
							null, eventTime, eventId, syscall);
					return null;
				}else{
					Integer fdInt = HelperFunctions.parseInt(fdString, null);
					if(fdInt == null){
						audit.log(Level.INFO, "Non-integer directory FD for key '"+atSyscallFdKey+"': '"+fdString+"'", 
								null, eventTime, eventId, syscall);
						return null;
					}else{
						if(fdInt == AT_FDCWD){
							getParentPathFromCwd = true;
						}else{
							FileDescriptor directoryFd = processManager.getFd(pid, fdString);
							if(directoryFd == null){
								audit.log(Level.INFO, "Missing directory FD for pid '"+pid+"' with FD '"+fdString+"'", 
										null, eventTime, eventId, syscall);
								return null;
							}else{
								ArtifactIdentifier artifactIdentifier = directoryFd.identifier;
								if(!(artifactIdentifier instanceof DirectoryIdentifier)){
									audit.log(Level.INFO, "Expected FD to be of type directory but is '"+artifactIdentifier.getSubtype()+"'", 
											null, eventTime, eventId, syscall);
									return null;
								}else{
									DirectoryIdentifier directoryIdentifier = (DirectoryIdentifier)artifactIdentifier;
									parentPath = directoryIdentifier.path;
									rootFSPath = directoryIdentifier.rootFSPath;
								}
							}
						}
					}
				}
			}else{
				getParentPathFromCwd = true;
			}
			
			if(getParentPathFromCwd){
				if(HANDLE_CHDIR){ // means we can possibly get cwd from process
					parentPath = processManager.getCwd(pid); // get from process
					rootFSPath = processManager.getCwdRoot(pid); // never null
					if(parentPath == null){
						/*
						audit.log(Level.INFO, "Missing CWD in process state. Falling back to CWD in audit record", 
								null, eventTime, eventId, syscall);
						*/
					}
				}
				// If failed to get from process state then get from event data
				if(parentPath == null){
					parentPath = cwdAuditRecord;
					rootFSPath = processManager.getRoot(pid); // never null
				}
				// If still failed to get then we cannot continue
				if(parentPath == null){
					audit.log(Level.INFO, "Missing CWD in the event data", null, eventTime, eventId, syscall);
					return null;
				}
			}
		}
		
		// path and rootfs
		String finalPath = constructAbsolutePath(path, parentPath, pid);
		PathIdentifier pathIdentifier = getArtifactIdentifierFromPathMode(
				finalPath, rootFSPath, 
				pathRecord.getPathType(), eventTime, eventId, syscall,
				audit);

		return pathIdentifier;
	}
}
