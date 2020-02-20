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
package spade.query.quickgrail.kernel;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.query.quickgrail.entities.Entity;
import spade.query.quickgrail.entities.EntityType;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.execution.CollapseEdge;
import spade.query.quickgrail.execution.CreateEmptyGraph;
import spade.query.quickgrail.execution.CreateEmptyGraphMetadata;
import spade.query.quickgrail.execution.DistinctifyGraph;
import spade.query.quickgrail.execution.EraseSymbols;
import spade.query.quickgrail.execution.EvaluateQuery;
import spade.query.quickgrail.execution.ExportGraph;
import spade.query.quickgrail.execution.GetEdgeEndpoint;
import spade.query.quickgrail.execution.GetLineage;
import spade.query.quickgrail.execution.GetLink;
import spade.query.quickgrail.execution.GetPath;
import spade.query.quickgrail.execution.GetShortestPath;
import spade.query.quickgrail.execution.GetSubgraph;
import spade.query.quickgrail.execution.InsertLiteralEdge;
import spade.query.quickgrail.execution.InsertLiteralVertex;
import spade.query.quickgrail.execution.Instruction;
import spade.query.quickgrail.execution.IntersectGraph;
import spade.query.quickgrail.execution.LimitGraph;
import spade.query.quickgrail.execution.ListGraphs;
import spade.query.quickgrail.execution.OverwriteGraphMetadata;
import spade.query.quickgrail.execution.SetGraphMetadata;
import spade.query.quickgrail.execution.StatGraph;
import spade.query.quickgrail.execution.SubtractGraph;
import spade.query.quickgrail.execution.UnionGraph;
import spade.query.quickgrail.parser.ParseAssignment;
import spade.query.quickgrail.parser.ParseCommand;
import spade.query.quickgrail.parser.ParseExpression;
import spade.query.quickgrail.parser.ParseExpression.ExpressionType;
import spade.query.quickgrail.parser.ParseLiteral;
import spade.query.quickgrail.parser.ParseName;
import spade.query.quickgrail.parser.ParseOperation;
import spade.query.quickgrail.parser.ParseProgram;
import spade.query.quickgrail.parser.ParseStatement;
import spade.query.quickgrail.parser.ParseString;
import spade.query.quickgrail.parser.ParseVariable;
import spade.query.quickgrail.types.Type;
import spade.query.quickgrail.types.TypeID;
import spade.query.quickgrail.types.TypedValue;

/**
 * Resolver that transforms a parse tree into a QuickGrail low-level program.
 */
public class Resolver {
  private ArrayList<Instruction> instructions;
  private Environment env;

  class ExpressionStream {
    private ArrayList<ParseExpression> stream;
    private int position = 0;

    public ExpressionStream(ArrayList<ParseExpression> stream) {
      this.stream = stream;
      this.position = 0;
    }

    public boolean hasNext() {
      return position < stream.size();
    }

    public ParseExpression getNextExpression() {
      if (!hasNext()) {
        throw new RuntimeException("Require more arguments");
      }
      return stream.get(position++);
    }

    public String tryGetNextNameAsString() {
      if (!hasNext()) {
        return null;
      }
      ParseExpression expression = stream.get(position);
      if (expression.getExpressionType() != ExpressionType.kName) {
        return null;
      }
      ++position;
      return ((ParseName)expression).getName().getValue();
    }

    public String getNextString() {
      if (!hasNext()) {
        throw new RuntimeException("Require more arguments");
      }
      return resolveString(stream.get(position++));
    }

    public String getNextNameOrString() {
      if (!hasNext()) {
        throw new RuntimeException("Require more arguments");
      }
      ParseExpression expression = stream.get(position++);
      switch (expression.getExpressionType()) {
        case kName:
          return resolveNameAsString(expression);
        case kLiteral:
          return resolveString(expression);
        default:
          break;
      }
      throw new RuntimeException("Expected name or string literal");
    }
  }

  /**
   * Top-level API for resolving parse trees (that represent a list of
   * QuickGrail queries) into a low-level program (a list of primitive
   * instructions ready to be executed).
   */
  public Program resolveProgram(ParseProgram parseProgram,
                                Environment env) {
    // Initialize
    this.instructions = new ArrayList<Instruction>();
    this.env = env;

    // Resolve statements.
    for (ParseStatement parseStatement : parseProgram.getStatements()) {
      resolveStatement(parseStatement);
    }

    Program program = new Program(instructions, env);

    // Cleanup and return.
    this.instructions = null;
    this.env = null;
    return program;
  }

  private void resolveStatement(ParseStatement parseStatement) {
    switch (parseStatement.getStatementType()) {
      case kAssignment:
        resolveAssignment((ParseAssignment)parseStatement);
        break;
      case kCommand:
        resolveCommand((ParseCommand)parseStatement);
        break;
      default:
        throw new RuntimeException(
            "Unsupported statement type: " +
            parseStatement.getStatementType().name());
    }
  }

