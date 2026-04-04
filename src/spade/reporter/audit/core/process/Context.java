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
package spade.reporter.audit.core.process;

import spade.reporter.audit.core.util.statetable.Indexable;
import spade.reporter.audit.core.util.statetable.State;
import spade.reporter.audit.core.util.statetable.Table;

public abstract class Context<T extends Indexable<T>, S extends State<T>>{

	private final Table<T, S> table;

	public Context(final Table<T, S> table){
		if(table == null){
			throw new IllegalArgumentException("Table cannot be NULL");
		}
		this.table = table;
	}

	public Table<T, S> getTable(){
		return table;
	}

}
