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

import spade.query.quickgrail.utility.TreeStringSerializable;

public class EnvironmentVariableOperation extends Instruction{

	public static enum Type{SET, UNSET, LIST, PRINT};
	
	public final Type type;
	public final String name;
	public final String value;
	
	private EnvironmentVariableOperation(final Type operation, final String name, final String value){
		this.type = operation;
		this.name = name;
		this.value = value;
	}
	
	public static final EnvironmentVariableOperation instanceOfPrint(final String name){
		return new EnvironmentVariableOperation(Type.PRINT, name, null);
	}
	
	public static final EnvironmentVariableOperation instanceOfUnset(final String name){
		return new EnvironmentVariableOperation(Type.UNSET, name, null);
	}
	
	public static final EnvironmentVariableOperation instanceOfSet(final String name, final String value){
		return new EnvironmentVariableOperation(Type.SET, name, value);
	}
	
	public static final EnvironmentVariableOperation instanceOfList(){
		return new EnvironmentVariableOperation(Type.LIST, null, null);
	}
	
	@Override
	public String getLabel(){
		return "EnvironmentVariableOperation";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("type");
		inline_field_values.add(String.valueOf(type));
		inline_field_names.add("name");
		inline_field_values.add(String.valueOf(name));
		inline_field_names.add("value");
		inline_field_values.add(String.valueOf(value));
	}
	
}
