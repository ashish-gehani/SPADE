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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Intermediate representation for a graph in QuickGrail optimizer.
 */
public class Graph extends Entity{
	
	public static enum Component{ kVertex, kEdge }

	// Each graph consists of two tables: <name>_vertex and <name>_edge.
	public final String name;

	private final LinkedHashSet<Remote> remotes = new LinkedHashSet<Remote>();

	public Graph(final String name){
		this.name = name;
		this.remotes.addAll(remotes);
	}

	public void addRemote(final Remote remote){
		remotes.add(remote);
	}

	public void removeRemote(final Remote remote){
		remotes.remove(remote);
	}

	public void clearRemotes(){
		remotes.clear();
	}

	public List<Remote> getRemotes(){
		return new ArrayList<>(remotes);
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
		inline_field_names.add("remotes");
		inline_field_values.add(remotes.toString());
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

	public final Map<SimpleEntry<String, Integer>, Set<Graph.Remote>> groupRemotesByConnections(){
		final Map<SimpleEntry<String, Integer>, Set<Graph.Remote>> grouped = new HashMap<>();
		for(final Graph.Remote remote : remotes){
			final SimpleEntry<String, Integer> sock = new SimpleEntry<String, Integer>(remote.host, remote.port);
			Set<Graph.Remote> list;
			if((list = grouped.get(sock)) == null){
				list = new LinkedHashSet<Graph.Remote>();
				grouped.put(sock, list);
			}
			list.add(remote);
		}
		return grouped;
	} 

	public final boolean containsRemote(final Graph.Remote remote){
		return remotes.contains(remote);
	}

	public static class Remote{
		public final String host;
		public final int port;
		public final String symbol;
		public Remote(String host, int port, String symbol){
			this.host = host;
			this.port = port;
			this.symbol = symbol;
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + ((host == null) ? 0 : host.hashCode());
			result = prime * result + port;
			result = prime * result + ((symbol == null) ? 0 : symbol.hashCode());
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
			Remote other = (Remote)obj;
			if(host == null){
				if(other.host != null)
					return false;
			}else if(!host.equals(other.host))
				return false;
			if(port != other.port)
				return false;
			if(symbol == null){
				if(other.symbol != null)
					return false;
			}else if(!symbol.equals(other.symbol))
				return false;
			return true;
		}
		@Override
		public String toString(){
			return "Remote [host=" + host + ", port=" + port + ", symbol=" + symbol + "]";
		}
		public String toFormattedString(){
			return "spade://" + host + ":" + port + "/" + symbol;
		}
	}
}
