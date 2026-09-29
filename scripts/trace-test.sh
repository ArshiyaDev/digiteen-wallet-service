#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib.sh"
wait_for_services

running_services="$(docker compose -f "${REPO_DIR}/docker-compose.yml" ps --services --status running)"
if ! grep -qx 'wallet-service' <<<"${running_services}" || \
   ! grep -qx 'event-consumer' <<<"${running_services}"; then
  echo 'FAIL: trace evidence requires wallet-service and event-consumer to run in Docker Compose.' >&2
  echo 'Run: docker compose up -d --build' >&2
  exit 1
fi

run_id="trace-evidence-$(date +%s)-$RANDOM"
sender_json="$(register_user "${run_id}-sender")"
receiver_json="$(register_user "${run_id}-receiver")"
sender_token="$(printf '%s' "${sender_json}" | json_field accessToken)"
receiver_token="$(printf '%s' "${receiver_json}" | json_field accessToken)"
target_wallet_id="$(wallet_get "${receiver_token}" | json_field walletId)"

curl --fail --silent --show-error -H "Authorization: Bearer ${sender_token}" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: ${run_id}-deposit" \
  -d '{"amount":1000}' "${WALLET_URL}/api/v1/wallet/deposits" >/dev/null
curl --fail --silent --show-error -H "Authorization: Bearer ${sender_token}" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: ${run_id}-transfer" \
  -H "X-Correlation-ID: ${run_id}" \
  -d "{\"targetWalletId\":\"${target_wallet_id}\",\"amount\":100}" \
  "${WALLET_URL}/api/v1/wallet/transfers" >/dev/null
echo "Trace evidence for ${run_id}:"
matches=''
for _ in $(seq 1 10); do
  matches="$(docker compose -f "${REPO_DIR}/docker-compose.yml" logs --no-color wallet-service event-consumer | grep "${run_id}" || true)"
  if grep -q 'wallet_transaction_completed' <<<"${matches}" && \
     grep -q 'outbox_event_published' <<<"${matches}" && \
     grep -q 'wallet_event_consumed' <<<"${matches}"; then
    break
  fi
  sleep 1
done
printf '%s\n' "${matches}"
if ! grep -q 'wallet_transaction_completed' <<<"${matches}" || \
   ! grep -q 'outbox_event_published' <<<"${matches}" || \
   ! grep -q 'wallet_event_consumed' <<<"${matches}"; then
  echo 'FAIL: trace did not cover transaction, publication, and consumption' >&2
  exit 1
fi
echo 'PASS'
