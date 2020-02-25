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
 * Similar to GetPath but treats the graph edges as indirected.
 */
public class GetLink extends Instruction{
	// Output graph.
	public final Graph targetGraph;
	// Input graph.
	public final Graph subjectGraph;
	// Set of source vertices.
	public final Graph srcGraph;
	// Set of destination vertices.
	public final Graph dstGraph;
	// Max path length.
	public final int maxDepth;

	public GetLink(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.srcGraph = srcGraph;
		this.dstGraph = dstGraph;
		this.maxDepth = maxDepth;
	}

	@Override
	public String getLabel(){
		return "GetLink";
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
		inline_field_names.add("srcGraph");
		inline_field_values.add(srcGraph.name);
		inline_field_names.add("dstGraph");
		inline_field_values.add(dstGraph.name);
		inline_field_names.add("maxDepth");
		inline_field_values.add(String.valueOf(maxDepth));
	}
}
