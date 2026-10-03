#!/usr/bin/env bash
# Builds and starts Kiwi Finance inside a dev container or GitHub Codespace.
#   bash .devcontainer/start.sh build   build the images (runs once, when the codespace is created)
#   bash .devcontainer/start.sh up      start the database, API and web app, and wait until they answer
set -euo pipefail
cd "$(dirname "$0")/.."

# In a codespace the app is reached through forwarded URLs, so the Akahu sandbox sign-in must
# send the browser to those rather than to localhost.
if [[ -n "${CODESPACE_NAME:-}" ]]; then
  domain="${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
  export KIWI_WEB_URL="https://${CODESPACE_NAME}-3000.${domain}"
  export KIWI_API_PUBLIC_URL="https://${CODESPACE_NAME}-8080.${domain}"
fi

case "${1:-up}" in
  build)
    docker compose build
    ;;
  up)
    docker compose up -d --build
    echo "Waiting for Kiwi Finance to start..."
    for _ in $(seq 1 120); do
      if curl -fsS -o /dev/null http://localhost:3000/sign-in; then
        echo "Kiwi Finance is running at ${KIWI_WEB_URL:-http://localhost:3000}"
        echo "Sign in as demo@kiwifinance.nz with password kiwi-demo-2026, or create your own account."
        exit 0
      fi
      sleep 5
    done
    echo "Kiwi Finance did not start in time. See: docker compose logs api web" >&2
    exit 1
    ;;
  *)
    echo "Usage: $0 build|up" >&2
    exit 2
    ;;
esac
