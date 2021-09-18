/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Get a graph that includes all the paths from a set of source vertices to a
 * set of destination vertices through intermediate vertices.
 */
public class GetPath extends Instruction<String>{
	// Output graph.
	public final Graph targetGraph;
	// Input graph.
	public final Graph subjectGraph;
	// Set of source vertices.
	public final Graph srcGraph;

	private final ArrayList<SimpleEntry<Graph, Integer>> intermediateSteps = new ArrayList<SimpleEntry<Graph, Integer>>();

	public GetPath(Graph targetGraph, Graph subjectGraph, Graph srcGraph){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.srcGraph = srcGraph;
	}

	public final void addIntermediateStep(final Graph graph, final int maxDepth){
		if(graph == null){
			throw new RuntimeException("NULL Graph in intermediate step");
		}
		intermediateSteps.add(new SimpleEntry<Graph, Integer>(graph, maxDepth));
	}

	public final int getIntermediateStepsCount(){
		return intermediateSteps.size();
	}

	public final SimpleEntry<Graph, Integer> getIntermediateStep(int i){
		if(i < 0 || i >= intermediateSteps.size()){
			throw new RuntimeException("Index out of range of intermediate steps: " + i);
		}
		return new SimpleEntry<Graph, Integer>(intermediateSteps.get(i));
	}

	@Override
	public final String getLabel(){
		return "GetPath";
	}

	@Override
	protected final void getFieldStringItems(ArrayList<String> inline_field_names,
			ArrayList<String> inline_field_values, ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		inline_field_names.add("srcGraph");
		inline_field_values.add(srcGraph.name);
		int stepNumber = 0;
		for(final SimpleEntry<Graph, Integer> intermediateStep : intermediateSteps){
			stepNumber++;
			inline_field_names.add("intermediateGraph." + stepNumber);
			inline_field_values.add(intermediateStep.getKey().name);
			inline_field_names.add("maxDepth." + stepNumber);
			inline_field_values.add(String.valueOf(intermediateStep.getValue()));
		}
	}

	@Override
	public final String execute(final QueryInstructionExecutor executor){
		executor.getPath(targetGraph, subjectGraph, srcGraph, intermediateSteps);
		return null;
	}
}