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
package spade.reporter;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.reporter.audit.AuditControlManager;
import spade.reporter.audit.AuditEventReader;
import spade.reporter.audit.Globals;
import spade.reporter.audit.IPCManager;
import spade.reporter.audit.Input;
import spade.reporter.audit.KernelModuleConfiguration;
import spade.reporter.audit.KernelModuleManager;
import spade.reporter.audit.LinuxConstants;
import spade.reporter.audit.LinuxPathResolver;
import spade.reporter.audit.MalformedAuditDataException;
import spade.reporter.audit.NetfilterHooksManager;
import spade.reporter.audit.OPMConstants;
import spade.reporter.audit.OutputLog;
import spade.reporter.audit.PathRecord;
import spade.reporter.audit.ProcessUserSyscallFilter;
import spade.reporter.audit.ProcessUserSyscallFilter.SystemCallRuleType;
import spade.reporter.audit.SPADEAuditBridgeProcess;
import spade.reporter.audit.SYSCALL;
import spade.reporter.audit.artifact.ArtifactIdentifier;
import spade.reporter.audit.artifact.ArtifactManager;
import spade.reporter.audit.artifact.DirectoryIdentifier;
import spade.reporter.audit.artifact.MemoryIdentifier;
import spade.reporter.audit.artifact.NetworkSocketIdentifier;
import spade.reporter.audit.artifact.PathIdentifier;
import spade.reporter.audit.artifact.PosixMessageQueue;
import spade.reporter.audit.artifact.UnixSocketIdentifier;
import spade.reporter.audit.artifact.UnknownIdentifier;
import spade.reporter.audit.artifact.UnnamedNetworkSocketPairIdentifier;
import spade.reporter.audit.artifact.UnnamedPipeIdentifier;
import spade.reporter.audit.artifact.UnnamedUnixSocketPairIdentifier;
import spade.reporter.audit.process.FileDescriptor;
import spade.reporter.audit.process.ProcessManager;
import spade.reporter.audit.process.ProcessWithAgentManager;
import spade.reporter.audit.process.ProcessWithoutAgentManager;
import spade.utility.HelperFunctions;
import spade.utility.profile.Intervaler;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 * @author Dawood Tariq, Sharjeel Ahmed Qureshi
 */
public class Audit extends AbstractReporter {

	static final Logger logger = Logger.getLogger(Audit.class.getName());

	private final Object cleanupLock = new Object();
	
	private LinuxConstants platformConstants = null;

	/********************** PROCESS STATE - START *************************/
	
	private ProcessManager processManager;

	/********************** PROCESS STATE - END *************************/
	
	/********************** ARITFACT STATE - START *************************/
	
	private ArtifactManager artifactManager;
	
	/********************** ARTIFACT STATE - END *************************/
	
	/********************** NETFILTER HOOKS STATE - START *************************/
	
	private NetfilterHooksManager netfilterHooksManager;
	
	/********************** NETFILTER HOOKS STATE - END *************************/
	
	private IPCManager ipcManager = new IPCManager(this);

	/********************** BEHAVIOR FLAGS - START *************************/
	
	private Globals globals = null;
	
	// These are the default values
	private boolean FAIL_FAST = true;
	private boolean USE_READ_WRITE = false;
	private boolean USE_SOCK_SEND_RCV = false;
	private boolean CREATE_BEEP_UNITS = false;
	private boolean SIMPLIFY = true;
	private boolean PROCFS = false;
	private boolean WAIT_FOR_LOG_END = true;
	private boolean AGENTS = false;
	private boolean CONTROL = true;
	private boolean USE_MEMORY_SYSCALLS = true;
	private String AUDITCTL_SYSCALL_SUCCESS_FLAG = "1";
	private boolean ANONYMOUS_MMAP = true;
	private Integer mergeUnit = null;
	private String REPORT_KILL_KEY = "reportKill";
	private boolean REPORT_KILL = true;
	private final String HANDLE_CHDIR_KEY = "cwd";
	private boolean HANDLE_CHDIR = true;
	private final String HANDLE_ROOTFS_KEY = "rootFS";
	private boolean HANDLE_ROOTFS = true;
	private final String HANDLE_NAMESPACES_KEY = "namespaces";
	private boolean HANDLE_NAMESPACES = false;
	private final String NETFILTER_HOOKS_KEY = "netfilterHooks";
	private boolean NETFILTER_HOOKS = false;
	private final String HANDLE_NETFILTER_HOOKS_KEY = "handleNetfilterHooks";
	private boolean HANDLE_NETFILTER_HOOKS = false;
	private final String NETFILTER_HOOKS_LOG_CT_KEY = "netfilterHooksLogCT";
	private boolean NETFILTER_HOOKS_LOG_CT = false;
	private final String NETFILTER_HOOKS_USER_KEY = "netfilterHooksUser";
	private boolean NETFILTER_HOOKS_USER = false;
	private final String CAPTURE_IPC_KEY = "logIpc";
	private boolean CAPTURE_IPC = false;
	private final String REPORT_IPC_KEY = "reportIpc";
	private boolean REPORT_IPC = false;
	
	private KernelModuleConfiguration kernelModuleConfiguration;
	
	private boolean excludeProctitle;
	private boolean filesystemCredentialUpdates;
	
	private OutputLog outputLog;
	private Intervaler reportingIntervaler;
	
	private Input input;
	
	private ProcessUserSyscallFilter processUserSyscallFilter;
	
	private SPADEAuditBridgeProcess spadeAuditBridgeProcess;
	
	private AuditEventReader auditEventReader;
	// A flag to block on shutdown call if buffers are being emptied and events are still being read
	private volatile boolean isMainEventLoopThreadRunning = false;
	private final Thread mainEventLoopThread = new Thread(new Runnable(){
		@Override
		public void run(){
			isMainEventLoopThreadRunning = true;
			while(true){
				Map<String, String> eventData = null;
				try{
					eventData = auditEventReader.readEventData();
					if(eventData == null){
						// EOF
						break;
					}else{
						try{
							finishEvent(eventData);
						}catch(Exception e){
							logger.log(Level.SEVERE, "Failed to handle event: " + eventData, e);
							if(FAIL_FAST){
								break;
							}
						}
					}
				}catch(MalformedAuditDataException made){
					logger.log(Level.SEVERE, "Failed to parse event", made);
					if(FAIL_FAST){
						break;
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, "Stopped reading event stream. ", e);
					break;
				}
			}
			try{
				if(auditEventReader != null){
					auditEventReader.close();
				}
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to close audit event reader", e);
			}

			final boolean forceStopSPADEAuditBridge = true;
			doCleanup(forceStopSPADEAuditBridge);
			logger.log(Level.INFO, "Exiting event reader thread for SPADE audit bridge");
			isMainEventLoopThreadRunning = false;
		}
	}, "Audit-Event-Loop-Thread");
	
	/********************** BEHAVIOR FLAGS - END *************************/
	
	private final String AUDIT_SYSCALL_SOURCE = OPMConstants.SOURCE_AUDIT_SYSCALL;

	public static final String PROTOCOL_NAME_UDP = "udp",
			PROTOCOL_NAME_TCP = "tcp";
	private final String IPV4_NETWORK_SOCKET_SADDR_PREFIX = "02";
	private final String IPV6_NETWORK_SOCKET_SADDR_PREFIX = "0A";
	private final String UNIX_SOCKET_SADDR_PREFIX = "01";
	private final String NETLINK_SOCKET_SADDR_PREFIX = "10";
	
	private boolean isNetlinkSaddr(String saddr){
		return saddr != null && saddr.startsWith(NETLINK_SOCKET_SADDR_PREFIX);
	}
	private boolean isUnixSaddr(String saddr){
		return saddr != null && saddr.startsWith(UNIX_SOCKET_SADDR_PREFIX);
	}
	private boolean isNetworkSaddr(String saddr){
		return saddr != null && (isIPv4Saddr(saddr) || isIPv6Saddr(saddr));
	}
	private boolean isIPv4Saddr(String saddr){
		return saddr != null && saddr.startsWith(IPV4_NETWORK_SOCKET_SADDR_PREFIX);
	}
	private boolean isIPv6Saddr(String saddr){
		return saddr != null && saddr.startsWith(IPV6_NETWORK_SOCKET_SADDR_PREFIX);
	}

	/**
	 * Returns true if the argument is null, true, false, 1, 0, yes or no.
	 * Else returns false.
	 * 
	 * @param str value to check
	 * @return true/false
	 */
	private boolean isValidBoolean(String str){
		return str == null 
				|| "true".equalsIgnoreCase(str.trim()) || "false".equalsIgnoreCase(str.trim())
				|| "1".equals(str.trim()) || "0".equals(str.trim())
				|| "yes".equalsIgnoreCase(str.trim()) || "no".equals(str.trim())
				|| "on".equalsIgnoreCase(str.trim()) || "off".equals(str.trim());

	}
	
	/**
	 * If the argument is null or doesn't match any of the value boolean options:
	 * true, false, 1, 0, yes or no then default value is returned. Else the parsed
	 * value is returned.
	 * 
	 * @param str string to convert to boolean
	 * @param defaultValue default value
	 * @return true/false
	 */
	private boolean parseBoolean(String str, boolean defaultValue){
		if(str == null){
			return defaultValue;
		}else{
			str = str.trim();
			if(str.equals("1") || str.equalsIgnoreCase("yes") || str.equalsIgnoreCase("true") || str.equalsIgnoreCase("on")){
				return true;
			}else if(str.equals("0") || str.equalsIgnoreCase("no") || str.equalsIgnoreCase("false") || str.equalsIgnoreCase("off")){
				return false;
			}else{
				return defaultValue;
			}
		}
	}
	
	public final boolean getFlagControl(){
		return CONTROL;
	}
	
	/**
	 * Initializes global boolean flags for this reporter
	 * 
	 * @param args a map made from arguments key-values
	 * @return true if all flags had valid values / false if any of the flags had a non-boolean value
	 */
	private boolean initFlagsFromArguments(Map<String, String> args){
		try{
			globals = Globals.parseArguments(args);
			if(globals == null){
				throw new Exception("NULL globals object. Failed to initialize flags.");
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to parse arguments", e);
			return false;
		}
		
		String argValue = args.get("failfast");
		if(isValidBoolean(argValue)){
			FAIL_FAST = parseBoolean(argValue, FAIL_FAST);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'failfast': " + argValue);
			return false;
		}
		
		argValue = args.get("excludeProctitle");
		if(isValidBoolean(argValue)){
			excludeProctitle = parseBoolean(argValue, true);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'excludeProctitle': " + argValue);
			return false;
		}

		argValue = args.get("fsids");
		if(isValidBoolean(argValue)){
			filesystemCredentialUpdates = parseBoolean(argValue, false);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'fsids': " + argValue);
			return false;
		}
		
		argValue = args.get("fileIO");
		if(isValidBoolean(argValue)){
			USE_READ_WRITE = parseBoolean(argValue, USE_READ_WRITE);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'fileIO': " + argValue);
			return false;
		}
		
		argValue = args.get("netIO");
		if(isValidBoolean(argValue)){
			USE_SOCK_SEND_RCV = parseBoolean(argValue, USE_SOCK_SEND_RCV);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'netIO': " + argValue);
			return false;
		}
		
		argValue = args.get("units");
		if(isValidBoolean(argValue)){
			CREATE_BEEP_UNITS = parseBoolean(argValue, CREATE_BEEP_UNITS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'units': " + argValue);
			return false;
		}

		argValue = args.get("agents");
		if(isValidBoolean(argValue)){
			AGENTS = parseBoolean(argValue, AGENTS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'agents': " + argValue);
			return false;
		}

		// Arguments below are only for experimental use
		argValue = args.get("simplify");
		if(isValidBoolean(argValue)){
			SIMPLIFY = parseBoolean(argValue, SIMPLIFY);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'simplify': " + argValue);
			return false;
		}
		
		argValue = args.get("procFS");
		if(isValidBoolean(argValue)){
			PROCFS = parseBoolean(argValue, PROCFS);
			if(PROCFS){
				logger.log(Level.SEVERE, "'procFS' cannot be enabled!");
				return false;
			}
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'procFS': " + argValue);
			return false;
		}

		argValue = args.get("waitForLog");
		if(isValidBoolean(argValue)){
			WAIT_FOR_LOG_END = parseBoolean(argValue, WAIT_FOR_LOG_END);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'waitForLog': " + argValue);
			return false;
		}
		
		argValue = args.get("memorySyscalls");
		if(isValidBoolean(argValue)){
			USE_MEMORY_SYSCALLS = parseBoolean(argValue, USE_MEMORY_SYSCALLS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'memorySyscalls': " + argValue);
			return false;
		}

		argValue = args.get("control");
		if(isValidBoolean(argValue)){
			CONTROL = parseBoolean(argValue, CONTROL);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'control': " + argValue);
			return false;
		}
		
		argValue = args.get("anonymousMmap");
		if(isValidBoolean(argValue)){
			ANONYMOUS_MMAP = parseBoolean(argValue, ANONYMOUS_MMAP);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'anonymousMmap': " + argValue);
			return false;
		}
		
		argValue = args.get(REPORT_KILL_KEY);
		if(isValidBoolean(argValue)){
			REPORT_KILL = parseBoolean(argValue, REPORT_KILL);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+REPORT_KILL_KEY+"': " + argValue);
			return false;
		}
		
		argValue = args.get(HANDLE_NETFILTER_HOOKS_KEY);
		if(isValidBoolean(argValue)){
			HANDLE_NETFILTER_HOOKS = parseBoolean(argValue, HANDLE_NETFILTER_HOOKS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+HANDLE_NETFILTER_HOOKS_KEY+"': " + argValue);
			return false;
		}

		argValue = args.get(NETFILTER_HOOKS_KEY);
		if(isValidBoolean(argValue)){
			NETFILTER_HOOKS = parseBoolean(argValue, NETFILTER_HOOKS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+NETFILTER_HOOKS_KEY+"': " + argValue);
			return false;
		}

		argValue = args.get(NETFILTER_HOOKS_LOG_CT_KEY);
		if(isValidBoolean(argValue)){
			NETFILTER_HOOKS_LOG_CT = parseBoolean(argValue, NETFILTER_HOOKS_LOG_CT);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+NETFILTER_HOOKS_LOG_CT_KEY+"': " + argValue);
			return false;
		}

		argValue = args.get(NETFILTER_HOOKS_USER_KEY);
		if(isValidBoolean(argValue)){
			NETFILTER_HOOKS_USER = parseBoolean(argValue, NETFILTER_HOOKS_USER);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+NETFILTER_HOOKS_USER_KEY+"': " + argValue);
			return false;
		}

		if(!kernelModuleConfiguration.isLocalEndpoints() && NETFILTER_HOOKS){
			logger.log(Level.SEVERE, "Argument '" + KernelModuleConfiguration.keyLocalEndpoints
					+ "' must be 'true' for argument '" + NETFILTER_HOOKS_KEY + "' to be 'true'");
			return false;
		}

		String mergeUnitKey = "mergeUnit";
		String mergeUnitValue = args.get(mergeUnitKey);
		if(mergeUnitValue != null){
			mergeUnit = HelperFunctions.parseInt(mergeUnitValue, null);
			if(mergeUnit != null){
				if(mergeUnit < 0){ // must be positive
					mergeUnit = null;
					logger.log(Level.SEVERE, "'"+mergeUnitKey+"' must be non-negative: '" + mergeUnitValue+"'");
					return false;
				}
			}else{
				logger.log(Level.SEVERE, "'"+mergeUnitKey+"' must be an integer: '" + mergeUnitValue+"'");
				return false;
			}
		}
		
		argValue = args.get(HANDLE_CHDIR_KEY);
		if(isValidBoolean(argValue)){
			HANDLE_CHDIR = parseBoolean(argValue, HANDLE_CHDIR);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+HANDLE_CHDIR_KEY+"': " + argValue);
			return false;
		}
		
		argValue = args.get(HANDLE_ROOTFS_KEY);
		if(isValidBoolean(argValue)){
			HANDLE_ROOTFS = parseBoolean(argValue, HANDLE_ROOTFS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+HANDLE_ROOTFS_KEY+"': " + argValue);
			return false;
		}
		
		argValue = args.get(CAPTURE_IPC_KEY);
		if(isValidBoolean(argValue)){
			CAPTURE_IPC = parseBoolean(argValue, CAPTURE_IPC);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+CAPTURE_IPC_KEY+"': " + argValue);
			return false;
		}
		
