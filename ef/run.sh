#!/bin/bash
cd "$(dirname "$0")"
EVM_JAR="${EVM_JAR-../../effesvm_j/effesvm_j/target/effesvm_j-1.0-SNAPSHOT-jar-with-dependencies.jar}"
if [[ ! -f "$EVM_JAR" ]]; then
  echo "Couldn't find EVM jar at $EVM_JAR" 1>&2
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
java $EF_DEBUG $EVM_DEBUG $YOURKIT_AGENT_OPTS $COVERAGE -jar "$EVM_JAR" "$@"
