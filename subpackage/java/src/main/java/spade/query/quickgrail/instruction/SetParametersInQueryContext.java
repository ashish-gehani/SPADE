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
package spade.query.quickgrail.instruction;

import java.util.ArrayList;
import java.util.logging.Logger;

import spade.query.execution.Context;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Set context based on the lineage of a set of vertices in a graph.
 */
public class SetParametersInQueryContext extends Instruction<String>{

	public static final Logger logger = Logger.getLogger(SetParametersInQueryContext.class.getName());

	public final Graph sourceGraph;
	public final int maxDepth;
	public final GetLineage.Direction direction;

	public SetParametersInQueryContext(Graph sourceGraph, int maxDepth, GetLineage.Direction direction)
    {
		this.sourceGraph = sourceGraph;
		this.maxDepth = maxDepth;
		this.direction = direction;
	}

	@Override
	public String getLabel(){
		return "SetParametersInQueryContext";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("sourceGraph");
		inline_field_values.add(sourceGraph.name);
		inline_field_names.add("maxDepth");
		inline_field_values.add(String.valueOf(maxDepth));
		inline_field_names.add("direction");
		inline_field_values.add(direction.name().substring(1));
	}

	@Override
	public String exec(final Context ctx) {
        ctx.getTransformerContext().sourceGraph.setResolvedValue(sourceGraph);
		ctx.getTransformerContext().maxDepth.setResolvedValue(maxDepth);
		ctx.getTransformerContext().lineageDirection.setResolvedValue(direction);

		return null;
	}

}
