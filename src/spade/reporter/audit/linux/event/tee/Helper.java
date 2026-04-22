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
package spade.reporter.audit.linux.event.tee;

import spade.reporter.audit.linux.event.reader.BufferedEventReader;
import spade.reporter.audit.linux.event.writer.EventWriter;

/**
 * Wires together the full audit tee pipeline and returns a
 * ready-to-start {@link Tee}.
 *
 * Pipeline: {@link spade.reporter.audit.linux.event.reader.BufferedEventReader}
 * → {@link Tee} → {@link spade.reporter.audit.linux.source.output.EventWriter}
 * (caller pulls events from the {@link Tee}).
 */
public class Helper {

    public Helper() {}

    public Tee createTee(final Config config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be NULL");
        }
        final BufferedEventReader reader =
            new spade.reporter.audit.linux.event.reader.Helper().createReader(
                config.getReaderConfig()
            );
        final EventWriter writer =
            new spade.reporter.audit.linux.event.writer.Helper().createWriter(
                config.getWriterConfig()
            );
        return new Tee(reader, writer, config.isVerbose());
    }

}