  private void resolveAssignment(ParseAssignment parseAssignment) {
    Type varType = parseAssignment.getLhs().getType();
    switch (varType.getTypeID()) {
      case kGraph:
        resolveGraphAssignment(parseAssignment);
        break;
      case kGraphMetadata:
        resolveGraphMetadataAssignment(parseAssignment);
        break;
      default:
        throw new RuntimeException(
            "Unsupported variable type " + varType.getName() + " at " +
            parseAssignment.getLhs().getLocationString());
    }
  }

  private void resolveGraphAssignment(ParseAssignment parseAssignment) {
    assert parseAssignment.getLhs().getType().getTypeID() == TypeID.kGraph;
    ParseString var = parseAssignment.getLhs().getName();
    ParseExpression rhs = parseAssignment.getRhs();
    ParseAssignment.AssignmentType atype = parseAssignment.getAssignmentType();

    Graph resultGraph;
    if (atype == ParseAssignment.AssignmentType.kEqual) {
      resultGraph = resolveGraphExpression(rhs, null, true);
    } else {
      String lhsGraphName = env.lookup(var.getValue());
      if (lhsGraphName == null) {
        throw new RuntimeException(
            "Cannot resolve Graph variable " + var.getValue() +
            " at " + var.getLocationString());
      }
      Graph lhsGraph = new Graph(lhsGraphName);
      switch (atype) {
        case kPlusEqual: {
          if (!Environment.IsBaseGraph(lhsGraph)) {
            resultGraph = lhsGraph;
          } else {
            resultGraph = allocateEmptyGraph();
            instructions.add(new UnionGraph(resultGraph, lhsGraph));
          }
          resolveGraphExpression(rhs, resultGraph, true);
          break;
        }
        case kMinusEqual: {
          Graph rhsGraph = resolveGraphExpression(rhs, null, true);
          resultGraph = allocateEmptyGraph();
          instructions.add(new SubtractGraph(resultGraph, lhsGraph, rhsGraph, null));
          break;
        }
        case kIntersectEqual: {
          Graph rhsGraph = resolveGraphExpression(rhs, null, true);
          resultGraph = allocateEmptyGraph();
          instructions.add(new IntersectGraph(resultGraph, lhsGraph, rhsGraph));
          break;
        }
        default:
          throw new RuntimeException(
              "Unsupported assignment " +
              parseAssignment.getAssignmentType().name().substring(1) +
              " at " + parseAssignment.getLocationString());
      }
    }
    Graph distinctifiedGraph = allocateEmptyGraph();
    instructions.add(new DistinctifyGraph(distinctifiedGraph, resultGraph));
    env.setValue(var.getValue(), distinctifiedGraph.getName());
  }

  private void resolveGraphMetadataAssignment(ParseAssignment parseAssignment) {
    assert parseAssignment.getLhs().getType().getTypeID() == TypeID.kGraphMetadata;
    ParseString var = parseAssignment.getLhs().getName();
    ParseExpression rhs = parseAssignment.getRhs();
    ParseAssignment.AssignmentType atype = parseAssignment.getAssignmentType();

    GraphMetadata resultMetadata;
    if (atype == ParseAssignment.AssignmentType.kEqual) {
      resultMetadata = resolveGraphMetadataExpression(rhs, null);
    } else {
      String lhsMetadataName = env.lookup(var.getValue());
      if (lhsMetadataName == null) {
        throw new RuntimeException(
            "Cannot resolve GraphMetadata variable " + var.getValue() +
            " at " + var.getLocationString());
      }
      GraphMetadata lhsGraph = new GraphMetadata(lhsMetadataName);
      switch (atype) {
      case kPlusEqual: {
        resultMetadata = resolveGraphMetadataExpression(rhs, lhsGraph);
        break;
      }
      default:
        throw new RuntimeException(
            "Unsupported assignment " +
            parseAssignment.getAssignmentType().name().substring(1) +
            " at " + parseAssignment.getLocationString());
      }
    }
    env.setValue(var.getValue(), resultMetadata.getName());
  }

  private void resolveCommand(ParseCommand parseCommand) {
    ParseString cmdName = parseCommand.getCommandName();
    ArrayList<ParseExpression> arguments = parseCommand.getArguments();
    switch (cmdName.getValue().toLowerCase()) {
      case "dump":
        resolveDumpCommand(arguments);
        break;
      case "visualize":
        resolveVisualizeCommand(arguments);
        break;
      case "stat":
        resolveStatCommand(arguments);
        break;
      case "list":
        resolveListCommand(arguments);
        break;
      case "reset":
        resolveResetCommand(arguments);
        break;
      case "erase":
        resolveEraseCommand(arguments);
        break;
      default:
        throw new RuntimeException(
            "Unsupported command \"" + cmdName.getValue() +
            "\" at " + cmdName.getLocationString());
    }
  }

