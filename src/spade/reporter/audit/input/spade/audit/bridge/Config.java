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

import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.Input;
import spade.reporter.audit.input.Type;

public class Config extends spade.reporter.audit.input.Config {

	public Config(
			final Input input,
			final AuditConfiguration auditConfiguration,
			final spade.reporter.audit.las.event.record.Factory recordFactory,
			final spade.reporter.audit.las.event.Factory eventFactory) {
		super(Type.SPADEAuditBridge, input, auditConfiguration, recordFactory, eventFactory);
	}
}
