#!/usr/bin/env bash
# Run the backend against a specific Spring profile.
#
# Runs in the BACKGROUND by default (builds a jar, launches it with nohup so
# it survives closing the terminal/SSH session) and tracks it via backend.pid
# / backend.log next to this script.
#
#   ./run.sh                # local (default), background
#   ./run.sh local
#   ./run.sh prod
#   ./run.sh prod --fg      # foreground instead — blocks the terminal, Ctrl+C to stop
#   ./run.sh stop           # stop whatever's running
#   ./run.sh status         # is it running, and as what pid
#
# Requires a real JDK 21 on PATH/JAVA_HOME (javac, not just a JRE) —
# `apt install openjdk-21-jdk-headless` if this fails with
# "release version 21 not supported".
set -euo pipefail
cd "$(dirname "$0")"

PIDFILE="backend.pid"
LOG="backend.log"

is_running() {
  [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null
}

case "${1:-}" in
  stop)
    if is_running; then
      kill "$(cat "$PIDFILE")"
      rm -f "$PIDFILE"
      echo "Stopped."
    else
      echo "Not running."
    fi
    exit 0
    ;;
  status)
    if is_running; then
      echo "Running — pid $(cat "$PIDFILE")"
    else
      echo "Not running."
    fi
    exit 0
    ;;
esac

PROFILE="${1:-local}"
FOREGROUND=false
[[ "${2:-}" == "--fg" ]] && FOREGROUND=true

case "$PROFILE" in
  local|prod) ;;
  *) echo "Usage: $0 [local|prod] [--fg]   |   $0 stop   |   $0 status" >&2; exit 1 ;;
esac

if is_running; then
  echo "Already running (pid $(cat "$PIDFILE")). Run '$0 stop' first." >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE="$PROFILE"

if $FOREGROUND; then
  echo "Starting backend — profile: $PROFILE (foreground)"
  exec mvn spring-boot:run
fi

echo "Building jar — profile: $PROFILE"
mvn -q -Dmaven.test.skip=true package

JAR="$(ls target/*.jar | head -n1)"
nohup java -jar "$JAR" --spring.profiles.active="$PROFILE" > "$LOG" 2>&1 &
echo $! > "$PIDFILE"
disown

echo "Started in background — profile: $PROFILE, pid $(cat "$PIDFILE")"
echo "Logs: tail -f $LOG"
echo "Stop:  $0 stop"
