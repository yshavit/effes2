#!/bin/bash
cd "$(dirname "$0")"
effes_jar=/mnt/c/Users/yuval/repos/effesvm_j/effesvm_j/target/effesvm_j-1.0-SNAPSHOT-jar-with-dependencies.jar
if [[ "$EF_DEBUG" == 1 ]]; then
  EF_DEBUG='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
elif [[ -n "$EF_DEBUG" ]]; then
  echo "Using EF_DEBUG=$EF_DEBUG" 1>&2
fi
export EFFES_CLASSPATH=../efct
if [[ -n "$EVM_DEBUG" ]]; then
  EVM_DEBUG="-Ddebug=$EVM_DEBUG"
fi
java $EF_DEBUG $EVM_DEBUG -jar "$effes_jar" "$@"
