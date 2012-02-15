/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2010 SRI International

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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spade.core.AbstractReporter;
import spade.edge.opm.*;
import spade.vertex.custom.File;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class AndroidAudit extends AbstractReporter {

    // True means open and close will be used to check for reads/writes and actual reads/writes will not be monitored. false means reads/writes will be monitored
    private boolean OpenCloseSemanticsOn = true;
    private int currentAuditId = 0;
    // String used as a buffer to hold the current event stream
    private String temporaryEventString = "";
    // ???
    private String hostname;
    // Hash tables
    // Currently running known processes
    private HashMap<String, Process> processes = new HashMap<String, Process>();
    // Mapping from process,file decriptior pair to the actual file. this needs to be kept due to lack of information about file in read/write events
    private HashMap<String, Artifact> processFdIsFile = new HashMap<String, Artifact>();
    // Mapping from from name to the opm artifact vertex
    private HashMap<String, Artifact> fileNameHasArtifact = new HashMap<String, Artifact>();
    // Buffer to hold events from the event stream that haven't recieved an EOE(end of event) event yet and are still not finished.
    private HashMap<Integer, ArrayList<HashMap<String, String>>> unfinishedEvents = new HashMap<Integer, ArrayList<HashMap<String, String>>>();
    // Agents we've seen
    private HashSet<Agent> cachedAgents = new HashSet<Agent>();
    public java.lang.Process socketToPipe;
    protected BufferedReader eventReader;
    private final PrintStream errorStream = System.err;
    private static final Logger logger = Logger.getLogger(AndroidAudit.class.getName());
    private HashSet<String> agent_fields =
            new HashSet<String>(
            Arrays.asList("uid,egid,arch,auid,sgid,fsgid,suid,euid,node,fsuid,gid".split("[\\s,]+")));
    private volatile boolean shutdown = false;

    public AndroidAudit() {
        // Generate syscalls mapping table
        Matcher m = pattern_key_val.matcher(syscalls_mapping_str);
        while (m.find()) {
            syscalls_mapping.put(Integer.parseInt(m.group(1)), m.group(2));
        }
    }

    @Override
    public boolean launch(String arguments) {

        // Environment stuff
        try {
            InetAddress addr = InetAddress.getLocalHost();
            // Get IP Address
            byte[] ipAddr = addr.getAddress();
            // Get hostname
            hostname = addr.getHostName();

        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, null, e);
        }
        startParsing();

        return true;
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        try {
            Runtime.getRuntime().exec("auditctl -D").waitFor();
        } catch (Exception ex) {
            Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }

    //this is currently the way to supply lines to the processLine function. This will be replaced with the socket code
    public void startParsing() {

        try {

            Runtime.getRuntime().exec("auditctl -D").waitFor();
            Runtime.getRuntime().exec("auditctl -a exit,always -S clone -S execve -S exit_group -S open -S write -S close -S fork").waitFor();
            Runtime.getRuntime().exec("auditd").waitFor();
            java.lang.Process pipeprocess = Runtime.getRuntime().exec("/system/bin/spade-audit");
            eventReader = new BufferedReader(new InputStreamReader(pipeprocess.getInputStream()));

            Runnable eventThread = new Runnable() {

                public void run() {
                    try {
                        while (!shutdown) {
                            if (eventReader.ready()) {
                                String line = eventReader.readLine();
                                if (line != null && line.length() != 0) {
                                    processInputLine(line);
                                }
                            }
                        }
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(eventThread).start();

        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }


    }

    protected void processInputLine(String inputLine) {

        //we still need to finish a mini event
        temporaryEventString = temporaryEventString + inputLine;

        //now check to see if the string contains two types of mini events. If it does we need to package off the first one and make a new item


        Pattern pattern = Pattern.compile("type=");
        Matcher matcher = pattern.matcher(temporaryEventString);

        int countOfSyscalls = 0;
        int truncateIndexStart;//variables to hold index for start of an event in the event stream
        int truncateIndexEnd;//var	iables to hold index for end of an event in the event stream

        truncateIndexStart = truncateIndexEnd = 0;


        while (matcher.find()) {
            countOfSyscalls++;
            if (countOfSyscalls == 1) {
                truncateIndexStart = matcher.start();
            }
            if (countOfSyscalls == 2) {
                truncateIndexEnd = matcher.start();
            }
        }

        if (countOfSyscalls > 1) {
            //there are two mini events in the current string. package off the first one and send it to the state machine (processEvent function)
            processEvent(temporaryEventString.substring(truncateIndexStart, truncateIndexEnd));
            temporaryEventString = temporaryEventString.substring(truncateIndexEnd, temporaryEventString.length());

        }

        if (countOfSyscalls > 2) {
            //in case we get a huge line with multiple mini events (>2) in it we recursively handle it
            processInputLine("");	//needs to be rethought. this doesn't work due to the null check
        }



    }
    Pattern pattern_key_val = Pattern.compile("((?:\\\\.|[^=\\s]+)*)=(\"(?:\\\\.|[^\"\\\\]+)*\"|(?:\\\\.|[^\\s\"\\\\]+)*)");
    Pattern pattern_auditid = Pattern.compile("audit\\([^:]+:([0-9]+)\\):");
    Pattern pattern_timestamp = Pattern.compile("audit\\(([^:])+:[0-9]+\\):");
    String syscalls_mapping_str = "2=fork 3=read 4=write 5=open 6=close 9=link 10=unlink 11=execve "
            + "14=mknod 38=rename 41=dup 42=pipe 63=dup2 83=symlink 92=truncate "
            + "93=ftruncate 102=socketcall 120=clone 145=readv 146=writev "
            + "190=vfork 252=exit_group";
    HashMap<Integer, String> syscalls_mapping = new HashMap<Integer, String>();

    //the process line event passes discrete events to this functions. we need to recognize type of event (these are actually mini events) and package them
    /**
     * This function takes mini-events in the form of a string and identifies
     * them as a starting mini-event or as a continuing mini-event by checking
     * for the audit id of the event that has been passed via the lidAudit
     * system. It adds it to a {@link HashMap} as a new event or as a continuing
     * event. If the mini-event is an EOE or end-of-event type of event then it
     * takes the chain of mini-events with the same audit id and passes them all
     * on to processFinishedEvent.
     *
     * @param event A string from the processLine function that represent one
     * mini-event from the lid audit system.
     *
     *
     * @return void
     *
     */
    private void processEvent(String event) {

        try {

            // Convert the event into key value pairs and store them in a hash map
            Matcher m = pattern_key_val.matcher(event);
            HashMap<String, String> fields = new HashMap<String, String>();
            while (m.find()) {
                fields.put(m.group(1), m.group(2));
            }

            // Get current audit id & timestamp
            String msg = fields.get("msg");
            m = pattern_auditid.matcher(fields.get("msg"));
            if (m.find()) {
                fields.put("auditId", m.group(1));
            } else {
                assert (false);
            }

            m = pattern_timestamp.matcher(fields.get("msg"));
            if (m.find()) {
                fields.put("time", m.group(1));
            } else {
                assert (false);
            }

            //////////////////////////////////////////////////////////this part processes all mini events///////////////////////////////////////////////

            String eventType = fields.get("type");

            if (eventType.compareTo("SYSCALL") == 0) {

                int eventId = Integer.parseInt(fields.get("syscall"));;

                if (syscalls_mapping.containsKey(eventId)) {
                    // New syscall means we need to create new unfinished event
                    ArrayList<HashMap<String, String>> currentEvent = new ArrayList<HashMap<String, String>>();
                    String currentEventType = syscalls_mapping.get(eventId);
                    currentEvent.add(fields);
                    unfinishedEvents.put(currentAuditId, currentEvent);
                } else {
                    // TODO: Handle an unseen/unimplemented sys call here
                }

            } //the mini event is not a system call so it must be a mini event that is related to it or a garbage mini event
            else {
                //check to make sure that the mini event is not a garbage event that we should throw away. if not then add it to its appropriate audit
                //event in the hashmap
                if (unfinishedEvents.containsKey(currentAuditId)) {

                    int givenEventNum = Integer.parseInt(getSyscallEvent(unfinishedEvents.get(currentAuditId)).get("syscall")); // TODO: syscall might be in any event of the array, not just zero
                    if (fields.get("type").compareTo("EOE") == 0) {
                        //if end of audit finish the event
                        processFinishedEvent(unfinishedEvents.get(currentAuditId), givenEventNum);
                        unfinishedEvents.remove(currentAuditId);
                    } //otherwise add the mini-event
                    else {
                        unfinishedEvents.get(currentAuditId).add(fields);
                    }
                }
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, "Problem process event: " + event);
            logger.log(Level.SEVERE, null, exception);
        }

    }

    //this function gets the finished event from the processEvent (all mini events pertaining to an event are packaged togeter)
    /**
     * This function recieves a chain of mini-events that are packaged together
     * and represent one system call. It identifies the type of the system call
     * and passes the event on to the handler for that particular system call.
     *
     * @param finishedEvent This is the Arraylist that contains mini-events for
     * one system call.
     *
     *
     * @param givenEventNum this is the system call Id for the passed event.
     * This is used to do a decision about which handler the system call should
     * be passed to.
     *
     *
     * @return void
     *
     */
    private void processFinishedEvent(ArrayList<HashMap<String, String>> finishedEvent, int givenEventNum) {

        //in this function we will be passed finished events. which means opens will come along with their cwds and paths and so on
        ///////////////////////////////////////////
        //ADD NEW CALL HANDLERS HERE (this can later be divied up into seperate functions so that you dont have to check again///////////////
        ///////////////////////////////////////////

        // TODO: deep check without toString()
        // TODO: Comment the functionality
        if (finishedEvent.toString().contains("socketToPipe")) {
            return;
        }

        try {
            switch (givenEventNum) {
                // Invoke appropriate handler
                case 2:
                    //fork
                    processFork(finishedEvent);
                    break;

                case 3:
                    //read
                    processRead(finishedEvent);
                    break;

                case 4:
                    //write
                    processWrite(finishedEvent);
                    break;

                case 5:
                    //open
                    processOpen(finishedEvent);
                    break;

                case 6:
                    //close
                    processClose(finishedEvent);
                    break;

                case 9:
                    //link
                    processLink(finishedEvent);
                    break;

                case 10:
                    //unlink
                    processUnLink(finishedEvent);
                    break;

                case 11:
                    //execve
                    processExecve(finishedEvent);
                    break;

                case 14:
                    //mknod
                    processMknod(finishedEvent);
                    break;

                case 38:
                    //rename
                    processRename(finishedEvent);
                    break;

                case 41:
                    //duplicate(dup)
                    processDup(finishedEvent);
                    break;

                case 42:
                    //pipe
                    processPipe(finishedEvent);
                    break;
                case 63:
                    //duplicate2(dup2)
                    processDup2(finishedEvent);
                    break;
                case 83:
                    //symlink
                    processSymlink(finishedEvent);
                    break;
                case 92:
                    //truncate
                    processTruncate(finishedEvent);
                    break;
                case 93:
                    //ftruncate
                    processFTruncate(finishedEvent);
                    break;
                case 102:
                    //socketcall
                    processSocketcall(finishedEvent);
                    break;
                case 120:
                    //clone
                    processClone(finishedEvent);
                    break;
                case 145:
                    //readv
                    processReadv(finishedEvent);
                    break;
                case 146:
                    //Writev
                    processWritev(finishedEvent);
                    break;

                case 190:
                    //vfork
                    processVFork(finishedEvent);
                    break;
                case 252:
                    //exitgroup
                    processExitGroup(finishedEvent);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            // Indicates code level issue
            Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE, null, e);
            assert (false);
        }
        /*
         * try {
         *
         * // Take the appropriate action by calling the function // done using
         * reflection: any event type is handled by // calling a function with
         * "process" as its prefix // e.g. unlink event is handled by
         * processUnlink function String event_str =
         * syscalls_mapping.get(givenEventNum); String str_process_func =
         * "process" + event_str.substring(0,1).toUpperCase() +
         * event_str.substring(1);
         *
         * ArrayList<HashMap<String, String>> obj = new
         * ArrayList<HashMap<String, String>>(); Method process =
         * this.getClass().getMethod(str_process_func, obj.getClass());
         * process.invoke(this, finishedEvent);
         *
         * }
         * catch(java.lang.NoSuchMethodException e) { // Ignore if not
         * implemented but log it logger.info("processFinishedEvent: No method
         * to handle event of type " + event_str); }
         * catch(java.lang.IllegalArgumentException e) { // Indicates code level
         * issue
         * Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE,
         * null, e); assert(false); } catch(Exception e) { // Indicates code
         * level issue
         * Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE,
         * null, e); assert(false); }
         */
    }

    ///////////////////////////////////////////////////////////////////
    ////////INDIVIDUAL HANDLER FUNCTIONS FOR EACH SYSTEM CALL//////////
    ///////////////////////////////////////////////////////////////////
    /**
     * This function takes a Fork system call and converts it into the
     * appropriate OPM specification objects. It also does house keeping
     * regarding new vertices in the OPM model and keeps track of whether the
     * process vertex generated by this function is a new one of has been seen
     * before. If it generates new OPM vertices or edges these are sent to the
     * filter for further processing.
     *
     * @param inputFork The chain of mini-events representing a fork system call
     *
     * @return void
     *
     */
    private void processFork(ArrayList<HashMap<String, String>> eventsChain) {
        //process the fork

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        //get the annotations for both processes
        Process cloningProcess = null;
        HashMap<String, String> processAnnotations = getProcessInformation(fields);
        HashMap<String, String> processAnnotationsChild = getProcessInformation(fields);
        HashMap<String, String> agentAnnotations = seperateAgentFromProcess(fields);
        HashMap<String, String> agentAnnotationsChild = seperateAgentFromProcess(fields);

        // check if the parent process is already there in our table
        if (processes.containsKey(processAnnotations.get("pid"))) {
            cloningProcess = processes.get(processAnnotations.get("pid"));
        } else {
            cloningProcess = new Process(processAnnotations);
            processes.put(processAnnotations.get("pid"), cloningProcess);
            putVertex(cloningProcess);

            Agent agent = getAgent(agentAnnotations);
            if (agent == null) {
                agent = new Agent(agentAnnotations);
                putVertex(agent);
                cachedAgents.add(agent);
            }
            WasControlledBy wcb = new WasControlledBy(cloningProcess, agent);
            putEdge(wcb);
        }

        // Child Process
        processAnnotationsChild.put("ppid", processAnnotations.get("pid"));
        processAnnotationsChild.put("pid", processAnnotations.get("exit"));

        Process clonedProcess = new Process(processAnnotationsChild);
        processes.put(processAnnotationsChild.get("pid"), clonedProcess);
        putVertex(clonedProcess);
        WasTriggeredBy clone = new WasTriggeredBy(clonedProcess, cloningProcess);
        putEdge(clone);

        // Get or create child agent
        Agent agentChild = getAgent(agentAnnotationsChild);
        if (agentChild == null) {
            agentChild = new Agent(agentAnnotationsChild);
            putVertex(agentChild);
            cachedAgents.add(agentChild);
        }
        WasControlledBy wcb2 = new WasControlledBy(clonedProcess, agentChild);
        putEdge(wcb2);

    }

    /**
     * This function takes a Read system call and converts it into the
     * appropriate OPM specification objects. If it generates new OPM vertices
     * or edges these are sent to the filter for further processing.
     *
     * @param inputRead The chain of mini-events representing a read system call
     *
     * @return void
     *
     */
    private void processRead(ArrayList<HashMap<String, String>> eventsChain) {
        //process the read.

        try {
            HashMap<String, String> fields = eventsChain.get(0);

            String pid = fields.get("pid");
            String fd = fields.get("a0");

            Process readingProcess = processes.get(pid);
            Artifact fileRead = processFdIsFile.get(pid + "," + fd);

            //make a read edge

            Used readEdge = new Used(readingProcess, fileRead);

            //Do nothing here are we are not doing read monitoring at the moment
            // TODO: Do we need some action here?


        } catch (Exception e) {
            // TODO: log here
            Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE, null, e);
        }

    }

    /**
     * This function takes a write system call and converts it into the
     * appropriate OPM specification objects. If it generates new OPM vertices
     * or edges these are sent to the filter for further processing.
     *
     * @param inputWrite The chain of mini-events representing a write system
     * call
     *
     * @return void
     *
     */
    private void processWrite(ArrayList<HashMap<String, String>> eventsChain) {
        //process the write

        try {
            HashMap<String, String> fields = eventsChain.get(0);

            String pid = fields.get("pid");
            String fd = fields.get("a0");

            Process writingProcess = processes.get(pid);

            //get the written file
            Artifact fileWritten = processFdIsFile.get(pid + "," + fd);
            if (fileWritten.getAnnotation("version") == null) {
                fileWritten.addAnnotation("version", "0");
            } else {
                int v = Integer.parseInt(fileWritten.getAnnotation("version"));
                fileWritten.addAnnotation("version", Integer.toString(v++));
            }

            WasGeneratedBy writeEdge = new WasGeneratedBy(fileWritten, writingProcess);

            putVertex(fileWritten);
            putEdge(writeEdge);

        } catch (Exception e) {
            Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    /**
     * This function takes an open system call and converts it into the
     * appropriate OPM specification objects. If it generates new OPM vertices
     * or edges these are sent to the filter for further processing. If
     * OpenCloseSemantics flag is set then this function is used to generate
     * read events and optimize the system by obviating the need for monitoring
     * actual read system calls.
     *
     * @param inputOpen The chain of mini-events representing a open system call
     *
     * @return void
     *
     */
    //////////////////////////////////////////////////////
    private void processOpen(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        try {
            //make a process

            Process openingProcess = processes.get(fields.get("pid"));

            Artifact fileOpened;

            //get the file info
            HashMap<String, String> fileAnnotations = getFileInformation(eventsChain);

            String filePath = fileAnnotations.get("filename");
            String fd = fields.get("exit");

            //now see if other people have this file open already
            //if yes fetch pointer to that file and put it in fileOpened reference.

            // TODO: Get or create file artifact
            if (fileNameHasArtifact.containsKey(filePath)) {
                fileOpened = fileNameHasArtifact.get(filePath);
                //add edge from process to file
                processFdIsFile.put(fields.get("pid") + "," + fd, fileOpened);
                //file name to artifact edge is already there
            } //if no then make a new file. This file has never been opened before in this session
            else {

                fileAnnotations.put("fullpath", filePath);
                fileAnnotations.put("filename", filePath);
                fileOpened = new File();
                
                for (Map.Entry<String, String> currentEntry : fileAnnotations.entrySet()) {
                    String key = currentEntry.getKey();
                    String value = currentEntry.getValue();
                    fileOpened.addAnnotation(key, value);
                }

                /*
                 * ??? ArrayList<String> processesThatOpenFiles = new
                 * ArrayList<String>(); processesThatOpenFiles.add(keyPair);
                 */

                //add edge from process to file
                processFdIsFile.put(fields.get("pid") + "," + fd, fileOpened);


                // TODO: Confirm this:
                //putVertex(openingProcess);

                //add edge from file name to file artifact
                fileNameHasArtifact.put(filePath, fileOpened);

            }

            /*
             * Kernel values for open flags O_RDONLY 00000000 O_WRONLY 00000001
             * O_RDWR 00000002
             *
             * system call looks like open(file path, flags, mode) so we get
             * flags in a1 parameter from lib audit
             *
             * so we only convert an open to a read only when the last digit of
             * a1 is either 0 or 2. If it is 1 then it was opened for write only
             *
             */

            if (OpenCloseSemanticsOn == false) {
                //no provenance in open call
            } else {
                //translate open to read call if needed

                if (fields.get("a1").endsWith("0") || fields.get("a1").endsWith("2")) {
                    //this means file possibly opened for reading. Make and send a read edge along
                    Used u = new Used(openingProcess, fileOpened);

                    if (fileOpened.getAnnotation("filename").contains("libAuditPipe")
                            || fileOpened.getAnnotation("filename").contains("libAudit")) {
                        // Prevent self logging
                        return;
                    } else if (openingProcess.getAnnotation("exe").endsWith("eclipse") || openingProcess.getAnnotation("exe").endsWith("java")) {
                        return;
                    }

                    putVertex(fileOpened);
                    putEdge(u);
                }
            }

        } catch (Exception exception) {
            Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE, null, exception);
        }

    }

    //////////////////////////////////////////////////////
    private void processClose(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        //now proceed
        try {

            //get the opening process

            String pid = fields.get("pid");
            Process closingProcess = processes.get(pid);

            String fd = fields.get("exit");
            String keyPair = pid + "," + fd;

            //get the closed file
            Artifact fileClosed = processFdIsFile.get(keyPair);

            // delete edge from process to file
            processFdIsFile.remove(keyPair);

            // no provenance in close
            // TODO: Why?
            // TODO: Dawood, do something here?
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    //////////////////////////////////////////////////////
    private void processLink(ArrayList<HashMap<String, String>> eventsChain) {
        // TODO: Implement this
    }

    //////////////////////////////////////////////////////
    private void processUnLink(ArrayList<HashMap<String, String>> eventsChain) {
        // TODO: Implement this
    }

    //////////////////////////////////////////////////////
    private void processExecve(ArrayList<HashMap<String, String>> eventsChain) {
        // TODO: This needs thorough testing

        //process the execve

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        HashMap<String, String> processAnnotations = getProcessInformation(fields);
        HashMap<String, String> agentAnnotations = seperateAgentFromProcess(fields);
        String commandLine = getExecveCommandLine(eventsChain.get(1));;

        //make the edges
        WasTriggeredBy execve;
        WasControlledBy wcb;

        processAnnotations.put("commandline", commandLine);
        processAnnotations.put("cwd", eventsChain.get(2).get("cwd"));

        // Update the process in table if exists
        String key = processAnnotations.get("pid");//+processAnnotations.get("exe");
        if (processes.containsKey(key)) {

            Process oldProcess = processes.get(key);
            //remove older process
            processes.remove(key);
            //add new process
            Process newProcess = new Process(processAnnotations);

            Agent agent = getAgent(agentAnnotations);


            if (agent == null) {
                agent = new Agent(agentAnnotations);
                putVertex(agent);
                cachedAgents.add(agent);
            } else {
                //agent is already in cache so dont send it. and dont make new vertex.
            }


            //make edges
            wcb = new WasControlledBy(newProcess, agent);
            execve = new WasTriggeredBy(newProcess, oldProcess);


            //add process
            putVertex(newProcess);


            //add both edges
            putEdge(wcb);
            putEdge(execve);


            //add process to table
            processes.put(key, newProcess);


            //extra processing to add the file artifact for the binary
            Artifact binaryFile = new File();
            binaryFile.addAnnotation("filename", processAnnotations.get("exe"));
            binaryFile.addAnnotation("version", "0");
            putVertex(binaryFile);
            Used u = new Used(oldProcess, binaryFile);
            putEdge(u);


        } //process not in table. so been seen the first time. create new process vertex
        else {
            Process newProcess = new Process(processAnnotations);

            //send this new process


            Agent agent = getAgent(agentAnnotations);

            if (agent == null) {
                //don't send vertex
                agent = new Agent(agentAnnotations);
                putVertex(agent);
                cachedAgents.add(agent);
            } else {
                //agent is already in cache so dont send it. and dont make new vertex.
            }


            //make the edge
            wcb = new WasControlledBy(newProcess, agent);

            //send process
            putVertex(newProcess);

            //send agent edge
            putEdge(wcb);

            // add to our table
            processes.put(newProcess.getAnnotation("pid"), newProcess);
        }



    }

    //////////////////////////////////////////////////////
    private void processMknod(ArrayList<HashMap<String, String>> eventsChain) {
        // TODO: Implement this?
    }

    //////////////////////////////////////////////////////(incomplete)
    private void processRename(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        try {

            //get the renaming process

            String pid = fields.get("pid");
            Process renamingProcess = processes.get(pid);
            Artifact renamedFile;
            Artifact newFile;
            WasDerivedFrom renameEdgeFileToFile;
            WasGeneratedBy renameEdgeProcessToFile;

            //get the renamed file path name

            //String renamedFilePathname = "";


            String cwd = eventsChain.get(1).get("cwd");
            String path1 = eventsChain.get(4).get("name");
            String path2 = eventsChain.get(5).get("name");

            // Make paths Canonical
            if (path1.charAt(0) == '/') {
                path1 = getCanonicalPath(path1);
            } else {
                path1 = getCanonicalPath(cwd + "/" + path1);
            }

            if (path2.charAt(0) == '/') {
                path2 = getCanonicalPath(path2);
            } else {
                path2 = getCanonicalPath(cwd + "/" + path2);
            }


            // TODO: Confirm this: might not be a good idea when files are renamed to and forth back
            // check if you already have renamed file in file table. if not then huzzah don't do anything
            if (!fileNameHasArtifact.containsKey(path1)) {
                //do nothing
            } else {
                // get renamed file
                renamedFile = fileNameHasArtifact.get(path1);

                // make new file Vertex
                newFile = new File();
                newFile.addAnnotation("fullpath", path2);
                newFile.addAnnotation("filename", path2);

                // copy all the annotations
                newFile.addAnnotation("cwd", cwd);
                for (String i : "inode,dev,mode,ouid,ogid,rdev".split(",")) {
                    newFile.addAnnotation(i, eventsChain.get(5).get(i));
                }

                //make file to file edge
                renameEdgeFileToFile = new WasDerivedFrom(newFile, renamedFile);
                renameEdgeProcessToFile = new WasGeneratedBy(newFile, renamingProcess);

                // TODO: Dawood: Should we delete old file and all its edges?

                //delete old file and all its edges. also delete that files filepath to file vertex link

                // TODO: Implement this
                //putVertex(newFile);
            }


        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }


    }

    //////////////////////////////////////////////////////
    private void processDup(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        //add a process,fd->filename,version edge in the table

        String pid = fields.get("pid");
        String oldFd = fields.get("a0");
        String newFd = fields.get("a1");
        String keyPair = pid + "," + oldFd;
        String newKeyPair = pid + "," + newFd;

        // TODO: Fix this!
        if (processFdIsFile.containsKey(keyPair)) {

            Artifact file = processFdIsFile.get(keyPair);
            //String fullpath = file.getAnnotation("fullpath");

            //add process to file edge
            processFdIsFile.put(newKeyPair, file);

            //add file to process edge(not needed)

        }
        // *** End <todo>

    }

    //////////////////////////////////////////////////////
    private void processPipe(ArrayList<HashMap<String, String>> eventsChain) {
        //process the pipe
        // TODO: Implement this!
    }

    //////////////////////////////////////////////////////
    private void processDup2(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        //proceed

        //add a process,fd->filename,version edge in the table

        String pid = fields.get("pid");
        String oldFd = fields.get("a0");
        String newFd = fields.get("a1");
        String keyPair = pid + "," + oldFd;
        String newKeyPair = pid + "," + newFd;

        // TODO: fix it
        if (processFdIsFile.containsKey(keyPair)) {

            Artifact file = processFdIsFile.get(keyPair);
            //String fullpath = file.getAnnotation("fullpath");

            //add process to file edge
            processFdIsFile.put(newKeyPair, file);

            //add file to process edge(not needed)
        }
    }

    //////////////////////////////////////////////////////
    private void processSymlink(ArrayList<HashMap<String, String>> eventsChain) {
        //process the Symlink
        // TODO: Implement this
    }

    //////////////////////////////////////////////////////
    private void processTruncate(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        try {

            //get the writing process
            String pid = fields.get("pid");
            Process writingProcess = processes.get(pid);

            String fd = fields.get("a0"); // TODO: verify that fd is passed
            String keyPair = pid + "," + fd;

            //get the written file
            Artifact fileWritten = processFdIsFile.get(keyPair);
            if (fileWritten.getAnnotation("version") == null) {
                fileWritten.addAnnotation("version", "0");
            } else {
                int v = Integer.parseInt(fileWritten.getAnnotation("version"));
                fileWritten.addAnnotation("version", Integer.toString(v++));
            }

            //make a read edge

            WasGeneratedBy writeEdge = new WasGeneratedBy(fileWritten, writingProcess);


            //put new vertex for new version of file if ReadWrite bunching not on
            putVertex(fileWritten);

            //send the edge
            putEdge(writeEdge);


        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    //////////////////////////////////////////////////////
    private void processFTruncate(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);
        try {
            //get the writing process
            String pid = fields.get("pid");
            Process writingProcess = processes.get(pid);

            String fd = fields.get("a0");
            String keyPair = pid + "," + fd;

            //get the written file
            Artifact fileWritten = processFdIsFile.get(keyPair);
            if (fileWritten.getAnnotation("version") == null) {
                fileWritten.addAnnotation("version", "0");
            } else {
                int v = Integer.parseInt(fileWritten.getAnnotation("version"));
                fileWritten.addAnnotation("version", Integer.toString(v++));
            }

            //make a read edge

            //WasGeneratedBy writeEdge = new WasGeneratedBy(fileWritten,writingProcess);
            WasGeneratedBy writeEdge = new WasGeneratedBy(fileWritten, writingProcess);


            //put new vertex for new version of file if ReadWrite bunching not on
            putVertex(fileWritten);

            //send the edge
            putEdge(writeEdge);


        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }


    }

    //////////////////////////////////////////////////////
    private void processSocketcall(ArrayList<HashMap<String, String>> eventsChain) {
        // TODO: Implement this
    }

    //////////////////////////////////////////////////////
    private void processClone(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        //String key="";
        Process cloningProcess;
        HashMap<String, String> annotations = getProcessInformation(eventsChain.get(0));
        HashMap<String, String> annotationsNew = getProcessInformation(eventsChain.get(0));
        HashMap<String, String> agentAnnotations = seperateAgentFromProcess(annotations);
        HashMap<String, String> agentAnnotations2 = seperateAgentFromProcess(annotationsNew);

        if (processes.containsKey(annotations.get("pid"))) {
            //cloning process already in table
            cloningProcess = processes.get(annotations.get("pid"));
        } else {
            cloningProcess = new Process(annotations);

            //send the cloning Process and its agent vertex and its wascontrolledby edge
            Agent agent = getAgent(agentAnnotations);
            if (agent == null) {

                agent = new Agent(agentAnnotations);
                putVertex(agent);
                cachedAgents.add(agent);
            } else {
                //agent is already in cache so dont send it. and dont make new vertex.
            }

            //now send send process
            putVertex(cloningProcess);

            //now send wcb edge
            WasControlledBy wcb = new WasControlledBy(cloningProcess, agent);
            putEdge(wcb);

            processes.put(annotations.get("pid"), cloningProcess);

        }

        //move pid to ppid
        annotationsNew.remove("ppid");
        annotationsNew.put("ppid", annotations.get("pid"));

        //move exit value to pid as that is child pid
        annotationsNew.remove("pid");
        annotationsNew.put("pid", annotations.get("exit"));

        Process clonedProcess = new Process(annotationsNew);
        //create a new process (add it to processes) and a clone edge
        processes.put(annotationsNew.get("pid"), clonedProcess);

        putVertex(clonedProcess);
        WasTriggeredBy clone = new WasTriggeredBy(clonedProcess, cloningProcess);
        putEdge(clone);

        // Agents
        Agent agent2 = getAgent(agentAnnotations2);
        if (agent2 == null) {
            agent2 = new Agent(agentAnnotations2);
            putVertex(agent2);
            cachedAgents.add(agent2);
        }

        WasControlledBy wcb2 = new WasControlledBy(clonedProcess, agent2);
        putEdge(wcb2);
    }

    /////////////////////////////////////////////////////////
    private void processReadv(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        //get the reading process
        String pid = fields.get("pid");
        Process readingProcess = processes.get(pid);

        String fd = fields.get("a0");
        String keyPair = pid + "," + fd;

        //get the read file
        Artifact fileRead = processFdIsFile.get(keyPair);

        //make a read edge

        Used readEdge = new Used(readingProcess, fileRead);

        //send the file,process and read edge

        // TODO: *sigh* implement this!


    }

    /////////////////////////////////////////////////////////
    private void processWritev(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        //get the writting process
        String pid = fields.get("pid");
        Process writingProcess = processes.get(pid);

        String fd = fields.get("a0");
        String keyPair = pid + "," + fd;

        //get the written file
        Artifact fileWritten = processFdIsFile.get(keyPair);
        if (fileWritten.getAnnotation("version") == null) {
            fileWritten.addAnnotation("version", "0");
        } else {
            int v = Integer.parseInt(fileWritten.getAnnotation("version"));
            fileWritten.addAnnotation("version", Integer.toString(v++));
        }

        //make a read edge

        WasGeneratedBy writeEdge = new WasGeneratedBy(fileWritten, writingProcess);

        putVertex(fileWritten);
        putEdge(writeEdge);
    }

    //////////////////////////////////////////////////////
    private void processVFork(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        if (fields.get("success").equals("no")) {
            return;
        }

        //String key="";
        boolean sendCloningProcess = false;
        Process cloningProcess = null;
        HashMap<String, String> processAnnotations = getProcessInformation(eventsChain.get(0));
        HashMap<String, String> processAnnotationsChild = getProcessInformation(eventsChain.get(0));
        HashMap<String, String> agentAnnotations = seperateAgentFromProcess(processAnnotations);
        HashMap<String, String> agentAnnotationsChild = seperateAgentFromProcess(processAnnotationsChild);

        if (processes.containsKey(processAnnotations.get("pid"))) {
            //cloning process already in table
            cloningProcess = processes.get(processAnnotations.get("pid"));
        } else {
            cloningProcess = new Process(processAnnotations);
            processes.put(processAnnotations.get("pid"), cloningProcess);
            putVertex(cloningProcess);

            //send the cloning Process and its agent vertex and its wascontrolledby edge
            Agent agent = getAgent(agentAnnotations);
            if (agent == null) {
                agent = new Agent(agentAnnotations);
                putVertex(agent);
                cachedAgents.add(agent);
            }
            WasControlledBy wcb = new WasControlledBy(cloningProcess, agent);
            putEdge(wcb);
        }


        // Child Process
        processAnnotationsChild.put("ppid", processAnnotations.get("pid"));
        processAnnotationsChild.put("pid", processAnnotations.get("exit"));

        Process clonedProcess = new Process(processAnnotationsChild);
        processes.put(processAnnotationsChild.get("pid"), clonedProcess);
        putVertex(clonedProcess);

        WasTriggeredBy clone = new WasTriggeredBy(clonedProcess, cloningProcess);
        putEdge(clone);

        // Get or create child agent
        Agent agent2 = getAgent(agentAnnotationsChild);
        if (agent2 == null) {

            agent2 = new Agent(agentAnnotationsChild);
            putVertex(agent2);
            cachedAgents.add(agent2);
        }

        WasControlledBy wcb2 = new WasControlledBy(clonedProcess, agent2);
        putEdge(wcb2);
    }

    //////////////////////////////////////////////////////
    private void processExitGroup(ArrayList<HashMap<String, String>> eventsChain) {

        HashMap<String, String> fields = eventsChain.get(0);

        //remove the process from the existing processes table
        if (processes.containsKey(fields.get("pid"))) {
            processes.remove(fields.get("pid") + fields.get("comm")); // TODO: comm?
        }
        //possibly send information to the kernel that this process has finished
    }

    private HashMap<String, String> getProcessInformation(HashMap<String, String> fields) {
        HashMap<String, String> ret = new HashMap<String, String>();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String k = entry.getKey();
            if (!agent_fields.contains(k)) {
                ret.put(k, entry.getValue());
            }
        }
        return ret;
    }

    private String getExecveCommandLine(HashMap<String, String> fields) {

        int argc = Integer.parseInt(fields.get("argc"));
        String commandLine = "";

        for (int i = 1; i < argc; ++i) {
            commandLine += fields.get("a" + Integer.toString(i)) + " ";
        }
        return commandLine.trim();

    }

    private HashMap<String, String> getFileInformation(ArrayList<HashMap<String, String>> eventChain) {

        HashMap<String, String> fileAnnotations = new HashMap<String, String>();

        try {

            fileAnnotations.put("cwd", eventChain.get(1).get("cwd"));
            fileAnnotations.put("time", eventChain.get(1).get("time"));
            fileAnnotations.put("auditId", eventChain.get(1).get("auditId"));

            //get file path
            int index_chain_filename = eventChain.size() - 1;
            assert (index_chain_filename > 2);

            HashMap<String, String> fields = eventChain.get(index_chain_filename);

            for (String i : "name,inode,dev,mode,ouid,ogid,rdev".split(",")) {
                fileAnnotations.put(i, fields.get(i));
            }

            // Convert filepath
            String filename = fileAnnotations.get("name");
            if (filename.startsWith("\"") && filename.endsWith("\"")) {
                filename = filename.substring(1, filename.length() - 1);
            }
            if (!filename.startsWith("/")) {
                java.io.File getFilePath = new java.io.File(fileAnnotations.get("cwd").substring(1, fileAnnotations.get("cwd").length() - 1) + "/" + filename);
                filename = getFilePath.getCanonicalPath();
            }
            fileAnnotations.put("filename", filename);


        } catch (Exception ioe) {
            Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE, null, ioe);
        }

        return fileAnnotations;

    }

    // TODO: Rename this
    private HashMap<String, String> seperateAgentFromProcess(HashMap<String, String> processAnnotations) {
        HashMap<String, String> agentAnnotations = new HashMap<String, String>();
        for (String i : agent_fields) {
            agentAnnotations.put(i, processAnnotations.get(i));
        }
        return agentAnnotations;
    }

    // TODO: Vertify with Dawood, the criteria of agent lookup
    private Agent getAgent(HashMap<String, String> annotations) {
        // TODO: This has O(n) lookup time ... check how it can be reduced
        for (String field : "uid,auid,suid".split(",")) {
            for (Agent agent : cachedAgents) {
                if (agent.getAnnotations().containsKey(field)
                        && agent.getAnnotations().get(field).equals(annotations.get(field))) {
                    return agent;
                }
            }
        }
        return null;
    }

    /*
     * returns the canonical path
     */
    private String getCanonicalPath(String path) {
        try {
            assert (path.startsWith("/"));
            java.io.File f = new java.io.File(path);
            String path1 = f.getCanonicalPath();
            return path1;
        } catch (IOException e) {
            assert (false);
        }
        return null;
    }

    /**
     * Gets the syscall mini-event from the events chain
     *
     * @param eventsChain - an array of mini-events
     *
     * @return HashMap<String, String>
     *
     *
     */
    private HashMap<String, String> getSyscallEvent(ArrayList<HashMap<String, String>> eventsChain) {
        for (HashMap<String, String> e : eventsChain) {
            if (e.get("type").equals("SYSCALL")) {
                return e;
            }
        }
        return null;
    }
}
