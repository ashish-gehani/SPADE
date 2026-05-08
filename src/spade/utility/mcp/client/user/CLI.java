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

import java.io.BufferedReader;
import java.io.InputStreamReader;

import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.MCPClient;

public class CLI extends Client {

    public CLI(final MCPClient mcpClient, final LLM llm) {
        super(mcpClient, llm);
    }

    @Override
    public void run() throws Exception {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { reader.close(); } catch (Exception e) { /* ignore */ }
            shutdown();
        }));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            final String response = getMCPClient().chat(line);
            System.out.println(response);
        }
    }

}
