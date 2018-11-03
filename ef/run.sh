#!/bin/bash
cd "$(dirname "$0")"
EVM_HOME="${EVM_HOME-../../effesvm_j/effesvm_j/target}"
if [[ ! -d "$EVM_HOME" ]]; then
  echo "EVM_HOME not set or is not a directory: $EVM_HOME" >&2
  exit 1
fi
effes_jar=$EVM_HOME/effesvm_j-1.0-SNAPSHOT-jar-with-dependencies.jar
if [[ ! -f "$effes_jar" ]]; then
  echo "Couldn't find EVM jar at $effes_jar" 1>&2
  exit 1
fi
if [[ "$EF_DEBUG" == 1 ]]; then
  EF_DEBUG='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
elif [[ -n "$EF_DEBUG" ]]; then
  echo "Using EF_DEBUG=$EF_DEBUG" 1>&2
fi
export EFFES_CLASSPATH=../efct
if [[ -n "$EVM_DEBUG" ]]; then
  EVM_DEBUG="-Ddebug=$EVM_DEBUG"
fi
if [[ -n "$COVERAGE" ]]; then
  COVERAGE="-Dcoverage=$COVERAGE"
fi
if [[ -n "${YOURKIT+x}" && -n "$YOURKIT_AGENT" ]]; then
  # https://www.yourkit.com/docs/java/help/startup_options.jsp
  YOURKIT_OPTS="dir=yourkit_profiles,${YOURKIT:-sampling}"
  YOURKIT_AGENT_OPTS="-agentpath:$YOURKIT_AGENT=$YOURKIT_OPTS"
fi
java $EF_DEBUG $EVM_DEBUG $YOURKIT_AGENT_OPTS $COVERAGE -jar "$effes_jar" "$@"
