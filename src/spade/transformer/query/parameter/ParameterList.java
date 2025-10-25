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
package spade.transformer.query.parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.parser.ParseExpression;
import spade.transformer.query.parameter.AbstractParameter.ValueMaterializationException;
import spade.utility.NotSetException;
import spade.utility.ValueResolutionException;

/**
 * A container for managing an ordered list of AbstractParameter instances.
 * Provides methods to add parameters and retrieve them in order.
 */
public class ParameterList {

	private final List<AbstractParameter<?,?>> parameters = new ArrayList<>();

	/*
	 * Clear all existing entries and set with the given one.
	 * 
	 */
	public void set(List<AbstractParameter<?, ?>> paramList) {
		if (paramList == null)
			throw new IllegalArgumentException("NULL argument(s)");
		parameters.clear();
		parameters.addAll(paramList);
	}

	/**
	 * Returns an unmodifiable view of the parameter list.
	 *
	 * @return an unmodifiable list of parameters in the order they were added
	 */
	public List<AbstractParameter<?,?>> getParameters() {
		return Collections.unmodifiableList(parameters);
	}

	public AbstractParameter<?,?> getParameter(int i) {
		if (i < 0 || i >= size())
			throw new IllegalArgumentException("Invalid parameter index");
		return this.parameters.get(i);
	}

	/**
	 * Returns the number of parameters in the list.
	 *
	 * @return the size of the parameter list
	 */
	public int size() {
		return parameters.size();
	}

	/**
	 * Checks if the parameter list is empty.
	 *
	 * @return true if the list contains no parameters, false otherwise
	 */
	public boolean isEmpty() {
		return parameters.isEmpty();
	}

	public String getFormattedParameterNames()
	{
		final List<String> names = new ArrayList<>();
		for (int i = 0; i < this.parameters.size(); i++)
		{
			AbstractParameter<?,?> p = this.parameters.get(i);
			if (p == null)
			{
				throw new RuntimeException("NULL transformer query parameter at index: " + i);
			}
			names.add(p.getName());
		}
		return names.toString();
	}

	public void resolveAllValues(
		List<ParseExpression> expressions, QuickGrailQueryResolver resolver
	) throws IllegalArgumentException, NotSetException, ValueResolutionException
	{
		if (expressions == null || resolver == null)
			throw new IllegalArgumentException("NULL argument(s)");
		if (expressions.size() != size())
			throw new IllegalArgumentException("Mismatch in expressions (" + expressions.size() + ") and parameters (" + size() + ") count.");
		for (int i = 0; i < size(); i++)
		{
			final ParseExpression pex = expressions.get(i);
			final AbstractParameter<?, ?> ap;
			try{
				ap = getParameter(i);
			} catch (IllegalArgumentException e)
			{
				throw new IllegalArgumentException("Failed at parameter index (" + i + ")", e);
			}
			if (ap.isResolvedValueSet())
				continue;
			try{
				ap.resolveValue(pex, resolver);
			} catch (ValueResolutionException e)
			{
				throw new ValueResolutionException("Failed at parameter index (" + i + ")", e);
			}
		}
	}

	public void materializeValue(QueryInstructionExecutor executor) throws IllegalArgumentException, ValueMaterializationException, NotSetException
	{
		if (executor == null)
			throw new IllegalArgumentException("NULL argument(s)");
		for (int i = 0; i < size(); i++)
		{
			final AbstractParameter<?, ?> ap;
			try{
				ap = getParameter(i);
			} catch (IllegalArgumentException e)
			{
				throw new IllegalArgumentException("Failed at parameter index (" + i + ")", e);
			}
			if (ap.isMaterializedValueSet())
				continue;
			try{
				ap.materializeValue(executor);
			} catch (ValueMaterializationException e)
			{
				throw new ValueMaterializationException("Failed at parameter index (" + i + ")", e);
			} catch (NotSetException e)
			{
				throw new NotSetException("Failed at parameter index (" + i + ")", e);
			}
		}
	}
}
