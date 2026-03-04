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

package spade.core.analyzer.command.execution;

import spade.core.analyzer.QueryableStorage;
import spade.core.analyzer.RequiredConfig;
import spade.core.analyzer.command.exception.UnexpectedFailure;
import spade.query.quickgrail.QuickGrailExecutor;

public class Context {
    
    private volatile boolean shutdown;

    private RequiredConfig requiredConfig;

    private QueryableStorage storage;

    private QuickGrailExecutor quickgrailExecutor;

    public Context(final RequiredConfig requiredConfig)
        throws IllegalArgumentException, UnexpectedFailure {
        if (requiredConfig == null) {
            throw new IllegalArgumentException("Null required config");
        }
        this.shutdown = false;
        this.requiredConfig = requiredConfig;
        this.storage = null;
        this.quickgrailExecutor = new QuickGrailExecutor();
    }

    public synchronized RequiredConfig getRequiredConfig() {
        return this.requiredConfig;
    }

    public synchronized boolean isShutdown() {
        return this.shutdown;
    }

    public synchronized void shutdown() {
        this.shutdown = true;
    }

    public synchronized boolean isStorageSet() {
        return this.storage != null;
    }

    public synchronized void setStorage(final QueryableStorage storage) {
        this.storage = storage;
    }

    public synchronized QueryableStorage getStorage() {
        return this.storage;
    }

    public synchronized QuickGrailExecutor getQuickGrailExecutor() {
        return this.quickgrailExecutor;
    }
}
