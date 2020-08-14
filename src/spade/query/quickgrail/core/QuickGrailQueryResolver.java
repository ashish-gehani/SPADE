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
package spade.query.quickgrail.core;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;

import spade.query.quickgrail.entities.Entity;
import spade.query.quickgrail.entities.EntityType;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphPredicate;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.DescribeGraph;
import spade.query.quickgrail.instruction.DescribeGraph.DescriptionType;
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EnvironmentVariableOperation;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetMatch;
import spade.query.quickgrail.instruction.GetPath;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSimplePath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.Instruction;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.List;
import spade.query.quickgrail.instruction.List.ListType;
import spade.query.quickgrail.instruction.PrintPredicate;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
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
import spade.query.quickgrail.utility.QuickGrailPredicateTree;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * Resolver that transforms a parse tree into a QuickGrail low-level program.
 */
public class QuickGrailQueryResolver{

	public static enum PredicateOperator{
		EQUAL,
		GREATER,
		GREATER_EQUAL,
		LESSER,
		LESSER_EQUAL,
		NOT_EQUAL,
		LIKE,
		REGEX
	}
	
	private ArrayList<Instruction> instructions;
	private AbstractQueryEnvironment env;

	class ExpressionStream{
		private ArrayList<ParseExpression> stream;
		private int position = 0;

		public ExpressionStream(ArrayList<ParseExpression> stream){
			this.stream = stream;
			this.position = 0;
		}

		public boolean hasNext(){
			return position < stream.size();
		}

		public ParseExpression getNextExpression(){
			if(!hasNext()){
				throw new RuntimeException("Require more arguments");
			}
			return stream.get(position++);
		}

		public String tryGetNextNameAsString(){
			if(!hasNext()){
				return null;
			}
			ParseExpression expression = stream.get(position);
			if(expression.getExpressionType() != ExpressionType.kName){
				return null;
			}
			++position;
			return ((ParseName)expression).getName().getValue();
		}

		public String getNextString(){
			if(!hasNext()){
				throw new RuntimeException("Require more arguments");
			}
			return resolveString(stream.get(position++));
		}

