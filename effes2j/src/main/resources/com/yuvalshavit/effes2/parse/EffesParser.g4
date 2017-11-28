parser grammar EffesParser;

options { tokenVocab=EffesLexer; }

//------------------------------------------------------------------------------------------
// Structural

declaration:
  typeDeclaration
| methodDeclaration
;

file:
  declaration+
  EOF
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
  INDENT
  (blockStop | (statement+ blockStop?) )
  DEDENT
;

blockStop:
  BREAK NL                                                  # BlockStopBreak
| CONTINUE NL                                               # BlockStopContinue
| RETURN (expression? NL | expressionMultiline)             # BlockStopReturn
;

elseStat:
  ELSE IF  expression COLON block elseStat?                 # IfElif
| ELSE COLON block                                          # IfElse
;

statement:
  NO_OP NL                                                  # StatNoop
| NO_OP IDENT_NAME IS IDENT_TYPE NL                         # StatTypeAssertion
| expression NO_OP matcher NL                               # StatMatch
| WHILE expression statementWhileConditionAndBody           # StatWhile
| FOR IDENT_NAME IN expression COLON block                  # StatFor
| IF expression statementIfConditionAndBody                 # StatIf
| qualifiedIdentName argsInvocation NL                      # StatMethodInvoke
| qualifiedIdentName EQUALS expression NL                   # StatAssign
| qualifiedIdentName EQUALS expressionMultiline             # StatAssignMultiline
| IDENT_NAME EQUALS QUESTION_MARK NL                        # StatVarDeclare
//| expression DOT IDENT_NAME EQUALS expression NL            # StatQualifiedAssign
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

cmp:
  CMP_EQ
| CMP_GE
| CMP_GT
| CMP_LE
| CMP_LT
| CMP_NE
;

// Note: It's important that these expressions never contain a colon, so that matchers
// within the expression are unambiguous.
expression:
  expression IS NOT? matcher                                # ExprIsA   // "matcher" is more broad than we need, but simplifies impl
| QUOTED_STRING                                             # ExprStringLiteral
| INT                                                       # ExprIntLiteral
| THIS                                                      # ExprThis
| qualifiedIdentName argsInvocation?                        # ExprVariableOrMethodInvocation
| IDENT_TYPE argsInvocation?                                # ExprInstantiation
| PAREN_OPEN expression PAREN_CLOSE                         # ExprParenthesis
| NOT expression                                            # ExprNegation
| expression (ASTERISK | SLASH) expression                  # ExprMultOrDivide
| expression (PLUS | DASH) expression                       # ExprPlusOrMinus
| expression cmp expression                                 # ExprCmp
;

// Expressions that span more than one line. Each one of these should include its own NLs
// as needed.
expressionMultiline:
  IF expression IS COLON expressionMatchers
;

qualifiedIdentName:
 (qualifiedIdentNameStart DOT)?
 qualifiedIdentNameMiddle*
 IDENT_NAME
;

qualifiedIdentNameMiddle:
  IDENT_NAME /* argsInvocation? */ DOT
;

qualifiedIdentNameStart:
  IDENT_TYPE                                                # QualifiedIdentType
| THIS                                                      # QualifiedIdentThis
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

// Logically, this is: "IDENT_NAME? matcherStuff?" except that we need at least one of them.
// If we had this as just one inlined variant, then the empty string would parse as a matcher,
// which we don't want. So, we take the "just name, no matcher" bit and make it its own rule.
matcher:
  ASTERISK                                                            # MatcherAny
| (AT ? IDENT_NAME)? matcherPattern                                   # MatcherWithPattern
| AT ? IDENT_NAME (SUCH_THAT expression)?                             # MatcherJustName
;

matcherPattern:
  IDENT_TYPE (PAREN_OPEN matcher (COMMA matcher)* PAREN_CLOSE)?       # PatternType
| REGEX_START REGEX? REGEX_END                                        # PatternRegex
| QUOTED_STRING                                                       # PatternStringLiteral
;
