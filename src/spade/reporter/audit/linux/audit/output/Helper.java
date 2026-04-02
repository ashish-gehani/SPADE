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
package spade.reporter.audit.linux.audit.output;

import spade.reporter.audit.linux.audit.output.writer.Factory;
import spade.reporter.audit.linux.audit.output.writer.LineWriter;

/**
 * Wires together the full audit-writing pipeline and returns a
 * ready-to-use {@link EventWriter}.
 *
 * Pipeline: {@link LineWriter} → {@link RecordWriter} → {@link EventWriter}
 */
public class Helper {

    public Helper() {}

    public EventWriter createWriter(final Config config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be NULL");
        }
        final LineWriter lineWriter = Factory.create(config);
        final RecordWriter recordWriter = new RecordWriter(lineWriter);
        return new EventWriter(recordWriter, config.getSnapshotIntervalMs());
    }

}
