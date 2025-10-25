/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.query.quickgrail.core;

import java.io.Serializable;

import spade.query.execution.Context;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Interface for a QuickGrail primitive instruction.
 */
public abstract class Instruction<R extends Serializable> extends TreeStringSerializable{

	private Serializable result;

	public final Serializable getResult(){
		return result;
	}

	public final void setResult(final Serializable result){
		this.result = result;
	}

	// Execution
	public final void execute(final Context ctx)
	{
		final R result = this.exec(ctx);
		setResult(result);
	}

	public abstract R exec(final Context ctx);

}
