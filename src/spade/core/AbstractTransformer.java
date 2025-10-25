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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spade.transformer.query.Context;
import spade.transformer.query.parameter.AbstractParameter;
import spade.utility.Result;

public abstract class AbstractTransformer {

	private final Context context = new Context();

	private String initArguments;

	public void setParametersInContext(AbstractParameter<?, ?>... parameters)
	{
		if (parameters == null)
			throw new IllegalArgumentException("NULL parameters for transformer");

		int i = -1;
		List<AbstractParameter<?, ?>> paramList = new ArrayList<>();
		for (AbstractParameter<?, ?> parameter : parameters) {
			i++;
			if (parameter == null)
				throw new IllegalArgumentException("NULL parameter at index (" + i + ") for transformer");
			paramList.add(parameter);
		}
		this.context.set(paramList);
	}

	public Context getContext()
	{
		return context;
	}

	public boolean initialize(String initArguments) {
		this.initArguments = initArguments;
		return true;
	}

	public boolean shutdown() {
		return true;
	}

	public final String getInitArguments() {
		return initArguments;
	}

	public abstract Graph transform(Graph graph);

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
			transformer.initArguments = initArguments;
			return Result.successful(true);
		}else{
			final String msg = "Failed to initialize transformer '" + transformer.getClass().getSimpleName() + "' with arguments '"
					+ initArguments + "'";
			return Result.failed(msg);
		}
	}

	public static Result<Graph> executeWithQueryContext(
		final AbstractTransformer transformer,
		final Graph graph,
		final spade.query.execution.Context executionCtx
	){
		try{
			final Context queryTransformerCtx = executionCtx.getTransformerContext();
			queryTransformerCtx.materialize(executionCtx.getExecutor());

			return AbstractTransformer.executeWithOtherContext(transformer, graph, queryTransformerCtx);
		}catch(Exception e){
			return Result.failed("Error in graph transformation by " + transformer.getClass().getName(), e, null);
		}
	}

	public static Result<Graph> executeWithOtherContext(
		final AbstractTransformer transformer,
		final Graph graph,
		final Context otherCtx
	){
		try{
			final Context thisTransformerCtx = transformer.getContext();

			thisTransformerCtx.copyValuesPresentIn(otherCtx);

			return AbstractTransformer.execute(transformer, graph);
		}catch(Exception e){
			return Result.failed("Error in graph transformation by " + transformer.getClass().getName(), e, null);
		}
	}

	public static Result<Graph> execute(
		final AbstractTransformer transformer,
		final Graph graph
	){
		try{
			final Graph transformedGraph = transformer.transform(graph);
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
}
