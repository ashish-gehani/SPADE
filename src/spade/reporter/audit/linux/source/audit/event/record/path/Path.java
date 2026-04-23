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
package spade.reporter.audit.linux.source.audit.event.record.path;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.type.fs.Inode;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.source.audit.event.record.type.Mode;

/**
 * Record subclass for PATH audit records.
 *
 * Contains file path information from the audit log including item index,
 * name, mode, nametype, and inode.
 *
 * Implements Comparable on the index field.
 */
public class Path extends Record implements Comparable<Path>{

	private final int itemNumber;
	private final String name;
	private final Mode mode;
	private final Nametype nametype;
	private final Inode inode;

	/**
	 * Construct from raw audit data (used by RecordFactory).
	 */
	public Path(
		final ID id, final String rawRecord
	){
		super(id, Type.PATH, rawRecord);
		final Map<String, String> tempMap = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.itemNumber = Integer.parseInt(tempMap.get("item"));
		this.nametype = Nametype.parse(tempMap.get("nametype"));
		this.name = AuditStringParser.parse(rawRecord, "name");
		this.mode = parseMode(this.nametype, tempMap);
		this.inode = parseInode(this.nametype, tempMap);
	}

	public int getItemNumber(){
		return itemNumber;
	}

	public String getName(){
		return name;
	}

	public String getPath(){
		return name;
	}

	public Mode getMode(){
		return mode;
	}

	public Nametype getNametype(){
		return nametype;
	}

	public Inode getInode(){
		return inode;
	}

	@Override
	public int compareTo(final Path o){
		if(o != null){
			return this.itemNumber - o.itemNumber;
		}
		return 1;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + itemNumber;
		result = prime * result + ((mode == null) ? 0 : mode.getOctal().hashCode());
		result = prime * result + ((nametype == null) ? 0 : nametype.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((inode == null) ? 0 : inode.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		final Path other = (Path)obj;
		if(itemNumber != other.itemNumber)
			return false;
		if(mode == null){
			if(other.mode != null)
				return false;
		}else if(!mode.getOctal().equals(other.mode.getOctal()))
			return false;
		if(nametype == null){
			if(other.nametype != null)
				return false;
		}else if(!nametype.equals(other.nametype))
			return false;
		if(name == null){
			if(other.name != null)
				return false;
		}else if(!name.equals(other.name))
			return false;
		if(inode == null){
			if(other.inode != null)
				return false;
		}else if(!inode.equals(other.inode))
			return false;
		return true;
	}

	private static Mode parseMode(final Nametype nametype, final Map<String, String> tempMap){
		if(nametype.isUnknown()){
			return null;
		}
		return Mode.parse(tempMap.get("mode"));
	}

	private static Inode parseInode(final Nametype nametype, final Map<String, String> tempMap){
		if(nametype.isUnknown()){
			return null;
		}
		final String raw = tempMap.get("inode");
		if(raw == null){
			return null;
		}
		return new Inode(Long.parseLong(raw));
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.PATH ? null : "Expected PATH, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Path(header.getId(), header.getRawLine());
		}
	}
}
