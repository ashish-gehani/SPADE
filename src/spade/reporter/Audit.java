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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.reporter.audit.ArtifactIdentifier;
import spade.reporter.audit.ArtifactProperties;
import spade.reporter.audit.AuditEventReader;
import spade.reporter.audit.BlockDeviceIdentifier;
import spade.reporter.audit.CharacterDeviceIdentifier;
import spade.reporter.audit.DirectoryIdentifier;
import spade.reporter.audit.FileIdentifier;
import spade.reporter.audit.IdentifierWithPath;
import spade.reporter.audit.LinkIdentifier;
import spade.reporter.audit.MemoryIdentifier;
import spade.reporter.audit.NamedPipeIdentifier;
import spade.reporter.audit.NetworkSocketIdentifier;
import spade.reporter.audit.OPMConstants;
import spade.reporter.audit.SYSCALL;
import spade.reporter.audit.UnixSocketIdentifier;
import spade.reporter.audit.UnknownIdentifier;
import spade.reporter.audit.UnnamedPipeIdentifier;
import spade.reporter.audit.process.ProcessManager;
import spade.reporter.audit.process.ProcessWithAgentManager;
import spade.reporter.audit.process.ProcessWithoutAgentManager;
import spade.utility.BerkeleyDB;
import spade.utility.CommonFunctions;
import spade.utility.Execute;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.utility.Hasher;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 * @author Dawood Tariq, Sharjeel Ahmed Qureshi
 */
public class Audit extends AbstractReporter {

	static final Logger logger = Logger.getLogger(Audit.class.getName());

	/********************** LINUX CONSTANTS - START *************************/
	
	//  Following constant values are taken from:
	//  http://lxr.free-electrons.com/source/include/uapi/linux/stat.h#L14
	private final int S_IFIFO = 0010000, S_IFREG = 0100000, S_IFSOCK = 0140000,
			S_IFLNK = 0120000, S_IFBLK = 0060000, S_IFDIR = 0040000,
			S_IFCHR = 0020000, S_IFMT = 00170000;

	//  Following constant values are taken from:
	//  http://lxr.free-electrons.com/source/include/uapi/linux/fcntl.h#L56
	private final int AT_FDCWD = -100;

	//  Following constant values are taken from:
	//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/fcntl.h#L19
	private final int O_RDONLY = 00000000, O_WRONLY = 00000001, O_RDWR = 00000002, 
			O_CREAT = 00000100, O_TRUNC = 00001000, O_APPEND = 00002000;
	
	//  Following constant values are taken from:
	//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/mman-common.h#L21
	private final int MAP_ANONYMOUS = 0x20;
	
	//  Following constant values are taken from:
	//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/fcntl.h#L99
	private final int F_LINUX_SPECIFIC_BASE = 1024, F_DUPFD = 0, F_SETFL = 4;
	//  Following constant values are taken from:
	//  http://lxr.free-electrons.com/source/include/uapi/linux/fcntl.h#L16
	private final int F_DUPFD_CLOEXEC = F_LINUX_SPECIFIC_BASE + 6;
	// Source of following: http://elixir.free-electrons.com/linux/latest/source/include/uapi/asm-generic/errno.h#L97
	private final int EINPROGRESS = -115;
	// Source: http://elixir.free-electrons.com/linux/latest/source/include/linux/net.h#L65
	private final int SOCK_STREAM = 1, SOCK_DGRAM = 2;
	/********************** LINUX CONSTANTS - END *************************/

	/********************** PROCESS STATE - START *************************/
	
	private ProcessManager processManager;

	/********************** PROCESS STATE - END *************************/
	
	/********************** ARITFACT STATE - START *************************/
	// Map for artifact infos to versions and bytes read/written on sockets 
	// Use cases:
	// 1) Track version
	// 2) Track epoch
	// 3) Avoid duplication of artifacts
	private ExternalMemoryMap<ArtifactIdentifier, ArtifactProperties> artifactIdentifierToArtifactProperties;
	//cache maps paths. global so that we delete on exit
	private String artifactsCacheDatabasePath;
	/********************** ARTIFACT STATE - END *************************/
	
	/********************** NETFILTER - START *************************/
	
	private final String[] iptablesRules = {
			"OUTPUT -p tcp -m state --state NEW -j AUDIT --type accept",
			"INPUT -p tcp -m state --state NEW -j AUDIT --type accept",
			"OUTPUT -p udp -m state --state NEW -j AUDIT --type accept",
			"INPUT -p udp -m state --state NEW -j AUDIT --type accept"
			};
	
	private int matchedNetfilterSyscall = 0,
			matchedSyscallNetfilter = 0;
	
	private List<Map<String, String>> networkAnnotationsFromSyscalls = 
			new ArrayList<Map<String, String>>();
	private List<Map<String, String>> networkAnnotationsFromNetfilter = 
			new ArrayList<Map<String, String>>();
	
	private Map<String, String> getNetworkAnnotationsSeenInList(
			List<Map<String, String>> list, String remoteAddress, String remotePort){
		for(int a = 0; a < list.size(); a++){
			Map<String, String> artifactAnnotation = list.get(a);
			if(String.valueOf(artifactAnnotation.get(OPMConstants.ARTIFACT_REMOTE_ADDRESS)).equals(remoteAddress) &&
					String.valueOf(artifactAnnotation.get(OPMConstants.ARTIFACT_REMOTE_PORT)).equals(remotePort)){
				return artifactAnnotation;
			}
		}
		return null;
	}
	
	/********************** NETFILTER - END *************************/

	/********************** BEHAVIOR FLAGS - START *************************/
	//Reporting variables
	private boolean reportingEnabled = false;
	private long reportEveryMs;
	private long lastReportedTime;
	
	private Boolean ARCH_32BIT = true;
	
	// These are the default values
	private boolean FAIL_FAST = true;
	private boolean USE_READ_WRITE = false;
	private boolean USE_SOCK_SEND_RCV = false;
	private boolean CREATE_BEEP_UNITS = false;
	private boolean SIMPLIFY = true;
	private boolean PROCFS = false;
	private boolean UNIX_SOCKETS = false;
	private boolean WAIT_FOR_LOG_END = true;
	private boolean AGENTS = false;
	private boolean CONTROL = true;
	private boolean USE_MEMORY_SYSCALLS = true;
	private String AUDITCTL_SYSCALL_SUCCESS_FLAG = "1";
	// Null  -> don't use i.e. the default behavior
	// True  -> override all other versioning flags and version everything
	// False -> override all other versioning flags and don't version anything 
	private Boolean VERSION_ARTIFACTS = null;
	private boolean VERSION_FILES = true,
			VERSION_MEMORYS = true,
			VERSION_NAMED_PIPES = true,
			VERSION_UNNAMED_PIPES = true,
			VERSION_UNKNOWNS = true,
			VERSION_NETWORK_SOCKETS = false,
			VERSION_UNIX_SOCKETS = true;
	private boolean KEEP_VERSIONS = true,
			KEEP_EPOCHS = true,
			KEEP_PATH_PERMISSIONS = true,
			KEEP_ARTIFACT_PROPERTIES_MAP = true;
	private boolean ANONYMOUS_MMAP = true;
	private boolean NETFILTER_RULES = false;
	private boolean REFINE_NET = false;
	private String ADD_KM_KEY = "localEndpoints";
	private boolean ADD_KM; // Default value set where flags are being initialized from arguments (unlike the variables above).
	private String HANDLE_KM_RECORDS_KEY = "handleLocalEndpoints";
	// Handle the flag below with care!
	private Boolean HANDLE_KM_RECORDS = null; // Default value set where flags are being initialized from arguments (unlike the variables above).
	/********************** BEHAVIOR FLAGS - END *************************/

	private String spadeAuditBridgeProcessPid = null;
	// true if live audit, false if log file. null not set.
	private Boolean isLiveAudit = null;
	// a flag to block on shutdown call if buffers are being emptied and events are still being read
	private volatile boolean eventReaderThreadRunning = false;
	
	private final long PID_MSG_WAIT_TIMEOUT = 1 * 1000;
	
	private final String AUDIT_SYSCALL_SOURCE = OPMConstants.SOURCE_AUDIT_SYSCALL;
	
	private final String kernelModulePath = "lib/kernel-modules/netio.ko";
	private final String kernelModuleControllerPath = "lib/kernel-modules/netio_controller.ko";
	
