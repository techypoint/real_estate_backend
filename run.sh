#!/usr/bin/env bash
# Run the backend against a specific Spring profile.
#
#   ./run.sh            # local (default) — Mongo via the SSH tunnel on :27018
#   ./run.sh local
#   ./run.sh prod        # Mongo over loopback on :27017 (see application-prod.properties)
set -euo pipefail

PROFILE="${1:-local}"
case "$PROFILE" in
  local|prod) ;;
  *) echo "Usage: $0 [local|prod]" >&2; exit 1 ;;
esac

# This machine has no system JDK — `java` is a JRE, `javac` doesn't exist
# under /usr/lib/jvm (see CLAUDE.md). Fall back to IntelliJ's bundled JDK
# unless JAVA_HOME is already set to something that actually has javac.
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
  export JAVA_HOME=/home/vbeast/.jdks/ms-21.0.7
fi

export SPRING_PROFILES_ACTIVE="$PROFILE"
cd "$(dirname "$0")"

echo "Starting backend — profile: $PROFILE, JAVA_HOME: $JAVA_HOME"
exec mvn spring-boot:run
