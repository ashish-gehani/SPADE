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

import java.net.SocketException;

import spade.reporter.audit.AuditRecord;

public class AuditRecordStreamTest {

    public static void main(String[] args) throws Exception {
        final AuditRecordStream stream = AuditRecordStream.create(Config.create());

        new Thread(() -> {
            try {
                Thread.sleep(30_000);
                stream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        while (true) {
            AuditRecord record;
            try {
                record = stream.read();
            } catch (SocketException e) {
                // Socket closed.
                continue;
            }
            if (record == null)
                break;
            System.out.println(record);
        }

        System.out.println("Buffer size: " + stream.getBufferCurrentSize());

        stream.close();
    }

}