	private static final String PROTOCOL_NAME_UDP = "udp",
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
	 * Returns a map which contains all the keys and values defined 
	 * in the default config file. 
	 * 
	 * Returns empty map if failed to read the config file.
	 * 
	 * @return HashMap<String, String>
	 */
	private Map<String, String> readDefaultConfigMap(){
		Map<String, String> configMap = new HashMap<String, String>();
		try{
			Map<String, String> temp = FileUtility.readConfigFileAsKeyValueMap(
					Settings.getDefaultConfigFilePath(this.getClass()),
					"=");
			if(temp != null){
				configMap.putAll(temp);
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to read config", e);
		}
		return configMap;
	}
	
	/**
	 * Used only in case of live audit.
	 * Returns null if failed to get the arch value.
	 * Returns true if the arch is 32 bit and return false
	 * if the arch is 64 bit.
	 * 
	 * @return true/false/null
	 */
	private Boolean is32BitArch(){
		try {
			InputStream archStream = Runtime.getRuntime().exec("uname -i").getInputStream();
			BufferedReader archReader = new BufferedReader(new InputStreamReader(archStream));
			String archLine = archReader.readLine().trim();
			return archLine.equals("i686");
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error reading the system architecture", e);
			return null; //if unable to find out the architecture then report failure
		}
	}
	
	/**
	 * Initializes the reporting globals based on the argument
	 * 
	 * The argument is read from the config file
	 * 
	 * Returns true if the value in the config file was defined properly or not 
	 * defined. If the value in the config value is ill-defined then returns false.
	 * 
	 * @param reportingIntervalSeconds Interval time in seconds to report stats after
	 */
	private boolean initReporting(String reportingIntervalSeconds){
		Long reportingInterval = CommonFunctions.parseLong(reportingIntervalSeconds, null);
		if(reportingInterval != null){
			if(reportingInterval < 1){ //at least 1 ms
				logger.log(Level.INFO, "Statistics reporting turned off");
			}else{
				reportingEnabled = true;
				reportEveryMs = reportingInterval * 1000;
				lastReportedTime = System.currentTimeMillis();
			}
		}else if(reportingInterval == null && 
				(reportingIntervalSeconds != null && !reportingIntervalSeconds.isEmpty())){
			logger.log(Level.SEVERE, "Invalid value for reporting interval in the config file");
			return false;
		}
		return true;
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
	
	/**
	 * Initializes global boolean flags for this reporter
	 * 
	 * @param args a map made from arguments key-values
	 * @return true if all flags had valid values / false if any of the flags had a non-boolean value
	 */
	private boolean initFlagsFromArguments(Map<String, String> args){
		String argValue = args.get("failfast");
		if(isValidBoolean(argValue)){
			FAIL_FAST = parseBoolean(argValue, FAIL_FAST);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'failfast': " + argValue);
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
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'procFS': " + argValue);
			return false;
		}

		argValue = args.get("unixSockets");
		if(isValidBoolean(argValue)){
			UNIX_SOCKETS = parseBoolean(argValue, UNIX_SOCKETS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'unixSockets': " + argValue);
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
		
		argValue = args.get("versionNetworkSockets");
		if(isValidBoolean(argValue)){
			VERSION_NETWORK_SOCKETS = parseBoolean(argValue, VERSION_NETWORK_SOCKETS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versionNetworkSockets': " + argValue);
			return false;
		}
		
		argValue = args.get("versionFiles");
		if(isValidBoolean(argValue)){
			VERSION_FILES = parseBoolean(argValue, VERSION_FILES);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versionFiles': " + argValue);
			return false;
		}
		
		argValue = args.get("versionMemorys");
		if(isValidBoolean(argValue)){
			VERSION_MEMORYS = parseBoolean(argValue, VERSION_MEMORYS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versionMemorys': " + argValue);
			return false;
		}
		
		argValue = args.get("versionNamedPipes");
		if(isValidBoolean(argValue)){
			VERSION_NAMED_PIPES = parseBoolean(argValue, VERSION_NAMED_PIPES);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versionNamedPipes': " + argValue);
			return false;
		}
		
		argValue = args.get("versionUnnamedPipes");
		if(isValidBoolean(argValue)){
			VERSION_UNNAMED_PIPES = parseBoolean(argValue, VERSION_UNNAMED_PIPES);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versionUnnamedPipes': " + argValue);
			return false;
		}
		
		argValue = args.get("versionUnknowns");
		if(isValidBoolean(argValue)){
			VERSION_UNKNOWNS = parseBoolean(argValue, VERSION_UNKNOWNS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versionUnknowns': " + argValue);
			return false;
		}
		
		argValue = args.get("versionUnixSockets");
		if(isValidBoolean(argValue)){
			VERSION_UNIX_SOCKETS = parseBoolean(argValue, VERSION_UNIX_SOCKETS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versionUnixSockets': " + argValue);
			return false;
		}
		
		argValue = args.get("versionArtifacts");
		if(argValue == null){
			// continue. NULL is the default value
		}else{
			if(isValidBoolean(argValue)){
				VERSION_ARTIFACTS = parseBoolean(argValue, true);
			}else{
				logger.log(Level.SEVERE, "Invalid flag value for 'versionArtifacts': " + argValue);
				return false;
			}
		}
		
		argValue = args.get("versions");
		if(isValidBoolean(argValue)){
			KEEP_VERSIONS = parseBoolean(argValue, KEEP_VERSIONS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'versions': " + argValue);
			return false;
		}

		argValue = args.get("epochs");
		if(isValidBoolean(argValue)){
			KEEP_EPOCHS = parseBoolean(argValue, KEEP_EPOCHS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'epochs': " + argValue);
			return false;
		}
		
		argValue = args.get("permissions");
		if(isValidBoolean(argValue)){
			KEEP_PATH_PERMISSIONS = parseBoolean(argValue, KEEP_PATH_PERMISSIONS);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'permissions': " + argValue);
			return false;
		}
		
		if(!KEEP_VERSIONS && !KEEP_EPOCHS && !KEEP_PATH_PERMISSIONS){
			KEEP_ARTIFACT_PROPERTIES_MAP = false;
		}	
		
		// Ignore for now. Changing it now would break code in places.
		// Sucess always assumed to be '1' for now (default value)
		// TODO
//		if("0".equals(args.get("auditctlSuccessFlag"))){
//			AUDITCTL_SYSCALL_SUCCESS_FLAG = "0";
//		}
		
		argValue = args.get("anonymousMmap");
		if(isValidBoolean(argValue)){
			ANONYMOUS_MMAP = parseBoolean(argValue, ANONYMOUS_MMAP);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'anonymousMmap': " + argValue);
			return false;
		}
		
		argValue = args.get("netfilter");
		if(isValidBoolean(argValue)){
			NETFILTER_RULES = parseBoolean(argValue, NETFILTER_RULES);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'netfilter': " + argValue);
			return false;
		}
		
		argValue = args.get("refineNet");
		if(isValidBoolean(argValue)){
			REFINE_NET = parseBoolean(argValue, REFINE_NET);
		}else{
			logger.log(Level.SEVERE, "Invalid flag value for 'refineNet': " + argValue);
			return false;
		}
		
		// Setting default values here instead of where variables are defined because default values for KM vars depend
		// on whether the data is live or playback.
		boolean logPlayback = argsSpecifyLogPlayback(args);
		String addKmArgValue = args.get(ADD_KM_KEY);
		String handleKmArgValue = args.get(HANDLE_KM_RECORDS_KEY);
		if(logPlayback){ // Can't use isLiveAudit flag because not set yet.
			// default values
			ADD_KM = false; // Doesn't matter for log playback so always false.
			if("true".equals(handleKmArgValue)){
				HANDLE_KM_RECORDS = true;
			}else if("false".equals(handleKmArgValue)){
				HANDLE_KM_RECORDS = false;
			}else if(handleKmArgValue == null){
				HANDLE_KM_RECORDS = null; // To be decided by the first network related record
			}else{
				logger.log(Level.SEVERE, "Invalid flag value for '"+HANDLE_KM_RECORDS_KEY+"': " + argValue);
				return false;
			}
		}else{ // live audit
			// Default values
			ADD_KM = false;
			
			// Parsing the values for the KM vars after the default values have be set appropriately (see above)
			if(isValidBoolean(addKmArgValue)){
				ADD_KM = parseBoolean(addKmArgValue, ADD_KM);
			}else{
				logger.log(Level.SEVERE, "Invalid flag value for '"+ADD_KM_KEY+"': " + addKmArgValue);
				return false;
			}
			
			// If added modules then also must handle. If not added then cannot handle.
			HANDLE_KM_RECORDS = ADD_KM;
		}
		
		if((ADD_KM && NETFILTER_RULES) // both can't be true
				|| ((HANDLE_KM_RECORDS != null && HANDLE_KM_RECORDS) && REFINE_NET)){ // both can't be true
			logger.log(Level.SEVERE, "Incompatible flags value (Can only handle data from either module or iptables): "
					+ "netfilter={0}, refineNet={1}, {2}={3}, {4}={5}", 
					new Object[]{NETFILTER_RULES, REFINE_NET, ADD_KM_KEY,
							ADD_KM, HANDLE_KM_RECORDS_KEY, HANDLE_KM_RECORDS});
			return false;
		}else{
			if(ADD_KM && (HANDLE_KM_RECORDS != null && !HANDLE_KM_RECORDS)){
				logger.log(Level.SEVERE, "Must handle kernel module data if kernel module added.");
				return false;
			}else{
				// Logging only relevant flags now for debugging
				logger.log(Level.INFO, "Audit flags: {0}={1}, {2}={3}, {4}={5}, {6}={7}, {8}={9}, {10}={11}, {12}={13}, "
                           + "{14}={15}, {16}={17}, {18}={19}, {20}={21}, {22}={23}, {24}={25}",
						new Object[]{"syscall", args.get("syscall"), "fileIO", USE_READ_WRITE, "netIO", USE_SOCK_SEND_RCV, "units", CREATE_BEEP_UNITS,
								"unixSockets", UNIX_SOCKETS, "waitForLog", WAIT_FOR_LOG_END, "versions", KEEP_VERSIONS, 
								"epochs", KEEP_EPOCHS, "permissions", KEEP_PATH_PERMISSIONS, "netfilter", NETFILTER_RULES, 
								"refineNet", REFINE_NET, ADD_KM_KEY, ADD_KM, 
								HANDLE_KM_RECORDS_KEY, HANDLE_KM_RECORDS});
				return true;
			}
		}
	}
	
	/**
	 * Tries to setup the temp directory at the given path.
	 * 
	 * Returns true if it already exists or gets created successfully. 
	 * Else returns false
	 * 
	 * @param tempDirectoryPath path of the temp directory to create
	 * @return true/false
	 */
	private boolean setupTempDirectory(String tempDirectoryPath){
		if(FileUtility.fileExists(tempDirectoryPath)){
			return true;
		}else{
			try{
				new File(tempDirectoryPath).mkdir();
				return true;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to create temp directory. Defined in config.", e);
				return false;
			}
		}
	}
	
	/**
	 * Creates a temp file in the given tempDir from the list of audit log files
	 * 
	 * Returns the path of the temp file is that is created successfully
	 * Else returns null
	 * 
	 * @param spadeAuditBridgeBinaryName name of the spade audit bridge binary
	 * @param inputLogFiles list of audit log files (in the defined order)
	 * @param tempDirPath path of the temp dir
	 * @return path of the temp file which contains the paths of audit logs OR null
	 */
	private String createLogListFileForSpadeAuditBridge(String spadeAuditBridgeBinaryName, 
			List<String> inputLogFiles, String tempDirPath){
		try{
			String spadeAuditBridgeInputFilePath = tempDirPath + File.separatorChar + 
					spadeAuditBridgeBinaryName + "." + System.nanoTime();
			File spadeAuditBridgeInputFile = new File(spadeAuditBridgeInputFilePath);
			if(!spadeAuditBridgeInputFile.createNewFile()){
				logger.log(Level.SEVERE, "Failed to create input file list file for " + spadeAuditBridgeBinaryName);
				return null;
			}else{
				FileUtils.writeLines(spadeAuditBridgeInputFile, inputLogFiles);
				return spadeAuditBridgeInputFile.getAbsolutePath();
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create input file for " + spadeAuditBridgeBinaryName, e);
		}
		return null;
	}
	
	/**
	 * Returns a list of audit log files if the rotate flag is true.
	 * Else returns a list with only the given audit log file as it's element.
	 * 
	 * The audit log files are added in the convention defined in the function code.
	 * 
	 * @param inputAuditLogFilePath path of the audit log file
	 * @param rotate a flag to tell whether to read the rotated logs or not
	 * @return list if input log files
	 */
	private List<String> getListOfInputAuditLogs(String inputAuditLogFilePath, boolean rotate){
		// Build a list of audit log files to be read
		LinkedList<String> inputAuditLogFiles = new LinkedList<String>();
		inputAuditLogFiles.addFirst(inputAuditLogFilePath); //add the file in the argument
		if(rotate){ //if rotate is true then add the rest too based on the decided convention
			//convention: name format of files to be processed -> name.1, name.2 and so on where 
			//name is the name of the file passed in as argument
			//can only process 99 logs
			for(int logCount = 1; logCount<=99; logCount++){
				if(FileUtility.fileExists(inputAuditLogFilePath + "." + logCount)){
					inputAuditLogFiles.addFirst(inputAuditLogFilePath + "." + logCount); 
					//adding first so that they are added in the reverse order
				}
			}
		}
		return inputAuditLogFiles;
	}
	
	private void doCleanup(String rulesType, String logListFile){
		if(isLiveAudit){
			if(!"none".equals(rulesType)){
				removeAuditctlRules();
			}
			if(NETFILTER_RULES){
				removeIptablesRules(iptablesRules);
			}
			if(ADD_KM){
				removeNetworkKernelModule(kernelModuleControllerPath);
			}
		}else{
			if(FileUtility.fileExists(logListFile)){
				FileUtility.deleteFile(logListFile);
			}
		}
		if(KEEP_ARTIFACT_PROPERTIES_MAP){
			deleteCacheMaps();
		}
	}
	
	private boolean argsSpecifyLogPlayback(Map<String, String> args){
		return args.containsKey("inputDir") || args.containsKey("inputLog");
	}
	
	@Override
	public boolean launch(String arguments) {
		String spadeAuditBridgeBinaryName = null;
		String spadeAuditBridgeBinaryPath = null;
		String outputLogFilePath = null;
		long recordsToRotateOutputLogAfter = 0;
		String spadeAuditBridgeCommand = null;
		String rulesType = null;
		String logListFile = null;
		
		Map<String, String> argsMap = CommonFunctions.parseKeyValPairs(arguments);
		Map<String, String> configMap = readDefaultConfigMap();
		
		// Init reporting globals
		if(!initReporting(configMap.get("reportingIntervalSeconds"))){
			return false;
		}

		// Get path of spadeAuditBridge binary from the config file
		spadeAuditBridgeBinaryPath = configMap.get("spadeAuditBridge");		
		if(!FileUtility.fileExists(spadeAuditBridgeBinaryPath)){
			logger.log(Level.SEVERE, "Must specify a valid 'spadeAuditBridge' key in config");
			return false;
		}
		
		String spadeAuditBridgeBinaryPathArray[] = spadeAuditBridgeBinaryPath.split(File.separator);
		spadeAuditBridgeBinaryName = spadeAuditBridgeBinaryPathArray[spadeAuditBridgeBinaryPathArray.length - 1];
		
		// Init boolean flags from the arguments
		if(!initFlagsFromArguments(argsMap)){
			return false;
		}
		
		if(AGENTS){
			processManager = new ProcessWithoutAgentManager(this, SIMPLIFY, CREATE_BEEP_UNITS);
		}else{
			processManager = new ProcessWithAgentManager(this, SIMPLIFY, CREATE_BEEP_UNITS);
		}
		
		// Check if the outputLog argument is valid or not
		outputLogFilePath = argsMap.get("outputLog");
		if(outputLogFilePath != null){
			if(!FileUtility.fileCanBeCreated(outputLogFilePath)){
				logger.log(Level.SEVERE, "Invalid path for 'outputLog' : " + outputLogFilePath);
				return false;
			}else{
				
				String recordsToRotateOutputLogAfterArgument = argsMap.get("outputLogRotate");
				if(recordsToRotateOutputLogAfterArgument != null){
					Long parsedOutputLogRotate = CommonFunctions.parseLong(recordsToRotateOutputLogAfterArgument, null);
					if(parsedOutputLogRotate == null){
						logger.log(Level.SEVERE, "Invalid value for 'outputLogRotate': "+ recordsToRotateOutputLogAfterArgument);
						return false;
					}else{
						recordsToRotateOutputLogAfter = parsedOutputLogRotate;
					}
				}
				
			}
		}

		String inputLogDirectoryArgument = argsMap.get("inputDir");
		String inputAuditLogFileArgument = argsMap.get("inputLog");
		if(argsSpecifyLogPlayback(argsMap)){
			// is log playback
			isLiveAudit = false;
			
			// In case of audit log files get arch from the arguments
			ARCH_32BIT = null;
			if("32".equals(argsMap.get("arch"))){
				ARCH_32BIT = true;
			}else if("64".equals(argsMap.get("arch"))){
				ARCH_32BIT = false;
			}else{
				logger.log(Level.SEVERE, "Must specify whether the system on which log was collected was 32 bit or 64 bit");
				return false;
			}
			
			if(inputAuditLogFileArgument != null){
			
				if(!FileUtility.fileExists(inputAuditLogFileArgument)){
					logger.log(Level.SEVERE, "Input audit log file at specified path doesn't exist : " 
										+ inputAuditLogFileArgument);
					return false;
				}
	
				// Whether to read from rotated logs or not
				boolean rotate = false;
				String rotateArgument = argsMap.get("rotate");
				if(isValidBoolean(rotateArgument)){
					rotate = parseBoolean(rotateArgument, false);
				}else{
					logger.log(Level.SEVERE, "Invalid value for 'rotate' flag: "+ rotateArgument);
					return false;
				}
	
				List<String> inputAuditLogFiles = getListOfInputAuditLogs(inputAuditLogFileArgument, rotate);
	
				logger.log(Level.INFO, "Total logs to process: " + inputAuditLogFiles.size() + " and list = " + inputAuditLogFiles);
	
				// Only needed in case of audit log files and not in case of live audit
				String tempDirPath = configMap.get("tempDir");
				if(!setupTempDirectory(tempDirPath)){
					return false;
				}
				// Create the input file for spadeAuditBridge to read the audit logs from 
				logListFile = createLogListFileForSpadeAuditBridge(spadeAuditBridgeBinaryName, inputAuditLogFiles, tempDirPath);
				if(logListFile == null){
					return false;
				}
				
				// Build the command to use
				spadeAuditBridgeCommand = spadeAuditBridgeBinaryPath + 
								((CREATE_BEEP_UNITS) ? " -u" : "") + 
								((WAIT_FOR_LOG_END) ? " -w" : "") + 
								" -f " + logListFile;
			}else{
				// Input log directory section
				
				try{
					File dir = new File(inputLogDirectoryArgument);
					
					if(dir.exists() && dir.isDirectory()){
						
						// Check if logs exist
						if(dir.list().length != 0){
							
							// Confirm timestamp
							String inputLogTimeArgument = argsMap.get("inputTime");
							if(inputLogTimeArgument != null){
								try{
									SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ss");
									dateFormat.parse(inputLogTimeArgument);
									// parsed successfully
								}catch(Exception e){
									logger.log(Level.SEVERE, "Invalid time format for argument 'inputTime'. "
											+ "Expected: yyyy-MM-dd:HH:mm:ss" , e);
									return false;
								}
							}
							
							// Build the command to use
							spadeAuditBridgeCommand = spadeAuditBridgeBinaryPath + 
											((CREATE_BEEP_UNITS) ? " -u" : "") + 
											((WAIT_FOR_LOG_END) ? " -w" : "") + 
											" -d " + inputLogDirectoryArgument +
											((inputLogTimeArgument != null) ? " -t " + inputLogTimeArgument : "");
							
						}else{
							logger.log(Level.SEVERE, "No log file in 'inputDir' to process");
							return false;
						}
						
					}else{
						logger.log(Level.SEVERE, "Path for 'inputDir' doesn't exist or isn't a directory");
						return false;
					}
					
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to process 'inputDir' argument", e);
					return false;
				}
				
			}

		}else{ // live audit

			isLiveAudit = true;
			
			ARCH_32BIT = is32BitArch();
			if(ARCH_32BIT == null){
				return false;
			}
			
			//valid values: null (i.e. default), 'none' no rules, 'all' an audit rule with all system calls
			rulesType = argsMap.get("syscall");
			if(rulesType != null && !rulesType.equals("none") && !rulesType.equals("all")){
				logger.log(Level.SEVERE, "Invalid value for 'rules' argument: " + rulesType);
				return false;
			}
			
			spadeAuditBridgeCommand = spadeAuditBridgeBinaryPath + 
					((CREATE_BEEP_UNITS) ? " -u" : "") + 
					// Don't use WAIT_FOR_LOG_END here because the interrupt would be ignored by spadeAuditBridge then
					" -s " + "/var/run/audispd_events";
			
		}
		
		// used to identify failure and do cleanup.
		boolean success = true;
		
		// Initialize cache data structures
		if(KEEP_ARTIFACT_PROPERTIES_MAP){
			if(!initCacheMaps(configMap)){
				success = false;
			}
		}
		
		if(success){
			if(isLiveAudit){
				// if live audit and no km but handling records then error
				if(!ADD_KM && HANDLE_KM_RECORDS){ // in case of live audit HANDLE_KM_RECORDS will never be null
					logger.log(Level.SEVERE, "Can't handle kernel module data without kernel module added for Live Audit");
					success = false;
				}
			}
		}
		
		if(success){
			try{
				
				java.lang.Process spadeAuditBridgeProcess = runSpadeAuditBridge(spadeAuditBridgeCommand);
				
				Thread errorReaderThread = getErrorStreamReaderForProcess(spadeAuditBridgeBinaryName, 
						spadeAuditBridgeProcess.getErrorStream());
				errorReaderThread.start();
				
				AuditEventReader auditEventReader = getAuditEventReader(spadeAuditBridgeCommand,
						spadeAuditBridgeProcess.getInputStream(), outputLogFilePath,
						recordsToRotateOutputLogAfter);
				
				Thread auditEventReaderThread = getAuditEventReaderThread(spadeAuditBridgeBinaryName, 
						auditEventReader, 
						isLiveAudit, rulesType, logListFile);
				auditEventReaderThread.start();
				
				try{ Thread.sleep(PID_MSG_WAIT_TIMEOUT); }catch(Exception e){}
				
				if(spadeAuditBridgeProcessPid == null){
					// still didn't get the pid that means the process didn't start successfully
					logger.log(Level.SEVERE, "Process didn't start successfully");
					success = false;
				}
				
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to start Audit", e);
				success = false;
			}
		}
		
		if(success){
			if(isLiveAudit){
				
				if(success){
					if(ADD_KM || NETFILTER_RULES || rulesType == null || rulesType.equals("all")){
						String uid = null;
						boolean ignoreUid; // if true then exclude the user else only include the given user
						String argsUsername = argsMap.get("user");
						if(argsUsername == null){
							ignoreUid = true;
							uid = getOwnUid();
						}else{
							ignoreUid = false;
							uid = checkIfValidUsername(argsUsername);
						}
						if(uid != null){
							String ignoreProcesses = "auditd kauditd audispd " + spadeAuditBridgeBinaryName;
							List<String> pidsToIgnore = listOfPidsToIgnore(ignoreProcesses);
							if(pidsToIgnore != null){
								List<String> ppidsToIgnore = new ArrayList<String>(pidsToIgnore); // same as pids
								List<String> pidsToIgnoreFromConfig = getPidsFromConfig(configMap, "ignoreProcesses");
								List<String> ppidsToIgnoreFromConfig = getPidsFromConfig(configMap, "ignoreParentProcesses");
								if(pidsToIgnoreFromConfig != null){ // optional
									pidsToIgnore.addAll(pidsToIgnoreFromConfig);
									logger.log(Level.INFO, "Ignoring pids from config {0} for processes with names: {1}",
											new Object[]{pidsToIgnoreFromConfig, configMap.get("ignoreProcesses")});
								}
								if(ppidsToIgnoreFromConfig != null){ // optional
									ppidsToIgnore.addAll(ppidsToIgnoreFromConfig);
									logger.log(Level.INFO, "Ignoring ppids from config {0} for processes with names: {1}",
											new Object[]{ppidsToIgnoreFromConfig, configMap.get("ignoreParentProcesses")});
								}
								
								if(ADD_KM){
									success = addNetworkKernelModule(kernelModulePath, kernelModuleControllerPath, 
											uid, ignoreUid, pidsToIgnore, ppidsToIgnore, USE_SOCK_SEND_RCV);
								}
								if(success){
									if(NETFILTER_RULES){
										success = setIptablesRules(iptablesRules);
									}
									if(success){
										success = setAuditControlRules(rulesType, uid, ignoreUid, pidsToIgnore, 
												ppidsToIgnore, ADD_KM);
									}
								}
							}else{
								success = false;
							}
						}else{
							success = false;
						}
					}
				}
			}
		}
		
		if(success){
			return true;
		}else{
			// The spadeAuditBridge might have started
			if(spadeAuditBridgeProcessPid != null){
				sendSignalToPid(spadeAuditBridgeProcessPid, "2");
			}
			doCleanup(rulesType, logListFile);
			return false;
		}
	}
	
	private List<String> getPidsFromConfig(Map<String, String> configMap, String processNamesKey){
		if(configMap != null && processNamesKey != null){
			String processNames = configMap.get(processNamesKey);
			if(processNames != null){
				processNames = processNames.trim();
				if(!processNames.isEmpty()){
					// The value is comma-separated. Replacing ',' with ' ' because that is the format
					// expected by the 'pidof' command.
					processNames = processNames.replace(',', ' ');
					List<String> pids = listOfPidsToIgnore(processNames); // Can return null;
					return pids;
				}
			}
		}
		return null;
	}
	
	private AuditEventReader getAuditEventReader(String spadeAuditBridgeCommand, 
			InputStream stdoutStream,
			String outputLogFilePath,
			Long recordsToRotateOutputLogAfter){
		
		try{
			// Create the audit event reader using the STDOUT of the spadeAuditBridge process
			AuditEventReader auditEventReader = new AuditEventReader(spadeAuditBridgeCommand, 
					stdoutStream, FAIL_FAST);
			if(outputLogFilePath != null){
				auditEventReader.setOutputLog(outputLogFilePath, recordsToRotateOutputLogAfter);
			}
			return auditEventReader;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create audit event reader", e);
			return null;
		}
	}
	
	private boolean sendSignalToPid(String pid, String signal){
		try{
			Runtime.getRuntime().exec("kill -" + signal + " " + pid);
			return true;
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to send signal '"+signal+"' to pid '"+pid+"'", e);
			return false;
		}
	}
	
	private Thread getErrorStreamReaderForProcess(final String processName, final InputStream errorStream){
		try{
			
			Thread errorStreamReaderThread = new Thread(new Runnable(){
				public void run(){
					BufferedReader errorStreamReader = null;
					try{
						errorStreamReader = new BufferedReader(new InputStreamReader(errorStream));
						String line = null;
						while((line = errorStreamReader.readLine()) != null){
							if(line.startsWith("#CONTROL_MSG#")){
								spadeAuditBridgeProcessPid = line.split("=")[1];
							}else{
								logger.log(Level.INFO, processName + " output: " + line);
							}
						}
					}catch(Exception e){
						logger.log(Level.WARNING, "Failed to read error stream for process: " + processName);
					}finally{
						if(errorStreamReader != null){
							try{
								errorStreamReader.close();
							}catch(Exception e){
								//ignore
							}
						}
					}
					logger.log(Level.INFO, "Exiting error reader thread for process: " + processName);
				}
			});
			
			return errorStreamReaderThread;
			
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create error reader thread for " + processName, e);
			return null;
		}
	}
	
	private Thread getAuditEventReaderThread(final String processName, final AuditEventReader auditEventReader, 
			final boolean isLiveAudit, final String rulesType, final String logListFile){
		Runnable runnable = new Runnable() {
			
			@Override
			public void run() {
				
				eventReaderThreadRunning = true;
				
				if(isLiveAudit){
					if(PROCFS){
						processManager.putProcessesFromProcFs();
					}
				}
				
				try{
					Map<String, String> eventData = null;
					while((eventData = auditEventReader.readEventData()) != null){
						finishEvent(eventData);
					}
				}catch(Exception e){
					logger.log(Level.WARNING, "Stopped reading event stream. ", e);
				}finally{
					try{
						if(auditEventReader != null){
							auditEventReader.close();
						}
					}catch(Exception e){
						logger.log(Level.WARNING, "Failed to close audit event reader", e);
					}
				}
				
				// Sent a signal to the process in shutdown to stop reading.
				// That's why here.
				doCleanup(rulesType, logListFile);
				logger.log(Level.INFO, "Exiting event reader thread for process: " + processName);
				eventReaderThreadRunning = false;
			}
		};
		return new Thread(runnable);
	}

	private java.lang.Process runSpadeAuditBridge(String command){
		try{
			java.lang.Process spadeAuditBridgeProcess = Runtime.getRuntime().exec(command);
			logger.log(Level.INFO, "Succesfully executed the command: '" + command + "'");
			return spadeAuditBridgeProcess;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to execute command: '" + command + "'", e);
			return null;
		}
	}
		
	private boolean setIptablesRules(String[] iptablesRules){
		try{
			for(String iptablesRule : iptablesRules){
				String executeCommand = "iptables -I " + iptablesRule;
				Execute.Output output = Execute.getOutput(executeCommand);
				output.log();
				if(output.exitValueIndicatesError()){
					return false;
				}
			}
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to set iptable rules", e);
			return false;
		}
	}
	
	private boolean removeIptablesRules(String [] iptablesRules){
		try{
			boolean allRemoved = true;
			for(String iptablesRule : iptablesRules){
				String executeCommand = "iptables -D " + iptablesRule;
				Execute.Output output = Execute.getOutput(executeCommand);
				output.log();
				allRemoved = allRemoved || !output.exitValueIndicatesError();
			}
			return allRemoved;
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to remove iptables rule(s). Remove manually.", e);
			return false;
		}
	}
	
	private Boolean kernelModuleExists(String kernelModuleName){
		try{
			Execute.Output output = Execute.getOutput("lsmod");
			if(output.exitValueIndicatesError()){
				output.log();
				return null;
			}else{
				List<String> stdOutLines = output.getStdOut();
				for(String line : stdOutLines){
					String[] tokens = line.split("\\s+");
					if(tokens[0].equals(kernelModuleName)){
						return true;
					}
				}
				return false;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to check if module '"+kernelModuleName+"' exists", e);
			return null;
		}
	}
	
	private String getKernelModuleName(String kernelModulePath){
		if(kernelModulePath != null){
			try{
				String tokens[] = kernelModulePath.split("/");
				String name = tokens[tokens.length - 1];
				tokens = name.split("\\.");
				name = tokens[0];
				return name;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to get module name for: "+ kernelModulePath, e);
				return null;
			}
		}else{
			return null;
		}
	}
	
	private boolean addKernelModule(String command){
		try{
			Execute.Output output = Execute.getOutput(command);
			output.log();
			return !output.exitValueIndicatesError();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to add kernel module with command: " + command, e);
			return false;
		}
	}
	
	private boolean addNetworkKernelModule(String kernelModulePath, String kernelModuleControllerPath, 
			String uid, boolean ignoreUid, List<String> ignorePids, List<String> ignorePpids, boolean interceptSendRecv){
		if(uid == null || uid.isEmpty() || ignorePids == null || ignorePids.isEmpty()
				|| ignorePpids == null || ignorePpids.isEmpty()){
			logger.log(Level.SEVERE, "Invalid args. uid={0}, pids={1}, ppids={2}", new Object[]{uid, ignorePids, ignorePpids});
			return false;
		}else{
			if(!FileUtility.fileExists(kernelModulePath)){
				logger.log(Level.SEVERE, "Missing kernel module at: " + kernelModulePath);
				return false;
			}else if(!FileUtility.fileExists(kernelModuleControllerPath)){
				logger.log(Level.SEVERE, "Missing kernel module at: " + kernelModuleControllerPath);
				return false;
			}else{ // both exist
				String kernelModuleName = getKernelModuleName(kernelModulePath);
				if(kernelModuleName != null){
					String kernelModuleControllerName = getKernelModuleName(kernelModuleControllerPath);
					if(kernelModuleControllerName != null){
						Boolean kernelModuleControllerExists = kernelModuleExists(kernelModuleControllerName);
						if(kernelModuleControllerExists != null){
							if(kernelModuleControllerExists){
								logger.log(Level.SEVERE, "Kernel module controller '"+kernelModuleControllerPath+"' "
										+ "already exists.");
								return false;
							}else{
								Boolean kernelModuleExists = kernelModuleExists(kernelModuleName);
								if(kernelModuleExists != null){
									if(kernelModuleExists == false){
										// add the main kernel module
										String kernelModuleAddCommand = "insmod " + kernelModulePath;
										if(!addKernelModule(kernelModuleAddCommand)){
											return false;
										}
									}
									// add the controller kernel module
									StringBuffer pids = new StringBuffer();
									ignorePids.forEach(ignorePid -> {pids.append(ignorePid).append(",");});
									pids.deleteCharAt(pids.length() - 1);// delete trailing comma
									
									StringBuffer ppids = new StringBuffer();
									ignorePpids.forEach(ignorePpid -> {ppids.append(ignorePpid).append(",");});
									ppids.deleteCharAt(ppids.length() - 1);// delete trailing comma
									
									String ignoreUidsArg = ignoreUid ? "1" : "0"; // 0 is capture
									
									String kernelModuleControllerAddCommand = 
											String.format("insmod %s uids=\"%s\" syscall_success=\"1\" "
											+ "pids_ignore=\"%s\" ppids_ignore=\"%s\" net_io=\"%s\" "
											+ "ignore_uids=\"%s\"", 
											kernelModuleControllerPath, uid, pids, ppids,
											interceptSendRecv ? "1" : "0", ignoreUidsArg);
									
									if(!addKernelModule(kernelModuleControllerAddCommand)){
										return false;
									}else{
										return true;
									}
								}
							}
						}
					}
				}
			}
		}
		return false;
	}
	
	private boolean removeNetworkKernelModule(String kernelModuleControllerPath){
		if(FileUtility.fileExists(kernelModuleControllerPath)){
			String kernelModuleControllerName = getKernelModuleName(kernelModuleControllerPath);
			if(kernelModuleControllerName != null){
				Boolean kernelModuleControllerExists = kernelModuleExists(kernelModuleControllerName);
				if(kernelModuleControllerExists != null){
					if(kernelModuleControllerExists){
						// remove
						String command = "rmmod " + kernelModuleControllerName;
						try{
							Execute.Output output = Execute.getOutput(command);
							output.log();
							return !output.exitValueIndicatesError();
						}catch(Exception e){
							logger.log(Level.SEVERE, "Failed to remove kernel module with command: " + command, e);
							return false;
						}
					}
				}
			}
		}else{
			logger.log(Level.WARNING, "No module at path: " + kernelModuleControllerPath);
			return false;
		}
		return false;
	}
	
	private boolean setAuditControlRules(String rulesType, String uid, boolean ignoreUid, List<String> ignorePids, 
			List<String> ignorePpids, boolean kmAdded){
		try {

			if(uid == null || uid.isEmpty() || ignorePids == null || ignorePids.isEmpty()
					|| ignorePpids == null || ignorePpids.isEmpty()){
				logger.log(Level.SEVERE, "Invalid args. uid={0}, pids={1}, ppids={2}", new Object[]{uid, ignorePids,
						ignorePpids});
				return false;
			}
			
			if("none".equals(rulesType)){
				// do nothing
				return true;
			}else{
				
				// Remove any existing audit rules
				if(!removeAuditctlRules()){
					return false;
				}
				
				// Set arch to use in the rules
				String archField = "";
				if (ARCH_32BIT){
					archField = "-F arch=b32 ";
				}else{
					archField = "-F arch=b64 ";
				}
				
				String uidField = null;
				if(ignoreUid){ // ignore the given uid
					uidField = "-F uid!=" + uid + " ";
				}else{ // only capture the given uid
					uidField = "-F uid=" + uid + " ";
				}

				StringBuffer pidFields = new StringBuffer();
				ignorePids.forEach(ignorePid -> {pidFields.append("-F pid!=").append(ignorePid).append(" ");});
				
				StringBuffer ppidFields = new StringBuffer();
				ignorePpids.forEach(ignorePpid -> {ppidFields.append("-F ppid!=").append(ignorePpid).append(" ");});
				
				String pidAndPpidFields = pidFields.toString() + ppidFields.toString();
				
				List<String> auditRules = new ArrayList<String>();

				if("all".equals(rulesType)){

					String netIONeverSyscallsRule = null;
					if(kmAdded){
						netIONeverSyscallsRule = "auditctl -a exit,never ";
						netIONeverSyscallsRule += archField;
						netIONeverSyscallsRule += "-S socket -S bind -S accept -S accept4 -S connect ";
						netIONeverSyscallsRule += "-S sendmsg -S sendto -S recvmsg -S recvfrom -S sendmmsg -S recvmmsg ";
					}
					
                    String specialSyscallsRule = "auditctl -a exit,always ";
					String allSyscallsAuditRule = "auditctl -a exit,always ";

					allSyscallsAuditRule += archField;
					specialSyscallsRule += archField;
					
					allSyscallsAuditRule += "-S all ";
					// The connect syscall rule won't be matched even if module added because the 'never' rule is before this one
					specialSyscallsRule += "-S exit -S exit_group -S kill -S connect ";

					allSyscallsAuditRule += uidField;
					specialSyscallsRule += uidField;

					allSyscallsAuditRule += "-F success=" + AUDITCTL_SYSCALL_SUCCESS_FLAG + " ";
					
					// THE NEVER RULE SHOULD ALWAYS BE THE FIRST IF IT IS INITIALIZED
					if(kmAdded && netIONeverSyscallsRule != null){
						auditRules.add(netIONeverSyscallsRule);
					}
					auditRules.add(specialSyscallsRule + pidAndPpidFields);
					auditRules.add(allSyscallsAuditRule + pidAndPpidFields);

				}else if(rulesType == null){

					String auditRuleWithoutSuccess = "auditctl -a exit,always ";
					String auditRuleWithSuccess = "auditctl -a exit,always ";

					auditRuleWithSuccess += archField;
					auditRuleWithoutSuccess += archField;

					auditRuleWithSuccess += uidField;
					auditRuleWithoutSuccess += uidField;

					auditRuleWithoutSuccess += "-S kill -S exit -S exit_group ";
					if(!kmAdded){
						auditRuleWithoutSuccess += "-S connect ";
					}

					if (USE_READ_WRITE) {
						auditRuleWithSuccess += "-S read -S readv -S pread -S preadv -S write -S writev -S pwrite -S pwritev ";
					}
					if(!kmAdded){ // since km not added we don't need to log these syscalls
						if (USE_SOCK_SEND_RCV) {
							auditRuleWithSuccess += "-S sendto -S recvfrom -S sendmsg -S recvmsg ";
						}
						auditRuleWithSuccess += "-S bind -S accept -S accept4 -S socket ";
					}
					if (USE_MEMORY_SYSCALLS) {
						auditRuleWithSuccess += "-S mmap -S mprotect ";
						if(ARCH_32BIT){
							auditRuleWithSuccess += "-S mmap2 ";
						}
					}
					auditRuleWithSuccess += "-S unlink -S unlinkat ";
					auditRuleWithSuccess += "-S link -S linkat -S symlink -S symlinkat ";
					auditRuleWithSuccess += "-S clone -S fork -S vfork -S execve ";
					auditRuleWithSuccess += "-S open -S close -S creat -S openat -S mknodat -S mknod ";
					auditRuleWithSuccess += "-S dup -S dup2 -S dup3 ";
					auditRuleWithSuccess += "-S fcntl ";
					auditRuleWithSuccess += "-S rename -S renameat ";
					auditRuleWithSuccess += "-S setuid -S setreuid ";
					auditRuleWithSuccess += "-S setgid -S setregid ";
					if(!SIMPLIFY){
						auditRuleWithSuccess += "-S setresuid -S setfsuid ";
						auditRuleWithSuccess += "-S setresgid -S setfsgid ";
					}
					auditRuleWithSuccess += "-S chmod -S fchmod -S fchmodat ";
					auditRuleWithSuccess += "-S pipe -S pipe2 ";
					auditRuleWithSuccess += "-S truncate -S ftruncate ";
					auditRuleWithSuccess += "-S init_module -S finit_module ";
					auditRuleWithSuccess += "-S tee -S splice -S vmsplice ";
					auditRuleWithSuccess += "-F success=" + AUDITCTL_SYSCALL_SUCCESS_FLAG + " ";

					auditRules.add(auditRuleWithoutSuccess + pidAndPpidFields);
					auditRules.add(auditRuleWithSuccess + pidAndPpidFields);

				}else{
					logger.log(Level.SEVERE, "Invalid rules arguments: " + rulesType);
					return false;
				}

				// Execute in provided order!
				for(String auditRule : auditRules){
					if(!executeAuditctlRule(auditRule)){
						removeAuditctlRules();
						return false;
					}
				}
			}

			return true;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error configuring audit rules", e);
			return false;
		}

	}

	private void deleteCacheMaps(){
		//Close the external store and then delete the folder
		try{
			if(artifactIdentifierToArtifactProperties != null){
				artifactIdentifierToArtifactProperties.close();
				artifactIdentifierToArtifactProperties = null;
			}
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
		}
		try{
			File artifactsCacheDatabaseDirectoryFile = new File(artifactsCacheDatabasePath);
			if(artifactsCacheDatabasePath != null && artifactsCacheDatabaseDirectoryFile.exists()){
				try{
					BigDecimal size = new BigDecimal(FileUtils.sizeOfDirectoryAsBigInteger(artifactsCacheDatabaseDirectoryFile));
					size = size.divide(new BigDecimal("1024")); //KB
					size = size.divide(new BigDecimal("1024")); //MB
					size = size.divide(new BigDecimal("1024")); //GB
					logger.log(Level.INFO, "Size of the artifacts properties map on disk: {0} GB", size.doubleValue());
				}catch(Exception e){
					logger.log(Level.INFO, "Failed to log the size of the artifacts properties map on disk", e);
				}
				FileUtils.forceDelete(artifactsCacheDatabaseDirectoryFile);
				artifactsCacheDatabasePath = null;
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to delete cache maps at path: '"+artifactsCacheDatabasePath+"'");
		}
	}

	private boolean executeAuditctlRule(String auditctlRule){
		try{
			Execute.Output output = Execute.getOutput(auditctlRule);
			output.log();
			return !output.exitValueIndicatesError();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to set audit rule: " + auditctlRule, e);
			return false;
		}
	}

	private boolean removeAuditctlRules(){
		try{
			Execute.Output output = Execute.getOutput("auditctl -D");
			output.log();
			return !output.exitValueIndicatesError();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to remove audit rules", e);
			return false;
		}
	}

	private boolean initCacheMaps(Map<String, String> configMap){
		try{
			long currentTime = System.currentTimeMillis(); 
			artifactsCacheDatabasePath = configMap.get("tempDir") + File.separatorChar + "artifacts_" + currentTime;
			try{
				FileUtils.forceMkdir(new File(artifactsCacheDatabasePath));
				FileUtils.forceDeleteOnExit(new File(artifactsCacheDatabasePath));
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to create cache database directories", e);
				return false;
			}

			try{
				Integer artifactsCacheSize = CommonFunctions.parseInt(configMap.get("artifactsCacheSize"), null);
				String artifactsDatabaseName = configMap.get("artifactsDatabaseName");
				Double artifactsFalsePositiveProbability = CommonFunctions.parseDouble(configMap.get("artifactsBloomfilterFalsePositiveProbability"), null);
				Integer artifactsExpectedNumberOfElements = CommonFunctions.parseInt(configMap.get("artifactsBloomFilterExpectedNumberOfElements"), null);

				logger.log(Level.INFO, "Audit cache properties: artifactsCacheSize={0}, artifactsDatabaseName={1}, artifactsBloomfilterFalsePositiveProbability={2}, "
						+ "artifactsBloomFilterExpectedNumberOfElements={3}", new Object[]{artifactsCacheSize, 
								artifactsDatabaseName, artifactsFalsePositiveProbability, artifactsExpectedNumberOfElements});

				if(artifactsCacheSize == null || artifactsDatabaseName == null || 
						artifactsFalsePositiveProbability == null || artifactsExpectedNumberOfElements == null){
					logger.log(Level.SEVERE, "Undefined cache properties in Audit config");
					return false;
				}

				artifactIdentifierToArtifactProperties = 
						new ExternalMemoryMap<ArtifactIdentifier, ArtifactProperties>(artifactsCacheSize, 
								new BerkeleyDB<ArtifactProperties>(artifactsCacheDatabasePath, artifactsDatabaseName), 
								artifactsFalsePositiveProbability, artifactsExpectedNumberOfElements);
								
				artifactIdentifierToArtifactProperties.setKeyHashFunction(new Hasher<ArtifactIdentifier>() {
				
					@Override
					public String getHash(ArtifactIdentifier t) {
						if(t != null){
							Map<String, String> annotations = t.getAnnotationsMap();
							String subtype = t.getSubtype();
							String stringToHash = String.valueOf(annotations) + "," + String.valueOf(subtype);
							return DigestUtils.sha256Hex(stringToHash);
						}else{
							return DigestUtils.sha256Hex("(null)");
						}
					}
				});
				
				Long externalMemoryMapReportingIntervalSeconds = 
						CommonFunctions.parseLong(configMap.get("externalMemoryMapReportingIntervalSeconds"), -1L);
				
				if(externalMemoryMapReportingIntervalSeconds > 0){
					artifactIdentifierToArtifactProperties.printStats(externalMemoryMapReportingIntervalSeconds * 1000); 
					//convert to millis
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to initialize necessary data structures", e);
				return false;
			}

		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read default config file", e);
			return false;
		}
		return true;
	}

	private List<String> listOfPidsToIgnore(String ignoreProcesses){
//		ignoreProcesses argument is a string of process names separated by blank space
		try{
			List<String> pids = new ArrayList<String>();
			if(ignoreProcesses != null && !ignoreProcesses.trim().isEmpty()){
				// Using pidof command now to get all pids of the mentioned processes
				java.lang.Process pidChecker = Runtime.getRuntime().exec("pidof " + ignoreProcesses);
				// pidof returns pids of given processes as a string separated by a blank space
				BufferedReader pidReader = new BufferedReader(new InputStreamReader(pidChecker.getInputStream()));
				String pidline = pidReader.readLine();
				// added all returned from pidof command
				pids.addAll(Arrays.asList(pidline.split("\\s+")));
				pidReader.close();
			}
			return pids;
		}catch(IOException e){
			logger.log(Level.WARNING, "Error building list of processes to ignore: " + ignoreProcesses, e);
			return null;
		}
	}

	// Returns the uid if valid username
	private String checkIfValidUsername(String name){
		String command = "id -u " + name;
		try{
			Execute.Output output = Execute.getOutput(command);
			if(output.exitValueIndicatesError()){
				logger.log(Level.SEVERE, "Invalid username provided. Command: {0}. Error: {1}", 
						new Object[]{command, output.getStdErr()});
			}else{
				List<String> stdOutLines = output.getStdOut();
				if(stdOutLines.size() == 0){
					logger.log(Level.SEVERE, "No uid in output for command: {0}. Output: {1}.", new Object[]{
							command, stdOutLines
					});
				}else{
					String uidLine = stdOutLines.get(0);
					if(uidLine == null || (uidLine = uidLine.trim()).isEmpty()){
						logger.log(Level.SEVERE, "NULL/Empty uid for command: {0}. Output: {1}.", new Object[]{
								command, stdOutLines
						});
					}else{
						return uidLine;
					}
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to execute command: " + command, e);
		}
		return null;
	}
	
	private String getOwnUid(){
		String command = "id -u";
		try{
			String uid = null;
			Execute.Output output = Execute.getOutput(command);
			if(output.exitValueIndicatesError()){
				logger.log(Level.SEVERE, "Failed to get user id of JVM. Command: {0}. Error: {1}.",
						new Object[]{command, output.getStdErr()});
				return null;
			}else{
				List<String> stdOutLines = output.getStdOut();
				if(stdOutLines.size() == 0){
					logger.log(Level.SEVERE, "No uid in output for command: {0}. Output: {1}.", new Object[]{
							command, stdOutLines
					});
					return null;
				}else{
					String uidLine = stdOutLines.get(0);
					if(uidLine == null || (uidLine = uidLine.trim()).isEmpty()){
						logger.log(Level.SEVERE, "NULL/Empty uid for command: {0}. Output: {1}.", new Object[]{
								command, stdOutLines
						});
						return null;
					}else{
						uid = uidLine;
					}
				}
				return uid;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to get user id of JVM using command: " + command, e);
			return null;
		}
	}

	@Override
	public boolean shutdown() {
		
		// Send an interrupt to the spadeAuditBridgeProcess
		
		sendSignalToPid(spadeAuditBridgeProcessPid, "2");
		
		// Return. The event reader thread and the error reader thread will exit on their own.
		// The event reader thread will do the state cleanup
		
		while(eventReaderThreadRunning){
			// Wait while the event reader thread is still running i.e. buffer being emptied
			try{ Thread.sleep(PID_MSG_WAIT_TIMEOUT); }catch(Exception e){}
		}
		
		// force print stats before exiting
		printStats(true);
		
		return true;
	}

	private void printStats(boolean forcePrint){
		if(reportingEnabled || forcePrint){
			long currentTime = System.currentTimeMillis();
			if(((currentTime - lastReportedTime) >= reportEveryMs) || forcePrint){
				Runtime runtime = Runtime.getRuntime();
				long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024*1024);   	
				int internalBufferSize = getBuffer().size();
				String statString = String.format("Internal buffer size: %d, JVM memory in use: %dMB", 
						internalBufferSize, usedMemoryMB);
				if(REFINE_NET){
					String netfilterStat = String.format("Unmatched: "
							+ "%d netfilter-syscall, "
							+ "%d syscall-netfilter. "
							+ "Matched: "
							+ "%d netfilter-syscall, "
							+ "%d syscall-netfilter.",
							networkAnnotationsFromNetfilter.size(),
							networkAnnotationsFromSyscalls.size(),
							matchedNetfilterSyscall,
							matchedSyscallNetfilter);
					statString += ", " + netfilterStat;
				}
				logger.log(Level.INFO, statString);
				lastReportedTime = currentTime;
			}
		}
	}
	
	private void setHandleKMRecordsFlag(boolean isLiveAudit, boolean valueOfHandleKMRecords){
		// Only set the value if it hasn't been set and is log playback
		if(HANDLE_KM_RECORDS == null && !isLiveAudit){
			HANDLE_KM_RECORDS = valueOfHandleKMRecords;
			logger.log(Level.INFO, "'handleLocalEndpoints' value set to '"+valueOfHandleKMRecords+"'");
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
			}else if(AuditEventReader.RECORD_TYPE_NETFILTER_PKT.equals(recordType)){
				setHandleKMRecordsFlag(isLiveAudit, false); // Always do first because HANDLE_KM_RECORDS can be null when playback
				if(REFINE_NET){
					handleNetfilterPacketEvent(eventData);
				}
			}else if(AuditEventReader.KMODULE_RECORD_TYPE.equals(recordType)){
				setHandleKMRecordsFlag(isLiveAudit, true); // Always do first because HANDLE_KM_RECORDS can be null when playback
				if(HANDLE_KM_RECORDS){
					handleKernelModuleEvent(eventData);
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
			Integer syscallNumber = CommonFunctions.parseInt(eventData.get(AuditEventReader.SYSCALL), null);
			String exit = eventData.get(AuditEventReader.EXIT);
			int success = CommonFunctions.parseInt(eventData.get(AuditEventReader.SUCCESS), -1);
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
		int arch = -1;
		if(ARCH_32BIT){
			arch = 32;
		}else{
			arch = 64;
		}
		return SYSCALL.getSyscall(syscallNumber, arch);
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
			int syscallNum = CommonFunctions.parseInt(eventData.get(AuditEventReader.SYSCALL), -1);
			
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
			for(int argumentNumber = 0; argumentNumber<4; argumentNumber++){ //only 4 arguments received from linux audit
				try{
					eventData.put("a"+argumentNumber, String.valueOf(new BigInteger(eventData.get("a"+argumentNumber), 16).longValue()));
				}catch(Exception e){
					logger.log(Level.INFO, "Missing/Non-numerical argument#" + argumentNumber + " for event id '"+eventId+"'");
				}
			}
			
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
					setHandleKMRecordsFlag(isLiveAudit, false);
					break;
				default:
					break;
			}

			switch (syscall) {
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
				handleIOEvent(syscall, eventData, false);
				break;
			case SENDMSG:
			case SENDTO:
				if(!HANDLE_KM_RECORDS){
					handleIOEvent(syscall, eventData, false);
				}
				break;
			case RECVFROM: 
			case RECVMSG:
				if(!HANDLE_KM_RECORDS){
					handleIOEvent(syscall, eventData, true);
				}
				break;
			case READ: 
			case READV:
			case PREAD:
			case PREADV:
				handleIOEvent(syscall, eventData, true);
				break;
			case MMAP:
			case MMAP2:
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
				handleOpen(eventData, syscall);
				break;
			case CLOSE:
				handleClose(eventData);
				break;
			case CREAT:
				handleCreat(eventData);
				break;
			case OPENAT:
				handleOpenat(eventData, syscall);
				break;
			case MKNODAT:
				handleMknodat(eventData);
				break;
			case MKNOD:
				handleMknod(eventData, syscall);
				break;
			case DUP:
			case DUP2:
			case DUP3:
				handleDup(eventData, syscall);
				break;
			case SOCKET:
				if(!HANDLE_KM_RECORDS){
					handleSocket(eventData, syscall);
				}
				break;
			case BIND:
				if(!HANDLE_KM_RECORDS){
					handleBind(eventData, syscall);
				}
				break;
			case ACCEPT4:
			case ACCEPT:
				if(!HANDLE_KM_RECORDS){
					handleAccept(eventData, syscall);
				}
				break;
			case CONNECT:
				if(!HANDLE_KM_RECORDS){
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

	private void handleIOEvent(SYSCALL syscall, Map<String, String> eventData, boolean isRead){
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);
		String bytesTransferred = eventData.get(AuditEventReader.EXIT);
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

	private void handleUnlink(Map<String, String> eventData, SYSCALL syscall){
		// unlink() and unlinkat() receive the following messages(s):
		// - SYSCALL
		// - PATH with PARENT nametype
		// - PATH with DELETE nametype relative to CWD
		// - CWD
		// - EOE

		if(CONTROL){
			String time = eventData.get(AuditEventReader.TIME);
			String eventId = eventData.get(AuditEventReader.EVENT_ID);
			String pid = eventData.get(AuditEventReader.PID);
			String cwd = eventData.get(AuditEventReader.CWD);
			
			String path = null;
			PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_DELETE);

			if(pathRecord == null){
				log(Level.INFO, "PATH record with nametype DELETE missing", null, time, eventId, syscall);
				return;
			}else{
				path = pathRecord.getPath();
			}

			if(syscall == SYSCALL.UNLINK){
				path = constructAbsolutePath(path, cwd, pid);
			}else if(syscall == SYSCALL.UNLINKAT){
				path = constructPathSpecial(path, eventData.get(AuditEventReader.ARG0), cwd, pid, time, eventId, syscall); 		
			}else{
				log(Level.INFO, "Unexpected syscall '"+syscall+"' in UNLINK handler", null, time, eventId, syscall);
				return;
			}

			if(path == null){
				log(Level.INFO, "Failed to build absolute path from log data", null, time, eventId, syscall);
				return;
			}

			ArtifactIdentifier artifactIdentifier = getArtifactIdentifierFromPathMode(path, pathRecord.getPathType(),
					time, eventId, syscall);

			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact artifact = putArtifact(eventData, artifactIdentifier, pathRecord.getPermissions(), false);
			WasGeneratedBy deletedEdge = new WasGeneratedBy(artifact, process);
			putEdge(deletedEdge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		}
	}
	
	private UnknownIdentifier addUnknownFd(String pid, String fd){
		UnknownIdentifier unknown = new UnknownIdentifier(pid, fd);
		unknown.setOpenedForRead(null);
		markNewEpochForArtifact(unknown);
		processManager.setFd(pid, fd, unknown);
		return unknown;
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
		
		int cmd = CommonFunctions.parseInt(cmdString, -1);
		int flags = CommonFunctions.parseInt(flagsString, -1);
		
		if(cmd == F_DUPFD || cmd == F_DUPFD_CLOEXEC){
			// In eventData, there should be a pid, a0 should be fd, and exit should be the new fd 
			handleDup(eventData, syscall);
		}else if(cmd == F_SETFL){
			if((flags & O_APPEND) == O_APPEND){
				ArtifactIdentifier artifactIdentifier = processManager.getFd(pid, fd);
				if(artifactIdentifier == null){
					artifactIdentifier = addUnknownFd(pid, fd);
				}
				// Made file descriptor 'appendable', so set open for read to false so 
				// that the edge on close is a WGB edge and not a Used edge
				if(artifactIdentifier.wasOpenedForRead() != null){
					artifactIdentifier.setOpenedForRead(false);
				}
			}
		}
	}
	
	private void handleExit(Map<String, String> eventData, SYSCALL syscall){
		// exit(), and exit_group() receives the following message(s):
		// - SYSCALL
		// - EOE
		if(CONTROL){
			// Only draw edge if CONTROL is true
			processManager.handleExit(eventData, syscall);
		}else{
			// Else remove the state only and not draw any edge
			String pid = eventData.get(AuditEventReader.PID);
			processManager.removeProcessUnitState(pid);
		}
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
		
		int flags = CommonFunctions.parseInt(eventData.get(AuditEventReader.ARG3), 0);
		
		// Put Process, Memory artifact and WasGeneratedBy edge always but return if flag
		// is MAP_ANONYMOUS
		
		if(((flags & MAP_ANONYMOUS) == MAP_ANONYMOUS) && !ANONYMOUS_MMAP){
			return;
		}
		
		Process process = processManager.handleProcessFromSyscall(eventData);		
		String tgid = processManager.getMemoryTgid(pid);
		ArtifactIdentifier memoryArtifactIdentifier = new MemoryIdentifier(tgid, address, length);
		Artifact memoryArtifact = putArtifact(eventData, memoryArtifactIdentifier, null, true);
		WasGeneratedBy wgbEdge = new WasGeneratedBy(memoryArtifact, process);
		wgbEdge.addAnnotation(OPMConstants.EDGE_PROTECTION, protection);
		putEdge(wgbEdge, getOperation(syscall, SYSCALL.WRITE), time, eventId, AUDIT_SYSCALL_SOURCE);		
		
		if((flags & MAP_ANONYMOUS) == MAP_ANONYMOUS){
			return;
		}else{
		
			String fd = eventData.get(AuditEventReader.FD);
	
			if(fd == null){
				log(Level.INFO, "FD record missing", null, time, eventId, syscall);
				return;
			}
	
			ArtifactIdentifier artifactIdentifier = processManager.getFd(pid, fd);
	
			if(artifactIdentifier == null){
				artifactIdentifier = addUnknownFd(pid, fd);
			}
	
			Artifact artifact = putArtifact(eventData, artifactIdentifier, null, false);
	
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
		
		ArtifactIdentifier memoryInfo = new MemoryIdentifier(tgid, address, length);
		Artifact memoryArtifact = putArtifact(eventData, memoryInfo, null, true);

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
		String cwd = eventData.get(AuditEventReader.CWD);
		String pid = eventData.get(AuditEventReader.PID);

		Process process = processManager.handleExecve(eventData, syscall);

		//add used edge to the paths in the event data. get the number of paths using the 'items' key and then iterate
		
		List<PathRecord> loadPathRecords = getPathsWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
		for(PathRecord loadPathRecord : loadPathRecords){
			String path = constructAbsolutePath(loadPathRecord.getPath(), cwd, pid);
			if(path == null){
				log(Level.INFO, "Missing PATH or CWD record", null, time, eventId, syscall);
				continue;
			}        	
			ArtifactIdentifier artifactIdentifier = getArtifactIdentifierFromPathMode(path, loadPathRecord.getPathType(),
					time, eventId, syscall);
			Artifact usedArtifact = putArtifact(eventData, artifactIdentifier, loadPathRecord.getPermissions(), false);
			Used usedEdge = new Used(process, usedArtifact);
			putEdge(usedEdge, getOperation(SYSCALL.LOAD), time, eventId, AUDIT_SYSCALL_SOURCE);
		}
	}

	private void handleCreat(Map<String, String> eventData){
		//creat() receives the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH of the parent with nametype=PARENT
		// - PATH of the created file with nametype=CREATE
		// - EOE

		//as mentioned in open syscall manpage    	
		int defaultFlags = O_CREAT|O_WRONLY|O_TRUNC;

		//modify the eventData as expected by open syscall and call open syscall function
		eventData.put(AuditEventReader.ARG2, eventData.get(AuditEventReader.ARG1)); //set mode to argument 3 (in open) from 2 (in creat)
		eventData.put(AuditEventReader.ARG1, String.valueOf(defaultFlags)); //flags is argument 2 in open

		handleOpen(eventData, SYSCALL.CREATE); //TODO change to creat. kept as create to keep current CDM data consistent

	}
	
	/**
	 * Get path from audit log. First see, if a path with CREATE nametype exists.
	 * If yes then return that. If no then check if path with NORMAL nametype exists.
	 * If yes then return that else return null.
	 * 
	 * @param eventData audit log event data as key values
	 * @return path/null
	 */
	private PathRecord getPathWithCreateOrNormalNametype(Map<String, String> eventData){
		PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_CREATE);
		if(pathRecord != null){
			return pathRecord;
		}else{
			pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			return pathRecord;
		}
	}

	private void handleOpenat(Map<String, String> eventData, SYSCALL syscall){
		//openat() receives the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH
		// - PATH
		// - EOE

		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		
		PathRecord pathRecord = getPathWithCreateOrNormalNametype(eventData);
		
		if(pathRecord == null){
			log(Level.INFO, "Missing PATH record", null, time, eventId, syscall);
			return;
		}
		
		String path = pathRecord.getPath();
		// If not absolute then only run the following logic according to the manpage
		if(!path.startsWith(File.separator)){
			Long dirFd = CommonFunctions.parseLong(eventData.get(AuditEventReader.ARG0), -1L);
	
			//according to manpage if following true then use cwd if path not absolute, which is already handled by open
			if(dirFd != AT_FDCWD){ //checking if cwd needs to be replaced by dirFd's path
				String pid = eventData.get(AuditEventReader.PID);
				String dirFdString = String.valueOf(dirFd);
				//if null of if not file then cannot process it
				ArtifactIdentifier artifactIdentifier = processManager.getFd(pid, dirFdString);
				if(artifactIdentifier == null || !(artifactIdentifier instanceof IdentifierWithPath)){
					log(Level.INFO, "Expected 'dir' type fd: '" + artifactIdentifier + "'", null, time, eventId, syscall);
					return;
				}else{ //is file
					String dirPath = ((IdentifierWithPath)artifactIdentifier).getPath();
					eventData.put(AuditEventReader.CWD, dirPath); //replace cwd with dirPath to make eventData compatible with open
				}
			}
		}

		//modify the eventData to match open syscall and then call it's function

		eventData.put(AuditEventReader.ARG0, eventData.get(AuditEventReader.ARG1)); //moved pathname address to first like in open
		eventData.put(AuditEventReader.ARG1, eventData.get(AuditEventReader.ARG2)); //moved flags to second like in open
		eventData.put(AuditEventReader.ARG2, eventData.get(AuditEventReader.ARG3)); //moved mode to third like in open

		handleOpen(eventData, syscall);
	}

	private void handleOpen(Map<String, String> eventData, SYSCALL syscall) {
		// open() receives the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH with nametype CREATE (file operated on) or NORMAL (file operated on) or PARENT (parent of file operated on) or DELETE (file operated on) or UNKNOWN (only when syscall fails)
		// - PATH with nametype CREATE or NORMAL or PARENT or DELETE or UNKNOWN
		// - EOE

		//three syscalls can come here: OPEN (for files and pipes), OPENAT (for files and pipes), CREAT (only for files)

		Long flags = CommonFunctions.parseLong(eventData.get(AuditEventReader.ARG1), 0L);
		Long modeArg = CommonFunctions.parseLong(eventData.get(AuditEventReader.ARG2), 0L);
		
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String cwd = eventData.get(AuditEventReader.CWD);
		String fd = eventData.get(AuditEventReader.EXIT);
		String time = eventData.get(AuditEventReader.TIME);

		boolean isCreate = syscall == SYSCALL.CREATE || syscall == SYSCALL.CREAT; //TODO later on change only to CREAT only
		
		PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_CREATE);
		if(pathRecord == null){
			isCreate = false;
			pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			if(pathRecord == null){
				log(Level.INFO, "Missing PATH record", null, time, eventId, syscall);
				return;
			}
		}else{
			isCreate = true;
		}

		String path = pathRecord.getPath();
		path = constructAbsolutePath(path, cwd, pid);

		if(path == null){
			log(Level.INFO, "Missing CWD or PATH record", null, time, eventId, syscall);
			return;
		}

		Process process = processManager.handleProcessFromSyscall(eventData);

		ArtifactIdentifier artifactIdentifier = getArtifactIdentifierFromPathMode(path, pathRecord.getPathType(),
				time, eventId, syscall);

		AbstractEdge edge = null;

		if(isCreate){

			//set new epoch
			markNewEpochForArtifact(artifactIdentifier);

			syscall = SYSCALL.CREATE;

		}

		boolean openedForRead = false;
		
		String flagsArgs = "";
		
		flagsArgs += ((flags & O_WRONLY) == O_WRONLY) ? "O_WRONLY|" : "";
		flagsArgs += ((flags & O_RDWR) == O_RDWR) ? "O_RDWR|" : "";
		// if neither write only nor read write then must be read only
		if(((flags & O_WRONLY) != O_WRONLY) && 
				((flags & O_RDWR) != O_RDWR)){ 
			// O_RDONLY is 0, so always true
			flagsArgs += ((flags & O_RDONLY) == O_RDONLY) ? "O_RDONLY|" : "";
		}
		
		flagsArgs += ((flags & O_APPEND) == O_APPEND) ? "O_APPEND|" : "";
		flagsArgs += ((flags & O_TRUNC) == O_TRUNC) ? "O_TRUNC|" : "";
		flagsArgs += ((flags & O_CREAT) == O_CREAT) ? "O_CREAT|" : "";
		
		if(!flagsArgs.isEmpty()){
			flagsArgs = flagsArgs.substring(0, flagsArgs.length() - 1);
		}

		if((flags & O_WRONLY) == O_WRONLY || 
				(flags & O_RDWR) == O_RDWR ||
				 (flags & O_APPEND) == O_APPEND || 
				 (flags & O_TRUNC) == O_TRUNC){
			Artifact vertex = putArtifact(eventData, artifactIdentifier, pathRecord.getPermissions(), true);
			edge = new WasGeneratedBy(vertex, process);
			openedForRead = false;
		} else if ((flags & O_RDONLY) == O_RDONLY) {
			if(isCreate){
				Artifact vertex = putArtifact(eventData, artifactIdentifier, pathRecord.getPermissions(), true);
				edge = new WasGeneratedBy(vertex, process);
			}else{
				Artifact vertex = putArtifact(eventData, artifactIdentifier, pathRecord.getPermissions(), false);
				edge = new Used(process, vertex);
			}
			openedForRead = true;
		} else {
			log(Level.INFO, "Unhandled value of FLAGS argument '"+flags+"'", null, time, eventId, syscall);
			return;
		}
		
		if(edge != null){
			edge.addAnnotation(OPMConstants.EDGE_MODE, Long.toOctalString(modeArg));
			if(!flagsArgs.isEmpty()){
				edge.addAnnotation(OPMConstants.EDGE_FLAGS, flagsArgs);
			}
			//everything happened successfully. add it to descriptors
			processManager.setFd(pid, fd, artifactIdentifier, openedForRead);

			putEdge(edge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		}
	}

	private void handleClose(Map<String, String> eventData) {
		// close() receives the following message(s):
		// - SYSCALL
		// - EOE
		String pid = eventData.get(AuditEventReader.PID);
		String fd = String.valueOf(CommonFunctions.parseLong(eventData.get(AuditEventReader.ARG0), -1L));
		ArtifactIdentifier closedArtifactIdentifier = processManager.removeFd(pid, fd);
		
		if(CONTROL){
			SYSCALL syscall = SYSCALL.CLOSE;
			String time = eventData.get(AuditEventReader.TIME);
			String eventId = eventData.get(AuditEventReader.EVENT_ID);
			if(closedArtifactIdentifier != null){
				Process process = processManager.handleProcessFromSyscall(eventData);
				Artifact artifact = putArtifact(eventData, closedArtifactIdentifier, null, false);
				AbstractEdge edge = null;
				Boolean wasOpenedForRead = closedArtifactIdentifier.wasOpenedForRead();
				if(wasOpenedForRead == null){
					// Not drawing an edge because didn't seen an open or was a 'bound' fd
				}else if(wasOpenedForRead){
					edge = new Used(process, artifact);
				}else {
					edge = new WasGeneratedBy(artifact, process);
				}	   
				if(edge != null){
					putEdge(edge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
				}
				//after everything done increment epoch is udp socket
				if(isUdp(closedArtifactIdentifier)){
					markNewEpochForArtifact(closedArtifactIdentifier);
				}
			}else{
				log(Level.INFO, "No FD with number '"+fd+"' for pid '"+pid+"'", null, time, eventId, syscall);
			}
		}

		//there is an option to either handle epochs 1) when artifact opened/created or 2) when artifacts deleted/closed.
		//handling epoch at opened/created in all cases
	}
	
	private boolean isUdp(ArtifactIdentifier identifier){
		if(identifier != null && identifier.getClass().equals(NetworkSocketIdentifier.class)){
			if(PROTOCOL_NAME_UDP.equals(((NetworkSocketIdentifier)identifier).getProtocol())){
				return true;
			}
		}
		return false;
	}

	private void handleTruncate(Map<String, String> eventData, SYSCALL syscall) {
		// write() receives the following message(s):
		// - SYSCALL
		// - EOE
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);

		ArtifactIdentifier artifactIdentifier = null;
		String permissions = null;

		if (syscall == SYSCALL.TRUNCATE) {
			PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			if(pathRecord == null){
				log(Level.INFO, "Missing PATH record", null, time, eventId, syscall);
				return;
			}
			String path = pathRecord.getPath();
			path = constructAbsolutePath(path, eventData.get(AuditEventReader.CWD), pid);
			if(path == null){
				log(Level.INFO, "Missing PATH or CWD record", null, time, eventId, syscall);
				return;
			}
			artifactIdentifier = getArtifactIdentifierFromPathMode(path, pathRecord.getPathType(), 
					time, eventId, syscall);
			permissions = pathRecord.getPermissions();
		} else if (syscall == SYSCALL.FTRUNCATE) {
			String fd = eventData.get(AuditEventReader.ARG0);
			artifactIdentifier = processManager.getFd(pid, fd);
			if(artifactIdentifier == null){
				artifactIdentifier = addUnknownFd(pid, fd);
			}
		}

		if(artifactIdentifier != null){
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact vertex = putArtifact(eventData, artifactIdentifier, permissions, true);
			WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
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
			ArtifactIdentifier artifactIdentifier = processManager.getFd(pid, fd);
			if(artifactIdentifier == null){
				artifactIdentifier = addUnknownFd(pid, fd);
			}
			processManager.setFd(pid, newFD, artifactIdentifier);
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
			ArtifactIdentifier fdOutIdentifier = processManager.getFd(pid, fdOut);
			fdOutIdentifier = fdOutIdentifier == null ? addUnknownFd(pid, fdOut) : fdOutIdentifier;
			
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact fdOutArtifact = putArtifact(eventData, fdOutIdentifier, null, true);
	
			WasGeneratedBy processToWrittenArtifact = new WasGeneratedBy(fdOutArtifact, process);
			processToWrittenArtifact.addAnnotation(OPMConstants.EDGE_SIZE, bytes);
			putEdge(processToWrittenArtifact, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		}
	}
	
	private void putTeeSplice(Map<String, String> eventData, SYSCALL syscall,
			String time, String eventId, String fdIn, String fdOut, String pid, String bytes){
		ArtifactIdentifier fdInIdentifier = processManager.getFd(pid, fdIn);
		ArtifactIdentifier fdOutIdentifier = processManager.getFd(pid, fdOut);

		// Use unknown if missing fds
		fdInIdentifier = fdInIdentifier == null ? addUnknownFd(pid, fdIn) : fdInIdentifier;
		fdOutIdentifier = fdOutIdentifier == null ? addUnknownFd(pid, fdOut) : fdOutIdentifier;

		Process process = processManager.handleProcessFromSyscall(eventData);
		Artifact fdInArtifact = putArtifact(eventData, fdInIdentifier, null, false);
		Artifact fdOutArtifact = putArtifact(eventData, fdOutIdentifier, null, true);

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
			moduleIdentifier = processManager.getFd(pid, fd);
			moduleIdentifier = moduleIdentifier == null ? addUnknownFd(pid, fd) : moduleIdentifier;
		}else{
			log(Level.WARNING, "Unexpected syscall in (f)init_module handler", null, time, eventId, syscall);
		}
		
		if(moduleIdentifier != null){
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact module = putArtifact(eventData, moduleIdentifier, null, false);
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
	
	/**
	 * Removes special path symbols '..' and '.'
	 * 
	 * @param path file system path
	 * @return file system path or null if not a valid path
	 */
	private String removeSpecialPathSymbols(String path){
		if(path == null){
			return null;
		}
		String finalPath = "";
		path = path.trim();
		if(path.isEmpty()){
			return null;
		}else{
			String[] parts = path.split(File.separator);
			for(int a = parts.length-1; a>-1; a--){
				if(parts[a].equals("..")){
					a--;
					continue;
				}else if(parts[a].equals(".")){
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
					finalPath = parts[a] + File.separator + finalPath;
				}
			}
			// Adding the slash in the end if the given path had a slash in the end
			if(!path.endsWith(File.separator) && finalPath.endsWith(File.separator)){
				finalPath = finalPath.substring(0, finalPath.length() - 1);
			}
			// Adding the slash in the beginning if the given path had a slash in the beginning
			if(path.startsWith(File.separator) && !finalPath.startsWith(File.separator)){
				finalPath = File.separator + finalPath;
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
	private String resolvePathStatically(String path, String pid){
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
	 * Constructs path by concatenating the two paths, then removes symbols such as '.' and 
	 * '..' and then finally resolves symbolic links like /proc/self.
	 * 
	 * Null returned if unable to construct absolute path
	 * 
	 * @param path path relative to parentPath
	 * @param parentPath parent path of the path
	 * @return constructed path or null
	 */
	private String constructAbsolutePath(String path, String parentPath, String pid){
		path = concatenatePaths(path, parentPath);
		if(path != null){
			path = removeSpecialPathSymbols(path);
			if(path != null){
				path = resolvePathStatically(path, pid);
				return path;
			}
		}
		return null;
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
	private String concatenatePaths(String path, String parentPath){
		if(path != null){
			path = path.trim();
			if(path.isEmpty()){
				return null;
			}else{
				if(path.startsWith(File.separator)){ //is absolute
					return path;
				}else{
	
					if(parentPath != null){
						parentPath = parentPath.trim();
						if(parentPath.isEmpty() || !parentPath.startsWith(File.separator)){
							return null;
						}else{
							return parentPath + File.separator + path;
						}
					}
				}	   
			}
		}
		return null;
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
		String pid = eventData.get(AuditEventReader.PID);
		String cwd = eventData.get(AuditEventReader.CWD);
		String oldFilePath = eventData.get(AuditEventReader.PATH_PREFIX+"2");
		String oldFilePathModeStr = eventData.get(AuditEventReader.MODE_PREFIX+"2");
		//if file renamed to already existed then path4 else path3. Both are same so just getting whichever exists
		String newFilePath = eventData.get(AuditEventReader.PATH_PREFIX+"4") == null ? 
				eventData.get(AuditEventReader.PATH_PREFIX+"3") : 
				eventData.get(AuditEventReader.PATH_PREFIX+"4");
		String newFilePathModeStr = eventData.get(AuditEventReader.MODE_PREFIX+"4") == null ? 
				eventData.get(AuditEventReader.MODE_PREFIX+"3") : 
				eventData.get(AuditEventReader.MODE_PREFIX+"4");
		
		if(syscall == SYSCALL.RENAME){
			oldFilePath = constructAbsolutePath(oldFilePath, cwd, pid);
			newFilePath = constructAbsolutePath(newFilePath, cwd, pid);
		}else if(syscall == SYSCALL.RENAMEAT){
			oldFilePath = constructPathSpecial(oldFilePath, eventData.get(AuditEventReader.ARG0), cwd, pid, time, eventId, syscall);        	
			newFilePath = constructPathSpecial(newFilePath, eventData.get(AuditEventReader.ARG2), cwd, pid, time, eventId, syscall);        	
		}else{
			log(Level.WARNING, "Unexpected syscall '"+syscall+"' in RENAME handler", null, time, eventId, syscall);
			return;
		}

		if(oldFilePath == null || newFilePath == null){
			log(Level.INFO, "Failed to create path(s)", null, time, eventId, syscall);
			return;
		}

		handleSpecialSyscalls(eventData, syscall, oldFilePath, newFilePath, oldFilePathModeStr, newFilePathModeStr);
	}

	private void handleMknodat(Map<String, String> eventData){
		//mknodat() receives the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH of the created file with nametype=CREATE
		// - EOE

		//first argument is the fd of the directory to create file in. if the directory fd is AT_FDCWD then use cwd

		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		PathRecord pathRecord = getPathWithCreateOrNormalNametype(eventData);
		
		if(pathRecord == null){
			log(Level.INFO, "Missing PATH record", null, time, eventId, SYSCALL.MKNODAT);
			return;
		}
		
		String path = pathRecord.getPath();
		
		// If not absolute then only run the following logic according to the manpage
		if(!path.startsWith(File.separator)){
			String fd = eventData.get(AuditEventReader.ARG0);
			Long fdLong = CommonFunctions.parseLong(fd, null);

			ArtifactIdentifier artifactIdentifier = null;

			if(fdLong != AT_FDCWD){
				artifactIdentifier = processManager.getFd(pid, fd);
				if(artifactIdentifier == null){
					log(Level.INFO, "No FD '"+fd+"' for pid '"+pid+"'", null, time, eventId, SYSCALL.MKNODAT);
					return;
				}else if(artifactIdentifier instanceof IdentifierWithPath){
					String directoryPath = ((IdentifierWithPath)artifactIdentifier).getPath();
					//update cwd to directoryPath and call handleMknod. the file created path is always relative in this syscall
					eventData.put(AuditEventReader.CWD, directoryPath);
				}else{
					log(Level.INFO, "FD '"+fd+"' for pid '"+pid+"' is of type '"+artifactIdentifier.getClass()+
							"' but only file allowed", null, time, eventId, SYSCALL.MKNODAT);
					return;
				}    		
			}
		}

		//replace the second argument (which is mode in mknod) with the third (which is mode in mknodat)
		eventData.put(AuditEventReader.ARG1, eventData.get(AuditEventReader.ARG2));
		handleMknod(eventData, SYSCALL.MKNODAT);
	}
	
	private void handleMknod(Map<String, String> eventData, SYSCALL syscall){
		//mknod() receives the following message(s):
		// - SYSCALL
		// - CWD
		// - PATH of the parent with nametype=PARENT
		// - PATH of the created file with nametype=CREATE
		// - EOE

		String modeString = eventData.get(AuditEventReader.ARG1);
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);

		PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_CREATE);

		if(pathRecord == null){
			log(Level.INFO, "PATH record missing", null, time, eventId, syscall);
			return;
		}

		String path = pathRecord.getPath();        
		path = constructAbsolutePath(path, eventData.get(AuditEventReader.CWD), 
				eventData.get(AuditEventReader.PID));

		if(path == null){
			log(Level.INFO, "Missing PATH or CWD record", null, time, eventId, syscall);
			return;
		}

		ArtifactIdentifier artifactIdentifier = getArtifactIdentifierFromPathMode(path, pathRecord.getPathType(),
				time, eventId, syscall);

		if(artifactIdentifier == null){
			log(Level.INFO, "Unsupported mode for mknod '"+modeString+"'", null, time, eventId, syscall);
			return;
		}	

		if(artifactIdentifier != null){
			markNewEpochForArtifact(artifactIdentifier);
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

		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String cwd = eventData.get(AuditEventReader.CWD);
		String srcPath = eventData.get(AuditEventReader.PATH_PREFIX+"0");
		String srcPathModeStr = eventData.get(AuditEventReader.MODE_PREFIX + "0");
		String dstPath = eventData.get(AuditEventReader.PATH_PREFIX + "2");
		String dstPathModeStr = eventData.get(AuditEventReader.MODE_PREFIX + "2");
		String time = eventData.get(AuditEventReader.TIME);
		
		if(syscall == SYSCALL.LINK || syscall == SYSCALL.SYMLINK){
			srcPath = constructAbsolutePath(srcPath, cwd, pid);
			dstPath = constructAbsolutePath(dstPath, cwd, pid);
		}else if(syscall == SYSCALL.LINKAT){
			srcPath = constructPathSpecial(srcPath, eventData.get(AuditEventReader.ARG0), cwd, pid, time, eventId, syscall);
			dstPath = constructPathSpecial(dstPath, eventData.get(AuditEventReader.ARG2), cwd, pid, time, eventId, syscall);
		}else if(syscall == SYSCALL.SYMLINKAT){
			srcPath = constructAbsolutePath(srcPath, cwd, pid);
			dstPath = constructPathSpecial(dstPath, eventData.get(AuditEventReader.ARG1), cwd, pid, time, eventId, syscall);
		}else{
			log(Level.WARNING, "Unexpected syscall '"+syscall+"' in LINK SYMLINK handler", null, time, eventId, syscall);
			return;
		}

		if(srcPath == null || dstPath == null){
			log(Level.INFO, "Failed to create path(s)", null, time, eventId, syscall);
			return;
		}

		handleSpecialSyscalls(eventData, syscall, srcPath, dstPath, srcPathModeStr, dstPathModeStr);
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
			String srcPath, String dstPath,
			String srcPathMode, String dstPathMode){

		if(eventData == null || syscall == null || srcPath == null || dstPath == null){
			logger.log(Level.INFO, "Missing arguments. srcPath:{0}, dstPath:{1}, syscall:{2}, eventData:{3}", new Object[]{srcPath, dstPath, syscall, eventData});
			return;
		}

		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String time = eventData.get(AuditEventReader.TIME);
		String pid = eventData.get(AuditEventReader.PID);

		if(eventId == null || time == null || pid == null){
			log(Level.INFO, "Missing keys in event data. pid:"+pid, null, time, eventId, syscall);
			return;
		}

		ArtifactIdentifier srcArtifactIdentifier = getArtifactIdentifierFromPathMode(srcPath, 
				PathRecord.parsePathType(srcPathMode), time, eventId, syscall);
		ArtifactIdentifier dstArtifactIdentifier = getArtifactIdentifierFromPathMode(dstPath, 
				PathRecord.parsePathType(dstPathMode), time, eventId, syscall);
		
		if(srcArtifactIdentifier == null || dstArtifactIdentifier == null){
			logger.log(Level.WARNING, "Missing path objects. Failed to "
					+ "process syscall {0} for event id {1}", new Object[]{syscall, eventId});
			return;
		}
		
		Process process = processManager.handleProcessFromSyscall(eventData);

		//destination is new so mark epoch
		markNewEpochForArtifact(dstArtifactIdentifier);

		Artifact srcVertex = putArtifact(eventData, srcArtifactIdentifier, PathRecord.parsePermissions(srcPathMode), false);
		Used used = new Used(process, srcVertex);
		putEdge(used, getOperation(syscall, SYSCALL.READ), time, eventId, AUDIT_SYSCALL_SOURCE);

		Artifact dstVertex = putArtifact(eventData, dstArtifactIdentifier, PathRecord.parsePermissions(dstPathMode), true);
		WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, process);
		putEdge(wgb, getOperation(syscall, SYSCALL.WRITE), time, eventId, AUDIT_SYSCALL_SOURCE);

		WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
		wdf.addAnnotation(OPMConstants.EDGE_PID, pid);
		putEdge(wdf, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
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
	private ArtifactIdentifier getArtifactIdentifierFromPathMode(String path, int pathType, 
			String time, String eventId, SYSCALL syscall){
		int type = pathType & S_IFMT;
		switch(type){
			case S_IFREG: return new FileIdentifier(path);
			case S_IFDIR: return new DirectoryIdentifier(path);
			case S_IFCHR: return new CharacterDeviceIdentifier(path);
			case S_IFBLK: return new BlockDeviceIdentifier(path);
			case S_IFLNK: return new LinkIdentifier(path);
			case S_IFIFO: return new NamedPipeIdentifier(path);
			case S_IFSOCK: return new UnixSocketIdentifier(path);
			default:
				log(Level.INFO, "Unknown file type: "+pathType+". Defaulted to 'File'", null, time, eventId, syscall);
				return new FileIdentifier(path);
		}
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
		// if syscall is chmod, then path is <path0> relative to <cwd>
		// if syscall is fchmod, look up file descriptor which is <a0>
		// if syscall is fchmodat, loop up the directory fd and build a path using the path in the audit log
		ArtifactIdentifier artifactIdentifier = null;
		if (syscall == SYSCALL.CHMOD) {
			PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			if(pathRecord == null){
				log(Level.INFO, "Missing PATH record", null, time, eventId, syscall);
				return;
			}
			String path = pathRecord.getPath();
			path = constructAbsolutePath(path, eventData.get(AuditEventReader.CWD), pid);
			if(path == null){
				log(Level.INFO, "Missing PATH or CWD records", null, time, eventId, syscall);
				return;
			}
			artifactIdentifier = getArtifactIdentifierFromPathMode(path, pathRecord.getPathType(),
					time, eventId, syscall);
			modeArgument = eventData.get(AuditEventReader.ARG1);
		} else if (syscall == SYSCALL.FCHMOD) {
			String fd = eventData.get(AuditEventReader.ARG0);
			artifactIdentifier = processManager.getFd(pid, fd);
			if(artifactIdentifier == null){
				artifactIdentifier = addUnknownFd(pid, fd);
			}
			modeArgument = eventData.get(AuditEventReader.ARG1);
		}else if(syscall == SYSCALL.FCHMODAT){
			PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			if(pathRecord == null){
				log(Level.INFO, "Missing PATH record", null, time, eventId, syscall);
				return;
			}
			String path = pathRecord.getPath();
			path = constructPathSpecial(path, eventData.get(AuditEventReader.ARG0), 
					eventData.get(AuditEventReader.CWD), pid, time, eventId, syscall);
			if(path == null){
				log(Level.INFO, "Failed to create path", null, time, eventId, syscall);
				return;
			}
			artifactIdentifier = getArtifactIdentifierFromPathMode(path, pathRecord.getPathType(),
					time, eventId, syscall);
			modeArgument = eventData.get(AuditEventReader.ARG2);
		}else{
			log(Level.INFO, "Unexpected syscall '"+syscall+"' in CHMOD handler", null, time, eventId, syscall);
			return;
		}

		if(artifactIdentifier == null){
			logger.log(Level.WARNING, "Failed to process syscall="+syscall +" because of missing artifact identifier");
			return;
		}
		
		Process process = processManager.handleProcessFromSyscall(eventData);
		String mode = new BigInteger(modeArgument).toString(8);
		mode = PathRecord.parsePermissions(mode);
		Artifact vertex = putArtifact(eventData, artifactIdentifier, mode, true);
		WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
		wgb.addAnnotation(OPMConstants.EDGE_MODE, mode);
		putEdge(wgb, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
	}

	private void handlePipe(Map<String, String> eventData, SYSCALL syscall) {
		// pipe() receives the following message(s):
		// - SYSCALL
		// - FD_PAIR
		// - EOE
		String pid = eventData.get(AuditEventReader.PID);

		String fd0 = eventData.get(AuditEventReader.FD0);
		String fd1 = eventData.get(AuditEventReader.FD1);
		ArtifactIdentifier readPipeIdentifier = new UnnamedPipeIdentifier(pid, fd0, fd1);
		ArtifactIdentifier writePipeIdentifier = new UnnamedPipeIdentifier(pid, fd0, fd1);
		processManager.setFd(pid, fd0, readPipeIdentifier, true);
		processManager.setFd(pid, fd1, writePipeIdentifier, false);

		// Since both (read, and write) pipe identifiers are the same, only need to mark epoch on one.
		markNewEpochForArtifact(readPipeIdentifier);
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

	private void handleNetfilterPacketEvent(Map<String, String> eventData){
//      Refer to the following link for protocol numbers
//    	http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
		String time = eventData.get(AuditEventReader.TIME);
    	String eventId = eventData.get(AuditEventReader.EVENT_ID);
    	String protocolNumberString = eventData.get(AuditEventReader.PROTO);
    	String hook = eventData.get(AuditEventReader.HOOK);//hook=1 (input), hook=3 (output)
    	
    	String localAddress = null, localPort = null, remoteAddress = null, remotePort = null;
    	
    	if(AuditEventReader.HOOK_INPUT.equals(hook)){
    		localAddress = eventData.get(AuditEventReader.DADDR);
        	localPort = eventData.get(AuditEventReader.DPORT);
        	remoteAddress = eventData.get(AuditEventReader.SADDR);
        	remotePort = eventData.get(AuditEventReader.SPORT);
    	}else if(AuditEventReader.HOOK_OUTPUT.equals(hook)){
    		localAddress = eventData.get(AuditEventReader.SADDR);
        	localPort = eventData.get(AuditEventReader.SPORT);
        	remoteAddress = eventData.get(AuditEventReader.DADDR);
        	remotePort = eventData.get(AuditEventReader.DPORT);
    	}else{
    		logger.log(Level.INFO, "Unexpected hook value: " + hook);
    		return;
    	}
    	
    	Integer protocolNumber = CommonFunctions.parseInt(protocolNumberString, null);
    	String protocolName = getProtocolName(protocolNumber);
    	protocolName = protocolName == null ? "" : protocolName; // put empty if null
    	
    	// Update this area if network annotations change. TODO in future
    	// epoch and version added from the syscall artifact
    	Map<String, String> annotationsFromNetfilter = new HashMap<String, String>();
    	annotationsFromNetfilter.put(OPMConstants.ARTIFACT_LOCAL_ADDRESS, localAddress);
    	annotationsFromNetfilter.put(OPMConstants.ARTIFACT_LOCAL_PORT, localPort);
    	annotationsFromNetfilter.put(OPMConstants.ARTIFACT_REMOTE_ADDRESS, remoteAddress);
    	annotationsFromNetfilter.put(OPMConstants.ARTIFACT_REMOTE_PORT, remotePort);
    	annotationsFromNetfilter.put(OPMConstants.ARTIFACT_PROTOCOL, protocolName);
    	annotationsFromNetfilter.put(OPMConstants.ARTIFACT_SUBTYPE, OPMConstants.SUBTYPE_NETWORK_SOCKET);
    	annotationsFromNetfilter.put(OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_NETFILTER);

    	Map<String, String> annotationsFromSyscall = getNetworkAnnotationsSeenInList(
    			networkAnnotationsFromSyscalls, remoteAddress, remotePort);
    	if(annotationsFromSyscall != null){ //found
    		String localPortFromSyscall = annotationsFromSyscall.get(OPMConstants.ARTIFACT_LOCAL_PORT);
    		if(localPortFromSyscall != null && !localPortFromSyscall.trim().isEmpty()){
    			if(!localPortFromSyscall.equals(localPort)){
    				// different connection
    				annotationsFromNetfilter.put(OPMConstants.EDGE_TIME, time);
    				annotationsFromNetfilter.put(OPMConstants.EDGE_EVENT_ID, eventId);
    				networkAnnotationsFromNetfilter.add(annotationsFromNetfilter);
    				return;
    			}
    		}
    		// logic for deduplication deviates from the current one
    		// standardize this too and handling of netfilter in syscall functions. TODO
    		String epoch = annotationsFromSyscall.get(OPMConstants.ARTIFACT_EPOCH);
    		String version = annotationsFromSyscall.get(OPMConstants.ARTIFACT_VERSION);
    		if(epoch != null){
    			annotationsFromNetfilter.put(OPMConstants.ARTIFACT_EPOCH, epoch);
    		}
    		if(version != null){
    			annotationsFromNetfilter.put(OPMConstants.ARTIFACT_VERSION, version);
    		}
    		
    		Artifact artifactFromNetfilter = new Artifact();
    		artifactFromNetfilter.addAnnotations(annotationsFromNetfilter);
    		putVertex(artifactFromNetfilter); // add this only. syscall one already added.
    		
    		Artifact artifactFromSyscall = new Artifact();
    		artifactFromSyscall.addAnnotations(annotationsFromSyscall);
    		
    		WasDerivedFrom syscallToNetfilter = new WasDerivedFrom(artifactFromNetfilter, artifactFromSyscall);
    		putEdge(syscallToNetfilter, getOperation(SYSCALL.UPDATE), time, eventId, OPMConstants.SOURCE_AUDIT_NETFILTER);
    		
    		// Found a match, and have consumed this. So, remove from the list.
    		networkAnnotationsFromSyscalls.remove(annotationsFromSyscall);
    		
    		matchedNetfilterSyscall++;
    		
    	}else{
    		annotationsFromNetfilter.put(OPMConstants.EDGE_EVENT_ID, eventId);
    		annotationsFromNetfilter.put(OPMConstants.EDGE_TIME, time);
    		networkAnnotationsFromNetfilter.add(annotationsFromNetfilter);
    	}
    }
	
	private NetworkSocketIdentifier getNetworkIdentifier(SYSCALL syscall, String time, String eventId, 
			String pid, String fd){
		ArtifactIdentifier identifier = processManager.getFd(pid, fd);
		if(identifier != null){
			if(identifier.getClass().equals(NetworkSocketIdentifier.class)){
				return ((NetworkSocketIdentifier)identifier);
			}else{
				log(Level.INFO, "Expected network identifier but found: " + 
						identifier.getClass(), null, time, eventId, syscall);
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
	
	private String getProtocolNameBySockType(Integer sockType){
		if(sockType != null){
			if((sockType | SOCK_STREAM) == SOCK_STREAM){
				return PROTOCOL_NAME_TCP;
			}else if((sockType | SOCK_DGRAM) == SOCK_DGRAM){
				return PROTOCOL_NAME_UDP;
			}
		}
		return null;
	}
		
	private void handleSocket(Map<String, String> eventData, SYSCALL syscall){
		// socket() receives the following message(s):
		// - SYSCALL
		// - EOE
		String sockFd = eventData.get(AuditEventReader.EXIT);
		Integer socketType = CommonFunctions.parseInt(eventData.get(AuditEventReader.ARG1), null);
		String protocolName = getProtocolNameBySockType(socketType);
		
		if(protocolName != null){
			String pid = eventData.get(AuditEventReader.PID);

			NetworkSocketIdentifier identifierForProtocol = new NetworkSocketIdentifier(
					null, null, null, null, protocolName);
			processManager.setFd(pid, sockFd, identifierForProtocol, null); // no close edge
		}
	}
	
	private void putBind(String pid, String fd, ArtifactIdentifier identifier){
		if(identifier != null){
			// no need to add to descriptors because we will have the address from other syscalls? TODO
			processManager.setFd(pid, fd, identifier, null);
			if(identifier instanceof UnixSocketIdentifier){
				markNewEpochForArtifact(identifier);
			}
		}
	}
	
	private void logInvalidSaddr(String saddr, String time, String eventId, SYSCALL syscall){
		if(!"0100".equals(saddr)){ // if not empty path
			log(Level.INFO, "Failed to parse saddr: " + saddr, null, time, eventId, syscall);
		}
	}
	
	// needed for marking epoch for unix socket
	private void handleBindKernelModule(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String exit, String sockFd, int sockType, String localSaddr, String remoteSaddr){
		boolean isNetwork = isNetworkSaddr(localSaddr) || isNetworkSaddr(remoteSaddr);
		if(!isNetwork){
			// is unix
			ArtifactIdentifier identifier = parseUnixSaddr(localSaddr); // local or remote. any is fine.
			if(identifier != null){
				putBind(pid, sockFd, identifier);
			}else{
				logInvalidSaddr(remoteSaddr, time, eventId, syscall);
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
							addressPort.address, addressPort.port, null, null, protocolName);
				}
			}else if(isUnixSaddr(saddr)){
				identifier = parseUnixSaddr(saddr);
			}
			
			if(identifier == null){
				logInvalidSaddr(saddr, time, eventId, syscall);
			}else{
				putBind(pid, sockFd, identifier);
			}
		}
	}
	
	private NetworkSocketIdentifier constructNetworkIdentifier(SYSCALL syscall, String time, String eventId, 
			String localSaddr, String remoteSaddr, Integer sockType){
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
					remoteAddress, remotePort, protocolName);
		}
		return null;
	}
		
	private void putConnect(SYSCALL syscall, String time, String eventId, String pid, String fd, 
			ArtifactIdentifier fdIdentifier, Map<String, String> eventData){
		if(fdIdentifier != null){
			if(fdIdentifier instanceof NetworkSocketIdentifier){
				markNewEpochForArtifact(fdIdentifier);
			}
			processManager.setFd(pid, fd, fdIdentifier, false);
			
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact artifact = putArtifact(eventData, fdIdentifier, null, true);
			WasGeneratedBy wgb = new WasGeneratedBy(artifact, process);
			putEdge(wgb, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
			
			if(REFINE_NET){
				putWasDerivedFromEdgeFromNetworkArtifacts(artifact);
			}
		}
	}
	
	private void handleConnectKernelModule(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String exit, String sockFd, int sockType, String localSaddr, String remoteSaddr){
		// if not network then unix. Only that being handled in kernel module
		boolean isNetwork = isNetworkSaddr(localSaddr) || isNetworkSaddr(remoteSaddr);
		
		ArtifactIdentifier identifier = null;
		
		if(isNetwork){
			identifier = constructNetworkIdentifier(syscall, time, eventId, localSaddr, remoteSaddr, sockType);
		}else{ // is unix socket
			identifier = parseUnixSaddr(remoteSaddr); // address in remote unlike accept
			if(identifier == null){
				logInvalidSaddr(localSaddr, time, eventId, syscall);
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
		
		Integer exit = CommonFunctions.parseInt(eventData.get(AuditEventReader.EXIT), null);
		if(exit == null){
			log(Level.WARNING, "Failed to parse exit value: " + eventData.get(AuditEventReader.EXIT), 
					null, time, eventId, syscall);
			return;
		}else{ // not null
			// only handling if success is 0 or success is EINPROGRESS
			if(exit != 0 // no success
					&& exit != EINPROGRESS){ //in progress with possible failure in the future. see manpage.
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
							null, null, addressPort.address, addressPort.port, protocolName);
					
				}
			}else if(isUnixSaddr(saddr)){
				identifier = parseUnixSaddr(saddr);
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
				markNewEpochForArtifact(fdIdentifier);
			}
			
			processManager.setFd(pid, fd, fdIdentifier, false);
			
			Process process = processManager.handleProcessFromSyscall(eventData);
			Artifact socket = putArtifact(eventData, fdIdentifier, null, false);
			Used used = new Used(process, socket);
			putEdge(used, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
			
			if(REFINE_NET){
				putWasDerivedFromEdgeFromNetworkArtifacts(socket);
			}
		}
	}
	
	private void handleAcceptKernelModule(Map<String, String> eventData, String time, String eventId, SYSCALL syscall,
			String pid, String fd, String sockFd, int sockType, String localSaddr, String remoteSaddr){
		// if not network then unix. Only that being handled in kernel module
		boolean isNetwork = isNetworkSaddr(localSaddr) || isNetworkSaddr(remoteSaddr);
		
		ArtifactIdentifier identifier = null;
		if(isNetwork){
			identifier = constructNetworkIdentifier(syscall, time, eventId, localSaddr, remoteSaddr, sockType);
		}else{ // is unix socket
			identifier = parseUnixSaddr(localSaddr);
			if(identifier == null){
				logInvalidSaddr(localSaddr, time, eventId, syscall);
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
			ArtifactIdentifier boundIdentifier = processManager.getFd(pid, sockFd); // sockFd
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
							addressPort.address, addressPort.port, protocol);
				}
			}else if(isUnixSaddr(saddr)){
				// The unix saddr in accept is empty. So, use the bound one.
				if(boundIdentifier != null){
					if(boundIdentifier.getClass().equals(UnixSocketIdentifier.class)){
						// Use a new one because the wasOpenedForRead is updated otherwise it would
						// be updated in the bound identifier too.
						identifier = new UnixSocketIdentifier(((UnixSocketIdentifier)boundIdentifier).getPath());
					}else{
						log(Level.INFO, "Expected unix identifier but found: " + 
								boundIdentifier.getClass(), null, time, eventId, syscall);
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
	
	private void putWasDerivedFromEdgeFromNetworkArtifacts(Artifact syscallArtifact){
		if(syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE).equals(OPMConstants.SUBTYPE_NETWORK_SOCKET)){
			String remoteAddress = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS);
			String remotePort = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_REMOTE_PORT);
			Map<String, String> netfilterAnnotations = getNetworkAnnotationsSeenInList(
					networkAnnotationsFromNetfilter, remoteAddress, remotePort);
			if(netfilterAnnotations != null){
				String localPortFromSyscall = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_LOCAL_PORT);
	    		if(localPortFromSyscall != null && !localPortFromSyscall.trim().isEmpty()){
	    			if(!localPortFromSyscall.equals(netfilterAnnotations.get(OPMConstants.ARTIFACT_LOCAL_PORT))){
	    				// different connection
	    				// basically further pruning
	    				networkAnnotationsFromSyscalls.add(syscallArtifact.getAnnotations());
	    				return;
	    			}
	    		}
				
	    		// remove the annotations map from netfilter because we are going to consume that.
	    		// removing that here because the map is going to be updated below.
	    		networkAnnotationsFromNetfilter.remove(netfilterAnnotations);
	    		
				// logic for deduplication deviates from the current one
	    		// standardize this too and handling of netfilter in syscall functions. TODO
	    		String epoch = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_EPOCH);
	    		String version = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_VERSION);
	    		if(epoch != null){
	    			netfilterAnnotations.put(OPMConstants.ARTIFACT_EPOCH, epoch);
	    		}
	    		if(version != null){
	    			netfilterAnnotations.put(OPMConstants.ARTIFACT_VERSION, version);
	    		}
	    		
	    		String netfilterTime = netfilterAnnotations.remove(OPMConstants.EDGE_TIME); //remove
	    		String netfilterEventId = netfilterAnnotations.remove(OPMConstants.EDGE_EVENT_ID); //remove
				
	    		Artifact netfilterArtifact = new Artifact();
	    		netfilterArtifact.addAnnotations(netfilterAnnotations);
	    		putVertex(netfilterArtifact);

				WasDerivedFrom edge = new WasDerivedFrom(netfilterArtifact, syscallArtifact);
				putEdge(edge, getOperation(SYSCALL.UPDATE), netfilterTime, netfilterEventId, OPMConstants.SOURCE_AUDIT_NETFILTER);
				
				matchedSyscallNetfilter++;
			}else{
				networkAnnotationsFromSyscalls.add(syscallArtifact.getAnnotations());
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
								addressPort.address, addressPort.port, PROTOCOL_NAME_UDP);
					}
				}else if(isUnixSaddr(saddr)){
					identifier = parseUnixSaddr(saddr);
					// just use this
				}
			}
		}else{
			// use fd
			identifier = processManager.getFd(pid, fd);
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
					constructNetworkIdentifier(syscall, time, eventId, localSaddr, remoteSaddr, sockType);
			ArtifactIdentifier fdIdentifier = processManager.getFd(pid, sockFd);
			if(fdIdentifier instanceof NetworkSocketIdentifier){
				NetworkSocketIdentifier fdNetworkIdentifier = (NetworkSocketIdentifier)fdIdentifier;
				if(CommonFunctions.isNullOrEmpty(fdNetworkIdentifier.getRemoteHost())){
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
			identifier = parseUnixSaddr(localSaddr);
			if(identifier == null){
				logInvalidSaddr(localSaddr, time, eventId, syscall);
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
			if(identifier instanceof UnixSocketIdentifier){
				if(!UNIX_SOCKETS){
					return;
				}
			}
		}
		
		if(identifier == null){
			identifier = addUnknownFd(pid, fd);
		}
		
		if(isNetworkUdp){
			// Since saddr present that means that it is SOCK_DGRAM.
			// Epoch for all SOCK_DGRAM
			markNewEpochForArtifact(identifier);
		}

		Process process = processManager.handleProcessFromSyscall(eventData);
		Artifact artifact = null;
		AbstractEdge edge = null;
		if(incoming){
			artifact = putArtifact(eventData, identifier, null, false);
			edge = new Used(process, artifact);
		}else{
			artifact = putArtifact(eventData, identifier, null, true);
			edge = new WasGeneratedBy(artifact, process);
		}
		edge.addAnnotation(OPMConstants.EDGE_SIZE, bytesTransferred);
		if(offset != null){
			edge.addAnnotation(OPMConstants.EDGE_OFFSET, offset);	
		}
		putEdge(edge, getOperation(syscall), time, eventId, AUDIT_SYSCALL_SOURCE);
		
		// UDP
		if(isNetworkUdp && REFINE_NET){
			putWasDerivedFromEdgeFromNetworkArtifacts(artifact);
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
			if(!UNIX_SOCKETS && 
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
				OPMConstants.SUBTYPE_UNIX_SOCKET.equals(vertex.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE));
	}

	/**
	 * To be used where an absolute path needs to be created in system calls like renameat, openat, linkat, and etc.
	 * 
	 * Constructs an absolute path from given params using the following rules:
	 * 
	 * 1) If path absolute then return that path
	 * 2) If path not absolute and fd == AT_FDCWD and cwd != null then concatenate cwd with path and return that
	 * 3) If path not absolute and fd is a valid existing FILE descriptor then get the path from fd, concatenate 
	 * this path from fd and the passed path and return that.
	 * 
	 * @param path path of the file as gotten in the audit log
	 * @param fdString fd of the directory. Should be in decimal format and a valid number to be usable
	 * @param cwd current working directory
	 * @param pid process id
	 * @param time time of the audit event
	 * @param eventId event id as gotten in the audit log. Used for logging.
	 * @param syscall system call from where this function is called. Used for logging.
	 * @return
	 */
	private String constructPathSpecial(String path, String fdString, String cwd, String pid, String time, String eventId, SYSCALL syscall){
		if(path == null){
			log(Level.INFO, "Missing PATH record", null, time, eventId, syscall);
			return null;
		}else if(path.startsWith(File.separator)){ //is absolute
			return constructAbsolutePath(path, cwd, pid); //just getting the path resolved if it has .. or .
		}else{ //is not absolute
			if(fdString == null){
				log(Level.INFO, "Missing FD", null, time, eventId, syscall);
				return null;
			}else{
				Long fd = CommonFunctions.parseLong(fdString, -1L);
				if(fd == AT_FDCWD){
					if(cwd == null){
						log(Level.INFO, "Missing CWD record", null, time, eventId, syscall);
						return null;
					}else{
						path = constructAbsolutePath(path, cwd, pid);
						return path;
					}
				}else{
					ArtifactIdentifier artifactIdentifier = processManager.getFd(pid, String.valueOf(fd));
					if(artifactIdentifier == null){
						log(Level.INFO, "No FD with number '"+fd+"' for pid '"+pid+"'", null, time, eventId, syscall);
						return null;
					}else if(!(artifactIdentifier instanceof IdentifierWithPath)){
						log(Level.INFO, "FD with number '"+fd+"' for pid '"+pid+"' must be of type file but is '"+artifactIdentifier.getClass()+"'", null, time, eventId, syscall);
						return null;
					}else{
						path = constructAbsolutePath(path, ((IdentifierWithPath)artifactIdentifier).getPath(), pid);
						if(path == null){
							log(Level.INFO, "Invalid path ("+((IdentifierWithPath)artifactIdentifier).getPath()+") for fd with number '"+fd+"' of pid '"+pid+"'", null, time, eventId, syscall);
							return null;
						}else{
							return path;
						}
					}
				}
			}
		}    	
	}

	/**
	 * Creates the artifact according to the rules decided on for the current version of Audit reporter
	 * and then puts the artifact in the buffer at the end if it wasn't put before.
	 * 
	 * @param eventData a map that contains the keys eventid, time, and pid. Used for creating the UPDATE edge. 
	 * @param artifactIdentifier artifact to create
	 * @param permissions permissions as found in the 'mode' key in the PATH audit record (or null)
	 * @param updateVersion true or false to tell if the version has to be updated. Is modified based on the rules in the function
	 * @return the created artifact
	 */
	private Artifact putArtifact(Map<String, String> eventData, 
			ArtifactIdentifier artifactIdentifier, String permissions,
			boolean updateVersion){
		return putArtifact(eventData, artifactIdentifier, permissions, updateVersion, null);
	}

	/**
	 * Creates the artifact according to the rules decided on for the current version of Audit reporter
	 * and then puts the artifact in the buffer at the end if it wasn't put before.
	 * 
	 * Rules:
	 * 1) If unix socket identifier and unix sockets disabled using {@link #UNIX_SOCKETS UNIX_SOCKETS} then null returned
	 * 2) If useThisSource param is null then {@link #DEV_AUDIT DEV_AUDIT} is used
	 * 3) If file identifier, pipe identifier or unix socket identifier and path starts with /dev then set updateVersion to false
	 * 4) If network socket identifier versioning is false then set updateVersion to false
	 * 5) If memory identifier then don't put the epoch annotation
	 * 6) Put vertex to buffer if not added before. We know if it is added before or not based on updateVersion and if version wasn't initialized before this call
	 * 7) Draw version update edge if file identifier and version has been updated
	 * 
	 * NEW: 
	 * 8) If KEEP_VERSIONS is false then version annotation not added and WDF edge between old and new version not drawn
	 * 9) If KEEP_EPOCHS is false then epoch annotation not added
	 * 10) If KEEP_ARTIFACT_PROPERTIES_MAP is false then version and epoch annotations NOT added
	 * 
	 * @param eventData a map that contains the keys eventid, time, and pid. Used for creating the UPDATE edge. 
	 * @param artifactIdentifier artifact to create
	 * @param permissions permissions as found in the 'mode' key in the PATH audit record (or null)
	 * @param updateVersion true or false to tell if the version has to be updated. Is modified based on the rules in the function
	 * @param useThisSource the source value to use. if null then {@link #DEV_AUDIT DEV_AUDIT} is used.
	 * @return the created artifact
	 */
	private Artifact putArtifact(Map<String, String> eventData, 
			ArtifactIdentifier artifactIdentifier, String permissions, 
			boolean updateVersion, String useThisSource){
		if(artifactIdentifier == null){
			return null;
		}
		
		Class<? extends ArtifactIdentifier> artifactIdentifierClass = artifactIdentifier.getClass();
		
		Artifact artifact = new Artifact();
		artifact.addAnnotation(OPMConstants.ARTIFACT_SUBTYPE, artifactIdentifier.getSubtype());
		artifact.addAnnotations(artifactIdentifier.getAnnotationsMap());

		if(useThisSource != null){
			artifact.addAnnotation(OPMConstants.SOURCE, useThisSource);
		}else{
			artifact.addAnnotation(OPMConstants.SOURCE, AUDIT_SYSCALL_SOURCE);
		}

		// Only consult global flags if updateVersion was true otherwise we are not going to version anyway
		if(updateVersion){
			if(VERSION_ARTIFACTS == null){
				if(FileIdentifier.class.equals(artifactIdentifierClass)
						|| DirectoryIdentifier.class.equals(artifactIdentifierClass)
						|| BlockDeviceIdentifier.class.equals(artifactIdentifierClass)
						|| CharacterDeviceIdentifier.class.equals(artifactIdentifierClass)
						|| LinkIdentifier.class.equals(artifactIdentifierClass)){
					updateVersion = VERSION_FILES;
				}else if(MemoryIdentifier.class.equals(artifactIdentifierClass)){
					updateVersion = VERSION_MEMORYS;
				}else if(NamedPipeIdentifier.class.equals(artifactIdentifierClass)){
					updateVersion = VERSION_NAMED_PIPES;
				}else if(UnnamedPipeIdentifier.class.equals(artifactIdentifierClass)){
					updateVersion = VERSION_UNNAMED_PIPES;
				}else if(UnknownIdentifier.class.equals(artifactIdentifierClass)){
					updateVersion = VERSION_UNKNOWNS;
				}else if(NetworkSocketIdentifier.class.equals(artifactIdentifierClass)){
					updateVersion = VERSION_NETWORK_SOCKETS;
				}else if(UnixSocketIdentifier.class.equals(artifactIdentifierClass)){
					updateVersion = VERSION_UNIX_SOCKETS;
				}else{
					logger.log(Level.WARNING, "Unexpected artifact type: {0}", new Object[]{artifactIdentifierClass});
				}
			}else if(VERSION_ARTIFACTS){ //if true
				updateVersion = true;
			}else if(!VERSION_ARTIFACTS){ //if false
				updateVersion = false;
			}
		}
		
		// Special case: if /dev prefix path then don't version
		if(artifactIdentifier instanceof IdentifierWithPath){
			String path = artifact.getAnnotation(OPMConstants.ARTIFACT_PATH);
			if(path != null){
				if(updateVersion && path.startsWith("/dev/")){ //need this check for path based identities
					updateVersion = false;
				}
			}
		}
		
		if(KEEP_ARTIFACT_PROPERTIES_MAP){
			
			/*
			 * Going to keep all the state if map is being used and just going to choose not to 
			 * add the relevant annotations
			 */

			Set<String> syntheticAnnotations = new HashSet<String>();
			syntheticAnnotations.add(OPMConstants.ARTIFACT_VERSION);
			syntheticAnnotations.add(OPMConstants.ARTIFACT_EPOCH);
			syntheticAnnotations.add(OPMConstants.ARTIFACT_PERMISSIONS);
			
			Map<String, Boolean> uninitialized = new HashMap<String, Boolean>();
			Map<String, Boolean> updated = new HashMap<String, Boolean>();
			Map<String, Boolean> added = new HashMap<String, Boolean>();
			
			// Add the default values
			added.put(OPMConstants.ARTIFACT_VERSION, false);
			added.put(OPMConstants.ARTIFACT_EPOCH, false);
			added.put(OPMConstants.ARTIFACT_PERMISSIONS, false);
			
			ArtifactProperties artifactProperties = getArtifactProperties(artifactIdentifier);
			
			if(KEEP_VERSIONS && updateVersion){
				artifactProperties.clearAllPermissionsExceptCurrent();
			}

			// Set if annotations have been initialized or not
			// Version can be reinitialized
			uninitialized.put(OPMConstants.ARTIFACT_VERSION, artifactProperties.isVersionUninitialized());
			uninitialized.put(OPMConstants.ARTIFACT_EPOCH, artifactProperties.isEpochUninitialized());
			uninitialized.put(OPMConstants.ARTIFACT_PERMISSIONS, artifactProperties.isPermissionsUninitialized());
			
			// Set if annotations have been updated or not
			updated.put(OPMConstants.ARTIFACT_VERSION, updateVersion || artifactProperties.isEpochPending());
			updated.put(OPMConstants.ARTIFACT_EPOCH, artifactProperties.isEpochPending());
			boolean permissionsUpdated = permissions != null && !artifactProperties.permissionsSeenBefore(permissions);
			updated.put(OPMConstants.ARTIFACT_PERMISSIONS, permissionsUpdated);
			
			// Get values for annotations / this also initializes the annotations
			Long version = artifactProperties.getVersion(updateVersion);
			Long epoch = artifactProperties.getEpoch();
			artifactProperties.initializePermissions();
			String previousPermissions = artifactProperties.getCurrentPermissions();
			if(permissions == null){
				permissions = artifactProperties.getCurrentPermissions();
			}else{
				artifactProperties.setCurrentPermissions(permissions);
			}
			
			if(KEEP_VERSIONS){
				added.put(OPMConstants.ARTIFACT_VERSION, true);
				artifact.addAnnotation(OPMConstants.ARTIFACT_VERSION, 
						String.valueOf(version));
			}
			
			if(KEEP_EPOCHS){
				if(!artifactIdentifierClass.equals(MemoryIdentifier.class)){
					added.put(OPMConstants.ARTIFACT_EPOCH, true);
					artifact.addAnnotation(OPMConstants.ARTIFACT_EPOCH, 
							String.valueOf(epoch));
				}
			}
			
			if(KEEP_PATH_PERMISSIONS){
				// Permissions for only path based ones
				if(artifactIdentifier instanceof IdentifierWithPath){
					if(permissions != null){
						added.put(OPMConstants.ARTIFACT_PERMISSIONS, true);
						artifact.addAnnotation(OPMConstants.ARTIFACT_PERMISSIONS, permissions);
					}
				}
			}
			
			boolean callPutVertex = false;
			boolean allUninitialized = true;
			
			for(String syntheticAnnotation : syntheticAnnotations){
				
				boolean callPutVertexForThisAnnotation = false;
				if(uninitialized.get(syntheticAnnotation)
						|| updated.get(syntheticAnnotation)){
					if(added.get(syntheticAnnotation)){
						callPutVertexForThisAnnotation = true;
					}
				}
				callPutVertex = callPutVertex || callPutVertexForThisAnnotation;
				allUninitialized = allUninitialized && uninitialized.get(syntheticAnnotation);
				
			}
			
			callPutVertex = callPutVertex || allUninitialized;
			
			if(isUnixSocketArtifact(artifact) && !UNIX_SOCKETS){
				// Don't do anything
			}else{
				
				// Version / permissions update edge only if the vertex is actually added
				
				if(callPutVertex){
					putVertex(artifact);
					if(!updated.get(OPMConstants.ARTIFACT_EPOCH)){	
						if((KEEP_VERSIONS && updated.get(OPMConstants.ARTIFACT_VERSION) && added.get(OPMConstants.ARTIFACT_VERSION) && version > -1)
								|| (KEEP_PATH_PERMISSIONS && updated.get(OPMConstants.ARTIFACT_PERMISSIONS) && added.get(OPMConstants.ARTIFACT_PERMISSIONS))){
							if(artifactIdentifier instanceof FileIdentifier){
								putVersionPermissionsUpdateEdge(artifact, eventData.get(AuditEventReader.TIME), 
										eventData.get(AuditEventReader.EVENT_ID), eventData.get(AuditEventReader.PID), 
										version - 1, previousPermissions);
							}
						}
					}
				}
			}			
		}else{
			// If not keeping the artifacts properties map then no way of telling if we should 
			// call putVertex or not again (possible duplication). So, calling putVertex each time
			if(isUnixSocketArtifact(artifact) && !UNIX_SOCKETS){
				// don't add
			}else{
				putVertex(artifact);
			}
		}

		return artifact;
	}
	
	/**
	 * Creates a version or permissions update edge i.e. from the new version/permissions of an artifact 
	 * to the old version/permissions of the same artifact.
	 * At the moment only being done for file, namedpipes, and unix socket artifacts. (only for path based artifacts)
	 * See {@link #putArtifact(Map, ArtifactIdentifier, boolean, String) putArtifact}.
	 * 
	 * If the previous version number of the artifact is less than 0 then the edge won't be drawn because that means that there was
	 * no previous version. 
	 * 
	 * Doesn't put the two artifact vertices because they should already have been added from where this function is called
	 * 
	 * Rules:
	 * 1) If epoch was pending and has been incremented then we don't draw this update edge
	 * 2) 
	 * 
	 * 
	 * @param newArtifact artifact which has the updated version
	 * @param time timestamp when this happened
	 * @param eventId event id of the new version of the artifact creation
	 * @param pid pid of the process which did the update
	 */
	private void putVersionPermissionsUpdateEdge(Artifact newArtifact,
			String time, String eventId, String pid,
			long previousVersion, String previousPermissions){
		Artifact oldArtifact = new Artifact();
		oldArtifact.addAnnotations(newArtifact.getAnnotations());
		if(KEEP_VERSIONS && previousVersion > -1){
			oldArtifact.addAnnotation(OPMConstants.ARTIFACT_VERSION, String.valueOf(previousVersion));
		}
		if(KEEP_PATH_PERMISSIONS && previousPermissions != null){
			oldArtifact.addAnnotation(OPMConstants.ARTIFACT_PERMISSIONS, String.valueOf(previousPermissions));
		}
		WasDerivedFrom versionUpdate = new WasDerivedFrom(newArtifact, oldArtifact);
		versionUpdate.addAnnotation(OPMConstants.EDGE_PID, pid);
		putEdge(versionUpdate, getOperation(SYSCALL.UPDATE), time, eventId, AUDIT_SYSCALL_SOURCE);
	}

	/**
	 * Returns artifact properties for the given artifact identifier. If there is no entry for the artifact identifier
	 * then it adds one for it and returns that. Simply observing it would modify it. Access the data structure 
	 * {@link #artifactIdentifierToArtifactProperties artifactIdentifierToArtifactProperties} directly if need to see
	 * if an entry for the given key exists.
	 * 
	 * @param artifactIdentifier artifact identifier object to get properties of
	 * @return returns artifact properties in the map
	 */
	private ArtifactProperties getArtifactProperties(ArtifactIdentifier artifactIdentifier){
		ArtifactProperties artifactProperties = artifactIdentifierToArtifactProperties.get(artifactIdentifier);
		if(artifactProperties == null){
			artifactProperties = new ArtifactProperties();
		}
		artifactIdentifierToArtifactProperties.put(artifactIdentifier, artifactProperties);
		return artifactProperties;
	}
	
	/**
	 * Get the corresponding artifact properties for the artifact identifier and marks a new epoch
	 * on that. If {@link #KEEP_ARTIFACT_PROPERTIES_MAP KEEP_ARTIFACT_PROPERTIES_MAP} is true 
	 * then epoch marked otherwise returns without doing anything.
	 * 
	 * @param artifactIdentifier artifact identifier to get the properties of
	 */
	private void markNewEpochForArtifact(ArtifactIdentifier artifactIdentifier){
		if(KEEP_ARTIFACT_PROPERTIES_MAP){
			if(KEEP_EPOCHS){			
				getArtifactProperties(artifactIdentifier).markNewEpoch();
			}
		}
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
	
	private UnixSocketIdentifier parseUnixSaddr(String saddr){
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
			return new UnixSocketIdentifier(path);
		}else{
			return null;
		}
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
	
	/**
	 * Every path record in Audit log has a key 'nametype' which can be used to identify what the artifact
	 * referenced in the path record was.
	 * 
	 * Possible options for nametype:
	 * 1) PARENT -> parent directory of the path
	 * 2) CREATE -> path was created
	 * 3) NORMAL -> path was just used for read or write
	 * 4) DELETE -> path was deleted
	 * 5) UNKNOWN -> can't tell what was done with the path. So far seen that it only happens when a syscall fails.
	 * 
	 * Returns empty if none found
	 *  
	 * @param eventData eventData that contains the paths information in the format: path[Index], nametype[Index]. example, path0, nametype0 
	 * @param nametypeValue one of the above-mentioned values. Case sensitive compare operation on nametypeValue
	 * @return returns a list PathRecord objects sorted by their index in ascending order
	 */
	private List<PathRecord> getPathsWithNametype(Map<String, String> eventData, String nametypeValue){
		List<PathRecord> pathRecords = new ArrayList<PathRecord>();
		if(eventData != null && nametypeValue != null){
			Long items = CommonFunctions.parseLong(eventData.get(AuditEventReader.ITEMS), 0L);
			for(int itemcount = 0; itemcount < items; itemcount++){
				if(nametypeValue.equals(eventData.get(AuditEventReader.NAMETYPE_PREFIX+itemcount))){
					PathRecord pathRecord = new PathRecord(itemcount, 
							eventData.get(AuditEventReader.PATH_PREFIX+itemcount), 
							eventData.get(AuditEventReader.NAMETYPE_PREFIX+itemcount), 
							eventData.get(AuditEventReader.MODE_PREFIX+itemcount));
					pathRecords.add(pathRecord);
				}
			}
		}
		Collections.sort(pathRecords);
		return pathRecords;
	}

	/**
	 * Returns the first PathRecord object where nametypeValue matches i.e. one with the lowest index
	 * 
	 * Returns null if none found
	 * 
	 * @param eventData eventData that contains the paths information in the format: path[Index], nametype[Index]. example, path0, nametype0 
	 * @param nametypeValue one of the above-mentioned values. Case sensitive compare operation on nametypeValue
	 * @return returns the PathRecord object with the lowest index
	 */
	private PathRecord getFirstPathWithNametype(Map<String, String> eventData, String nametypeValue){
		List<PathRecord> pathRecords = getPathsWithNametype(eventData, nametypeValue);
		if(pathRecords == null || pathRecords.size() == 0){
			return null;
		}else{
			return pathRecords.get(0);
		}
	}
	
}

class AddressPort{
	public final String address, port;
	public AddressPort(String address, String port){
		this.address = address;
		this.port = port;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + ((port == null) ? 0 : port.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AddressPort other = (AddressPort) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		if (port == null) {
			if (other.port != null)
				return false;
		} else if (!port.equals(other.port))
			return false;
		return true;
	}
}

/**
 * A class to represent all of Path record data as received from Audit logs
 * 
 * Implements Comparable interface on the index field. And is immutable.
 * 
 */
class PathRecord implements Comparable<PathRecord>{

	/**
	 * Value of the name field in the audit log
	 */
	private String path;
	/**
	 * Value of the item field in the audit log
	 */
	private int index;
	/**
	 * Value of the nametype field in the audit log
	 */
	private String nametype;
	/**
	 * Value of the mode field in the audit log
	 */
	private String mode;
	/**
	 * Extracted from the mode variable by parsing it with base-8
	 */
	private int pathType = 0;
	/**
	 * Extracted from the mode variable
	 */
	private String permissions = null;
	
	public PathRecord(int index, String path, String nametype, String mode){
		this.index = index;
		this.path = path;
		this.nametype = nametype;
		this.mode = mode;
		this.pathType = parsePathType(mode);
		this.permissions = parsePermissions(mode);
	}
	
	/**
	 * Parses the string mode into an integer with base 8
	 * 
	 * @param mode base 8 representation of string
	 * @return integer value of mode
	 */
	public static int parsePathType(String mode){
		try{
			return Integer.parseInt(mode, 8);
		}catch(Exception e){
			return 0;
		}
	}
	
	/**
	 * Returns the last 4 characters in the mode string.
	 * If the length of the mode string is less than 4 than pads the
	 * remaining zeroes at the beginning of the return value.
	 * If the mode argument is null then null returned.
	 * @param mode mode string with last 4 characters as permissions
	 * @return only the last 4 characters or null
	 */
	public static String parsePermissions(String mode){
		if(mode != null){
			if(mode.length() >= 4){
				return mode.substring(mode.length() - 4);
			}else{
				int difference = 4 - mode.length();
				for(int a = 0; a< difference; a++){
					mode = "0" + mode;
				}
				return mode;
			}
		}
		return null;
	}
	
	public String getPermissions(){
		return permissions;
	}
	
	public int getPathType(){
		return pathType;
	}
	
	public String getPath(){
		return path;
	}
	
	public String getNametype(){
		return nametype;
	}
	
	public int getIndex(){
		return index;
	}
	
	/**
	 * Compares based on index. If the passed object is null then 1 returned always
	 */
	@Override
	public int compareTo(PathRecord o) {
		if(o != null){
			return this.index - o.index;
		}
		return 1;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		result = prime * result + ((mode == null) ? 0 : mode.hashCode());
		result = prime * result + ((nametype == null) ? 0 : nametype.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PathRecord other = (PathRecord) obj;
		if (index != other.index)
			return false;
		if (mode == null) {
			if (other.mode != null)
				return false;
		} else if (!mode.equals(other.mode))
			return false;
		if (nametype == null) {
			if (other.nametype != null)
				return false;
		} else if (!nametype.equals(other.nametype))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PathRecord [path=" + path + ", index=" + index + ", nametype=" + nametype + ", mode=" + mode + "]";
	}
}
