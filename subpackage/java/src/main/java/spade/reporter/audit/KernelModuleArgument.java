/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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
import java.util.List;
import java.util.Set;

import spade.reporter.audit.ProcessUserSyscallFilter.UserMode;

/**
 * Represents kernel module arguments for the SPADE audit kernel module.
 * This class mirrors the parameters defined in the kernel module's param.c.
 */
public class KernelModuleArgument{

	// Parameter name constants
	public static final String PARAM_NF_HANDLE_USER = "nf_handle_user";
	public static final String PARAM_NF_HOOKS = "nf_hooks";
	public static final String PARAM_NF_HOOKS_LOG_ALL_CT = "nf_hooks_log_all_ct";
	public static final String PARAM_LOG_SYSCALLS = "log_syscalls";
	public static final String PARAM_NET_IO = "net_io";
	public static final String PARAM_NAMESPACES = "namespaces";
	public static final String PARAM_PID_TRACE_MODE = "pid_trace_mode";
	public static final String PARAM_PIDS = "pids";
	public static final String PARAM_PPID_TRACE_MODE = "ppid_trace_mode";
	public static final String PARAM_PPIDS = "ppids";
	public static final String PARAM_UID_TRACE_MODE = "uid_trace_mode";
	public static final String PARAM_UIDS = "uids";
	public static final String PARAM_CONFIG_FILE = "config_file";
	public static final String PARAM_HARDEN_TGIDS = "harden_tgids";
	public static final String PARAM_AUTHORIZED_UIDS = "authorized_uids";

	// Default values
	public static final boolean DEFAULT_NF_HANDLE_USER = false;
	public static final boolean DEFAULT_NF_HOOKS = false;
	public static final MonitorConnectionType DEFAULT_NF_HOOKS_LOG_ALL_CT = MonitorConnectionType.ALL;
	public static final MonitorSyscallType DEFAULT_LOG_SYSCALLS = MonitorSyscallType.ONLY_SUCCESSFUL;
	public static final boolean DEFAULT_NET_IO = false;
	public static final boolean DEFAULT_NAMESPACES = false;
	public static final MonitorMode DEFAULT_PID_TRACE_MODE = MonitorMode.IGNORE;
	public static final MonitorMode DEFAULT_PPID_TRACE_MODE = MonitorMode.IGNORE;
	public static final MonitorMode DEFAULT_UID_TRACE_MODE = MonitorMode.IGNORE;
	public static final String DEFAULT_CONFIG_FILE = "/opt/spade/audit/audit.config";

	// Maximum array lengths
	public static final int MAX_PID_ARRAY_LENGTH = 64;
	public static final int MAX_UID_ARRAY_LENGTH = 64;

	/**
	 * Monitoring mode for PIDs and UIDs
	 */
	public enum MonitorMode{
		CAPTURE(0, "Capture the specified list of ids"),
		IGNORE(1, "Ignore the specified list of ids");

		private final int value;
		private final String description;

		MonitorMode(int value, String description){
			this.value = value;
			this.description = description;
		}

		public int getValue(){
			return value;
		}

		public String getDescription(){
			return description;
		}
	}

	/**
	 * System call monitoring types based on result
	 */
	public enum MonitorSyscallType{
		ALL(-1, "Monitor syscalls with any result"),
		ONLY_FAILED(0, "Monitor failed syscalls"),
		ONLY_SUCCESSFUL(1, "Monitor successful syscalls");

		private final int value;
		private final String description;

		MonitorSyscallType(int value, String description){
			this.value = value;
			this.description = description;
		}

		public int getValue(){
			return value;
		}

		public String getDescription(){
			return description;
		}
	}

	/**
	 * Connection monitoring types
	 */
	public enum MonitorConnectionType{
		ALL(-1, "Monitor packets with all connection states"),
		ONLY_NEW(0, "Monitor packets with only new connection states");

		private final int value;
		private final String description;

		MonitorConnectionType(int value, String description){
			this.value = value;
			this.description = description;
		}

		public int getValue(){
			return value;
		}

		public String getDescription(){
			return description;
		}
	}

	// Netfilter arguments
	private boolean nfHandleUser;
	private boolean nfHooks;
	private MonitorConnectionType nfHooksLogAllCt;

	// General monitoring flags
	private MonitorSyscallType logSyscalls;
	private boolean netIO;
	private boolean namespaces;

