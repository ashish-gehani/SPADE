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
package spade.reporter.audit.las.event;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.channel.Channel;
import spade.reporter.audit.las.event.channel.ReadTimeoutExpired;
import spade.reporter.audit.las.event.reader.Reader;
import spade.reporter.audit.las.event.writer.Writer;

public class Tee implements AutoCloseable {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private Reader source;
    private Writer sink;
    private Channel channel;
    private Thread pumpThread;

    public Tee(
        final Reader source,
        final Writer sink,
        final Channel channel
    ) throws IllegalArgumentException {
        if (source == null) {
            throw new IllegalArgumentException("NULL source");
        }
        if (sink == null) {
            throw new IllegalArgumentException("NULL sink");
        }
        if (channel == null) {
            throw new IllegalArgumentException("NULL channel");
        }
        this.source = source;
        this.sink = sink;
        this.channel = channel;
    }

    public synchronized void start() {
        if (pumpThread != null) {
            throw new IllegalStateException("Already started");
        }
        pumpThread = new Thread(this::pump, "tee-pump");
        pumpThread.setDaemon(true);
        pumpThread.start();
    }

    private void pump() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Event e;
                try {
                    e = source.readEvent();
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Failed to read event from source", ex);
                    break;
                }
                if (e == null) {
                    break;
                }
                writeEventToSink(e);
                writeEventToChannel(e);
            }
        } finally {
            closeSource();
            closeSink();
            closeChannel();
        }
    }

    public Event read() throws InterruptedException, ReadTimeoutExpired {
        return channel.read();
    }

    private void writeEventToChannel(final Event e) {
        try {
            channel.write(e);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to write event to channel", ex);
        }
    }

    private void writeEventToSink(final Event e) {
        try {
            sink.writeEvent(e);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to write event to sink", ex);
        }
    }

    @Override
    public synchronized void close() {
        if (pumpThread != null) {
            pumpThread.interrupt();
            pumpThread = null;
        }
    }

    private synchronized void closeSource() {
        if (source == null) {
            return;
        }
        try {
            source.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close source", e);
        } finally {
            source = null;
        }
    }

    private synchronized void closeSink() {
        if (sink == null) {
            return;
        }
        try {
            sink.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close sink", e);
        } finally {
            sink = null;
        }
    }

    private synchronized void closeChannel() {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to gracefully close channel", e);
        } finally {
            channel = null;
        }
    }

}
