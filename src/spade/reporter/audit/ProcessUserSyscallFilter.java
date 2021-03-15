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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.utility.HelperFunctions;
import spade.utility.Result;

public class ProcessUserSyscallFilter{

	public static enum UserMode{
		IGNORE, CAPTURE
	}
	
	public static enum SystemCallRuleType{
		DEFAULT, ALL, NONE
	}
	
	public static final String keyUser = "user",
			keyIgnoreProcesses = "ignoreProcesses",
			keyIgnoreParentProcesses = "ignoreParentProcesses",
			keySyscall = "syscall";
	
	private SystemCallRuleType systemCallRuleType;
	private String userName;
	private String userId;
	private UserMode userMode;
	private Set<String> namesOfProcessesToIgnore;
	private Set<String> namesOfParentProcessesToIgnore;
	private String spadeAuditBridgeProcessName;
	
	private Set<String> pidsToIgnore = null, ppidsToIgnore = null, tgidsToHarden = null;

	public ProcessUserSyscallFilter(SystemCallRuleType systemCallRuleType, 
			String userName, String userId, UserMode userMode, 
			Set<String> namesOfProcessesToIgnore, Set<String> namesOfParentProcessesToIgnore, String spadeAuditBridgeProcessName){
		this.systemCallRuleType = systemCallRuleType;
		this.userName = userName;
		this.userId = userId;
		this.userMode = userMode;
		this.namesOfProcessesToIgnore = namesOfProcessesToIgnore;
		this.namesOfParentProcessesToIgnore = namesOfParentProcessesToIgnore;
		this.spadeAuditBridgeProcessName = spadeAuditBridgeProcessName;
	}

	public String getUserId(){
		return userId;
	}

	public UserMode getUserMode(){
		return userMode;
	}
	
	public SystemCallRuleType getSystemCallRuleType(){
		return systemCallRuleType;
	}
	
	public synchronized Set<String> getPidsOfProcessesToIgnore() throws Exception{
		if(pidsToIgnore == null){
			final Set<String> pids = new HashSet<String>();
			for(final String ignoreProcessNames : namesOfProcessesToIgnore){
				pids.addAll(HelperFunctions.nixPidsOfProcessesWithName(ignoreProcessNames));
			}
			pids.addAll(HelperFunctions.nixPidsOfProcessesWithName(spadeAuditBridgeProcessName));
			pids.add(HelperFunctions.nixPidOfSelf());
			
			pidsToIgnore = pids;
		}
		return new HashSet<String>(pidsToIgnore);
	}
	
	public synchronized Set<String> getPpidsOfProcessesToIgnore() throws Exception{
		if(ppidsToIgnore == null){
			final Set<String> ppids = new HashSet<String>();
			for(final String ignoreProcessNames : namesOfParentProcessesToIgnore){
				ppids.addAll(HelperFunctions.nixPidsOfProcessesWithName(ignoreProcessNames));
			}
			ppids.addAll(HelperFunctions.nixPidsOfProcessesWithName(spadeAuditBridgeProcessName));
			ppids.add(HelperFunctions.nixPidOfSelf());
	
			ppidsToIgnore = ppids;
		}
		return new HashSet<String>(ppidsToIgnore);
	}
	
	public synchronized Set<String> getTgidsOfProcessesToHarden(final Set<String> namesOfProcessesToHarden) throws Exception{
		if(tgidsToHarden == null){
			final Set<String> tgids = new HashSet<String>();
			for(final String name : namesOfProcessesToHarden){
				tgids.addAll(HelperFunctions.nixTgidsOfProcessesWithName(name));
			}
			tgids.addAll(HelperFunctions.nixTgidsOfProcessesWithName(spadeAuditBridgeProcessName));
			tgids.add(HelperFunctions.nixTgidOfSelf());
			
			tgidsToHarden = tgids;
		}
		return new HashSet<String>(tgidsToHarden);
	}
	
	public boolean isProcessNameInIgnoreProcessSet(final String processName){
		if(processName != null){
			return namesOfProcessesToIgnore.contains(processName);
		}
		return false;
	}

	@Override
	public String toString(){
		return "ProcessUserSyscallFilter [systemCallRuleType=" + systemCallRuleType + ", userName=" + userName
				+ ", userId=" + userId + ", userMode=" + userMode + ", namesOfProcessesToIgnore="
				+ namesOfProcessesToIgnore + ", namesOfParentProcessesToIgnore=" + namesOfParentProcessesToIgnore + "]";
	}

	public static ProcessUserSyscallFilter instance(final Map<String, String> map, final String spadeAuditBridgeProcessName,
			final boolean isLive) throws Exception{
		final SystemCallRuleType systemCallRuleType;
		final String userName;
		final String userId;
		final UserMode userMode;
		final Set<String> ignoreProcesses = new HashSet<String>();
		final Set<String> ignoreParentProcesses = new HashSet<String>();
		
		if(isLive){
			final String valueSyscall = map.get(keySyscall);
			final Result<SystemCallRuleType> resultSystemCallRuleType = HelperFunctions.parseEnumValue(SystemCallRuleType.class, valueSyscall, true);
			if(resultSystemCallRuleType.error){
				throw new Exception("Invalid value for key '" + keySyscall + "'. " + resultSystemCallRuleType.toErrorString());
			}
			systemCallRuleType = resultSystemCallRuleType.result;
		}else{
			systemCallRuleType = null;
		}
		
		try{
			final String valueUserName = map.get(keyUser);
			if(HelperFunctions.isNullOrEmpty(valueUserName)){
				userMode = UserMode.IGNORE;
				userId = HelperFunctions.nixUidOfSelf();
				userName = HelperFunctions.nixUsernameOfUid(userId);
			}else{
				userMode = UserMode.CAPTURE;
				userId = HelperFunctions.nixUidOfUsername(valueUserName);
				userName = valueUserName;
			}
		}catch(Exception e){
			throw new Exception("Failed to parse value for '" + keyUser + "'", e);
		}
		
		try{
			ignoreProcesses.addAll(parseNamesOfProcessesToIgnore(keyIgnoreProcesses, map));
		}catch(Exception e){
			throw new Exception("Failed to parse value for '" + keyIgnoreProcesses + "'", e);
		}
		
		try{
			ignoreParentProcesses.addAll(parseNamesOfProcessesToIgnore(keyIgnoreParentProcesses, map));
		}catch(Exception e){
			throw new Exception("Failed to parse value for '" + keyIgnoreParentProcesses + "'", e);
		}

		if(HelperFunctions.isNullOrEmpty(spadeAuditBridgeProcessName)){
			throw new Exception("NULL/Empty name for SPADE audit bridge process");
		}

		return new ProcessUserSyscallFilter(systemCallRuleType, 
				userName, userId, userMode, 
				ignoreProcesses, ignoreParentProcesses, spadeAuditBridgeProcessName);
	}
	
	private static Set<String> parseNamesOfProcessesToIgnore(final String key, final Map<String, String> map) throws Exception{
		final Set<String> set = new HashSet<String>();
		final String value = map.get(key);
		if(!HelperFunctions.isNullOrEmpty(value)){
			final String tokens[] = value.split(",");
			for(final String token : tokens){
				if(token.trim().isEmpty()){
					throw new Exception("Empty name for process to ignore");
				}else{
					set.add(token.trim());
				}
			}
		}
		return set;
	}
}
