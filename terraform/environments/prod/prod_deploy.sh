#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# prod_deploy.sh — Production deployment helper for the Quarkus-Admin stack
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
RESET_DB=false
RESET_REDIS=false
RESET_QUARKUS=true
RESET_MAILHOG=false
RESET_CERTBOT=false
AUTO_APPROVE=false # New variable

# Parse arguments
for arg in "$@"; do
  case "$arg" in
    --full-reset)
      RESET_DB=true
      RESET_REDIS=true
      RESET_QUARKUS=true
      RESET_MAILHOG=true
      RESET_CERTBOT=true
      ;;
    -auto-approve)
      AUTO_APPROVE=true
      ;;
    *)
      # Ignore other args for now, or handle specific resets
      ;;
  esac
done



echo "Cleaning up stale Docker containers for prod environment..."

# Containers that are always removed for prod environment
for c in prod-quarkus-mysql prod-quarkus-admin prod-quarkus-redis prod-mailhog prod-certbot-sync prod-quarkus-mysql-flyway; do
  echo "Checking for container: $c"
  if docker container inspect "$c" &>/dev/null; then
    echo "Found container: $c. Attempting to remove..."
    if timeout 10s docker rm -f "$c"; then
      echo "Successfully removed container: $c"
    else
      echo "WARNING: Failed to remove container: $c within 10 seconds. It might be stuck."
    fi
  else
    echo "Container $c not found or already removed."
  fi
done

# Network (only on full reset)
if $FULL_RESET && docker network inspect prod_net &>/dev/null; then
  echo "Removing existing network: prod_net"
  docker network rm prod_net
fi

# Image (only on full reset)
for img in mysql:8 redis:7 quarkus-admin:prod prod-certbot-sync; do
  if $FULL_RESET && docker image inspect "$img" &>/dev/null; then
    echo "Removing image: $img"
    docker rmi -f "$img"
  fi
done

echo "Running terraform init..."
terraform -chdir="$SCRIPT_DIR" init -upgrade

# ==========================
# Build target list
# ==========================
declare -a TARGETS=()
$RESET_DB      && TARGETS+=("-target=module.mysql")
$RESET_REDIS   && TARGETS+=("-target=module.redis")
$RESET_QUARKUS && TARGETS+=("-target=module.quarkus_app")
$RESET_CERTBOT && TARGETS+=("-target=module.certbot_sync") # Updated to module
# Mailhog intentionally excluded from prod
# $RESET_MAILHOG && TARGETS+=("-target=module.mailhog")

# If no individual flags are set, do full plan
if [[ ${#TARGETS[@]} -eq 0 ]]; then
  echo "No specific targets selected. Doing full terraform plan."
  terraform -chdir="$SCRIPT_DIR" plan -out=tf.plan | tee tf_plan_output.txt
else
  echo "Planning with targets: ${TARGETS[*]}"
  terraform -chdir="$SCRIPT_DIR" plan -out=tf.plan "${TARGETS[@]}" | tee tf_plan_output.txt
fi

# ==========================
# Safety check for MySQL
# ==========================
if grep -q '^-.*docker_container\.mysql' tf_plan_output.txt; then
  echo "WARNING: This plan includes destruction of the MySQL container."
  echo "Aborting to prevent data loss."
  exit 1
fi

# ==========================
# Apply or Prompt
# ==========================
if $AUTO_APPROVE; then
  echo "Auto-approve enabled. Applying Terraform plan..."
  terraform -chdir="$SCRIPT_DIR" apply -auto-approve tf.plan
else
  echo -e "\nReview the plan and choose an option:"
  echo "  (a)pply - Apply the planned changes"
  echo "  (q)uit  - Exit without applying"
  
  read -p "Enter choice (a/q): " choice
  case "$choice" in
    a|A)
      echo "Applying Terraform plan..."
      terraform -chdir="$SCRIPT_DIR" apply tf.plan
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
