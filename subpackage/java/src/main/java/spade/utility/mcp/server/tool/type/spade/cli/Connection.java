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

package spade.utility.mcp.server.tool.type.spade.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class Connection implements AutoCloseable {

    private final String host;
    private final int port;
    private final File serverPublicKeystorePath;
    private final File clientPrivateKeystorePath;
    private final char[] passwordPublicKeystore;
    private final char[] passwordPrivateKeystore;

    private SSLSocket socket;
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
        this.host = host;
        this.port = port;
        this.serverPublicKeystorePath = serverPublicKeystorePath;
        this.clientPrivateKeystorePath = clientPrivateKeystorePath;
        this.passwordPublicKeystore = passwordPublicKeystore;
        this.passwordPrivateKeystore = passwordPrivateKeystore;
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
        this.out = new PrintStream(this.socket.getOutputStream());
        this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

    }

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
        if (this.socket != null) {
            try { this.socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

}
