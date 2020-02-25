package spade.storage.quickstep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetPath;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.ListGraphs;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.SetGraphMetadata.Component;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.types.LongType;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.QuickstepUtil;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;

public class QuickstepInstructionExecutor extends QueryInstructionExecutor{

	private final QuickstepExecutor qs;
	private final QuickstepQueryEnvironment queryEnvironment;

	public QuickstepInstructionExecutor(QuickstepExecutor qs, QuickstepQueryEnvironment queryEnvironment){
		this.qs = qs;
		this.queryEnvironment = queryEnvironment;
		if(this.queryEnvironment == null){
			throw new IllegalArgumentException("NULL Query Environment");
		}
		if(this.qs == null){
			throw new IllegalArgumentException("NULL query executor");
		}
	}

	@Override
	public QueryEnvironment getQueryEnvironment(){
		return queryEnvironment;
	}

	@Override
	public void insertLiteralEdge(InsertLiteralEdge instruction){
		String prefix = "INSERT INTO " + queryEnvironment.getEdgeTableName(instruction.targetGraph) + " VALUES(";
		StringBuilder sqlQuery = new StringBuilder();
		for(String edge : instruction.getEdges()){
			sqlQuery.append(prefix + edge + ");\n");
		}
		qs.executeQuery(sqlQuery.toString());
	}

	@Override
	public void insertLiteralVertex(InsertLiteralVertex instruction){
		String prefix = "INSERT INTO " + queryEnvironment.getVertexTableName(instruction.targetGraph) + " VALUES(";
		StringBuilder sqlQuery = new StringBuilder();
		for(String vertex : instruction.getVertices()){
			sqlQuery.append(prefix + vertex + ");\n");
		}
		qs.executeQuery(sqlQuery.toString());
	}

	@Override
	public void createEmptyGraphMetadata(CreateEmptyGraphMetadata instruction){
		QuickstepUtil.CreateEmptyGraphMetadata(qs, queryEnvironment, instruction.metadata);
	}

	@Override
	public void overwriteGraphMetadata(OverwriteGraphMetadata instruction){
		String targetVertexTable = queryEnvironment.getMetadataVertexTableName(instruction.targetMetadata);
		String targetEdgeTable = queryEnvironment.getMetadataEdgeTableName(instruction.targetMetadata);
		String lhsVertexTable = queryEnvironment.getMetadataVertexTableName(instruction.lhsMetadata);
		String lhsEdgeTable = queryEnvironment.getMetadataEdgeTableName(instruction.lhsMetadata);
		String rhsVertexTable = queryEnvironment.getMetadataVertexTableName(instruction.rhsMetadata);
		String rhsEdgeTable = queryEnvironment.getMetadataEdgeTableName(instruction.rhsMetadata);

		qs.executeQuery("\\analyzerange " + rhsVertexTable + " " + rhsEdgeTable + "\n" + "INSERT INTO "
				+ targetVertexTable + " SELECT id, name, value FROM " + lhsVertexTable + " l"
				+ " WHERE NOT EXISTS (SELECT * FROM " + rhsVertexTable + " r"
				+ " WHERE l.id = r.id AND l.name = r.name);\n" + "INSERT INTO " + targetEdgeTable
				+ " SELECT id, name, value FROM " + lhsEdgeTable + " l" + " WHERE NOT EXISTS (SELECT * FROM "
				+ rhsEdgeTable + " r" + " WHERE l.id = r.id AND l.name = r.name);\n" + "INSERT INTO "
				+ targetVertexTable + " SELECT id, name, value FROM " + rhsVertexTable + ";\n" + "INSERT INTO "
				+ targetEdgeTable + " SELECT id, name, value FROM " + rhsEdgeTable + ";");
	}

	private static String FormatStringLiteral(String input){
		final String kDigits = "0123456789ABCDEF";
		StringBuilder sb = new StringBuilder();
		sb.append("e'");
		for(int i = 0; i < input.length(); ++i){
			char c = input.charAt(i);
			if(c >= 32){
				if(c == '\\' || c == '\''){
					sb.append(c);
				}
				sb.append(c);
				continue;
			}
			switch(c){
			case '\b':
				sb.append("\\b");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			default:
				// Use hexidecimal representation.
				sb.append("\\x");
				sb.append(kDigits.charAt(c >> 4));
				sb.append(kDigits.charAt(c & 0xF));
				break;
			}
		}
		sb.append("'");
		return sb.toString();
	}

