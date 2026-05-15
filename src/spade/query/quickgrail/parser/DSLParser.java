// Generated from DSL.g4 by ANTLR 4.13.2
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

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class DSLParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

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
		TOKEN_GRAPH_PREDICATE_VARIABLE=30, TOKEN_COMMENTS=31, TOKEN_WS=32;
	public static final int
		RULE_program = 0, RULE_statement = 1, RULE_expression = 2, RULE_or_expression = 3, 
		RULE_and_expression = 4, RULE_not_expression = 5, RULE_comparison_expression = 6, 
		RULE_add_expression = 7, RULE_intersect_expression = 8, RULE_function_call = 9, 
		RULE_factor = 10, RULE_argument_list = 11, RULE_name = 12, RULE_variable = 13, 
		RULE_literal = 14;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "statement", "expression", "or_expression", "and_expression", 
			"not_expression", "comparison_expression", "add_expression", "intersect_expression", 
			"function_call", "factor", "argument_list", "name", "variable", "literal"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "','", "'.'", "';'", "'('", "')'", "'+'", "'-'", "'&'", null, null, 
			null, null, "'=='", null, "'<'", "'>'", "'<='", "'>='", null, "'='", 
			"'+='", "'-='", "'&='"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "TOKEN_COMMA", "TOKEN_DOT", "TOKEN_SEMICOLON", "TOKEN_LPAREN", 
			"TOKEN_RPAREN", "TOKEN_PLUS", "TOKEN_MINUS", "TOKEN_INTERSECT", "TOKEN_OR", 
			"TOKEN_AND", "TOKEN_NOT", "TOKEN_LIKE", "TOKEN_EQUAL", "TOKEN_NOT_EQUAL", 
			"TOKEN_LESS", "TOKEN_GREATER", "TOKEN_LESS_EQUAL", "TOKEN_GREATER_EQUAL", 
			"TOKEN_REGEX", "TOKEN_ASSIGN", "TOKEN_PLUS_ASSIGN", "TOKEN_MINUS_ASSIGN", 
			"TOKEN_INTERSECT_ASSIGN", "TOKEN_NAME", "TOKEN_NUMBER", "TOKEN_SINGLE_QUOTED_STRING_LITERAL", 
			"TOKEN_DOUBLE_QUOTED_NAME", "TOKEN_GRAPH_VARIABLE", "TOKEN_GRAPH_METADATA_VARIABLE", 
			"TOKEN_GRAPH_PREDICATE_VARIABLE", "TOKEN_COMMENTS", "TOKEN_WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitProgram(this);
		}
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
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2030043136L) != 0)) {
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitStatement(this);
		}
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
			case TOKEN_GRAPH_PREDICATE_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(49);
				((StatementContext)_localctx).v = variable();
				setState(50);
				((StatementContext)_localctx).t = _input.LT(1);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 15728640L) != 0)) ) {
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
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2130708496L) != 0)) {
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitExpression(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterOr_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitOr_expression(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterAnd_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitAnd_expression(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterNot_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitNot_expression(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterComparison_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitComparison_expression(this);
		}
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
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 2093056L) != 0)) ) {
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterAdd_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitAdd_expression(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterIntersect_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitIntersect_expression(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterFunction_call(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitFunction_call(this);
		}
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
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2130708496L) != 0)) {
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
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2130708496L) != 0)) {
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterFactor(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitFactor(this);
		}
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
			case TOKEN_GRAPH_PREDICATE_VARIABLE:
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

	@SuppressWarnings("CheckReturnValue")
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
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterArgument_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitArgument_list(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
	public static class NameContext extends ParserRuleContext {
		public ParseName r;
		public Token n;
		public TerminalNode TOKEN_NAME() { return getToken(DSLParser.TOKEN_NAME, 0); }
		public TerminalNode TOKEN_DOUBLE_QUOTED_NAME() { return getToken(DSLParser.TOKEN_DOUBLE_QUOTED_NAME, 0); }
		public NameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitName(this);
		}
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

	@SuppressWarnings("CheckReturnValue")
	public static class VariableContext extends ParserRuleContext {
		public ParseVariable r;
		public Token v;
		public TerminalNode TOKEN_GRAPH_VARIABLE() { return getToken(DSLParser.TOKEN_GRAPH_VARIABLE, 0); }
		public TerminalNode TOKEN_GRAPH_METADATA_VARIABLE() { return getToken(DSLParser.TOKEN_GRAPH_METADATA_VARIABLE, 0); }
		public TerminalNode TOKEN_GRAPH_PREDICATE_VARIABLE() { return getToken(DSLParser.TOKEN_GRAPH_PREDICATE_VARIABLE, 0); }
		public VariableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterVariable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitVariable(this);
		}
	}

	public final VariableContext variable() throws RecognitionException {
		VariableContext _localctx = new VariableContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_variable);
		try {
			setState(222);
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
			case TOKEN_GRAPH_PREDICATE_VARIABLE:
				enterOuterAlt(_localctx, 3);
				{
				setState(220);
				((VariableContext)_localctx).v = match(TOKEN_GRAPH_PREDICATE_VARIABLE);

				  ParseString name = new ParseString(((VariableContext)_localctx).v.getLine(), ((VariableContext)_localctx).v.getCharPositionInLine(), (((VariableContext)_localctx).v!=null?((VariableContext)_localctx).v.getText():null));
				  ((VariableContext)_localctx).r =  new ParseVariable(((VariableContext)_localctx).v.getLine(), ((VariableContext)_localctx).v.getCharPositionInLine(),
				                         name, GraphPredicateType.GetInstance());

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

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralContext extends ParserRuleContext {
		public ParseLiteral r;
		public Token t;
		public TerminalNode TOKEN_NUMBER() { return getToken(DSLParser.TOKEN_NUMBER, 0); }
		public TerminalNode TOKEN_SINGLE_QUOTED_STRING_LITERAL() { return getToken(DSLParser.TOKEN_SINGLE_QUOTED_STRING_LITERAL, 0); }
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).enterLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof DSLListener ) ((DSLListener)listener).exitLiteral(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_literal);
		try {
			setState(228);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOKEN_NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(224);
				((LiteralContext)_localctx).t = match(TOKEN_NUMBER);

				  Integer value = Integer.parseInt((((LiteralContext)_localctx).t!=null?((LiteralContext)_localctx).t.getText():null));
				  ((LiteralContext)_localctx).r =  new ParseLiteral(((LiteralContext)_localctx).t.getLine(), ((LiteralContext)_localctx).t.getCharPositionInLine(),
				                        new TypedValue(IntegerType.GetInstance(), value));

				}
				break;
			case TOKEN_SINGLE_QUOTED_STRING_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(226);
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
		"\u0004\u0001 \u00e7\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0005\u0000"+
		"&\b\u0000\n\u0000\f\u0000)\t\u0000\u0001\u0000\u0003\u0000,\b\u0000\u0003"+
		"\u0000.\b\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0005\u0001<\b\u0001\n\u0001\f\u0001?\t\u0001\u0001"+
		"\u0001\u0001\u0001\u0003\u0001C\b\u0001\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002K\b\u0002\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0005"+
		"\u0003S\b\u0003\n\u0003\f\u0003V\t\u0003\u0001\u0004\u0001\u0004\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0005\u0004^\b\u0004\n\u0004"+
		"\f\u0004a\t\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005f\b\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0003\u0005n\b\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0003\u0006v\b\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0005\u0007\u0082\b\u0007\n\u0007\f\u0007\u0085"+
		"\t\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0005\b\u008d"+
		"\b\b\n\b\f\b\u0090\t\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t"+
		"\u0001\t\u0001\t\u0003\t\u009a\b\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0003\t\u00a1\b\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0003\t\u00ac\b\t\u0001\t\u0001\t\u0001\t\u0005\t\u00b1"+
		"\b\t\n\t\f\t\u00b4\t\t\u0003\t\u00b6\b\t\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0001\n\u0003\n\u00c6\b\n\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0005\u000b\u00ce\b\u000b\n\u000b\f\u000b"+
		"\u00d1\t\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0003\f\u00d7\b\f\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0003\r\u00df\b\r\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0003\u000e\u00e5\b\u000e\u0001\u000e"+
		"\u0000\u0000\u000f\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014"+
		"\u0016\u0018\u001a\u001c\u0000\u0003\u0001\u0000\u0014\u0017\u0001\u0000"+
		"\f\u0014\u0001\u0000\u0006\u0007\u00f1\u0000\u001e\u0001\u0000\u0000\u0000"+
		"\u0002B\u0001\u0000\u0000\u0000\u0004J\u0001\u0000\u0000\u0000\u0006L"+
		"\u0001\u0000\u0000\u0000\bW\u0001\u0000\u0000\u0000\nb\u0001\u0000\u0000"+
		"\u0000\fq\u0001\u0000\u0000\u0000\u000e{\u0001\u0000\u0000\u0000\u0010"+
		"\u0086\u0001\u0000\u0000\u0000\u0012\u00b5\u0001\u0000\u0000\u0000\u0014"+
		"\u00c5\u0001\u0000\u0000\u0000\u0016\u00c7\u0001\u0000\u0000\u0000\u0018"+
		"\u00d6\u0001\u0000\u0000\u0000\u001a\u00de\u0001\u0000\u0000\u0000\u001c"+
		"\u00e4\u0001\u0000\u0000\u0000\u001e-\u0006\u0000\uffff\uffff\u0000\u001f"+
		" \u0003\u0002\u0001\u0000 \'\u0006\u0000\uffff\uffff\u0000!\"\u0005\u0003"+
		"\u0000\u0000\"#\u0003\u0002\u0001\u0000#$\u0006\u0000\uffff\uffff\u0000"+
		"$&\u0001\u0000\u0000\u0000%!\u0001\u0000\u0000\u0000&)\u0001\u0000\u0000"+
		"\u0000\'%\u0001\u0000\u0000\u0000\'(\u0001\u0000\u0000\u0000(+\u0001\u0000"+
		"\u0000\u0000)\'\u0001\u0000\u0000\u0000*,\u0005\u0003\u0000\u0000+*\u0001"+
		"\u0000\u0000\u0000+,\u0001\u0000\u0000\u0000,.\u0001\u0000\u0000\u0000"+
		"-\u001f\u0001\u0000\u0000\u0000-.\u0001\u0000\u0000\u0000./\u0001\u0000"+
		"\u0000\u0000/0\u0005\u0000\u0000\u00010\u0001\u0001\u0000\u0000\u0000"+
		"12\u0003\u001a\r\u000023\u0007\u0000\u0000\u000034\u0003\u0004\u0002\u0000"+
		"45\u0006\u0001\uffff\uffff\u00005C\u0001\u0000\u0000\u000067\u0003\u0018"+
		"\f\u00007=\u0006\u0001\uffff\uffff\u000089\u0003\u0004\u0002\u00009:\u0006"+
		"\u0001\uffff\uffff\u0000:<\u0001\u0000\u0000\u0000;8\u0001\u0000\u0000"+
		"\u0000<?\u0001\u0000\u0000\u0000=;\u0001\u0000\u0000\u0000=>\u0001\u0000"+
		"\u0000\u0000>@\u0001\u0000\u0000\u0000?=\u0001\u0000\u0000\u0000@A\u0006"+
		"\u0001\uffff\uffff\u0000AC\u0001\u0000\u0000\u0000B1\u0001\u0000\u0000"+
		"\u0000B6\u0001\u0000\u0000\u0000C\u0003\u0001\u0000\u0000\u0000DE\u0003"+
		"\u000e\u0007\u0000EF\u0006\u0002\uffff\uffff\u0000FK\u0001\u0000\u0000"+
		"\u0000GH\u0003\u0006\u0003\u0000HI\u0006\u0002\uffff\uffff\u0000IK\u0001"+
		"\u0000\u0000\u0000JD\u0001\u0000\u0000\u0000JG\u0001\u0000\u0000\u0000"+
		"K\u0005\u0001\u0000\u0000\u0000LM\u0003\b\u0004\u0000MT\u0006\u0003\uffff"+
		"\uffff\u0000NO\u0005\t\u0000\u0000OP\u0003\b\u0004\u0000PQ\u0006\u0003"+
		"\uffff\uffff\u0000QS\u0001\u0000\u0000\u0000RN\u0001\u0000\u0000\u0000"+
		"SV\u0001\u0000\u0000\u0000TR\u0001\u0000\u0000\u0000TU\u0001\u0000\u0000"+
		"\u0000U\u0007\u0001\u0000\u0000\u0000VT\u0001\u0000\u0000\u0000WX\u0003"+
		"\n\u0005\u0000X_\u0006\u0004\uffff\uffff\u0000YZ\u0005\n\u0000\u0000Z"+
		"[\u0003\n\u0005\u0000[\\\u0006\u0004\uffff\uffff\u0000\\^\u0001\u0000"+
		"\u0000\u0000]Y\u0001\u0000\u0000\u0000^a\u0001\u0000\u0000\u0000_]\u0001"+
		"\u0000\u0000\u0000_`\u0001\u0000\u0000\u0000`\t\u0001\u0000\u0000\u0000"+
		"a_\u0001\u0000\u0000\u0000be\u0006\u0005\uffff\uffff\u0000cd\u0005\u000b"+
		"\u0000\u0000df\u0006\u0005\uffff\uffff\u0000ec\u0001\u0000\u0000\u0000"+
		"ef\u0001\u0000\u0000\u0000fm\u0001\u0000\u0000\u0000gh\u0003\f\u0006\u0000"+
		"hi\u0006\u0005\uffff\uffff\u0000in\u0001\u0000\u0000\u0000jk\u0003\u0012"+
		"\t\u0000kl\u0006\u0005\uffff\uffff\u0000ln\u0001\u0000\u0000\u0000mg\u0001"+
		"\u0000\u0000\u0000mj\u0001\u0000\u0000\u0000no\u0001\u0000\u0000\u0000"+
		"op\u0006\u0005\uffff\uffff\u0000p\u000b\u0001\u0000\u0000\u0000qr\u0006"+
		"\u0006\uffff\uffff\u0000ru\u0003\u000e\u0007\u0000st\u0005\u000b\u0000"+
		"\u0000tv\u0006\u0006\uffff\uffff\u0000us\u0001\u0000\u0000\u0000uv\u0001"+
		"\u0000\u0000\u0000vw\u0001\u0000\u0000\u0000wx\u0007\u0001\u0000\u0000"+
		"xy\u0003\u000e\u0007\u0000yz\u0006\u0006\uffff\uffff\u0000z\r\u0001\u0000"+
		"\u0000\u0000{|\u0003\u0010\b\u0000|\u0083\u0006\u0007\uffff\uffff\u0000"+
		"}~\u0007\u0002\u0000\u0000~\u007f\u0003\u0010\b\u0000\u007f\u0080\u0006"+
		"\u0007\uffff\uffff\u0000\u0080\u0082\u0001\u0000\u0000\u0000\u0081}\u0001"+
		"\u0000\u0000\u0000\u0082\u0085\u0001\u0000\u0000\u0000\u0083\u0081\u0001"+
		"\u0000\u0000\u0000\u0083\u0084\u0001\u0000\u0000\u0000\u0084\u000f\u0001"+
		"\u0000\u0000\u0000\u0085\u0083\u0001\u0000\u0000\u0000\u0086\u0087\u0003"+
		"\u0012\t\u0000\u0087\u008e\u0006\b\uffff\uffff\u0000\u0088\u0089\u0005"+
		"\b\u0000\u0000\u0089\u008a\u0003\u0012\t\u0000\u008a\u008b\u0006\b\uffff"+
		"\uffff\u0000\u008b\u008d\u0001\u0000\u0000\u0000\u008c\u0088\u0001\u0000"+
		"\u0000\u0000\u008d\u0090\u0001\u0000\u0000\u0000\u008e\u008c\u0001\u0000"+
		"\u0000\u0000\u008e\u008f\u0001\u0000\u0000\u0000\u008f\u0011\u0001\u0000"+
		"\u0000\u0000\u0090\u008e\u0001\u0000\u0000\u0000\u0091\u0092\u0003\u0014"+
		"\n\u0000\u0092\u0093\u0006\t\uffff\uffff\u0000\u0093\u00b6\u0001\u0000"+
		"\u0000\u0000\u0094\u0099\u0006\t\uffff\uffff\u0000\u0095\u0096\u0003\u0014"+
		"\n\u0000\u0096\u0097\u0006\t\uffff\uffff\u0000\u0097\u0098\u0005\u0002"+
		"\u0000\u0000\u0098\u009a\u0001\u0000\u0000\u0000\u0099\u0095\u0001\u0000"+
		"\u0000\u0000\u0099\u009a\u0001\u0000\u0000\u0000\u009a\u009b\u0001\u0000"+
		"\u0000\u0000\u009b\u009c\u0003\u0018\f\u0000\u009c\u00a0\u0005\u0004\u0000"+
		"\u0000\u009d\u009e\u0003\u0016\u000b\u0000\u009e\u009f\u0006\t\uffff\uffff"+
		"\u0000\u009f\u00a1\u0001\u0000\u0000\u0000\u00a0\u009d\u0001\u0000\u0000"+
		"\u0000\u00a0\u00a1\u0001\u0000\u0000\u0000\u00a1\u00a2\u0001\u0000\u0000"+
		"\u0000\u00a2\u00a3\u0005\u0005\u0000\u0000\u00a3\u00b2\u0006\t\uffff\uffff"+
		"\u0000\u00a4\u00a5\u0005\u0002\u0000\u0000\u00a5\u00a6\u0003\u0018\f\u0000"+
		"\u00a6\u00a7\u0005\u0004\u0000\u0000\u00a7\u00ab\u0006\t\uffff\uffff\u0000"+
		"\u00a8\u00a9\u0003\u0016\u000b\u0000\u00a9\u00aa\u0006\t\uffff\uffff\u0000"+
		"\u00aa\u00ac\u0001\u0000\u0000\u0000\u00ab\u00a8\u0001\u0000\u0000\u0000"+
		"\u00ab\u00ac\u0001\u0000\u0000\u0000\u00ac\u00ad\u0001\u0000\u0000\u0000"+
		"\u00ad\u00ae\u0005\u0005\u0000\u0000\u00ae\u00af\u0006\t\uffff\uffff\u0000"+
		"\u00af\u00b1\u0001\u0000\u0000\u0000\u00b0\u00a4\u0001\u0000\u0000\u0000"+
		"\u00b1\u00b4\u0001\u0000\u0000\u0000\u00b2\u00b0\u0001\u0000\u0000\u0000"+
		"\u00b2\u00b3\u0001\u0000\u0000\u0000\u00b3\u00b6\u0001\u0000\u0000\u0000"+
		"\u00b4\u00b2\u0001\u0000\u0000\u0000\u00b5\u0091\u0001\u0000\u0000\u0000"+
		"\u00b5\u0094\u0001\u0000\u0000\u0000\u00b6\u0013\u0001\u0000\u0000\u0000"+
		"\u00b7\u00b8\u0003\u001c\u000e\u0000\u00b8\u00b9\u0006\n\uffff\uffff\u0000"+
		"\u00b9\u00c6\u0001\u0000\u0000\u0000\u00ba\u00bb\u0003\u001a\r\u0000\u00bb"+
		"\u00bc\u0006\n\uffff\uffff\u0000\u00bc\u00c6\u0001\u0000\u0000\u0000\u00bd"+
		"\u00be\u0003\u0018\f\u0000\u00be\u00bf\u0006\n\uffff\uffff\u0000\u00bf"+
		"\u00c6\u0001\u0000\u0000\u0000\u00c0\u00c1\u0005\u0004\u0000\u0000\u00c1"+
		"\u00c2\u0003\u0004\u0002\u0000\u00c2\u00c3\u0005\u0005\u0000\u0000\u00c3"+
		"\u00c4\u0006\n\uffff\uffff\u0000\u00c4\u00c6\u0001\u0000\u0000\u0000\u00c5"+
		"\u00b7\u0001\u0000\u0000\u0000\u00c5\u00ba\u0001\u0000\u0000\u0000\u00c5"+
		"\u00bd\u0001\u0000\u0000\u0000\u00c5\u00c0\u0001\u0000\u0000\u0000\u00c6"+
		"\u0015\u0001\u0000\u0000\u0000\u00c7\u00c8\u0003\u0004\u0002\u0000\u00c8"+
		"\u00cf\u0006\u000b\uffff\uffff\u0000\u00c9\u00ca\u0005\u0001\u0000\u0000"+
		"\u00ca\u00cb\u0003\u0004\u0002\u0000\u00cb\u00cc\u0006\u000b\uffff\uffff"+
		"\u0000\u00cc\u00ce\u0001\u0000\u0000\u0000\u00cd\u00c9\u0001\u0000\u0000"+
		"\u0000\u00ce\u00d1\u0001\u0000\u0000\u0000\u00cf\u00cd\u0001\u0000\u0000"+
		"\u0000\u00cf\u00d0\u0001\u0000\u0000\u0000\u00d0\u0017\u0001\u0000\u0000"+
		"\u0000\u00d1\u00cf\u0001\u0000\u0000\u0000\u00d2\u00d3\u0005\u0018\u0000"+
		"\u0000\u00d3\u00d7\u0006\f\uffff\uffff\u0000\u00d4\u00d5\u0005\u001b\u0000"+
		"\u0000\u00d5\u00d7\u0006\f\uffff\uffff\u0000\u00d6\u00d2\u0001\u0000\u0000"+
		"\u0000\u00d6\u00d4\u0001\u0000\u0000\u0000\u00d7\u0019\u0001\u0000\u0000"+
		"\u0000\u00d8\u00d9\u0005\u001c\u0000\u0000\u00d9\u00df\u0006\r\uffff\uffff"+
		"\u0000\u00da\u00db\u0005\u001d\u0000\u0000\u00db\u00df\u0006\r\uffff\uffff"+
		"\u0000\u00dc\u00dd\u0005\u001e\u0000\u0000\u00dd\u00df\u0006\r\uffff\uffff"+
		"\u0000\u00de\u00d8\u0001\u0000\u0000\u0000\u00de\u00da\u0001\u0000\u0000"+
		"\u0000\u00de\u00dc\u0001\u0000\u0000\u0000\u00df\u001b\u0001\u0000\u0000"+
		"\u0000\u00e0\u00e1\u0005\u0019\u0000\u0000\u00e1\u00e5\u0006\u000e\uffff"+
		"\uffff\u0000\u00e2\u00e3\u0005\u001a\u0000\u0000\u00e3\u00e5\u0006\u000e"+
		"\uffff\uffff\u0000\u00e4\u00e0\u0001\u0000\u0000\u0000\u00e4\u00e2\u0001"+
		"\u0000\u0000\u0000\u00e5\u001d\u0001\u0000\u0000\u0000\u0017\'+-=BJT_"+
		"emu\u0083\u008e\u0099\u00a0\u00ab\u00b2\u00b5\u00c5\u00cf\u00d6\u00de"+
		"\u00e4";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}