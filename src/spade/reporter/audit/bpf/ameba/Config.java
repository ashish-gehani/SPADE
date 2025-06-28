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

import java.util.Map;

import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class Config {

    private final static long MIN_BUFFER_TTL = 5000;

    public final static String
        KEY_VERBOSE = "verbose",
        KEY_AMEBA_LOG_PATH = "ameba_log_path",
        KEY_AMEBA_BIN_PATH = "ameba_bin_path",
        KEY_OUTPUT_TYPE = "output_type",
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
    private OutputType outputType;
    private int outputReaderTimeoutMillis;
    private int outputBufferSize;
    private long outputBufferTtl;

    public Config(final Map<String, String> configMap) throws Exception {
        this.verbose = ArgumentFunctions.mustParseBoolean(Config.KEY_VERBOSE, configMap);

        final String strAmebaBinPath = configMap.get(Config.KEY_AMEBA_BIN_PATH);
        FileUtility.pathMustBeAReadableExecutableFile(strAmebaBinPath);
        this.amebaBinPath = strAmebaBinPath;

        this.outputReaderTimeoutMillis = ArgumentFunctions.mustParseNonNegativeInteger(
            Config.KEY_OUTPUT_READER_TIMEOUT_MILLIS, configMap
        );

        this.outputBufferSize = ArgumentFunctions.mustParsePositiveInteger(
            Config.KEY_OUTPUT_BUFFER_SIZE, configMap
        );

        this.outputBufferTtl = ArgumentFunctions.mustParseLong(
            Config.KEY_OUTPUT_BUFFER_TTL, configMap, MIN_BUFFER_TTL
        );

        this.outputType = ArgumentFunctions.mustParseEnum(
            OutputType.class, Config.KEY_OUTPUT_TYPE, configMap
        );

        if (this.outputType == OutputType.NET) {
            this.outputIP = ArgumentFunctions.mustParseHost(Config.KEY_OUTPUT_IP, configMap);
            this.outputPort = ArgumentFunctions.mustParsePort(Config.KEY_OUTPUT_PORT, configMap);
        } else if (this.outputType == OutputType.FILE) {
            final String strOutputFilePath = configMap.get(Config.KEY_OUTPUT_FILE_PATH);
            FileUtility.pathMustBeAWritableFile(strOutputFilePath);
            this.outputFilePath = strOutputFilePath;
        } else {
            throw new Exception("Unexpected output type: " + this.outputType);
        }

        // Optional
        final String strAmebaLogPath = configMap.get(Config.KEY_AMEBA_LOG_PATH);
        if (strAmebaLogPath != null) {
            FileUtility.pathMustBeAWritableFile(strAmebaLogPath);
        }
        this.amebaLogPath = strAmebaLogPath;
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

    public OutputType getOutputType() {
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

    public static Config create() throws Exception {
        return Config.create("");
    }

    public static Config create(final String arguments) throws Exception {
        final Config amebaConfig = new Config(
            HelperFunctions.parseKeyValuePairsFrom(
                arguments,
                new String[]{
                    Settings.getDefaultConfigFilePath(
                        spade.reporter.audit.bpf.ameba.Config.class
                    )
                }
            )
        );
        return amebaConfig;
    }
}
