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
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;

public class GraphRemoteCount implements Serializable{

	private static final long serialVersionUID = -4628180807528689379L;

	public final String localName;

	public final Map<String, GraphStatistic.Count> remotes = new TreeMap<>();

	public GraphRemoteCount(final String localName){
		this.localName = localName;
	}

	public void put(final String remoteUri, final GraphStatistic.Count count){
		remotes.put(remoteUri, count);
	}

	public ResultTable getAsResultTable(){
		final ResultTable table = new ResultTable();
		for(final String remoteSymbolName : remotes.keySet()){
			final GraphStatistic.Count graphStats = remotes.get(remoteSymbolName);
			final ResultTable.Row row = new ResultTable.Row();
			row.add(remoteSymbolName);
			row.add(graphStats.getVertices());
			row.add(graphStats.getEdges());
			table.addRow(row);
		}

		final Schema schema = new Schema();
		schema.addColumn("Remote Graph Name", StringType.GetInstance());
		schema.addColumn("Number of Vertices", LongType.GetInstance());
		schema.addColumn("Number of Edges", LongType.GetInstance());
		table.setSchema(schema);

		return table;
	}

	@Override
	public String toString(){
		final ResultTable table = getAsResultTable();
		return table.toString();
	}
}
