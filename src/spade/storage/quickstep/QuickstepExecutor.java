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
package spade.storage.quickstep;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for encapsulation of related methods and asynchronous execution.
 */
public class QuickstepExecutor {
  private QuickstepClient client;
  private ExecutorService queryExecutor;
  private Future<String> queryFuture;
  private Logger logger;
  private Consumer<String> prioritizedLogger;
  private int numRetriesOnFailure;

  /**
   * Query instance that can be scheduled by ExecutorService.
   */
  private class QueryInstance implements Callable<String> {
    private String query;
    private String data;

    public QueryInstance(String query, String data) {
      this.query = query;
      this.data = data;
    }

    @Override
    public String call() {
      if (query.length() < 256) {
        logInfo("[Quickstep query]\n" + query);
      } else {
        logInfo("[Quickstep query]\n" + query.substring(0, 64).replace("\n", "\\n") + " ...");
      }

      int numRetries = 0;
      QuickstepFailure failure = null;

      while (numRetries <= numRetriesOnFailure) {
        if (numRetries > 0) {
          logInfo("[Retry " + numRetries + "]");
        }
        try {
          final long queryStartTime = System.currentTimeMillis();
          QuickstepResponse response = client.requestForResponse(query, data);
          final String stdout = response.getStdout();
          final String stderr = response.getStderr();
          logOutput(response.getStdout(), "output");
          logOutput(response.getStderr(), "error");
          logInfo("[Done] " + (System.currentTimeMillis() - queryStartTime) + "ms");
          if (stderr.isEmpty()) {
            return stdout;
          }
          failure = new QuickstepFailure(stderr);
        } catch (IOException e) {
          failure = new QuickstepFailure(e.getMessage());
        }
        ++numRetries;
      }
      throw failure;
    }

    private void logOutput(String output, String kind) {
      if (!output.isEmpty()) {
        if (output.length() < 64) {
          logInfo("[Quickstep " + kind + "] " + output.replace("\n", "\\n"));
        } else {
          logInfo("[Quickstep " + kind + "] " + output.substring(0, 64).replace("\n", "\\n") + " ...");
        }
      }
    }
  }

  private Lock transactionLock = new ReentrantLock();

  public QuickstepExecutor(QuickstepClient client) {
    this.client = client;
    this.numRetriesOnFailure = 0;
    queryExecutor = Executors.newSingleThreadExecutor();
  }

  /**
   * Shutdown this executor.
   */
  public void shutdown() {
    queryExecutor.shutdown();
  }

  /**
   * Set the general logger.
   */
  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  /**
   * Set a "prioritized" logger for convenience of debugging.
   */
  public void setPriortizedLogger(Consumer<String> prioritizedLogger) {
    this.prioritizedLogger = prioritizedLogger;
  }

  /**
   * Log a message.
   */
  public void logInfo(String msg) {
    if (prioritizedLogger != null) {
      prioritizedLogger.accept(msg);
    } else if (logger != null) {
      logger.log(Level.INFO, msg);
    }
  }

  /**
   * Submit a query and return immediately (if possible).
   */
  public void submitQuery(String query, String data) {
    finalizeQuery();
    queryFuture = queryExecutor.submit(new QueryInstance(query, data));
  }

  /**
   * Submit a query and return immediately (if possible).
   */
  public void submitQuery(String query) {
    submitQuery(query, null);
  }

  /**
   * Wait for the current query to finish and return the result.
   */
  public String finalizeQuery() {
    String result = null;
    if (queryFuture != null) {
      try {
        result = queryFuture.get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      } finally {
        queryFuture = null;
      }
    }
    return result;
  }

  /**
   * Submit a query and wait for the result.
   */
  public String executeQuery(String query, String data) {
    submitQuery(query, data);
    return finalizeQuery();
  }

  /**
   * Submit a query and wait for the result.
   */
  public String executeQuery(String query) {
    return executeQuery(query, null);
  }

  /**
   * Submit a query and cast result as long type.
   */
  public long executeQueryForLongResult(String query) {
    String resultStr = executeQuery(query).trim();
    try {
      return Long.parseLong(resultStr);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unexpected result \"" + resultStr + "\" from Quickstep: expecting an integer value");
    }
  }

  /**
   * Start a transaction to force serialized execution of multiple queries.
   */
  public void beginTransaction() {
    transactionLock.lock();
  }

  /**
   * Commit the transaction (abort is not supported yet).
   */
  public void finalizeTransction() {
    // May need better handling of error recovery.
    transactionLock.unlock();
  }

  /**
   * Get number of retries on Quickstep execution failure.
   */
  public int getNumRetriesOnFailure() {
    return numRetriesOnFailure;
  }

  /**
   * Set number of retries on Quickstep execution failure.
   */
  public void setNumRetriesOnFailure(final int numRetries) {
    this.numRetriesOnFailure = numRetries;
  }
}
