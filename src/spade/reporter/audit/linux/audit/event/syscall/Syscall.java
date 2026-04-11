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
package spade.reporter.audit.linux.audit.event.syscall;

import java.util.List;

import spade.reporter.audit.core.event.MalformedEventException;
import spade.reporter.audit.linux.audit.event.Event;
import spade.reporter.audit.linux.audit.event.Type;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Event for SYSCALL events.
 *
 * Contains exactly one {@link Syscall} record plus optional supplementary
 * records (CWD, PATH, EXECVE, FD_PAIR, SOCKADDR, MMAP, IPC, MQ_SENDRECV).
 */
public class Syscall extends Event{

	private final spade.reporter.audit.linux.audit.event.record.Syscall syscallRecord;

	protected Syscall(
		final spade.reporter.audit.linux.audit.event.record.Syscall syscallRecord,
		final List<Record> records
	){
		super(syscallRecord.getEventId(), syscallRecord.getTime(), Type.SYSCALL);
		setRecords(records);
		this.syscallRecord = syscallRecord;
	}

	public spade.reporter.audit.linux.audit.event.record.Syscall getSyscallRecord(){
		return syscallRecord;
	}

	public static class Creator extends Event.Creator{

		@Override
		protected String validate(final List<Record> records){
			if(records == null || records.isEmpty()){
				return "Expected at least one record for Syscall, got: "
					+ (records == null ? "null" : records.size());
			}
			int count = 0;
			for(final Record r : records){
				if(r instanceof spade.reporter.audit.linux.audit.event.record.Syscall) count++;
			}
			if(count == 0) return "Expected exactly one Syscall record, got none";
			if(count > 1) return "Expected exactly one Syscall record, got more than one";
			return null;
		}

		@Override
		protected Event create(final List<Record> records) throws MalformedEventException{
			final String error = validate(records);
			if(error != null) throw new MalformedEventException(error);
			spade.reporter.audit.linux.audit.event.record.Syscall syscall = null;
			for(final Record r : records){
				if(r instanceof spade.reporter.audit.linux.audit.event.record.Syscall){
					syscall = (spade.reporter.audit.linux.audit.event.record.Syscall) r;
					break;
				}
			}
			return new Syscall(syscall, records);
		}
	}
}
