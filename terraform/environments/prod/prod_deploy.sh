#!/bin/bash
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

echo "Running terraform init..."
terraform init -upgrade

# ==========================
# Build target list
# ==========================
declare -a TARGETS=()
$RESET_DB      && TARGETS+=("-target=module.mysql")
$RESET_REDIS   && TARGETS+=("-target=module.redis")
$RESET_QUARKUS && TARGETS+=("-target=module.quarkus_app")
$RESET_CERTBOT && TARGETS+=("-target=docker_container.certbot_sync_run")
# Mailhog intentionally excluded from prod
# $RESET_MAILHOG && TARGETS+=("-target=module.mailhog")

# If no individual flags are set, do full plan
if [[ ${#TARGETS[@]} -eq 0 ]]; then
  echo "No specific targets selected. Doing full terraform plan."
  terraform plan -out=tf.plan | tee tf_plan_output.txt
else
  echo "Planning with targets: ${TARGETS[*]}"
  terraform plan -out=tf.plan "${TARGETS[@]}" | tee tf_plan_output.txt
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
if [[ "$1" == "-auto-approve" ]]; then
  echo "Auto-approve enabled. Applying..."
  terraform apply -auto-approve tf.plan
else
  echo "Terraform plan saved to tf.plan"
  echo "To apply manually, run:"
  echo "terraform apply tf.plan"
fi
