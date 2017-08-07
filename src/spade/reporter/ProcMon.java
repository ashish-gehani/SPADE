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
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractReporter;
import spade.edge.opm.Used;
import spade.edge.opm.WasControlledBy;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 *
 * @author dawood
 */
public class ProcMon extends AbstractReporter {

    private BufferedReader eventReader;
    private Map<String, Process> processMap = new HashMap<String, Process>();
    private Map<String, Set<String>> threadMap = new HashMap<String,Set<String>>();
    private Map<String, Integer> artifactVersions = new HashMap<String, Integer>();
    private Set<String> loadedImages = new HashSet<String>();
    private Set<String> networkConnections = new HashSet<String>();
    static final Logger logger = Logger.getLogger(ProcMon.class.getName());
    private volatile boolean shutdown = false;
    ////////////////////////////////////////////////////////////////////////////
    private final String COLUMN_TIME = "Time of Day";
    private final String COLUMN_PROCESS_NAME = "Process Name";
    private final String COLUMN_PID = "PID";
    private final String COLUMN_OPERATION = "Operation";
    private final String COLUMN_PATH = "Path";
    private final String COLUMN_RESULT = "Result";
    private final String COLUMN_DETAIL = "Detail";
    private final String COLUMN_DURATION = "Duration";
    private final String COLUMN_EVENT_CLASS = "Event Class";
    private final String COLUMN_IMAGE_PATH = "Image Path";
    private final String COLUMN_COMPANY = "Company";
    private final String COLUMN_DESCRIPTION = "Description";
    private final String COLUMN_VERSION = "Version";
    private final String COLUMN_USER = "User";
    private final String COLUMN_COMMAND_LINE = "Command Line";
    private final String COLUMN_PARENT_PID = "Parent PID";
    private final String COLUMN_ARCHITECTURE = "Architecture";
    private final String COLUMN_CATEGORY = "Category";
    private final String COLUMN_DATE_AND_TIME = "Date & Time";
    private final String COLUMN_TID = "TID";
    ////////////////////////////////////////////////////////////////////////////
    private int N_TIME;
    private int N_PROCESS_NAME;
    private int N_PID;
    private int N_OPERATION;
    private int N_PATH;
    private int N_RESULT;
    private int N_DETAIL;
    private int N_DURATION;
    private int N_EVENT_CLASS;
    private int N_IMAGE_PATH;
    private int N_COMPANY;
    private int N_DESCRIPTION;
    private int N_VERSION;
    private int N_USER;
    private int N_COMMAND_LINE;
    private int N_PARENT_PID;
    private int N_ARCHITECTURE;
    private int N_CATEGORY;
    private int N_DATE_AND_TIME;
    private int N_TID;
    private boolean CONTAIN_TID = false;
    private boolean CONTAIN_DATETIME = false;
    ////////////////////////////////////////////////////////////////////////////
    private final String EVENT_CLASS_REGISTRY = "Registry";
    private final String EVENT_CLASS_FILE_SYSTEM = "File System";
    ////////////////////////////////////////////////////////////////////////////
    private final String CATEGORY_READ = "Read";
    private final String CATEGORY_WRITE = "Write";
    private final String CATEGORY_READ_METADATA = "Read Metadata";
    private final String CATEGORY_WRITE_METADATA = "Write Metadata";
    ////////////////////////////////////////////////////////////////////////////
    private final String OPERATION_LoadImage = "Load Image";
    private final String OPERATION_TCPSend = "TCP Send";
    private final String OPERATION_UDPSend = "UDP Send";
    private final String OPERATION_TCPReceive = "TCP Receive";
    private final String OPERATION_UDPReceive = "UDP Receive";
    private final String OPERATION_TCPConnect = "TCP Connect"; //appear in ProcMon
    private final String OPERATION_TCPReconnect = "TCP Reconnect";//appear in ProcMon
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean launch(String arguments) {
        if (arguments == null) {
            return false;
        }
        try {
            Map<String, Integer> columnMap = new HashMap<String, Integer>();
            eventReader = new BufferedReader(new FileReader(arguments));
            String initLine = eventReader.readLine();
            String[] columns = initLine.substring(2, initLine.length() - 1).split("\",\"");
            for (int i = 0; i < columns.length; i++) {
                columnMap.put(columns[i], i);
            }
            N_TIME = columnMap.get(COLUMN_TIME);
            N_PROCESS_NAME = columnMap.get(COLUMN_PROCESS_NAME);
            N_PID = columnMap.get(COLUMN_PID);
            N_OPERATION = columnMap.get(COLUMN_OPERATION);
            N_PATH = columnMap.get(COLUMN_PATH);
            N_RESULT = columnMap.get(COLUMN_RESULT);
            N_DETAIL = columnMap.get(COLUMN_DETAIL);
            N_DURATION = columnMap.get(COLUMN_DURATION);
            N_EVENT_CLASS = columnMap.get(COLUMN_EVENT_CLASS);
            N_IMAGE_PATH = columnMap.get(COLUMN_IMAGE_PATH);
            N_COMPANY = columnMap.get(COLUMN_COMPANY);
            N_DESCRIPTION = columnMap.get(COLUMN_DESCRIPTION);
            N_VERSION = columnMap.get(COLUMN_VERSION);
            N_USER = columnMap.get(COLUMN_USER);
            N_COMMAND_LINE = columnMap.get(COLUMN_COMMAND_LINE);
            N_PARENT_PID = columnMap.get(COLUMN_PARENT_PID);
            N_ARCHITECTURE = columnMap.get(COLUMN_ARCHITECTURE);
            N_CATEGORY = columnMap.get(COLUMN_CATEGORY);
            try{
            	N_DATE_AND_TIME = columnMap.get(COLUMN_DATE_AND_TIME);
            	CONTAIN_DATETIME = true;
            }catch(Exception e){
            	logger.log(Level.WARNING,e+" no date column in log");
            }
            try{
            	N_TID = columnMap.get(COLUMN_TID);
              CONTAIN_TID = true;
            }catch(Exception e){
            	logger.log(Level.WARNING,e+" no thread column in log");
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
        Runnable lineProcessor = new Runnable() {
            public void run() {
                try {
                    String line;
                    while (!shutdown) {
                        line = eventReader.readLine();
                        if (line == null) {
                            break;
                        }
                        while (!line.endsWith("\"")) {
                            line += eventReader.readLine();
                        }
                        processLine(line);
                    }
                    logger.log(Level.INFO, "Finished parsing file");
                    eventReader.close();
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(lineProcessor, "ProcMon-Thread").start();
        return true;
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        return true;
    }

    private void processLine(String line) {
        try {
            String[] data = line.substring(1, line.length() - 1).split("\",\"");
            if (!data[N_RESULT].equals("SUCCESS")) {
                return;
            }

            if (!processMap.containsKey(data[N_PID])) {
                createProcess(data);
                if(CONTAIN_TID){
                	Set<String> thread = new HashSet<String>();
                	thread.add(data[N_TID]);
                	threadMap.put(data[N_PID],thread);
                }
            } 
            if(CONTAIN_TID){//to allow to count how many thread it launched, graph no longer acyclic
            	if(processMap.containsKey(data[N_PID])&&(!threadMap.get(data[N_PID]).contains(data[N_TID]))){
            		createWtb(data);
            		threadMap.get(data[N_PID]).add(data[N_TID]);
            	}
            }

            String eventClass = data[N_EVENT_CLASS];
            String category = data[N_CATEGORY];
            String operation = data[N_OPERATION];

            if (category.equals(CATEGORY_READ) || category.equals(CATEGORY_READ_METADATA)) {
                if (eventClass.equals(EVENT_CLASS_REGISTRY) || eventClass.equals(EVENT_CLASS_FILE_SYSTEM)) {
                    readArtifact(data);
                }
            } else if (category.equals(CATEGORY_WRITE) || category.equals(CATEGORY_WRITE_METADATA)) {
                if (eventClass.equals(EVENT_CLASS_REGISTRY) || eventClass.equals(EVENT_CLASS_FILE_SYSTEM)) {
                    writeArtifact(data);
                }
            } else if (operation.equals(OPERATION_LoadImage)) {
                loadImage(data);
            } else if (operation.equals(OPERATION_TCPSend) || operation.equals(OPERATION_UDPSend) || operation.equals(OPERATION_TCPConnect) || operation.equals(OPERATION_TCPReconnect)) {
                networkSend(data);
            } else if (operation.equals(OPERATION_TCPReceive) || operation.equals(OPERATION_UDPReceive)) {
                networkReceive(data);
            }
        } catch (Exception exception) {
            System.err.println("Error parsing line: " + line);
//            logger.log(Level.SEVERE, null, exception);
        }
    }

    private void createProcess(String[] data) {
        String pid = data[N_PID];
        String ppid = data[N_PARENT_PID];
        Process process = new Process();
        process.addAnnotation("pid", pid);

        process.addAnnotation("ppid", ppid);
        process.addAnnotation("name", data[N_PROCESS_NAME]);
        process.addAnnotation("imagepath", data[N_IMAGE_PATH]);
        process.addAnnotation("commandline", data[N_COMMAND_LINE]);
        process.addAnnotation("arch", data[N_ARCHITECTURE]);
        process.addAnnotation("company", data[N_COMPANY]);
        process.addAnnotation("description", data[N_DESCRIPTION]);
        process.addAnnotation("version", data[N_VERSION]);

        putVertex(process);
        processMap.put(pid, process);



        Agent user = new Agent();
       	user.addAnnotation(COLUMN_USER, data[N_USER]);
       	putVertex(user);

       	WasControlledBy wcb = new WasControlledBy(process, user);
       	putEdge(wcb);



       	if (processMap.containsKey(ppid)) {
        	WasTriggeredBy wtb = new WasTriggeredBy(process, processMap.get(ppid));
        	wtb.addAnnotation("time", data[N_TIME]);
        	try{
        		wtb.addAnnotation("datetime", data[N_DATE_AND_TIME]);
        	}catch(Exception e){}

        	putEdge(wtb);
        }


    }

    private void createWtb(String[] data){
    	String pid = data[N_PID];
    	WasTriggeredBy wtb = new WasTriggeredBy(processMap.get(pid), processMap.get(pid));
    	wtb.addAnnotation("time", data[N_TIME]);
    	if(CONTAIN_DATETIME){
    		wtb.addAnnotation("datetime", data[N_DATE_AND_TIME]);
    	}

    	putEdge(wtb);
    }

    private void readArtifact(String[] data) {
        String pid = data[N_PID];
        String path = data[N_PATH];

        boolean put = !artifactVersions.containsKey(path);
        int version = artifactVersions.containsKey(path) ? artifactVersions.get(path) : 0;
        artifactVersions.put(path, version);

        Artifact artifact = new Artifact();
        artifact.addAnnotation("class", data[N_EVENT_CLASS]);
        artifact.addAnnotation("path", path);
        //once version is commented graph is no longer acyclic
        //artifact.addAnnotation("version", Integer.toString(version));

        if (put) {
            putVertex(artifact);
        }

        Used used = new Used(processMap.get(pid), artifact);
        used.addAnnotation("time", data[N_TIME]);
        if(CONTAIN_DATETIME){
        	used.addAnnotation("datetime", data[N_DATE_AND_TIME]);
        }
        used.addAnnotation("operation", data[N_OPERATION]);
        used.addAnnotation("category", data[N_CATEGORY]);
        used.addAnnotation("detail", data[N_DETAIL]);
        used.addAnnotation("duration", data[N_DURATION]);
        putEdge(used);
    }

    private void writeArtifact(String[] data) {
        String pid = data[N_PID];
        String path = data[N_PATH];

        boolean put = !artifactVersions.containsKey(path);
        int version = artifactVersions.containsKey(path) ? artifactVersions.get(path) + 1 : 1;
        artifactVersions.put(path, version);

        Artifact artifact = new Artifact();
        artifact.addAnnotation("class", data[N_EVENT_CLASS]);
        artifact.addAnnotation("path", path);
        //once version is commented graph is no longer acyclic
        //artifact.addAnnotation("version", Integer.toString(version));
        if (put) {
            putVertex(artifact);
        }

        WasGeneratedBy wgb = new WasGeneratedBy(artifact, processMap.get(pid));
        wgb.addAnnotation("time", data[N_TIME]);
        if(CONTAIN_DATETIME){
        	wgb.addAnnotation("datetime", data[N_DATE_AND_TIME]);
        }
        wgb.addAnnotation("operation", data[N_OPERATION]);
        wgb.addAnnotation("category", data[N_CATEGORY]);
        wgb.addAnnotation("detail", data[N_DETAIL]);
        wgb.addAnnotation("duration", data[N_DURATION]);
        putEdge(wgb);
    }

    private void loadImage(String[] data) {
        String pid = data[N_PID];

        Artifact image = new Artifact();
        image.addAnnotation("type", "file");
        image.addAnnotation("path", data[N_PATH]);
        if (loadedImages.add(data[N_PATH])) {
            putVertex(image);
        }

        Used used = new Used(processMap.get(pid), image);
        used.addAnnotation("time", data[N_TIME]);
        if(CONTAIN_DATETIME){
        	used.addAnnotation("datetime", data[N_DATE_AND_TIME]);
        }
        used.addAnnotation("operation", data[N_OPERATION]);
        used.addAnnotation("detail", data[N_DETAIL]);
        used.addAnnotation("duration", data[N_DURATION]);
        putEdge(used);
    }

    private void networkSend(String[] data) {
        String pid = data[N_PID];
        String[] hosts = data[N_PATH].split(" -> ");
        String[] local = hosts[0].split(":");
        String[] remote = hosts[1].split(":");

        Artifact network = new Artifact();
        network.addAnnotation("subtype", "network");
        int n = local.length;
        if (n == 2){
          network.addAnnotation("local host", local[0]);
          network.addAnnotation("local port", local[1]);
        }
        else{
          network.addAnnotation("local host", hosts[0].substring(0,hosts[0].length()-1 - local[n-1].length()));
          network.addAnnotation("local port", local[n-1]);
        }
        int m = remote.length;
        if (m == 2){
          network.addAnnotation("remote host", remote[0]);
          network.addAnnotation("remote port", remote[1]);
        }
        else{
          network.addAnnotation("remote host", hosts[1].substring(0,hosts[1].length()-1 - remote[m-1].length()));
          network.addAnnotation("remote port", remote[m-1]);
        }
        if (networkConnections.add(data[N_PATH])) {
            putVertex(network);
        }

        WasGeneratedBy wgb = new WasGeneratedBy(network, processMap.get(pid));
        wgb.addAnnotation("time", data[N_TIME]);
        if(CONTAIN_DATETIME){
        	wgb.addAnnotation("datetime", data[N_DATE_AND_TIME]);
        }
        wgb.addAnnotation("operation", data[N_OPERATION]);
        wgb.addAnnotation("detail", data[N_DETAIL]);
        putEdge(wgb);
    }

    private void networkReceive(String[] data) {
        String pid = data[N_PID];
        String[] hosts = data[N_PATH].split(" -> ");
        String[] local = hosts[0].split(":");
        String[] remote = hosts[1].split(":");

        Artifact network = new Artifact();
        network.addAnnotation("subtype", "network");
        int n = local.length;
        if (n == 2){
          network.addAnnotation("local host", local[0]);
          network.addAnnotation("local port", local[1]);
        }
        else{
          network.addAnnotation("local host", hosts[0].substring(0,hosts[0].length()-1 - local[n-1].length()));
          network.addAnnotation("local port", local[n-1]);
        }
        int m = remote.length;
        if (m == 2){
          network.addAnnotation("remote host", remote[0]);
          network.addAnnotation("remote port", remote[1]);
        }
        else{
          network.addAnnotation("remote host", hosts[1].substring(0,hosts[1].length()-1 - remote[m-1].length()));
          network.addAnnotation("remote port", remote[m-1]);
        }
        if (networkConnections.add(data[N_PATH])) {
            putVertex(network);
        }

        Used used = new Used(processMap.get(pid), network);
        used.addAnnotation("time", data[N_TIME]);
        if(CONTAIN_DATETIME){
        	used.addAnnotation("datetime", data[N_DATE_AND_TIME]);
        }
        used.addAnnotation("operation", data[N_OPERATION]);
        used.addAnnotation("detail", data[N_DETAIL]);
        putEdge(used);
    }
}
