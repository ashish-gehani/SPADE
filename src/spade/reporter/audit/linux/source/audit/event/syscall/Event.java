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
package spade.reporter.audit.linux.source.audit.event.syscall;

import java.util.List;

import spade.reporter.audit.linux.source.audit.event.Type;
import spade.reporter.audit.linux.source.audit.event.record.Record;

/**
 * Event for SYSCALL events.
 *
 * Contains exactly one Syscall record plus optional supplementary
 * records (CWD, PATH, EXECVE, FD_PAIR, SOCKADDR, MMAP, IPC, MQ_SENDRECV).
 */
public class Event extends spade.reporter.audit.linux.source.audit.event.Event{

	private final spade.reporter.audit.linux.source.audit.event.record.Syscall syscallRecord;

	protected Event(
		final spade.reporter.audit.linux.source.audit.event.record.Syscall syscallRecord,
		final List<Record> records
	){
		super(syscallRecord.getId(), Type.SYSCALL);
		setRecords(records);
		this.syscallRecord = syscallRecord;
	}

	public spade.reporter.audit.linux.source.audit.event.record.Syscall getSyscallRecord(){
		return syscallRecord;
	}
}
