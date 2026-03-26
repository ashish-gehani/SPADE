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
 * Exception thrown when an audit event cannot be constructed from its records.
 *
 * Analogous to {@link spade.reporter.audit.MalformedAuditDataException}
 * but scoped to the event layer.
 */
public class MalformedEventException extends Exception{

	private static final long serialVersionUID = 1L;

	public MalformedEventException(final String msg){
		super(msg);
	}

	public MalformedEventException(final String msg, final String eventId){
		super(String.format("msg='%s' eventId='%s'", msg, eventId));
	}

	public MalformedEventException(final String msg, final String eventId, final Throwable t){
		super(String.format("msg='%s' eventId='%s'", msg, eventId), t);
	}
}
