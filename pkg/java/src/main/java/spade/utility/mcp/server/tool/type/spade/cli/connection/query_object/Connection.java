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

package spade.utility.mcp.server.tool.type.spade.cli.connection.query_object;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import spade.core.Query;
import spade.query.quickgrail.instruction.SaveGraph;
import spade.utility.mcp.server.tool.type.spade.cli.connection.DataType;
import spade.utility.query.Result;

public class Connection extends spade.utility.mcp.server.tool.type.spade.cli.connection.Connection {

    private static final String localHostName = "localhost";

    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;

    public Connection(
        final String host,
        final int port,
        final File serverPublicKeystorePath,
        final File clientPrivateKeystorePath,
        final char[] passwordPublicKeystore,
        final char[] passwordPrivateKeystore
    ) {
        super(
            DataType.QUERY_OBJECT,
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
        final OutputStream outStream = this.socket.getOutputStream();
        final InputStream inStream = this.socket.getInputStream();
        this.objectInputStream = new ObjectInputStream(inStream);
        this.objectOutputStream = new ObjectOutputStream(outStream);
    }

    @Override
    public String send(final String command) throws Exception {
        if (this.socket == null || this.socket.isClosed()) {
            throw new IllegalStateException("Not connected");
        }

        final String queryNonce = null; // null for local queries
        final Query request = new Query(localHostName, this.host, command, queryNonce);

        this.objectOutputStream.writeObject(request);
        this.objectOutputStream.flush();

        final Object responseObject = this.objectInputStream.readObject();
        if (responseObject == null) {
            throw new Exception("Server closed the connection");
        }

        return Result.format(Result.ensureQuery(responseObject), SaveGraph.Format.kJson);
    }

    @Override
    public void close() {
        if (this.objectInputStream != null) {
            try { this.objectInputStream.close(); } catch (IOException e) { /* ignore */ }
        }
        if (this.objectOutputStream != null) {
            try { this.objectOutputStream.close(); } catch (IOException e) { /* ignore */ }
        }
        super.close();
    }

}
