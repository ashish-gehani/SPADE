// Generated from DSL.g4 by ANTLR 4.7

package spade.query.quickgrail.parser;

import spade.query.quickgrail.types.*;

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class DSLParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		TOKEN_COMMA=1, TOKEN_DOT=2, TOKEN_SEMICOLON=3, TOKEN_LPAREN=4, TOKEN_RPAREN=5, 
		TOKEN_PLUS=6, TOKEN_MINUS=7, TOKEN_INTERSECT=8, TOKEN_OR=9, TOKEN_AND=10, 
		TOKEN_NOT=11, TOKEN_LIKE=12, TOKEN_EQUAL=13, TOKEN_NOT_EQUAL=14, TOKEN_LESS=15, 
		TOKEN_GREATER=16, TOKEN_LESS_EQUAL=17, TOKEN_GREATER_EQUAL=18, TOKEN_REGEX=19, 
		TOKEN_ASSIGN=20, TOKEN_PLUS_ASSIGN=21, TOKEN_MINUS_ASSIGN=22, TOKEN_INTERSECT_ASSIGN=23, 
		TOKEN_NAME=24, TOKEN_NUMBER=25, TOKEN_SINGLE_QUOTED_STRING_LITERAL=26, 
		TOKEN_DOUBLE_QUOTED_NAME=27, TOKEN_GRAPH_VARIABLE=28, TOKEN_GRAPH_METADATA_VARIABLE=29, 
		TOKEN_COMMENTS=30, TOKEN_WS=31;
	public static final int
		RULE_program = 0, RULE_statement = 1, RULE_expression = 2, RULE_or_expression = 3, 
		RULE_and_expression = 4, RULE_not_expression = 5, RULE_comparison_expression = 6, 
		RULE_add_expression = 7, RULE_intersect_expression = 8, RULE_function_call = 9, 
		RULE_factor = 10, RULE_argument_list = 11, RULE_name = 12, RULE_variable = 13, 
		RULE_literal = 14;
	public static final String[] ruleNames = {
		"program", "statement", "expression", "or_expression", "and_expression", 
		"not_expression", "comparison_expression", "add_expression", "intersect_expression", 
		"function_call", "factor", "argument_list", "name", "variable", "literal"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "','", "'.'", "';'", "'('", "')'", "'+'", "'-'", "'&'", null, null, 
		null, null, "'=='", null, "'<'", "'>'", "'<='", "'>='", null, "'='", "'+='", 
		"'-='", "'&='"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, "TOKEN_COMMA", "TOKEN_DOT", "TOKEN_SEMICOLON", "TOKEN_LPAREN", "TOKEN_RPAREN", 
		"TOKEN_PLUS", "TOKEN_MINUS", "TOKEN_INTERSECT", "TOKEN_OR", "TOKEN_AND", 
		"TOKEN_NOT", "TOKEN_LIKE", "TOKEN_EQUAL", "TOKEN_NOT_EQUAL", "TOKEN_LESS", 
		"TOKEN_GREATER", "TOKEN_LESS_EQUAL", "TOKEN_GREATER_EQUAL", "TOKEN_REGEX", 
		"TOKEN_ASSIGN", "TOKEN_PLUS_ASSIGN", "TOKEN_MINUS_ASSIGN", "TOKEN_INTERSECT_ASSIGN", 
		"TOKEN_NAME", "TOKEN_NUMBER", "TOKEN_SINGLE_QUOTED_STRING_LITERAL", "TOKEN_DOUBLE_QUOTED_NAME", 
		"TOKEN_GRAPH_VARIABLE", "TOKEN_GRAPH_METADATA_VARIABLE", "TOKEN_COMMENTS", 
		"TOKEN_WS"
	};
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "DSL.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }


	  private static String StripQuotedStringLiteral(String input) {
	    StringBuilder sb = new StringBuilder();
	    for (int i = 1; i < input.length() - 1; ++i) {
	      char c = input.charAt(i);
	      if (c == '\\') {
	        char ec = input.charAt(++i);
	        switch (ec) {
	          case 'b':
	            // Backslash.
	            sb.append('\b');
	            break;
	          case 'n':
	            // Newline.
	            sb.append('\n');
	            break;
	          case 'r':
	            // Carriage return.
	            sb.append('\r');
	            break;
	          case 't':
	            // Tab.
	            sb.append('\t');
	            break;
	          default:
	            sb.append(ec);
	            break;
	        }
	      } else {
	        sb.append(c);
	      }
	    }
	    return sb.toString();
	  }

	public DSLParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class ProgramContext extends ParserRuleContext {
		public ParseProgram r;
		public StatementContext h;
		public StatementContext t;
		public TerminalNode EOF() { return getToken(DSLParser.EOF, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public List<TerminalNode> TOKEN_SEMICOLON() { return getTokens(DSLParser.TOKEN_SEMICOLON); }
		public TerminalNode TOKEN_SEMICOLON(int i) {
			return getToken(DSLParser.TOKEN_SEMICOLON, i);
		}
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{

			  ((ProgramContext)_localctx).r =  new ParseProgram(0, 0);

			setState(45);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << TOKEN_NAME) | (1L << TOKEN_DOUBLE_QUOTED_NAME) | (1L << TOKEN_GRAPH_VARIABLE) | (1L << TOKEN_GRAPH_METADATA_VARIABLE))) != 0)) {
				{
				setState(31);
				((ProgramContext)_localctx).h = statement();

				    _localctx.r.addStatement(((ProgramContext)_localctx).h.r);
				  
				setState(39);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(33);
						match(TOKEN_SEMICOLON);
						setState(34);
						((ProgramContext)_localctx).t = statement();

						      _localctx.r.addStatement(((ProgramContext)_localctx).t.r);
						    
						}
						} 
					}
					setState(41);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
				}
				setState(43);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==TOKEN_SEMICOLON) {
					{
					setState(42);
					match(TOKEN_SEMICOLON);
					}
				}

				}
			}

			setState(47);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StatementContext extends ParserRuleContext {
		public ParseStatement r;
		public VariableContext v;
		public Token t;
		public ExpressionContext e;
		public NameContext n;
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode TOKEN_ASSIGN() { return getToken(DSLParser.TOKEN_ASSIGN, 0); }
		public TerminalNode TOKEN_PLUS_ASSIGN() { return getToken(DSLParser.TOKEN_PLUS_ASSIGN, 0); }
		public TerminalNode TOKEN_MINUS_ASSIGN() { return getToken(DSLParser.TOKEN_MINUS_ASSIGN, 0); }
		public TerminalNode TOKEN_INTERSECT_ASSIGN() { return getToken(DSLParser.TOKEN_INTERSECT_ASSIGN, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_statement);
		int _la;
		try {
			setState(66);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOKEN_GRAPH_VARIABLE:
			case TOKEN_GRAPH_METADATA_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(49);
				((StatementContext)_localctx).v = variable();
				setState(50);
				((StatementContext)_localctx).t = _input.LT(1);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << TOKEN_ASSIGN) | (1L << TOKEN_PLUS_ASSIGN) | (1L << TOKEN_MINUS_ASSIGN) | (1L << TOKEN_INTERSECT_ASSIGN))) != 0)) ) {
					((StatementContext)_localctx).t = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(51);
				((StatementContext)_localctx).e = expression();

				  ((StatementContext)_localctx).r =  new ParseAssignment(((StatementContext)_localctx).v.r.getLineNumber(), ((StatementContext)_localctx).v.r.getColumnNumber(),
				                           ParseAssignment.ResolveAssignmentType((((StatementContext)_localctx).t!=null?((StatementContext)_localctx).t.getText():null)),
				                           ((StatementContext)_localctx).v.r, ((StatementContext)_localctx).e.r);

				}
				break;
			case TOKEN_NAME:
			case TOKEN_DOUBLE_QUOTED_NAME:
				enterOuterAlt(_localctx, 2);
				{
				setState(54);
				((StatementContext)_localctx).n = name();

				  ParseCommand command =
				      new ParseCommand(((StatementContext)_localctx).n.r.getLineNumber(), ((StatementContext)_localctx).n.r.getColumnNumber(), ((StatementContext)_localctx).n.r.getName());

				setState(61);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << TOKEN_LPAREN) | (1L << TOKEN_NOT) | (1L << TOKEN_NAME) | (1L << TOKEN_NUMBER) | (1L << TOKEN_SINGLE_QUOTED_STRING_LITERAL) | (1L << TOKEN_DOUBLE_QUOTED_NAME) | (1L << TOKEN_GRAPH_VARIABLE) | (1L << TOKEN_GRAPH_METADATA_VARIABLE))) != 0)) {
					{
					{
					setState(56);
					((StatementContext)_localctx).e = expression();

					    command.addArgument(((StatementContext)_localctx).e.r);
					  
					}
					}
					setState(63);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}

				  ((StatementContext)_localctx).r =  command;

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionContext extends ParserRuleContext {
		public ParseExpression r;
		public Add_expressionContext add_expression;
		public Or_expressionContext or_expression;
		public Add_expressionContext add_expression() {
			return getRuleContext(Add_expressionContext.class,0);
		}
		public Or_expressionContext or_expression() {
			return getRuleContext(Or_expressionContext.class,0);
		}
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	}

	public final ExpressionContext expression() throws RecognitionException {
		ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_expression);
		try {
			setState(74);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(68);
				((ExpressionContext)_localctx).add_expression = add_expression();

				  ((ExpressionContext)_localctx).r =  ((ExpressionContext)_localctx).add_expression.r;

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(71);
				((ExpressionContext)_localctx).or_expression = or_expression();

				  ((ExpressionContext)_localctx).r =  ((ExpressionContext)_localctx).or_expression.r;

				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Or_expressionContext extends ParserRuleContext {
		public ParseExpression r;
		public And_expressionContext lhs;
		public Token t;
		public And_expressionContext rhs;
		public List<And_expressionContext> and_expression() {
			return getRuleContexts(And_expressionContext.class);
		}
		public And_expressionContext and_expression(int i) {
			return getRuleContext(And_expressionContext.class,i);
		}
		public List<TerminalNode> TOKEN_OR() { return getTokens(DSLParser.TOKEN_OR); }
		public TerminalNode TOKEN_OR(int i) {
			return getToken(DSLParser.TOKEN_OR, i);
		}
		public Or_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_or_expression; }
	}

	public final Or_expressionContext or_expression() throws RecognitionException {
		Or_expressionContext _localctx = new Or_expressionContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_or_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(76);
			((Or_expressionContext)_localctx).lhs = and_expression();

			  ((Or_expressionContext)_localctx).r =  ((Or_expressionContext)_localctx).lhs.r;

			setState(84);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==TOKEN_OR) {
				{
				{
				setState(78);
				((Or_expressionContext)_localctx).t = match(TOKEN_OR);
				setState(79);
				((Or_expressionContext)_localctx).rhs = and_expression();

				    ParseString operator =
				        new ParseString(((Or_expressionContext)_localctx).t.getLine(), ((Or_expressionContext)_localctx).t.getCharPositionInLine(), (((Or_expressionContext)_localctx).t!=null?((Or_expressionContext)_localctx).t.getText():null));
				    ParseOperation operation =
				        new ParseOperation(((Or_expressionContext)_localctx).t.getLine(), ((Or_expressionContext)_localctx).t.getCharPositionInLine(), null, operator);
				    operation.addOperand(_localctx.r);
				    operation.addOperand(((Or_expressionContext)_localctx).rhs.r);
				    ((Or_expressionContext)_localctx).r =  operation;
				  
				}
				}
				setState(86);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class And_expressionContext extends ParserRuleContext {
		public ParseExpression r;
		public Not_expressionContext lhs;
		public Token t;
		public Not_expressionContext rhs;
		public List<Not_expressionContext> not_expression() {
			return getRuleContexts(Not_expressionContext.class);
		}
		public Not_expressionContext not_expression(int i) {
			return getRuleContext(Not_expressionContext.class,i);
		}
		public List<TerminalNode> TOKEN_AND() { return getTokens(DSLParser.TOKEN_AND); }
		public TerminalNode TOKEN_AND(int i) {
			return getToken(DSLParser.TOKEN_AND, i);
		}
		public And_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_and_expression; }
	}

	public final And_expressionContext and_expression() throws RecognitionException {
		And_expressionContext _localctx = new And_expressionContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_and_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(87);
			((And_expressionContext)_localctx).lhs = not_expression();

			  ((And_expressionContext)_localctx).r =  ((And_expressionContext)_localctx).lhs.r;

			setState(95);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==TOKEN_AND) {
				{
				{
				setState(89);
				((And_expressionContext)_localctx).t = match(TOKEN_AND);
				setState(90);
				((And_expressionContext)_localctx).rhs = not_expression();

				    ParseString operator =
				        new ParseString(((And_expressionContext)_localctx).t.getLine(), ((And_expressionContext)_localctx).t.getCharPositionInLine(), (((And_expressionContext)_localctx).t!=null?((And_expressionContext)_localctx).t.getText():null));
				    ParseOperation operation =
				        new ParseOperation(((And_expressionContext)_localctx).t.getLine(), ((And_expressionContext)_localctx).t.getCharPositionInLine(), null, operator);
				    operation.addOperand(_localctx.r);
				    operation.addOperand(((And_expressionContext)_localctx).rhs.r);
				    ((And_expressionContext)_localctx).r =  operation;
				  
				}
				}
				setState(97);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Not_expressionContext extends ParserRuleContext {
		public ParseExpression r;
		public Token t;
		public Comparison_expressionContext c;
		public Function_callContext f;
		public Comparison_expressionContext comparison_expression() {
			return getRuleContext(Comparison_expressionContext.class,0);
		}
		public Function_callContext function_call() {
			return getRuleContext(Function_callContext.class,0);
		}
		public TerminalNode TOKEN_NOT() { return getToken(DSLParser.TOKEN_NOT, 0); }
		public Not_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_not_expression; }
	}

	public final Not_expressionContext not_expression() throws RecognitionException {
		Not_expressionContext _localctx = new Not_expressionContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_not_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			  ParseString operator = null;
			  ParseExpression operand = null;

			setState(101);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==TOKEN_NOT) {
				{
				setState(99);
				((Not_expressionContext)_localctx).t = match(TOKEN_NOT);

				    operator = new ParseString(((Not_expressionContext)_localctx).t.getLine(), ((Not_expressionContext)_localctx).t.getCharPositionInLine(), (((Not_expressionContext)_localctx).t!=null?((Not_expressionContext)_localctx).t.getText():null));
				  
				}
			}

			setState(109);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,9,_ctx) ) {
			case 1:
				{
				setState(103);
				((Not_expressionContext)_localctx).c = comparison_expression();

				  	operand = ((Not_expressionContext)_localctx).c.r;
				  
				}
				break;
			case 2:
				{
				setState(106);
				((Not_expressionContext)_localctx).f = function_call();

				  	operand = ((Not_expressionContext)_localctx).f.r;
				  
				}
				break;
			}

			  if (operator == null) {
			    ((Not_expressionContext)_localctx).r =  operand;
			  } else {
			    ParseOperation operation =
			        new ParseOperation(((Not_expressionContext)_localctx).t.getLine(), ((Not_expressionContext)_localctx).t.getCharPositionInLine(), null, operator);
			    operation.addOperand(operand);
			    ((Not_expressionContext)_localctx).r =  operation;
			  }

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Comparison_expressionContext extends ParserRuleContext {
		public ParseExpression r;
		public Add_expressionContext lhs;
		public Token n;
		public Token t;
		public Add_expressionContext rhs;
		public List<Add_expressionContext> add_expression() {
			return getRuleContexts(Add_expressionContext.class);
		}
		public Add_expressionContext add_expression(int i) {
			return getRuleContext(Add_expressionContext.class,i);
		}
		public TerminalNode TOKEN_LIKE() { return getToken(DSLParser.TOKEN_LIKE, 0); }
		public TerminalNode TOKEN_EQUAL() { return getToken(DSLParser.TOKEN_EQUAL, 0); }
		public TerminalNode TOKEN_ASSIGN() { return getToken(DSLParser.TOKEN_ASSIGN, 0); }
		public TerminalNode TOKEN_NOT_EQUAL() { return getToken(DSLParser.TOKEN_NOT_EQUAL, 0); }
		public TerminalNode TOKEN_REGEX() { return getToken(DSLParser.TOKEN_REGEX, 0); }
		public TerminalNode TOKEN_LESS() { return getToken(DSLParser.TOKEN_LESS, 0); }
		public TerminalNode TOKEN_GREATER() { return getToken(DSLParser.TOKEN_GREATER, 0); }
		public TerminalNode TOKEN_LESS_EQUAL() { return getToken(DSLParser.TOKEN_LESS_EQUAL, 0); }
		public TerminalNode TOKEN_GREATER_EQUAL() { return getToken(DSLParser.TOKEN_GREATER_EQUAL, 0); }
		public TerminalNode TOKEN_NOT() { return getToken(DSLParser.TOKEN_NOT, 0); }
		public Comparison_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparison_expression; }
	}

	public final Comparison_expressionContext comparison_expression() throws RecognitionException {
		Comparison_expressionContext _localctx = new Comparison_expressionContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_comparison_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			  ParseString negateOp = null;

			setState(114);
			((Comparison_expressionContext)_localctx).lhs = add_expression();
			setState(117);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==TOKEN_NOT) {
				{
				setState(115);
				((Comparison_expressionContext)_localctx).n = match(TOKEN_NOT);

				    negateOp = new ParseString(((Comparison_expressionContext)_localctx).n.getLine(), ((Comparison_expressionContext)_localctx).n.getCharPositionInLine(), (((Comparison_expressionContext)_localctx).n!=null?((Comparison_expressionContext)_localctx).n.getText():null));
				  
				}
			}

			setState(119);
			((Comparison_expressionContext)_localctx).t = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << TOKEN_LIKE) | (1L << TOKEN_EQUAL) | (1L << TOKEN_NOT_EQUAL) | (1L << TOKEN_LESS) | (1L << TOKEN_GREATER) | (1L << TOKEN_LESS_EQUAL) | (1L << TOKEN_GREATER_EQUAL) | (1L << TOKEN_REGEX) | (1L << TOKEN_ASSIGN))) != 0)) ) {
				((Comparison_expressionContext)_localctx).t = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(120);
			((Comparison_expressionContext)_localctx).rhs = add_expression();

			  ParseString comparator =
			      new ParseString(((Comparison_expressionContext)_localctx).t.getLine(), ((Comparison_expressionContext)_localctx).t.getCharPositionInLine(), (((Comparison_expressionContext)_localctx).t!=null?((Comparison_expressionContext)_localctx).t.getText():null));
			  ParseOperation comparison =
			      new ParseOperation(((Comparison_expressionContext)_localctx).t.getLine(), ((Comparison_expressionContext)_localctx).t.getCharPositionInLine(), null, comparator);
			  comparison.addOperand(((Comparison_expressionContext)_localctx).lhs.r);
			  comparison.addOperand(((Comparison_expressionContext)_localctx).rhs.r);
			  ((Comparison_expressionContext)_localctx).r =  comparison;

			  if (negateOp != null) {
			  ParseOperation operation =
			        new ParseOperation(negateOp.getLineNumber(), negateOp.getColumnNumber(),
			                           null, negateOp);
			    operation.addOperand(_localctx.r);
			    ((Comparison_expressionContext)_localctx).r =  operation;
			  }

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Add_expressionContext extends ParserRuleContext {
		public ParseExpression r;
		public Intersect_expressionContext lhs;
		public Token t;
		public Intersect_expressionContext rhs;
		public List<Intersect_expressionContext> intersect_expression() {
			return getRuleContexts(Intersect_expressionContext.class);
		}
		public Intersect_expressionContext intersect_expression(int i) {
			return getRuleContext(Intersect_expressionContext.class,i);
		}
		public List<TerminalNode> TOKEN_PLUS() { return getTokens(DSLParser.TOKEN_PLUS); }
		public TerminalNode TOKEN_PLUS(int i) {
			return getToken(DSLParser.TOKEN_PLUS, i);
		}
		public List<TerminalNode> TOKEN_MINUS() { return getTokens(DSLParser.TOKEN_MINUS); }
		public TerminalNode TOKEN_MINUS(int i) {
			return getToken(DSLParser.TOKEN_MINUS, i);
		}
		public Add_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_add_expression; }
	}

	public final Add_expressionContext add_expression() throws RecognitionException {
		Add_expressionContext _localctx = new Add_expressionContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_add_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(123);
			((Add_expressionContext)_localctx).lhs = intersect_expression();

			  ((Add_expressionContext)_localctx).r =  ((Add_expressionContext)_localctx).lhs.r;

			setState(131);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==TOKEN_PLUS || _la==TOKEN_MINUS) {
				{
				{
				setState(125);
				((Add_expressionContext)_localctx).t = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==TOKEN_PLUS || _la==TOKEN_MINUS) ) {
					((Add_expressionContext)_localctx).t = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(126);
				((Add_expressionContext)_localctx).rhs = intersect_expression();

				    ParseString operator =
				        new ParseString(((Add_expressionContext)_localctx).t.getLine(), ((Add_expressionContext)_localctx).t.getCharPositionInLine(), (((Add_expressionContext)_localctx).t!=null?((Add_expressionContext)_localctx).t.getText():null));
				    ParseOperation operation =
				        new ParseOperation(((Add_expressionContext)_localctx).t.getLine(), ((Add_expressionContext)_localctx).t.getCharPositionInLine(), null, operator);
				    operation.addOperand(_localctx.r);
				    operation.addOperand(((Add_expressionContext)_localctx).rhs.r);
				    ((Add_expressionContext)_localctx).r =  operation;
				  
				}
				}
				setState(133);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Intersect_expressionContext extends ParserRuleContext {
		public ParseExpression r;
		public Function_callContext lhs;
		public Token t;
		public Function_callContext rhs;
		public List<Function_callContext> function_call() {
			return getRuleContexts(Function_callContext.class);
		}
		public Function_callContext function_call(int i) {
			return getRuleContext(Function_callContext.class,i);
		}
		public List<TerminalNode> TOKEN_INTERSECT() { return getTokens(DSLParser.TOKEN_INTERSECT); }
		public TerminalNode TOKEN_INTERSECT(int i) {
			return getToken(DSLParser.TOKEN_INTERSECT, i);
		}
		public Intersect_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_intersect_expression; }
	}

	public final Intersect_expressionContext intersect_expression() throws RecognitionException {
		Intersect_expressionContext _localctx = new Intersect_expressionContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_intersect_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(134);
			((Intersect_expressionContext)_localctx).lhs = function_call();

			  ((Intersect_expressionContext)_localctx).r =  ((Intersect_expressionContext)_localctx).lhs.r;

			setState(142);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==TOKEN_INTERSECT) {
				{
				{
				setState(136);
				((Intersect_expressionContext)_localctx).t = match(TOKEN_INTERSECT);
				setState(137);
				((Intersect_expressionContext)_localctx).rhs = function_call();

				    ParseString operator =
				        new ParseString(((Intersect_expressionContext)_localctx).t.getLine(), ((Intersect_expressionContext)_localctx).t.getCharPositionInLine(), (((Intersect_expressionContext)_localctx).t!=null?((Intersect_expressionContext)_localctx).t.getText():null));
				    ParseOperation operation =
				        new ParseOperation(((Intersect_expressionContext)_localctx).t.getLine(), ((Intersect_expressionContext)_localctx).t.getCharPositionInLine(), null, operator);
				    operation.addOperand(_localctx.r);
				    operation.addOperand(((Intersect_expressionContext)_localctx).rhs.r);
				    ((Intersect_expressionContext)_localctx).r =  operation;
				  
				}
				}
				setState(144);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Function_callContext extends ParserRuleContext {
		public ParseExpression r;
		public FactorContext factor;
		public FactorContext s;
		public NameContext f;
		public Argument_listContext al;
		public FactorContext factor() {
			return getRuleContext(FactorContext.class,0);
		}
		public List<TerminalNode> TOKEN_LPAREN() { return getTokens(DSLParser.TOKEN_LPAREN); }
		public TerminalNode TOKEN_LPAREN(int i) {
			return getToken(DSLParser.TOKEN_LPAREN, i);
		}
		public List<TerminalNode> TOKEN_RPAREN() { return getTokens(DSLParser.TOKEN_RPAREN); }
		public TerminalNode TOKEN_RPAREN(int i) {
			return getToken(DSLParser.TOKEN_RPAREN, i);
		}
		public List<NameContext> name() {
			return getRuleContexts(NameContext.class);
		}
		public NameContext name(int i) {
			return getRuleContext(NameContext.class,i);
		}
		public List<TerminalNode> TOKEN_DOT() { return getTokens(DSLParser.TOKEN_DOT); }
		public TerminalNode TOKEN_DOT(int i) {
			return getToken(DSLParser.TOKEN_DOT, i);
		}
		public List<Argument_listContext> argument_list() {
			return getRuleContexts(Argument_listContext.class);
		}
		public Argument_listContext argument_list(int i) {
			return getRuleContext(Argument_listContext.class,i);
		}
		public Function_callContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function_call; }
	}

	public final Function_callContext function_call() throws RecognitionException {
		Function_callContext _localctx = new Function_callContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_function_call);
		int _la;
		try {
			setState(181);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,17,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(145);
				((Function_callContext)_localctx).factor = factor();

				  ((Function_callContext)_localctx).r =  ((Function_callContext)_localctx).factor.r;

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{

				  ParseExpression subject = null;
				  ArrayList<ParseExpression> arguments = null;

				setState(153);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,13,_ctx) ) {
				case 1:
					{
					setState(149);
					((Function_callContext)_localctx).s = factor();

					    subject = ((Function_callContext)_localctx).s.r;
					  
					setState(151);
					match(TOKEN_DOT);
					}
					break;
				}
				setState(155);
				((Function_callContext)_localctx).f = name();
				setState(156);
				match(TOKEN_LPAREN);
				setState(160);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << TOKEN_LPAREN) | (1L << TOKEN_NOT) | (1L << TOKEN_NAME) | (1L << TOKEN_NUMBER) | (1L << TOKEN_SINGLE_QUOTED_STRING_LITERAL) | (1L << TOKEN_DOUBLE_QUOTED_NAME) | (1L << TOKEN_GRAPH_VARIABLE) | (1L << TOKEN_GRAPH_METADATA_VARIABLE))) != 0)) {
					{
					setState(157);
					((Function_callContext)_localctx).al = argument_list();

					    arguments = ((Function_callContext)_localctx).al.r;
					  
					}
				}

				setState(162);
				match(TOKEN_RPAREN);

				  ParseOperation operation =
				      new ParseOperation(((Function_callContext)_localctx).f.r.getLineNumber(), ((Function_callContext)_localctx).f.r.getColumnNumber(), subject, ((Function_callContext)_localctx).f.r.getName());
				  if (arguments != null) {
				    for (ParseExpression e : arguments) {
				      operation.addOperand(e);
				    }
				  }
				  ((Function_callContext)_localctx).r =  operation;

				setState(178);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==TOKEN_DOT) {
					{
					{
					setState(164);
					match(TOKEN_DOT);
					setState(165);
					((Function_callContext)_localctx).f = name();
					setState(166);
					match(TOKEN_LPAREN);

					    arguments = null;
					  
					setState(171);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << TOKEN_LPAREN) | (1L << TOKEN_NOT) | (1L << TOKEN_NAME) | (1L << TOKEN_NUMBER) | (1L << TOKEN_SINGLE_QUOTED_STRING_LITERAL) | (1L << TOKEN_DOUBLE_QUOTED_NAME) | (1L << TOKEN_GRAPH_VARIABLE) | (1L << TOKEN_GRAPH_METADATA_VARIABLE))) != 0)) {
						{
						setState(168);
						((Function_callContext)_localctx).al = argument_list();

						      arguments = ((Function_callContext)_localctx).al.r;
						    
						}
					}

					setState(173);
					match(TOKEN_RPAREN);

					    ParseOperation ro =
					        new ParseOperation(((Function_callContext)_localctx).f.r.getLineNumber(), ((Function_callContext)_localctx).f.r.getColumnNumber(), _localctx.r, ((Function_callContext)_localctx).f.r.getName());
					    if (arguments != null) {
					      for (ParseExpression e : arguments) {
					        ro.addOperand(e);
					      }
					    }
					    ((Function_callContext)_localctx).r =  ro;
					  
					}
					}
					setState(180);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FactorContext extends ParserRuleContext {
		public ParseExpression r;
		public LiteralContext literal;
		public VariableContext variable;
		public NameContext name;
		public ExpressionContext expression;
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public TerminalNode TOKEN_LPAREN() { return getToken(DSLParser.TOKEN_LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode TOKEN_RPAREN() { return getToken(DSLParser.TOKEN_RPAREN, 0); }
		public FactorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_factor; }
	}

	public final FactorContext factor() throws RecognitionException {
		FactorContext _localctx = new FactorContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_factor);
		try {
			setState(197);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOKEN_NUMBER:
			case TOKEN_SINGLE_QUOTED_STRING_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(183);
				((FactorContext)_localctx).literal = literal();

				  ((FactorContext)_localctx).r =  ((FactorContext)_localctx).literal.r;

				}
				break;
			case TOKEN_GRAPH_VARIABLE:
			case TOKEN_GRAPH_METADATA_VARIABLE:
				enterOuterAlt(_localctx, 2);
				{
				setState(186);
				((FactorContext)_localctx).variable = variable();

				  ((FactorContext)_localctx).r =  ((FactorContext)_localctx).variable.r;

				}
				break;
			case TOKEN_NAME:
			case TOKEN_DOUBLE_QUOTED_NAME:
				enterOuterAlt(_localctx, 3);
				{
				setState(189);
				((FactorContext)_localctx).name = name();

				  ((FactorContext)_localctx).r =  ((FactorContext)_localctx).name.r;

				}
				break;
			case TOKEN_LPAREN:
				enterOuterAlt(_localctx, 4);
				{
				setState(192);
				match(TOKEN_LPAREN);
				setState(193);
				((FactorContext)_localctx).expression = expression();
				setState(194);
				match(TOKEN_RPAREN);

				  ((FactorContext)_localctx).r =  ((FactorContext)_localctx).expression.r;

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Argument_listContext extends ParserRuleContext {
		public ArrayList<ParseExpression> r;
		public ExpressionContext e;
		public ExpressionContext t;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> TOKEN_COMMA() { return getTokens(DSLParser.TOKEN_COMMA); }
		public TerminalNode TOKEN_COMMA(int i) {
			return getToken(DSLParser.TOKEN_COMMA, i);
		}
		public Argument_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argument_list; }
	}

	public final Argument_listContext argument_list() throws RecognitionException {
		Argument_listContext _localctx = new Argument_listContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_argument_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(199);
			((Argument_listContext)_localctx).e = expression();

			  ((Argument_listContext)_localctx).r =  new ArrayList<ParseExpression>();
			  _localctx.r.add(((Argument_listContext)_localctx).e.r);

			setState(207);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==TOKEN_COMMA) {
				{
				{
				setState(201);
				match(TOKEN_COMMA);
				setState(202);
				((Argument_listContext)_localctx).t = expression();

				    _localctx.r.add(((Argument_listContext)_localctx).t.r);
				  
				}
				}
				setState(209);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NameContext extends ParserRuleContext {
		public ParseName r;
		public Token n;
		public TerminalNode TOKEN_NAME() { return getToken(DSLParser.TOKEN_NAME, 0); }
		public TerminalNode TOKEN_DOUBLE_QUOTED_NAME() { return getToken(DSLParser.TOKEN_DOUBLE_QUOTED_NAME, 0); }
		public NameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name; }
	}

	public final NameContext name() throws RecognitionException {
		NameContext _localctx = new NameContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_name);
		try {
			setState(214);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOKEN_NAME:
				enterOuterAlt(_localctx, 1);
				{
				setState(210);
				((NameContext)_localctx).n = match(TOKEN_NAME);

				  ParseString name = new ParseString(((NameContext)_localctx).n.getLine(), ((NameContext)_localctx).n.getCharPositionInLine(), (((NameContext)_localctx).n!=null?((NameContext)_localctx).n.getText():null));
				  ((NameContext)_localctx).r =  new ParseName(((NameContext)_localctx).n.getLine(), ((NameContext)_localctx).n.getCharPositionInLine(), name);

				}
				break;
			case TOKEN_DOUBLE_QUOTED_NAME:
				enterOuterAlt(_localctx, 2);
				{
				setState(212);
				((NameContext)_localctx).n = match(TOKEN_DOUBLE_QUOTED_NAME);

				  String value = StripQuotedStringLiteral((((NameContext)_localctx).n!=null?((NameContext)_localctx).n.getText():null));
				  ParseString name = new ParseString(((NameContext)_localctx).n.getLine(), ((NameContext)_localctx).n.getCharPositionInLine(), value);
				  ((NameContext)_localctx).r =  new ParseName(((NameContext)_localctx).n.getLine(), ((NameContext)_localctx).n.getCharPositionInLine(), name);

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VariableContext extends ParserRuleContext {
		public ParseVariable r;
		public Token v;
		public TerminalNode TOKEN_GRAPH_VARIABLE() { return getToken(DSLParser.TOKEN_GRAPH_VARIABLE, 0); }
		public TerminalNode TOKEN_GRAPH_METADATA_VARIABLE() { return getToken(DSLParser.TOKEN_GRAPH_METADATA_VARIABLE, 0); }
		public VariableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variable; }
	}

	public final VariableContext variable() throws RecognitionException {
		VariableContext _localctx = new VariableContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_variable);
		try {
			setState(220);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOKEN_GRAPH_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(216);
				((VariableContext)_localctx).v = match(TOKEN_GRAPH_VARIABLE);

				  ParseString name = new ParseString(((VariableContext)_localctx).v.getLine(), ((VariableContext)_localctx).v.getCharPositionInLine(), (((VariableContext)_localctx).v!=null?((VariableContext)_localctx).v.getText():null));
				  ((VariableContext)_localctx).r =  new ParseVariable(((VariableContext)_localctx).v.getLine(), ((VariableContext)_localctx).v.getCharPositionInLine(),
				                         name, GraphType.GetInstance());

				}
				break;
			case TOKEN_GRAPH_METADATA_VARIABLE:
				enterOuterAlt(_localctx, 2);
				{
				setState(218);
				((VariableContext)_localctx).v = match(TOKEN_GRAPH_METADATA_VARIABLE);

				  ParseString name = new ParseString(((VariableContext)_localctx).v.getLine(), ((VariableContext)_localctx).v.getCharPositionInLine(), (((VariableContext)_localctx).v!=null?((VariableContext)_localctx).v.getText():null));
				  ((VariableContext)_localctx).r =  new ParseVariable(((VariableContext)_localctx).v.getLine(), ((VariableContext)_localctx).v.getCharPositionInLine(),
				                         name, GraphMetadataType.GetInstance());

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LiteralContext extends ParserRuleContext {
		public ParseLiteral r;
		public Token t;
		public TerminalNode TOKEN_NUMBER() { return getToken(DSLParser.TOKEN_NUMBER, 0); }
		public TerminalNode TOKEN_SINGLE_QUOTED_STRING_LITERAL() { return getToken(DSLParser.TOKEN_SINGLE_QUOTED_STRING_LITERAL, 0); }
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_literal);
		try {
			setState(226);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOKEN_NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(222);
				((LiteralContext)_localctx).t = match(TOKEN_NUMBER);

				  Integer value = Integer.parseInt((((LiteralContext)_localctx).t!=null?((LiteralContext)_localctx).t.getText():null));
				  ((LiteralContext)_localctx).r =  new ParseLiteral(((LiteralContext)_localctx).t.getLine(), ((LiteralContext)_localctx).t.getCharPositionInLine(),
				                        new TypedValue(IntegerType.GetInstance(), value));

				}
				break;
			case TOKEN_SINGLE_QUOTED_STRING_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(224);
				((LiteralContext)_localctx).t = match(TOKEN_SINGLE_QUOTED_STRING_LITERAL);

				  String value = StripQuotedStringLiteral((((LiteralContext)_localctx).t!=null?((LiteralContext)_localctx).t.getText():null));
				  ((LiteralContext)_localctx).r =  new ParseLiteral(((LiteralContext)_localctx).t.getLine(), ((LiteralContext)_localctx).t.getCharPositionInLine(),
				                        new TypedValue(StringType.GetInstance(), value));

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3!\u00e7\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\3\2\3\2\3\2\3\2\3\2"+
		"\3\2\3\2\7\2(\n\2\f\2\16\2+\13\2\3\2\5\2.\n\2\5\2\60\n\2\3\2\3\2\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\7\3>\n\3\f\3\16\3A\13\3\3\3\3\3\5\3"+
		"E\n\3\3\4\3\4\3\4\3\4\3\4\3\4\5\4M\n\4\3\5\3\5\3\5\3\5\3\5\3\5\7\5U\n"+
		"\5\f\5\16\5X\13\5\3\6\3\6\3\6\3\6\3\6\3\6\7\6`\n\6\f\6\16\6c\13\6\3\7"+
		"\3\7\3\7\5\7h\n\7\3\7\3\7\3\7\3\7\3\7\3\7\5\7p\n\7\3\7\3\7\3\b\3\b\3\b"+
		"\3\b\5\bx\n\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\t\7\t\u0084\n\t\f"+
		"\t\16\t\u0087\13\t\3\n\3\n\3\n\3\n\3\n\3\n\7\n\u008f\n\n\f\n\16\n\u0092"+
		"\13\n\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\5\13\u009c\n\13\3\13\3\13"+
		"\3\13\3\13\3\13\5\13\u00a3\n\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13"+
		"\3\13\5\13\u00ae\n\13\3\13\3\13\3\13\7\13\u00b3\n\13\f\13\16\13\u00b6"+
		"\13\13\5\13\u00b8\n\13\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f"+
		"\3\f\3\f\5\f\u00c8\n\f\3\r\3\r\3\r\3\r\3\r\3\r\7\r\u00d0\n\r\f\r\16\r"+
		"\u00d3\13\r\3\16\3\16\3\16\3\16\5\16\u00d9\n\16\3\17\3\17\3\17\3\17\5"+
		"\17\u00df\n\17\3\20\3\20\3\20\3\20\5\20\u00e5\n\20\3\20\2\2\21\2\4\6\b"+
		"\n\f\16\20\22\24\26\30\32\34\36\2\5\3\2\26\31\3\2\16\26\3\2\b\t\2\u00f0"+
		"\2 \3\2\2\2\4D\3\2\2\2\6L\3\2\2\2\bN\3\2\2\2\nY\3\2\2\2\fd\3\2\2\2\16"+
		"s\3\2\2\2\20}\3\2\2\2\22\u0088\3\2\2\2\24\u00b7\3\2\2\2\26\u00c7\3\2\2"+
		"\2\30\u00c9\3\2\2\2\32\u00d8\3\2\2\2\34\u00de\3\2\2\2\36\u00e4\3\2\2\2"+
		" /\b\2\1\2!\"\5\4\3\2\")\b\2\1\2#$\7\5\2\2$%\5\4\3\2%&\b\2\1\2&(\3\2\2"+
		"\2\'#\3\2\2\2(+\3\2\2\2)\'\3\2\2\2)*\3\2\2\2*-\3\2\2\2+)\3\2\2\2,.\7\5"+
		"\2\2-,\3\2\2\2-.\3\2\2\2.\60\3\2\2\2/!\3\2\2\2/\60\3\2\2\2\60\61\3\2\2"+
		"\2\61\62\7\2\2\3\62\3\3\2\2\2\63\64\5\34\17\2\64\65\t\2\2\2\65\66\5\6"+
		"\4\2\66\67\b\3\1\2\67E\3\2\2\289\5\32\16\29?\b\3\1\2:;\5\6\4\2;<\b\3\1"+
		"\2<>\3\2\2\2=:\3\2\2\2>A\3\2\2\2?=\3\2\2\2?@\3\2\2\2@B\3\2\2\2A?\3\2\2"+
		"\2BC\b\3\1\2CE\3\2\2\2D\63\3\2\2\2D8\3\2\2\2E\5\3\2\2\2FG\5\20\t\2GH\b"+
		"\4\1\2HM\3\2\2\2IJ\5\b\5\2JK\b\4\1\2KM\3\2\2\2LF\3\2\2\2LI\3\2\2\2M\7"+
		"\3\2\2\2NO\5\n\6\2OV\b\5\1\2PQ\7\13\2\2QR\5\n\6\2RS\b\5\1\2SU\3\2\2\2"+
		"TP\3\2\2\2UX\3\2\2\2VT\3\2\2\2VW\3\2\2\2W\t\3\2\2\2XV\3\2\2\2YZ\5\f\7"+
		"\2Za\b\6\1\2[\\\7\f\2\2\\]\5\f\7\2]^\b\6\1\2^`\3\2\2\2_[\3\2\2\2`c\3\2"+
		"\2\2a_\3\2\2\2ab\3\2\2\2b\13\3\2\2\2ca\3\2\2\2dg\b\7\1\2ef\7\r\2\2fh\b"+
		"\7\1\2ge\3\2\2\2gh\3\2\2\2ho\3\2\2\2ij\5\16\b\2jk\b\7\1\2kp\3\2\2\2lm"+
		"\5\24\13\2mn\b\7\1\2np\3\2\2\2oi\3\2\2\2ol\3\2\2\2pq\3\2\2\2qr\b\7\1\2"+
		"r\r\3\2\2\2st\b\b\1\2tw\5\20\t\2uv\7\r\2\2vx\b\b\1\2wu\3\2\2\2wx\3\2\2"+
		"\2xy\3\2\2\2yz\t\3\2\2z{\5\20\t\2{|\b\b\1\2|\17\3\2\2\2}~\5\22\n\2~\u0085"+
		"\b\t\1\2\177\u0080\t\4\2\2\u0080\u0081\5\22\n\2\u0081\u0082\b\t\1\2\u0082"+
		"\u0084\3\2\2\2\u0083\177\3\2\2\2\u0084\u0087\3\2\2\2\u0085\u0083\3\2\2"+
		"\2\u0085\u0086\3\2\2\2\u0086\21\3\2\2\2\u0087\u0085\3\2\2\2\u0088\u0089"+
		"\5\24\13\2\u0089\u0090\b\n\1\2\u008a\u008b\7\n\2\2\u008b\u008c\5\24\13"+
		"\2\u008c\u008d\b\n\1\2\u008d\u008f\3\2\2\2\u008e\u008a\3\2\2\2\u008f\u0092"+
		"\3\2\2\2\u0090\u008e\3\2\2\2\u0090\u0091\3\2\2\2\u0091\23\3\2\2\2\u0092"+
		"\u0090\3\2\2\2\u0093\u0094\5\26\f\2\u0094\u0095\b\13\1\2\u0095\u00b8\3"+
		"\2\2\2\u0096\u009b\b\13\1\2\u0097\u0098\5\26\f\2\u0098\u0099\b\13\1\2"+
		"\u0099\u009a\7\4\2\2\u009a\u009c\3\2\2\2\u009b\u0097\3\2\2\2\u009b\u009c"+
		"\3\2\2\2\u009c\u009d\3\2\2\2\u009d\u009e\5\32\16\2\u009e\u00a2\7\6\2\2"+
		"\u009f\u00a0\5\30\r\2\u00a0\u00a1\b\13\1\2\u00a1\u00a3\3\2\2\2\u00a2\u009f"+
		"\3\2\2\2\u00a2\u00a3\3\2\2\2\u00a3\u00a4\3\2\2\2\u00a4\u00a5\7\7\2\2\u00a5"+
		"\u00b4\b\13\1\2\u00a6\u00a7\7\4\2\2\u00a7\u00a8\5\32\16\2\u00a8\u00a9"+
		"\7\6\2\2\u00a9\u00ad\b\13\1\2\u00aa\u00ab\5\30\r\2\u00ab\u00ac\b\13\1"+
		"\2\u00ac\u00ae\3\2\2\2\u00ad\u00aa\3\2\2\2\u00ad\u00ae\3\2\2\2\u00ae\u00af"+
		"\3\2\2\2\u00af\u00b0\7\7\2\2\u00b0\u00b1\b\13\1\2\u00b1\u00b3\3\2\2\2"+
		"\u00b2\u00a6\3\2\2\2\u00b3\u00b6\3\2\2\2\u00b4\u00b2\3\2\2\2\u00b4\u00b5"+
		"\3\2\2\2\u00b5\u00b8\3\2\2\2\u00b6\u00b4\3\2\2\2\u00b7\u0093\3\2\2\2\u00b7"+
		"\u0096\3\2\2\2\u00b8\25\3\2\2\2\u00b9\u00ba\5\36\20\2\u00ba\u00bb\b\f"+
		"\1\2\u00bb\u00c8\3\2\2\2\u00bc\u00bd\5\34\17\2\u00bd\u00be\b\f\1\2\u00be"+
		"\u00c8\3\2\2\2\u00bf\u00c0\5\32\16\2\u00c0\u00c1\b\f\1\2\u00c1\u00c8\3"+
		"\2\2\2\u00c2\u00c3\7\6\2\2\u00c3\u00c4\5\6\4\2\u00c4\u00c5\7\7\2\2\u00c5"+
		"\u00c6\b\f\1\2\u00c6\u00c8\3\2\2\2\u00c7\u00b9\3\2\2\2\u00c7\u00bc\3\2"+
		"\2\2\u00c7\u00bf\3\2\2\2\u00c7\u00c2\3\2\2\2\u00c8\27\3\2\2\2\u00c9\u00ca"+
		"\5\6\4\2\u00ca\u00d1\b\r\1\2\u00cb\u00cc\7\3\2\2\u00cc\u00cd\5\6\4\2\u00cd"+
		"\u00ce\b\r\1\2\u00ce\u00d0\3\2\2\2\u00cf\u00cb\3\2\2\2\u00d0\u00d3\3\2"+
		"\2\2\u00d1\u00cf\3\2\2\2\u00d1\u00d2\3\2\2\2\u00d2\31\3\2\2\2\u00d3\u00d1"+
		"\3\2\2\2\u00d4\u00d5\7\32\2\2\u00d5\u00d9\b\16\1\2\u00d6\u00d7\7\35\2"+
		"\2\u00d7\u00d9\b\16\1\2\u00d8\u00d4\3\2\2\2\u00d8\u00d6\3\2\2\2\u00d9"+
		"\33\3\2\2\2\u00da\u00db\7\36\2\2\u00db\u00df\b\17\1\2\u00dc\u00dd\7\37"+
		"\2\2\u00dd\u00df\b\17\1\2\u00de\u00da\3\2\2\2\u00de\u00dc\3\2\2\2\u00df"+
		"\35\3\2\2\2\u00e0\u00e1\7\33\2\2\u00e1\u00e5\b\20\1\2\u00e2\u00e3\7\34"+
		"\2\2\u00e3\u00e5\b\20\1\2\u00e4\u00e0\3\2\2\2\u00e4\u00e2\3\2\2\2\u00e5"+
		"\37\3\2\2\2\31)-/?DLVagow\u0085\u0090\u009b\u00a2\u00ad\u00b4\u00b7\u00c7"+
		"\u00d1\u00d8\u00de\u00e4";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}