  private void resolveDumpCommand(ArrayList<ParseExpression> arguments) {
    if (arguments.isEmpty()) {
      throw new RuntimeException(
          "Invalid number of arguments for dump: expected at least 1");
    }

    boolean force = false;
    int idx = 0;
    ParseExpression expression = arguments.get(idx);
    if (expression.getExpressionType() == ParseExpression.ExpressionType.kName) {
      String forceStr = ((ParseName)expression).getName().getValue();
      if (forceStr.equalsIgnoreCase("force")) {
        force = true;
      } else {
        throw new RuntimeException(
            "Invalid argument for dump: " + forceStr);

      }
      if (++idx >= arguments.size()) {
        throw new RuntimeException(
            "Invalid arguments for dump: expected 1 graph argument");
      }
      expression = arguments.get(idx);
    }

    Graph targetGraph = resolveGraphExpression(expression, null, true);
    instructions.add(new ExportGraph(targetGraph, ExportGraph.Format.kNormal, force));
  }

  private void resolveVisualizeCommand(ArrayList<ParseExpression> arguments) {
    if (arguments.isEmpty()) {
      throw new RuntimeException(
          "Invalid number of arguments for visualize: expected at least 1");
    }

    boolean force = false;
    int idx = 0;
    ParseExpression expression = arguments.get(idx);
    if (expression.getExpressionType() == ParseExpression.ExpressionType.kName) {
      String forceStr = ((ParseName)expression).getName().getValue();
      if (forceStr.equalsIgnoreCase("force")) {
        force = true;
      } else {
        throw new RuntimeException(
            "Invalid argument for visualize: " + forceStr);
      }
      expression = arguments.get(++idx);
    }

    Graph targetGraph = resolveGraphExpression(expression, null, true);
    instructions.add(new ExportGraph(targetGraph, ExportGraph.Format.kDot, force));
  }

  private void resolveStatCommand(ArrayList<ParseExpression> arguments) {
    if (arguments.size() != 1) {
      throw new RuntimeException(
          "Invalid number of arguments for stat: expected 1");
    }

    Graph targetGraph = resolveGraphExpression(arguments.get(0), null, true);
    Graph distinctifiedGraph = allocateEmptyGraph();
    instructions.add(new DistinctifyGraph(distinctifiedGraph, targetGraph));
    instructions.add(new StatGraph(distinctifiedGraph));
  }

  private void resolveListCommand(ArrayList<ParseExpression> arguments) {
    ExpressionStream stream = new ExpressionStream(arguments);
    String style = stream.tryGetNextNameAsString();
    if (style == null) {
      style = "standard";
    }
    instructions.add(new ListGraphs(style));
  }

  private void resolveEraseCommand(ArrayList<ParseExpression> arguments) {
    ArrayList<String> symbols = new ArrayList<String>();
    for (ParseExpression argument : arguments) {
      if (argument.getExpressionType() != ExpressionType.kVariable) {
        throw new RuntimeException("Invalid arguments: expected variables");
      }
      ParseVariable var = (ParseVariable)argument;
      symbols.add(var.getName().getValue());
    }
    instructions.add(new EraseSymbols(symbols));
  }

  private void resolveResetCommand(ArrayList<ParseExpression> arguments) {
    if (arguments.size() == 1) {
      String target = resolveNameAsString(arguments.get(0));
      if (target.equals("workspace")) {
        env.clear();
      }
    }
  }

  private Entity resolveExpression(ParseExpression parseExpression,
                                   Entity outputEntity,
                                   boolean isConstReference) {
    switch (parseExpression.getExpressionType()) {
      case kOperation:
        return resolveOperation((ParseOperation)parseExpression, outputEntity);
      case kVariable:
        return resolveVariable((ParseVariable)parseExpression, outputEntity, isConstReference);
      default:
        break;
    }
    throw new RuntimeException(
        "Unsupported expression type: " + parseExpression.getExpressionType().name());
  }

  private Graph resolveGraphExpression(ParseExpression parseExpression,
                                       Graph outputGraph,
                                       boolean isConstReference) {
    return ToGraph(resolveExpression(parseExpression, outputGraph, isConstReference));
  }

  private GraphMetadata resolveGraphMetadataExpression(ParseExpression parseExpression,
                                                       GraphMetadata outputMetadata) {
    return ToGraphMetadata(resolveExpression(parseExpression, outputMetadata, true));
  }

