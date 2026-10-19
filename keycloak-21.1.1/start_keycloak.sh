#!/bin/bash

KEYCLOAK_HOME="/home/imed/keycloak-21.1.1"
LOG_FILE="$KEYCLOAK_HOME/keycloak.log"

echo "Starting Keycloak..."

cd $KEYCLOAK_HOME/bin || exit 1

# Start Keycloak in background and redirect logs
nohup ./kc.sh start-dev > "$LOG_FILE" 2>&1 &

PID=$!

echo "Keycloak started with PID: $PID"
echo $PID > "$KEYCLOAK_HOME/keycloak.pid"

echo "Logs: tail -f $LOG_FILE"
