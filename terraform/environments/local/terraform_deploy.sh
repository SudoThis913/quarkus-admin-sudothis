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

# Always remove Redis container if it exists
if docker container inspect quarkus-redis &>/dev/null; then
  echo "Removing existing container: quarkus-redis"
  docker rm -f quarkus-redis
fi

echo "Cleaning up MySQL container and volume..."
docker rm -f quarkus-mysql 2>/dev/null || true
docker volume rm quarkus-mysql-data 2>/dev/null || true

if $FULL_RESET && docker volume inspect quarkus-mysql-data &>/dev/null; then
  echo "Removing MySQL data volume: quarkus-mysql-data"
  docker volume rm quarkus-mysql-data
  echo "Removing volume from Terraform state..."
  terraform state rm 'module.mysql.docker_volume.mysql_data' || true
fi

# Always remove Quarkus container if it exists
if docker container inspect quarkus-admin &>/dev/null; then
  echo "Removing existing container: quarkus-admin"
  docker rm -f quarkus-admin
fi

if docker container inspect mailhog &>/dev/null; then
  echo "Removing existing container: mailhog"
  docker rm -f mailhog
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

docker rm -f flyway-init 2>/dev/null || true


echo "Running Terraform deployment steps..."
terraform init
terraform plan -out=tf_plan
terraform show tf_plan > tf_plan_readable.txt

echo -e "\nTerraform plan saved. Review with:\n  cat tf_plan_readable.txt\nThen apply with:\n  terraform apply tf_plan"
