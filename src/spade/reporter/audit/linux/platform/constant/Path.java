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
package spade.reporter.audit.linux.platform.constant;

public class Path {

	public final String DEFAULT_ROOT = "/";
	public final String SEPARATOR = "/";
	
	public final int
		STAT_IFIFO		= 0010000,
		STAT_IFREG		= 0100000,
		STAT_IFSOCK		= 0140000,
		STAT_IFLNK		= 0120000,
		STAT_IFBLK		= 0060000,
		STAT_IFDIR		= 0040000,
		STAT_IFCHR		= 0020000,
		STAT_IFMT		= 00170000;

    protected Path () {}

}