  private Entity resolveOperation(ParseOperation parseOperation,
                                  Entity outputEntity) {
    ParseExpression parseSubject = parseOperation.getSubject();
    ParseString op = parseOperation.getOperator();
    ArrayList<ParseExpression> operands = parseOperation.getOperands();
    if (parseSubject != null) {
      Entity subject = resolveExpression(parseSubject, null, true);
      switch (subject.getEntityType()) {
        case kGraph:
          return resolveGraphMethod(ToGraph(subject), op, operands, outputEntity);
        case kGraphMetadata:
          return resolveGraphMetadataMethod(ToGraphMetadata(subject), op.getValue(), operands);
        default:
          throw new RuntimeException(
              "Invalid subject type " +
              subject.getEntityType().name().substring(1) + " at " +
              parseSubject.getLocationString());
      }
    }

    // Pure functions.
    switch (op.getValue()) {
      case "+": {
        assert operands.size() == 2;
        Entity lhsEntity = resolveExpression(operands.get(0), outputEntity, false);
        return resolveExpression(operands.get(1), lhsEntity, true);
      }
      case "&":  // Fall through
      case "-": {
        assert operands.size() == 2;
        return resolveGraphBinaryOperation(op, operands.get(0), operands.get(1), ToGraph(outputEntity));
      }
      case "vertices":
        return resolveInsertLiteralVertex(operands, ToGraph(outputEntity));
      case "edges":
        return resolveInsertLiteralEdge(operands, ToGraph(outputEntity));
      case "asVertex":
        return resolveAsVertexOrEdge(Graph.Component.kVertex, operands, ToGraph(outputEntity));
      case "asEdge":
        return resolveAsVertexOrEdge(Graph.Component.kEdge, operands, ToGraph(outputEntity));
      default:
        break;
    }
    throw new RuntimeException(
        "Unsupported operation " + op.getValue() +
        " at " + op.getLocationString());
  }

  private Graph resolveGraphBinaryOperation(ParseString op,
                                            ParseExpression lhs,
                                            ParseExpression rhs,
                                            Graph outputGraph) {
    Graph lhsGraph = resolveGraphExpression(lhs, null, true);
    Graph rhsGraph = resolveGraphExpression(rhs, null, true);

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    switch (op.getValue()) {
      case "&":
        instructions.add(new IntersectGraph(outputGraph, lhsGraph, rhsGraph));
        break;
      case "-":
        instructions.add(new SubtractGraph(outputGraph, lhsGraph, rhsGraph, null));
        break;
      default:
        throw new RuntimeException(
            "Unsupported graph binary operator " + op.getValue() +
            " at " + op.getLocationString());
    }
    return outputGraph;
  }

  private Entity resolveGraphMethod(Graph subject,
                                    ParseString methodName,
                                    ArrayList<ParseExpression> arguments,
                                    Entity outputEntity) {
    switch (methodName.getValue()) {
      case "getVertex":
        return resolveGetVertexOrEdge(Graph.Component.kVertex, subject, arguments, ToGraph(outputEntity));
      case "getEdge":
        return resolveGetVertexOrEdge(Graph.Component.kEdge, subject, arguments, ToGraph(outputEntity));
      case "getEdgeWithEndpoints": {
        Graph edges = resolveGetVertexOrEdge(Graph.Component.kEdge, subject, arguments, ToGraph(outputEntity));
        Graph outputGraph = (Graph) outputEntity;
        if (outputGraph == null) {
          outputGraph = allocateEmptyGraph();
        }
        instructions.add(new UnionGraph(outputGraph, edges));
        instructions.add(new GetEdgeEndpoint(outputGraph, edges, GetEdgeEndpoint.Component.kBoth));
        return outputGraph;
      }
      case "getLineage":
        return resolveGetLineage(subject, arguments, ToGraph(outputEntity));
      case "getLink":
        return resolveGetLink(subject, arguments, ToGraph(outputEntity));
      case "getPath":
        return resolveGetPath(subject, arguments, ToGraph(outputEntity));
      case "getShortestPath":
        return resolveGetShortestPath(subject, arguments, ToGraph(outputEntity));
      case "getSubgraph":
        return resolveGetSubgraph(subject, arguments, ToGraph(outputEntity));
      case "getEdgeSource":
        return resolveGetEdgeEndpoint(GetEdgeEndpoint.Component.kSource,
                                      subject, arguments, ToGraph(outputEntity));
      case "getEdgeDestination":
        return resolveGetEdgeEndpoint(GetEdgeEndpoint.Component.kDestination,
                                      subject, arguments, ToGraph(outputEntity));
      case "getEdgeEndpoints":
        return resolveGetEdgeEndpoint(GetEdgeEndpoint.Component.kBoth,
                                      subject, arguments, ToGraph(outputEntity));
      case "collapseEdge":
        return resolveCollapseEdge(subject, arguments, ToGraph(outputEntity));
      case "attr":
        return resolveSetMetadata(SetGraphMetadata.Component.kBoth,
                                  subject, arguments, ToGraphMetadata(outputEntity));
      case "attrVertex":
        return resolveSetMetadata(SetGraphMetadata.Component.kVertex,
                                  subject, arguments, ToGraphMetadata(outputEntity));
      case "attrEdge":
        return resolveSetMetadata(SetGraphMetadata.Component.kEdge,
                                  subject, arguments, ToGraphMetadata(outputEntity));
      case "span":
        return resolveSpan(subject, arguments, ToGraph(outputEntity));
      case "limit":
        return resolveLimit(subject, arguments, ToGraph(outputEntity));
      default:
        break;
    }
    throw new RuntimeException(
        "Unsupported Graph method " + methodName.getValue() +
        " at " + methodName.getLocationString());
  }

  private Entity resolveGraphMetadataMethod(GraphMetadata subject,
                                            String methodName,
                                            ArrayList<ParseExpression> arguments) {
    throw new RuntimeException("No GraphMetadata method is supported yet");
  }

