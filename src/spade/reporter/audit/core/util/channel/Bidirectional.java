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
package spade.reporter.audit.core.util.channel;

public class Bidirectional<W, R> implements AutoCloseable {

    private final Unidirectional<W> forwardChannel;
    private final Unidirectional<R> reverseChannel;

    public Bidirectional(final Config forwardConfig, final Config reverseConfig) {
        if (forwardConfig == null) {
            throw new IllegalArgumentException("forwardConfig cannot be null");
        }
        if (reverseConfig == null) {
            throw new IllegalArgumentException("reverseConfig cannot be null");
        }
        this.forwardChannel = new Unidirectional<>(forwardConfig);
        this.reverseChannel = new Unidirectional<>(reverseConfig);
    }

    // Sender side: write W into the forward channel
    public void write(final W item) throws InterruptedException, WriteTimeoutExpired {
        forwardChannel.write(item);
    }

    // Sender side: read R from the reverse channel
    public R read() throws InterruptedException, ReadTimeoutExpired {
        return reverseChannel.read();
    }

    // Receiver side: read W from the forward channel
    public W readForward() throws InterruptedException, ReadTimeoutExpired {
        return forwardChannel.read();
    }

    // Receiver side: write R into the reverse channel
    public void writeBack(final R item) throws InterruptedException, WriteTimeoutExpired {
        reverseChannel.write(item);
    }

    public Metrics getForwardMetrics() {
        return forwardChannel.getMetrics();
    }

    public Metrics getReverseMetrics() {
        return reverseChannel.getMetrics();
    }

    public boolean isClosed() {
        return forwardChannel.isClosed() && reverseChannel.isClosed();
    }

    @Override
    public void close() {
        forwardChannel.close();
        reverseChannel.close();
    }

}
