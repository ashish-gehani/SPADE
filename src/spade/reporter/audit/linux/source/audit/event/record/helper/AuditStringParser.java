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
package spade.reporter.audit.linux.source.audit.event.record.helper;

import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.utility.HelperFunctions;

/**
 * Parses individual audit string values from record data.
 *
 * Handles three value formats:
 *   key="value"       -> value (quoted string)
 *   key=(value)       -> value (parenthesized, returns null if "null")
 *   key=hexvalue      -> decoded hex string
 *
 * Uses indexOf/substring only — no regexes.
 */
public final class AuditStringParser{

	private AuditStringParser(){
	}

	/**
	 * Parse a value for a given key from audit data.
	 *
	 * @param recordData the record data string
	 * @param key the key to search for
	 * @return the parsed value, or null if not found
	 */
	public static String parse(final String recordData, final String key){
		final String formattedKey = key + "=";
		final int keyStartIndex = recordData.indexOf(formattedKey);
		if(keyStartIndex < 0){
			return null;
		}
		final int valueStartIndex = keyStartIndex + formattedKey.length();
		if(valueStartIndex >= recordData.length()){
			return null;
		}
		final char valueFirstChar = recordData.charAt(valueStartIndex);
		if(valueFirstChar == '"'){
			final int valueEndIndex = recordData.indexOf('"', valueStartIndex + 1);
			if(valueEndIndex < 0){
				return null;
			}
			return recordData.substring(valueStartIndex + 1, valueEndIndex);
		}else if(valueFirstChar == '('){
			final int valueEndIndex = recordData.indexOf(')', valueStartIndex + 1);
			if(valueEndIndex < 0){
				return null;
			}
			final String value = recordData.substring(valueStartIndex + 1, valueEndIndex);
			if(value.equals("null")){
				return null;
			}
			return value;
		}else{
			int valueEndIndex = recordData.indexOf(' ', valueStartIndex + 1);
			if(valueEndIndex < 0){
				valueEndIndex = recordData.length();
			}
			final String hexValue = recordData.substring(valueStartIndex, valueEndIndex);
			return HelperFunctions.decodeHex(hexValue);
		}
	}

	/**
	 * Non-null version of parseAuditString.
	 *
	 * @param recordData the record data string
	 * @param key the key to search for
	 * @return the parsed value
	 * @throws MalformedRecordException if the value is null/missing
	 */
	public static String mustParse(final String recordData, final String key)
			throws MalformedRecordException{
		final String value = parse(recordData, key);
		if(value == null){
			throw new MalformedRecordException("Missing field: " + key);
		}
		return value;
	}
}
