/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import spade.reporter.audit.ProcessUserSyscallFilter.SystemCallRuleType;
import spade.reporter.audit.ProcessUserSyscallFilter.UserMode;
import spade.utility.Execute;

public class AuditControlManager{

	private static String constructSubRuleForFields(final String key, final String operator, final Set<String> values){
		final StringBuffer str = new StringBuffer();
		for(final String value : values){
			str.append("-F " + key + operator + value + " ");
		}
		return str.toString().trim();
	}
	
	private static String constructSubRuleForSystemCalls(final String ... systemCalls){
		final StringBuffer str = new StringBuffer();
		for(final String systemCall : systemCalls){
			str.append("-S " + systemCall + " ");
		}
		return str.toString().trim();
	}

	private static List<String> constructRules(
			final SystemCallRuleType systemCallRuleType, 
			final String userId, final UserMode userMode,
			final Set<String> pidsToIgnore, final Set<String> ppidsToIgnore,
			final boolean excludeProctitle,
			final boolean kernelModulesAdded,
			final boolean netIO, final boolean fileIO,
			final boolean memorySystemCalls, final boolean filesystemCredentialUpdates,
			final boolean directoryChanges, final boolean filesystemRootChanges,
			final boolean namespaces, 
			final boolean ipcSystemCalls
			) throws Exception{
		final List<String> rules = new ArrayList<String>();

		if(systemCallRuleType == SystemCallRuleType.NONE){
			// Do nothing
		}else{
			if(excludeProctitle){
				rules.add("always,exclude -F msgtype=PROCTITLE");
			}
			
			final String archField = "-F arch=b64";
			final String userField;
			if(userMode == null){
				throw new Exception("NULL user mode");
			}else{
				switch(userMode){
					case IGNORE: userField = ("-F uid!=" + userId); break;
					case CAPTURE: userField = ("-F uid=" + userId); break;
					default: throw new Exception("Unexpected user mode: '" + userMode + "'");
				}
			}
			
			final String pidAndPpidFields = (constructSubRuleForFields("pid", "!=", pidsToIgnore) + " "
					+ constructSubRuleForFields("ppid", "!=", ppidsToIgnore)).trim();
		
			if(systemCallRuleType == SystemCallRuleType.ALL){
				if(kernelModulesAdded){
					final String neverSystemCallRule = 
							"exit,never " 
							+ archField + " "
							+ constructSubRuleForSystemCalls("kill", "socket", "bind", "accept", "accept4", "connect", 
									"sendmsg", "sendto", "sendmmsg", "recvmsg", "recvfrom", "recvmmsg");
					rules.add(neverSystemCallRule);
				}
				
				final String nonSuccessSystemCallRule = 
						("exit,always " 
						+ archField + " "
						+ constructSubRuleForSystemCalls("exit", "exit_group", "kill", "connect") + " "
						+ userField + " "
						+ pidAndPpidFields).trim();
				rules.add(nonSuccessSystemCallRule);
				
                final String allSystemCallRule = 
                		("exit,always " 
                		+ archField + " "
                		+ constructSubRuleForSystemCalls("all") + " "
                		+ userField + " "
                		+ "-F success=1 "
                		+ pidAndPpidFields).trim();
                rules.add(allSystemCallRule);
			}else if(systemCallRuleType == SystemCallRuleType.DEFAULT){
				final List<String> systemCallsWithSuccess = new ArrayList<String>();
				if(fileIO){
					systemCallsWithSuccess.addAll(Arrays.asList("read", "readv", "pread", "preadv", 
							"write", "writev", "pwrite", "pwritev", "lseek"));
				}
				if(!kernelModulesAdded){
					if(netIO){
						systemCallsWithSuccess.addAll(Arrays.asList("sendmsg", "sendto", "recvmsg", "recvfrom"));
					}
					systemCallsWithSuccess.addAll(Arrays.asList("bind", "accept", "accept4", "socket"));
				}
				if(memorySystemCalls){
					systemCallsWithSuccess.addAll(Arrays.asList("mmap", "mprotect", "madvise"));
				}
				systemCallsWithSuccess.addAll(Arrays.asList(
						"unlink", "unlinkat", "link", "linkat", "symlink", "symlinkat",
						"clone", "fork", "vfork", "execve",
						"open", "openat", "creat", "close", "mknod", "mknodat",
						"dup", "dup2", "dup3", "fcntl", "rename", "renameat",
						"setuid", "setreuid", "setresuid", "setgid", "setregid", "setresgid",
						"chmod", "fchmod", "fchmodat", "truncate", "ftruncate",
						"pipe", "pipe2", "tee", "splice", "vmsplice", "socketpair",
						"init_module", "finit_module", "ptrace"
						));
				if(filesystemCredentialUpdates){
					systemCallsWithSuccess.addAll(Arrays.asList("setfsuid", "setfsgid"));
				}
				if(directoryChanges){
					systemCallsWithSuccess.addAll(Arrays.asList("chdir", "fchdir"));
				}
				if(filesystemRootChanges){
					systemCallsWithSuccess.addAll(Arrays.asList("chroot", "pivot_root"));
				}
				if(namespaces){
					systemCallsWithSuccess.addAll(Arrays.asList("setns", "unshare"));
				}
				if(ipcSystemCalls){
					systemCallsWithSuccess.addAll(IPCManager.getSyscallNamesForAuditctlForAll());
				}
				final String withSuccessSystemCallRule = 
						("exit,always " 
						+ archField + " "
						+ userField + " "
						+ constructSubRuleForSystemCalls(systemCallsWithSuccess.toArray(new String[]{})) + " "
						+ "-F success=1 "
						+ pidAndPpidFields).trim();
				rules.add(withSuccessSystemCallRule);
				
				final List<String> systemCallsWithoutSuccess = new ArrayList<String>();
				systemCallsWithoutSuccess.addAll(Arrays.asList("exit", "exit_group"));
				if(!kernelModulesAdded){
					systemCallsWithoutSuccess.addAll(Arrays.asList("connect", "kill"));	
				}
				final String withoutSuccessSystemCallRule = 
						("exit,always "
						+ archField + " "
						+ userField + " "
						+ constructSubRuleForSystemCalls(systemCallsWithoutSuccess.toArray(new String[]{})) + " "
						+ pidAndPpidFields).trim();
				rules.add(withoutSuccessSystemCallRule);
			}else{
				throw new Exception("Unexpected system call rule type: '" + systemCallRuleType + "'");
			}
		}
		
		return rules;
	}
	
