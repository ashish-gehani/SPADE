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

import java.util.ArrayList;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class GetMatch extends Instruction{

	public final Graph targetGraph, graph1, graph2;
	private final ArrayList<String> annotationKeys = new ArrayList<String>();
	
	public GetMatch(final Graph targetGraph, final Graph graph1, final Graph graph2, final ArrayList<String> annotationKeys){
		this.targetGraph = targetGraph;
		this.graph1 = graph1;
		this.graph2 = graph2;
		if(annotationKeys != null){
			this.annotationKeys.addAll(annotationKeys);
		}
	}
	
	public final ArrayList<String> getAnnotationKeys(){
		return new ArrayList<String>(annotationKeys);
	}
	
	@Override
	public String getLabel(){
		return "GetMatch";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("graph1");
		inline_field_values.add(graph1.name);
		inline_field_names.add("graph2");
		inline_field_values.add(graph2.name);
		inline_field_names.add("annotationKeys");
		inline_field_values.add(String.join(",", annotationKeys));
	}
	
}