	@Override
	public void setGraphMetadata(SetGraphMetadata instruction){
		String targetVertexTable = queryEnvironment.getMetadataVertexTableName(instruction.targetMetadata);
		String targetEdgeTable = queryEnvironment.getMetadataEdgeTableName(instruction.targetMetadata);
		String sourceVertexTable = queryEnvironment.getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = queryEnvironment.getEdgeTableName(instruction.sourceGraph);

		qs.executeQuery("\\analyzerange " + sourceVertexTable + " " + sourceEdgeTable + "\n");

		if(instruction.component == Component.kVertex || instruction.component == Component.kBoth){
			qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id, " + FormatStringLiteral(instruction.name)
					+ ", " + FormatStringLiteral(instruction.value) + " FROM " + sourceVertexTable + " GROUP BY id;");
		}

		if(instruction.component == Component.kEdge || instruction.component == Component.kBoth){
			qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT id, " + FormatStringLiteral(instruction.name)
					+ ", " + FormatStringLiteral(instruction.value) + " FROM " + sourceEdgeTable + " GROUP BY id;");
		}
	}

	@Override
	public void createEmptyGraph(CreateEmptyGraph instruction){
		QuickstepUtil.CreateEmptyGraph(qs, queryEnvironment, instruction.graph);
	}

	@Override
	public void distinctifyGraph(DistinctifyGraph instruction){
		String sourceVertexTable = queryEnvironment.getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = queryEnvironment.getEdgeTableName(instruction.sourceGraph);
		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);

		qs.executeQuery("\\analyzerange " + sourceVertexTable + " " + sourceEdgeTable + "\n");
		qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id FROM " + sourceVertexTable + " GROUP BY id;");
		qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT id FROM " + sourceEdgeTable + " GROUP BY id;");
	}

	@Override
	public void eraseSymbols(EraseSymbols instruction){
		for(String symbol : instruction.getSymbols()){
			queryEnvironment.eraseGraphSymbol(symbol);
			queryEnvironment.eraseGraphMetadataSymbol(symbol);
		}
	}

	@Override
	public void getVertex(GetVertex instruction){
		Graph.Component graphComponent = Graph.Component.kVertex;
		if(!instruction.hasArguments()){
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + queryEnvironment.getTableName(graphComponent, instruction.targetGraph)
					+ " SELECT id FROM " + queryEnvironment.GetBaseAnnotationTableName(graphComponent));
			if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
				String analyzeQuery = "\\analyzerange "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + "\n";
				evaluateQuery((new EvaluateQuery(analyzeQuery)));
				sqlQuery.append(" WHERE id IN (SELECT id FROM "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			evaluateQuery(new EvaluateQuery(sqlQuery.toString()));
		}else{
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + queryEnvironment.getTableName(graphComponent, instruction.targetGraph)
					+ " SELECT id FROM " + queryEnvironment.GetBaseAnnotationTableName(graphComponent) + " WHERE");
			if(!instruction.annotationKey.equals("*")){
				sqlQuery.append(" field = " + FormatStringLiteral(instruction.annotationKey) + " AND");
			} // TODO double check
			sqlQuery.append(" value " + instruction.operator + " " + FormatStringLiteral(instruction.annotationValue));
			if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
				String analyzeQuery = "\\analyzerange "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + "\n";
				evaluateQuery(new EvaluateQuery(analyzeQuery));
				sqlQuery.append(" AND id IN (SELECT id FROM "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			evaluateQuery(new EvaluateQuery(sqlQuery.toString()));
		}
	}

	@Override
	public ResultTable evaluateQuery(EvaluateQuery instruction){
		return null;//qs.executeQuery(instruction.nativeQuery); // TODO
	}

	@Override
	public void getEdge(GetEdge instruction){
		Graph.Component graphComponent = Graph.Component.kEdge;
		if(!instruction.hasArguments()){
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + queryEnvironment.getTableName(graphComponent, instruction.targetGraph)
					+ " SELECT id FROM " + queryEnvironment.GetBaseAnnotationTableName(graphComponent));
			if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
				String analyzeQuery = "\\analyzerange "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + "\n";
				evaluateQuery(new EvaluateQuery(analyzeQuery));
				sqlQuery.append(" WHERE id IN (SELECT id FROM "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			evaluateQuery(new EvaluateQuery(sqlQuery.toString()));
		}else{
			StringBuilder sqlQuery = new StringBuilder();
			sqlQuery.append("INSERT INTO " + queryEnvironment.getTableName(graphComponent, instruction.targetGraph)
					+ " SELECT id FROM " + queryEnvironment.GetBaseAnnotationTableName(graphComponent) + " WHERE");
			if(!instruction.annotationKey.equals("*")){
				sqlQuery.append(" field = " + FormatStringLiteral(instruction.annotationKey) + " AND");
			}
			sqlQuery.append(" value " + instruction.operator + " " + FormatStringLiteral(instruction.annotationValue));
			if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
				String analyzeQuery = "\\analyzerange "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + "\n";
				evaluateQuery(new EvaluateQuery(analyzeQuery));
				sqlQuery.append(" AND id IN (SELECT id FROM "
						+ queryEnvironment.getTableName(graphComponent, instruction.subjectGraph) + ")");
			}
			sqlQuery.append(" GROUP BY id;");
			evaluateQuery(new EvaluateQuery(sqlQuery.toString()));
		}
	}

	@Override
	public void getEdgeEndpoint(GetEdgeEndpoint instruction){
		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String subjectEdgeTable = queryEnvironment.getEdgeTableName(instruction.subjectGraph);
		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n" + "\\analyzerange "
				+ subjectEdgeTable + "\n");
		if(instruction.component == GetEdgeEndpoint.Component.kSource
				|| instruction.component == GetEdgeEndpoint.Component.kBoth){
			qs.executeQuery("INSERT INTO m_answer SELECT src FROM edge" + " WHERE id IN (SELECT id FROM "
					+ subjectEdgeTable + ");");
		}
		if(instruction.component == GetEdgeEndpoint.Component.kDestination
				|| instruction.component == GetEdgeEndpoint.Component.kBoth){
			qs.executeQuery("INSERT INTO m_answer SELECT dst FROM edge" + " WHERE id IN (SELECT id FROM "
					+ subjectEdgeTable + ");");
		}
		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable
				+ " SELECT id FROM m_answer GROUP BY id;\n" + "DROP TABLE m_answer;");
	}

	@Override
	public void intersectGraph(IntersectGraph instruction){
		String outputVertexTable = queryEnvironment.getVertexTableName(instruction.outputGraph);
		String outputEdgeTable = queryEnvironment.getEdgeTableName(instruction.outputGraph);
		String lhsVertexTable = queryEnvironment.getVertexTableName(instruction.lhsGraph);
		String lhsEdgeTable = queryEnvironment.getEdgeTableName(instruction.lhsGraph);
		String rhsVertexTable = queryEnvironment.getVertexTableName(instruction.rhsGraph);
		String rhsEdgeTable = queryEnvironment.getVertexTableName(instruction.rhsGraph);

		qs.executeQuery("\\analyzerange " + rhsVertexTable + " " + rhsEdgeTable + "\n");
		qs.executeQuery("INSERT INTO " + outputVertexTable + " SELECT id FROM " + lhsVertexTable
				+ " WHERE id IN (SELECT id FROM " + rhsVertexTable + ");");
		qs.executeQuery("INSERT INTO " + outputEdgeTable + " SELECT id FROM " + lhsEdgeTable
				+ " WHERE id IN (SELECT id FROM " + rhsEdgeTable + ");");
	}

	@Override
	public void limitGraph(LimitGraph instruction){
		String sourceVertexTable = queryEnvironment.getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = queryEnvironment.getEdgeTableName(instruction.sourceGraph);

		long numVertices = qs
				.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + sourceVertexTable + " TO stdout;");
		long numEdges = qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + sourceEdgeTable + " TO stdout;");

		if(numVertices > 0){
			qs.executeQuery("\\analyzerange " + sourceVertexTable + "\n" + "INSERT INTO "
					+ queryEnvironment.getVertexTableName(instruction.targetGraph) + " SELECT id FROM "
					+ sourceVertexTable + " GROUP BY id" + " ORDER BY id LIMIT " + instruction.limit + ";");

		}
		if(numEdges > 0){
			qs.executeQuery("\\analyzerange " + sourceEdgeTable + "\n" + "INSERT INTO "
					+ queryEnvironment.getVertexTableName(instruction.targetGraph) + " SELECT id FROM "
					+ sourceEdgeTable + " GROUP BY id" + " ORDER BY id LIMIT " + instruction.limit + ";");
		}
	}

	@Override
	public ResultTable listGraphs(ListGraphs instruction){
		ResultTable table = new ResultTable();

		Map<String, String> symbols = new HashMap<String, String>();//queryEnvironment.getSymbols();
		for(Map.Entry<String, String> entry : symbols.entrySet()){
			String symbol = entry.getKey();
			if(symbol.startsWith("$")){
				addSymbol(qs, symbol, new Graph(entry.getValue()), table, instruction.style);
			}
		}
		addSymbol(qs, "$base", queryEnvironment.kBaseGraph, table, instruction.style);

		Schema schema = new Schema();
		schema.addColumn("Graph Name", StringType.GetInstance());
		if(!instruction.style.equals("name")){
			schema.addColumn("Number of Vertices", LongType.GetInstance());
			schema.addColumn("Number of Edges", LongType.GetInstance());
			if(instruction.style.equals("detail")){
				schema.addColumn("Start Time", LongType.GetInstance());
				schema.addColumn("End Time", LongType.GetInstance());
			}
		}
		table.setSchema(schema);

		return table;
	}

	private void addSymbol(QuickstepExecutor qs, String symbol, Graph graph, ResultTable table, String style){
		ResultTable.Row row = new ResultTable.Row();
		row.add(symbol);
		if(!style.equals("name")){
			row.add(QuickstepUtil.GetNumVertices(qs, queryEnvironment, graph));
			row.add(QuickstepUtil.GetNumEdges(qs, queryEnvironment, graph));
			if(style.equals("detail")){
				Long[] span = QuickstepUtil.GetTimestampRange(qs, queryEnvironment, graph);
				row.add(span[0]);
				row.add(span[1]);
			}
		}
		table.addRow(row);
	}

	@Override
	public GraphStats statGraph(StatGraph instruction){
		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);
		long numVertices = qs
				.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetVertexTable + " TO stdout;");
		long numEdges = qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetEdgeTable + " TO stdout;");
		return new GraphStats(numVertices, numEdges);
	}

	@Override
	public void subtractGraph(SubtractGraph instruction){
		String outputVertexTable = queryEnvironment.getVertexTableName(instruction.outputGraph);
		String outputEdgeTable = queryEnvironment.getEdgeTableName(instruction.outputGraph);
		String minuendVertexTable = queryEnvironment.getVertexTableName(instruction.minuendGraph);
		String minuendEdgeTable = queryEnvironment.getEdgeTableName(instruction.minuendGraph);
		String subtrahendVertexTable = queryEnvironment.getVertexTableName(instruction.subtrahendGraph);
		String subtrahendEdgeTable = queryEnvironment.getEdgeTableName(instruction.subtrahendGraph);

		if(instruction.component == null || instruction.component == Graph.Component.kVertex){
			qs.executeQuery("\\analyzerange " + subtrahendVertexTable + "\n");
			qs.executeQuery("INSERT INTO " + outputVertexTable + " SELECT id FROM " + minuendVertexTable
					+ " WHERE id NOT IN (SELECT id FROM " + subtrahendVertexTable + ");");
		}
		if(instruction.component == null || instruction.component == Graph.Component.kEdge){
			qs.executeQuery("\\analyzerange " + subtrahendEdgeTable + "\n");
			qs.executeQuery("INSERT INTO " + outputEdgeTable + " SELECT id FROM " + minuendEdgeTable
					+ " WHERE id NOT IN (SELECT id FROM " + subtrahendEdgeTable + ");");
		}
	}

	@Override
	public void unionGraph(UnionGraph instruction){
		String sourceVertexTable = queryEnvironment.getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = queryEnvironment.getEdgeTableName(instruction.sourceGraph);
		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);

		qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id FROM " + sourceVertexTable + ";");
		qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT id FROM " + sourceEdgeTable + ";");
	}

	@Override
	public void getAdjacentVertex(GetAdjacentVertex instruction){
		// TODO Auto-generated method stub

	}

	private HashMap<Integer, AbstractVertex> exportVertices(QuickstepExecutor qs, String targetVertexTable){
		HashMap<Integer, AbstractVertex> vertices = new HashMap<Integer, AbstractVertex>();
		long numVertices = qs
				.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetVertexTable + " TO stdout;");
		if(numVertices == 0){
			return vertices;
		}

		qs.executeQuery("\\analyzerange " + targetVertexTable + "\n");

		String vertexAnnoStr = qs.executeQuery("COPY SELECT * FROM vertex_anno WHERE id IN (SELECT id FROM "
				+ targetVertexTable + ") TO stdout WITH (DELIMITER e'\\n');");
		String[] vertexAnnoLines = vertexAnnoStr.split("\n");
		vertexAnnoStr = null;

		assert vertexAnnoLines.length % 3 == 0;
		for(int i = 0; i < vertexAnnoLines.length; i += 3){
			// TODO: accelerate with cache.
			Integer id = Integer.parseInt(vertexAnnoLines[i]);
			AbstractVertex vertex = vertices.get(id);
			if(vertex == null){
				vertex = new spade.core.Vertex();
				vertices.put(id, vertex);
			}
			vertex.addAnnotation(vertexAnnoLines[i + 1], vertexAnnoLines[i + 2]);
		}
		return vertices;
	}

	private HashMap<Long, AbstractEdge> exportEdges(QuickstepExecutor qs, String targetEdgeTable){
		HashMap<Long, AbstractEdge> edges = new HashMap<Long, AbstractEdge>();

		long numEdges = qs.executeQueryForLongResult("COPY SELECT COUNT(*) FROM " + targetEdgeTable + " TO stdout;");
		if(numEdges == 0){
			return edges;
		}

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer(id INT);\n" + "DROP TABLE m_answer_edge;\n"
				+ "CREATE TABLE m_answer_edge(id LONG, src INT, dst INT);\n" + "\\analyzerange " + targetEdgeTable
				+ "\n" + "INSERT INTO m_answer_edge SELECT * FROM edge" + " WHERE id IN (SELECT id FROM "
				+ targetEdgeTable + ");\n" + "INSERT INTO m_answer SELECT src FROM m_answer_edge;\n"
				+ "INSERT INTO m_answer SELECT dst FROM m_answer_edge;");

		HashMap<Integer, AbstractVertex> vertices = exportVertices(qs, "m_answer");

		String edgeStr = qs.executeQuery("COPY SELECT * FROM m_answer_edge TO stdout WITH (DELIMITER e'\\n');");
		String[] edgeLines = edgeStr.split("\n");
		edgeStr = null;

		assert edgeLines.length % 3 == 0;
		for(int i = 0; i < edgeLines.length; i += 3){
			Long id = Long.parseLong(edgeLines[i]);
			Integer src = Integer.parseInt(edgeLines[i + 1]);
			Integer dst = Integer.parseInt(edgeLines[i + 2]);
			edges.put(id, new spade.core.Edge(vertices.get(src), vertices.get(dst)));
		}
		edgeLines = null;

		String edgeAnnoStr = qs.executeQuery("COPY SELECT * FROM edge_anno WHERE id IN (SELECT id FROM "
				+ targetEdgeTable + ") TO stdout WITH (DELIMITER e'\\n');");
		String[] edgeAnnoLines = edgeAnnoStr.split("\n");
		edgeAnnoStr = null;

		assert edgeAnnoLines.length % 3 == 0;
		for(int i = 0; i < edgeAnnoLines.length; i += 3){
			// TODO: accelerate with cache.
			Long id = Long.parseLong(edgeAnnoLines[i]);
			AbstractEdge edge = edges.get(id);
			if(edge == null){
				continue;
			}
			edge.addAnnotation(edgeAnnoLines[i + 1], edgeAnnoLines[i + 2]);
		}
		qs.executeQuery("DROP TABLE m_answer;\n" + "DROP TABLE m_answer_edge;");
		return edges;
	}

	@Override
	public spade.core.Graph exportGraph(ExportGraph instruction){
		qs.executeQuery("DROP TABLE m_init_vertex;\n" + "DROP TABLE m_vertex;\n" + "DROP TABLE m_edge;\n"
				+ "CREATE TABLE m_init_vertex(id INT);\n" + "CREATE TABLE m_vertex(id INT);\n"
				+ "CREATE TABLE m_edge(id LONG);");

		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);
		qs.executeQuery("INSERT INTO m_init_vertex" + " SELECT id FROM " + targetVertexTable + ";\n"
				+ "INSERT INTO m_init_vertex" + " SELECT src FROM edge WHERE id IN (SELECT id FROM " + targetEdgeTable
				+ ");\n" + "INSERT INTO m_init_vertex" + " SELECT dst FROM edge WHERE id IN (SELECt id FROM "
				+ targetEdgeTable + ");\n" + "\\analyzerange " + targetVertexTable + " " + targetEdgeTable + "\n"
				+ "INSERT INTO m_vertex SELECT id FROM m_init_vertex GROUP BY id;\n"
				+ "INSERT INTO m_edge SELECt id FROM " + targetEdgeTable + " GROUP BY id;");

		HashMap<Integer, AbstractVertex> vertices = exportVertices(qs, "m_vertex");
		HashMap<Long, AbstractEdge> edges = exportEdges(qs, "m_edge");

		qs.executeQuery("DROP TABLE m_init_vertex;\n" + "DROP TABLE m_vertex;\n" + "DROP TABLE m_edge;");

		spade.core.Graph graph = new spade.core.Graph();
		graph.vertexSet().addAll(vertices.values());
		graph.edgeSet().addAll(edges.values());
		return graph;
	}

	@Override
	public void getLink(GetLink instruction){
		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "CREATE TABLE m_next (id INT);\n"
				+ "CREATE TABLE m_answer (id INT);");

		String filter;
		if(queryEnvironment.isBaseGraph(instruction.subjectGraph)){
			filter = "";
		}else{
			filter = " AND edge.id IN (SELECT id FROM " + queryEnvironment.getEdgeTableName(instruction.subjectGraph)
					+ ")";
		}

		// Create subgraph edges table.
		qs.executeQuery("DROP TABLE m_sgconn;\n" + "CREATE TABLE m_sgconn (src INT, dst INT, depth INT);");

		qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + queryEnvironment.getVertexTableName(instruction.dstGraph)
				+ ";\n" + "INSERT INTO m_answer SELECT id FROM m_cur;\n" + "\\analyzerange edge\n");

		String loopStmts = "\\analyzerange m_cur\n" + "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge"
				+ " WHERE dst IN (SELECT id FROM m_cur)" + filter + ";\n"
				+ "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge" + " WHERE src IN (SELECT id FROM m_cur)"
				+ filter + ";\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id INT);\n"
				+ "INSERT INTO m_next SELECT src FROM edge" + " WHERE dst IN (SELECT id FROM m_cur)" + filter
				+ " GROUP BY src;\n" + "INSERT INTO m_next SELECT dst FROM edge"
				+ " WHERE src IN (SELECT id FROM m_cur)" + filter + " GROUP BY dst;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < instruction.maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i + 1)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_cur (id INT);\n"
				+ "CREATE TABLE m_next (id INT);");

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO m_cur SELECT id FROM "
				+ queryEnvironment.getVertexTableName(instruction.srcGraph)
				+ " WHERE id IN (SELECT id FROM m_answer);\n");

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;" + "\\analyzerange m_answer m_sgconn\n");

		loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id int);\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_next SELECT dst FROM m_sgconn" + " WHERE src IN (SELECT id FROM m_cur)"
				+ " AND depth + $depth <= " + String.valueOf(instruction.maxDepth) + " GROUP BY dst;\n"
				+ "INSERT INTO m_next SELECT src FROM m_sgconn" + " WHERE dst IN (SELECT id FROM m_cur)"
				+ " AND depth + $depth <= " + String.valueOf(instruction.maxDepth) + " GROUP BY src;\n"
				+ "DROP TABLE m_cur;\n" + "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < instruction.maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;\n"
				+ "INSERT INTO " + targetEdgeTable + " SELECT id FROM edge" + " WHERE src IN (SELECT id FROM m_answer)"
				+ " AND dst IN (SELECT id FROM m_answer)" + filter + ";");

		qs.executeQuery(
				"DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n" + "DROP TABLE m_sgconn;");
	}

	@Override
	public void getShortestPath(GetShortestPath instruction){
		String filter;
		qs.executeQuery("DROP TABLE m_conn;\n" + "CREATE TABLE m_conn (src INT, dst INT);");
		if(queryEnvironment.isBaseGraph(instruction.subjectGraph)){
			filter = "";
			qs.executeQuery(
					"\\analyzecount edge\n" + "INSERT INTO m_conn SELECT src, dst FROM edge GROUP BY src, dst;");
		}else{
			String subjectEdgeTable = queryEnvironment.getEdgeTableName(instruction.subjectGraph);
			filter = " AND edge.id IN (SELECT id FROM " + subjectEdgeTable + ")";
			qs.executeQuery("DROP TABLE m_sgedge;\n" + "CREATE TABLE m_sgedge (src INT, dst INT);\n" + "\\analyzerange "
					+ subjectEdgeTable + "\n" + "INSERT INTO m_sgedge SELECT src, dst FROM edge"
					+ " WHERE id IN (SELECT id FROM " + subjectEdgeTable + ");\n" + "\\analyzecount m_sgedge\n"
					+ "INSERT INTO m_conn SELECT src, dst FROM m_sgedge GROUP BY src, dst;\n" + "DROP TABLE m_sgedge;");
		}
		qs.executeQuery("\\analyze m_conn\n");

		// Create subgraph edges table.
		qs.executeQuery(
				"DROP TABLE m_sgconn;\n" + "CREATE TABLE m_sgconn (src INT, dst INT, reaching INT, depth INT);");

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "CREATE TABLE m_cur (id INT, reaching INT);\n" + "CREATE TABLE m_next (id INT, reaching INT);\n"
				+ "CREATE TABLE m_answer (id INT);");

		qs.executeQuery(
				"INSERT INTO m_cur SELECT id, id FROM " + queryEnvironment.getVertexTableName(instruction.dstGraph)
						+ ";\n" + "\\analyzerange m_cur\n" + "INSERT INTO m_answer SELECT id FROM m_cur GROUP BY id;");

		String loopStmts = "\\analyzecount m_cur\n" + "INSERT INTO m_sgconn SELECT src, dst, reaching, $depth"
				+ " FROM m_cur, m_conn WHERE id = dst;\n" + "DROP TABLE m_next;\n"
				+ "CREATE TABLE m_next (id INT, reaching INT);\n" + "INSERT INTO m_next SELECT src, reaching"
				+ " FROM m_cur, m_conn WHERE id = dst;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT, reaching INT);\n" + "\\analyzerange m_answer\n"
				+ "\\analyzecount m_next\n" + "INSERT INTO m_cur SELECT id, reaching FROM m_next"
				+ " WHERE id NOT IN (SELECT id FROM m_answer) GROUP BY id, reaching;\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur GROUP BY id;";
		for(int i = 0; i < instruction.maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i + 1)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_cur (id INT);\n"
				+ "CREATE TABLE m_next (id INT);");

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO m_cur SELECT id FROM "
				+ queryEnvironment.getVertexTableName(instruction.srcGraph)
				+ " WHERE id IN (SELECT id FROM m_answer);\n");

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;");

		qs.executeQuery("\\analyzerange m_answer\n" + "\\analyze m_sgconn\n");

		loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next(id INT);\n" + "INSERT INTO m_next SELECT MIN(dst)"
				+ " FROM m_cur, m_sgconn WHERE id = src" + " AND depth + $depth <= "
				+ String.valueOf(instruction.maxDepth) + " GROUP BY src, reaching;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur(id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < instruction.maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;\n"
				+ "INSERT INTO " + targetEdgeTable + " SELECT id FROM edge" + " WHERE src IN (SELECT id FROM m_answer)"
				+ " AND dst IN (SELECT id FROM m_answer)" + filter + ";");

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "DROP TABLE m_conn;\n" + "DROP TABLE m_sgconn;");
	}

	@Override
	public void getSubgraph(GetSubgraph instruction){
		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);
		String subjectVertexTable = queryEnvironment.getVertexTableName(instruction.subjectGraph);
		String subjectEdgeTable = queryEnvironment.getEdgeTableName(instruction.subjectGraph);
		String skeletonVertexTable = queryEnvironment.getVertexTableName(instruction.skeletonGraph);
		String skeletonEdgeTable = queryEnvironment.getEdgeTableName(instruction.skeletonGraph);

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);");

		// Get vertices.
		qs.executeQuery("\\analyzerange " + subjectVertexTable + "\n" + "INSERT INTO m_answer SELECT id FROM "
				+ skeletonVertexTable + " WHERE id IN (SELECT id FROM " + subjectVertexTable + ");\n"
				+ "INSERT INTO m_answer SELECT src FROM edge " + " WHERE id IN (SELECT id FROM " + skeletonEdgeTable
				+ ")" + " AND src IN (SELECT id FROM " + subjectVertexTable + ");\n"
				+ "INSERT INTO m_answer SELECT dst FROM edge" + " WHERE id IN (SELECT id FROM " + skeletonEdgeTable
				+ ")" + " AND dst IN (SELECT id FROM " + subjectVertexTable + ");\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer GROUP BY id;\n");

		// Get edges.
		qs.executeQuery(
				"\\analyzerange " + subjectEdgeTable + "\n" + "INSERT INTO " + targetEdgeTable + " SELECT s.id FROM "
						+ subjectEdgeTable + " s, edge e" + " WHERE s.id = e.id AND e.src IN (SELECT id FROM m_answer)"
						+ " AND e.dst IN (SELECT id FROM m_answer) GROUP BY s.id;");

		qs.executeQuery("DROP TABLE m_answer;");
	}

	private void executeOneDirection(Direction dir, QuickstepExecutor qs, String filter, GetLineage instruction){
		String src, dst;
		if(dir == Direction.kAncestor){
			src = "src";
			dst = "dst";
		}else{
			assert dir == Direction.kDescendant;
			src = "dst";
			dst = "src";
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "DROP TABLE m_answer_edge;\n" + "CREATE TABLE m_cur (id INT);\n" + "CREATE TABLE m_next (id INT);\n"
				+ "CREATE TABLE m_answer (id INT);\n" + "CREATE TABLE m_answer_edge (id LONG);");

		String startVertexTable = queryEnvironment.getVertexTableName(instruction.startGraph);
		qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + startVertexTable + ";\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;");

		String loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id INT);\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_next SELECT " + dst + " FROM edge" + " WHERE " + src + " IN (SELECT id FROM m_cur)"
				+ filter + " GROUP BY " + dst + ";\n" + "INSERT INTO m_answer_edge SELECT id FROM edge" + " WHERE "
				+ src + " IN (SELECT id FROM m_cur)" + filter + ";\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < instruction.depth; ++i){
			qs.executeQuery(loopStmts);

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}
	}

	public void getLineage(GetLineage instruction){
		ArrayList<Direction> oneDirs = new ArrayList<Direction>();
		if(instruction.direction == Direction.kBoth){
			oneDirs.add(Direction.kAncestor);
			oneDirs.add(Direction.kDescendant);
		}else{
			oneDirs.add(instruction.direction);
		}

		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);

		String subjectEdgeTable = queryEnvironment.getEdgeTableName(instruction.subjectGraph);
		String filter = "";
		if(!queryEnvironment.isBaseGraph(instruction.subjectGraph)){
			qs.executeQuery("\\analyzerange " + subjectEdgeTable + "\n");
			filter = " AND edge.id IN (SELECT id FROM " + subjectEdgeTable + ")";
		}

		for(Direction oneDir : oneDirs){
			executeOneDirection(oneDir, qs, filter, instruction);
			qs.executeQuery("\\analyzerange m_answer m_answer_edge\n" + "INSERT INTO " + targetVertexTable
					+ " SELECT id FROM m_answer;\n" + "INSERT INTO " + targetEdgeTable
					+ " SELECT id FROM m_answer_edge GROUP BY id;");
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "DROP TABLE m_answer_edge;");
	}

	public void getPath(GetPath instruction){
		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "CREATE TABLE m_next (id INT);\n"
				+ "CREATE TABLE m_answer (id INT);");

		String filter;
		if(queryEnvironment.isBaseGraph(instruction.subjectGraph)){
			filter = "";
		}else{
			filter = " AND edge.id IN (SELECT id FROM " + queryEnvironment.getEdgeTableName(instruction.subjectGraph) + ")";
		}

		// Create subgraph edges table.
		qs.executeQuery("DROP TABLE m_sgconn;\n" + "CREATE TABLE m_sgconn (src INT, dst INT, depth INT);");

		qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + queryEnvironment.getVertexTableName(instruction.dstGraph) + ";\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;\n" + "\\analyzerange edge\n");

		String loopStmts = "\\analyzerange m_cur\n" + "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge"
				+ " WHERE dst IN (SELECT id FROM m_cur)" + filter + ";\n" + "DROP TABLE m_next;\n"
				+ "CREATE TABLE m_next (id INT);\n" + "INSERT INTO m_next SELECT src FROM edge"
				+ " WHERE dst IN (SELECT id FROM m_cur)" + filter + " GROUP BY src;\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < instruction.maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i + 1)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "CREATE TABLE m_cur (id INT);\n"
				+ "CREATE TABLE m_next (id INT);");

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO m_cur SELECT id FROM "
				+ queryEnvironment.getVertexTableName(instruction.srcGraph) + " WHERE id IN (SELECT id FROM m_answer);\n");

		qs.executeQuery("DROP TABLE m_answer;\n" + "CREATE TABLE m_answer (id INT);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;" + "\\analyzerange m_answer m_sgconn\n");

		loopStmts = "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id int);\n" + "\\analyzerange m_cur\n"
				+ "INSERT INTO m_next SELECT dst FROM m_sgconn" + " WHERE src IN (SELECT id FROM m_cur)"
				+ " AND depth + $depth <= " + String.valueOf(instruction.maxDepth) + " GROUP BY dst;\n" + "\\analyzerange m_next\n"
				+ "INSERT INTO " + targetEdgeTable + " SELECT id FROM edge" + " WHERE src IN (SELECT id FROM m_cur)"
				+ " AND dst IN (SELECT id FROM m_next)" + filter + ";\n" + "DROP TABLE m_cur;\n"
				+ "CREATE TABLE m_cur (id INT);\n" + "\\analyzerange m_answer\n"
				+ "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n"
				+ "INSERT INTO m_answer SELECT id FROM m_cur;";
		for(int i = 0; i < instruction.maxDepth; ++i){
			qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i)));

			String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
			if(qs.executeQueryForLongResult(worksetSizeQuery) == 0){
				break;
			}
		}

		qs.executeQuery("\\analyzerange m_answer\n" + "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;");

		qs.executeQuery(
				"DROP TABLE m_cur;\n" + "DROP TABLE m_next;\n" + "DROP TABLE m_answer;\n" + "DROP TABLE m_sgconn;");
	}

	public void collapseEdge(CollapseEdge instruction){
		String sourceVertexTable = queryEnvironment.getVertexTableName(instruction.sourceGraph);
		String sourceEdgeTable = queryEnvironment.getEdgeTableName(instruction.sourceGraph);
		String targetVertexTable = queryEnvironment.getVertexTableName(instruction.targetGraph);
		String targetEdgeTable = queryEnvironment.getEdgeTableName(instruction.targetGraph);

		qs.executeQuery("\\analyzerange " + sourceVertexTable + " " + sourceEdgeTable + "\n");
		qs.executeQuery("INSERT INTO " + targetVertexTable + " SELECT id FROM " + sourceVertexTable + ";");

		StringBuilder tables = new StringBuilder();
		StringBuilder predicates = new StringBuilder();
		StringBuilder groups = new StringBuilder();

		for(int i = 0; i < instruction.getFields().size(); ++i){
			String edgeAnnoName = "ea" + i;
			tables.append(", edge_anno " + edgeAnnoName);
			predicates.append(" AND e.id = " + edgeAnnoName + ".id" + " AND " + edgeAnnoName + ".field = '"
					+ instruction.getFields().get(i) + "'");
			groups.append(", " + edgeAnnoName + ".value");
		}

		qs.executeQuery("INSERT INTO " + targetEdgeTable + " SELECT MIN(e.id) FROM edge e" + tables.toString()
		+ " WHERE e.id IN (SELECT id FROM " + sourceEdgeTable + ")" + predicates.toString()
		+ " GROUP BY src, dst" + groups.toString() + ";");
	}
}
