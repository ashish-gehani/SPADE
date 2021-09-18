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

import spade.query.quickgrail.core.GraphRemoteCount;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public abstract class RemoteVariableOperation<R extends Serializable> extends Instruction<R>{

	public final Graph localGraph;

	public RemoteVariableOperation(final Graph localGraph){
		this.localGraph = localGraph;
	}

	public static class Link extends RemoteVariableOperation<String>{
		public final String host;
		public final int port;
		public final String remoteSymbol;

		public Link(final Graph localGraph, final String host, final int port, final String remoteSymbol){
			super(localGraph);
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
			inline_field_names.add("localGraph");
			inline_field_values.add(localGraph.name);
			inline_field_names.add("host");
			inline_field_values.add(String.valueOf(host));
			inline_field_names.add("port");
			inline_field_values.add(String.valueOf(port));
			inline_field_names.add("remoteSymbol");
			inline_field_values.add(String.valueOf(remoteSymbol));
		}

		@Override
		public final String execute(final QueryInstructionExecutor executor){
			executor.getQueryEnvironment().setRemoteSymbol(localGraph, new Graph.Remote(host, port, remoteSymbol));
			return null;
		}
	}

	public static class Unlink extends RemoteVariableOperation<String>{
		public final String host;
		public final int port;
		public final String remoteSymbol;

		public Unlink(final Graph localGraph, final String host, final int port, final String remoteSymbol){
			super(localGraph);
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
			inline_field_names.add("localGraph");
			inline_field_values.add(localGraph.name);
			inline_field_names.add("host");
			inline_field_values.add(String.valueOf(host));
			inline_field_names.add("port");
			inline_field_values.add(String.valueOf(port));
			inline_field_names.add("remoteSymbol");
			inline_field_values.add(String.valueOf(remoteSymbol));
		}

		@Override
		public final String execute(final QueryInstructionExecutor executor){
			executor.getQueryEnvironment().removeRemoteSymbol(localGraph, new Graph.Remote(host, port, remoteSymbol));
			return null;
		}
	}

	public static class List extends RemoteVariableOperation<GraphRemoteCount>{
		public List(final Graph localGraph){
			super(localGraph);
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
			inline_field_names.add("localGraph");
			inline_field_values.add(localGraph.name);
		}

		@Override
		public final GraphRemoteCount execute(final QueryInstructionExecutor executor){
			return executor.listRemoteVariables(localGraph);
		}
	}

	public static class Clear extends RemoteVariableOperation<String>{
		public Clear(final Graph localGraph){
			super(localGraph);
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
			inline_field_names.add("localGraph");
			inline_field_values.add(localGraph.name);
		}

		@Override
		public final String execute(final QueryInstructionExecutor executor){
			executor.getQueryEnvironment().removeRemoteSymbols(localGraph);
			return null;
		}
	}

	public static class Copy extends RemoteVariableOperation<String>{
		public final Graph localDstGraph;

		public Copy(final Graph localGraph, final Graph localDstGraph){
			super(localGraph);
			this.localDstGraph = localDstGraph;
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
			inline_field_names.add("localGraph");
			inline_field_values.add(localGraph.name);
			inline_field_names.add("localDstGraph");
			inline_field_values.add(localDstGraph.name);
		}

		@Override
		public final String execute(final QueryInstructionExecutor executor){
			executor.getQueryEnvironment().copyRemoteSymbols(localGraph, localDstGraph);
			return null;
		}
	}

}