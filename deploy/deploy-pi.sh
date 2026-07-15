#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy-pi.sh — build → test → ship → run pipeline for the Raspberry Pi.
#
# Run from WSL (needs: java 17+, docker with buildx, ssh alias for the pi).
#   ./deploy-pi.sh [--skip-tests] [--skip-build] [--app-only]
#
# Pipeline gates: tests must pass before an image is built; the deploy fails
# loudly if the app never reaches /q/health/ready. The compose project is
# fully isolated (own name/network/volumes) from anything else on the pi.
# ---------------------------------------------------------------------------
set -euo pipefail

PI_SSH="${PI_SSH:-pi}"
REMOTE_DIR="${REMOTE_DIR:-/home/greg/sudothis}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SKIP_TESTS=false
SKIP_BUILD=false
SKIP_MVN=false
APP_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=true ;;
    --skip-build) SKIP_BUILD=true ;;
    # jar already built (e.g. on the Windows side where the .m2 cache lives);
    # still builds and ships the docker image
    --skip-mvn)   SKIP_MVN=true ;;
    --app-only)   APP_ONLY=true ;;
    *) echo "Unknown flag: $arg" >&2; exit 2 ;;
  esac
done

PI_HOST="$(ssh -G "$PI_SSH" | awk '/^hostname /{print $2}')"
[ -n "$PI_HOST" ] || { echo "Cannot resolve hostname for ssh alias '$PI_SSH'" >&2; exit 1; }
echo "==> Deploy target: $PI_SSH ($PI_HOST), remote dir $REMOTE_DIR"

TAG="prod-$(date +%Y%m%d%H%M%S)"
IMAGE="quarkus-admin:$TAG"

cd "$PROJECT_ROOT"

if ! $SKIP_BUILD; then
  if $SKIP_MVN; then
    [ -d target/quarkus-app ] || { echo "--skip-mvn but target/quarkus-app missing" >&2; exit 1; }
    echo "==> Using existing fast-jar build"
  else
    if ! $SKIP_TESTS; then
      echo "==> Running test suite (gate)"
      ./mvnw -q test
    fi
    echo "==> Packaging (fast-jar)"
    ./mvnw -q package -DskipTests
  fi
  echo "==> Building arm64 image $IMAGE"
  docker buildx build --platform linux/arm64 \
    -f src/main/docker/Dockerfile.jvm \
    -t "$IMAGE" -t quarkus-admin:prod --load .
else
  IMAGE="quarkus-admin:prod"
  TAG="prod"
  echo "==> Skipping build; deploying existing $IMAGE"
fi

echo "==> Generating pi realm import (redirect URIs + LAN URLs)"
python3 - "$SCRIPT_DIR/../src/main/resources/keycloak-realm.json" \
          "$SCRIPT_DIR/keycloak-realm-pi.json" "$PI_HOST" <<'PYEOF'
import json, sys
src, dst, host = sys.argv[1], sys.argv[2], sys.argv[3]
realm = json.load(open(src))
# LAN deployment is plain http — require no TLS inside Keycloak
realm["sslRequired"] = "none"
for client in realm.get("clients", []):
    if client.get("clientId") == "sudothis-app":
        uris = set(client.get("redirectUris", []))
        uris.add(f"http://{host}:8081/*")
        client["redirectUris"] = sorted(uris)
        client["webOrigins"] = [f"http://{host}:8081"]
json.dump(realm, open(dst, "w"), indent=2)
print(f"    wrote {dst}")
PYEOF

echo "==> Preparing remote directory and secrets"
ssh "$PI_SSH" "mkdir -p '$REMOTE_DIR'"
# Generate .env once; never overwrite (it holds the DB/KC credentials in use)
ssh "$PI_SSH" "test -f '$REMOTE_DIR/.env'" || ssh "$PI_SSH" "
  umask 077
  cat > '$REMOTE_DIR/.env' <<EOF
PI_HOST=$PI_HOST
DB_USER=sudothis
DB_PASSWORD=\$(openssl rand -hex 16)
DB_ROOT_PASSWORD=\$(openssl rand -hex 16)
KC_ADMIN_USER=admin
KC_ADMIN_PASSWORD=\$(openssl rand -hex 12)
APP_IMAGE=quarkus-admin:prod
EOF
  echo '    generated new $REMOTE_DIR/.env (chmod 600)'
"

echo "==> Copying compose + realm"
scp -q "$SCRIPT_DIR/docker-compose.pi.yml" "$PI_SSH:$REMOTE_DIR/docker-compose.yml"
scp -q "$SCRIPT_DIR/keycloak-realm-pi.json" "$PI_SSH:$REMOTE_DIR/keycloak-realm-pi.json"

echo "==> Shipping image to pi (docker save | ssh | docker load)"
docker save "$IMAGE" | gzip | ssh "$PI_SSH" "gunzip | docker load"

echo "==> Pinning APP_IMAGE=$IMAGE in remote .env"
ssh "$PI_SSH" "sed -i 's|^APP_IMAGE=.*|APP_IMAGE=$IMAGE|' '$REMOTE_DIR/.env'"

echo "==> Starting stack"
if $APP_ONLY; then
  ssh "$PI_SSH" "cd '$REMOTE_DIR' && docker compose up -d app"
else
  ssh "$PI_SSH" "cd '$REMOTE_DIR' && docker compose up -d --remove-orphans"
fi

echo "==> Waiting for app health (up to 10 min — first Keycloak boot is slow on the pi)"
for i in $(seq 1 60); do
  if ssh "$PI_SSH" "curl -fsS -m 5 http://127.0.0.1:8081/q/health/ready" >/dev/null 2>&1; then
    echo "==> HEALTHY after ~$((i*10))s"
    ssh "$PI_SSH" "docker ps --filter name=sudothis- --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'; free -h | head -2"
    echo "==> Deployed $IMAGE — app: http://$PI_HOST:8081  keycloak: http://$PI_HOST:8180"
    exit 0
  fi
  sleep 10
done

echo "!! App did not become healthy. Container states:" >&2
ssh "$PI_SSH" "docker ps -a --filter name=sudothis- --format 'table {{.Names}}\t{{.Status}}'; docker logs --tail 40 sudothis-admin 2>&1 | tail -40" >&2 || true
exit 1
