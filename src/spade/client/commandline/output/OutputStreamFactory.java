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

import java.io.FileNotFoundException;
import java.io.IOException;

public class OutputStreamFactory {

    private OutputStreamFactory() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a FileOutputStream for the given filepath.
     *
     * @param filepath The path to the file
     * @return A FileOutputStream instance
     * @throws IllegalArgumentException if filepath is null
     * @throws FileNotFoundException if the file cannot be created
     * @throws IOException if an I/O error occurs
     */
    public static FileOutputStream createFileOutputStream(final String filepath)
            throws IllegalArgumentException, FileNotFoundException, IOException {
        return new FileOutputStream(filepath);
    }

    /**
     * Creates a StandardOutputStream that writes to System.out.
     *
     * @return A StandardOutputStream instance
     */
    public static OutputStream createStandardOutputStream() {
        return new StandardOutputStream();
    }

    /**
     * Creates an OutputStream based on the type specified.
     *
     * @param type The type of output stream to create
     * @param filepath The filepath (required only for FILE type, ignored for STANDARD_OUT)
     * @return An OutputStream instance
     * @throws IllegalArgumentException if type is null, or if filepath is null when type is FILE
     * @throws FileNotFoundException if the file cannot be created (for FILE type)
     * @throws IOException if an I/O error occurs
     */
    public static OutputStream create(final Type type, final String filepath)
            throws IllegalArgumentException, FileNotFoundException, IOException {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        switch (type) {
            case FILE:
                return createFileOutputStream(filepath);
            case STANDARD_OUT:
                return createStandardOutputStream();
            default:
                throw new IllegalArgumentException("Unknown output stream type: " + type);
        }
    }

    /**
     * Creates an OutputStream based on the type specified.
     * For STANDARD_OUT type, filepath is not required.
     *
     * @param type The type of output stream to create
     * @return An OutputStream instance
     * @throws IllegalArgumentException if type is null or if type is FILE (filepath required)
     * @throws FileNotFoundException if the file cannot be created (for FILE type)
     * @throws IOException if an I/O error occurs
     */
    public static OutputStream create(final Type type)
            throws IllegalArgumentException, FileNotFoundException, IOException {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        switch (type) {
            case STANDARD_OUT:
                return createStandardOutputStream();
            case FILE:
                throw new IllegalArgumentException("Filepath is required for FILE type output stream");
            default:
                throw new IllegalArgumentException("Unknown output stream type: " + type);
        }
    }

}
