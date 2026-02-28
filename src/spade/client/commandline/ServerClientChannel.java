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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Channel for client-server communication.
 * Provides a connection to the SPADE server and object-level send/receive operations.
 */
public interface ServerClientChannel extends AutoCloseable {

    /**
     * Returns the local host name of this client.
     *
     * @return local host name
     */
    String getLocalHostName();

    /**
     * Returns the remote host name of the server this channel connects to.
     *
     * @return remote host name
     */
    String getRemoteHostName();

    /**
     * Establishes the connection to the server.
     *
     * @throws Exception if the connection cannot be established
     */
    void connect() throws Exception;

    /**
     * Returns the stream used to write (send) objects to the server.
     *
     * @return output stream for sending to server
     */
    ObjectOutputStream getServerInputStream();

    /**
     * Returns the stream used to read (receive) objects from the server.
     *
     * @return input stream for receiving from server
     */
    ObjectInputStream getServerOutputStream();

    /**
     * Returns whether the channel is currently connected to the server.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Sends an object to the server.
     *
     * @param obj the object to send; must not be null
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if the channel is not connected
     */
    void sendToServer(Object obj) throws IOException, IllegalStateException;

    /**
     * Reads and returns the next object sent by the server.
     *
     * @return the object received from the server
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the class of the received object cannot be found
     * @throws IllegalStateException if the channel is not connected
     */
    Object readFromServer() throws IOException, ClassNotFoundException, IllegalStateException;

    /**
     * Closes the channel and releases all associated resources.
     */
    @Override
    void close();

}
