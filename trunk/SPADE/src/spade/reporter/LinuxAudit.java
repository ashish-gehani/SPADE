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
import java.io.File;
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
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class LinuxAudit extends AbstractReporter {

    private boolean OpenCloseSemanticsOn; //true means open and close will be used to check for reads/writes and actual reads/writes will not be monitored. false means reads/writes will be monitored
    private int currentAuditId;
    private String currentEventType;//current event type
    private String temporaryEventString;//string used as a buffer to hold the current event stream
    private String hostname;
    private HashMap<String, Process> processes;//a hash table to hold all the currently running processes that we know of
    private HashMap<String, Artifact> processFdIsFile;//mapping from process,file decriptior pair to the actual file. this needs to be kept due to lack of information about file in read/write events
    private HashMap<String, Artifact> fileNameHasArtifact;//mapping from from name to the opm artifact vertex
    private HashMap<Integer, ArrayList<ArrayList<String>>> unfinishedEvents;//buffer to hold events from the event stream that haven't recieved an EOE(end of event) event yet and are still not finished.
    private HashSet<Agent> cachedAgents;
    public java.lang.Process socketToPipe;
    private BufferedReader eventReader;
    private final PrintStream errorStream = System.err;

    @Override
    public boolean launch(String arguments) {
        OpenCloseSemanticsOn = true;

        fileNameHasArtifact = new HashMap<String, Artifact>();
        processes = new HashMap<String, Process>();
        processFdIsFile = new HashMap<String, Artifact>();
        currentAuditId = 0;
        temporaryEventString = "";
        currentEventType = "";
        unfinishedEvents = new HashMap<Integer, ArrayList<ArrayList<String>>>();
        cachedAgents = new HashSet<Agent>();

        try {
            InetAddress addr = InetAddress.getLocalHost();
            // Get IP Address
            byte[] ipAddr = addr.getAddress();
            // Get hostname
            hostname = addr.getHostName();

        } catch (UnknownHostException e) {
        }
        startParsing();

        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }

    //this is currently the way to supply lines to the processLine function. This will be replaced with the socket code
    public void startParsing() {

        try {

            Runtime.getRuntime().exec("sudo auditctl -D").waitFor();
            Runtime.getRuntime().exec("sudo auditctl -a exit,always -S clone -S execve -S exit_group -S open -S write -S close -S fork").waitFor();
            String[] cmd = {"/bin/sh", "-c", "sudo ./spade/reporter/spadeLinuxAudit"};
            java.lang.Process pipeprocess = Runtime.getRuntime().exec(cmd);
            eventReader = new BufferedReader(new InputStreamReader(pipeprocess.getInputStream()));
            eventReader.readLine();

            Runnable eventThread = new Runnable() {

                public void run() {
                    try {
                        while (true) {
                            if (eventReader.ready()) {
                                String line = eventReader.readLine();
                                if (line != null) {
                                    processInputLine(line);
                                }
                            }
                        }
                    } catch (Exception exception) {
                        Logger.getLogger(LinuxAudit.class.getName()).log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(eventThread).start();

            /*
             * while (true) { // this statement reads the line from the file and
             * print it to // the console. String inputLine = br.readLine(); if
             * (inputLine != null) { //System.out.println(inputLine);
             * processInputLine(inputLine); }
             *
             * }
             *
             */

            // dispose all the resources after using them.
            //br.close();


        } catch (Exception exception) {
            Logger.getLogger(LinuxAudit.class.getName()).log(Level.SEVERE, null, exception);
        }


    }

    private void processInputLine(String inputLine) {

        //just in case we get something empty so that we don't throw an exception
        if (inputLine.length() == 0) {
            return;
        } else {

            //we still need to finish a mini event
            temporaryEventString = temporaryEventString + inputLine;

            //now check to see if the string contains two types of mini events. If it does we need to package off the first one and make a new item


            Pattern pattern = Pattern.compile("node=" + hostname + " type=");
            Matcher matcher = pattern.matcher(temporaryEventString);

            int countOfSyscalls = 0;
            int truncateIndexStart;//variables to hold index for start of an event in the event stream
            int truncateIndexEnd;//variables to hold index for end of an event in the event stream

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


    }

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
        //we have gotten a mini event. Do whatever processing here

        //aims are to identify the type of mini event and event id and take the appropriate actions. we also need audit it. this works like a state machine



        StringTokenizer st = new StringTokenizer(event);
        ArrayList<String> fields = new ArrayList<String>();

        while (st.hasMoreTokens()) {
            fields.add(st.nextToken());
        }
        //if you want the node information to be available as well you can add it back here
        String node = fields.get(0);
        fields.remove(0);
        fields.add(node);

        // all the mini event information is nicely stored in an arraylist. now check id of mini event and see if it is a new audit event

        //first of all we need to get the audit id. everything is synchronized based on this. This is stored in the 1st place but we need some
        //some cleverness to get it out as the format is as follows msg=audit(129392123.321:90) where 90 is audit id.

        String auditString = fields.get(1).substring(0, fields.get(1).length() - 2);
        StringTokenizer st2 = new StringTokenizer(auditString, ":");
        ArrayList<String> auditStringParts = new ArrayList<String>();
        while (st2.hasMoreTokens()) {
            auditStringParts.add(st2.nextToken());
        }
        currentAuditId = Integer.parseInt(auditStringParts.get(1));

        //ok now we have the current auditId. if it is present in the map we chain the mini event. if it is not then we check if it is a needed system call
        //and if it then we create a new entry in hashmap otherwise we throw it away as it useless

        try {


            //////////////////////////////////////////////////////////this part processes all mini events///////////////////////////////////////////////

            String eventType = fields.get(0).substring(5, fields.get(0).length());
            int eventId = -1;
            if (eventType.compareTo("SYSCALL") == 0) {
                eventId = Integer.parseInt(fields.get(3).substring(8, fields.get(3).length()));

                //new syscall means we need to create new unfinished event
                currentEventType = "";
                ArrayList<ArrayList<String>> currentEvent;


                //////////////////////////////
                //ADD NEW SYSTEM CALLS HERE///
                //////////////////////////////////////////////////////////this part processes system calls///////////////////////////////////////////////
                switch (eventId) {

                    //here we take the appropriate action according to the type of system call that has taken place

                    case 2:
                        //fork
                        currentEventType = "fork";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 3:
                        //read

                        currentEventType = "read";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 4:
                        //writ
                        currentEventType = "write";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 5:
                        //open
                        currentEventType = "open";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);

                        break;

                    case 6:
                        //close
                        currentEventType = "close";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 9:
                        //link
                        currentEventType = "link";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 10:
                        //unlink
                        currentEventType = "unlink";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;


                    case 11:
                        //execve
                        currentEventType = "execve";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 14:
                        //mknod
                        currentEventType = "mknod";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 38:
                        //rename
                        currentEventType = "rename";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 41:
                        //duplicate(dup)
                        currentEventType = "dup";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;


                    case 42:
                        //pipe
                        currentEventType = "pipe";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 63:
                        //duplicate2(dup2)
                        currentEventType = "dup2";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 83:
                        //symlink
                        currentEventType = "symlink";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 92:
                        //truncate
                        currentEventType = "truncate";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 93:
                        //ftruncate
                        currentEventType = "ftruncate";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 102:
                        //socketcall
                        currentEventType = "socketcall";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;


                    case 120:
                        //clone
                        currentEventType = "clone";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 145:
                        //readv
                        currentEventType = "readv";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 146:
                        //writev
                        currentEventType = "writev";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;


                    case 190:
                        //vfork
                        currentEventType = "vfork";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;

                    case 252:
                        //exitgroup
                        currentEventType = "exit_group";
                        currentEvent = new ArrayList<ArrayList<String>>();
                        currentEvent.add(fields);
                        //add the new event to the hash map so we can track it now
                        unfinishedEvents.put(currentAuditId, currentEvent);
                        break;


                    default:

                        break;

                    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

                }
            } //the mini event is not a system call so it must be a mini event that is related to it or a garbage mini event
            else {
                //check to make sure that the mini event is not a garbage event that we should throw away. if not then add it to its appropriate audit
                //event in the hashmap
                if (currentEventType != null && unfinishedEvents.containsKey(currentAuditId)) {

                    //we have a useful event which has a trailing system call in the hashmap
                    //add the events to the appropriate unfinished event in the hash map

                    //now check if the number of mini events in the given event are complete if yes then package it off and send it to process finished events

                    int givenEventNum = Integer.parseInt(unfinishedEvents.get(currentAuditId).get(0).get(3).substring(8, unfinishedEvents.get(currentAuditId).get(0).get(3).length()));


                    //if end of audit finish the event
                    if (fields.get(0).compareTo("type=EOE") == 0) {
                        processFinishedEvent(unfinishedEvents.get(currentAuditId), givenEventNum);
                        unfinishedEvents.remove(currentAuditId);
                    } //otherwise add the mini-event
                    else {

                        unfinishedEvents.get(currentAuditId).add(fields);
                    }




                }

            }

        } catch (Exception exception) {
            Logger.getLogger(LinuxAudit.class.getName()).log(Level.SEVERE, null, exception);
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
    private void processFinishedEvent(ArrayList<ArrayList<String>> finishedEvent, int givenEventNum) {

        //in this function we will be passed finished events. which means opens will come along with their cwds and paths and so on
        ///////////////////////////////////////////
        //ADD NEW CALL HANDLERS HERE (this can later be divied up into seperate functions so that you dont have to check again///////////////
        ///////////////////////////////////////////

        if (finishedEvent.toString().contains("socketToPipe")) {
            return;
        }


        switch (givenEventNum) {

            //here we take the appropriate action according to the type of system call that has taken place

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
    private void processFork(ArrayList<ArrayList<String>> inputFork) {
        //process the fork


        //check for success
        String[] success = inputFork.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }




        //get the annotations for both processes
        boolean sendCloningProcess = false;
        Process cloningProcess;
        HashMap<String, String> annotations = getProcessInformation(inputFork.get(0));
        HashMap<String, String> annotationsNew = getProcessInformation(inputFork.get(0));
        HashMap<String, String> agentAnnotations = seperateAgentFromProcess(annotations);
        HashMap<String, String> agentAnnotations2 = seperateAgentFromProcess(annotationsNew);





        //check if the parent process is already there in our table
        if (processes.containsKey(annotations.get("pid"))) {
            //cloning process already in table
            cloningProcess = processes.get(annotations.get("pid"));

        } else {

            cloningProcess = new Process();
            cloningProcess.addAnnotations(annotations);
            sendCloningProcess = true;
        }

        //do processing to fix the annotations for child process
        //move pid to ppid
        annotationsNew.remove("ppid");
        annotationsNew.put("ppid", annotations.get("pid"));

        //move exit value to pid as that is child pid
        annotationsNew.remove("pid");
        annotationsNew.put("pid", annotations.get("exit"));


        Process clonedProcess = new Process();
        clonedProcess.addAnnotations(annotationsNew);
        //create a new process (add it to processes) and a clone edge
        processes.put(annotationsNew.get("pid"), clonedProcess);




        if (sendCloningProcess == true) {
            //send the cloning Process and its agent vertex and its wascontrolledby edge
            Agent agent = getAgent(agentAnnotations);
            if (agent == null) {

                agent = new Agent();
                agent.addAnnotations(agentAnnotations);
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

        //send cloned Process
        putVertex(clonedProcess);

        //send cloned Edge
        //make the cloned edge
        WasTriggeredBy clone = new WasTriggeredBy(clonedProcess, cloningProcess);
        //send edge
        putEdge(clone);

        //send agent for cloned process here
        Agent agent2 = getAgent(agentAnnotations2);
        if (agent2 == null) {

            agent2 = new Agent();
            agent2.addAnnotations(agentAnnotations2);
            putVertex(agent2);
            cachedAgents.add(agent2);
        } else {
            //agent is already in cache so dont send it. and dont make new vertex.
        }

        //send was controlled by edge for cloned process

        WasControlledBy wcb2 = new WasControlledBy(clonedProcess, agent2);
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
    private void processRead(ArrayList<ArrayList<String>> inputRead) {
        //process the read.

        try {

            //get the reading process
            String[] pid = inputRead.get(0).get(12).split("=");
            Process readingProcess = processes.get(pid[1]);

            String[] fd = inputRead.get(0).get(6).split("=");
            String keyPair = pid[1] + "," + fd[1];

            //get the read file
            Artifact fileRead = processFdIsFile.get(keyPair);

            //make a read edge

            Used readEdge = new Used(readingProcess, fileRead);


            //send the file,process and read edge


            //Do nothing here are we are not doing read monitoring at the moment



        } catch (Exception e) {
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
    private void processWrite(ArrayList<ArrayList<String>> inputWrite) {
        //process the write

        try {

            //get the writting process
            String[] pid = inputWrite.get(0).get(12).split("=");
            Process writingProcess = processes.get(pid[1]);

            String[] fd = inputWrite.get(0).get(6).split("=");
            String keyPair = pid[1] + "," + fd[1];

            //get the written file
            Artifact fileWritten = processFdIsFile.get(keyPair);
            if (fileWritten.getAnnotation("version") == null) {
                fileWritten.addAnnotation("version", "0");
            } else {
                int v = Integer.parseInt(fileWritten.getAnnotation("version"));
                fileWritten.addAnnotation("version", Integer.toString(v++));
            }



            WasGeneratedBy writeEdge = new WasGeneratedBy(fileWritten, writingProcess);


            //put new vertex for new version of file if ReadWrite bunching not on
            putVertex(fileWritten);

            //send the edge
            putEdge(writeEdge);


        } catch (Exception e) {
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
    private void processOpen(ArrayList<ArrayList<String>> inputOpen) {
        //process the open


        //check for success
        String[] success = inputOpen.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }

        try {
            //make a process
            HashMap<String, String> processAnnotations = new HashMap<String, String>();

            processAnnotations = getProcessInformation(inputOpen.get(0));

            Process openingProcess = processes.get(processAnnotations.get("pid"));
            if (!processes.containsKey(processAnnotations.get("pid"))) {
                return;
            }
            Artifact fileOpened;

            //get the file info
            HashMap<String, String> fileAnnotations = getFileInformation(inputOpen);


            //get file path
            String filePath = "";
            if (fileAnnotations.get("name").charAt(1) == '/') {
                //path is absolute
                if (fileAnnotations.get("name").charAt(0) == '\"') {
                    filePath = fileAnnotations.get("name").substring(1, fileAnnotations.get("name").length() - 1);
                } else {
                    filePath = fileAnnotations.get("name");
                }
            } else {
                //get canonical path for this file


                if (fileAnnotations.get("name").charAt(0) == '\"') {
                    File getFilePath = new File(fileAnnotations.get("cwd").substring(1, fileAnnotations.get("cwd").length() - 1) + "/" + fileAnnotations.get("name").substring(1, fileAnnotations.get("name").length() - 1));
                    filePath = getFilePath.getCanonicalPath();
                } else {
                    File getFilePath = new File(fileAnnotations.get("cwd").substring(1, fileAnnotations.get("cwd").length() - 1) + "/" + fileAnnotations.get("name"));
                    filePath = getFilePath.getCanonicalPath();

                }




            }

            //we have the file path now
            //get process key
            String fd = processAnnotations.get("exit");
            String processKey = processAnnotations.get("pid");
            String keyPair = processKey + "," + fd;

            boolean fileWasThere = false;

            //now see if other people have this file open already
            //if yes fetch pointer to that file and put it in fileOpened reference.

            if (fileNameHasArtifact.containsKey(filePath)) {

                fileOpened = fileNameHasArtifact.get(filePath);

                //add edge from process to file
                processFdIsFile.put(keyPair, fileOpened);
                fileWasThere = true;

                //file name to artifact edge is already there

            } //if no then make a new file. This file has never been opened before in this session
            else {

                fileAnnotations.put("fullpath", filePath);
                fileAnnotations.put("filename", filePath);
                fileOpened = new Artifact();
                fileOpened.addAnnotations(fileAnnotations);

                ArrayList<String> processesThatOpenFiles = new ArrayList<String>();
                processesThatOpenFiles.add(keyPair);

                //add edge from process to file
                processFdIsFile.put(keyPair, fileOpened);
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

                if (processAnnotations.get("a1").endsWith("0") || processAnnotations.get("a1").endsWith("2")) {
                    //this means file possibly opened for reading. Make and send a read edge along
                    Used u = new Used(openingProcess, fileOpened);



                    if (fileOpened.getAnnotation("filename").contains("libAuditPipe")) {
                        return;
                    } else if (openingProcess.getAnnotation("exe").endsWith("eclipse") || openingProcess.getAnnotation("exe").endsWith("java")) {
                        return;
                    }





                    putVertex(fileOpened);
                    putEdge(u);
                }
            }







        } catch (Exception exception) {
            Logger.getLogger(LinuxAudit.class.getName()).log(Level.SEVERE, null, exception);
        }

    }

    //////////////////////////////////////////////////////
    private void processClose(ArrayList<ArrayList<String>> inputClose) {
        //process the close

        //check for success
        String[] success = inputClose.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }

        //now proceed
        try {

            //get the opening process

            String[] pid = inputClose.get(0).get(12).split("=");
            Process closingProcess = processes.get(pid[1]);

            String[] fd = inputClose.get(0).get(6).split("=");
            String keyPair = pid[1] + "," + fd[1];


            //get the closed file
            Artifact fileClosed = processFdIsFile.get(keyPair);


            //delete edge from process to file
            processFdIsFile.remove(keyPair);



            //no provenance in close



        } catch (Exception e) {
        }




    }

    //////////////////////////////////////////////////////
    private void processLink(ArrayList<ArrayList<String>> inputLink) {
        //process the link
        //Needs to be done
    }

    //////////////////////////////////////////////////////
    private void processUnLink(ArrayList<ArrayList<String>> inputUnLink) {
        //process the unlink
        //Needs to be done
    }

    //////////////////////////////////////////////////////
    private void processExecve(ArrayList<ArrayList<String>> inputExecve) {
        //process the execve
        HashMap<String, String> processAnnotations = new HashMap<String, String>();
        HashMap<String, String> agentAnnotations = new HashMap<String, String>();
        String commandLine = "";

        //make the edges
        WasTriggeredBy execve;
        WasControlledBy wcb;

        processAnnotations = getProcessInformation(inputExecve.get(0));
        agentAnnotations = seperateAgentFromProcess(processAnnotations);

        //check it call was successful. if not then return
        if (processAnnotations.get("success").equals("no")) {
            return;
        }

        //else do processing

        commandLine = getExecveCommandLine(inputExecve.get(1));

        processAnnotations.put("commandline", commandLine);
        String cwd = inputExecve.get(2).get(2).split("=")[1];
        processAnnotations.put("cwd", cwd);

        //check if the process is already there in table
        String key = processAnnotations.get("pid");//+processAnnotations.get("exe");
        if (processes.containsKey(key)) {
            //get older process
            Process oldProcess = processes.get(key);
            //remove older process
            processes.remove(key);
            //add new process
            Process newProcess = new Process();
            newProcess.addAnnotations(processAnnotations);

            Agent agent = getAgent(agentAnnotations);


            if (agent == null) {
                agent = new Agent();
                agent.addAnnotations(agentAnnotations);
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
            Artifact binaryFile = new Artifact();
            HashMap<String, String> binaryAnnotations = new HashMap<String, String>();
            binaryAnnotations.put("filename", processAnnotations.get("exe"));
            binaryAnnotations.put("version", "0");
            binaryAnnotations.put("type", "Artifact");
            binaryFile.addAnnotations(binaryAnnotations);
            putVertex(binaryFile);
            Used u = new Used(oldProcess, binaryFile);
            putEdge(u);


        } //process not in table. so been seen the first time. create new process vertex
        else {
            Process newProcess = new Process();
            newProcess.addAnnotations(processAnnotations);

            //send this new process


            Agent agent = getAgent(agentAnnotations);

            if (agent == null) {
                //don't send vertex
                agent = new Agent();
                agent.addAnnotations(agentAnnotations);
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
    private void processMknod(ArrayList<ArrayList<String>> inputMknod) {
        //process the Mknod
        //dont do this
    }

    //////////////////////////////////////////////////////(incomplete)
    private void processRename(ArrayList<ArrayList<String>> inputRename) {
        //process the Rename

        //check for success
        String[] success = inputRename.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }


        //now proceed
        try {

            //get the renaming process

            String[] pid = inputRename.get(0).get(12).split("=");

            Process renamingProcess = processes.get(pid[1]);
            Artifact renamedFile;
            Artifact newFile;
            WasDerivedFrom renameEdgeFileToFile;
            WasGeneratedBy renameEdgeProcessToFile;
            //get the renamed file path name

            //String renamedFilePathname = "";


            String cwd = inputRename.get(1).get(2).split("=")[1];
            cwd = cwd.substring(1, cwd.length() - 1);

            String path1 = inputRename.get(4).get(3).split("=")[1];
            path1 = path1.substring(1, cwd.length() - 1);

            String path2 = inputRename.get(5).get(3).split("=")[1];
            path2 = path2.substring(1, cwd.length() - 1);

            //get file one canonical path
            if (path1.charAt(0) == '/') {
                //path is absolute still needs to be resolved
                File rename = new File(path1);
                path1 = rename.getCanonicalPath();
            } else {
                File rename = new File(cwd + "/" + path1);
                path1 = rename.getCanonicalPath();
            }

            //get file 2 canonical path
            if (path2.charAt(0) == '/') {
                //path is absolute still needs to be resolved
                File rename = new File(path2);
                path2 = rename.getCanonicalPath();
            } else {
                File rename = new File(cwd + "/" + path2);
                path2 = rename.getCanonicalPath();
            }

            //check if you already have renamed file in file table. if not then huzzah don't do anything
            if (!fileNameHasArtifact.containsKey(path1)) {
                //do nothing
            } else {
                //get renamed file
                renamedFile = fileNameHasArtifact.get(path1);


                //make new file Vertex
                newFile = new Artifact();
                HashMap<String, String> newFileAnnotations = new HashMap<String, String>();
                newFileAnnotations.put("fullpath", path2);
                newFileAnnotations.put("filename", path2);
                newFileAnnotations.put("type", "Artifact");

                //copy all the annotations

                String[] cwd2 = inputRename.get(1).get(2).split("=");
                newFileAnnotations.put(cwd2[0], cwd2[1]);
                String[] filePath = inputRename.get(2).get(3).split("=");
                newFileAnnotations.put(filePath[0], filePath[1]);
                String[] inode = inputRename.get(2).get(4).split("=");
                newFileAnnotations.put(inode[0], inode[1]);
                String[] dev = inputRename.get(2).get(5).split("=");
                newFileAnnotations.put(dev[0], dev[1]);
                String[] mode = inputRename.get(2).get(6).split("=");
                newFileAnnotations.put(mode[0], mode[1]);
                String[] ouid = inputRename.get(2).get(7).split("=");
                newFileAnnotations.put(ouid[0], ouid[1]);
                String[] ogid = inputRename.get(2).get(8).split("=");
                newFileAnnotations.put(ogid[0], ogid[1]);
                String[] rdev = inputRename.get(2).get(9).split("=");
                newFileAnnotations.put(rdev[0], rdev[1]);
                String[] obj = inputRename.get(2).get(10).split("=");
                newFileAnnotations.put(obj[0], obj[1]);

                newFile.addAnnotations(newFileAnnotations);



                //make file to file edge
                renameEdgeFileToFile = new WasDerivedFrom(newFile, renamedFile);
                renameEdgeProcessToFile = new WasGeneratedBy(newFile, renamingProcess);

                //delete old file and all its edges. also delete that files filepath to file vertex link
                //DO THIS

                //putVertex(newFile);




            }


        } catch (Exception e) {
        }


    }

    //////////////////////////////////////////////////////
    private void processDup(ArrayList<ArrayList<String>> inputDup) {
        //process the Dup
        //check for success
        String[] success = inputDup.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }

        //proceed

        //add a process,fd->filename,version edge in the table

        String[] pid = inputDup.get(0).get(12).split("=");
        String fd = inputDup.get(0).get(6);
        String newFd = inputDup.get(0).get(5);
        String keyPair = pid[1] + "," + fd;
        String newKeyPair = pid[1] + "," + newFd;
        if (processFdIsFile.containsKey(keyPair)) {

            Artifact file = processFdIsFile.get(keyPair);
            //String fullpath = file.getAnnotation("fullpath");

            //add process to file edge
            processFdIsFile.put(newKeyPair, file);

            //add file to process edge(not needed)

        }


    }

    //////////////////////////////////////////////////////
    private void processPipe(ArrayList<ArrayList<String>> inputPipe) {
        //process the pipe
        //Needs to be done
    }

    //////////////////////////////////////////////////////
    private void processDup2(ArrayList<ArrayList<String>> inputDup2) {
        //process the Dup2

        //check for success
        String[] success = inputDup2.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }

        //proceed

        //add a process,fd->filename,version edge in the table

        String[] pid = inputDup2.get(0).get(12).split("=");
        String fd = inputDup2.get(0).get(6);
        String newFd = inputDup2.get(0).get(5);
        String keyPair = pid[1] + "," + fd;
        String newKeyPair = pid[1] + "," + newFd;
        if (processFdIsFile.containsKey(keyPair)) {

            Artifact file = processFdIsFile.get(keyPair);
            //String fullpath = file.getAnnotation("fullpath");

            //add process to file edge
            processFdIsFile.put(newKeyPair, file);

            //add file to process edge(not needed)

        }



    }

    //////////////////////////////////////////////////////
    private void processSymlink(ArrayList<ArrayList<String>> inputSymlink) {
        //process the Symlink
        //Needs to be done
    }

    //////////////////////////////////////////////////////
    private void processTruncate(ArrayList<ArrayList<String>> inputTruncate) {
        //process the truncate


        try {

            //get the writting process
            String[] pid = inputTruncate.get(0).get(12).split("=");
            Process writingProcess = processes.get(pid[1]);

            String[] fd = inputTruncate.get(0).get(6).split("=");
            String keyPair = pid[1] + "," + fd[1];

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
        }






    }

    //////////////////////////////////////////////////////
    private void processFTruncate(ArrayList<ArrayList<String>> inputFTruncate) {
        //process the ftruncate
        try {

            //get the writting process
            String[] pid = inputFTruncate.get(0).get(12).split("=");
            Process writingProcess = processes.get(pid[1]);

            String[] fd = inputFTruncate.get(0).get(6).split("=");
            String keyPair = pid[1] + "," + fd[1];

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
        }


    }

    //////////////////////////////////////////////////////
    private void processSocketcall(ArrayList<ArrayList<String>> inputSocketcall) {
        //process the Socketcall
    }

    //////////////////////////////////////////////////////
    private void processClone(ArrayList<ArrayList<String>> inputClone) {
        //process the clone


        //check for success
        String[] success = inputClone.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }




        //String key="";
        boolean sendCloningProcess = false;
        Process cloningProcess;
        HashMap<String, String> annotations = getProcessInformation(inputClone.get(0));
        HashMap<String, String> annotationsNew = getProcessInformation(inputClone.get(0));
        HashMap<String, String> agentAnnotations = seperateAgentFromProcess(annotations);
        HashMap<String, String> agentAnnotations2 = seperateAgentFromProcess(annotationsNew);






        if (processes.containsKey(annotations.get("pid"))) {
            //cloning process already in table
            cloningProcess = processes.get(annotations.get("pid"));

        } else {

            cloningProcess = new Process();
            cloningProcess.addAnnotations(annotations);
            sendCloningProcess = true;
        }


        //move pid to ppid
        annotationsNew.remove("ppid");
        annotationsNew.put("ppid", annotations.get("pid"));

        //move exit value to pid as that is child pid
        annotationsNew.remove("pid");
        annotationsNew.put("pid", annotations.get("exit"));


        Process clonedProcess = new Process();
        clonedProcess.addAnnotations(annotationsNew);
        //create a new process (add it to processes) and a clone edge
        processes.put(annotationsNew.get("pid"), clonedProcess);




        if (sendCloningProcess == true) {
            //send the cloning Process and its agent vertex and its wascontrolledby edge
            Agent agent = getAgent(agentAnnotations);
            if (agent == null) {

                agent = new Agent();
                agent.addAnnotations(agentAnnotations);
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

        //send cloned Process
        putVertex(clonedProcess);

        //send cloned Edge
        //make the cloned edge
        WasTriggeredBy clone = new WasTriggeredBy(clonedProcess, cloningProcess);
        //send edge
        putEdge(clone);

        //send agent for cloned process here
        Agent agent2 = getAgent(agentAnnotations2);
        if (agent2 == null) {

            agent2 = new Agent();
            agent2.addAnnotations(agentAnnotations2);
            putVertex(agent2);
            cachedAgents.add(agent2);
        } else {
            //agent is already in cache so dont send it. and dont make new vertex.
        }

        //send was controlled by edge for cloned process

        WasControlledBy wcb2 = new WasControlledBy(clonedProcess, agent2);
        putEdge(wcb2);

    }

    /////////////////////////////////////////////////////////
    private void processReadv(ArrayList<ArrayList<String>> inputReadv) {
        //process the Readv


        try {

            //get the reading process
            String[] pid = inputReadv.get(0).get(12).split("=");
            Process readingProcess = processes.get(pid[1]);

            String[] fd = inputReadv.get(0).get(6).split("=");
            String keyPair = pid[1] + "," + fd[1];

            //get the read file
            Artifact fileRead = processFdIsFile.get(keyPair);

            //make a read edge

            Used readEdge = new Used(readingProcess, fileRead);

            //send the file,process and read edge



        } catch (Exception e) {
        }

    }

    /////////////////////////////////////////////////////////
    private void processWritev(ArrayList<ArrayList<String>> inputWritev) {
        //process the Writev
        try {

            //get the writting process
            String[] pid = inputWritev.get(0).get(12).split("=");
            Process writingProcess = processes.get(pid[1]);

            String[] fd = inputWritev.get(0).get(6).split("=");
            String keyPair = pid[1] + "," + fd[1];

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
        }








    }

    //////////////////////////////////////////////////////
    private void processVFork(ArrayList<ArrayList<String>> inputVFork) {
        //process the vfork


        //check for success
        String[] success = inputVFork.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }




        //String key="";
        boolean sendCloningProcess = false;
        Process cloningProcess;
        HashMap<String, String> annotations = getProcessInformation(inputVFork.get(0));
        HashMap<String, String> annotationsNew = getProcessInformation(inputVFork.get(0));
        HashMap<String, String> agentAnnotations = seperateAgentFromProcess(annotations);
        HashMap<String, String> agentAnnotations2 = seperateAgentFromProcess(annotationsNew);






        if (processes.containsKey(annotations.get("pid"))) {
            //cloning process already in table
            cloningProcess = processes.get(annotations.get("pid"));

        } else {

            cloningProcess = new Process();
            cloningProcess.addAnnotations(annotations);
            sendCloningProcess = true;
        }


        //move pid to ppid
        annotationsNew.remove("ppid");
        annotationsNew.put("ppid", annotations.get("pid"));

        //move exit value to pid as that is child pid
        annotationsNew.remove("pid");
        annotationsNew.put("pid", annotations.get("exit"));


        Process clonedProcess = new Process();
        clonedProcess.addAnnotations(annotationsNew);
        //create a new process (add it to processes) and a clone edge
        processes.put(annotationsNew.get("pid"), clonedProcess);




        if (sendCloningProcess == true) {
            //send the cloning Process and its agent vertex and its wascontrolledby edge
            Agent agent = getAgent(agentAnnotations);
            if (agent == null) {

                agent = new Agent();
                agent.addAnnotations(agentAnnotations);
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

        //send cloned Process
        putVertex(clonedProcess);

        //send cloned Edge
        //make the cloned edge
        WasTriggeredBy clone = new WasTriggeredBy(clonedProcess, cloningProcess);
        //send edge
        putEdge(clone);

        //send agent for cloned process here
        Agent agent2 = getAgent(agentAnnotations2);
        if (agent2 == null) {

            agent2 = new Agent();
            agent2.addAnnotations(agentAnnotations2);
            putVertex(agent2);
            cachedAgents.add(agent2);
        } else {
            //agent is already in cache so dont send it. and dont make new vertex.
        }

        //send was controlled by edge for cloned process

        WasControlledBy wcb2 = new WasControlledBy(clonedProcess, agent2);
        putEdge(wcb2);


    }

    //////////////////////////////////////////////////////
    private void processExitGroup(ArrayList<ArrayList<String>> inputExitGroup) {
        //process the exit group;

        //check for success
        String[] success = inputExitGroup.get(0).get(4).split("=");
        //if sys call didn't succeed just return. no use logging it
        if (success[1].equals("no")) {
            return;
        }




        HashMap<String, String> processAnnotations = getProcessInformation(inputExitGroup.get(0));

        //remove the process from the existing processes table
        if (processes.containsKey(processAnnotations.get("pid"))) {
            processes.remove(processAnnotations.get("pid") + processAnnotations.get("comm"));
        }


        //possibly send information to the kernel that this process has finished


    }

    private HashMap<String, String> getProcessInformation(ArrayList<String> listOfVariables) {
        HashMap<String, String> annotations = new HashMap<String, String>();


        //get timestamp and audit id
        String[] auditIds = listOfVariables.get(1).split("=");
        String[] timeAndAuditId = auditIds[1].split(":");
        String timestamp = timeAndAuditId[0];
        String auditId = timeAndAuditId[1];

        timestamp = timestamp.substring(6, timestamp.length());
        auditId = auditId.substring(0, auditId.length() - 1);


        annotations.put("time", timestamp);
        annotations.put("auditId", auditId);

        // get architecture
        String[] architecture = listOfVariables.get(2).split("=");
        annotations.put(architecture[0], architecture[1]);

        for (int i = 3; i < listOfVariables.size(); i++) {
            String[] var = listOfVariables.get(i).split("=");
            annotations.put(var[0], var[1]);

        }

        return annotations;
    }

    private String getExecveCommandLine(ArrayList<String> inputList) {

        HashMap<String, String> returnMap = new HashMap<String, String>();

        // get num args
        String[] numArgs = inputList.get(2).split("=");
        String commandLine = "";



        for (int i = 1; i <= Integer.parseInt(numArgs[1]); i++) {
            // get all command line variables
            String[] arg = inputList.get(2 + i).split("=");

            returnMap.put(arg[0], arg[1]);
            commandLine = commandLine + arg[1] + " ";
        }



        return commandLine;

    }

    private HashMap<String, String> getFileInformation(ArrayList<ArrayList<String>> openEvent) {

        HashMap<String, String> fileAnnotations = new HashMap<String, String>();
        try {
            String[] cwd = openEvent.get(1).get(2).split("=");
            fileAnnotations.put(cwd[0], cwd[1]);

            String[] auditIds = openEvent.get(1).get(1).split("=");
            String[] timeAndAuditId = auditIds[1].split(":");
            String timestamp = timeAndAuditId[0];
            String auditId = timeAndAuditId[1];

            timestamp = timestamp.substring(6, timestamp.length());
            auditId = auditId.substring(0, auditId.length() - 1);


            fileAnnotations.put("time", timestamp);
            fileAnnotations.put("auditId", auditId);




            //get file path
            if (openEvent.size() <= 3) {
                String[] filePath = openEvent.get(2).get(3).split("=");
                fileAnnotations.put(filePath[0], filePath[1]);
                String[] inode = openEvent.get(2).get(4).split("=");
                fileAnnotations.put(inode[0], inode[1]);
                String[] dev = openEvent.get(2).get(5).split("=");
                fileAnnotations.put(dev[0], dev[1]);
                String[] mode = openEvent.get(2).get(6).split("=");
                fileAnnotations.put(mode[0], mode[1]);
                String[] ouid = openEvent.get(2).get(7).split("=");
                fileAnnotations.put(ouid[0], ouid[1]);
                String[] ogid = openEvent.get(2).get(8).split("=");
                fileAnnotations.put(ogid[0], ogid[1]);
                String[] rdev = openEvent.get(2).get(9).split("=");
                fileAnnotations.put(rdev[0], rdev[1]);
                String[] obj = openEvent.get(2).get(10).split("=");
                fileAnnotations.put(obj[0], obj[1]);

            } else {
                String[] filePath = openEvent.get(3).get(3).split("=");
                fileAnnotations.put(filePath[0], filePath[1]);
                String[] inode = openEvent.get(3).get(4).split("=");
                fileAnnotations.put(inode[0], inode[1]);
                String[] dev = openEvent.get(3).get(5).split("=");
                fileAnnotations.put(dev[0], dev[1]);
                String[] mode = openEvent.get(3).get(6).split("=");
                fileAnnotations.put(mode[0], mode[1]);
                String[] ouid = openEvent.get(3).get(7).split("=");
                fileAnnotations.put(ouid[0], ouid[1]);
                String[] ogid = openEvent.get(3).get(8).split("=");
                fileAnnotations.put(ogid[0], ogid[1]);
                String[] rdev = openEvent.get(3).get(9).split("=");
                fileAnnotations.put(rdev[0], rdev[1]);
                String[] obj = openEvent.get(3).get(10).split("=");
                fileAnnotations.put(obj[0], obj[1]);
            }





            String filePath = "";
            if (fileAnnotations.get("name").charAt(1) == '/') {
                //path is absolute
                if (fileAnnotations.get("name").charAt(0) == '\"') {
                    filePath = fileAnnotations.get("name").substring(1, fileAnnotations.get("name").length() - 1);
                } else {
                    filePath = fileAnnotations.get("name");
                }
            } else {
                //get canonical path


                if (fileAnnotations.get("name").charAt(0) == '\"') {
                    File getFilePath = new File(fileAnnotations.get("cwd").substring(1, fileAnnotations.get("cwd").length() - 1) + "/" + fileAnnotations.get("name").substring(1, fileAnnotations.get("name").length() - 1));
                    filePath = getFilePath.getCanonicalPath();
                } else {
                    File getFilePath = new File(fileAnnotations.get("cwd").substring(1, fileAnnotations.get("cwd").length() - 1) + "/" + fileAnnotations.get("name"));
                    filePath = getFilePath.getCanonicalPath();

                }




            }



            fileAnnotations.put("filename", filePath);


        } catch (Exception ioe) {
        }

        return fileAnnotations;

    }

    private HashMap<String, String> seperateAgentFromProcess(HashMap<String, String> processAnnotations) {
        HashMap<String, String> agentAnnotations = new HashMap<String, String>();

//this to put in agent
/*
         * | uid | egid | arch | auid | sgid | fsgid | suid | euid | node |
         * fsuid | gid
         */

        if (processAnnotations.containsKey("uid")) {

            agentAnnotations.put("uid", processAnnotations.get("uid"));
            processAnnotations.remove("uid");
        }

        if (processAnnotations.containsKey("egid")) {
            agentAnnotations.put("egid", processAnnotations.get("egid"));
            processAnnotations.remove("egid");
        }

        if (processAnnotations.containsKey("arch")) {
            agentAnnotations.put("arch", processAnnotations.get("arch"));
            processAnnotations.remove("arch");
        }

        if (processAnnotations.containsKey("auid")) {
            agentAnnotations.put("auid", processAnnotations.get("auid"));
            processAnnotations.remove("auid");
        }

        if (processAnnotations.containsKey("sgid")) {
            agentAnnotations.put("sgid", processAnnotations.get("sgid"));
            processAnnotations.remove("sgid");
        }

        if (processAnnotations.containsKey("fsgid")) {
            agentAnnotations.put("fsgid", processAnnotations.get("fsgid"));
            processAnnotations.remove("fsgid");
        }

        if (processAnnotations.containsKey("suid")) {
            agentAnnotations.put("suid", processAnnotations.get("suid"));
            processAnnotations.remove("suid");
        }

        if (processAnnotations.containsKey("euid")) {
            agentAnnotations.put("euid", processAnnotations.get("euid"));
            processAnnotations.remove("euid");
        }

        if (processAnnotations.containsKey("node")) {
            agentAnnotations.put("node", processAnnotations.get("node"));
            processAnnotations.remove("node");
        }

        if (processAnnotations.containsKey("fsuid")) {
            agentAnnotations.put("fsuid", processAnnotations.get("fsuid"));
            processAnnotations.remove("fsuid");
        }

        if (processAnnotations.containsKey("gid")) {
            agentAnnotations.put("gid", processAnnotations.get("gid"));
            processAnnotations.remove("gid");
        }






        return agentAnnotations;
    }

    private Agent getAgent(HashMap<String, String> annotations) {
        Agent fake = null;

        Iterator<Agent> agents = cachedAgents.iterator();

        while (agents.hasNext()) {
            Agent storedAgent = agents.next();
            boolean wantedAgent = true;
            //Iterator<String> storedAgentAnnotations = storedAgent.getAnnotations().keySet().iterator();
            Iterator<String> passedAgentAnnotations = annotations.keySet().iterator();
            while (passedAgentAnnotations.hasNext()) {
                String key = passedAgentAnnotations.next();
                String value = annotations.get(key);

                if (storedAgent.getAnnotations().containsKey(key) && storedAgent.getAnnotation(key).equals(value)) {
                } else {
                    wantedAgent = false;
                    break;
                }


            }

            if (wantedAgent = true) {
                return storedAgent;
            }
        }

        return fake;

    }
}
