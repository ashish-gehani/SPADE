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

package spade.utility.mcp.arg;

public class Arg {

    private final String host;
    private final int spadeQueryPort;
    private final int spadeControlPort;

    public Arg(final String host, final int spadeQueryPort, final int spadeControlPort) {
        this.host = host;
        this.spadeQueryPort = spadeQueryPort;
        this.spadeControlPort = spadeControlPort;
    }

    public String getHost() {
        return host;
    }

    public int getSpadeQueryPort() {
        return spadeQueryPort;
    }

    public int getSpadeControlPort() {
        return spadeControlPort;
    }

    @Override
    public String toString() {
        return "Arg[host=" + host
            + ", spadeQueryPort=" + spadeQueryPort
            + ", spadeControlPort=" + spadeControlPort
            + "]";
    }

}
