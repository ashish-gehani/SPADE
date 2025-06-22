/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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
package spade.reporter.audit.bpf;

import java.util.List;
import java.util.ArrayList;

public class AmebaArguments {

    private final static int MAX_ID_LIST_SIZE = 10;
    public final static String 
        ARG_NAME_GLOBAL_MODE = "--global-mode",
        ARG_NAME_PPID_MODE = "--ppid-mode",
        ARG_NAME_PID_MODE = "--pid-mode",
        ARG_NAME_UID_MODE = "--uid-mode",
        ARG_NAME_NETIO_MODE = "--netio-mode",
        ARG_NAME_PPID_LIST = "--ppid-list",
        ARG_NAME_PID_LIST = "--pid-list",
        ARG_NAME_UID_LIST = "--uid-list",
        ARG_NAME_OUTPUT_FILE_PATH = "--file-path",
        ARG_NAME_OUTPUT_IP = "--ip",
        ARG_NAME_OUTPUT_PORT = "--port";


    private AmebaMode
        globalMode,
        ppidMode,
        pidMode,
        uidMode,
        netioMode;

    private int 
        ppidList[] = new int[MAX_ID_LIST_SIZE],
        pidList[] = new int[MAX_ID_LIST_SIZE],
        uidList[] = new int[MAX_ID_LIST_SIZE];

    private String outputFilePath;
    private String outputIP;
    private int outputPort;

    private AmebaOutputType outputType;
    
    public AmebaMode getGlobalMode() {
        return globalMode;
    }

    public void setGlobalMode(AmebaMode globalMode) {
        this.globalMode = globalMode;
    }

    public AmebaMode getPpidMode() {
        return ppidMode;
    }

    public void setPpidMode(AmebaMode ppidMode) {
        this.ppidMode = ppidMode;
    }

    public AmebaMode getPidMode() {
        return pidMode;
    }

    public void setPidMode(AmebaMode pidMode) {
        this.pidMode = pidMode;
    }

    public AmebaMode getUidMode() {
        return uidMode;
    }

    public void setUidMode(AmebaMode uidMode) {
        this.uidMode = uidMode;
    }

    public AmebaMode getNetioMode() {
        return netioMode;
    }

    public void setNetioMode(AmebaMode netioMode) {
        this.netioMode = netioMode;
    }

    // Getters and setters for list fields
    public int[] getPpidList() {
        return ppidList;
    }

    public void setPpidList(int[] ppidList) {
        if (ppidList.length <= MAX_ID_LIST_SIZE) {
            System.arraycopy(ppidList, 0, this.ppidList, 0, ppidList.length);
        } else {
            throw new IllegalArgumentException("PPID list exceeds max size of " + MAX_ID_LIST_SIZE);
        }
    }

    public int[] getPidList() {
        return pidList;
    }

    public void setPidList(int[] pidList) {
        if (pidList.length <= MAX_ID_LIST_SIZE) {
            System.arraycopy(pidList, 0, this.pidList, 0, pidList.length);
        } else {
            throw new IllegalArgumentException("PID list exceeds max size of " + MAX_ID_LIST_SIZE);
        }
    }

    public int[] getUidList() {
        return uidList;
    }

    public void setUidList(int[] uidList) {
        if (uidList.length <= MAX_ID_LIST_SIZE) {
            System.arraycopy(uidList, 0, this.uidList, 0, uidList.length);
        } else {
            throw new IllegalArgumentException("UID list exceeds max size of " + MAX_ID_LIST_SIZE);
        }
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public void setOutputFilePath(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public String getOutputIP() {
        return outputIP;
    }

    public void setOutputIP(String outputIP) {
        this.outputIP = outputIP;
    }

    public int getOutputPort() {
        return outputPort;
    }

    public void setOutputPort(int outputPort) {
        this.outputPort = outputPort;
    }

    public AmebaOutputType getOutputType() {
        return this.outputType;
    }

    public void setOutputType(AmebaOutputType outputType) {
        this.outputType = outputType;
    }

    public List<String> buildArgumentArray() {
        final List<String> args = new ArrayList<String>();

        if (globalMode != null)
            args.add(ARG_NAME_GLOBAL_MODE + "=" + globalMode.getValue());

        if (ppidMode != null)
            args.add(ARG_NAME_PPID_MODE + "=" + ppidMode.getValue());

        if (pidMode != null)
            args.add(ARG_NAME_PID_MODE + "=" + pidMode.getValue());

        if (uidMode != null)
            args.add(ARG_NAME_UID_MODE + "=" + uidMode.getValue());

        if (netioMode != null)
            args.add(ARG_NAME_NETIO_MODE + "=" + netioMode.getValue());

        args.add(ARG_NAME_PPID_LIST + "=" + arrayToString(ppidList));
        args.add(ARG_NAME_PID_LIST + "=" + arrayToString(pidList));
        args.add(ARG_NAME_UID_LIST + "=" + arrayToString(uidList));

        if (outputFilePath != null && !outputFilePath.isEmpty())
            args.add(ARG_NAME_OUTPUT_FILE_PATH + "=" + outputFilePath);

        if (outputIP != null && !outputIP.isEmpty())
            args.add(ARG_NAME_OUTPUT_IP + "=" + outputIP);

        if (outputPort > 0)
            args.add(ARG_NAME_OUTPUT_PORT + "=" + outputPort);

        return args;
    }

    public String buildArgumentString() {
        List<String> args = this.buildArgumentArray();
        return String.join(" ", args);
    }

    private String arrayToString(int[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length && array[i] != 0; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        return sb.toString();
    }
}
