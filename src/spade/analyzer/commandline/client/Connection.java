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
package spade.analyzer.commandline.client;

import java.io.EOFException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Query;
import spade.core.analyzer.RequiredConfig;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;
import spade.core.analyzer.command.execution.Context;
import spade.core.analyzer.command.execution.Execute;


public class Connection implements Runnable {
    
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private final Socket socket;
	private final ObjectOutputStream queryOutputWriter;
	private final ObjectInputStream queryInputReader;

    private final State state;
    private final RequiredConfig requiredConfig;

    public Connection(
        final Socket socket, final State state, final RequiredConfig requiredConfig
    ) throws IllegalArgumentException {
        if(socket == null) {
            throw new IllegalArgumentException("NULL query client socket");
        }
        if(state == null) {
            throw new IllegalArgumentException("NULL state");
        }
        if (requiredConfig == null) {
            throw new IllegalArgumentException("NULL required config");
        }

        this.socket = socket;
        this.state = state;
        this.requiredConfig = requiredConfig;

        try{
            final OutputStream outStream = socket.getOutputStream();
            this.queryOutputWriter = new ObjectOutputStream(outStream);
        }catch(Exception e){
            throw new IllegalArgumentException("Failed to create query writer", e);
        }
        try{
            final InputStream inStream = socket.getInputStream();
            this.queryInputReader = new ObjectInputStream(inStream);
        }catch(Exception e){
            throw new IllegalArgumentException("Failed to create query reader", e);
        }

    }

    @Override
    public void run() {
        try {
            this.state.setRunning(true);
            mainLoop();
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Closed query client connection because of interrupt", e);
            Thread.currentThread().interrupt();
        } catch (EOFException e) {
            logger.log(Level.WARNING, "Closed query client connection because of EOF", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Closed query client connection because of error", e);
        } finally {
            cleanup();
            this.state.setRunning(false);
        }
    }

    private void mainLoop() throws Exception {
        while (!this.state.isShutdown()) {
            final Query queryRecved = readQueryFromClient();
            if (queryRecved == null) {
                break;
            }
            final Query queryResult = executeQuery(queryRecved);
            writeQueryToClient(queryResult);
        }
    }

    private Context prepareCommandExecutionContext () throws ServerFailure {
        try {
            final Context cmdExecCtx = new Context(this.requiredConfig);
            cmdExecCtx.setStorage(this.state.getStorage());
            return cmdExecCtx;
        } catch (IllegalArgumentException | UnexpectedFailure e) {
            throw new ServerFailure("Failed to prepare command execution context", e);
        }
    }

    private void consumeCommandExecutionContext (final Context cmdExecCtx) {
        if (cmdExecCtx == null) {
            return;
        }
        this.state.setShutdown(cmdExecCtx.isShutdown());
        this.state.setStorage(cmdExecCtx.getStorage());
    }

    private Query executeQuery(final Query query) {
        final String rawQuery = query.query;

        Context cmdExecCtx = null;
        try {
            if (rawQuery == null) {
                throw new Exception ("Null query to execute");
            }

            cmdExecCtx = prepareCommandExecutionContext();
            final Execute cmdExec = new Execute();
            final Serializable result = cmdExec.execute(cmdExecCtx, rawQuery);
            final Query queryResult = new Query(
                query.localName,
                query.remoteName,
                query.query,
                query.queryNonce
            );
            queryResult.querySucceeded(result);
            return queryResult;
        } catch (Exception e) {
            final Query queryResult = new Query(
                query.localName,
                query.remoteName,
                query.query,
                query.queryNonce
            );
            final String errMsg = createErrorMessageFromException(e);
            queryResult.queryFailed(errMsg);
            return queryResult;
        } finally {
            if (cmdExecCtx != null) {
                consumeCommandExecutionContext(cmdExecCtx);
            }
        }
    }

    private final String createErrorMessageFromException(Throwable t) {
        if (t == null) {
            return "NULL";
        }

        /*
            Somewhat arbitrary decision to show only exception msgs
            until the last RuntimeException i.e. hide any exception
            msgs after the last RuntimeException.

            Reasoning: For convenience, most of the exceptions thrown 
            by QuickGrail are RuntimeExceptions. These RuntimeExceptions 
            contain most of the context for the reason of the underlying 
            error. Any underlying error exposes too much irrelevant info 
            to the user. It is too much effort to recheck all exceptions.
            Thus, doing it this way.
            
            For logging to SPADE log, the whole stack trace is logged.

            Downside: Might miss something that would be helpful for the
            user but will see when such an issue is reported.
        */

        boolean previousExceptionWasRuntimeException = false;
        boolean currentExceptionIsNotRuntimeException = false;

        String msg = "";
        while (t != null) {
            currentExceptionIsNotRuntimeException = !t.getClass().equals(RuntimeException.class);
            if (previousExceptionWasRuntimeException && currentExceptionIsNotRuntimeException) {
                break;
            }

            msg += t.getMessage() + ". ";

            previousExceptionWasRuntimeException = t.getClass().equals(RuntimeException.class);

            t = t.getCause();
        }
        return msg;
    }

    private Query readQueryFromClient() throws Exception {
        return (Query)queryInputReader.readObject();
    }

    private void writeQueryToClient(Query query) throws Exception {
        queryOutputWriter.writeObject(query);
        queryOutputWriter.flush();
    }

    private void cleanup() {
        try {
            queryOutputWriter.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close query response writer", e);
        }
        try {
            queryInputReader.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close query response reader", e);
        }
        try {
            socket.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close query socket", e);
        }
    }

    public void stop() {
        this.state.setShutdown(true);
    }

    public boolean isRunning() {
        return this.state.isRunning();
    }

}
