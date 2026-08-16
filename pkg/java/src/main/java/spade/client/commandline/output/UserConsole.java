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

package spade.client.commandline.output;

import java.io.BufferedWriter;

import spade.core.Graph;

/*
    User output backed by standard output and an optional file stream.
    Errors are reported to a provided stderr writer.
*/
public class UserConsole implements User {

    private final OutputStream standardOutputStream;
    private final BufferedWriter stdErrWriter;
    private FileOutputStream fileOutputStream;
    private final boolean isBatchMode;

    public UserConsole(
        final OutputStream standardOutputStream,
        final BufferedWriter stdErrWriter,
        final boolean isBatchMode
    ) throws IllegalArgumentException {
        if (standardOutputStream == null) {
            throw new IllegalArgumentException("Null standard output stream");
        }
        if (stdErrWriter == null) {
            throw new IllegalArgumentException("Null standard error writer");
        }
        this.standardOutputStream = standardOutputStream;
        this.stdErrWriter = stdErrWriter;
        this.fileOutputStream = null;
        this.isBatchMode = isBatchMode;
    }

    @Override
    public synchronized void openFile(final String filepath) throws IllegalArgumentException {
        if (filepath == null) {
            throw new IllegalArgumentException("Filepath cannot be null");
        }
        try {
            if (isFileOpen()) {
                this.fileOutputStream.close();
            }
            this.fileOutputStream = OutputStreamFactory.createFileOutputStream(filepath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to open file '" + filepath + "': " + e.getMessage(), e);
        }
    }

    private synchronized void closeFile() {
        if (isFileOpen()) {
            try {
                this.fileOutputStream.close();
            } catch (Exception e) {
                final String eMsg = (
                    "Failed to close file '"
                    + this.fileOutputStream.getFilepath()
                    + "' gracefully. Error: " + e.getMessage()
                );
                try {
                    this.stdErrWriter.write(eMsg);
                } catch (Exception eIgnore) {
                    // Ignore. If we cannot even write to stderr then we cannot do anything.
                }
            }
            try {
                this.standardOutputStream.writeString(
                    "Output exported to file: "
                    + this.fileOutputStream.getFilepath()
                    + System.lineSeparator()
                );
            } catch (Exception e) {
                // Ignore
            }
        }
        this.fileOutputStream = null;
    }

    private synchronized OutputStream getActiveOutputStream() {
        if (isFileOpen()) {
            return this.fileOutputStream;
        }
        return this.standardOutputStream;
    }

    public void writeProgramHeader(final String localHostName) {
        if (!this.isBatchMode) {
            writeStringLn("");
            writeStringLn("Host '" + localHostName + "': SPADE Query Client");
        }
    }

    @Override
    public synchronized void writeString(final String str) {
        try {
            getActiveOutputStream().writeString(str);
        } catch (Exception eOuter) {
            final String msg = (
                "Failed to write str. Error: "
                + eOuter.getMessage()
                + System.lineSeparator()
            );
            try {
                this.stdErrWriter.write(msg);
            } catch (Exception e) {
                // Ignore. If we cannot even write to stderr then we cannot do anything.
            }
        } finally {
            closeFile();
        }
    }

    @Override
    public synchronized void writeStringLn(final String str) {
        final String s = String.valueOf(str) + System.lineSeparator();
        this.writeString(s);
    }

    @Override
    public synchronized void writeGraph(final Graph graph) {
        try {
            getActiveOutputStream().writeGraph(graph);
        } catch (Exception eOuter) {
            final String msg = (
                "Failed to write graph. Error: "
                + eOuter.getMessage()
                + System.lineSeparator()
            );
            try {
                this.stdErrWriter.write(msg);
            } catch (Exception e) {
                // Ignore. If we cannot even write to stderr then we cannot do anything.
            }
        } finally {
            closeFile();
        }
    }

    private synchronized boolean isFileOpen() {
        return this.fileOutputStream != null && !this.fileOutputStream.isClosed();
    }

    @Override
    public void close() {
        // Don't want to close stdout and stderr. They are global to Java process.
        closeFile();
    }

}
