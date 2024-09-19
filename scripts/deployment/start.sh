#!/usr/bin/env bash

SENSEI_HOME=/home/pi/sensei
APP_JAR="$SENSEI_HOME/sensei-0.1.0-SNAPSHOT-all.jar"
APP_LOG="$SENSEI_HOME/sensei.output"
APP_NAME="sensei"

# Function to start the application
start_app() {
    nohup java -jar "$APP_JAR" > "$APP_LOG" 2>&1 &
}

# Function to stop the application
stop_app() {
    PID=$(pgrep -f "java.*${APP_NAME}.*jar")
    if [ ! -z "${PID}" ]; then
        kill $PID 2>/dev/null
    fi
}

# Start the application
start_app

# Record the initial modification time of the JAR file
LAST_MOD_TIME=$(stat -c %Y "$APP_JAR")

echo "Monitoring $APP_JAR for changes..."

# Start monitoring the JAR file for changes
while true; do
    sleep 2
    NEW_MOD_TIME=$(stat -c %Y "$APP_JAR")
    if [ "$NEW_MOD_TIME" != "$LAST_MOD_TIME" ]; then
        echo "Detected change in $APP_JAR. Restarting application..."
        stop_app
        LAST_MOD_TIME=$NEW_MOD_TIME
        start_app
    fi
    # Check for stop signal
    if [ -f "$SENSEI_HOME/stop.${APP_NAME}" ]; then
        echo "Stop signal detected. Stopping monitoring and application..."
        stop_app
        rm "$SENSEI_HOME/stop.${APP_NAME}"
        exit 0
    fi
done &
