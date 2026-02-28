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

import spade.core.Settings;

public class UserArguments {

    private final String DEFAULT_REMOTEHOSTNAME = "localhost";
    private final int DEFAULT_MAXQUERIESINFILE = 1000;
    private final String DEFAULT_COMMANDHISTORYFILE = Settings.getQueryHistoryFilePath();

    private String remoteHostName = DEFAULT_REMOTEHOSTNAME;
    private int maxQueriesInFile = DEFAULT_MAXQUERIESINFILE;
    private String commandHistoryFile = DEFAULT_COMMANDHISTORYFILE;
    private boolean showHelp = false;
    private boolean batchMode = false;

    public String getRemoteHostName() {
        return remoteHostName;
    }

    public void setRemoteHostName(final String remoteHostName) {
        this.remoteHostName = remoteHostName;
    }

    public int getMaxQueriesInFile() {
        return maxQueriesInFile;
    }

    public void setMaxQueriesInFile(final int maxQueriesInFile) {
        this.maxQueriesInFile = maxQueriesInFile;
    }

    public String getCommandHistoryFile() {
        return commandHistoryFile;
    }

    public void setCommandHistoryFile(final String commandHistoryFile) {
        this.commandHistoryFile = commandHistoryFile;
    }

    public boolean isShowHelp() {
        return showHelp;
    }

    public void setShowHelp(final boolean showHelp) {
        this.showHelp = showHelp;
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public void setBatchMode(final boolean batchMode) {
        this.batchMode = batchMode;
    }

    @Override
    public String toString() {
        return "UserArguments{"
            + "remoteHostName='" + remoteHostName + "'"
            + ", maxQueriesInFile=" + maxQueriesInFile
            + ", commandHistoryFile='" + commandHistoryFile + "'"
            + ", showHelp=" + showHelp
            + ", batchMode=" + batchMode
            + "}";
    }

}
