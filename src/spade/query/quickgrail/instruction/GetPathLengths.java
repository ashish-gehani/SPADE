/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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

public class GetPathLengths extends Instruction<java.util.ArrayList<Integer>>{

	public final Graph startGraph, subjectGraph, toGraph;
	public final int maxDepth;

	public GetPathLengths(final Graph startGraph, final Graph subjectGraph, final Graph toGraph, final int maxDepth){
		this.startGraph = startGraph;
		this.subjectGraph = subjectGraph;
		this.toGraph = toGraph;
		this.maxDepth = maxDepth;
	}

	@Override
	public final java.util.ArrayList<Integer> exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		return executor.getPathLengths(subjectGraph, startGraph, toGraph, maxDepth);
	}

	@Override
	public String getLabel(){
		return "GetPathLengths";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("startGraph");
		inline_field_values.add(startGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		inline_field_names.add("toGraph");
		inline_field_values.add(toGraph.name);
		inline_field_names.add("maxDepth");
		inline_field_values.add(String.valueOf(maxDepth));
	}

}
