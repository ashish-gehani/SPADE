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

package spade.reporter.audit.bpf.ameba;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;

import spade.utility.BufferState;
import spade.utility.HelperFunctions;

public class AmebaOutputBuffer {

    private final AmebaOutputReader reader;
    private final Queue<AmebaRecord> buffer = new LinkedList<>();

    private volatile boolean eof = false;
    private volatile boolean closed = false;

    private final BufferState bufferState;

    public AmebaOutputBuffer(AmebaOutputReader reader, int bufferSize, long bufferTtlMillis) {
        this.reader = reader;
        this.bufferState = new BufferState(bufferSize, bufferTtlMillis);
    }

    /*
     * Read from the underlying async stream.
     *
     * Fill buffer upto bufferSize.
     *
     * If buffer full then empty buffer until buffer not full.
     *
     * If closed or EOF then empty the buffer.
     *
     * If buffer ttl expired then empty the buffer completely. Reset buffer ttl. Go back to reading more.
     */
    public AmebaRecord poll() throws Exception {
        while (true) {
            if (this.bufferState.isReady()) {
                this.bufferState.initialize();
            }
            if (this.bufferState.isExpired()) {
                if (buffer.isEmpty()) {
                    this.bufferState.makeReady();
                    continue;
                }
                this.bufferState.initializeFlushing(buffer.size());
            }
            if (this.bufferState.isFlushing()) {
                final AmebaRecord ret = buffer.poll();
                this.bufferState.flushItem();
                if (this.bufferState.isFlushed() || ret == null) {
                    this.bufferState.makeReady();
                }
                if (ret == null) {
                    continue;
                }
                return ret;
            }

            if (this.closed || this.eof || this.bufferState.isFull(buffer.size())) {
                final AmebaRecord ret = buffer.poll();
                return ret;
            }

            // Read buffer
            AmebaRecord record = null;
            try {
                record = this.reader.read();
            } catch (TimeoutException e) {
                HelperFunctions.sleepSafe(100);
                // ignore and re-loop
                continue;
            }

            // End of file/stream.
            if (record == null) {
                this.eof = true;
                // Return if anything in the buffer
                return buffer.poll();
            }

            // Add to buffer and loop over.
            buffer.add(record);
        }
    }

    public Pair<Integer, AmebaRecord> findNext(
        final String taskCtxId, final int recordType
    ) throws Exception {
        int i = 0;
        for (AmebaRecord r : buffer) {
            String rTaskCtxId = r.getTaskCtxId();
            if (rTaskCtxId.equals(taskCtxId)) {
                int rType = r.getRecordType();
                if (rType == recordType) {
                    return Pair.of(i, r);
                    // else: return -1, null; // commented out in original
                }
            }
            i++;
        }
        return Pair.of(-1, null);
    }

    public void removeIndex(int i) {
        int idx = 0;
        Iterator<AmebaRecord> iterator = buffer.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            if (idx == i) {
                iterator.remove();
                break;
            }
            idx++;
        }
    }

    public int getCurrentSize() {
        return this.buffer.size();
    }

    public void close() throws Exception {
        this.closed = true;
        this.bufferState.shutdown();
        this.reader.close();
    }

    public static AmebaOutputBuffer create(final AmebaConfig config) throws Exception {
        return new AmebaOutputBuffer(
            AmebaOutputReader.create(config),
            config.getOutputBufferSize(),
            config.getOutputBufferTtl()
        );
    }
}
