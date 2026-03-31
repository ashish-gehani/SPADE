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

/**
 * Low-level string extraction utilities using indexOf/substring only.
 * No regexes.
 */
public final class StringHelper{

	private StringHelper(){
	}

	/**
	 * Extract substring between two delimiters using indexOf only.
	 *
	 * @param str the source string
	 * @param open the opening delimiter
	 * @param close the closing delimiter
	 * @return the substring between the delimiters, or null if not found
	 */
	public static String substringBetween(final String str, final String open, final String close){
		if(str == null || open == null || close == null){
			return null;
		}
		final int start = str.indexOf(open);
		if(start < 0){
			return null;
		}
		final int contentStart = start + open.length();
		final int end = str.indexOf(close, contentStart);
		if(end < 0){
			return null;
		}
		return str.substring(contentStart, end);
	}

	/**
	 * Extract substring after a delimiter using indexOf only.
	 *
	 * @param str the source string
	 * @param separator the delimiter
	 * @return the substring after the delimiter, or null if not found
	 */
	public static String substringAfter(final String str, final String separator){
		if(str == null || separator == null){
			return null;
		}
		final int pos = str.indexOf(separator);
		if(pos < 0){
			return null;
		}
		return str.substring(pos + separator.length());
	}
}
