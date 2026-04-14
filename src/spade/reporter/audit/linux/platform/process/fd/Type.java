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
package spade.reporter.audit.linux.platform.process.fd;

public enum Type{

	BLOCK_DEVICE("block device"),
	CHARACTER_DEVICE("character device"),
	DIRECTORY("directory"),
	FILE("file"),
	LINK("link"),
	MEMORY("memory"),
	NAMED_PIPE("named pipe"),
	NETWORK_SOCKET("network socket"),
	NETWORK_SOCKET_PAIR("network socket pair"),
	POSIX_MESSAGE_QUEUE("posix message queue"),
	SYSV_MESSAGE_QUEUE("system v message queue"),
	SYSV_SHARED_MEMORY("system v shared memory"),
	UNIX_SOCKET("unix socket"),
	UNIX_SOCKET_PAIR("unix socket pair"),
	UNKNOWN("unknown"),
	UNNAMED_PIPE("unnamed pipe");

	public final String subtype;

	Type(final String subtype){
		this.subtype = subtype;
	}

}