		public String getNextNameOrString(){
			if(!hasNext()){
				throw new RuntimeException("Require more arguments");
			}
			ParseExpression expression = stream.get(position++);
			switch(expression.getExpressionType()){
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
	 * Top-level API for resolving parse trees (that represent a list of QuickGrail
	 * queries) into a low-level program (a list of primitive instructions ready to
	 * be executed).
	 */
	public Program resolveProgram(ParseProgram parseProgram, AbstractQueryEnvironment env){
		// Initialize
		this.instructions = new ArrayList<Instruction>();
		this.env = env;

		// Resolve statements.
		for(ParseStatement parseStatement : parseProgram.getStatements()){
			resolveStatement(parseStatement);
		}

		Program program = new Program(this.instructions);

		// Cleanup and return.
		this.instructions = null;
		this.env = null;
		return program;
	}

	private void resolveStatement(ParseStatement parseStatement){
		switch(parseStatement.getStatementType()){
		case kAssignment:
			resolveAssignment((ParseAssignment)parseStatement);
			break;
		case kCommand:
			resolveCommand((ParseCommand)parseStatement);
			break;
		default:
			throw new RuntimeException("Unsupported statement type: " + parseStatement.getStatementType().name());
		}
	}

	private void resolveAssignment(ParseAssignment parseAssignment){
		Type varType = parseAssignment.getLhs().getType();
		switch(varType.getTypeID()){
		case kGraph:
			resolveGraphAssignment(parseAssignment);
			break;
		case kGraphMetadata:
			throw new RuntimeException("Graph metadata queries not supported yet");
//			resolveGraphMetadataAssignment(parseAssignment); break;
		case kGraphPredicate:
			resolveGraphPredicateAssignment(parseAssignment);
			break;
		default:
			throw new RuntimeException("Unsupported variable type " + varType.getName() + " at "
					+ parseAssignment.getLhs().getLocationString());
		}
	}

	private void resolveGraphPredicateAssignment(ParseAssignment parseAssignment){
		if(parseAssignment.getLhs().getType().getTypeID() != TypeID.kGraphPredicate){
			throw new RuntimeException("Unexpected LHS type '" + parseAssignment.getLhs().getType().getTypeID() + "'. "
					+ "Expected '" + TypeID.kGraphPredicate + "'");
		}

		if(parseAssignment.getAssignmentType() == ParseAssignment.AssignmentType.kEqual){
			ParseString var = parseAssignment.getLhs().getName();
			PredicateNode predicateNode = QuickGrailPredicateTree.resolveGraphPredicate(parseAssignment.getRhs(), env);
			if(predicateNode == null){
				throw new RuntimeException("Failed to resolve predicate");
			}else{
				env.setPredicateSymbol(var.getValue(), new GraphPredicate(predicateNode));
			}
		}else{
			throw new RuntimeException(
					"Unsupported predicate assignment " + parseAssignment.getAssignmentType().name().substring(1) + " at "
							+ parseAssignment.getLocationString());
		}
	}

	private void resolveGraphAssignment(ParseAssignment parseAssignment){
		if(parseAssignment.getLhs().getType().getTypeID() != TypeID.kGraph){
			throw new RuntimeException("Unexpected LHS type '" + parseAssignment.getLhs().getType().getTypeID() + "'. "
					+ "Expected '" + TypeID.kGraph + "'");
		}

		ParseString var = parseAssignment.getLhs().getName();
		ParseExpression rhs = parseAssignment.getRhs();
		ParseAssignment.AssignmentType atype = parseAssignment.getAssignmentType();

		Graph resultGraph;
		if(atype == ParseAssignment.AssignmentType.kEqual){
			resultGraph = resolveGraphExpression(rhs, null, true);
		}else{
			Graph lhsGraph = env.getGraphSymbol(var.getValue());
			if(lhsGraph == null){
				throw new RuntimeException(
						"Cannot resolve Graph variable " + var.getValue() + " at " + var.getLocationString());
			}
			switch(atype){
			case kPlusEqual:{
				if(!env.isBaseGraph(lhsGraph)){
					resultGraph = lhsGraph;
				}else{
					resultGraph = allocateEmptyGraph();
					instructions.add(new UnionGraph(resultGraph, lhsGraph));
				}
				resolveGraphExpression(rhs, resultGraph, true);
				break;
			}
			case kMinusEqual:{
				Graph rhsGraph = resolveGraphExpression(rhs, null, true);
				resultGraph = allocateEmptyGraph();
				instructions.add(new SubtractGraph(resultGraph, lhsGraph, rhsGraph, null));
				break;
			}
			case kIntersectEqual:{
				Graph rhsGraph = resolveGraphExpression(rhs, null, true);
				resultGraph = allocateEmptyGraph();
				instructions.add(new IntersectGraph(resultGraph, lhsGraph, rhsGraph));
				break;
			}
			default:
				throw new RuntimeException(
						"Unsupported assignment " + parseAssignment.getAssignmentType().name().substring(1) + " at "
								+ parseAssignment.getLocationString());
			}
		}
		Graph distinctifiedGraph = allocateEmptyGraph();
		instructions.add(new DistinctifyGraph(distinctifiedGraph, resultGraph));
		env.setGraphSymbol(var.getValue(), distinctifiedGraph);
	}

	private void resolveCommand(ParseCommand parseCommand){
		ParseString cmdName = parseCommand.getCommandName();
		ArrayList<ParseExpression> arguments = parseCommand.getArguments();
		switch(cmdName.getValue().toLowerCase()){
		case "dump":
			resolveDumpCommand(arguments);
			break;
		case "visualize":
			resolveVisualizeCommand(arguments);
			break;
		case "stat":
			resolveStatCommand(arguments);
			break;
		case "describe":
			resolveDescribeCommand(arguments);
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
		case "native":
			resolveNativeCommand(arguments);
			break;
		case "env":
			resolveEnvCommand(arguments);
			break;
		default:
			throw new RuntimeException(
					"Unsupported command \"" + cmdName.getValue() + "\" at " + cmdName.getLocationString());
		}
	}

	private void resolveDumpCommand(final ArrayList<ParseExpression> arguments){
		if(arguments.isEmpty()){
			throw new RuntimeException("Invalid number of arguments for dump: expected at least 1");
		}

		boolean force = false;
		int idx = 0;
		ParseExpression expression = arguments.get(idx);
		if(expression.getExpressionType() == ParseExpression.ExpressionType.kName){
			String forceStr = ((ParseName)expression).getName().getValue();
			if(forceStr.equalsIgnoreCase("force")){
				force = true;
			}else{
				throw new RuntimeException("Invalid argument for dump: " + forceStr);

			}
			if(++idx >= arguments.size()){
				throw new RuntimeException("Invalid arguments for dump: expected 1 graph argument");
			}
			expression = arguments.get(idx);
		}

		if(QuickGrailPredicateTree.isGraphPredicateExpression(expression)){
			PredicateNode predicateNode = QuickGrailPredicateTree.resolveGraphPredicate(expression, env);
			instructions.add(new PrintPredicate(predicateNode));	
		}else{ // Must be graph expression if not graph predicate
			resolveGraphExportCommand(arguments);
		}
	}

	private void resolveVisualizeCommand(ArrayList<ParseExpression> arguments){
		resolveGraphExportCommand(arguments);
	}
	
	private void resolveGraphExportCommand(final ArrayList<ParseExpression> arguments){
		if(arguments.isEmpty()){
			throw new RuntimeException("Invalid number of arguments for dump/visualize: expected at least 1");
		}

		boolean force = false;
		int idx = 0;
		ParseExpression expression = arguments.get(idx);
		if(expression.getExpressionType() == ParseExpression.ExpressionType.kName){
			String forceStr = ((ParseName)expression).getName().getValue();
			if(forceStr.equalsIgnoreCase("force")){
				force = true;
			}else{
				throw new RuntimeException("Invalid argument for dump/visualize: " + forceStr);

			}
			if(++idx >= arguments.size()){
				throw new RuntimeException("Invalid arguments for dump/visualize: expected 1 graph argument");
			}
			expression = arguments.get(idx);
		}

		final Graph targetGraph = resolveGraphExpression(expression, null, true);
		
		ExportGraph.Format outputFormat = ExportGraph.Format.kNormal;
		
		String filePathOnServer = null;
		idx++;
		if(idx < arguments.size()){
			filePathOnServer = resolveString(arguments.get(idx)).trim();
			try{
				FileUtility.pathMustBeAWritableFile(filePathOnServer);
			}catch(Exception e){
				throw new RuntimeException("Invalid server file path for dump/visualize command to export graph to: '"+filePathOnServer+"'", e);
			}
			
			if(filePathOnServer.toLowerCase().endsWith(".json")){
				outputFormat = ExportGraph.Format.kJson;
			}else{
				outputFormat = ExportGraph.Format.kDot;
			}
		}
		
		instructions.add(new ExportGraph(targetGraph, outputFormat, force, filePathOnServer));
	}

	private void resolveStatCommand(ArrayList<ParseExpression> arguments){
		if(arguments.size() != 1){
			throw new RuntimeException("Invalid number of arguments for stat: expected 1");
		}

		boolean isConstVariable = false;
		
		final ParseExpression parseExpression = arguments.get(0);
		
		if(parseExpression.getExpressionType().equals(ParseExpression.ExpressionType.kVariable)){
			final ParseVariable parseVariable = (ParseVariable)parseExpression;
			if(parseVariable.getType().getTypeID().equals(TypeID.kGraph)){
				isConstVariable = true;
			}
		}
		
		if(isConstVariable){
			instructions.add(new StatGraph(resolveGraphExpression(parseExpression, null, true)));
		}else{
			Graph targetGraph = resolveGraphExpression(parseExpression, null, true);
			Graph distinctifiedGraph = allocateEmptyGraph();
			instructions.add(new DistinctifyGraph(distinctifiedGraph, targetGraph));
			instructions.add(new StatGraph(distinctifiedGraph));
		}
	}
	
	private final Integer getOptionalPositiveIntegerArgument(final ArrayList<ParseExpression> arguments, final int i){
		if(i >= arguments.size()){
			return null;
		}else{
			if(i < 0){
				throw new RuntimeException("Negative index for argument");
			}
			final Integer value = resolveInteger(arguments.get(i));
			if(value < 1){
				throw new RuntimeException("Only positive value for limit allowed");
			}
			return value;
		}
	}
	
	private void resolveDescribeCommand(final ArrayList<ParseExpression> arguments){
		if(arguments.size() < 2){
			throw new RuntimeException("Invalid number of arguments for describe: expected at least 2");
		}
		
		final Graph targetGraph = resolveGraphExpression(arguments.get(0), null, true);
		
		final ParseExpression elementTypeExpression = arguments.get(1);
		if(elementTypeExpression.getExpressionType() != ParseExpression.ExpressionType.kName){
			throw new RuntimeException("Invalid value at " + elementTypeExpression.getLocationString() + ": expected name");
		}
		final String elementTypeValue = ((ParseName)elementTypeExpression).getName().getValue();
		
		final Result<ElementType> elementTypeResult = HelperFunctions.parseEnumValue(ElementType.class, elementTypeValue, true);
		if(elementTypeResult.error){
			throw new RuntimeException("Invalid value at " + elementTypeExpression.getLocationString() + ". Expected one of: " 
					+ Arrays.asList(ElementType.values()));
		}
		
		if(arguments.size() > 3){
			if(arguments.size() > 5){
				throw new RuntimeException("Invalid number of arguments for describe: expected at max 4");
			}

			final ParseExpression annotationNameExpression = arguments.get(2);
			if(annotationNameExpression.getExpressionType() != ParseExpression.ExpressionType.kName){
				throw new RuntimeException("Invalid value at " + annotationNameExpression.getLocationString() + ": expected name");
			}
			final String annotationNameValue = ((ParseName)annotationNameExpression).getName().getValue();
			
			final ParseExpression descriptionTypeExpression = arguments.get(3);
			if(descriptionTypeExpression.getExpressionType() != ParseExpression.ExpressionType.kName){
				throw new RuntimeException("Invalid value at " + descriptionTypeExpression.getLocationString() + ": expected name");
			}
			final String descriptionTypeValue = ((ParseName)descriptionTypeExpression).getName().getValue();
			
			final Result<DescriptionType> descriptionTypeResult = HelperFunctions.parseEnumValue(DescriptionType.class, descriptionTypeValue, true);
			if(descriptionTypeResult.error){
				throw new RuntimeException("Invalid value at " + descriptionTypeExpression.getLocationString() + ". Expected one of: " 
						+ Arrays.asList(DescriptionType.values()));
			}
			
			instructions.add(new DescribeGraph(targetGraph, elementTypeResult.result, annotationNameValue, descriptionTypeResult.result,
					getOptionalPositiveIntegerArgument(arguments, 4)));
		}else{
			instructions.add(new DescribeGraph(targetGraph, elementTypeResult.result, getOptionalPositiveIntegerArgument(arguments, 2)));
		}
	}

	private void resolveListCommand(ArrayList<ParseExpression> arguments){
		ExpressionStream stream = new ExpressionStream(arguments);
		String listTypeString = stream.tryGetNextNameAsString();
		if(listTypeString == null){
			instructions.add(new List(null));
		}else{
			try{
				ListType listType = ListType.valueOf(listTypeString.toUpperCase());
				instructions.add(new List(listType));
			}catch(Exception e){
				throw new RuntimeException("Unknown type of object to list: '"+listTypeString+"'");
			}
		}
	}

	private void resolveEraseCommand(ArrayList<ParseExpression> arguments){
		ArrayList<String> symbols = new ArrayList<String>();
		for(ParseExpression argument : arguments){
			if(argument.getExpressionType() != ExpressionType.kVariable){
				throw new RuntimeException("Invalid arguments: expected variables");
			}
			ParseVariable var = (ParseVariable)argument;
			symbols.add(var.getName().getValue());
		}
		instructions.add(new EraseSymbols(symbols));
	}

	private void resolveNativeCommand(ArrayList<ParseExpression> arguments){
		if(arguments.size() != 1){
			throw new RuntimeException("Invalid arguments: expected 1 argument");
		}else{
			ParseExpression argument = arguments.get(0);
			if(argument.getExpressionType() != ExpressionType.kLiteral){
				throw new RuntimeException("Invalid arguments: expected literal");
			}else{
				TypedValue value = ((ParseLiteral)argument).getLiteralValue();
				if(value.getType().getTypeID() != TypeID.kString){
					throw new RuntimeException("Invalid argument type at " + argument.getLocationString() + ": expected string");
				}
				instructions.add(new EvaluateQuery(String.valueOf(value.getValue())));
			}
		}
	}
	
	private void resolveEnvCommand(ArrayList<ParseExpression> arguments){
		if(arguments.size() < 1){
			throw new RuntimeException("Invalid arguments for 'env' command: expected at least 1");
		}
		final String subCommand = resolveNameAsString(arguments.get(0)).toLowerCase();
		switch(subCommand){
			case "set":{
				if(arguments.size() != 3){
					throw new RuntimeException("Invalid arguments for 'env' set command: expected 3");
				}
				final String variableName = resolveNameAsString(arguments.get(1)).toLowerCase();
				String variableValue = null;
				if(arguments.get(2).getExpressionType() != ParseExpression.ExpressionType.kLiteral){
					throw new RuntimeException("Invalid value at " + arguments.get(2).getLocationString() + ": expected literal");
				}
				TypedValue value = ((ParseLiteral)arguments.get(2)).getLiteralValue();
				if(value.getType().getTypeID() == TypeID.kString){
					variableValue = (String)value.getValue();
				}else if(value.getType().getTypeID() == TypeID.kInteger){
					variableValue = String.valueOf(value.getValue());
				}else{
					throw new RuntimeException("Invalid value type at " + arguments.get(2).getLocationString() + ": expected string or integer");
				}
				instructions.add(EnvironmentVariableOperation.instanceOfSet(variableName, variableValue));
			}
			break;
			case "unset":{
				if(arguments.size() != 2){
					throw new RuntimeException("Invalid arguments for 'env' unset command: expected 2");
				}
				final String variableName = resolveNameAsString(arguments.get(1)).toLowerCase();
				instructions.add(EnvironmentVariableOperation.instanceOfUnset(variableName));
			}
			break;
			case "list":{
				if(arguments.size() != 1){
					throw new RuntimeException("Invalid arguments for 'env' list command: expected 1");
				}
				instructions.add(EnvironmentVariableOperation.instanceOfList());
			}
			break;
			case "print":{
				if(arguments.size() != 2){
					throw new RuntimeException("Invalid arguments for 'env' print command: expected 2");
				}
				final String variableName = resolveNameAsString(arguments.get(1)).toLowerCase();
				instructions.add(EnvironmentVariableOperation.instanceOfPrint(variableName));
			}
			break;
			default: throw new RuntimeException("No subcommand '"+subCommand+"' for 'env' command");
		}
	}
	
	private void resolveResetCommand(ArrayList<ParseExpression> arguments){
		if(arguments.size() == 1){
			String target = resolveNameAsString(arguments.get(0));
			if(target.equals("workspace")){
				env.resetWorkspace();
			}
		}
	}

	private Entity resolveExpression(ParseExpression parseExpression, Entity outputEntity, boolean isConstReference){
		switch(parseExpression.getExpressionType()){
		case kOperation:
			return resolveOperation((ParseOperation)parseExpression, outputEntity);
		case kVariable:
			return resolveVariable((ParseVariable)parseExpression, outputEntity, isConstReference);
		default:
			break;
		}
		throw new RuntimeException("Unsupported expression type: " + parseExpression.getExpressionType().name());
	}

	private Graph resolveGraphExpression(ParseExpression parseExpression, Graph outputGraph, boolean isConstReference){
		return ToGraph(resolveExpression(parseExpression, outputGraph, isConstReference));
	}

	private Entity resolveOperation(ParseOperation parseOperation, Entity outputEntity){
		ParseExpression parseSubject = parseOperation.getSubject();
		ParseString op = parseOperation.getOperator();
		ArrayList<ParseExpression> operands = parseOperation.getOperands();
		if(parseSubject != null){
			Entity subject = resolveExpression(parseSubject, null, true);
			switch(subject.getEntityType()){
			case kGraph:
				return resolveGraphMethod(ToGraph(subject), op, operands, outputEntity);
			case kGraphMetadata:
				throw new RuntimeException("Graph metadata queries not supported yet");
//				return resolveGraphMetadataMethod(ToGraphMetadata(subject), op.getValue(), operands);
			default:
				throw new RuntimeException("Invalid subject type " + subject.getEntityType().name().substring(1)
						+ " at " + parseSubject.getLocationString());
			}
		}

		// Pure functions.
		switch(op.getValue()){
		case "+":{
			if(operands.size() != 2){
				throw new RuntimeException("Unexpected '+' operands count: " + operands.size() + ". Expected: " + 2);
			}
			Entity lhsEntity = resolveExpression(operands.get(0), outputEntity, false);
			return resolveExpression(operands.get(1), lhsEntity, true);
		}
		case "&": // Fall through
		case "-":{
			if(operands.size() != 2){
				throw new RuntimeException("Unexpected '"+op.getValue()+"' operands count: " + operands.size() + ". Expected: " + 2);
			}
			return resolveGraphBinaryOperation(op, operands.get(0), operands.get(1), ToGraph(outputEntity));
		}
		case "vertices":
			return resolveInsertLiteralVertex(operands, ToGraph(outputEntity));
		case "edges":
			return resolveInsertLiteralEdge(operands, ToGraph(outputEntity));
//		TODO
//		case "asVertex":
//			return resolveAsVertexOrEdge(Graph.Component.kVertex, operands, ToGraph(outputEntity));
//		case "asEdge":
//			return resolveAsVertexOrEdge(Graph.Component.kEdge, operands, ToGraph(outputEntity));
		default:
			break;
		}
		throw new RuntimeException("Unsupported operation " + op.getValue() + " at " + op.getLocationString());
	}

	private Graph resolveGraphBinaryOperation(ParseString op, ParseExpression lhs, ParseExpression rhs,
			Graph outputGraph){
		Graph lhsGraph = resolveGraphExpression(lhs, null, true);
		Graph rhsGraph = resolveGraphExpression(rhs, null, true);

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		switch(op.getValue()){
		case "&":
			instructions.add(new IntersectGraph(outputGraph, lhsGraph, rhsGraph));
			break;
		case "-":
			instructions.add(new SubtractGraph(outputGraph, lhsGraph, rhsGraph, null));
			break;
		default:
			throw new RuntimeException(
					"Unsupported graph binary operator " + op.getValue() + " at " + op.getLocationString());
		}
		return outputGraph;
	}

	private Entity resolveGraphMethod(Graph subject, ParseString methodName, ArrayList<ParseExpression> arguments,
			Entity outputEntity){
		switch(methodName.getValue()){
		case "getVertex":
			return resolveGetVertexOrEdge(Graph.Component.kVertex, subject, arguments, ToGraph(outputEntity));
		case "getEdge":
			return resolveGetVertexOrEdge(Graph.Component.kEdge, subject, arguments, ToGraph(outputEntity));
		case "getEdgeWithEndpoints":{
			Graph edges = resolveGetVertexOrEdge(Graph.Component.kEdge, subject, arguments, ToGraph(outputEntity));
			Graph outputGraph = (Graph)outputEntity;
			if(outputGraph == null){
				outputGraph = allocateEmptyGraph();
			}
			instructions.add(new UnionGraph(outputGraph, edges));
			instructions.add(new GetEdgeEndpoint(outputGraph, edges, GetEdgeEndpoint.Component.kBoth));
			return outputGraph;
		}
		case "getLineage":
			return resolveGetLineage(subject, arguments, ToGraph(outputEntity), false);
		case "getLocalLineage":
			return resolveGetLineage(subject, arguments, ToGraph(outputEntity), true);
		case "getNeighbor":
			return resolveGetNeighbor(subject, arguments, ToGraph(outputEntity));
		case "getLink":
			return resolveGetLink(subject, arguments, ToGraph(outputEntity));
		case "getPath":
			return resolveGetPath(subject, arguments, ToGraph(outputEntity));
		case "getShortestPath":
			return resolveGetShortestPath(subject, arguments, ToGraph(outputEntity));
		case "getSubgraph":
			return resolveGetSubgraph(subject, arguments, ToGraph(outputEntity));
		case "getEdgeSource":
			return resolveGetEdgeEndpoint(GetEdgeEndpoint.Component.kSource, subject, arguments, ToGraph(outputEntity));
		case "getEdgeDestination":
			return resolveGetEdgeEndpoint(GetEdgeEndpoint.Component.kDestination, subject, arguments,
					ToGraph(outputEntity));
		case "getEdgeEndpoints":
			return resolveGetEdgeEndpoint(GetEdgeEndpoint.Component.kBoth, subject, arguments, ToGraph(outputEntity));
		case "collapseEdge":
			return resolveCollapseEdge(subject, arguments, ToGraph(outputEntity));
		case "attr":
		case "attrVertex":
		case "attrEdge":
			throw new RuntimeException("Graph metadata queries not supported yet");
//		case "attr": TODO partial implementation
//			return resolveSetMetadata(SetGraphMetadata.Component.kBoth, subject, arguments,
//					ToGraphMetadata(outputEntity));
//		case "attrVertex":
//			return resolveSetMetadata(SetGraphMetadata.Component.kVertex, subject, arguments,
//					ToGraphMetadata(outputEntity));
//		case "attrEdge":
//			return resolveSetMetadata(SetGraphMetadata.Component.kEdge, subject, arguments,
//					ToGraphMetadata(outputEntity));
		case "span":
			return resolveSpan(subject, arguments, ToGraph(outputEntity));
		case "limit":
			return resolveLimit(subject, arguments, ToGraph(outputEntity));
		case "getMatch":
			return resolveGetMatch(subject, arguments, ToGraph(outputEntity));
		default:
			break;
		}
		throw new RuntimeException(
				"Unsupported Graph method " + methodName.getValue() + " at " + methodName.getLocationString());
	}

	public Entity resolveVariable(ParseVariable var, Entity outputEntity, boolean isConstReference){
		switch(var.getType().getTypeID()){
		case kGraph:
			return resolveGraphVariable(var, ToGraph(outputEntity), isConstReference);
		case kGraphMetadata:
			throw new RuntimeException("Graph metadata queries not supported yet");
//			return resolveGraphMetadataVariable(var, ToGraphMetadata(outputEntity));
		default:
			break;
		}
		throw new RuntimeException(
				"Unsupported variable type " + var.getType().getName() + " at " + var.getLocationString());
	}

	private Graph resolveGraphVariable(ParseVariable var, Graph outputGraph, boolean isConstReference){
		if(var.getType().getTypeID() != TypeID.kGraph){
			throw new RuntimeException("Unexpected variable type: " + var.getType().getTypeID() + ". Expected: " + TypeID.kGraph);
		}
		Graph savedGraph = env.getGraphSymbol(var.getName().getValue());
		if(savedGraph == null){
			throw new RuntimeException(
					"Cannot resolve Graph variable " + var.getName().getValue() + " at " + var.getLocationString());
		}
		if(outputGraph == null){
			if(isConstReference){
				return new Graph(savedGraph.name);
			}
			outputGraph = allocateEmptyGraph();
		}
		instructions.add(new UnionGraph(outputGraph, new Graph(savedGraph.name)));
		return outputGraph;
	}

	private Graph resolveInsertLiteralVertex(ArrayList<ParseExpression> operands, Graph outputGraph){
		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}
		ArrayList<String> vertices = new ArrayList<String>();
		for(ParseExpression e : operands){
			if(e.getExpressionType() != ParseExpression.ExpressionType.kLiteral){
				throw new RuntimeException(
						"Invalid argument at " + e.getLocationString() + ": expected integer literal");
			}
			TypedValue value = ((ParseLiteral)e).getLiteralValue();
			if(value.getType().getTypeID() != TypeID.kString){
				throw new RuntimeException("Invalid argument type at " + e.getLocationString() + ": expected string hash");
			}
			vertices.add(String.valueOf(value.getValue()));
		}
		instructions.add(new InsertLiteralVertex(outputGraph, vertices));
		return outputGraph;
	}

	private Graph resolveInsertLiteralEdge(ArrayList<ParseExpression> operands, Graph outputGraph){
		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}
		ArrayList<String> edges = new ArrayList<String>();
		for(ParseExpression e : operands){
			if(e.getExpressionType() != ParseExpression.ExpressionType.kLiteral){
				throw new RuntimeException(
						"Invalid argument at " + e.getLocationString() + ": expected integer literal");
			}
			TypedValue value = ((ParseLiteral)e).getLiteralValue();
			if(value.getType().getTypeID() != TypeID.kString){
				throw new RuntimeException("Invalid argument type at " + e.getLocationString() + ": expected string hash");
			}
			edges.add(String.valueOf(value.getValue()));
		}
		instructions.add(new InsertLiteralEdge(outputGraph, edges));
		return outputGraph;
	}

