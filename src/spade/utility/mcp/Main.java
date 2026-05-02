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

package spade.utility.mcp;

import java.util.logging.Level;

import spade.utility.mcp.arg.Arg;
import spade.utility.mcp.arg.Parser;
import spade.utility.mcp.connection.Context;
import spade.utility.mcp.connection.SPADEControl;
import spade.utility.mcp.connection.SPADEQuery;
import spade.utility.mcp.server.Http;
import spade.utility.mcp.server.Stdio;
import spade.utility.mcp.tool.Registry;

public class Main {

    private static void log(final Level level, final String msg) {
        System.err.println("[" + level.getName() + "] [Main] " + msg);
    }

    public static void main(final String[] args) throws Exception {
        final Arg arg;
        try {
            arg = Parser.parse(args);
            log(Level.INFO, "Args - " + arg.toString());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            Parser.printHelp();
            System.exit(1);
            return;
        }

        final SPADEQuery spadeQuery = new SPADEQuery(arg.getSpadeHost(), arg.getSpadeQueryPort());
        spadeQuery.connect();

        final SPADEControl spadeControl = new SPADEControl(arg.getSpadeHost(), arg.getSpadeControlPort());
        spadeControl.connect();

        final Context ctx = new Context(spadeQuery, spadeControl);
        final Registry registry = new Registry(ctx);

        final spade.utility.mcp.server.Server server;
        switch (arg.getMCPServerMode()) {
            case STDIO:
                server = new Stdio(arg, registry);
                break;
            case HTTP:
                server = new Http(arg, registry);
                break;
            default:
                throw new Exception("Unknown MCP server mode: " + arg.getMCPServerMode().name);
        }

        try {
            server.initialize();
            server.start();
            log(Level.INFO, "Server started");
        } catch (Exception e) {
            System.err.println("Failed to start: " + e.getMessage());
            throw e;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log(Level.INFO, "Server stopping");
            server.shutdown();
            log(Level.INFO, "Server stopped");
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
