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

package spade.client.commandline;

public class UserArgumentsParser {

    public static UserArguments parse(final String[] cliArgs)
        throws IllegalArgumentException {
        if (cliArgs == null) {
            throw new IllegalArgumentException("NULL CLI args");
        }

        final UserArguments args = new UserArguments();

        for (int i = 0; i < cliArgs.length; i++) {
            if ("-H".equals(cliArgs[i])) {
                args.setRemoteHostName(parseRemoteHostName(cliArgs, i));
                i++;
            } else if ("-m".equals(cliArgs[i])) {
                args.setMaxQueriesInFile(parseMaxQueriesInFile(cliArgs, i));
                i++;
            } else if ("-t".equals(cliArgs[i])) {
                args.setCommandHistoryFile(parseCommandHistoryFile(cliArgs, i));
                i++;
            } else if ("-h".equals(cliArgs[i])) {
                args.setShowHelp(true);
            } else if ("-b".equals(cliArgs[i])) {
                args.setBatchMode(true);
            }
        }

        return args;
    }

    public static String help() {
        final String sep = System.lineSeparator();
        return "Usage: [options]"
            + sep + "  -H <host>    Remote host name to connect to"
            + sep + "  -m <count>   Max number of queries to read from a file"
            + sep + "  -t <file>    Command history file path"
            + sep + "  -b           Batch mode (disables SPADE header printing)"
            + sep + "  -h           Show help";
    }

    private static String parseRemoteHostName(final String[] cliArgs, final int i)
        throws IllegalArgumentException {
        if (i + 1 >= cliArgs.length) {
            throw new IllegalArgumentException("Missing host name after '-h'");
        }
        return cliArgs[i + 1];
    }

    private static int parseMaxQueriesInFile(final String[] cliArgs, final int i)
        throws IllegalArgumentException {
        if (i + 1 >= cliArgs.length) {
            throw new IllegalArgumentException("Missing value after '-m'");
        }
        try {
            return Integer.parseInt(cliArgs[i + 1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid value for '-m': '" + cliArgs[i + 1] + "' is not an integer"
            );
        }
    }

    private static String parseCommandHistoryFile(final String[] cliArgs, final int i)
        throws IllegalArgumentException {
        if (i + 1 >= cliArgs.length) {
            throw new IllegalArgumentException("Missing file path after '-t'");
        }
        return cliArgs[i + 1];
    }

}
