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
import java.util.logging.Logger;

import spade.query.execution.Context;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Get the lineage of a set of vertices in a graph.
 */
public class GetLineage extends Instruction<String>{

	public static final Logger logger = Logger.getLogger(GetLineage.class.getName());
	
	public static enum Direction{
		kAncestor, kDescendant, kBoth
	}

	// Output graph.
	public final Graph targetGraph;
	// Input graph.
	public final Graph subjectGraph;
	// Set of starting vertices.
	public final Graph startGraph;
	// Max depth.
	public final int depth;
	// Direction (ancestors / descendants, or both).
	public final Direction direction;

	public GetLineage(Graph targetGraph, Graph subjectGraph, Graph startGraph, int depth, Direction direction){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.startGraph = startGraph;
		this.depth = depth;
		this.direction = direction;
	}

	@Override
	public String getLabel(){
		return "GetLineage";
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
		inline_field_names.add("startGraph");
		inline_field_values.add(startGraph.name);
		inline_field_names.add("depth");
		inline_field_values.add(String.valueOf(depth));
		inline_field_names.add("direction");
		inline_field_values.add(direction.name().substring(1));
	}

	@Override
	public String exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		if(executor.getGraphCount(startGraph).getVertices() <= 0){
			return null;
		}

		if(executor.getGraphCount(subjectGraph).getEdges() == 0){
			// Nothing to start from since no vertices OR no where to go in the subject since no edges
			return null;
		}

		if(Direction.kAncestor.equals(direction) || Direction.kDescendant.equals(direction)){
			executor.getLineage(targetGraph, subjectGraph, startGraph, depth, direction);
		}else if(Direction.kBoth.equals(direction)){
			executor.getLineage(targetGraph, subjectGraph, startGraph, depth, Direction.kAncestor);
			executor.getLineage(targetGraph, subjectGraph, startGraph, depth, Direction.kDescendant);
		}else{
			throw new RuntimeException(
					"Unexpected direction: '" + direction + "'. Expected: Ancestor, Descendant or Both");
		}

		return null;
	}

}
