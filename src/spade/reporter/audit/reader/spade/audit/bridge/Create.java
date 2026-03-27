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
package spade.reporter.audit.reader.spade.audit.bridge;

import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.Input;

public class Create{

	public static Reader reader(
		final Input input,
		final AuditConfiguration auditConfiguration,
		final spade.reporter.audit.las.event.record.Factory recordFactory,
		final spade.reporter.audit.las.event.Factory eventFactory
	) throws Exception{
		if(input == null){
			throw new IllegalArgumentException("Input cannot be NULL");
		}
		if(auditConfiguration == null){
			throw new IllegalArgumentException("AuditConfiguration cannot be NULL");
		}
		if(recordFactory == null){
			throw new IllegalArgumentException("Record factory cannot be NULL");
		}
		if(eventFactory == null){
			throw new IllegalArgumentException("Event factory cannot be NULL");
		}
		final ProcessConfig processConfig = new ProcessConfig(
			input.getSPADEAuditBridgePath(),
			input.getMode(),
			input.getInputLogListFile(),
			input.getInputDir(),
			input.getInputDirTime(),
			input.getLinuxAuditSocketPath(),
			input.isWaitForLog(),
			auditConfiguration.isUnits(),
			auditConfiguration.getMergeUnit()
		);
		final Process process = new Process(processConfig);
		return new Reader(process, recordFactory, eventFactory);
	}
}
