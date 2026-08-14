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

package spade.analyzer.commandline.client;

import spade.core.analyzer.QueryableStorage;

public class State {
    
    private volatile boolean shutdown;
    private volatile boolean isRunning;

    private volatile QueryableStorage storage;

    public State() {
        this.shutdown = false;
        this.isRunning = false;
        this.storage = null;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(final boolean isRunning) {
        this.isRunning = isRunning;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public void setShutdown(final boolean shutdown) {
        this.shutdown = shutdown;
    }

    public QueryableStorage getStorage() {
        return storage;
    }

    public void setStorage(final QueryableStorage storage) {
        this.storage = storage;
    }

}
