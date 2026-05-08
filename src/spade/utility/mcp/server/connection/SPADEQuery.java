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

package spade.utility.mcp.server.connection;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import spade.core.Query;
import spade.core.Settings;

public class SPADEQuery implements AutoCloseable {

    private final String host;
    private final int port;

    private SSLSocket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public SPADEQuery(final String host, final int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws Exception {
        final KeyStore serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(
            new FileInputStream(Settings.getServerPublicKeystorePath()),
            Settings.getPasswordPublicKeystoreAsCharArray()
        );

        final KeyStore clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(
            new FileInputStream(Settings.getClientPrivateKeystorePath()),
            Settings.getPasswordPrivateKeystoreAsCharArray()
        );

        final SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, Settings.getPasswordPrivateKeystoreAsCharArray());

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);

        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        this.socket = (SSLSocket) sslSocketFactory.createSocket(this.host, port);
        this.out = new ObjectOutputStream(this.socket.getOutputStream());
        this.in = new ObjectInputStream(this.socket.getInputStream());
    }

    public Query query(final String queryStr) throws Exception {
        if (this.socket == null || this.socket.isClosed()) {
            throw new IllegalStateException("Not connected");
        }
        final Query request = new Query(this.host, this.host, queryStr, null);
        this.out.writeObject(request);
        this.out.flush();

        final Object response = this.in.readObject();
        if (response == null) {
            throw new Exception("Server closed the connection");
        }
        if (!(response instanceof Query)) {
            throw new Exception("Unexpected response type: " + response.getClass().getName());
        }
        return (Query) response;
    }

    @Override
    public void close() {
        if (this.in != null) {
            try { this.in.close(); } catch (IOException e) { /* ignore */ }
        }
        if (this.out != null) {
            try { this.out.close(); } catch (IOException e) { /* ignore */ }
        }
        if (this.socket != null) {
            try { this.socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

}
