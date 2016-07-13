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
//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/fcntl.h#L19
    private final int O_RDONLY = 00000000, O_WRONLY = 00000001, O_RDWR = 00000002;
  
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
    private String auditRules;
    
    private final static long MAX_BYTES_PER_NETWORK_ARTIFACT = 100;
    
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
    private boolean log_successful_events_only = true; 
    
    private boolean CREATE_BEEP_UNITS = false;
        
    private Map<String, BigInteger> pidToMemAddress = new HashMap<String, BigInteger>(); 
    
    private final static String EVENT_ID = "event id";
    
    private boolean SIMPLIFY = true;
    private boolean PROCFS = false;
    
    private boolean SORTLOG = true;
    
    private boolean NET_VERSIONING = false;
    
    private boolean UNIX_SOCKETS = false;
    
    private boolean WAIT_FOR_LOG_END = false;
    
//  To toggle monitoring of mmap, mmap2 and mprotect syscalls
    private boolean USE_MEMORY_SYSCALLS = true;
    
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
        if("true".equals(args.get("netVersioning"))){
        	NET_VERSIONING = true;
        }
        if("true".equals(args.get("waitForLog"))){
        	WAIT_FOR_LOG_END = true;
        }
        if("false".equals(args.get("memorySyscalls"))){
        	USE_MEMORY_SYSCALLS = false;
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
	
		    String noSuccessAuditRules = "";
	
	            auditRules = "-a exit,always ";
	            noSuccessAuditRules = "-a exit,always ";
	            if (ARCH_32BIT){
	            	auditRules += "-F arch=b32 ";
	            	noSuccessAuditRules += "-F arch=b32 ";
	            }else{
	            	auditRules += "-F arch=b64 ";
	            	noSuccessAuditRules += "-F arch=b64 ";
	            }
	            noSuccessAuditRules += "-S kill ";
	            if (USE_READ_WRITE) {
	                auditRules += "-S read -S readv -S write -S writev ";
	            }
	            if (USE_SOCK_SEND_RCV) {
	                auditRules += "-S sendto -S recvfrom -S sendmsg -S recvmsg ";
	            }
	            if (USE_MEMORY_SYSCALLS) {
	            	auditRules += "-S mmap -S mprotect ";
	            	if(ARCH_32BIT){
	            		auditRules += "-S mmap2 ";
	            	}
	            }
	            auditRules += "-S link -S symlink -S clone -S fork -S vfork -S execve -S open -S close "
	                    + "-S mknod -S rename -S dup -S dup2 -S setreuid -S setresuid -S setuid "
	                    + "-S connect -S accept -S chmod -S fchmod -S pipe -S truncate -S ftruncate -S pipe2 "
	                    + (log_successful_events_only ? "-F success=1 " : "");
	            
	            
	            //Find the pids of the processes to ignore (below) and all the pids for the JVM and it's threads.
	            /*
	             * All these pids would have been added to the main auditctl rule as "-F pid!=xxxx -F ppid!=xxxx" but 
	             * only 64 fields are allowed per each auditctl rule.
	             * 
	             * Add whatever number of fields that can be added to the main auditctl rule and add the rest individual rules as: 
	             *  "auditctl -a exit,never -F pid=xxxx" and "auditctl -a exit,never -F ppid=xxxx"
	             */
	            
	            String ignoreProcesses = "spadeSocketBridge auditd kauditd audispd";
	            List<String> pidsToIgnore = listOfPidsToIgnore(ignoreProcesses);
	            
	            int maxFieldsAllowed = 64; //max allowed by auditctl command
	            //split the pre-formed rule on -F to find out the number of fields already present
	            int existingFieldsCount = auditRules.split(" -F ").length - 1; 
	            
	            //find out the pids & ppids that can be added to the main rule from the list of pids. divided by two to account for pid and ppid fields for the same pid
	            int pidsForMainRuleCount = (maxFieldsAllowed - existingFieldsCount)/2; 
	            
	            //handling the case if the main rule can accommodate all pids in the list of pids to ignore 
	            int loopPidsForMainRuleTill = Math.min(pidsForMainRuleCount, pidsToIgnore.size());
	            
	            String pidsForMainRule = "";
	            //build the pid and ppid  to ignore portion for the main rule
	            for(int a = 0; a<loopPidsForMainRuleTill; a++){
	            	pidsForMainRule += " -F pid!=" +pidsToIgnore.get(a) + " -F ppid!=" + pidsToIgnore.get(a); 
	            }
	            
	            List<String> auditctlOutput = null;
	            //add the remaining pids as individual rules
	            for(int a = pidsForMainRuleCount; a<pidsToIgnore.size(); a++){
	            	String pidIgnoreAuditRule = "auditctl -a exit,never -F pid="+pidsToIgnore.get(a);
	            	String ppidIgnoreAuditRule = "auditctl -a exit,never -F ppid="+pidsToIgnore.get(a);
                    auditctlOutput = Execute.getOutput(pidIgnoreAuditRule);
	            	logger.log(Level.INFO, "configured audit rules: {0} with ouput: {1}", new Object[]{pidIgnoreAuditRule, auditctlOutput});
                    auditctlOutput = Execute.getOutput(ppidIgnoreAuditRule);
	            	logger.log(Level.INFO, "configured audit rules: {0} with ouput: {1}", new Object[]{ppidIgnoreAuditRule, auditctlOutput});
	            }
	            
	            auditctlOutput = Execute.getOutput("auditctl " + noSuccessAuditRules + pidsForMainRule);
            	    logger.log(Level.INFO, "configured audit rules: {0} with ouput: {1}", new Object[]{noSuccessAuditRules + pidsForMainRule, auditctlOutput});
	            
	            //add the main rule. ALWAYS ADD THIS AFTER THE ABOVE INDIVIDUAL RULES HAVE BEEN ADDED TO AVOID INCLUSION OF AUDIT INFO OF ABOVE PIDS
	            auditctlOutput = Execute.getOutput("auditctl " + auditRules + pidsForMainRule);
            	    logger.log(Level.INFO, "configured audit rules: {0} with ouput: {1}", new Object[]{auditRules + pidsForMainRule, auditctlOutput});
            	
	        } catch (Exception e) {
	            logger.log(Level.SEVERE, "Error configuring audit rules", e);
	            return false;
	        }

        }
        
        return true;
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
    
    private static List<String> listOfPidsToIgnore(String ignoreProcesses) {
    	
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
            
            // Get the PIDs of SPADE's JVM from /proc/self
            File[] procTaskDirectories = new File("/proc/self/task").listFiles();
            for (File procTaskDirectory : procTaskDirectories) {
                String pid = procTaskDirectory.getCanonicalFile().getName();
                pids.add(pid);
            }
            
            return pids;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error building list of processes to ignore. Partial list: " + pids, e);
            return new ArrayList<String>();
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

    	if("NETFILTER_PKT".equals(eventBuffer.get(eventId).get("type"))){ //for events with no syscalls
    		try{
    			handleNetfilterPacketEvent(eventBuffer.get(eventId));
    		}catch(Exception e){
    			logger.log(Level.WARNING, "Error processing finish syscall event with event id '"+eventId+"'", e);
    		}
    	}else{ //for events with syscalls
    		handleSyscallEvent(eventId);
    	}
    }
    
    private void handleSyscallEvent(String eventId) {
    	try {

    		Map<String, String> eventData = eventBuffer.get(eventId);
    		Integer syscallNum = CommonFunctions.parseInt(eventData.get("syscall"), -1);

    		int arch = -1;
    		if(ARCH_32BIT){
    			arch = 32;
    		}else{
    			arch = 64;
    		}

    		SYSCALL syscall = SYSCALL.getSyscall(syscallNum, arch);
    		
    		if(syscall == null){
    			logger.log(Level.WARNING, "A non-syscall audit event OR missing syscall record with for event with id '" + eventId + "'");
    			return;
    		}

    		if(log_successful_events_only && "no".equals(eventData.get("success")) && syscall != SYSCALL.KILL){ //in case the audit log is being read from a user provided file and syscall must not be kill to log units properly
    			eventBuffer.remove(eventId);
    			return;
    		}

    		switch (syscall) {
	    		case MMAP:
	    		case MMAP2:
	    			handleMmap(eventData, syscall);
	    			break;
	    		case MPROTECT:
	    			handleMprotect(eventData, syscall);
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
	    			handleOpen(eventData);
	    			break;
	    		case CLOSE:
	    			handleClose(eventData);
	    			break;
	    		case SYMLINK:
	    		case LINK:
	    			handleLink(eventData, syscall);
	    			break;
	    		case RENAME: 
	    			handleRename(eventData);
	    			break;
	    		case PIPE:
	    		case PIPE2:
	    			handlePipe(eventData);
	    			break;
	    		case DUP:
	    		case DUP2:
	    			handleDup(eventData);
	    			break;
	    		case SETREUID:
	    		case SETRESUID:
	    		case SETUID:
	    			handleSetuid(eventData, syscall);
	    			break;                
	
	    		case TRUNCATE:
	    		case FTRUNCATE:
	    			handleTruncate(eventData, syscall);
	    			break;
	    		case CHMOD:
	    		case FCHMOD:
	    			handleChmod(eventData, syscall);
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
	    		case CONNECT:
	    			handleConnect(eventData);
	    			break;
	    		case ACCEPT4:
	    		case ACCEPT:
	    			handleAccept(eventData, syscall);
	    			break;
	    			//                case SOCKET: // socket()
	    			//                    break;
	    		case KILL: // kill()
	    			handleKill(eventData);
	    			break;
	    		default:
	    			logger.log(Level.WARNING, "Unsupported syscall '"+syscall+"' for eventid '" + eventId + "'");
    		}
    		eventBuffer.remove(eventId);
    	} catch (Exception e) {
    		logger.log(Level.WARNING, "Error processing finish syscall event with eventid '"+eventId+"'", e);
    	}
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
    	String address = eventData.get("exit");
    	String length = eventData.get("a1");
    	String protection = eventData.get("a2");
    	String fd = eventData.get("fd");
    	    	
    	if(descriptors.getDescriptor(pid, fd) == null){
    		descriptors.addUnknownDescriptor(pid, fd);
    	}
    	
    	ArtifactIdentity artifactIdentity = descriptors.getDescriptor(pid, fd);
    	Artifact artifact = createArtifact(artifactIdentity, false, syscall);
    	
    	ArtifactIdentity memoryInfo = new MemoryIdentity(address, length, protection);
    	Artifact memoryArtifact = createArtifact(memoryInfo, true, syscall);
		
		Process process = checkProcessVertex(eventData, true, false);

		putVertex(memoryArtifact);
		putVertex(artifact);
		
		WasGeneratedBy wgbEdge = new WasGeneratedBy(memoryArtifact, process);
		wgbEdge.addAnnotation("time", time);
		wgbEdge.addAnnotation("operation", getOperation(syscall)+"_"+getOperation(SYSCALL.WRITE));
		addEventIdAndSourceAnnotationToEdge(wgbEdge, eventData.get("eventid"), DEV_AUDIT);
		
		Used usedEdge = new Used(process, artifact);
		usedEdge.addAnnotation("time", time);
		usedEdge.addAnnotation("operation", getOperation(syscall)+"_"+getOperation(SYSCALL.READ));
		addEventIdAndSourceAnnotationToEdge(usedEdge, eventData.get("eventid"), DEV_AUDIT);
		
		WasDerivedFrom wdfEdge = new WasDerivedFrom(memoryArtifact, artifact);
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
    	String address = eventData.get("a0");
    	String length = eventData.get("a1");
    	String protection = eventData.get("a2");
    	
    	ArtifactIdentity memoryInfo = new MemoryIdentity(address, length, protection);
    	Artifact memoryArtifact = createArtifact(memoryInfo, true, syscall);
		putVertex(memoryArtifact);
		
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
    		arg0 = new BigInteger(eventData.get("a0"), 16);
    		arg1 = new BigInteger(eventData.get("a1"), 16);
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
    				memArtifact = createArtifact(new MemoryIdentity(address.toString(16), null, null), false, SYSCALL.KILL);
    				edge = new Used(process, memArtifact);
    				edge.addAnnotation("operation", getOperation(SYSCALL.READ));
    			}else if(arg0.intValue() == -301){
    				memArtifact = createArtifact(new MemoryIdentity(address.toString(16), null, null), true, SYSCALL.KILL);
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
    
    //handles memoryidentity
    private Artifact createMemoryArtifact(ArtifactIdentity memoryIdentity, boolean update){
    	Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", memoryIdentity.getSubtype());
        artifact.addAnnotations(memoryIdentity.getAnnotationsMap());
        ArtifactProperties artifactProperties = getArtifactProperties(memoryIdentity);
        long version = artifactProperties.getMemoryVersion(update);
        artifact.addAnnotation("version", Long.toString(version));
        return artifact;
    }        
    
    //handles socketinfo, unixsocketinfo
    private Artifact createNetworkArtifact(ArtifactIdentity artifactIdentity, SYSCALL syscall) {
        Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactIdentity.getSubtype());
        
        long version = 0;
        String hostAnnotation = null, portAnnotation = null;
        Boolean isRead = isSocketRead(syscall);
        if(isRead == null){
        	return null; 
        }else if(isRead){
        	version = getArtifactProperties(artifactIdentity).getSocketReadVersion();
        	if(version == ArtifactProperties.VERSION_UNINITIALIZED){
        		version = 0;
        		getArtifactProperties(artifactIdentity).setSocketReadVersion(version);
        	}
        	hostAnnotation = "source host";
			portAnnotation = "source port";
        }else if(!isRead){
        	version = getArtifactProperties(artifactIdentity).getSocketWriteVersion();
        	if(version == ArtifactProperties.VERSION_UNINITIALIZED){
        		version = 0;
        		getArtifactProperties(artifactIdentity).setSocketWriteVersion(version);
        	}
        	hostAnnotation = "destination host";
			portAnnotation = "destination port";
        }

        artifact.addAnnotation("version", Long.toString(version));
        
        if(artifactIdentity instanceof NetworkSocketIdentity){ //either internet socket

        	//always address as source in the artifactidentity
    		artifact.addAnnotation(hostAnnotation, ((NetworkSocketIdentity) artifactIdentity).getSourceHost());
			artifact.addAnnotation(portAnnotation, ((NetworkSocketIdentity) artifactIdentity).getSourcePort());
			
			
        }else if(artifactIdentity instanceof UnixSocketIdentity){ //or unix socket
        	
        	artifact.addAnnotations(artifactIdentity.getAnnotationsMap());
        	
        }
                
        return artifact;
    }
    
    private Artifact createArtifact(ArtifactIdentity artifactIdentity, boolean update, SYSCALL syscall){
    	if(artifactIdentity == null){
    		return null;
    	}
    	
    	Artifact artifact = new Artifact();
    	
    	Class<? extends ArtifactIdentity> artifactIdentityClass = artifactIdentity.getClass();
    	if(FileIdentity.class.equals(artifactIdentityClass) 
    			|| NamedPipeIdentity.class.equals(artifactIdentityClass)
    			|| UnnamedPipeIdentity.class.equals(artifactIdentityClass)
    			|| UnknownIdentity.class.equals(artifactIdentityClass)){
    		artifact = createFileArtifact(artifactIdentity, update);
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}else if(MemoryIdentity.class.equals(artifactIdentityClass)){
    		artifact = createMemoryArtifact(artifactIdentity, update);
    		if(syscall == SYSCALL.MMAP || syscall == SYSCALL.MMAP2 || syscall == SYSCALL.MPROTECT){
    			artifact.addAnnotation(SOURCE, DEV_AUDIT);
    		}else if(syscall == SYSCALL.KILL){
    			artifact.addAnnotation(SOURCE, BEEP);
    		}else{
    			logger.log(Level.WARNING, "Missing source attribute for memory artifact because of unhandled syscall '"+syscall+"' ");
    		}		
    	}else if(NetworkSocketIdentity.class.equals(artifactIdentityClass) || UnixSocketIdentity.class.equals(artifactIdentityClass)){
    		artifact = createNetworkArtifact(artifactIdentity, syscall);
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}else{
    		return null;
    	}
    	return artifact;
    }
    
    //handles fileidentity, namedpipeidentity, unnamedpipeidentity, unknownidentity
    private Artifact createFileArtifact(ArtifactIdentity artifactInfo, boolean update) {
        Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactInfo.getSubtype());
        artifact.addAnnotations(artifactInfo.getAnnotationsMap());
        String path = artifactInfo.getAnnotationsMap().get("path");
        if(path != null){
	        if(update && path.startsWith("/dev/")){
	        	update = false;
	        }
        }
        ArtifactProperties artifactProperties = getArtifactProperties(artifactInfo);
        long version = artifactProperties.getFileVersion(update);
        artifact.addAnnotation("version", Long.toString(version));
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
    
    //true is read, false is write, null is neither read nor write
    private Boolean isSocketRead(SYSCALL syscall){
       	if((syscall == SYSCALL.ACCEPT4 || syscall == SYSCALL.ACCEPT || syscall == SYSCALL.RECVFROM || 
       			syscall == SYSCALL.RECVMSG || syscall == SYSCALL.READ || syscall == SYSCALL.READV || syscall == SYSCALL.PREAD64)){
       		return true;
       	}else if(syscall == SYSCALL.CONNECT || syscall == SYSCALL.SENDTO || syscall == SYSCALL.SENDMSG || syscall == SYSCALL.WRITE
       			|| syscall == SYSCALL.WRITEV || syscall == SYSCALL.PWRITE64){
       		return false;
       	}
       	return null;
    }   

    private void handleForkClone(Map<String, String> eventData, SYSCALL syscall) {
        // fork() and clone() receive the following message(s):
        // - SYSCALL
        // - EOE

        String time = eventData.get("time");
        String oldPID = eventData.get("pid");
        String newPID = eventData.get("exit");
        
        if(syscall == SYSCALL.CLONE){
        	//arguments are in hexdec format
        	Long flags = Long.parseLong(eventData.get("a0"), 16);
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
        		logger.log(Level.INFO, "Unable to create load edge for 'execve' syscall. event id '"+eventData.get("eventid")+"'");
        		continue;
        	}        	
        	ArtifactIdentity fileInfo = new FileIdentity(path);
        	Artifact usedArtifact = createArtifact(fileInfo, false, null);
        	Used usedEdge = new Used(newProcess, usedArtifact);
        	usedEdge.addAnnotation("time", time);
        	usedEdge.addAnnotation("operation", getOperation(SYSCALL.LOAD));
        	putVertex(usedArtifact);
        	addEventIdAndSourceAnnotationToEdge(usedEdge, eventData.get("eventid"), DEV_AUDIT);
        	putEdge(usedEdge);
        }
        
        descriptors.unlinkDescriptors(pid);
    }

    private void handleOpen(Map<String, String> eventData) {
        // open() receives the following message(s):
        // - SYSCALL
        // - CWD
    	// - PATH with nametype CREATE (file operated on) or NORMAL (file operated on) or PARENT (parent of file operated on) or DELETE (file operated on) or UNKNOWN (only when syscall fails)
    	// - PATH with nametype CREATE or NORMAL or PARENT or DELETE or UNKNOWN
        // - EOE
    	    	
    	Long flags = CommonFunctions.parseLong(eventData.get("a1"), 0L);
    	
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        String path = eventData.get("path1") == null ? eventData.get("path0") : eventData.get("path1"); 
        String nametype = eventData.get("nametype1") == null ? eventData.get("nametype0") : eventData.get("nametype1"); 
        boolean isCreate = "CREATE".equalsIgnoreCase(nametype);
        String fd = eventData.get("exit");
        String time = eventData.get("time");
        
        path = constructAbsolutePathIfNotAbsolute(cwd, path);
        
        if(path == null){
        	logger.log(Level.WARNING, "Missing CWD or PATH record in 'open'. Event with id '"+ eventData.get("eventid"));
        	return;
        }
        
        //this calls the putVertex function on the process vertex internally, that's why the process vertex isn't added here
        checkProcessVertex(eventData, true, false); 
        
        ArtifactIdentity fileInfo = new FileIdentity(path);
        
        descriptors.addDescriptor(pid, fd, fileInfo);

        AbstractEdge edge = null;
        
        //always handling open for files now irrespective of the fileIO flag being true or false.
        
        String operation = null;
        
        if(isCreate){
        	
        	Artifact vertex = createArtifact(fileInfo, true, null);
            putVertex(vertex);
            edge = new WasGeneratedBy(vertex, getProcess(pid));
        	
            operation = getOperation(SYSCALL.CREATE);
            
        }else{
        
        	if ((flags & O_RDONLY) == O_RDONLY) {
	        	boolean put = getArtifactProperties(fileInfo).getFileVersion() == ArtifactProperties.VERSION_UNINITIALIZED;
	            Artifact vertex = createArtifact(fileInfo, false, null);
	            if (put) {
	                putVertex(vertex);
	            }
	            edge = new Used(getProcess(pid), vertex);
        	} else if((flags & O_WRONLY) == O_WRONLY || (flags & O_RDWR) == O_RDWR){
	            Artifact vertex = createArtifact(fileInfo, true, null);
	            putVertex(vertex);
	            putVersionUpdateEdge(vertex, time, eventData.get("eventid"), pid);
	            edge = new WasGeneratedBy(vertex, getProcess(pid));
	        } else{
	        	logger.log(Level.INFO, "Unknown flag for open '"+flags+"'. event id '"+eventData.get("eventid")+"'" );
	        	return;
	        }
	        
	        operation = getOperation(SYSCALL.OPEN);
	        
        }
        if(edge != null){
	        edge.addAnnotation("operation", operation);
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
        String hexFD = eventData.get("a0");
        checkProcessVertex(eventData, true, false);

        String fd = new BigInteger(hexFD, 16).toString();
        descriptors.removeDescriptor(pid, fd);
    }

    private void handleRead(Map<String, String> eventData, SYSCALL syscall) {
        // read() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        String bytesRead = eventData.get("exit");
        checkProcessVertex(eventData, true, false);

        String fd = new BigInteger(hexFD, 16).toString();
        String time = eventData.get("time");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd);
        }
        
        ArtifactIdentity fileInfo = descriptors.getDescriptor(pid, fd);
        boolean put = getArtifactProperties(fileInfo).getFileVersion() == ArtifactProperties.VERSION_UNINITIALIZED;
        Artifact vertex = createArtifact(fileInfo, false, null);
        if (put) {
            putVertex(vertex);
        }
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

        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        String time = eventData.get("time");
        String bytesWritten = eventData.get("exit");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd);
        }

        ArtifactIdentity fileInfo = descriptors.getDescriptor(pid, fd);
        Artifact vertex = createArtifact(fileInfo, true, null);
        putVertex(vertex);
        putVersionUpdateEdge(vertex, time, eventData.get("eventid"), pid);
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
        ArtifactIdentity fileInfo = null;

        if (syscall == SYSCALL.TRUNCATE) {
        	String path = constructAbsolutePathIfNotAbsolute(eventData.get("cwd"), eventData.get("path0"));
        	if(path == null){
        		logger.log(Level.INFO, "Missing PATH or CWD record in 'truncate'. event id '"+eventData.get("eventid")+"'");
        		return;
        	}
            fileInfo = new FileIdentity(path);
        } else if (syscall == SYSCALL.FTRUNCATE) {
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	descriptors.addUnknownDescriptor(pid, fd);
            }
                        
            fileInfo = descriptors.getDescriptor(pid, fd);
        }

        Artifact vertex = createArtifact(fileInfo, true, null);
        putVertex(vertex);
        putVersionUpdateEdge(vertex, time, eventData.get("eventid"), pid);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(syscall));
        wgb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);
    }

    private void handleDup(Map<String, String> eventData) {
        // dup() and dup2() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        String newFD = eventData.get("exit");
        
        if(descriptors.getDescriptor(pid, fd) == null){
        	descriptors.addUnknownDescriptor(pid, fd);
        }
                
        ArtifactIdentity fileInfo = descriptors.getDescriptor(pid, fd);
        descriptors.addDescriptor(pid, newFD, fileInfo);
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
        
        ArtifactIdentity srcFileInfo = new FileIdentity(srcpath);
        ArtifactIdentity dstFileInfo = new FileIdentity(dstpath);

        boolean put = getArtifactProperties(srcFileInfo).getFileVersion() == ArtifactProperties.VERSION_UNINITIALIZED;
        Artifact srcVertex = createArtifact(srcFileInfo, false, null);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(getProcess(pid), srcVertex);
        used.addAnnotation("operation", getOperation(SYSCALL.RENAME)+"_"+getOperation(SYSCALL.READ));
        used.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);

        Artifact dstVertex = createArtifact(dstFileInfo, true, null);
        putVertex(dstVertex);
        putVersionUpdateEdge(dstVertex, time, eventData.get("eventid"), pid);
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
        
        ArtifactIdentity srcFileInfo = new FileIdentity(srcpath);
        ArtifactIdentity dstFileInfo = new FileIdentity(dstpath);

        boolean put = getArtifactProperties(srcFileInfo).getFileVersion() == ArtifactProperties.VERSION_UNINITIALIZED;
        Artifact srcVertex = createArtifact(srcFileInfo, false, null);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(getProcess(pid), srcVertex);
        used.addAnnotation("operation", syscallName + "_" + getOperation(SYSCALL.READ));
        used.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);

        Artifact dstVertex = createArtifact(dstFileInfo, true, null);
        putVertex(dstVertex);
        putVersionUpdateEdge(dstVertex, time, eventData.get("eventid"), pid);
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
        String mode = new BigInteger(eventData.get("a1"), 16).toString(8);
        // if syscall is chmod, then path is <path0> relative to <cwd>
        // if syscall is fchmod, look up file descriptor which is <a0>
        ArtifactIdentity fileInfo = null;
        if (syscall == SYSCALL.CHMOD) {
        	String path = constructAbsolutePathIfNotAbsolute(eventData.get("cwd"), eventData.get("path0"));
        	if(path == null){
        		logger.log(Level.INFO, "Missing CWD or PATH records in 'chmod' syscall. event id '"+eventData.get("eventid")+"'");
        		return;
        	}
            fileInfo = new FileIdentity(path);
        } else if (syscall == SYSCALL.FCHMOD) {
            String hexfd = eventData.get("a0");
            //arguments are in hexdec form
            String fd = String.valueOf(Long.parseLong(hexfd, 16));
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	descriptors.addUnknownDescriptor(pid, fd);
            }
                        
            fileInfo = descriptors.getDescriptor(pid, fd);
        }
        Artifact vertex = createArtifact(fileInfo, true, null);
        putVertex(vertex);
        //new version created.
        putVersionUpdateEdge(vertex, time, eventData.get("eventid"), pid);

        WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(syscall));
        wgb.addAnnotation("mode", mode);
        wgb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);
    }

    private void handlePipe(Map<String, String> eventData) {
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
    
    private ArtifactIdentity parseSaddr(String saddr){
    	if(saddr != null){
	    	ArtifactIdentity artifactInfo = null;
	    	if (saddr.charAt(1) == '2') {
	            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
	            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
	            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
	            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
	            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
	            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
	            artifactInfo = new NetworkSocketIdentity(address, port, null, null, null);
	        }else if(saddr.charAt(1) == 'A' || saddr.charAt(1) == 'a'){
	        	String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
	        	int oct1 = Integer.parseInt(saddr.substring(40, 42), 16);
	        	int oct2 = Integer.parseInt(saddr.substring(42, 44), 16);
	        	int oct3 = Integer.parseInt(saddr.substring(44, 46), 16);
	        	int oct4 = Integer.parseInt(saddr.substring(46, 48), 16);
	        	String address = String.format("::%s:%d.%d.%d.%d", saddr.substring(36, 40).toLowerCase(), oct1, oct2, oct3, oct4);
	        	artifactInfo = new NetworkSocketIdentity(address, port, null, null, null);
	        }else if(saddr.charAt(1) == '1'){
	        	String path = "";
	        	for(int a = 4; a<saddr.length() && saddr.charAt(a) != '0'; a+=2){
	        		char c = (char)(Integer.parseInt(saddr.substring(a, a+2), 16));
	        		path += c;
	        	}
	        	if(!path.isEmpty()){
	        		artifactInfo = new UnixSocketIdentity(path);
	        	}
	        }
	    	return artifactInfo;
    	}
    	return null;
    }

  /*private void processSocketCall(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentity artifactInfo = parseSaddr(saddr);
        if (artifactInfo != null) {
        	if(UnixSocketIdentity.class.equals(artifactInfo.getClass()) && !UNIX_SOCKETS){
        		return;
        	}        	
            int callType = Integer.parseInt(eventData.get("socketcall_a0"));
            // socketcall number is derived from /usr/include/linux/net.h
            if (callType == 3) {
                // connect()
            	Artifact network = createArtifact(artifactInfo, false, SYSCALL.CONNECT);
            	putVertex(network);
                WasGeneratedBy wgb = new WasGeneratedBy(network, getProcess(pid));
                wgb.addAnnotation("time", time);
                wgb.addAnnotation("operation", getOperation(SYSCALL.CONNECT));
                addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
                putEdge(wgb);
            } else if (callType == 5) {
                // accept()
            	Artifact network = createArtifact(artifactInfo, false, SYSCALL.ACCEPT);
            	putVertex(network);
                Used used = new Used(getProcess(pid), network);
                used.addAnnotation("time", time);
                used.addAnnotation("operation", getOperation(SYSCALL.ACCEPT));
                addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
                putEdge(used);
            }
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            descriptors.addDescriptor(pid, fd, artifactInfo);
        }
    }*/
    
    private void handleConnect(Map<String, String> eventData) {
    	//connect() receives the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentity artifactInfo = parseSaddr(saddr);
        if (artifactInfo != null) {
        	if(UnixSocketIdentity.class.equals(artifactInfo.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
            Artifact network = createArtifact(artifactInfo, false, SYSCALL.CONNECT);
            putVertex(network);
            WasGeneratedBy wgb = new WasGeneratedBy(network, getProcess(pid));
            wgb.addAnnotation("time", time);
            wgb.addAnnotation("operation", getOperation(SYSCALL.CONNECT));
            addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
            putEdge(wgb);
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            descriptors.addDescriptor(pid, fd, artifactInfo);
        }
    }

    private void handleAccept(Map<String, String> eventData, SYSCALL syscall) {
    	//accept() & accept4() receive the following message(s):
        // - SYSCALL
        // - SADDR
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentity artifactInfo = parseSaddr(saddr);
        if (artifactInfo != null) {
        	if(UnixSocketIdentity.class.equals(artifactInfo.getClass()) && !UNIX_SOCKETS){
        		return;
        	}
            Artifact network = createArtifact(artifactInfo, false, syscall);
            putVertex(network);
            Used used = new Used(getProcess(pid), network);
            used.addAnnotation("time", time);
            used.addAnnotation("operation", getOperation(syscall));
            addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
            putEdge(used);
            // update file descriptor table
            String fd = eventData.get("exit");
            descriptors.addDescriptor(pid, fd, artifactInfo);
        }
    }

    private void handleSend(Map<String, String> eventData, SYSCALL syscall) {
        // sendto()/sendmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        String time = eventData.get("time");
        String bytesSent = eventData.get("exit");
        
        if(descriptors.getDescriptor(pid, fd) == null){
//			unknownidentities can't be used for network artifacts because network artifacts require read and write versions but unknownidentities don't have them
//        	descriptors.addUnknownDescriptor(pid, fd); 
        	return;
        }else{
        	if(UnixSocketIdentity.class.equals(descriptors.getDescriptor(pid, fd).getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        }
       
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);
        
        if(!NET_VERSIONING){
        	putSocketSendEdge(artifactInfo, syscall, time, bytesSent, pid, eventData.get("eventid"));
        }else{
        
	        long bytesRemaining = Long.parseLong(bytesSent);
	        while(bytesRemaining > 0){
	        	long currSize = getArtifactProperties(artifactInfo).getBytesWrittenToSocket();
	        	long leftTillNext = MAX_BYTES_PER_NETWORK_ARTIFACT - currSize;
	        	if(leftTillNext > bytesRemaining){
	        		putSocketSendEdge(artifactInfo, syscall, time, String.valueOf(bytesRemaining), pid, eventData.get("eventid"));
	        		getArtifactProperties(artifactInfo).setBytesWrittenToSocket(currSize + bytesRemaining);
	        		bytesRemaining = 0;
	        	}else{ //greater or equal
	        		putSocketSendEdge(artifactInfo, syscall, time, String.valueOf(leftTillNext), pid, eventData.get("eventid"));
	        		getArtifactProperties(artifactInfo).setBytesWrittenToSocket(0);
	        		getArtifactProperties(artifactInfo).getSocketWriteVersion(true); //incrementing version
	        		//new version of network artifact for this path created. call putVertex here just once for that vertex.
	        		putVertex(createArtifact(artifactInfo, false, syscall));
	        		bytesRemaining -= leftTillNext;
	        	}
	        }
        }
    }
    
    private void putSocketSendEdge(ArtifactIdentity artifactInfo, SYSCALL syscall, String time, String size, String pid, String eventId){
    	Artifact vertex = createArtifact(artifactInfo, false, syscall);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(syscall));
        wgb.addAnnotation("time", time);
        wgb.addAnnotation("size", size);
        addEventIdAndSourceAnnotationToEdge(wgb, eventId, DEV_AUDIT);
        putEdge(wgb);
    }
    
    private void handleRecv(Map<String, String> eventData, SYSCALL syscall) {
        // sendto()/sendmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        String time = eventData.get("time");
        String bytesReceived = eventData.get("exit");

        if(descriptors.getDescriptor(pid, fd) == null){
//			unknownidentities can't be used for network artifacts because network artifacts require read and write versions but unknownidentities don't have them
//        	descriptors.addUnknownDescriptor(pid, fd); 
        	return;
        }else{
        	if(UnixSocketIdentity.class.equals(descriptors.getDescriptor(pid, fd).getClass()) && !UNIX_SOCKETS){
        		return;
        	}
        }
        
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);     
        
        if(!NET_VERSIONING){
        	putSocketRecvEdge(artifactInfo, syscall, time, bytesReceived, pid, eventData.get("eventid"));
        }else{
        
	        long bytesRemaining = Long.parseLong(bytesReceived);
	        while(bytesRemaining > 0){
	        	long currSize = getArtifactProperties(artifactInfo).getBytesReadFromSocket();
	        	long leftTillNext = MAX_BYTES_PER_NETWORK_ARTIFACT - currSize;
	        	if(leftTillNext > bytesRemaining){
	        		putSocketRecvEdge(artifactInfo, syscall, time, String.valueOf(bytesRemaining), pid, eventData.get("eventid"));
	        		getArtifactProperties(artifactInfo).setBytesReadFromSocket(currSize + bytesRemaining);
	        		bytesRemaining = 0;
	        	}else{ //greater or equal
	        		putSocketRecvEdge(artifactInfo, syscall, time, String.valueOf(leftTillNext), pid, eventData.get("eventid"));
	        		getArtifactProperties(artifactInfo).setBytesReadFromSocket(0);
	        		getArtifactProperties(artifactInfo).getSocketReadVersion(true);
	        		//new version of network artifact for this path created. call putVertex here just once for that vertex.
	        		putVertex(createArtifact(artifactInfo, false, syscall));
	        		bytesRemaining -= leftTillNext;
	        	}
	        }
        }
    }
    
    private void putSocketRecvEdge(ArtifactIdentity artifactInfo, SYSCALL syscall, String time, String size, String pid, String eventId){
    	Artifact vertex = createArtifact(artifactInfo, false, syscall);
    	Used used = new Used(getProcess(pid), vertex);
        used.addAnnotation("operation", getOperation(syscall));
        used.addAnnotation("time", time);
        used.addAnnotation("size", size);
        addEventIdAndSourceAnnotationToEdge(used, eventId, DEV_AUDIT);
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
    public Process createProcessVertex(String pid, String ppid, String name, String commandline, String cwd, 
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
    	Artifact oldArtifact = new Artifact();
    	oldArtifact.addAnnotations(newArtifact.getAnnotations());
    	Integer oldVersion = null;
    	try{
    		oldVersion = Integer.parseInt(newArtifact.getAnnotation("version")) - 1;
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
