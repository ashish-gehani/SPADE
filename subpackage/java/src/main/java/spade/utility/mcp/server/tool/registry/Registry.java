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

package spade.utility.mcp.server.tool.registry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spade.utility.mcp.server.tool.Tool;
import spade.utility.mcp.server.tool.definition.Definition;
import spade.utility.mcp.server.tool.type.Type;
import spade.utility.mcp.server.tool.type.spade.cli.CLI;
import spade.utility.mcp.server.tool.type.web.doc.Doc;
import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;

public class Registry {

    private static final String defaultConfigFilePath = Helper.getDefaultConfigFilePath(Registry.class);

    private final List<Tool> tools;

    public Registry() throws InvalidSettingException {
        this(defaultConfigFilePath);
    }

    public Registry(final String configFilePath) throws InvalidSettingException {
        final Config config = Parser.parse(configFilePath);

        final List<Tool> tools = new ArrayList<>();
        for (final File toolConfigFilePath : config.getToolConfigFilePaths()) {
            tools.add(loadTool(toolConfigFilePath.getPath()));
        }
        this.tools = List.copyOf(tools);
    }

    private Tool loadTool(final String toolConfigFilePath) throws InvalidSettingException {
        final Definition definition = spade.utility.mcp.server.tool.definition.Parser.parse(toolConfigFilePath);
        final Type type = definition.getType();

        switch (type) {
            case SPADE_CLI:
                return new CLI(definition, toolConfigFilePath);
            case WEB_DOC:
                return new Doc(definition, toolConfigFilePath);
            default:
                throw new InvalidSettingException(
                    "Unhandled tool type '" + type + "' in config file '" + toolConfigFilePath + "'");
        }
    }

    public List<Tool> getTools(){
        return tools;
    }

}
