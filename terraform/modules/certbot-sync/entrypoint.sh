#!/bin/sh
# entrypoint.sh - Smart certbot for local or Let's Encrypt deployment

set -euo pipefail

MODE=${MODE:-auto}        # dev | prod | auto
DOMAIN=${DOMAIN:-example.com}
EMAIL=${EMAIL:-admin@example.com}
DNS_PROVIDER=${DNS_PROVIDER:-cloudflare}
CREDENTIALS_FILE="/secrets/cloudflare.ini"
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
TARGET_PATH="/mnt/certsync/$DOMAIN"
HOST_LIST="/mnt/hosts.txt"
RSYNC_OPTS="-az --delete"

log() {
  echo "[$(date +%Y-%m-%dT%H:%M:%S)] $1"
}

check_custom_cert() {
  [[ -f "$CERT_PATH/fullchain.pem" && -f "$CERT_PATH/privkey.pem" ]] && return 0 || return 1
}

should_renew() {
  # renew if cert expires in <= 30 days (2592000s)
  openssl x509 -in "$CERT_PATH/fullchain.pem" -noout -checkend 2592000 > /dev/null 2>&1 || return 0
  return 1
}

log "Starting cert manager in mode: $MODE"

if [[ "$MODE" == "dev" ]]; then
  log "Using existing self-signed dev certificate for $DOMAIN."
elif [[ "$MODE" == "prod" || "$MODE" == "auto" ]]; then
  if check_custom_cert; then
    if should_renew; then
      log "Certificate exists and needs renewal. Running certbot."
    else
      log "Certificate is valid. Skipping renewal."
      exit 0
    fi
  else
    log "No certificate found. Running certbot for $DOMAIN."
  fi

  certbot certonly \
    --dns-${DNS_PROVIDER} \
    --dns-${DNS_PROVIDER}-credentials ${CREDENTIALS_FILE} \
    -d "${DOMAIN}" -d "*.${DOMAIN}" \
    --email "${EMAIL}" \
    --agree-tos \
    --non-interactive \
    --keep-until-expiring
else
  log "Invalid MODE specified: $MODE. Must be one of dev, prod, auto."
  exit 1
fi

log "Distributing certificates to target hosts."

if [[ ! -f "$HOST_LIST" ]]; then
  log "Host list $HOST_LIST not found. Aborting sync."
  exit 1
fi

for HOST in $(cat "$HOST_LIST"); do
  log "Syncing to $HOST..."
  rsync $RSYNC_OPTS "$CERT_PATH/" "$HOST:$TARGET_PATH/" && \
    ssh "$HOST" "systemctl reload nginx || true"
  log "Sync to $HOST complete."
  sleep 0.5
done

log "All done."
exit 0
