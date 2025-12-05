#!/bin/bash

MAX_RETRIES=20
SLEEP_TIME=3
COUNTER=0

DB_HOST=${DB_HOST:-"localhost"} # Default to localhost if not set
DB_PORT=${DB_PORT:-"3306"}

echo "Waiting for MySQL at ${DB_HOST}:${DB_PORT} to become available..."

until nc -z ${DB_HOST} ${DB_PORT}; do
  sleep $SLEEP_TIME
  COUNTER=$((COUNTER+1))
  echo "Attempt $COUNTER: MySQL not ready yet."

  if [ $COUNTER -ge $MAX_RETRIES ]; then
    echo "Max retries reached. Exiting."
    exit 1
  fi
done

echo "MySQL is up - launching application..."
# The original Dockerfile.jvm used /opt/jboss/container/java/run/run-java.sh
# We need to ensure that the original entrypoint logic is still executed.
# It typically involves starting the JAR or executing the run-java.sh script.
# Assuming the default Quarkus Docker image puts the JAR at /deployments/quarkus-run.jar
# and the run-java.sh script is already part of the base image setup.
# Let's call the original entrypoint script after successful connection.
exec /opt/jboss/container/java/run/run-java.sh
