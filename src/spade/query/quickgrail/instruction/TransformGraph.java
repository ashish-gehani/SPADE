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

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class TransformGraph extends Instruction{

	public final String transformerName;
	public final String transformerInitializeArgument;
	public final Graph outputGraph, subjectGraph;
	private final java.util.List<Object> arguments = new ArrayList<Object>();
	
	public TransformGraph(final String transformerName, final String transformerInitializeArgument, 
			final Graph outputGraph, final Graph subjectGraph){
		this.transformerName = transformerName;
		this.transformerInitializeArgument = transformerInitializeArgument;
		this.outputGraph = outputGraph;
		this.subjectGraph = subjectGraph;
	}
	
	public void addArgument(final Object argument){
		if(argument == null){
			throw new RuntimeException("NULL argument not allowed for transformers");
		}
		arguments.add(argument);
	}
	
	public int getArgumentsSize(){
		return arguments.size();
	}

	public Object getArgument(int i){
		return arguments.get(i);
	}

	@Override
	public String getLabel(){
		return "TransformGraph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("transformerName");
		inline_field_values.add(transformerName);
		inline_field_names.add("initializeArgument");
		inline_field_values.add(transformerInitializeArgument);
		inline_field_names.add("outputGraph");
		inline_field_values.add(outputGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		int i = 0;
		for(final Object argument : arguments){
			inline_field_names.add("argument<" + argument.getClass().getSimpleName() + ">[" + i + "]");
			inline_field_values.add(argument.toString());
			i++;
		}
	}
}
