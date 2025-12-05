#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# dev_deploy.sh — Development deployment helper for the Quarkus-Admin stack
# ---------------------------------------------------------------------------

set -euo pipefail

# 1. Resolve script directory (follow symlinks) and cd into it
SOURCE="${BASH_SOURCE[0]}"
while [[ -h "$SOURCE" ]]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
SCRIPT_DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR" || { echo "Cannot cd to $SCRIPT_DIR"; exit 1; }

echo "Running in $SCRIPT_DIR"

# 2. CLI flags
FULL_RESET=false
AUTO_APPROVE=false # New variable
for arg in "$@"; do
  if [[ "$arg" == "--full-reset" ]]; then
    FULL_RESET=true
  elif [[ "$arg" == "-auto-approve" ]]; then
    AUTO_APPROVE=true
  fi
done

echo "Cleaning up stale Docker containers..."

# Cleanup logic (simplified for dev)
# Prefixing with dev- ensures we don't kill local/prod containers
# Also cleaning up potential stale flyway-init from previous runs
for c in dev-quarkus-mysql dev-quarkus-admin dev-quarkus-redis dev-quarkus-mysql-flyway flyway-init; do
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

if $FULL_RESET && docker network inspect dev_net &>/dev/null; then
  echo "Removing existing network: dev_net"
  docker network rm dev_net
fi

if $FULL_RESET && docker image inspect mysql:8 &>/dev/null; then
  echo "Removing existing image: mysql:8"
  docker rmi -f mysql:8
fi

# 3. Terraform workflow
echo "Running Terraform deployment steps..."
terraform -chdir="$SCRIPT_DIR" init
terraform -chdir="$SCRIPT_DIR" plan -out=tf_plan
terraform -chdir="$SCRIPT_DIR" show tf_plan > tf_plan_readable.txt

PLAN_FILE="$SCRIPT_DIR/tf_plan"
READABLE="$SCRIPT_DIR/tf_plan_readable.txt"

echo "Terraform plan saved at:"
echo "  $PLAN_FILE"
echo "Review with:"
echo "  cat $READABLE"

if $AUTO_APPROVE; then
  echo "Auto-approve enabled. Applying Terraform plan..."
  terraform -chdir="$SCRIPT_DIR" apply -auto-approve tf_plan
else
  echo -e "\nReview the plan and choose an option:"
  echo "  (a)pply - Apply the planned changes"
  echo "  (q)uit  - Exit without applying"
  
  read -p "Enter choice (a/q): " choice
  case "$choice" in
    a|A)
      echo "Applying Terraform plan..."
      terraform -chdir="$SCRIPT_DIR" apply tf_plan
      ;;
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
