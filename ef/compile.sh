#!/bin/bash

cd "$(dirname $0)"
compiler_jar=../effes2j/target/effes2j-1.0-SNAPSHOT-jar-with-dependencies.jar
DEBUG_OPTS=''
if [[ -n "$EF_DEBUG" ]]; then
  DEBUG_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005'
fi
java $DEBUG_OPTS -Dsource=. -Doutput=../efct -jar "$compiler_jar" 
