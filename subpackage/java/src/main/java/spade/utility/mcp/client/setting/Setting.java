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

package spade.utility.mcp.client.setting;

public class Setting {

    private final MCP mcp;
    private final LLM llm;
    private final User user;
    private final boolean verbose;

    public Setting(
        final MCP mcp,
        final LLM llm,
        final User user,
        final boolean verbose
    ) {
        this.mcp = mcp;
        this.llm = llm;
        this.user = user;
        this.verbose = verbose;
    }

    public MCP getMCP() {
        return mcp;
    }

    public LLM getLLM() {
        return llm;
    }

    public User getUser() {
        return user;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public String toString() {
        return "Setting[mcp=" + mcp
            + ", llm=" + llm
            + ", user=" + user
            + ", verbose=" + verbose
            + "]";
    }

    public static class MCP {

        private final String host;
        private final int port;
        private final String endpoint;

        public MCP(
            final String host,
            final int port,
            final String endpoint
        ) {
            this.host = host;
            this.port = port;
            this.endpoint = endpoint;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public String getUrl() {
            return "http://" + host + ":" + port + endpoint;
        }

        @Override
        public String toString() {
            return "MCP[host=" + host
                + ", port=" + port
                + ", endpoint=" + endpoint
                + "]";
        }

    }

    public static class LLM {

        private final LLMType type;
        private final String anthropicApiKey;
        private final String anthropicModel;
        private final String mockScenario;

        public LLM(
            final LLMType type,
            final String anthropicApiKey,
            final String anthropicModel,
            final String mockScenario
        ) {
            this.type = type;
            this.anthropicApiKey = anthropicApiKey;
            this.anthropicModel = anthropicModel;
            this.mockScenario = mockScenario;
        }

        public LLMType getType() {
            return type;
        }

        public String getAnthropicApiKey() {
            return anthropicApiKey;
        }

        public String getAnthropicModel() {
            return anthropicModel;
        }

        public String getMockScenario() {
            return mockScenario;
        }

        @Override
        public String toString() {
            return "LLM[type=" + type.name
                + ", anthropicModel=" + anthropicModel
                + ", mockScenario=" + mockScenario
                + "]";
        }

    }

    public static class User {

        private final UserClientMode mode;
        private final String webHost;
        private final int webPort;

        public User(
            final UserClientMode mode,
            final String webHost,
            final int webPort
        ) {
            this.mode = mode;
            this.webHost = webHost;
            this.webPort = webPort;
        }

        public UserClientMode getMode() {
            return mode;
        }

        public String getWebHost() {
            return webHost;
        }

        public int getWebPort() {
            return webPort;
        }

        @Override
        public String toString() {
            return "User[mode=" + mode.name
                + ", webHost=" + webHost
                + ", webPort=" + webPort
                + "]";
        }

    }

}
