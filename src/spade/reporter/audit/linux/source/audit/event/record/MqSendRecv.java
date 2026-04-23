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
package spade.reporter.audit.linux.source.audit.event.record;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.type.fd.Num;

/**
 * Record subclass for MQ_SENDRECV (Message Queue send/receive) audit records.
 *
 * Example raw data: mqdes=3 msg_len=266 msg_prio=0 abs_timeout_sec=0 abs_timeout_nsec=0
 */
public class MqSendRecv extends Record{

	private final Num mqdes;
	private final long msgLen;
	private final int msgPrio;
	private final long absTimeoutSec;
	private final long absTimeoutNsec;

	public MqSendRecv(
		final ID id, final String rawRecord
	){
		super(id, Type.MQ_SENDRECV, rawRecord);
		final Map<String, String> parsedFields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.mqdes = new Num(Integer.parseInt(parsedFields.get("mqdes")));
		this.msgLen = Long.parseLong(parsedFields.get("msg_len"));
		this.msgPrio = Integer.parseInt(parsedFields.get("msg_prio"));
		this.absTimeoutSec = Long.parseLong(parsedFields.get("abs_timeout_sec"));
		this.absTimeoutNsec = Long.parseLong(parsedFields.get("abs_timeout_nsec"));
	}

	public Num getMqdes(){
		return mqdes;
	}

	public long getMsgLen(){
		return msgLen;
	}

	public int getMsgPrio(){
		return msgPrio;
	}

	public long getAbsTimeoutSec(){
		return absTimeoutSec;
	}

	public long getAbsTimeoutNsec(){
		return absTimeoutNsec;
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.MQ_SENDRECV ? null : "Expected MQ_SENDRECV, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new MqSendRecv(header.getId(), header.getRawLine());
		}
	}
}
