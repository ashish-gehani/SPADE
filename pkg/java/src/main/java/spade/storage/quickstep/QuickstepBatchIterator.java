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
package spade.storage.quickstep;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import spade.query.quickgrail.core.BatchIterator;
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.utility.ResultTable;
import spade.storage.Quickstep;

public class QuickstepBatchIterator extends BatchIterator{
	private final String resultTable;
	private final String batchTable;
	private final ElementType elementType;

	private final Quickstep storage;

	public QuickstepBatchIterator(final String resultTable, final int batchSize, final ElementType elementType,
			final Quickstep storage){
		super(batchSize);
		this.resultTable = resultTable;
		this.batchTable = "m_batch_" + resultTable;
		this.elementType = elementType;
		this.storage = storage;
	}

	@Override
	public String nextBatch(){
		// copy batchSize elements from result table into batch table
		String setupQuery = 
				"DROP TABLE " + batchTable + ";\n"
				+ "create table " + batchTable + "(id INT, value VARCHAR (%s));\n"
				+ "INSERT INTO " + batchTable + " SELECT * FROM " + resultTable + " ORDER BY id " + " LIMIT " + batchSize + ";\n";
		if(elementType.equals(ElementType.VERTEX)){
			setupQuery = String.format(setupQuery, storage.getMaxVertexValueLength());
		}else if(elementType.equals(ElementType.EDGE)){
			setupQuery = String.format(setupQuery, storage.getMaxEdgeValueLength());
		}
		storage.executeQuery(setupQuery);

		// return batchSize result
		final String result = storage
				.executeQuery("COPY SELECT * FROM " + batchTable + " TO STDOUT  WITH (DELIMITER ',');\n");

		// delete batch table rows from the result
		final String copyQuery = "COPY SELECT id FROM " + batchTable + " to stdout with (delimiter ',');\n";
		final String temp_result = storage.executeQuery(copyQuery);
		final ResultTable table = ResultTable.FromText(temp_result, ',');
		final List<Integer> list = new ArrayList<>();
		for(final ResultTable.Row row : table.getRows()){
			final Integer id = Integer.parseInt(row.getValue(0));
			list.add(id);
		}
		final String ids = StringUtils.join(list, ',');

		storage.executeQuery("DELETE FROM " + resultTable + " WHERE id IN (" + ids + ");\n");
		return result;
	}

	@Override
	public boolean hasNextBatch(){
		long numVertices = storage
				.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + resultTable + " TO stdout;");
		return numVertices > 0;
	}
}