  public Entity resolveVariable(ParseVariable var,
                                Entity outputEntity,
                                boolean isConstReference) {
    switch (var.getType().getTypeID()) {
      case kGraph:
        return resolveGraphVariable(var, ToGraph(outputEntity), isConstReference);
      case kGraphMetadata:
        return resolveGraphMetadataVariable(var, ToGraphMetadata(outputEntity));
      default:
        break;
    }
    throw new RuntimeException(
        "Unsupported variable type " + var.getType().getName() +
        " at " + var.getLocationString());
  }

  private Graph resolveGraphVariable(ParseVariable var,
                                     Graph outputGraph,
                                     boolean isConstReference) {
    assert var.getType().getTypeID() == TypeID.kGraph;
    String varGraph = env.lookup(var.getName().getValue());
    if (varGraph == null) {
      throw new RuntimeException(
          "Cannot resolve Graph variable " + var.getName().getValue() +
          " at " + var.getLocationString());
    }
    if (outputGraph == null) {
      if (isConstReference) {
        return new Graph(varGraph);
      }
      outputGraph = allocateEmptyGraph();
    }
    instructions.add(new UnionGraph(outputGraph, new Graph(varGraph)));
    return outputGraph;
  }

  private GraphMetadata resolveGraphMetadataVariable(ParseVariable var,
                                                     GraphMetadata lhsMetadata) {
    assert var.getType().getTypeID() == TypeID.kGraphMetadata;
    String varGraphMetadata = env.lookup(var.getName().getValue());
    if (varGraphMetadata == null) {
      throw new RuntimeException(
          "Cannot resolve GraphMetadata variable " + var.getName().getValue() +
          " at " + var.getLocationString());
    }

    GraphMetadata rhsMetadata = new GraphMetadata(varGraphMetadata);
    if (lhsMetadata == null) {
      return rhsMetadata;
    }

    GraphMetadata outputMetadata = allocateEmptyGraphMetadata();
    instructions.add(new OverwriteGraphMetadata(outputMetadata, lhsMetadata, rhsMetadata));
    return outputMetadata;
  }

