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
package spade.reporter.audit;

import java.util.Map;

import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

public class MultiStreamAuditRecordReaderConfig {

    private final static long MIN_BUFFER_TTL = 5000;
    private final static long MIN_IO_TIMEOUT = 5000;

    private final static String
        KEY_BUFFER_SIZE = "buffer_size",
        KEY_BUFFER_TTL = "buffer_ttl",
        KEY_IO_TIMEOUT = "io_timeout";

    private final int bufferSize;
    private final long bufferTtl;
    private final long ioTimeout;

    public MultiStreamAuditRecordReaderConfig(final Map<String, String> configMap) throws Exception {
        this.bufferSize = ArgumentFunctions.mustParsePositiveInteger(
            KEY_BUFFER_SIZE, configMap
        );

        this.bufferTtl = ArgumentFunctions.mustParseLong(
            KEY_BUFFER_TTL, configMap, MIN_BUFFER_TTL
        );

        this.ioTimeout = ArgumentFunctions.mustParseLong(
            KEY_IO_TIMEOUT, configMap, MIN_IO_TIMEOUT
        );
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public long getBufferTtl() {
        return bufferTtl;
    }

    public long getIOTimeout() {
        return ioTimeout;
    }

    @Override
    public String toString() {
        return "MultiStreamAuditRecordReaderConfig{" +
                "bufferSize=" + bufferSize +
                ", bufferTtl=" + bufferTtl +
                ", ioTimeout=" + ioTimeout +
                '}';
    }

    public static MultiStreamAuditRecordReaderConfig create() throws Exception {
        return MultiStreamAuditRecordReaderConfig.create("");
    }

    public static MultiStreamAuditRecordReaderConfig create(final String arguments) throws Exception {
        final Map<String, String> map = HelperFunctions.parseKeyValuePairsFrom(
            arguments,
            new String[]{
                Settings.getDefaultConfigFilePath(
                    spade.reporter.audit.MultiStreamAuditRecordReader.class
                )
            }
        );
        final MultiStreamAuditRecordReaderConfig config = new MultiStreamAuditRecordReaderConfig(
            map
        );
        return config;
    }
}

