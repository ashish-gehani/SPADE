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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import spade.client.commandline.ServerClientChannel;

/**
 * @author Claude code
 * 
 * Test implementation of {@link ServerClientChannel} backed by in-memory piped
 * object streams. No socket or network connection is involved.
 *
 * <p>Two pipe pairs are set up at construction time:
 * <ul>
 *   <li><b>client → server</b>: the client writes via {@link #sendToServer};
 *       tests read what was sent via {@link #getServerRequestStream}.</li>
 *   <li><b>server → client</b>: tests inject responses via
 *       {@link #getServerResponseStream}; the client reads them via
 *       {@link #readFromServer}.</li>
 * </ul>
 */
public class ServerClientTestChannel implements ServerClientChannel {

    private final String localHostName;
    private final String remoteHostName;

    /*
        Client → server pipe.
        Client writes here; test reads via serverRequestStream.
    */
    private final ObjectOutputStream serverInputStream;
    private final ObjectInputStream serverRequestStream;

    /*
        Server → client pipe.
        Test writes here via serverResponseStream; client reads via serverOutputStream.
    */
    private final ObjectInputStream serverOutputStream;
    private final ObjectOutputStream serverResponseStream;

    private boolean connected = false;

    public ServerClientTestChannel(
        final String localHostName,
        final String remoteHostName
    ) throws IOException, IllegalArgumentException {
        if (localHostName == null) {
            throw new IllegalArgumentException("Local host name cannot be null");
        }
        if (remoteHostName == null) {
            throw new IllegalArgumentException("Remote host name cannot be null");
        }
        this.localHostName = localHostName;
        this.remoteHostName = remoteHostName;

        // Client → server: ObjectOutputStream header must be written before
        // ObjectInputStream is constructed so there is no deadlock.
        final PipedOutputStream clientWriteOut = new PipedOutputStream();
        final PipedInputStream clientWriteIn = new PipedInputStream(clientWriteOut);
        this.serverInputStream = new ObjectOutputStream(clientWriteOut);
        this.serverRequestStream = new ObjectInputStream(clientWriteIn);

        // Server → client: same ordering requirement applies.
        final PipedOutputStream serverWriteOut = new PipedOutputStream();
        final PipedInputStream serverWriteIn = new PipedInputStream(serverWriteOut);
        this.serverResponseStream = new ObjectOutputStream(serverWriteOut);
        this.serverOutputStream = new ObjectInputStream(serverWriteIn);
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
     * Marks the channel as connected. No network operation is performed.
     */
    @Override
    public void connect() {
        this.connected = true;
    }

    @Override
    public ObjectOutputStream getServerInputStream() {
        return serverInputStream;
    }

    @Override
    public ObjectInputStream getServerOutputStream() {
        return serverOutputStream;
    }

    @Override
    public boolean isConnected() {
        return connected;
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

    /**
     * Returns the stream that tests use to read objects sent by the client.
     * Each call to {@link #sendToServer} by the client produces one object
     * readable here.
     *
     * @return input stream carrying objects the client sent
     */
    public ObjectInputStream getServerRequestStream() {
        return serverRequestStream;
    }

    /**
     * Returns the stream that tests use to write objects as if they were
     * responses from the server. Each object written here will be returned
     * by the next {@link #readFromServer} call on the client side.
     *
     * @return output stream for injecting server responses
     */
    public ObjectOutputStream getServerResponseStream() {
        return serverResponseStream;
    }

    @Override
    public void close() {
        try { serverInputStream.close(); } catch (IOException e) { /* ignore */ }
        try { serverRequestStream.close(); } catch (IOException e) { /* ignore */ }
        try { serverResponseStream.close(); } catch (IOException e) { /* ignore */ }
        try { serverOutputStream.close(); } catch (IOException e) { /* ignore */ }
        connected = false;
    }

}