  private Graph resolveInsertLiteralVertex(ArrayList<ParseExpression> operands,
                                           Graph outputGraph) {
    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }
    ArrayList<String> vertices = new ArrayList<String>();
    for (ParseExpression e : operands) {
      if (e.getExpressionType() != ParseExpression.ExpressionType.kLiteral) {
        throw new RuntimeException(
            "Invalid argument at " + e.getLocationString() + ": expected integer literal");
      }
      TypedValue value = ((ParseLiteral)e).getLiteralValue();
      if (value.getType().getTypeID() != TypeID.kInteger) {
        throw new RuntimeException(
            "Invalid argument type at " + e.getLocationString() + ": expected integer");
      }
      vertices.add(String.valueOf(value.getValue()));
    }
    instructions.add(new InsertLiteralVertex(outputGraph, vertices));
    return outputGraph;
  }

  private Graph resolveInsertLiteralEdge(ArrayList<ParseExpression> operands, Graph outputGraph) {
    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }
    ArrayList<String> edges = new ArrayList<String>();
    for (ParseExpression e : operands) {
      if (e.getExpressionType() != ParseExpression.ExpressionType.kLiteral) {
        throw new RuntimeException(
            "Invalid argument at " + e.getLocationString() + ": expected integer literal");
      }
      TypedValue value = ((ParseLiteral) e).getLiteralValue();
      if (value.getType().getTypeID() != TypeID.kInteger) {
        throw new RuntimeException(
            "Invalid argument type at " + e.getLocationString() + ": expected integer");
      }
      edges.add(String.valueOf(value.getValue()));
    }
    instructions.add(new InsertLiteralEdge(outputGraph, edges));
    return outputGraph;
  }

  private Graph resolveGetVertexOrEdge(Graph.Component component,
                                       Graph subjectGraph,
                                       ArrayList<ParseExpression> arguments,
                                       Graph outputGraph) {
    if (arguments.size() > 1) {
      throw new RuntimeException(
          "Invalid number of arguments for getVertex/getEdge: expected 0 or 1");
    }

    if (arguments.isEmpty()) {
      // Get all the vertices.
      if (outputGraph == null) {
        outputGraph = allocateEmptyGraph();
      }

      StringBuilder sqlQuery = new StringBuilder();
      sqlQuery.append("INSERT INTO " + outputGraph.getTableName(component) +
                      " SELECT id FROM "+ Graph.GetBaseAnnotationTableName(component));
      if (!Environment.IsBaseGraph(subjectGraph)) {
        String analyzeQuery =
            "\\analyzerange " + subjectGraph.getTableName(component) + "\n";
        instructions.add(new EvaluateQuery(analyzeQuery));
        sqlQuery.append(" WHERE id IN (SELECT id FROM " +
                        subjectGraph.getTableName(component) + ")");
      }
      sqlQuery.append(" GROUP BY id;");
      instructions.add(new EvaluateQuery(sqlQuery.toString()));
      return outputGraph;
    } else {
      return resolveGetVertexOrEdgePredicate(component, subjectGraph, arguments.get(0), outputGraph);
    }
  }

  private Graph resolveGetVertexOrEdgePredicate(Graph.Component component,
                                                Graph subjectGraph,
                                                ParseExpression expression,
                                                Graph outputGraph) {
    if (expression.getExpressionType() != ParseExpression.ExpressionType.kOperation) {
      throw new RuntimeException(
          "Unexpected expression at " + expression.getLocationString());
    }
    ParseOperation predicate = (ParseOperation)expression;
    ParseString op = predicate.getOperator();
    ArrayList<ParseExpression> operands = predicate.getOperands();
    switch (op.getValue().toLowerCase()) {
      case "or": {
        assert operands.size() == 2;
        for (int i = 0; i < 2; ++i) {
          outputGraph = resolveGetVertexOrEdgePredicate(
              component, subjectGraph, operands.get(i),  outputGraph);
        }
        return outputGraph;
      }
      case "and": {
        assert operands.size() == 2;
        Graph lhsGraph =
            resolveGetVertexOrEdgePredicate(component, subjectGraph, operands.get(0), null);
        Graph rhsGraph =
            resolveGetVertexOrEdgePredicate(component, lhsGraph, operands.get(1), outputGraph);
        return rhsGraph;
      }
      case "not":
        assert operands.size() == 1;
        Graph subtrahendGraph =
            resolveGetVertexOrEdgePredicate(component, subjectGraph, operands.get(0), null);
        if (outputGraph == null) {
          outputGraph = allocateEmptyGraph();
        }
        instructions.add(new SubtractGraph(outputGraph, subjectGraph, subtrahendGraph, component));
        return outputGraph;
      default:
        break;
    }
    return resolveGetVertexOrEdgeComparison(component, subjectGraph, op, operands, outputGraph);
  }


  private Graph resolveGetVertexOrEdgeComparison(Graph.Component component,
                                                 Graph subjectGraph,
                                                 ParseString comparator,
                                                 ArrayList<ParseExpression> operands,
                                                 Graph outputGraph) {
    final String cp = comparator.getValue().toLowerCase();
    String op;
    switch (cp) {
      case "=":
      case "==":
        op = "=";
        break;
      case "<>":
      case "!=":
        op = "<>";
        break;
      case "<":
      case "<=":
      case ">":
      case ">=":
        op = cp;
        break;
      case "like":
        op = "LIKE";
        break;
      case "~":
      case "regexp":
        op = "REGEXP";
        break;
      default:
        throw new RuntimeException(
            "Unexpected comparator " + comparator.getValue() +
            " at " + comparator.getLocationString());
    }

    if (operands.size() != 2) {
      throw new RuntimeException(
          "Invalid number of operands at " +
          comparator.getLocationString() + ": expected 2");
    }

    ParseExpression lhs = operands.get(0);
    ParseExpression rhs = operands.get(1);
    if (lhs.getExpressionType() != ParseExpression.ExpressionType.kName) {
      throw new RuntimeException("Unexpected operand at " + lhs.getLocationString());
    }
    if (rhs.getExpressionType() != ParseExpression.ExpressionType.kLiteral) {
      throw new RuntimeException("Unexpected operand at " + rhs.getLocationString());
    }
    String field = ((ParseName)lhs).getName().getValue();
    TypedValue literal = ((ParseLiteral)rhs).getLiteralValue();
    String value = literal.getType().printValueToString(literal.getValue());

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    StringBuilder sqlQuery = new StringBuilder();
    sqlQuery.append("INSERT INTO " + outputGraph.getTableName(component) +
                    " SELECT id FROM " + Graph.GetBaseAnnotationTableName(component) + " WHERE");
    if (!field.equals("*")) {
      sqlQuery.append(" field = " + formatString(field) + " AND");
    }
    sqlQuery.append(" value " + op + " " + formatString(value));
    if (!Environment.IsBaseGraph(subjectGraph)) {
      String analyzeQuery =
          "\\analyzerange " + subjectGraph.getTableName(component) + "\n";
      instructions.add(new EvaluateQuery(analyzeQuery));
      sqlQuery.append(" AND id IN (SELECT id FROM " +
                      subjectGraph.getTableName(component) + ")");
    }
    sqlQuery.append(" GROUP BY id;");
    instructions.add(new EvaluateQuery(sqlQuery.toString()));

    return outputGraph;
  }

  private Graph resolveGetLineage(Graph subjectGraph,
                                  ArrayList<ParseExpression> arguments,
                                  Graph outputGraph) {
    if (arguments.size() != 3) {
      throw new RuntimeException(
          "Invalid number of arguments for getLineage: expected 3");
    }

    Graph startGraph = resolveGraphExpression(arguments.get(0), null, true);
    Integer depth = resolveInteger(arguments.get(1));

    String dirStr = resolveString(arguments.get(2));
    GetLineage.Direction direction;
    if (dirStr.startsWith("a")) {
      direction = GetLineage.Direction.kAncestor;
    } else if (dirStr.startsWith("d")) {
      direction = GetLineage.Direction.kDescendant;
    } else {
      direction = GetLineage.Direction.kBoth;
    }

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    instructions.add(new GetLineage(outputGraph, subjectGraph, startGraph, depth, direction));
    return outputGraph;
  }

  private Graph resolveGetLink(Graph subjectGraph,
                               ArrayList<ParseExpression> arguments,
                               Graph outputGraph) {
    if (arguments.size() != 3) {
      throw new RuntimeException(
          "Invalid number of arguments for getLink: expected 3");
    }

    Graph srcGraph = resolveGraphExpression(arguments.get(0), null, true);
    Graph dstGraph = resolveGraphExpression(arguments.get(1), null, true);
    Integer maxDepth = resolveInteger(arguments.get(2));

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    instructions.add(new GetLink(outputGraph, subjectGraph, srcGraph, dstGraph, maxDepth));
    return outputGraph;
  }

  private Graph resolveGetPath(Graph subjectGraph,
                               ArrayList<ParseExpression> arguments,
                               Graph outputGraph) {
    if (arguments.size() != 3) {
      throw new RuntimeException(
          "Invalid number of arguments for getPath: expected 3");
    }

    Graph srcGraph = resolveGraphExpression(arguments.get(0), null, true);
    Graph dstGraph = resolveGraphExpression(arguments.get(1), null, true);
    Integer maxDepth = resolveInteger(arguments.get(2));

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    instructions.add(new GetPath(outputGraph, subjectGraph, srcGraph, dstGraph, maxDepth));
    return outputGraph;
  }

  private Graph resolveCollapseEdge(Graph subjectGraph,
                                    ArrayList<ParseExpression> arguments,
                                    Graph outputGraph) {
    ArrayList<String> fields = new ArrayList<String>();
    for (ParseExpression e : arguments) {
      fields.add(resolveString(e));
    }

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    instructions.add(new CollapseEdge(outputGraph, subjectGraph, fields));
    return outputGraph;
  }

  private Graph resolveGetSubgraph(Graph subjectGraph,
                                   ArrayList<ParseExpression> arguments,
                                   Graph outputGraph) {
    if (arguments.size() != 1) {
      throw new RuntimeException(
          "Invalid number of arguments for getSubgraph: expected 1");
    }

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    Graph skeletonGraph = resolveGraphExpression(arguments.get(0), null, true);
    instructions.add(new GetSubgraph(outputGraph, subjectGraph, skeletonGraph));
    return outputGraph;
  }

  private Graph resolveGetEdgeEndpoint(GetEdgeEndpoint.Component component,
                                       Graph subjectGraph,
                                       ArrayList<ParseExpression> arguments,
                                       Graph outputGraph) {
    if (!arguments.isEmpty()) {
      throw new RuntimeException(
          "Invalid number of arguments at " +
          arguments.get(0).getLocationString() + ": expected 0");
    }

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    instructions.add(new GetEdgeEndpoint(outputGraph, subjectGraph, component));
    return outputGraph;
  }

  private GraphMetadata resolveSetMetadata(SetGraphMetadata.Component component,
                                           Graph subjectGraph,
                                           ArrayList<ParseExpression> arguments,
                                           GraphMetadata outputMetadata) {
    if (arguments.size() != 2) {
      throw new RuntimeException(
          "Invalid number of arguments for attr: expected 2");
    }

    String name = resolveString(arguments.get(0));
    String value = resolveString(arguments.get(1));

    GraphMetadata metadata = allocateEmptyGraphMetadata();
    instructions.add(new SetGraphMetadata(metadata, component, subjectGraph, name, value));

    if (outputMetadata == null) {
      return metadata;
    } else {
      GraphMetadata combinedMetadata = allocateEmptyGraphMetadata();
      instructions.add(new OverwriteGraphMetadata(combinedMetadata, outputMetadata, metadata));
      return combinedMetadata;
    }
  }

  private Graph resolveSpan(Graph subjectGraph,
                            ArrayList<ParseExpression> arguments,
                            Graph outputGraph) {
    if (arguments.size() != 1) {
      throw new RuntimeException(
          "Invalid number of arguments for span: expected 1");
    }

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    Graph sourceGraph = resolveGraphExpression(arguments.get(0), null, true);
    instructions.add(new GetSubgraph(outputGraph, sourceGraph, subjectGraph));
    return outputGraph;
  }

  private Graph resolveLimit(Graph subjectGraph,
                             ArrayList<ParseExpression> arguments,
                             Graph outputGraph) {
    if (arguments.size() != 1) {
      throw new RuntimeException(
          "Invalid number of arguments for limit: expected 1");
    }

    Integer limit = resolveInteger(arguments.get(0));

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    instructions.add(new LimitGraph(outputGraph, subjectGraph, limit));
    return outputGraph;
  }

  private Graph resolveGetShortestPath(Graph subjectGraph,
                                       ArrayList<ParseExpression> arguments,
                                       Graph outputGraph) {
    if (arguments.size() != 3) {
      throw new RuntimeException(
          "Invalid number of arguments for getPath: expected 3");
    }

    Graph srcGraph = resolveGraphExpression(arguments.get(0), null, true);
    Graph dstGraph = resolveGraphExpression(arguments.get(1), null, true);
    Integer maxDepth = resolveInteger(arguments.get(2));

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    instructions.add(new GetShortestPath(outputGraph, subjectGraph, srcGraph, dstGraph, maxDepth));
    return outputGraph;
  }

  private Graph resolveAsVertexOrEdge(Graph.Component component,
                                      ArrayList<ParseExpression> arguments,
                                      Graph outputGraph) {
    // TODO: handle subject?
    if (arguments.size() != 1) {
      throw new RuntimeException(
          "Invalid number of arguments for asVertex/asEdge: expected 1");
    }

    if (outputGraph == null) {
      outputGraph = allocateEmptyGraph();
    }

    String rawQuery = resolveString(arguments.get(0));
    StringBuffer sb = new StringBuffer();
    sb.append("INSERT INTO " + outputGraph.getTableName(component) + " ");

    Pattern pattern = Pattern.compile("[$][^.]+[.](vertex|edge)");
    Matcher m = pattern.matcher(rawQuery);
    while (m.find()) {
      String ref = m.group();
      String var;
      Graph.Component refComponent;
      if (ref.endsWith(".vertex")) {
        refComponent = Graph.Component.kVertex;
        var = ref.substring(0, ref.length() - 7);
      } else {
        assert ref.endsWith(".edge");
        refComponent = Graph.Component.kEdge;
        var = ref.substring(0, ref.length() - 5);
      }
      String graphName = env.lookup(var);
      if (graphName == null) {
        throw new RuntimeException(
            "Cannot resolve variable " + var + " in the query at " +
            arguments.get(0).getLocationString());
      }
      m.appendReplacement(sb, new Graph(graphName).getTableName(refComponent));
    }
    m.appendTail(sb);

    instructions.add(new EvaluateQuery(sb.toString()));
    return outputGraph;
  }

  private Integer resolveInteger(ParseExpression expression) {
    if (expression.getExpressionType() != ParseExpression.ExpressionType.kLiteral) {
      throw new RuntimeException(
          "Invalid value at " + expression.getLocationString() +
          ": expected integer literal");
    }
    TypedValue value = ((ParseLiteral)expression).getLiteralValue();
    if (value.getType().getTypeID() != TypeID.kInteger) {
      throw new RuntimeException(
          "Invalid value type at " + expression.getLocationString() +
          ": expected integer");
    }
    return (Integer)value.getValue();
  }

  private String resolveString(ParseExpression expression) {
    if (expression.getExpressionType() != ParseExpression.ExpressionType.kLiteral) {
      throw new RuntimeException(
          "Invalid value at " + expression.getLocationString() +
          ": expected string literal");
    }
    TypedValue value = ((ParseLiteral)expression).getLiteralValue();
    if (value.getType().getTypeID() != TypeID.kString) {
      throw new RuntimeException(
          "Invalid value type at " + expression.getLocationString() +
          ": expected string");
    }
    return (String)value.getValue();
  }

  private String resolveNameAsString(ParseExpression expression) {
    if (expression.getExpressionType() != ParseExpression.ExpressionType.kName) {
      throw new RuntimeException(
          "Invalid value at " + expression.getLocationString() +
          ": expected name");
    }
    return ((ParseName)expression).getName().getValue();
  }

  private Graph allocateEmptyGraph() {
    Graph graph = env.allocateGraph();
    instructions.add(new CreateEmptyGraph(graph));
    return graph;
  }

  private GraphMetadata allocateEmptyGraphMetadata() {
    GraphMetadata metadata = env.allocateGraphMetadata();
    instructions.add(new CreateEmptyGraphMetadata(metadata));
    return metadata;
  }

  private static Graph ToGraph(Entity entity) {
    if (entity == null) {
      return null;
    }
    if (entity.getEntityType() != EntityType.kGraph) {
      throw new RuntimeException(
          "Invalid casting from an instance of " +
          entity.getEntityType().name().substring(1) + " to Graph");
    }
    return (Graph)entity;
  }

  private static GraphMetadata ToGraphMetadata(Entity entity) {
    if (entity == null) {
      return null;
    }
    if (entity.getEntityType() != EntityType.kGraphMetadata) {
      throw new RuntimeException(
          "Invalid casting from an instance of " +
          entity.getEntityType().name().substring(1) + " to Graph");
    }
    return (GraphMetadata)entity;
  }

  private String formatString(String str) {
    StringBuilder sb = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      if (c < 32) {
        switch (c) {
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
          default:
            sb.append("\\x" + Integer.toHexString(c));
            break;
        }
        escaped = true;
      } else {
        if (c == '\\') {
          sb.append('\\');
          escaped = true;
        }
        sb.append(c);
      }
    }
    return (escaped ? "e" : "") + "'" + sb.toString() + "'";
  }
}