		argValue = args.get(REPORT_IPC_KEY);
		if(isValidBoolean(argValue)){
			REPORT_IPC = parseBoolean(argValue, REPORT_IPC);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+REPORT_IPC_KEY+"': " + argValue);
			return false;
		}

		if(HANDLE_ROOTFS){
			if(!HANDLE_CHDIR){
				logger.log(Level.INFO, "'"+HANDLE_CHDIR_KEY+"' set to 'true' because '"+HANDLE_ROOTFS_KEY+"'='true'");
				HANDLE_CHDIR = true;
			}
		}
		
		argValue = args.get(HANDLE_NAMESPACES_KEY);
		if(isValidBoolean(argValue)){
			HANDLE_NAMESPACES = parseBoolean(argValue, HANDLE_NAMESPACES);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for '"+HANDLE_NAMESPACES_KEY+"': " + argValue);
			return false;
		}

		// Logging only relevant flags now for debugging
		logger.log(Level.INFO,
				"Audit flags: {0}={1}, {2}={3}, {4}={5}, {6}={7}, {8}={9}, {10}={11}, {12}={13}, "
						+ "{14}={15}, {16}={17}, {18}={19}, {20}={21}, {22}={23}, {24}={25}, {26}={27}, {28}={29}, "
						+ "{30}={31}, {32}={33}, {34}={35}, {36}={37}, {38}={39}, {40}={41}, {42}={43}",
				new Object[]{"syscall", args.get("syscall"), "fileIO", USE_READ_WRITE, "netIO", USE_SOCK_SEND_RCV,
						"units", CREATE_BEEP_UNITS, "waitForLog", WAIT_FOR_LOG_END, "netfilter", false, "refineNet",
						false, KernelModuleConfiguration.keyLocalEndpoints,
						kernelModuleConfiguration.isLocalEndpoints(), KernelModuleConfiguration.keyHandleLocalEndpoints,
						kernelModuleConfiguration.isHandleLocalEndpoints(), "failfast", FAIL_FAST, mergeUnitKey,
						mergeUnit, KernelModuleConfiguration.keyHarden, kernelModuleConfiguration.isHarden(),
						REPORT_KILL_KEY, REPORT_KILL, HANDLE_CHDIR_KEY, HANDLE_CHDIR, HANDLE_ROOTFS_KEY, HANDLE_ROOTFS,
						HANDLE_NAMESPACES_KEY, HANDLE_NAMESPACES, HANDLE_NETFILTER_HOOKS_KEY, HANDLE_NETFILTER_HOOKS,
						NETFILTER_HOOKS_KEY, NETFILTER_HOOKS, NETFILTER_HOOKS_LOG_CT_KEY, NETFILTER_HOOKS_LOG_CT,
						NETFILTER_HOOKS_USER_KEY, NETFILTER_HOOKS_USER, CAPTURE_IPC_KEY, CAPTURE_IPC, REPORT_IPC_KEY,
						REPORT_IPC});
		logger.log(Level.INFO, globals.toString());
		return true;
	}
	
	private void doCleanup(final boolean forceStopSPADEAuditBridge){
		synchronized(cleanupLock){
			if(input != null && processUserSyscallFilter != null){
				if(input.isLiveMode()){
					try{
						AuditControlManager.unset(processUserSyscallFilter.getSystemCallRuleType());
					}catch(Exception e){
						logger.log(Level.WARNING, "Failed to do Linux audit rules cleanup", e);
					}
				}else if(input.getMode() == Input.Mode.FILE){
					try{
						input.deleteInputLogListFile();
					}catch(Exception e){
						logger.log(Level.WARNING, "Failed to delete the temporary input log list file: '" + input.getInputLogListFile() + "'", e);
					}
				}
			}
			
			if(kernelModuleConfiguration != null && kernelModuleConfiguration.isLocalEndpoints()){
				try{
					KernelModuleManager.disableModule(kernelModuleConfiguration.getKernelModuleControllerPath(),
							kernelModuleConfiguration.isHarden(),
							kernelModuleConfiguration.getKernelModuleDeleteBinaryPath(), new Consumer<String>(){
								public void accept(String str){
									logger.log(Level.INFO, str);
								}
							}, new BiConsumer<String, Throwable>(){
								public void accept(String str, Throwable t){
									logger.log(Level.WARNING, str, t);
								}
							});
					logger.log(Level.INFO, "Successfully disabled kernel modules");
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to disable kernel modules", e);
				}
			}
			
			if(spadeAuditBridgeProcess != null){
				try{
					spadeAuditBridgeProcess.stop(forceStopSPADEAuditBridge);
				}catch(Exception e){
					logger.log(Level.WARNING, "Failed to SPADE audit bridge process cleanup", e);
				}
			}
			if(auditEventReader != null){
				try{
					auditEventReader.close();
				}catch(Exception e){
					logger.log(Level.WARNING, "Failed to gracefully close audit event reader", e);
				}
			}
			if(artifactManager != null){
				artifactManager.doCleanUp();
			}
			if(processManager != null){
				processManager.doCleanUp();
			}
			if(netfilterHooksManager != null){
				netfilterHooksManager.shutdown();
			}	
		}
	}

	@Override
	public boolean launch(final String arguments){
		boolean success = false;
		Exception exception = null;

		try{
			success = _launch(arguments);
			exception = null;
		}catch(Exception e){
			success = false;
			exception = e;
		}

		if(success){
			logger.log(Level.INFO, "Successfully launched reporter");
		}else{
			logger.log(Level.SEVERE, "Failed to launch reporter", exception);
			final boolean forceStopSPADEAuditBridge = true;
			doCleanup(forceStopSPADEAuditBridge);
		}

		return success;
	}

	private boolean _launch(final String arguments){
		final Map<String, String> map = new HashMap<String, String>();
		try{
			final String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());
			map.putAll(HelperFunctions.parseKeyValuePairsFrom(arguments, new String[]{defaultConfigFilePath}));
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to parse arguments and/or default config file", e);
			return false;
		}
		
		try{
			final String keyReportingIntervalSeconds = "reportingIntervalSeconds";
			this.reportingIntervaler = Intervaler.instance(map, keyReportingIntervalSeconds);
			logger.log(Level.INFO, this.reportingIntervaler.toString());
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to setup reporting", e);
			return false;
		}
		
		try{
			this.outputLog = OutputLog.instance(map);
			logger.log(Level.INFO, this.outputLog.toString());
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to setup output log", e);
			return false;
		}
		
		try{
			final String keyConstantsSource = "constantsSource";
			this.platformConstants = LinuxConstants.instance(map, keyConstantsSource);
			logger.log(Level.INFO, this.platformConstants.toString());
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to setup platform constants", e);
			return false;
		}
		
		try{
			input = Input.instance(map);
			logger.log(Level.INFO, input.toString());
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize input configuration", e);
			return false;
		}
		
		try{
			processUserSyscallFilter = ProcessUserSyscallFilter.instance(map, input.getSPADEAuditBridgeName(), input.isLiveMode());
			logger.log(Level.INFO, processUserSyscallFilter.toString());
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize process, user, and system call filter configuration", e);
			return false;
		}
		
		try{
			kernelModuleConfiguration = KernelModuleConfiguration.instance(map, input.isLiveMode());
			logger.log(Level.INFO, kernelModuleConfiguration.toString());
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize kernel module configuration", e);
			return false;
		}

		// Init boolean flags from the arguments
		if(!initFlagsFromArguments(map)){
			return false;
		}

		try{
			artifactManager = new ArtifactManager(this, globals);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to instantiate artifact manager", e);
			return false;
		}
		
		try{
			if(AGENTS){ // Make sure that this is done before starting the event reader thread
				processManager = new ProcessWithoutAgentManager(this, SIMPLIFY, CREATE_BEEP_UNITS, HANDLE_NAMESPACES, platformConstants);
			}else{
				processManager = new ProcessWithAgentManager(this, SIMPLIFY, CREATE_BEEP_UNITS, HANDLE_NAMESPACES, platformConstants);
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to instantiate process manager", e);
			return false;
		}
		
		if(HANDLE_NETFILTER_HOOKS){
			try{
				netfilterHooksManager = new NetfilterHooksManager(this, HANDLE_NAMESPACES);
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to instantiate netfilter hooks manager", e);
				return false;
			}
		}
		
		try{
			this.spadeAuditBridgeProcess = SPADEAuditBridgeProcess.launch(this.input, CREATE_BEEP_UNITS, mergeUnit);
			logger.log(Level.INFO, "Launched SPADE audit bridge process with pid '" + spadeAuditBridgeProcess.getPid() + "' using command:"
					+ " " + spadeAuditBridgeProcess.getCommand());
			this.spadeAuditBridgeProcess.consumeStdErr(new BiConsumer<String, Exception>(){
				@Override
				public void accept(final String msg, final Exception exception){
					if(msg != null && exception != null){
						logger.log(Level.SEVERE, "[SPADE audit bridge] [ERROR] " + msg, exception);
					}else if(msg != null && exception == null){
						logger.log(Level.INFO, "[SPADE audit bridge] [OUTPUT] " + msg);
					}else if(msg == null && exception != null){
						logger.log(Level.SEVERE, "[SPADE audit bridge] [ERROR] " + "Unexpected error", exception);
					}else{
						logger.log(Level.INFO, "[SPADE audit bridge] [OUTPUT] " + "Exiting error thread");
					}
				}
			});
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to start SPADE audit bridge process", e);
			return false;
		}
		
		try{
			this.auditEventReader = new AuditEventReader(input.getSPADEAuditBridgeName(), spadeAuditBridgeProcess.getStdOutStream());
			if(this.outputLog.isEnabled()){
				this.auditEventReader.setOutputLog(this.outputLog.getOutputLogPath(), this.outputLog.getRotateLogAfterLines());
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to instantiate audit event reader", e);
			return false;
		}
		
		try{
			this.mainEventLoopThread.start();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to instantiate/start main event reader thread", e);
			return false;
		}
		
		if(input.isLiveMode()){
			if(kernelModuleConfiguration.isLocalEndpoints()
					|| processUserSyscallFilter.getSystemCallRuleType() == SystemCallRuleType.ALL
					|| processUserSyscallFilter.getSystemCallRuleType() == SystemCallRuleType.DEFAULT){
				final Set<String> pidsToIgnore = new HashSet<String>();
				final Set<String> ppidsToIgnore = new HashSet<String>();
				
				try{
					pidsToIgnore.addAll(processUserSyscallFilter.getPidsOfProcessesToIgnore());
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to get pids of processes to ignore", e);
					return false;
				}
				
				try{
					ppidsToIgnore.addAll(processUserSyscallFilter.getPpidsOfProcessesToIgnore());
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to get ppids of processes to ignore", e);
					return false;
				}
				
				if(kernelModuleConfiguration.isLocalEndpoints()){
					final Set<String> tgidsToHarden = new HashSet<String>();
					if(kernelModuleConfiguration.isHarden()){
						try{
							tgidsToHarden.addAll(processUserSyscallFilter.getTgidsOfProcessesToHarden(kernelModuleConfiguration.getHardenProcesses()));
						}catch(Exception e){
							logger.log(Level.SEVERE, "Failed to get tgids of processes to harden", e);
							return false;
						}
					}
					
					try{
						KernelModuleManager.insertModules(
								kernelModuleConfiguration.getKernelModuleMainPath(), 
								kernelModuleConfiguration.getKernelModuleControllerPath(),
								processUserSyscallFilter.getUserId(), processUserSyscallFilter.getUserMode(),
								processUserSyscallFilter.getPidsOfProcessesToIgnore(), processUserSyscallFilter.getPpidsOfProcessesToIgnore(),
								USE_SOCK_SEND_RCV,
								HANDLE_NAMESPACES,
								NETFILTER_HOOKS, NETFILTER_HOOKS_LOG_CT, NETFILTER_HOOKS_USER,
								kernelModuleConfiguration.isHarden(), tgidsToHarden,
								new Consumer<String>(){
									public void accept(final String str){
										logger.log(Level.INFO, str);
									}
								});
					}catch(Exception e){
						logger.log(Level.SEVERE, "Failed to setup kernel modules", e);
						return false;
					}
				}
				
				try{
					AuditControlManager.set(processUserSyscallFilter.getSystemCallRuleType(),
							processUserSyscallFilter.getUserId(), processUserSyscallFilter.getUserMode(),
							processUserSyscallFilter.getPidsOfProcessesToIgnore(), processUserSyscallFilter.getPpidsOfProcessesToIgnore(),
							excludeProctitle, 
							kernelModuleConfiguration.isLocalEndpoints(), 
							USE_SOCK_SEND_RCV, USE_READ_WRITE, 
							USE_MEMORY_SYSCALLS, filesystemCredentialUpdates, 
							HANDLE_CHDIR, HANDLE_ROOTFS, 
							HANDLE_NAMESPACES, CAPTURE_IPC, 
							new Consumer<String>(){
								public void accept(String str){
									logger.log(Level.INFO, str);
								}
							}, new BiConsumer<String, Throwable>(){
								public void accept(String str, Throwable t){
									logger.log(Level.WARNING, str, t);
								}
							});
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to setup Linux audit rules", e);
					return false;
				}
			}
		}
		return true;
	}

	public final ProcessManager getProcessManager(){
		return processManager;
	}
	
	public final ArtifactManager getArtifactManager(){
		return artifactManager;
	}
	
	public final IPCManager getIPCManager(){
		return ipcManager;
	}

	@Override
	public boolean shutdown(){
		try{
			return _shutdown();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to successfully shutdown the Audit reporter", e);
			return false;
		}
	}
	/*
	 * a. Kernel modules. Will stop events stream. (external)
	 * b. Audit rules. Will stop Linux Audit. (external)
	 * c. Input log list file. In use by spade audit bridge.
	 * d. spade audit bridge process. 
	 * e. main event loop thread
	 * f. audit event reader
	 * g. artifact/process/netfilter manager
	 */
	private boolean _shutdown(){
		// Remove the kernel module first because we need to kill spadeAuditBridge
		// because it might be hardened
		if(kernelModuleConfiguration != null && kernelModuleConfiguration.isLocalEndpoints()){
			try{
				KernelModuleManager.disableModule(kernelModuleConfiguration.getKernelModuleControllerPath(),
						kernelModuleConfiguration.isHarden(),
						kernelModuleConfiguration.getKernelModuleDeleteBinaryPath(), new Consumer<String>(){
							public void accept(String str){
								logger.log(Level.INFO, str);
							}
						}, new BiConsumer<String, Throwable>(){
							public void accept(String str, Throwable t){
								logger.log(Level.WARNING, str, t);
							}
						});
				logger.log(Level.INFO, "Successfully disabled kernel modules");
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to disable kernel modules", e);
			}
		}

		// Send an interrupt to the spadeAuditBridgeProcess
		if(spadeAuditBridgeProcess != null){
			try{
				final boolean forceStop = false;
				spadeAuditBridgeProcess.stop(forceStop);
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to stop SPADE audit bridge process", e);
			}
		}

		// Return. The event reader thread and the error reader thread will exit on
		// their own.
		// The event reader thread will do the state cleanup

		logger.log(Level.INFO, "Going to wait for main event loop thread to finish");

		while(isMainEventLoopThreadRunning){
			// Wait while the event reader thread is still running i.e. buffer being emptied
			HelperFunctions.sleepSafe(1000);
		}

		// force print stats before exiting
		printStats(true);

		return true;
	}

	private void printStats(boolean forcePrint){
		if(reportingIntervaler.check() || forcePrint){
			if(HANDLE_NETFILTER_HOOKS && netfilterHooksManager != null){
				netfilterHooksManager.printStats();
			}
		}
	}

	private void setHandleKMRecordsFlag(boolean isLiveAudit, boolean valueOfHandleKMRecords){
		// Only set the value if it hasn't been set and is log playback
		if(!kernelModuleConfiguration.isHandleLocalEndpointsSpecified() && !isLiveAudit){
			kernelModuleConfiguration.setHandleLocalEndpoints(valueOfHandleKMRecords);
			logger.log(Level.INFO, "'" + KernelModuleConfiguration.keyHandleLocalEndpoints + "' value set to '"+valueOfHandleKMRecords+"'");
		}
	}

	private void finishEvent(Map<String, String> eventData){

		printStats(false);

		if (eventData == null) {
			logger.log(Level.WARNING, "Null event data read");
			return;
		}

		try{
			String recordType = eventData.get(AuditEventReader.RECORD_TYPE_KEY);
			if(AuditEventReader.RECORD_TYPE_UBSI_ENTRY.equals(recordType)){
				processManager.handleUnitEntry(eventData);
			}else if(AuditEventReader.RECORD_TYPE_UBSI_EXIT.equals(recordType)){
				processManager.handleUnitExit(eventData);
			}else if(AuditEventReader.RECORD_TYPE_UBSI_DEP.equals(recordType)){
				processManager.handleUnitDependency(eventData);
			}else if(AuditEventReader.RECORD_TYPE_DAEMON_START.equals(recordType)){
				//processManager.daemonStart(); TODO Not being used until figured out how to handle it.
			}else if(AuditEventReader.KMODULE_RECORD_TYPE.equals(recordType)){
				setHandleKMRecordsFlag(input.isLiveMode(), true); // Always do first because HANDLE_KM_RECORDS can be null when playback
				if(kernelModuleConfiguration.isHandleLocalEndpoints()){
					handleKernelModuleEvent(eventData);
				}
			}else if(AuditEventReader.RECORD_TYPE_NETFILTER_HOOK.equals(recordType)){
				if(HANDLE_NETFILTER_HOOKS){
					netfilterHooksManager.handleNetfilterHookEvent(eventData);
				}
			}else{
				handleSyscallEvent(eventData);
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to process eventData: " + eventData, e);
		}
	}
	
	private void handleKernelModuleEvent(Map<String, String> eventData){
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		SYSCALL syscall = null;
		try{
			String pid = eventData.get(AuditEventReader.PID);
			Integer syscallNumber = HelperFunctions.parseInt(eventData.get(AuditEventReader.SYSCALL), null);
			String exit = eventData.get(AuditEventReader.EXIT);
			int success = HelperFunctions.parseInt(eventData.get(AuditEventReader.SUCCESS), -1);
			String sockFd = eventData.get(AuditEventReader.KMODULE_FD);
			int sockType = Integer.parseInt(eventData.get(AuditEventReader.KMODULE_SOCKTYPE));
			String localSaddr = eventData.get(AuditEventReader.KMODULE_LOCAL_SADDR);
			String remoteSaddr = eventData.get(AuditEventReader.KMODULE_REMOTE_SADDR);
			
			if(success == 1){
				syscall = getSyscall(syscallNumber);
				if(syscall == null || syscall == SYSCALL.UNSUPPORTED){
					log(Level.WARNING, "Invalid syscall: " + syscallNumber, null, time, eventId, null);
				}else{
					switch (syscall) {
						case BIND:
							handleBindKernelModule(eventData, time, eventId, syscall, pid,
									exit, sockFd, sockType, localSaddr, remoteSaddr);
							break;
						case ACCEPT:
						case ACCEPT4:
							handleAcceptKernelModule(eventData, time, eventId, syscall, pid, 
									exit, sockFd, sockType, localSaddr, remoteSaddr);
							break;
						case CONNECT:
							handleConnectKernelModule(eventData, time, eventId, syscall, pid,
									exit, sockFd, sockType, localSaddr, remoteSaddr);
							break;
						case SENDMSG:
						case SENDTO:
//						case SENDMMSG: // TODO
							handleNetworkIOKernelModule(eventData, time, eventId, syscall, pid, exit, sockFd, 
									sockType, localSaddr, remoteSaddr, false);
							break;
						case RECVMSG:
						case RECVFROM:
//						case RECVMMSG:
							handleNetworkIOKernelModule(eventData, time, eventId, syscall, pid, exit, sockFd, 
									sockType, localSaddr, remoteSaddr, true);
							break;
						default:
							log(Level.WARNING, "Unexpected syscall: " + syscallNumber, null, time, eventId, syscall);
							break;
					}
				}
			}
		}catch(Exception e){
			log(Level.WARNING, "Failed to parse kernel module event", null, time, eventId, syscall);
		}
	}
	
	private SYSCALL getSyscall(int syscallNumber){
		return SYSCALL.get64BitSyscall(syscallNumber);
	}
	
	/**
	 * Converts syscall args: 'a0', 'a1', 'a2', and 'a3' from hexadecimal values to decimal values
	 * 
	 * Conversion done based on the length of the hex value string. If length <= 8 then integer else a long.
	 * If length > 16 then truncated to long.
	 * 
	 * Done so to avoid the issue of incorrectly fitting a small negative (i.e. int) value into a big (i.e. long) value
	 * causing a wrong interpretation of bits.
	 * 
	 * @param eventData map that contains the above-mentioned args keys and values
	 * @param time time of the event
	 * @param eventId id of the event
	 * @param syscall syscall of the event
	 */
	private void convertArgsHexToDec(Map<String, String> eventData, String time, String eventId, SYSCALL syscall){
		String[] argKeys = {AuditEventReader.ARG0, AuditEventReader.ARG1, AuditEventReader.ARG2, AuditEventReader.ARG3};
		for(String argKey : argKeys){
			String hexArgValue = eventData.get(argKey);
			if(hexArgValue != null){
				int hexArgValueLength = hexArgValue.length();
				try{
					BigInteger bigInt = new BigInteger(hexArgValue, 16);
					String argValueString = null;
					if(hexArgValueLength <= 8){
						int argInt = bigInt.intValue();
						argValueString = Integer.toString(argInt);
					}else{ // greater than 8
						if(hexArgValueLength > 16){
							log(Level.SEVERE, "Truncated value for '" + argKey + "': '"+hexArgValue+"'. Too big for 'long' datatype", null, time, eventId, syscall);
						}
						long argLong = bigInt.longValue();
						argValueString = Long.toString(argLong);
					}
					eventData.put(argKey, argValueString);
				}catch(Exception e){
					log(Level.SEVERE, "Non-numerical value for '" + argKey + "': '"+hexArgValue+"'", e, time, eventId, syscall);
				}
			}else{
				log(Level.SEVERE, "NULL value for '" + argKey + "'", null, time, eventId, syscall);
			}
		}
	}
	
	/**
	 * Gets the key value map from the internal data structure and gets the system call from the map.
	 * Gets the appropriate system call based on current architecture
	 * If global flag to log only successful events is set to true but the current event wasn't successful then only handle it if was either a kill
	 * system call or exit system call or exit_group system call.
	 * 
	 * IMPORTANT: Converts all 4 arguments, a0 o a3 to decimal integers from hexadecimal integers and puts them back in the key value map
	 * 
	 * Calls the appropriate system call handler based on the system call
	 * 
	 * @param eventId id of the event against which the key value maps are saved
	 */
	private void handleSyscallEvent(Map<String, String> eventData) {
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		try {
			processManager.processSeenInUnsupportedSyscall(eventData); // Always set first because that is what is done in spadeAuditBridge and it is updated if syscall handled.
			
			int syscallNum = HelperFunctions.parseInt(eventData.get(AuditEventReader.SYSCALL), -1);
			
			if(syscallNum == -1){
				return;
			}
			
			SYSCALL syscall = getSyscall(syscallNum);
			
			if(syscall == null){
				log(Level.WARNING, "Invalid syscall: " + syscallNum, null, time, eventId, null);
				return;
			}else if(syscall == SYSCALL.UNSUPPORTED){
				return;
			}

			if("1".equals(AUDITCTL_SYSCALL_SUCCESS_FLAG) 
					&& AuditEventReader.SUCCESS_NO.equals(eventData.get(AuditEventReader.SUCCESS))){
				//if only log successful events but the current event had success no then only monitor the following calls.
				if(syscall == SYSCALL.EXIT || syscall == SYSCALL.EXIT_GROUP
						|| syscall == SYSCALL.CONNECT){
					//continue and log these syscalls irrespective of success
					// Syscall connect can fail with EINPROGRESS flag which we want to 
					// mark as successful even though we don't know yet
				}else{ //for all others don't log
					return;
				}
			}

			//convert all arguments from hexadecimal format to decimal format and replace them. done for convenience here and to avoid issues. 
			convertArgsHexToDec(eventData, time, eventId, syscall);
			
			// Check if one of the network related syscalls. Must do this check before because HANDLE_KM_RECORDS can be null
			switch (syscall) {
				case SENDMSG:
				case SENDTO:
				case RECVFROM: 
				case RECVMSG:
				case SOCKET:
				case BIND:
				case ACCEPT:
				case ACCEPT4:
				case CONNECT:
					setHandleKMRecordsFlag(input.isLiveMode(), false);
					break;
				default:
					break;
			}

			switch (syscall) {
			case MQ_OPEN: if(REPORT_IPC){ ipcManager.handleMq_open(eventData, syscall); } break;
			case MQ_TIMEDSEND: if(REPORT_IPC){ ipcManager.handleMq_timedsend(eventData, syscall); } break;
			case MQ_TIMEDRECEIVE: if(REPORT_IPC){ ipcManager.handleMq_timedreceive(eventData, syscall); } break;
			case MQ_UNLINK: if(REPORT_IPC){ ipcManager.handleMq_unlink(eventData, syscall); } break;
			case SHMGET: if(REPORT_IPC){ ipcManager.handleShmget(eventData, syscall); } break;
			case SHMAT: if(REPORT_IPC){ ipcManager.handleShmat(eventData, syscall); } break;
			case SHMDT: if(REPORT_IPC){ ipcManager.handleShmdt(eventData, syscall); } break;
			case SHMCTL: if(REPORT_IPC){ ipcManager.handleShmctl(eventData, syscall); } break;
			case MSGGET: if(REPORT_IPC){ ipcManager.handleMsgget(eventData, syscall); } break;
			case MSGSND: if(REPORT_IPC){ ipcManager.handleMsgsnd(eventData, syscall); } break;
			case MSGRCV: if(REPORT_IPC){ ipcManager.handleMsgrcv(eventData, syscall); } break;
			case MSGCTL: if(REPORT_IPC){ ipcManager.handleMsgctl(eventData, syscall); } break;
			case SETNS:
				if(HANDLE_NAMESPACES){
					processManager.handleSetns(eventData, syscall);
				}
				break;
			case UNSHARE:
				if(HANDLE_NAMESPACES){
					processManager.handleUnshare(eventData, syscall);
				}
				break;
			case PIVOT_ROOT:
				if(HANDLE_ROOTFS){
					handlePivotRoot(eventData, syscall);
				}
				break;
			case CHROOT:
				if(HANDLE_ROOTFS){
					handleChroot(eventData, syscall);
				}
				break;
			case CHDIR:
			case FCHDIR:
				if(HANDLE_CHDIR){
					handleChdir(eventData, syscall);
				}
			break;
			case LSEEK:
				if(USE_READ_WRITE){
					handleLseek(eventData, syscall);
				}
			break;
			case MADVISE:
				if(USE_MEMORY_SYSCALLS){
					handleMadvise(eventData, syscall);
				}
			break;
			case KILL:
				if(REPORT_KILL){
					handleKill(eventData, syscall);
				}
				break;
			case PTRACE:
				handlePtrace(eventData, syscall);
				break;
			case SOCKETPAIR:
				handleSocketPair(eventData, syscall);
				break;
			case TEE:
			case SPLICE:
				handleTeeSplice(eventData, syscall);
				break;
			case VMSPLICE:
				handleVmsplice(eventData, syscall);
				break;
			case INIT_MODULE:
			case FINIT_MODULE:
				handleInitModule(eventData, syscall);
				break;
			case FCNTL:
				handleFcntl(eventData, syscall);
				break;
			case EXIT:
			case EXIT_GROUP:
				handleExit(eventData, syscall);
				break;
			case WRITE: 
			case WRITEV:
			case PWRITE:
			case PWRITEV:
				handleIOEvent(syscall, eventData, false, eventData.get(AuditEventReader.EXIT));
				break;
			case SENDMSG:
			case SENDTO:
				if(!kernelModuleConfiguration.isHandleLocalEndpoints()){
					handleIOEvent(syscall, eventData, false, eventData.get(AuditEventReader.EXIT));
				}
				break;
			case RECVFROM: 
			case RECVMSG:
				if(!kernelModuleConfiguration.isHandleLocalEndpoints()){
					handleIOEvent(syscall, eventData, true, eventData.get(AuditEventReader.EXIT));
				}
				break;
			case READ: 
			case READV:
			case PREAD:
			case PREADV:
				handleIOEvent(syscall, eventData, true, eventData.get(AuditEventReader.EXIT));
				break;
			case MMAP:
				handleMmap(eventData, syscall);
				break;
			case MPROTECT:
				handleMprotect(eventData, syscall);
				break;
			case SYMLINK:
			case LINK:
			case SYMLINKAT:
			case LINKAT:
				handleLinkSymlink(eventData, syscall);
				break;
			case UNLINK:
			case UNLINKAT:
				handleUnlink(eventData, syscall);
				break;
			case VFORK:
			case FORK:
			case CLONE:
				processManager.handleForkVforkClone(eventData, syscall);
				break;
			case EXECVE:
				handleExecve(eventData, syscall);
				break;
			case OPEN:
			case OPENAT:
			case CREAT:
				handleOpen(eventData, syscall);
				break;
			case CLOSE:
				handleClose(eventData);
				break;
			case MKNOD:
			case MKNODAT:
				handleMknod(eventData, syscall);
				break;
			case DUP:
			case DUP2:
			case DUP3:
				handleDup(eventData, syscall);
				break;
			case SOCKET:
				if(!kernelModuleConfiguration.isHandleLocalEndpoints()){
					handleSocket(eventData, syscall);
				}
				break;
			case BIND:
				if(!kernelModuleConfiguration.isHandleLocalEndpoints()){
					handleBind(eventData, syscall);
				}
				break;
			case ACCEPT4:
			case ACCEPT:
				if(!kernelModuleConfiguration.isHandleLocalEndpoints()){
					handleAccept(eventData, syscall);
				}
				break;
			case CONNECT:
				if(!kernelModuleConfiguration.isHandleLocalEndpoints()){
					handleConnect(eventData, syscall);
				}
				break;
			case RENAME:
			case RENAMEAT:
				handleRename(eventData, syscall);
				break;
			case SETUID:
			case SETREUID:
			case SETRESUID:
			case SETFSUID:
			case SETGID:
			case SETREGID:
			case SETRESGID:
			case SETFSGID:
				handleSetuidAndSetgid(eventData, syscall);
				break; 
			case CHMOD:
			case FCHMOD:
			case FCHMODAT:
				handleChmod(eventData, syscall);
				break;
			case PIPE:
			case PIPE2:
				handlePipe(eventData, syscall);
				break;
			case TRUNCATE:
			case FTRUNCATE:
				handleTruncate(eventData, syscall);
				break;
			default: //SYSCALL.UNSUPPORTED
				//log(Level.INFO, "Unsupported syscall '"+syscallNum+"'", null, eventData.get("time"), eventId, syscall);
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Error processing finish syscall event with eventid '"+eventId+"'", e);
		}
	}

	public final void handleIOEvent(SYSCALL syscall, Map<String, String> eventData, boolean isRead, final String bytesTransferred){
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);
		String saddr = eventData.get(AuditEventReader.SADDR);
		String fd = eventData.get(AuditEventReader.ARG0);
		String offset = null;
		ArtifactIdentifier artifactIdentifier = null;	
		
		if(!isNetlinkSaddr(saddr)){
			artifactIdentifier = getNetworkIdentifierFromFdAndOrSaddr(syscall, time, eventId, pid, fd, saddr);
			if(syscall == SYSCALL.PREAD || syscall == SYSCALL.PREADV
					|| syscall == SYSCALL.PWRITE || syscall == SYSCALL.PWRITEV){
				offset = eventData.get(AuditEventReader.ARG3);
			}
			
			putIO(eventData, time, eventId, syscall, pid, fd, artifactIdentifier, bytesTransferred, offset, isRead);
		}
	}
	
	private void handleMadvise(Map<String, String> eventData, SYSCALL syscall){
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);
		String address = new BigInteger(eventData.get(AuditEventReader.ARG0)).toString(16);
		String length = new BigInteger(eventData.get(AuditEventReader.ARG1)).toString(16);
		String adviceString = eventData.get(AuditEventReader.ARG2);

		Integer adviceInt = HelperFunctions.parseInt(adviceString, null);
		if(adviceInt == null){
			log(Level.WARNING, "Expected 3rd argument (a2) to be integer but is '"+adviceString+"'", 
					null, time, eventId, syscall);
		}else{
			String adviceAnnotation = platformConstants.getMadviseAdviceName(adviceInt);
			if(adviceAnnotation == null){
				log(Level.WARNING, 
						"Expected 3rd argument (a2), which is '"+adviceString+"', to be one of: "
						+ platformConstants.getMadviseAllowedAdviceNames(), 
						null, time, eventId, syscall);
			}else{
				String tgid = processManager.getMemoryTgid(pid);
				
				ArtifactIdentifier memoryIdentifier = new MemoryIdentifier(tgid, address, length);
				Artifact memoryArtifact = putArtifactFromSyscall(eventData, memoryIdentifier);

				Process process = processManager.handleProcessFromSyscall(eventData);
				WasGeneratedBy edge = new WasGeneratedBy(memoryArtifact, process);
				edge.addAnnotation(OPMConstants.EDGE_ADVICE, adviceAnnotation);
				putEdge(edge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
			}
		}
	}
	
	private void handleLseek(Map<String, String> eventData, SYSCALL syscall){
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);
		String fd = eventData.get(AuditEventReader.ARG0);
		String offsetRequested = eventData.get(AuditEventReader.ARG1);
		String whenceString = eventData.get(AuditEventReader.ARG2);
		String offsetActual = eventData.get(AuditEventReader.EXIT);
		
		Integer whence = HelperFunctions.parseInt(whenceString, null);
		if(whence == null){
			log(Level.WARNING, "Expected 3rd argument (a2) to be integer but is '"+whenceString+"'", 
					null, time, eventId, syscall);
		}else{
			String whenceAnnotation = platformConstants.getLseekWhenceName(whence);
			if(whenceAnnotation == null){
				log(Level.WARNING, 
						"Expected 3rd argument (a2), which is '"+whenceString+"', to be one of: "
						+ platformConstants.getLseekAllowedWhenceNames(), 
						null, time, eventId, syscall);
			}else{
				FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
				if(fileDescriptor == null){
					fileDescriptor = addUnknownFd(pid, fd);
				}
				Process process = processManager.handleProcessFromSyscall(eventData);
				Artifact artifact = putArtifactFromSyscall(eventData, fileDescriptor.identifier);
				WasGeneratedBy wgb = new WasGeneratedBy(artifact, process);
				wgb.addAnnotation(OPMConstants.EDGE_OFFSET, offsetActual);
				wgb.addAnnotation(OPMConstants.EDGE_LSEEK_WHENCE, whenceAnnotation);
				putEdge(wgb, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
			}
		}
	}
	
	private void handlePivotRoot(Map<String, String> eventData, SYSCALL syscall){
		// pivot_root() receives the following messages(s):
		// - SYSCALL
		// - PATH with NORMAL nametype
		// - CWD - different in different cases. Not handling those for now.
		// - EOE
		
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String processCwd = processManager.getCwd(pid); // never null
		
		final PathRecord pathRecord = PathRecord.getFirstPathWithNametype(eventData, 
				AuditEventReader.NAMETYPE_NORMAL);
		
		if(pathRecord == null){
			log(Level.WARNING, "Missing PATH record", null, time, eventId, syscall);
		}else{
			String path = pathRecord.getPath();
			String newRoot = LinuxPathResolver.constructAbsolutePath(path, processCwd, pid);
			newRoot = LinuxPathResolver.joinPaths(newRoot, processManager.getRoot(pid), pid);
			if(newRoot == null){
				log(Level.WARNING, "Failed to construct path", null, time, eventId, syscall);
			}else{
				processManager.pivot_root(pid, newRoot, null);
			}
		}
	}
	
	private void handleChroot(Map<String, String> eventData, SYSCALL syscall){
		// chroot() receives the following messages(s):
		// - SYSCALL
		// - PATH with NORMAL nametype
		// - CWD - different in different cases. Not handling those for now.
		// - EOE
		
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String processCwd = processManager.getCwd(pid); // never null
		
		final PathRecord pathRecord = PathRecord.getFirstPathWithNametype(eventData, 
				AuditEventReader.NAMETYPE_NORMAL);
		
		if(pathRecord == null){
			log(Level.WARNING, "Missing PATH record", null, time, eventId, syscall);
		}else{
			String path = pathRecord.getPath();
			String newRoot = LinuxPathResolver.constructAbsolutePath(path, processCwd, pid);
			newRoot = LinuxPathResolver.joinPaths(newRoot, processManager.getRoot(pid), pid);
			if(newRoot == null){
				log(Level.WARNING, "Failed to construct path", null, time, eventId, syscall);
			}else{
				processManager.chroot(pid, newRoot);
			}
		}
	}
	
	private void handleChdir(Map<String, String> eventData, SYSCALL syscall){
		// chdir() receives the following messages(s):
		// - SYSCALL
		// - PATH with NORMAL nametype
		// - CWD (pre-syscall value) (i.e. not taken at exit of syscall)
		// - EOE
		
		// fchdir() receives the following messages(s):
		// - SYSCALL
		// - EOE
		
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		
		if(syscall == SYSCALL.CHDIR){
			PathRecord normalPathRecord = PathRecord.getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			if(normalPathRecord == null){
				log(Level.WARNING, "Missing PATH record", null, time, eventId, syscall);
			}else{
				String path = normalPathRecord.getPath();
				if(LinuxPathResolver.isAbsolutePath(path)){
					// update the cwd to this
					// update the cwd root to the current process root
					processManager.absoluteChdir(pid, path);
				}else{
					// relative path
					// Need to resolve according to CWD
					String currentProcessCwd = processManager.getCwd(pid);
					if(currentProcessCwd == null){
						String auditRecordCwd = eventData.get(AuditEventReader.CWD);
						if(auditRecordCwd == null){
							log(Level.WARNING, "Missing CWD record as well as process state", null, time, eventId, syscall);
						}else{
							String finalPath = LinuxPathResolver.constructAbsolutePath(path, auditRecordCwd, pid);
							// update the cwd and not the cwd root
							processManager.relativeChdir(pid, finalPath);
						}
					}else{
						String finalPath = LinuxPathResolver.constructAbsolutePath(path, currentProcessCwd, pid);
						// update cwd and not the cwd root
						processManager.relativeChdir(pid, finalPath);
					}
				}
			}
		}else if(syscall == SYSCALL.FCHDIR){
			String fd = eventData.get(AuditEventReader.ARG0);
			FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
			if(fileDescriptor != null){
				if(fileDescriptor.identifier instanceof DirectoryIdentifier){
					DirectoryIdentifier directoryIdentifier = (DirectoryIdentifier)fileDescriptor.identifier;
					String newCwdPath = directoryIdentifier.path;
					String newCwdRoot = directoryIdentifier.rootFSPath;
					// root path from dir
					processManager.fdChdir(pid, newCwdPath, newCwdRoot);
				}else{
					log(Level.WARNING, "Unexpected FD type to change currrent working directory to. Expected directory. Found: "
							+ fileDescriptor.identifier.getClass(), null, time, eventId, syscall);
				}
			}else{
				log(Level.WARNING, "Missing FD to change current working directory to", null, time, eventId, syscall);
			}	
		}else{
			log(Level.INFO, "Unexpected syscall '"+syscall+"' in CHDIR handler", null, time, eventId, syscall);
		}
	}

	private PathIdentifier resolvePath_At(PathRecord pathRecord,  
			String atSyscallFdKey,
			Map<String, String> eventData, SYSCALL syscall){
		return LinuxPathResolver.resolvePath(
				pathRecord, eventData.get(AuditEventReader.CWD), eventData.get(AuditEventReader.PID), 
				atSyscallFdKey, eventData.get(atSyscallFdKey), true,
				eventData.get(AuditEventReader.TIME), eventData.get(AuditEventReader.EVENT_ID), syscall, 
				this, processManager, artifactManager, HANDLE_CHDIR);
	}
	
	public final PathIdentifier resolvePath(PathRecord pathRecord,
			Map<String, String> eventData, SYSCALL syscall){
		return LinuxPathResolver.resolvePath(
				pathRecord, eventData.get(AuditEventReader.CWD), eventData.get(AuditEventReader.PID), 
				null, null, false,
				eventData.get(AuditEventReader.TIME), eventData.get(AuditEventReader.EVENT_ID), syscall, 
				this, processManager, artifactManager, HANDLE_CHDIR);
	}

	public final void handleUnlink(Map<String, String> eventData, SYSCALL syscall){
		// unlink() and unlinkat() receive the following messages(s):
		// - SYSCALL
		// - PATH with PARENT nametype
		// - PATH with DELETE nametype relative to CWD
		// - CWD
		// - EOE

		if(CONTROL){
			String time = eventData.get(AuditEventReader.TIME);
			String eventId = eventData.get(AuditEventReader.EVENT_ID);
			
			String pathAuditNametype = AuditEventReader.NAMETYPE_DELETE;
			PathRecord pathRecord = PathRecord.getFirstPathWithNametype(eventData, pathAuditNametype);
			
			PathIdentifier pathIdentifier = null;
			if(syscall == SYSCALL.UNLINK || syscall == SYSCALL.MQ_UNLINK){
				pathIdentifier = resolvePath(pathRecord, eventData, syscall);
				if(syscall == SYSCALL.MQ_UNLINK){
					pathIdentifier = new PosixMessageQueue(pathIdentifier.path, pathIdentifier.rootFSPath);
				}
			}else if(syscall == SYSCALL.UNLINKAT){
				pathIdentifier = resolvePath_At(pathRecord, AuditEventReader.ARG0, eventData, syscall);
			}else{
				log(Level.INFO, "Unexpected syscall '"+syscall+"' in UNLINK handler", null, time, eventId, syscall);
			}

			if(pathIdentifier != null){
				artifactManager.artifactPermissioned(pathIdentifier, pathRecord.getPermissions());
				
				Process process = processManager.handleProcessFromSyscall(eventData);
				Artifact artifact = putArtifactFromSyscall(eventData, pathIdentifier);
				WasGeneratedBy deletedEdge = new WasGeneratedBy(artifact, process);
				putEdge(deletedEdge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
			}
		}
	}

	private FileDescriptor addUnknownFd(String pid, String fd){
		String fdTgid = processManager.getFdTgid(pid);
		UnknownIdentifier unknown = new UnknownIdentifier(fdTgid, fd);
		FileDescriptor fileDescriptor = new FileDescriptor(unknown, null);
		artifactManager.artifactCreated(unknown);
		processManager.setFd(pid, fd, fileDescriptor);
		return fileDescriptor;
	}
	
	private void handleFcntl(Map<String, String> eventData, SYSCALL syscall){
		// fcntl() receives the following message(s):
		// - SYSCALL
		// - EOE
		 
		String exit = eventData.get(AuditEventReader.EXIT);
		if("-1".equals(exit)){ // Failure check
			return;
		}
		
		String pid = eventData.get(AuditEventReader.PID);
		String fd = eventData.get(AuditEventReader.ARG0);
		String cmdString = eventData.get(AuditEventReader.ARG1);
		String flagsString = eventData.get(AuditEventReader.ARG2);
		
		int cmd = HelperFunctions.parseInt(cmdString, -1);
		int flags = HelperFunctions.parseInt(flagsString, -1);
		
		if(platformConstants.isDuplicateFDFlagInFcntl(cmd)){
			// In eventData, there should be a pid, a0 should be fd, and exit should be the new fd 
			handleDup(eventData, syscall);
		}else if(platformConstants.isSettingFDFlagsInFcntl(cmd)){
			if(platformConstants.testForOpenFlagAppend(flags)){
				FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
				if(fileDescriptor == null){
					fileDescriptor = addUnknownFd(pid, fd);
				}
				// Made file descriptor 'appendable', so set open for read to false so 
				// that the edge on close is a WGB edge and not a Used edge
				if(fileDescriptor.getWasOpenedForRead() != null){
					fileDescriptor.setWasOpenedForRead(false);
				}
			}
		}
	}
	
	private void handleExit(Map<String, String> eventData, SYSCALL syscall){
		// exit(), and exit_group() receives the following message(s):
		// - SYSCALL
		// - EOE
		processManager.handleExit(eventData, syscall, CONTROL);
	}

	private void handleMmap(Map<String, String> eventData, SYSCALL syscall){
		// mmap() receive the following message(s):
		// - MMAP
		// - SYSCALL
		// - EOE

		if(!USE_MEMORY_SYSCALLS){
			return;
		}

		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String time = eventData.get(AuditEventReader.TIME);
		String address = new BigInteger(eventData.get(AuditEventReader.EXIT)).toString(16); //convert to hexadecimal
		String length = new BigInteger(eventData.get(AuditEventReader.ARG1)).toString(16); //convert to hexadecimal
		String protection = new BigInteger(eventData.get(AuditEventReader.ARG2)).toString(16); //convert to hexadecimal
		
		int flags = HelperFunctions.parseInt(eventData.get(AuditEventReader.ARG3), 0);
		
		// Put Process, Memory artifact and WasGeneratedBy edge always but return if flag
		// is MAP_ANONYMOUS
		
		final boolean isAnonymousMmap = platformConstants.isAnonymousMmap(flags);
		
		if(isAnonymousMmap && !ANONYMOUS_MMAP){
			return;
		}
		
		Process process = processManager.handleProcessFromSyscall(eventData);		
		String tgid = processManager.getMemoryTgid(pid);
		ArtifactIdentifier memoryArtifactIdentifier = new MemoryIdentifier(tgid, address, length);
		artifactManager.artifactVersioned(memoryArtifactIdentifier);
		Artifact memoryArtifact = putArtifactFromSyscall(eventData, memoryArtifactIdentifier);
		WasGeneratedBy wgbEdge = new WasGeneratedBy(memoryArtifact, process);
		wgbEdge.addAnnotation(OPMConstants.EDGE_PROTECTION, protection);
		putEdge(wgbEdge, getOperation(syscall, SYSCALL.WRITE), time, eventId, AUDIT_SYSCALL_SOURCE);		
		
		if(isAnonymousMmap){
			return;
		}else{
		
			String fd = eventData.get(AuditEventReader.FD);
	
			if(fd == null){
				log(Level.INFO, "FD record missing", null, time, eventId, syscall);
				return;
			}
	
			FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
	
			if(fileDescriptor == null){
				fileDescriptor = addUnknownFd(pid, fd);
			}
	
			Artifact artifact = putArtifactFromSyscall(eventData, fileDescriptor.identifier);
	
			Used usedEdge = new Used(process, artifact);
			putEdge(usedEdge, getOperation(syscall, SYSCALL.READ), time, eventId, AUDIT_SYSCALL_SOURCE);
	
			WasDerivedFrom wdfEdge = new WasDerivedFrom(memoryArtifact, artifact);
			wdfEdge.addAnnotation(OPMConstants.EDGE_PID, pid);
			putEdge(wdfEdge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		}

	}

	private void handleMprotect(Map<String, String> eventData, SYSCALL syscall){
		// mprotect() receive the following message(s):
		// - SYSCALL
		// - EOE

		if(!USE_MEMORY_SYSCALLS){
			return;
		}

		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String time = eventData.get(AuditEventReader.TIME);
		String address = new BigInteger(eventData.get(AuditEventReader.ARG0)).toString(16);
		String length = new BigInteger(eventData.get(AuditEventReader.ARG1)).toString(16);
		String protection = new BigInteger(eventData.get(AuditEventReader.ARG2)).toString(16);

		String tgid = processManager.getMemoryTgid(pid);
		
		ArtifactIdentifier memoryIdentifier = new MemoryIdentifier(tgid, address, length);
		artifactManager.artifactVersioned(memoryIdentifier);
		Artifact memoryArtifact = putArtifactFromSyscall(eventData, memoryIdentifier);

		Process process = processManager.handleProcessFromSyscall(eventData);
		WasGeneratedBy edge = new WasGeneratedBy(memoryArtifact, process);
		edge.addAnnotation(OPMConstants.EDGE_PROTECTION, protection);
		putEdge(edge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
	}

	private void handleExecve(Map<String, String> eventData, SYSCALL syscall) {
		// execve() receives the following message(s):
		// - SYSCALL
		// - EXECVE
		// - BPRM_FCAPS (ignored)
		// - CWD
		// - PATH
		// - PATH
		// - EOE

		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);

		Process process = processManager.handleExecve(eventData, syscall);

		List<PathRecord> loadPathRecords = PathRecord.getPathsWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
		for(PathRecord loadPathRecord : loadPathRecords){
			if(loadPathRecord != null){
				PathIdentifier pathIdentifier = resolvePath(loadPathRecord, eventData, syscall);
				if(pathIdentifier != null){
					artifactManager.artifactPermissioned(pathIdentifier, loadPathRecord.getPermissions());
					
					Artifact usedArtifact = putArtifactFromSyscall(eventData, pathIdentifier);
					Used usedEdge = new Used(process, usedArtifact);
					putEdge(usedEdge, getOperation(SYSCALL.LOAD), time, eventId, AUDIT_SYSCALL_SOURCE);
				}
			}
		}

		final String processName = process.getAnnotation(OPMConstants.PROCESS_NAME);
		if(processUserSyscallFilter.isProcessNameInIgnoreProcessSet(processName)){
			log(Level.INFO, "'"+processName+"' (pid="+pid+") process seen in execve and present in list of processes to ignore", 
					null, time, eventId, syscall);
		}
	}
	
	public final void handleOpen(Map<String, String> eventData, SYSCALL syscall){
		// open() receives the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH with nametype CREATE (file operated on) or NORMAL (file operated on) or PARENT (parent of file operated on) or DELETE (file operated on) or UNKNOWN (only when syscall fails)
		// - PATH with nametype CREATE or NORMAL or PARENT or DELETE or UNKNOWN
		// - EOE
		
		String eventTime = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String fd = eventData.get(AuditEventReader.EXIT);
		String modeString = null;
		String flagsString = null;

		final PathRecord pathRecord = PathRecord.getPathWithCreateOrNormalNametype(eventData);
		
		if(pathRecord == null){
			log(Level.INFO, "Missing PATH record with NORMAL/CREATE nametype", null, eventTime, eventId, syscall);
			return;
		}
		
		PathIdentifier artifactIdentifier = null;

		if(syscall == SYSCALL.OPEN || syscall == SYSCALL.MQ_OPEN){
			artifactIdentifier = resolvePath(pathRecord, eventData, syscall);
			if(syscall == SYSCALL.MQ_OPEN){
				artifactIdentifier = new PosixMessageQueue(artifactIdentifier.path, artifactIdentifier.rootFSPath);
			}
			flagsString = eventData.get(AuditEventReader.ARG1);
			modeString = eventData.get(AuditEventReader.ARG2);
		}else if(syscall == SYSCALL.OPENAT){
			artifactIdentifier = resolvePath_At(pathRecord, AuditEventReader.ARG0, eventData, syscall);
			flagsString = eventData.get(AuditEventReader.ARG2);
			modeString = eventData.get(AuditEventReader.ARG3);
		}else if(syscall == SYSCALL.CREAT || syscall == SYSCALL.CREATE){
			syscall = SYSCALL.CREATE;
			artifactIdentifier = resolvePath(pathRecord, eventData, syscall);
			long flagsInt = platformConstants.getBitwiseOROfOpenFlags(
					platformConstants.O_CREAT,
					platformConstants.O_WRONLY,
					platformConstants.O_TRUNC
					);
			flagsString = String.valueOf(flagsInt);
			modeString = eventData.get(AuditEventReader.ARG1);
		}else{
			log(Level.INFO, "Unexpected syscall in OPEN handler", null, eventTime, eventId, syscall);
			return;
		}
		
		if(artifactIdentifier != null){
			int flagsInt = HelperFunctions.parseInt(flagsString, 0);
			
			final boolean isCreate = platformConstants.testForOpenFlagCreate(flagsInt);
			
			String flagsAnnotation = platformConstants.stringifyOpenFlags(flagsInt);
			
			String modeAnnotation = null;
			
			if(isCreate){
				syscall = SYSCALL.CREATE;
				artifactManager.artifactCreated(artifactIdentifier);
				modeAnnotation = Long.toOctalString(HelperFunctions.parseInt(modeString, 0));
			}
			
			boolean openedForRead;
			AbstractEdge edge = null;
			Process process = processManager.handleProcessFromSyscall(eventData);
			
			if(platformConstants.testForOpenFlagsThatAllowModification(flagsInt)){
				if(!isCreate){
					// If artifact not created
					artifactManager.artifactVersioned(artifactIdentifier);
				}
				artifactManager.artifactPermissioned(artifactIdentifier, pathRecord.getPermissions());
				Artifact vertex = putArtifactFromSyscall(eventData, artifactIdentifier);
				edge = new WasGeneratedBy(vertex, process);
				openedForRead = false;
			}else if(platformConstants.testForOpenFlagReadOnly(flagsInt)){
				artifactManager.artifactPermissioned(artifactIdentifier, pathRecord.getPermissions());
				if(isCreate){
					Artifact vertex = putArtifactFromSyscall(eventData, artifactIdentifier);
					edge = new WasGeneratedBy(vertex, process);
				}else{
					Artifact vertex = putArtifactFromSyscall(eventData, artifactIdentifier);
					edge = new Used(process, vertex);
				}
				openedForRead = true;
			}else{
				log(Level.INFO, "Unhandled value of FLAGS argument '"+flagsString+"'", null, eventTime, eventId, syscall);
				return;
			}
			
			if(edge != null){
				if(modeAnnotation != null){
					edge.addAnnotation(OPMConstants.EDGE_MODE, modeAnnotation);
				}
				if(!flagsAnnotation.isEmpty()){
					edge.addAnnotation(OPMConstants.EDGE_FLAGS, flagsAnnotation);
				}
				//everything happened successfully. add it to descriptors
				FileDescriptor fileDescriptor = new FileDescriptor(artifactIdentifier, openedForRead);
				processManager.setFd(pid, fd, fileDescriptor);

				putEdge(edge, getOperation(syscall), eventTime, eventId, AUDIT_SYSCALL_SOURCE);
			}
		}
	}

	private void handleClose(Map<String, String> eventData) {
		// close() receives the following message(s):
		// - SYSCALL
		// - EOE
		String pid = eventData.get(AuditEventReader.PID);
		String fd = String.valueOf(HelperFunctions.parseLong(eventData.get(AuditEventReader.ARG0), -1L));
		FileDescriptor closedFileDescriptor = processManager.removeFd(pid, fd);
		
		if(CONTROL){
			SYSCALL syscall = SYSCALL.CLOSE;
			String time = eventData.get(AuditEventReader.TIME);
			String eventId = eventData.get(AuditEventReader.EVENT_ID);
			if(closedFileDescriptor != null){
				Process process = processManager.handleProcessFromSyscall(eventData);
				AbstractEdge edge = null;
				Boolean wasOpenedForRead = closedFileDescriptor.getWasOpenedForRead();
				if(wasOpenedForRead == null){
					// Not drawing an edge because didn't seen an open or was a 'bound' fd
				}else{
					Artifact artifact = putArtifactFromSyscall(eventData, closedFileDescriptor.identifier);
					if(wasOpenedForRead){
						edge = new Used(process, artifact);
					}else{
						edge = new WasGeneratedBy(artifact, process);
					}
				}
				if(edge != null){
					putEdge(edge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
				}
				//after everything done increment epoch is udp socket
				if(isUdp(closedFileDescriptor.identifier)){
					artifactManager.artifactCreated(closedFileDescriptor.identifier);
				}
			}else{
				log(Level.INFO, "No FD with number '"+fd+"' for pid '"+pid+"'", null, time, eventId, syscall);
			}
		}

		//there is an option to either handle epochs 1) when artifact opened/created or 2) when artifacts deleted/closed.
		//handling epoch at opened/created in all cases
	}

	private void handleTruncate(Map<String, String> eventData, SYSCALL syscall) {
		// write() receives the following message(s):
		// - SYSCALL
		// - EOE
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String size = eventData.get(AuditEventReader.ARG1);

		ArtifactIdentifier artifactIdentifier = null;
		
		if(syscall == SYSCALL.TRUNCATE){
			PathRecord pathRecord = PathRecord.getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			artifactIdentifier = resolvePath(pathRecord, eventData, syscall);
			if(artifactIdentifier != null){
				artifactManager.artifactVersioned(artifactIdentifier);
				artifactManager.artifactPermissioned(artifactIdentifier, pathRecord.getPermissions());
			}
		}else if(syscall == SYSCALL.FTRUNCATE){
			String fd = eventData.get(AuditEventReader.ARG0);
			FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
			fileDescriptor = fileDescriptor == null ? addUnknownFd(pid, fd) : fileDescriptor;
			artifactIdentifier = fileDescriptor.identifier;
			artifactManager.artifactVersioned(artifactIdentifier);
		}

		if(artifactIdentifier != null){
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact vertex = putArtifactFromSyscall(eventData, artifactIdentifier);
			WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
			if(size != null){
				wgb.addAnnotation(OPMConstants.EDGE_SIZE, size);
			}
			putEdge(wgb, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		}else{
			log(Level.INFO, "Failed to find artifact identifier from the event data", null, time, eventId, syscall);
		}
	}

	private void handleDup(Map<String, String> eventData, SYSCALL syscall) {
		// dup(), dup2(), and dup3() receive the following message(s):
		// - SYSCALL
		// - EOE
		String pid = eventData.get(AuditEventReader.PID);

		String fd = eventData.get(AuditEventReader.ARG0);
		String newFD = eventData.get(AuditEventReader.EXIT); //new fd returned in all: dup, dup2, dup3

		if(!fd.equals(newFD)){ //if both fds same then it succeeds in case of dup2 and it does nothing so do nothing here too
			FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
			if(fileDescriptor == null){
				fileDescriptor = addUnknownFd(pid, fd);
			}
			processManager.setFd(pid, newFD, fileDescriptor);
		}
	}
	
	private void handleVmsplice(Map<String, String> eventData, SYSCALL syscall){
		// vmsplice() receives the following messages:
		// - SYSCALL
		// - EOE
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		
		String fdOut = eventData.get(AuditEventReader.ARG0);
		String bytes = eventData.get(AuditEventReader.EXIT);
		
		if(!"0".equals(bytes)){	
			FileDescriptor fdOutDescriptor = processManager.getFd(pid, fdOut);
			fdOutDescriptor = fdOutDescriptor == null ? addUnknownFd(pid, fdOut) : fdOutDescriptor;
			
			Process process = processManager.handleProcessFromSyscall(eventData);
			artifactManager.artifactVersioned(fdOutDescriptor.identifier);
			Artifact fdOutArtifact = putArtifactFromSyscall(eventData, fdOutDescriptor.identifier);
	
			WasGeneratedBy processToWrittenArtifact = new WasGeneratedBy(fdOutArtifact, process);
			processToWrittenArtifact.addAnnotation(OPMConstants.EDGE_SIZE, bytes);
			putEdge(processToWrittenArtifact, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		}
	}
	
	private void putTeeSplice(Map<String, String> eventData, SYSCALL syscall,
			String time, String eventId, String fdIn, String fdOut, String pid, String bytes){
		FileDescriptor fdInDescriptor = processManager.getFd(pid, fdIn);
		FileDescriptor fdOutDescriptor = processManager.getFd(pid, fdOut);

		// Use unknown if missing fds
		fdInDescriptor = fdInDescriptor == null ? addUnknownFd(pid, fdIn) : fdInDescriptor;
		fdOutDescriptor = fdOutDescriptor == null ? addUnknownFd(pid, fdOut) : fdOutDescriptor;

		Process process = processManager.handleProcessFromSyscall(eventData);
		Artifact fdInArtifact = putArtifactFromSyscall(eventData, fdInDescriptor.identifier);
		artifactManager.artifactVersioned(fdOutDescriptor.identifier);
		Artifact fdOutArtifact = putArtifactFromSyscall(eventData, fdOutDescriptor.identifier);

		Used processToReadArtifact = new Used(process, fdInArtifact);
		processToReadArtifact.addAnnotation(OPMConstants.EDGE_SIZE, bytes);
		putEdge(processToReadArtifact, getOperation(syscall, SYSCALL.READ), time, eventId, AUDIT_SYSCALL_SOURCE);
		
		WasGeneratedBy processToWrittenArtifact = new WasGeneratedBy(fdOutArtifact, process);
		processToWrittenArtifact.addAnnotation(OPMConstants.EDGE_SIZE, bytes);
		putEdge(processToWrittenArtifact, getOperation(syscall, SYSCALL.WRITE), time, eventId, AUDIT_SYSCALL_SOURCE);
		
		WasDerivedFrom writtenToReadArtifact = new WasDerivedFrom(fdOutArtifact, fdInArtifact);
		writtenToReadArtifact.addAnnotation(OPMConstants.EDGE_PID, pid);
		putEdge(writtenToReadArtifact, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
	}
	
	private void handleTeeSplice(Map<String, String> eventData, SYSCALL syscall){
		// tee(), and splice() receive the following messages:
		// - SYSCALL
		// - EOE
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);

		String fdIn = eventData.get(AuditEventReader.ARG0), fdOut = null;
		String bytes = eventData.get(AuditEventReader.EXIT);
		
		if(syscall == SYSCALL.TEE){
			fdOut = eventData.get(AuditEventReader.ARG1);
		}else if(syscall == SYSCALL.SPLICE){
			fdOut = eventData.get(AuditEventReader.ARG2);
		}else{
			log(Level.WARNING, "Unexpected syscall: " + syscall, null, time, eventId, syscall);
			return;
		}
		
		// If fd out set and bytes transferred is not zero
		if(fdOut != null && !"0".equals(bytes)){
			putTeeSplice(eventData, syscall, time, eventId, fdIn, fdOut, pid, bytes);
		}
	}

	private void handleInitModule(Map<String, String> eventData, SYSCALL syscall){
		// init_module(), and finit_module receive the following messages:
		// - SYSCALL
		// - PATH [OPTIONAL] why? not the path of the kernel module
		// - EOE
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		
		ArtifactIdentifier moduleIdentifier = null;
		if(syscall == SYSCALL.INIT_MODULE){
			String memoryAddress = new BigInteger(eventData.get(AuditEventReader.ARG0)).toString(16); //convert to hexadecimal
			String memorySize = new BigInteger(eventData.get(AuditEventReader.ARG1)).toString(16); //convert to hexadecimal
			String tgid = processManager.getMemoryTgid(pid);
			moduleIdentifier = new MemoryIdentifier(tgid, memoryAddress, memorySize);
		}else if(syscall == SYSCALL.FINIT_MODULE){
			String fd = eventData.get(AuditEventReader.ARG0);
			FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
			fileDescriptor = fileDescriptor == null ? addUnknownFd(pid, fd) : fileDescriptor;
			moduleIdentifier = fileDescriptor.identifier;
		}else{
			log(Level.WARNING, "Unexpected syscall in (f)init_module handler", null, time, eventId, syscall);
		}
		
		if(moduleIdentifier != null){
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact module = putArtifactFromSyscall(eventData, moduleIdentifier);
			Used loadedModule = new Used(process, module);
			putEdge(loadedModule, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		}
	}
	
	private void handleSetuidAndSetgid(Map<String, String> eventData, SYSCALL syscall){
		// setuid(), setreuid(), setresuid(), setfsuid(), 
		// setgid(), setregid(), setresgid(), and setfsgid() receive the following message(s):
		// - SYSCALL
		// - EOE
		
		processManager.handleSetuidSetgid(eventData, syscall);
	}

	private void handleRename(Map<String, String> eventData, SYSCALL syscall) {
		// rename(), renameat(), and renameat2() receive the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH 0
		// - PATH 1
		// - PATH 2 with nametype DELETE
		// - PATH 3 with nametype DELETE or CREATE
		// - [OPTIONAL] PATH 4 with nametype CREATE
		// - EOE
		// Resolving paths relative to CWD if not absolute

		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		
		PathRecord oldPathRecord = PathRecord.getPathWithItemNumber(eventData, 2);
		PathRecord newPathRecord = PathRecord.getPathWithItemNumber(eventData, 4);
		//if file renamed to already existed then path4 else path3. Both are same so just getting whichever exists
		newPathRecord = newPathRecord == null ? PathRecord.getPathWithItemNumber(eventData, 3) : newPathRecord;
		
		if(oldPathRecord == null){
			log(Level.WARNING, "Missing source PATH record", null, time, eventId, syscall);
			return;
		}
		
		if(newPathRecord == null){
			log(Level.WARNING, "Missing destination PATH record", null, time, eventId, syscall);
			return;
		}
		
		ArtifactIdentifier oldArtifactIdentifier = null;
		ArtifactIdentifier newArtifactIdentifier = null;
				
		if(syscall == SYSCALL.RENAME){
			oldArtifactIdentifier = resolvePath(oldPathRecord, eventData, syscall);
			newArtifactIdentifier = resolvePath(newPathRecord, eventData, syscall);
		}else if(syscall == SYSCALL.RENAMEAT){
			oldArtifactIdentifier = resolvePath_At(oldPathRecord, AuditEventReader.ARG0, eventData, syscall);
			newArtifactIdentifier = resolvePath_At(newPathRecord, AuditEventReader.ARG2, eventData, syscall);     	
		}else{
			log(Level.WARNING, "Unexpected syscall '"+syscall+"' in RENAME handler", null, time, eventId, syscall);
			return;
		}

		if(oldArtifactIdentifier == null || newArtifactIdentifier == null){
			return;
		}

		handleSpecialSyscalls(eventData, syscall, 
				oldPathRecord, oldArtifactIdentifier, 
				newPathRecord, newArtifactIdentifier);
	}
	
	private void handleMknod(Map<String, String> eventData, SYSCALL syscall){
		//mknod() receives the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH of the parent with nametype=PARENT
		// - PATH of the created file with nametype=CREATE
		// - EOE

		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);

		PathRecord pathRecord = PathRecord.getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_CREATE);
		
		ArtifactIdentifier artifactIdentifier = null;
		
		if(syscall == SYSCALL.MKNOD){
			artifactIdentifier = resolvePath(pathRecord, eventData, syscall);
		}else if(syscall == SYSCALL.MKNODAT){
			artifactIdentifier = resolvePath_At(pathRecord, AuditEventReader.ARG0, eventData, syscall);
		}else{
			log(Level.WARNING, "Unexpected syscall '"+syscall+"' in MKNOD handler", null, time, eventId, syscall);
			return;
		}
		
		if(artifactIdentifier != null){
			artifactManager.artifactCreated(artifactIdentifier);
		}
	}

	private void handleLinkSymlink(Map<String, String> eventData, SYSCALL syscall) {
		// link(), symlink(), linkat(), and symlinkat() receive the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH 0 is path of <src> relative to <cwd>
		// - PATH 1 is directory of <dst>
		// - PATH 2 is path of <dst> relative to <cwd>
		// - EOE

		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		
		PathRecord srcPathRecord = PathRecord.getPathWithItemNumber(eventData, 0);
		PathRecord dstPathRecord = PathRecord.getPathWithItemNumber(eventData, 2);
		
		if(srcPathRecord == null){
			log(Level.WARNING, "Missing source PATH record", null, time, eventId, syscall);
			return;
		}
		
		if(dstPathRecord == null){
			log(Level.WARNING, "Missing destination PATH record", null, time, eventId, syscall);
			return;
		}
		
		ArtifactIdentifier srcArtifactIdentifier = null;
		ArtifactIdentifier dstArtifactIdentifier = null;
		
		if(syscall == SYSCALL.LINK || syscall == SYSCALL.SYMLINK){
			srcArtifactIdentifier = resolvePath(srcPathRecord, eventData, syscall);
			dstArtifactIdentifier = resolvePath(dstPathRecord, eventData, syscall);
		}else if(syscall == SYSCALL.LINKAT){
			srcArtifactIdentifier = resolvePath_At(srcPathRecord, AuditEventReader.ARG0, eventData, syscall);
			dstArtifactIdentifier = resolvePath_At(dstPathRecord, AuditEventReader.ARG2, eventData, syscall);
		}else if(syscall == SYSCALL.SYMLINKAT){
			srcArtifactIdentifier = resolvePath(srcPathRecord, eventData, syscall);
			dstArtifactIdentifier = resolvePath_At(dstPathRecord, AuditEventReader.ARG1, eventData, syscall);
		}else{
			log(Level.WARNING, "Unexpected syscall '"+syscall+"' in LINK/SYMLINK handler", null, time, eventId, syscall);
			return;
		}

		if(srcArtifactIdentifier == null || dstArtifactIdentifier == null){
			return;
		}

		handleSpecialSyscalls(eventData, syscall, 
				srcPathRecord, srcArtifactIdentifier, 
				dstPathRecord, dstArtifactIdentifier);
	}

	/**
	 * Creates OPM vertices and edges for link, symlink, linkat, symlinkat, rename, renameat syscalls.
	 * 
	 * Steps:
	 * 1) Gets the valid source artifact type (can be either file, named pipe, unix socket)
	 * 2) Creates a valid destination artifact type with the same type as the source artifact type
	 * 2.a) Marks new epoch for the destination file
	 * 3) Adds the process vertex, artifact vertices, and edges (Process to srcArtifact [Used], Process to dstArtifact [WasGeneratedBy], dstArtifact to oldArtifact [WasDerivedFrom])
	 * to reporter's internal buffer.
	 * 
	 * @param eventData event data as gotten from the audit log
	 * @param syscall syscall being handled
	 * @param srcPath path of the file being linked
	 * @param dstPath path of the link
	 */
	private void handleSpecialSyscalls(Map<String, String> eventData, SYSCALL syscall, 
			PathRecord srcPathRecord, ArtifactIdentifier srcArtifactIdentifier, 
			PathRecord dstPathRecord, ArtifactIdentifier dstArtifactIdentifier){

		if(eventData == null || syscall == null || srcArtifactIdentifier == null || dstArtifactIdentifier == null){
			logger.log(Level.INFO, "Missing arguments. srcPath:{0}, dstPath:{1}, syscall:{2}, eventData:{3}", 
					new Object[]{srcArtifactIdentifier, dstArtifactIdentifier, syscall, eventData});
			return;
		}

		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);

		if(eventId == null || time == null || pid == null){
			log(Level.INFO, "Missing keys in event data. pid:"+pid, null, time, eventId, syscall);
			return;
		}

		Process process = processManager.handleProcessFromSyscall(eventData);

		//destination is new so mark epoch
		artifactManager.artifactCreated(dstArtifactIdentifier);

		artifactManager.artifactPermissioned(srcArtifactIdentifier, srcPathRecord.getPermissions());
		Artifact srcVertex = putArtifactFromSyscall(eventData, srcArtifactIdentifier);
		Used used = new Used(process, srcVertex);
		putEdge(used, getOperation(syscall, SYSCALL.READ), time, eventId, AUDIT_SYSCALL_SOURCE);

		artifactManager.artifactPermissioned(dstArtifactIdentifier, dstPathRecord.getPermissions());
		Artifact dstVertex = putArtifactFromSyscall(eventData, dstArtifactIdentifier);
		WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, process);
		putEdge(wgb, getOperation(syscall, SYSCALL.WRITE), time, eventId, AUDIT_SYSCALL_SOURCE);

		WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
		wdf.addAnnotation(OPMConstants.EDGE_PID, pid);
		putEdge(wdf, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
	}

	private void handleChmod(Map<String, String> eventData, SYSCALL syscall) {
		// chmod(), fchmod(), and fchmodat() receive the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH
		// - EOE
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);
		String modeArgument = null;
		
		PathRecord pathRecord = PathRecord.getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
		
		// if syscall is chmod, then path is <path0> relative to <cwd>
		// if syscall is fchmod, look up file descriptor which is <a0>
		// if syscall is fchmodat, loop up the directory fd and build a path using the path in the audit log
		ArtifactIdentifier artifactIdentifier = null;
		if(syscall == SYSCALL.CHMOD){
			artifactIdentifier = resolvePath(pathRecord, eventData, syscall);
			modeArgument = eventData.get(AuditEventReader.ARG1);
		}else if(syscall == SYSCALL.FCHMOD){
			String fd = eventData.get(AuditEventReader.ARG0);
			FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
			fileDescriptor = fileDescriptor == null ? addUnknownFd(pid, fd) : fileDescriptor;
			artifactIdentifier = fileDescriptor.identifier;
			modeArgument = eventData.get(AuditEventReader.ARG1);
		}else if(syscall == SYSCALL.FCHMODAT){
			artifactIdentifier = resolvePath_At(pathRecord, AuditEventReader.ARG0, eventData, syscall);
			modeArgument = eventData.get(AuditEventReader.ARG2);
		}else{
			log(Level.INFO, "Unexpected syscall '"+syscall+"' in CHMOD handler", null, time, eventId, syscall);
			return;
		}

		if(artifactIdentifier == null){
			logger.log(Level.WARNING, "Failed to process syscall="+syscall +" because of missing artifact identifier");
			return;
		}
		
		String mode = new BigInteger(modeArgument).toString(8);
		String permissions = PathRecord.parsePermissions(mode);
		
		artifactManager.artifactVersioned(artifactIdentifier);
		artifactManager.artifactPermissioned(artifactIdentifier, permissions);
		
		Process process = processManager.handleProcessFromSyscall(eventData);
		Artifact vertex = putArtifactFromSyscall(eventData, artifactIdentifier);
		WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
		wgb.addAnnotation(OPMConstants.EDGE_MODE, mode);
		putEdge(wgb, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
	}
	
	private void handleKill(Map<String, String> eventData, SYSCALL syscall){
		// kill() receives the following message(s):
		// - SYSCALL
		// - EOE

		/*
		 * a0   = pid 
		 * 0    - targetPid = caller pid
		 * -1   - to all processes to which the called pid can send the signal to
		 * -pid - send to pid and the group of the pid
		 */
		
		// Only handling successful ones
		if("0".equals(eventData.get(AuditEventReader.EXIT))){
			String time = eventData.get(AuditEventReader.TIME);
			String eventId = eventData.get(AuditEventReader.EVENT_ID);

			String targetPidStr = eventData.get(AuditEventReader.ARG0);
			
			Process targetProcess = null;
			try{
				int targetPidInt = Integer.parseInt(targetPidStr);
				if(targetPidInt < -1){
					targetPidInt = targetPidInt * -1;
					targetPidStr = String.valueOf(targetPidInt);
					targetProcess = processManager.getVertex(targetPidStr);
				}else if(targetPidInt == 0){
					String pid = eventData.get(AuditEventReader.PID);
					targetPidStr = pid;
					targetProcess = processManager.getVertex(targetPidStr);
				}else if(targetPidInt == -1){
					// Unhandled TODO
					// Let targetProcess stay null and it won't be sent to OPM
					logger.log(Level.INFO, "'-1' target pid");
				}else{
					// Regular non-negative and non-zero pid
					targetProcess = processManager.getVertex(targetPidStr);
				}
			}catch(Exception e){
				log(Level.WARNING, "Invalid target pid(a0): " + targetPidStr, null, time, eventId, syscall);
			}

			// If the target process hasn't been seen yet then can't draw an edge because don't 
			// have enough annotations for the target process.
			if(targetProcess != null){
				String signalString = eventData.get(AuditEventReader.ARG1);
				Integer signal = HelperFunctions.parseInt(signalString, null);
				
				if(signal != null){
					String signalAnnotation = String.valueOf(signal);
					if(signalAnnotation != null){
						Process actingProcess = processManager.handleProcessFromSyscall(eventData);
						
						String operation = getOperation(syscall);
						
						WasTriggeredBy edge = new WasTriggeredBy(targetProcess, actingProcess);
						edge.addAnnotation(OPMConstants.EDGE_SIGNAL, signalAnnotation);
						
						putEdge(edge, operation, time, eventId, AUDIT_SYSCALL_SOURCE);
					}
				}
			}
		}
	}
	
	private void handlePtrace(Map<String, String> eventData, SYSCALL syscall){
		// ptrace() receives the following message(s):
		// - SYSCALL
		// - EOE
		String targetPid = eventData.get(AuditEventReader.ARG1);
		Process targetProcess = processManager.getVertex(targetPid);
		
		// If the target process hasn't been seen yet then can't draw an edge because don't 
		// have enough annotations for the target process.
		if(targetProcess != null){
			String actionString = eventData.get(AuditEventReader.ARG0);
			Integer action = HelperFunctions.parseInt(actionString, null);
			
			// If the action argument is valid only then can continue because only handling some
			if(action != null){
				String actionAnnotation = platformConstants.getPtraceActionName(action);
				// If this is one of the actions that needs to be handled then it won't be null
				if(actionAnnotation != null){
					Process actingProcess = processManager.handleProcessFromSyscall(eventData);
					
					String time = eventData.get(AuditEventReader.TIME);
					String eventId = eventData.get(AuditEventReader.EVENT_ID);
					String operation = getOperation(syscall);
					
					WasTriggeredBy edge = new WasTriggeredBy(targetProcess, actingProcess);
					edge.addAnnotation(OPMConstants.EDGE_REQUEST, actionAnnotation);
					
					putEdge(edge, operation, time, eventId, AUDIT_SYSCALL_SOURCE);
				}
			}
		}
	}
	
	private void handleSocketPair(Map<String, String> eventData, SYSCALL syscall){
		// socketpair() receives the following message(s):
		// - SYSCALL
		// - FD_PAIR
		// - EOE
		String pid = eventData.get(AuditEventReader.PID);
		String fd0 = eventData.get(AuditEventReader.FD0);
		String fd1 = eventData.get(AuditEventReader.FD1);
		String domainString = eventData.get(AuditEventReader.ARG0);
		String sockTypeString = eventData.get(AuditEventReader.ARG1);
		String fdTgid = processManager.getFdTgid(pid);
		
		int domain = HelperFunctions.parseInt(domainString, null); // Let exception be thrown
		int sockType = HelperFunctions.parseInt(sockTypeString, null);
		
		String protocol = getProtocolNameBySockType(sockType);
		
		ArtifactIdentifier fdIdentifier = null;
		
		if(platformConstants.isSocketDomainNetwork(domain)){
			fdIdentifier = new UnnamedNetworkSocketPairIdentifier(fdTgid, fd0, fd1, protocol);
		}else if(platformConstants.isSocketDomainLocal(domain)){
			fdIdentifier = new UnnamedUnixSocketPairIdentifier(fdTgid, fd0, fd1);
		}else{
			// Unsupported domain
		}
		
		if(fdIdentifier != null){
			processManager.setFd(pid, fd0, new FileDescriptor(fdIdentifier, false));
			processManager.setFd(pid, fd1, new FileDescriptor(fdIdentifier, false));
			
			artifactManager.artifactCreated(fdIdentifier);
		}
	}

	private void handlePipe(Map<String, String> eventData, SYSCALL syscall) {
		// pipe() receives the following message(s):
		// - SYSCALL
		// - FD_PAIR
		// - EOE
		String pid = eventData.get(AuditEventReader.PID);
		String fdTgid = processManager.getFdTgid(pid);
		String fd0 = eventData.get(AuditEventReader.FD0);
		String fd1 = eventData.get(AuditEventReader.FD1);
		ArtifactIdentifier readPipeIdentifier = new UnnamedPipeIdentifier(fdTgid, fd0, fd1);
		ArtifactIdentifier writePipeIdentifier = new UnnamedPipeIdentifier(fdTgid, fd0, fd1);
		processManager.setFd(pid, fd0, new FileDescriptor(readPipeIdentifier, true));
		processManager.setFd(pid, fd1, new FileDescriptor(writePipeIdentifier, false));

		// Since both (read, and write) pipe identifiers are the same, only need to mark epoch on one.
		artifactManager.artifactCreated(readPipeIdentifier);
	}
	
	private NetworkSocketIdentifier getNetworkIdentifier(SYSCALL syscall, String time, String eventId, 
			String pid, String fd){
		FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
		if(fileDescriptor != null){
			if(fileDescriptor.identifier.getClass().equals(NetworkSocketIdentifier.class)){
				return ((NetworkSocketIdentifier)fileDescriptor.identifier);
			}else{
				log(Level.INFO, "Expected network identifier but found: " + 
						fileDescriptor.identifier.getClass(), null, time, eventId, syscall);
				return null;
			}
		}else{
			return null;
		}
	}
	
	private String getProtocol(SYSCALL syscall, String time, String eventId, String pid, String fd){
		NetworkSocketIdentifier identifier = getNetworkIdentifier(syscall, time, eventId, pid, fd);
		if(identifier != null){
			return identifier.getProtocol();
		}else{
			return null;
		}
	}
	
	private AddressPort getLocalAddressPort(SYSCALL syscall, String time, String eventId, String pid, String fd){
		NetworkSocketIdentifier identifier = getNetworkIdentifier(syscall, time, eventId, pid, fd);
		if(identifier != null){
			NetworkSocketIdentifier networkIdentifier = ((NetworkSocketIdentifier)identifier);
			return new AddressPort(networkIdentifier.getLocalHost(), networkIdentifier.getLocalPort());
		}else{
			return null;
		}
	}
	
	private String getNetworkNamespaceForPid(String pid){
		if(HANDLE_NAMESPACES){
			return processManager.getNetNamespace(pid);
		}else{
			return null;
		}
	}
	
	private void handleSocket(Map<String, String> eventData, SYSCALL syscall){
		// socket() receives the following message(s):
		// - SYSCALL
		// - EOE
		String sockFd = eventData.get(AuditEventReader.EXIT);
		Integer socketType = HelperFunctions.parseInt(eventData.get(AuditEventReader.ARG1), null);
		String protocolName = getProtocolNameBySockType(socketType);
		
		if(protocolName != null){
			String pid = eventData.get(AuditEventReader.PID);

			NetworkSocketIdentifier identifierForProtocol = new NetworkSocketIdentifier(
					null, null, null, null, protocolName, getNetworkNamespaceForPid(eventData.get(AuditEventReader.PID)));
			processManager.setFd(pid, sockFd, new FileDescriptor(identifierForProtocol, null)); // no close edge
		}
	}
	
	private void putBind(String pid, String fd, ArtifactIdentifier identifier){
		if(identifier != null){
			// no need to add to descriptors because we will have the address from other syscalls? TODO
			processManager.setFd(pid, fd, new FileDescriptor(identifier, null));
			if(identifier instanceof UnixSocketIdentifier){
				artifactManager.artifactCreated(identifier);
			}
		}
	}

	// needed for marking epoch for unix socket
	private void handleBindKernelModule(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String exit, String sockFd, int sockType, String localSaddr, String remoteSaddr){
		boolean isNetwork = isNetworkSaddr(localSaddr) || isNetworkSaddr(remoteSaddr);
		if(!isNetwork){
			// is unix
			if(isUnixSaddr(localSaddr)){
				ArtifactIdentifier identifier = parseUnixSaddr(pid, localSaddr); // local or remote. any is fine.
				if(identifier != null){
					putBind(pid, sockFd, identifier);
				}else{
					logInvalidSaddr(remoteSaddr, time, eventId, syscall);
				}
			}
		}else{
			// nothing needed in case of network because we get all info from other syscalls if kernel module
		}
	}
	
	private void handleBind(Map<String, String> eventData, SYSCALL syscall) {
		// bind() receives the following message(s):
		// - SYSCALL
		// - SADDR
		// - EOE
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String saddr = eventData.get(AuditEventReader.SADDR);
		String sockFd = eventData.get(AuditEventReader.ARG0);
		String pid = eventData.get(AuditEventReader.PID);

		if(!isNetlinkSaddr(saddr)){ // not handling netlink
			ArtifactIdentifier identifier = null;
			if(isNetworkSaddr(saddr)){
				AddressPort addressPort = parseNetworkSaddr(saddr);
				if(addressPort != null){
					String protocolName = getProtocol(syscall, time, eventId, pid, sockFd);
					identifier = new NetworkSocketIdentifier(
							addressPort.address, addressPort.port, null, null, protocolName,
							getNetworkNamespaceForPid(pid));
				}
			}else if(isUnixSaddr(saddr)){
				identifier = parseUnixSaddr(pid, saddr);
			}
			
			if(identifier == null){
				logInvalidSaddr(saddr, time, eventId, syscall);
			}else{
				putBind(pid, sockFd, identifier);
			}
		}
	}
	
	private NetworkSocketIdentifier constructNetworkIdentifier(SYSCALL syscall, String time, String eventId, 
			String localSaddr, String remoteSaddr, Integer sockType, String pid){
		String protocolName = getProtocolNameBySockType(sockType);
		AddressPort local = parseNetworkSaddr(localSaddr);
		AddressPort remote = parseNetworkSaddr(remoteSaddr);
		if(local == null && remote == null){
			log(Level.INFO, "Local and remote saddr both null", null, time, eventId, syscall);
		}else{
			String localAddress = null, localPort = null, remoteAddress = null, remotePort = null;
			if(local != null){
				localAddress = local.address;
				localPort = local.port;
			}
			if(remote != null){
				remoteAddress = remote.address;
				remotePort = remote.port;
			}
			return new NetworkSocketIdentifier(localAddress, localPort, 
					remoteAddress, remotePort, protocolName, getNetworkNamespaceForPid(pid));
		}
		return null;
	}
		
	private void putConnect(SYSCALL syscall, String time, String eventId, String pid, String fd, 
			ArtifactIdentifier fdIdentifier, Map<String, String> eventData){
		if(fdIdentifier != null){
			if(fdIdentifier instanceof NetworkSocketIdentifier){
				artifactManager.artifactCreated(fdIdentifier);
			}
			processManager.setFd(pid, fd, new FileDescriptor(fdIdentifier, false));
			
			Process process = processManager.handleProcessFromSyscall(eventData);
			artifactManager.artifactVersioned(fdIdentifier);
			Artifact artifact = putArtifactFromSyscall(eventData, fdIdentifier);
			WasGeneratedBy wgb = new WasGeneratedBy(artifact, process);
			putEdge(wgb, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
			
			if(HANDLE_NETFILTER_HOOKS){
				try{
					netfilterHooksManager.handleNetworkSyscallEvent(time, eventId, false, artifact);
				}catch(Exception e){
					log(Level.SEVERE, "Unexpected error", e, time, eventId, syscall);
				}
			}
		}
	}
	
	private void handleConnectKernelModule(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String exit, String sockFd, int sockType, String localSaddr, String remoteSaddr){
		// if not network then unix. Only that being handled in kernel module
		boolean isNetwork = isNetworkSaddr(localSaddr) || isNetworkSaddr(remoteSaddr);
		
		ArtifactIdentifier identifier = null;
		
		if(isNetwork){
			identifier = constructNetworkIdentifier(syscall, time, eventId, localSaddr, remoteSaddr, sockType, pid);
		}else{ // is unix socket
			if(isUnixSaddr(remoteSaddr)){
				identifier = parseUnixSaddr(pid, remoteSaddr); // address in remote unlike accept
				if(identifier == null){
					logInvalidSaddr(localSaddr, time, eventId, syscall);
				}
			}
		}
		if(identifier != null){
			putConnect(syscall, time, eventId, pid, sockFd, identifier, eventData);
		}
	}

	private void handleConnect(Map<String, String> eventData, SYSCALL syscall){
		//connect() receives the following message(s):
		// - SYSCALL
		// - SADDR
		// - EOE	
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);
		String saddr = eventData.get(AuditEventReader.SADDR);
		String sockFd = eventData.get(AuditEventReader.ARG0);
		
		Integer exit = HelperFunctions.parseInt(eventData.get(AuditEventReader.EXIT), null);
		if(exit == null){
			log(Level.WARNING, "Failed to parse exit value: " + eventData.get(AuditEventReader.EXIT), 
					null, time, eventId, syscall);
			return;
		}else{ // not null
			// only handling if success is 0 or success is EINPROGRESS
			if(exit != 0 // no success
					&& !platformConstants.isConnectInProgress(exit)){ //in progress with possible failure in the future. see manpage.
				return;
			}
		}

		if(!isNetlinkSaddr(saddr)){ // not handling netlink saddr
			ArtifactIdentifier identifier = null;
			if(isNetworkSaddr(saddr)){
				AddressPort addressPort = parseNetworkSaddr(saddr);
				if(addressPort != null){
					String protocolName = getProtocol(syscall, time, eventId, pid, sockFd);
					identifier = new NetworkSocketIdentifier(
							null, null, addressPort.address, addressPort.port, protocolName, getNetworkNamespaceForPid(pid));
					
				}
			}else if(isUnixSaddr(saddr)){
				identifier = parseUnixSaddr(pid, saddr);
			}
			
			if(identifier == null){
				logInvalidSaddr(saddr, time, eventId, syscall);
			}else{
				putConnect(syscall, time, eventId, pid, sockFd, identifier, eventData);
			}
		}
	}

	private void putAccept(SYSCALL syscall, String time, String eventId, String pid, String fd, 
			ArtifactIdentifier fdIdentifier, Map<String, String> eventData){
		// eventData must contain all information need to create a process vertex
		if(fdIdentifier != null){
			if(fdIdentifier instanceof NetworkSocketIdentifier){
				artifactManager.artifactCreated(fdIdentifier);
			}
			
			processManager.setFd(pid, fd, new FileDescriptor(fdIdentifier, false));
			
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact socket = putArtifactFromSyscall(eventData, fdIdentifier);
			Used used = new Used(process, socket);
			putEdge(used, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
			
			if(HANDLE_NETFILTER_HOOKS){
				try{
					netfilterHooksManager.handleNetworkSyscallEvent(time, eventId, true, socket);
				}catch(Exception e){
					log(Level.SEVERE, "Unexpected error", e, time, eventId, syscall);
				}
			}
		}
	}
	
	private void handleAcceptKernelModule(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String fd, String sockFd, int sockType, String localSaddr, String remoteSaddr){
		// if not network then unix. Only that being handled in kernel module
		boolean isNetwork = isNetworkSaddr(localSaddr) || isNetworkSaddr(remoteSaddr);
		
		ArtifactIdentifier identifier = null;
		if(isNetwork){
			identifier = constructNetworkIdentifier(syscall, time, eventId, localSaddr, remoteSaddr, sockType, pid);
		}else{ // is unix socket
			if(isUnixSaddr(localSaddr)){
				identifier = parseUnixSaddr(pid, localSaddr);
				if(identifier == null){
					logInvalidSaddr(localSaddr, time, eventId, syscall);
				}
			}
		}
		if(identifier != null){
			putAccept(syscall, time, eventId, pid, fd, identifier, eventData);
		}
	}
	
	private void handleAccept(Map<String, String> eventData, SYSCALL syscall) {
		//accept() & accept4() receive the following message(s):
		// - SYSCALL
		// - SADDR
		// - EOE
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);
		String sockFd = eventData.get(AuditEventReader.ARG0); //the fd on which the connection was accepted, not the fd of the connection
		String fd = eventData.get(AuditEventReader.EXIT); //fd of the connection
		String saddr = eventData.get(AuditEventReader.SADDR);

		if(!isNetlinkSaddr(saddr)){ // not handling netlink saddr
			ArtifactIdentifier identifier = null;
			FileDescriptor boundFileDescriptor = processManager.getFd(pid, sockFd);
			if(isNetworkSaddr(saddr)){
				AddressPort addressPort = parseNetworkSaddr(saddr);
				if(addressPort != null){
					String localAddress = null, localPort = null, 
							protocol = getProtocol(syscall, time, eventId, pid, sockFd);
					AddressPort localAddressPort = getLocalAddressPort(syscall, time, eventId, pid, sockFd);
					if(localAddressPort != null){
						localAddress = localAddressPort.address;
						localPort = localAddressPort.port;
					}
					identifier = new NetworkSocketIdentifier(localAddress, localPort, 
							addressPort.address, addressPort.port, protocol, getNetworkNamespaceForPid(pid));
				}
			}else if(isUnixSaddr(saddr)){
				// The unix saddr in accept is empty. So, use the bound one.
				if(boundFileDescriptor != null){
					if(boundFileDescriptor.identifier.getClass().equals(UnixSocketIdentifier.class)){
						// Use a new one because the wasOpenedForRead is updated otherwise it would
						// be updated in the bound identifier too.
						UnixSocketIdentifier boundUnixIdentifier = (UnixSocketIdentifier)(boundFileDescriptor.identifier);
						identifier = new UnixSocketIdentifier(boundUnixIdentifier.path, boundUnixIdentifier.rootFSPath);
					}else{
						log(Level.INFO, "Expected unix identifier but found: " + 
								boundFileDescriptor.identifier.getClass(), null, time, eventId, syscall);
					}
				}
			}
			
			if(identifier == null){
				logInvalidSaddr(saddr, time, eventId, syscall);
			}else{
				putAccept(syscall, time, eventId, pid, fd, identifier, eventData);
			}
		}
	}
	
	private ArtifactIdentifier getNetworkIdentifierFromFdAndOrSaddr(SYSCALL syscall, String time, String eventId,
			String pid, String fd, String saddr){
		ArtifactIdentifier identifier = null;
		if(saddr != null){
			if(!isNetlinkSaddr(saddr)){ // not handling netlink saddr
				if(isNetworkSaddr(saddr)){
					AddressPort addressPort = parseNetworkSaddr(saddr);
					if(addressPort != null){
						String localAddress = null, localPort = null;
						//	Protocol has to be UDP since SOCK_DGRAM and (AF_INET or AF_INET6) 
						//	protocol = getProtocol(syscall, time, eventId, pid, fd);
						AddressPort localAddressPort = getLocalAddressPort(syscall, time, eventId, pid, fd);
						if(localAddressPort != null){
							localAddress = localAddressPort.address;
							localPort = localAddressPort.port;
						}
						// Protocol can only be UDP because saddr only present when SOCK_DGRAM
						// and family is AF_INET or AF_INET6.
						identifier = new NetworkSocketIdentifier(localAddress, localPort, 
								addressPort.address, addressPort.port, PROTOCOL_NAME_UDP, getNetworkNamespaceForPid(pid));
					}
				}else if(isUnixSaddr(saddr)){
					identifier = parseUnixSaddr(pid, saddr);
					// just use this
				}
			}
		}else{
			// use fd
			FileDescriptor fileDescriptor = processManager.getFd(pid, fd);
			if(fileDescriptor != null){
				identifier = fileDescriptor.identifier;
			}
		}
	
		// Don't update fd in descriptors even if updated identifier
		// Not updating because if fd used again then saddr must be present
		return identifier;
	}
	
	private void handleNetworkIOKernelModule(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String bytes, String sockFd, int sockType, String localSaddr, String remoteSaddr,
			boolean isRecv){
		boolean isNetwork = isNetworkSaddr(localSaddr) || isNetworkSaddr(remoteSaddr);
		ArtifactIdentifier identifier = null;
		if(isNetwork){
			NetworkSocketIdentifier recordIdentifier =
					constructNetworkIdentifier(syscall, time, eventId, localSaddr, remoteSaddr, sockType, pid);
			FileDescriptor fileDescriptor = processManager.getFd(pid, sockFd);
			if(fileDescriptor != null && fileDescriptor.identifier instanceof NetworkSocketIdentifier){
				NetworkSocketIdentifier fdNetworkIdentifier = (NetworkSocketIdentifier)fileDescriptor.identifier;
				if(HelperFunctions.isNullOrEmpty(fdNetworkIdentifier.getRemoteHost())){
					// Connection based IO
					identifier = recordIdentifier;
				}else{
					// Non-connection based IO
					identifier = fdNetworkIdentifier;
				}
			}else{
				identifier = recordIdentifier;
			}
		}else{ // is unix socket
			if(isUnixSaddr(localSaddr)){
				identifier = parseUnixSaddr(pid, localSaddr);
				if(identifier == null){
					logInvalidSaddr(localSaddr, time, eventId, syscall);
				}
			}
		}
		putIO(eventData, time, eventId, syscall, pid, sockFd, identifier, bytes, null, isRecv);
	}
	
	private void putIO(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String fd, ArtifactIdentifier identifier, 
			String bytesTransferred, String offset, boolean incoming){

		boolean isNetworkUdp = false;
		if(identifier instanceof NetworkSocketIdentifier){
			if(!USE_SOCK_SEND_RCV){
				return;
			}
			isNetworkUdp = isUdp(identifier);
		}else{ // all else are local i.e. file io include unix
			if(!USE_READ_WRITE){
				return;
			}
			if(identifier instanceof UnixSocketIdentifier || identifier instanceof UnnamedUnixSocketPairIdentifier){
				if(!globals.unixSockets){
					return;
				}
			}
		}
		
		if(identifier == null){
			identifier = addUnknownFd(pid, fd).identifier;
		}
		
		if(isNetworkUdp){
			// Since saddr present that means that it is SOCK_DGRAM.
			// Epoch for all SOCK_DGRAM
			artifactManager.artifactCreated(identifier);
		}

		Process process = processManager.handleProcessFromSyscall(eventData);
		Artifact artifact = null;
		AbstractEdge edge = null;
		if(incoming){
			artifact = putArtifactFromSyscall(eventData, identifier);
			edge = new Used(process, artifact);
		}else{
			artifactManager.artifactVersioned(identifier);
			artifact = putArtifactFromSyscall(eventData, identifier);
			edge = new WasGeneratedBy(artifact, process);
		}
		edge.addAnnotation(OPMConstants.EDGE_SIZE, bytesTransferred);
		if(offset != null){
			edge.addAnnotation(OPMConstants.EDGE_OFFSET, offset);	
		}
		putEdge(edge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		
		// UDP
		if(isNetworkUdp && HANDLE_NETFILTER_HOOKS){
			try{
				netfilterHooksManager.handleNetworkSyscallEvent(time, eventId, incoming, artifact);
			}catch(Exception e){
				log(Level.SEVERE, "Unexpected error", e, time, eventId, syscall);
			}
		}
	}

	/**
	 * Outputs formatted messages in the format-> [Event ID:###, SYSCALL:... MSG Exception]
	 *  
	 * @param level Level of the log message
	 * @param msg Message to print
	 * @param exception Exception (if any)
	 * @param time time of the audit event
	 * @param eventId id of the audit event
	 * @param syscall system call of the audit event
	 */
	public void log(Level level, String msg, Exception exception, String time, String eventId, SYSCALL syscall){
		String msgPrefix = "";
		if(eventId != null && syscall != null){
			msgPrefix = "[Time:EventID="+time+":"+eventId+", SYSCALL="+syscall+"] ";
		}else if(eventId != null && syscall == null){
			msgPrefix = "[Time:EventID="+time+":"+eventId+"] ";
		}else if(eventId == null && syscall != null){
			msgPrefix = "[SYSCALL="+syscall+"] ";
		}
		if(exception == null){
			logger.log(level, msgPrefix + msg);
		}else{
			logger.log(level, msgPrefix + msg, exception);
		}
	}
	
	public final Artifact putArtifactFromSyscall(Map<String, String> eventData, ArtifactIdentifier identifier){
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String source = AUDIT_SYSCALL_SOURCE;
		String operation = getOperation(SYSCALL.UPDATE);
		return artifactManager.putArtifact(time, eventId, operation, pid, source, identifier);
	}
	
	/**
	 * Standardized method to be called for putting an edge into the reporter's buffer.
	 * Adds the arguments to the edge with proper annotations. If any argument null then 
	 * that annotation isn't put to the edge.
	 * 
	 * @param edge edge to add annotations to and to put to reporter's buffer
	 * @param operation operation as gotten from {@link #getOperation(SYSCALL) getOperation}
	 * @param time time of the audit log which generated this edge
	 * @param eventId event id in the audit log which generated this edge
	 * @param source source of the edge
	 */
	public void putEdge(AbstractEdge edge, String operation, String time, String eventId, String source){
		if(edge != null && edge.getChildVertex() != null && edge.getParentVertex() != null){
			if(!globals.unixSockets && 
					(isUnixSocketArtifact(edge.getChildVertex()) ||
							isUnixSocketArtifact(edge.getParentVertex()))){
				return;
			}
			if(time != null){
				edge.addAnnotation(OPMConstants.EDGE_TIME, time);
			}
			if(eventId != null){
				edge.addAnnotation(OPMConstants.EDGE_EVENT_ID, eventId);
			}
			if(source != null){
				edge.addAnnotation(OPMConstants.SOURCE, source);
			}
			if(operation != null){
				edge.addAnnotation(OPMConstants.EDGE_OPERATION, operation);
			}
			putEdge(edge);
		}else{
			log(Level.WARNING, "Failed to put edge. edge = "+edge+", sourceVertex = "+(edge != null ? edge.getChildVertex() : null)+", "
					+ "destination vertex = "+(edge != null ? edge.getParentVertex() : null)+", operation = "+operation+", "
					+ "time = "+time+", eventId = "+eventId+", source = " + source, null, time, eventId, SYSCALL.valueOf(operation.toUpperCase()));
		}
	}
	
	private boolean isUnixSocketArtifact(AbstractVertex vertex){
		return vertex != null && 
				(OPMConstants.SUBTYPE_UNIX_SOCKET.equals(vertex.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE))
						|| OPMConstants.SUBTYPE_UNNAMED_UNIX_SOCKET_PAIR.equals(vertex.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE)));
	}
	
	private AddressPort parseNetworkSaddr(String saddr){
		// TODO the address = 0.0.0.0 and 127.0.0.1 issue! Rename or not?
		try{
			String address = null, port = null;
			if (isIPv4Saddr(saddr) && saddr.length() >= 17){
				port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
				int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
				int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
				int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
				int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
				address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
			}else if(isIPv6Saddr(saddr) && saddr.length() >= 49){
				port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
				String hextet1 = saddr.substring(16, 20);
				String hextet2 = saddr.substring(20, 24);
				String hextet3 = saddr.substring(24, 28);
				String hextet4 = saddr.substring(28, 32);
				String hextet5 = saddr.substring(32, 36);
				String hextet6 = saddr.substring(36, 40);
				String hextet7 = saddr.substring(40, 44);
				String hextet8 = saddr.substring(44, 48);
				address = String.format("%s:%s:%s:%s:%s:%s:%s:%s", hextet1, hextet2, hextet3, hextet4,
						hextet5, hextet6, hextet7, hextet8);
			}
			if(address != null && port != null){
				return new AddressPort(address, port);
			}
		}catch(Exception e){
			// Logged by the caller
		}
		return null;
	}
	
	private UnixSocketIdentifier parseUnixSaddr(String pid, String saddr){
		String path = "";
		int start = -1;
		//starting from 2 since first two characters are 01
		for(int a = 2;a <= saddr.length()-2; a+=2){
			if(saddr.substring(a,a+2).equals("00")){ //null char
				//continue until non-null found
				continue;
			}else{
				//first non-null char i.e. we are going to start from here
				start = a;
				break;
			}
		}
		
		if(start != -1){ //found
			try{
				for(; start <= saddr.length() - 2; start+=2){
					char c = (char)(Integer.parseInt(saddr.substring(start, start+2), 16));
					if(c == 0){ //null char
						break;
					}
					path += c;
				}
			}catch(Exception e){
				return null;
			}
		}

		// TODO handle unnamed unix socket. Need a new identifier to contain the tgid like for memory identifier.
		// Unnamed unix socket created through socketpair.
		// TODO need to handle socketpair syscall for that too.
		if(path != null && !path.isEmpty()){
			String rootFSPath = processManager.getRoot(pid);
			return new UnixSocketIdentifier(path, rootFSPath);
		}else{
			return null;
		}
	}

	

	private boolean isUdp(ArtifactIdentifier identifier){
		if(identifier != null && identifier.getClass().equals(NetworkSocketIdentifier.class)){
			if(PROTOCOL_NAME_UDP.equals(((NetworkSocketIdentifier)identifier).getProtocol())){
				return true;
			}
		}
		return false;
	}
	
	public static Integer getProtocolNumber(String protocolName){
		if(PROTOCOL_NAME_UDP.equals(protocolName)){
			return 17;
		}else if(PROTOCOL_NAME_TCP.equals(protocolName)){
			return 6;
		}else{
			return null;
		}
	}
	
	private static String getProtocolName(Integer protocolNumber){
		if(protocolNumber != null){
			if(protocolNumber == 17){
				return PROTOCOL_NAME_UDP;
			}else if(protocolNumber == 6){
				return PROTOCOL_NAME_TCP;
			}
		}
		return null;
	}
	

	private void logInvalidSaddr(String saddr, String time, String eventId, SYSCALL syscall){
		if(!"0100".equals(saddr)){ // if not empty path
			log(Level.INFO, "Failed to parse saddr: " + saddr, null, time, eventId, syscall);
		}
	}
	
	private String getProtocolNameBySockType(Integer sockType){
		if(sockType != null){
			if(platformConstants.isSocketTypeTCP(sockType)){
				return PROTOCOL_NAME_TCP;
			}else if(platformConstants.isSocketTypeUDP(sockType)){
				return PROTOCOL_NAME_UDP;
			}
		}
		return null;
	}
	
	/**
	 * Groups system call names by functionality and returns that name to simplify identification of the type of system call.
	 * Grouping only done if {@link #SIMPLIFY SIMPLIFY} is true otherwise the system call name is returned simply.
	 * 
	 * @param syscall system call to get operation for
	 * @return operation corresponding to the syscall
	 */
	public String getOperation(SYSCALL primary){
		return getOperation(primary, null);
	}
	
	private String getOperation(SYSCALL primary, SYSCALL secondary){
		return OPMConstants.getOperation(primary, secondary, SIMPLIFY);
	}

}

class AddressPort{
	public final String address, port;

	public AddressPort(String address, String port){
		this.address = address;
		this.port = port;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + ((port == null) ? 0 : port.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		AddressPort other = (AddressPort)obj;
		if(address == null){
			if(other.address != null)
				return false;
		}else if(!address.equals(other.address))
			return false;
		if(port == null){
			if(other.port != null)
				return false;
		}else if(!port.equals(other.port))
			return false;
		return true;
	}
}
