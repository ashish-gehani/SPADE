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

package spade.core.analyzer.command.execution;

import java.io.Serializable;

import spade.core.analyzer.command.AbstractCommand;
import spade.core.analyzer.command.Factory;
import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;

public class Execute {

    public final synchronized Serializable execute(
		final Context cmdExecCtx,
		final String query
	) throws ServerFailure, CommandFailure, UnexpectedFailure {

		if (cmdExecCtx == null) {
			throw new CommandFailure("Null command execution context");
		}
		if (query == null) {
			throw new ServerFailure("Null query");
		}

		final Factory cmdFactory = new Factory();

		final AbstractCommand cmd = cmdFactory.createCommand(query);
		cmd.execute(cmdExecCtx);

		final Serializable cmdResult = cmd.getResult();
		return cmdResult;
	}

}
