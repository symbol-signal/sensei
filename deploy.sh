#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="rpi_central"
REMOTE_DIR="/opt/sensei"

echo "Building fat jar..."
./gradlew shadowJar

JAR_PATH=$(ls build/libs/*-all.jar)
JAR_NAME=$(basename "$JAR_PATH")

echo "Deploying $JAR_NAME to $REMOTE_HOST..."
scp "$JAR_PATH" "$REMOTE_HOST:/tmp/"

ssh "$REMOTE_HOST" "sudo systemctl stop sensei.service && sudo mv /tmp/$JAR_NAME $REMOTE_DIR/ && sudo ln -sf $REMOTE_DIR/$JAR_NAME $REMOTE_DIR/sensei.jar && sudo systemctl start sensei.service"

echo "Done. Checking service status..."
ssh "$REMOTE_HOST" "sudo systemctl status sensei.service --no-pager -l" || true
