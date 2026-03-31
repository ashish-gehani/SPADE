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
package spade.reporter.audit.input;

import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.Input;

public abstract class Config {

	private final Type type;
	private final Input input;
	private final AuditConfiguration auditConfiguration;
	private final spade.reporter.audit.las.event.record.Factory recordFactory;
	private final spade.reporter.audit.las.event.Factory eventFactory;

	protected Config(
			final Type type,
			final Input input,
			final AuditConfiguration auditConfiguration,
			final spade.reporter.audit.las.event.record.Factory recordFactory,
			final spade.reporter.audit.las.event.Factory eventFactory) {
		if(type == null){
			throw new IllegalArgumentException("Type cannot be NULL");
		}
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
		this.type = type;
		this.input = input;
		this.auditConfiguration = auditConfiguration;
		this.recordFactory = recordFactory;
		this.eventFactory = eventFactory;
	}

	public Type getType() {
		return type;
	}

	public Input getInput() {
		return input;
	}

	public AuditConfiguration getAuditConfiguration() {
		return auditConfiguration;
	}

	public spade.reporter.audit.las.event.record.Factory getRecordFactory() {
		return recordFactory;
	}

	public spade.reporter.audit.las.event.Factory getEventFactory() {
		return eventFactory;
	}
}
