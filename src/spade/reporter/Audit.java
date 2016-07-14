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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.reporter.audit.ArtifactIdentity;
import spade.reporter.audit.ArtifactProperties;
import spade.reporter.audit.BatchReader;
import spade.reporter.audit.DescriptorManager;
import spade.reporter.audit.FileIdentity;
import spade.reporter.audit.MemoryIdentity;
import spade.reporter.audit.NamedPipeIdentity;
import spade.reporter.audit.NetworkSocketIdentity;
import spade.reporter.audit.SYSCALL;
import spade.reporter.audit.UnixSocketIdentity;
import spade.reporter.audit.UnknownIdentity;
import spade.reporter.audit.UnnamedPipeIdentity;
import spade.utility.BerkeleyDB;
import spade.utility.CommonFunctions;
import spade.utility.Execute;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
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
    
    // Store log for debugging purposes
    private boolean DEBUG_DUMP_LOG;
    private String DEBUG_DUMP_FILE;
    private BufferedReader eventReader;
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
    
    // Event buffer map based on <audit_record_id <key, value>> pairs
//    private final Map<String, Map<String, String>> eventBuffer = new HashMap<>();
    private ExternalMemoryMap<String, HashMap<String, String>> eventBuffer;
    
    // Map for artifact infos to versions and bytes read/written on sockets 
    private ExternalMemoryMap<ArtifactIdentity, ArtifactProperties> artifactIdentityToArtifactProperties;
    
    private Thread eventProcessorThread = null;
    
    // Group 1: key
    // Group 2: value
    private static final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

    // Group 1: node
    // Group 2: type
    // Group 3: time
    // Group 4: recordid
    private static final Pattern pattern_message_start = Pattern.compile("(?:node=(\\S+) )?type=(\\w*) msg=audit\\(([0-9\\.]+)\\:([0-9]+)\\):\\s*");

    // Group 1: cwd
    private static final Pattern pattern_cwd = Pattern.compile("cwd=\"*((?<=\")[^\"]*(?=\"))\"*");

    // Group 1: item number
    // Group 2: name
    // Group 3: nametype
    private static final Pattern pattern_path = Pattern.compile("item=([0-9]*)\\s*name=\"*((?<=\")[^\"]*(?=\"))\"*.*nametype=([a-zA-Z]*)");
    
    //  Added to indicate in the output from where the process info was read. Either from 
    //  1) procfs or directly from 2) audit log. 
    private static final String SOURCE = "source",
            PROC_FS = "/proc",
            DEV_AUDIT = "/dev/audit",
            BEEP = "beep";
    
    private Thread auditLogThread = null;
    
    private BufferedWriter dumpWriter = null;
        
    private boolean CREATE_BEEP_UNITS = false;
        
    private Map<String, BigInteger> pidToMemAddress = new HashMap<String, BigInteger>(); 
    
    private final static String EVENT_ID = "event id";
    
    private boolean SIMPLIFY = true;
    private boolean PROCFS = false;
    
    private boolean SORTLOG = true;
    
    private boolean NET_SOCKET_VERSIONING = false;
    
    private boolean UNIX_SOCKETS = false;
    
    private boolean WAIT_FOR_LOG_END = false;
    
