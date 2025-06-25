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

import java.net.SocketException;

public class AmebaEventReaderTest {

    public static void main(String[] args) throws Exception {
        final AmebaEventReader eventReader = AmebaEventReader.create(AmebaConfig.create());

        new Thread(() -> {
            try {
                Thread.sleep(10_000);
                eventReader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        while (true) {
            String eventStr;
            try {
                eventStr = eventReader.readEvent();
            } catch (SocketException e) {
                continue;
            }
            if (eventStr == null)
                break;
            System.out.println(eventStr);
        }

        System.out.println("Buffer size: " + eventReader.getBufferCurrentSize());

        eventReader.close();
    }

}
