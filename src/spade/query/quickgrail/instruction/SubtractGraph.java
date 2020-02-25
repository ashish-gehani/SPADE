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

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Subtract one graph from the other.
 */
public class SubtractGraph extends Instruction{
	// Output graph.
	public final Graph outputGraph;
	// Minuend graph.
	public final Graph minuendGraph;
	// Subtrahend graph.
	public final Graph subtrahendGraph;
	// Graph components that should be involved in the operation (vertices / edges,
	// or both).
	public final Graph.Component component;

	public SubtractGraph(Graph outputGraph, Graph minuendGraph, Graph subtrahendGraph, Graph.Component component){
		this.outputGraph = outputGraph;
		this.minuendGraph = minuendGraph;
		this.subtrahendGraph = subtrahendGraph;
		this.component = component;
	}

	@Override
	public String getLabel(){
		return "SubtractGraph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("outputGraph");
		inline_field_values.add(outputGraph.name);
		inline_field_names.add("minuendGraph");
		inline_field_values.add(minuendGraph.name);
		inline_field_names.add("subtrahendGraph");
		inline_field_values.add(subtrahendGraph.name);
		if(this.component != null){
			inline_field_names.add("component");
			inline_field_values.add(component.name());
		}
	}
}
