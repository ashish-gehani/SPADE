/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.reporter.audit;

import org.apache.commons.lang.StringUtils;

public class AuditRecord{

	public final String id;
	public final String time;
	public final String type;
	public final String data;
	
	public AuditRecord(final String auditRecord) throws MalformedAuditDataException{
		if(auditRecord == null || auditRecord.isBlank()){
			throw new MalformedAuditDataException("NULL/Empty audit record", auditRecord);
		}
		this.type = StringUtils.substringBetween(auditRecord, "type=", " ");
		if(this.type == null){
			throw new MalformedAuditDataException("No 'type' in the audit record", auditRecord);
		}
		this.id = StringUtils.substringBetween(auditRecord, ":", "):");
		if(this.id == null){
			throw new MalformedAuditDataException("No event id in the audit record", auditRecord);
		}
		this.time = StringUtils.substringBetween(auditRecord, "(", ":");
		if(this.time == null){
			throw new MalformedAuditDataException("No event time in the audit record", auditRecord);
		}
		this.data = StringUtils.substringAfter(auditRecord, "):").trim();
	}

	@Override
	public String toString(){
		return "AuditRecord [id=" + id + ", time=" + time + ", type=" + type + ", data=" + data + "]";
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((data == null) ? 0 : data.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((time == null) ? 0 : time.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		AuditRecord other = (AuditRecord)obj;
		if(data == null){
			if(other.data != null)
				return false;
		}else if(!data.equals(other.data))
			return false;
		if(id == null){
			if(other.id != null)
				return false;
		}else if(!id.equals(other.id))
			return false;
		if(time == null){
			if(other.time != null)
				return false;
		}else if(!time.equals(other.time))
			return false;
		if(type == null){
			if(other.type != null)
				return false;
		}else if(!type.equals(other.type))
			return false;
		return true;
	}
}
