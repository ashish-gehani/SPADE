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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import spade.reporter.audit.KernelModuleArgument.MonitorConnectionType;
import spade.reporter.audit.ProcessUserSyscallFilter.UserMode;

class KernelModuleArgumentTest{

	private static KernelModuleArgument withLogCt(final boolean netfilterHooksLogCt){
		return KernelModuleArgument.createArgument("1000", UserMode.IGNORE,
				Collections.emptySet(), Collections.emptySet(), false, true,
				true, netfilterHooksLogCt, false, Collections.emptySet(), Collections.emptySet());
	}

	@Test
	void netfilterHooksLogCtFalseLogsOnlyNewConnections(){
		// cfg/spade.reporter.Audit.config: "If false then only packets which represent a new connection are logged."
		final KernelModuleArgument k = withLogCt(false);
		assertEquals(MonitorConnectionType.ONLY_NEW, k.getNfHooksLogAllCt());
		assertTrue(k.toString().contains("nfHooksLogAllCt=ONLY_NEW"), k.toString());
	}

	@Test
	void netfilterHooksLogCtTrueLogsAllPackets(){
		// "If true then all packets logged."
		assertEquals(MonitorConnectionType.ALL, withLogCt(true).getNfHooksLogAllCt());
	}
}
