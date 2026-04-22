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
package spade.reporter.audit.linux.source.audit.writer;

public class Factory {

    public static LineWriter create(final Config config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be NULL");
        }
        switch (config.getLineWriterType()) {
            case FILE:
                return new spade.reporter.audit.linux.source.audit.writer.type.file.LineWriter(
                    config.getFilePath()
                );
            case ROTATING_FILE:
                return new spade.reporter.audit.linux.source.audit.writer.type.rotating.file.LineWriter(
                    config.getFilePath(),
                    config.getRotationBytes()
                );
            case NO_OP:
                return new spade.reporter.audit.linux.source.audit.writer.type.noop.LineWriter();
            default:
                throw new IllegalArgumentException("Unknown line writer type: " + config.getLineWriterType());
        }
    }

}
