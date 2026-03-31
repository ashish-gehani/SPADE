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
package spade.reporter.audit.linux.audit.event;

/**
 * Concrete event identity for Linux Audit Subsystem events.
 *
 * The LAS event ID is a decimal string (e.g. "1234567890"); {@link #parse}
 * converts it to the underlying {@code long}.
 */
public final class ID extends spade.reporter.audit.core.event.ID{

	public ID(final long id){
		super(id);
	}

	/**
	 * Parse the decimal event-ID string produced by the Linux Audit Subsystem.
	 *
	 * @param eventId decimal string, e.g. {@code "1234567890"}
	 * @return a new {@code ID} wrapping the parsed value
	 * @throws NumberFormatException if {@code eventId} is not a valid long
	 */
	public static ID parse(final String eventId){
		return new ID(Long.parseLong(eventId));
	}

}
