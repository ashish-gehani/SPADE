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
package spade.reporter.audit.linux.audit.event.record;

/**
 * Exception thrown when an audit record line cannot be parsed.
 *
 * Analogous to {@link spade.reporter.audit.MalformedAuditDataException}
 * but scoped to the record layer.
 */
public class MalformedRecordException extends Exception{

	private static final long serialVersionUID = 1L;

	public MalformedRecordException(final String msg){
		super(msg);
	}

	public MalformedRecordException(final String msg, final String data){
		super(String.format("msg='%s' data='%s'", msg, data));
	}

	public MalformedRecordException(final String msg, final String data, final Throwable t){
		super(String.format("msg='%s' data='%s'", msg, data), t);
	}
}