	private Graph resolveGetVertexOrEdge(Graph.Component component, Graph subjectGraph,
			ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() > 1){
			throw new RuntimeException("Invalid number of arguments for getVertex/getEdge: expected 0 or 1");
		}

		if(arguments.isEmpty()){
			// Get all the vertices.
			if(outputGraph == null){
				outputGraph = allocateEmptyGraph();
			}

			switch(component){
				case kEdge: instructions.add(new GetEdge(outputGraph, subjectGraph)); break;
				case kVertex: instructions.add(new GetVertex(outputGraph, subjectGraph)); break;
				default: throw new RuntimeException("Invalid component type " + component.name().substring(1) + " instead of vertex or edge");
			}
			
			return outputGraph;
		}else{
			PredicateNode predicateNode = QuickGrailPredicateTree.resolveGraphPredicate(arguments.get(0), env);
			if(predicateNode == null){
				throw new RuntimeException("Failed to resolve predicate");
			}
			return resolveGetVertexOrEdgePredicate(component, subjectGraph, predicateNode, outputGraph);
		}
	}

	private Graph resolveGetVertexOrEdgePredicate(Graph.Component component, Graph subjectGraph,
			PredicateNode predicateNode, Graph outputGraph){
		switch(predicateNode.value){
		case QuickGrailPredicateTree.BOOLEAN_OPERATOR_OR:{
			outputGraph = resolveGetVertexOrEdgePredicate(component, subjectGraph, predicateNode.getLeft(), outputGraph);
			outputGraph = resolveGetVertexOrEdgePredicate(component, subjectGraph, predicateNode.getRight(), outputGraph);
			return outputGraph;
		}
		case QuickGrailPredicateTree.BOOLEAN_OPERATOR_AND:{
			Graph lhsGraph = resolveGetVertexOrEdgePredicate(component, subjectGraph, predicateNode.getLeft(), null);
			Graph rhsGraph = resolveGetVertexOrEdgePredicate(component, lhsGraph, predicateNode.getRight(), outputGraph);
			return rhsGraph;
		}
		case QuickGrailPredicateTree.BOOLEAN_OPERATOR_NOT:
			Graph subtrahendGraph = resolveGetVertexOrEdgePredicate(component, subjectGraph, predicateNode.getLeft(), null);
			if(outputGraph == null){
				outputGraph = allocateEmptyGraph();
			}
			instructions.add(new SubtractGraph(outputGraph, subjectGraph, subtrahendGraph, component));
			return outputGraph;
		default:
			break;
		}
		return resolveGetVertexOrEdgeComparison(component, subjectGraph, predicateNode, outputGraph);
	}
	
