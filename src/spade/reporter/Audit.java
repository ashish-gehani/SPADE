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
import spade.reporter.audit.PipeIdentity;
import spade.reporter.audit.SocketIdentity;
import spade.reporter.audit.UnixSocketIdentity;
import spade.reporter.audit.UnknownIdentity;
import spade.utility.BerkeleyDB;
import spade.utility.CommandUtility;
import spade.utility.CommonFunctions;
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

//  Following constant values taken from:
//  http://lxr.free-electrons.com/source/include/uapi/linux/sched.h 
//  AND  
//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/signal.h
    private final int SIGCHLD = 17, CLONE_VFORK = 0x00004000, CLONE_VM = 0x00000100;
    
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
//    To toggle monitoring of mmap, mmap2 and mprotect syscalls
    private boolean USE_MEM_MMAP_MPROTECT = true;
    private Boolean ARCH_32BIT = true;
//    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    private String AUDIT_EXEC_PATH;
    // Process map based on <pid, stack of vertices> pairs
    private final Map<String, LinkedList<Process>> processUnitStack = new HashMap<String, LinkedList<Process>>();
    // Process version map. Versioning based on units
    private final Map<String, Long> unitNumber = new HashMap<String, Long>();

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
    
//    private String lastEventId = null;
    private Thread auditLogThread = null;

    private enum SYSCALL {

        FORK, VFORK, CLONE, CHMOD, FCHMOD, SENDTO, SENDMSG, RECVFROM, RECVMSG, 
        TRUNCATE, FTRUNCATE, READ, READV, PREAD64, WRITE, WRITEV, PWRITE64, 
        ACCEPT, ACCEPT4, CONNECT, SYMLINK, LINK, SETUID, SETREUID, SETRESUID,
        SEND, RECV, OPEN, LOAD, MMAP, MMAP2, MPROTECT, CREATE, RENAME, UNIT,
        EXECVE, UPDATE
    }
    
    private BufferedWriter dumpWriter = null;
    private boolean log_successful_events_only = true; 
    
    private boolean CREATE_BEEP_UNITS = false;
        
    private Map<String, BigInteger> pidToMemAddress = new HashMap<String, BigInteger>(); 
    
    private final static String EVENT_ID = "event id";
    
    private boolean SIMPLIFY = true;
    private boolean PROCFS = false;
    
    private boolean SORTLOG = true;
    
    private String inputAuditLogFile = null;
        
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
//        if("true".equals(args.get("memOp"))){
//        	USE_MEM_MMAP_MPROTECT = true;
//        }
        // End of experimental arguments

