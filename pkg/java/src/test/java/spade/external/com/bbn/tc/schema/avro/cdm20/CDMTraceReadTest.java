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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.bbn.tc.schema.avro.cdm20.TCCDMDatum;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

public class CDMTraceReadTest {

    // Opt-in: requires a locally supplied binary CDM trace, which is not shipped with the tests.
    @Test
    @EnabledIfSystemProperty(named = "spade.external.com.bbn.tc.schema.avro.cdm20.CDMTraceReadTest.readsEntireBinaryTrace", matches = ".+")
    public void readsEntireBinaryTrace() throws Exception {
        final Path input = Path.of(System.getProperty("spade.external.com.bbn.tc.schema.avro.cdm20.CDMTraceReadTest.readsEntireBinaryTrace"));
        final long count = readTrace(input, loadSchema());
        System.out.println("Successfully decoded entire trace: " + count + " CDM records");
        assertTrue(count > 0);
    }

    private static Schema loadSchema() throws java.io.IOException {
        final Path schemaPath = Path.of(System.getProperty("basedir"), "..", "..", "cfg", "spade.storage.CDM.avsc");
        return new Schema.Parser().parse(schemaPath.toFile());
    }

    private static long readTrace(final Path input, final Schema schema) throws java.io.IOException {
        long count = 0;
        try (DataFileReader<TCCDMDatum> reader = new DataFileReader<>(input.toFile(),
                new SpecificDatumReader<TCCDMDatum>(schema))) {
            while (reader.hasNext()) {
                final TCCDMDatum record = reader.next();
                assertNotNull(record.getDatum());
                count++;
                if (count % 1000000 == 0) {
                    System.out.println("Decoded " + count + " CDM records");
                }
            }
        } catch (Exception | LinkageError e) {
            throw new AssertionError("Trace read failed after " + count + " decoded records", e);
        }
        return count;
    }
}
