#!/bin/bash

# Generic wait-for-it.sh style script

cmd="$@"

if [ -z "$cmd" ]; then
  echo "Usage: $0 host:port -- command_to_execute"
  exit 1
fi

HOSTPORT=$(echo $cmd | cut -d' ' -f1)
cmd=${cmd#* } # Remove host:port part
cmd=${cmd#-- } # Remove -- part

HOST=$(echo $HOSTPORT | cut -d: -f1)
PORT=$(echo $HOSTPORT | cut -d: -f2)

MAX_RETRIES=40
SLEEP_TIME=3
COUNTER=0

echo "Waiting for $HOST:$PORT to become available..."

until bash -c "</dev/tcp/$HOST/$PORT" &>/dev/null; do
  sleep $SLEEP_TIME
  COUNTER=$((COUNTER+1))
  echo "Attempt $COUNTER: $HOST:$PORT not ready yet."

  if [ $COUNTER -ge $MAX_RETRIES ]; then
    echo "Max retries reached. Exiting."
    exit 1
  fi
done

echo "$HOST:$PORT is up - launching application..."
exec $cmd