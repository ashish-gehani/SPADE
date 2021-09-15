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
import spade.query.quickgrail.utility.TreeStringSerializable;

public class RemoteVariableOperation extends Instruction{

	public static enum Type{
		LINK, UNLINK, LIST, CLEAR, COPY
	};

	public final Type type;
	public final Graph localGraph;
	public final Graph dstLocalGraph;
	public final String host;
	public final int port;
	public final String remoteSymbol;

	public RemoteVariableOperation(Type type, Graph localGraph, String host, int port, String remoteSymbol,
			final Graph dstLocalGraph){
		this.type = type;
		this.localGraph = localGraph;
		this.host = host;
		this.port = port;
		this.remoteSymbol = remoteSymbol;
		this.dstLocalGraph = dstLocalGraph;
	}

	public static final RemoteVariableOperation instanceOfUnlink(Graph localGraph, String host, int port,
			String remoteSymbol){
		return new RemoteVariableOperation(Type.UNLINK, localGraph, host, port, remoteSymbol, null);
	}

	public static final RemoteVariableOperation instanceOfLink(Graph localGraph, String host, int port,
			String remoteSymbol){
		return new RemoteVariableOperation(Type.LINK, localGraph, host, port, remoteSymbol, null);
	}

	public static final RemoteVariableOperation instanceOfClear(Graph localGraph){
		return new RemoteVariableOperation(Type.CLEAR, localGraph, null, 0, null, null);
	}

	public static final RemoteVariableOperation instanceOfList(Graph localGraph){
		return new RemoteVariableOperation(Type.LIST, localGraph, null, 0, null, null);
	}

	public static final RemoteVariableOperation instanceOfCopy(Graph srcLocalGraph, Graph dstLocalGraph){
		return new RemoteVariableOperation(Type.COPY, srcLocalGraph, null, 0, null, dstLocalGraph);
	}

	@Override
	public String getLabel(){
		return "RemoteVariableOperation";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("type");
		inline_field_values.add(String.valueOf(type));
		inline_field_names.add("localGraph");
		inline_field_values.add(localGraph.name);
		inline_field_names.add("host");
		inline_field_values.add(String.valueOf(host));
		inline_field_names.add("port");
		inline_field_values.add(String.valueOf(port));
		inline_field_names.add("remoteSymbol");
		inline_field_values.add(String.valueOf(remoteSymbol));
		inline_field_names.add("dstLocalGraph");
		inline_field_values.add(String.valueOf(dstLocalGraph));
	}

}