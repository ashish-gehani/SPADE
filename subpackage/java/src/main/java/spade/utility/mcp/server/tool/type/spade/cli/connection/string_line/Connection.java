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

package spade.utility.mcp.server.tool.type.spade.cli.connection.string_line;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import spade.utility.mcp.server.tool.type.spade.cli.connection.DataType;

public class Connection extends spade.utility.mcp.server.tool.type.spade.cli.connection.Connection {

    private PrintStream out;
    private BufferedReader in;

    public Connection(
        final String host,
        final int port,
        final File serverPublicKeystorePath,
        final File clientPrivateKeystorePath,
        final char[] passwordPublicKeystore,
        final char[] passwordPrivateKeystore
    ) {
        super(
            DataType.STRING_LINE,
            host,
            port,
            serverPublicKeystorePath,
            clientPrivateKeystorePath,
            passwordPublicKeystore,
            passwordPrivateKeystore
        );
    }

    @Override
    public void connect() throws Exception {
        super.connect();
        this.out = new PrintStream(this.socket.getOutputStream());
        this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
    }

    @Override
    public String send(final String command) throws Exception {
        if (this.socket == null || this.socket.isClosed()) {
            throw new IllegalStateException("Not connected");
        }
        this.out.println(command);
        return readResponse();
    }

    private String readResponse() throws Exception {
        final StringBuilder sb = new StringBuilder();
        String line;
        while ((line = this.in.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    @Override
    public void close() {
        if (this.in != null) {
            try { this.in.close(); } catch (IOException e) { /* ignore */ }
        }
        if (this.out != null) {
            this.out.close();
        }
        super.close();
    }

}
