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
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.parser.ParseExpression;
import spade.query.quickgrail.parser.ParseVariable;
import spade.query.quickgrail.types.TypeID;
import spade.utility.NotSetException;
import spade.utility.ValueResolutionException;

/**
 * A query parameter that holds a Graph value.
 */
public class SourceGraph extends AbstractParameter<spade.query.quickgrail.entities.Graph, spade.core.Graph> {

	public SourceGraph()
	{
		super("Source Graph");
	}

	@Override
	public void resolveValue(ParseExpression expression, QuickGrailQueryResolver resolver) throws ValueResolutionException {
		if (expression == null) {
			throw new ValueResolutionException("Expression cannot be null");
		}
		if (expression.getExpressionType() != ParseExpression.ExpressionType.kVariable) {
			throw new ValueResolutionException("Parameter requires a variable expression, got: " + expression.getExpressionType());
		}
		ParseVariable variable = (ParseVariable) expression;
		if (variable.getType().getTypeID() != TypeID.kGraph) {
			throw new ValueResolutionException("Parameter requires a graph type, got: " + variable.getType().getTypeID());
		}

		final Graph value = resolver.resolveConstGraphVariable(variable);
		if (value == null) {
			throw new ValueResolutionException("Failed to extract integer value");
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

		final spade.query.quickgrail.entities.Graph graphVar = this.getResolvedValue();

		final boolean forceExport = true;
		final spade.core.Graph graph = executor.exportGraph(graphVar, forceExport);

		if (graph == null)
			throw new ValueMaterializationException("Resolved value is null");

		setMaterializedValue(graph);
	}
}
