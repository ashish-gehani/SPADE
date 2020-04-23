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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import spade.utility.HelperFunctions;

/**
 * A class to represent all of Path record data as received from Audit logs
 * 
 * Implements Comparable interface on the index field. And is immutable.
 * 
 */
public class PathRecord implements Comparable<PathRecord>{

	/**
	 * Value of the name field in the audit log
	 */
	private String path;
	/**
	 * Value of the item field in the audit log
	 */
	private int index;
	/**
	 * Value of the nametype field in the audit log
	 */
	private String nametype;
	/**
	 * Value of the mode field in the audit log
	 */
	private String mode;
	/**
	 * Extracted from the mode variable by parsing it with base-8
	 */
	private int pathType = 0;
	/**
	 * Extracted from the mode variable
	 */
	private String permissions = null;
	
	public PathRecord(int index, String path, String nametype, String mode){
		this.index = index;
		this.path = path;
		this.nametype = nametype;
		this.mode = mode;
		this.pathType = parsePathType(mode);
		this.permissions = parsePermissions(mode);
	}
	
	/**
	 * Parses the string mode into an integer with base 8
	 * 
	 * @param mode base 8 representation of string
	 * @return integer value of mode
	 */
	public static int parsePathType(String mode){
		try{
			return Integer.parseInt(mode, 8);
		}catch(Exception e){
			return 0;
		}
	}
	
	/**
	 * Returns the last 4 characters in the mode string.
	 * If the length of the mode string is less than 4 than pads the
	 * remaining zeroes at the beginning of the return value.
	 * If the mode argument is null then null returned.
	 * @param mode mode string with last 4 characters as permissions
	 * @return only the last 4 characters or null
	 */
	public static String parsePermissions(String mode){
		if(mode != null){
			if(mode.length() >= 4){
				return mode.substring(mode.length() - 4);
			}else{
				int difference = 4 - mode.length();
				for(int a = 0; a< difference; a++){
					mode = "0" + mode;
				}
				return mode;
			}
		}
		return null;
	}
	
	public String getPermissions(){
		return permissions;
	}
	
	public int getPathType(){
		return pathType;
	}
	
	public String getPath(){
		return path;
	}
	
	public String getNametype(){
		return nametype;
	}
	
	public int getIndex(){
		return index;
	}
	
	/**
	 * Compares based on index. If the passed object is null then 1 returned always
	 */
	@Override
	public int compareTo(PathRecord o) {
		if(o != null){
			return this.index - o.index;
		}
		return 1;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		result = prime * result + ((mode == null) ? 0 : mode.hashCode());
		result = prime * result + ((nametype == null) ? 0 : nametype.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PathRecord other = (PathRecord) obj;
		if (index != other.index)
			return false;
		if (mode == null) {
			if (other.mode != null)
				return false;
		} else if (!mode.equals(other.mode))
			return false;
		if (nametype == null) {
			if (other.nametype != null)
				return false;
		} else if (!nametype.equals(other.nametype))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PathRecord [path=" + path + ", index=" + index + ", nametype=" + nametype + ", mode=" + mode + "]";
	}
	
	/**
	 * Every path record in Audit log has a key 'nametype' which can be used to identify what the artifact
	 * referenced in the path record was.
	 * 
	 * Possible options for nametype:
	 * 1) PARENT -> parent directory of the path
	 * 2) CREATE -> path was created
	 * 3) NORMAL -> path was just used for read or write
	 * 4) DELETE -> path was deleted
	 * 5) UNKNOWN -> can't tell what was done with the path. So far seen that it only happens when a syscall fails.
	 * 
	 * Returns empty if none found
	 *  
	 * @param eventData eventData that contains the paths information in the format: path[Index], nametype[Index]. example, path0, nametype0 
	 * @param nametypeValue one of the above-mentioned values. Case sensitive compare operation on nametypeValue
	 * @return returns a list PathRecord objects sorted by their index in ascending order
	 */
	public static List<PathRecord> getPathsWithNametype(Map<String, String> eventData, String nametypeValue){
		List<PathRecord> pathRecords = new ArrayList<PathRecord>();
		if(eventData != null && nametypeValue != null){
			Long items = HelperFunctions.parseLong(eventData.get(AuditEventReader.ITEMS), 0L);
			for(int itemcount = 0; itemcount < items; itemcount++){
				if(nametypeValue.equals(eventData.get(AuditEventReader.NAMETYPE_PREFIX+itemcount))){
					PathRecord pathRecord = new PathRecord(itemcount, 
							eventData.get(AuditEventReader.PATH_PREFIX+itemcount), 
							eventData.get(AuditEventReader.NAMETYPE_PREFIX+itemcount), 
							eventData.get(AuditEventReader.MODE_PREFIX+itemcount));
					pathRecords.add(pathRecord);
				}
			}
		}
		Collections.sort(pathRecords);
		return pathRecords;
	}

	/**
	 * Returns the first PathRecord object where nametypeValue matches i.e. one with the lowest index
	 * 
	 * Returns null if none found
	 * 
	 * @param eventData eventData that contains the paths information in the format: path[Index], nametype[Index]. example, path0, nametype0 
	 * @param nametypeValue one of the above-mentioned values. Case sensitive compare operation on nametypeValue
	 * @return returns the PathRecord object with the lowest index
	 */
	public static PathRecord getFirstPathWithNametype(Map<String, String> eventData, String nametypeValue){
		List<PathRecord> pathRecords = getPathsWithNametype(eventData, nametypeValue);
		if(pathRecords == null || pathRecords.size() == 0){
			return null;
		}else{
			return pathRecords.get(0);
		}
	}
	
	/**
	 * Get the PathRecord with the item number given
	 * @param eventData
	 * @param itemNumberInt
	 * @return NULL if not found
	 */
	public static PathRecord getPathWithItemNumber(Map<String, String> eventData, int itemNumberInt){
		String path = eventData.get(AuditEventReader.PATH_PREFIX+itemNumberInt);
		if(path != null){
			String nametype = eventData.get(AuditEventReader.NAMETYPE_PREFIX+itemNumberInt);
			String mode = eventData.get(AuditEventReader.MODE_PREFIX+itemNumberInt);
			return new PathRecord(itemNumberInt, path, nametype, mode);
		}
		return null;
	}
	
	/**
	 * Get path from audit log. First see, if a path with CREATE nametype exists.
	 * If yes then return that. If no then check if path with NORMAL nametype exists.
	 * If yes then return that else return null.
	 * 
	 * @param eventData audit log event data as key values
	 * @return path/null
	 */
	public static PathRecord getPathWithCreateOrNormalNametype(Map<String, String> eventData){
		PathRecord pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_CREATE);
		if(pathRecord != null){
			return pathRecord;
		}else{
			pathRecord = getFirstPathWithNametype(eventData, AuditEventReader.NAMETYPE_NORMAL);
			return pathRecord;
		}
	}
}
