#!/bin/bash

cd "$(dirname $0)"
COMPILER_JAR="${COMPILER_JAR-../effes2j/target/effes2j-1.0-SNAPSHOT-jar-with-dependencies.jar}"
if [[ ! -f "$COMPILER_JAR" ]]; then
  echo "Couldn't find effes2j jar at $COMPILER_JAR" 1>&2
  exit 1
fi
DEBUG_OPTS=''
if [[ -n "$EF_DEBUG" ]]; then
  DEBUG_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
fi
java $DEBUG_OPTS -Dsource=. -Doutput=../efct -jar "$COMPILER_JAR" 
