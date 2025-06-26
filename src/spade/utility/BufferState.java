/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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

package spade.utility;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * A class to implement the functionality in advisory mode (i.e. nothing is enforced):
 * 
 * 1. The buffer is full
 * 2. The buffer elements have been buffered for N milliseconds
 * 3. Flush the last M buffered elements
 * 
 * It does so by maintaining states:
 * 
 *   -------------> SHUTDOWN <-------------
 *   ^           ^            ^           ^
 *   |           |            |           |
 * READY -> INITIALIZED -> EXPIRED -> FLUSHING
 *   ^___________|____________|___________|
 * 
 */
public class BufferState {
    
    private enum State {
        READY,
        INITIALIZED,
        EXPIRED,
        FLUSHING,
        SHUTDOWN
    };

    private final int maxSize;
    private final long ttlMillis;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    private volatile Future<?> future;
    private volatile State state;
    private volatile int itemsLeftToFlush;

    public BufferState (final int maxSize, final long ttlMillis) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        try {
            makeReady();
        } catch (InvalidStateTransitonException iste) {
            // ignore... should not happen.
        }
    }

    public boolean isFull (final int currentSize) {
        return currentSize >= this.maxSize;
    }

    public boolean isReady () {
        return this.state == State.READY;
    }

    public void initialize () throws InvalidStateTransitonException {
        if (this.state != State.READY)
            throw new InvalidStateTransitonException(this.state, State.INITIALIZED);
        this.state = State.INITIALIZED;
        this.future = scheduler.schedule(
            () -> {
                expired();
            },
            ttlMillis,
            TimeUnit.MILLISECONDS
        );
    }

    private void expired () {
        // Expected but not required to throw since internal func:
        // if (this.state != State.INITIALIZED)
        //     throw new InvalidStateTransitonException(this.state, State.EXPIRED);
        if (this.state == State.SHUTDOWN)
            return;
        this.state = State.EXPIRED;
    }

    public boolean isExpired () {
        return this.state == State.EXPIRED;
    }

    public void initializeFlushing (final int itemsToFlush) throws InvalidStateTransitonException {
        if (this.state != State.EXPIRED)
            throw new InvalidStateTransitonException(this.state, State.FLUSHING);
        this.itemsLeftToFlush = Math.min(itemsToFlush, maxSize);
        this.state = State.FLUSHING;
    }

    public boolean isFlushing () {
        return this.state == State.FLUSHING;
    }

    public void flushItem () throws InvalidStateTransitonException {
        if (this.state != State.FLUSHING)
            throw new InvalidStateTransitonException(this.state, State.FLUSHING);
        this.itemsLeftToFlush--;
    }

    public boolean isFlushed () throws InvalidStateTransitonException {
        if (this.state != State.FLUSHING)
            throw new InvalidStateTransitonException(this.state, State.FLUSHING);
        return this.itemsLeftToFlush <= 0;
    }

    public void makeReady () throws InvalidStateTransitonException {
        if (this.state == State.SHUTDOWN)
            throw new InvalidStateTransitonException(State.SHUTDOWN, this.state);
        if (this.future != null) {
            try {
                this.future.cancel(true);
            } catch (Exception e) {
                // ignore
            } finally {
                this.future = null;
            }
        }
        this.state = State.READY;
        this.itemsLeftToFlush = 0;
    }

    public void shutdown () {
        if (this.state == State.SHUTDOWN)
            return;
        this.state = State.SHUTDOWN;
        scheduler.shutdown();
    }

    public static class InvalidStateTransitonException extends Exception {
        public InvalidStateTransitonException (State from, State to) {
            super("Invalid buffer state transition from '" + from.name() + "' to '" + to.name() + "'");
        }
    }
}