//        initialize datastructures
        
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
        			List<String> output = CommandUtility.getOutputOfCommand("./bin/sortAuditLog " + inputAuditLogFile + " " + sortedInputAuditLog);
        			logger.log(Level.INFO, output.toString());
        			logger.log(Level.INFO, "File sorted successfully");
        			inputAuditLogFile = sortedInputAuditLog;
        			if(!new File(inputAuditLogFile).exists()){
                		logger.log(Level.SEVERE, "Failed to write sorted file to '"+inputAuditLogFile+"'");
                		return false;
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
    	        		while(!shutdown && (line = inputLogReader.readLine()) != null){
    	        			parseEventLine(line);
    	        		}
    	        		boolean printed = false;
    	        		// The loop below prevents the reporter from being removed while the records are still being processed
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
		                        wtb.addAnnotation(SOURCE, PROC_FS);
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
	
	            // Determine pids of processes that are to be ignored. These are
	            // appended to the audit rule.
	            String ignoreProcesses = "spadeSocketBridge auditd kauditd audispd";
	            List<String> pidFieldRules = ignorePidsString(ignoreProcesses);
	            auditRules = "-a exit,always ";
	            if (ARCH_32BIT){
	            	auditRules += "-F arch=b32 ";
	            }else{
	            	auditRules += "-F arch=b64 ";
	            }
	            if (USE_READ_WRITE) {
	                auditRules += "-S read -S readv -S write -S writev ";
	            }
	            if (USE_SOCK_SEND_RCV) {
	                auditRules += "-S sendto -S recvfrom -S sendmsg -S recvmsg ";
	            }
	            if (USE_MEM_MMAP_MPROTECT) {
	            	auditRules += "-S mmap -S mprotect ";
	            	if(ARCH_32BIT){
	            		auditRules += "-S mmap2 ";
	            	}
	            }
	            auditRules += "-S link -S symlink -S clone -S fork -S vfork -S execve -S open -S close "
	                    + "-S mknod -S rename -S dup -S dup2 -S setreuid -S setresuid -S setuid "
	                    + "-S connect -S accept -S chmod -S fchmod -S pipe -S truncate -S ftruncate -S pipe2 "
	                    + (log_successful_events_only ? "-F success=1 " : "");
	            for(String pidFieldRule : pidFieldRules){
	            	List<String> commandOutput = CommandUtility.getOutputOfCommand("auditctl " + auditRules + " " + pidFieldRule);
	            	logger.log(Level.INFO, "configured audit rules: {0} with ouput: {1}", new Object[]{auditRules, commandOutput});
	            }	            
	        } catch (Exception e) {
	            logger.log(Level.SEVERE, "Error configuring audit rules", e);
	            return false;
	        }

        }
        
        return true;
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
             	logger.log(Level.SEVERE, "Failed to initialize necessary datastructures", e);
             	return false;
             }
             
         }catch(Exception e){
         	logger.log(Level.SEVERE, "Failed to read default config file", e);
         	return false;
         }
    	 return true;
    }
    
    static private List<String> ignorePidsString(String ignoreProcesses) {
    	List<String> pidFieldRules = new ArrayList<String>();
        try {
        	List<String> pids = new ArrayList<String>();
            // Using pidof command now to get all pids of the mentioned processes
            java.lang.Process pidChecker = Runtime.getRuntime().exec("pidof " + ignoreProcesses);
            BufferedReader pidReader = new BufferedReader(new InputStreamReader(pidChecker.getInputStream()));
            String pidline = pidReader.readLine();
            pids.addAll(Arrays.asList(pidline.split(" ")));
            pidReader.close();
            
            // Get the PID of SPADE's JVM from /proc/self
            File[] procTaskDirectories = new File("/proc/self/task").listFiles();
            for (File procTaskDirectory : procTaskDirectories) {
                String pid = procTaskDirectory.getCanonicalFile().getName();
                pids.add(pid);
            }
            
            //one auditctl add command can only contain upto 64 fields. Dividing them up into separate lines.
            
            int fieldCount = 0;
            
            String rule = "";
            
            for(String pid : pids){
            	rule += "-F pid!=" + pid + " -F ppid!=" + pid + " ";
            	fieldCount+=2;
            	if(fieldCount >= 62){
            		fieldCount = 0;
            		pidFieldRules.add(rule);
            		rule = "";
            	}
            }
            
            if(!rule.isEmpty()){
            	pidFieldRules.add(rule);
            }

            return pidFieldRules;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error building list of processes to ignore. Partial list: " + pidFieldRules, e);
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
    		List<String> lines = CommandUtility.getOutputOfCommand("lsof -p " + pid);
    		if(lines != null && lines.size() > 1){
    			lines.remove(0); //remove the heading line
    			for(String line : lines){
    				String tokens[] = line.split("\\s+");
    				if(tokens.length >= 9){
    					String type = tokens[4].toLowerCase().trim();
    					String fd = tokens[3].trim();
    					fd = fd.replaceAll("[^0-9]", ""); //ends with r(read), w(write), u(read and write), W (lock)
    					if(isAnInteger(fd)){
	    					if("fifo".equals(type)){
	    						String path = tokens[8];
	    						if("pipe".equals(path)){ //unnamed pipe
	    							String inode = tokens[7];
		    						if(inodefd0.get(inode) == null){
		    							inodefd0.put(inode, fd);
		    						}else{
		    							ArtifactIdentity pipeInfo = new PipeIdentity(fd, inodefd0.get(inode));
		    							fds.put(fd, pipeInfo);
		    							fds.put(inodefd0.get(inode), pipeInfo);
		    							inodefd0.remove(inode);
		    						}
	    						}else{ //named pipe
	    							fds.put(fd, new PipeIdentity(path));
	    						}	    						
	    					}else if("ipv4".equals(type) || "ipv6".equals(type)){
	    						String[] hostport = tokens[8].split("->")[0].split(":");
	    						fds.put(fd, new SocketIdentity(hostport[0], hostport[1]));
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
    
    public boolean isAnInteger(String string){
    	try{
    		Integer.parseInt(string);
    		return true;
    	}catch(Exception e){
    		return false;
    	}
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
    			processNetfilterPacketEvent(eventBuffer.get(eventId));
    		}catch(Exception e){
    			logger.log(Level.WARNING, "Error processing finish syscall event with event id '"+eventId+"'", e);
    		}
    	}else{ //for events with syscalls
	    	if(ARCH_32BIT){
	    		finishEvent32(eventId);
	    	}else{
	    		finishEvent64(eventId);
	    	}
    	}
    }

    private void finishEvent32(String eventId) {
        try {
            // System call numbers are derived from:
            // https://android.googlesource.com/platform/bionic/+/android-4.1.1_r1/libc/SYSCALLS.TXT
            // TODO: Update the calls to make them linux specific.

		int NO_SYSCALL = -1;
		
		Map<String, String> eventData = eventBuffer.get(eventId);
		Integer syscall = CommonFunctions.parseInt(eventData.get("syscall"), NO_SYSCALL);
            
            if(log_successful_events_only && "no".equals(eventData.get("success")) && syscall != 129){ //in case the audit log is being read from a user provided file and syscall must not be kill to log units properly
            	eventBuffer.remove(eventId);
            	return;
            }

            switch (syscall) {
//            source : https://github.com/bnoordhuis/strace/blob/master/linux/i386/syscallent.h
	            case 90: //old_mmap
	            case 192: //mmap2
	            	processMmap(eventData, SYSCALL.MMAP2);
	            	break;
	            case 125: //mprotect
	            	processMprotect(eventData, SYSCALL.MPROTECT);
	            	break;
                case 2: // fork()
                	processForkClone(eventData, SYSCALL.FORK);
                	break;
                case 190: // vfork()
                    processForkClone(eventData, SYSCALL.VFORK);
                    break;

                case 120: // clone()
                    processForkClone(eventData, SYSCALL.CLONE);
                    break;

                case 11: // execve()
                    processExecve(eventData);
                    break;

                case 5: // open()
                    processOpen(eventData);
                    break;

                case 6: // close()
                    processClose(eventData);
                    break;

                case 9: // link()
                	processLink(eventData, SYSCALL.LINK);
                	break;
                case 83: // symlink()
                    processLink(eventData, SYSCALL.SYMLINK);
                    break;

                case 10: // unlink()
                    break;

                case 14: // mknod()
                    break;

                case 38: // rename()
                    processRename(eventData);
                    break;

                case 42: // pipe()
                case 331: // pipe2()
                case 359: // pipe2()
                    processPipe(eventData);
                    break;

                case 41: // dup()
                case 63: // dup2()
                    processDup(eventData);
                    break;

                case 203: // setreuid()
                	processSetuid(eventData, SYSCALL.SETREUID);
                    break;
                case 208: // setresuid()
                	processSetuid(eventData, SYSCALL.SETRESUID);
                    break;
                case 213: // setuid()
                    processSetuid(eventData, SYSCALL.SETUID);
                    break;

                case 92: // truncate()
                    processTruncate(eventData, SYSCALL.TRUNCATE);
                    break;

                case 93: // ftruncate()
                    processTruncate(eventData, SYSCALL.FTRUNCATE);
                    break;

                case 15: // chmod()
                    processChmod(eventData, SYSCALL.CHMOD);
                    break;

                case 94: // fchmod()
                    processChmod(eventData, SYSCALL.FCHMOD);
                    break;

                case 102: // socketcall()
                    processSocketCall(eventData);
                    break;

                case 3: // read()
                case 145: // readv()
                case 180: // pread64()
                case 4: // write()
                case 146: // writev()
                case 181: // pwrite64()
                case 290: // sendto()
                case 296: // sendmsg()
                case 292: // recvfrom()
                case 297: // recvmsg()
                	processIOEvent32(syscall, eventData);
                	break;
                case 283: // connect()
                    processConnect(eventData);
                    break;
                case 285: // accept()
                    processAccept(eventData, SYSCALL.ACCEPT);
                    break;
                case 281: // socket()
                    break;
                case 129: // kill()
                	processKill(eventData);
                	break;
                default:
                	if(syscall == NO_SYSCALL){ //i.e. didn't contain a syscall record
	               		logger.log(Level.WARNING, "Unsupported audit event type with eventid '" + eventId + "'");
                	}else{ //did contain syscall but we are not handling it yet
                		logger.log(Level.WARNING, "Unsupported syscall '"+syscall+"' for eventid '" + eventId + "'");
                	}
                    break;
            }
            eventBuffer.remove(eventId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing finish syscall event with eventid '"+eventId+"'", e);
        }
    }
    
    private void processIOEvent32(int syscall, Map<String, String> eventData){
    	String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);
    	if(artifactInfo instanceof SocketIdentity || artifactInfo instanceof UnixSocketIdentity || artifactInfo instanceof UnknownIdentity){ 
    		if(USE_SOCK_SEND_RCV){
    			switch (syscall) {
    				case 1: // write()
    					processSend(eventData, SYSCALL.WRITE);
    					break;
    				case 20: // writev()
    					processSend(eventData, SYSCALL.WRITEV);
    					break;
                	case 18: // pwrite64()	
                		processSend(eventData, SYSCALL.PWRITE64);
                		break;
	    			case 290: // sendto()
	    				processSend(eventData, SYSCALL.SENDTO);
	                	break;
	                case 296: // sendmsg()
	                	processSend(eventData, SYSCALL.SENDMSG);
	                	break;
	                case 0: // read()
	                	processRecv(eventData, SYSCALL.READ);
	                	break;
	                case 19: // readv()
	                	processRecv(eventData, SYSCALL.READV);
	                	break;
	                case 17: // pread64()
	                	processRecv(eventData, SYSCALL.PREAD64);
	                	break;
	                case 45: // recvfrom()
	                	processRecv(eventData, SYSCALL.RECVFROM);
	                	break;
	                case 47: // recvmsg()
	                	processRecv(eventData, SYSCALL.RECVMSG);
	                	break;
	                default:
						break;
				}
    		}
    	}else if(artifactInfo instanceof FileIdentity || artifactInfo instanceof MemoryIdentity || artifactInfo instanceof PipeIdentity){
    		if(USE_READ_WRITE){
    			switch(syscall){
	    			case 0: // read()
	    				processRead(eventData, SYSCALL.READ);
	                	break;
	                case 19: // readv()
	                	processRead(eventData, SYSCALL.READV);
	                	break;
	                case 17: // pread64()
	                	processRead(eventData, SYSCALL.PREAD64);
	                	break;
	                case 1: // write()
	                	processWrite(eventData, SYSCALL.WRITE);
	                    break;
	                case 20: // writev()
	                	processWrite(eventData, SYSCALL.WRITEV);
	                    break;
	                case 18: // pwrite64()
	                	processWrite(eventData, SYSCALL.PWRITE64);
	                    break;
    				default:
    					break;
    			}
    		}
    	}else{
    		logger.log(Level.WARNING, "Unknown file descriptor type for eventid '"+eventData.get("eventid")+"'");
    	}
    }
    
    private void processIOEvent64(int syscall, Map<String, String> eventData){
    	String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);
    	if(artifactInfo instanceof SocketIdentity || artifactInfo instanceof UnixSocketIdentity || artifactInfo instanceof UnknownIdentity){ 
    		if(USE_SOCK_SEND_RCV){
    			switch (syscall) {
    				case 1: // write()
    					processSend(eventData, SYSCALL.WRITE);
	                	break;
    				case 20: // writev()
    					processSend(eventData, SYSCALL.WRITEV);
	                	break;
    				case 18: // pwrite64()
    					processSend(eventData, SYSCALL.PWRITE64);
	                	break;
    				case 44: // sendto()
	    				processSend(eventData, SYSCALL.SENDTO);
	                	break;
	                case 46: // sendmsg()
	                	processSend(eventData, SYSCALL.SENDMSG);
	                	break;
	                case 0: // read()
	                	processRecv(eventData, SYSCALL.READ);
	                	break;
	                case 19: // readv()
	                	processRecv(eventData, SYSCALL.READV);
	                	break;
	                case 17: // pread64()
	                	processRecv(eventData, SYSCALL.PREAD64);
	                	break;
	                case 45: // recvfrom()
	                	processRecv(eventData, SYSCALL.RECVFROM);
	                	break;
	                case 47: // recvmsg()
	                	processRecv(eventData, SYSCALL.RECVMSG);
	                	break;
	                default:
						break;
				}
    		}
    	}else if(artifactInfo instanceof FileIdentity || artifactInfo instanceof MemoryIdentity || artifactInfo instanceof PipeIdentity){
    		if(USE_READ_WRITE){
    			switch(syscall){
	    			case 0: // read()
	    				processRead(eventData, SYSCALL.READ);
	                    break;
	                case 19: // readv()
	                	processRead(eventData, SYSCALL.READV);
	                    break;
	                case 17: // pread64()
	                	processRead(eventData, SYSCALL.PREAD64);
	                    break;
	                case 1: // write()
	                	processWrite(eventData, SYSCALL.WRITE);
	                    break;
	                case 20: // writev()
	                	processWrite(eventData, SYSCALL.WRITEV);
	                    break;
	                case 18: // pwrite64()
	                	processWrite(eventData, SYSCALL.PWRITE64);
	                    break;
    				default:
    					break;
    			}
    		}
    	}else{
    		logger.log(Level.WARNING, "Unknown file descriptor type for eventid '"+eventData.get("eventid")+"'");
    	}
    }

    private void finishEvent64(String eventId) {
        try {
            // System call numbers are derived from:
            // http://blog.rchapman.org/post/36801038863/linux-system-call-table-for-x86-64

            int NO_SYSCALL = -1;
            Map<String, String> eventData = eventBuffer.get(eventId);
            Integer syscall = CommonFunctions.parseInt(eventData.get("syscall"), NO_SYSCALL);

            if(log_successful_events_only && "no".equals(eventData.get("success")) && syscall != 62){ //in case the audit log is being read from a user provided file and syscall must not be kill to log units properly
            	eventBuffer.remove(eventId);
            	return;
            }
            
            switch (syscall) {
//            source : https://github.com/bnoordhuis/strace/blob/master/linux/x86_64/syscallent.h
	            case 9: //mmap
	            	processMmap(eventData, SYSCALL.MMAP);
	            	break;
	            case 10: //mprotect
	            	processMprotect(eventData, SYSCALL.MPROTECT);
	            	break;
                case 57: // fork()
                	processForkClone(eventData, SYSCALL.VFORK);
                    break;
                case 58: // vfork()
                    processForkClone(eventData, SYSCALL.FORK);
                    break;

                case 56: // clone()
                    processForkClone(eventData, SYSCALL.CLONE);
                    break;

                case 59: // execve()
                    processExecve(eventData);
                    break;

                case 2: // open()
                    processOpen(eventData);
                    break;

                case 3: // close()
                    processClose(eventData);
                    break;

                case 86: // link()
                	processLink(eventData, SYSCALL.LINK);
                    break;
                case 88: // symlink()
                    processLink(eventData, SYSCALL.SYMLINK);
                    break;

                case 87: // unlink()
                    break;

                case 133: // mknod()
                    break;

                case 82: // rename()
                    processRename(eventData);
                    break;

                case 22: // pipe()
                case 293: // pipe2()
                    processPipe(eventData);
                    break;

                case 32: // dup()
                case 33: // dup2()
                    processDup(eventData);
                    break;

                case 113: // setreuid()
                	processSetuid(eventData, SYSCALL.SETREUID);
                    break;
                case 117: // setresuid()
                	processSetuid(eventData, SYSCALL.SETRESUID);
                    break;
                case 105: // setuid()
                    processSetuid(eventData, SYSCALL.SETUID);
                    break;

                case 76: // truncate()
                    processTruncate(eventData, SYSCALL.TRUNCATE);
                    break;

                case 77: // ftruncate()
                    processTruncate(eventData, SYSCALL.FTRUNCATE);
                    break;

                case 90: // chmod()
                    processChmod(eventData, SYSCALL.CHMOD);
                    break;

                case 91: // fchmod()
                    processChmod(eventData, SYSCALL.FCHMOD);
                    break;

//                case 102: // socketcall()
//                    processSocketCall(eventData);
//                    break;
                    
                case 0: // read()
                case 19: // readv()
                case 17: // pread64()
                case 1: // write()
                case 20: // writev()
                case 18: // pwrite64()
                case 44: // sendto()
                case 46: // sendmsg()
                case 45: // recvfrom()
                case 47: // recvmsg()
                	processIOEvent64(syscall, eventData);
                	break;
                case 42: // connect()
                    processConnect(eventData);
                    break;
                case 288: //accept4()
                	processAccept(eventData, SYSCALL.ACCEPT4);
                    break;
                case 43: // accept()
                    processAccept(eventData, SYSCALL.ACCEPT);
                    break;
                // ////////////////////////////////////////////////////////////////
                case 41: // socket()
                    break;
                case 62:
                	processKill(eventData);
                	break;
                default:
                	if(syscall == NO_SYSCALL){ //i.e. didn't contain a syscall record
                		logger.log(Level.WARNING, "Unsupported audit event type with eventid '" + eventId + "'");
                	}else{ //did contain syscall but we are not handling it yet
                		logger.log(Level.WARNING, "Unsupported syscall '"+syscall+"' for eventid '" + eventId + "'");
	               	}
                    break;
            }
            eventBuffer.remove(eventId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing finish syscall event with eventid '"+eventId+"'", e);
        }
    }
    
    private void processMmap(Map<String, String> eventData, SYSCALL syscall){
    	// mmap() receive the following message(s):
    	// - MMAP
        // - SYSCALL
        // - EOE
    	
    	if(!USE_MEM_MMAP_MPROTECT){
    		return;
    	}
    	
    	String pid = eventData.get("pid");
    	String time = eventData.get("time");
    	String address = eventData.get("a0");
    	String length = eventData.get("a1");
    	String protection = eventData.get("a2");
    	String fd = eventData.get("fd");
    	
    	ArtifactIdentity artifactIdentity = descriptors.getDescriptor(pid, fd);
    	if(artifactIdentity == null){
    		descriptors.addUnknownDescriptor(pid, fd);
    		artifactIdentity = descriptors.getDescriptor(pid, fd);
    	}
    	Artifact artifact = createArtifact(artifactIdentity, false, syscall);
    	
    	ArtifactIdentity memoryInfo = new MemoryIdentity(address, length, protection);
    	Artifact memoryArtifact = createArtifact(memoryInfo, true, syscall);
		putVertex(memoryArtifact);
		
		Process process = null;
		if((process = getProcess(pid)) == null){
			process = checkProcessVertex(eventData, true, false);
		}
		
		putVertex(artifact);
		
		WasGeneratedBy wgbEdge = new WasGeneratedBy(memoryArtifact, process);
		wgbEdge.addAnnotation("time", time);
		wgbEdge.addAnnotation("operation", getOperation(syscall)+"_"+SYSCALL.WRITE);
		addEventIdAndSourceAnnotationToEdge(wgbEdge, eventData.get("eventid"), DEV_AUDIT);
		
		Used usedEdge = new Used(process, artifact);
		usedEdge.addAnnotation("time", time);
		usedEdge.addAnnotation("operation", getOperation(syscall)+"_"+SYSCALL.READ);
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
    
    private void processMprotect(Map<String, String> eventData, SYSCALL syscall){
    	// mprotect() receive the following message(s):
        // - SYSCALL
        // - EOE
    	
    	if(!USE_MEM_MMAP_MPROTECT){
    		return;
    	}
    	
    	String pid = eventData.get("pid");
    	String time = eventData.get("time");
    	String address = eventData.get("a0");
    	String length = eventData.get("a1");
    	String protection = eventData.get("a2");
    	
    	ArtifactIdentity memoryInfo = new MemoryIdentity(address, length, protection);
    	Artifact memoryArtifact = createArtifact(memoryInfo, true, syscall);
		putVertex(memoryArtifact);
		
		Process process = null;
		if((process = getProcess(pid)) == null){
			process = checkProcessVertex(eventData, true, false);
		}
		
		WasGeneratedBy edge = new WasGeneratedBy(memoryArtifact, process);
		edge.addAnnotation("time", time);
		edge.addAnnotation("operation", getOperation(syscall));
		addEventIdAndSourceAnnotationToEdge(edge, eventData.get("eventid"), DEV_AUDIT);
		
		putEdge(edge);
    }

    private void processKill(Map<String, String> eventData){
    	if(!CREATE_BEEP_UNITS){
    		return;
    	}
    	String pid = eventData.get("pid");
    	BigInteger arg0;
    	BigInteger arg1;
    	try{
    		arg0 = new BigInteger(eventData.get("a0"), 16);
    		arg1 = new BigInteger(eventData.get("a1"), 16);
    	}catch(Exception e){
    		logger.log(Level.WARNING, "Failed to process kill syscall", e);
    		return;
    	}
    	if(arg0.intValue() == -100 && arg1.intValue() == 1){ //unit start
    		Process addedUnit = pushUnitOnStack(pid, eventData.get("time"));
    		if(addedUnit == null){ //failed to add because there was no process
    			//add a process first using the info here and then add the unit
    			Process process = checkProcessVertex(eventData, false, false);
    			addProcess(pid, process);
    			addedUnit = pushUnitOnStack(pid, eventData.get("time"));
    		}
    		putVertex(addedUnit);
    		//add edge between the new unit and the main unit to keep the graph connected
    		WasTriggeredBy wtb = new WasTriggeredBy(addedUnit, getUnitForPid(pid, 0));
        	wtb.addAnnotation("operation", getOperation(SYSCALL.UNIT));
        	wtb.addAnnotation("time", eventData.get("time"));
        	addEventIdAndSourceAnnotationToEdge(wtb, eventData.get("eventid"), BEEP);
        	putEdge(wtb);
    	}else if(arg0.intValue() == -101 && arg1.intValue() == 1){ //unit end
    		//remove the last added unit
    		popUnitFromStack(pid);
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
    				memArtifact = createArtifact(new MemoryIdentity(address.toString(16), null, null), false, null);
    				edge = new Used(process, memArtifact);
    				edge.addAnnotation("operation", getOperation(SYSCALL.READ));
    			}else if(arg0.intValue() == -301){
    				memArtifact = createArtifact(new MemoryIdentity(address.toString(16), null, null), true, null);
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
    
    //handles memoryinfo
    private Artifact createMemoryArtifact(ArtifactIdentity artifactInfo, boolean update){
    	MemoryIdentity memoryInfo = (MemoryIdentity)artifactInfo;
    	Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactInfo.getSubtype());
        artifact.addAnnotation("memory address", memoryInfo.getMemoryAddress());
        if(memoryInfo.getSize() != null){
        	artifact.addAnnotation("size", memoryInfo.getSize());
        }
        if(memoryInfo.getProtection() != null){
        	artifact.addAnnotation("protection", memoryInfo.getProtection());
        }
        ArtifactProperties artifactProperties = getArtifactProperties(artifactInfo);
        long version = artifactProperties.getMemoryVersion(update);
        artifact.addAnnotation("version", Long.toString(version));
        return artifact;
    }        
    
    //handles socketinfo, unixsocketinfo, unknowninfo
    private Artifact createNetworkArtifact(ArtifactIdentity artifactInfo, SYSCALL syscall) {
        Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactInfo.getSubtype());
        
        long version = 0;
        String hostAnnotation = null, portAnnotation = null;
        Boolean isRead = isSocketRead(syscall);
        if(isRead == null){
        	return null; 
        }else if(isRead){
        	version = getArtifactProperties(artifactInfo).getSocketReadVersion();
        	hostAnnotation = "source host";
			portAnnotation = "source port";
        }else if(!isRead){
        	version = getArtifactProperties(artifactInfo).getSocketWriteVersion();
        	hostAnnotation = "destination host";
			portAnnotation = "destination port";
        }
        artifact.addAnnotation("version", Long.toString(version));
        
        if(artifactInfo instanceof SocketIdentity){ //either internet socket
        	    		
    		artifact.addAnnotation(hostAnnotation, ((SocketIdentity) artifactInfo).getHost());
			artifact.addAnnotation(portAnnotation, ((SocketIdentity) artifactInfo).getPort());
			
        }else if(artifactInfo instanceof UnixSocketIdentity || artifactInfo instanceof UnknownIdentity){ //or unix socket
        	
        	artifact.addAnnotation("path", artifactInfo.getStringFormattedValue());
        	
        }
        
        return artifact;
    }
    
    private Artifact createArtifact(ArtifactIdentity artifactInfo, boolean update, SYSCALL syscall){
    	Artifact artifact = null;
    	if(artifactInfo instanceof FileIdentity || artifactInfo instanceof PipeIdentity || (artifactInfo instanceof UnknownIdentity && (syscall == SYSCALL.MMAP || syscall == SYSCALL.MMAP2 || syscall == SYSCALL.MPROTECT))){
    		artifact = createFileArtifact(artifactInfo, update);
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}else if(artifactInfo instanceof MemoryIdentity){
    		artifact = createMemoryArtifact(artifactInfo, update);
    		if(syscall == SYSCALL.MMAP || syscall == SYSCALL.MMAP2 || syscall == SYSCALL.MPROTECT){
    			artifact.addAnnotation(SOURCE, DEV_AUDIT);
    		}else{
    			artifact.addAnnotation(SOURCE, BEEP);
    		}    		
    	}else if(artifactInfo instanceof SocketIdentity || artifactInfo instanceof UnixSocketIdentity || (artifactInfo instanceof UnknownIdentity && syscall != null)){
    		artifact = createNetworkArtifact(artifactInfo, syscall);
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}
    	return artifact;
    }
    
    //handles fileinfo, pipeinfo, unknowninfo
    private Artifact createFileArtifact(ArtifactIdentity artifactInfo, boolean update) {
        Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactInfo.getSubtype());
        artifact.addAnnotation("path", artifactInfo.getStringFormattedValue());
        if(update && artifactInfo.getStringFormattedValue().startsWith("/dev/")){
        	update = false;
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

    private void processForkClone(Map<String, String> eventData, SYSCALL syscall) {
        // fork() and clone() receive the following message(s):
        // - SYSCALL
        // - EOE

        String time = eventData.get("time");
        String oldPID = eventData.get("pid");
        String newPID = eventData.get("exit");
        
        if(syscall == SYSCALL.CLONE){
        	Integer flags = CommonFunctions.parseInt(eventData.get("a2"), 0);
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

    private void processExecve(Map<String, String> eventData) {
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

        Process newProcess = checkProcessVertex(eventData, false, true);
        if (!newProcess.getAnnotations().containsKey("commandline")) {
            // Unable to retrieve complete process information; generate vertex
            // based on audit information
            String commandline = null;
            if(eventData.get("execve_argc") != null){
            	int argc = Integer.parseInt(eventData.get("execve_argc"));
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
        Integer totalPaths = CommonFunctions.parseInt(eventData.get("items"), 0);
        for(int pathNumber = 0; pathNumber < totalPaths; pathNumber++){
        	String path = eventData.get("path"+pathNumber);
        	putLoadEdge(cwd, path, newProcess, time, eventData.get("eventid"));
        }
        
        descriptors.unlinkDescriptors(pid);
    }
    
    private void putLoadEdge(String cwd, String path, Process process, String time, String eventId){
    	if(path == null){
    		return;
    	}
    	boolean joinPaths = false;
    	if(path.startsWith("./")){ //path of the following format : ./helloworld
    		path = path.substring(2);
    	 	joinPaths = true;
    	}else if(!path.startsWith(".") && !path.startsWith("/")){
    		joinPaths = true;
    	}
    	if(joinPaths){
    		if(cwd == null){
    			return;
    		}else{
    			path = joinPaths(cwd, path);
    		}
    	}
    	ArtifactIdentity fileInfo = new FileIdentity(path);
    	Artifact usedArtifact = createArtifact(fileInfo, false, null);
    	Used usedEdge = new Used(process, usedArtifact);
    	usedEdge.addAnnotation("time", time);
    	usedEdge.addAnnotation("operation", getOperation(SYSCALL.LOAD));
    	putVertex(usedArtifact);
    	addEventIdAndSourceAnnotationToEdge(usedEdge, eventId, DEV_AUDIT);
    	putEdge(usedEdge);
    }

    private void processOpen(Map<String, String> eventData) {
        // open() receives the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH
        // - EOE
    	    	
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        String path = eventData.get("path1") == null ? eventData.get("path0") : eventData.get("path1"); 
        String nametype = eventData.get("nametype1") == null ? eventData.get("nametype0") : eventData.get("nametype1"); 
        boolean isCreate = "CREATE".equalsIgnoreCase(nametype);
        String fd = eventData.get("exit");
        String time = eventData.get("time");
        String flags = eventData.get("a1");
        
        if(path == null){
        	logger.log(Level.WARNING, "Missing PATH record. Event with id '"+ eventData.get("eventid") +"' ignored.");
        	return;
        }
        
        if (!path.startsWith("/")) {
            path = joinPaths(cwd, path);
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
        
	        if (flags.charAt(flags.length() - 1) == '0') {
	        	boolean put = getArtifactProperties(fileInfo).getFileVersion() == -1;
	            Artifact vertex = createArtifact(fileInfo, false, null);
	            if (put) {
	                putVertex(vertex);
	            }
	            edge = new Used(getProcess(pid), vertex);
	        } else {
	            Artifact vertex = createArtifact(fileInfo, true, null);
	            putVertex(vertex);
	            putVersionUpdateEdge(vertex, time, eventData.get("eventid"), pid);
	            edge = new WasGeneratedBy(vertex, getProcess(pid));
	        }
	        
	        operation = getOperation(SYSCALL.OPEN);
	        
        }
        edge.addAnnotation("operation", operation);
        edge.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(edge, eventData.get("eventid"), DEV_AUDIT);
        putEdge(edge);
    }

    private void processClose(Map<String, String> eventData) {
        // close() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        checkProcessVertex(eventData, true, false);

        String fd = new BigInteger(hexFD, 16).toString();
        descriptors.removeDescriptor(pid, fd);
    }

    private void processRead(Map<String, String> eventData, SYSCALL syscall) {
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
        boolean put = getArtifactProperties(fileInfo).getFileVersion() == -1;
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

    private void processWrite(Map<String, String> eventData, SYSCALL syscall) {
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

    private void processTruncate(Map<String, String> eventData, SYSCALL syscall) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String time = eventData.get("time");
        ArtifactIdentity fileInfo = null;

        if (syscall == SYSCALL.TRUNCATE) {
            fileInfo = new FileIdentity(joinPaths(eventData.get("cwd"), eventData.get("path0")));
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

    private void processDup(Map<String, String> eventData) {
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

    private void processSetuid(Map<String, String> eventData, SYSCALL syscall) {
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

    private void processRename(Map<String, String> eventData) {
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

        String srcpath = joinPaths(cwd, eventData.get("path2"));
        String dstpath = joinPaths(cwd, eventData.get("path3"));
        
        ArtifactIdentity srcFileInfo = new FileIdentity(srcpath);
        ArtifactIdentity dstFileInfo = new FileIdentity(dstpath);

        boolean put = getArtifactProperties(srcFileInfo).getFileVersion() == -1;
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

    private void processLink(Map<String, String> eventData, SYSCALL syscall) {
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

        String srcpath = joinPaths(cwd, eventData.get("path0"));
        String dstpath = joinPaths(cwd, eventData.get("path2"));
        
        ArtifactIdentity srcFileInfo = new FileIdentity(srcpath);
        ArtifactIdentity dstFileInfo = new FileIdentity(dstpath);

        boolean put = getArtifactProperties(srcFileInfo).getFileVersion() == -1;
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

    private void processChmod(Map<String, String> eventData, SYSCALL syscall) {
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
            fileInfo = new FileIdentity(joinPaths(eventData.get("cwd"), eventData.get("path0")));
        } else if (syscall == SYSCALL.FCHMOD) {
            String fd = eventData.get("a0");
            
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

    private void processPipe(Map<String, String> eventData) {
        // pipe() receives the following message(s):
        // - SYSCALL
        // - FD_PAIR
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String fd0 = eventData.get("fd0");
        String fd1 = eventData.get("fd1");
        ArtifactIdentity pipeInfo = new PipeIdentity(fd0, fd1);
        descriptors.addDescriptor(pid, fd0, pipeInfo);
        descriptors.addDescriptor(pid, fd1, pipeInfo);
    }
    
    
    
    private void processNetfilterPacketEvent(Map<String, String> eventData){
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
    	ArtifactIdentity artifactInfo = null;
    	if (saddr.charAt(1) == '2') {
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            artifactInfo = new SocketIdentity(address, port);
        }else if(saddr.charAt(1) == 'A' || saddr.charAt(1) == 'a'){
        	String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
        	int oct1 = Integer.parseInt(saddr.substring(40, 42), 16);
        	int oct2 = Integer.parseInt(saddr.substring(42, 44), 16);
        	int oct3 = Integer.parseInt(saddr.substring(44, 46), 16);
        	int oct4 = Integer.parseInt(saddr.substring(46, 48), 16);
        	String address = String.format("::%s:%d.%d.%d.%d", saddr.substring(36, 40).toLowerCase(), oct1, oct2, oct3, oct4);
        	artifactInfo = new SocketIdentity(address, port);
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

    private void processSocketCall(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentity artifactInfo = parseSaddr(saddr);
        if (artifactInfo != null) {
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
    }
    
    private void processConnect(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentity artifactInfo = parseSaddr(saddr);
        if (artifactInfo != null) {
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

    private void processAccept(Map<String, String> eventData, SYSCALL syscall) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactIdentity artifactInfo = parseSaddr(saddr);
        if (artifactInfo != null) {
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

    private void processSend(Map<String, String> eventData, SYSCALL syscall) {
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
        	descriptors.addUnknownDescriptor(pid, fd);
        }
       
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);
        
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
    
    private void putSocketSendEdge(ArtifactIdentity artifactInfo, SYSCALL syscall, String time, String size, String pid, String eventId){
    	Artifact vertex = createArtifact(artifactInfo, false, syscall);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, getProcess(pid));
        wgb.addAnnotation("operation", getOperation(syscall));
        wgb.addAnnotation("time", time);
        wgb.addAnnotation("size", size);
        addEventIdAndSourceAnnotationToEdge(wgb, eventId, DEV_AUDIT);
        putEdge(wgb);
    }
    
    private void processRecv(Map<String, String> eventData, SYSCALL syscall) {
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
        	descriptors.addUnknownDescriptor(pid, fd);
        }
        
        ArtifactIdentity artifactInfo = descriptors.getDescriptor(pid, fd);        
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
    
    private void putSocketRecvEdge(ArtifactIdentity artifactInfo, SYSCALL syscall, String time, String size, String pid, String eventId){
    	Artifact vertex = createArtifact(artifactInfo, false, syscall);
    	Used used = new Used(getProcess(pid), vertex);
        used.addAnnotation("operation", getOperation(syscall));
        used.addAnnotation("time", time);
        used.addAnnotation("size", size);
        addEventIdAndSourceAnnotationToEdge(used, eventId, DEV_AUDIT);
        putEdge(used);
    }

    private String joinPaths(String path1, String path2) {
        if (path1.endsWith("/") && path2.startsWith("/")) {
            return path1 + path2.substring(1);
        } else if (!path1.endsWith("/") && !path2.startsWith("/")) {
            return path1 + "/" + path2;
        } else {
            return path1 + path2;
        }
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
                putEdge(wtb);
            }
        }
        return resultProcess;
    }

    private String getOperation(SYSCALL syscall){
    	SYSCALL returnSyscall = syscall;
    	if(SIMPLIFY){
    		switch (syscall) {
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
        
    private Process getUnitForPid(String pid, Integer unitNumber){
    	if(processUnitStack.get(pid) != null && processUnitStack.get(pid).size() > unitNumber && unitNumber > -1){
    		return processUnitStack.get(pid).get(unitNumber);
    	}
    	return null;
    }
    
    private Long getNextUnitNumber(String pid){
    	if(unitNumber.get(pid) == null){
    		unitNumber.put(pid, -1L);
    	}
    	unitNumber.put(pid, unitNumber.get(pid) + 1);
    	return unitNumber.get(pid);
    }
    
    private void addProcess(String pid, Process process){ 
    	unitNumber.remove(pid); //start unit count from start
    	processUnitStack.put(pid, new LinkedList<Process>()); //always reset the stack whenever the main process is being added
    	if(CREATE_BEEP_UNITS){
    		process.addAnnotation("unit", String.valueOf(getNextUnitNumber(pid)));
    	}
    	processUnitStack.get(pid).addLast(process);
    }
    
    private Process getProcess(String pid){
    	if(processUnitStack.get(pid) != null && !processUnitStack.get(pid).isEmpty()){
    		Process process = processUnitStack.get(pid).peekLast();
    		return process;
    	}
    	return null;
    }
    
    private Process pushUnitOnStack(String pid, String startTime){
    	if(processUnitStack.get(pid) == null || processUnitStack.get(pid).isEmpty()){ //stack must always contain one element which needs to be the original process
    		return null;
    	}
    	Process newUnit = createCopyOfProcess(processUnitStack.get(pid).peekFirst(), startTime); //first element is always the main process vertex
    	newUnit.addAnnotation("unit", String.valueOf(getNextUnitNumber(pid)));
    	processUnitStack.get(pid).addLast(newUnit);
    	
    	return newUnit;
    }
    
    private Process popUnitFromStack(String pid){
    	if(processUnitStack.get(pid) != null && processUnitStack.get(pid).size() > 1){ //first element is the main process and not the unit so there for a unit to exist there must be two processes at least
    		return processUnitStack.get(pid).removeLast();
    	}
    	return null;
    }
    
    private Process createCopyOfProcess(Process process, String startTime){
    	//passing commandline and cwd as null because we don't want those two fields copied onto units
    	return createProcessVertex(process.getAnnotation("pid"), process.getAnnotation("ppid"), process.getAnnotation("name"), null, null, 
    			process.getAnnotation("uid"), process.getAnnotation("euid"), process.getAnnotation("suid"), process.getAnnotation("fsuid"), 
    			process.getAnnotation("gid"), process.getAnnotation("egid"), process.getAnnotation("sgid"), process.getAnnotation("fsgid"), 
    			BEEP, startTime);
    }
    
}