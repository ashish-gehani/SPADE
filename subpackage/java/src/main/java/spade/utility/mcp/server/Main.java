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

import spade.utility.mcp.server.connection.Context;
import spade.utility.mcp.server.connection.SPADEControl;
import spade.utility.mcp.server.connection.SPADEQuery;
import spade.utility.mcp.server.setting.Parser;
import spade.utility.mcp.server.setting.Setting;
import spade.utility.mcp.server.tool.Registry;
import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;

public class Main {

    private static final String defaultConfigFilePath = Helper.getDefaultConfigFilePath(Main.class);

    private static void log(final Level level, final String msg) {
        System.err.println("[" + level.getName() + "] [Main] " + msg);
    }

    /**
     * The config file path used by {@link #parse(String[])} when no config file is specified
     * explicitly, i.e. {@code cfg/spade.utility.mcp.server.Main.config} relative to the SPADE
     * root.
     */
    static String getDefaultConfigFilePath() {
        return defaultConfigFilePath;
    }

    /**
     * Parses settings from command-line args, falling back to {@link #getDefaultConfigFilePath()}.
     */
    static Setting parse(final String[] args) throws InvalidSettingException {
        if (args == null) {
            throw new InvalidSettingException("NULL args");
        }
        return Parser.parse(spade.utility.arg.Helper.rejoin(args), defaultConfigFilePath);
    }

    public static void main(final String[] args) throws Exception {
        final Setting setting;
        try {
            setting = parse(args);
            log(Level.INFO, "Setting - " + setting.toString());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            Parser.printHelp(defaultConfigFilePath);
            System.exit(1);
            return;
        }

        final SPADEQuery spadeQuery = new SPADEQuery(setting.getSpade().getHost(), setting.getSpade().getQueryPort());
        spadeQuery.connect();

        final SPADEControl spadeControl = new SPADEControl(setting.getSpade().getHost(), setting.getSpade().getControlPort());
        spadeControl.connect();

        final Context ctx = new Context(spadeQuery, spadeControl);
        final Registry registry = new Registry(ctx);

        final spade.utility.mcp.server.Server server;
        switch (setting.getMCP().getServerMode()) {
            case STDIO:
                server = new Stdio(setting, registry);
                break;
            case HTTP:
                server = new Http(setting, registry);
                break;
            default:
                throw new Exception("Unknown MCP server mode: " + setting.getMCP().getServerMode().name);
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
