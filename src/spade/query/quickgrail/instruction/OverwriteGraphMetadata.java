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

import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * This class is not yet used in the SPADE integrated QuickGrail.
 */
public class OverwriteGraphMetadata extends Instruction{
	public final GraphMetadata targetMetadata;
	public final GraphMetadata lhsMetadata;
	public final GraphMetadata rhsMetadata;

	public OverwriteGraphMetadata(GraphMetadata targetMetadata, GraphMetadata lhsMetadata, GraphMetadata rhsMetadata){
		this.targetMetadata = targetMetadata;
		this.lhsMetadata = lhsMetadata;
		this.rhsMetadata = rhsMetadata;
	}

	@Override
	public String getLabel(){
		return "OverwritehGraphMetadata";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetMetadata");
		inline_field_values.add(targetMetadata.name);
		inline_field_names.add("lhsMetadata");
		inline_field_values.add(lhsMetadata.name);
		inline_field_names.add("rhsMetadata");
		inline_field_values.add(rhsMetadata.name);
	}
}
