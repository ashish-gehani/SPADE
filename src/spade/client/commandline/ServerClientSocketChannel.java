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

package spade.client.commandline;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import spade.core.Settings;

/*
    Channel for secure client-server communication over SSL/TLS.
    Handles SSL/TLS connection setup and provides object streams for communication.
*/
public class ServerClientSocketChannel implements ServerClientChannel {

    private SSLSocket remoteSocket;
    private KeyStore clientKeyStorePrivate;
    private KeyStore serverKeyStorePublic;
    private SSLSocketFactory sslSocketFactory;

    private ObjectOutputStream serverInputStream;
    private ObjectInputStream serverOutputStream;

    private final String localHostName;
    private final String remoteHostName;

    public ServerClientSocketChannel(final String localHostName, final String remoteHostName)
        throws IllegalArgumentException {
        if (localHostName == null) {
            throw new IllegalArgumentException("Local host name cannot be null");
        }
        if (remoteHostName == null) {
            throw new IllegalArgumentException("Remote host name cannot be null");
        }
        this.localHostName = localHostName;
        this.remoteHostName = remoteHostName;
    }

    @Override
    public String getLocalHostName() {
        return localHostName;
    }

    @Override
    public String getRemoteHostName() {
        return remoteHostName;
    }

    /**
     * Sets up the keystores required for SSL connection.
     *
     * @throws Exception if keystore setup fails
     */
    private void setupKeyStores() throws Exception {
        final String serverPublicPath = Settings.getServerPublicKeystorePath();
        final String clientPrivatePath = Settings.getClientPrivateKeystorePath();

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(serverPublicPath),
                                  Settings.getPasswordPublicKeystoreAsCharArray());

        clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(new FileInputStream(clientPrivatePath),
                                   Settings.getPasswordPrivateKeystoreAsCharArray());
    }

    /**
     * Sets up the SSL context for secure communication.
     *
     * @throws Exception if SSL context setup fails
     */
    private void setupClientSSLContext() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, Settings.getPasswordPrivateKeystoreAsCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);

        sslSocketFactory = sslContext.getSocketFactory();
    }

    /**
     * Establishes a secure connection to the SPADE server.
     *
     * @throws Exception if connection setup or keystore/SSL initialization fails
     */
    @Override
    public void connect() throws Exception {
        setupKeyStores();
        setupClientSSLContext();

        try {
            final String host = getRemoteHostName();
            final int port = Settings.getCommandLineQueryPort();

            remoteSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();

            serverOutputStream = new ObjectInputStream(inStream);
            serverInputStream = new ObjectOutputStream(outStream);
        } catch (NumberFormatException | IOException ex) {
            close();
            throw new Exception("Error connecting to SPADE: " + ex.getMessage() +
                              ". Make sure that the CommandLine analyzer is running.", ex);
        }
    }

    @Override
    public ObjectOutputStream getServerInputStream() {
        return serverInputStream;
    }

    @Override
    public ObjectInputStream getServerOutputStream() {
        return serverOutputStream;
    }

    /**
     * Gets the underlying SSL socket.
     *
     * @return The SSL socket connection
     */
    public SSLSocket getSocket() {
        return remoteSocket;
    }

    @Override
    public boolean isConnected() {
        return remoteSocket != null && !remoteSocket.isClosed() && remoteSocket.isConnected();
    }

    @Override
    public void sendToServer(final Object obj) throws IOException, IllegalStateException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server");
        }
        if (obj == null) {
            throw new IllegalArgumentException("Object to send cannot be null");
        }
        serverInputStream.writeObject(obj);
        serverInputStream.flush();
    }

    @Override
    public Object recvFromServer() throws IOException, ClassNotFoundException, IllegalStateException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server");
        }
        return serverOutputStream.readObject();
    }

    private void cleanup() {
        if (serverInputStream != null) {
            try {
                serverInputStream.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        if (serverOutputStream != null) {
            try {
                serverOutputStream.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        if (remoteSocket != null) {
            try {
                remoteSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Override
    public void close() {
        cleanup();
    }

}
