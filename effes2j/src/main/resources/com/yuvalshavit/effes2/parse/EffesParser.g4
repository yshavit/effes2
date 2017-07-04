parser grammar EffesParser;

options { tokenVocab=EffesLexer; }

//------------------------------------------------------------------------------------------
// Structural

declaration:
  typeDeclaration
| methodDeclaration
;

file:
  importLine*
  declaration*
  EOF
;

importLine:
  IMPORT IDENT_TYPE
  COLON importDeclaration (COMMA importDeclaration )* NL
;

importDeclaration:
  IDENT_TYPE
| IDENT_NAME
;

//------------------------------------------------------------------------------------------
// Common

//------------------------------------------------------------------------------------------
// Types and methods
argsDeclaration:
  PAREN_OPEN
  (IDENT_NAME (COMMA IDENT_NAME)* )?
  PAREN_CLOSE
;

argsInvocation:
  PAREN_OPEN
  (expression (COMMA expression)* )?
  PAREN_CLOSE
;

methodDeclaration:
  IDENT_NAME argsDeclaration
  ARROW?
  COLON block
;

typeDeclaration:
  TYPE IDENT_TYPE
  argsDeclaration?
  ((COLON INDENT methodDeclaration+ DEDENT) | NL)
;

//------------------------------------------------------------------------------------------
// Statements
block:
  INDENT (statement)+ DEDENT
;

elseStat:
  ELIF expression COLON block elseStat?                     # IfElif
| ELSE COLON block                                          # IfElse
;

statement:
  NO_OP NL                                                  # StatNoop
| WHILE expression COLON block                              # StatWhile
| WHILE expression IS matcher COLON block                   # StatWhileIsSingle
| WHILE expression IS blockMatchers                         # StatWhileIsMulti
| IF expression COLON block elseStat?                       # StatIf
| IF expression IS matcher COLON block                      # StatIfIsSingle
| IF expression IS COLON blockMatchers                      # StatIfIsMulti
| RETURN expression? NL                                     # StatReturn
| BREAK NL                                                  # StatBreak
| IDENT_NAME EQUALS (expression | QUESTION_MARK) NL         # StatAssign
| expression DOT IDENT_NAME EQUALS expression NL            # StatQualifiedAssign
| IDENT_NAME argsInvocation NL                              # StatMethodInvoke
| expression DOT IDENT_NAME argsInvocation NL               # StatQualifiedMethodInvoke
;

//------------------------------------------------------------------------------------------
// Expressions

// Note: It's important that these expressions never contain a colon, so that matchers
// within the expression are unambiguous.
expression:
  QUOTED_STRING                                             # ExprStringLiteral
| THIS                                                      # ExprThis
| IF expression IS COLON NL expressionMatcher+              # ExprIfIs
| IDENT_TYPE argsInvocation?                                # ExprInstantiation
| IDENT_NAME argsInvocation?                                # ExprVariableOrMethodInvocation
| expression DOT IDENT_NAME argsInvocation?                 # ExprQualifiedVariableOrMethodInvocation
| expression IS NOT? matcher                                # ExprIsA
;

//------------------------------------------------------------------------------------------
// Matchers

blockMatchers:
  INDENT (matcher COLON block)+ DEDENT
;


expressionMatcher:
  IDENT (matcher COLON expression NL)+ DEDENT
;

matcher:
  ASTERISK (COLON expression)?                                                       # MatchAny
| AT? IDENT_NAME (COLON expression)?                                                 # MatchBind
| (AT? IDENT_NAME)? IDENT_TYPE (PAREN_OPEN matcher (COMMA matcher)* PAREN_CLOSE)?    # MatchType
| (AT? IDENT_NAME COLON)? REGEX_START REGEX? REGEX_END                               # MatchRegex
| QUOTED_STRING                                                                      # MatchStringLiteral
;
