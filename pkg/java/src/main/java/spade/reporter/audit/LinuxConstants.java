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

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class LinuxConstants{

	public final String
		O_RDONLY = "O_RDONLY",
		O_WRONLY = "O_WRONLY",
		O_RDWR = "O_RDWR",
		O_CREAT = "O_CREAT",
		O_TRUNC = "O_TRUNC",
		O_APPEND = "O_APPEND";

	public final String
		MAP_ANONYMOUS = "MAP_ANONYMOUS";

	public final String
		F_LINUX_SPECIFIC_BASE = "F_LINUX_SPECIFIC_BASE",
		F_DUPFD = "F_DUPFD",
		F_SETFL = "F_SETFL",
		F_DUPFD_CLOEXEC = "F_DUPFD_CLOEXEC";

	public final String
		EINPROGRESS = "EINPROGRESS";

	public final String
		SOCK_STREAM = "SOCK_STREAM",
		SOCK_DGRAM = "SOCK_DGRAM",
		SOCK_SEQPACKET = "SOCK_SEQPACKET";

	public final String 
		AF_UNIX = "AF_UNIX",
		AF_LOCAL = "AF_LOCAL",
		AF_INET = "AF_INET",
		AF_INET6 = "AF_INET6",
		PF_UNIX = "AF_UNIX",
		PF_LOCAL = "AF_LOCAL",
		PF_INET = "AF_INET",
		PF_INET6 = "AF_INET6";

	public final String
		PTRACE_POKETEXT = "PTRACE_POKETEXT",
		PTRACE_POKEDATA = "PTRACE_POKEDATA",
		PTRACE_POKEUSR = "PTRACE_POKEUSR",
		PTRACE_SETREGS = "PTRACE_SETREGS",
		PTRACE_SETFPREGS = "PTRACE_SETFPREGS",
		PTRACE_SETREGSET = "PTRACE_SETREGSET",
		PTRACE_SETSIGINFO = "PTRACE_SETSIGINFO",
		PTRACE_SETSIGMASK = "PTRACE_SETSIGMASK",
		PTRACE_SET_THREAD_AREA = "PTRACE_SET_THREAD_AREA",
		PTRACE_SETOPTIONS = "PTRACE_SETOPTIONS",
		PTRACE_CONT = "PTRACE_CONT",
		PTRACE_SYSCALL = "PTRACE_SYSCALL",
		PTRACE_SINGLESTEP = "PTRACE_SINGLESTEP",
		PTRACE_SYSEMU = "PTRACE_SYSEMU",
		PTRACE_SYSEMU_SINGLESTEP = "PTRACE_SYSEMU_SINGLESTEP",
		PTRACE_KILL = "PTRACE_KILL",
		PTRACE_INTERRUPT = "PTRACE_INTERRUPT",
		PTRACE_ATTACH = "PTRACE_ATTACH",
		PTRACE_DETACH = "PTRACE_DETACH",
		PTRACE_LISTEN = "PTRACE_LISTEN";

	public final String
		MADV_NORMAL = "MADV_NORMAL",
		MADV_RANDOM = "MADV_RANDOM",
		MADV_SEQUENTIAL = "MADV_SEQUENTIAL",
		MADV_WILLNEED = "MADV_WILLNEED",
		MADV_DONTNEED = "MADV_DONTNEED",
		MADV_FREE = "MADV_FREE",
		MADV_REMOVE = "MADV_REMOVE",
		MADV_DONTFORK = "MADV_DONTFORK",
		MADV_DOFORK = "MADV_DOFORK",
		MADV_MERGEABLE = "MADV_MERGEABLE",
		MADV_UNMERGEABLE = "MADV_UNMERGEABLE",
		MADV_HUGEPAGE = "MADV_HUGEPAGE",
		MADV_NOHUGEPAGE = "MADV_NOHUGEPAGE",
		MADV_DONTDUMP = "MADV_DONTDUMP",
		MADV_DODUMP = "MADV_DODUMP",
		MADV_WIPEONFORK = "MADV_WIPEONFORK",
		MADV_KEEPONFORK = "MADV_KEEPONFORK",
		MADV_HWPOISON = "MADV_HWPOISON",
		MADV_SOFT_OFFLINE = "MADV_SOFT_OFFLINE";

	public final String
		SEEK_SET = "SEEK_SET",
		SEEK_CUR = "SEEK_CUR",
		SEEK_END = "SEEK_END",
		SEEK_DATA = "SEEK_DATA",
		SEEK_HOLE = "SEEK_HOLE";

	public final String
		CLONE_CHILD_CLEARTID = "CLONE_CHILD_CLEARTID",
		CLONE_CHILD_SETTID = "CLONE_CHILD_SETTID",
		CLONE_FILES = "CLONE_FILES",
		CLONE_FS = "CLONE_FS",
		CLONE_IO = "CLONE_IO",
		CLONE_NEWUSER = "CLONE_NEWUSER",
		CLONE_NEWIPC = "CLONE_NEWIPC",
		CLONE_NEWNET = "CLONE_NEWNET",
		CLONE_NEWNS = "CLONE_NEWNS",
		CLONE_NEWPID = "CLONE_NEWPID",
		CLONE_NEWUTS = "CLONE_NEWUTS",
		CLONE_PARENT = "CLONE_PARENT",
		CLONE_PARENT_SETTID = "CLONE_PARENT_SETTID",
		CLONE_PTRACE = "CLONE_PTRACE",
		CLONE_SETTLS = "CLONE_SETTLS",
		CLONE_SIGHAND = "CLONE_SIGHAND",
		CLONE_SYSVSEM = "CLONE_SYSVSEM",
		CLONE_THREAD = "CLONE_THREAD",
		CLONE_UNTRACED = "CLONE_UNTRACED",
		CLONE_VFORK = "CLONE_VFORK",
		CLONE_VM = "CLONE_VM",
		SIGCHLD = "SIGCHLD";

	private final Map<String, Long> openFlagsMap = new HashMap<>();
	private final Map<String, Long> mmapFlagsMap = new HashMap<>();
	private final Map<String, Long> fcntlFlagsMap = new HashMap<>();
	private final Map<String, Long> errnoMap = new HashMap<>();
	private final Map<String, Long> socketTypeMap = new HashMap<>();
	private final Map<String, Long> addressFamilyMap = new HashMap<>();
	private final Map<String, Long> ptraceActionMap = new HashMap<>();
	private final Map<String, Long> madviseAdviceMap = new HashMap<>();
	private final Map<String, Long> lseekWhenceMap = new HashMap<>();
	private final Map<String, Long> cloneFlagsMap = new HashMap<>();

	private String configFilePath;
	
	public String getConfigFilePath(){
		return configFilePath;
	}
	
	private final long parseLong(final Map<String, String> map, final String key) throws Exception{
		String valueString = map.get(key);
		if(valueString == null){
			throw new Exception("Missing key '" + key + "'");
		}
		valueString = valueString.trim();
		if(valueString.isEmpty()){
			throw new Exception("Empty value for key '" + key + "'");
		}
		if(valueString.equals("0")){
			return 0;
		}
		final int base;
		if(valueString.startsWith("0x")){ // base 16
			valueString = valueString.substring(2);
			base = 16;
		}else if(valueString.startsWith("0")){ // base 8
			base = 8;
		}else{ // base 10
			base = 10;
		}
		try{
			return Long.parseLong(valueString, base);
		}catch(Exception e){
			throw new Exception("Non-numeric value for key '" + key + "': '" + valueString + "'", e);
		}
	}

	public static final LinuxConstants instance(final Map<String, String> map, final String key) throws Exception{
		final String value = map.get(key);
		if(HelperFunctions.isNullOrEmpty(key)){
			throw new Exception("NULL/empty value for '" + key+ "': " + value);
		}
		final String tokens[] = value.split("\\s+", 2);
		final String qualifiedClassName = tokens[0];

		if(tokens.length != 2){
			throw new Exception("Value for '" + key + "' must be specified in format: '<qualifiedClassName> <configFilePath>'");
		}

		final LinuxConstants instance;
		try{
			@SuppressWarnings("unchecked")
			final Class<LinuxConstants> clazz = (Class<LinuxConstants>)Class.forName(qualifiedClassName);
			final Constructor<LinuxConstants> constructor = clazz.getDeclaredConstructor();
			instance = constructor.newInstance();
		}catch(Exception e){
			throw new Exception("Failed to create instance of class '" + qualifiedClassName + "' specified by key '" + key + "'", e);
		}

		try{
			final String constantsFilePath = tokens[1].trim();
			final String resolvedPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(constantsFilePath);
			instance.initialize(resolvedPath);
		}catch(Exception e){
			throw new Exception("Failed to initialize constants source specified by key '" + key + "'", e);
		}
		return instance;
	}
	
	public final void initialize(final String filePath) throws Exception{
		this.configFilePath = filePath;
		
		try{
			final Map<String, String> map = FileUtility.readConfigFileAsKeyValueMap(filePath, "=");

			openFlagsMap.put(O_RDONLY, parseLong(map, O_RDONLY));
			openFlagsMap.put(O_WRONLY, parseLong(map, O_WRONLY));
			openFlagsMap.put(O_RDWR, parseLong(map, O_RDWR));
			openFlagsMap.put(O_CREAT, parseLong(map, O_CREAT));
			openFlagsMap.put(O_TRUNC, parseLong(map, O_TRUNC));
			openFlagsMap.put(O_APPEND, parseLong(map, O_APPEND));

			mmapFlagsMap.put(MAP_ANONYMOUS, parseLong(map, MAP_ANONYMOUS));

			fcntlFlagsMap.put(F_LINUX_SPECIFIC_BASE, parseLong(map, F_LINUX_SPECIFIC_BASE));
			fcntlFlagsMap.put(F_DUPFD, parseLong(map, F_DUPFD));
			fcntlFlagsMap.put(F_SETFL, parseLong(map, F_SETFL));
			fcntlFlagsMap.put(F_DUPFD_CLOEXEC, parseLong(map, F_DUPFD_CLOEXEC));

			errnoMap.put(EINPROGRESS, parseLong(map, EINPROGRESS));

			socketTypeMap.put(SOCK_STREAM, parseLong(map, SOCK_STREAM));
			socketTypeMap.put(SOCK_DGRAM, parseLong(map, SOCK_DGRAM));
			socketTypeMap.put(SOCK_SEQPACKET, parseLong(map, SOCK_SEQPACKET));

			addressFamilyMap.put(AF_UNIX, parseLong(map, AF_UNIX));
			addressFamilyMap.put(AF_LOCAL, parseLong(map, AF_LOCAL));
			addressFamilyMap.put(AF_INET, parseLong(map, AF_INET));
			addressFamilyMap.put(AF_INET6, parseLong(map, AF_INET6));
			addressFamilyMap.put(PF_UNIX, parseLong(map, PF_UNIX));
			addressFamilyMap.put(PF_LOCAL, parseLong(map, PF_LOCAL));
			addressFamilyMap.put(PF_INET, parseLong(map, PF_INET));
			addressFamilyMap.put(PF_INET6, parseLong(map, PF_INET6));

			ptraceActionMap.put(PTRACE_POKETEXT, parseLong(map, PTRACE_POKETEXT));
			ptraceActionMap.put(PTRACE_POKEDATA, parseLong(map, PTRACE_POKEDATA));
			ptraceActionMap.put(PTRACE_POKEUSR, parseLong(map, PTRACE_POKEUSR));
			ptraceActionMap.put(PTRACE_SETREGS, parseLong(map, PTRACE_SETREGS));
			ptraceActionMap.put(PTRACE_SETFPREGS, parseLong(map, PTRACE_SETFPREGS));
			ptraceActionMap.put(PTRACE_SETREGSET, parseLong(map, PTRACE_SETREGSET));
			ptraceActionMap.put(PTRACE_SETSIGINFO, parseLong(map, PTRACE_SETSIGINFO));
			ptraceActionMap.put(PTRACE_SETSIGMASK, parseLong(map, PTRACE_SETSIGMASK));
			ptraceActionMap.put(PTRACE_SET_THREAD_AREA, parseLong(map, PTRACE_SET_THREAD_AREA));
			ptraceActionMap.put(PTRACE_SETOPTIONS, parseLong(map, PTRACE_SETOPTIONS));
			ptraceActionMap.put(PTRACE_CONT, parseLong(map, PTRACE_CONT));
			ptraceActionMap.put(PTRACE_SYSCALL, parseLong(map, PTRACE_SYSCALL));
			ptraceActionMap.put(PTRACE_SINGLESTEP, parseLong(map, PTRACE_SINGLESTEP));
			ptraceActionMap.put(PTRACE_SYSEMU, parseLong(map, PTRACE_SYSEMU));
			ptraceActionMap.put(PTRACE_SYSEMU_SINGLESTEP, parseLong(map, PTRACE_SYSEMU_SINGLESTEP));
			ptraceActionMap.put(PTRACE_KILL, parseLong(map, PTRACE_KILL));
			ptraceActionMap.put(PTRACE_INTERRUPT, parseLong(map, PTRACE_INTERRUPT));
			ptraceActionMap.put(PTRACE_ATTACH, parseLong(map, PTRACE_ATTACH));
			ptraceActionMap.put(PTRACE_DETACH, parseLong(map, PTRACE_DETACH));
			ptraceActionMap.put(PTRACE_LISTEN, parseLong(map, PTRACE_LISTEN));

			madviseAdviceMap.put(MADV_NORMAL, parseLong(map, MADV_NORMAL));
			madviseAdviceMap.put(MADV_RANDOM, parseLong(map, MADV_RANDOM));
			madviseAdviceMap.put(MADV_SEQUENTIAL, parseLong(map, MADV_SEQUENTIAL));
			madviseAdviceMap.put(MADV_WILLNEED, parseLong(map, MADV_WILLNEED));
			madviseAdviceMap.put(MADV_DONTNEED, parseLong(map, MADV_DONTNEED));
			madviseAdviceMap.put(MADV_FREE, parseLong(map, MADV_FREE));
			madviseAdviceMap.put(MADV_REMOVE, parseLong(map, MADV_REMOVE));
			madviseAdviceMap.put(MADV_DONTFORK, parseLong(map, MADV_DONTFORK));
			madviseAdviceMap.put(MADV_DOFORK, parseLong(map, MADV_DOFORK));
			madviseAdviceMap.put(MADV_MERGEABLE, parseLong(map, MADV_MERGEABLE));
			madviseAdviceMap.put(MADV_UNMERGEABLE, parseLong(map, MADV_UNMERGEABLE));
			madviseAdviceMap.put(MADV_HUGEPAGE, parseLong(map, MADV_HUGEPAGE));
			madviseAdviceMap.put(MADV_NOHUGEPAGE, parseLong(map, MADV_NOHUGEPAGE));
			madviseAdviceMap.put(MADV_DONTDUMP, parseLong(map, MADV_DONTDUMP));
			madviseAdviceMap.put(MADV_DODUMP, parseLong(map, MADV_DODUMP));
			madviseAdviceMap.put(MADV_WIPEONFORK, parseLong(map, MADV_WIPEONFORK));
			madviseAdviceMap.put(MADV_KEEPONFORK, parseLong(map, MADV_KEEPONFORK));
			madviseAdviceMap.put(MADV_HWPOISON, parseLong(map, MADV_HWPOISON));
			madviseAdviceMap.put(MADV_SOFT_OFFLINE, parseLong(map, MADV_SOFT_OFFLINE));

			lseekWhenceMap.put(SEEK_SET, parseLong(map, SEEK_SET));
			lseekWhenceMap.put(SEEK_CUR, parseLong(map, SEEK_CUR));
			lseekWhenceMap.put(SEEK_END, parseLong(map, SEEK_END));
			lseekWhenceMap.put(SEEK_DATA, parseLong(map, SEEK_DATA));
			lseekWhenceMap.put(SEEK_HOLE, parseLong(map, SEEK_HOLE));

			cloneFlagsMap.put(CLONE_CHILD_CLEARTID, parseLong(map, CLONE_CHILD_CLEARTID));
			cloneFlagsMap.put(CLONE_CHILD_SETTID, parseLong(map, CLONE_CHILD_SETTID));
			cloneFlagsMap.put(CLONE_FILES, parseLong(map, CLONE_FILES));
			cloneFlagsMap.put(CLONE_FS, parseLong(map, CLONE_FS));
			cloneFlagsMap.put(CLONE_IO, parseLong(map, CLONE_IO));
			cloneFlagsMap.put(CLONE_NEWUSER, parseLong(map, CLONE_NEWUSER));
			cloneFlagsMap.put(CLONE_NEWIPC, parseLong(map, CLONE_NEWIPC));
			cloneFlagsMap.put(CLONE_NEWNET, parseLong(map, CLONE_NEWNET));
			cloneFlagsMap.put(CLONE_NEWNS, parseLong(map, CLONE_NEWNS));
			cloneFlagsMap.put(CLONE_NEWPID, parseLong(map, CLONE_NEWPID));
			cloneFlagsMap.put(CLONE_NEWUTS, parseLong(map, CLONE_NEWUTS));
			cloneFlagsMap.put(CLONE_PARENT, parseLong(map, CLONE_PARENT));
			cloneFlagsMap.put(CLONE_PARENT_SETTID, parseLong(map, CLONE_PARENT_SETTID));
			cloneFlagsMap.put(CLONE_PTRACE, parseLong(map, CLONE_PTRACE));
			cloneFlagsMap.put(CLONE_SETTLS, parseLong(map, CLONE_SETTLS));
			cloneFlagsMap.put(CLONE_SIGHAND, parseLong(map, CLONE_SIGHAND));
			cloneFlagsMap.put(CLONE_SYSVSEM, parseLong(map, CLONE_SYSVSEM));
			cloneFlagsMap.put(CLONE_THREAD, parseLong(map, CLONE_THREAD));
			cloneFlagsMap.put(CLONE_UNTRACED, parseLong(map, CLONE_UNTRACED));
			cloneFlagsMap.put(CLONE_VFORK, parseLong(map, CLONE_VFORK));
			cloneFlagsMap.put(CLONE_VM, parseLong(map, CLONE_VM));
			cloneFlagsMap.put(SIGCHLD, parseLong(map, SIGCHLD));

		}catch(Exception e){
			throw new Exception("Failed to initialize from file '" + filePath + "'", e);
		}
	}

	private final boolean testBits(final long needle, final long hay){
		return (needle & hay) == needle;
	}
	
	private final boolean testOpenFlags(final String flagName, final long hay){
		return testBits(openFlagsMap.get(flagName), hay);
	}
	
	public final long getBitwiseOROfOpenFlags(final String ... flagNames){
		long value = 0;
		for(final String flagName : flagNames){
			value = value | openFlagsMap.get(flagName);
		}
		return value;
	}

	public final boolean testForOpenFlagReadOnly(final long value){
		if(testOpenFlags(O_WRONLY, value) || testOpenFlags(O_RDWR, value)){
			return false;
		}else{
			return testOpenFlags(O_RDONLY, value);
		}
	}

	public final boolean testForOpenFlagAppend(final long value){
		return testOpenFlags(O_APPEND, value);
	}

	public final boolean testForOpenFlagCreate(final long value){
		return testOpenFlags(O_CREAT, value);
	}

	public final boolean testForOpenFlagsThatAllowModification(final long value){
		return testOpenFlags(O_WRONLY, value)
				|| testOpenFlags(O_RDWR, value)
				|| testOpenFlags(O_APPEND, value)
				|| testOpenFlags(O_TRUNC, value);
	}

	public final String stringifyOpenFlags(final long value){
		final String separator = "|";

		String flagsAnnotation = "";

		final boolean isWriteOnly = testOpenFlags(O_WRONLY, value);
		final boolean isReadWrite = testOpenFlags(O_RDWR, value);
		
		if(isWriteOnly){
			flagsAnnotation += O_WRONLY + separator;
		}
		if(isReadWrite){
			flagsAnnotation += O_RDWR + separator;
		}
		// If neither write-only nor read-write then must be read only
		if(!isWriteOnly && !isReadWrite){
			if(testOpenFlags(O_RDONLY, value)){
				flagsAnnotation += O_RDONLY + separator;
			}
		}

		if(testOpenFlags(O_APPEND, value)){
			flagsAnnotation += O_APPEND + separator;
		}

		if(testOpenFlags(O_TRUNC, value)){
			flagsAnnotation += O_TRUNC + separator;
		}

		if(testOpenFlags(O_CREAT, value)){
			flagsAnnotation += O_CREAT + separator;
		}

		if(!flagsAnnotation.isEmpty()){
			flagsAnnotation = flagsAnnotation.substring(0, flagsAnnotation.length() - separator.length());
		}
		return flagsAnnotation;
	}

	public final boolean isAnonymousMmap(final long value){
		return testBits(mmapFlagsMap.get(MAP_ANONYMOUS), value);
	}

	public final boolean isDuplicateFDFlagInFcntl(final long value){
		return value == fcntlFlagsMap.get(F_DUPFD) || value == fcntlFlagsMap.get(F_DUPFD_CLOEXEC);
	}

	public final boolean isSettingFDFlagsInFcntl(final long value){
		return value == fcntlFlagsMap.get(F_SETFL);
	}

	public final boolean isConnectInProgress(final long value){
		return value == (-1 * errnoMap.get(EINPROGRESS));
	}

	public final boolean isSocketTypeTCP(final long socketType){
		return socketType == socketTypeMap.get(SOCK_SEQPACKET) || socketType == socketTypeMap.get(SOCK_STREAM);
	}

	public final boolean isSocketTypeUDP(final long socketType){
		return socketType == socketTypeMap.get(SOCK_DGRAM);
	}

	public final boolean isSocketDomainNetwork(final long domain){
		return domain == addressFamilyMap.get(AF_INET) 
				|| domain == addressFamilyMap.get(AF_INET6)
				|| domain == addressFamilyMap.get(PF_INET) 
				|| domain == addressFamilyMap.get(PF_INET6);
	}

	public final boolean isSocketDomainLocal(final long domain){
		return domain == addressFamilyMap.get(AF_LOCAL) 
				|| domain == addressFamilyMap.get(AF_UNIX)
				|| domain == addressFamilyMap.get(PF_LOCAL)
				|| domain == addressFamilyMap.get(PF_UNIX);
	}

	public final String getPtraceActionName(final long action){
		for(final Map.Entry<String, Long> entry : ptraceActionMap.entrySet()){
			if(entry.getValue() == action){
				return entry.getKey();
			}
		}
		return null;
	}

	public final String getMadviseAdviceName(final long advice){
		for(final Map.Entry<String, Long> entry : madviseAdviceMap.entrySet()){
			if(entry.getValue() == advice){
				return entry.getKey();
			}
		}
		return null;
	}

	public final String getMadviseAllowedAdviceNames(){
		return stringifyMapForLogging(madviseAdviceMap);
	}

	public final String getLseekWhenceName(final long whence){
		for(final Map.Entry<String, Long> entry : lseekWhenceMap.entrySet()){
			if(entry.getValue() == whence){
				return entry.getKey();
			}
		}
		return null;
	}

	public final String getLseekAllowedWhenceNames(){
		return stringifyMapForLogging(lseekWhenceMap);
	}

	private final boolean testCloneFlags(final String flagName, final long hay){
		return testBits(cloneFlagsMap.get(flagName), hay);
	}

	public final boolean testForCloneFlagSigChild(final long value){
		return testCloneFlags(SIGCHLD, value);
	}

	public final boolean testForCloneFlagVM(final long value){
		return testCloneFlags(CLONE_VM, value);
	}

	public final boolean testForCloneFlagVfork(final long value){
		return testCloneFlags(CLONE_VFORK, value);
	}

	public final boolean testForCloneFlagThread(final long value){
		return testCloneFlags(CLONE_THREAD, value);
	}

	public final boolean isCloneWithSharedMemory(final long value){
		return testCloneFlags(CLONE_VM, value);
	}

	public final boolean isCloneWithSharedFileDescriptors(final long value){
		return testCloneFlags(CLONE_FILES, value);
	}

	public final boolean isCloneWithSharedFileSystem(final long value){
		return testCloneFlags(CLONE_FS, value);
	}

	public final String stringifyCloneFlags(final long value){
		final String separator = "|";

		String flagsAnnotation = "";

		for(final String flagName : cloneFlagsMap.keySet()){
			if(testCloneFlags(flagName, value)){
				flagsAnnotation += flagName + separator;
			}
		}

		if(!flagsAnnotation.isEmpty()){
			flagsAnnotation = flagsAnnotation.substring(0, flagsAnnotation.length() - separator.length());
		}
		return flagsAnnotation;
	}

	private final String stringifyMapForLogging(final Map<String, Long> map){
		final String separator = ", ";
		String str = "";
		for(final Map.Entry<String, Long> entry : map.entrySet()){
			String name = entry.getKey();
			Long value = entry.getValue();
			str += name + "("+value+")" + separator;
		}
		if(!str.isEmpty()){
			str = str.substring(0, str.length() - separator.length());
		}
		return str;
	}
	
	public String toString(){
		return "LinuxConstants [configFilePath=" + configFilePath + "]";
	}
}
