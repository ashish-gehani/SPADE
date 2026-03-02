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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import spade.client.commandline.command.Server;
import spade.client.commandline.command.Source;
import spade.query.quickgrail.core.EnvironmentVariableManager;
import spade.utility.FileUtility;

public class ServerQueryEnvironmentState {

    private String currentStorageName;

    private final Map<String, String> quickgrailEnvVars = new HashMap<String, String>();

    private boolean isFetched = false;

    private final void logException(final ExecutionContext ctx, final String msg, final Throwable t) {
        final spade.client.commandline.output.User out = ctx.getUserOutput();
        final String errMsg = msg + ". Error: " + t.getMessage();
        out.writeStringLn(errMsg);
    }

    private final void executeCmdBestEffort(final ExecutionContext ctx, final Server envCmd) {
        try {
            envCmd.execute(ctx);
        } catch (Exception e) {
            logException(ctx, "Failed to execute command: '" + envCmd + "'", e);
        }
    }

    private final void fetchCurrentStorageNameBestEffort(final ExecutionContext ctx) {
        try {
            final boolean mustBeSuccessful = true;
            final Server cmd = Server.create(Source.LOAD, "print storage");
            executeCmdBestEffort(ctx, cmd);
            final String res = cmd.getExecutionResultAsString(mustBeSuccessful);
            if (res != null) {
                this.currentStorageName = String.valueOf(res);
            }
        } catch (Exception e) {
            logException(ctx, "Failed to get current storage name", e);
        }
    }

    private final void fetchQuickGrailEnvVarBestEffort(
        final ExecutionContext ctx, final String envVarName
    ) {
        try {
            final boolean mustBeSuccessful = true;
            final Server cmd = Server.create(Source.LOAD, "env print " + envVarName);
            executeCmdBestEffort(ctx, cmd);
            final String res = cmd.getExecutionResultAsString(mustBeSuccessful);
            if (res != null) {
                this.quickgrailEnvVars.put(envVarName, res);
            }
        } catch (Exception e) {
            logException(ctx, "Failed to get quickgrail query env: '" + envVarName + "'", e);
        }
    }

    private final void fetchAllQuickGrailEnvVarBestEffort(final ExecutionContext ctx) {
        for(final String envVarName : EnvironmentVariableManager.getAllNames()){
            fetchQuickGrailEnvVarBestEffort(ctx, envVarName);
        }
    }

    public synchronized void fetchBestEffort(final ExecutionContext ctx) throws Exception {
        if (ctx == null) {
            throw new IllegalArgumentException("NULL execution context");
        }
        fetchCurrentStorageNameBestEffort(ctx);
        fetchAllQuickGrailEnvVarBestEffort(ctx);
        this.isFetched = true;
    }

    public synchronized boolean isFetched() {
        return this.isFetched;
    }

    private synchronized List<String> getAsFileLines() {
        final List<String> fileLines = new ArrayList<String>();
        if (currentStorageName != null) {
            fileLines.add("set storage " + currentStorageName);
        }
        for (String key : quickgrailEnvVars.keySet()) {
            String val = quickgrailEnvVars.get(key);
            if (val != null && !EnvironmentVariableManager.isUndefinedConstant(val)) {
                fileLines.add("env set " + key + " " + val);
            }
        }
        return fileLines;
    }

    public synchronized void saveToFile(final String filePath)
        throws IllegalStateException, IllegalArgumentException, Exception {
        if (filePath == null) {
            throw new IllegalArgumentException("NULL file path to save query env to.");
        }
        if (!isFetched()) {
            throw new IllegalStateException("Must fetch query env before attempting to save to file");
        }
        final List<String> fileLines = getAsFileLines();
        try{
            FileUtility.writeLines(new File(filePath).getCanonicalPath(), fileLines);
        }catch(Exception e){
            throw e;
        }
    }

}
