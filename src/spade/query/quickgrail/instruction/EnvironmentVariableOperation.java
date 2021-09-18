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

import java.io.Serializable;
import java.util.ArrayList;

import spade.query.quickgrail.core.EnvironmentVariable;
import spade.query.quickgrail.core.EnvironmentVariableManager;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.utility.TreeStringSerializable;

public abstract class EnvironmentVariableOperation<R extends Serializable> extends Instruction<R>{

	public static class Set extends EnvironmentVariableOperation<String>{
		public final String name;
		public final String value;

		public Set(final String name, final String value){
			this.name = name;
			this.value = value;
		}

		@Override
		public String getLabel(){
			return "EnvironmentVariableOperation.Set";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("name");
			inline_field_values.add(String.valueOf(name));
			inline_field_names.add("value");
			inline_field_values.add(String.valueOf(value));
		}

		@Override
		public final String execute(final QueryInstructionExecutor executor){
			final EnvironmentVariable envVar = executor.getQueryEnvironment().getEnvVarManager().get(name);
			if(envVar == null){
				throw new RuntimeException("No environment variable defined by name: " + name);
			}
			envVar.setValue(value);
			return null;
		}
	}

	public static class Get extends EnvironmentVariableOperation<String>{
		public final String name;

		public Get(final String name){
			this.name = name;
		}

		@Override
		public String getLabel(){
			return "EnvironmentVariableOperation.Get";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("name");
			inline_field_values.add(String.valueOf(name));
		}

		@Override
		public final String execute(final QueryInstructionExecutor executor){
			final EnvironmentVariable envVar = executor.getQueryEnvironment().getEnvVarManager().get(name);
			if(envVar == null){
				throw new RuntimeException("No environment variable defined by name: " + name);
			}
			Object value = envVar.getValue();
			final String valueStr;
			if(value == null){
				valueStr = EnvironmentVariableManager.getUndefinedConstant(); // empty
			}else{
				valueStr = String.valueOf(value);
			}
			return valueStr;
		}
	}

	public static class Unset extends EnvironmentVariableOperation<String>{
		public final String name;

		public Unset(final String name){
			this.name = name;
		}

		@Override
		public String getLabel(){
			return "EnvironmentVariableOperation.Unset";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
			inline_field_names.add("name");
			inline_field_values.add(String.valueOf(name));
		}

		@Override
		public final String execute(final QueryInstructionExecutor executor){
			final EnvironmentVariable envVar = executor.getQueryEnvironment().getEnvVarManager().get(name);
			if(envVar == null){
				throw new RuntimeException("No environment variable defined by name: " + name);
			}
			envVar.unsetValue();
			return null;
		}
	}

	public static class List extends EnvironmentVariableOperation<spade.query.quickgrail.core.List.EnvironmentList>{
		@Override
		public String getLabel(){
			return "EnvironmentVariableOperation.List";
		}

		@Override
		protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
				ArrayList<String> non_container_child_field_names,
				ArrayList<TreeStringSerializable> non_container_child_fields,
				ArrayList<String> container_child_field_names,
				ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		}

		@Override
		public final spade.query.quickgrail.core.List.EnvironmentList execute(final QueryInstructionExecutor executor){
			return executor.listEnvironment();
		}
	}

}
