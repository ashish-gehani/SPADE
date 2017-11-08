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
grammar DSL;

@header {
package spade.query.quickgrail.parser;

import spade.query.quickgrail.types.*;
}

@lexer::members {
  @Override
  public void recover(LexerNoViableAltException e) {
    throw new RuntimeException(e); // Bail out
  }

  @Override
  public void recover(RecognitionException e) {
    throw new RuntimeException(e); // Bail out
  }
 }

@parser::members {
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
}

program returns [ParseProgram r]
:
{
  $r = new ParseProgram(0, 0);
}
(
  h=statement
  {
    $r.addStatement($h.r);
  }
  (
    TOKEN_SEMICOLON t=statement
    {
      $r.addStatement($t.r);
    }
  )*
  (TOKEN_SEMICOLON)?
)?
EOF
;

statement returns [ParseStatement r]
:
v=variable
t=(TOKEN_ASSIGN | TOKEN_PLUS_ASSIGN | TOKEN_MINUS_ASSIGN | TOKEN_INTERSECT_ASSIGN)
e=expression
{
  $r = new ParseAssignment($v.r.getLineNumber(), $v.r.getColumnNumber(),
                           ParseAssignment.ResolveAssignmentType($t.text),
                           $v.r, $e.r);
}
|
n=name
{
  ParseCommand command =
      new ParseCommand($n.r.getLineNumber(), $n.r.getColumnNumber(), $n.r.getName());
}
(
  e=expression
  {
    command.addArgument($e.r);
  }
)*
{
  $r = command;
}
;

expression returns [ParseExpression r]
:
add_expression
{
  $r = $add_expression.r;
}
|
or_expression
{
  $r = $or_expression.r;
}
;

or_expression returns [ParseExpression r]
:
lhs=and_expression
{
  $r = $lhs.r;
}
(
  t=TOKEN_OR rhs=and_expression
  {
    ParseString operator =
        new ParseString($t.getLine(), $t.getCharPositionInLine(), $t.text);
    ParseOperation operation =
        new ParseOperation($t.getLine(), $t.getCharPositionInLine(), null, operator);
    operation.addOperand($r);
    operation.addOperand($rhs.r);
    $r = operation;
  }
)*
;

and_expression returns [ParseExpression r]
:
lhs=not_expression
{
  $r = $lhs.r;
}
(
  t=TOKEN_AND rhs=not_expression
  {
    ParseString operator =
        new ParseString($t.getLine(), $t.getCharPositionInLine(), $t.text);
    ParseOperation operation =
        new ParseOperation($t.getLine(), $t.getCharPositionInLine(), null, operator);
    operation.addOperand($r);
    operation.addOperand($rhs.r);
    $r = operation;
  }
)*
;

not_expression returns [ParseExpression r]
:
{
  ParseString operator = null;
  ParseExpression operand = null;
}
(
  t=TOKEN_NOT
  {
    operator = new ParseString($t.getLine(), $t.getCharPositionInLine(), $t.text);
  }
)?
(
  c=comparison_expression
  {
  	operand = $c.r;
  }
  |
  f=function_call
  {
  	operand = $f.r;
  }
)
{
  if (operator == null) {
    $r = operand;
  } else {
    ParseOperation operation =
        new ParseOperation($t.getLine(), $t.getCharPositionInLine(), null, operator);
    operation.addOperand(operand);
    $r = operation;
  }
}
;

comparison_expression returns [ParseExpression r]
:
{
  ParseString negateOp = null;
}
lhs=add_expression
(
  n=TOKEN_NOT
  {
    negateOp = new ParseString($n.getLine(), $n.getCharPositionInLine(), $n.text);
  }
)?
t=(TOKEN_LIKE | TOKEN_EQUAL | TOKEN_ASSIGN | TOKEN_NOT_EQUAL | TOKEN_REGEX |
   TOKEN_LESS | TOKEN_GREATER | TOKEN_LESS_EQUAL | TOKEN_GREATER_EQUAL)
rhs=add_expression
{
  ParseString comparator =
      new ParseString($t.getLine(), $t.getCharPositionInLine(), $t.text);
  ParseOperation comparison =
      new ParseOperation($t.getLine(), $t.getCharPositionInLine(), null, comparator);
  comparison.addOperand($lhs.r);
  comparison.addOperand($rhs.r);
  $r = comparison;

  if (negateOp != null) {
  ParseOperation operation =
        new ParseOperation(negateOp.getLineNumber(), negateOp.getColumnNumber(),
                           null, negateOp);
    operation.addOperand($r);
    $r = operation;
  }
}
;


add_expression returns [ParseExpression r]
:
lhs=intersect_expression
{
  $r = $lhs.r;
}
(
  t=(TOKEN_PLUS | TOKEN_MINUS) rhs=intersect_expression
  {
    ParseString operator =
        new ParseString($t.getLine(), $t.getCharPositionInLine(), $t.text);
    ParseOperation operation =
        new ParseOperation($t.getLine(), $t.getCharPositionInLine(), null, operator);
    operation.addOperand($r);
    operation.addOperand($rhs.r);
    $r = operation;
  }
)*
;

intersect_expression returns [ParseExpression r]
:
lhs=function_call
{
  $r = $lhs.r;
}
(
  t=TOKEN_INTERSECT rhs=function_call
  {
    ParseString operator =
        new ParseString($t.getLine(), $t.getCharPositionInLine(), $t.text);
    ParseOperation operation =
        new ParseOperation($t.getLine(), $t.getCharPositionInLine(), null, operator);
    operation.addOperand($r);
    operation.addOperand($rhs.r);
    $r = operation;
  }
)*
;

