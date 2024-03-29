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
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * This class is not yet used in the SPADE integrated QuickGrail.
 */
public class SetGraphMetadata extends Instruction<String>{

	public static enum Component{
		kVertex, kEdge, kBoth
	}

	public final GraphMetadata targetMetadata;
	public final Component component;
	public final Graph sourceGraph;
	public final String name;
	public final String value;

	public SetGraphMetadata(GraphMetadata targetMetadata, Component component, Graph sourceGraph, String name,
			String value){
		this.targetMetadata = targetMetadata;
		this.component = component;
		this.sourceGraph = sourceGraph;
		this.name = name;
		this.value = value;
	}

	@Override
	public String getLabel(){
		return "SetGraphMetadata";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetMetadata");
		inline_field_values.add(targetMetadata.name);
		inline_field_names.add("component");
		inline_field_values.add(component.name().substring(1));
		inline_field_names.add("sourceGraph");
		inline_field_values.add(sourceGraph.name);
		inline_field_names.add("name");
		inline_field_values.add(name);
		inline_field_names.add("value");
		inline_field_values.add(value);
	}

	@Override
	public final String execute(final QueryInstructionExecutor executor){
		executor.setGraphMetadata(targetMetadata, component, sourceGraph, name, value);
		return null;
	}
}
