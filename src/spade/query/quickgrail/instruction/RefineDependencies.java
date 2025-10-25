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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.query.execution.Context;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class RefineDependencies extends Instruction<String>{

	private static final Pattern edgePattern = Pattern.compile("\"(.*)\" -> \"(.*)\" \\[label=\"WasDependentOn\"", Pattern.DOTALL);

	public final Graph targetGraph, subjectGraph;
	public final String dependencyMapPath;
	public final int maxDepth;
	public final String edgeAnnotationName;

	private final Map<String, LinkedHashSet<String>> edgeToDepdendentOnEdges = new HashMap<>();

	public RefineDependencies(final Graph targetGraph, final Graph subjectGraph, final String dependencyMapPath, final int maxDepth,
			final String edgeAnnotationName){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.dependencyMapPath = dependencyMapPath;
		this.maxDepth = maxDepth;
		this.edgeAnnotationName = edgeAnnotationName;
	}

	private final Map<String, LinkedHashSet<String>> readDependencyMap(final String path) throws Exception{
		try(final BufferedReader reader = new BufferedReader(new FileReader(new File(path)))){
			final Map<String, LinkedHashSet<String>> result = new HashMap<>();

			String line = null;
			while((line = reader.readLine()) != null){
				final Matcher edgeMatcher = edgePattern.matcher(line);
				if(edgeMatcher.find()){
					final String dependent = edgeMatcher.group(1);
					final String dependentOn = edgeMatcher.group(2);
					LinkedHashSet<String> existingList = result.get(dependent);
					if(existingList == null){
						existingList = new LinkedHashSet<String>();
						result.put(dependent, existingList);
					}
					existingList.add(dependentOn);
				}
			}

			return result;
		}catch(Exception e){
			throw e;
		}
	}

	@Override
	public final String exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		try{
			edgeToDepdendentOnEdges.putAll(readDependencyMap(this.dependencyMapPath));
		}catch(Exception e){
			throw new RuntimeException("Failed to read dependency map: '" + this.dependencyMapPath + "'", e);
		}
		final Graph srcEdgeGraph = executor.createNewGraph();
		final Graph srcVerticesGraph = executor.createNewGraph();
		final Graph dstEdgeGraph = executor.createNewGraph();
		final Graph dstVerticesGraph = executor.createNewGraph();
		final Graph tmpPathGraph = executor.createNewGraph();
		for(final Map.Entry<String, LinkedHashSet<String>> entry : edgeToDepdendentOnEdges.entrySet()){
			final String srcCallSite = entry.getKey();

			executor.getEdge(srcEdgeGraph, subjectGraph, this.edgeAnnotationName, PredicateOperator.EQUAL, srcCallSite, true);
			executor.getEdgeEndpoint(srcVerticesGraph, srcEdgeGraph, GetEdgeEndpoint.Component.kSource);

			final GraphStatistic.Count srcVerticesCount = executor.getGraphCount(srcVerticesGraph);
			if(srcVerticesCount.hasVertices()){
				final LinkedHashSet<String> dstCallSites = entry.getValue();
				for(final String dstCallSite : dstCallSites){
					executor.getEdge(dstEdgeGraph, subjectGraph, this.edgeAnnotationName, PredicateOperator.EQUAL, dstCallSite, true);
					executor.getEdgeEndpoint(dstVerticesGraph, dstEdgeGraph, GetEdgeEndpoint.Component.kDestination);

					final GraphStatistic.Count dstVerticesCount = executor.getGraphCount(dstVerticesGraph);
					if(dstVerticesCount.hasVertices()){
						executor.getSimplePath(tmpPathGraph, subjectGraph, srcVerticesGraph, dstVerticesGraph, maxDepth);
						executor.unionGraph(targetGraph, tmpPathGraph);
						executor.clearGraph(tmpPathGraph);
					}

					executor.clearGraph(dstEdgeGraph);
					executor.clearGraph(dstVerticesGraph);
				}
			}

			executor.clearGraph(srcEdgeGraph);
			executor.clearGraph(srcVerticesGraph);
		}
		return null;
	}

	@Override
	public String getLabel(){
		return "RefineDependencies";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		inline_field_names.add("dependencyMapPath");
		inline_field_values.add(dependencyMapPath);
		inline_field_names.add("maxDepth");
		inline_field_values.add(String.valueOf(maxDepth));
		inline_field_names.add("edgeAnnotationName");
		inline_field_values.add(edgeAnnotationName);
	}

}
