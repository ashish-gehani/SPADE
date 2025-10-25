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

import spade.query.execution.Context;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class GetSubset extends Instruction<String>{
	public final Graph targetGraph, sourceGraph;
	public final Long fromInclusive, toExclusive;
	public final Graph.Component component;

	public GetSubset(final Graph targetGraph, final Graph sourceGraph, final Long fromInclusive, final Long toExclusive,
			final Graph.Component component){
		this.targetGraph = targetGraph;
		this.sourceGraph = sourceGraph;
		this.fromInclusive = fromInclusive;
		this.toExclusive = toExclusive;
		this.component = component;
	}

	@Override
	public String getLabel(){
		return "GetSubset";
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
		inline_field_names.add("fromInclusive");
		inline_field_values.add(String.valueOf(fromInclusive));
		inline_field_names.add("toExclusive");
		inline_field_values.add(String.valueOf(toExclusive));
		inline_field_names.add("component");
		inline_field_values.add(String.valueOf(component));
	}

	private long resolveFromIndex(final Long fromOriginal, final long lastIndex){
		if(fromOriginal == null){
			return 0;
		}
		if(fromOriginal < 0){
			return lastIndex + fromOriginal;
		}
		if(fromOriginal > lastIndex){
			throw new RuntimeException("FROM index (" + fromOriginal + ") out of bounds (" + lastIndex + ")");
		}
		return fromOriginal;
	}

	private long resolveToIndex(final Long toOriginal, final long lastIndex){
		if(toOriginal == null){
			return lastIndex;
		}
		if(toOriginal < 0){
			return lastIndex + toOriginal;
		}
		if(toOriginal > lastIndex){
			throw new RuntimeException("TO index (" + toOriginal + ") out of bounds (" + lastIndex + ")");
		}
		return toOriginal;
	}

	private final void validateFromAndToIndex(final long from, final long to){
		if(from >= to){
			throw new RuntimeException(
					"FROM index (" + from + ") must be less than TO index (" + to + ") - resolved values shown");
		}
	}

	private final String _executeForVertex(final QueryInstructionExecutor executor, final GraphStatistic.Count count){
		final long last = count.getVertices();
		final long from = resolveFromIndex(fromInclusive, last);
		final long to = resolveToIndex(toExclusive, last);
		validateFromAndToIndex(from, to);
		executor.getSubsetVertex(targetGraph, sourceGraph, from, to);
		return null;
	}

	private final String _executeForEdge(final QueryInstructionExecutor executor, final GraphStatistic.Count count){
		final long last = count.getEdges();
		final long from = resolveFromIndex(fromInclusive, last);
		final long to = resolveToIndex(toExclusive, last);
		validateFromAndToIndex(from, to);
		executor.getSubsetEdge(targetGraph, sourceGraph, from, to);
		return null;
	}

	@Override
	public final String exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		final GraphStatistic.Count count = executor.getGraphCount(sourceGraph);
		switch(component){
		case kEdge:
			return _executeForEdge(executor, count);
		case kVertex:
			return _executeForVertex(executor, count);
		default:
			throw new RuntimeException("Unknown Graph component to get subset of: " + component);
		}
	}
}
