#!/usr/bin/env bash

SENSEI_HOME=/home/pi/sensei
APP_NAME="sensei"

# Signal the monitoring script to stop
touch "$SENSEI_HOME/stop.${APP_NAME}"
echo "Stop signal sent"

# Wait briefly to allow the monitoring loop to exit
sleep 3

# Ensure the application is stopped
PID=$(pgrep -f "java.*${APP_NAME}.*jar")
if [ ! -z "${PID}" ]; then
    kill $PID 2>/dev/null
    echo "Application process terminated"
fi

# Clean up the stop signal file
rm -f "$SENSEI_HOME/stop.${APP_NAME}"

exit 0
