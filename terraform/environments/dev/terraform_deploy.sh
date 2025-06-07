#!/bin/bash
set -euo pipefail

FULL_RESET=false

# Parse arguments
for arg in "$@"; do
  if [[ "$arg" == "--full-reset" ]]; then
    FULL_RESET=true
  fi
done

echo "Cleaning up stale Docker containers..."

# Always remove MySQL container if it exists
if docker container inspect quarkus-mysql &>/dev/null; then
  echo "Removing existing container: quarkus-mysql"
  docker rm -f quarkus-mysql
fi

# Always remove Quarkus container if it exists
if docker container inspect quarkus-admin &>/dev/null; then
  echo "Removing existing container: quarkus-admin"
  docker rm -f quarkus-admin
fi

# Conditionally remove network
if $FULL_RESET && docker network inspect quarkus_net &>/dev/null; then
  echo "Removing existing network: quarkus_net"
  docker network rm quarkus_net
fi

# Conditionally remove MySQL image
if $FULL_RESET && docker image inspect mysql:8 &>/dev/null; then
  echo "Removing existing image: mysql:8"
  docker rmi -f mysql:8
fi

echo "Running Terraform deployment steps..."
terraform init
terraform plan -out=tf_plan
terraform show tf_plan > tf_plan_readable.txt

echo -e "\nTerraform plan saved. Review with:\n  cat tf_plan_readable.txt\nThen apply with:\n  terraform apply tf_plan"
