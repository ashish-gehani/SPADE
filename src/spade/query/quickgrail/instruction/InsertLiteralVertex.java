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

import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Insert a list of vertices into a graph by ID.
 */
public class InsertLiteralVertex extends Instruction<String>{
	// The target graph to insert the vertices.
	public final Graph targetGraph;
	// Edge IDs to be inserted.
	private final ArrayList<String> vertices;

	public InsertLiteralVertex(Graph targetGraph, ArrayList<String> vertices){
		this.targetGraph = targetGraph;
		this.vertices = vertices;
	}

	public final ArrayList<String> getVertices(){
		return vertices == null ? null : new ArrayList<String>(vertices);
	}

	@Override
	public String getLabel(){
		return "InsertLiteralVertex";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("vertices");
		inline_field_values.add("{" + String.join(",", vertices == null ? new ArrayList<String>() : vertices) + "}");
	}

	@Override
	public final String execute(final QueryInstructionExecutor executor){
		executor.insertLiteralVertex(targetGraph, getVertices());
		return null;
	}
}
