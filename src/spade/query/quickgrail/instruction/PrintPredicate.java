/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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

import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class PrintPredicate extends Instruction<PredicateNode>{

	public final PredicateNode predicateRoot;

	public PrintPredicate(PredicateNode predicateRoot){
		this.predicateRoot = predicateRoot;
	}

	@Override
	public String getLabel(){
		return this.getClass().getSimpleName();
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("predicateRoot");
		inline_field_values.add(predicateRoot.toString());
	}

	@Override
	public final PredicateNode execute(final QueryInstructionExecutor executor){
		return executor.printPredicate(predicateRoot);
	}
}
