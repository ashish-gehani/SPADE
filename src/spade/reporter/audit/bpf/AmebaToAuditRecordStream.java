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

import spade.reporter.audit.AuditRecord;

public class AmebaToAuditRecordStream {
    
    private final AmebaToAuditConverter converter;

    private final AmebaOutputBuffer buffer;

    public AmebaToAuditRecordStream(final AmebaToAuditConverter converter, final AmebaOutputBuffer buffer) {
        this.converter = converter;
        this.buffer = buffer;
    }

    public AuditRecord read() throws Exception {
        AuditRecord convertedRecord = null;
        while (true) {
            final AmebaRecord record = this.buffer.poll();
            if (record == null) {
                return null;
            }
            convertedRecord = converter.convert(buffer, record);
            if (convertedRecord != null) {
                return convertedRecord;
            }
        }
    }

    public int getBufferCurrentSize() {
        return this.buffer.getCurrentSize();
    }

    public void close() throws Exception {
        this.buffer.close();
    }

    public static AmebaToAuditRecordStream create(final AmebaConfig config) throws Exception {
        return new AmebaToAuditRecordStream(
            AmebaToAuditConverter.create(),
            AmebaOutputBuffer.create(config)
        );
    }

    public static AmebaToAuditRecordStream createNullStream() {
        return new NULLAmebaToAuditRecordStream();
    }

    private static class NULLAmebaToAuditRecordStream extends AmebaToAuditRecordStream {
        public NULLAmebaToAuditRecordStream () { 
            super(null, null);
        }
        @Override
        public AuditRecord read () throws Exception {
            return null;
        }
        @Override
        public int getBufferCurrentSize () {
            return 0;
        }
        @Override
        public void close () throws Exception {
            return;
        }
    }
}
