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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.reporter.audit.ArtifactInfo;
import spade.reporter.audit.DescriptorManager;
import spade.reporter.audit.FileInfo;
import spade.reporter.audit.MemoryInfo;
import spade.reporter.audit.PipeInfo;
import spade.reporter.audit.SocketInfo;
import spade.reporter.audit.UnixSocketInfo;
import spade.reporter.audit.UnknownInfo;
import spade.utility.CommandUtility;
import spade.utility.CommonFunctions;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 *
 * @author Dawood Tariq, Sharjeel Ahmed Qureshi
 */
public class Audit extends AbstractReporter {

    static final Logger logger = Logger.getLogger(Audit.class.getName());

    // Store log for debugging purposes
    private boolean DEBUG_DUMP_LOG;
    private String DEBUG_DUMP_FILE;
    private BufferedReader eventReader;
    private volatile boolean shutdown = false;
    private long boottime = 0;
    private final long THREAD_CLEANUP_TIMEOUT = 1000;
    private final boolean USE_PROCFS = false;
    private boolean USE_READ_WRITE = false;
    // To toggle monitoring of system calls: sendmsg, recvmsg, sendto, and recvfrom
    private boolean USE_SOCK_SEND_RCV = false;
    private Boolean ARCH_32BIT = true;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    private String AUDIT_EXEC_PATH;
    // Process map based on <pid, stack of vertices> pairs
    private final Map<String, LinkedList<Process>> processUnitStack = new HashMap<String, LinkedList<Process>>();
    // Process version map. Versioning based on units
    private final Map<String, Long> unitNumber = new HashMap<String, Long>();

    private final DescriptorManager descriptors = new DescriptorManager();
    // Event buffer map based on <audit_record_id <key, value>> pairs
    private final Map<String, Map<String, String>> eventBuffer = new HashMap<>();
    // File and memory version map based on <path, version> pairs
    private final Map<ArtifactInfo, Integer> artifactVersions = new HashMap<>();
    // Socket read version map based on <location, version> pairs
    private final Map<ArtifactInfo, Integer> socketReadVersions = new HashMap<>();
    // Socket write version map based on <location, version> pairs
    private final Map<ArtifactInfo, Integer> socketWriteVersions = new HashMap<>();
    private Thread eventProcessorThread = null;
    private String auditRules;
    
    private Map<ArtifactInfo, Long> networkLocationToBytesWrittenMap = new HashMap<ArtifactInfo, Long>(){
    	public Long get(Object key){
    		if(super.get(key) == null){
    			super.put((ArtifactInfo)key, 0L);
    		}
    		return super.get(key);
    	}
    };
    