	// PID monitoring
	private MonitorMode pidTraceMode;
	private List<Integer> pids;

	// PPID monitoring
	private MonitorMode ppidTraceMode;
	private List<Integer> ppids;

	// UID monitoring
	private MonitorMode uidTraceMode;
	private List<Integer> uids;

	// Configuration
	private String configFile;

	// Hardening
	private List<Integer> hardenTgids;
	private List<Integer> authorizedUids;

	/**
	 * Constructor with default values
	 */
	public KernelModuleArgument(){
		this.nfHandleUser = DEFAULT_NF_HANDLE_USER;
		this.nfHooks = DEFAULT_NF_HOOKS;
		this.nfHooksLogAllCt = DEFAULT_NF_HOOKS_LOG_ALL_CT;
		this.logSyscalls = DEFAULT_LOG_SYSCALLS;
		this.netIO = DEFAULT_NET_IO;
		this.namespaces = DEFAULT_NAMESPACES;
		this.pidTraceMode = DEFAULT_PID_TRACE_MODE;
		this.pids = new ArrayList<>();
		this.ppidTraceMode = DEFAULT_PPID_TRACE_MODE;
		this.ppids = new ArrayList<>();
		this.uidTraceMode = DEFAULT_UID_TRACE_MODE;
		this.uids = new ArrayList<>();
		this.configFile = DEFAULT_CONFIG_FILE;
		this.hardenTgids = new ArrayList<>();
		this.authorizedUids = new ArrayList<>();
	}

	// Create instance from raw argument
	public static KernelModuleArgument createArgument(
		final String userId, final UserMode userMode,
		final Set<String> pidsToIgnore, final Set<String> ppidsToIgnore,
		final boolean hookSendRecv, final boolean namespaces,
		final boolean networkAddressTranslation, final boolean netfilterHooksLogCt, final boolean netfilterHooksUser,
		final Set<String> hardenTgids, final Set<String> authorizedUids
	){
		final KernelModuleArgument k = new KernelModuleArgument();

		// Set user ID monitoring
		if(userId != null && !userId.isEmpty()){
			try{
				int uid = Integer.parseInt(userId);
				List<Integer> uidList = new ArrayList<>();
				uidList.add(uid);
				k.setUids(uidList);

				// Set UID trace mode based on user mode
				if(userMode == UserMode.CAPTURE){
					k.setUidTraceMode(MonitorMode.CAPTURE);
				}else if(userMode == UserMode.IGNORE){
					k.setUidTraceMode(MonitorMode.IGNORE);
				}
			}catch(NumberFormatException e){
				throw new IllegalArgumentException("Invalid user id", e);
			}
		}

		// Set PID filtering
		if(pidsToIgnore != null && !pidsToIgnore.isEmpty()){
			List<Integer> pidList = new ArrayList<>();
			for(String pidStr : pidsToIgnore){
				try{
					pidList.add(Integer.parseInt(pidStr));
				}catch(NumberFormatException e){
					throw new IllegalArgumentException("Invalid pid", e);
				}
			}
			if(!pidList.isEmpty()){
				k.setPids(pidList);
				k.setPidTraceMode(MonitorMode.IGNORE);
			}
		}

		// Set PPID filtering
		if(ppidsToIgnore != null && !ppidsToIgnore.isEmpty()){
			List<Integer> ppidList = new ArrayList<>();
			for(String ppidStr : ppidsToIgnore){
				try{
					ppidList.add(Integer.parseInt(ppidStr));
				}catch(NumberFormatException e){
					throw new IllegalArgumentException("Invalid ppid", e);
				}
			}
			if(!ppidList.isEmpty()){
				k.setPpids(ppidList);
				k.setPpidTraceMode(MonitorMode.IGNORE);
			}
		}

		// Set network I/O monitoring (send/recv hooks)
		k.setNetIO(hookSendRecv);

		// Set namespaces monitoring
		k.setNamespaces(namespaces);

		// Set netfilter hooks
		k.setNfHooks(networkAddressTranslation);

		// Set netfilter connection tracking mode
		if(netfilterHooksLogCt){
			k.setNfHooksLogAllCt(MonitorConnectionType.ONLY_NEW);
		}else{
			k.setNfHooksLogAllCt(MonitorConnectionType.ALL);
		}

		// Set netfilter user-space handling
		k.setNfHandleUser(netfilterHooksUser);

		// Set harden TGIDs
		if(hardenTgids != null && !hardenTgids.isEmpty()){
			List<Integer> hardenTgidList = new ArrayList<>();
			for(String tgidStr : hardenTgids){
				try{
					hardenTgidList.add(Integer.parseInt(tgidStr));
				}catch(NumberFormatException e){
					throw new IllegalArgumentException("Invalid harden tgid", e);
				}
			}
			if(!hardenTgidList.isEmpty()){
				k.setHardenTgids(hardenTgidList);
			}
		}

		// Set authorized UIDs
		if(authorizedUids != null && !authorizedUids.isEmpty()){
			List<Integer> authorizedUidList = new ArrayList<>();
			for(String uidStr : authorizedUids){
				try{
					authorizedUidList.add(Integer.parseInt(uidStr));
				}catch(NumberFormatException e){
					throw new IllegalArgumentException("Invalid authorized uid", e);
				}
			}
			if(!authorizedUidList.isEmpty()){
				k.setAuthorizedUids(authorizedUidList);
			}
		}

		return k;
	}

