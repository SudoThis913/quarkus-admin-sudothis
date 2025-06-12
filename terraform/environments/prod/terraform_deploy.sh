#!/usr/bin/env bash
set -euo pipefail

# ==========================
# Toggle individual services
# ==========================
RESET_DB=false
RESET_REDIS=false
RESET_QUARKUS=true
RESET_MAILHOG=false
RESET_CERTBOT=false

# ==========================
# Terraform setup
# ==========================
cd "$(dirname "$0")"  # ensure we're in terraform/environments/prod

# Always init first
terraform init -upgrade

# Apply core networking & volumes
terraform apply -auto-approve -target=docker_network.quarkus_net

# Conditional applies
$RESET_DB     && terraform apply -auto-approve -target=module.mysql
$RESET_REDIS  && terraform apply -auto-approve -target=module.redis
$RESET_QUARKUS && terraform apply -auto-approve -target=module.quarkus_app
$RESET_CERTBOT && terraform apply -auto-approve -target=docker_container.certbot_sync_run
# Mailhog is not a suitable prod mail server
# $RESET_MAILHOG && terraform apply -auto-approve -target=module.mailhog

# Full sync (optional)
# terraform apply -auto-approve