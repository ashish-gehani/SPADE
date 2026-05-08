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

package spade.utility.mcp.client;

import spade.utility.mcp.client.llm.Factory;
import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.user.arg.Arg;
import spade.utility.mcp.client.user.arg.Parser;

public class Main {

    public static void main(final String[] args) throws Exception {
        final Arg arg;
        try {
            arg = Parser.parse(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            Parser.printHelp();
            System.exit(1);
            return;
        }

        final LLM llm = Factory.create(arg);
        final MCPClient mcpClient = new MCPClient(arg.getMcpUrl(), llm);
        mcpClient.initialize();

        spade.utility.mcp.client.user.Factory.create(arg, mcpClient, llm).run();
    }

}
