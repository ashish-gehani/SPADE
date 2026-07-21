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

package spade.analyzer.commandline.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

import spade.core.analyzer.QueryableStorage;
import spade.core.analyzer.RequiredConfig;
import spade.core.exception.StorageNotQueryable;
import spade.utility.HelperFunctions;

public class Connection implements Runnable {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private final long millisWaitSocketClose = 100;

    private final int maxConcurrentConnections = 5;
    private final ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentConnections);

    private final State state;
    private final RequiredConfig requiredConfig;

    private final ServerSocket serverSocket;

    public Connection(
        final ServerSocket serverSocket, final State state, final RequiredConfig requiredConfig
    ) throws IllegalArgumentException {
        if(serverSocket == null) {
            throw new IllegalArgumentException("NULL query server socket");
        }
        if(state == null) {
            throw new IllegalArgumentException("NULL state");
        }
        if (requiredConfig == null) {
            throw new IllegalArgumentException("NULL required config");
        }

        this.serverSocket = serverSocket;
        this.state = state;
        this.requiredConfig = requiredConfig;
    }

    @Override
    public void run() {
        try {
            this.state.setRunning(true);
            mainLoop();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Closed server connection because of error", e);
        } finally {
            cleanup();
            this.state.setRunning(false);
        }
    }

    private void mainLoop() throws Exception {
        while (!this.state.isShutdown()) {
            final Socket clientSocket = acceptClientConnection();
            startClientConnectionThread(clientSocket);
        }
    }

    private Socket acceptClientConnection() throws Exception {
        Socket clientSocket = null;
        try {
            clientSocket = serverSocket.accept();
        } catch (Exception e) {
            if (serverSocket.isClosed()) {
                throw new Exception("Server closed the socket");
            } else {
                logger.log(Level.SEVERE, "Unexpected error on server socket", e);
                closeServerSocketSafe();
                throw e;
            }
        }
        return clientSocket;
    }

    private void startClientConnectionThread(final Socket clientSocket) throws Exception {
        if (clientSocket == null) {
            throw new Exception ("NULL client connection socket");
        }

        QueryableStorage storage = null;
        try {
            storage = QueryableStorage.getCurrentQueryingDefaultInKernel();
        } catch (StorageNotQueryable e) {
            closeClientSocketSafe(clientSocket);
            throw e;
        }

        final spade.analyzer.commandline.client.State clientState = 
            new spade.analyzer.commandline.client.State();
        clientState.setStorage(storage);

        try {
            final spade.analyzer.commandline.client.Connection conn = 
                new spade.analyzer.commandline.client.Connection(
                    clientSocket, clientState, requiredConfig
                );
            executor.execute(conn);
        } catch (Exception e) {
            closeClientSocketSafe(clientSocket);
            throw new Exception("Failed to start client connection thread", e);
        }
    }

    private void closeServerSocketSafe() {
        if (serverSocket.isClosed()) {
            return;
        }
        try {
            serverSocket.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close query response writer", e);
        }
    }

    private void closeClientSocketSafe(final Socket s) {
        if (s == null) {
            return;
        }
        if (s.isClosed()) {
            return;
        }
        try {
            s.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close client socket", e);
        }
    }

    private void cleanupExecutorService() {
        executor.shutdown();
        try {
            final int secondsToWait = 5;
            if (!executor.awaitTermination(secondsToWait, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // Interrupted while waiting.
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void cleanup() {
        closeServerSocketSafe();

        HelperFunctions.sleepSafe(millisWaitSocketClose);

        cleanupExecutorService();
    }

    public void stop() {
        this.state.setShutdown(true);
        // To break out of the blocking accept on the socket.
        cleanup();
    }

    public boolean isRunning() {
        return this.state.isRunning();
    }
}
