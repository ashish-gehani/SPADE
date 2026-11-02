/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International
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

package spade.external.com.bbn.tc.schema.avro.cdm20;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.bbn.tc.schema.avro.cdm20.InstrumentationSource;
import com.bbn.tc.schema.avro.cdm20.RecordType;
import com.bbn.tc.schema.avro.cdm20.TCCDMDatum;
import com.bbn.tc.schema.avro.cdm20.TimeMarker;
import com.bbn.tc.schema.avro.cdm20.UUID;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spade.storage.kafka.FileWriter;

public class CDMFileOutputTest {

    @Test
    public void writesAndReadsCdmRecord(@TempDir final Path tempDir) throws Exception {
        final TCCDMDatum record = createRecord();
        final Path output = tempDir.resolve("record.avro");

        writeRecord(record, output);
        assertRecordMatches(record, output);
    }

    private static TCCDMDatum createRecord() {
        return TCCDMDatum.newBuilder()
            .setDatum(new TimeMarker(123456789L))
            .setSource(InstrumentationSource.SOURCE_LINUX_SYSCALL_TRACE)
            .setHostId(new UUID(new byte[16]))
            .setSessionNumber(1)
            .setType(RecordType.RECORD_TIME_MARKER)
            .build();
    }

    private static void writeRecord(final TCCDMDatum record, final Path output) throws Exception {
        final Path schema = Path.of(System.getProperty("basedir"), "..", "..", "cfg", "spade.storage.CDM.avsc");
        final FileWriter writer = new FileWriter(schema.toString(), output.toString());
        try {
            writer.writeRecord(record);
        } finally {
            writer.close();
        }
    }

    private static void assertRecordMatches(final TCCDMDatum expected, final Path output) throws Exception {
        try (DataFileReader<TCCDMDatum> reader = new DataFileReader<>(output.toFile(),
                new SpecificDatumReader<TCCDMDatum>(TCCDMDatum.getClassSchema()))) {
            assertTrue(reader.hasNext());
            final TCCDMDatum actual = reader.next();
            assertEquals(((TimeMarker) expected.getDatum()).getTsNanos(),
                ((TimeMarker) actual.getDatum()).getTsNanos());
            assertEquals(expected.getType(), actual.getType());
            assertEquals(expected.getSource(), actual.getSource());
            assertEquals(expected.getHostId(), actual.getHostId());
            assertEquals(expected.getSessionNumber(), actual.getSessionNumber());
            assertFalse(reader.hasNext());
        }
    }
}
