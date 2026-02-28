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

package spade.client.commandline.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestFile {

    private static final String TEMP_FILE_PREFIX = "spade-commandline-test-file";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    private final String path;

    public TestFile(final String path) throws IllegalArgumentException {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        this.path = path;
    }

    public static TestFile createTemp() throws IOException {
        final String path = File.createTempFile(
            TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX
        ).getCanonicalPath();
        return new TestFile(path);
    }

    public String getPath() {
        return path;
    }

    public void create() throws IOException {
        final File file = new File(path);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw new IOException("Failed to create file: " + path);
            }
        }
    }

    public void delete() throws IOException {
        final File file = new File(path);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete file: " + path);
            }
        }
    }

    public void writeLines(final List<String> lines) throws IOException {
        if (lines == null) {
            throw new IllegalArgumentException("Lines cannot be null");
        }
        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (final String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    public List<String> readLines() throws IOException {
        final List<String> lines = new ArrayList<String>();
        try (final BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

}
