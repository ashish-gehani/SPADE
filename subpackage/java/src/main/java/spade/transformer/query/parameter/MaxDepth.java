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
import spade.query.quickgrail.parser.ParseLiteral;
import spade.query.quickgrail.types.TypeID;
import spade.utility.NotSetException;
import spade.utility.ValueResolutionException;

/**
 * A query parameter that holds a positive integer value for max depth.
 * Only accepts integer literals greater than 0.
 */
public class MaxDepth extends AbstractParameter<Integer, Integer> {

	public MaxDepth()
	{
		super("Max depth");
	}

	@Override
	public void resolveValue(ParseExpression expression, QuickGrailQueryResolver resolver) throws ValueResolutionException {
		if (expression == null) {
			throw new ValueResolutionException("Expression cannot be null");
		}
		if (expression.getExpressionType() != ParseExpression.ExpressionType.kLiteral) {
			throw new ValueResolutionException("Parameter requires a literal expression, got: " + expression.getExpressionType());
		}
		ParseLiteral literal = (ParseLiteral) expression;
		if (literal.getLiteralValue().getType().getTypeID() != TypeID.kInteger) {
			throw new ValueResolutionException("Parameter requires an integer literal, got: " + literal.getLiteralValue().getType().getTypeID());
		}

		Integer value = (Integer) literal.getLiteralValue().getValue();
		if (value == null) {
			throw new ValueResolutionException("Failed to extract integer value");
		}

		if (value <= 0) {
			throw new ValueResolutionException("Parameter requires a positive integer (> 0), got: " + value);
		}

		setResolvedValue(value);
	}

	@Override
	public void materializeValue(QueryInstructionExecutor executor) throws ValueMaterializationException, NotSetException {
		if (!this.isResolvedValueSet()) {
			throw new ValueMaterializationException("Resolved value is not set");
		}
		if (executor == null) {
			throw new ValueMaterializationException("Query executor cannot be null");
		}

		final Integer maxDepth = this.getResolvedValue();

		if (maxDepth == null)
			throw new ValueMaterializationException("Resolved value is null");

		setMaterializedValue(maxDepth);
	}
}
