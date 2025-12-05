#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# prod_deploy.sh — Production deployment helper for the Quarkus-Admin stack
# ---------------------------------------------------------------------------

set -euo pipefail

# 1. Resolve script directory (follow symlinks) and cd into it
SOURCE="${BASH_SOURCE[0]}"
while [[ -h $SOURCE ]]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
SCRIPT_DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR" || { echo "Cannot cd to $SCRIPT_DIR"; exit 1; }

echo "Running in $SCRIPT_DIR"

# ==========================
# Toggle individual services
# ==========================
RESET_DB=false
RESET_REDIS=false
RESET_QUARKUS=true
RESET_MAILHOG=false
RESET_CERTBOT=false

echo "Running terraform init..."
terraform init -upgrade

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
if [[ "${1:-}" == "-auto-approve" ]]; then
  echo "Auto-approve enabled. Applying..."
  terraform apply -auto-approve tf.plan
else
  echo "Terraform plan saved to tf.plan"
  echo "To apply manually, run:"
  echo "terraform apply tf.plan"
fi