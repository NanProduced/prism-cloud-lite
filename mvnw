#!/bin/sh
# Apache Maven Wrapper
# https://maven.apache.org/wrapper/

set -euf

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
JAR="$WRAPPER_DIR/maven-wrapper.jar"
PROPS="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -f "$JAR" ]; then
  echo "maven-wrapper.jar is missing: $JAR" >&2
  exit 1
fi

JAVA_CMD="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_CMD" ]; then
  JAVA_CMD="java"
fi

exec "$JAVA_CMD" -classpath "$JAR" -Dmaven.multiModuleProjectDirectory="$BASE_DIR" org.apache.maven.wrapper.MavenWrapperMain "$@"

