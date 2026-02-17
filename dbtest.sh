#!/usr/bin/env bash
#
# Wrapper script to run oracle-jdbc-test
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/app/build/libs/oracle-jdbc-test-1.0.0.jar"

if [[ ! -f "$JAR_PATH" ]]; then
    echo "Error: JAR not found at $JAR_PATH" >&2
    echo "Run './gradlew shadowJar' to build." >&2
    exit 2
fi

exec java -Doracle.jdbc.diagnostic.enableLogging=true -Djava.util.logging.config.file=jul.properties -jar "$JAR_PATH" "$@"
