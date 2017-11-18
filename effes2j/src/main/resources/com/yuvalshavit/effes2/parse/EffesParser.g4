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
  INDENT
  (blockStop | (statement+ blockStop?) )
  DEDENT
;

blockStop:
  BREAK NL
| CONTINUE NL
| RETURN expression? NL
| RETURN expressionMultiline
;

elseStat:
  ELIF expression COLON block elseStat?                     # IfElif
| ELSE COLON block                                          # IfElse
;

statement:
  NO_OP NL                                                  # StatNoop
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
  IF expression IS COLON expressionMatchers                 # ExprIfIs
;

qualifiedIdentName:
 (qualifiedIdentNameStart DOT)?
 IDENT_NAME
 (DOT qualifiedIdentNameEnd)*
;

qualifiedIdentNameEnd:
  IDENT_NAME argsInvocation?
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
