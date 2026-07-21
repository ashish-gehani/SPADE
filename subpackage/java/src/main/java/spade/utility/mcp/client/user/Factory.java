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

import spade.utility.mcp.client.MCPClient;
import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.user.arg.Arg;

public class Factory {

    public static Client create(final Arg arg, final MCPClient mcpClient, final LLM llm) throws Exception {
        switch (arg.getUserClientMode()) {
            case CLI: return new CLI(mcpClient, llm);
            case WEB: return new Web(mcpClient, llm, arg.getWebHost(), arg.getWebPort());
            default: throw new Exception("Unknown user client mode: " + arg.getUserClientMode().name);
        }
    }

}
