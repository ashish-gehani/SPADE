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
package spade.reporter.audit.linux.source.audit.event.type.syscall;

import java.util.List;

import spade.reporter.audit.core.source.event.MalformedEventException;
import spade.reporter.audit.linux.source.audit.event.record.Record;

public class Creator extends spade.reporter.audit.linux.source.audit.event.Creator{

	@Override
	protected String validate(final List<Record> records){
		if(records == null || records.isEmpty()){
			return "Expected at least one record for Syscall, got: "
				+ (records == null ? "null" : records.size());
		}
		int count = 0;
		for(final Record r : records){
			if(r instanceof spade.reporter.audit.linux.source.audit.event.record.type.Syscall) count++;
		}
		if(count == 0) return "Expected exactly one Syscall record, got none";
		if(count > 1) return "Expected exactly one Syscall record, got more than one";
		return null;
	}

	@Override
	protected spade.reporter.audit.linux.source.audit.event.Event create(
		final List<Record> records
	) throws MalformedEventException{
		final String error = validate(records);
		if(error != null) throw new MalformedEventException(error);
		spade.reporter.audit.linux.source.audit.event.record.type.Syscall syscall = null;
		for(final Record r : records){
			if(r instanceof spade.reporter.audit.linux.source.audit.event.record.type.Syscall){
				syscall = (spade.reporter.audit.linux.source.audit.event.record.type.Syscall) r;
				break;
			}
		}
		return new Event(syscall, records);
	}
}
