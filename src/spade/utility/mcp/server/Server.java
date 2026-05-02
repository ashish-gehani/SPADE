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

package spade.utility.mcp.server;

import java.util.logging.Level;

import spade.utility.mcp.arg.Arg;
import spade.utility.mcp.connection.SPADEControl;
import spade.utility.mcp.connection.SPADEQuery;
import spade.utility.mcp.tool.Registry;

public abstract class Server {

    private final State state = new State();
    private final Arg arg;
    private final Registry toolRegistry;

    public Server(
        final Arg arg,
        final Registry toolRegistry
    ){
        if (arg == null) {
            throw new IllegalArgumentException("NULL arg");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("NULL toolRegistry");
        }
        this.arg = arg;
        this.toolRegistry = toolRegistry;
    }

    public State getState() {
        return state;
    }

    public Arg getArg(){
        return arg;
    }

    public Registry getToolRegistry(){
        return toolRegistry;
    }

    public SPADEQuery getSPADEQuery(){
        return toolRegistry.getContext().getSpadeQuery();
    }

    public SPADEControl getSPADEControl(){
        return toolRegistry.getContext().getSpadeControl();
    }

    public abstract void initialize() throws Exception;

    public abstract void start() throws Exception;

    public void shutdown(){
        getSPADEQuery().close();
        getSPADEControl().close();
        this.state.setRunning(false);
        this.state.setShutdown(true);
    }

    public void log(final Level level, final String msg) {
        // Logging to error stream to avoid corrupting System.in. System.in is used by MCP server.
        System.err.println("[" + level.getName() + "] [" + this.getClass().getName() + "] " + msg);
    }
}
