#!/bin/bash

PID_FILE="/home/imed/keycloak-21.1.1/keycloak.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    echo "Stopping Keycloak (PID $PID)..."
    kill $PID
    rm $PID_FILE
else
    echo "PID file not found."
fi
