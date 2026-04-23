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
package spade.reporter.audit.linux.source.audit.event.record.helper;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;

/**
 * Syscall execution fields common to SYSCALL and UBSI_RAW records.
 */
public final class SyscallInfo{

	private final int syscall;
	private final boolean success;
	private final long exit;
	private final long arg0;
	private final long arg1;
	private final long arg2;
	private final long arg3;
	private final int items;
	private final ProcessInfo processInfo;

	private SyscallInfo(
		final int syscall, final boolean success, final long exit,
		final long arg0, final long arg1, final long arg2, final long arg3,
		final int items, final ProcessInfo processInfo
	){
		this.syscall = syscall;
		this.success = success;
		this.exit = exit;
		this.arg0 = arg0;
		this.arg1 = arg1;
		this.arg2 = arg2;
		this.arg3 = arg3;
		this.items = items;
		this.processInfo = processInfo;
	}

	public static SyscallInfo parse(final String data) throws MalformedRecordException{
		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(data);
		return new SyscallInfo(
			Integer.parseInt(fields.get("syscall")),
			parseSuccess(fields.get("success")),
			Long.parseLong(fields.get("exit")),
			Long.parseUnsignedLong(fields.get("a0"), 16),
			Long.parseUnsignedLong(fields.get("a1"), 16),
			Long.parseUnsignedLong(fields.get("a2"), 16),
			Long.parseUnsignedLong(fields.get("a3"), 16),
			Integer.parseInt(fields.get("items")),
			ProcessInfo.parse(data)
		);
	}

	private static boolean parseSuccess(final String value){
		return "true".equalsIgnoreCase(value)
			|| "yes".equalsIgnoreCase(value)
			|| "1".equals(value);
	}

	public int getSyscall(){ return syscall; }
	public boolean getSuccess(){ return success; }
	public long getExit(){ return exit; }
	public long getArg0(){ return arg0; }
	public long getArg1(){ return arg1; }
	public long getArg2(){ return arg2; }
	public long getArg3(){ return arg3; }
	public int getItems(){ return items; }
	public ProcessInfo getProcessInfo(){ return processInfo; }
}
