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

import java.io.IOException;

public interface OutputStream {

    /*
        Function write out a plain string to the output stream.
    */
    void writeString(final String str) throws IllegalArgumentException, IOException, Exception;

    /*
        Function write out a SPADE graph to the output stream.
    */
    void writeGraph(final spade.core.Graph graph) throws IllegalArgumentException, IOException, Exception;

    /*
        Function to close the output stream.
    */
    void close() throws IOException;

    /*
        Function to check if the output stream is closed.
    */
    boolean isClosed();

    /*
        Function to get the type of the output stream.
    */
    Type getType();

}