	// Getters and Setters

	public boolean isNfHandleUser(){
		return nfHandleUser;
	}

	public void setNfHandleUser(boolean nfHandleUser){
		this.nfHandleUser = nfHandleUser;
	}

	public boolean isNfHooks(){
		return nfHooks;
	}

	public void setNfHooks(boolean nfHooks){
		this.nfHooks = nfHooks;
	}

	public MonitorConnectionType getNfHooksLogAllCt(){
		return nfHooksLogAllCt;
	}

	public void setNfHooksLogAllCt(MonitorConnectionType nfHooksLogAllCt){
		this.nfHooksLogAllCt = nfHooksLogAllCt;
	}

	public MonitorSyscallType getLogSyscalls(){
		return logSyscalls;
	}

	public void setLogSyscalls(MonitorSyscallType logSyscalls){
		this.logSyscalls = logSyscalls;
	}

	public boolean isNetIO(){
		return netIO;
	}

	public void setNetIO(boolean netIO){
		this.netIO = netIO;
	}

	public boolean isNamespaces(){
		return namespaces;
	}

	public void setNamespaces(boolean namespaces){
		this.namespaces = namespaces;
	}

	public MonitorMode getPidTraceMode(){
		return pidTraceMode;
	}

	public void setPidTraceMode(MonitorMode pidTraceMode){
		this.pidTraceMode = pidTraceMode;
	}

	public List<Integer> getPids(){
		return pids;
	}

	public void setPids(List<Integer> pids){
		if(pids.size() > MAX_PID_ARRAY_LENGTH){
			throw new IllegalArgumentException("PID array length exceeds maximum of " + MAX_PID_ARRAY_LENGTH);
		}
		this.pids = pids;
	}

	public MonitorMode getPpidTraceMode(){
		return ppidTraceMode;
	}

	public void setPpidTraceMode(MonitorMode ppidTraceMode){
		this.ppidTraceMode = ppidTraceMode;
	}

	public List<Integer> getPpids(){
		return ppids;
	}

	public void setPpids(List<Integer> ppids){
		if(ppids.size() > MAX_PID_ARRAY_LENGTH){
			throw new IllegalArgumentException("PPID array length exceeds maximum of " + MAX_PID_ARRAY_LENGTH);
		}
		this.ppids = ppids;
	}

	public MonitorMode getUidTraceMode(){
		return uidTraceMode;
	}

	public void setUidTraceMode(MonitorMode uidTraceMode){
		this.uidTraceMode = uidTraceMode;
	}

	public List<Integer> getUids(){
		return uids;
	}

	public void setUids(List<Integer> uids){
		if(uids.size() > MAX_UID_ARRAY_LENGTH){
			throw new IllegalArgumentException("UID array length exceeds maximum of " + MAX_UID_ARRAY_LENGTH);
		}
		this.uids = uids;
	}

	public String getConfigFile(){
		return configFile;
	}

	public void setConfigFile(String configFile){
		this.configFile = configFile;
	}

	public List<Integer> getHardenTgids(){
		return hardenTgids;
	}

