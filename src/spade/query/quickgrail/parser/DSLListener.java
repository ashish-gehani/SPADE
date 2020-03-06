// Generated from DSL.g4 by ANTLR 4.7

package spade.query.quickgrail.parser;

import spade.query.quickgrail.types.*;

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link DSLParser}.
 */
public interface DSLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link DSLParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(DSLParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(DSLParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(DSLParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(DSLParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(DSLParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(DSLParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#or_expression}.
	 * @param ctx the parse tree
	 */
	void enterOr_expression(DSLParser.Or_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#or_expression}.
	 * @param ctx the parse tree
	 */
	void exitOr_expression(DSLParser.Or_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#and_expression}.
	 * @param ctx the parse tree
	 */
	void enterAnd_expression(DSLParser.And_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#and_expression}.
	 * @param ctx the parse tree
	 */
	void exitAnd_expression(DSLParser.And_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#not_expression}.
	 * @param ctx the parse tree
	 */
	void enterNot_expression(DSLParser.Not_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#not_expression}.
	 * @param ctx the parse tree
	 */
	void exitNot_expression(DSLParser.Not_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#comparison_expression}.
	 * @param ctx the parse tree
	 */
	void enterComparison_expression(DSLParser.Comparison_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#comparison_expression}.
	 * @param ctx the parse tree
	 */
	void exitComparison_expression(DSLParser.Comparison_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#add_expression}.
	 * @param ctx the parse tree
	 */
	void enterAdd_expression(DSLParser.Add_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#add_expression}.
	 * @param ctx the parse tree
	 */
	void exitAdd_expression(DSLParser.Add_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#intersect_expression}.
	 * @param ctx the parse tree
	 */
	void enterIntersect_expression(DSLParser.Intersect_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#intersect_expression}.
	 * @param ctx the parse tree
	 */
	void exitIntersect_expression(DSLParser.Intersect_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#function_call}.
	 * @param ctx the parse tree
	 */
	void enterFunction_call(DSLParser.Function_callContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#function_call}.
	 * @param ctx the parse tree
	 */
	void exitFunction_call(DSLParser.Function_callContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#factor}.
	 * @param ctx the parse tree
	 */
	void enterFactor(DSLParser.FactorContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#factor}.
	 * @param ctx the parse tree
	 */
	void exitFactor(DSLParser.FactorContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#argument_list}.
	 * @param ctx the parse tree
	 */
	void enterArgument_list(DSLParser.Argument_listContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#argument_list}.
	 * @param ctx the parse tree
	 */
	void exitArgument_list(DSLParser.Argument_listContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#name}.
	 * @param ctx the parse tree
	 */
	void enterName(DSLParser.NameContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#name}.
	 * @param ctx the parse tree
	 */
	void exitName(DSLParser.NameContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#variable}.
	 * @param ctx the parse tree
	 */
	void enterVariable(DSLParser.VariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#variable}.
	 * @param ctx the parse tree
	 */
	void exitVariable(DSLParser.VariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link DSLParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(DSLParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link DSLParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(DSLParser.LiteralContext ctx);
}