#!/bin/bash

MAX_RETRIES=20
SLEEP_TIME=3
COUNTER=0

echo "Waiting for MySQL to become available..."

until nc -z quarkus-mysql 3306; do
  sleep $SLEEP_TIME
  COUNTER=$((COUNTER+1))
  echo "Attempt $COUNTER: MySQL not ready yet."

  if [ $COUNTER -ge $MAX_RETRIES ]; then
    echo "Max retries reached. Exiting."
    exit 1
  fi
done

echo "MySQL is up - launching application..."
exec java -jar /app/quarkus-run.jar
