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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeoutInputStreamLineReader implements Closeable {

    private final BufferedReader reader;
    private final ExecutorService executor;
    private final long timeoutMillis;

    public TimeoutInputStreamLineReader(InputStream inputStream, long timeoutMillis) {
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
        this.executor = Executors.newSingleThreadExecutor();
        this.timeoutMillis = timeoutMillis;
    }

    public String readLine() throws IOException, TimeoutException {
        Future<String> future = executor.submit(reader::readLine);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw new IOException("Error during read line", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during read line", e);
        }
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
        reader.close();
    }
}