    private Map<ArtifactInfo, Long> networkLocationToBytesReadMap = new HashMap<ArtifactInfo, Long>(){
    	public Long get(Object key){
    		if(super.get(key) == null){
    			super.put((ArtifactInfo)key, 0L);
    		}
    		return super.get(key);
    	}
    };
    
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
    private static final Pattern pattern_path = Pattern.compile("item=([0-9]*)\\s*name=\"*((?<=\")[^\"]*(?=\"))\"*");
    
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
	SEND, RECV
    }
    
    private BufferedWriter dumpWriter = null;
    private boolean log_successful_events_only = true; 
    
    private boolean CREATE_BEEP_UNITS = false;
        
    private Map<String, BigInteger> pidToMemAddress = new HashMap<String, BigInteger>(); 
    
    private final static String EVENT_ID = "event id";
    
    private boolean SIMPLIFY = true;
        
    @Override
    public boolean launch(String arguments) {

        arguments = arguments == null ? "" : arguments;

        try {
            InputStream archStream = Runtime.getRuntime().exec("uname -i").getInputStream();
            BufferedReader archReader = new BufferedReader(new InputStreamReader(archStream));
            String archLine = archReader.readLine().trim();
            ARCH_32BIT = archLine.equals("i686");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "error reading the system architecture", e);
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
            	logger.log(Level.WARNING, "Failed to create output log writer");
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
        if("false".equals(args.get("simplify"))){
        	SIMPLIFY = false;
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
            logger.log(Level.SEVERE, "error reading boot time information from /proc/", e);
        }
        
        final String inputAuditLogFile = args.get("inputLog");
        if(inputAuditLogFile != null){ //if a path is passed but it is not a valid file then throw an error
        	
        	if(!new File(inputAuditLogFile).exists()){
        		logger.log(Level.SEVERE, "File at specified path doesn't exist.");
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
        	        	while(!shutdown){
        	        		if(!printed && getBuffer().size() == 0){//buffer processed
        	        			printed = true;
        	        			logger.log(Level.INFO, "Audit log processing succeeded: " + inputAuditLogFile);
        	        		}
        	        		try{
        	        			Thread.sleep(500);
        	        		}catch(Exception e){
        	        			logger.log(Level.SEVERE, null, e);
        	        		}
    					}
        	        	if(!printed){
        	        		logger.log(Level.INFO, "Audit log processing succeeded: " + inputAuditLogFile);
        	        	}
    	        	}catch(Exception e){
    	        		logger.log(Level.WARNING, "Audit log processing failed: " + inputAuditLogFile, e);
    	        	}finally{
    	        		try{
    	        			if(inputLogReader != null){
    	        				inputLogReader.close();
    	        			}
    	        		}catch(Exception e){
    	        			logger.log(Level.SEVERE, "Failed to close audit input log reader", e);
    	        		}
    	        	}
    			}
    		});
        	auditLogThread.start();
        	
        }else{

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
	                    Map<String, ArtifactInfo> fds = getFileDescriptors(currentPID);
	                    if (fds != null) {
	                        descriptors.addDescriptors(currentPID, fds);
	                    }
	                } catch (Exception e) {
	                    // Continue
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
	                        logger.log(Level.SEVERE, "error launching main runnable thread", e);
	                    }
	                }
	            };
	            eventProcessorThread = new Thread(eventProcessor, "Audit-Thread");
	            eventProcessorThread.start();
	
	            // Determine pids of processes that are to be ignored. These are
	            // appended to the audit rule.
	            String ignoreProcesses = "spadeSocketBridge auditd kauditd audispd";
	            StringBuilder ignorePids = ignorePidsString(ignoreProcesses);
	            auditRules = "-a exit,always ";
	            if (USE_READ_WRITE) {
	                auditRules += "-S read -S readv -S write -S writev ";
	            }
	            if (USE_SOCK_SEND_RCV) {
	                auditRules += "-S sendto -S recvfrom -S sendmsg -S recvmsg ";
	            }
	            auditRules += "-S link -S symlink -S clone -S fork -S vfork -S execve -S open -S close "
	                    + "-S mknod -S rename -S dup -S dup2 -S setreuid -S setresuid -S setuid "
	                    + "-S connect -S accept -S chmod -S fchmod -S pipe -S truncate -S ftruncate -S pipe2 "
	                    + (log_successful_events_only ? "-F success=1 " : "") + ignorePids.toString();
	            Runtime.getRuntime().exec("auditctl " + auditRules).waitFor();
	            logger.log(Level.INFO, "configured audit rules: {0}", auditRules);
	        } catch (IOException | InterruptedException e) {
	            logger.log(Level.SEVERE, "error configuring audit rules", e);
	            return false;
	        }

        }
        return true;
    }
    
    static private StringBuilder ignorePidsString(String ignoreProcesses) {
        StringBuilder ignorePids = new StringBuilder();
        try {
            // Using pidof command now to get all pids of the mentioned processes
            java.lang.Process pidChecker = Runtime.getRuntime().exec("pidof " + ignoreProcesses);
            BufferedReader pidReader = new BufferedReader(new InputStreamReader(pidChecker.getInputStream()));
            String pidline = pidReader.readLine();
            String ppidline = pidline;
            if (pidline != null) {
                ignorePids.append(" -F pid!=").append(pidline.replace(" ", " -F pid!="));
                ignorePids.append(" -F ppid!=").append(ppidline.replace(" ", " -F ppid!="));
            }
            pidReader.close();

            // Get the PID of SPADE's JVM from /proc/self
            File[] procTaskDirectories = new File("/proc/self/task").listFiles();
            for (File procTaskDirectory : procTaskDirectories) {
                String pid = procTaskDirectory.getCanonicalFile().getName();
                ignorePids.append(" -F pid!=").append(pid);
                ignorePids.append(" -F ppid!=").append(pid);
            }

            return ignorePids;
        } catch (IOException e) {
            logger.log(Level.WARNING, "error building list of processes to ignore. partial list: " + ignorePids, e);
            return new StringBuilder();
        }
    }

	private Map<String, ArtifactInfo> getFileDescriptors(String pid){
		
		if(auditLogThread != null){ //i.e. the audit log is being read from a file.
			return null;
		}
    	
    	Map<String, ArtifactInfo> fds = new HashMap<String, ArtifactInfo>();
    	
    	Map<String, String> inodefd0 = new HashMap<String, String>();
    	
    	try{
    		List<String> lines = CommandUtility.getOutputOfCommand("lsof -p /proc/" + pid);
    		if(lines != null && lines.size() > 1){
    			lines.remove(0); //remove the heading line
    			for(String line : lines){
    				String tokens[] = line.split("\\s+");
    				if(tokens.length >= 9){
    					String type = tokens[4].toLowerCase().trim();
    					String fd = tokens[3].trim();
    					fd = fd.substring(0, fd.length() - 1); //last character is either r(read), w(write) or u(read and write)
    					if(isAnInteger(fd)){
	    					if("fifo".equals(type)){
	    						String path = tokens[8];
	    						if("pipe".equals(path)){ //unnamed pipe
	    							String inode = tokens[7];
		    						if(inodefd0.get(inode) == null){
		    							inodefd0.put(inode, fd);
		    						}else{
		    							ArtifactInfo pipeInfo = new PipeInfo(fd, inodefd0.get(inode));
		    							fds.put(fd, pipeInfo);
		    							fds.put(inodefd0.get(inode), pipeInfo);
		    							inodefd0.remove(inode);
		    						}
	    						}else{ //named pipe
	    							fds.put(fd, new PipeInfo(path));
	    						}	    						
	    					}else if("ipv4".equals(type) || "ipv6".equals(type)){
	    						String[] hostport = tokens[8].split("->")[0].split(":");
	    						fds.put(fd, new SocketInfo(hostport[0], hostport[1]));
	    					}else if("reg".equals(type) || "chr".equals(type)){
	    						String path = tokens[8];
	    						fds.put(fd, new FileInfo(path));  						
	    					}else if("unix".equals(type)){
	    						String path = tokens[8];
	    						if(!path.equals("socket")){
	    							fds.put(fd, new UnixSocketInfo(path));
	    						}
	    					}
    					}
    				}
    			}
    		}
    	}catch(Exception e){
    		logger.log(Level.SEVERE, null, e);
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
            logger.log(Level.SEVERE, "error shutting down", e);
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
            String node = event_start_matcher.group(1);
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
                    eventBuffer.get(eventId).put("path" + item, name);
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
    	
    	if (!eventBuffer.containsKey(eventId)) {
            logger.log(Level.WARNING, "EOE for eventID {0} received with no prior Event Info", new Object[]{eventId});
            return;
        }
    	
    	if("NETFILTER_PKT".equals(eventBuffer.get(eventId).get("type"))){ //for events with no syscalls
    		try{
    			processNetfilterPacketEvent(eventBuffer.get(eventId));
    		}catch(Exception e){
    			logger.log(Level.SEVERE, "error processing finish syscall event with event id '"+eventId+"'", e);
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
            logger.log(Level.SEVERE, "error processing finish syscall event with eventid '"+eventId+"'", e);
        }
    }
    
    private void processIOEvent32(int syscall, Map<String, String> eventData){
    	String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        ArtifactInfo artifactInfo = descriptors.getDescriptor(pid, fd);
    	if(artifactInfo instanceof SocketInfo || artifactInfo instanceof UnixSocketInfo || artifactInfo instanceof UnknownInfo){ 
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
    	}else if(artifactInfo instanceof FileInfo || artifactInfo instanceof MemoryInfo || artifactInfo instanceof PipeInfo){
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
    		logger.log(Level.SEVERE, "Unknown file descriptor type for eventid '"+eventData.get("eventid")+"'");
    	}
    }

    private void processIOEvent64(int syscall, Map<String, String> eventData){
    	String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        ArtifactInfo artifactInfo = descriptors.getDescriptor(pid, fd);
    	if(artifactInfo instanceof SocketInfo || artifactInfo instanceof UnixSocketInfo || artifactInfo instanceof UnknownInfo){ 
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
    	}else if(artifactInfo instanceof FileInfo || artifactInfo instanceof MemoryInfo || artifactInfo instanceof PipeInfo){
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
    		logger.log(Level.SEVERE, "Unknown file descriptor type for eventid '"+eventData.get("eventid")+"'");
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
            logger.log(Level.SEVERE, "error processing finish syscall event with eventid '"+eventId+"'", e);
        }
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
        	wtb.addAnnotation("operation", "unit");
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
    				memArtifact = createArtifact(new MemoryInfo(address.toString(16)), false, null);
    				edge = new Used(process, memArtifact);
    				edge.addAnnotation("operation", "read");
    			}else if(arg0.intValue() == -301){
    				memArtifact = createArtifact(new MemoryInfo(address.toString(16)), true, null);
    				edge = new WasGeneratedBy(memArtifact, process);
    				edge.addAnnotation("operation", "write");
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
    private Artifact createMemoryArtifact(ArtifactInfo artifactInfo, boolean update){
    	Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactInfo.getSubtype());
        artifact.addAnnotation("memory address", artifactInfo.getStringFormattedValue());
        Integer version = getVersion(artifactVersions, artifactInfo, update);
        artifact.addAnnotation("version", Integer.toString(version));
        return artifact;
    }        
    
    //handles socketinfo, unixsocketinfo, unknowninfo
    private Artifact createNetworkArtifact(ArtifactInfo artifactInfo, SYSCALL syscall) {
        Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactInfo.getSubtype());
        
        String hostAnnotation = null, portAnnotation = null;
    	Map<ArtifactInfo, Integer> versionMap = null;
        Boolean isRead = isSocketRead(syscall);
        if(isRead == null){
        	return null; 
        }else if(isRead){
        	versionMap = socketReadVersions;
        	hostAnnotation = "source host";
			portAnnotation = "source port";
        }else if(!isRead){
        	versionMap = socketWriteVersions;
        	hostAnnotation = "destination host";
			portAnnotation = "destination port";
        }
        artifact.addAnnotation("version", Integer.toString(getVersion(versionMap, artifactInfo, false)));
        
        if(artifactInfo instanceof SocketInfo){ //either internet socket
        	    		
    		artifact.addAnnotation(hostAnnotation, ((SocketInfo) artifactInfo).getHost());
			artifact.addAnnotation(portAnnotation, ((SocketInfo) artifactInfo).getPort());
			
        }else if(artifactInfo instanceof UnixSocketInfo || artifactInfo instanceof UnknownInfo){ //or unix socket
        	
        	artifact.addAnnotation("path", artifactInfo.getStringFormattedValue());
        	
        }
        
        return artifact;
    }
    
    private Artifact createArtifact(ArtifactInfo artifactInfo, boolean update, SYSCALL syscall){
    	Artifact artifact = null;
    	if(artifactInfo instanceof FileInfo || artifactInfo instanceof PipeInfo || (artifactInfo instanceof UnknownInfo && syscall == null)){
    		artifact = createFileArtifact(artifactInfo, update);
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}else if(artifactInfo instanceof MemoryInfo){
    		artifact = createMemoryArtifact(artifactInfo, update);
    		artifact.addAnnotation(SOURCE, BEEP);
    	}else if(artifactInfo instanceof SocketInfo || artifactInfo instanceof UnixSocketInfo || (artifactInfo instanceof UnknownInfo && syscall != null)){
    		artifact = createNetworkArtifact(artifactInfo, syscall);
    		artifact.addAnnotation(SOURCE, DEV_AUDIT);
    	}
    	return artifact;
    }
    
    //handles fileinfo, pipeinfo, unknowninfo
    private Artifact createFileArtifact(ArtifactInfo artifactInfo, boolean update) {
        Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", artifactInfo.getSubtype());
        artifact.addAnnotation("path", artifactInfo.getStringFormattedValue());
        if(update && artifactInfo.getStringFormattedValue().startsWith("/dev/")){
        	update = false;
        }
        Integer version = getVersion(artifactVersions, artifactInfo, update);
        artifact.addAnnotation("version", Integer.toString(version));
        return artifact;
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
    
    private Integer getVersion(Map<ArtifactInfo, Integer> versionMap, ArtifactInfo artifactInfo, boolean update){
    	Integer version = 0;
        if((version = versionMap.get(artifactInfo)) == null){
        	version = 0;
        	versionMap.put(artifactInfo, version);
        }else{
        	if(update){
        		version++;
        		versionMap.put(artifactInfo, version);
        	}
        }
        return version;
    }

    private void processForkClone(Map<String, String> eventData, SYSCALL syscall) {
        // fork() and clone() receive the following message(s):
        // - SYSCALL
        // - EOE

        String time = eventData.get("time");
        String oldPID = eventData.get("pid");
        String newPID = eventData.get("exit");
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
        wtb.addAnnotation("operation", "execve");
        wtb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wtb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wtb);
        addProcess(pid, newProcess);
        
        //add used edge to the paths in the event data
        String cwd = eventData.get("cwd");
        String path0 = eventData.get("path0");
        String path1 = eventData.get("path1"); 
        putUsedEdge(cwd, path0, newProcess, time, eventData.get("eventid"));
        putUsedEdge(cwd, path1, newProcess, time, eventData.get("eventid"));
        
        descriptors.unlinkDescriptors(pid);
    }
    
    private void putUsedEdge(String cwd, String path, Process process, String time, String eventId){
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
    	ArtifactInfo fileInfo = new FileInfo(path);
    	Artifact usedArtifact = createArtifact(fileInfo, false, null);
    	Used usedEdge = new Used(process, usedArtifact);
    	usedEdge.addAnnotation("time", time);
    	usedEdge.addAnnotation("operation", "read");
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
        String fd = eventData.get("exit");

        if (!path.startsWith("/")) {
            path = joinPaths(cwd, path);
        }
        
        checkProcessVertex(eventData, true, false);
        
        descriptors.addDescriptor(pid, fd, new FileInfo(path));

        if (!USE_READ_WRITE) {
            String flags = eventData.get("a1");
            Map<String, String> newData = new HashMap<>();
            newData.put("pid", pid);
            newData.put("a0", Integer.toHexString(Integer.parseInt(fd)));
            newData.put("time", eventData.get("time"));
            if (flags.charAt(flags.length() - 1) == '0') {
                // read
                processRead(newData, SYSCALL.READ);
            } else {
                // write
                processWrite(newData, SYSCALL.WRITE);
            }
        }
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
        	addMissingFD(pid, fd);
        }
        
        ArtifactInfo fileInfo = descriptors.getDescriptor(pid, fd);
        boolean put = !artifactVersions.containsKey(fileInfo);
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
        	addMissingFD(pid, fd);
        }

        ArtifactInfo fileInfo = descriptors.getDescriptor(pid, fd);
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
        ArtifactInfo fileInfo = null;

        if (syscall == SYSCALL.TRUNCATE) {
            fileInfo = new FileInfo(joinPaths(eventData.get("cwd"), eventData.get("path0")));
        } else if (syscall == SYSCALL.FTRUNCATE) {
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	addMissingFD(pid, fd);
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
        	addMissingFD(pid, fd);
        }
                
        ArtifactInfo fileInfo = descriptors.getDescriptor(pid, fd);
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
        
        ArtifactInfo srcFileInfo = new FileInfo(srcpath);
        ArtifactInfo dstFileInfo = new FileInfo(dstpath);

        boolean put = !artifactVersions.containsKey(srcFileInfo);
        Artifact srcVertex = createArtifact(srcFileInfo, false, null);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(getProcess(pid), srcVertex);
        used.addAnnotation("operation", "rename_read");
        used.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);

        Artifact dstVertex = createArtifact(dstFileInfo, true, null);
        putVertex(dstVertex);
        putVersionUpdateEdge(dstVertex, time, eventData.get("eventid"), pid);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, getProcess(pid));
        wgb.addAnnotation("operation", "rename_write");
        wgb.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
        putEdge(wgb);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("operation", "rename");
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
        
        ArtifactInfo srcFileInfo = new FileInfo(srcpath);
        ArtifactInfo dstFileInfo = new FileInfo(dstpath);

        boolean put = !artifactVersions.containsKey(srcFileInfo);
        Artifact srcVertex = createArtifact(srcFileInfo, false, null);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(getProcess(pid), srcVertex);
        used.addAnnotation("operation", syscallName + "_read");
        used.addAnnotation("time", time);
        addEventIdAndSourceAnnotationToEdge(used, eventData.get("eventid"), DEV_AUDIT);
        putEdge(used);

        Artifact dstVertex = createArtifact(dstFileInfo, true, null);
        putVertex(dstVertex);
        putVersionUpdateEdge(dstVertex, time, eventData.get("eventid"), pid);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, getProcess(pid));
        wgb.addAnnotation("operation", syscallName + "_write");
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
        ArtifactInfo fileInfo = null;
        if (syscall == SYSCALL.CHMOD) {
            fileInfo = new FileInfo(joinPaths(eventData.get("cwd"), eventData.get("path0")));
        } else if (syscall == SYSCALL.FCHMOD) {
            String fd = eventData.get("a0");
            
            if(descriptors.getDescriptor(pid, fd) == null){
            	addMissingFD(pid, fd); //add the missing fd and continue
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
        ArtifactInfo pipeInfo = new PipeInfo(fd0, fd1);
        descriptors.addDescriptor(pid, fd0, pipeInfo);
        descriptors.addDescriptor(pid, fd1, pipeInfo);
    }
    
    
    
    private void processNetfilterPacketEvent(Map<String, String> eventData){
//      Refer to the following link for protocol numbers
//    	http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
    	String protocol = eventData.get("proto");
    	
    	if(protocol.equals("6") || protocol.equals("17")){ // 6 is tcp and 17 is udp
    		String length = eventData.get("len");
        	
//        	hook = 1 is input, hook = 3 is forward
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
    	
    }
    
    private ArtifactInfo parseSaddr(String saddr){
    	ArtifactInfo artifactInfo = null;
    	if (saddr.charAt(1) == '2') {
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            artifactInfo = new SocketInfo(address, port);
        }else if(saddr.charAt(1) == 'A' || saddr.charAt(1) == 'a'){
        	String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
        	int oct1 = Integer.parseInt(saddr.substring(40, 42), 16);
        	int oct2 = Integer.parseInt(saddr.substring(42, 44), 16);
        	int oct3 = Integer.parseInt(saddr.substring(44, 46), 16);
        	int oct4 = Integer.parseInt(saddr.substring(46, 48), 16);
        	String address = String.format("::%s:%d.%d.%d.%d", saddr.substring(36, 40).toLowerCase(), oct1, oct2, oct3, oct4);
        	artifactInfo = new SocketInfo(address, port);
        }else if(saddr.charAt(1) == '1'){
        	String path = "";
        	for(int a = 4; a<saddr.length() && saddr.charAt(a) != '0'; a+=2){
        		char c = (char)(Integer.parseInt(saddr.substring(a, a+2), 16));
        		path += c;
        	}
        	if(!path.isEmpty()){
        		artifactInfo = new UnixSocketInfo(path);
        	}
        }
    	return artifactInfo;
    }

    private void processSocketCall(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        ArtifactInfo artifactInfo = parseSaddr(saddr);
        if (artifactInfo != null) {
            int callType = Integer.parseInt(eventData.get("socketcall_a0"));
            // socketcall number is derived from /usr/include/linux/net.h
            if (callType == 3) {
                // connect()
            	Artifact network = createArtifact(artifactInfo, false, SYSCALL.CONNECT);
            	putVertex(network);
                WasGeneratedBy wgb = new WasGeneratedBy(network, getProcess(pid));
                wgb.addAnnotation("time", time);
                wgb.addAnnotation("operation", "connect");
                addEventIdAndSourceAnnotationToEdge(wgb, eventData.get("eventid"), DEV_AUDIT);
                putEdge(wgb);
            } else if (callType == 5) {
                // accept()
            	Artifact network = createArtifact(artifactInfo, false, SYSCALL.ACCEPT);
            	putVertex(network);
                Used used = new Used(getProcess(pid), network);
                used.addAnnotation("time", time);
                used.addAnnotation("operation", "accept");
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
        ArtifactInfo artifactInfo = parseSaddr(saddr);
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
        ArtifactInfo artifactInfo = parseSaddr(saddr);
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
        	addMissingFD(pid, fd);
        }
       
        ArtifactInfo artifactInfo = descriptors.getDescriptor(pid, fd);
        
        long bytesRemaining = Long.parseLong(bytesSent);
        while(bytesRemaining > 0){
        	long currSize = networkLocationToBytesWrittenMap.get(artifactInfo);
        	long leftTillNext = MAX_BYTES_PER_NETWORK_ARTIFACT - currSize;
        	if(leftTillNext > bytesRemaining){
        		putSocketSendEdge(artifactInfo, syscall, time, String.valueOf(bytesRemaining), pid, eventData.get("eventid"));
        		networkLocationToBytesWrittenMap.put(artifactInfo, currSize + bytesRemaining);
        		bytesRemaining = 0;
        	}else{ //greater or equal
        		putSocketSendEdge(artifactInfo, syscall, time, String.valueOf(leftTillNext), pid, eventData.get("eventid"));
        		networkLocationToBytesWrittenMap.put(artifactInfo, 0L);
        		socketWriteVersions.put(artifactInfo, socketWriteVersions.get(artifactInfo) + 1);
        		//new version of network artifact for this path created. call putVertex here just once for that vertex.
        		putVertex(createArtifact(artifactInfo, false, syscall));
        		bytesRemaining -= leftTillNext;
        	}
        }
    }
    
    private void putSocketSendEdge(ArtifactInfo artifactInfo, SYSCALL syscall, String time, String size, String pid, String eventId){
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
        	addMissingFD(pid, fd);
        }
        
        ArtifactInfo artifactInfo = descriptors.getDescriptor(pid, fd);        
        long bytesRemaining = Long.parseLong(bytesReceived);
        while(bytesRemaining > 0){
        	long currSize = networkLocationToBytesReadMap.get(artifactInfo);
        	long leftTillNext = MAX_BYTES_PER_NETWORK_ARTIFACT - currSize;
        	if(leftTillNext > bytesRemaining){
        		putSocketRecvEdge(artifactInfo, syscall, time, String.valueOf(bytesRemaining), pid, eventData.get("eventid"));
        		networkLocationToBytesReadMap.put(artifactInfo, currSize + bytesRemaining);
        		bytesRemaining = 0;
        	}else{ //greater or equal
        		putSocketRecvEdge(artifactInfo, syscall, time, String.valueOf(leftTillNext), pid, eventData.get("eventid"));
        		networkLocationToBytesReadMap.put(artifactInfo, 0L);
        		socketReadVersions.put(artifactInfo, socketReadVersions.get(artifactInfo) + 1);
        		//new version of network artifact for this path created. call putVertex here just once for that vertex.
        		putVertex(createArtifact(artifactInfo, false, syscall));
        		bytesRemaining -= leftTillNext;
        	}
        }
    }
    
    private void putSocketRecvEdge(ArtifactInfo artifactInfo, SYSCALL syscall, String time, String size, String pid, String eventId){
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
        Process resultProcess = USE_PROCFS ? createProcessFromProcFS(pid) : null;
        if (resultProcess == null) {
            resultProcess = createProcessVertex(pid, eventData.get("ppid"), eventData.get("comm"), null, null, 
            		eventData.get("uid"), eventData.get("euid"), eventData.get("suid"), eventData.get("fsuid"),
            		eventData.get("gid"), eventData.get("egid"), eventData.get("sgid"), eventData.get("fsgid"), 
            		DEV_AUDIT, null);
        }
        if (link == true) {
            Map<String, ArtifactInfo> fds = getFileDescriptors(pid);
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
    
//  this function to be used always to create a process vertex
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
                String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(startTime));
                String stime = Long.toString(startTime);

                String name = nameline.split("\\s+")[1];
                String ppid = ppidline.split("\\s+")[1];
                cmdline = (cmdline == null) ? "" : cmdline.replace("\0", " ").replace("\"", "'").trim();

                // see for order of uid, euid, suid, fsiud: http://man7.org/linux/man-pages/man5/proc.5.html
                String gidTokens[] = gidline.split("//s+");
                String uidTokens[] = uidline.split("//s+");
                
                Process newProcess = createProcessVertex(pid, ppidline, nameline, null, null, 
                		uidTokens[0], uidTokens[1], uidTokens[2], uidTokens[3], 
                		gidTokens[0], gidTokens[1], gidTokens[2], gidTokens[3], 
                		PROC_FS, Long.toString(startTime));
                
                // newProcess.addAnnotation("starttime_unix", stime);
                // newProcess.addAnnotation("starttime_simple", stime_readable);
                // newProcess.addAnnotation("commandline", cmdline);
                return newProcess;
            } catch (IOException | NumberFormatException e) {
                logger.log(Level.WARNING, "unable to create process vertex for pid " + pid + " from /proc/", e);
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
    		logger.log(Level.SEVERE, "Failed to create version update edge between (" + newArtifact.toString() + ") and ("+oldArtifact.toString()+")" , e);
    		return;
    	}
    	oldArtifact.addAnnotation("version", String.valueOf(oldVersion));
    	WasDerivedFrom versionUpdate = new WasDerivedFrom(newArtifact, oldArtifact);
    	versionUpdate.addAnnotation("pid", pid);
    	versionUpdate.addAnnotation("operation", "update");
    	versionUpdate.addAnnotation("time", time);
    	addEventIdAndSourceAnnotationToEdge(versionUpdate, eventId, DEV_AUDIT);
    	putEdge(versionUpdate);
    }
    
    //for cases when open syscall wasn't gotten in the log for the fd being used.
    private void addMissingFD(String pid, String fd){
    	descriptors.addDescriptor(pid, fd, new UnknownInfo(pid, fd));
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
      
    //all records of any event are assumed to be placed contiguously in the file
    private class BatchReader{
    	private final Pattern event_line_pattern = Pattern.compile("msg=(audit[()0-9.:]+:)\\s*");
    	private BufferedReader br = null;
    	
    	private boolean EOF = false;
    	private String nextLine = null;
    	private String lastMsg = null;
    	
    	public BatchReader(BufferedReader br){
    		this.br = br;
    	}
    	
    	public String readLine() throws IOException{
    		if(EOF || nextLine != null){
    			String temp = nextLine;
    			nextLine = null;
    			return temp;
    		}
    		String line = br.readLine();
    		if(line == null){
    			EOF = true;
    			nextLine = null;
    			if(lastMsg != null){
    				return "type=EOE msg="+lastMsg;
    			}else{
    				return null;
    			}    			
    		} else {
    			Matcher matcher = event_line_pattern.matcher(line);
    			if(matcher.find()){
    				String msg = matcher.group(1);
    				
    				if(lastMsg == null){
    					lastMsg = msg;
    				}
    				
    				if(!msg.equals(lastMsg)){
    					String tempMsg = lastMsg;
    					lastMsg = msg;
    					nextLine = line;
    					return "type=EOE msg="+tempMsg;
    				}else{
    					return line;
    				}
    			}else{
    				return line;
    			}
    		}
    	}
    	
    	public void close() throws IOException{
    		br.close();
    	}
    
    }
}
