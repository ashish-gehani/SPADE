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

import spade.query.execution.Context;
import spade.query.quickgrail.core.GraphRemoteCount;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Show statistics of a graph.
 */
public abstract class GetGraphStatistic<R extends GraphStatistic> extends Instruction<R>{

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

	public void setResultPrecisionScale(final GraphStatistic result){
		if (result == null)
			return;
		result.setPrecisionScale(this.getPrecisionScale());
	}

	public abstract R executeGraphStatisticInstruction(final Context ctx);

	@Override
	public final R exec(final Context ctx) {
		final R result = executeGraphStatisticInstruction(ctx);
		setResultPrecisionScale(result);
		return result;

	}

	public static class Count extends GetGraphStatistic<GraphStatistic.Count>{
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

		@Override
		public final GraphStatistic.Count executeGraphStatisticInstruction(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			GraphStatistic.Count result = executor.getGraphCount(graph);
			long remoteVertexCount = 0, remoteEdgeCount = 0;
			final GraphRemoteCount remoteCounts = new RemoteVariableOperation.List(graph).exec(ctx);
			for(final GraphStatistic.Count count : remoteCounts.get().values()){
				if(count.getVertices() > 0){
					remoteVertexCount += count.getVertices();
				}
				if(count.getEdges() > 0){
					remoteEdgeCount += count.getEdges();
				}
			}
			if(result == null){
				result = new GraphStatistic.Count(remoteVertexCount, remoteEdgeCount);
			}else{
				result.addVertices(remoteVertexCount);
				result.addEdges(remoteEdgeCount);
			}
			return result;
		}
	}

	public static class Histogram extends GetGraphStatistic<GraphStatistic.Histogram>{
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

		@Override
		public final GraphStatistic.Histogram executeGraphStatisticInstruction(final Context ctx) {
			final GraphStatistic.Histogram result = ctx.getExecutor().getGraphHistogram(graph, elementType, annotationKey);
			return result;
		}
	}

	public static class Mean extends GetGraphStatistic<GraphStatistic.Mean>{
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

		@Override
		public final GraphStatistic.Mean executeGraphStatisticInstruction(final Context ctx) {
			final GraphStatistic.Mean result = ctx.getExecutor().getGraphMean(graph, elementType, annotationKey);
			return result;
		}
	}

	public static class StandardDeviation extends GetGraphStatistic<GraphStatistic.StandardDeviation>{
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

		@Override
		public final GraphStatistic.StandardDeviation executeGraphStatisticInstruction(final Context ctx) {
			final GraphStatistic.StandardDeviation result = ctx.getExecutor().getGraphStandardDeviation(graph, elementType, annotationKey);
			return result;
		}
	}

	public static class Distribution extends GetGraphStatistic<GraphStatistic.Distribution>{
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

		@Override
		public final GraphStatistic.Distribution executeGraphStatisticInstruction(final Context ctx) {
			final GraphStatistic.Distribution result = ctx.getExecutor().getGraphDistribution(graph, elementType, annotationKey, binCount);
			return result;
		}
	}
}