	public void setHardenTgids(List<Integer> hardenTgids){
		if(hardenTgids.size() > MAX_PID_ARRAY_LENGTH){
			throw new IllegalArgumentException("Harden TGIDs array length exceeds maximum of " + MAX_PID_ARRAY_LENGTH);
		}
		this.hardenTgids = hardenTgids;
	}

	public List<Integer> getAuthorizedUids(){
		return authorizedUids;
	}

	public void setAuthorizedUids(List<Integer> authorizedUids){
		if(authorizedUids.size() > MAX_UID_ARRAY_LENGTH){
			throw new IllegalArgumentException("Authorized UIDs array length exceeds maximum of " + MAX_UID_ARRAY_LENGTH);
		}
		this.authorizedUids = authorizedUids;
	}

	/**
	 * Builds a kernel module argument string suitable for insmod
	 *
	 * @return formatted argument string
	 */
	public String toModuleArgumentString(){
		StringBuilder args = new StringBuilder();

		args.append(PARAM_NF_HANDLE_USER).append("=").append(nfHandleUser ? 1 : 0);
		args.append(" ").append(PARAM_NF_HOOKS).append("=").append(nfHooks ? 1 : 0);
		args.append(" ").append(PARAM_NF_HOOKS_LOG_ALL_CT).append("=").append(nfHooksLogAllCt.getValue());
		args.append(" ").append(PARAM_NET_IO).append("=").append(netIO ? 1 : 0);
		args.append(" ").append(PARAM_NAMESPACES).append("=").append(namespaces ? 1 : 0);

		// PID monitoring
		args.append(" ").append(PARAM_PID_TRACE_MODE).append("=").append(pidTraceMode.getValue());
		if(!pids.isEmpty()){
			args.append(" ").append(PARAM_PIDS).append("=\"");
			for(int i = 0; i < pids.size(); i++){
				if(i > 0) args.append(",");
				args.append(pids.get(i));
			}
			args.append("\"");
		}

		// PPID monitoring
		args.append(" ").append(PARAM_PPID_TRACE_MODE).append("=").append(ppidTraceMode.getValue());
		if(!ppids.isEmpty()){
			args.append(" ").append(PARAM_PPIDS).append("=\"");
			for(int i = 0; i < ppids.size(); i++){
				if(i > 0) args.append(",");
				args.append(ppids.get(i));
			}
			args.append("\"");
		}

		// UID monitoring
		args.append(" ").append(PARAM_UID_TRACE_MODE).append("=").append(uidTraceMode.getValue());
		if(!uids.isEmpty()){
			args.append(" ").append(PARAM_UIDS).append("=\"");
			for(int i = 0; i < uids.size(); i++){
				if(i > 0) args.append(",");
				args.append(uids.get(i));
			}
			args.append("\"");
		}

		// Configuration file
		args.append(" ").append(PARAM_CONFIG_FILE).append("=\"").append(configFile).append("\"");

		// Harden PIDs
		if(!hardenTgids.isEmpty()){
			args.append(" ").append(PARAM_HARDEN_TGIDS).append("=\"");
			for(int i = 0; i < hardenTgids.size(); i++){
				if(i > 0) args.append(",");
				args.append(hardenTgids.get(i));
			}
			args.append("\"");
		}

		// Authorized UIDs
		if(!authorizedUids.isEmpty()){
			args.append(" ").append(PARAM_AUTHORIZED_UIDS).append("=\"");
			for(int i = 0; i < authorizedUids.size(); i++){
				if(i > 0) args.append(",");
				args.append(authorizedUids.get(i));
			}
			args.append("\"");
		}

		return args.toString();
	}

	@Override
	public String toString(){
		return "KernelModuleArgument{" +
				"nfHandleUser=" + nfHandleUser +
				", nfHooks=" + nfHooks +
				", nfHooksLogAllCt=" + nfHooksLogAllCt +
				", logSyscalls=" + logSyscalls +
				", netIO=" + netIO +
				", namespaces=" + namespaces +
				", pidTraceMode=" + pidTraceMode +
				", pids=" + pids +
				", ppidTraceMode=" + ppidTraceMode +
				", ppids=" + ppids +
				", uidTraceMode=" + uidTraceMode +
				", uids=" + uids +
				", configFile='" + configFile + '\'' +
				", hardenTgids=" + hardenTgids +
				", authorizedUids=" + authorizedUids +
				'}';
	}
}
