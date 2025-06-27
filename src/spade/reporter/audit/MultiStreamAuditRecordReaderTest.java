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

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Map;

import spade.core.Settings;
import spade.reporter.audit.bpf.AmebaConfig;
import spade.reporter.audit.bpf.AmebaOutputType;
import spade.reporter.audit.bpf.AmebaToAuditRecordStream;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

public class MultiStreamAuditRecordReaderTest {

    public static void main(String[] args) throws Exception {
        final Config config = Config.create();
        System.out.println(config);

        BufferedWriter mergedLogWriter = null;
        AmebaToAuditRecordStream stream1 = null;
        FileInputStream stream2 = null;
        MultiStreamAuditRecordReader mt = null;
        Thread t = null;

        try {

            final String mergedLog = config.outputLog;
            mergedLogWriter = new BufferedWriter(new FileWriter(mergedLog));

            final String amebaLogPath = config.inputAmebaLog;
            stream1 = AmebaToAuditRecordStream.create(
                AmebaConfig.create(
                    String.join(
                        " ",
                        new String[] {
                            AmebaConfig.KEY_OUTPUT_TYPE + "=" + AmebaOutputType.FILE.toString(),
                            AmebaConfig.KEY_OUTPUT_FILE_PATH + "=" + amebaLogPath
                        }
                    )
                )
            );
            final String auditLogPath = config.inputAuditLog;
            stream2 = new FileInputStream(auditLogPath);

            mt = MultiStreamAuditRecordReader.create(
                stream1, stream2
            );

            t = new Thread(() -> {
                try {
                    Thread.sleep(30_000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            t.start();

            while (true) {
                AuditRecord record;
                try {
                    record = mt.read();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    continue;
                }
                if (record == null)
                    break;
                mergedLogWriter.write(record.toRawForm());
                mergedLogWriter.newLine();
            }

            t.join();
        } finally {
            if (mergedLogWriter != null) try { mergedLogWriter.close(); } catch (Exception e) {}
            if (mt != null) try { mt.close(); } catch (Exception e) {}
        }
    }

    private static class Config {

        private static final String
            KEY_INPUT_AUDIT_LOG = "inputAuditLog",
            KEY_INPUT_AMEBA_LOG = "inputAmebaLog",
            KEY_OUTPUT_LOG = "outputLog";

        private final String
            inputAuditLog,
            inputAmebaLog,
            outputLog;

        private Config(Map<String, String> map) throws Exception {
            this.inputAuditLog = ArgumentFunctions.mustParseReadableFilePath(KEY_INPUT_AUDIT_LOG, map);
            this.inputAmebaLog = ArgumentFunctions.mustParseReadableFilePath(KEY_INPUT_AMEBA_LOG, map);
            this.outputLog = ArgumentFunctions.mustParseWritableFilePath(KEY_OUTPUT_LOG, map);
        }

        public String toString() {
            return String.format(
                "[%s=%s %s=%s, %s=%s, %s=%s]",
                "class", this.getClass().toString(),
                KEY_INPUT_AUDIT_LOG, inputAuditLog,
                KEY_INPUT_AMEBA_LOG, inputAmebaLog,
                KEY_OUTPUT_LOG, outputLog
            );
        }

        private static Config create() throws Exception {
            return Config.create("");
        }

        private static Config create(final String arguments) throws Exception {
            final Config config = new Config(
                HelperFunctions.parseKeyValuePairsFrom(
                    arguments,
                    new String[] {
                        Settings.getDefaultConfigFilePath(
                            MultiStreamAuditRecordReaderTest.class
                        )
                    }
                )
            );
            return config;
        }
    }
}
