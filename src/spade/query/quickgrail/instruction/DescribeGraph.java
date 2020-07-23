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

public class DescribeGraph extends Instruction{
	
	public static enum ElementType{ VERTEX, EDGE }
	public static enum DescriptionType{ COUNT, MINMAX }
	
	public final Graph graph;
	public final ElementType elementType;
	public final String annotationName;
	public final DescriptionType descriptionType;
	
	public final Integer limit;
	
	public final boolean all;
	
	public DescribeGraph(final Graph graph, final ElementType elementType,
			final String annotationName, final DescriptionType descriptionType, final Integer limit){
		this.graph = graph;
		this.elementType = elementType;
		this.annotationName = annotationName;
		this.descriptionType = descriptionType;
		this.limit = limit;
		this.all = false;
	}
	
	public DescribeGraph(final Graph graph, final ElementType elementType, final Integer limit){
		this.graph = graph;
		this.elementType = elementType;
		this.annotationName = null;
		this.descriptionType = null;
		this.limit = limit;
		this.all = true;
	}
	
	@Override
	public String getLabel(){
		return "DescribeGraph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("graph");
		inline_field_values.add(graph.name);
		inline_field_names.add("elementType");
		inline_field_values.add(elementType.toString());
		if(annotationName != null){
			inline_field_names.add("annotationName");
			inline_field_values.add(annotationName);
		}
		if(descriptionType != null){
			inline_field_names.add("descriptionType");
			inline_field_values.add(descriptionType.toString());
		}
		if(limit != null){
			inline_field_names.add("limit");
			inline_field_values.add(String.valueOf(limit));
		}
	}
}
