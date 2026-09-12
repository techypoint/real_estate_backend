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

# Requires a real JDK 21 on PATH/JAVA_HOME (javac, not just a JRE) —
# `sudo apt install openjdk-21-jdk` if `mvn compile` fails with
# "release version 21 not supported".
export SPRING_PROFILES_ACTIVE="$PROFILE"
cd "$(dirname "$0")"

echo "Starting backend — profile: $PROFILE"
exec mvn spring-boot:run
