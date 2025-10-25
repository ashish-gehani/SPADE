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
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class SaveGraph extends Instruction<String>{

	public static enum Format{
		kJson, kDot;
	}

	public final Graph targetGraph;
	public final Format format;
	public final boolean force;
	public final String filePath;

	public SaveGraph(final Graph targetGraph, final Format format, final boolean force,
			final String filePath){
		this.targetGraph = targetGraph;
		this.format = format;
		this.force = force;
		this.filePath = filePath;
	}

	@Override
	public String getLabel(){
		return "SaveGraph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("format");
		inline_field_values.add(format.name());
		inline_field_names.add("force");
		inline_field_values.add(String.valueOf(force));
		inline_field_names.add("filePath");
		inline_field_values.add(String.valueOf(filePath));
	}

	@Override
	public final String exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		executor.saveGraph(targetGraph, format, force, filePath);
		return null;
	}
}
