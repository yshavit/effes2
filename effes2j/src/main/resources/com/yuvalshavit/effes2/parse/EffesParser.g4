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
  importDeclaration (COMMA importDeclaration)*              # ExplicitImports
| ASTERISK                                                  # AllImports
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
| FOR IDENT_NAME IN expression COLON block                  # StatFor
| IF expression statementIfConditionAndBody                 # StatIf
| RETURN expression? NL                                     # StatReturn
| RETURN expressionMultiline                                # StatReturnMultiline
| BREAK NL                                                  # StatBreak
| qualifiedIdentName argsInvocation NL                      # StatMethodInvoke
| qualifiedIdentName EQUALS expression NL                   # StatAssign
| qualifiedIdentName EQUALS expressionMultiline             # StatAssignMultiline
| IDENT_NAME EQUALS QUESTION_MARK NL                        # StatVarDeclare
| expression DOT IDENT_NAME EQUALS expression NL            # StatQualifiedAssign
;

statementIfConditionAndBody:
  COLON block elseStat?                                     # IfElseSimple
| IS COLON blockMatchers                                    # IfMatchMulti
;

statementWhileConditionAndBody:
  COLON block                                               # WhileBodySimple
| IS COLON blockMatchers                                    # WhileBodyMultiMatchers
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
| qualifiedIdentName argsInvocation?                        # ExprVariableOrMethodInvocation
| IDENT_TYPE argsInvocation?                                # ExprInstantiation
| PAREN_OPEN expression PAREN_CLOSE                         # ExprParenthesis
| NOT expression                                            # ExprNegation
| expression (ASTERISK | SLASH) expression                  # ExprMultOrDivide
| expression (PLUS | DASH) expression                       # ExprPlusOrMinus
;

// Expressions that span more than one line. Each one of these should include its own NLs
// as needed.
expressionMultiline:
  IF expression IS COLON expressionMatchers                 # ExprIfIs
;

qualifiedIdentName:
 (IDENT_TYPE DOT)?
 (IDENT_NAME argsInvocation? DOT)*
 IDENT_NAME
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
  INDENT expressionMatcher+ DEDENT
;

expressionMatcher:
  matcher COLON expression NL
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
