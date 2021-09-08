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
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Show statistics of a graph.
 */
public abstract class GetGraphStatistic extends Instruction{

	public final Graph graph;
	private int precisionScale;

	public GetGraphStatistic(final Graph graph){
		this.graph = graph;
	}

	public int getPrecisionScale(){
		return precisionScale;
	}

	public void setPrecisionScale(final int precisionScale){
		this.precisionScale = precisionScale;
	}

	public static class Count extends GetGraphStatistic{
		public Count(final Graph graph){
			super(graph);
		}

		@Override
		public String getLabel(){
			return "GetGraphStatistic.Count";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
		}
	}

	public static class Histogram extends GetGraphStatistic{
		public final ElementType elementType;
		public final String annotationKey;

		public Histogram(final Graph graph, final ElementType elementType, final String annotationKey){
			super(graph);
			this.elementType = elementType;
			this.annotationKey = annotationKey;
		}

		@Override
		public String getLabel(){
			return "GetGraphStatistic.Histogram";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
			inline_field_names.add("elementType");
			inline_field_values.add(elementType.toString());
			inline_field_names.add("annotationKey");
			inline_field_values.add(annotationKey);
		}
	}

	public static class Mean extends GetGraphStatistic{
		public final ElementType elementType;
		public final String annotationKey;

		public Mean(final Graph graph, final ElementType elementType, final String annotationKey){
			super(graph);
			this.elementType = elementType;
			this.annotationKey = annotationKey;
		}

		@Override
		public String getLabel(){
			return "GetGraphStatistic.Mean";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
			inline_field_names.add("elementType");
			inline_field_values.add(elementType.toString());
			inline_field_names.add("annotationKey");
			inline_field_values.add(annotationKey);
		}
	}

	public static class StandardDeviation extends GetGraphStatistic{
		public final ElementType elementType;
		public final String annotationKey;

		public StandardDeviation(final Graph graph, final ElementType elementType, final String annotationKey){
			super(graph);
			this.elementType = elementType;
			this.annotationKey = annotationKey;
		}

		@Override
		public String getLabel(){
			return "GetGraphStatistic.StandardDeviation";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
			inline_field_names.add("elementType");
			inline_field_values.add(elementType.toString());
			inline_field_names.add("annotationKey");
			inline_field_values.add(annotationKey);
		}
	}

	public static class Distribution extends GetGraphStatistic{
		public final ElementType elementType;
		public final String annotationKey;
		public final Integer binCount;

		public Distribution(final Graph graph, final ElementType elementType, final String annotationKey,
				final Integer binCount){
			super(graph);
			this.elementType = elementType;
			this.annotationKey = annotationKey;
			this.binCount = binCount;
		}

		@Override
		public String getLabel(){
			return "GetGraphStatistic.Distribution";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
			inline_field_names.add("elementType");
			inline_field_values.add(elementType.toString());
			inline_field_names.add("annotationKey");
			inline_field_values.add(annotationKey);
			inline_field_names.add("binCount");
			inline_field_values.add(Integer.toString(binCount));
		}
	}
}
