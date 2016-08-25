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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.reporter.audit.ArtifactIdentifier;
import spade.reporter.audit.ArtifactProperties;
import spade.reporter.audit.AuditEventReader;
import spade.reporter.audit.DescriptorManager;
import spade.reporter.audit.FileIdentifier;
import spade.reporter.audit.MemoryIdentifier;
import spade.reporter.audit.NamedPipeIdentifier;
import spade.reporter.audit.NetworkSocketIdentifier;
import spade.reporter.audit.SYSCALL;
import spade.reporter.audit.UnixSocketIdentifier;
import spade.reporter.audit.UnknownIdentifier;
import spade.reporter.audit.UnnamedPipeIdentifier;
import spade.utility.BerkeleyDB;
import spade.utility.CommonFunctions;
import spade.utility.Execute;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 *
 * IMPORTANT NOTE: To output OPM objects just once, use putProcess and putArtifact functions 
 * because they take care of that using internal data structures but more importantly those functions
 * contains the logic on how to create processes and artifacts which MUST be kept consistent across a run.
 *
 * @author Dawood Tariq, Sharjeel Ahmed Qureshi
 */
public class Audit extends AbstractReporter {

    static final Logger logger = Logger.getLogger(Audit.class.getName());

//  Following constant values are taken from:
//  http://lxr.free-electrons.com/source/include/uapi/linux/sched.h 
//  AND  
//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/signal.h
    private final int SIGCHLD = 17, CLONE_VFORK = 0x00004000, CLONE_VM = 0x00000100;
    
//  Following constant values are taken from:
//  http://lxr.free-electrons.com/source/include/uapi/linux/stat.h#L14
    private final int S_IFIFO = 0010000, S_IFREG = 0100000, S_IFSOCK = 0140000;
  
//  Following constant values are taken from:
//  http://lxr.free-electrons.com/source/include/uapi/linux/fcntl.h#L56
    private final int AT_FDCWD = -100;
  
//  Following constant values are taken from:
//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/fcntl.h#L19
    private final int O_RDONLY = 00000000, O_WRONLY = 00000001, O_RDWR = 00000002, O_CREAT = 00000100, O_TRUNC = 00001000;
  
    private final long BUFFER_DRAIN_DELAY = 500;
    
    private volatile boolean shutdown = false;
    private long boottime = 0;
    private final long THREAD_CLEANUP_TIMEOUT = 1000;
    private boolean USE_READ_WRITE = false;
    // To toggle monitoring of system calls: sendmsg, recvmsg, sendto, and recvfrom
    private boolean USE_SOCK_SEND_RCV = false;
    private Boolean ARCH_32BIT = true;
//    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    private String AUDIT_EXEC_PATH;
    // Process map based on <pid, stack of vertices> pairs
    private final Map<String, LinkedList<Process>> processUnitStack = new HashMap<String, LinkedList<Process>>();
    // Process version map. Versioning based on units. pid -> unitid -> iterationcount
    private final Map<String, Map<String, Long>> iterationNumber = new HashMap<String, Map<String, Long>>();

    private final DescriptorManager descriptors = new DescriptorManager();
    
    // Map for artifact infos to versions and bytes read/written on sockets 
    private ExternalMemoryMap<ArtifactIdentifier, ArtifactProperties> artifactIdentifierToArtifactProperties;
    
    private Thread eventProcessorThread = null;
    
    // Group 1: key
    // Group 2: value
    private static final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

    //  Added to indicate in the output from where the process info was read. Either from 
    //  1) procfs or directly from 2) audit log. 
    private static final String SOURCE = "source",
            PROC_FS = "/proc",
            DEV_AUDIT = "/dev/audit",
            BEEP = "beep";
    
    private Thread auditLogThread = null;
        
    private boolean CREATE_BEEP_UNITS = false;
        
    private Map<String, BigInteger> pidToMemAddress = new HashMap<String, BigInteger>(); 
    
    private final static String EVENT_ID = "event id";
    
    private boolean SIMPLIFY = true;
    private boolean PROCFS = false;
    
    private boolean NET_SOCKET_VERSIONING = false;
    
    private boolean UNIX_SOCKETS = false;
    
    private boolean WAIT_FOR_LOG_END = false;
    
    // Flag to use to decide whether to generate OPM objects for the following syscalls or not: exit, close, unlink
    private boolean CONTROL = true;
    
//  To toggle monitoring of mmap, mmap2 and mprotect syscalls
    private boolean USE_MEMORY_SYSCALLS = true;
    
    private String AUDITCTL_SYSCALL_SUCCESS_FLAG = "1";
    
    // Just a human-friendly renaming of null
    private final static String NOT_SET = null;
    // Timestamp that is used to identify when the time has changed in unit begin. Assumes that the timestamps are received in sorted order
    private String lastTimestamp = NOT_SET;
    // A map to keep track of counts of unit vertex with the same pid, unitid and iteration number.
    private Map<UnitVertexIdentifier, Integer> unitIterationRepetitionCounts = new HashMap<UnitVertexIdentifier, Integer>();
        
    //cache maps paths. global so that we delete on exit
    private String artifactsCacheDatabasePath;
    
    //pid to set of process hashes seen so far. Used to check if a process vertex has been added to internal buffer before or not
    private Map<String, Set<String>> pidToProcessHashes = new HashMap<String, Set<String>>();
    
    @Override
    public boolean launch(String arguments) {

        arguments = arguments == null ? "" : arguments;

        try {
            InputStream archStream = Runtime.getRuntime().exec("uname -i").getInputStream();
            BufferedReader archReader = new BufferedReader(new InputStreamReader(archStream));
            String archLine = archReader.readLine().trim();
            ARCH_32BIT = archLine.equals("i686");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error reading the system architecture", e);
            return false; //if unable to find out the architecture then report failure
        }

        AUDIT_EXEC_PATH = SPADE_ROOT + "lib/spadeSocketBridge";
        String auditOutputLog = SPADE_ROOT + "log/LinuxAudit.log";

        Map<String, String> args = parseKeyValPairs(arguments);
        if (args.containsKey("outputLog") && !args.get("outputLog").isEmpty()) {
        	auditOutputLog = args.get("outputLog");
        	if(!new File(auditOutputLog).getParentFile().exists()){
        		auditOutputLog = null;
        	}
        } else {
        	auditOutputLog = null;
        }
        
        final String auditOutputLogPath = auditOutputLog;
        
        // Check if file IO and net IO is also asked by the user to be turned on
        if ("true".equals(args.get("fileIO"))) {
            USE_READ_WRITE = true;
        }
        if ("true".equals(args.get("netIO"))) {
            USE_SOCK_SEND_RCV = true;
        }
        if("true".equals(args.get("units"))){
        	CREATE_BEEP_UNITS = true;
        }
        
        // Arguments below are only for experimental use
        if("false".equals(args.get("simplify"))){
        	SIMPLIFY = false;
        }
        if("true".equals(args.get("procFS"))){
        	PROCFS = true;
        }
        if("true".equals(args.get("unixSockets"))){
        	UNIX_SOCKETS = true;
        }
        if("true".equals(args.get("netSocketVersioning"))){
        	NET_SOCKET_VERSIONING = true;
        }
        if("true".equals(args.get("waitForLog"))){
        	WAIT_FOR_LOG_END = true;
        }
        if("false".equals(args.get("memorySyscalls"))){
        	USE_MEMORY_SYSCALLS = false;
        }
        if("0".equals(args.get("auditctlSuccessFlag"))){
        	AUDITCTL_SYSCALL_SUCCESS_FLAG = "0";
        }
        if("false".equals(args.get("control"))){
        	CONTROL = false;
        }
        // End of experimental arguments

//        initialize cache data structures
        
        if(!initCacheMaps()){
        	return false;
        }
        
        // Get system boot time from /proc/stat. This is later used to determine
        // the start time for processes.
        try {
            BufferedReader boottimeReader = new BufferedReader(new FileReader("/proc/stat"));
            String line;
            while ((line = boottimeReader.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);
                if (st.nextToken().equals("btime")) {
                    boottime = Long.parseLong(st.nextToken()) * 1000;
                    break;
                }
            }
            boottimeReader.close();
        } catch (IOException | NumberFormatException e) {
            logger.log(Level.WARNING, "Error reading boot time information from /proc/", e);
        }
        
        Long auditReaderWindowSizeValue = null;
        try{
        	Map<String, String> auditConfigProperties = FileUtility.readConfigFileAsKeyValueMap(Settings.getDefaultConfigFilePath(this.getClass()), "=");
        	if(auditConfigProperties.get("windowSize") == null){
        		logger.log(Level.SEVERE, "Undefined window size in audit config");
        		return false;
        	}else{
        		auditReaderWindowSizeValue = CommonFunctions.parseLong(auditConfigProperties.get("windowSize"), null);
        		if(auditReaderWindowSizeValue == null){
        			logger.log(Level.SEVERE, "Invalid window size defined in audit config");
        			return false;
        		}else{
        			if(auditReaderWindowSizeValue < 1){
        				logger.log(Level.SEVERE, "Window size must be greater than 1 (defined in audit config)");
            			return false;
        			}
        		}
        	}
        }catch(Exception e){
        	logger.log(Level.SEVERE, "Failed to read audit config file", e);
        	return false;
        }
        
        // the value being assigned cannot be null
        final long auditReaderWindowSize = auditReaderWindowSizeValue;
        
        String inputAuditLogFileArgument = args.get("inputLog");
        if(inputAuditLogFileArgument != null){ //if a path is passed but it is not a valid file then throw an error
        	
        	if(!new File(inputAuditLogFileArgument).exists()){
        		logger.log(Level.SEVERE, "Input audit log file at specified path doesn't exist.");
        		return false;
        	}
        	        	
        	ARCH_32BIT = null;
        	
        	if("32".equals(args.get("arch"))){
        		ARCH_32BIT = true;
        	}else if("64".equals(args.get("arch"))){
        		ARCH_32BIT = false;
        	}
        	
        	if(ARCH_32BIT == null){
        		logger.log(Level.SEVERE, "Must specify whether the system on which log was collected was 32 bit or 64 bit");
        		return false;
        	}
        	
        	boolean rotate = false;
            if("true".equals(args.get("rotate"))){
            	rotate = true;
            }
            
            final LinkedList<String> inputAuditLogFiles = new LinkedList<String>();
            inputAuditLogFiles.addFirst(inputAuditLogFileArgument); //add the file in the argument
            if(rotate){ //if rotate is true then add the rest too based on the decided convention
            	//convention: name format of files to be processed -> name.1, name.2 and so on where name is the name of the file passed in as argument
            	//can only process 99 logs
            	for(int logCount = 1; logCount<=99; logCount++){
            		if(new File(inputAuditLogFileArgument + "." + logCount).exists()){
            			inputAuditLogFiles.addFirst(inputAuditLogFileArgument + "." + logCount); //adding first so that they are added in the reverse order
            		}
            	}
            }
            
            logger.log(Level.INFO, "Total logs to process: " + inputAuditLogFiles.size() + " and list = " + inputAuditLogFiles);
                    	
        	auditLogThread = new Thread(new Runnable(){
    			public void run(){
    				
    				AuditEventReader auditEventReader = null;
    	        	try{
    	        		auditEventReader = new AuditEventReader(auditReaderWindowSize, inputAuditLogFiles.toArray(new String[]{}));
    	        		if(auditOutputLogPath != null){
    	        			auditEventReader.setOutputLog(new FileOutputStream(auditOutputLogPath));
    	        		}
    	        		Map<String, String> eventData = new HashMap<String ,String>();
    	        		while((!shutdown || WAIT_FOR_LOG_END) && (eventData = auditEventReader.readEventData()) != null){
    	        			finishEvent(eventData);
    	        		}
    	        		
    	        		// Possible cases to be here: 1) shutdown called i.e. log not read completely, 2) log read completely
    	        		// Let the buffer drain while checking for shutdown
        	        	while(getBuffer().size() > 0){
        	        		if(shutdown){
        	        			break;
        	        		}
        	        		try{
        	        			Thread.sleep(BUFFER_DRAIN_DELAY);
        	        		}catch(Exception e){
        	        			//logger.log(Level.SEVERE, null, e);
        	        		}
    					}
        	        	
        	        	if(eventData == null){ // Means that EOF was reached
    	        			if(getBuffer().size() == 0){ // Buffer emptied
    	        				logger.log(Level.INFO, "Audit log processing succeeded: " + inputAuditLogFiles);
    	        			}else{ // Buffer size isn't 0 then it means that shutdown was called
    	        				logger.log(Level.INFO, "Audit log processing succeeded partially: " + inputAuditLogFiles);
    	        			}
    	        		}else{ // Means that shutdown was called before EOF reached 
    	        			logger.log(Level.INFO, "Audit log processing succeeded partially: " + inputAuditLogFiles);
    	        		}
    	        	}catch(Exception e){
    	        		logger.log(Level.SEVERE, "Audit log processing failed: " + inputAuditLogFiles, e);
    	        	}finally{
    	        		try{
    	        			if(auditEventReader != null){
    	        				auditEventReader.close();
    	        			}
    	        		}catch(Exception e){
    	        			logger.log(Level.WARNING, "Failed to close audit input log reader", e);
    	        		}
    	        	}
    				//after processing all the files delete the cache maps or when shutdown has been called
    				deleteCacheMaps();
    			}
    		});
        	auditLogThread.start();
        	
        }else{
        	
        	buildProcFSTree();

	        try {
	            // Start auditd and clear existing rules.
	            Runtime.getRuntime().exec("auditctl -D").waitFor();
	            Runnable eventProcessor = new Runnable() {
	                public void run() {
	                    try {
	                        java.lang.Process auditProcess = Runtime.getRuntime().exec(AUDIT_EXEC_PATH);
	                        AuditEventReader auditEventReader = new AuditEventReader(auditReaderWindowSize, auditProcess.getInputStream());
	                        if(auditOutputLogPath != null){
	                        	auditEventReader.setOutputLog(new FileOutputStream(auditOutputLogPath));
	                        }
	                        while (!shutdown) {
	                            Map<String, String> eventData = auditEventReader.readEventData();
	                            if ((eventData != null)) {
	                            	finishEvent(eventData);
	                            }
	                        }
	                        //Added this command here because once the spadeSocketBridge process has exited any rules involving it cannot be cleared.
	                        //So, deleting the rules before destroying the spadeSocketBridge process.
	                        
	                        Runtime.getRuntime().exec("auditctl -D").waitFor();
	                        auditProcess.destroy();
	                        
	                        if(shutdown){
	                        	auditEventReader.stopReading();
	                        	Map<String, String> eventData = null;
	                        	while((eventData = auditEventReader.readEventData()) != null){
	                        		finishEvent(eventData);
	                        	}
	                        }
	                        
	                        auditEventReader.close();
	                    } catch (Exception e) {
	                        logger.log(Level.SEVERE, "Error launching main runnable thread", e);
	                    }finally{
	                    	deleteCacheMaps();
	                    }
	                }
	            };
	            eventProcessorThread = new Thread(eventProcessor, "Audit-Thread");
	            eventProcessorThread.start();
	
	            String auditRuleWithoutSuccess = "-a exit,always ";
	            String auditRulesWithSuccess = "-a exit,always ";
	            
	            if (ARCH_32BIT){
	            	auditRulesWithSuccess += "-F arch=b32 ";
	            	auditRuleWithoutSuccess += "-F arch=b32 ";
	            }else{
	            	auditRulesWithSuccess += "-F arch=b64 ";
	            	auditRuleWithoutSuccess += "-F arch=b64 ";
	            }
	            
	            auditRuleWithoutSuccess += "-S kill -S exit -S exit_group ";
	            
	            if (USE_READ_WRITE) {
	            	auditRulesWithSuccess += "-S read -S readv -S write -S writev ";
	            }
	            if (USE_SOCK_SEND_RCV) {
	            	auditRulesWithSuccess += "-S sendto -S recvfrom -S sendmsg -S recvmsg ";
	            }
	            if (USE_MEMORY_SYSCALLS) {
	            	auditRulesWithSuccess += "-S mmap -S mprotect ";
	            	if(ARCH_32BIT){
	            		auditRulesWithSuccess += "-S mmap2 ";
	            	}
	            }
	            auditRulesWithSuccess += "-S unlink -S unlinkat ";
	            auditRulesWithSuccess += "-S link -S linkat -S symlink -S symlinkat ";
	            auditRulesWithSuccess += "-S clone -S fork -S vfork -S execve ";
	            auditRulesWithSuccess += "-S open -S close -S creat -S openat -S mknodat -S mknod ";
	            auditRulesWithSuccess += "-S dup -S dup2 -S dup3 ";
	            auditRulesWithSuccess += "-S bind -S accept -S accept4 -S connect ";
	            auditRulesWithSuccess += "-S rename -S renameat ";
	            auditRulesWithSuccess += "-S setuid -S setreuid -S setresuid ";
	            auditRulesWithSuccess += "-S chmod -S fchmod -S fchmodat ";
	            auditRulesWithSuccess += "-S pipe -S pipe2 ";
	            auditRulesWithSuccess += "-S truncate -S ftruncate ";
	            auditRulesWithSuccess += "-F success=" + AUDITCTL_SYSCALL_SUCCESS_FLAG + " ";
	            //Find the pids of the processes to ignore (below) and the uid for the JVM
	            /*
	             * All these fields would have been added to the main auditctl rule as "-F pid!=xxxx -F ppid!=xxxx" but 
	             * only 64 fields are allowed per each auditctl rule.
	             * 
	             * Add whatever number of fields that can be added to the main auditctl rule and add the rest individual rules as: 
	             *  "auditctl -a exit,never -F pid=xxxx" and "auditctl -a exit,never -F ppid=xxxx"
	             *  
	             * Adding the uid rule always first
	             */
	            
	            String uid = getOwnUid();
	            
	            if(uid == null){
	            	shutdown = true;
	            	return false;
	            }
	            
	            auditRulesWithSuccess += "-F uid!=" + uid + " ";
	            auditRuleWithoutSuccess += "-F uid!=" + uid + " ";
	            
	            String ignoreProcesses = "auditd kauditd audispd spadeSocketBridge";
	            List<String> pidsToIgnore = listOfPidsToIgnore(ignoreProcesses);
	            
	            int maxFieldsAllowed = 64; //max allowed by auditctl command
	            //split the pre-formed rule on -F to find out the number of fields already present
	            int existingFieldsCount = auditRulesWithSuccess.split(" -F ").length - 1; 
	            
	            //find out the pids & ppids that can be added to the main rule from the list of pids. divided by two to account for pid and ppid fields for the same pid
	            int fieldsForAuditRuleCount = (maxFieldsAllowed - existingFieldsCount)/2; 
	            
	            //handling the case if the main rule can accommodate all pids in the list of pids to ignore 
	            int loopFieldsForMainRuleTill = Math.min(fieldsForAuditRuleCount, pidsToIgnore.size());
	            
	            String fieldsForAuditRule = "";
	            //build the pid and ppid  to ignore portion for the main rule
	            for(int a = 0; a<loopFieldsForMainRuleTill; a++){
	            	fieldsForAuditRule += " -F pid!=" +pidsToIgnore.get(a) + " -F ppid!=" + pidsToIgnore.get(a); 
	            }
	            
	            //add the remaining pids as individual rules
	            for(int a = fieldsForAuditRuleCount; a<pidsToIgnore.size(); a++){
	            	String pidIgnoreAuditRule = "auditctl -a exit,never -F pid="+pidsToIgnore.get(a);
	            	if(!addAuditctlRule(pidIgnoreAuditRule)){
	            		shutdown = true;
	            		return false;
	            	}
	            	
	            	String ppidIgnoreAuditRule = "auditctl -a exit,never -F ppid="+pidsToIgnore.get(a);
	            	if(!addAuditctlRule(ppidIgnoreAuditRule)){
	            		shutdown = true;
	            		return false;
	            	}
	            }
	            
            	if(!addAuditctlRule(auditRuleWithoutSuccess + fieldsForAuditRule)){
            		shutdown = true;
            		return false;
            	}
            	
            	//add the main rule. ALWAYS ADD THIS AFTER THE ABOVE INDIVIDUAL RULES HAVE BEEN ADDED TO AVOID INCLUSION OF AUDIT INFO OF ABOVE PIDS
            	if(!addAuditctlRule(auditRulesWithSuccess + fieldsForAuditRule)){
            		shutdown = true;
            		return false;
            	}
            	
	        } catch (Exception e) {
	            logger.log(Level.SEVERE, "Error configuring audit rules", e);
	            shutdown = true;
	            return false;
	        }

        }
        
        return true;
    }
    
