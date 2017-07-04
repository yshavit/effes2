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
  COLON importDeclarations NL
;

importDeclaration:
  IDENT_TYPE
| IDENT_NAME
;

importDeclarations:
  importDeclaration (COMMA importDeclaration)*
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
| WHILE expression statementWhileConditionAndBody           # StatWhile
| IF expression statementIfConditionAndBody                 # StatIf
| RETURN expression? NL                                     # StatReturn
| BREAK NL                                                  # StatBreak
| IDENT_NAME argsInvocation NL                              # StatMethodInvoke
| IDENT_NAME EQUALS expression NL                           # StatAssign
| IDENT_NAME EQUALS QUESTION_MARK NL                        # StatVarDeclare
| expression DOT IDENT_NAME argsInvocation NL               # StatQualifiedMethodInvoke
| expression DOT IDENT_NAME EQUALS expression NL            # StatQualifiedAssign
;

statementIfConditionAndBody:
  COLON block elseStat?                                     # IfElseSimple
| IS matcher COLON block                                    # IfMatchSingle
| IS COLON blockMatchers                                    # IfMatchMulti
;

statementWhileConditionAndBody:
  COLON block                                               # WhileSimple
| IS matcher COLON block                                    # WhileMatchSingle
| IS COLON blockMatchers                                    # WhileMatchMulti
;

//------------------------------------------------------------------------------------------
// Expressions

// Note: It's important that these expressions never contain a colon, so that matchers
// within the expression are unambiguous.
expression:
  expression IS NOT? matcher                                # ExprIsA
| QUOTED_STRING                                             # ExprStringLiteral
| INT                                                       # ExprIntLiteral
| THIS                                                      # ExprThis
| IF expression IS COLON NL expressionMatchers              # ExprIfIs
| IDENT_TYPE argsInvocation?                                # ExprInstantiation
| IDENT_NAME argsInvocation?                                # ExprVariableOrMethodInvocation
| expression DOT IDENT_NAME argsInvocation?                 # ExprQualifiedVariableOrMethodInvocation
;

//------------------------------------------------------------------------------------------
// Matchers

blockMatcher:
  matcher COLON block
;

blockMatchers:
  INDENT blockMatcher+ DEDENT
;

expressionMatchers:
  matcher COLON expression NL
;

expressionMatcher:
  INDENT expressionMatcher  DEDENT
;

matcher:
  IDENT_NAME? matcherPattern (PIPE expression)?                       # MatcherWithPattern
| IDENT_NAME                                                          # MatcherJustName
;

matcherPattern:
  ASTERISK                                                            # PatternAny
| IDENT_TYPE (PAREN_OPEN matcher (COMMA matcher)* PAREN_CLOSE)?       # PatternType
| REGEX_START REGEX? REGEX_END                                        # PatternRegex
| QUOTED_STRING                                                       # PatternStringLiteral
;
