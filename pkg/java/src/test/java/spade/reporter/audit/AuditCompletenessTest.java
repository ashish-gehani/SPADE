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
package spade.reporter.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuditCompletenessTest{

	@Test
	void theBridgesLossMessagesWarnOnlyWhenSomethingWasLost(){
		assertTrue(AuditCompleteness.reportsLoss("Audit events not received so far (missing serial numbers): 129460"));
		assertTrue(AuditCompleteness.reportsLoss("Audit events not received in total (missing serial numbers): 3"));
		assertFalse(AuditCompleteness.reportsLoss("Audit events not received in total (missing serial numbers): 0"));
		assertTrue(AuditCompleteness.reportsLoss("Socket queue: peak 1073741824 bytes, 52428 bytes dropped because the queue was full"));
		assertFalse(AuditCompleteness.reportsLoss("Socket queue: peak 835515720 bytes, 0 bytes dropped because the queue was full"));
		assertFalse(AuditCompleteness.reportsLoss("UBSI flush the log buffer: 12 events"));
		assertFalse(AuditCompleteness.reportsLoss(null));
	}

	@Test
	void theBridgesSocketFailuresAreRecognised(){
		assertTrue(AuditCompleteness.reportsBridgeFailure(
				"spadeAuditBridge: Unable to connect to the socket: /var/run/audispd_events. Error: Permission denied"));
		assertTrue(AuditCompleteness.reportsBridgeFailure("spadeAuditBridge: Server closed the connection. Errror: Success"));
		assertTrue(AuditCompleteness.reportsBridgeFailure("spadeAuditBridge: Unable to start the socket reader thread. Error: x"));
		assertFalse(AuditCompleteness.reportsBridgeFailure("Audit events not received so far (missing serial numbers): 3"));
		assertFalse(AuditCompleteness.reportsBridgeFailure(null));
	}

	@Test
	void aMissingBridgeCountsAsRunnable(){
		// not a POSIX file system, or no such file: nothing to check, so no refusal
		assertTrue(AuditCompleteness.bridgeRunsAsRoot("/no/such/spadeAuditBridge"));
	}

	@Test
	void enrichedRecordsCarryTheSeparator(){
		final String raw = "type=SYSCALL msg=audit(1793583428.047:3609733): arch=c000003e syscall=44 success=yes exit=1056";
		assertFalse(AuditCompleteness.isEnrichedRecord(raw));
		assertTrue(AuditCompleteness.isEnrichedRecord(raw + "\u001dARCH=x86_64 SYSCALL=sendto UID=\"root\""));
	}

	@Test
	void theKernelKeepsUndeliveredRecordsOnlyWhenBootedWithAuditOn(){
		// this VM's command line, like a default Ubuntu install: no audit=
		assertFalse(AuditCompleteness.kernelKeepsUndeliveredRecords(
				"BOOT_IMAGE=/vmlinuz-6.8.0-146-generic root=/dev/mapper/ubuntu--vg-ubuntu--lv ro console=ttyS0,115200\n"));
		assertTrue(AuditCompleteness.kernelKeepsUndeliveredRecords("ro audit=1 quiet"));
		assertTrue(AuditCompleteness.kernelKeepsUndeliveredRecords("ro audit=on"));
		assertFalse(AuditCompleteness.kernelKeepsUndeliveredRecords("ro audit=0"));
		assertFalse(AuditCompleteness.kernelKeepsUndeliveredRecords("ro audit=1 audit=off"));
		assertTrue(AuditCompleteness.kernelKeepsUndeliveredRecords("ro audit=yes")); // any other value is on
		assertFalse(AuditCompleteness.kernelKeepsUndeliveredRecords("ro audit_backlog_limit=8192"));
		assertFalse(AuditCompleteness.kernelKeepsUndeliveredRecords(null));
	}
}
