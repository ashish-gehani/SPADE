/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.storage.quickstep;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class QuickstepClient {
  private String serverIp;
  private int serverPort;

  /**
   * Constructor.
   *
   * @param serverIp Quickstep server IP address.
   * @param serverPort Quickstep server port.
   */
  public QuickstepClient(String serverIp, int serverPort) {
    this.serverIp = serverIp;
    this.serverPort = serverPort;
  }

  /**
   * Submit a query to Quickstep server and wait for result.
   *
   * @param query A SQL query (could be multiple statements) or Quickstep command.
   * @param data The associated data (currently for bulk-load only, i.e. COPY ... FROM stdin).
   * @return The response from Quickstep server.
   * @throws UnknownHostException
   * @throws IOException
   */
  public QuickstepResponse requestForResponse(String query, String data)
      throws UnknownHostException, IOException {
    Map<String, String> request = new HashMap<String, String>();
    request.put("query", query);
    if (data != null) {
      request.put("data", data);
    }
    Map<String, String> response = requestForResponse(request);
    if (!response.containsKey("stdout") || !response.containsKey("stderr")) {
      throw new RuntimeException("Invalid response");
    }
    return new QuickstepResponse(response.get("stdout"), response.get("stderr"));
  }

  /**
   * Submit a query to Quickstep server and wait for result.
   *
   * @param query A SQL query (could be multiple statements) or Quickstep command.
   * @return The response from Quickstep server.
   * @throws UnknownHostException
   * @throws IOException
   */
  public QuickstepResponse requestForResponse(String query)
      throws UnknownHostException, IOException {
    return requestForResponse(query, null);
  }

  /**
   * Internal request-response encoding and communication.
   *
   * @param request Should contain two fields "query" and "data".
   * @return The response contains two fields "stdout" and "stderr".
   */
  private Map<String, String> requestForResponse(final Map<String, String> request)
      throws UnknownHostException, IOException {
    Socket socket = new Socket(serverIp, serverPort);
    Write(new DataOutputStream(socket.getOutputStream()), request);
    Map<String, String> response = Read(new DataInputStream(socket.getInputStream()));
    socket.close();
    return response;
  }

  /**
   * Send request via socket.
   */
  private static void Write(DataOutputStream dos,
                            final Map<String, String> fields) throws IOException {
    final int numFields = fields.size();
    byte[][][] fieldData = new byte[numFields][2][];
    long[][] fieldSizes = new long[numFields][2];

    long totalSize = 0;
    int idx = 0;
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      final byte[] key = entry.getKey().getBytes();
      final byte[] value = entry.getValue().getBytes();
      fieldData[idx][0] = key;
      fieldData[idx][1] = value;
      fieldSizes[idx][0] = key.length;
      fieldSizes[idx][1] = value.length;
      totalSize += key.length + value.length;
      ++idx;
    }
    totalSize += Long.BYTES * (1 + 2 * numFields);

    // 8 bytes: total payload size
    dos.writeLong(totalSize);
    // 8 bytes: total number of key-value pairs
    dos.writeLong(numFields);
    // 8 * 2 * numFields bytes: individual key/value payload sizes
    for (int i = 0; i < numFields; ++i) {
      dos.writeLong(fieldSizes[i][0]);
      dos.writeLong(fieldSizes[i][1]);
    }
    // Actual payloads
    for (int i = 0; i < numFields; ++i) {
      dos.write(fieldData[i][0]);
      dos.write(fieldData[i][1]);
    }
    dos.flush();
  }

  /**
   * Receive response via socket.
   */
  private static Map<String, String> Read(DataInputStream dis) throws IOException {
    dis.readLong();
    Map<String, String> fields = new HashMap<String, String>();

    // Same encoding as those in Write().
    final int numFields = CastToInt(dis.readLong());
    int[][] fieldSizes = new int[numFields][2];
    int maxSize = 0;
    for (int i = 0; i < numFields; ++i) {
      final int keySize = CastToInt(dis.readLong());
      final int valueSize = CastToInt(dis.readLong());
      fieldSizes[i][0] = keySize;
      fieldSizes[i][1] = valueSize;
      maxSize = Math.max(maxSize, fieldSizes[i][0]);
      maxSize = Math.max(maxSize, fieldSizes[i][1]);
    }
    byte[] buffer = new byte[maxSize];
    for (int i = 0; i < numFields; ++i) {
      final int keySize = fieldSizes[i][0];
      final int valueSize = fieldSizes[i][1];
      dis.readFully(buffer, 0, keySize);
      String key = new String(buffer, 0, keySize);
      dis.readFully(buffer, 0, valueSize);
      String value = new String(buffer, 0, valueSize);
      fields.put(key, value);
    }
    return fields;
  }

  private static int CastToInt(final long value) {
    if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
      throw new RuntimeException("Value out of range");
    }
    return (int) value;
  }
}
