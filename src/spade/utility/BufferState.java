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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    
    private AtomicInteger itemsLeftToFlush = new AtomicInteger(0);
    private AtomicReference<State> stateRef = new AtomicReference<>();
    private AtomicReference<Future<?>> futureRef = new AtomicReference<>(null);

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
        if (isShutdown())
            return false; // never full
        return currentSize >= this.maxSize;
    }

    public boolean isReady () {
        return stateRef.get() == State.READY;
    }

    public void initialize () throws InvalidStateTransitonException {
        if (isShutdown())
            return;
        if (stateRef.compareAndSet(State.READY, State.INITIALIZED)) {
            futureRef.set(
                scheduler.schedule(
                    () -> {
                        expired();
                    },
                    ttlMillis,
                    TimeUnit.MILLISECONDS
                )
            );
        } else {
            throw new InvalidStateTransitonException(stateRef.get(), State.INITIALIZED);
        }
    }

    private void expired () {
        // Expected but not required to throw since internal func:
        // if (this.state != State.INITIALIZED)
        //     throw new InvalidStateTransitonException(this.state, State.EXPIRED);
        if (isShutdown())
            return;
        stateRef.set(State.EXPIRED);
    }

    public boolean isExpired () {
        return stateRef.get() == State.EXPIRED;
    }

    public void initializeFlushing (final int itemsToFlush) throws InvalidStateTransitonException {
        if (isShutdown())
            return;
        if (stateRef.compareAndSet(State.EXPIRED, State.FLUSHING)) {
            itemsLeftToFlush.set(Math.min(itemsToFlush, maxSize));
        } else {
            throw new InvalidStateTransitonException(stateRef.get(), State.FLUSHING);
        }
    }

    public boolean isFlushing () {
        return stateRef.get() == State.FLUSHING;
    }

    public void flushItem () throws InvalidStateTransitonException {
        if (isShutdown())
            return;
        if (stateRef.compareAndSet(State.FLUSHING, State.FLUSHING)) {
            itemsLeftToFlush.decrementAndGet();
        } else {
            throw new InvalidStateTransitonException(stateRef.get(), State.FLUSHING);
        }
    }

    public boolean isFlushed () throws InvalidStateTransitonException {
        if (isShutdown())
            return true; // always flushed
        if (stateRef.compareAndSet(State.FLUSHING, State.FLUSHING)) {
            return itemsLeftToFlush.get() <= 0;
        } else {
            throw new InvalidStateTransitonException(stateRef.get(), State.FLUSHING);
        }
    }

    public void makeReady () throws InvalidStateTransitonException {
        if (isShutdown())
            return;
        final Future<?> currentFuture = futureRef.getAndSet(null);
        if (currentFuture != null) {
            try {
                currentFuture.cancel(true);
            } catch (Exception e) {
                // ignore
            }
        }
        stateRef.set(State.READY);
        itemsLeftToFlush.set(0);
    }

    public void shutdown () {
        if (isShutdown())
            return;
        stateRef.set(State.SHUTDOWN);
        scheduler.shutdown();
    }

    public boolean isShutdown () {
        return stateRef.get() == State.SHUTDOWN;
    }

    public static class InvalidStateTransitonException extends Exception {
        public InvalidStateTransitonException (State from, State to) {
            super("Invalid buffer state transition from '" + from.name() + "' to '" + to.name() + "'");
        }
    }
}
