#!/usr/bin/env bash
# Prints the URL a phone on the same Wi-Fi can use to reach the local gateway.
# Requires GATEWAY_BIND=0.0.0.0 in .env (the default only listens on localhost).
set -euo pipefail

ip=""
if command -v ipconfig >/dev/null 2>&1; then          # macOS
  ip="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
fi
if [[ -z "$ip" ]] && command -v hostname >/dev/null 2>&1; then  # Linux
  ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
fi
[[ -n "$ip" ]] || { echo "Could not detect a LAN IP address" >&2; exit 1; }

port="${GATEWAY_PORT:-8080}"
echo "LAN API URL:   http://$ip:$port"
echo "Expo:          EXPO_PUBLIC_API_BASE_URL=http://$ip:$port npx expo start"
if ! grep -q '^GATEWAY_BIND=0.0.0.0' .env 2>/dev/null; then
  echo "Note: set GATEWAY_BIND=0.0.0.0 in .env and run 'docker compose up -d gateway' so phones can connect."
fi
echo "Android emulator shortcut: http://10.0.2.2:$port   iOS simulator: http://localhost:$port"
