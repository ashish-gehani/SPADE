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

package spade.client.commandline.command;

import spade.client.commandline.ExecutionContext;
import spade.client.commandline.ServerClientChannel;
import spade.client.commandline.command.exception.IllegalCommandResult;
import spade.client.commandline.command.exception.CommandExecutionNotComplete;
import spade.client.commandline.command.exception.CommandResultNotSuccessful;
import spade.client.commandline.command.exception.IllegalCommand;
import spade.core.Graph;
import spade.core.Query;
import spade.query.quickgrail.instruction.SaveGraph;
import spade.query.quickgrail.utility.ResultTable;

/*
    Generic server command.
*/
public class Server extends AbstractCommand {

    public Server(final Source source, final Type type, final String raw)
        throws IllegalArgumentException {
        super(source, type, raw);
    }

    public static Server create(final Source source, final String raw)
        throws IllegalArgumentException, IllegalCommand {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final Server instance = new Server(source, Type.SERVER, raw);
        return instance;
    }

    private final Query createRequest(
        final String localHostName, final String remoteHostName
    ) throws Exception {
        final String rawCmd = getRaw();
        final String queryNonce = null; // null for local queries
        final Query request = new Query(localHostName, remoteHostName, rawCmd, queryNonce);
        return request;
    }

    private final Object getResponse(
        final Query request, final ServerClientChannel channel
    ) throws Exception {
        channel.sendToServer(request);

        final Object resultObject = channel.readFromServer();
        if (resultObject == null) {
            // EOF
            throw new Exception("Server closed the connection");
        }

        return resultObject;
    }

    @Override
    protected synchronized Object executeInternal(final ExecutionContext ctx) throws IllegalArgumentException {
        if (ctx == null) {
            throw new IllegalArgumentException("Execution context cannot be null");
        }

        final spade.client.commandline.output.User userOutput = ctx.getUserOutput();

        // If server-client comms fail then shutdown since client cannot do anything.
        try {
            final ServerClientChannel channel = ctx.getServerClientChannel();
            final Query request = createRequest(
                channel.getLocalHostName(), channel.getRemoteHostName()
            );
            final Object response = getResponse(request, channel);
            return response;
        } catch (Exception eOuter) {
            // Shutdown
            ctx.shutdown();

            final String errMsg = (
                "Failed to execute server command: '" + getRaw() + "'"
                + ". Error: " + eOuter.getMessage()
            );
            userOutput.writeStringLn(errMsg);
            return null;
        }
    }

    private synchronized void ensureExecutionComplete() 
        throws CommandExecutionNotComplete {
        if (!isExecutionComplete()) {
            throw new CommandExecutionNotComplete(
                "Command '" + getRaw() + "' is still in progress"
            );
        }
    }

    private synchronized Query ensureExecutionResultIsQuery() 
        throws IllegalCommandResult {
        final Object resultObject = this.getExecutionResult();
        if (!(resultObject instanceof Query)) {
            throw new IllegalCommandResult(
                "Server sent back unknown response. Expected: "
                + Query.class.getName()
                + ". Actual: "
                + resultObject.getClass().getName()
            );
        }
        final Query resultQuery = (Query)resultObject;
        return resultQuery;
    }

    public synchronized String getExecutionResultAsString(final boolean mustBeSuccessful) 
        throws
            CommandExecutionNotComplete,
            IllegalCommandResult,
            CommandResultNotSuccessful,
            Exception {
        ensureExecutionComplete();
        final Query query = ensureExecutionResultIsQuery();
        if (mustBeSuccessful && !query.wasQuerySuccessful()) {
            throw new CommandResultNotSuccessful("Result of query is not successful");
        }

        final Object responseResult = query.getResult();
        if (responseResult == null) {
            return "";
        }

        if (responseResult instanceof Graph) {
            return Graph.exportGraphToString(
                SaveGraph.Format.kDot, ((Graph) responseResult)
            );
        } else if (responseResult instanceof ResultTable) {
            return ((ResultTable) responseResult).toString();
        } else {
            return String.valueOf(responseResult);
        }
    }

    @Override
    protected synchronized void writeExecutionResultInternal(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult {
        if (userOutput == null) {
            throw new IllegalArgumentException("Null user output");
        }

        ensureExecutionComplete();
        final Query response = ensureExecutionResultIsQuery();

        try {
            processResponse(response, userOutput);
        } catch (Exception eOuter) {
            // No need to shutdown since only a malformed response.

            final String errMsg = (
                "Failed to process response of server command: '" + getRaw() + "'"
                + ". Error: " + eOuter.getMessage()
            );
            userOutput.writeStringLn(errMsg);
        }
    }

    /**
     * Processes the query response and writes the result to user output.
     *
     * @param response The query response from the server
     * @param userOutput The user output to write to
     * @throws Exception if an error occurs during processing
     */
    private void processResponse(
        final Query response, final spade.client.commandline.output.User userOutput
    ) throws Exception {
        if (response.wasQuerySuccessful()) {
            processSuccessfulResponse(response, userOutput);
        } else {
            processFailedResponse(response, userOutput);
        }
    }

    /**
     * Processes a successful query response and writes the result to user output.
     *
     * @param response The successful query response from the server
     * @param userOutput The user output to write to
     * @throws Exception if an error occurs during processing
     */
    private void processSuccessfulResponse(
        final Query response, final spade.client.commandline.output.User userOutput
    ) throws Exception {
        Object responseResult = response.getResult();
        if (responseResult == null) {
            // Empty result
            userOutput.writeStringLn("");
            return;
        }

        if (responseResult instanceof Graph) {
            Graph graph = (Graph) responseResult;
            userOutput.writeGraph(graph);
        } else if (responseResult instanceof ResultTable) {
            userOutput.writeStringLn(((ResultTable) responseResult).toString());
        } else {
            userOutput.writeStringLn(String.valueOf(responseResult));
        }
    }

    /**
     * Processes a failed query response and writes the error to user output.
     *
     * @param response The failed query response from the server
     * @param userOutput The user output to write to
     * @throws Exception if an error occurs during processing
     */
    private void processFailedResponse(
        final Query response, final spade.client.commandline.output.User userOutput
    ) throws Exception {
        if (response.getError() == null) {
            userOutput.writeStringLn("");
        } else {
            Object errorObject = response.getError();
            if (errorObject instanceof Throwable) {
                userOutput.writeStringLn("Error: " + ((Throwable) errorObject).getMessage());
            } else {
                userOutput.writeStringLn("Error: " + String.valueOf(errorObject));
            }
        }
    }

}
