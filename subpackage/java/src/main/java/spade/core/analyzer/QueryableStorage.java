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

package spade.core.analyzer;


import java.util.logging.Logger;
import java.util.logging.Level;

import spade.core.AbstractStorage;
import spade.core.Kernel;
import spade.core.exception.StorageNotQueryable;
import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.core.QueryInstructionExecutor;

public class QueryableStorage {

    private final Logger logger = Logger.getLogger(this.getClass().getName());
    
    private AbstractStorage storage;

    public QueryableStorage (final AbstractStorage storage) 
        throws StorageNotQueryable {
        if (storage == null) {
            throw new StorageNotQueryable("Null storage");
        }
        if (storage.getQueryInstructionExecutor() == null) {
            throw new StorageNotQueryable("Null query instruction executor");
        }
        this.storage = storage;
    }

    public AbstractStorage getStorage() {
        return this.storage;
    }

    public void doQueryStateCleanup() {
        if (storage == null) {
            return;
        }
        try {
            final QueryInstructionExecutor qie = storage.getQueryInstructionExecutor();
            if (qie == null) {
                return;
            }
            final AbstractQueryEnvironment aqe = qie.getQueryEnvironment();
            if (aqe == null) {
                return;
            }
            aqe.doGarbageCollection();
        } catch (Exception e) {
            // Log the exception. Don't need to throw it since it is going to be overwritten.
            logger.log(
                Level.WARNING, 
                "Failed to clear storage '" + this.storage.getClass().getSimpleName() + "' query state",
                e
            );
        }
    }

    public static QueryableStorage getCurrentQueryingDefaultInKernel() throws StorageNotQueryable {
        final AbstractStorage storage = Kernel.getDefaultQueryStorage();
        if (storage == null) {
            return null;
        }
        return new QueryableStorage(storage);
    }
}
