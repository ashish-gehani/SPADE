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

package spade.client.commandline.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import spade.client.commandline.UserArgumentsParser;


/***
 * @author Claude Code
 */
public class UserArguments {

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    public void defaultRemoteHostName() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        assertEquals("localhost", args.getRemoteHostName());
    }

    @Test
    public void defaultMaxQueriesInFile() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        assertEquals(1000, args.getMaxQueriesInFile());
    }

    @Test
    public void defaultShowHelpIsFalse() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        assertFalse(args.isShowHelp());
    }

    @Test
    public void defaultBatchModeIsFalse() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        assertFalse(args.isBatchMode());
    }

    // -------------------------------------------------------------------------
    // Setters / getters
    // -------------------------------------------------------------------------

    @Test
    public void setRemoteHostName() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        args.setRemoteHostName("myhost");
        assertEquals("myhost", args.getRemoteHostName());
    }

    @Test
    public void setMaxQueriesInFile() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        args.setMaxQueriesInFile(42);
        assertEquals(42, args.getMaxQueriesInFile());
    }

    @Test
    public void setCommandHistoryFile() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        args.setCommandHistoryFile("/tmp/test-history");
        assertEquals("/tmp/test-history", args.getCommandHistoryFile());
    }

    @Test
    public void setShowHelp() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        args.setShowHelp(true);
        assertTrue(args.isShowHelp());
    }

    @Test
    public void setBatchMode() {
        final spade.client.commandline.UserArguments args = new spade.client.commandline.UserArguments();
        args.setBatchMode(true);
        assertTrue(args.isBatchMode());
    }

    // -------------------------------------------------------------------------
    // Parser: valid flags
    // -------------------------------------------------------------------------

    @Test
    public void parseEmptyArgs() {
        final spade.client.commandline.UserArguments args = UserArgumentsParser.parse(new String[]{});
        assertEquals("localhost", args.getRemoteHostName());
        assertEquals(1000, args.getMaxQueriesInFile());
        assertFalse(args.isShowHelp());
        assertFalse(args.isBatchMode());
    }

    @Test
    public void parseRemoteHostFlag() {
        final spade.client.commandline.UserArguments args = UserArgumentsParser.parse(new String[]{"-H", "remotehost"});
        assertEquals("remotehost", args.getRemoteHostName());
    }

    @Test
    public void parseMaxQueriesFlag() {
        final spade.client.commandline.UserArguments args = UserArgumentsParser.parse(new String[]{"-m", "500"});
        assertEquals(500, args.getMaxQueriesInFile());
    }

    @Test
    public void parseCommandHistoryFileFlag() {
        final spade.client.commandline.UserArguments args = UserArgumentsParser.parse(new String[]{"-t", "/tmp/history"});
        assertEquals("/tmp/history", args.getCommandHistoryFile());
    }

    @Test
    public void parseShowHelpFlag() {
        final spade.client.commandline.UserArguments args = UserArgumentsParser.parse(new String[]{"-h"});
        assertTrue(args.isShowHelp());
    }

    @Test
    public void parseBatchModeFlag() {
        final spade.client.commandline.UserArguments args = UserArgumentsParser.parse(new String[]{"-b"});
        assertTrue(args.isBatchMode());
    }

    @Test
    public void parseAllFlags() {
        final spade.client.commandline.UserArguments args = UserArgumentsParser.parse(
            new String[]{"-H", "host1", "-m", "200", "-t", "/tmp/h", "-h", "-b"}
        );
        assertEquals("host1", args.getRemoteHostName());
        assertEquals(200, args.getMaxQueriesInFile());
        assertEquals("/tmp/h", args.getCommandHistoryFile());
        assertTrue(args.isShowHelp());
        assertTrue(args.isBatchMode());
    }

    // -------------------------------------------------------------------------
    // Parser: error cases
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void parseNullArgsFails() {
        UserArgumentsParser.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseRemoteHostFlagMissingValueFails() {
        UserArgumentsParser.parse(new String[]{"-H"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseMaxQueriesFlagMissingValueFails() {
        UserArgumentsParser.parse(new String[]{"-m"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseMaxQueriesFlagNonIntegerFails() {
        UserArgumentsParser.parse(new String[]{"-m", "notanumber"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseCommandHistoryFileFlagMissingValueFails() {
        UserArgumentsParser.parse(new String[]{"-t"});
    }

}
