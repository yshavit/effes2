lexer grammar EffesLexer;

tokens { INDENT, DEDENT }
@lexer::header {
  import com.yuvalshavit.antlr4.DenterHelper;
  import com.yuvalshavit.antlr4.DenterOptions;
}
@lexer::members {
  private final DenterHelper denter = DenterHelper.builder()
    .nl(NL)
    .indent(EffesParser.INDENT)
    .dedent(EffesParser.DEDENT)
    .pullToken(EffesLexer.super::nextToken);

  @Override
  public Token nextToken() {
    return denter.nextToken();
  }
  
  public DenterOptions getDenterOptions() {
    return denter.getOptions();
  }
}

//==========================================================================================
// Default mode

//------------------------------------------------------------------------------------------
// literals
QUOTED_STRING : DQUOTE ( ~["\r\n\\] )* DQUOTE ;
fragment DQUOTE         : '"' ;
INT : '0' | ([1-9] [0-9]*) ;

//------------------------------------------------------------------------------------------
// keywords
BREAK : 'break' ;
ELIF : 'elif' ;
ELSE : 'else' ;
IMPORT : 'import' ;
IF : 'if' ;
IS : 'is' ;
NOT : 'not' ;
RETURN : 'return' ;
THIS : 'this' ;
TYPE : 'type' ;
WHILE : 'while' ;

//------------------------------------------------------------------------------------------
// operators and punctuation
ARROW : '->' ;
ASTERISK : '*' ;
COLON : ':' ;
COMMA : ',' ;
DOT : '.' ;
EQUALS : '=' ;
NO_OP : ':::' ;
PAREN_OPEN : '(' ;
PAREN_CLOSE : ')' ;
PIPE : '|' ;
PLUS : '+' ;
QUESTION_MARK : '?' ;
REGEX_START : '~/' -> mode(REGEX_MODE) ;
SLASH : '/' ;

//------------------------------------------------------------------------------------------
// identifiers
IDENT_TYPE : '_'* [A-Z] GENERIC_IDENT_CHAR* [a-z] GENERIC_IDENT_CHAR* ;
IDENT_NAME : '@' ? '_'* [a-z] GENERIC_IDENT_CHAR* ;
IDENT_GENERIC : [A-Z]+ ;
fragment GENERIC_IDENT_CHAR : [A-Za-z0-9_];

//------------------------------------------------------------------------------------------
// whitespace and comments
NL: ('\r'? '\n' ' '*) | EOF;
WS: [ \t]+ -> skip;
LINE_COMMENT: '#' ~[\r\n]* -> skip;

//==========================================================================================
// Regex mode
mode REGEX_MODE;
REGEX: REGEX_CHAR+ ;
REGEX_END: '/' -> mode(DEFAULT_MODE) ;

fragment REGEX_CHAR:
  ~[\r\n\\/]
| ('\\' [tnrdDsSwWbBAGZzQE\\()[\]] )
;
