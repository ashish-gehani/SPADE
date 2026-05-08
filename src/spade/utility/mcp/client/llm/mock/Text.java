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

package spade.utility.mcp.client.llm.mock;

import java.util.Random;

public class Text {

    private static final String[] RESPONSES = {
        "I found the provenance graph for the requested process.",
        "The storage has been configured successfully.",
        "No matching vertices were found for the given query.",
        "The QuickGrail query returned 42 edges.",
        "The audit log shows 3 write operations on the file.",
        "Provenance data is currently being collected.",
        "The requested storage is not currently active.",
        "I executed the query and retrieved the subgraph.",
    };

    private final Random random = new Random();

    public String randomResponse() {
        return RESPONSES[random.nextInt(RESPONSES.length)];
    }

}
