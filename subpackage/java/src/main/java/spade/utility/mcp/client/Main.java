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

import java.util.logging.Level;

import spade.utility.mcp.client.llm.Factory;
import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.setting.Parser;
import spade.utility.mcp.client.setting.Setting;
import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;

public class Main {

    private static final String defaultConfigFilePath = Helper.getDefaultConfigFilePath(Main.class);

    private static void log(final Level level, final String msg) {
        System.err.println("[" + level.getName() + "] [Main] " + msg);
    }

    static String getDefaultConfigFilePath() {
        return defaultConfigFilePath;
    }

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
            Parser.printHelp();
            System.exit(1);
            return;
        }

        final LLM llm = Factory.create(setting);
        final Client mcpClient = new Client(setting.getMCP().getUrl(), llm, setting.isVerbose());
        mcpClient.initialize();

        spade.utility.mcp.client.user.Factory.create(setting, mcpClient, llm).run();
    }

}
