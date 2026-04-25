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
package spade.reporter.audit.linux.platform.resource;

public enum Type{

	PATH_BLOCK_DEVICE("block device"),
	PATH_CHARACTER_DEVICE("character device"),
	PATH_DIRECTORY("directory"),
	PATH_FILE("file"),
	PATH_LINK("link"),
	PATH_NAMED_PIPE("named pipe"),
	PATH_POSIX_MESSAGE_QUEUE("posix message queue"),
	PATH_UNIX_SOCKET("unix socket"),
	MEMORY("memory"),
	NETWORK("network"),
	NETWORK_SOCKET_PAIR("network socket pair"),
	SYSTEMV_MESSAGE_QUEUE("systemv message queue"),
	SYSTEMV_SHARED_MEMORY("systemv shared memory"),
	UNKNOWN("unknown"),
	UNIX_SOCKET_PAIR("unix socket pair"),
	UNNAMED_PIPE("unnamed pipe")
	;

	public final String name;

	Type(final String name){
		this.name = name;
	}

}
