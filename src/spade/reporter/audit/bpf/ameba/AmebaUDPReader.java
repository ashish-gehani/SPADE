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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

public class AmebaUDPReader implements AmebaOutputReader {

    private final DatagramSocket socket;
    private final byte[] buffer;
    private final StringBuilder lineBuffer;
    private final Queue<String> pendingLines;

    public AmebaUDPReader(String ip, int port, int timeoutMillis) throws IOException {
        this.socket = new DatagramSocket(new InetSocketAddress(ip, port));
        this.socket.setSoTimeout(timeoutMillis);
        this.buffer = new byte[65535];
        this.lineBuffer = new StringBuilder();
        this.pendingLines = new LinkedList<>();
    }

    @Override
    public AmebaRecord read() throws TimeoutException, JSONException, IOException {
        while (pendingLines.isEmpty()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                String received = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                lineBuffer.append(received);

                int newlineIdx;
                while ((newlineIdx = lineBuffer.indexOf("\n")) != -1) {
                    String line = lineBuffer.substring(0, newlineIdx).trim();
                    lineBuffer.delete(0, newlineIdx + 1);
                    if (!line.isEmpty()) {
                        pendingLines.add(line);
                    }
                }

            } catch (SocketTimeoutException e) {
                throw new TimeoutException();
            }
        }

        String nextLine = pendingLines.poll();
        return nextLine != null ? new AmebaRecord(new JSONObject(nextLine)) : null;
    }

    @Override
    public void close() {
        socket.close();
    }

    public static AmebaOutputReader create(final AmebaConfig config) throws Exception {
        return new AmebaUDPReader(
            config.getOutputIP(),
            config.getOutputPort(),
            config.getOutputReaderTimeoutMillis()
        );
    }
}
