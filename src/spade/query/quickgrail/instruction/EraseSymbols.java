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
import java.util.List;

import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Remove the specified graphs stored in QuickGrail.
 */
public class EraseSymbols extends Instruction<String>{
	// The graph variable names to be erased.
	private final List<String> symbols;

	public EraseSymbols(ArrayList<String> symbols){
		this.symbols = symbols;
	}

	public final ArrayList<String> getSymbols(){
		return symbols == null ? null : new ArrayList<String>(symbols);
	}

	@Override
	public String getLabel(){
		return "EraseSymbols";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("symbols");
		inline_field_values.add(String.join(",", symbols == null ? new ArrayList<String>() : symbols));
	}

	@Override
	public final String execute(final QueryInstructionExecutor executor){
		executor.eraseSymbols(getSymbols());
		return null;
	}
}