	private Graph resolveGetVertexOrEdgeComparison(Graph.Component component, Graph subjectGraph,
			PredicateNode predicateNode, Graph outputGraph){
		PredicateOperator predicateOperator = null;
		switch(predicateNode.value){
		case QuickGrailPredicateTree.COMPARATOR_EQUAL1: case QuickGrailPredicateTree.COMPARATOR_EQUAL2:
			predicateOperator = PredicateOperator.EQUAL; break;
		case QuickGrailPredicateTree.COMPARATOR_NOT_EQUAL1: case QuickGrailPredicateTree.COMPARATOR_NOT_EQUAL2:
			predicateOperator = PredicateOperator.NOT_EQUAL; break;
		case QuickGrailPredicateTree.COMPARATOR_LESS:
			predicateOperator = PredicateOperator.LESSER; break;
		case QuickGrailPredicateTree.COMPARATOR_LESS_EQUAL:
			predicateOperator = PredicateOperator.LESSER_EQUAL; break;
		case QuickGrailPredicateTree.COMPARATOR_GREATER:
			predicateOperator = PredicateOperator.GREATER; break;
		case QuickGrailPredicateTree.COMPARATOR_GREATER_EQUAL:
			predicateOperator = PredicateOperator.GREATER_EQUAL; break;
		case QuickGrailPredicateTree.COMPARATOR_LIKE:
			predicateOperator = PredicateOperator.LIKE; break;
		case QuickGrailPredicateTree.COMPARATOR_REGEX1: case QuickGrailPredicateTree.COMPARATOR_REGEX2:
			predicateOperator = PredicateOperator.REGEX; break;
		default:
			throw new RuntimeException(
					"Unexpected comparator: " + predicateNode.value);
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}
		
		String field = predicateNode.getLeft().value;
		String value = predicateNode.getRight().value;

		switch(component){
			case kEdge: instructions.add(new GetEdge(outputGraph, subjectGraph, field, predicateOperator, value)); break;
			case kVertex: instructions.add(new GetVertex(outputGraph, subjectGraph, field, predicateOperator, value)); break;
			default: throw new RuntimeException("Invalid component type " + component.name().substring(1) + " instead of vertex or edge");
		}

		return outputGraph;
	}

