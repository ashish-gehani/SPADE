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

import spade.reporter.audit.linux.audit.output.writer.Type;

public class Config {

    private final String filePath;
    private final Type lineWriterType;
    private final long rotationBytes;

    public Config(
        final String filePath,
        final Type lineWriterType,
        final long rotationBytes
    ) {
        if (lineWriterType == null) {
            throw new IllegalArgumentException("Line writer type cannot be NULL");
        }
        this.filePath = filePath;
        this.lineWriterType = lineWriterType;
        this.rotationBytes = rotationBytes;
    }

    public boolean hasFilePath() {
        return filePath != null;
    }

    public String getFilePath() {
        return filePath;
    }

    public Type getLineWriterType() {
        return lineWriterType;
    }

    public long getRotationBytes() {
        return rotationBytes;
    }

}
