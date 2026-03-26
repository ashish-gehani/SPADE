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
package spade.reporter.audit.reader;

import spade.reporter.audit.las.event.Factory;

public final class State{

	private final spade.reporter.audit.las.event.record.Factory recordFactory;
	private final Factory eventFactory;

	public State(final Config config){
		if(config == null){
			throw new IllegalArgumentException("Config cannot be NULL");
		}
		final boolean verbose = config.isVerboseEventFactory();
		this.recordFactory = new spade.reporter.audit.las.event.record.Factory(verbose);
		this.eventFactory = new Factory(verbose);
	}

	public spade.reporter.audit.las.event.record.Factory getRecordFactory(){
		return recordFactory;
	}

	public Factory getEventFactory(){
		return eventFactory;
	}
}
