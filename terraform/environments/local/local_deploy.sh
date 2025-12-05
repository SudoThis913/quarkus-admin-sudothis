#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# local_deploy.sh — Local deployment helper for the Quarkus-Admin stack
#
#   • Cleans up project-scoped Docker containers, volumes, network, image
#   • Optional --full-reset also purges volumes/images and scrubs tf-state
#   • Runs Terraform init / plan / show inside terraform/environments/local
#
# Usage:
#   ./local_deploy.sh            # normal run
#   ./local_deploy.sh --full-reset  # deep clean before plan
# ---------------------------------------------------------------------------

set -euo pipefail

##############################################################################
# 1. CLI flags
##############################################################################
FULL_RESET=false
for arg in "$@"; do
  [[ $arg == --full-reset ]] && FULL_RESET=true
done

##############################################################################
# 2. Colour + logging helper
##############################################################################
##############################################################################
# 2. Colour-coded logging helpers (6 colours)
##############################################################################
RED='\033[0;31m'
AMB='\033[0;33m'   # amber / yellow
GRN='\033[0;32m'
BLU='\033[0;34m'
CYN='\033[0;36m'
MAG='\033[0;35m'
NC='\033[0m'       # reset

# internal: colour-agnostic printer
_log() { local clr="$1"; shift; printf "${clr}%b${NC}\n" "$*"; }

# public helpers
red()     { _log "$RED" "$@"; }    # errors / fatal
amber()   { _log "$AMB" "$@"; }    # warnings
green()   { _log "$GRN" "$@"; }    # success
blue()    { _log "$BLU" "$@"; }    # headings
cyan()    { _log "$CYN" "$@"; }    # info (default)
magenta() { _log "$MAG" "$@"; }    # debug / misc

##############################################################################
# 3. Resolve script directory (follow symlinks) and cd into it
##############################################################################
SOURCE="${BASH_SOURCE[0]}"
while [[ -h $SOURCE ]]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
SCRIPT_DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR" || { red "Cannot cd to $SCRIPT_DIR"; exit 1; }
cyan "Source: $SOURCE"

##############################################################################
# 4. Abort fast if Docker daemon is unreachable
##############################################################################
if ! timeout 30s docker info >/dev/null 2>&1; then
  red "Docker daemon not reachable (waited 5 s). Start Docker and retry."
  exit 1
fi

##############################################################################
# 5. Docker cleanup (explicit names; safe for other projects)
##############################################################################
cyan "Cleaning up stale Docker containers…"

# Containers that are always removed
for c in local-quarkus-mysql local-quarkus-admin local-quarkus-redis local-mailhog local-quarkus-mysql-flyway; do
  if docker container inspect "$c" &>/dev/null; then
    cyan "Removing container: $c"
    docker rm -f "$c"
  fi
done

# Volumes
cyan "Cleaning up MySQL container and volume…"
docker rm -f local-quarkus-mysql  2>/dev/null || true
docker volume rm local-quarkus-mysql-data 2>/dev/null || true

if $FULL_RESET && docker volume inspect local-quarkus-mysql-data &>/dev/null; then
  cyan "Removing MySQL data volume and scrubbing TF state"
  docker volume rm local-quarkus-mysql-data
  terraform state rm 'module.mysql.docker_volume.mysql_data' || true
fi

# Network (only on full reset)
if $FULL_RESET && docker network inspect local_net &>/dev/null; then
  cyan "Removing network: local_net"
  docker network rm local_net
fi

# Image (only on full reset)
if $FULL_RESET && docker image inspect mysql:8 &>/dev/null; then
  cyan "Removing image: mysql:8"
  docker rmi -f mysql:8
fi

# Certbot tidy-up (container already removed above)
if docker volume inspect certbot_data &>/dev/null; then
  cyan "Removing Certbot data volume: certbot_data"
  docker volume rm certbot_data || true
  if $FULL_RESET; then
    cyan "Scrubbing Certbot volume from TF state"
    terraform state rm 'module.certbot.docker_volume.certbot_data' || true
  fi
fi

##############################################################################
# 6. Terraform workflow
##############################################################################
green "Running Terraform deployment steps…"
terraform -chdir="$SCRIPT_DIR" init
terraform -chdir="$SCRIPT_DIR" plan -out=tf_plan
terraform -chdir="$SCRIPT_DIR" show tf_plan > tf_plan_readable.txt

PLAN_FILE="$SCRIPT_DIR/tf_plan"
READABLE="$SCRIPT_DIR/tf_plan_readable.txt"

green "Terraform plan saved at:" 
cyan "$PLAN_FILE"
green "Review with:"
cyan "cat $READABLE"
green "Apply with:"
cyan "terraform -chdir=$SCRIPT_DIR apply $(basename "$PLAN_FILE")"
