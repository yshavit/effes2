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
  COLON importDeclaration (COMMA importDeclaration )*
;

importDeclaration:
  IDENT_TYPE
| IDENT_NAME
;

//------------------------------------------------------------------------------------------
// Common

//------------------------------------------------------------------------------------------
// Types and methods
args:
  PAREN_OPEN
  (IDENT_NAME (COMMA IDENT_NAME)* )?
  PAREN_CLOSE
;

methodDeclaration:
  IDENT_NAME args
  ARROW?
  COLON block
;

typeDeclaration:
  TYPE IDENT_TYPE
  args?
  (COLON INDENT methodDeclaration+ DEDENT)?
;

//------------------------------------------------------------------------------------------
// Statements
block:
  INDENT statement+ DEDENT
;

elseStat:
  ELIF expression COLON block elseStat?                     # IfElif
| ELSE COLON block                                          # IfElse
;

statement:
  NO_OP                                                     # StatNoop
| WHILE expression COLON block                              # StatWhile
| WHILE expression IS blockMatchers                         # StatWhileIs
| IF expression COLON block elseStat?                       # StatIf
| IF expression IS COLON blockMatchers+                     # StatIfIs
| RETURN expression?                                        # StatReturn
| BREAK                                                     # StatBreak
| IDENT_NAME EQUALS expression                              # StatAssign
| IDENT_NAME args                                           # StatMethodInvoke
| expression DOT IDENT_NAME args                            # StatQualifiedMethodInvoke
;

//------------------------------------------------------------------------------------------
// Expressions

// Note: It's important that these expressions never contain a colon, so that matchers
// within the expression are unambiguous.
expression:
  QUOTED_STRING                                             # ExprStringLiteral
| THIS                                                      # ExprThis
| IF expression IS COLON expressionMatcher+                 # ExprIfIs
| IDENT_TYPE args?                                          # ExprInstantiation
| IDENT_NAME args?                                          # ExprVariableOrMethodInvocation
| expression DOT IDENT_NAME args?                           # ExprQualifiedVariableOrMethodInvocation
| expression IS NOT? matcher                                # ExprIsA
;

//------------------------------------------------------------------------------------------
// Matchers

blockMatchers:
  INDENT (matcher COLON block)+ DEDENT
;


expressionMatcher:
  (matcher COLON expression)+
;

matcher:
  ASTERISK (COLON expression)?                                        # MatchAny
| AT? IDENT_NAME (COLON expression)?                                  # MatchBind
| IDENT_TYPE (PAREN_OPEN matcher (COMMA matcher)* PAREN_CLOSE)?       # MatchType
| (IDENT_NAME COLON)? SLASH_REGEX REGEX                               # MatchRegex
| QUOTED_STRING                                                       # MatchStringLiteral
;
