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
import spade.query.quickgrail.core.List;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.utility.TreeStringSerializable;

public abstract class GetList<R extends List> extends Instruction<R>{

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
	}

	public static class GetGraph extends GetList<List.GraphList>{
		@Override
		public String getLabel(){
			return "GetList.GetGraph";
		}
		@Override
		public final List.GraphList exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			return executor.listGraphs();
		}
	}

	public static class GetEnvironment extends GetList<List.EnvironmentList>{
		@Override
		public String getLabel(){
			return "GetList.GetEnvironment";
		}
		@Override
		public final List.EnvironmentList exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			return executor.listEnvironment();
		}
	}

	public static class GetConstraint extends GetList<List.ConstraintList>{
		@Override
		public String getLabel(){
			return "GetList.GetConstraint";
		}
		@Override
		public final List.ConstraintList exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			return executor.listConstraints();
		}
	}

	public static class GetAll extends GetList<List.AllList>{
		@Override
		public String getLabel(){
			return "GetList.GetAll";
		}
		@Override
		public final List.AllList exec(final Context ctx) {
			final QueryInstructionExecutor executor = ctx.getExecutor();
			return executor.listAll();
		}
	}
}
