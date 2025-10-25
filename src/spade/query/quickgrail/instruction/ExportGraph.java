/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.RemoteGraph;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Export a QuickGrail graph to spade.core.Graph or to DOT representation.
 */
public class ExportGraph extends Instruction<spade.core.Graph>{

	public final Graph targetGraph;
	public final boolean force, verify;

	public ExportGraph(final Graph targetGraph, final boolean force, final boolean verify){
		this.targetGraph = targetGraph;
		this.force = force;
		this.verify = verify;
	}

	@Override
	public String getLabel(){
		return "ExportGraph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("force");
		inline_field_values.add(String.valueOf(force));
		inline_field_names.add("verify");
		inline_field_values.add(String.valueOf(verify));
	}

	@Override
	public final spade.core.Graph exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		spade.core.Graph resultGraph = executor.exportGraph(targetGraph, force);
		if(resultGraph == null){
			resultGraph = new spade.core.Graph();
		}

		final boolean verifyRemote = false;
		final RemoteGraph remoteGraph = new RemoteVariableOperation.Export(targetGraph, force, verifyRemote).exec(ctx);
		resultGraph.union(remoteGraph);

		return resultGraph;
	}
}
