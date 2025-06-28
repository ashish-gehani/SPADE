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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class FileReader implements OutputReader {

    private final AsynchronousFileChannel asyncChannel;
    private final ByteBuffer buffer;
    private final StringBuilder lineBuffer;
    private final AtomicLong position;
    private final long timeoutMillis;

    private static final int BUFFER_SIZE = 4096;

    public FileReader(Path path, long timeoutMillis) throws IOException {
        this.asyncChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
        this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.lineBuffer = new StringBuilder();
        this.position = new AtomicLong(0);
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public Record read() throws InterruptedException, ExecutionException, TimeoutException, JSONException {
        while (true) {
            int bytesRead = readChunkWithTimeout();
            if (bytesRead <= 0 && lineBuffer.length() == 0) {
                return null; // EOF
            }

            int newlineIdx = lineBuffer.indexOf("\n");
            if (newlineIdx != -1) {
                String line = lineBuffer.substring(0, newlineIdx).trim();
                lineBuffer.delete(0, newlineIdx + 1);
                if (!line.isEmpty()) {
                    return new Record(new JSONObject(line));
                }
            }

            if (bytesRead == -1) {
                return null; // EOF
            }
        }
    }

    private int readChunkWithTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        buffer.clear();
        Future<Integer> future = asyncChannel.read(buffer, position.get());
        int bytesRead;
        try {
            bytesRead = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[bytesRead];
            buffer.get(data);
            position.addAndGet(bytesRead);
            lineBuffer.append(new String(data, StandardCharsets.UTF_8));
        }
        return bytesRead;
    }

    @Override
    public void close() throws IOException {
        asyncChannel.close();
    }

    public static OutputReader create(final Config config) throws Exception {
        return new FileReader(
            Paths.get(config.getOutputFilePath()),
            config.getOutputReaderTimeoutMillis()
        );
    }
}