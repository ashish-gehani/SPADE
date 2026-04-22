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
package spade.reporter.audit.linux.source.audit.event.record.type.path;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;

/**
 * Record subclass for PATH audit records.
 *
 * Contains file path information from the audit log including item index,
 * name, mode, nametype, and inode.
 *
 * Also provides static utility methods for looking up path records from
 * event data maps and parsing mode/permissions strings.
 *
 * Implements Comparable on the index field.
 */
public class Path extends Record implements Comparable<Path>{

	private final int itemNumber;
	private final String name;
	private final String mode;
	private final Nametype nametype;
	private final String inode;
	private final int pathType;
	private final String permissions;

	/**
	 * Construct from raw audit data (used by RecordFactory).
	 */
	public Path(
		final ID id, final String rawRecord
	){
		super(id, Type.PATH, rawRecord);
		final Map<String, String> tempMap = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.itemNumber = Integer.parseInt(tempMap.get("item"));
		this.mode = tempMap.get("mode") == null ? "0" : tempMap.get("mode");
		this.nametype = Nametype.parse(tempMap.get("nametype"));
		this.name = AuditStringParser.parse(rawRecord, "name");
		this.inode = tempMap.get("inode") == null ? "-1" : tempMap.get("inode");
		this.pathType = parsePathType(mode);
		this.permissions = parsePermissions(mode);
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

	public String getMode(){
		return mode;
	}

	public Nametype getNametype(){
		return nametype;
	}

	public String getInode(){
		return inode;
	}

	public int getPathType(){
		return pathType;
	}

	public String getPermissions(){
		return permissions;
	}

	/**
	 * Parses the string mode into an integer with base 8.
	 *
	 * @param mode base 8 representation of string
	 * @return integer value of mode
	 */
	public static int parsePathType(final String mode){
		try{
			return Integer.parseInt(mode, 8);
		}catch(Exception e){
			return 0;
		}
	}

	/**
	 * Returns the last 4 characters in the mode string.
	 * If the length of the mode string is less than 4 then pads the
	 * remaining zeroes at the beginning of the return value.
	 * If the mode argument is null then null returned.
	 *
	 * @param mode mode string with last 4 characters as permissions
	 * @return only the last 4 characters or null
	 */
	public static String parsePermissions(String mode){
		if (mode == null) {
			return null;
		}
		if(mode.length() >= 4){
			return mode.substring(mode.length() - 4);
		}else{
			int difference = 4 - mode.length();
			for(int a = 0; a < difference; a++){
				mode = "0" + mode;
			}
			return mode;
		}
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
		result = prime * result + ((mode == null) ? 0 : mode.hashCode());
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
		}else if(!mode.equals(other.mode))
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
