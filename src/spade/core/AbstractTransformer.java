/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.core;

import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import spade.query.quickgrail.instruction.GetLineage;
import spade.utility.Result;

public abstract class AbstractTransformer{

	public String arguments;

	public boolean initialize(String arguments){
		return true;
	}

	public boolean shutdown(){
		return true;
	}

	public abstract Graph transform(Graph graph, final ExecutionContext executionContext);

	public static String getAnnotationSafe(AbstractVertex vertex, String annotation){
		if(vertex != null){
			String value;
			if((value = vertex.getAnnotation(annotation)) != null){
				return value;
			}
		}
		return "";
	}

	public static String getAnnotationSafe(AbstractEdge edge, String annotation){
		if(edge != null){
			String value;
			if((value = edge.getAnnotation(annotation)) != null){
				return value;
			}
		}
		return "";
	}

	public static AbstractVertex createNewWithoutAnnotations(AbstractVertex vertex, String... annotations){
		AbstractVertex newVertex = vertex.copyAsVertex();
		if(annotations != null){
			for(String annotation : annotations){
				newVertex.removeAnnotation(annotation);
			}
		}
		newVertex.removeAnnotation(PARENT_VERTEX_KEY);
		newVertex.removeAnnotation(CHILD_VERTEX_KEY);
		newVertex.removeAnnotation(PRIMARY_KEY);
		return newVertex;
	}

	public static AbstractEdge createNewWithoutAnnotations(AbstractEdge edge, String... annotations){
		AbstractVertex newChild = createNewWithoutAnnotations(edge.getChildVertex(), annotations);
		AbstractVertex newParent = createNewWithoutAnnotations(edge.getParentVertex(), annotations);
		AbstractEdge newEdge = new Edge(newChild, newParent);
		newEdge.addAnnotations(edge.getCopyOfAnnotations());
		if(annotations != null){
			for(String annotation : annotations){
				newEdge.removeAnnotation(annotation);
			}
		}
		newEdge.removeAnnotation(PARENT_VERTEX_KEY);
		newEdge.removeAnnotation(CHILD_VERTEX_KEY);
		newEdge.removeAnnotation(PRIMARY_KEY);
		return newEdge;
	}

	public static void removeEdges(Graph result, Graph removeFrom, Graph toRemove){
		Set<AbstractEdge> toRemoveEdges = new HashSet<>();
		for(AbstractEdge edge : toRemove.edgeSet()){
			toRemoveEdges.add(createNewWithoutAnnotations(edge));
		}
		for(AbstractEdge edge : removeFrom.edgeSet()){
			AbstractEdge strippedEdge = createNewWithoutAnnotations(edge);
			if(toRemoveEdges.contains(strippedEdge)){
				continue;
			}
			result.putVertex(strippedEdge.getChildVertex());
			result.putVertex(strippedEdge.getParentVertex());
			result.putEdge(strippedEdge);
		}
	}

	///////////////////////////////////////////

	/**
	 * Using LinkedHashSet to ensure that all elements are unique and there is a
	 * fixed iteration order
	 * 
	 * Order matters for query side. Whatever the order is specified here, it must
	 * be used in the query
	 * 
	 * @return The list of arguments, as available in enum
	 *         'spade.core.AbstractTransformer.ArgumentName', (if any) that the
	 *         transformer expects
	 */
	public abstract LinkedHashSet<ArgumentName> getArgumentNames();

