#!/bin/bash
set -x
cd "$(dirname "$0")"
effes_jar=/mnt/c/Users/yuval/repos/effesvm_j/effesvm_j/target/effesvm_j-1.0-SNAPSHOT-jar-with-dependencies.jar
DEBUG_OPTS=''
if [[ -n "$EF_DEBUG" ]]; then
  DEBUG_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
fi
export EFFES_CLASSPATH=../efct
java -jar "$effes_jar" "$@"
