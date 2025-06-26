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

import java.io.BufferedReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.bpf.AmebaToAuditRecordStream;
import spade.utility.BufferState;
import spade.utility.HelperFunctions;

public class MultiStreamAuditRecordReader {

    private static final Logger logger = Logger.getLogger(MultiStreamAuditRecordReader.class.getName());

    private final MultiStreamAuditRecordReaderConfig config;

    private final ExecutorService executor;

    private final AmebaToAuditRecordStream reader1;
    private final BufferedReader reader2;

    private volatile boolean running1 = false;
    private volatile boolean running2 = false;

    private final PriorityBlockingQueue<AuditRecord> buffer;
    private BufferState bufferState;

    private final AtomicReference<Exception> readerException = new AtomicReference<>(null);

    public MultiStreamAuditRecordReader(
        final MultiStreamAuditRecordReaderConfig config,
        final AmebaToAuditRecordStream stream1,
        final BufferedReader stream2
    ) {
        this.config = config;

        this.reader1 = stream1;
        this.reader2 = stream2;

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
            running1 = true;
            try {
                while (running1) {
                    AuditRecord record = reader1.read();
                    if (record == null) break;
                    buffer.put(record);
                }
            } catch (Exception e) {
                readerException.compareAndSet(null, e);
            } finally {
                running1 = false;
            }
        });

        executor.submit(() -> {
            running2 = true;
            try {
                while (running2) {
                    String line = reader2.readLine();
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
                running2 = false;
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

            if (running1 || running2) {

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

    public void close() throws Exception {
        running1 = running2 = false;

        this.bufferState.shutdown();

        Exception ex = null;

        try { executor.shutdownNow(); } catch (Exception e) { ex = e; }
        try { reader1.close(); } catch (Exception e) { if (ex == null) ex = e; }
        try { reader2.close(); } catch (Exception e) { if (ex == null) ex = e; }

        if (ex != null) throw ex;
    }

    public static MultiStreamAuditRecordReader create(
        final AmebaToAuditRecordStream stream1,
        final BufferedReader stream2
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