	private static List<String> executeAuditctl(final String subcommand) throws Exception{
		final String command = "auditctl " + subcommand;
		try{
			final Execute.Output output = Execute.getOutput(command);
			if(output.hasError()){
				throw new Exception("Error: " + output.getStdErr().toString());
			}
			return output.getStdOut();
		}catch(Exception e){
			throw new Exception("Failed to execute auditctl command: '" + command + "'", e);
		}
	}
	
	private static List<String> deleteAllRules() throws Exception{
		try{
			return executeAuditctl("-D");
		}catch(Exception e){
			throw new Exception("Failed to delete all audit rules", e);
		}
	}
	
	private static List<String> deleteRule(final String subcommand) throws Exception{
		try{
			return executeAuditctl("-d " + subcommand);
		}catch(Exception e){
			throw new Exception("Failed to delete audit rule", e);
		}
	}
	
	public static List<String> listAllRules() throws Exception{
		try{
			return executeAuditctl("-l");
		}catch(Exception e){
			throw new Exception("Failed to list all audit rules", e);
		}
	}

	private static List<String> appendRule(final String subcommand) throws Exception{
		try{
			return executeAuditctl("-a " + subcommand);
		}catch(Exception e){
			throw new Exception("Failed to add audit rule", e);
		}
	}
	
	public static void unset(final SystemCallRuleType systemCallRuleType) throws Exception{
		try{
			if(systemCallRuleType == SystemCallRuleType.ALL || systemCallRuleType == SystemCallRuleType.DEFAULT){
				deleteAllRules();
			}
		}catch(Exception e){
			throw new Exception("Failed to unset audit rules", e);
		}
	}
	
	public static void set(
			final SystemCallRuleType systemCallRuleType, 
			final String userId, final UserMode userMode,
			final Set<String> pidsToIgnore, final Set<String> ppidsToIgnore,
			final boolean excludeProctitle,
			final boolean kernelModulesAdded,
			final boolean netIO, final boolean fileIO,
			final boolean memorySystemCalls, final boolean filesystemCredentialUpdates,
			final boolean directoryChanges, final boolean filesystemRootChanges,
			final boolean namespaces, 
			final boolean ipcSystemCalls,
			final Consumer<String> outputConsumer, final BiConsumer<String, Throwable> errorConsumer
			) throws Exception{
		final List<String> rules = constructRules(systemCallRuleType, 
					userId, userMode, 
					pidsToIgnore, ppidsToIgnore, 
					excludeProctitle, 
					kernelModulesAdded, 
					netIO, fileIO, 
					memorySystemCalls, filesystemCredentialUpdates, 
					directoryChanges, filesystemRootChanges, 
					namespaces, ipcSystemCalls);

		if(systemCallRuleType == SystemCallRuleType.ALL || systemCallRuleType == SystemCallRuleType.DEFAULT){
			try{
				deleteAllRules();
				outputConsumer.accept("Successfully deleted all existing rules");
			}catch(Exception e){
				throw new Exception("Failed to set audit rules", e);
			}
		}
		
		final List<String> addedRules = new ArrayList<String>();
		for(final String rule : rules){
			try{
				appendRule(rule);
				addedRules.add(rule);
				outputConsumer.accept("Successfully added audit rule: '" + rule + "'");
			}catch(Exception e){
				for(final String addedRule : addedRules){
					try{
						deleteRule(addedRule);
						outputConsumer.accept("Successfully undid audit rule: '" + addedRule + "'");
					}catch(Exception sube){
						errorConsumer.accept("Failed to undo audit rule", sube);
					}
				}
				throw new Exception("Failed to set audit rules", e);
			}
		}
	}
	
}
