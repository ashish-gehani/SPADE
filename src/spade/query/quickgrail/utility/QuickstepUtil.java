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
package spade.query.quickgrail.utility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.storage.Quickstep;
import spade.storage.quickstep.QuickstepQueryEnvironment;

/**
 * Convenient functions.
 */
public class QuickstepUtil{
	private static Pattern tableNamePattern = Pattern.compile("([^ \n]+)[ |].*table.*");

	public static void CreateEmptyGraph(Quickstep qs, QuickstepQueryEnvironment qqe, Graph graph){
		String vertexTable = qqe.getGraphVertexTableName(graph);
		String edgeTable = qqe.getGraphEdgeTableName(graph);

		StringBuilder sb = new StringBuilder();
		sb.append("DROP TABLE " + vertexTable + ";\n");
		sb.append("DROP TABLE " + edgeTable + ";\n");
		sb.append("CREATE TABLE " + vertexTable + " (id INT) "
				+ "WITH BLOCKPROPERTIES (TYPE columnstore, SORT id, BLOCKSIZEMB 4);\n");
		sb.append("CREATE TABLE " + edgeTable + " (id LONG) "
				+ "WITH BLOCKPROPERTIES (TYPE columnstore, SORT id, BLOCKSIZEMB 4);\n");
		qs.executeQuery(sb.toString());
	}

	public static void CreateEmptyGraphMetadata(Quickstep qs, QuickstepQueryEnvironment qqe,
			GraphMetadata metadata){
		String vertexTable = qqe.getMetadataVertexTableName(metadata);
		String edgeTable = qqe.getMetadataEdgeTableName(metadata);

		StringBuilder sb = new StringBuilder();
		sb.append("DROP TABLE " + vertexTable + ";\n");
		sb.append("DROP TABLE " + edgeTable + ";\n");
		sb.append("CREATE TABLE " + vertexTable + " (id INT, name VARCHAR(64), value VARCHAR(256));");
		sb.append("CREATE TABLE " + edgeTable + " (id LONG, name VARCHAR(64), value VARCHAR(256));");
		qs.executeQuery(sb.toString());
	}

	public static ArrayList<String> GetAllTableNames(Quickstep qs){
		ArrayList<String> tableNames = new ArrayList<String>();
		String output = qs.executeQuery("\\d\n");
		Matcher matcher = tableNamePattern.matcher(output);
		while(matcher.find()){
			tableNames.add(matcher.group(1));
		}
		return tableNames;
	}

	public static long GetNumVertices(Quickstep qs, QuickstepQueryEnvironment qqe, Graph graph){
		return qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + qqe.getGraphVertexTableName(graph) + " TO stdout;");
	}

	public static long GetNumEdges(Quickstep qs, QuickstepQueryEnvironment qqe, Graph graph){
		return qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + qqe.getGraphEdgeTableName(graph) + " TO stdout;");
	}

	public static long GetNumTimestamps(Quickstep qs, QuickstepQueryEnvironment qqe, Graph graph){
		return qs.executeQueryForLongResult(
				"COPY SELECT COUNT(*) FROM edge_anno" + " WHERE id IN (SELECT id FROM " + qqe.getGraphEdgeTableName(graph) + ")" +
						" AND field = 'timestampNanos' TO stdout;");
	}

	public static Long[] GetTimestampRange(Quickstep qs, QuickstepQueryEnvironment qqe, Graph graph){
		// TODO(jianqiao): Fix the return type problem in Quickstep.
		if(GetNumTimestamps(qs, qqe, graph) == 0){
			return new Long[]{0L, 0L};
		}

		String span = qs.executeQuery(
				"COPY SELECT MIN(value), MAX(value) FROM edge_anno" + " WHERE id IN (SELECT id FROM " 
						+ qqe.getGraphEdgeTableName(graph) + ")" +
						" AND field = 'timestampNanos' TO stdout WITH (DELIMITER '|');");
		String[] timestamps = span.trim().split("\\|");
		return new Long[]{Long.parseLong(timestamps[0]), Long.parseLong(timestamps[1])};
	}

	public static String[] GetTimestampRangeString(Quickstep qs, QuickstepQueryEnvironment qqe, Graph graph){
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E, dd MMM yyyy HH:mm:ss z");
		Long[] span = QuickstepUtil.GetTimestampRange(qs, qqe, graph);
		String startDateStr = "";
		String endDateStr = "";
		if(span[0] != 0){
			final ZonedDateTime startDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(span[0] / 1000000),
					ZoneId.systemDefault());
			startDateStr = startDate.format(formatter);
		}
		if(span[1] != 0){
			final ZonedDateTime endDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(span[1] / 1000000),
					ZoneId.systemDefault());
			endDateStr = endDate.format(formatter);
		}
		return new String[]{startDateStr, endDateStr};
	}
}
