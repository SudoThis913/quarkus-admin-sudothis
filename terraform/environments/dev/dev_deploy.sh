#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# dev_deploy.sh Development deployment helper for the Quarkus-Admin stack
# ---------------------------------------------------------------------------

set -euo pipefail

# 1. Resolve script directory (follow symlinks) and cd into it
SOURCE="${BASH_SOURCE[0]}"
while [[ -h "$SOURCE" ]]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ "$SOURCE" != /* ]] && SOURCE="$DIR/$SOURCE"
done
SCRIPT_DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR" || { echo "Cannot cd to $SCRIPT_DIR"; exit 1; }

# Go to project root to build
PROJECT_ROOT="$( cd ../../../ >/dev/null 2>&1 && pwd )"
cd "$PROJECT_ROOT" || { echo "Cannot cd to $PROJECT_ROOT"; exit 1; }

echo "Running in $SCRIPT_DIR"

# 2. CLI flags
FULL_RESET=false
AUTO_APPROVE=false # New variable
for arg in "$@"; do
  if [[ "$arg" == "--full-reset" ]]; then
    FULL_RESET=true
  elif [[ "$arg" == "--accept-all" ]]; then
    AUTO_APPROVE=true
  fi
done

echo "Cleaning up stale Docker containers..."

# Cleanup logic (simplified for dev)
# Prefixing with dev- ensures we don't kill local/prod containers
# Also cleaning up potential stale flyway-init from previous runs
for c in dev-quarkus-mysql dev-quarkus-admin dev-quarkus-redis dev-quarkus-mysql-flyway flyway-init keycloak; do
  echo "Checking for container: $c"
  if docker container inspect "$c" &>/dev/null; then
    echo "Found container: $c. Attempting to remove..."
    if timeout 10s docker rm -f "$c"; then # Add a 10s timeout to rm -f
      echo "Successfully removed container: $c"
    else
      echo "WARNING: Failed to remove container: $c within 10 seconds. It might be stuck."
      # Optionally, try harder or log more
    fi
  else
    echo "Container $c not found or already removed."
  fi
done



if $FULL_RESET && docker image inspect mysql:8 &>/dev/null; then
  echo "Removing existing image: mysql:8"
  docker rmi -f mysql:8
fi


# Build the Quarkus app and Docker image
echo " Building Quarkus App (Uber Jar)..."
./mvnw package -DskipTests

echo "Whale Building Docker Image (quarkus-admin:dev)..."
docker build -f src/main/docker/Dockerfile.jvm -t quarkus-admin:dev .


# 3. Terraform workflow
echo "Running Terraform deployment steps..."


cd "$SCRIPT_DIR" || { echo "Cannot cd back to ${SCRIPT_DIR}"; exit 1; } # Cd back to terraform dir for apply
terraform init -reconfigure -upgrade
terraform plan -out=tf_plan
terraform show tf_plan > tf_plan_readable.txt

PLAN_FILE="$SCRIPT_DIR/tf_plan"
READABLE="$SCRIPT_DIR/tf_plan_readable.txt"

echo "Terraform plan saved at:"
echo "  $PLAN_FILE"
echo "Review with:"
echo "  cat $READABLE"

if ! $FULL_RESET && docker network inspect dev_net &>/dev/null; then
  echo "Docker network 'dev_net' already exists. Attempting to import into Terraform state..."
  # If the network exists and FULL_RESET is false, Terraform needs to adopt it.
  # The 'docker_network' resource itself can't be conditionally imported directly via `terraform import`
  # when part of a module in this way.
  # However, if Terraform attempts to `apply` and finds the network, it will often adopt it
  # into the state if it exactly matches the configuration.
  # If it doesn't match, or if a manual import is truly needed, the user would need to
  # run `terraform import docker_network.app_net dev_net` manually.
  # For now, let's rely on Terraform's implicit adoption if the state is empty or if it matches.
fi

if $AUTO_APPROVE; then
  echo "Auto-approve enabled. Applying Terraform plan..."
  terraform apply -auto-approve tf_plan
else
  echo -e "\nReview the plan and choose an option:"
  echo "  (a)pply - Apply the planned changes"
  echo "  (q)uit  - Exit without applying"
  
  read -p "Enter choice (a/q): " choice
  case "$choice" in
    a|A)

      
      echo "Applying Terraform plan..."
      terraform apply tf_plan      ;;
    q|Q)
      echo "Exiting without applying Terraform plan."
      exit 0
      ;;
    *)
      echo "Invalid choice. Exiting without applying Terraform plan."
      exit 1
      ;;
  esac
fi

echo "Starting Keycloak container manually..."

# Resolve current working directory for volume mounts
CURRENT_DIR=$(pwd)

echo "Pulling quay.io/keycloak/keycloak:latest image..."
docker pull quay.io/keycloak/keycloak:latest

docker run -d \
  --name keycloak \
  -p 8180:8080 \
  --network dev_net \
  -v "${CURRENT_DIR}/src/main/resources/keycloak-realm.json:/opt/keycloak/data/import/keycloak-realm.json:ro" \
  -v "${CURRENT_DIR}/wait-for-mysql.sh:/usr/local/bin/wait-for-it.sh" \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -e KC_DB=mysql \
  -e KC_DB_URL=jdbc:mysql://dev-quarkus-mysql:3306/keycloak \
  -e KC_DB_USERNAME=admin \
  -e KC_DB_PASSWORD=admin123 \
  -e KC_HOSTNAME=keycloak \
  -e KC_HTTP_PORT=8080 \
  -e KC_HTTPS_PORT=8443 \
  -e KC_PROXY=edge \
  -e KC_HEALTH_ENABLED=true \
  -e KC_METRICS_ENABLED=true \
  -e KC_LOG_LEVEL=INFO \
  -e KC_DB_SCHEMA=keycloak \
  -e KC_MIGRATION_STRATEGY=update \
  --entrypoint sh \
  quay.io/keycloak/keycloak:latest \
  -c "chmod +x /usr/local/bin/wait-for-it.sh && /usr/local/bin/wait-for-it.sh dev-quarkus-mysql:3306 -- /opt/keycloak/bin/kc.sh start-dev --import-realm"

echo "Waiting for Keycloak to start and stabilize (approx 60 seconds)..."
sleep 60
