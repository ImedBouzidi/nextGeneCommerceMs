#!/bin/bash

KEYCLOAK_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$KEYCLOAK_HOME/keycloak.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    echo "Stopping Keycloak (PID $PID)..."
    kill $PID
    rm $PID_FILE
else
    echo "PID file not found."
fi
