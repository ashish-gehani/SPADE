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
package spade.transformer.query;

import java.util.List;

import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.parser.ParseExpression;
import spade.transformer.query.parameter.AbstractParameter;
import spade.transformer.query.parameter.AbstractParameter.ValueMaterializationException;
import spade.transformer.query.parameter.ParameterList;
import spade.utility.NotSetException;
import spade.utility.ValueResolutionException;

/**
 * Context class that manages a collection of parameters for transformer queries.
 * Provides functionality to add parameters and perform bulk resolution and materialization operations.
 */
public class Context {

	private final ParameterList parameterList = new ParameterList();

	/**
	 * Constructs a Context with the specified parameters.
	 *
	 * @param parameters varargs of AbstractParameter instances to add to this context
	 */
	public void set(List<AbstractParameter<?, ?>> paramList) {
		this.parameterList.set(paramList);
	}

	/**
	 * Resolves all parameters in this context using the provided expressions and resolver.
	 *
	 * @param expressions list of ParseExpression instances to resolve parameters from
	 * @param resolver the QuickGrailQueryResolver instance for context
	 * @throws IllegalArgumentException if arguments are invalid or expression count doesn't match parameter count
	 * @throws NotSetException if a required value is not set
	 * @throws ValueResolutionException if a parameter value cannot be resolved
	 */
	public void resolve(List<ParseExpression> expressions, QuickGrailQueryResolver resolver)
			throws IllegalArgumentException, NotSetException, ValueResolutionException {
		parameterList.resolveAllValues(expressions, resolver);
	}

	/**
	 * Materializes all parameters in this context using the provided executor.
	 *
	 * @param executor the QueryInstructionExecutor instance
	 * @throws IllegalArgumentException if executor is null
	 * @throws ValueMaterializationException if a parameter value cannot be materialized
	 * @throws NotSetException if a required value is not set
	 */
	public void materialize(QueryInstructionExecutor executor)
			throws IllegalArgumentException, ValueMaterializationException, NotSetException {
		parameterList.materializeValue(executor);
	}

	private AbstractParameter<?,?> getTheSameParameterTypeAs(final AbstractParameter<?,?> p)
	{
		for (int i = 0; i < this.parameterList.size(); i++)
		{
			final AbstractParameter<?,?> x = this.parameterList.getParameter(i);
			if (x.getClass().equals(p.getClass()))
				return x;
		}
		return null;
	}

	public void copyValuesPresentIn(final Context srcCtx)
	{
		for (int i = 0; i < srcCtx.parameterList.size(); i++)
		{
			final AbstractParameter<?,?> srcP = srcCtx.parameterList.getParameter(i);
			final AbstractParameter<?,?> dstP = getTheSameParameterTypeAs(srcP);
			if (dstP == null)
				continue;
			// Safe cast: getTheSameParameterTypeAs ensures both parameters are of the same class type
			copyParameterValues(dstP, srcP);
		}
	}

	@SuppressWarnings("unchecked")
	private static <Q, T> void copyParameterValues(AbstractParameter<Q, T> dst, AbstractParameter<?, ?> src)
	{
		// This cast is safe because we verify the class types match in getTheSameParameterTypeAs
		dst.copyValuesPresentIn((AbstractParameter<Q, T>) src);
	}
}
