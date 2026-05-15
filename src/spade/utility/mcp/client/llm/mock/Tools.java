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
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Tools {

    // Tool names and input schemas mirror spade.utility.mcp.server.tool
    public static final String ADD_STORAGE         = "add_storage";
    public static final String SET_STORAGE         = "set_storage";
    public static final String LIST_STORAGES       = "list_storages";
    public static final String PRINT_STORAGE       = "print_storage";
    public static final String QUERY               = "query";
    public static final String READ_QUICKGRAIL_DOC = "read_quickgrail_doc";

    private static final String[] ALL = {
        // ADD_STORAGE, // Disabled because Mock llm can add ridiculously many.
        SET_STORAGE, LIST_STORAGES,
        PRINT_STORAGE, QUERY, READ_QUICKGRAIL_DOC
    };
    private static final String[] STORAGE_NAMES  = { "PostgreSQL" };
    private static final String[] SAMPLE_QUERIES = {
        "stat $base"
    };

    private final ObjectMapper mapper;
    private final Random random = new Random();

    public Tools(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String randomToolName() {
        return ALL[random.nextInt(ALL.length)];
    }

    public String randomToolUseId() {
        return "tool_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public ObjectNode randomInputFor(final String toolName) {
        final ObjectNode input = mapper.createObjectNode();
        switch (toolName) {
            case ADD_STORAGE:
            case SET_STORAGE:
                input.put("storageName", STORAGE_NAMES[random.nextInt(STORAGE_NAMES.length)]);
                break;
            case QUERY:
                input.put("query", SAMPLE_QUERIES[random.nextInt(SAMPLE_QUERIES.length)]);
                break;
            default:
                break;
        }
        return input;
    }

}