	private Graph resolveGetNeighbor(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() != 2){
			throw new RuntimeException("Invalid number of arguments for getNeighbor: expected 2");
		}

		Graph startGraph = resolveGraphExpression(arguments.get(0), null, true);

		String dirStr = resolveString(arguments.get(1));
		GetLineage.Direction direction;
		if(dirStr.startsWith("a")){
			direction = GetLineage.Direction.kAncestor;
		}else if(dirStr.startsWith("d")){
			direction = GetLineage.Direction.kDescendant;
		}else{
			direction = GetLineage.Direction.kBoth;
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		instructions.add(new GetAdjacentVertex(outputGraph, subjectGraph, startGraph, direction));
		return outputGraph;
	}
	
	private Graph resolveGetLineage(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph, boolean onlyLocal){
		if(arguments.size() != 2 && arguments.size() != 3){
			throw new RuntimeException("Invalid number of arguments for getLineage: expected either 2 or 3");
		}

		final EnvironmentVariable maxDepthVar = env.getEnvironmentVariable(AbstractQueryEnvironment.environmentVariableNameMaxDepth);
		
		Graph startGraph = resolveGraphExpression(arguments.get(0), null, true);
		
		Integer depth = null;
		String dirStr = null;
		
		if(arguments.get(1).getExpressionType() != ParseExpression.ExpressionType.kLiteral){
			throw new RuntimeException("Invalid value at " + arguments.get(1).getLocationString() + ": expected literal");
		}
		
		TypedValue value = ((ParseLiteral)arguments.get(1)).getLiteralValue();
		if(value.getType().getTypeID() == TypeID.kInteger){
			depth = (Integer)value.getValue();
			dirStr = resolveString(arguments.get(2));
		}else if(value.getType().getTypeID() == TypeID.kString){
			if(maxDepthVar == null || maxDepthVar.getValue() == null){
				throw new RuntimeException("Must explicitly specify max depth or set it in environment");
			}
			depth = (Integer)maxDepthVar.getValue();
			dirStr = resolveString(arguments.get(1));
		}else{
			throw new RuntimeException("Invalid value at " + arguments.get(1).getLocationString() + ": expected integer or string literal");
		}

		GetLineage.Direction direction;
		if(dirStr.startsWith("a")){
			direction = GetLineage.Direction.kAncestor;
		}else if(dirStr.startsWith("d")){
			direction = GetLineage.Direction.kDescendant;
		}else{
			direction = GetLineage.Direction.kBoth;
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		instructions.add(new GetLineage(outputGraph, subjectGraph, startGraph, depth, direction, onlyLocal));
		return outputGraph;
	}

	private Graph resolveGetLink(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() != 3){
			throw new RuntimeException("Invalid number of arguments for getLink: expected 3");
		}

		Graph srcGraph = resolveGraphExpression(arguments.get(0), null, true);
		Graph dstGraph = resolveGraphExpression(arguments.get(1), null, true);
		Integer maxDepth = resolveInteger(arguments.get(2));

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		instructions.add(new GetLink(outputGraph, subjectGraph, srcGraph, dstGraph, maxDepth));
		return outputGraph;
	}

