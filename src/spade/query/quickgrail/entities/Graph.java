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
package spade.query.quickgrail.entities;

import java.util.ArrayList;

import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Intermediate representation for a graph in QuickGrail optimizer.
 */
public class Graph extends Entity{
	
	public static enum Component{ kVertex, kEdge }

	// Each graph consists of two tables: <name>_vertex and <name>_edge.
	public final String name;

	public Graph(String name){
		this.name = name;
	}

	@Override
	public EntityType getEntityType(){
		return EntityType.kGraph;
	}

	@Override
	public String getLabel(){
		return "Graph";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("name");
		inline_field_values.add(name);
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		Graph other = (Graph)obj;
		if(name == null){
			if(other.name != null)
				return false;
		}else if(!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "Graph [name=" + name + "]";
	}
}
