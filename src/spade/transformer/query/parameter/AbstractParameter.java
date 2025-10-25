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

import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.parser.ParseExpression;
import spade.utility.NotSetException;
import spade.utility.ValueResolutionException;

/**
 * Abstract base class for query parameters in transformers.
 * Defines the contract for parameter name, type, and value resolution.
 * 
 * @param <Q> the type of the resolved value
 * @param <T> the type of the materialized value
 */
public abstract class AbstractParameter<Q, T> {

	private final String name;

	private final CheckedValue<Q> resolvedVal = new CheckedValue<>();

	private final CheckedValue<T> materializedVal = new CheckedValue<>();

	public AbstractParameter(final String name)
	{
		if (name == null)
			throw new IllegalArgumentException("NULL parameter name");
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	/**
	 * Set the resolved value of this parameter directly.
	 *
	 * @param value the value to set
	 */
	public void setResolvedValue(Q value) {
		this.resolvedVal.setValue(value);
	}

	/**
	 * Set the materialized value of this parameter directly.
	 *
	 * @param value the value to set
	 */
	public void setMaterializedValue(T value) {
		this.materializedVal.setValue(value);
	}

	/**
	 * Gets the resolved value.
	 *
	 * @return the value
	 * @throws NotSetException if the value has not been set
	 */
	public Q getResolvedValue() throws NotSetException {
		return this.resolvedVal.getValue();
	}

	/**
	 * Gets the materialized value.
	 *
	 * @return the value
	 * @throws NotSetException if the value has not been set
	 */
	public T getMaterializedValue() throws NotSetException {
		return this.materializedVal.getValue();
	}

	/**
	 * Checks if the resolved value has been set.
	 *
	 * @return true if the value has been set, false otherwise
	 */
	public boolean isResolvedValueSet() {
		return resolvedVal.isSet();
	}

	/**
	 * Checks if the materialized value has been set.
	 *
	 * @return true if the value has been set, false otherwise
	 */
	public boolean isMaterializedValueSet() {
		return materializedVal.isSet();
	}

	/**
	 * Copies values present in the source parameter to this parameter.
	 * Only copies values that are set in the source parameter.
	 *
	 * @param srcP the source parameter to copy values from
	 */
	public void copyValuesPresentIn(final AbstractParameter<Q, T> srcP) {
		if (srcP == null) {
			return;
		}
		if (srcP.isResolvedValueSet()) {
			try {
				setResolvedValue(srcP.getResolvedValue());
			} catch (NotSetException e) {
				// Should not happen since we checked isResolvedValueSet()
			}
		}
		if (srcP.isMaterializedValueSet()) {
			try {
				setMaterializedValue(srcP.getMaterializedValue());
			} catch (NotSetException e) {
				// Should not happen since we checked isMaterializedValueSet()
			}
		}
	}

	/**
	 * Validates and resolves the value of this parameter from a ParseExpression.
	 * Subclasses must implement validation logic and call setValue() when successful.
	 *
	 * @param expression the ParseExpression to validate and extract value from
	 * @param resolver the QuickGrailQueryResolver instance for context
	 * @throws ValueResolutionException if the expression cannot be resolved to a valid value
	 */
	public abstract void resolveValue(ParseExpression expression, QuickGrailQueryResolver resolver) throws ValueResolutionException;

	/**
	 * Materialize the value of this parameter from a queryParam.
	 * Subclasses must implement validation logic and call setValue() when successful.
	 *
	 * @param queryParam the query param to validate and extract value of of
	 * @param executor the QueryInstructionExecutor instance
	 * @throws ValueMaterializationException if the param cannot be materialized to a valid value
	 */
	public abstract void materializeValue(QueryInstructionExecutor executor) throws ValueMaterializationException, NotSetException;

	/**
	 * Exception thrown when a parameter value cannot be materialized from a query param.
	 */
	public static class ValueMaterializationException extends Exception {
		private static final long serialVersionUID = 1L;

		public ValueMaterializationException(String message) {
			super(message);
		}

		public ValueMaterializationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
