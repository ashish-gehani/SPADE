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

package spade.utility.mcp.connection;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Level;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import spade.core.Settings;
import spade.utility.mcp.Server;

public class SPADEControl implements AutoCloseable {

    private final String host;
    private final int port;

    private SSLSocket socket;
    private PrintStream out;
    private BufferedReader in;

    public SPADEControl(final String host, final int port) {
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