	private Graph resolveGetPath(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() < 2){
			throw new RuntimeException("Invalid number of arguments for getPath: expected at least 2");
		}
		
		final Graph srcGraph = resolveGraphExpression(arguments.get(0), null, true);
		final Graph dstGraph = resolveGraphExpression(arguments.get(1), null, true);
		
		final ArrayList<SimpleEntry<Graph, Integer>> intermediateSteps = new ArrayList<SimpleEntry<Graph, Integer>>();
		
		SimpleEntry<Graph, Integer> currentEntry = new SimpleEntry<Graph, Integer>(dstGraph, null);
		
//		To identify a graph the kVariable type is being used. Start off with kVariable because dstGraph already taken out. 
		ParseExpression.ExpressionType lastExpressionType = ParseExpression.ExpressionType.kVariable;
		
		final EnvironmentVariable maxDepthVar = env.getEnvironmentVariable(AbstractQueryEnvironment.environmentVariableNameMaxDepth);
		
		int i = 2;
		int total = arguments.size();
		while(i < total){
			final ParseExpression currentExpression = arguments.get(i);
			if(lastExpressionType == ParseExpression.ExpressionType.kVariable){
				// Last was graph expression so can be another graph expression or a literal
				if(currentExpression.getExpressionType() == ParseExpression.ExpressionType.kLiteral){
					// Must be an integer
					final Integer maxDepth = resolveInteger(currentExpression);
					currentEntry.setValue(maxDepth);
					intermediateSteps.add(currentEntry);
					lastExpressionType = ParseExpression.ExpressionType.kLiteral;
				}else{
					// Must be a graph since only graph or literal allowed
					// This is graph and the last one was graph too
					Graph graph = resolveGraphExpression(currentExpression, null, true);
					// Need to get max depth from the saved one
					if(maxDepthVar == null || maxDepthVar.getValue() == null){
						throw new RuntimeException("Must explicitly specify max depth or set it in environment");
					}
					currentEntry.setValue((Integer)maxDepthVar.getValue());
					intermediateSteps.add(currentEntry);
					
					currentEntry = new SimpleEntry<Graph, Integer>(graph, null);
					lastExpressionType = ParseExpression.ExpressionType.kVariable;
				}
			}else if(lastExpressionType == ParseExpression.ExpressionType.kLiteral){
				// Last was a literal so this must be a graph
				Graph graph = resolveGraphExpression(currentExpression, null, true);
				currentEntry = new SimpleEntry<Graph, Integer>(graph, null);
				lastExpressionType = ParseExpression.ExpressionType.kVariable;
			}else{
				throw new RuntimeException("Invalid argument. Expected graph expression or integer: " + currentExpression);
			}
			i++;
		}