	public static void validateArguments(final AbstractTransformer transformer, final ExecutionContext context){
		if(transformer == null){
			throw new RuntimeException("NULL transformer to validate arguments for");
		}
		if(context == null){
			throw new RuntimeException("NULL transformer execution context");
		}
		final LinkedHashSet<ArgumentName> argumentNames = transformer.getArgumentNames();
		if(argumentNames == null){
			throw new RuntimeException("NULL transformer argument names");
		}
		for(final ArgumentName argumentName : argumentNames){
			if(argumentName == null){
				throw new RuntimeException("NULL transformer argument name");
			}
			switch(argumentName){
				case SOURCE_GRAPH:
					if(context.getSourceGraph() == null){
						throw new RuntimeException("NULL " + argumentName + " argument for transformer");
					}
					break;
				case MAX_DEPTH:
					if(context.getMaxDepth() == null){
						throw new RuntimeException("NULL " + argumentName + " argument for transformer");
					}
					break;
				case DIRECTION:
					if(context.getDirection() == null){
						throw new RuntimeException("NULL " + argumentName + " argument for transformer");
					}
					break;
				default:
					throw new RuntimeException("Unhandled tranformer argument name: " + argumentName);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static Result<AbstractTransformer> create(final String transformerName){
		final String qualifiedClassName = "spade.transformer." + transformerName;

		final Class<AbstractTransformer> clazz;
		try{
			clazz = (Class<AbstractTransformer>)Class.forName(qualifiedClassName);
		}catch(Exception e){
			return Result.failed("Failed to find/load transformer class: '" + qualifiedClassName + "'", e, null);
		}

		final Constructor<AbstractTransformer> constructor;
		try{
			constructor = clazz.getDeclaredConstructor();
		}catch(Exception e){
			return Result.failed("Illegal implementation for '" + qualifiedClassName + "' transformer."
					+ " Must have an empty public constructor", e, null);
		}

		final AbstractTransformer instance;
		try{
			instance = constructor.newInstance();
		}catch(Exception e){
			return Result.failed("Failed to instantiate transformer using the empty constructor: " + clazz, e, null);
		}

		return Result.successful(instance);
	}

	public static Result<Boolean> init(final AbstractTransformer transformer, final String initArguments){
		if(transformer == null){
			return Result.failed("NULL transformer to initialize");
		}

		final boolean isInitialized;
		try{
			if(initArguments == null){
				isInitialized = transformer.initialize("");
			}else{
				isInitialized = transformer.initialize(initArguments);
			}
		}catch(Exception e){
			return Result.failed("Failed to initialize transformer: " + transformer.getClass().getSimpleName(), e, null);
		}

		if(isInitialized){
			transformer.arguments = initArguments;
			return Result.successful(true);
		}else{
			final String msg = "Failed to initialize transformer '" + transformer.getClass().getSimpleName() + "' with arguments '"
					+ initArguments + "'";
			return Result.failed(msg);
		}
	}

	public static Result<Graph> execute(final AbstractTransformer transformer, final Graph graph,
			final ExecutionContext executionContext){
		try{
			validateArguments(transformer, executionContext);

			final Graph transformedGraph = transformer.transform(graph, executionContext);
			if(transformedGraph == null){
				return Result.failed("NULL result for graph transformation by " + transformer.getClass().getName());
			}else{
				return Result.successful(transformedGraph);
			}
		}catch(Exception e){
			return Result.failed("Error in graph transformation by " + transformer.getClass().getName(), e, null);
		}
	}

	public static Result<Boolean> destroy(final AbstractTransformer transformer){
		if(transformer == null){
			return Result.failed("NULL transformer to shutdown");
		}
		try{
			final boolean result = transformer.shutdown();
			if(!result){
				return Result.failed("Failed to shutdown transformer gracefully");
			}else{
				return Result.successful(result);
			}
		}catch(Exception e){
			return Result.failed("Failed to shutdown transformer gracefully", e, null);
		}
	}

	/*
	 * Adding a new field in execution context
	 * 1) Add in the enum ArgumentName
	 * 2) Add in the class ExecutionContext
	 * 3) Handle the field from query-side in 'QuickGrailQueryResolver' function 'resolveTransformGraph'
	 * 		a) Convert the data received in query to data types that are passed to the instruction execution side
	 * 4) Handle the field in 'QuickGrailExecutor' function 'transformGraph'
	 * 		a) Convert the data received from (3.a) to data types in the ExecutionContext
	 * 5) Update the functions like 'getLineage' in 'QuickGrailExecutor' to populate the new field where necessary 
	 * 6) Add validation code for the new field in 'validateArguments' in AbstractTransformer
	 * 7) Update the list of arguments (getArgumentNames) for the transformer where necessary
	 */
	public static enum ArgumentName{
		// This is the order of argument for get lineage query. 
		SOURCE_GRAPH("The graph of vertices for a lineage query"),
		MAX_DEPTH("The maximum depth for a lineage or path query"),
		DIRECTION("The direction in which the lineage query was executed");

		public final String description;
		private ArgumentName(final String description){
			this.description = description;
		}
	}

	public static class ExecutionContext implements Serializable{
		private static final long serialVersionUID = -5190194133392169719L;

		private spade.core.Graph sourceGraph;
		private Integer maxDepth;
		private GetLineage.Direction direction;

		public spade.core.Graph getSourceGraph(){
			return sourceGraph;
		}

		public void setSourceGraph(spade.core.Graph sourceGraph){
			this.sourceGraph = sourceGraph;
		}

		public Integer getMaxDepth(){
			return maxDepth;
		}

		public void setMaxDepth(Integer maxDepth){
			this.maxDepth = maxDepth;
		}

		public GetLineage.Direction getDirection(){
			return direction;
		}

		public void setDirection(GetLineage.Direction direction){
			this.direction = direction;
		}
	}
}
