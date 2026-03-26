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
package spade.reporter.audit.las.event.record.helper;

import java.util.Map;

import spade.utility.HelperFunctions;

/**
 * Parses space-separated key=value pairs from audit record data.
 * Delegates to {@link HelperFunctions#parseKeyValPairs(String)}.
 */
public final class KeyValueParser{

	private KeyValueParser(){
	}

	/**
	 * Parse space-separated key=value pairs from audit data.
	 *
	 * @param data the data string with key=value pairs
	 * @return map of key-value pairs
	 */
	public static Map<String, String> parseKeyValuePairs(final String data){
		return HelperFunctions.parseKeyValPairs(data);
	}
}
