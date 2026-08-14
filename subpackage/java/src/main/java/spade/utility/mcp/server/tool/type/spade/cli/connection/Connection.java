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

package spade.utility.mcp.server.tool.type.spade.cli.connection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public abstract class Connection implements AutoCloseable {

    protected final DataType connectionDataType;
    protected final String host;
    protected final int port;
    protected final File serverPublicKeystorePath;
    protected final File clientPrivateKeystorePath;
    protected final char[] passwordPublicKeystore;
    protected final char[] passwordPrivateKeystore;

    protected SSLSocket socket;

    protected Connection(
        final DataType connectionDataType,
        final String host,
        final int port,
        final File serverPublicKeystorePath,
        final File clientPrivateKeystorePath,
        final char[] passwordPublicKeystore,
        final char[] passwordPrivateKeystore
    ) {
        this.connectionDataType = connectionDataType;
        this.host = host;
        this.port = port;
        this.serverPublicKeystorePath = serverPublicKeystorePath;
        this.clientPrivateKeystorePath = clientPrivateKeystorePath;
        this.passwordPublicKeystore = passwordPublicKeystore;
        this.passwordPrivateKeystore = passwordPrivateKeystore;
    }

    public DataType getConnectionDataType() {
        return connectionDataType;
    }

    public void connect() throws Exception {
        final KeyStore serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(
            new FileInputStream(this.serverPublicKeystorePath),
            this.passwordPublicKeystore
        );

        final KeyStore clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(
            new FileInputStream(this.clientPrivateKeystorePath),
            this.passwordPrivateKeystore
        );

        final SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, this.passwordPrivateKeystore);

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);

        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        this.socket = (SSLSocket) sslSocketFactory.createSocket(this.host, this.port);
    }

    public abstract String send(final String command) throws Exception;

    @Override
    public void close() {
        if (this.socket != null) {
            try { this.socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

    public static Connection create(
        final DataType connectionDataType,
        final String host,
        final int port,
        final File serverPublicKeystorePath,
        final File clientPrivateKeystorePath,
        final char[] passwordPublicKeystore,
        final char[] passwordPrivateKeystore
    ) {
        if (connectionDataType == null) {
            throw new IllegalArgumentException("NULL connection data type");
        }

        switch (connectionDataType) {
            case STRING_LINE:
                return new spade.utility.mcp.server.tool.type.spade.cli.connection.string_line.Connection(
                    host, port, serverPublicKeystorePath, clientPrivateKeystorePath,
                    passwordPublicKeystore, passwordPrivateKeystore
                );
            case QUERY_OBJECT:
                return new spade.utility.mcp.server.tool.type.spade.cli.connection.query_object.Connection(
                    host, port, serverPublicKeystorePath, clientPrivateKeystorePath,
                    passwordPublicKeystore, passwordPrivateKeystore
                );
            default:
                throw new IllegalArgumentException("Unknown connection data type: " + connectionDataType);
        }
    }

}