//  To toggle monitoring of mmap, mmap2 and mprotect syscalls
    private boolean USE_MEMORY_SYSCALLS = true;
    
    private String AUDITCTL_SYSCALL_SUCCESS_FLAG = "1";
    
    private String inputAuditLogFile = null;
    
    // Just a human-friendly renaming of null
    private final static String NOT_SET = null;
    // Timestamp that is used to identify when the time has changed in unit begin. Assumes that the timestamps are received in sorted order
    private String lastTimestamp = NOT_SET;
    // A map to keep track of counts of unit vertex with the same pid, unitid and iteration number.
    private Map<UnitVertexIdentifier, Integer> unitIterationRepetitionCounts = new HashMap<UnitVertexIdentifier, Integer>();
        
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
        DEBUG_DUMP_FILE = SPADE_ROOT + "log/LinuxAudit.log";

        Map<String, String> args = parseKeyValPairs(arguments);
        if (args.containsKey("outputLog")) {
            DEBUG_DUMP_LOG = true;
            if (!args.get("outputLog").isEmpty()) {
                DEBUG_DUMP_FILE = args.get("outputLog");
            }
            try{
            	dumpWriter = new BufferedWriter(new FileWriter(DEBUG_DUMP_FILE));
            }catch(Exception e){
            	logger.log(Level.WARNING, "Failed to create output log writer. Continuing...", e);
            }
        } else {
            DEBUG_DUMP_LOG = false;
        }
        
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
        
        if("false".equals(args.get("sortLog"))){
        	SORTLOG = false;
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
        
        inputAuditLogFile = args.get("inputLog");
        if(inputAuditLogFile != null){ //if a path is passed but it is not a valid file then throw an error
        	
        	if(!new File(inputAuditLogFile).exists()){
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
        	
        	if(SORTLOG){
        		try{
        			String sortedInputAuditLog = inputAuditLogFile + "." + System.currentTimeMillis();
        			logger.log(Level.INFO, "Sorting audit log file '"+inputAuditLogFile+"'");
        			List<String> output = Execute.getOutput("./bin/sortAuditLog " + inputAuditLogFile + " " + sortedInputAuditLog);
        			logger.log(Level.INFO, output.toString());
        			
        			inputAuditLogFile = sortedInputAuditLog;
        			if(!new File(inputAuditLogFile).exists()){
                		logger.log(Level.SEVERE, "Failed to write sorted file to '"+inputAuditLogFile+"'");
                		return false;
                	}else{
                		logger.log(Level.INFO, "File sorted successfully");
                	}
        			
        		}catch(Exception e){
        			logger.log(Level.SEVERE, "Failed to sort input audit log file at '"+inputAuditLogFile+"'", e);
        			return false;
        		}
        	}
        	        	
        	auditLogThread = new Thread(new Runnable(){
    			public void run(){
    				BatchReader inputLogReader = null;
    	        	try{
    	        		inputLogReader = new BatchReader(new BufferedReader(new FileReader(inputAuditLogFile)));
    	        		String line = null;
    	        		while((!shutdown || WAIT_FOR_LOG_END) && (line = inputLogReader.readLine()) != null){
    	        			parseEventLine(line);
    	        		}
    	        		
    	        		// Either the reporter has been shutdown or the log has been ingested
    	        		boolean printed = false;

        	        	while(!shutdown){
        	        		if(!printed && getBuffer().size() == 0){//buffer processed
        	        			printed = true;
        	        			logger.log(Level.INFO, "Audit log processing succeeded: " + inputAuditLogFile);
        	        		}
        	        		try{
        	        			Thread.sleep(BUFFER_DRAIN_DELAY);
        	        		}catch(Exception e){
        	        			//logger.log(Level.SEVERE, null, e);
        	        		}
    					}
        	        	if(!printed){
        	        		logger.log(Level.INFO, "Audit log processing succeeded: " + inputAuditLogFile);
        	        	}
    	        	}catch(Exception e){
    	        		logger.log(Level.SEVERE, "Audit log processing failed: " + inputAuditLogFile, e);
    	        	}finally{
    	        		try{
    	        			if(inputLogReader != null){
    	        				inputLogReader.close();
    	        			}
    	        		}catch(Exception e){
    	        			logger.log(Level.WARNING, "Failed to close audit input log reader", e);
    	        		}
    	        	}
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
	                        eventReader = new BufferedReader(new InputStreamReader(auditProcess.getInputStream()));
	                        while (!shutdown) {
	                            String line = eventReader.readLine();
	                            if ((line != null) && !line.isEmpty()) {
	                                parseEventLine(line);
	                            }
	                        }
	                        //Added this command here because once the spadeSocketBridge process has exited any rules involving it cannot be cleared.
	                        //So, deleting the rules before destroying the spadeSocketBridge process.
	                        Runtime.getRuntime().exec("auditctl -D").waitFor();
	                        eventReader.close();
	                        auditProcess.destroy();
	                    } catch (IOException | InterruptedException e) {
	                        logger.log(Level.SEVERE, "Error launching main runnable thread", e);
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
	            auditRulesWithSuccess += "-S link -S symlink ";
	            auditRulesWithSuccess += "-S clone -S fork -S vfork -S execve ";
	            auditRulesWithSuccess += "-S open -S close -S creat -S openat -S mknodat -S mknod ";
	            auditRulesWithSuccess += "-S dup -S dup2 -S dup3 ";
	            auditRulesWithSuccess += "-S bind -S accept -S accept4 -S connect ";
	            auditRulesWithSuccess += "-S rename ";
	            auditRulesWithSuccess += "-S setuid -S setreuid -S setresuid ";
	            auditRulesWithSuccess += "-S chmod -S fchmod ";
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
	            		return false;
	            	}
	            	
	            	String ppidIgnoreAuditRule = "auditctl -a exit,never -F ppid="+pidsToIgnore.get(a);
	            	if(!addAuditctlRule(ppidIgnoreAuditRule)){
	            		return false;
	            	}
	            }
	            
            	if(!addAuditctlRule(auditRuleWithoutSuccess + fieldsForAuditRule)){
            		return false;
            	}
            	
            	//add the main rule. ALWAYS ADD THIS AFTER THE ABOVE INDIVIDUAL RULES HAVE BEEN ADDED TO AVOID INCLUSION OF AUDIT INFO OF ABOVE PIDS
            	if(!addAuditctlRule(auditRulesWithSuccess + fieldsForAuditRule)){
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
	                    Process processVertex = createProcessFromProcFS(currentPID);
	                    addProcess(currentPID, processVertex);
	                    Process parentVertex = getProcess(processVertex.getAnnotation("ppid"));
	                    putVertex(processVertex);
	                    if (parentVertex != null) {
	                        WasTriggeredBy wtb = new WasTriggeredBy(processVertex, parentVertex);
	                        addEventIdAndSourceAnnotationToEdge(wtb, "0", PROC_FS);
	                        wtb.addAnnotation("operation", getOperation(SYSCALL.UNKNOWN));
	                        putEdge(wtb);
	                    }
	
	                    // Get existing file descriptors for this process
	                    Map<String, ArtifactIdentity> fds = getFileDescriptors(currentPID);
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
             String eventBufferCacheDatabasePath = configMap.get("cacheDatabasePath") + File.separatorChar + "eventbuffer_" + currentTime;
             String artifactsCacheDatabasePath = configMap.get("cacheDatabasePath") + File.separatorChar + "artifacts_" + currentTime;
             try{
     	        FileUtils.forceMkdir(new File(eventBufferCacheDatabasePath));
     	        FileUtils.forceMkdir(new File(artifactsCacheDatabasePath));
     	        FileUtils.forceDeleteOnExit(new File(eventBufferCacheDatabasePath));
     	        FileUtils.forceDeleteOnExit(new File(artifactsCacheDatabasePath));
             }catch(Exception e){
             	logger.log(Level.SEVERE, "Failed to create cache database directories", e);
             	return false;
             }
             
             try{
             	Integer eventBufferCacheSize = CommonFunctions.parseInt(configMap.get("eventBufferCacheSize"), null);
             	String eventBufferDatabaseName = configMap.get("eventBufferDatabaseName");
             	Double eventBufferFalsePositiveProbability = CommonFunctions.parseDouble(configMap.get("eventBufferBloomfilterFalsePositiveProbability"), null);
             	Integer eventBufferExpectedNumberOfElements = CommonFunctions.parseInt(configMap.get("eventBufferBloomFilterExpectedNumberOfElements"), null);
             	
             	Integer artifactsCacheSize = CommonFunctions.parseInt(configMap.get("artifactsCacheSize"), null);
             	String artifactsDatabaseName = configMap.get("artifactsDatabaseName");
             	Double artifactsFalsePositiveProbability = CommonFunctions.parseDouble(configMap.get("artifactsBloomfilterFalsePositiveProbability"), null);
             	Integer artifactsExpectedNumberOfElements = CommonFunctions.parseInt(configMap.get("artifactsBloomFilterExpectedNumberOfElements"), null);
             	
             	logger.log(Level.INFO, "Audit cache properties: eventBufferCacheSize={0}, eventBufferDatabaseName={1}, "
             			+ "eventBufferBloomfilterFalsePositiveProbability={2}, eventBufferBloomFilterExpectedNumberOfElements={3}, "
             			+ "artifactsCacheSize={4}, artifactsDatabaseName={5}, artifactsBloomfilterFalsePositiveProbability={6}, "
             			+ "artifactsBloomFilterExpectedNumberOfElements={7}", new Object[]{eventBufferCacheSize, eventBufferDatabaseName,
             					eventBufferFalsePositiveProbability, eventBufferExpectedNumberOfElements, artifactsCacheSize, 
             					artifactsDatabaseName, artifactsFalsePositiveProbability, artifactsExpectedNumberOfElements});
             	
             	if(eventBufferCacheSize == null || eventBufferDatabaseName == null || eventBufferFalsePositiveProbability == null || 
             			eventBufferExpectedNumberOfElements == null || artifactsCacheSize == null || artifactsDatabaseName == null || 
             			artifactsFalsePositiveProbability == null || artifactsExpectedNumberOfElements == null){
             		logger.log(Level.SEVERE, "Undefined cache properties in Audit config");
             		return false;
             	}
             	
             	eventBuffer = new ExternalMemoryMap<String, HashMap<String, String>>(eventBufferCacheSize, 
                 				new BerkeleyDB<HashMap<String, String>>(eventBufferCacheDatabasePath, eventBufferDatabaseName), 
                 				eventBufferFalsePositiveProbability, eventBufferExpectedNumberOfElements);
                artifactIdentityToArtifactProperties = 
                 		new ExternalMemoryMap<ArtifactIdentity, ArtifactProperties>(artifactsCacheSize, 
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
            
        	//ignoring these pids using uid now
//            // Get the PIDs of SPADE's JVM from /proc/self
//            File[] procTaskDirectories = new File("/proc/self/task").listFiles();
//            for (File procTaskDirectory : procTaskDirectories) {
//                String pid = procTaskDirectory.getCanonicalFile().getName();
//                pids.add(pid);
//            }
            
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
    
	private Map<String, ArtifactIdentity> getFileDescriptors(String pid){
		
		if(auditLogThread != null  // the audit log is being read from a file.
				|| !PROCFS){ // the flag to read from procfs is false
			return null;
		}
    	
    	Map<String, ArtifactIdentity> fds = new HashMap<String, ArtifactIdentity>();
    	
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
		    							ArtifactIdentity pipeInfo = new UnnamedPipeIdentity(fd, inodefd0.get(inode));
		    							fds.put(fd, pipeInfo);
		    							fds.put(inodefd0.get(inode), pipeInfo);
		    							inodefd0.remove(inode);
		    						}
	    						}else{ //named pipe
	    							fds.put(fd, new NamedPipeIdentity(path));
	    						}	    						
	    					}else if("ipv4".equals(type) || "ipv6".equals(type)){
	    						String protocol = tokens[7];
	    						//example token 8 = 10.0.2.15:35859->172.231.72.152:443 (ESTABLISHED)
	    						String[] srchostport = tokens[8].split("->")[0].split(":");
	    						String[] dsthostport = tokens[8].split("->")[1].split("\\s+")[0].split(":");
	    						fds.put(fd, new NetworkSocketIdentity(srchostport[0], srchostport[1], dsthostport[0], dsthostport[1], protocol));
	    					}else if("reg".equals(type) || "chr".equals(type)){
	    						String path = tokens[8];
	    						fds.put(fd, new FileIdentity(path));  						
	    					}else if("unix".equals(type)){
	    						String path = tokens[8];
	    						if(!path.equals("socket")){
	    							fds.put(fd, new UnixSocketIdentity(path));
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

	//TODO What to do when WAIT_FOR_LOG_END is set to true and auditLogThread won't stop on exit?
    @Override
    public boolean shutdown() {
        shutdown = true;
        try {
        	if(dumpWriter != null){
        		dumpWriter.close();
        	}
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
    
    private void parseEventLine(String line) {
    	
    	if (DEBUG_DUMP_LOG) {
    		try{
    			dumpWriter.write(line + System.getProperty("line.separator"));
            	dumpWriter.flush();
    		}catch(Exception e){
    			logger.log(Level.WARNING, "Failed to write to output log", e);
    		}
        }
    	
        Matcher event_start_matcher = pattern_message_start.matcher(line);
        if (event_start_matcher.find()) {
//            String node = event_start_matcher.group(1);
            String type = event_start_matcher.group(2);
            String time = event_start_matcher.group(3);
            String eventId = event_start_matcher.group(4);
            String messageData = line.substring(event_start_matcher.end());
            
            if(eventBuffer.get(eventId) == null){
            	eventBuffer.put(eventId, new HashMap<String, String>());
            	eventBuffer.get(eventId).put("eventid", eventId);
            }
            
            if (type.equals("SYSCALL")) {
                Map<String, String> eventData = parseKeyValPairs(messageData);
                eventData.put("time", time);
            	eventBuffer.get(eventId).putAll(eventData);
            } else if (type.equals("EOE")) {
                finishEvent(eventId);
            } else if (type.equals("CWD")) {
                Matcher cwd_matcher = pattern_cwd.matcher(messageData);
                if (cwd_matcher.find()) {
                    String cwd = cwd_matcher.group(1);
                    eventBuffer.get(eventId).put("cwd", cwd);
                }
            } else if (type.equals("PATH")) {
                Matcher path_matcher = pattern_path.matcher(messageData);
                if (path_matcher.find()) {
                    String item = path_matcher.group(1);
                    String name = path_matcher.group(2);
                    String nametype = path_matcher.group(3);
                    eventBuffer.get(eventId).put("path" + item, name);
                    eventBuffer.get(eventId).put("nametype" + item, nametype);
                }
            } else if (type.equals("EXECVE")) {
                Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                while (key_value_matcher.find()) {
                    eventBuffer.get(eventId).put("execve_" + key_value_matcher.group(1), key_value_matcher.group(2));
                }
            } else if (type.equals("FD_PAIR")) {
                Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                while (key_value_matcher.find()) {
                    eventBuffer.get(eventId).put(key_value_matcher.group(1), key_value_matcher.group(2));
                }
            } else if (type.equals("SOCKETCALL")) {
                Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                while (key_value_matcher.find()) {
                    eventBuffer.get(eventId).put("socketcall_" + key_value_matcher.group(1), key_value_matcher.group(2));
                }
            } else if (type.equals("SOCKADDR")) {
                Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                while (key_value_matcher.find()) {
                    eventBuffer.get(eventId).put(key_value_matcher.group(1), key_value_matcher.group(2));
                }
            } else if(type.equals("NETFILTER_PKT")){
            	Matcher key_value_matcher = pattern_key_value.matcher(messageData);
            	while (key_value_matcher.find()) {
                    eventBuffer.get(eventId).put(key_value_matcher.group(1), key_value_matcher.group(2));
                }
            	finishEvent(eventId);
            } else if (type.equals("MMAP")){
            	Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                while (key_value_matcher.find()) {
                    eventBuffer.get(eventId).put(key_value_matcher.group(1), key_value_matcher.group(2));
                }
            } else if(type.equals("PROCTITLE")){
            	//event type not being handled at the moment. TO-DO
            } else {
                logger.log(Level.WARNING, "unknown type {0} for message: {1}", new Object[]{type, line});
            }
            
        } else {
            logger.log(Level.WARNING, "unable to match line: {0}", line);
        }
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
    
    private void finishEvent(String eventId){

    	if (eventBuffer.get(eventId) == null) {
    		logger.log(Level.WARNING, "EOE for eventID {0} received with no prior Event Info", new Object[]{eventId});
    		return;
    	}

//    	if("NETFILTER_PKT".equals(eventBuffer.get(eventId).get("type"))){ //for events with no syscalls
//    		try{
//    			handleNetfilterPacketEvent(eventBuffer.get(eventId));
//    		}catch(Exception e){
//    			logger.log(Level.WARNING, "Error processing finish syscall event with event id '"+eventId+"'", e);
//    		}
//    	}else{ //for events with syscalls
//    		handleSyscallEvent(eventId);
//    	}
    	
    	handleSyscallEvent(eventId);
    }
    
    private void handleSyscallEvent(String eventId) {
    	try {

    		Map<String, String> eventData = eventBuffer.get(eventId);
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
	    			//continue and log these failed syscalls
    			}else{ //for all others don't log
    				eventBuffer.remove(eventId);
	    			return;
    			}
    		}
    		
    		//convert all arguments from hexadecimal format to decimal format and replace them. done for convenience here and to avoid issues. 
    		for(int argumentNumber = 0; argumentNumber<4; argumentNumber++){ //only 4 arguments received from linux audit
    			try{
    				eventData.put("a"+argumentNumber, new BigInteger(eventData.get("a"+argumentNumber), 16).toString());
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
	    			handleLink(eventData, syscall);
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
	    			handleRename(eventData);
	    			break;
	    		case SETREUID:
	    		case SETRESUID:
	    		case SETUID:
	    			handleSetuid(eventData, syscall);
	    			break; 
	    		case CHMOD:
	    		case FCHMOD:
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
//                case SOCKET: // socket()
//                    break;
	    		
	    		default: //SYSCALL.UNSUPPORTED
	    			logger.log(Level.WARNING, "Unsupported syscall '"+syscallNum+"' for eventid '" + eventId + "'");
    		}
    		eventBuffer.remove(eventId);
    	} catch (Exception e) {
    		logger.log(Level.WARNING, "Error processing finish syscall event with eventid '"+eventId+"'", e);
    	}
    }
    
    private void handleExit(Map<String, String> eventData, SYSCALL syscall){
    	//TODO
    }
    
    private void handleIOEvent(SYSCALL syscall, Map<String, String> eventData){
    	String pid = eventData.get("pid");
    	String fd = eventData.get("a0");
    	Class<? extends ArtifactIdentity> artifactInfoClass = null;

    	if(descriptors.getDescriptor(pid, fd) != null){
    		artifactInfoClass = descriptors.getDescriptor(pid, fd).getClass();
    	}
    	
    	if(artifactInfoClass == null || UnknownIdentity.class.equals(artifactInfoClass)){ //either a new unknown i.e. null or a previously seen unknown
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
    	}else if(NetworkSocketIdentity.class.equals(artifactInfoClass) || UnixSocketIdentity.class.equals(artifactInfoClass)){ 
    		handleNetworkIOEvent(syscall, eventData);
    	}else if(FileIdentity.class.equals(artifactInfoClass) || MemoryIdentity.class.equals(artifactInfoClass) 
    			|| UnnamedPipeIdentity.class.equals(artifactInfoClass) || NamedPipeIdentity.class.equals(artifactInfoClass)){
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
    
    private void handleMmap(Map<String, String> eventData, SYSCALL syscall){
    	// mmap() receive the following message(s):
    	// - MMAP
        // - SYSCALL
        // - EOE
    	
    	if(!USE_MEMORY_SYSCALLS){
    		return;
    	}
    	
    	String pid = eventData.get("pid");
    	String time = eventData.get("time");
    	String address = new BigInteger(eventData.get("exit")).toString(16); //convert to hexadecimal
    	String length = new BigInteger(eventData.get("a1")).toString(16); //convert to hexadecimal
    	String protection = new BigInteger(eventData.get("a2")).toString(16); //convert to hexadecimal
    	String fd = eventData.get("fd");
    	
    	ArtifactIdentity fileArtifactIdentity = descriptors.getDescriptor(pid, fd);
    	
    	if(fileArtifactIdentity == null){
    		descriptors.addUnknownDescriptor(pid, fd);
    		getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
    		fileArtifactIdentity = descriptors.getDescriptor(pid, fd);
    	}
    	
    	//if not unknown and not file
    	if((!UnknownIdentity.class.equals(fileArtifactIdentity.getClass()) && !FileIdentity.class.equals(fileArtifactIdentity.getClass()))){
    		logger.log(Level.INFO, "Syscall {0} only supported for unknown and file artifact types and not {1}. event id {2}",
    				new Object[]{syscall, fileArtifactIdentity.getClass(), eventData.get("eventid")});
    		return;
    	}
    	
    	Artifact fileArtifact = putArtifact(eventData, fileArtifactIdentity, false);
    	
    	ArtifactIdentity memoryArtifactIdentity = new MemoryIdentity(address, length, protection);
    	Artifact memoryArtifact = putArtifact(eventData, memoryArtifactIdentity, true);
		
		Process process = checkProcessVertex(eventData, true, false);
		
		WasGeneratedBy wgbEdge = new WasGeneratedBy(memoryArtifact, process);
		wgbEdge.addAnnotation("time", time);
		wgbEdge.addAnnotation("operation", getOperation(syscall)+"_"+getOperation(SYSCALL.WRITE));
		addEventIdAndSourceAnnotationToEdge(wgbEdge, eventData.get("eventid"), DEV_AUDIT);
		
		Used usedEdge = new Used(process, fileArtifact);
		usedEdge.addAnnotation("time", time);
		usedEdge.addAnnotation("operation", getOperation(syscall)+"_"+getOperation(SYSCALL.READ));
		addEventIdAndSourceAnnotationToEdge(usedEdge, eventData.get("eventid"), DEV_AUDIT);
		
		WasDerivedFrom wdfEdge = new WasDerivedFrom(memoryArtifact, fileArtifact);
		wdfEdge.addAnnotation("time", time);
		wdfEdge.addAnnotation("operation", getOperation(syscall));
		wdfEdge.addAnnotation("pid", pid);
		addEventIdAndSourceAnnotationToEdge(wdfEdge, eventData.get("eventid"), DEV_AUDIT);
		
		putEdge(wdfEdge);
		putEdge(wgbEdge);
		putEdge(usedEdge);
    }
    
    private void handleMprotect(Map<String, String> eventData, SYSCALL syscall){
    	// mprotect() receive the following message(s):
        // - SYSCALL
        // - EOE
    	
    	if(!USE_MEMORY_SYSCALLS){
    		return;
    	}
    	
//    	String pid = eventData.get("pid");
    	String time = eventData.get("time");
    	String address = new BigInteger(eventData.get("a0")).toString(16);
    	String length = new BigInteger(eventData.get("a1")).toString(16);
    	String protection = new BigInteger(eventData.get("a2")).toString(16);
    	
    	ArtifactIdentity memoryInfo = new MemoryIdentity(address, length, protection);
    	Artifact memoryArtifact = putArtifact(eventData, memoryInfo, true);
		
		Process process = checkProcessVertex(eventData, true, false);
		
		WasGeneratedBy edge = new WasGeneratedBy(memoryArtifact, process);
		edge.addAnnotation("time", time);
		edge.addAnnotation("operation", getOperation(syscall));
		addEventIdAndSourceAnnotationToEdge(edge, eventData.get("eventid"), DEV_AUDIT);
		
		putEdge(edge);
    }

    private void handleKill(Map<String, String> eventData){
    	if(!CREATE_BEEP_UNITS){
    		return;
    	}
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
    		logger.log(Level.WARNING, "Failed to process kill syscall", e);
    		return;
    	}
    	if(arg0.intValue() == -100){ //unit start
    		Process addedUnit = pushUnitIterationOnStack(pid, unitId, time);
    		if(addedUnit == null){ //failed to add because there was no process
    			//add a process first using the info here and then add the unit
    			Process process = checkProcessVertex(eventData, false, false);
    			addProcess(pid, process);
    			addedUnit = pushUnitIterationOnStack(pid, unitId, time);
    		}
    		putVertex(addedUnit);
    		//add edge between the new unit and the main unit to keep the graph connected
    		WasTriggeredBy wtb = new WasTriggeredBy(addedUnit, getContainingProcessVertex(pid));
        	wtb.addAnnotation("operation", getOperation(SYSCALL.UNIT));
        	wtb.addAnnotation("time", eventData.get("time"));
        	addEventIdAndSourceAnnotationToEdge(wtb, eventData.get("eventid"), BEEP);
        	putEdge(wtb);
    	}else if(arg0.intValue() == -101){ //unit end
    		//remove all iterations of the given unit
    		popUnitIterationsFromStack(pid, unitId, time);
    	}else if(arg0.intValue() == -200 || arg0.intValue() == -300){ //-200 highbits of read, -300 highbits of write
    		pidToMemAddress.put(pid, arg1);
    	}else if(arg0.intValue() == -201 || arg0.intValue() == -301){ //-201 lowbits of read, -301 lowbits of write 
    		BigInteger address = pidToMemAddress.get(pid);
    		if(address != null){
    			Artifact memArtifact = null;
    			AbstractEdge edge = null;
    			Process process = getProcess(pid);
    			address = address.shiftLeft(32);
    			address = address.add(arg1);
    			pidToMemAddress.remove(pid);
    			if(arg0.intValue() == -201){
    				memArtifact = putArtifact(eventData, new MemoryIdentity(address.toString(16), "", ""), false, BEEP);
    				edge = new Used(process, memArtifact);
    				edge.addAnnotation("operation", getOperation(SYSCALL.READ));
    			}else if(arg0.intValue() == -301){
    				memArtifact = putArtifact(eventData, new MemoryIdentity(address.toString(16), "", ""), true, BEEP);
    				edge = new WasGeneratedBy(memArtifact, process);
    				edge.addAnnotation("operation", getOperation(SYSCALL.WRITE));
    			}
    			if(edge != null && memArtifact != null && process != null){
	    			edge.addAnnotation("time", eventData.get("time"));
	    			putVertex(process);
	    			putVertex(memArtifact);
	    			addEventIdAndSourceAnnotationToEdge(edge, eventData.get("eventid"), BEEP);
	    			putEdge(edge);
    			}
    		}
    	}
    }
    
    private void addEventIdAndSourceAnnotationToEdge(AbstractEdge edge, String eventId, String source){
    	edge.addAnnotation(EVENT_ID, eventId);
    	edge.addAnnotation(SOURCE, source);
    }
    
    private Artifact putArtifact(Map<String, String> eventData, ArtifactIdentity artifactIdentity,
    								boolean updateVersion){
    	return putArtifact(eventData, artifactIdentity, updateVersion, null);
    }
    
    private Artifact putArtifact(Map<String, String> eventData, ArtifactIdentity artifactIdentity, 
    								boolean updateVersion, String useThisSource){
    	/*
    	 * Sort of general rules that encompass all artifacts:
    	 * if artifactidentity null then null
    	 * check if version is uninitialized which is an indicator whether to call putVertex or not (doing this before getting the actual version because it might modify it)
    	 * add subtype, source and artifact specific annotations
    	 * after adding artifact specific annotations check if the artifact is path based (file, named pipe or unix socket) and do path-based artifact specific checks
    	 * add version annotation
    	 * only add epoch annotation if not memory artifact
    	 * put vertex is version was uninitialized which means that this artifact is being seen for the first time
    	 * if artifact is a file then put a version update edge if version was updated
    	 */
    	if(artifactIdentity == null || (artifactIdentity.getClass().equals(UnixSocketIdentity.class) && !UNIX_SOCKETS)){
    		return null;
    	}
    
    	ArtifactProperties artifactProperties = getArtifactProperties(artifactIdentity);

    	Artifact artifact = new Artifact();
    	artifact.addAnnotation("subtype", artifactIdentity.getSubtype().toString().toLowerCase());
    	artifact.addAnnotations(artifactIdentity.getAnnotationsMap());
    	
    	if(useThisSource != null){
    		artifact.addAnnotation(SOURCE, useThisSource);
    	}else{
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}

    	Class<? extends ArtifactIdentity> artifactIdentityClass = artifactIdentity.getClass();
    	if(FileIdentity.class.equals(artifactIdentityClass)
    			|| NamedPipeIdentity.class.equals(artifactIdentityClass)
    			|| UnixSocketIdentity.class.equals(artifactIdentityClass)){
    		String path = artifact.getAnnotation("path");
    		if(path != null){
	    		if(updateVersion && path.startsWith("/dev/")){ //need this check for path based identities
	            	updateVersion = false;
	            }
    		}
    	}
    	
    	if(NetworkSocketIdentity.class.equals(artifactIdentityClass)){ //if network socket and if no version then don't update version
    		if(!NET_SOCKET_VERSIONING){
    			updateVersion = false;
    		}
    	}
    	
    	//version is always uninitialized if the epoch has been seen so using that to infer about epoch
    	boolean vertexNotSeenBefore = updateVersion || artifactProperties.isVersionUninitialized(); //do this before getVersion because it updates it based on updateVersion flag
    	
    	artifact.addAnnotation("version", String.valueOf(artifactProperties.getVersion(updateVersion)));
    	
    	if(!MemoryIdentity.class.equals(artifactIdentityClass)){ //epoch for everything except memory
    		artifact.addAnnotation("epoch", String.valueOf(artifactProperties.getEpoch()));
    	}   	
    	
    	if(vertexNotSeenBefore){//not seen because of either it has been updated or it is the first time it is seen
    		putVertex(artifact);
    	}
    	
    	//always at the end after the vertex has been added
    	if(updateVersion && FileIdentity.class.equals(artifactIdentity.getClass())){ //put the version update edge if version updated for a file
    		putVersionUpdateEdge(artifact, eventData.get("time"), eventData.get("eventid"), eventData.get("pid"));
    	}
    	    	
    	return artifact;
    }
    
    private ArtifactProperties getArtifactProperties(ArtifactIdentity artifactInfo){
    	ArtifactProperties artifactProperties = artifactIdentityToArtifactProperties.get(artifactInfo);
    	if(artifactProperties == null){
    		artifactProperties = new ArtifactProperties();
    	}
    	artifactIdentityToArtifactProperties.put(artifactInfo, artifactProperties);
    	return artifactProperties;
    }

    private void handleForkClone(Map<String, String> eventData, SYSCALL syscall) {
        // fork() and clone() receive the following message(s):
        // - SYSCALL
        // - EOE

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
        
        checkProcessVertex(eventData, true, false);

        Process newProcess = createProcessVertex(newPID, oldPID, eventData.get("comm"), null, null, 
        		eventData.get("uid"), eventData.get("euid"), eventData.get("suid"), eventData.get("fsuid"), 
        		eventData.get("gid"), eventData.get("egid"), eventData.get("sgid"), eventData.get("fsgid"), 
        		DEV_AUDIT, time);

        addProcess(newPID, newProcess);
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, getProcess(oldPID));
        wtb.addAnnotation("operation", getOperation(syscall));
        wtb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wtb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wtb); // Copy file descriptors from old process to new one
        
        if(syscall == SYSCALL.CLONE){ //share file descriptors when clone
        	descriptors.linkDescriptors(oldPID, newPID);
        }else if(syscall == SYSCALL.FORK || syscall == SYSCALL.VFORK){ //copy file descriptors just once here when fork
        	descriptors.copyDescriptors(oldPID, newPID);
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

        String pid = eventData.get("pid");
        String time = eventData.get("time");
        
        //added to avoid the case where the process is seen for the first time doing execve.
        checkProcessVertex(eventData, true, false); //done to make sure that the process doing execve has an existing process entry

        Process newProcess = checkProcessVertex(eventData, false, true);
        if (!newProcess.getAnnotations().containsKey("commandline")) {
            // Unable to retrieve complete process information; generate vertex
            // based on audit information
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
            newProcess.addAnnotation("cwd", eventData.get("cwd"));
            newProcess.addAnnotation("commandline", commandline);
        }
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, getProcess(pid));
        wtb.addAnnotation("operation", getOperation(SYSCALL.EXECVE));
        wtb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wtb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wtb);
        addProcess(pid, newProcess);
        
        //add used edge to the paths in the event data. get the number of paths using the 'items' key and then iterate
        String cwd = eventData.get("cwd");
        Long totalPaths = CommonFunctions.parseLong(eventData.get("items"), 0L);
        for(int pathNumber = 0; pathNumber < totalPaths; pathNumber++){
        	String path = eventData.get("path"+pathNumber);
        	path = constructAbsolutePathIfNotAbsolute(cwd, path);
        	if(path == null){
        		logger.log(Level.INFO, "Unable to create load edge for execve syscall. event id '"+eventData.get("eventid")+"'");
        		continue;
        	}        	
        	ArtifactIdentity fileIdentity = new FileIdentity(path);
        	Artifact usedArtifact = putArtifact(eventData, fileIdentity, false);
        	Used usedEdge = new Used(newProcess, usedArtifact);
        	usedEdge.addAnnotation("time", time);
        	usedEdge.addAnnotation("operation", getOperation(SYSCALL.LOAD));
        	addEventIdAndSourceAnnotationToEdge(usedEdge, eventData.get("eventid"), DEV_AUDIT);
        	putEdge(usedEdge);
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
    	eventData.put("a1", String.valueOf(defaultFlags)); //convert defaultflags argument to hexadecimal
    	
    	handleOpen(eventData, SYSCALL.CREATE); //TODO change to creat. kept as create to keep CDM current data consistent
    	
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
			ArtifactIdentity artifactIdentity = descriptors.getDescriptor(pid, dirFdString);
			if(artifactIdentity == null || !FileIdentity.class.equals(artifactIdentity.getClass())){
				logger.log(Level.INFO, "openat doesn't support directory fds of type={0}. event id {1}", new Object[]{artifactIdentity, eventData.get("eventid")});
				return;
			}else{ //is file
				String dirPath = ((FileIdentity) artifactIdentity).getPath();
				eventData.put("cwd", dirPath); //replace cwd with dirPath to make eventData compatible with open
			}
    	}
    	
    	//modify the eventData to match open syscall and then call it's function
    	
    	eventData.put("a0", eventData.get("a1")); //moved pathname address to first like in open
    	eventData.put("a1", eventData.get("a2")); //moved flags to second like in open
    	eventData.put("a2", eventData.get("a3")); //moved mode to third like in open
    	
    	handleOpen(eventData, SYSCALL.OPENAT);
    }
    
    //path based artifacts = file, namedpipe, unixsocket, null can only be returned.
    private ArtifactIdentity getValidArtifactIdentityForPath(String path){
    	FileIdentity fileIdentity = new FileIdentity(path);
    	NamedPipeIdentity namedPipeIdentity = new NamedPipeIdentity(path);
    	UnixSocketIdentity unixSocketIdentity = new UnixSocketIdentity(path);
    	//NOTE: get them directly without using the utility function. done to get null properties if not initialized yet.
    	//dont use the getArtifactProperties function here
    	ArtifactProperties fileProperties = artifactIdentityToArtifactProperties.get(fileIdentity); 
    	ArtifactProperties namedPipeProperties = artifactIdentityToArtifactProperties.get(namedPipeIdentity);
    	ArtifactProperties unixSocketProperties = artifactIdentityToArtifactProperties.get(unixSocketIdentity);
    	
    	fileProperties = fileProperties == null ? new ArtifactProperties() : fileProperties;
    	namedPipeProperties = namedPipeProperties == null ? new ArtifactProperties() : namedPipeProperties;
    	unixSocketProperties = unixSocketProperties == null ? new ArtifactProperties() : unixSocketProperties;
    	
    	long fileCreationEventId = fileProperties.getCreationEventId();
		long namedPipeCreationEventId = namedPipeProperties.getCreationEventId();
		long unixSocketCreationEventId = namedPipeProperties.getCreationEventId();

		//creation event ids won't be same unless two or more haven't been initialized yet. the uninitialized value would just be equal 
		
		if(fileCreationEventId >= namedPipeCreationEventId && fileCreationEventId >= unixSocketCreationEventId){ //always first because if all equals then we want file
			return fileIdentity;
		}else if(namedPipeCreationEventId >= fileCreationEventId && namedPipeCreationEventId >= unixSocketCreationEventId) {
			return namedPipeIdentity; 
		}else if(unixSocketCreationEventId >= fileCreationEventId && unixSocketCreationEventId >= namedPipeCreationEventId){
			return unixSocketIdentity; 
		}else{
			return fileIdentity;
		}
    }
    
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
        boolean isCreate = syscall == SYSCALL.CREATE || syscall == SYSCALL.CREAT; //TODO later on change only to CREAT
        String path = null;
        Map<Integer, String> paths = getPathsWithNametype(eventData, "CREATE"); 
        if(paths.size() == 0){
        	isCreate = false;
        	paths = getPathsWithNametype(eventData, "NORMAL");
        	if(paths.size() == 0){ //missing audit record
        		logger.log(Level.INFO, "Missing required path record in 'open'. event id '"+eventData.get("eventid")+"'");
        		return;
        	}
        }else{ //found path with CREATE nametype. will always be one
        	isCreate = true;        	
        }
        path = paths.values().iterator().next(); //get the first
        String fd = eventData.get("exit");
        String time = eventData.get("time");
        
        path = constructAbsolutePathIfNotAbsolute(cwd, path);
        
        if(path == null){
        	logger.log(Level.WARNING, "Missing CWD or PATH record in 'open'. Event with id '"+ eventData.get("eventid"));
        	return;
        }
        
        //this calls the putVertex function on the process vertex internally, that's why the process vertex isn't added here
        checkProcessVertex(eventData, true, false); 
        
        ArtifactIdentity artifactIdentity = getValidArtifactIdentityForPath(path);
        
        AbstractEdge edge = null;
        
        if(isCreate){
        	
        	if(!FileIdentity.class.equals(artifactIdentity.getClass())){
        		artifactIdentity = new FileIdentity(path); //can only create a file using open
        	}
        	
        	//set new epoch
        	getArtifactProperties(artifactIdentity).markNewEpoch(eventData.get("eventid"));
        	
        	Artifact vertex = putArtifact(eventData, artifactIdentity, true); //updating version too
            edge = new WasGeneratedBy(vertex, getProcess(pid));
            
        }else{
        	
        	if((!FileIdentity.class.equals(artifactIdentity.getClass()) && !artifactIdentity.getClass().equals(NamedPipeIdentity.class))){ //not a file and not a named pipe
        		//make it a file identity
        		artifactIdentity = new FileIdentity(path);
        	}
        
        	if ((flags & O_RDONLY) == O_RDONLY) {
        		Artifact vertex = putArtifact(eventData, artifactIdentity, false);
	            edge = new Used(getProcess(pid), vertex);
        	} else if((flags & O_WRONLY) == O_WRONLY || (flags & O_RDWR) == O_RDWR){
        		Artifact vertex = putArtifact(eventData, artifactIdentity, true);
 	            edge = new WasGeneratedBy(vertex, getProcess(pid));
	        } else{
	        	logger.log(Level.INFO, "Unknown flag for open '"+flags+"'. event id '"+eventData.get("eventid")+"'" );
	        	return;
	        }
	        
        }
        if(edge != null){
        	//everything happened successfully. add it to descriptors
        	descriptors.addDescriptor(pid, fd, artifactIdentity);
        	
        	//put the edge
	        edge.addAnnotation("operation", getOperation(syscall));
	        edge.addAnnotation("time", time);
	        addEventIdAndSourceAnnotationToEdge(edge, eventData.get("eventid"), DEV_AUDIT);
	        putEdge(edge);
        }
    }

    private void handleClose(Map<String, String> eventData) {
        // close() receives the following message(s):
        // - SYSCALL
        // - EOE
        
    	String pid = eventData.get("pid");
        String fd = String.valueOf(CommonFunctions.parseLong(eventData.get("a0"), -1L));
        descriptors.removeDescriptor(pid, fd);
       
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
        checkProcessVertex(eventData, true, false);

        String time = eventData.get("time");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd);
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }
        
        ArtifactIdentity artifactIdentity = descriptors.getDescriptor(pid, fd);
        Artifact vertex = putArtifact(eventData, artifactIdentity, false);
        Used used = new Used(getProcess(pid), vertex);
        used.addAnnotation("operation", getOperation(syscall));
        used.addAnnotation("time", time);
        used.addAnnotation("size", bytesRead);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);
        
    }

    private void handleWrite(Map<String, String> eventData, SYSCALL syscall) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String fd = eventData.get("a0");
        String time = eventData.get("time");
        String bytesWritten = eventData.get("exit");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd);
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }
        
        ArtifactIdentity artifactIdentity = descriptors.getDescriptor(pid, fd);
        
        Artifact vertex = putArtifact(eventData, artifactIdentity, true);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(syscall));
        wgb.addAnnotation("time", time);
        wgb.addAnnotation("size", bytesWritten);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);
    }

    private void handleTruncate(Map<String, String> eventData, SYSCALL syscall) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String time = eventData.get("time");
        ArtifactIdentity artifactIdentity = null;

        if (syscall == SYSCALL.TRUNCATE) {
        	Map<Integer, String> paths = getPathsWithNametype(eventData, "NORMAL");
        	if(paths.size() == 0){
        		logger.log(Level.INFO, "Missing required path in 'truncate'. event id '"+eventData.get("eventid")+"'");
        		return;
        	}
        	String path = paths.values().iterator().next();
        	path = constructAbsolutePathIfNotAbsolute(eventData.get("cwd"), path);
        	if(path == null){
        		logger.log(Level.INFO, "Missing required CWD record in 'truncate'. event id '"+eventData.get("eventid")+"'");
        		return;
        	}
            artifactIdentity = new FileIdentity(path);
        } else if (syscall == SYSCALL.FTRUNCATE) {
        	String fd = eventData.get("a0");
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	descriptors.addUnknownDescriptor(pid, fd);
            	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
            }
                        
            artifactIdentity = descriptors.getDescriptor(pid, fd);
        }

        if(FileIdentity.class.equals(artifactIdentity.getClass()) || UnknownIdentity.class.equals(artifactIdentity.getClass())){
        	Artifact vertex = putArtifact(eventData, artifactIdentity, true);
            WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
            wgb.addAnnotation("operation", getOperation(syscall));
            wgb.addAnnotation("time", time);
            addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
            putEdge(wgb);
        }else{
        	logger.log(Level.INFO, "Unexpected artifact type '"+artifactIdentity+"' for truncate. event id '"+eventData.get("eventid")+"'");
        }  
    }

    private void handleDup(Map<String, String> eventData, SYSCALL syscall) {
        // dup(), dup2(), and dup3() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String fd = eventData.get("a0");
        String newFD = eventData.get("exit"); //new fd returned in all: dup, dup2, dup3
        
        if(!fd.equals(newFD)){ //if both fds same then it succeeds in case of dup2 and it does nothing so do nothing here too
            if(descriptors.getDescriptor(pid, fd) == null){
	        	descriptors.addUnknownDescriptor(pid, fd);
	        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
	        }
	        descriptors.addDescriptor(pid, newFD, descriptors.getDescriptor(pid, fd));
	        
        }
    }

    private void handleSetuid(Map<String, String> eventData, SYSCALL syscall) {
        // setuid() receives the following message(s):
        // - SYSCALL
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);
        Process newProcess = checkProcessVertex(eventData, false, false);
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, getProcess(pid));
        wtb.addAnnotation("operation", getOperation(syscall));
        wtb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wtb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wtb);
        addProcess(pid, newProcess);
    }

    private void handleRename(Map<String, String> eventData) {
        // rename() receives the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH 0 is directory of <src>
        // - PATH 1 is directory of <dst>
        // - PATH 2 is path of <src> relative to <cwd>
        // - PATH 3 is path of <dst> relative to <cwd>
        // - EOE
        // we use cwd and paths 2 and 3
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        checkProcessVertex(eventData, true, false);

        String srcpath = constructAbsolutePathIfNotAbsolute(cwd, eventData.get("path2"));
        String dstpath = constructAbsolutePathIfNotAbsolute(cwd, eventData.get("path3"));
                
        if(srcpath == null || dstpath == null){
        	logger.log(Level.INFO, "Missing required PATH or CWD records in 'rename'. event id '"+eventData.get("eventid")+"'");
        	return;
        }
        
        ArtifactIdentity srcArtifactIdentity = getValidArtifactIdentityForPath(srcpath);
        ArtifactIdentity dstArtifactIdentity = null;
        
        if(FileIdentity.class.equals(srcArtifactIdentity.getClass())){
        	dstArtifactIdentity = new FileIdentity(dstpath);
        }else if(NamedPipeIdentity.class.equals(srcArtifactIdentity.getClass())){
        	dstArtifactIdentity = new NamedPipeIdentity(dstpath);
        }else if(UnixSocketIdentity.class.equals(srcArtifactIdentity.getClass())){
        	dstArtifactIdentity = new UnixSocketIdentity(dstpath);
        }else{
        	logger.log(Level.INFO, "Unexpected artifact type '"+srcArtifactIdentity+"' for rename. event id '"+eventData.get("eventid")+"'");
        	return;
        }

        //destination is always created. So, set the epoch whenever later on it is opened
        getArtifactProperties(dstArtifactIdentity).markNewEpoch(eventData.get("eventid"));
        
        Artifact srcVertex = putArtifact(eventData, srcArtifactIdentity, false);
        Used used = new Used(getProcess(pid), srcVertex);
        used.addAnnotation("operation", getOperation(SYSCALL.RENAME)+"_"+getOperation(SYSCALL.READ));
        used.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);

        Artifact dstVertex = putArtifact(eventData, dstArtifactIdentity, true);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(SYSCALL.RENAME)+"_"+getOperation(SYSCALL.WRITE));
        wgb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("operation", getOperation(SYSCALL.RENAME));
        wdf.addAnnotation("time", time);
        wdf.addAnnotation("pid", pid);
        addEventIdAndSourceAnnotationToEdge(wdf, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wdf);
    }
    
    private void handleMknodat(Map<String, String> eventData){
    	//mknodat() receives the following message(s):
    	// - SYSCALL
        // - CWD
        // - PATH with nametype=PARENT
        // - PATH of the created file with nametype=CREATE
        // - EOE
    	
    	//first argument is the fd of the directory to create file in. if the directory fd is AT_FDCWD then use cwd
    	
    	String pid = eventData.get("pid");
    	String fd = eventData.get("a0");
    	Long fdLong = CommonFunctions.parseLong(fd, null);
    	
    	ArtifactIdentity artifactIdentity = null;
    	
    	if(fdLong != AT_FDCWD){
    		artifactIdentity = descriptors.getDescriptor(pid, fd);
    		if(artifactIdentity == null){
    			logger.log(Level.INFO, "Couldn't find directory fd in local map for mknodat. event id = '"+eventData.get("eventid")+"'");
    			return;
    		}else if(artifactIdentity.getClass().equals(FileIdentity.class)){
    			String directoryPath = ((FileIdentity)artifactIdentity).getPath();
	    		//update cwd to directoryPath and call handleMknod. the file created path is always relative in this syscall
	    		eventData.put("cwd", directoryPath);
    		}else{
    			logger.log(Level.INFO, "Couldn't find directory fd in local map for 'mknodat'. event id = '"+eventData.get("eventid")+"' " + " artifact type = " + artifactIdentity.getClass());
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
    	
    	String cwd = eventData.get("cwd");
        
        String path = null;
        
        Map<Integer, String> paths = getPathsWithNametype(eventData, "CREATE");
        
        if(paths.size() == 0 || cwd == null){
        	logger.log(Level.INFO, "No PATH records with CREATE nametype for syscall {0}. event id {1}", new Object[]{syscall, eventData.get("eventid")});
        	return;
        }
        
        path = paths.values().iterator().next();        
        path = constructAbsolutePathIfNotAbsolute(cwd, path);
        
        if(path == null){
        	logger.log(Level.INFO, "No CWD record for syscall {0}. event id {1}", new Object[]{syscall, eventData.get("eventid")});
        	return;
        }
        
        ArtifactIdentity artifactIdentity = null;
    	
    	if((mode & S_IFIFO) == S_IFIFO){ //is pipe
            artifactIdentity = new NamedPipeIdentity(path);
    	}else if((mode & S_IFREG) == S_IFREG){ //is regular file
    		artifactIdentity = new FileIdentity(path);
    	}else if((mode & S_IFSOCK) == S_IFSOCK){ //is unix socket
    		artifactIdentity = new UnixSocketIdentity(path);
    	}else{
    		logger.log(Level.INFO, "Unsupported mode for mknod '"+mode+"'. event id '"+eventData.get("eventid")+"'");
    		return;
    	}	
    	
    	if(artifactIdentity != null){
	    	getArtifactProperties(artifactIdentity).markNewEpoch(eventData.get("eventid"));
	    	
	    	String pid = eventData.get("pid");
	    	String time = eventData.get("time");
	    	
	    	Artifact vertex = putArtifact(eventData, artifactIdentity, true); //updating version too
            AbstractEdge edge = new WasGeneratedBy(vertex, getProcess(pid));
	        edge.addAnnotation("operation", getOperation(SYSCALL.CREATE)); //TODO update syscall to correct one
	        edge.addAnnotation("time", time);
	        addEventIdAndSourceAnnotationToEdge(edge, eventData.get("eventid"), DEV_AUDIT);
	        putEdge(edge);
    	}
    }

    private void handleLink(Map<String, String> eventData, SYSCALL syscall) {
        // link() and symlink() receive the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH 0 is path of <src> relative to <cwd>
        // - PATH 1 is directory of <dst>
        // - PATH 2 is path of <dst> relative to <cwd>
        // - EOE
        // we use cwd and paths 0 and 2
    	
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        checkProcessVertex(eventData, true, false);
        
        String syscallName = getOperation(syscall);

        String srcpath = constructAbsolutePathIfNotAbsolute(cwd, eventData.get("path0"));
        String dstpath = constructAbsolutePathIfNotAbsolute(cwd, eventData.get("path2"));
        
        if(srcpath == null || dstpath == null){
        	logger.log(Level.INFO, "Missing CWD or PATH records in 'link' syscall. event id '"+eventData.get("eventid")+"'");
        	return;
        }
        
        ArtifactIdentity srcArtifactIdentity = getValidArtifactIdentityForPath(srcpath);
        
        //not determining dstArtifactIdentity based on srcArtifactIdentity, like in rename, because it is just an FS entry
        ArtifactIdentity dstArtifactIdentity = new FileIdentity(dstpath);
        //destination is new so mark epoch
        getArtifactProperties(dstArtifactIdentity).markNewEpoch(eventData.get("eventid"));

        Artifact srcVertex = putArtifact(eventData, srcArtifactIdentity, false);
        Used used = new Used(getProcess(pid), srcVertex);
        used.addAnnotation("operation", syscallName + "_" + getOperation(SYSCALL.READ));
        used.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);

        Artifact dstVertex = putArtifact(eventData, dstArtifactIdentity, true);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, getProcess(pid));
        wgb.addAnnotation("operation", syscallName + "_" + getOperation(SYSCALL.WRITE));
        wgb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("operation", syscallName);
        wdf.addAnnotation("time", time);
        wdf.addAnnotation("pid", pid);
        addEventIdAndSourceAnnotationToEdge(wdf, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wdf);
    }

    private void handleChmod(Map<String, String> eventData, SYSCALL syscall) {
        // chmod() receives the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);
        // mode is in hex format in <a1>
        String mode = new BigInteger(eventData.get("a1")).toString(8);
        // if syscall is chmod, then path is <path0> relative to <cwd>
        // if syscall is fchmod, look up file descriptor which is <a0>
        ArtifactIdentity artifactIdentity = null;
        if (syscall == SYSCALL.CHMOD) {
        	Map<Integer, String> paths = getPathsWithNametype(eventData, "NORMAL");
        	if(paths.size() == 0){
        		logger.log(Level.INFO, "Missing required path in 'chmod'. event id '"+eventData.get("eventid")+"'");
        		return;
        	}
        	String path = paths.values().iterator().next();
        	path = constructAbsolutePathIfNotAbsolute(eventData.get("cwd"), path);
        	if(path == null){
        		logger.log(Level.INFO, "Missing required CWD record in 'chmod'. event id '"+eventData.get("eventid")+"'");
        		return;
        	}
            artifactIdentity = getValidArtifactIdentityForPath(path);
        } else if (syscall == SYSCALL.FCHMOD) {
        	
        	String fd = eventData.get("a0");
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	descriptors.addUnknownDescriptor(pid, fd);
            	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
            }
                        
            artifactIdentity = descriptors.getDescriptor(pid, fd);
        }
        
        Artifact vertex = putArtifact(eventData, artifactIdentity, true);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(syscall));
        wgb.addAnnotation("mode", mode);
        wgb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);
    }

    private void handlePipe(Map<String, String> eventData, SYSCALL syscall) {
    	// pipe() receives the following message(s):
        // - SYSCALL
        // - FD_PAIR
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String fd0 = eventData.get("fd0");
        String fd1 = eventData.get("fd1");
        ArtifactIdentity pipeInfo = new UnnamedPipeIdentity(fd0, fd1);
        descriptors.addDescriptor(pid, fd0, pipeInfo);
        descriptors.addDescriptor(pid, fd1, pipeInfo);
        
        getArtifactProperties(pipeInfo).markNewEpoch(eventData.get("eventid"));
    }
    
    
    
    private void handleNetfilterPacketEvent(Map<String, String> eventData){
//      Refer to the following link for protocol numbers
//    	http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
//    	String protocol = eventData.get("proto");
    	
//    	if(protocol.equals("6") || protocol.equals("17")){ // 6 is tcp and 17 is udp
//    		String length = eventData.get("len");
//        	
////        	hook = 1 is input, hook = 3 is forward
//        	String hook = eventData.get("hook");
//        	
//        	String sourceAddress = eventData.get("saddr");
//        	String destinationAddress = eventData.get("daddr");
//        	
//        	String sourcePort = eventData.get("sport");
//        	String destinationPort = eventData.get("dport");
//        	
//        	String time = eventData.get("time");
//        	String eventId = eventData.get("eventid");
//        	
//        	SocketInfo source = new SocketInfo(sourceAddress, sourcePort);
//        	SocketInfo destination = new SocketInfo(destinationAddress, destinationPort);
//    	}
    	
    }
    
    private ArtifactIdentity parseSaddr(String saddr, SYSCALL syscall){
    	if(saddr != null && saddr.length() >= 2){
	    	if(saddr.charAt(1) == '1'){ //unix socket
	        	String path = "";
	        	int a = 2;
	        	while(a < saddr.length()){
	        		//iterating until start of path found or string ends
	        		if(saddr.charAt(a) != '0'){
	        			break;
	        		}
	        		a++;
	        	}
	        	for(; a<saddr.length()-2; a+=2){
	        		char c = (char)(Integer.parseInt(saddr.substring(a, a+2), 16));
	        		if(c == '0'){ //null char
	        			break;
	        		}
	        		path += c;
	        	}
	        	if(!path.isEmpty()){
	        		return new UnixSocketIdentity(path);
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
		        	address = String.format("::%s:%d.%d.%d.%d", saddr.substring(36, 40).toLowerCase(), oct1, oct2, oct3, oct4); //TODO format for ipv6?
		        }
		    	
		    	if(address != null && port != null){
		    		if(syscall == SYSCALL.BIND){ //put address as destination
		    			return new NetworkSocketIdentity("", "", address, port, "");
		    		}else if(syscall == SYSCALL.CONNECT){ //put address as destination
		    			return new NetworkSocketIdentity("", "", address, port, "");
		    		}else if(syscall == SYSCALL.ACCEPT || syscall == SYSCALL.ACCEPT4){ //put address as source
		    			return new NetworkSocketIdentity(address, port, "", "", "");
		    		}else{
		    			logger.log(Level.INFO, "Unsupported syscall '"+syscall+"' to parse saddr '"+saddr+"'");
		    		}
		    	}
		    	
	        }
    	}
    	
    	return null;
    }

	 /* incorrect implementation and not in 64bit archs
	  private void handleSocketcall(Map<String, String> eventData) {
	  	String saddr = eventData.get("saddr");
	  	int callType = Integer.parseInt(eventData.get("socketcall_a0"));
	  	SYSCALL syscall = null;
	  	// socketcall number is derived from /usr/include/linux/net.h
	  	if(callType == 2){
	  		syscall = SYSCALL.BIND;
	  	}else if(callType == 3){
	  		syscall = SYSCALL.CONNECT;
	  	}else if(callType == 5){
	  		syscall = SYSCALL.ACCEPT;
	  	}
	      ArtifactIdentity artifactInfo = parseSaddr(saddr, syscall);
	      if (artifactInfo != null) {
	      	if(UnixSocketIdentity.class.equals(artifactInfo.getClass()) && !UNIX_SOCKETS){
	      		return;
	      	}        	
	          
	          if(callType == 2){ //bind
	          	handleBind(eventData);
	          }else if (callType == 3) { //connect
	          	handleConnect(eventData);
	          } else if (callType == 5) { //accept
	          	handleAccept(eventData, SYSCALL.ACCEPT);
	          }
	      }
	  } */
    
    private void handleBind(Map<String, String> eventData, SYSCALL syscall) {
    	// bind() receives the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
        String saddr = eventData.get("saddr");
        ArtifactIdentity artifactIdentity = parseSaddr(saddr, syscall);
        if (artifactIdentity != null) {
        	if(UnixSocketIdentity.class.equals(artifactIdentity.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
            String pid = eventData.get("pid");
            //NOTE: not using the file descriptor. using the socketFD here!
            //Doing this to be able to link the accept syscalls to the correct artifactIdentity.
            //In case of unix socket accept, the saddr is almost always reliably invalid
            String socketFd = eventData.get("a0");
            descriptors.addDescriptor(pid, socketFd, artifactIdentity);
        }else{
        	logger.log(Level.INFO, "Invalid saddr '"+saddr+"' in 'bind'. event id '"+eventData.get("eventid")+"'");
        }
    }
    
    private void handleConnect(Map<String, String> eventData) {
    	//connect() receives the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentity parsedArtifactIdentity = parseSaddr(saddr, SYSCALL.CONNECT);
        if (parsedArtifactIdentity != null) {
        	if(UnixSocketIdentity.class.equals(parsedArtifactIdentity.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        	// update file descriptor table
            String fd = eventData.get("a0");
            descriptors.addDescriptor(pid, fd, parsedArtifactIdentity);
            getArtifactProperties(parsedArtifactIdentity).markNewEpoch(eventData.get("eventid"));
        	
            Artifact artifact = putArtifact(eventData, parsedArtifactIdentity, false);
            WasGeneratedBy wgb = new WasGeneratedBy(artifact, getProcess(pid));
            wgb.addAnnotation("time", time);
            wgb.addAnnotation("operation", getOperation(SYSCALL.CONNECT));
            addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
            putEdge(wgb);
        }else{
        	logger.log(Level.INFO, "Unable to find artifact type from saddr field in 'connect'. event id '"+eventData.get("eventid")+"'");
        }
    }

    private void handleAccept(Map<String, String> eventData, SYSCALL syscall) {
    	//accept() & accept4() receive the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String socketFd = eventData.get("a0"); //the fd on which the connection was accepted, not the fd of the connection
        String fd = eventData.get("exit"); //fd of the connection
        String saddr = eventData.get("saddr");
                
        ArtifactIdentity boundArtifactIdentity = descriptors.getDescriptor(pid, socketFd); //previously bound
        ArtifactIdentity parsedArtifactIdentity = parseSaddr(saddr, syscall);
        
        //discarding cases that cannot be handled
        if(parsedArtifactIdentity == null){ //if null then cannot do anything unless bound artifact was unix socket
        	if(boundArtifactIdentity == null || !UnixSocketIdentity.class.equals(boundArtifactIdentity.getClass())){
        		logger.log(Level.INFO, "Invalid or no 'saddr' in 'accept' syscall. event id '"+eventData.get("eventid")+"'");
        		return;
        	}else{ //is a unix socket identity
        		if(UnixSocketIdentity.class.equals(boundArtifactIdentity.getClass()) && !UNIX_SOCKETS){
            		return;
            	}
        		descriptors.addDescriptor(pid, fd, boundArtifactIdentity);
        	}
        }else if(UnixSocketIdentity.class.equals(parsedArtifactIdentity.getClass())){
        	if(UnixSocketIdentity.class.equals(parsedArtifactIdentity.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        	if(boundArtifactIdentity == null || !UnixSocketIdentity.class.equals(boundArtifactIdentity.getClass())){ //we need the bound address always
        		logger.log(Level.INFO, "Invalid or no 'saddr' in 'accept' syscall. event id '"+eventData.get("eventid")+"'");
        		return;
        	}else{ //is a unix socket identity
        		descriptors.addDescriptor(pid, fd, boundArtifactIdentity);
        	}
        }else if(NetworkSocketIdentity.class.equals(parsedArtifactIdentity.getClass())){
        	//anything goes. dont really need the bound. but if it is there then its good
        	if(boundArtifactIdentity == null || !NetworkSocketIdentity.class.equals(boundArtifactIdentity.getClass())){
        		descriptors.addDescriptor(pid, socketFd, new NetworkSocketIdentity("", "", "", "", "")); //add a dummy one if null or a mismatch
        	}
        	NetworkSocketIdentity boundSocketIdentity = (NetworkSocketIdentity)descriptors.getDescriptor(pid, socketFd);
        	NetworkSocketIdentity parsedSocketIdentity = (NetworkSocketIdentity)parsedArtifactIdentity;
        	ArtifactIdentity socketIdentity = new NetworkSocketIdentity(parsedSocketIdentity.getSourceHost(), parsedSocketIdentity.getSourcePort(),
        			boundSocketIdentity.getDestinationHost(), boundSocketIdentity.getDestinationPort(), parsedSocketIdentity.getProtocol());
        	descriptors.addDescriptor(pid, fd, socketIdentity);
        }else{
        	logger.log(Level.INFO, "Unexpected artifact type '"+parsedArtifactIdentity+"' in 'accept' syscall. event id '"+eventData.get("eventid")+"'");
        	return;
        }        
        
        //if reached this point then can process the accept event 
        
        ArtifactIdentity artifactIdentity = descriptors.getDescriptor(pid, fd);
        if (artifactIdentity != null) { //well shouldn't be null since all cases handled above but for future code changes  
        	getArtifactProperties(artifactIdentity).markNewEpoch(eventData.get("eventid"));
            Artifact socket = putArtifact(eventData, artifactIdentity, false);
            Used used = new Used(getProcess(pid), socket);
            used.addAnnotation("time", time);
            used.addAnnotation("operation", getOperation(syscall));
            addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
            putEdge(used);
        }else{
        	logger.log(Level.INFO, "No artifact found to 'accept' on. event id '"+eventData.get("eventid")+"'");
        }
    }

    private void handleSend(Map<String, String> eventData, SYSCALL syscall) {
    	// sendto()/sendmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String fd = eventData.get("a0");
        String time = eventData.get("time");
        String bytesSent = eventData.get("exit");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd); 
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }else{
        	if(UnixSocketIdentity.class.equals(descriptors.getDescriptor(pid, fd).getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        }
       
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);
        
        Artifact vertex = putArtifact(eventData, artifactInfo, true);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(syscall));
        wgb.addAnnotation("time", time);
        wgb.addAnnotation("size", bytesSent);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);
    }
    
    private void handleRecv(Map<String, String> eventData, SYSCALL syscall) {
    	// recvfrom()/recvmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String fd = eventData.get("a0");
        String time = eventData.get("time");
        String bytesReceived = eventData.get("exit");

        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd); 
        	getArtifactProperties(descriptors.getDescriptor(pid, fd)).markNewEpoch(eventData.get("eventid"));
        }else{
        	if(UnixSocketIdentity.class.equals(descriptors.getDescriptor(pid, fd).getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        }
        
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);     
        
        Artifact vertex = putArtifact(eventData, artifactInfo, false);
    	Used used = new Used(getProcess(pid), vertex);
        used.addAnnotation("operation", getOperation(syscall));
        used.addAnnotation("time", time);
        used.addAnnotation("size", bytesReceived);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);
    }
        
    private String constructAbsolutePathIfNotAbsolute(String parentDirectoryPath, String path){
    	if(path == null){
    		return null;
    	}
    	if(!path.startsWith("/")){ //is not absolute
    		//what kind of not absolute
    		if(path.startsWith("./")){
    			path = path.substring(2);
    		}
    		//path doesn't start with '/' if here
    		//do the constructing
    		if(parentDirectoryPath == null){
    			return null;
    		}
    		if (parentDirectoryPath.endsWith("/")) {
                return parentDirectoryPath + path;
            } else {
                return parentDirectoryPath + "/" + path;
            }
    	}
    	return path;
    }

    private Process checkProcessVertex(Map<String, String> eventData, boolean link, boolean refresh) {
        String pid = eventData.get("pid");
        if (getProcess(pid) != null && !refresh) {
            return getProcess(pid);
        }
        Process resultProcess = PROCFS ? createProcessFromProcFS(pid) : null;
        if (resultProcess == null) {
            resultProcess = createProcessVertex(pid, eventData.get("ppid"), eventData.get("comm"), null, null, 
            		eventData.get("uid"), eventData.get("euid"), eventData.get("suid"), eventData.get("fsuid"),
            		eventData.get("gid"), eventData.get("egid"), eventData.get("sgid"), eventData.get("fsgid"), 
            		DEV_AUDIT, null);
        }
        if (link == true) {
            Map<String, ArtifactIdentity> fds = getFileDescriptors(pid);
            if (fds != null) {
            	descriptors.addDescriptors(pid, fds);
            }
            putVertex(resultProcess);
            addProcess(pid, resultProcess);
            String ppid = resultProcess.getAnnotation("ppid");
            if (getProcess(ppid) != null) {
                WasTriggeredBy wtb = new WasTriggeredBy(resultProcess, getProcess(ppid));
                addEventIdAndSourceAnnotationToEdge(wtb, "0", DEV_AUDIT);
                wtb.addAnnotation("operation", getOperation(SYSCALL.UNKNOWN));
                putEdge(wtb);
            }
        }
        return resultProcess;
    }

    private String getOperation(SYSCALL syscall){
    	SYSCALL returnSyscall = syscall;
    	if(SIMPLIFY){
    		switch (syscall) {
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
	    			returnSyscall = SYSCALL.RENAME;
	    			break;
    			case CREATE:
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
    			case CREAT:
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
    
//  NOTE: this function to be used always to create a process vertex
    private Process createProcessVertex(String pid, String ppid, String name, String commandline, String cwd, 
    		String uid, String euid, String suid, String fsuid, 
    		String gid, String egid, String sgid, String fsgid,
    		String source, String startTime){
    	
    	Process process = new Process();
    	process.addAnnotation("pid", pid);
    	process.addAnnotation("ppid", ppid);
    	process.addAnnotation("name", name);
    	process.addAnnotation("uid", uid);
    	process.addAnnotation("euid", euid);
    	process.addAnnotation("gid", gid);
    	process.addAnnotation("egid", egid);
    	process.addAnnotation(SOURCE, source);
    	
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
    	
    	return process;
    }

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
//                String name = nameline.split("\\s+")[1];
//                String ppid = ppidline.split("\\s+")[1];
                cmdline = (cmdline == null) ? "" : cmdline.replace("\0", " ").replace("\"", "'").trim();

                // see for order of uid, euid, suid, fsiud: http://man7.org/linux/man-pages/man5/proc.5.html
                String gidTokens[] = gidline.split("\\s+");
                String uidTokens[] = uidline.split("\\s+");
                
                Process newProcess = createProcessVertex(pid, ppidline, nameline, null, null, 
                		uidTokens[1], uidTokens[2], uidTokens[3], uidTokens[4], 
                		gidTokens[1], gidTokens[2], gidTokens[3], gidTokens[4], 
                		PROC_FS, Long.toString(startTime));
                
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
    
    private void putVersionUpdateEdge(Artifact newArtifact, String time, String eventId, String pid){
    	if(newArtifact == null || time == null || eventId == null || pid == null){
    		logger.log(Level.INFO, "Invalid arguments. newArtifact="+newArtifact+", time="+time+", eventId="+eventId+", pid="+pid);
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
    	versionUpdate.addAnnotation("operation", getOperation(SYSCALL.UPDATE));
    	versionUpdate.addAnnotation("time", time);
    	addEventIdAndSourceAnnotationToEdge(versionUpdate, eventId, DEV_AUDIT);
    	putEdge(versionUpdate);
    }
        
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
    
    private Process getContainingProcessVertex(String pid){
    	if(processUnitStack.get(pid) != null && processUnitStack.get(pid).size() > 0){
    		return processUnitStack.get(pid).getFirst();
    	}
    	return null;
    }
    
    private void addProcess(String pid, Process process){ 
    	iterationNumber.remove(pid); //start iteration count from start
    	processUnitStack.put(pid, new LinkedList<Process>()); //always reset the stack whenever the main process is being added
    	if(CREATE_BEEP_UNITS){
    		process.addAnnotation("unit", "0"); //containing process always has the unit id '0'. and no iteration count
    	}
    	processUnitStack.get(pid).addFirst(process);
    }
    
    private Process getProcess(String pid){
    	if(processUnitStack.get(pid) != null && !processUnitStack.get(pid).isEmpty()){
    		Process process = processUnitStack.get(pid).peekLast();
    		return process;
    	}
    	return null;
    }
    
    private Process pushUnitIterationOnStack(String pid, String unitId, String startTime){
    	if("0".equals(unitId)){
    		return null; //unit 0 is containing process and cannot have iterations
    	}
    	Process containingProcess = getContainingProcessVertex(pid);
    	if(containingProcess == null){
    		return null;
    	}
    	//remove the previous iteration (if any) for the pid and unitId combination
    	Long nextIterationNumber = getNextIterationNumber(pid, unitId);
    	if(nextIterationNumber > 0){ //if greater than zero then remove otherwise this is the first one
    		removePreviousIteration(pid, unitId, nextIterationNumber);
    	}
    	Process newUnit = createBEEPCopyOfProcess(containingProcess, startTime); //first element is always the main process vertex
    	newUnit.addAnnotation("unit", unitId);
    	newUnit.addAnnotation("iteration", String.valueOf(nextIterationNumber));
    	
    	if(lastTimestamp == NOT_SET || !startTime.equals(lastTimestamp)){
		lastTimestamp = startTime;
		unitIterationRepetitionCounts.clear();
	}
    	
    	UnitVertexIdentifier unitVertexIdentifier = new UnitVertexIdentifier(pid, unitId, String.valueOf(nextIterationNumber));
	if(unitIterationRepetitionCounts.get(unitVertexIdentifier) == null){
		unitIterationRepetitionCounts.put(unitVertexIdentifier, -1);
	}
	unitIterationRepetitionCounts.put(unitVertexIdentifier, unitIterationRepetitionCounts.get(unitVertexIdentifier)+1);
	newUnit.addAnnotation("count", String.valueOf(unitIterationRepetitionCounts.get(unitVertexIdentifier)));
    	
    	processUnitStack.get(pid).addLast(newUnit);
    	return newUnit;
    }
    
    /*
     * This function removes all the units with given unitid and their iteration units from the process stack, leaving only the main process (with unit 0) behind.  
     */
    private void popUnitIterationsFromStack(String pid, String unitId, String endTime){
    	if("0".equals(unitId)){
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
    
    //only removes the last one
    private void removePreviousIteration(String pid, String unitId, Long currentIteration){
    	if("0".equals(unitId)){
    		return; //unit 0 is containing process and should be removed using the addProcess method
    	}
    	if(processUnitStack.get(pid) != null){
    		int size = processUnitStack.get(pid).size() - 1;
    		for(int a = size; a>=0; a--){ //going in reverse because we would be removing elements
    			Process unit = processUnitStack.get(pid).get(a);
    			if(unitId.equals(unit.getAnnotation("unit"))){
    				Long iteration = CommonFunctions.parseLong(unit.getAnnotation("iteration"), currentIteration+1); 
    				//so that we by mistake don't remove an iteration which didn't have a proper iteration value
    				if(iteration < currentIteration){
    					processUnitStack.get(pid).remove(a);
    					//just removing one
    					break;
    				}
    			}
    		}
    	}
    }
    
    private Process createBEEPCopyOfProcess(Process process, String startTime){
    	//passing commandline and cwd as null because we don't want those two fields copied onto units
    	return createProcessVertex(process.getAnnotation("pid"), process.getAnnotation("ppid"), process.getAnnotation("name"), null, null, 
    			process.getAnnotation("uid"), process.getAnnotation("euid"), process.getAnnotation("suid"), process.getAnnotation("fsuid"), 
    			process.getAnnotation("gid"), process.getAnnotation("egid"), process.getAnnotation("sgid"), process.getAnnotation("fsgid"), 
    			BEEP, startTime);
    }
    
    
}

//Added for the case where there can be two of these exactly the same in a single timestamp
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
