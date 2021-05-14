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
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Show statistics of a graph.
 */
public class StatGraph extends Instruction{
	public final Graph targetGraph;
	public final Aggregate aggregate;

	public StatGraph(final Graph targetGraph){
		this.targetGraph = targetGraph;
		this.aggregate = null;
	}

	public StatGraph(final Graph targetGraph, final ElementType elementType, 
			final String annotationName, final AggregateType aggregateType, final java.util.List<String> extras){
		this.targetGraph = targetGraph;
		this.aggregate = new Aggregate(elementType, annotationName, aggregateType, extras);
	}

	@Override
	public String getLabel(){
		return "StatGraph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		if(aggregate != null){
			inline_field_names.add("elementType");
			inline_field_values.add(aggregate.elementType.toString());
			inline_field_names.add("annotationName");
			inline_field_values.add(aggregate.annotationName);
			inline_field_names.add("aggregateType");
			inline_field_values.add(aggregate.aggregateType.toString());
			inline_field_names.add("extras");
			inline_field_values.add(aggregate.extras.toString());
		}
	}

	public static enum AggregateType{
		HISTOGRAM, MEAN, STD
	}

	public static class Aggregate{
		public final ElementType elementType;
		public final String annotationName;
		public final AggregateType aggregateType;
		private final java.util.List<String> extras = new ArrayList<String>();

		private Aggregate(final ElementType elementType, 
				final String annotationName, final AggregateType aggregateType, final java.util.List<String> extras){
			this.elementType = elementType;
			this.annotationName = annotationName;
			this.aggregateType = aggregateType;
			this.extras.addAll(extras);
		}

		public int getExtraSize(){
			return extras.size();
		}

		public String getExtra(int i){
			return extras.get(i);
		}
		
		public java.util.List<String> getExtras(){
			return new ArrayList<String>(extras);
		}
	}
}
