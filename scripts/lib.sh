#!/usr/bin/env bash
set -euo pipefail

USER_URL="${USER_URL:-http://localhost:8080}"
WALLET_URL="${WALLET_URL:-http://localhost:8081}"
EVIDENCE_PASSWORD="${EVIDENCE_PASSWORD:-StrongPassword123!}"

json_field() {
  python3 -c 'import json,sys; value=json.load(sys.stdin)[sys.argv[1]]; print(value)' "$1"
}

register_user() {
  local suffix="$1"
  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"Evidence ${suffix}\",\"email\":\"${suffix}@example.test\",\"password\":\"${EVIDENCE_PASSWORD}\"}" \
    "${USER_URL}/api/v1/auth/register"
}

login_user() {
  local email="$1"
  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"identifier\":\"${email}\",\"password\":\"${EVIDENCE_PASSWORD}\"}" \
    "${USER_URL}/api/v1/auth/login"
}

wallet_get() {
  local token="$1"
  curl --fail --silent --show-error -H "Authorization: Bearer ${token}" "${WALLET_URL}/api/v1/wallet"
}

wait_for_services() {
  curl --fail --silent --retry 40 --retry-delay 2 --retry-all-errors \
    "${USER_URL}/actuator/health" >/dev/null
  curl --fail --silent --retry 40 --retry-delay 2 --retry-all-errors \
    "${WALLET_URL}/actuator/health" >/dev/null
}
