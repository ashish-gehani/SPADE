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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Kernel;
import spade.query.execution.Context;
import spade.query.quickgrail.core.GraphRemoteCount;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.RemoteGraph;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.utility.HelperFunctions;
import spade.utility.RemoteSPADEQueryConnection;

public abstract class RemoteVariableOperation<R extends Serializable> extends Instruction<R>{

	public static class Query extends RemoteVariableOperation<Serializable>{
		public final String host;
		public final int port;
		public final String remoteQuery;

		public Query(final String host, final int port, final String remoteQuery){
			this.host = host;
			this.port = port;
			this.remoteQuery = remoteQuery;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Query";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("host");
			inline_field_values.add(String.valueOf(host));
			inline_field_names.add("port");
			inline_field_values.add(String.valueOf(port));
			inline_field_names.add("remoteQuery");
			inline_field_values.add(String.valueOf(remoteQuery));
		}

		@Override
		public final Serializable exec(final Context ctx) {
			// final QueryInstructionExecutor executor = ctx.getExecutor();
			try(final RemoteSPADEQueryConnection connection = 
					new RemoteSPADEQueryConnection(Kernel.getHostName(), host, port)){
				connection.connect(Kernel.getClientSocketFactory(), 5 * 1000);
				return connection.executeQuery(remoteQuery).getResult();
			}catch(Exception e){
				throw new RuntimeException("Failed to execute remote query", e);
			}
		}
	}

	public static class Link extends RemoteVariableOperation<String>{
		public final Graph graph;
		public final String host;
		public final int port;
		public final String remoteSymbol;

		public Link(final Graph graph, final String host, final int port, final String remoteSymbol){
			this.graph = graph;
			this.host = host;
			this.port = port;
			this.remoteSymbol = remoteSymbol;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Link";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
			inline_field_names.add("host");
			inline_field_values.add(String.valueOf(host));
			inline_field_names.add("port");
			inline_field_values.add(String.valueOf(port));
			inline_field_names.add("remoteSymbol");
			inline_field_values.add(String.valueOf(remoteSymbol));
		}

		@Override
		public final String exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			executor.getQueryEnvironment().setRemoteSymbol(graph, new Graph.Remote(host, port, remoteSymbol));
			return null;
		}
	}

	public static class Unlink extends RemoteVariableOperation<String>{
		public final Graph graph;
		public final String host;
		public final int port;
		public final String remoteSymbol;

		public Unlink(final Graph graph, final String host, final int port, final String remoteSymbol){
			this.graph = graph;
			this.host = host;
			this.port = port;
			this.remoteSymbol = remoteSymbol;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Unlink";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
			inline_field_names.add("host");
			inline_field_values.add(String.valueOf(host));
			inline_field_names.add("port");
			inline_field_values.add(String.valueOf(port));
			inline_field_names.add("remoteSymbol");
			inline_field_values.add(String.valueOf(remoteSymbol));
		}

		@Override
		public final String exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			executor.getQueryEnvironment().removeRemoteSymbol(graph, new Graph.Remote(host, port, remoteSymbol));
			return null;
		}
	}

	public static class List extends RemoteVariableOperation<GraphRemoteCount>{
		public final Graph graph;
		public List(final Graph graph){
			this.graph = graph;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.List";
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
		public final GraphRemoteCount exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			return executor.listRemoteVariables(graph);
		}
	}

	public static class Clear extends RemoteVariableOperation<String>{
		public final Graph graph;
		public Clear(final Graph graph){
			this.graph = graph;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Clear";
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
		public final String exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			executor.getQueryEnvironment().removeRemoteSymbols(graph);
			return null;
		}
	}

	public static class Copy extends RemoteVariableOperation<String>{
		public final Graph dstGraph, srcGraph;

		public Copy(final Graph dstGraph, final Graph srcGraph){
			this.dstGraph = dstGraph;
			this.srcGraph = srcGraph;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Copy";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("dstGraph");
			inline_field_values.add(dstGraph.name);
			inline_field_names.add("srcGraph");
			inline_field_values.add(srcGraph.name);
		}

		@Override
		public final String exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			executor.getQueryEnvironment().copyRemoteSymbols(dstGraph, srcGraph);
			return null;
		}
	}

	public static class Intersect extends RemoteVariableOperation<String>{
		public final Graph resultGraph;
		public final Graph lhsGraph, rhsGraph;

		public Intersect(final Graph resultGraph, final Graph lhsGraph, final Graph rhsGraph){
			this.resultGraph = resultGraph;
			this.lhsGraph = lhsGraph;
			this.rhsGraph = rhsGraph;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Intersect";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("resultGraph");
			inline_field_values.add(resultGraph.name);
			inline_field_names.add("lhsGraph");
			inline_field_values.add(lhsGraph.name);
			inline_field_names.add("rhsGraph");
			inline_field_values.add(rhsGraph.name);
		}

		@Override
		public final String exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			executor.getQueryEnvironment().intersectRemoteSymbols(resultGraph, lhsGraph, rhsGraph);
			return null;
		}
	}

	public static class Subtract extends RemoteVariableOperation<String>{
		public final Graph resultGraph;
		public final Graph lhsGraph, rhsGraph;

		public Subtract(final Graph resultGraph, final Graph lhsGraph, final Graph rhsGraph){
			this.resultGraph = resultGraph;
			this.lhsGraph = lhsGraph;
			this.rhsGraph = rhsGraph;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Subtract";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("resultGraph");
			inline_field_values.add(resultGraph.name);
			inline_field_names.add("lhsGraph");
			inline_field_values.add(lhsGraph.name);
			inline_field_names.add("rhsGraph");
			inline_field_values.add(rhsGraph.name);
		}

		@Override
		public final String exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			executor.getQueryEnvironment().subtractRemoteSymbols(resultGraph, lhsGraph, rhsGraph);
			return null;
		}
	}

	public static class Export extends RemoteVariableOperation<RemoteGraph>{
		public final Graph graph;
		public final boolean force;
		public final boolean verify;
		public Export(final Graph graph, final boolean force, final boolean verify){
			this.graph = graph;
			this.force = force;
			this.verify = verify;
		}

		@Override
		public String getLabel(){
			return "RemoteVariableOperation.Export";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("graph");
			inline_field_values.add(graph.name);
			inline_field_names.add("force");
			inline_field_values.add(String.valueOf(force));
			inline_field_names.add("verify");
			inline_field_values.add(String.valueOf(verify));
		}

		@Override
		public final RemoteGraph exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			final RemoteGraph result = new RemoteGraph(graph.name);
			for(final Graph.Remote remote : graph.getRemotes()){
				RemoteSPADEQueryConnection connection = null;
				try{
					connection = new RemoteSPADEQueryConnection(Kernel.getHostName(), remote.host, remote.port);
					connection.connect(Kernel.getClientSocketFactory(), 5 * 1000);

					spade.core.Graph remoteGraph = connection.exportGraph(remote.symbol, force, verify);
					try{
						remoteGraph = HelperFunctions.decryptGraph(remoteGraph);
					}catch(Exception e){
						throw new RuntimeException("Failed to decrypt graph", e);
					}
					result.put(remote.toFormattedString(), remoteGraph);
				}catch(Exception e){
					Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Failed to query remote SPADE server", e);
				}finally{
					if(connection != null){
						try{
							connection.close();
						}catch(Exception e){

						}
					}
				}	
			}
			return result;
		}
	}
}