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

import spade.utility.HelperFunctions;
import spade.utility.Result;

public class Parser {

    private static final String argHost            = "--host";
    private static final String argSpadeQueryPort  = "--spadeQueryPort";
    private static final String argSpadeControlPort = "--spadeControlPort";

    public static void printHelp() {
        System.err.println("Usage:");
        System.err.println("  " + argHost               + "\t\t=<host>   (required) Hostname of the SPADE server");
        System.err.println("  " + argSpadeQueryPort     + "\t=<port>   (required) Query port on SPADE server");
        System.err.println("  " + argSpadeControlPort   + "\t=<port>   (required) Control port on SPADE server");
    }

    public static Arg parse(final String[] args) throws Exception {
        if (args == null) {
            throw new Exception("NULL args");
        }

        String host = null;
        String rawSpadeQueryPort = null;
        String rawSpadeControlPort = null;
        for (final String arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg.startsWith(argHost + "=")) {
                host = arg.substring(argHost.length() + 1).trim();
            } else if (arg.startsWith(argSpadeQueryPort + "=")) {
                rawSpadeQueryPort = arg.substring(argSpadeQueryPort.length() + 1).trim();
            } else if (arg.startsWith(argSpadeControlPort + "=")) {
                rawSpadeControlPort = arg.substring(argSpadeControlPort.length() + 1).trim();
            }
        }

        if (host == null || host.isEmpty()) {
            throw new Exception("Missing required argument '" + argHost + "'");
        }

        if (rawSpadeQueryPort == null) {
            throw new Exception("Missing required argument '" + argSpadeQueryPort + "'");
        }

        if (rawSpadeControlPort == null) {
            throw new Exception("Missing required argument '" + argSpadeControlPort + "'");
        }

        final Result<Long> queryPortResult = HelperFunctions.parseLong(rawSpadeQueryPort, 10, 1, Integer.MAX_VALUE);
        if (queryPortResult.error) {
            throw new Exception(
                "Invalid value for argument '" + argSpadeQueryPort + "': " + queryPortResult.toErrorString());
        }

        final Result<Long> controlPortResult = HelperFunctions.parseLong(rawSpadeControlPort, 10, 1, Integer.MAX_VALUE);
        if (controlPortResult.error) {
            throw new Exception(
                "Invalid value for argument '" + argSpadeControlPort + "': " + controlPortResult.toErrorString());
        }

        return new Arg(host, queryPortResult.result.intValue(), controlPortResult.result.intValue());
    }

}
