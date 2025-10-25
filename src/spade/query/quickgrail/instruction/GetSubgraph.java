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
package spade.query.quickgrail.instruction;

import java.util.ArrayList;

import spade.query.execution.Context;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Let $S be the subject graph and $T be the skeleton graph. The operation
 * $S.getSubgraph($T) is to find all the vertices and edges that are spanned by
 * the skeleton graph.
 */
public class GetSubgraph extends Instruction<String>{
	public final Graph targetGraph;
	public final Graph subjectGraph;
	public final Graph skeletonGraph;

	public GetSubgraph(Graph targetGraph, Graph subjectGraph, Graph skeletonGraph){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.skeletonGraph = skeletonGraph;
	}

	@Override
	public String getLabel(){
		return "GetSubgraph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		inline_field_names.add("skeletonGraph");
		inline_field_values.add(skeletonGraph.name);
	}

	@Override
	public final String exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		executor.getSubgraph(targetGraph, subjectGraph, skeletonGraph);
		return null;
	}
}
