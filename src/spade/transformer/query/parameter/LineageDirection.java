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
import spade.query.quickgrail.core.QueryResolverHelper;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.parser.ParseExpression;
import spade.utility.NotSetException;
import spade.utility.ValueResolutionException;

/**
 * A query parameter that holds a GetLineage.Direction value.
 */
public class LineageDirection extends AbstractParameter<GetLineage.Direction, GetLineage.Direction> {

	public LineageDirection()
	{
		super("Lineage Direction");
	}

	@Override
	public void resolveValue(ParseExpression expression, QuickGrailQueryResolver resolver) throws ValueResolutionException {
		// A Direction parameter accepts string literals with valid direction values
		if (expression == null) {
			throw new ValueResolutionException("Expression cannot be null for LINEAGE_DIRECTION parameter");
		}

		final GetLineage.Direction direction;
		final String directionLiteral;

		try {
			directionLiteral = QueryResolverHelper.resolveString(expression).toLowerCase();
		} catch (RuntimeException e) {
			// QueryResolverHelper.resolveString throws RuntimeException for non-string literals
			throw new ValueResolutionException("LINEAGE_DIRECTION parameter requires a string literal", e);
		}

		// Check if the direction literal is one of the valid values
		if ("ancestors".startsWith(directionLiteral)) {
			direction = GetLineage.Direction.kAncestor;
		} else if ("descendants".startsWith(directionLiteral)) {
			direction = GetLineage.Direction.kDescendant;
		} else if ("both".startsWith(directionLiteral)) {
			direction = GetLineage.Direction.kBoth;
		} else {
			throw new ValueResolutionException("LINEAGE_DIRECTION parameter requires a valid direction value (ancestors, descendants, or both), got: " + directionLiteral);
		}

		setResolvedValue(direction);
	}

	@Override
	public void materializeValue(QueryInstructionExecutor executor) throws ValueMaterializationException, NotSetException {
		if (!this.isResolvedValueSet()) {
			throw new ValueMaterializationException("Resolved value is not set");
		}
		if (executor == null) {
			throw new ValueMaterializationException("Query executor cannot be null");
		}

		final GetLineage.Direction direction = getResolvedValue();

		if (direction == null)
			throw new ValueMaterializationException("Resolved value is null");

		setMaterializedValue(direction);
	}
}
