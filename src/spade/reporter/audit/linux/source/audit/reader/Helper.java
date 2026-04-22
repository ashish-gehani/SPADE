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
package spade.reporter.audit.linux.source.audit.reader;

import spade.reporter.audit.core.util.channel.Unidirectional;
import spade.reporter.audit.linux.source.audit.event.Event;
import spade.reporter.audit.linux.source.audit.event.Factory;

/**
 * Wires together the full audit-reading pipeline and returns a
 * ready-to-start {@link BufferedEventReader}.
 *
 * Pipeline: {@link spade.reporter.audit.linux.source.audit.reader.LineReader} →
 * {@link RecordReader} → {@link EventReader} → {@link BufferedEventReader}
 */
public class Helper {

    public Helper() {}

    public EventReader createEventReader(final Config config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be NULL");
        }
        final spade.reporter.audit.linux.source.audit.reader.LineReader lineReader =
            new spade.reporter.audit.linux.source.audit.reader.Factory().create(
                config.getLineReaderType(), config
            );
        final RecordReader recordReader = new RecordReader(
            lineReader,
            new spade.reporter.audit.linux.source.audit.event.record.Factory(config.isRecordFactoryVerbose())
        );
        return new EventReader(
            new Factory(config.isEventFactoryVerbose()),
            recordReader
        );
    }

    public BufferedEventReader createReader(final Config config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be NULL");
        }
        final EventReader eventReader = createEventReader(config);
        final Unidirectional<Event> channel = new Unidirectional<>(config.getChannelConfig());
        return new BufferedEventReader(eventReader, channel, config);
    }

}