    private void deleteCacheMaps(){
    	try{
    		if(artifactsCacheDatabasePath != null && new File(artifactsCacheDatabasePath).exists()){
    			FileUtils.forceDelete(new File(artifactsCacheDatabasePath));
    		}
    	}catch(Exception e){
    		logger.log(Level.WARNING, "Failed to delete cache maps at path: '"+artifactsCacheDatabasePath+"'");
    	}
    }
    
    private boolean addAuditctlRule(String auditctlRule){
    	try{
    		List<String> auditctlOutput = Execute.getOutput("auditctl " + auditctlRule);
        	logger.log(Level.INFO, "configured audit rules: {0} with ouput: {1}", new Object[]{auditctlRule, auditctlOutput});
        	if(outputHasError(auditctlOutput)){
        		removeAuditctlRules();
        		shutdown = true;
        		return false;
        	}
        	return true;
    	}catch(Exception e){
    		logger.log(Level.SEVERE, "Failed to add auditctl rule = " + auditctlRule, e);
    		return false;
    	}
    }
    
    private void removeAuditctlRules(){
    	try{
    		logger.log(Level.INFO, "auditctl -D... output = " + Execute.getOutput("auditctl -D"));
    	}catch(Exception e){
    		logger.log(Level.SEVERE, "Failed to remove auditctl rules", e);
    	}
    }
    
    /**
     * Used to tell if the output of a command gotten from Execute.getOutput function has errors or not
     * 
     * @param outputLines output lines received from Execute.getOutput
     * @return true if errors exist, otherwise false
     */
    private boolean outputHasError(List<String> outputLines){
    	if(outputLines != null){
	    	for(String outputLine : outputLines){
	    		if(outputLine != null){
	    			if(outputLine.contains("[STDERR]")){
	    				return true;
	    			}
	    		}
	    	}
    	}
    	return false;
    }
    
    private void buildProcFSTree(){
    	if(PROCFS){
	        // Build the process tree using the directories under /proc/.
	        // Directories which have a numeric name represent processes.
	        String path = "/proc";
	        java.io.File directory = new java.io.File(path);
	        java.io.File[] listOfFiles = directory.listFiles();
	        for (int i = 0; i < listOfFiles.length; i++) {
	            if (listOfFiles[i].isDirectory()) {
	
	                String currentPID = listOfFiles[i].getName();
	                try {
	                    // Parse the current directory name to make sure it is
	                    // numeric. If not, ignore and continue.
	                    Integer.parseInt(currentPID);
	                    Process processVertex = createProcessFromProcFS(currentPID); //create
	                    addProcess(currentPID, processVertex);//add to memory
	                    if(!processVertexHasBeenPutBefore(processVertex)){
	        	    		putVertex(processVertex);//add to internal buffer
	        			}
	                    Process parentVertex = getProcess(processVertex.getAnnotation("ppid"));
	                    if (parentVertex != null) {
	                    	WasTriggeredBy childToParent = new WasTriggeredBy(processVertex, parentVertex);
	                    	putEdge(childToParent, getOperation(SYSCALL.UNKNOWN), "0", null, PROC_FS);
	                    }
	
	                    // Get existing file descriptors for this process
	                    Map<String, ArtifactIdentifier> fds = getFileDescriptors(currentPID);
	                    if (fds != null) {
	                        descriptors.addDescriptors(currentPID, fds);
	                    }
	                } catch (Exception e) {
	                    // Continue
	                }
	            }
	        }
    	}
    }
    
