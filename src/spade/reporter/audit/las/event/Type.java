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
package spade.reporter.audit.las.event;

/**
 * Enum representing all Linux Audit Subsystem event types handled by the system.
 *
 * An event is a collection of records sharing the same event ID.
 * The event type is determined by the primary record in the event.
 */
public enum Type{

	SYSCALL,
	DAEMON_START,
	UBSI_ENTRY,
	UBSI_EXIT,
	UBSI_DEP,
	UBSI_RAW,
	NETIO,
	NETFILTER;
}
