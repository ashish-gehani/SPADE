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
		TOKEN_GRAPH_PREDICATE_VARIABLE=30, TOKEN_COMMENTS=31, TOKEN_WS=32;
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
		"TOKEN_GRAPH_VARIABLE", "TOKEN_GRAPH_METADATA_VARIABLE", "TOKEN_GRAPH_PREDICATE_VARIABLE", 
		"TOKEN_COMMENTS", "TOKEN_WS"
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
		"TOKEN_GRAPH_VARIABLE", "TOKEN_GRAPH_METADATA_VARIABLE", "TOKEN_GRAPH_PREDICATE_VARIABLE", 
		"TOKEN_COMMENTS", "TOKEN_WS"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\"\u00ec\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t\3"+
		"\n\3\n\3\n\3\n\5\nX\n\n\3\13\3\13\3\13\3\13\3\13\3\13\5\13`\n\13\3\f\3"+
		"\f\3\f\3\f\3\f\3\f\5\fh\n\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\5\rr\n\r\3"+
		"\16\3\16\3\16\3\17\3\17\3\17\3\17\5\17{\n\17\3\20\3\20\3\21\3\21\3\22"+
		"\3\22\3\22\3\23\3\23\3\23\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\3\24\3\24\5\24\u0094\n\24\3\25\3\25\3\26\3\26\3\26\3\27\3\27"+
		"\3\27\3\30\3\30\3\30\3\31\3\31\3\31\7\31\u00a4\n\31\f\31\16\31\u00a7\13"+
		"\31\5\31\u00a9\n\31\3\32\6\32\u00ac\n\32\r\32\16\32\u00ad\3\33\3\33\3"+
		"\33\3\33\7\33\u00b4\n\33\f\33\16\33\u00b7\13\33\3\33\3\33\3\34\3\34\3"+
		"\34\3\34\7\34\u00bf\n\34\f\34\16\34\u00c2\13\34\3\34\3\34\3\35\3\35\6"+
		"\35\u00c8\n\35\r\35\16\35\u00c9\3\36\3\36\6\36\u00ce\n\36\r\36\16\36\u00cf"+
		"\3\37\3\37\6\37\u00d4\n\37\r\37\16\37\u00d5\3 \3 \7 \u00da\n \f \16 \u00dd"+
		"\13 \3 \5 \u00e0\n \3 \3 \3 \3 \3!\6!\u00e7\n!\r!\16!\u00e8\3!\3!\2\2"+
		"\"\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20"+
		"\37\21!\22#\23%\24\'\25)\26+\27-\30/\31\61\32\63\33\65\34\67\359\36;\37"+
		"= ?!A\"\3\2\13\4\2C\\c|\6\2\62;C\\aac|\3\2\62;\4\2))^^\4\2$$^^\7\2&&\62"+
		";C\\aac|\4\2%%\'\'\4\2\f\f\17\17\5\2\13\f\17\17\"\"\2\u00ff\2\3\3\2\2"+
		"\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3"+
		"\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2"+
		"\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2"+
		"\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2"+
		"\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3\2\2\2\2=\3"+
		"\2\2\2\2?\3\2\2\2\2A\3\2\2\2\3C\3\2\2\2\5E\3\2\2\2\7G\3\2\2\2\tI\3\2\2"+
		"\2\13K\3\2\2\2\rM\3\2\2\2\17O\3\2\2\2\21Q\3\2\2\2\23W\3\2\2\2\25_\3\2"+
		"\2\2\27g\3\2\2\2\31q\3\2\2\2\33s\3\2\2\2\35z\3\2\2\2\37|\3\2\2\2!~\3\2"+
		"\2\2#\u0080\3\2\2\2%\u0083\3\2\2\2\'\u0093\3\2\2\2)\u0095\3\2\2\2+\u0097"+
		"\3\2\2\2-\u009a\3\2\2\2/\u009d\3\2\2\2\61\u00a8\3\2\2\2\63\u00ab\3\2\2"+
		"\2\65\u00af\3\2\2\2\67\u00ba\3\2\2\29\u00c5\3\2\2\2;\u00cb\3\2\2\2=\u00d1"+
		"\3\2\2\2?\u00d7\3\2\2\2A\u00e6\3\2\2\2CD\7.\2\2D\4\3\2\2\2EF\7\60\2\2"+
		"F\6\3\2\2\2GH\7=\2\2H\b\3\2\2\2IJ\7*\2\2J\n\3\2\2\2KL\7+\2\2L\f\3\2\2"+
		"\2MN\7-\2\2N\16\3\2\2\2OP\7/\2\2P\20\3\2\2\2QR\7(\2\2R\22\3\2\2\2ST\7"+
		"q\2\2TX\7t\2\2UV\7Q\2\2VX\7T\2\2WS\3\2\2\2WU\3\2\2\2X\24\3\2\2\2YZ\7c"+
		"\2\2Z[\7p\2\2[`\7f\2\2\\]\7C\2\2]^\7P\2\2^`\7F\2\2_Y\3\2\2\2_\\\3\2\2"+
		"\2`\26\3\2\2\2ab\7p\2\2bc\7q\2\2ch\7v\2\2de\7P\2\2ef\7Q\2\2fh\7V\2\2g"+
		"a\3\2\2\2gd\3\2\2\2h\30\3\2\2\2ij\7n\2\2jk\7k\2\2kl\7m\2\2lr\7g\2\2mn"+
		"\7N\2\2no\7K\2\2op\7M\2\2pr\7G\2\2qi\3\2\2\2qm\3\2\2\2r\32\3\2\2\2st\7"+
		"?\2\2tu\7?\2\2u\34\3\2\2\2vw\7>\2\2w{\7@\2\2xy\7#\2\2y{\7?\2\2zv\3\2\2"+
		"\2zx\3\2\2\2{\36\3\2\2\2|}\7>\2\2} \3\2\2\2~\177\7@\2\2\177\"\3\2\2\2"+
		"\u0080\u0081\7>\2\2\u0081\u0082\7?\2\2\u0082$\3\2\2\2\u0083\u0084\7@\2"+
		"\2\u0084\u0085\7?\2\2\u0085&\3\2\2\2\u0086\u0094\7\u0080\2\2\u0087\u0088"+
		"\7t\2\2\u0088\u0089\7g\2\2\u0089\u008a\7i\2\2\u008a\u008b\7g\2\2\u008b"+
		"\u008c\7z\2\2\u008c\u0094\7r\2\2\u008d\u008e\7T\2\2\u008e\u008f\7G\2\2"+
		"\u008f\u0090\7I\2\2\u0090\u0091\7G\2\2\u0091\u0092\7Z\2\2\u0092\u0094"+
		"\7R\2\2\u0093\u0086\3\2\2\2\u0093\u0087\3\2\2\2\u0093\u008d\3\2\2\2\u0094"+
		"(\3\2\2\2\u0095\u0096\7?\2\2\u0096*\3\2\2\2\u0097\u0098\7-\2\2\u0098\u0099"+
		"\7?\2\2\u0099,\3\2\2\2\u009a\u009b\7/\2\2\u009b\u009c\7?\2\2\u009c.\3"+
		"\2\2\2\u009d\u009e\7(\2\2\u009e\u009f\7?\2\2\u009f\60\3\2\2\2\u00a0\u00a9"+
		"\7,\2\2\u00a1\u00a5\t\2\2\2\u00a2\u00a4\t\3\2\2\u00a3\u00a2\3\2\2\2\u00a4"+
		"\u00a7\3\2\2\2\u00a5\u00a3\3\2\2\2\u00a5\u00a6\3\2\2\2\u00a6\u00a9\3\2"+
		"\2\2\u00a7\u00a5\3\2\2\2\u00a8\u00a0\3\2\2\2\u00a8\u00a1\3\2\2\2\u00a9"+
		"\62\3\2\2\2\u00aa\u00ac\t\4\2\2\u00ab\u00aa\3\2\2\2\u00ac\u00ad\3\2\2"+
		"\2\u00ad\u00ab\3\2\2\2\u00ad\u00ae\3\2\2\2\u00ae\64\3\2\2\2\u00af\u00b5"+
		"\7)\2\2\u00b0\u00b4\n\5\2\2\u00b1\u00b2\7^\2\2\u00b2\u00b4\13\2\2\2\u00b3"+
		"\u00b0\3\2\2\2\u00b3\u00b1\3\2\2\2\u00b4\u00b7\3\2\2\2\u00b5\u00b3\3\2"+
		"\2\2\u00b5\u00b6\3\2\2\2\u00b6\u00b8\3\2\2\2\u00b7\u00b5\3\2\2\2\u00b8"+
		"\u00b9\7)\2\2\u00b9\66\3\2\2\2\u00ba\u00c0\7$\2\2\u00bb\u00bf\n\6\2\2"+
		"\u00bc\u00bd\7^\2\2\u00bd\u00bf\13\2\2\2\u00be\u00bb\3\2\2\2\u00be\u00bc"+
		"\3\2\2\2\u00bf\u00c2\3\2\2\2\u00c0\u00be\3\2\2\2\u00c0\u00c1\3\2\2\2\u00c1"+
		"\u00c3\3\2\2\2\u00c2\u00c0\3\2\2\2\u00c3\u00c4\7$\2\2\u00c48\3\2\2\2\u00c5"+
		"\u00c7\7&\2\2\u00c6\u00c8\t\7\2\2\u00c7\u00c6\3\2\2\2\u00c8\u00c9\3\2"+
		"\2\2\u00c9\u00c7\3\2\2\2\u00c9\u00ca\3\2\2\2\u00ca:\3\2\2\2\u00cb\u00cd"+
		"\7B\2\2\u00cc\u00ce\t\7\2\2\u00cd\u00cc\3\2\2\2\u00ce\u00cf\3\2\2\2\u00cf"+
		"\u00cd\3\2\2\2\u00cf\u00d0\3\2\2\2\u00d0<\3\2\2\2\u00d1\u00d3\7\'\2\2"+
		"\u00d2\u00d4\t\7\2\2\u00d3\u00d2\3\2\2\2\u00d4\u00d5\3\2\2\2\u00d5\u00d3"+
		"\3\2\2\2\u00d5\u00d6\3\2\2\2\u00d6>\3\2\2\2\u00d7\u00db\t\b\2\2\u00d8"+
		"\u00da\n\t\2\2\u00d9\u00d8\3\2\2\2\u00da\u00dd\3\2\2\2\u00db\u00d9\3\2"+
		"\2\2\u00db\u00dc\3\2\2\2\u00dc\u00df\3\2\2\2\u00dd\u00db\3\2\2\2\u00de"+
		"\u00e0\7\17\2\2\u00df\u00de\3\2\2\2\u00df\u00e0\3\2\2\2\u00e0\u00e1\3"+
		"\2\2\2\u00e1\u00e2\7\f\2\2\u00e2\u00e3\3\2\2\2\u00e3\u00e4\b \2\2\u00e4"+
		"@\3\2\2\2\u00e5\u00e7\t\n\2\2\u00e6\u00e5\3\2\2\2\u00e7\u00e8\3\2\2\2"+
		"\u00e8\u00e6\3\2\2\2\u00e8\u00e9\3\2\2\2\u00e9\u00ea\3\2\2\2\u00ea\u00eb"+
		"\b!\2\2\u00ebB\3\2\2\2\26\2W_gqz\u0093\u00a5\u00a8\u00ad\u00b3\u00b5\u00be"+
		"\u00c0\u00c9\u00cf\u00d5\u00db\u00df\u00e8\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}