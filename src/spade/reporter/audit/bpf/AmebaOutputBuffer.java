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

package spade.reporter.audit.bpf;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;

import spade.utility.BufferTtlState;
import spade.utility.HelperFunctions;

public class AmebaOutputBuffer {

    private final AmebaOutputReader reader;
    private final int bufferSize;
    private final Queue<AmebaRecord> buffer = new LinkedList<>();

    private volatile boolean eof = false;
    private volatile boolean closed = false;

    private final BufferTtlState bufferTtlState;

    public AmebaOutputBuffer(AmebaOutputReader reader, int bufferSize, long bufferTtlMillis) {
        this.reader = reader;
        this.bufferSize = bufferSize;
        this.bufferTtlState = new BufferTtlState(bufferTtlMillis);
    }

    public AmebaRecord poll() throws Exception {
        while (true) {
            this.bufferTtlState.initOrUpdate();
            // Empty the current buffer if closed or eof.
            // Get the element from buffer if buffer size exceeded.
            if (
                this.bufferTtlState.isExpired() ||
                this.closed || this.eof || buffer.size() >= bufferSize
            ) {
                final AmebaRecord ret = buffer.poll();
                if (this.bufferTtlState.isExpired() && ret == null) {
                    // The buffer has been emptied.
                    this.bufferTtlState.reset();
                    continue; // Go back to reading normally.
                }
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