    private boolean initCacheMaps(){
    	 try{
         	Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(Settings.getDefaultConfigFilePath(this.getClass()), "=");
         	long currentTime = System.currentTimeMillis(); 
             artifactsCacheDatabasePath = configMap.get("cacheDatabasePath") + File.separatorChar + "artifacts_" + currentTime;
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
    
    private List<String> listOfPidsToIgnore(String ignoreProcesses) {
    	
//    	ignoreProcesses argument is a string of process names separated by blank space
    	
    	List<String> pids = new ArrayList<String>();
        try {
        	if(ignoreProcesses != null && !ignoreProcesses.trim().isEmpty()){
//	            Using pidof command now to get all pids of the mentioned processes
	            java.lang.Process pidChecker = Runtime.getRuntime().exec("pidof " + ignoreProcesses);
//	            pidof returns pids of given processes as a string separated by a blank space
	            BufferedReader pidReader = new BufferedReader(new InputStreamReader(pidChecker.getInputStream()));
	            String pidline = pidReader.readLine();
//	            added all returned from pidof command
	            pids.addAll(Arrays.asList(pidline.split("\\s+")));
	            pidReader.close();
        	}
            
            return pids;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error building list of processes to ignore. Partial list: " + pids, e);
            return new ArrayList<String>();
        }
    }
    
    private String getOwnUid(){
    	try{
    		String uid = null;
    		List<String> outputLines = Execute.getOutput("id -u");
    		for(String outputLine : outputLines){
    			if(outputLine != null){
	    			if(outputLine.contains("[STDOUT]")){
	    				uid = outputLine.replace("[STDOUT]", "").trim(); 
	    			}else if(outputLine.contains("[STDERR]")){
	    				logger.log(Level.SEVERE, "Failed to get user id of JVM. Output = " + outputLines);
	    				return null;
	    			}
    			}
    		}
    		return uid;
    	}catch(Exception e){
    		logger.log(Level.SEVERE, "Failed to get user id of JVM", e);
    		return null;
    	}
    }
    
	private Map<String, ArtifactIdentifier> getFileDescriptors(String pid){
		
		if(auditLogThread != null  // the audit log is being read from a file.
				|| !PROCFS){ // the flag to read from procfs is false
			return null;
		}
    	
    	Map<String, ArtifactIdentifier> fds = new HashMap<String, ArtifactIdentifier>();
    	
    	Map<String, String> inodefd0 = new HashMap<String, String>();
    	
    	try{
    		//LSOF args -> n = no DNS resolution, P = no port user-friendly naming, p = pid of process
    		List<String> lines = Execute.getOutput("lsof -nPp " + pid);
    		if(lines != null && lines.size() > 1){
    			lines.remove(0); //remove the heading line
    			for(String line : lines){
    				String tokens[] = line.split("\\s+");
    				if(tokens.length >= 9){
    					String type = tokens[4].toLowerCase().trim();
    					String fd = tokens[3].trim();
    					fd = fd.replaceAll("[^0-9]", ""); //ends with r(read), w(write), u(read and write), W (lock)
    					if(CommonFunctions.parseInt(fd, null) != null){
	    					if("fifo".equals(type)){
	    						String path = tokens[8];
	    						if("pipe".equals(path)){ //unnamed pipe
	    							String inode = tokens[7];
		    						if(inodefd0.get(inode) == null){
		    							inodefd0.put(inode, fd);
		    						}else{
		    							ArtifactIdentifier pipeInfo = new UnnamedPipeIdentifier(pid, fd, inodefd0.get(inode));
		    							fds.put(fd, pipeInfo);
		    							fds.put(inodefd0.get(inode), pipeInfo);
		    							inodefd0.remove(inode);
		    						}
	    						}else{ //named pipe
	    							fds.put(fd, new NamedPipeIdentifier(path));
	    						}	    						
	    					}else if("ipv4".equals(type) || "ipv6".equals(type)){
	    						String protocol = tokens[7];
	    						//example of this token = 10.0.2.15:35859->172.231.72.152:443 (ESTABLISHED)
	    						String[] srchostport = tokens[8].split("->")[0].split(":");
	    						String[] dsthostport = tokens[8].split("->")[1].split("\\s+")[0].split(":");
	    						fds.put(fd, new NetworkSocketIdentifier(srchostport[0], srchostport[1], dsthostport[0], dsthostport[1], protocol));
	    					}else if("reg".equals(type) || "chr".equals(type)){
	    						String path = tokens[8];
	    						fds.put(fd, new FileIdentifier(path));  						
	    					}else if("unix".equals(type)){
	    						String path = tokens[8];
	    						if(!path.equals("socket")){
	    							fds.put(fd, new UnixSocketIdentifier(path));
	    						}
	    					}
    					}
    				}
    			}
    		}
    	}catch(Exception e){
    		logger.log(Level.WARNING, "Failed to get file descriptors for pid " + pid, e);
    	}
    	
    	return fds;
    }
	
	/*
     * Takes a string with keyvalue pairs and returns a Map Input e.g.
     * "key1=val1 key2=val2" etc. Input string validation is callee's
     * responsiblity
     */
    private static Map<String, String> parseKeyValPairs(String messageData) {
        Matcher key_value_matcher = pattern_key_value.matcher(messageData);
        Map<String, String> keyValPairs = new HashMap<>();
        while (key_value_matcher.find()) {
            keyValPairs.put(key_value_matcher.group(1), key_value_matcher.group(2));
        }
        return keyValPairs;
    }

	//TODO What to do when WAIT_FOR_LOG_END is set to true and auditLogThread won't stop on exit?
    @Override
    public boolean shutdown() {
        shutdown = true;
        try {
        	if(eventProcessorThread != null){
        		eventProcessorThread.join(THREAD_CLEANUP_TIMEOUT);
        	}
        	if(auditLogThread != null){
        		auditLogThread.join(THREAD_CLEANUP_TIMEOUT);
        	}
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error shutting down", e);
        }
        return true;
    }
    
    

    private void finishEvent(Map<String, String> eventData){

    	if (eventData == null) {
    		logger.log(Level.WARNING, "Null event data read");
    		return;
    	}

    	/*if("NETFILTER_PKT".equals(eventBuffer.get(eventId).get("type"))){ //for events with no syscalls
    		try{
    			handleNetfilterPacketEvent(eventBuffer.get(eventId));
    		}catch(Exception e){
    			logger.log(Level.WARNING, "Error processing finish syscall event with event id '"+eventId+"'", e);
    		}
    	}else{ //for events with syscalls
    		handleSyscallEvent(eventId);
    	}*/
    	
    	handleSyscallEvent(eventData);
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
    	String eventId = eventData.get("eventid");
    	try {
    		
    		int syscallNum = CommonFunctions.parseInt(eventData.get("syscall"), -1);

    		int arch = -1;
    		if(ARCH_32BIT){
    			arch = 32;
    		}else{
    			arch = 64;
    		}
    		
    		if(syscallNum == -1){
    			logger.log(Level.INFO, "A non-syscall audit event OR missing syscall record with for event with id '" + eventId + "'");
    			return;
    		}

    		SYSCALL syscall = SYSCALL.getSyscall(syscallNum, arch);

    		if("1".equals(AUDITCTL_SYSCALL_SUCCESS_FLAG) && "no".equals(eventData.get("success"))){
    			//if only log successful events but the current event had success no then only monitor the following calls.
    			if(syscall == SYSCALL.KILL || syscall == SYSCALL.EXIT || syscall == SYSCALL.EXIT_GROUP){
	    			//continue and log these syscalls irrespective of success
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

    		switch (syscall) {
    			case EXIT:
    			case EXIT_GROUP:
    				handleExit(eventData, syscall);
    				break;
	    		case READ: 
	    		case READV:
	    		case PREAD64:
	    		case WRITE: 
	    		case WRITEV:
	    		case PWRITE64:
	    		case SENDMSG: 
	    		case RECVMSG: 
	    		case SENDTO: 
	    		case RECVFROM: 
	    			handleIOEvent(syscall, eventData);
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
	    			handleForkClone(eventData, syscall);
	    			break;
	    		case EXECVE:
	    			handleExecve(eventData);
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
	    			handleOpenat(eventData);
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
	    		case BIND:
	    			handleBind(eventData, syscall);
	    			break;
	    		case ACCEPT4:
	    		case ACCEPT:
	    			handleAccept(eventData, syscall);
	    			break;
	    		case CONNECT:
	    			handleConnect(eventData);
	    			break;
	    		case KILL:
	    			handleKill(eventData);
	    			break;
	    		case RENAME:
	    		case RENAMEAT:
	    			handleRename(eventData, syscall);
	    			break;
	    		case SETREUID:
	    		case SETRESUID:
	    		case SETUID:
	    			handleSetuid(eventData, syscall);
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
	    			log(Level.INFO, "Unsupported syscall '"+syscallNum+"'", null, eventId, syscall);
    		}
    	} catch (Exception e) {
    		logger.log(Level.WARNING, "Error processing finish syscall event with eventid '"+eventId+"'", e);
    	}
    }
    
    /**
     * Only for IO syscalls where the FD is argument 0 (a0).
     * If the descriptor is not a valid descriptor then handled as file or socket based on the syscall.
     * Otherwise handled based on artifact identifier type
     * 
     * @param syscall system call
     * @param eventData audit event data gotten in the log
     */
    private void handleIOEvent(SYSCALL syscall, Map<String, String> eventData){
    	String pid = eventData.get("pid");
    	String fd = eventData.get("a0");
    	Class<? extends ArtifactIdentifier> artifactIdentifierClass = null;

    	if(descriptors.getDescriptor(pid, fd) != null){
    		artifactIdentifierClass = descriptors.getDescriptor(pid, fd).getClass();
    	}
    	
    	if(artifactIdentifierClass == null || UnknownIdentifier.class.equals(artifactIdentifierClass)){ //either a new unknown i.e. null or a previously seen unknown
    		if((syscall == SYSCALL.READ || syscall == SYSCALL.READV || syscall == SYSCALL.PREAD64 || syscall == SYSCALL.WRITE || syscall == SYSCALL.WRITEV || syscall == SYSCALL.PWRITE64)){
    			if(USE_READ_WRITE){
    				handleFileIOEvent(syscall, eventData);
    			}
    		}else if((syscall == SYSCALL.SENDMSG || syscall == SYSCALL.SENDTO || syscall == SYSCALL.RECVFROM || syscall == SYSCALL.RECVMSG)){
    			if(USE_SOCK_SEND_RCV){
    				handleNetworkIOEvent(syscall, eventData);
    			}
    		}else {
    			logger.log(Level.WARNING, "Unknown file descriptor type for eventid '"+eventData.get("eventid")+"' and syscall '"+syscall+"'");
    		}
    	}else if(NetworkSocketIdentifier.class.equals(artifactIdentifierClass) || UnixSocketIdentifier.class.equals(artifactIdentifierClass)){ 
    		handleNetworkIOEvent(syscall, eventData);
    	}else if(FileIdentifier.class.equals(artifactIdentifierClass) || MemoryIdentifier.class.equals(artifactIdentifierClass) 
    			|| UnnamedPipeIdentifier.class.equals(artifactIdentifierClass) || NamedPipeIdentifier.class.equals(artifactIdentifierClass)){
    		handleFileIOEvent(syscall, eventData);
    	}
    }

    private void handleNetworkIOEvent(SYSCALL syscall, Map<String, String> eventData){
    	if(USE_SOCK_SEND_RCV){
    		switch (syscall) {
	    		case WRITE: 
	    		case WRITEV: 
	    		case PWRITE64:
	    		case SENDTO:
	    		case SENDMSG:
	    			handleSend(eventData, syscall);
	    			break;
	    		case READ:
	    		case READV:
	    		case PREAD64:
	    		case RECVFROM:
	    		case RECVMSG:
	    			handleRecv(eventData, syscall);
	    			break;
	    		default:
	    			break;
    		}
    	}
    }

    private void handleFileIOEvent(SYSCALL syscall, Map<String, String> eventData){
    	if(USE_READ_WRITE){
    		switch(syscall){
	    		case READ:
	    		case READV:
	    		case PREAD64:
	    			handleRead(eventData, syscall);
	    			break;
	    		case WRITE:
	    		case WRITEV:
	    		case PWRITE64:
	    			handleWrite(eventData, syscall);
	    			break;
	    		default:
	    			break;
    		}
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
	    	String eventId = eventData.get("eventid");
	    	String pid = eventData.get("pid");
	    	String cwd = eventData.get("cwd");
	    	String deletedPath = null;
	    	
	    	Map<Integer, String> deletedPaths = getPathsWithNametype(eventData, "DELETE");
	    	
	    	if(deletedPaths.size() == 0){
	    		log(Level.INFO, "PATH record with nametype DELETE missing", null, eventId, syscall);
	    		return;
	    	}else{
	    		deletedPath = deletedPaths.values().iterator().next();
	    	}
	    	 
	    	if(syscall == SYSCALL.UNLINK){
		    	deletedPath = constructPath(deletedPath, cwd);
	    	}else if(syscall == SYSCALL.UNLINKAT){
	    		deletedPath = constructPathSpecial(deletedPath, eventData.get("a0"), cwd, pid, eventId, syscall); 		
	    	}else{
	    		log(Level.INFO, "Unexpected syscall '"+syscall+"' in UNLINK handler", null, eventId, syscall);
	    		return;
	    	}
	    	
	    	if(deletedPath == null){
	    		log(Level.INFO, "Failed to build absolute path from log data", null, eventId, syscall);
	    		return;
	    	}
	    	
	    	ArtifactIdentifier artifactIdentifier = getValidArtifactIdentifierForPath(deletedPath);
	    	
	    	Artifact artifact = putArtifact(eventData, artifactIdentifier, false);    	
	    	Process process = putProcess(eventData);    	
	    	WasGeneratedBy deletedEdge = new WasGeneratedBy(artifact, process);
	    	putEdge(deletedEdge, getOperation(syscall), eventData.get("time"), eventData.get("eventid"), DEV_AUDIT);
    	}
    }
    
    private void handleExit(Map<String, String> eventData, SYSCALL syscall){
    	// exit(), and exit_group() receives the following message(s):
        // - SYSCALL
        // - EOE
    	
    	String pid = eventData.get("pid");
    	
    	if(CONTROL){
	    	Process process = putProcess(eventData); //put Process vertex if it didn't exist before
	    	process = getContainingProcessVertex(pid); //edge to be drawn from the containing to the containing one
    	   	WasTriggeredBy exitEdge = new WasTriggeredBy(process, process);
	    	putEdge(exitEdge, getOperation(syscall), eventData.get("time"), eventData.get("eventid"), DEV_AUDIT);
    	}
    	
    	// Now clearing the process unit stack after the Process vertex has been gotten.
    	// If cleared before putVertex then it would populate the processUnitStack after it has been cleared.
    	
    	processUnitStack.remove(pid); // Remove all process and beep unit vertices of the process
    	pidToMemAddress.remove(pid);  // Remove all the beep unit memory addresses
    	iterationNumber.remove(pid); // Remove the iteration numbers for the units of this process
    	descriptors.removeDescriptorsOf(pid); // Remove all the descriptors of the process
    	pidToProcessHashes.remove(pid); // Remove all hashes of process vertices for this pid
    }
    
    private void handleMmap(Map<String, String> eventData, SYSCALL syscall){
    	// mmap() receive the following message(s):
    	// - MMAP
        // - SYSCALL
        // - EOE
    	
    	if(!USE_MEMORY_SYSCALLS){
    		return;
    	}
    	
    	String eventId = eventData.get("eventid");
    	String pid = eventData.get("pid");
    	String time = eventData.get("time");
    	String address = new BigInteger(eventData.get("exit")).toString(16); //convert to hexadecimal
    	String length = new BigInteger(eventData.get("a1")).toString(16); //convert to hexadecimal
    	String protection = new BigInteger(eventData.get("a2")).toString(16); //convert to hexadecimal
    	String fd = eventData.get("fd");
    	
    	if(fd == null){
    		log(Level.INFO, "FD record missing", null, eventId, syscall);
    		return;
    	}
    	
    	ArtifactIdentifier fileArtifactIdentifier = descriptors.getDescriptor(pid, fd);
    	
    	if(fileArtifactIdentifier == null){
    		descriptors.addUnknownDescriptor(pid, fd);
    		getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
    		fileArtifactIdentifier = descriptors.getDescriptor(pid, fd);
    	}
    	
    	//if not unknown and not file
    	if((!UnknownIdentifier.class.equals(fileArtifactIdentifier.getClass()) && !FileIdentifier.class.equals(fileArtifactIdentifier.getClass()))){
    		log(Level.INFO, "Artifact with FD '"+fd+"' is '"+fileArtifactIdentifier.getClass()+"'. Must be either a file or an unknown. ", null, eventId, syscall);
    		return;
    	}
    	
    	Artifact fileArtifact = putArtifact(eventData, fileArtifactIdentifier, false);
    	
    	ArtifactIdentifier memoryArtifactIdentifier = new MemoryIdentifier(pid, address, length);
    	Artifact memoryArtifact = putArtifact(eventData, memoryArtifactIdentifier, true);
		
		Process process = putProcess(eventData); //create if doesn't exist
		
		WasGeneratedBy wgbEdge = new WasGeneratedBy(memoryArtifact, process);
		putEdge(wgbEdge, getOperation(syscall)+"_"+getOperation(SYSCALL.WRITE), time, eventId, DEV_AUDIT);
		
		Used usedEdge = new Used(process, fileArtifact);
		putEdge(usedEdge, getOperation(syscall)+"_"+getOperation(SYSCALL.READ), time, eventId, DEV_AUDIT);
		
		WasDerivedFrom wdfEdge = new WasDerivedFrom(memoryArtifact, fileArtifact);
		wdfEdge.addAnnotation("protection", protection);
		wdfEdge.addAnnotation("pid", pid);
		putEdge(wdfEdge, getOperation(syscall), time, eventId, DEV_AUDIT);
		
    }
    
    private void handleMprotect(Map<String, String> eventData, SYSCALL syscall){
    	// mprotect() receive the following message(s):
        // - SYSCALL
        // - EOE
    	
    	if(!USE_MEMORY_SYSCALLS){
    		return;
    	}
    	
    	String pid = eventData.get("pid");
    	String time = eventData.get("time");
    	String address = new BigInteger(eventData.get("a0")).toString(16);
    	String length = new BigInteger(eventData.get("a1")).toString(16);
    	String protection = new BigInteger(eventData.get("a2")).toString(16);
    	
    	ArtifactIdentifier memoryInfo = new MemoryIdentifier(pid, address, length);
    	Artifact memoryArtifact = putArtifact(eventData, memoryInfo, true);
		
		Process process = putProcess(eventData); //create if doesn't exist
		
		WasGeneratedBy edge = new WasGeneratedBy(memoryArtifact, process);
		edge.addAnnotation("protection", protection);
		putEdge(edge, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
    }

    private void handleKill(Map<String, String> eventData){
    	if(!CREATE_BEEP_UNITS){
    		return;
    	}
    	SYSCALL syscall = SYSCALL.KILL;
    	String eventId = eventData.get("eventid");
    	String pid = eventData.get("pid");
    	String time = eventData.get("time");
    	BigInteger arg0 = null;
    	BigInteger arg1 = null;
    	String unitId = null;
    	try{
    		arg0 = new BigInteger(eventData.get("a0"));
    		arg1 = new BigInteger(eventData.get("a1"));
    		unitId = arg1.toString();
    	}catch(Exception e){
    		log(Level.WARNING, "Failed to parse argument number 0 or 1", e, eventId, syscall);
    		return;
    	}
    	if(arg0.intValue() == -100){ //unit start
    		putProcess(eventData); //check if it exists. if not then add and return.
    		Process addedUnit = pushUnitIterationOnStack(pid, unitId, time); //create unit and add it to data structure
    		//add edge between the new unit and the main unit to keep the graph connected
    		WasTriggeredBy unitToContainingProcess = new WasTriggeredBy(addedUnit, getContainingProcessVertex(pid));
        	putEdge(unitToContainingProcess, getOperation(SYSCALL.UNIT), time, eventData.get("eventid"), BEEP);
    	}else if(arg0.intValue() == -101){ //unit end
    		//remove all iterations of the given unit
    		popUnitIterationsFromStack(pid, unitId);
    	}else if(arg0.intValue() == -200 || arg0.intValue() == -300){ //-200 highbits of read, -300 highbits of write
    		pidToMemAddress.put(pid, arg1);
    	}else if(arg0.intValue() == -201 || arg0.intValue() == -301){ //-201 lowbits of read, -301 lowbits of write 
    		BigInteger address = pidToMemAddress.get(pid);
    		if(address != null){
    			String operation = null;
    			Artifact memArtifact = null;
    			AbstractEdge edge = null;
    			Process process = getProcess(pid);
    			if(process == null || process.getAnnotation("unit").equals("0")){ //process cannot be null or cannot be the containing process which is doing the memory read or write in BEEP
    				log(Level.INFO, "Unit vertex not found for beep memory read/write. Possibly missing unit creation 'kill' syscall", null, eventId, syscall);
    				return;
    			}
    			address = address.shiftLeft(32);
    			address = address.add(arg1);
    			pidToMemAddress.remove(pid);
    			if(arg0.intValue() == -201){
    				memArtifact = putArtifact(eventData, new MemoryIdentifier(pid, address.toString(16), ""), false, BEEP);
    				edge = new Used(process, memArtifact);
    				operation = getOperation(SYSCALL.READ);
    			}else if(arg0.intValue() == -301){
    				memArtifact = putArtifact(eventData, new MemoryIdentifier(pid, address.toString(16), ""), true, BEEP);
    				edge = new WasGeneratedBy(memArtifact, process);
    				operation = getOperation(SYSCALL.WRITE);
    			}
    			if(edge != null && memArtifact != null && process != null && operation != null){
	    			putEdge(edge, operation, time, eventData.get("eventid"), BEEP);
    			}
    		}
    	}
    }

    private void handleForkClone(Map<String, String> eventData, SYSCALL syscall) {
        // fork() and clone() receive the following message(s):
        // - SYSCALL
        // - EOE

    	String eventId = eventData.get("eventid");
        String time = eventData.get("time");
        String oldPID = eventData.get("pid");
        String newPID = eventData.get("exit");
        
        if(syscall == SYSCALL.CLONE){
        	Long flags = CommonFunctions.parseLong(eventData.get("a0"), 0L);
        	//source: http://www.makelinux.net/books/lkd2/ch03lev1sec3
        	if((flags & SIGCHLD) == SIGCHLD && (flags & CLONE_VM) == CLONE_VM && (flags & CLONE_VFORK) == CLONE_VFORK){ //is vfork
        		syscall = SYSCALL.VFORK;
        	}else if((flags & SIGCHLD) == SIGCHLD){ //is fork
        		syscall = SYSCALL.FORK;
        	}
        	//otherwise it is just clone
        }        
        
        Process oldProcess = putProcess(eventData); //will create if doesn't exist

        // Whenever any new annotation is added to a Process which doesn't come from audit log then update the following ones. TODO
        Map<String, String> newEventData = new HashMap<String, String>();
        newEventData.putAll(eventData);
        newEventData.put("pid", newPID);
        newEventData.put("ppid", oldPID);
        newEventData.put("commandline", oldProcess.getAnnotation("commandline"));
        newEventData.put("cwd", eventData.get("cwd"));
        newEventData.put("start time", time);
        
        boolean RECREATE_AND_REPLACE = true; //true because a new process with the same pid might be being created. pids are recycled.
        Process newProcess = putProcess(newEventData, RECREATE_AND_REPLACE);
        
        WasTriggeredBy forkCloneEdge = new WasTriggeredBy(newProcess, oldProcess);
        putEdge(forkCloneEdge, getOperation(syscall), time, eventId, DEV_AUDIT);
        
        if(syscall == SYSCALL.CLONE){
        	descriptors.linkDescriptors(oldPID, newPID);//share file descriptors when clone
        }else if(syscall == SYSCALL.FORK || syscall == SYSCALL.VFORK){ //copy file descriptors just once here when fork
        	descriptors.copyDescriptors(oldPID, newPID);
        }else{
        	log(Level.WARNING, "Unexpected syscall '"+syscall+"' in FORK CLONE handler", null, eventId, syscall);
        	return;
        }
    }

    private void handleExecve(Map<String, String> eventData) {
        // execve() receives the following message(s):
        // - SYSCALL
        // - EXECVE
        // - BPRM_FCAPS (ignored)
        // - CWD
        // - PATH
        // - PATH
        // - EOE

    	/*
    	 * Steps:
    	 * 0) check and get if the vertex with the pid exists already
    	 * 1) create the new vertex with the commandline which also replaces the vertex with the same pid
    	 * 2) if the vertex gotten in step 0 is null then it means that we missed that vertex
    	 * and cannot know what it uids and gids were. So, not trying to repair that vertex and not going
    	 * to put the edge from the child to the parent.
    	 * 3) add the Used edges from the newly created vertex in step 1 to the libraries used when execve
    	 *  was done
    	 */
    	
        String pid = eventData.get("pid");
        String time = eventData.get("time");

        String commandline = null;
        if(eventData.get("execve_argc") != null){
        	Long argc = CommonFunctions.parseLong(eventData.get("execve_argc"), 0L);
        	commandline = "";
        	for(int i = 0; i < argc; i++){
        		commandline += eventData.get("execve_a" + i) + " ";
        	}
        	commandline = commandline.trim();
        }else{
        	commandline = "[Record Missing]";
        }
        
        eventData.put("commandline", commandline);
        eventData.put("start time", time);
        
        //doing it before recreating and replacing the vertex with the same pid
        //try to get it. if doesn't exist then don't add it because it's user or group identifiers might have been different
        Process oldProcess = getProcess(pid); 
        
        boolean RECREATE_AND_REPLACE = true; //true because a process vertex with the same pid created in execve
        //this call would clear all the units for the pid because the process is doing execve, replacing itself.
        Process newProcess = putProcess(eventData, RECREATE_AND_REPLACE);
        
        if(oldProcess != null){
        	WasTriggeredBy execveEdge = new WasTriggeredBy(newProcess, oldProcess);
        	putEdge(execveEdge, getOperation(SYSCALL.EXECVE), time, eventData.get("eventid"), DEV_AUDIT);
        }else{
        	log(Level.INFO, "Unable to create edge because process with pid '"+pid+"' missing", null, eventData.get("eventid"), SYSCALL.EXECVE);
        }
        
        //add used edge to the paths in the event data. get the number of paths using the 'items' key and then iterate
        String cwd = eventData.get("cwd");
        Long totalPaths = CommonFunctions.parseLong(eventData.get("items"), 0L);
        for(int pathNumber = 0; pathNumber < totalPaths; pathNumber++){
        	String path = eventData.get("path"+pathNumber);
        	path = constructPath(path, cwd);
        	if(path == null){
        		log(Level.INFO, "Missing PATH or CWD record", null, eventData.get("eventid"), SYSCALL.EXECVE);
        		continue;
        	}        	
        	ArtifactIdentifier fileIdentifier = new FileIdentifier(path);
        	Artifact usedArtifact = putArtifact(eventData, fileIdentifier, false);
        	Used usedEdge = new Used(newProcess, usedArtifact);
        	putEdge(usedEdge, getOperation(SYSCALL.LOAD), time, eventData.get("eventid"), DEV_AUDIT);
        }
        
        descriptors.unlinkDescriptors(pid);
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
    	eventData.put("a2", eventData.get("a1")); //set mode to argument 3 (in open) from 2 (in creat)
    	eventData.put("a1", String.valueOf(defaultFlags)); //flags is argument 2 in open
    	
    	handleOpen(eventData, SYSCALL.CREATE); //TODO change to creat. kept as create to keep current CDM data consistent
    	
    }
    
    private void handleOpenat(Map<String, String> eventData){
    	//openat() receives the following message(s):
    	// - SYSCALL
        // - CWD
        // - PATH
        // - PATH
        // - EOE
    	
    	Long dirFd = CommonFunctions.parseLong(eventData.get("a0"), -1L);
    	
    	//according to manpage if following true then use cwd if path not absolute, which is already handled by open
    	if(dirFd != AT_FDCWD){ //checking if cwd needs to be replaced by dirFd's path
    		String pid = eventData.get("pid");
			String dirFdString = String.valueOf(dirFd);
			//if null of if not file then cannot process it
			ArtifactIdentifier artifactIdentifier = descriptors.getDescriptor(pid, dirFdString);
			if(artifactIdentifier == null || !FileIdentifier.class.equals(artifactIdentifier.getClass())){
				log(Level.INFO, "Artifact not of type file '" + artifactIdentifier + "'", null, eventData.get("eventid"), SYSCALL.OPENAT);
				return;
			}else{ //is file
				String dirPath = ((FileIdentifier) artifactIdentifier).getPath();
				eventData.put("cwd", dirPath); //replace cwd with dirPath to make eventData compatible with open
			}
    	}
    	
    	//modify the eventData to match open syscall and then call it's function
    	
    	eventData.put("a0", eventData.get("a1")); //moved pathname address to first like in open
    	eventData.put("a1", eventData.get("a2")); //moved flags to second like in open
    	eventData.put("a2", eventData.get("a3")); //moved mode to third like in open
    	
    	handleOpen(eventData, SYSCALL.OPENAT);
    }

    private void handleOpen(Map<String, String> eventData, SYSCALL syscall) {
        // open() receives the following message(s):
        // - SYSCALL
        // - CWD
    	// - PATH with nametype CREATE (file operated on) or NORMAL (file operated on) or PARENT (parent of file operated on) or DELETE (file operated on) or UNKNOWN (only when syscall fails)
    	// - PATH with nametype CREATE or NORMAL or PARENT or DELETE or UNKNOWN
        // - EOE
    	
    	//three syscalls can come here: OPEN (for files and pipes), OPENAT (for files and pipes), CREAT (only for files)
    	    	
    	Long flags = CommonFunctions.parseLong(eventData.get("a1"), 0L);
    	
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        String fd = eventData.get("exit");
        String time = eventData.get("time");
        
        boolean isCreate = syscall == SYSCALL.CREATE || syscall == SYSCALL.CREAT; //TODO later on change only to CREAT only
        String path = null;
        Map<Integer, String> paths = getPathsWithNametype(eventData, "CREATE"); 
        if(paths.size() == 0){
        	isCreate = false;
        	paths = getPathsWithNametype(eventData, "NORMAL");
        	if(paths.size() == 0){ //missing audit record
        		log(Level.INFO, "Missing PATH record", null, eventData.get("eventid"), syscall);
        		return;
        	}
        }else{ //found path with CREATE nametype. will always be one
        	isCreate = true;        	
        }
        
        path = paths.values().iterator().next(); //get the first
        path = constructPath(path, cwd);
        
        if(path == null){
        	log(Level.INFO, "Missing CWD or PATH record", null, eventData.get("eventid"), syscall);
        	return;
        }
        
        Process process = putProcess(eventData);
        
        ArtifactIdentifier artifactIdentifier = getValidArtifactIdentifierForPath(path);
        
        AbstractEdge edge = null;
        
        if(isCreate){
        	
        	if(!FileIdentifier.class.equals(artifactIdentifier.getClass())){
        		artifactIdentifier = new FileIdentifier(path); //can only create a file using open
        	}
        	
        	//set new epoch
        	getArtifactProperties(artifactIdentifier).markNewEpoch(eventData.get("eventid"));
        	            
            syscall = SYSCALL.CREATE;
            
        }

        if((!FileIdentifier.class.equals(artifactIdentifier.getClass()) && !artifactIdentifier.getClass().equals(NamedPipeIdentifier.class))){ //not a file and not a named pipe
    		//make it a file identifier
    		artifactIdentifier = new FileIdentifier(path);
    	}
    
        boolean openedForRead = false;
        
    	if ((flags & O_RDONLY) == O_RDONLY) {
    		Artifact vertex = putArtifact(eventData, artifactIdentifier, false);
            edge = new Used(process, vertex);
            openedForRead = true;
    	} else if((flags & O_WRONLY) == O_WRONLY || (flags & O_RDWR) == O_RDWR){
    		Artifact vertex = putArtifact(eventData, artifactIdentifier, true);
	        edge = new WasGeneratedBy(vertex, process);
	        openedForRead = false;
        } else{
        	log(Level.INFO, "Unhandled value of FLAGS argument '"+flags+"'", null, eventData.get("eventid"), syscall);
        	return;
        }
        
        if(edge != null){
        	//everything happened successfully. add it to descriptors
        	descriptors.addDescriptor(pid, fd, artifactIdentifier, openedForRead);
        	
	        putEdge(edge, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
        }
    }

    private void handleClose(Map<String, String> eventData) {
        // close() receives the following message(s):
        // - SYSCALL
        // - EOE
    	String pid = eventData.get("pid");
    	String fd = String.valueOf(CommonFunctions.parseLong(eventData.get("a0"), -1L));
    	ArtifactIdentifier closedArtifactIdentifier = descriptors.removeDescriptor(pid, fd);

    	if(CONTROL){
	    	String time = eventData.get("time");
	    	String eventId = eventData.get("eventid");
	        if(closedArtifactIdentifier != null){
	        	Process process = putProcess(eventData);
		        Artifact artifact = putArtifact(eventData, closedArtifactIdentifier, false);
		        AbstractEdge edge = null;
		        Boolean wasOpenedForRead = closedArtifactIdentifier.wasOpenedForRead();
	        	if(wasOpenedForRead == null){
	        		// Not drawing an edge because didn't seen an open
	        	}else if(wasOpenedForRead){
	        		edge = new Used(process, artifact);
	        	}else {
	        		edge = new WasGeneratedBy(artifact, process);
	        	}	   
	        	if(edge != null){
	        		putEdge(edge, getOperation(SYSCALL.CLOSE), time, eventId, DEV_AUDIT);
	        	}
	        }else{
	        	log(Level.INFO, "No FD with number '"+fd+"' for pid '"+pid+"'", null, eventId, SYSCALL.CLOSE);
	        }
    	}
       
        //there is an option to either handle epochs 1) when artifact opened/created or 2) when artifacts deleted/closed.
        //handling epoch at opened/created in all cases
    }

    private void handleRead(Map<String, String> eventData, SYSCALL syscall) {
        // read() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        String fd = eventData.get("a0");
        String bytesRead = eventData.get("exit");
        Process process = putProcess(eventData);

        String time = eventData.get("time");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd);
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }
        
        ArtifactIdentifier artifactIdentifier = descriptors.getDescriptor(pid, fd);
        Artifact vertex = putArtifact(eventData, artifactIdentifier, false);
        Used used = new Used(process, vertex);
        used.addAnnotation("size", bytesRead);
        putEdge(used, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
        
    }

    private void handleWrite(Map<String, String> eventData, SYSCALL syscall) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        Process process = putProcess(eventData);

        String fd = eventData.get("a0");
        String time = eventData.get("time");
        String bytesWritten = eventData.get("exit");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd);
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }
        
        ArtifactIdentifier artifactIdentifier = descriptors.getDescriptor(pid, fd);
        
        if(artifactIdentifier != null){
        	Artifact vertex = putArtifact(eventData, artifactIdentifier, true);
	        WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
	        wgb.addAnnotation("size", bytesWritten);
	        putEdge(wgb, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
        }
    }

    private void handleTruncate(Map<String, String> eventData, SYSCALL syscall) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        Process process = putProcess(eventData);

        String time = eventData.get("time");
        ArtifactIdentifier artifactIdentifier = null;

        if (syscall == SYSCALL.TRUNCATE) {
        	Map<Integer, String> paths = getPathsWithNametype(eventData, "NORMAL");
        	if(paths.size() == 0){
        		log(Level.INFO, "Missing PATH record", null, eventData.get("eventid"), syscall);
        		return;
        	}
        	String path = paths.values().iterator().next();
        	path = constructPath(path, eventData.get("cwd"));
        	if(path == null){
        		log(Level.INFO, "Missing PATH or CWD record", null, eventData.get("eventid"), syscall);
        		return;
        	}
            artifactIdentifier = new FileIdentifier(path);
        } else if (syscall == SYSCALL.FTRUNCATE) {
        	String fd = eventData.get("a0");
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	descriptors.addUnknownDescriptor(pid, fd);
            	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
            }
                        
            artifactIdentifier = descriptors.getDescriptor(pid, fd);
        }

        if(FileIdentifier.class.equals(artifactIdentifier.getClass()) || UnknownIdentifier.class.equals(artifactIdentifier.getClass())){
        	Artifact vertex = putArtifact(eventData, artifactIdentifier, true);
            WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
            putEdge(wgb, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
        }else{
        	log(Level.INFO, "Unexpected artifact type '"+artifactIdentifier+"'. Can only be file or unknown", null, eventData.get("eventid"), syscall);
        }  
    }

    private void handleDup(Map<String, String> eventData, SYSCALL syscall) {
        // dup(), dup2(), and dup3() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");

        String fd = eventData.get("a0");
        String newFD = eventData.get("exit"); //new fd returned in all: dup, dup2, dup3
        
        if(!fd.equals(newFD)){ //if both fds same then it succeeds in case of dup2 and it does nothing so do nothing here too
            if(descriptors.getDescriptor(pid, fd) == null){
	        	descriptors.addUnknownDescriptor(pid, fd);
	        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
	        }
	        descriptors.duplicateDescriptor(pid, fd, newFD);
        }
    }

    private void handleSetuid(Map<String, String> eventData, SYSCALL syscall) {
        // setuid() receives the following message(s):
        // - SYSCALL
        // - EOE
    	
    	String time = eventData.get("time");
        String pid = eventData.get("pid");
        String eventId = eventData.get("eventid");
        
        /*
         * Pseudo-code
         * 
         * oldProcess = current process with pid
         * if oldProcess is null then
         * 		putVertex(eventData)
         * 		put no edge since we don't have oldProcess
         * else
         * 		if oldProcess is not an iteration i.e. a simple process then
         * 			newProcess = putVertex(eventData)
         * 			draw edge from newProcess to oldProcess
         * 		else if oldProcess is an iteration then
         * 			oldContainingProcess = get old containing process
         * 			newContainingProcess = create new containing process
         * 			oldRunningIterationsList = list of all currently valid unit iterations (can be multiple when nested loop only)
         * 			newRunningIterationsList = copy of all unit iterations with updated fields
         * 			put newContainingVertex and all newRunningIterationsList to storage
         * 			create edge from newContainingProcess to oldContainingProcess
         * 			for each newRunningIteration in newRunningIterationsList
         * 				draw edge from newRunningIteration to oldRunningIteration
         * 				draw edge from newRunningIteration to newContainingProcess
         * 			create edge from newProcessIteration to oldProcess (which is a unit iteration)
         * 			//now update internal data structures	
         * 			manually replace oldContainingProcess with newContainingProcess
         * 			
         * 			manually add newProcessIteration to the process stack. Doing this manually so as to not reset the iteration counter for the process
         * 	
         */
        
        Process exitingVertex = getProcess(pid);
        
        if(exitingVertex == null){
        	putProcess(eventData); //can't add the edge since no existing vertex with the same pid
        }else{
        	
        	// Following are the annotations that need to be updated in processes and units while keep all other the same
        	Map<String, String> annotationsToUpdate = new HashMap<String, String>();
    		annotationsToUpdate.put("auid", eventData.get("auid"));
    		annotationsToUpdate.put("uid", eventData.get("uid"));
    		annotationsToUpdate.put("suid", eventData.get("suid"));
    		annotationsToUpdate.put("euid", eventData.get("euid"));
    		annotationsToUpdate.put("fsuid", eventData.get("fsuid"));
        	
        	/* A check for containing process. Either no unit annotation meaning that units weren't enabled or
        	 * the units are enabled and the unit annotation value is zero i.e. the containing process	
        	 */  
        	if(exitingVertex.getAnnotation("unit") == null || exitingVertex.getAnnotation("unit").equals("0")){
        		Map<String, String> existingProcessAnnotations = exitingVertex.getAnnotations();
        		Map<String, String> newProcessAnnotations = updateKeyValuesInMap(existingProcessAnnotations, annotationsToUpdate);
        		//here then it means that there are no active units for the process
        		//update the modified annotations by the syscall and create the new process vertex
        		boolean RECREATE_AND_REPLACE = true; //has to be true since already an entry for the same pid exists
        		Process newProcess = putProcess(newProcessAnnotations, RECREATE_AND_REPLACE); 
        		//drawing edge from the new to the old
        		WasTriggeredBy newProcessToOldProcess = new WasTriggeredBy(newProcess, exitingVertex);
    			putEdge(newProcessToOldProcess, getOperation(syscall), time, eventId, DEV_AUDIT);
        	}else{ //is a unit i.e. unit annotation is non-null and non-zero. 
        		
        		// oldProcessUnitStack has only active iterations. Should be only one active one since nested loops haven't been instrumented yet in BEEP.
        		// but taking care of nested loops still anyway. 
        		// IMPORTANT: Getting this here first because putProcess call on newContainingProcess would discard it
        		LinkedList<Process> oldProcessUnitStack = processUnitStack.get(pid);
        		
        		Process oldContainingProcess = getContainingProcessVertex(pid);
        		Map<String, String> oldContainingProcessAnnotations = oldContainingProcess.getAnnotations();
        		Map<String, String> newContainingProcessAnnotations = updateKeyValuesInMap(oldContainingProcessAnnotations, annotationsToUpdate);
        		boolean RECREATE_AND_REPLACE = true; //has to be true since already an entry for the same pid exists
        		Process newContainingProcess = putProcess(newContainingProcessAnnotations, RECREATE_AND_REPLACE);
        		
        		//Get the new process unit stack now in which the new units would be added. New because putProcess above has replaced the old one
        		LinkedList<Process> newProcessUnitStack = processUnitStack.get(pid);
        		
        		WasTriggeredBy newProcessToOldProcess = new WasTriggeredBy(newContainingProcess, oldContainingProcess);
    			putEdge(newProcessToOldProcess, getOperation(syscall), time, eventId, DEV_AUDIT);
        		
        		//recreating the rest of the stack manually because existing iteration and count annotations need to be preserved
        		for(int a = 1; a<oldProcessUnitStack.size(); a++){ //start from 1 because 0 is the containing process
        			Process oldProcessUnit = oldProcessUnitStack.get(a);
        			Map<String, String> oldProcessUnitAnnotations = oldProcessUnit.getAnnotations();
        			Map<String, String> newProcessUnitAnnotations = updateKeyValuesInMap(oldProcessUnitAnnotations, annotationsToUpdate);
        			Process newProcessUnit = createProcessVertex(newProcessUnitAnnotations); //create process unit
        			newProcessUnitStack.addLast(newProcessUnit); //add to memory
        			if(!processVertexHasBeenPutBefore(newProcessUnit)){
        	    		putVertex(newProcessUnit);//add to internal buffer
        			}
        			
        			//drawing an edge from newProcessUnit to currentProcessUnit with operation based on current syscall
        			WasTriggeredBy newUnitToOldUnit = new WasTriggeredBy(newProcessUnit, oldProcessUnit);
        			putEdge(newUnitToOldUnit, getOperation(syscall), time, eventId, DEV_AUDIT);
        			//drawing an edge from newProcessUnit to newContainingProcess with operation unit to keep things consistent
        			WasTriggeredBy newUnitToNewProcess = new WasTriggeredBy(newProcessUnit, newContainingProcess);
        			putEdge(newUnitToNewProcess, getOperation(SYSCALL.UNIT), time, eventId, BEEP);
        		}
        	}
        }
    }
        
    /**
     * Resolves paths received in audit log to build a canonical path.
     * 
     * Rules:
     * 1) If path absolute then return path i.e. starts with a slash in path
     * 2) If path not absolute and parentPath absolute then return parentPath + slash + path
     * 3) In all other cases return null
     * 
     * Note: Returning canonical path to resolve cases where .. or . are used in a path
     * 
     * @param path path or name
     * @param parentPath path of the parent
     * @return canonical path or null if not enough information
     */    
    private String constructPath(String path, String parentPath){
    	try{
    		
    		if(path != null){
    		
	    		if(path.startsWith(File.separator)){ //is absolute
	    			return new File(path).getCanonicalPath();
	    		}else{
	    			
	    			if(parentPath != null){
	    				if(parentPath.startsWith(File.separator)){ //is absolute
		    				return new File(parentPath + File.separator + path).getCanonicalPath();
		    			}
	    			}
	    		}	    		
    		}
    		
    	}catch(Exception e){
    		logger.log(Level.INFO, "Failed to create resolved path. path:"+path+", parentPath:"+parentPath, e);
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
        
    	String eventId = eventData.get("eventid");
    	String pid = eventData.get("pid");
    	String cwd = eventData.get("cwd"); 
        String oldFilePath = eventData.get("path2");
        //if file renamed to already existed then path4 else path3. Both are same so just getting whichever exists
        String newFilePath = eventData.get("path4") == null ? eventData.get("path3") : eventData.get("path4");
        
        if(syscall == SYSCALL.RENAME){
	        oldFilePath = constructPath(oldFilePath, cwd);
	        newFilePath = constructPath(newFilePath, cwd);
        }else if(syscall == SYSCALL.RENAMEAT){
        	oldFilePath = constructPathSpecial(oldFilePath, eventData.get("a0"), cwd, pid, eventId, syscall);        	
        	newFilePath = constructPathSpecial(newFilePath, eventData.get("a2"), cwd, pid, eventId, syscall);        	
        }else{
        	log(Level.WARNING, "Unexpected syscall '"+syscall+"' in RENAME handler", null, eventId, syscall);
        	return;
        }
        
        if(oldFilePath == null || newFilePath == null){
        	log(Level.INFO, "Failed to create path(s)", null, eventId, syscall);
        	return;
        }
        
        handleSpecialSyscalls(eventData, syscall, oldFilePath, newFilePath);
    }
        
    private void handleMknodat(Map<String, String> eventData){
    	//mknodat() receives the following message(s):
    	// - SYSCALL
        // - CWD
        // - PATH of the created file with nametype=CREATE
        // - EOE
    	
    	//first argument is the fd of the directory to create file in. if the directory fd is AT_FDCWD then use cwd
    	
    	String pid = eventData.get("pid");
    	String fd = eventData.get("a0");
    	Long fdLong = CommonFunctions.parseLong(fd, null);
    	
    	ArtifactIdentifier artifactIdentifier = null;
    	
    	if(fdLong != AT_FDCWD){
    		artifactIdentifier = descriptors.getDescriptor(pid, fd);
    		if(artifactIdentifier == null){
    			log(Level.INFO, "No FD '"+fd+"' for pid '"+pid+"'", null, eventData.get("eventid"), SYSCALL.MKNODAT);
    			return;
    		}else if(artifactIdentifier.getClass().equals(FileIdentifier.class)){
    			String directoryPath = ((FileIdentifier)artifactIdentifier).getPath();
	    		//update cwd to directoryPath and call handleMknod. the file created path is always relative in this syscall
	    		eventData.put("cwd", directoryPath);
    		}else{
    			log(Level.INFO, "FD '"+fd+"' for pid '"+pid+"' is of type '"+artifactIdentifier.getClass()+"' but only file allowed", null, eventData.get("eventid"), SYSCALL.MKNODAT);
    			return;
    		}    		
    	}
   	
    	//replace the second argument (which is mode in mknod) with the third (which is mode in mknodat)
		eventData.put("a1", eventData.get("a2"));
		handleMknod(eventData, SYSCALL.MKNODAT);
    }
    
    private void handleMknod(Map<String, String> eventData, SYSCALL syscall){
    	//mknod() receives the following message(s):
    	// - SYSCALL
        // - CWD
        // - PATH of the parent with nametype=PARENT
        // - PATH of the created file with nametype=CREATE
        // - EOE
    	
    	String modeString = eventData.get("a1");
    	
    	Long mode = CommonFunctions.parseLong(modeString, 0L);
    	        
        String path = null;
        
        Map<Integer, String> paths = getPathsWithNametype(eventData, "CREATE");
        
        if(paths.size() == 0){
        	log(Level.INFO, "PATH record missing", null, eventData.get("eventid"), syscall);
        	return;
        }
                
        path = paths.values().iterator().next();        
        path = constructPath(path, eventData.get("cwd"));
        
        if(path == null){
        	log(Level.INFO, "Missing PATH or CWD record", null, eventData.get("eventid"), syscall);
        	return;
        }
        
        ArtifactIdentifier artifactIdentifier = null;
    	
    	if((mode & S_IFIFO) == S_IFIFO){ //is pipe
            artifactIdentifier = new NamedPipeIdentifier(path);
    	}else if((mode & S_IFREG) == S_IFREG){ //is regular file
    		artifactIdentifier = new FileIdentifier(path);
    	}else if((mode & S_IFSOCK) == S_IFSOCK){ //is unix socket
    		artifactIdentifier = new UnixSocketIdentifier(path);
    	}else{
    		log(Level.INFO, "Unsupported mode for mknod '"+mode+"'", null, eventData.get("eventid"), syscall);
    		return;
    	}	
    	
    	if(artifactIdentifier != null){
	    	getArtifactProperties(artifactIdentifier).markNewEpoch(eventData.get("eventid"));
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
    	
    	String eventId = eventData.get("eventid");
    	String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        String srcPath = eventData.get("path0");
        String dstPath = eventData.get("path2");
        
        if(syscall == SYSCALL.LINK || syscall == SYSCALL.SYMLINK){
        	srcPath = constructPath(srcPath, cwd);
        	dstPath = constructPath(dstPath, cwd);
        }else if(syscall == SYSCALL.LINKAT || syscall == SYSCALL.SYMLINKAT){
        	srcPath = constructPathSpecial(srcPath, eventData.get("a0"), cwd, pid, eventId, syscall);
        	dstPath = constructPathSpecial(dstPath, eventData.get("a2"), cwd, pid, eventId, syscall);
        }else{
        	log(Level.WARNING, "Unexpected syscall '"+syscall+"' in LINK SYMLINK handler", null, eventId, syscall);
        	return;
        }
        
        if(srcPath == null || dstPath == null){
        	log(Level.INFO, "Failed to create path(s)", null, eventId, syscall);
        	return;
        }
        
        handleSpecialSyscalls(eventData, syscall, srcPath, dstPath);
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
    private void handleSpecialSyscalls(Map<String, String> eventData, SYSCALL syscall, String srcPath, String dstPath){
    	
    	if(eventData == null || syscall == null || srcPath == null || dstPath == null){
    		logger.log(Level.INFO, "Missing arguments. srcPath:{0}, dstPath:{1}, syscall:{2}, eventData:{3}", new Object[]{srcPath, dstPath, syscall, eventData});
    		return;
    	}
    	
    	String eventId = eventData.get("eventid");
    	String time = eventData.get("time");
    	String operationPrefix = getOperation(syscall);
    	String pid = eventData.get("pid");
    	
    	if(eventId == null || time == null || pid == null){
    		log(Level.INFO, "Missing keys in event data. pid:"+pid+", time:"+time+", eventid:"+eventId, null, eventId, syscall);
    		return;
    	}
    	
    	Process process = putProcess(eventData);
    	
    	ArtifactIdentifier srcArtifactIdentifier = getValidArtifactIdentifierForPath(srcPath);
        ArtifactIdentifier dstArtifactIdentifier = null;
        
        if(FileIdentifier.class.equals(srcArtifactIdentifier.getClass())){
        	dstArtifactIdentifier = new FileIdentifier(dstPath);
        }else if(NamedPipeIdentifier.class.equals(srcArtifactIdentifier.getClass())){
        	dstArtifactIdentifier = new NamedPipeIdentifier(dstPath);
        }else if(UnixSocketIdentifier.class.equals(srcArtifactIdentifier.getClass())){
        	dstArtifactIdentifier = new UnixSocketIdentifier(dstPath);
        }else{
        	log(Level.INFO, "Unexpected artifact type '"+srcArtifactIdentifier+"' for syscall", null, eventId, syscall);
        	return;
        }
        
        //destination is new so mark epoch
        getArtifactProperties(dstArtifactIdentifier).markNewEpoch(eventId);

        Artifact srcVertex = putArtifact(eventData, srcArtifactIdentifier, false);
        Used used = new Used(process, srcVertex);
        putEdge(used, operationPrefix + "_" + getOperation(SYSCALL.READ), time, eventId, DEV_AUDIT);

        Artifact dstVertex = putArtifact(eventData, dstArtifactIdentifier, true);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, process);
        putEdge(wgb, operationPrefix + "_" + getOperation(SYSCALL.WRITE), time, eventId, DEV_AUDIT);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("pid", pid);
        putEdge(wdf, operationPrefix, time, eventId, DEV_AUDIT);
    }
    
    private void handleChmod(Map<String, String> eventData, SYSCALL syscall) {
        // chmod(), fchmod(), and fchmodat() receive the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH
        // - EOE
    	String eventId = eventData.get("eventid");
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        Process process = putProcess(eventData);
        String modeArgument = null;
        // if syscall is chmod, then path is <path0> relative to <cwd>
        // if syscall is fchmod, look up file descriptor which is <a0>
        // if syscall is fchmodat, loop up the directory fd and build a path using the path in the audit log
        ArtifactIdentifier artifactIdentifier = null;
        if (syscall == SYSCALL.CHMOD) {
        	Map<Integer, String> paths = getPathsWithNametype(eventData, "NORMAL");
        	if(paths.size() == 0){
        		log(Level.INFO, "Missing PATH record", null, eventId, syscall);
        		return;
        	}
        	String path = paths.values().iterator().next();
        	path = constructPath(path, eventData.get("cwd"));
        	if(path == null){
        		log(Level.INFO, "Missing PATH or CWD records", null, eventId, syscall);
        		return;
        	}
            artifactIdentifier = getValidArtifactIdentifierForPath(path);
            modeArgument = eventData.get("a1");
        } else if (syscall == SYSCALL.FCHMOD) {
        	
        	String fd = eventData.get("a0");
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	descriptors.addUnknownDescriptor(pid, fd);
            	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
            }
                        
            artifactIdentifier = descriptors.getDescriptor(pid, fd);
            modeArgument = eventData.get("a1");
        }else if(syscall == SYSCALL.FCHMODAT){
        	Map<Integer, String> paths = getPathsWithNametype(eventData, "NORMAL");
        	if(paths.size() == 0){
        		log(Level.INFO, "Missing PATH record", null, eventId, syscall);
        		return;
        	}
        	String path = paths.values().iterator().next();
        	path = constructPathSpecial(path, eventData.get("a0"), eventData.get("cwd"), pid, eventId, syscall);
        	if(path == null){
        		log(Level.INFO, "Failed to create path", null, eventId, syscall);
        		return;
        	}
        	artifactIdentifier = getValidArtifactIdentifierForPath(path);
        	modeArgument = eventData.get("a2");
        }else{
        	log(Level.INFO, "Unexpected syscall '"+syscall+"' in CHMOD handler", null, eventId, syscall);
        	return;
        }
        
        String mode = new BigInteger(modeArgument).toString(8);
        Artifact vertex = putArtifact(eventData, artifactIdentifier, true);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
        wgb.addAnnotation("mode", mode);
        putEdge(wgb, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
    }

    private void handlePipe(Map<String, String> eventData, SYSCALL syscall) {
    	// pipe() receives the following message(s):
        // - SYSCALL
        // - FD_PAIR
        // - EOE
        String pid = eventData.get("pid");

        String fd0 = eventData.get("fd0");
        String fd1 = eventData.get("fd1");
        ArtifactIdentifier readPipeIdentifier = new UnnamedPipeIdentifier(pid, fd0, fd1);
        ArtifactIdentifier writePipeIdentifier = new UnnamedPipeIdentifier(pid, fd0, fd1);
        descriptors.addDescriptor(pid, fd0, readPipeIdentifier, true);
        descriptors.addDescriptor(pid, fd1, writePipeIdentifier, false);
        
        getArtifactProperties(readPipeIdentifier).markNewEpoch(eventData.get("eventid"));
        getArtifactProperties(writePipeIdentifier).markNewEpoch(eventData.get("eventid"));
    }    
    
    
    /*private void handleNetfilterPacketEvent(Map<String, String> eventData){
      Refer to the following link for protocol numbers
//    	http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
    	String protocol = eventData.get("proto");
    	
    	if(protocol.equals("6") || protocol.equals("17")){ // 6 is tcp and 17 is udp
    		String length = eventData.get("len");
        	
        	hook = 1 is input, hook = 3 is forward
        	String hook = eventData.get("hook");
        	
        	String sourceAddress = eventData.get("saddr");
        	String destinationAddress = eventData.get("daddr");
        	
        	String sourcePort = eventData.get("sport");
        	String destinationPort = eventData.get("dport");
        	
        	String time = eventData.get("time");
        	String eventId = eventData.get("eventid");
        	
        	SocketInfo source = new SocketInfo(sourceAddress, sourcePort);
        	SocketInfo destination = new SocketInfo(destinationAddress, destinationPort);
    	}
    	
    }*/
    
    private void handleBind(Map<String, String> eventData, SYSCALL syscall) {
    	// bind() receives the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
        String saddr = eventData.get("saddr");
        ArtifactIdentifier artifactIdentifier = parseSaddr(saddr, eventData.get("eventid"), syscall);
        if (artifactIdentifier != null) {
        	if(UnixSocketIdentifier.class.equals(artifactIdentifier.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
            String pid = eventData.get("pid");
            //NOTE: not using the file descriptor. using the socketFD here!
            //Doing this to be able to link the accept syscalls to the correct artifactIdentifier.
            //In case of unix socket accept, the saddr is almost always reliably invalid
            String socketFd = eventData.get("a0");
            descriptors.addDescriptor(pid, socketFd, artifactIdentifier, false);
        }else{
        	log(Level.INFO, "Invalid saddr '"+saddr+"'", null, eventData.get("eventid"), syscall);
        }
    }
    
    private void handleConnect(Map<String, String> eventData) {
    	//connect() receives the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
    	String eventId = eventData.get("eventid");
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentifier parsedArtifactIdentifier = parseSaddr(saddr, eventId, SYSCALL.CONNECT);
        if (parsedArtifactIdentifier != null) {
        	if(UnixSocketIdentifier.class.equals(parsedArtifactIdentifier.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        	Process process = putProcess(eventData);
        	// update file descriptor table
            String fd = eventData.get("a0");
            descriptors.addDescriptor(pid, fd, parsedArtifactIdentifier, false);
            getArtifactProperties(parsedArtifactIdentifier).markNewEpoch(eventData.get("eventid"));
        	
            Artifact artifact = putArtifact(eventData, parsedArtifactIdentifier, false);
            WasGeneratedBy wgb = new WasGeneratedBy(artifact, process);
            putEdge(wgb, getOperation(SYSCALL.CONNECT), time, eventData.get("eventid"), DEV_AUDIT);
        }else{
        	log(Level.INFO, "Invalid saddr '"+saddr+"'", null, eventId, SYSCALL.CONNECT);
        }
    }

    private void handleAccept(Map<String, String> eventData, SYSCALL syscall) {
    	//accept() & accept4() receive the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
    	String eventId = eventData.get("eventid");
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String socketFd = eventData.get("a0"); //the fd on which the connection was accepted, not the fd of the connection
        String fd = eventData.get("exit"); //fd of the connection
        String saddr = eventData.get("saddr");
                
        ArtifactIdentifier boundArtifactIdentifier = descriptors.getDescriptor(pid, socketFd); //previously bound
        ArtifactIdentifier parsedArtifactIdentifier = parseSaddr(saddr, eventId, syscall);
        
        //discarding cases that cannot be handled
        if(parsedArtifactIdentifier == null){ //if null then cannot do anything unless bound artifact was unix socket
        	if(boundArtifactIdentifier == null || !UnixSocketIdentifier.class.equals(boundArtifactIdentifier.getClass())){
        		log(Level.INFO, "Invalid or no 'saddr' in syscall", null, eventId, syscall);
        		return;
        	}else{ //is a unix socket identifier
        		if(UnixSocketIdentifier.class.equals(boundArtifactIdentifier.getClass()) && !UNIX_SOCKETS){
            		return;
            	}
        		descriptors.addDescriptor(pid, fd, boundArtifactIdentifier, false);
        	}
        }else if(UnixSocketIdentifier.class.equals(parsedArtifactIdentifier.getClass())){
        	if(UnixSocketIdentifier.class.equals(parsedArtifactIdentifier.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        	if(boundArtifactIdentifier == null || !UnixSocketIdentifier.class.equals(boundArtifactIdentifier.getClass())){ //we need the bound address always
        		log(Level.INFO, "Invalid or no 'saddr' in syscall", null, eventId, syscall);
        		return;
        	}else{ //is a unix socket identifier
        		descriptors.addDescriptor(pid, fd, boundArtifactIdentifier, false);
        	}
        }else if(NetworkSocketIdentifier.class.equals(parsedArtifactIdentifier.getClass())){
        	//anything goes. dont really need the bound. but if it is there then its good
        	if(boundArtifactIdentifier == null || !NetworkSocketIdentifier.class.equals(boundArtifactIdentifier.getClass())){
        		descriptors.addDescriptor(pid, socketFd, new NetworkSocketIdentifier("", "", "", "", ""), false); //add a dummy one if null or a mismatch
        	}
        	NetworkSocketIdentifier boundSocketIdentifier = (NetworkSocketIdentifier)descriptors.getDescriptor(pid, socketFd);
        	NetworkSocketIdentifier parsedSocketIdentifier = (NetworkSocketIdentifier)parsedArtifactIdentifier;
        	ArtifactIdentifier socketIdentifier = new NetworkSocketIdentifier(parsedSocketIdentifier.getSourceHost(), parsedSocketIdentifier.getSourcePort(),
        			boundSocketIdentifier.getDestinationHost(), boundSocketIdentifier.getDestinationPort(), parsedSocketIdentifier.getProtocol());
        	descriptors.addDescriptor(pid, fd, socketIdentifier, false);
        }else{
        	log(Level.INFO, "Unexpected artifact type '"+parsedArtifactIdentifier.getClass()+"'", null, eventId, syscall);
        	return;
        }        
        
        //if reached this point then can process the accept event 
        
        ArtifactIdentifier artifactIdentifier = descriptors.getDescriptor(pid, fd);
        if (artifactIdentifier != null) { //well shouldn't be null since all cases handled above but for future code changes
        	Process process = putProcess(eventData);
        	getArtifactProperties(artifactIdentifier).markNewEpoch(eventData.get("eventid"));
            Artifact socket = putArtifact(eventData, artifactIdentifier, false);
            Used used = new Used(process, socket);
            putEdge(used, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
        }else{
        	log(Level.INFO, "No artifact found for pid '"+pid+"' and fd '"+fd+"'", null, eventId, syscall);
        }
    }

    private void handleSend(Map<String, String> eventData, SYSCALL syscall) {
    	// sendto()/sendmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        Process process = putProcess(eventData);

        String fd = eventData.get("a0");
        String time = eventData.get("time");
        String bytesSent = eventData.get("exit");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd); 
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }else{
        	if(UnixSocketIdentifier.class.equals(descriptors.getDescriptor(pid, fd).getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        }
       
        ArtifactIdentifier artifactIdentifier = descriptors.getDescriptor(pid, fd);
        
        if(artifactIdentifier != null){
	        Artifact vertex = putArtifact(eventData, artifactIdentifier, true);
	        WasGeneratedBy wgb = new WasGeneratedBy(vertex, process);
	        wgb.addAnnotation("size", bytesSent);
	        putEdge(wgb, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
        }        
    }
    
    private void handleRecv(Map<String, String> eventData, SYSCALL syscall) {
    	// recvfrom()/recvmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        
        Process process = putProcess(eventData);

        String fd = eventData.get("a0");
        String time = eventData.get("time");
        String bytesReceived = eventData.get("exit");

        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd); 
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }else{
        	if(UnixSocketIdentifier.class.equals(descriptors.getDescriptor(pid, fd).getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        }
        
        ArtifactIdentifier artifactIdentifier = descriptors.getDescriptor(pid, fd);     
        
        Artifact vertex = putArtifact(eventData, artifactIdentifier, false);
    	Used used = new Used(process, vertex);
        used.addAnnotation("size", bytesReceived);
        putEdge(used, getOperation(syscall), time, eventData.get("eventid"), DEV_AUDIT);
    }
    
    /**
     * Outputs formatted messages in the format-> [Event ID:###, SYSCALL:... MSG Exception]
     *  
     * @param level Level of the log message
     * @param msg Message to print
     * @param exception Exception (if any)
     * @param eventId id of the audit event
     * @param syscall system call of the audit event
     */
    private void log(Level level, String msg, Exception exception, String eventId, SYSCALL syscall){
    	String msgPrefix = "";
    	if(eventId != null && syscall != null){
    		msgPrefix = "[Event ID:"+eventId+", SYSCALL:"+syscall+"] ";
    	}else if(eventId != null && syscall == null){
    		msgPrefix = "[Event ID:"+eventId+"] ";
    	}else if(eventId == null && syscall != null){
    		msgPrefix = "[SYSCALL:"+syscall+"] ";
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
     * @param source source of the edge i.e. {@link #DEV_AUDIT /dev/audit}, {@link #PROC_FS /proc}, or
     * {@link #BEEP beep}
     */
    private void putEdge(AbstractEdge edge, String operation, String time, String eventId, String source){
    	if(edge != null && edge.getSourceVertex() != null && edge.getDestinationVertex() != null){
    		if(time != null){
    			edge.addAnnotation("time", time);
    		}
    		if(eventId != null){
	    		edge.addAnnotation(EVENT_ID, eventId);
	    	}
	    	if(source != null){
	    		edge.addAnnotation(SOURCE, source);
	    	}
    		if(operation != null){
    			edge.addAnnotation("operation", operation);
    		}			
    		putEdge(edge);
    	}else{
    		log(Level.WARNING, "Failed to put edge. edge = "+edge+", sourceVertex = "+(edge != null ? edge.getSourceVertex() : null)+", "
    				+ "destination vertex = "+(edge != null ? edge.getDestinationVertex() : null)+", operation = "+operation+", "
    				+ "time = "+time+", eventId = "+eventId+", source = " + source, null, eventId, SYSCALL.valueOf(operation.toUpperCase()));
    	}
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
     * @param eventId event id as gotten in the audit log. Used for logging.
     * @param syscall system call from where this function is called. Used for logging.
     * @return
     */
    private String constructPathSpecial(String path, String fdString, String cwd, String pid, String eventId, SYSCALL syscall){
    	
    	if(path == null){
    		log(Level.INFO, "Missing PATH record", null, eventId, syscall);
    		return null;
    	}else if(path.startsWith(File.separator)){ //is absolute
    		return constructPath(path, cwd); //just getting the path resolved if it has .. or .
    	}else{ //is not absolute
    		if(fdString == null){
    			log(Level.INFO, "Missing FD", null, eventId, syscall);
    			return null;
    		}else{
    			Long fd = CommonFunctions.parseLong(fdString, -1L);
    			if(fd == AT_FDCWD){
    				if(cwd == null){
    					log(Level.INFO, "Missing CWD record", null, eventId, syscall);
    					return null;
    				}else{
    					path = constructPath(path, cwd);
    					return path;
    				}
    			}else{
    				ArtifactIdentifier artifactIdentifier = descriptors.getDescriptor(pid, String.valueOf(fd));
    				if(artifactIdentifier == null){
    					log(Level.INFO, "No FD with number '"+fd+"' for pid '"+pid+"'", null, eventId, syscall);
    					return null;
    				}else if(!FileIdentifier.class.equals(artifactIdentifier.getClass())){
    					log(Level.INFO, "FD with number '"+fd+"' for pid '"+pid+"' must be of type file but is '"+artifactIdentifier.getClass()+"'", null, eventId, syscall);
        				return null;
        			}else{
        				path = constructPath(path, ((FileIdentifier)artifactIdentifier).getPath());
        				if(path == null){
        					log(Level.INFO, "Invalid path ("+((FileIdentifier)artifactIdentifier).getPath()+") for fd with number '"+fd+"' of pid '"+pid+"'", null, eventId, syscall);
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
     * @param updateVersion true or false to tell if the version has to be updated. Is modified based on the rules in the function
     * @return the created artifact
     */
    private Artifact putArtifact(Map<String, String> eventData, ArtifactIdentifier artifactIdentifier,
    								boolean updateVersion){
    	return putArtifact(eventData, artifactIdentifier, updateVersion, null);
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
     * @param eventData a map that contains the keys eventid, time, and pid. Used for creating the UPDATE edge. 
     * @param artifactIdentifier artifact to create
     * @param updateVersion true or false to tell if the version has to be updated. Is modified based on the rules in the function
     * @param useThisSource the source value to use. if null then {@link #DEV_AUDIT DEV_AUDIT} is used.
     * @return the created artifact
     */
    private Artifact putArtifact(Map<String, String> eventData, ArtifactIdentifier artifactIdentifier, 
    								boolean updateVersion, String useThisSource){
    	if(artifactIdentifier == null || (artifactIdentifier.getClass().equals(UnixSocketIdentifier.class) && !UNIX_SOCKETS)){
    		return null;
    	}
    
    	ArtifactProperties artifactProperties = getArtifactProperties(artifactIdentifier);

    	Artifact artifact = new Artifact();
    	artifact.addAnnotation("subtype", artifactIdentifier.getSubtype().toString().toLowerCase());
    	artifact.addAnnotations(artifactIdentifier.getAnnotationsMap());
    	
    	if(useThisSource != null){
    		artifact.addAnnotation(SOURCE, useThisSource);
    	}else{
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}

    	Class<? extends ArtifactIdentifier> artifactIdentifierClass = artifactIdentifier.getClass();
    	if(FileIdentifier.class.equals(artifactIdentifierClass)
    			|| NamedPipeIdentifier.class.equals(artifactIdentifierClass)
    			|| UnixSocketIdentifier.class.equals(artifactIdentifierClass)){
    		String path = artifact.getAnnotation("path");
    		if(path != null){
	    		if(updateVersion && path.startsWith("/dev/")){ //need this check for path based identities
	            	updateVersion = false;
	            }
    		}
    	}
    	
    	if(NetworkSocketIdentifier.class.equals(artifactIdentifierClass)){ //if network socket and if no version then don't update version
    		if(!NET_SOCKET_VERSIONING){
    			updateVersion = false;
    		}
    	}
    	
    	//version is always uninitialized if the epoch has been seen so using that to infer about epoch
    	boolean vertexNotSeenBefore = updateVersion || artifactProperties.isVersionUninitialized(); //do this before getVersion because it updates it based on updateVersion flag
    	
    	artifact.addAnnotation("version", String.valueOf(artifactProperties.getVersion(updateVersion)));
    	
    	if(!MemoryIdentifier.class.equals(artifactIdentifierClass)){ //epoch for everything except memory
    		artifact.addAnnotation("epoch", String.valueOf(artifactProperties.getEpoch()));
    	}   	
    	
    	if(vertexNotSeenBefore){//not seen because of either it has been updated or it is the first time it is seen
    		putVertex(artifact);
    	}
    	
    	//always at the end after the vertex has been added
    	if(updateVersion && FileIdentifier.class.equals(artifactIdentifier.getClass())){ //put the version update edge if version updated for a file
    		if(eventData != null){
    			putVersionUpdateEdge(artifact, eventData.get("time"), eventData.get("eventid"), eventData.get("pid"));
    		}else{
    			logger.log(Level.WARNING, "Failed to create version update for artifact '" +artifact + "' because time, eventid and pid missing");
    		}
    	}
    	    	
    	return artifact;
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
     * Convenience function to get a new map with the existing key-values and given key-values replaced. 
     * 
     * @param keyValues existing key-values
     * @param newKeyValues key-values to update or replace
     * @return a new map with existing and updated key-values
     */    
    private Map<String, String> updateKeyValuesInMap(Map<String, String> keyValues, Map<String, String> newKeyValues){
    	Map<String, String> result = new HashMap<String, String>();
    	if(keyValues != null){
    		result.putAll(keyValues);
    	}
    	if(newKeyValues != null){
    		result.putAll(newKeyValues);
    	}
    	return result;
    }
    
    /**
     * Returns either NetworkSocketIdentifier or UnixSocketIdentifier depending on the type in saddr.
     * 
     * If saddr starts with 01 then unix socket
     * If saddr starts with 02 then ipv4 network socket
     * If saddr starts with 0A then ipv6 network socket
     * If none of the above then null returned
     * 
     * Syscall parameter is using to identify (in case of network sockets) whether the address and port are 
     * source or destination ones.
     * 
     * @param saddr a hex string of format 0100... or 0200... or 0A...
     * @param eventId id of the event from where the saddr value is recevied
     * @param syscall syscall in which this saddr was received
     * @return the appropriate subclass of ArtifactIdentifier or null
     */
    private ArtifactIdentifier parseSaddr(String saddr, String eventId, SYSCALL syscall){
    	if(saddr != null && saddr.length() >= 2){
	    	if(saddr.charAt(1) == '1'){ //unix socket
	    		
	    		String path = "";
	        	int start = saddr.indexOf("2F"); //2F in ASCII is '/'. so starting from there since unix file paths start from there
	        	
	        	if(start != -1){ //found
	        		try{
		        		for(; start < saddr.length() - 2; start+=2){
		        			char c = (char)(Integer.parseInt(saddr.substring(start, start+2), 16));
		        			if(c == 0){ //null char
		        				break;
		        			}
		        			path += c;
		        		}
	        		}catch(Exception e){
	        			log(Level.INFO, "Failed to parse saddr value '"+saddr+"'", null, eventId, syscall);
	        			return null;
	        		}
	        	}
	    		
	        	if(path != null && !path.isEmpty()){
	        		return new UnixSocketIdentifier(path);
	        	}
	        }else{ //ip
	    	
	        	String address = null, port = null;
		    	if (saddr.charAt(1) == '2') {
		            port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
		            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
		            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
		            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
		            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
		            address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
		        }else if(saddr.charAt(1) == 'A' || saddr.charAt(1) == 'a'){
		        	port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
		        	int oct1 = Integer.parseInt(saddr.substring(40, 42), 16);
		        	int oct2 = Integer.parseInt(saddr.substring(42, 44), 16);
		        	int oct3 = Integer.parseInt(saddr.substring(44, 46), 16);
		        	int oct4 = Integer.parseInt(saddr.substring(46, 48), 16);
		        	address = String.format("::%s:%d.%d.%d.%d", saddr.substring(36, 40).toLowerCase(), oct1, oct2, oct3, oct4);
		        }
		    	
		    	if(address != null && port != null){
		    		if(syscall == SYSCALL.BIND){ //put address as destination
		    			return new NetworkSocketIdentifier("", "", address, port, "");
		    		}else if(syscall == SYSCALL.CONNECT){ //put address as destination
		    			return new NetworkSocketIdentifier("", "", address, port, "");
		    		}else if(syscall == SYSCALL.ACCEPT || syscall == SYSCALL.ACCEPT4){ //put address as source
		    			return new NetworkSocketIdentifier(address, port, "", "", "");
		    		}else{
		    			log(Level.INFO, "Unsupported syscall to parse saddr '"+saddr+"'", null, eventId, syscall);
		    		}
		    	}
		    	
	        }
    	}
    	
    	return null;
    }
    
    /**
     * Creates a version update edge i.e. from the new version of an artifact to the old version of the same artifact.
     * At the moment only being done for file artifacts. See {@link #putArtifact(Map, ArtifactIdentifier, boolean, String) putArtifact}.
     * 
     * If the previous version number of the artifact is less than 0 then the edge won't be drawn because that means that there was
     * no previous version. 
     * 
     * Doesn't put the two artifact vertices because they should already have been added from where this function is called
     * 
     * @param newArtifact artifact which has the updated version
     * @param time timestamp when this happened
     * @param eventId event id of the new version of the artifact creation
     * @param pid pid of the process which did the update
     */
    private void putVersionUpdateEdge(Artifact newArtifact, String time, String eventId, String pid){
    	if(newArtifact == null || time == null || eventId == null || pid == null){
    		logger.log(Level.WARNING, "Invalid arguments. newArtifact="+newArtifact+", time="+time+", eventId="+eventId+", pid="+pid);
    		return;
    	}
    	Artifact oldArtifact = new Artifact();
    	oldArtifact.addAnnotations(newArtifact.getAnnotations());
    	Long oldVersion = null;
    	try{
    		//this takes care of the case where not to put version update in case an epoch has happened because on epoch version is reset to 0. 
    		//so, there would be no previous version to update from.
    		oldVersion = CommonFunctions.parseLong(newArtifact.getAnnotation("version"), -1L) - 1;
    		if(oldVersion < 0){ //i.e. no previous one, it is the first artifact for the path
    			return;
    		}
    	}catch(Exception e){
    		logger.log(Level.WARNING, "Failed to create version update edge between (" + newArtifact.toString() + ") and ("+oldArtifact.toString()+")" , e);
    		return;
    	}
    	oldArtifact.addAnnotation("version", String.valueOf(oldVersion));
    	WasDerivedFrom versionUpdate = new WasDerivedFrom(newArtifact, oldArtifact);
    	versionUpdate.addAnnotation("pid", pid);
    	putEdge(versionUpdate, getOperation(SYSCALL.UPDATE), time, eventId, DEV_AUDIT);
    }

    /**
     * Groups system call names by functionality and returns that name to simplify identification of the type of system call.
     * Grouping only done if {@link #SIMPLIFY SIMPLIFY} is true otherwise the system call name is returned simply.
     * 
     * @param syscall system call to get operation for
     * @return operation corresponding to the syscall
     */
    private String getOperation(SYSCALL syscall){
    	if(syscall == null){
    		return null;
    	}
    	SYSCALL returnSyscall = syscall;
    	if(SIMPLIFY){
    		switch (syscall) {
	    		case UNLINK:
	    		case UNLINKAT:
	    			returnSyscall = SYSCALL.UNLINK;
	    			break;
	    		case CLOSE:
	    			returnSyscall = SYSCALL.CLOSE;
	    			break;
	    		case PIPE:
	    		case PIPE2:
	    			returnSyscall = SYSCALL.PIPE;
	    			break;
	    		case EXIT:
	    		case EXIT_GROUP:
	    			returnSyscall = SYSCALL.EXIT;
	    			break;
	    		case DUP:
	    		case DUP2:
	    		case DUP3:
	    			returnSyscall = SYSCALL.DUP;
	    			break;
	    		case BIND:
	    			returnSyscall = SYSCALL.BIND;
	    			break;
	    		case MKNOD:
	    		case MKNODAT:
	    			returnSyscall = SYSCALL.MKNOD;
	    			break;
	    		case UNKNOWN:
	    			returnSyscall = SYSCALL.UNKNOWN;
	    			break;
	    		case UPDATE:
	    			returnSyscall = SYSCALL.UPDATE;
	    			break;
	    		case EXECVE:
	    			returnSyscall = SYSCALL.EXECVE;
	    			break;
	    		case UNIT:
	    			returnSyscall = SYSCALL.UNIT;
	    			break;
    			case RENAME:
    			case RENAMEAT:
	    			returnSyscall = SYSCALL.RENAME;
	    			break;
    			case CREATE:
    			case CREAT:
	    			returnSyscall = SYSCALL.CREATE;
	    			break;
    			case MMAP:
    			case MMAP2:
    				returnSyscall = SYSCALL.MMAP;
    				break;
    			case MPROTECT:
    				returnSyscall = SYSCALL.MPROTECT;
    				break;
    			case LOAD:
    				returnSyscall = SYSCALL.LOAD;
    				break;
    			case OPEN:
    			case OPENAT:
    				returnSyscall = SYSCALL.OPEN;
    				break;
				case FORK:
				case VFORK:
					returnSyscall = SYSCALL.FORK;
					break;
				case CLONE:
					returnSyscall = SYSCALL.CLONE;
					break;
				case CHMOD:
				case FCHMOD:
				case FCHMODAT:
					returnSyscall = SYSCALL.CHMOD;
					break;
				case SENDTO:
				case SENDMSG:
					returnSyscall = SYSCALL.SEND;
					break;
				case RECVFROM:
				case RECVMSG:
					returnSyscall = SYSCALL.RECV;
					break;
				case TRUNCATE:
				case FTRUNCATE:
					returnSyscall = SYSCALL.TRUNCATE;
					break;
				case READ:
				case READV:
				case PREAD64:
					returnSyscall = SYSCALL.READ;
					break;
				case WRITE:
				case WRITEV:
				case PWRITE64:
					returnSyscall = SYSCALL.WRITE;
					break;
				case ACCEPT:
				case ACCEPT4:
					returnSyscall = SYSCALL.ACCEPT;
					break;
				case CONNECT:
					returnSyscall = SYSCALL.CONNECT;
					break;
				case SYMLINK:
				case LINK:
				case SYMLINKAT:
				case LINKAT:
					returnSyscall = SYSCALL.LINK;
					break;
				case SETUID:
				case SETREUID:
				case SETRESUID:
					returnSyscall = SYSCALL.SETUID;
					break;
				case KILL:
					returnSyscall = SYSCALL.KILL;
					break;
				default:
					break;
			}
    	}
    	return returnSyscall.toString().toLowerCase();
    }
    
    /**
     * Checks the internal data structure for artifacts to see if a path-based artifact identifier with the same path exists.
     * If multiple type of artifact identities with the same path exist then it returns the one which was created last i.e. with
     * the biggest event id.
     * 
     * If unable to find an artifact identifier with the given path then it returns a file artifact identifier with that path. 
     * Rule: Everything is a file until proven otherwise.
     * 
     * Note: The internal data structure {@link #artifactIdentifierToArtifactProperties artifactIdentifierToArtifactProperties} 
     * has to be accessed directly in this function instead of using the function {@link #getArtifactProperties(ArtifactIdentifier) 
     * getArtifactProperties} because that function adds and returns the properties for the artifact identifier even if doesn't exist.
     * Using that function would say something existed which didn't exist before.
     * 
     * @param path path to check against
     * @return the artifact identifier with the matched path or a file artifact identifier if path didn't match any artifact identifier
     */
    private ArtifactIdentifier getValidArtifactIdentifierForPath(String path){
    	FileIdentifier fileIdentifier = new FileIdentifier(path);
    	NamedPipeIdentifier namedPipeIdentifier = new NamedPipeIdentifier(path);
    	UnixSocketIdentifier unixSocketIdentifier = new UnixSocketIdentifier(path);
    	//NOTE: get them directly without using the utility function. done to get null properties if not initialized yet.
    	//dont use the getArtifactProperties function here
    	ArtifactProperties fileProperties = artifactIdentifierToArtifactProperties.get(fileIdentifier); 
    	ArtifactProperties namedPipeProperties = artifactIdentifierToArtifactProperties.get(namedPipeIdentifier);
    	ArtifactProperties unixSocketProperties = artifactIdentifierToArtifactProperties.get(unixSocketIdentifier);
    	
    	fileProperties = fileProperties == null ? new ArtifactProperties() : fileProperties;
    	namedPipeProperties = namedPipeProperties == null ? new ArtifactProperties() : namedPipeProperties;
    	unixSocketProperties = unixSocketProperties == null ? new ArtifactProperties() : unixSocketProperties;
    	
    	long fileCreationEventId = fileProperties.getCreationEventId();
		long namedPipeCreationEventId = namedPipeProperties.getCreationEventId();
		long unixSocketCreationEventId = namedPipeProperties.getCreationEventId();

		//creation event ids won't be same unless two or more haven't been initialized yet. the uninitialized value would just be equal 
		
		if(fileCreationEventId >= namedPipeCreationEventId && fileCreationEventId >= unixSocketCreationEventId){ //always first because if all equals then we want file
			return fileIdentifier;
		}else if(namedPipeCreationEventId >= fileCreationEventId && namedPipeCreationEventId >= unixSocketCreationEventId) {
			return namedPipeIdentifier; 
		}else if(unixSocketCreationEventId >= fileCreationEventId && unixSocketCreationEventId >= namedPipeCreationEventId){
			return unixSocketIdentifier; 
		}else{
			return fileIdentifier;
		}
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
     * @param eventData eventData that contains the paths information in the format: path[Index], nametype[Index]. example, path0, nametype0 
     * @param nametypeValue one of the above-mentioned values. Case sensitive compare operation on nametypeValue
     * @return returns a Map where key is the index and the value is the path name.
     */
    private Map<Integer, String> getPathsWithNametype(Map<String, String> eventData, String nametypeValue){
    	Map<Integer, String> indexToPathMap = new HashMap<Integer, String>();
    	if(eventData != null && nametypeValue != null){
    		Long items = CommonFunctions.parseLong(eventData.get("items"), 0L);
    		for(int itemcount = 0; itemcount < items; itemcount++){
    			if(nametypeValue.equals(eventData.get("nametype"+itemcount))){
    				indexToPathMap.put(itemcount, eventData.get("path"+itemcount));
    			}
    		}
    	}    	
    	return indexToPathMap;
    }
    
    //////////////////////////////////////// Process and Unit management code below
    
    /**
     * The eventData is used to get the pid and then get the process vertex if it already exists.
     * If the vertex didn't exist then it creates the new one and returns that. The new vertex is created
     * using the information in the eventData. The putVertex function is also called on the vertex if
     * it was created in this function and the new vertex is also added to the internal process map.
     * 
     * @param eventData key value pairs to use to create vertex from. This is in most cases all the key values
     * gotten in a syscall audit record. But additional information can also be added to this to be used by 
     * createProcessVertex function.
     * @return the vertex with the pid in the eventData map
     */
    private Process putProcess(Map<String, String> eventData){
    	return putProcess(eventData, false);
    }
    
    
    /**
     * See {@link #putProcess(Map) putProcess}
     * 
     * @param annotations key value pairs to use to create vertex from. This is in most cases all the key values
     * gotten in a syscall audit record. But additional information can also be added to this to be used by 
     * createProcessVertex function.
     * @param recreateAndReplace if true, it would create a new vertex using information in the eventData and 
     * replace the existing one if there is one. If false, it would recreate the vertex. 
     * @return
     */
    private Process putProcess(Map<String, String> annotations, boolean recreateAndReplace){
    	if(annotations != null){
	    	String pid = annotations.get("pid");
	    	Process process = getProcess(pid);
	        if(process == null || recreateAndReplace){
	        	process = createProcessVertex(annotations);
	        	addProcess(pid, process);
	        	      	
	        	if(!processVertexHasBeenPutBefore(process)){
	        		putVertex(process);
	        	}	        	
	        }
	        return process;
    	}
    	return null;
    }
    
    /**
     * Checks if the hash of the process vertex exists in the map {@link #pidToProcessHashes pidToProcessHashes}
     * 
     * If it didn't exist then it returns false but it does add it
     * 
     * True if it exists
     * False if it doesn't exist
     * 
     * @param process process vertex to check
     * @return true/false
     */
    private boolean processVertexHasBeenPutBefore(Process process){
    	String pid = process.getAnnotation("pid");
    	String hash = DigestUtils.sha256Hex(process.toString());
    	if(pidToProcessHashes.get(pid) == null){
    		pidToProcessHashes.put(pid, new HashSet<String>());
    	}
		if(pidToProcessHashes.get(pid).contains(hash)){
			return true;
		}else{
			pidToProcessHashes.get(pid).add(hash); //add it
			return false;
		}
    }
    
    /**
     * A convenience wrapper of 
     * {@link #createProcessVertex(String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String) createProcessVertex}.
     * 
     * Takes out the values for keys required by the above referenced function and calls it
     * 
     * Note: Values NOT in eventData when received from audit log -> commandline, cwd, SOURCE, start time, unit, iteration, and count.
     * So, put them manually in the map before calling this function.
     * 
     * @param annotations a key value map that usually contains the key values gotten in the audit syscall record
     * @return returns a Process instance with the passed in annotations
     */    
    private Process createProcessVertex(Map<String, String> annotations){
    	//TODO have you added new annotations? if yes then update the annotation copying code in fork and clone handler
    	if(annotations == null){
    		return null;
    	}
    	return createProcessVertex(annotations.get("pid"), annotations.get("ppid"), 
    			//if data from audit log then 'name' is 'comm' otherwise audit annotation key is 'name'
    			annotations.get("name") == null ? annotations.get("comm") : annotations.get("name"), 
    			annotations.get("commandline"),
    			annotations.get("cwd"), annotations.get("uid"), annotations.get("euid"), annotations.get("suid"), annotations.get("fsuid"), 
    			annotations.get("gid"), annotations.get("egid"), annotations.get("sgid"), annotations.get("fsgid"), annotations.get(SOURCE), 
    			annotations.get("start time"), annotations.get("unit"), annotations.get("iteration"), annotations.get("count"));
    }
    
    /**
     * The function to be used always to create a Process vertex. Don't use 'new Process()' invocation directly under any circumstances.
     * 
     * The function applies some rules on various annotations based on global variables. Always use this function to keep things consistent.
     * 
     * @param pid pid of the process
     * @param ppid parent's pid of the process
     * @param name name or 'comm' of the process as in Audit log syscall record
     * @param commandline gotten through execve syscall event only or when copied from an existing one [Optional]
     * @param cwd current working directory of the process [Optional]
     * @param uid user id of the process
     * @param euid effective user id of the process
     * @param suid saved user id of the process
     * @param fsuid file system user id of the process
     * @param gid group id of the process
     * @param egid effective group id of the process
     * @param sgid saved group id of the process
     * @param fsgid file system group id of the process
     * @param source source of the process information: /dev/audit, beep or /proc only. Defaults to /dev/audit if none given.
     * @param startTime time at which the process was created [Optional]
     * @param unit id of the unit loop. [Optional]
     * @param iteration iteration of the unit loop. [Optional]
     * @param count count of a unit with all other annotation values same. [Optional]
     * @return returns a Process instance with the passed in annotations.
     */
    private Process createProcessVertex(String pid, String ppid, String name, String commandline, String cwd, 
    		String uid, String euid, String suid, String fsuid, 
    		String gid, String egid, String sgid, String fsgid,
    		String source, String startTime, String unit, String iteration, String count){
    	
    	Process process = new Process();
    	process.addAnnotation("pid", pid);
    	process.addAnnotation("ppid", ppid);
    	process.addAnnotation("name", name);
    	process.addAnnotation("uid", uid);
    	process.addAnnotation("euid", euid);
    	process.addAnnotation("gid", gid);
    	process.addAnnotation("egid", egid);
    	
    	if(source == null){
    		process.addAnnotation(SOURCE, DEV_AUDIT);
    	}else{
    		process.addAnnotation(SOURCE, source);
    	}
    	
    	//optional annotations below:
    	
    	if(commandline != null){
    		process.addAnnotation("commandline", commandline);
    	}
    	if(cwd != null){
    		process.addAnnotation("cwd", cwd);
    	}
    	
    	if(!SIMPLIFY){
        	process.addAnnotation("suid", suid);
        	process.addAnnotation("fsuid", fsuid);
        	
        	process.addAnnotation("sgid", sgid);
        	process.addAnnotation("fsgid", fsgid);
    	}
    	
    	if(startTime != null){
    		process.addAnnotation("start time", startTime);
    	}
    	
    	if(CREATE_BEEP_UNITS){
    		if(unit == null){
    			process.addAnnotation("unit", "0"); // 0 indicates containing process
    		}else{
    			process.addAnnotation("unit", unit);
    			// The iteration and count annotations are only for units and not the containing process
    			if(iteration != null){
        			process.addAnnotation("iteration", iteration);
        		}
        		if(count != null){
        			process.addAnnotation("count", count);
        		}
    		}
    	}
    	
    	return process;
    }

    /**
     * Creates a process object by reading from the /proc FS. Calls the
 	 * {@link #createProcessVertex(String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String)}
 	 * internally to create the process vertex.
 	 * 
     * @param pid pid of the process to look for in the /proc FS
     * @return a Process object populated using /proc FS
     */
    private Process createProcessFromProcFS(String pid) {
        // Check if this pid exists in the /proc/ filesystem
        if ((new java.io.File("/proc/" + pid).exists())) {
            // The process vertex is created using the proc filesystem.
            try {
                // order of keys in the status file changed. So, now looping through the file to get the necessary ones
                int keysGottenCount = 0; //used to stop reading the file once all the required keys have been gotten
                String line = null, nameline = null, ppidline = null, uidline = null, gidline = null;
                BufferedReader procReader = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
                while ((line = procReader.readLine()) != null && keysGottenCount < 4) {
                    String tokens[] = line.split(":");
                    String key = tokens[0].trim().toLowerCase();
                    switch (key) {
                        case "name":
                            nameline = line;
                            keysGottenCount++;
                            break;
                        case "ppid":
                            ppidline = line;
                            keysGottenCount++;
                            break;
                        case "uid":
                            uidline = line;
                            keysGottenCount++;
                            break;
                        case "gid":
                            gidline = line;
                            keysGottenCount++;
                            break;
                        default:
                            break;
                    }
                }
                procReader.close();

                BufferedReader statReader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
                String statline = statReader.readLine();
                statReader.close();

                BufferedReader cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"));
                String cmdline = cmdlineReader.readLine();
                cmdlineReader.close();

                String stats[] = statline.split("\\s+");
                long elapsedtime = Long.parseLong(stats[21]) * 10;
                long startTime = boottime + elapsedtime;
//                String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(startTime));
//                String stime = Long.toString(startTime);
//
                String name = nameline.split("name:")[1].trim();
                String ppid = ppidline.split("\\s+")[1];
                cmdline = (cmdline == null) ? "" : cmdline.replace("\0", " ").replace("\"", "'").trim();

                // see for order of uid, euid, suid, fsiud: http://man7.org/linux/man-pages/man5/proc.5.html
                String gidTokens[] = gidline.split("\\s+");
                String uidTokens[] = uidline.split("\\s+");
                
                Process newProcess = createProcessVertex(pid, ppid, name, null, null, 
                		uidTokens[1], uidTokens[2], uidTokens[3], uidTokens[4], 
                		gidTokens[1], gidTokens[2], gidTokens[3], gidTokens[4], 
                		PROC_FS, Long.toString(startTime), CREATE_BEEP_UNITS ? "0" : null, null, null);
                
                // newProcess.addAnnotation("starttime_unix", stime);
                // newProcess.addAnnotation("starttime_simple", stime_readable);
                // newProcess.addAnnotation("commandline", cmdline);
                return newProcess;
            } catch (IOException | NumberFormatException e) {
                logger.log(Level.WARNING, "Unable to create process vertex for pid " + pid + " from /proc/", e);
                return null;
            }
        } else {
            return null;
        }
    }
    
    /**
     * Returns the next iteration number for the pid and unitId combination.
     * 
     * @param pid pid of the process to which the unit would belong
     * @param unitId id of the loop of which the iteration is
     * @return the iteration number. Always greater than or equal to 0 and starts from 0
     */        
    private Long getNextIterationNumber(String pid, String unitId){
    	if(iterationNumber.get(pid) == null){
    		iterationNumber.put(pid, new HashMap<String, Long>());
    	}
    	if(iterationNumber.get(pid).get(unitId) == null){
    		iterationNumber.get(pid).put(unitId, -1L);
    	}
    	iterationNumber.get(pid).put(unitId, iterationNumber.get(pid).get(unitId) + 1);
    	return iterationNumber.get(pid).get(unitId);
    }
    
    /**
     * Returns the main process i.e. at the zeroth index in the process stack for the pid, if any.
     * 
     * @param pid pid of the process
     * @return the main process with unit id 0. null if none found.
     */
    private Process getContainingProcessVertex(String pid){
    	if(processUnitStack.get(pid) != null && processUnitStack.get(pid).size() > 0){
    		return processUnitStack.get(pid).getFirst();
    	}
    	return null;
    }
    
    /**
     * This function resets the stack for a pid used to keep the main containing process for a pid
     * and all the units for that pid. It clears the stack. Only call this when it is intended to clear 
     * all the state related to a process, state being the units and the existing process. Example, execve.
     *
     * @param pid pid of the process to return
     * @param process the process to add against the pid
     */
    private void addProcess(String pid, Process process){ 
    	if(pid == null || process == null){
    		logger.log(Level.WARNING, "Failed to add process vertex in addProcess function because pid or process passed is null. Pid {0}, Process {1}", new Object[]{pid, String.valueOf(process)});
    	}else{
	    	iterationNumber.remove(pid); //start iteration count from start
	    	processUnitStack.put(pid, new LinkedList<Process>()); //always reset the stack whenever the main process is being added
	    	processUnitStack.get(pid).addFirst(process);
    	}
    }
    
    /**
     * Checks the internal process hashmap and returns the process vertex. 
     * The process hashmap must have at least one vertex for that pid to return with not null.
     * The function returns the process vertex whichever is on top of the stack using a peek i.e.
     * can be a unit iteration
     * 
     * @param pid pid of the process to return
     * @return process process with the given pid or null (if none found)
     */
    private Process getProcess(String pid){
    	if(processUnitStack.get(pid) != null && !processUnitStack.get(pid).isEmpty()){
    		Process process = processUnitStack.get(pid).peekLast();
    		return process;
    	}
    	return null;
    }
    
    /**
     * Creates a unit iteration vertex, pushes it onto the vertex stack and calls putVertex on the created vertex.
     * Before adding this new unit iteration the previous one is removed from the stack.
     * The new unit iteration vertex is a copy of the annotation process with a new iteration number.
     * Furthermore, a count annotation is used to distinguish between the ith iteration of unitId x and the jth iteration 
     * of unitId x where i=j and a unit end was seen between the ith and the jth iteration, all in one timestamp of audit 
     * log.
     * 
     * Finally, the created unit iteration is added to the memory stack and putVertex is called on it.
     * 
     * @param pid the pid of the process which created this unit iteration
     * @param unitId the id of the unit as gotten from the audit log
     * @param startTime the time at which this unit was created
     * @return the created unit iteration vertex. null if there was no containing process for the pid
     */
    private Process pushUnitIterationOnStack(String pid, String unitId, String startTime){
    	if("0".equals(unitId)){
    		return null; //unit 0 is containing process and cannot have iterations
    	}
    	Process containingProcess = getContainingProcessVertex(pid);
    	if(containingProcess == null){
    		return null;
    	}
    	//get next iteration number and remove the previous iteration (if any) for the pid and unitId combination
    	Long nextIterationNumber = getNextIterationNumber(pid, unitId);
    	if(nextIterationNumber > 0){ //if greater than zero then remove otherwise this is the first one
    		removePreviousIteration(pid, unitId, nextIterationNumber);
    	}
    	
    	if(lastTimestamp == NOT_SET || !startTime.equals(lastTimestamp)){
			lastTimestamp = startTime;
			unitIterationRepetitionCounts.clear();
		}
    	
    	UnitVertexIdentifier unitVertexIdentifier = new UnitVertexIdentifier(pid, unitId, String.valueOf(nextIterationNumber));
		if(unitIterationRepetitionCounts.get(unitVertexIdentifier) == null){
			unitIterationRepetitionCounts.put(unitVertexIdentifier, -1);
		}
		unitIterationRepetitionCounts.put(unitVertexIdentifier, unitIterationRepetitionCounts.get(unitVertexIdentifier)+1);
		
		String count = String.valueOf(unitIterationRepetitionCounts.get(unitVertexIdentifier));
		
		Process newUnit = createBEEPCopyOfProcess(containingProcess, startTime, unitId, String.valueOf(nextIterationNumber), count); //first element is always the main process vertex
    	
		processUnitStack.get(pid).addLast(newUnit);
    	
		if(!processVertexHasBeenPutBefore(newUnit)){
    		putVertex(newUnit);//add to internal buffer. not calling putProcess here because that would reset the stack
		}
    	return newUnit;
    }
    
    /**
     * This function removes all the units of the given unitId and their iteration units from the process stack.
     * Also resets the iteration count for the given unitId
     * 
     *   @param pid pid of the process
     *   @param unitId id of the unit for which the iterations have to be removed
     */
    private void popUnitIterationsFromStack(String pid, String unitId){
    	if("0".equals(unitId) || unitId == null){
    		return; //unit 0 is containing process and should be removed using the addProcess method
    	}
    	if(processUnitStack.get(pid) != null){
    		int size = processUnitStack.get(pid).size() - 1;
    		for(int a = size; a>=0; a--){ //going in reverse because we would be removing elements
    			Process unit = processUnitStack.get(pid).get(a);
    			if(unitId.equals(unit.getAnnotation("unit"))){
    				processUnitStack.get(pid).remove(a);
    			}
    		}
    		if(iterationNumber.get(pid) != null){
    			iterationNumber.get(pid).remove(unitId);//start iteration count from start
    		}
    	}
    }
    
    /**
     * Only removes the previous unit iteration i.e. the currentIteration-1 iteration.
     * Used to remove the last iteration when a new iteration occurs for the pid and unitId
     * combination because each unit iteration's lifetime is only till the new one
     * 
     * @param pid pid of the process to which the unit belongs
     * @param unitId unitId of the unit whose iteration is going to be removed
     * @param currentIteration number of the iteration whose previous one needs to be removed
     */
    private void removePreviousIteration(String pid, String unitId, Long currentIteration){
    	if("0".equals(unitId) || unitId == null || currentIteration == null){
    		return; //unit 0 is containing process and should be removed using the addProcess method
    	}
    	if(processUnitStack.get(pid) != null){
    		int size = processUnitStack.get(pid).size() - 1;
    		for(int a = size; a>=0; a--){ //going in reverse because we would be removing elements
    			Process unit = processUnitStack.get(pid).get(a);
    			if(unitId.equals(unit.getAnnotation("unit"))){
    				Long iteration = CommonFunctions.parseLong(unit.getAnnotation("iteration"), currentIteration+1); 
    				//default value is currentIteration+1, so that we by mistake don't remove an iteration which didn't have a proper iteration value
    				if(iteration < currentIteration){
    					processUnitStack.get(pid).remove(a);
    					//just removing one
    					break;
    				}
    			}
    		}
    	}
    }
    
    /**
     * Creates a copy of the given process with the source annotation being 'beep'  
     * 
     * @param process process to create a copy of
     * @param startTime start time of the unit
     * @param unitId id of the unit
     * @return a copy with the copied and the updated annotations as in the process argument
     */
    private Process createBEEPCopyOfProcess(Process process, String startTime, String unitId, String iteration, String count){
    	if(process == null){
    		return null;
    	}
    	return createProcessVertex(process.getAnnotation("pid"), process.getAnnotation("ppid"), process.getAnnotation("name"), 
    			process.getAnnotation("commandline"), process.getAnnotation("cwd"), 
    			process.getAnnotation("uid"), process.getAnnotation("euid"), process.getAnnotation("suid"), process.getAnnotation("fsuid"), 
    			process.getAnnotation("gid"), process.getAnnotation("egid"), process.getAnnotation("sgid"), process.getAnnotation("fsgid"), 
    			BEEP, startTime, unitId, iteration, count);
    }
    
}

/**
 * Used to uniquely identifies a unit iteration for a single timestamp. See {@link #pushUnitIterationOnStack(String, String, String) pushUnitIterationOnStack}.
 */
class UnitVertexIdentifier{
	private String pid, unitId, iteration;
	public UnitVertexIdentifier(String pid, String unitId, String iteration){
		this.pid = pid;
		this.unitId = unitId;
		this.iteration = iteration;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((iteration == null) ? 0 : iteration.hashCode());
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
		result = prime * result + ((unitId == null) ? 0 : unitId.hashCode());
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
		UnitVertexIdentifier other = (UnitVertexIdentifier) obj;
		if (iteration == null) {
			if (other.iteration != null)
				return false;
		} else if (!iteration.equals(other.iteration))
			return false;
		if (pid == null) {
			if (other.pid != null)
				return false;
		} else if (!pid.equals(other.pid))
			return false;
		if (unitId == null) {
			if (other.unitId != null)
				return false;
		} else if (!unitId.equals(other.unitId))
			return false;
		return true;
	}
}
