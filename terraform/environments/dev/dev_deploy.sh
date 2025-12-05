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
for arg in "$@"; do
  [[ $arg == --full-reset ]] && FULL_RESET=true
done

echo "Cleaning up stale Docker containers..."

# Cleanup logic (simplified for dev)
for c in quarkus-mysql quarkus-admin; do
  if docker container inspect "$c" &>/dev/null; then
    echo "Removing existing container: $c"
    docker rm -f "$c"
  fi
done

if $FULL_RESET && docker network inspect quarkus_net &>/dev/null; then
  echo "Removing existing network: quarkus_net"
  docker network rm quarkus_net
fi

if $FULL_RESET && docker image inspect mysql:8 &>/dev/null; then
  echo "Removing existing image: mysql:8"
  docker rmi -f mysql:8
fi

# 3. Terraform workflow
echo "Running Terraform deployment steps..."
terraform init
terraform plan -out=tf_plan
terraform show tf_plan > tf_plan_readable.txt

echo -e "\nTerraform plan saved. Review with:\n  cat tf_plan_readable.txt\nThen apply with:\n  terraform apply tf_plan"