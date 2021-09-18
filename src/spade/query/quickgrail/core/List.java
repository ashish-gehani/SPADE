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
package spade.query.quickgrail.core;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

import spade.query.quickgrail.types.LongType;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;

public abstract class List implements Serializable{

	private static final long serialVersionUID = -5258600526470561505L;

	protected abstract ResultTable getAsResultTable();

	@Override
	public String toString(){
		final ResultTable table = getAsResultTable();
		return table.toString();
	}

	public static class GraphList extends List{

		private static final long serialVersionUID = -2849540258881942113L;

		private final String baseSymbolName;

		private final Map<String, GraphStatistic.Count> map = new TreeMap<String, GraphStatistic.Count>();

		public GraphList(final String baseSymbolName){
			this.baseSymbolName = baseSymbolName;
		}

		public final void put(final String symbolName, final GraphStatistic.Count count){
			map.put(symbolName, count);
		}		

		private final void addRow(final ResultTable table, final String name){
			final GraphStatistic.Count count = map.get(name);
			final ResultTable.Row row = new ResultTable.Row();
			row.add(name);
			row.add(count.getVertices());
			row.add(count.getEdges());
			table.addRow(row);
		}

		@Override
		protected final ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();

			for(final String symbolName : map.keySet()){
				if(symbolName.equals(baseSymbolName)){
					continue; // print as the last one
				}
				addRow(table, symbolName);
			}

			// Add base
			addRow(table, baseSymbolName);

			final Schema schema = new Schema();
			schema.addColumn("Graph Name", StringType.GetInstance());
			schema.addColumn("Number of Vertices", LongType.GetInstance());
			schema.addColumn("Number of Edges", LongType.GetInstance());
			table.setSchema(schema);

			return table;
		}
	}

	public static class ConstraintList extends List{

		private static final long serialVersionUID = -2446226809295317984L;

		private final Map<String, PredicateNode> map = new TreeMap<>();

		public final void put(final String name, final PredicateNode constraint){
			map.put(name, constraint);
		}

		@Override
		protected final ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();
			for(final String name : map.keySet()){
				final PredicateNode constraint = map.get(name);
				final ResultTable.Row row = new ResultTable.Row();
				row.add(name);
				row.add(constraint.toString());
				table.addRow(row);
			}

			final Schema schema = new Schema();
			schema.addColumn("Constraint Name", StringType.GetInstance());
			schema.addColumn("Value", StringType.GetInstance());
			table.setSchema(schema);
			return table;
		}
	}

	public static class EnvironmentList extends List{

		private static final long serialVersionUID = -6517672001956558509L;

		private final Map<String, String> map = new TreeMap<>();

		public final void put(final String name, final String value){
			map.put(name, value);
		}

		@Override
		protected final ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();
			for(final String name : map.keySet()){
				final String value = map.get(name);
				final ResultTable.Row row = new ResultTable.Row();
				row.add(name);
				row.add(value);
				table.addRow(row);
			}
			final Schema schema = new Schema();
			schema.addColumn("Environment Variable Name", StringType.GetInstance());
			schema.addColumn("Value", StringType.GetInstance());
			table.setSchema(schema);
			return table;
		}
	}

	public static class AllList extends List{

		private static final long serialVersionUID = -3027246891186282965L;

		private final GraphList graphList;
		private final ConstraintList constraintList;
		private final EnvironmentList environmentList;

		public AllList(final GraphList graphList, final ConstraintList constraintList,
				final EnvironmentList environmentList){
			this.graphList = graphList;
			this.constraintList = constraintList;
			this.environmentList = environmentList;
		}

		@Override
		protected final ResultTable getAsResultTable(){
			return ResultTable.FromText("", ',');
		}

		@Override
		public String toString(){
			return graphList.toString() + System.lineSeparator() + constraintList.toString() + System.lineSeparator()
					+ environmentList.toString();
		}
	}
}
