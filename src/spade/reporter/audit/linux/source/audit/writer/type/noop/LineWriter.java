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
package spade.reporter.audit.linux.source.audit.writer.type.noop;

import spade.reporter.audit.linux.source.audit.writer.Type;

/**
 * A {@link spade.reporter.audit.linux.event.writer.output.writer.LineWriter} that
 * silently discards all lines.
 */
public class LineWriter extends spade.reporter.audit.linux.source.audit.writer.LineWriter {

    public LineWriter() {
        super(Type.NO_OP);
    }

    @Override
    public long writeLine(final String line) throws Exception {
        return 0;
    }

    @Override
    public void close() throws Exception {
        // no-op
    }

}
