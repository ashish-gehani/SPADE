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
package spade.query.quickgrail.core;

import java.util.ArrayList;
import java.util.List;

import spade.query.quickgrail.instruction.Instruction;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * A compiled QuickGrail program that is composed of a list of primitive
 * instructions.
 */
public class Program extends TreeStringSerializable{
	private final List<Instruction> instructions = new ArrayList<Instruction>();

	public Program(List<Instruction> instructions){
		if(instructions != null){
			this.instructions.addAll(instructions);
		}
	}
	
	public final Instruction getInstruction(int i){
		if(i > -1 && i < instructions.size()){
			return instructions.get(i);
		}
		return null;
	}
	
	public final int getInstructionsSize(){
		return instructions.size();
	}

	@Override
	public String getLabel(){
		return "Program";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		container_child_field_names.add("instructions");
		container_child_fields.add(new ArrayList<Instruction>(instructions));
	}
}
