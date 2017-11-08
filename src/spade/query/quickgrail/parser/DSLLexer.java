// Generated from DSL.g4 by ANTLR 4.7

package spade.query.quickgrail.parser;

import spade.query.quickgrail.types.*;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class DSLLexer extends Lexer {
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
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"TOKEN_COMMA", "TOKEN_DOT", "TOKEN_SEMICOLON", "TOKEN_LPAREN", "TOKEN_RPAREN", 
		"TOKEN_PLUS", "TOKEN_MINUS", "TOKEN_INTERSECT", "TOKEN_OR", "TOKEN_AND", 
		"TOKEN_NOT", "TOKEN_LIKE", "TOKEN_EQUAL", "TOKEN_NOT_EQUAL", "TOKEN_LESS", 
		"TOKEN_GREATER", "TOKEN_LESS_EQUAL", "TOKEN_GREATER_EQUAL", "TOKEN_REGEX", 
		"TOKEN_ASSIGN", "TOKEN_PLUS_ASSIGN", "TOKEN_MINUS_ASSIGN", "TOKEN_INTERSECT_ASSIGN", 
		"TOKEN_NAME", "TOKEN_NUMBER", "TOKEN_SINGLE_QUOTED_STRING_LITERAL", "TOKEN_DOUBLE_QUOTED_NAME", 
		"TOKEN_GRAPH_VARIABLE", "TOKEN_GRAPH_METADATA_VARIABLE", "TOKEN_COMMENTS", 
		"TOKEN_WS"
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
	  public void recover(LexerNoViableAltException e) {
	    throw new RuntimeException(e); // Bail out
	  }

	  @Override
	  public void recover(RecognitionException e) {
	    throw new RuntimeException(e); // Bail out
	  }
	 

	public DSLLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "DSL.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2!\u00e4\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \3\2"+
		"\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3"+
		"\n\3\n\5\nV\n\n\3\13\3\13\3\13\3\13\3\13\3\13\5\13^\n\13\3\f\3\f\3\f\3"+
		"\f\3\f\3\f\5\ff\n\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\5\rp\n\r\3\16\3\16"+
		"\3\16\3\17\3\17\3\17\3\17\5\17y\n\17\3\20\3\20\3\21\3\21\3\22\3\22\3\22"+
		"\3\23\3\23\3\23\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\5\24\u0092\n\24\3\25\3\25\3\26\3\26\3\26\3\27\3\27\3\27\3\30"+
		"\3\30\3\30\3\31\3\31\3\31\7\31\u00a2\n\31\f\31\16\31\u00a5\13\31\5\31"+
		"\u00a7\n\31\3\32\6\32\u00aa\n\32\r\32\16\32\u00ab\3\33\3\33\3\33\3\33"+
		"\7\33\u00b2\n\33\f\33\16\33\u00b5\13\33\3\33\3\33\3\34\3\34\3\34\3\34"+
		"\7\34\u00bd\n\34\f\34\16\34\u00c0\13\34\3\34\3\34\3\35\3\35\6\35\u00c6"+
		"\n\35\r\35\16\35\u00c7\3\36\3\36\6\36\u00cc\n\36\r\36\16\36\u00cd\3\37"+
		"\3\37\7\37\u00d2\n\37\f\37\16\37\u00d5\13\37\3\37\5\37\u00d8\n\37\3\37"+
		"\3\37\3\37\3\37\3 \6 \u00df\n \r \16 \u00e0\3 \3 \2\2!\3\3\5\4\7\5\t\6"+
		"\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23%\24"+
		"\'\25)\26+\27-\30/\31\61\32\63\33\65\34\67\359\36;\37= ?!\3\2\13\4\2C"+
		"\\c|\6\2\62;C\\aac|\3\2\62;\4\2))^^\4\2$$^^\7\2&&\62;C\\aac|\4\2%%\'\'"+
		"\4\2\f\f\17\17\5\2\13\f\17\17\"\"\2\u00f6\2\3\3\2\2\2\2\5\3\2\2\2\2\7"+
		"\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2"+
		"\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2"+
		"\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2"+
		"\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2\2"+
		"\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3\2\2\2"+
		"\3A\3\2\2\2\5C\3\2\2\2\7E\3\2\2\2\tG\3\2\2\2\13I\3\2\2\2\rK\3\2\2\2\17"+
		"M\3\2\2\2\21O\3\2\2\2\23U\3\2\2\2\25]\3\2\2\2\27e\3\2\2\2\31o\3\2\2\2"+
		"\33q\3\2\2\2\35x\3\2\2\2\37z\3\2\2\2!|\3\2\2\2#~\3\2\2\2%\u0081\3\2\2"+
		"\2\'\u0091\3\2\2\2)\u0093\3\2\2\2+\u0095\3\2\2\2-\u0098\3\2\2\2/\u009b"+
		"\3\2\2\2\61\u00a6\3\2\2\2\63\u00a9\3\2\2\2\65\u00ad\3\2\2\2\67\u00b8\3"+
		"\2\2\29\u00c3\3\2\2\2;\u00c9\3\2\2\2=\u00cf\3\2\2\2?\u00de\3\2\2\2AB\7"+
		".\2\2B\4\3\2\2\2CD\7\60\2\2D\6\3\2\2\2EF\7=\2\2F\b\3\2\2\2GH\7*\2\2H\n"+
		"\3\2\2\2IJ\7+\2\2J\f\3\2\2\2KL\7-\2\2L\16\3\2\2\2MN\7/\2\2N\20\3\2\2\2"+
		"OP\7(\2\2P\22\3\2\2\2QR\7q\2\2RV\7t\2\2ST\7Q\2\2TV\7T\2\2UQ\3\2\2\2US"+
		"\3\2\2\2V\24\3\2\2\2WX\7c\2\2XY\7p\2\2Y^\7f\2\2Z[\7C\2\2[\\\7P\2\2\\^"+
		"\7F\2\2]W\3\2\2\2]Z\3\2\2\2^\26\3\2\2\2_`\7p\2\2`a\7q\2\2af\7v\2\2bc\7"+
		"P\2\2cd\7Q\2\2df\7V\2\2e_\3\2\2\2eb\3\2\2\2f\30\3\2\2\2gh\7n\2\2hi\7k"+
		"\2\2ij\7m\2\2jp\7g\2\2kl\7N\2\2lm\7K\2\2mn\7M\2\2np\7G\2\2og\3\2\2\2o"+
		"k\3\2\2\2p\32\3\2\2\2qr\7?\2\2rs\7?\2\2s\34\3\2\2\2tu\7>\2\2uy\7@\2\2"+
		"vw\7#\2\2wy\7?\2\2xt\3\2\2\2xv\3\2\2\2y\36\3\2\2\2z{\7>\2\2{ \3\2\2\2"+
		"|}\7@\2\2}\"\3\2\2\2~\177\7>\2\2\177\u0080\7?\2\2\u0080$\3\2\2\2\u0081"+
		"\u0082\7@\2\2\u0082\u0083\7?\2\2\u0083&\3\2\2\2\u0084\u0092\7\u0080\2"+
		"\2\u0085\u0086\7t\2\2\u0086\u0087\7g\2\2\u0087\u0088\7i\2\2\u0088\u0089"+
		"\7g\2\2\u0089\u008a\7z\2\2\u008a\u0092\7r\2\2\u008b\u008c\7T\2\2\u008c"+
		"\u008d\7G\2\2\u008d\u008e\7I\2\2\u008e\u008f\7G\2\2\u008f\u0090\7Z\2\2"+
		"\u0090\u0092\7R\2\2\u0091\u0084\3\2\2\2\u0091\u0085\3\2\2\2\u0091\u008b"+
		"\3\2\2\2\u0092(\3\2\2\2\u0093\u0094\7?\2\2\u0094*\3\2\2\2\u0095\u0096"+
		"\7-\2\2\u0096\u0097\7?\2\2\u0097,\3\2\2\2\u0098\u0099\7/\2\2\u0099\u009a"+
		"\7?\2\2\u009a.\3\2\2\2\u009b\u009c\7(\2\2\u009c\u009d\7?\2\2\u009d\60"+
		"\3\2\2\2\u009e\u00a7\7,\2\2\u009f\u00a3\t\2\2\2\u00a0\u00a2\t\3\2\2\u00a1"+
		"\u00a0\3\2\2\2\u00a2\u00a5\3\2\2\2\u00a3\u00a1\3\2\2\2\u00a3\u00a4\3\2"+
		"\2\2\u00a4\u00a7\3\2\2\2\u00a5\u00a3\3\2\2\2\u00a6\u009e\3\2\2\2\u00a6"+
		"\u009f\3\2\2\2\u00a7\62\3\2\2\2\u00a8\u00aa\t\4\2\2\u00a9\u00a8\3\2\2"+
		"\2\u00aa\u00ab\3\2\2\2\u00ab\u00a9\3\2\2\2\u00ab\u00ac\3\2\2\2\u00ac\64"+
		"\3\2\2\2\u00ad\u00b3\7)\2\2\u00ae\u00b2\n\5\2\2\u00af\u00b0\7^\2\2\u00b0"+
		"\u00b2\13\2\2\2\u00b1\u00ae\3\2\2\2\u00b1\u00af\3\2\2\2\u00b2\u00b5\3"+
		"\2\2\2\u00b3\u00b1\3\2\2\2\u00b3\u00b4\3\2\2\2\u00b4\u00b6\3\2\2\2\u00b5"+
		"\u00b3\3\2\2\2\u00b6\u00b7\7)\2\2\u00b7\66\3\2\2\2\u00b8\u00be\7$\2\2"+
		"\u00b9\u00bd\n\6\2\2\u00ba\u00bb\7^\2\2\u00bb\u00bd\13\2\2\2\u00bc\u00b9"+
		"\3\2\2\2\u00bc\u00ba\3\2\2\2\u00bd\u00c0\3\2\2\2\u00be\u00bc\3\2\2\2\u00be"+
		"\u00bf\3\2\2\2\u00bf\u00c1\3\2\2\2\u00c0\u00be\3\2\2\2\u00c1\u00c2\7$"+
		"\2\2\u00c28\3\2\2\2\u00c3\u00c5\7&\2\2\u00c4\u00c6\t\7\2\2\u00c5\u00c4"+
		"\3\2\2\2\u00c6\u00c7\3\2\2\2\u00c7\u00c5\3\2\2\2\u00c7\u00c8\3\2\2\2\u00c8"+
		":\3\2\2\2\u00c9\u00cb\7B\2\2\u00ca\u00cc\t\7\2\2\u00cb\u00ca\3\2\2\2\u00cc"+
		"\u00cd\3\2\2\2\u00cd\u00cb\3\2\2\2\u00cd\u00ce\3\2\2\2\u00ce<\3\2\2\2"+
		"\u00cf\u00d3\t\b\2\2\u00d0\u00d2\n\t\2\2\u00d1\u00d0\3\2\2\2\u00d2\u00d5"+
		"\3\2\2\2\u00d3\u00d1\3\2\2\2\u00d3\u00d4\3\2\2\2\u00d4\u00d7\3\2\2\2\u00d5"+
		"\u00d3\3\2\2\2\u00d6\u00d8\7\17\2\2\u00d7\u00d6\3\2\2\2\u00d7\u00d8\3"+
		"\2\2\2\u00d8\u00d9\3\2\2\2\u00d9\u00da\7\f\2\2\u00da\u00db\3\2\2\2\u00db"+
		"\u00dc\b\37\2\2\u00dc>\3\2\2\2\u00dd\u00df\t\n\2\2\u00de\u00dd\3\2\2\2"+
		"\u00df\u00e0\3\2\2\2\u00e0\u00de\3\2\2\2\u00e0\u00e1\3\2\2\2\u00e1\u00e2"+
		"\3\2\2\2\u00e2\u00e3\b \2\2\u00e3@\3\2\2\2\25\2U]eox\u0091\u00a3\u00a6"+
		"\u00ab\u00b1\u00b3\u00bc\u00be\u00c7\u00cd\u00d3\u00d7\u00e0\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}