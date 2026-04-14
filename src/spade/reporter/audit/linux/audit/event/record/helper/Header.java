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
package spade.reporter.audit.linux.audit.event.record.helper;

import spade.reporter.audit.linux.audit.event.ID;
import spade.reporter.audit.linux.audit.event.Num;
import spade.reporter.audit.linux.audit.event.Timestamp;
import spade.reporter.audit.linux.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.audit.event.record.Type;

/**
 * Parses the header of a raw audit log line into its component fields.
 *
 * Expected format: type=XXX msg=audit(TIMESTAMP:Num): rest_of_data
 *
 * Uses indexOf/substring only — no regexes.
 */
public final class Header{

	private final Type type;
	private final ID id;
	private final String rawLine;

	public Header(final Type type, final ID id, final String rawLine){
		this.type = type;
		this.id = id;
		this.rawLine = rawLine;
	}

	public Type getType(){
		return type;
	}

	public ID getId(){
		return id;
	}

	public String getRawLine(){
		return rawLine;
	}

	/**
	 * Parse a raw audit log line header.
	 *
	 * @param rawLine the raw audit log line
	 * @return parsed header fields
	 * @throws MalformedRecordException if the line is malformed
	 */
	public static Header parse(final String rawLine) throws MalformedRecordException{
		if(rawLine == null || rawLine.isBlank()){
			throw new MalformedRecordException("NULL/Empty audit record", rawLine);
		}
		final String typeStr = StringHelper.substringBetween(rawLine, "type=", " ");
		if(typeStr == null){
			throw new MalformedRecordException("No 'type' in the audit record", rawLine);
		}
		final Type type = Type.fromAuditName(typeStr);
		final String eventIdStr = StringHelper.substringBetween(rawLine, ":", "):");
		if(eventIdStr == null){
			throw new MalformedRecordException("No event id in the audit record", rawLine);
		}
		final Num eventId = new Num(eventIdStr);
		final String timeStr = StringHelper.substringBetween(rawLine, "(", ":");
		if(timeStr == null){
			throw new MalformedRecordException("No event time in the audit record", rawLine);
		}
		final Timestamp time;
		try{
			time = new Timestamp(timeStr);
		}catch(NumberFormatException e){
			throw new MalformedRecordException("Malformed event time: " + timeStr, rawLine);
		}
		return new Header(type, new ID(eventId, time), rawLine);
	}
}
