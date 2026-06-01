#!/usr/bin/env bash

JAVA_OPTS="--enable-native-access=ALL-UNNAMED"

if ! command -v java &> /dev/null; then
    echo "[ERROR] Java not found. Please install JDK 17+."
    exit 1
fi

echo "Starting AzurLaneServer..."
java $JAVA_OPTS -jar server.jar "$@"
