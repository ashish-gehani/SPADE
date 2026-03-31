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
package spade.reporter.audit.core.event.channel;

import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import spade.reporter.audit.core.event.Event;

public class Channel implements AutoCloseable {

    private final Config config;
    private final LinkedList<Event> buffer = new LinkedList<>();
    private final ChannelMetrics metrics = new ChannelMetrics();
    private final ScheduledExecutorService snapshotScheduler;
    private boolean closed = false;

    public Channel(final Config config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        this.config = config;
        if (config.getSnapshotIntervalMs() > 0) {
            snapshotScheduler = Executors.newSingleThreadScheduledExecutor();
            snapshotScheduler.scheduleAtFixedRate(
                metrics::snapshot,
                config.getSnapshotIntervalMs(),
                config.getSnapshotIntervalMs(),
                TimeUnit.MILLISECONDS
            );
        } else {
            snapshotScheduler = null;
        }
    }

    public synchronized void write(final Event event) throws InterruptedException, WriteTimeoutExpired {
        if (closed) {
            throw new IllegalStateException("Channel is closed");
        }
        if (event == null) {
            return;
        }
        long waitMs = 0;
        if (config.isWriteBlocking()) {
            final long writeTimeoutMs = config.getWriteTimeoutMs();
            final long deadline = writeTimeoutMs > 0 ? System.currentTimeMillis() + writeTimeoutMs : 0;
            final long waitStart = System.currentTimeMillis();
            while (buffer.size() >= config.getBufferMaxSize() && !closed) {
                if (writeTimeoutMs == 0) {
                    wait();
                } else {
                    final long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    wait(remaining);
                }
            }
            waitMs = System.currentTimeMillis() - waitStart;
        }
        if (buffer.size() >= config.getBufferMaxSize()) {
            if (config.getLossMode() == LossMode.LOSSY) {
                metrics.recordLost();
                return;
            } else {
                throw new WriteTimeoutExpired("Buffer is full");
            }
        }
        buffer.addLast(event);
        metrics.recordWritten(waitMs);
        notifyAll();
    }

    public synchronized Event read() throws InterruptedException, ReadTimeoutExpired {
        long waitMs = 0;
        if (config.isReadBlocking()) {
            final long readTimeoutMs = config.getReadTimeoutMs();
            final long deadline = readTimeoutMs > 0 ? System.currentTimeMillis() + readTimeoutMs : 0;
            final long waitStart = System.currentTimeMillis();
            while (buffer.isEmpty() && !closed) {
                if (readTimeoutMs == 0) {
                    wait();
                } else {
                    final long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    wait(remaining);
                }
            }
            waitMs = System.currentTimeMillis() - waitStart;
        }
        if (buffer.isEmpty()) {
            if (!closed) {
                throw new ReadTimeoutExpired("Buffer is empty");
            }
            return null;
        }
        final Event event = buffer.removeFirst();
        metrics.recordRead(waitMs);
        notifyAll();
        return event;
    }

    public ChannelMetrics getMetrics() {
        return metrics;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (snapshotScheduler != null) {
            snapshotScheduler.shutdown();
        }
        notifyAll();
    }

}