function_call returns [ParseExpression r]
:
factor
{
  $r = $factor.r;
}
|
{
  ParseExpression subject = null;
  ArrayList<ParseExpression> arguments = null;
}
(
  s=factor
  {
    subject = $s.r;
  }
  TOKEN_DOT
)? f=name TOKEN_LPAREN
(
  al=argument_list
  {
    arguments = $al.r;
  }
)? TOKEN_RPAREN
{
  ParseOperation operation =
      new ParseOperation($f.r.getLineNumber(), $f.r.getColumnNumber(), subject, $f.r.getName());
  if (arguments != null) {
    for (ParseExpression e : arguments) {
      operation.addOperand(e);
    }
  }
  $r = operation;
}
(
  TOKEN_DOT f=name TOKEN_LPAREN
  {
    arguments = null;
  }
  (
    al=argument_list
    {
      arguments = $al.r;
    }
  )? TOKEN_RPAREN
  {
    ParseOperation ro =
        new ParseOperation($f.r.getLineNumber(), $f.r.getColumnNumber(), $r, $f.r.getName());
    if (arguments != null) {
      for (ParseExpression e : arguments) {
        ro.addOperand(e);
      }
    }
    $r = ro;
  }
)*
;

factor returns [ParseExpression r]
:
literal
{
  $r = $literal.r;
}
|
variable
{
  $r = $variable.r;
}
|
name
{
  $r = $name.r;
}
|
TOKEN_LPAREN expression TOKEN_RPAREN
{
  $r = $expression.r;
}
;

argument_list returns [ArrayList<ParseExpression> r]
:
e=expression
{
  $r = new ArrayList<ParseExpression>();
  $r.add($e.r);
}
(
  TOKEN_COMMA t=expression
  {
    $r.add($t.r);
  }
)*
;

name returns [ParseName r]
:
n=TOKEN_NAME
{
  ParseString name = new ParseString($n.getLine(), $n.getCharPositionInLine(), $n.text);
  $r = new ParseName($n.getLine(), $n.getCharPositionInLine(), name);
}
|
n=TOKEN_DOUBLE_QUOTED_NAME
{
  String value = StripQuotedStringLiteral($n.text);
  ParseString name = new ParseString($n.getLine(), $n.getCharPositionInLine(), value);
  $r = new ParseName($n.getLine(), $n.getCharPositionInLine(), name);
}
;

variable returns [ParseVariable r]
:
v=TOKEN_GRAPH_VARIABLE
{
  ParseString name = new ParseString($v.getLine(), $v.getCharPositionInLine(), $v.text);
  $r = new ParseVariable($v.getLine(), $v.getCharPositionInLine(),
                         name, GraphType.GetInstance());
}
|
v=TOKEN_GRAPH_METADATA_VARIABLE
{
  ParseString name = new ParseString($v.getLine(), $v.getCharPositionInLine(), $v.text);
  $r = new ParseVariable($v.getLine(), $v.getCharPositionInLine(),
                         name, GraphMetadataType.GetInstance());
}
;

literal returns [ParseLiteral r]
:
t=TOKEN_NUMBER
{
  Integer value = Integer.parseInt($t.text);
  $r = new ParseLiteral($t.getLine(), $t.getCharPositionInLine(),
                        new TypedValue(IntegerType.GetInstance(), value));
}
|
t=TOKEN_SINGLE_QUOTED_STRING_LITERAL
{
  String value = StripQuotedStringLiteral($t.text);
  $r = new ParseLiteral($t.getLine(), $t.getCharPositionInLine(),
                        new TypedValue(StringType.GetInstance(), value));
}
;

TOKEN_COMMA : ',' ;
TOKEN_DOT : '.' ;
TOKEN_SEMICOLON : ';' ;
TOKEN_LPAREN : '(';
TOKEN_RPAREN : ')';
TOKEN_PLUS : '+';
TOKEN_MINUS : '-';
TOKEN_INTERSECT : '&';
TOKEN_OR : 'or' | 'OR';
TOKEN_AND : 'and' | 'AND';
TOKEN_NOT : 'not' | 'NOT';
TOKEN_LIKE : 'like' | 'LIKE';
TOKEN_EQUAL : '==';
TOKEN_NOT_EQUAL : '<>' | '!=';
TOKEN_LESS : '<';
TOKEN_GREATER: '>';
TOKEN_LESS_EQUAL: '<=';
TOKEN_GREATER_EQUAL: '>=';
TOKEN_REGEX : '~' | 'regexp' | 'REGEXP';
TOKEN_ASSIGN : '=';
TOKEN_PLUS_ASSIGN : '+=';
TOKEN_MINUS_ASSIGN : '-=';
TOKEN_INTERSECT_ASSIGN : '&=';

TOKEN_NAME : '*' | ([A-Za-z][A-Za-z0-9_]*);
TOKEN_NUMBER: [0-9]+;
TOKEN_SINGLE_QUOTED_STRING_LITERAL : '\'' (~['\\] | '\\'.)* '\'';
TOKEN_DOUBLE_QUOTED_NAME : '"' (~["\\] | '\\'.)* '"';
TOKEN_GRAPH_VARIABLE : '$'[$A-Za-z0-9_]+;
TOKEN_GRAPH_METADATA_VARIABLE : '@'[$A-Za-z0-9_]+;

TOKEN_COMMENTS : ('%'|'#') (~[\n\r])* '\r'? '\n' -> skip;
TOKEN_WS : [ \t\r\n]+ -> skip ;
