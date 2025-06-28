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
package spade.reporter.audit;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.bpf.ameba.AuditRecordStream;
import spade.utility.BufferState;
import spade.utility.HelperFunctions;
import spade.utility.TimeoutInputStreamLineReader;

public class MultiStreamAuditRecordReader {

    private static final Logger logger = Logger.getLogger(MultiStreamAuditRecordReader.class.getName());

    private final MultiStreamAuditRecordReaderConfig config;

    private final ExecutorService executor;

    private final AuditRecordStream reader1;
    private final TimeoutInputStreamLineReader reader2;

    private AtomicBoolean closed = new AtomicBoolean(false);

    private AtomicBoolean running1 = new AtomicBoolean(false);
    private AtomicBoolean running2 = new AtomicBoolean(false);

    private final PriorityBlockingQueue<AuditRecord> buffer;
    private BufferState bufferState;

    private final AtomicReference<Exception> readerException = new AtomicReference<>(null);

    public MultiStreamAuditRecordReader(
        final MultiStreamAuditRecordReaderConfig config,
        final AuditRecordStream stream1,
        final InputStream stream2
    ) {
        this.config = config;

        this.reader1 = stream1;
        this.reader2 = new TimeoutInputStreamLineReader(stream2, config.getIOTimeout());

        this.bufferState = new BufferState(
            this.config.getBufferSize(),
            this.config.getBufferTtl()
        );
        this.buffer = new PriorityBlockingQueue<>();

        this.executor = Executors.newFixedThreadPool(2);
        startReaderThreads();
    }

    private void startReaderThreads() {
        executor.submit(() -> {
            running1.set(true);
            try {
                while (running1.get() == true) {
                    AuditRecord record = reader1.read();
                    if (record == null) break;
                    buffer.put(record);
                }
            } catch (Exception e) {
                readerException.compareAndSet(null, e);
            } finally {
                running1.set(false);
                try {
                    reader1.close();
                } catch (Exception e) {
                    // Only log any close errors
                    logger.log(Level.WARNING, "Failed to close ameba to audit record stream", e);
                }
            }
        });

        executor.submit(() -> {
            running2.set(true);
            try {
                while (running2.get() == true) {
                    String line = null;
                    try {
                        line = reader2.readLine();
                    } catch (TimeoutException e) {
                        // ignore
                        HelperFunctions.sleepSafe(100);
                        continue;
                    }
                    if (line == null) break;
                    try {
                        AuditRecord record = new AuditRecord(line);
                        buffer.put(record);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to convert audit record: " + line, e);
                    }
                }
            } catch (Exception e) {
                readerException.compareAndSet(null, e);
            } finally {
                running2.set(false);
                try {
                    reader2.close();
                } catch (Exception e) {
                    // Only log any close errors
                    logger.log(Level.WARNING, "Failed to close audit record reader", e);
                }
            }
        });
    }

    public AuditRecord read() throws Exception {
        Exception ex = readerException.get();
        if (ex != null) throw ex;

        while (true) {
            if (this.bufferState.isReady()) {
                this.bufferState.initialize();
            }

            if (
                running1.get() == true
                || running2.get() == true
            ) {

                if (this.bufferState.isExpired()) {
                    if (buffer.isEmpty()) {
                        this.bufferState.makeReady();
                        continue;
                    }
                    this.bufferState.initializeFlushing(buffer.size());
                }

                if (this.bufferState.isFlushing()) {
                    final AuditRecord ret = buffer.poll();

                    this.bufferState.flushItem();

                    if (this.bufferState.isFlushed() || ret == null) {
                        this.bufferState.makeReady();
                    }

                    if (ret == null) {
                        continue;
                    }

                    return ret;
                }
                if (this.bufferState.isFull(buffer.size())) {
                    return this.buffer.poll();
                }
                HelperFunctions.sleepSafe(100);
                continue;
            } else {
                return this.buffer.poll();
            }
        }
    }

    public boolean isClosed () {
        return closed.get();
    }

    public void close() throws Exception {
        if (closed.compareAndSet(false, true)) {
            running1.set(false);
            running2.set(false);

            this.bufferState.shutdown();

            Exception ex = null;

            try { executor.shutdownNow(); } catch (Exception e) { ex = e; }

            if (ex != null) throw ex;
        }
    }

    public static MultiStreamAuditRecordReader create(
        final AuditRecordStream stream1,
        final InputStream stream2
    ) throws Exception {
        final MultiStreamAuditRecordReaderConfig config = MultiStreamAuditRecordReaderConfig.create();
        final MultiStreamAuditRecordReader r = new MultiStreamAuditRecordReader(
            config,
            stream1,
            stream2
        );
        return r;
    }
}
