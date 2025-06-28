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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.tuple.Pair;

import spade.utility.BufferState;
import spade.utility.HelperFunctions;

public class OutputBuffer {

    private final OutputReader reader;
    private final Queue<Record> buffer = new LinkedList<>();

    private AtomicBoolean eof = new AtomicBoolean(false);
    private AtomicBoolean closed = new AtomicBoolean(false);

    private final BufferState bufferState;

    public OutputBuffer(OutputReader reader, int bufferSize, long bufferTtlMillis) {
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
    public Record poll() throws Exception {
        while (!(closed.get() || eof.get())) {
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
                final Record ret = buffer.poll();
                this.bufferState.flushItem();
                if (this.bufferState.isFlushed() || ret == null) {
                    this.bufferState.makeReady();
                }
                if (ret == null) {
                    continue;
                }
                return ret;
            }

            if (closed.get() || eof.get() || this.bufferState.isFull(buffer.size())) {
                final Record ret = buffer.poll();
                return ret;
            }

            // Read buffer
            Record record = null;
            try {
                record = this.reader.read();
            } catch (TimeoutException e) {
                HelperFunctions.sleepSafe(100);
                // ignore and re-loop
                continue;
            }

            // End of file/stream.
            if (record == null) {
                eof.set(true);
                // Return if anything in the buffer
                return buffer.poll();
            }

            // Add to buffer and loop over.
            buffer.add(record);
        }
        return buffer.poll();
    }

    public Pair<Integer, Record> findNext(
        final String taskCtxId, final int recordType
    ) throws Exception {
        int i = 0;
        for (Record r : buffer) {
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
        Iterator<Record> iterator = buffer.iterator();
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
        closed.set(true);
        this.bufferState.shutdown();
        this.reader.close();
    }

    public static OutputBuffer create(final Config config) throws Exception {
        return new OutputBuffer(
            OutputReader.create(config),
            config.getOutputBufferSize(),
            config.getOutputBufferTtl()
        );
    }
}
