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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
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
    // Process map based on <pid, vertex> pairs
    private final Map<String, Process> processes = new HashMap<>();
    // File descriptor map based on <pid <fd, path>> pairs
    private final Map<String, Map<String, String>> fileDescriptors = new HashMap<>();
    // Event buffer map based on <audit_record_id <key, value>> pairs
    private final Map<String, Map<String, String>> eventBuffer = new HashMap<>();
    // File version map based on <path, version> pairs
    private final Map<String, Integer> fileVersions = new HashMap<>();
    private Thread eventProcessorThread = null;
    private String auditRules;

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
    private static final String PROC_INFO_SRC_KEY = "source",
            PROC_INFO_PROCFS = "/proc",
            PROC_INFO_AUDIT = "/dev/audit";
    
//    private String lastEventId = null;
    private Thread auditLogThread = null;

    private enum SYSCALL {

        FORK, CLONE, CHMOD, FCHMOD, SENDTO, SENDMSG, RECVFROM, RECVMSG, TRUNCATE, FTRUNCATE
    }
    
    private BufferedWriter dumpWriter = null;
    
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
        	        	
        	ARCH_32BIT = "32".equals(args.get("arch")) ? true : "64".equals(args.get("arch")) ? false : null;
        	
        	if(ARCH_32BIT == null){
        		logger.log(Level.SEVERE, "Must specify whether the system on which log was collected was 32 bit or 64 bit");
        		return false;
        	}
        	
        	auditLogThread = new Thread(new Runnable(){
    			public void run(){
    				BatchReader inputLogBatchReader = null;
    				java.lang.Process ausearchProcess = null;
    	        	try{
    	        		ausearchProcess = Runtime.getRuntime().exec("ausearch --input " + inputAuditLogFile);
    	        		inputLogBatchReader = new BatchReader(new BufferedReader(new InputStreamReader(ausearchProcess.getInputStream())));
    	        		String line = null;
    	        		while(!shutdown && (line = inputLogBatchReader.readLine()) != null){
    	        			parseEventLine(line);
    	        		}
						while(!shutdown){
							//wait for user to shut it down
						}
    	        	}catch(Exception e){
    	        		logger.log(Level.SEVERE, "Failed to read input audit log file", e);
    	        	}finally{
    	        		try{
    	        			if(ausearchProcess != null){
    	        				ausearchProcess.destroy();
    	        			}
    	        		}catch(Exception e){
    	        			logger.log(Level.SEVERE, "Failed to destroy ausearch process.", e);
    	        		}
    	        		try{
    	        			if(inputLogBatchReader != null){
    	        				inputLogBatchReader.close();
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
	                    Process processVertex = createProcess(currentPID);
	                    processes.put(currentPID, processVertex);
	                    Process parentVertex = processes.get(processVertex.getAnnotation("ppid"));
	                    putVertex(processVertex);
	                    if (parentVertex != null) {
	                        WasTriggeredBy wtb = new WasTriggeredBy(processVertex, parentVertex);
	                        putEdge(wtb);
	                    }
	
	                    // Get existing file descriptors for this process
	                    Map<String, String> descriptors = getFileDescriptors(currentPID);
	                    if (descriptors != null) {
	                        fileDescriptors.put(currentPID, descriptors);
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
	                    + "-F success=1 " + ignorePids.toString();
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

    private Map<String, String> getFileDescriptors(String pid) {
        // Check if this pid exists in the /proc/ filesystem
        if (!(new java.io.File("/proc/" + pid).exists())) {
            return null;
        }
        // Get file descriptors for this process from /proc/
        try {
            java.lang.Process fdInfo = Runtime.getRuntime().exec("ls -l /proc/" + pid + "/fd");
            BufferedReader fdReader = new BufferedReader(new InputStreamReader(fdInfo.getInputStream()));
            Map<String, String> descriptors = new HashMap<>();
            while (true) {
                String line = fdReader.readLine();
                if (line == null) {
                    break;
                }
                String tokens[] = line.split("\\s+");
                int toks = tokens.length;
                if (toks > 3) {
                    String fd = tokens[toks - 3];
                    String location = tokens[toks - 1];
                    descriptors.put(fd, location);
                }
            }
            fdReader.close();
            return descriptors;
        } catch (Exception e) {
            logger.log(Level.WARNING, "unable to retrieve file descriptors for pid: " + pid, e);
            return null;
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
            }
            
            if (type.equals("SYSCALL")) {
                Map<String, String> eventData = parseKeyValPairs(messageData);
                eventData.put("time", time);
            	eventBuffer.get(eventId).putAll(eventData);
            } else if (type.equals("EOE")) {
                if (ARCH_32BIT) {
                    finishEvent32(eventId);
                } else {
                    finishEvent64(eventId);
                }
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

    private void finishEvent32(String eventId) {
        try {
            // System call numbers are derived from:
            // https://android.googlesource.com/platform/bionic/+/android-4.1.1_r1/libc/SYSCALLS.TXT
            // TODO: Update the calls to make them linux specific.

            if (!eventBuffer.containsKey(eventId)) {
                logger.log(Level.WARNING, "EOE for eventID {0} received with no prior Event Info", new Object[]{eventId});
                return;
            }
            Map<String, String> eventData = eventBuffer.get(eventId);
            int syscall = Integer.parseInt(eventData.get("syscall"));
            
            if("no".equals(eventData.get("success"))){ //in case the audit log is being read from a user provided file.
            	eventBuffer.remove(eventId);
            	return;
            }

            switch (syscall) {
                case 2: // fork()
                case 190: // vfork()
                    processForkClone(eventData, SYSCALL.FORK);
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

                case 3: // read()
                case 145: // readv()
                case 180: // pread64()
                	if(USE_READ_WRITE){
                		processRead(eventData);
                	}
                    break;

                case 4: // write()
                case 146: // writev()
                case 181: // pwrite64()
                	if(USE_READ_WRITE){
                		processWrite(eventData);
                	}
                    break;

                case 9: // link()
                case 83: // symlink()
                    processLink(eventData);
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
                case 208: // setresuid()
                case 213: // setuid()
                    processSetuid(eventData);
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

                case 290: // sendto()
                	if(USE_SOCK_SEND_RCV){
                		processSend(eventData, SYSCALL.SENDTO);
                	}
                    break;
                case 296: // sendmsg()
                	if(USE_SOCK_SEND_RCV){
                		processSend(eventData, SYSCALL.SENDMSG);
                	}
                	break;
                case 292: // recvfrom()
                	if(USE_SOCK_SEND_RCV){
                		processRecv(eventData, SYSCALL.RECVFROM);
                	}
                	break;
                case 297: // recvmsg()
                	if(USE_SOCK_SEND_RCV){
                		processRecv(eventData, SYSCALL.RECVMSG);
                	}
                	break;
                case 283: // connect()
                    processConnect(eventData);
                    break;
                case 285: // accept()
                    processAccept(eventData);
                    break;
                case 281: // socket()
                    break;

                default:
                    break;
            }
            eventBuffer.remove(eventId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "error processing finish syscall event with eventid '"+eventId+"'", e);
        }
    }

    private void finishEvent64(String eventId) {
        try {
            // System call numbers are derived from:
            // http://blog.rchapman.org/post/36801038863/linux-system-call-table-for-x86-64

            if (!eventBuffer.containsKey(eventId)) {
                logger.log(Level.WARNING, "EOE for eventID {0} received with no prior Event Info", new Object[]{eventId});
                return;
            }
            Map<String, String> eventData = eventBuffer.get(eventId);
            int syscall = Integer.parseInt(eventData.get("syscall"));

            if("no".equals(eventData.get("success"))){ //in case the audit log is being read from a user provided file.
            	eventBuffer.remove(eventId);
            	return;
            }
            
            switch (syscall) {
                case 57: // fork()
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

                case 0: // read()
                case 19: // readv()
                case 17: // pread64()
                	if(USE_READ_WRITE){
                		processRead(eventData);
                	}
                    break;

                case 1: // write()
                case 20: // writev()
                case 18: // pwrite64()
                	if(USE_READ_WRITE){
                		processWrite(eventData);
                	}
                    break;

                case 86: // link()
                case 88: // symlink()
                    processLink(eventData);
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
                case 117: // setresuid()
                case 105: // setuid()
                    processSetuid(eventData);
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
                case 44: // sendto()
                	if(USE_SOCK_SEND_RCV){
                		processSend(eventData, SYSCALL.SENDTO);
                	}
                	break;
                case 46: // sendmsg()
                	if(USE_SOCK_SEND_RCV){
                		processSend(eventData, SYSCALL.SENDMSG);
                	}
                	break;
                case 45: // recvfrom()
                	if(USE_SOCK_SEND_RCV){
                		processRecv(eventData, SYSCALL.RECVFROM);
                	}
                	break;
                case 47: // recvmsg()
                	if(USE_SOCK_SEND_RCV){
                		processRecv(eventData, SYSCALL.RECVMSG);
                	}
                	break;
                case 42: // connect()
                    processConnect(eventData);
                    break;
                case 43: // accept()
                    processAccept(eventData);
                    break;
                // ////////////////////////////////////////////////////////////////
                case 41: // socket()
                    break;

                default:
                    break;
            }
            eventBuffer.remove(eventId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "error processing finish syscall event with eventid '"+eventId+"'", e);
        }
    }

    private Artifact createNetworkArtifact(String path, boolean update) {
        Artifact artifact = createFileArtifact(path, update);
        artifact.addAnnotation("subtype", "network");
        return artifact;
    }

    private Artifact createFileArtifact(String path, boolean update) {
        Artifact artifact = new Artifact();
        artifact.addAnnotation("subtype", "file");
        path = path.replace("//", "/");
        artifact.addAnnotation("path", path);
        String[] filename = path.split("/");
        if (filename.length > 0) {
            artifact.addAnnotation("filename", filename[filename.length - 1]);
        }
        int version = fileVersions.containsKey(path) ? fileVersions.get(path) : 0;
        if (update && path.startsWith("/") && !path.startsWith("/dev/")) {
            version++;
        }
        artifact.addAnnotation("version", Integer.toString(version));
        fileVersions.put(path, version);
        return artifact;
    }

    private void processForkClone(Map<String, String> eventData, SYSCALL syscall) {
        // fork() and clone() receive the following message(s):
        // - SYSCALL
        // - EOE

        String time = eventData.get("time");
        String oldPID = eventData.get("pid");
        String newPID = eventData.get("exit");
        checkProcessVertex(eventData, true, false);

        Process newProcess = new Process();
        String uid = String.format("%s\t%s\t%s\t%s", eventData.get("uid"), eventData.get("euid"), eventData.get("suid"), eventData.get("fsuid"));
        String gid = String.format("%s\t%s\t%s\t%s", eventData.get("gid"), eventData.get("egid"), eventData.get("sgid"), eventData.get("fsgid"));
        newProcess.addAnnotation("pid", newPID);
        newProcess.addAnnotation("ppid", oldPID);
        newProcess.addAnnotation("uid", uid);
        newProcess.addAnnotation("gid", gid);
        newProcess.addAnnotation(PROC_INFO_SRC_KEY, PROC_INFO_AUDIT);

        processes.put(newPID, newProcess);
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, processes.get(oldPID));
        wtb.addAnnotation("operation", syscall.name().toLowerCase());
        wtb.addAnnotation("time", time);
        putEdge(wtb); // Copy file descriptors from old process to new one
        if (fileDescriptors.containsKey(oldPID)) {
            Map<String, String> newfds = new HashMap<>();
            for (Map.Entry<String, String> entry : fileDescriptors.get(oldPID).entrySet()) {
                newfds.put(entry.getKey(), entry.getValue());
            }
            fileDescriptors.put(newPID, newfds);
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
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, processes.get(pid));
        wtb.addAnnotation("operation", "execve");
        wtb.addAnnotation("time", time);
        putEdge(wtb);
        processes.put(pid, newProcess);
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
        checkProcessVertex(eventData, true, false);

        if (!path.startsWith("/")) {
            path = joinPaths(cwd, path);
        }
        addDescriptor(pid, fd, path);

        if (!USE_READ_WRITE) {
            String flags = eventData.get("a1");
            Map<String, String> newData = new HashMap<>();
            newData.put("pid", pid);
            newData.put("a0", new BigInteger(fd, 16).toString());
            newData.put("time", eventData.get("time"));
            if (flags.charAt(flags.length() - 1) == '0') {
                // read
                processRead(newData);
            } else {
                // write
                processWrite(newData);
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
        removeDescriptor(pid, fd);
    }

    private void processRead(Map<String, String> eventData) {
        // read() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        String bytesRead = eventData.get("exit");
        checkProcessVertex(eventData, true, false);

        String fd = new BigInteger(hexFD, 16).toString();
        String time = eventData.get("time");
        
        if(!(fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd))){
        	addMissingFD(pid, fd);
        }

        String path = fileDescriptors.get(pid).get(fd);
        boolean put = !fileVersions.containsKey(path);
        Artifact vertex = createFileArtifact(path, false);
        if (put) {
            putVertex(vertex);
        }
        Used used = new Used(processes.get(pid), vertex);
        used.addAnnotation("operation", "read");
        used.addAnnotation("time", time);
        used.addAnnotation("size", bytesRead);
        putEdge(used);
        
    }

    private void processWrite(Map<String, String> eventData) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = new BigInteger(hexFD, 16).toString();
        String time = eventData.get("time");
        String bytesWritten = eventData.get("exit");

        if(!(fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd))){
        	addMissingFD(pid, fd);
        }
        
        String path = fileDescriptors.get(pid).get(fd);
        Artifact vertex = createFileArtifact(path, true);
        putVertex(vertex);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
        wgb.addAnnotation("operation", "write");
        wgb.addAnnotation("time", time);
        wgb.addAnnotation("size", bytesWritten);
        putEdge(wgb);
    }

    private void processTruncate(Map<String, String> eventData, SYSCALL syscall) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String time = eventData.get("time");
        String path = null;

        if (syscall == SYSCALL.TRUNCATE) {
            path = joinPaths(eventData.get("cwd"), eventData.get("path0"));
        } else if (syscall == SYSCALL.FTRUNCATE) {
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            
            if(!(fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd))){
            	addMissingFD(pid, fd);
            }
            
            path = fileDescriptors.get(pid).get(fd);
        }

        Artifact vertex = createFileArtifact(path, true);
        putVertex(vertex);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
        wgb.addAnnotation("operation", syscall.name().toLowerCase());
        wgb.addAnnotation("time", time);
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
        
        if(!(fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd))){
        	addMissingFD(pid, fd);
        }
        
        String path = fileDescriptors.get(pid).get(fd);
        fileDescriptors.get(pid).put(newFD, path);
    }

    private void processSetuid(Map<String, String> eventData) {
        // setuid() receives the following message(s):
        // - SYSCALL
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);
        Process newProcess = checkProcessVertex(eventData, false, false);
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, processes.get(pid));
        wtb.addAnnotation("operation", "setuid");
        wtb.addAnnotation("time", time);
        putEdge(wtb);
        processes.put(pid, newProcess);
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

        boolean put = !fileVersions.containsKey(srcpath);
        Artifact srcVertex = createFileArtifact(srcpath, false);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(processes.get(pid), srcVertex);
        used.addAnnotation("operation", "read");
        used.addAnnotation("time", time);
        putEdge(used);

        Artifact dstVertex = createFileArtifact(dstpath, true);
        putVertex(dstVertex);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, processes.get(pid));
        wgb.addAnnotation("operation", "write");
        wgb.addAnnotation("time", time);
        putEdge(wgb);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("operation", "rename");
        wdf.addAnnotation("time", time);
        putEdge(wdf);
    }

    private void processLink(Map<String, String> eventData) {
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

        String srcpath = joinPaths(cwd, eventData.get("path0"));
        String dstpath = joinPaths(cwd, eventData.get("path2"));

        boolean put = !fileVersions.containsKey(srcpath);
        Artifact srcVertex = createFileArtifact(srcpath, false);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(processes.get(pid), srcVertex);
        used.addAnnotation("operation", "read");
        used.addAnnotation("time", time);
        putEdge(used);

        Artifact dstVertex = createFileArtifact(dstpath, true);
        putVertex(dstVertex);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, processes.get(pid));
        wgb.addAnnotation("operation", "write");
        wgb.addAnnotation("time", time);
        putEdge(wgb);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("operation", "link");
        wdf.addAnnotation("time", time);
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
        String path = null;
        if (syscall == SYSCALL.CHMOD) {
            path = joinPaths(eventData.get("cwd"), eventData.get("path0"));
        } else if (syscall == SYSCALL.FCHMOD) {
            String fd = eventData.get("a0");
            
            if(!(fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd))){
            	addMissingFD(pid, fd); //add the missing fd and continue
            }
            
            path = fileDescriptors.get(pid).get(fd);
        }
        Artifact vertex = createFileArtifact(path, true);
        putVertex(vertex);

        WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
        wgb.addAnnotation("operation", syscall.name().toLowerCase());
        wgb.addAnnotation("mode", mode);
        wgb.addAnnotation("time", time);
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
        String location = "pipe:[" + fd0 + "-" + fd1 + "]";
        addDescriptor(pid, fd0, location);
        addDescriptor(pid, fd1, location);
    }

    private void processSocketCall(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String location = null;
        String saddr = eventData.get("saddr");
        // continue if this is an AF_INET socket address
        if (saddr.charAt(1) == '2') {
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            location = String.format("address:%s, port:%s", address, port);
        }
        if (location != null) {
            Artifact network = createNetworkArtifact(location, true);
            putVertex(network);
            int callType = Integer.parseInt(eventData.get("socketcall_a0"));
            // socketcall number is derived from /usr/include/linux/net.h
            if (callType == 3) {
                // connect()
                WasGeneratedBy wgb = new WasGeneratedBy(network, processes.get(pid));
                wgb.addAnnotation("time", time);
                wgb.addAnnotation("operation", "connect");
                putEdge(wgb);
            } else if (callType == 5) {
                // accept()
                Used used = new Used(processes.get(pid), network);
                used.addAnnotation("time", time);
                used.addAnnotation("operation", "accept");
                putEdge(used);
            }
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            addDescriptor(pid, fd, location);
        }
    }

    private void processConnect(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String location = null;
        String saddr = eventData.get("saddr");
        // continue if this is an AF_INET socket address
        if (saddr.charAt(1) == '2') {
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            location = String.format("address:%s, port:%s", address, port);
        }
        if (location != null) {
            Artifact network = createNetworkArtifact(location, true);
            putVertex(network);
            WasGeneratedBy wgb = new WasGeneratedBy(network, processes.get(pid));
            wgb.addAnnotation("time", time);
            wgb.addAnnotation("operation", "connect");
            putEdge(wgb);
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            addDescriptor(pid, fd, location);
        }
    }

    private void processAccept(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String location = null;
        String saddr = eventData.get("saddr");
        // continue if this is an AF_INET socket address
        if (saddr.charAt(1) == '2') {
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            location = String.format("address:%s, port:%s", address, port);
        }
        if (location != null) {
            Artifact network = createNetworkArtifact(location, false);
            putVertex(network);
            Used used = new Used(processes.get(pid), network);
            used.addAnnotation("time", time);
            used.addAnnotation("operation", "accept");
            putEdge(used);
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = new BigInteger(hexFD, 16).toString();
            addDescriptor(pid, fd, location);
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
        
        if(!(fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd))){
        	addMissingFD(pid, fd);
        }

        String path = fileDescriptors.get(pid).get(fd);
        Artifact vertex = createNetworkArtifact(path, true);
        putVertex(vertex);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
        wgb.addAnnotation("operation", syscall.name().toLowerCase());
        wgb.addAnnotation("time", time);
        wgb.addAnnotation("size", bytesSent);
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

        if(!(fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd))){
        	addMissingFD(pid, fd);
        }
        
        String path = fileDescriptors.get(pid).get(fd);
        Artifact vertex = createNetworkArtifact(path, false);
        putVertex(vertex);
        Used used = new Used(processes.get(pid), vertex);
        used.addAnnotation("operation", syscall.name().toLowerCase());
        used.addAnnotation("time", time);
        used.addAnnotation("size", bytesReceived);
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

    private void addDescriptor(String pid, String fd, String path) {
        if (fileDescriptors.containsKey(pid)) {
            fileDescriptors.get(pid).put(fd, path);
        } else {
            Map<String, String> fdMap = new HashMap<>();
            fdMap.put(fd, path);
            fileDescriptors.put(pid, fdMap);
        }
    }

    private void removeDescriptor(String pid, String fd) {
        if (fileDescriptors.containsKey(pid)) {
            fileDescriptors.get(pid).remove(fd);
        } else {
            logger.log(Level.WARNING, "remove descriptor: fd {0} not found for pid {1}", new Object[]{fd, pid});
        }
    }

    private Process checkProcessVertex(Map<String, String> eventData, boolean link, boolean refresh) {
        String pid = eventData.get("pid");
        if (processes.containsKey(pid) && !refresh) {
            return processes.get(pid);
        }
        Process resultProcess = USE_PROCFS ? createProcess(pid) : null;
        if (resultProcess == null) {
            resultProcess = new Process();
            String ppid = eventData.get("ppid");
            String uid = String.format("%s\t%s\t%s\t%s", eventData.get("uid"), eventData.get("euid"), eventData.get("suid"), eventData.get("fsuid"));
            String gid = String.format("%s\t%s\t%s\t%s", eventData.get("gid"), eventData.get("egid"), eventData.get("sgid"), eventData.get("fsgid"));
            resultProcess.addAnnotation("name", eventData.get("comm"));
            resultProcess.addAnnotation("pid", pid);
            resultProcess.addAnnotation("ppid", ppid);
            resultProcess.addAnnotation("uid", uid);
            resultProcess.addAnnotation("gid", gid);
            resultProcess.addAnnotation(PROC_INFO_SRC_KEY, PROC_INFO_AUDIT);
        }
        if (link == true) {
            Map<String, String> fds = getFileDescriptors(pid);
            if (fds != null) {
                fileDescriptors.put(pid, fds);
            }
            putVertex(resultProcess);
            processes.put(pid, resultProcess);
            String ppid = resultProcess.getAnnotation("ppid");
            if (processes.containsKey(ppid)) {
                WasTriggeredBy wtb = new WasTriggeredBy(resultProcess, processes.get(ppid));
                putEdge(wtb);
            }
        }
        return resultProcess;
    }

    private Process createProcess(String pid) {
        // Check if this pid exists in the /proc/ filesystem
        if ((new java.io.File("/proc/" + pid).exists())) {
            // The process vertex is created using the proc filesystem.
            try {
                Process newProcess = new Process();
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
                long starttime = boottime + elapsedtime;
                String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(starttime));
                String stime = Long.toString(starttime);

                String name = nameline.split("\\s+")[1];
                String ppid = ppidline.split("\\s+")[1];
                String uid = uidline.split("\\s+", 2)[1];
                String gid = gidline.split("\\s+", 2)[1];
                cmdline = (cmdline == null) ? "" : cmdline.replace("\0", " ").replace("\"", "'").trim();

                newProcess.addAnnotation("name", name);
                newProcess.addAnnotation("pid", pid);
                newProcess.addAnnotation("ppid", ppid);
                newProcess.addAnnotation("uid", uid);
                newProcess.addAnnotation("gid", gid);
                // newProcess.addAnnotation("starttime_unix", stime);
                // newProcess.addAnnotation("starttime_simple", stime_readable);
                // newProcess.addAnnotation("commandline", cmdline);

                newProcess.addAnnotation(PROC_INFO_SRC_KEY, PROC_INFO_PROCFS);
                return newProcess;
            } catch (IOException | NumberFormatException e) {
                logger.log(Level.WARNING, "unable to create process vertex for pid " + pid + " from /proc/", e);
                return null;
            }
        } else {
            return null;
        }
    }
    
    //for cases when open syscall wasn't gotten in the log for the fd being used.
    public void addMissingFD(String pid, String fd){
    	addDescriptor(pid, fd, "/unknown/"+pid+"_"+fd);
    }
    
    private class BatchReader{
    	
    	private final Pattern event_line_pattern = Pattern.compile("msg=audit\\(([0-9\\.]+)\\:([0-9]+)\\):\\s*");
    	private BufferedReader br = null;
    	
    	private Map<Long, AuditEvent> auditEventsMap = new HashMap<Long, AuditEvent>();
    	private LinkedList<AuditEvent> auditEventsQ = new LinkedList<AuditEvent>();
    	
    	private AuditEvent nextBatch = null;
    	
    	public BatchReader(BufferedReader br){
    		this.br = br;
    	}
    	
    	public String readLine() throws IOException{
    		if(nextBatch == null){ 
	    		String line = null;
	    		String lastTimestamp = null;
	    		while((line = br.readLine()) != null){
	    			Matcher matcher = event_line_pattern.matcher(line);
	    			if(matcher.find()){
	    				String timestamp = matcher.group(1);
	    				Long eventId = Long.parseLong(matcher.group(2));

	    				if(lastTimestamp == null){
	    					lastTimestamp = timestamp;
	    				}
	    				
	    				if(!lastTimestamp.equals(timestamp)){ //new
	    					nextBatch = new AuditEvent(eventId, timestamp);
	    					nextBatch.addLine(line);
	    					auditEventsQ.addAll(auditEventsMap.values());
	    					Collections.sort(auditEventsQ, new Comparator<AuditEvent>(){
	    						public int compare(AuditEvent a, AuditEvent b){
	    							return a.getEventId().compareTo(b.getEventId());
	    						}
	    					});
	    					return getLine();
	    				}else{//current
	    					if(auditEventsMap.get(eventId) == null){
	    						auditEventsMap.put(eventId, new AuditEvent(eventId, timestamp));
		    				}
		    				
	    					auditEventsMap.get(eventId).addLine(line);
	    				}
    				
	    			}
	    		}
	    		//end of file reached
	    		nextBatch = new AuditEvent(null, null);
	    		auditEventsQ.addAll(auditEventsMap.values());
				Collections.sort(auditEventsQ, new Comparator<AuditEvent>(){
					public int compare(AuditEvent a, AuditEvent b){
						return a.getEventId().compareTo(b.getEventId());
					}
				});
	    		return getLine();
    		}else{
    			return getLine();
    		}
    	}
    	
    	private String getLine(){
    		if(auditEventsQ.size() == 1 && auditEventsQ.getFirst().getLines().isEmpty()){
        		String eoeLine = "type=EOE msg=audit("+auditEventsQ.getFirst().getTimestamp()+":"+auditEventsQ.getFirst().getEventId()+"):";
        		auditEventsMap.clear();
        		auditEventsQ.clear();
        		if(nextBatch.getEventId() == null){//means end of file was reached in readLine function
        			
        		}else{
	        		auditEventsMap.put(nextBatch.getEventId(), nextBatch);
	        		nextBatch = null;
        		}
				return eoeLine;
    		}else if(auditEventsQ.isEmpty()){//end of file reached and last batch completely written out
    			return null;
    		}else {
    			if(auditEventsQ.getFirst().getLines().isEmpty()){
    				AuditEvent auditEvent = auditEventsQ.removeFirst();
    				auditEventsMap.remove(auditEvent.getEventId());
    				String eoeLine = "type=EOE msg=audit("+auditEvent.getTimestamp()+":"+auditEvent.getEventId()+"):";
    				return eoeLine;
    			}else{
    				return auditEventsQ.getFirst().getLines().removeFirst();
    			}
    		}
    	} 
    	
    	public void close() throws IOException{
    		br.close();
    	}
    
    	
    	private class AuditEvent{
    		private Long eventId;
    		private String timestamp;
    		private LinkedList<String> lines;
    		public AuditEvent(Long eventId, String timestamp){
    			this.eventId = eventId;
    			this.timestamp = timestamp;
    			this.lines = new LinkedList<String>();
    		}
    		public Long getEventId(){
    			return eventId;
    		}
    		public String getTimestamp(){
    			return timestamp;
    		}
    		public LinkedList<String> getLines(){
    			return lines;
    		}
    		public void addLine(String line){
    			lines.add(line);
    		}
    	}
    }
}
