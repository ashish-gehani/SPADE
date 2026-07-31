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

package spade.utility.mcp.server.setting;

public class Setting {

    private final String registryConfigFilePath;
    private final MCP mcp;

    public Setting(final String registryConfigFilePath, final MCP mcp) {
        this.registryConfigFilePath = registryConfigFilePath;
        this.mcp = mcp;
    }

    public String getRegistryConfigFilePath() {
        return registryConfigFilePath;
    }

    public MCP getMCP() {
        return mcp;
    }

    @Override
    public String toString() {
        return "Setting[registryConfigFilePath=" + registryConfigFilePath + ", mcp=" + mcp + "]";
    }

    public static class MCP {

        private final ServerMode serverMode;
        private final String httpHostName;
        private final int httpHostPort;
        private final String httpHostEndpoint;

        public MCP(
            final ServerMode serverMode,
            final String httpHostName,
            final int httpHostPort,
            final String httpHostEndpoint
        ) {
            this.serverMode = serverMode;
            this.httpHostName = httpHostName;
            this.httpHostPort = httpHostPort;
            this.httpHostEndpoint = httpHostEndpoint;
        }

        public ServerMode getServerMode() {
            return serverMode;
        }

        public String getHttpHostName() {
            return httpHostName;
        }

        public int getHttpHostPort() {
            return httpHostPort;
        }

        public String getHttpHostEndpoint() {
            return httpHostEndpoint;
        }

        @Override
        public String toString() {
            return "MCP[serverMode=" + serverMode.name
                + ", httpHostName=" + httpHostName
                + ", httpHostPort=" + httpHostPort
                + ", httpHostEndpoint=" + httpHostEndpoint
                + "]";
        }

    }

}
