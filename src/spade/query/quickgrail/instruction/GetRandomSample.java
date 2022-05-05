/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2022 SRI International

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
import java.util.PrimitiveIterator.OfLong;
import java.util.Random;
import java.util.stream.LongStream;

import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class GetRandomSample extends Instruction<String>{
	public final Graph targetGraph, sourceGraph;
	public final long sampleSize;
	public final Graph.Component component;

	public GetRandomSample(final Graph targetGraph, final Graph sourceGraph, final long sampleSize,
			final Graph.Component component){
		this.targetGraph = targetGraph;
		this.sourceGraph = sourceGraph;
		this.sampleSize = sampleSize;
		this.component = component;
	}

	@Override
	public String getLabel(){
		return "GetRandomSample";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("sourceGraph");
		inline_field_values.add(sourceGraph.name);
		inline_field_names.add("sampleSize");
		inline_field_values.add(String.valueOf(sampleSize));
		inline_field_names.add("component");
		inline_field_values.add(String.valueOf(component));
	}

	private final LongStream getRandomNumberStream(final long sampleSize, final long maxValue){
		final Random random = new Random(System.nanoTime());
		return random.longs(sampleSize, 0, maxValue);
	}

	private final String _executeForVertex(final QueryInstructionExecutor executor, final long vertexCount){
		final long localSampleSize = Math.min(sampleSize, vertexCount);
		final LongStream indexStream = getRandomNumberStream(localSampleSize, vertexCount);
		final OfLong iterator = indexStream.iterator();
		while(iterator.hasNext()){
			final long l = iterator.nextLong();
			executor.getSubsetVertex(targetGraph, sourceGraph, l, l + 1);
		}
		return null;
	}

	private final String _executeForEdge(final QueryInstructionExecutor executor, final long edgeCount){
		final long localSampleSize = Math.min(sampleSize, edgeCount);
		final LongStream indexStream = getRandomNumberStream(localSampleSize, edgeCount);
		final OfLong iterator = indexStream.iterator();
		while(iterator.hasNext()){
			final long l = iterator.nextLong();
			executor.getSubsetEdge(targetGraph, sourceGraph, l, l + 1);
		}
		return null;
	}

	@Override
	public final String execute(final QueryInstructionExecutor executor){
		final GraphStatistic.Count count = executor.getGraphCount(sourceGraph);
		switch(component){
		case kEdge:
			return _executeForEdge(executor, count.getEdges());
		case kVertex:
			return _executeForVertex(executor, count.getVertices());
		default:
			throw new RuntimeException("Unknown Graph component to get random sample of: " + component);
		}
	}
}
