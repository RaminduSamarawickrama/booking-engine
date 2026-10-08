#!/usr/bin/env bash
# Exposes ONLY the API gateway through a free Cloudflare quick tunnel and prints
# ready-to-use links for the web apps and the mobile apps.
#
#   ./scripts/tunnel.sh          # gateway running in Docker (compose "apps" profile)
#   ./scripts/tunnel.sh --ide    # gateway running in your IDE on localhost:8080
#   ./scripts/tunnel.sh --stop
#
# Quick tunnels need no Cloudflare account or domain and cost nothing. The URL is
# random and changes every time the tunnel restarts; that is why the apps let you
# switch backend at runtime instead of baking the URL into a build.
# PostgreSQL, Redis and RabbitMQ are never reachable through the tunnel.
set -euo pipefail
cd "$(dirname "$0")/.."

CUSTOMER_WEB_URL="${CUSTOMER_WEB_URL:-https://booking-customer-web.vercel.app}"
ADMIN_WEB_URL="${ADMIN_WEB_URL:-https://booking-admin-web.vercel.app}"

case "${1:-}" in
  --stop)
    docker compose --profile tunnel stop tunnel
    exit 0
    ;;
  --ide)
    export TUNNEL_TARGET="http://host.docker.internal:${GATEWAY_PORT:-8080}"
    ;;
  "") ;;
  *) echo "usage: $0 [--ide|--stop]" >&2; exit 1 ;;
esac

echo "Starting Cloudflare quick tunnel -> ${TUNNEL_TARGET:-http://gateway:8080}"
docker compose --profile tunnel up -d --force-recreate tunnel >/dev/null

url=""
for _ in $(seq 1 30); do
  url="$(docker compose --profile tunnel logs tunnel 2>/dev/null \
    | grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1 || true)"
  [[ -n "$url" ]] && break
  sleep 1
done
if [[ -z "$url" ]]; then
  echo "No tunnel URL after 30s. Check: docker compose --profile tunnel logs tunnel" >&2
  exit 1
fi

encoded="$(printf '%s' "$url" | sed 's/:/%3A/g; s#/#%2F#g')"
cat <<EOF

  Tunnel is up (changes on every restart):

    API             $url
    Health          $url/health
    Live updates    ${url/https/wss}/v1/live

  Web apps (open, then confirm the backend at the top of the page):
    Customer        $CUSTOMER_WEB_URL/?api=$encoded
    Admin           $ADMIN_WEB_URL/?api=$encoded

  Mobile apps (Expo):
    EXPO_PUBLIC_API_BASE_URL=$url npx expo start
    or paste the API URL into the app's Settings > Backend screen.

  Stripe test webhooks (only if you use PAYMENT_PROVIDER=stripe):
    Endpoint        $url/v1/payments/webhooks/stripe

  Stop with: $0 --stop
EOF
