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

import java.util.Map;

import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class AmebaConfig {

    private final static long MIN_BUFFER_TTL = 5000;

    private final static String
        KEY_VERBOSE = "verbose",
        KEY_AMEBA_LOG_PATH = "ameba_log_path",
        KEY_AMEBA_BIN_PATH = "ameba_bin_path",
        KEY_OUTPUT_FILE_PATH = "output_file_path",
        KEY_OUTPUT_IP = "output_ip",
        KEY_OUTPUT_PORT = "output_port",
        KEY_OUTPUT_READER_TIMEOUT_MILLIS = "output_reader_timeout_millis",
        KEY_OUTPUT_BUFFER_SIZE = "output_buffer_size",
        KEY_OUTPUT_BUFFER_TTL = "output_buffer_ttl";

    private boolean verbose;
    private String amebaLogPath;
    private String amebaBinPath;
    private String outputFilePath;
    private String outputIP;
    private Integer outputPort;
    private AmebaOutputType outputType;
    private int outputReaderTimeoutMillis;
    private int outputBufferSize;
    private long outputBufferTtl;

    public AmebaConfig(final Map<String, String> configMap) throws Exception {
        final String strAmebaLogPath = configMap.get(AmebaConfig.KEY_AMEBA_LOG_PATH);
        final String strAmebaBinPath = configMap.get(AmebaConfig.KEY_AMEBA_BIN_PATH);
        final String strOutputFilePath = configMap.get(AmebaConfig.KEY_OUTPUT_FILE_PATH);
        final String strOutputIP = configMap.get(AmebaConfig.KEY_OUTPUT_IP);
        final String strOutputPort = configMap.get(AmebaConfig.KEY_OUTPUT_PORT);

        this.verbose = ArgumentFunctions.mustParseBoolean(AmebaConfig.KEY_VERBOSE, configMap);

        FileUtility.pathMustBeAReadableExecutableFile(strAmebaBinPath);
        this.amebaBinPath = strAmebaBinPath;

        this.outputReaderTimeoutMillis = ArgumentFunctions.mustParseNonNegativeInteger(
            AmebaConfig.KEY_OUTPUT_READER_TIMEOUT_MILLIS, configMap
        );

        this.outputBufferSize = ArgumentFunctions.mustParsePositiveInteger(
            AmebaConfig.KEY_OUTPUT_BUFFER_SIZE, configMap
        );

        this.outputBufferTtl = ArgumentFunctions.mustParseLong(
            AmebaConfig.KEY_OUTPUT_BUFFER_TTL, configMap, MIN_BUFFER_TTL
        );

        // Optional
        if (strAmebaLogPath != null) {
            FileUtility.pathMustBeAWritableFile(strAmebaLogPath);
        }
        this.amebaLogPath = strAmebaLogPath;

        if (
            (strOutputIP == null && strOutputPort != null) || 
            strOutputIP != null && strOutputPort == null
        ){
            throw new Exception("Must specify either 1) Both IP and port, or 2) No IP and no port.");
        }

        if (strOutputFilePath != null && strOutputIP != null) {
            throw new Exception("Must specify either 1) Both IP and port, or 2) File path.");
        }

        if (strOutputFilePath != null) {
            FileUtility.pathMustBeAWritableFile(strOutputFilePath);
            this.outputFilePath = strOutputFilePath;
            this.outputType = AmebaOutputType.FILE;
        }

        if (strOutputIP != null) {
            this.outputIP = ArgumentFunctions.mustParseHost(AmebaConfig.KEY_OUTPUT_IP, configMap);
            this.outputPort = ArgumentFunctions.mustParsePort(AmebaConfig.KEY_OUTPUT_PORT, configMap);
            this.outputType = AmebaOutputType.NET;
        }
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getAmebaLogPath() {
        return amebaLogPath;
    }

    public String getAmebaBinPath() {
        return amebaBinPath;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public String getOutputIP() {
        return outputIP;
    }

    public Integer getOutputPort() {
        return outputPort;
    }

    public AmebaOutputType getOutputType() {
        return this.outputType;
    }

    public int getOutputReaderTimeoutMillis() {
        return this.outputReaderTimeoutMillis;
    }

    public int getOutputBufferSize() {
        return this.outputBufferSize;
    }

    public long getOutputBufferTtl() {
        return this.outputBufferTtl;
    }

    public static AmebaConfig create() throws Exception {
        final AmebaConfig amebaConfig = new AmebaConfig(
            HelperFunctions.parseKeyValuePairsFrom(
                "", new String[]{
                    Settings.getDefaultConfigFilePath(
                        spade.reporter.audit.bpf.AmebaConfig.class
                    )
                }
            )
        );
        return amebaConfig;
    }
}
