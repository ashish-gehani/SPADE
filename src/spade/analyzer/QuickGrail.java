/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.analyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractAnalyzer;
import spade.core.AbstractStorage;
import spade.core.Kernel;
import spade.query.quickgrail.QuickGrailExecutor;
import spade.storage.Quickstep;
import spade.storage.quickstep.QuickstepClient;
import spade.storage.quickstep.QuickstepConfiguration;
import spade.storage.quickstep.QuickstepExecutor;

public class QuickGrail extends AbstractAnalyzer {
  private Logger logger = Logger.getLogger(QuickGrail.class.getName());

  private class SocketListener implements Runnable {
    private ServerSocket serverSocket;

    public SocketListener(ServerSocket serverSocket) {
      this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
      try {
        while(!Kernel.isShutdown() && !SHUTDOWN) {
          Socket querySocket = serverSocket.accept();
          QueryConnection thisConnection = new QueryConnection(querySocket);
          Thread connectionThread = new Thread(thisConnection);
          connectionThread.start();
        }
      } catch (SocketException e) {
        logger.log(Level.INFO, "Stopping socket listener");
      } catch (Exception e) {
        logger.log(Level.SEVERE, null, e);
      } finally {
        try {
          serverSocket.close();
        } catch (Exception e) {
          logger.log(Level.SEVERE, "Unable to close server socket", e);
        }
      }
    }
  }

  private QuickGrailExecutor executor;

  public QuickGrail() {
    QUERY_PORT = "commandline_query_port";
  }

  @Override
  public boolean initialize() {
    ServerSocket serverSocket = AbstractAnalyzer.getServerSocket(QUERY_PORT);
    if (serverSocket == null) {
      logger.log(Level.SEVERE, "Server Socket not initialized");
      return false;
    }

    AbstractStorage storage = QuickGrailExecutor.getCurrentStorage();
    QuickstepExecutor qs;
    if (storage != null && storage instanceof Quickstep) {
      qs = ((Quickstep) storage).getExecutor();
    } else {
      String msg = "Cannot find Quickstep storage instance, " +
                   "now try creating a standalone executor ...";
      logger.log(Level.WARNING, msg);

      String configFile = Kernel.CONFIG_PATH + Kernel.FILE_SEPARATOR + "spade.storage.Quickstep.config";
      QuickstepConfiguration conf = new QuickstepConfiguration(configFile, "");
      QuickstepClient client = new QuickstepClient(conf.getServerIP(), conf.getServerPort());
      qs = new QuickstepExecutor(client);
      qs.setLogger(logger);
    }
    executor = new QuickGrailExecutor(qs);

    new Thread(new SocketListener(serverSocket), "SocketListener-Thread").start();;
    return true;
  }

  private class QueryConnection extends AbstractAnalyzer.QueryConnection {
    public QueryConnection(Socket socket) {
      super(socket);
    }

    @Override
    public void run() {
      try {
        InputStream inStream = querySocket.getInputStream();
        OutputStream outStream = querySocket.getOutputStream();
        BufferedReader queryInputStream = new BufferedReader(new InputStreamReader(inStream));
        ObjectOutputStream responseOutputStream = new ObjectOutputStream(outStream);

        boolean quitting = false;
        while (!quitting && !SHUTDOWN) {
          quitting = processRequest(queryInputStream, responseOutputStream);
        }

        queryInputStream.close();
        responseOutputStream.close();
        inStream.close();
        outStream.close();
      } catch (Exception e) {
        logger.log(Level.SEVERE, null, e);
      } finally {
        try {
          querySocket.close();
        } catch (Exception e) {
          logger.log(Level.SEVERE, "Unable to close query socket", e);
        }
      }
    }

    private boolean processRequest(BufferedReader inputStream,
                                   ObjectOutputStream outputStream) throws IOException {
      String query = inputStream.readLine();
      if (query != null && query.toLowerCase().startsWith("export")) {
        query = query.substring(6);
      }
      if (query == null || query.trim().equalsIgnoreCase("exit")) {
        return true;
      }

      outputStream.writeObject(executor.execute(query));
      return false;
    }

    @Override
    protected boolean parseQuery(String line) {
      return true;
    }
  }
}
