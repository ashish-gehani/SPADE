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

package spade.utility.mcp.client.user.arg;

public class Arg {

    private final String mcpUrl;
    private final String anthropicApiKey;
    private final String anthropicModel;
    private final UserClientMode userClientMode;
    private final String webHost;
    private final int webPort;
    private final LLMType llmType;
    private final boolean verbose;
    private final boolean onlyTools;

    public Arg(
        final String mcpUrl,
        final String anthropicApiKey,
        final String anthropicModel,
        final UserClientMode userClientMode,
        final String webHost,
        final int webPort,
        final LLMType llmType,
        final boolean verbose,
        final boolean onlyTools
    ) {
        this.mcpUrl = mcpUrl;
        this.anthropicApiKey = anthropicApiKey;
        this.anthropicModel = anthropicModel;
        this.userClientMode = userClientMode;
        this.webHost = webHost;
        this.webPort = webPort;
        this.llmType = llmType;
        this.verbose = verbose;
        this.onlyTools = onlyTools;
    }

    public String getMcpUrl() {
        return mcpUrl;
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public String getAnthropicModel() {
        return anthropicModel;
    }

    public UserClientMode getUserClientMode() {
        return userClientMode;
    }

    public String getWebHost() {
        return webHost;
    }

    public int getWebPort() {
        return webPort;
    }

    public LLMType getLlmType() {
        return llmType;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isOnlyTools() {
        return onlyTools;
    }

    @Override
    public String toString() {
        return "Arg[mcpUrl=" + mcpUrl
            + ", anthropicModel=" + anthropicModel
            + ", userClientMode=" + userClientMode.name
            + ", webHost=" + webHost
            + ", webPort=" + webPort
            + ", llmType=" + llmType.name
            + ", verbose=" + verbose
            + ", onlyTools=" + onlyTools
            + "]";
    }

}
