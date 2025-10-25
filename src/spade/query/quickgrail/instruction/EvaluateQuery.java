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
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Evaluate an arbitrary SQL query (that is supported by Quickstep).
 */
public class EvaluateQuery extends Instruction<ResultTable>{
	public final String nativeQuery;

	public EvaluateQuery(String nativeQuery){
		this.nativeQuery = nativeQuery;
	}

	@Override
	public String getLabel(){
		return "EvaluateQuery";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("nativeQuery");
		inline_field_values.add(nativeQuery);
	}

	@Override
	public final ResultTable exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		ResultTable table = executor.evaluateQuery(nativeQuery);
		if(table == null){
			table = ResultTable.FromText("", ',');
		}
		return table;
	}
}
