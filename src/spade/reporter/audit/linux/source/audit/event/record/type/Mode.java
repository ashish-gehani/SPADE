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
package spade.reporter.audit.linux.source.audit.event.record.type;

import spade.reporter.audit.linux.type.fs.Permission;

/**
 * File permissions and type encoded in octal (e.g. 0100644).
 */
public class Mode{

	private final String octal;
	private final int value;
	private final Permission permissions;

	private Mode(final String octal, final int value, final Permission permissions){
		this.octal = octal;
		this.value = value;
		this.permissions = permissions;
	}

	/**
	 * Parses a raw mode string from an audit PATH record.
	 * A null or missing value is treated as "0".
	 */
	public static Mode parse(final String raw){
		final String octal = raw == null ? "0" : raw;
		int value;
		try{
			value = Integer.parseInt(octal, 8);
		}catch(final Exception e){
			value = 0;
		}
		return new Mode(octal, value, parsePermissions(octal));
	}

	/** Raw octal string as it appears in the audit record (e.g. "0100644"). */
	public String getOctal(){
		return octal;
	}

	/** Full mode as an integer (file type bits + permission bits). */
	public int getValue(){
		return value;
	}

	/** Last four octal digits representing the permission bits (e.g. 0644). */
	public Permission getPermissions(){
		return permissions;
	}

	private static Permission parsePermissions(final String octal){
		final String permOctal;
		if(octal.length() >= 4){
			permOctal = octal.substring(octal.length() - 4);
		}else{
			String padded = octal;
			while(padded.length() < 4){
				padded = "0" + padded;
			}
			permOctal = padded;
		}
		try{
			return new Permission(Integer.parseInt(permOctal, 8));
		}catch(final Exception e){
			return new Permission(0);
		}
	}
}
