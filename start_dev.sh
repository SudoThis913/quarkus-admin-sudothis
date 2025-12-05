#!/bin/bash
set -e

# Ensure we are in the script's directory
cd "$(dirname "$0")"

# Check for Maven Wrapper execution permissions
if [ ! -x "./mvnw" ]; then
    echo "🔧 Granting execute permissions to mvnw..."
    chmod +x ./mvnw
fi

# Check if init_setup.sh has been run (look for secrets)
if [ ! -f "secrets/dev-keystore.p12" ] || [ ! -f ".env" ]; then
    echo "⚠️  Environment not initialized. Running init_setup.sh..."
    if [ -x "./init_setup.sh" ]; then
        ./init_setup.sh
    else
        chmod +x ./init_setup.sh
        ./init_setup.sh
    fi
fi

# Check for Docker (Required for Dev Services)
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running or accessible. Quarkus Dev Services requires Docker."
    echo "   Please start Docker and try again."
    exit 1
fi

echo "🚀 Starting Quarkus in Dev Mode..."
echo "   - Admin UI: http://localhost:8081/admin"
echo "   - Dev UI:   http://localhost:8081/q/dev"
echo "   - API:      http://localhost:8081/api"

# Source .env to ensure environment variables are available to the process
# (Quarkus reads .env, but explicit sourcing doesn't hurt for shell context)
if [ -f .env ]; then
  set -a
  source .env
  set +a
fi

./mvnw quarkus:dev