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
package spade.core;

/**
 * What SPADE writes to the configuration it saves at shutdown and replays at the next start.
 */
public final class SavedConfiguration{

	private SavedConfiguration(){}

	/**
	 * A storage's arguments without reset: a storage added with reset=true was emptied then, and
	 * replaying the reset would empty it again at every start.
	 *
	 * @param arguments the arguments the storage was added with, or null
	 * @return the arguments to save, or null if none are left
	 */
	public static String storageArgumentsToSave(final String arguments){
		if(arguments == null){
			return null;
		}
		final String kept = arguments.replaceAll("(?i)(^|\\s)reset\\s*=\\s*\\S*", " ").trim().replaceAll("\\s+", " ");
		return kept.isEmpty() ? null : kept;
	}
}
