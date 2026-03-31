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
package spade.reporter.audit.input.spade.audit.bridge;

public class Helper{

	public static Process createProcess(final Config config) throws Exception{
		final spade.reporter.audit.Input input = config.getInput();
		final spade.reporter.audit.AuditConfiguration auditConfiguration = config.getAuditConfiguration();
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
		return process;
	}
}
