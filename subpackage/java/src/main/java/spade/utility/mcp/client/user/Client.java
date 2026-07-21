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

package spade.utility.mcp.client.user;

import java.util.logging.Level;

import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.MCPClient;

public abstract class Client {

    private final MCPClient mcpClient;
    private final LLM llm;

    public Client(final MCPClient mcpClient, final LLM llm) {
        if (mcpClient == null) {
            throw new IllegalArgumentException("NULL mcpClient");
        }
        if (llm == null) {
            throw new IllegalArgumentException("NULL llm");
        }
        this.mcpClient = mcpClient;
        this.llm = llm;
    }

    public MCPClient getMCPClient() {
        return mcpClient;
    }

    public LLM getLlm() {
        return llm;
    }

    public abstract void run() throws Exception;

    public void shutdown() {
        llm.close();
        mcpClient.close();
    }

    public void log(final Level level, final String msg) {
        System.err.println("[" + level.getName() + "] [" + this.getClass().getName() + "] " + msg);
    }

}
