/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.storage.neo4j;

import org.neo4j.graphdb.Transaction;

import spade.storage.Neo4j;

public abstract class StorageTask<R>{

	private final Object completedLock = new Object();
	private volatile boolean completed = false;
	
	private final Object errorLock = new Object();
	private Throwable error = null;
	
	private final Object resultLock = new Object();
	private volatile R result;
	
	public final boolean commitBeforeExecution;
	public final boolean commitAfterExecution;
	
	private final Object transactionTimeoutInSecondsLock = new Object();
	private int transactionTimeoutInSeconds = -1;

	public StorageTask(final boolean commitBeforeExecution, final boolean commitAfterExecution){
		this.commitBeforeExecution = commitBeforeExecution;
		this.commitAfterExecution = commitAfterExecution;
	}
	
	public final boolean isTransactionTimeoutable(){
		synchronized(transactionTimeoutInSecondsLock){
			return transactionTimeoutInSeconds > 0;
		}
	}
	
	public final int getTransactionTimeoutInSeconds(){
		synchronized(transactionTimeoutInSecondsLock){
			return transactionTimeoutInSeconds;
		}
	}
	
	public final void setTransactionTimeoutInSeconds(final int transactionTimeoutInSeconds){
		synchronized(transactionTimeoutInSecondsLock){
			this.transactionTimeoutInSeconds = transactionTimeoutInSeconds;
		}
	}

	public final boolean isCompleted(){
		synchronized(completedLock){
			return this.completed;
		}
	}

	public final void completed(){
		synchronized(completedLock){
			this.completed = true;
		}
	}

	public final Throwable getError(){
		synchronized(errorLock){
			return this.error;
		}
	}

	public final void setError(final Throwable error){
		synchronized(errorLock){
			this.error = error;
		}
	}
	
	public final R getResult(){
		synchronized(resultLock){
			return this.result;
		}
	}

	public final void setResult(final R result){
		synchronized(resultLock){
			this.result = result;
		}
	}

	// Execute and set result if necessary
	public abstract R execute(final Neo4j storage, final Transaction tx) throws Exception;
	
}
