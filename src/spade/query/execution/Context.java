/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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
package spade.query.execution;

import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.transformer.query.ContextAllParams;


public class Context {

	private final ContextAllParams transformerContext = new ContextAllParams();;

	private final QueryInstructionExecutor executor;

	public Context(final QueryInstructionExecutor executor)
	{
		if (executor == null)
			throw new IllegalArgumentException("Query instruction executor in query executor context cannot be set to null");
		if (executor.getQueryEnvironment() == null)
			throw new IllegalArgumentException("Query environment in query instruction executor cannot be null");

		this.executor = executor;
	}

	/**
	 * Gets the query instruction executor reference.
	 *
	 * @return the query instruction executor
	 */
	public QueryInstructionExecutor getExecutor() {
		return executor;
	}

	public ContextAllParams getTransformerContext()
	{
		return transformerContext;
	}

	public void destroy()
	{
		// todo do instruction executor garbage collection... maybe?
	}
}
