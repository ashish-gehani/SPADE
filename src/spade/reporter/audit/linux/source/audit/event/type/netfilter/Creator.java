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
package spade.reporter.audit.linux.source.audit.event.type.netfilter;

import java.util.List;

import spade.reporter.audit.core.source.event.MalformedEventException;
import spade.reporter.audit.linux.source.audit.event.record.Record;

public class Creator extends spade.reporter.audit.linux.source.audit.event.creator.Creator{

	@Override
	public String validate(final List<Record> records){
		if(records == null || records.size() != 1){
			return "Expected exactly one record for Netfilter event, got: "
				+ (records == null ? "null" : records.size());
		}
		final Record r = records.get(0);
		if(!(r.getType() == spade.reporter.audit.linux.source.audit.event.record.Type.NETFILTER)){
			return "Expected Netfilter record, got type: " + r.getType();
		}
		if(!(r instanceof spade.reporter.audit.linux.source.audit.event.record.type.Netfilter)){
			return "Expected Netfilter record, got class: " + r.getClass().getName();
		}
		return null;
	}

	@Override
	public spade.reporter.audit.linux.source.audit.event.Event create(
		final List<Record> records
	) throws MalformedEventException{
		final String error = validate(records);
		if(error != null) throw new MalformedEventException(error);
		return new Event(
			(spade.reporter.audit.linux.source.audit.event.record.type.Netfilter) records.get(0));
	}
}