		if(currentEntry.getValue() == null){
			if(maxDepthVar == null || maxDepthVar.getValue() == null){
				throw new RuntimeException("Must explicitly specify max depth or set it in environment");
			}
			currentEntry.setValue((Integer)maxDepthVar.getValue());
			intermediateSteps.add(currentEntry);
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}
		
		Instruction instruction = null;
		
		if(intermediateSteps.size() == 1){
			instruction = new GetSimplePath(outputGraph, subjectGraph, srcGraph, 
					intermediateSteps.get(0).getKey(), intermediateSteps.get(0).getValue());
		}else{
			final GetPath getPath = new GetPath(outputGraph, subjectGraph, srcGraph);
			for(final SimpleEntry<Graph, Integer> intermediateStep : intermediateSteps){
				getPath.addIntermediateStep(intermediateStep.getKey(), intermediateStep.getValue());
			}
			instruction = getPath;
		}

		instructions.add(instruction);
		return outputGraph;
	}

	private Graph resolveCollapseEdge(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		ArrayList<String> fields = new ArrayList<String>();
		for(ParseExpression e : arguments){
			fields.add(resolveString(e));
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		instructions.add(new CollapseEdge(outputGraph, subjectGraph, fields));
		return outputGraph;
	}

	private Graph resolveGetSubgraph(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() != 1){
			throw new RuntimeException("Invalid number of arguments for getSubgraph: expected 1");
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		Graph skeletonGraph = resolveGraphExpression(arguments.get(0), null, true);
		instructions.add(new GetSubgraph(outputGraph, subjectGraph, skeletonGraph));
		return outputGraph;
	}

	private Graph resolveGetEdgeEndpoint(GetEdgeEndpoint.Component component, Graph subjectGraph,
			ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(!arguments.isEmpty()){
			throw new RuntimeException(
					"Invalid number of arguments at " + arguments.get(0).getLocationString() + ": expected 0");
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		instructions.add(new GetEdgeEndpoint(outputGraph, subjectGraph, component));
		return outputGraph;
	}

	private Graph resolveSpan(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() != 1){
			throw new RuntimeException("Invalid number of arguments for span: expected 1");
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		Graph sourceGraph = resolveGraphExpression(arguments.get(0), null, true);
		instructions.add(new GetSubgraph(outputGraph, sourceGraph, subjectGraph));
		return outputGraph;
	}

	private Graph resolveGetMatch(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() < 2){ // at least 2
			throw new RuntimeException("Invalid number of arguments for limit: expected at least 2");
		}
		Graph graph2 = resolveGraphExpression(arguments.get(0), null, true);
		ArrayList<String> annotationKeys = new ArrayList<String>();
		for(int i = 1; i < arguments.size(); i++){
			final String str = resolveString(arguments.get(i));
			if(HelperFunctions.isNullOrEmpty(str)){
				throw new RuntimeException("Invalid blank/null string in arguments at " + arguments.get(i).getLocationString());
			}
			annotationKeys.add(str);
		}
		
		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}
		
		instructions.add(new GetMatch(outputGraph, subjectGraph, graph2, annotationKeys));
		return outputGraph;
	}
	
	private Graph resolveLimit(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		Integer limit = null;
		
		if(arguments.size() == 1){
			limit = resolveInteger(arguments.get(0));
		}else if(arguments.size() == 0){
			final EnvironmentVariable limitVar = env.getEnvironmentVariable(AbstractQueryEnvironment.environmentVariableNameLimit);
			if(limitVar == null || limitVar.getValue() == null){
				throw new RuntimeException("Must explicitly specify 'limit' or set it in environment");
			}
			limit = (Integer)limitVar.getValue();
		}else{
			throw new RuntimeException("Invalid number of arguments for limit: expected 0 or 1");
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		instructions.add(new LimitGraph(outputGraph, subjectGraph, limit));
		return outputGraph;
	}

	private Graph resolveGetShortestPath(Graph subjectGraph, ArrayList<ParseExpression> arguments, Graph outputGraph){
		if(arguments.size() != 2 && arguments.size() != 3){
			throw new RuntimeException("Invalid number of arguments for getShortestPath: expected either 2 or 3");
		}

		Graph srcGraph = resolveGraphExpression(arguments.get(0), null, true);
		Graph dstGraph = resolveGraphExpression(arguments.get(1), null, true);
		
		Integer maxDepth = null;
		
		if(arguments.size() == 2){
			final EnvironmentVariable maxDepthVar = env.getEnvironmentVariable(AbstractQueryEnvironment.environmentVariableNameMaxDepth);
			if(maxDepthVar == null || maxDepthVar.getValue() == null){
				throw new RuntimeException("Must explicitly specify max depth or set it in environment");
			}
			maxDepth = (Integer)maxDepthVar.getValue();
		}else{ // size is 3
			maxDepth = resolveInteger(arguments.get(2));
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		instructions.add(new GetShortestPath(outputGraph, subjectGraph, srcGraph, dstGraph, maxDepth));
		return outputGraph;
	}

	private Integer resolveInteger(ParseExpression expression){
		if(expression.getExpressionType() != ParseExpression.ExpressionType.kLiteral){
			throw new RuntimeException(
					"Invalid value at " + expression.getLocationString() + ": expected integer literal");
		}
		TypedValue value = ((ParseLiteral)expression).getLiteralValue();
		if(value.getType().getTypeID() != TypeID.kInteger){
			throw new RuntimeException(
					"Invalid value type at " + expression.getLocationString() + ": expected integer");
		}
		return (Integer)value.getValue();
	}

	private String resolveString(ParseExpression expression){
		if(expression.getExpressionType() != ParseExpression.ExpressionType.kLiteral){
			throw new RuntimeException(
					"Invalid value at " + expression.getLocationString() + ": expected string literal");
		}
		TypedValue value = ((ParseLiteral)expression).getLiteralValue();
		if(value.getType().getTypeID() != TypeID.kString){
			throw new RuntimeException("Invalid value type at " + expression.getLocationString() + ": expected string");
		}
		return (String)value.getValue();
	}

	private String resolveNameAsString(ParseExpression expression){
		if(expression.getExpressionType() != ParseExpression.ExpressionType.kName){
			throw new RuntimeException("Invalid value at " + expression.getLocationString() + ": expected name");
		}
		return ((ParseName)expression).getName().getValue();
	}

	private Graph allocateEmptyGraph(){
		Graph graph = env.allocateGraph();
		instructions.add(new CreateEmptyGraph(graph));
		return graph;
	}

	private static Graph ToGraph(Entity entity){
		if(entity == null){
			return null;
		}
		if(entity.getEntityType() != EntityType.kGraph){
			throw new RuntimeException(
					"Invalid casting from an instance of " + entity.getEntityType().name().substring(1) + " to Graph");
		}
		return (Graph)entity;
	}

	/*
	private Graph resolveAsVertexOrEdge(Graph.Component component, ArrayList<ParseExpression> arguments,
			Graph outputGraph){
		// TODO: handle subject?
		if(arguments.size() != 1){
			throw new RuntimeException("Invalid number of arguments for asVertex/asEdge: expected 1");
		}

		if(outputGraph == null){
			outputGraph = allocateEmptyGraph();
		}

		String rawQuery = resolveString(arguments.get(0));
		StringBuffer sb = new StringBuffer();
		//sb.append("INSERT INTO " + env.getTableName(component, outputGraph) + " ");

		Pattern pattern = Pattern.compile("[$][^.]+[.](vertex|edge)");
		Matcher m = pattern.matcher(rawQuery);
		while(m.find()){
			String ref = m.group();
			String var;
			Graph.Component refComponent;
			if(ref.endsWith(".vertex")){
				refComponent = Graph.Component.kVertex;
				var = ref.substring(0, ref.length() - 7);
			}else{
				assert ref.endsWith(".edge");
				refComponent = Graph.Component.kEdge;
				var = ref.substring(0, ref.length() - 5);
			}
			Graph graphNameGraph = env.lookupGraphSymbol(var);
			if(graphNameGraph == null){
				throw new RuntimeException(
						"Cannot resolve variable " + var + " in the query at " + arguments.get(0).getLocationString());
			}
			//m.appendReplacement(sb, env.getTableName(refComponent, new Graph(graphName)));
		}
		m.appendTail(sb);

		instructions.add(new EvaluateQuery(sb.toString()));
		return outputGraph;
	}
	*/
	
	/*
	private GraphMetadata allocateEmptyGraphMetadata(){
		GraphMetadata metadata = env.allocateGraphMetadata();
		instructions.add(new CreateEmptyGraphMetadata(metadata));
		return metadata;
	}
	
	private static GraphMetadata ToGraphMetadata(Entity entity){
		if(entity == null){
			return null;
		}
		if(entity.getEntityType() != EntityType.kGraphMetadata){
			throw new RuntimeException(
					"Invalid casting from an instance of " + entity.getEntityType().name().substring(1) + " to GraphMetadata");
		}
		return (GraphMetadata)entity;
	}

	private GraphMetadata resolveSetMetadata(SetGraphMetadata.Component component, Graph subjectGraph,
			ArrayList<ParseExpression> arguments, GraphMetadata outputMetadata){
		if(arguments.size() != 2){
			throw new RuntimeException("Invalid number of arguments for attr: expected 2");
		}

		String name = resolveString(arguments.get(0));
		String value = resolveString(arguments.get(1));

		GraphMetadata metadata = allocateEmptyGraphMetadata();
		instructions.add(new SetGraphMetadata(metadata, component, subjectGraph, name, value));

		if(outputMetadata == null){
			return metadata;
		}else{
			GraphMetadata combinedMetadata = allocateEmptyGraphMetadata();
			instructions.add(new OverwriteGraphMetadata(combinedMetadata, outputMetadata, metadata));
			return combinedMetadata;
		}
	}
	
	private void resolveGraphMetadataAssignment(ParseAssignment parseAssignment){
		if(parseAssignment.getLhs().getType().getTypeID() != TypeID.kGraphMetadata){
			throw new RuntimeException("Unexpected LHS type '" + parseAssignment.getLhs().getType().getTypeID() + "'. "
					+ "Expected '" + TypeID.kGraphMetadata + "'");
		}
		
		ParseString var = parseAssignment.getLhs().getName();
		ParseExpression rhs = parseAssignment.getRhs();
		ParseAssignment.AssignmentType atype = parseAssignment.getAssignmentType();

		GraphMetadata resultMetadata;
		if(atype == ParseAssignment.AssignmentType.kEqual){
			resultMetadata = resolveGraphMetadataExpression(rhs, null);
		}else{
			GraphMetadata lhsGraph = env.lookupGraphMetadataSymbol(var.getValue());
			if(lhsGraph == null){
				throw new RuntimeException(
						"Cannot resolve GraphMetadata variable " + var.getValue() + " at " + var.getLocationString());
			}
			switch(atype){
			case kPlusEqual:{
				resultMetadata = resolveGraphMetadataExpression(rhs, lhsGraph);
				break;
			}
			default:
				throw new RuntimeException(
						"Unsupported assignment " + parseAssignment.getAssignmentType().name().substring(1) + " at "
								+ parseAssignment.getLocationString());
			}
		}
		env.setGraphMetadataSymbol(var.getValue(), resultMetadata);
	}
	
	private GraphMetadata resolveGraphMetadataExpression(ParseExpression parseExpression, GraphMetadata outputMetadata){
		return ToGraphMetadata(resolveExpression(parseExpression, outputMetadata, true));
	}
	
	private Entity resolveGraphMetadataMethod(GraphMetadata subject, String methodName,
			ArrayList<ParseExpression> arguments){
		throw new RuntimeException("No GraphMetadata method is supported yet");
	}
	
	private GraphMetadata resolveGraphMetadataVariable(ParseVariable var, GraphMetadata lhsMetadata){
		if(var.getType().getTypeID() != TypeID.kGraphMetadata){
			throw new RuntimeException("Unexpected variable type: " + var.getType().getTypeID() + ". Expected: " + TypeID.kGraphMetadata);
		}
		GraphMetadata rhsMetadata = env.lookupGraphMetadataSymbol(var.getName().getValue());
		if(rhsMetadata == null){
			throw new RuntimeException("Cannot resolve GraphMetadata variable " + var.getName().getValue() + " at "
					+ var.getLocationString());
		}

		if(lhsMetadata == null){
			return rhsMetadata;
		}

		GraphMetadata outputMetadata = allocateEmptyGraphMetadata();
		instructions.add(new OverwriteGraphMetadata(outputMetadata, lhsMetadata, rhsMetadata));
		return outputMetadata;
	}
	*/
}
