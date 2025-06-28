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
package spade.reporter.audit.bpf.ameba;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.ProcessUserSyscallFilter;
import spade.reporter.audit.ProcessUserSyscallFilter.UserMode;

public class Arguments {

    private static final int MAX_ID_LIST_SIZE = 10;

    public static final String
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

    private final Mode globalMode;
    private final Mode ppidMode;
    private final Mode pidMode;
    private final Mode uidMode;
    private final Mode netioMode;

    private final int[] ppidList;
    private final int[] pidList;
    private final int[] uidList;

    private final String outputFilePath;
    private final String outputIP;
    private final int outputPort;

    private final OutputType outputType;

    private Arguments(Builder builder) {
        this.globalMode = builder.globalMode;
        this.ppidMode = builder.ppidMode;
        this.pidMode = builder.pidMode;
        this.uidMode = builder.uidMode;
        this.netioMode = builder.netioMode;
        this.ppidList = builder.ppidList;
        this.pidList = builder.pidList;
        this.uidList = builder.uidList;
        this.outputFilePath = builder.outputFilePath;
        this.outputIP = builder.outputIP;
        this.outputPort = builder.outputPort;
        this.outputType = builder.outputType;
    }

    public static class Builder {
        private Mode globalMode;
        private Mode ppidMode;
        private Mode pidMode;
        private Mode uidMode;
        private Mode netioMode;

        private int[] ppidList = new int[MAX_ID_LIST_SIZE];
        private int[] pidList = new int[MAX_ID_LIST_SIZE];
        private int[] uidList = new int[MAX_ID_LIST_SIZE];

        private String outputFilePath;
        private String outputIP;
        private int outputPort;

        private OutputType outputType;

        public Builder setGlobalMode(Mode mode) { this.globalMode = mode; return this; }
        public Builder setPpidMode(Mode mode) { this.ppidMode = mode; return this; }
        public Builder setPidMode(Mode mode) { this.pidMode = mode; return this; }
        public Builder setUidMode(Mode mode) { this.uidMode = mode; return this; }
        public Builder setNetioMode(Mode mode) { this.netioMode = mode; return this; }

        public Builder setPpidList(int[] list) {
            validateList(list, "PPID");
            this.ppidList = Arrays.copyOf(list, MAX_ID_LIST_SIZE);
            return this;
        }

        public Builder setPidList(int[] list) {
            validateList(list, "PID");
            this.pidList = Arrays.copyOf(list, MAX_ID_LIST_SIZE);
            return this;
        }

        public Builder setUidList(int[] list) {
            validateList(list, "UID");
            this.uidList = Arrays.copyOf(list, MAX_ID_LIST_SIZE);
            return this;
        }

        public Builder setOutputFilePath(String path) { this.outputFilePath = path; return this; }
        public Builder setOutputIP(String ip) { this.outputIP = ip; return this; }
        public Builder setOutputPort(int port) { this.outputPort = port; return this; }
        public Builder setOutputType(OutputType type) { this.outputType = type; return this; }

        public Arguments build() {
            return new Arguments(this);
        }

        private void validateList(int[] list, String name) {
            if (list.length > MAX_ID_LIST_SIZE)
                throw new IllegalArgumentException(name + " list exceeds max size of " + MAX_ID_LIST_SIZE);
        }
    }

    // Accessors
    public Mode getGlobalMode() { return globalMode; }
    public Mode getPpidMode() { return ppidMode; }
    public Mode getPidMode() { return pidMode; }
    public Mode getUidMode() { return uidMode; }
    public Mode getNetioMode() { return netioMode; }
    public int[] getPpidList() { return ppidList; }
    public int[] getPidList() { return pidList; }
    public int[] getUidList() { return uidList; }
    public String getOutputFilePath() { return outputFilePath; }
    public String getOutputIP() { return outputIP; }
    public int getOutputPort() { return outputPort; }
    public OutputType getOutputType() { return outputType; }

    public List<String> buildArgumentArray() {
        List<String> args = new ArrayList<>();

        if (globalMode != null) args.add(ARG_NAME_GLOBAL_MODE + "=" + globalMode.getValue());
        if (ppidMode != null) args.add(ARG_NAME_PPID_MODE + "=" + ppidMode.getValue());
        if (pidMode != null) args.add(ARG_NAME_PID_MODE + "=" + pidMode.getValue());
        if (uidMode != null) args.add(ARG_NAME_UID_MODE + "=" + uidMode.getValue());
        if (netioMode != null) args.add(ARG_NAME_NETIO_MODE + "=" + netioMode.getValue());

        args.add(ARG_NAME_PPID_LIST + "=" + arrayToString(ppidList));
        args.add(ARG_NAME_PID_LIST + "=" + arrayToString(pidList));
        args.add(ARG_NAME_UID_LIST + "=" + arrayToString(uidList));

        if (outputFilePath != null && !outputFilePath.isEmpty()) args.add(ARG_NAME_OUTPUT_FILE_PATH + "=" + outputFilePath);
        if (outputIP != null && !outputIP.isEmpty()) args.add(ARG_NAME_OUTPUT_IP + "=" + outputIP);
        if (outputPort > 0) args.add(ARG_NAME_OUTPUT_PORT + "=" + outputPort);

        return args;
    }

    public String buildArgumentString() {
        return String.join(" ", buildArgumentArray());
    }

    private String arrayToString(int[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length && array[i] != 0; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        return sb.toString();
    }

    private static int[] convertSetToIntArray(Set<String> stringSet) {
        return stringSet.stream()
            .mapToInt(Integer::parseInt)
            .toArray();
    }

    public static Arguments create(
        final AuditConfiguration auditConfiguration,
        final ProcessUserSyscallFilter processUserSyscallFilter,
        final Config amebaConfig
    ) throws Exception {
        final Arguments amebaArgs = 
            new Arguments.Builder()
            .setGlobalMode(Mode.CAPTURE)
            .setNetioMode(auditConfiguration.isNetIO() ? Mode.CAPTURE : Mode.IGNORE)
            .setOutputFilePath(amebaConfig.getOutputFilePath())
            .setOutputIP(amebaConfig.getOutputIP())
            .setOutputPort(amebaConfig.getOutputPort())
            .setOutputType(amebaConfig.getOutputType())
            .setPidList(convertSetToIntArray(processUserSyscallFilter.getPidsOfProcessesToIgnore()))
            .setPidMode(Mode.IGNORE)
            .setPpidList(convertSetToIntArray(processUserSyscallFilter.getPpidsOfProcessesToIgnore()))
            .setPpidMode(Mode.IGNORE)
            .setUidList(convertSetToIntArray(Set.of(processUserSyscallFilter.getUserId())))
            .setUidMode(processUserSyscallFilter.getUserMode() == UserMode.CAPTURE ? Mode.CAPTURE : Mode.IGNORE)
            .build();
        return amebaArgs;
    }
}

