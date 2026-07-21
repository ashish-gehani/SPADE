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

package spade.utility.mcp.server.arg;

public class Arg {

    private final String spadeHost;
    private final int spadeQueryPort;
    private final int spadeControlPort;

    private final MCPServerMode mcpServerMode;

    private final String mcpHttpHostName;
    private final int mcpHttpHostPort;
    private final String mcpHttpHostEndpoint;

    public Arg(
        final String spadeHost,
        final int spadeQueryPort,
        final int spadeControlPort,
        final MCPServerMode mcpServerMode,
        final String mcpHttpHostName,
        final int mcpHttpHostPort,
        final String mcpHttpHostEndpoint
    ) {
        this.spadeHost = spadeHost;
        this.spadeQueryPort = spadeQueryPort;
        this.spadeControlPort = spadeControlPort;
        this.mcpServerMode = mcpServerMode;
        this.mcpHttpHostName = mcpHttpHostName;
        this.mcpHttpHostPort = mcpHttpHostPort;
        this.mcpHttpHostEndpoint = mcpHttpHostEndpoint;
    }

    public String getSpadeHost() {
        return spadeHost;
    }

    public int getSpadeQueryPort() {
        return spadeQueryPort;
    }

    public int getSpadeControlPort() {
        return spadeControlPort;
    }

    public MCPServerMode getMCPServerMode() {
        return mcpServerMode;
    }

    public String getMCPHttpHostName() {
        return mcpHttpHostName;
    }

    public int getMCPHttpHostPort() {
        return mcpHttpHostPort;
    }

    public String getMCPHttpHostEndpoint() {
        return mcpHttpHostEndpoint;
    }

    @Override
    public String toString() {
        return "Arg[spadeHost=" + spadeHost
            + ", spadeQueryPort=" + spadeQueryPort
            + ", spadeControlPort=" + spadeControlPort
            + ", mcpServerMode=" + mcpServerMode.name
            + ", mcpHttpHostName=" + mcpHttpHostName
            + ", mcpHttpHostPort=" + mcpHttpHostPort
            + ", mcpHttpHostEndpoint=" + mcpHttpHostEndpoint
            + "]";
    }

}
