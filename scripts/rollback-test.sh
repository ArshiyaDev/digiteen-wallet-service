#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib.sh"
wait_for_services

# Use fresh wallets only. Overflow occurs in target.credit(), after source.debit().
run_id="rollback-$(date +%s)-$RANDOM"
sender_token="$(register_user "${run_id}-sender" | json_field accessToken)"
receiver_token="$(register_user "${run_id}-receiver" | json_field accessToken)"
target_id="$(wallet_get "${receiver_token}" | json_field walletId)"
maximum=9223372036854775807

post() {
  curl --fail --silent --show-error -H "Authorization: Bearer $1" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $2" \
    -d "$3" "${WALLET_URL}/api/v1/wallet/$4"
}
sql() {
  docker compose -f "${REPO_DIR}/docker-compose.yml" exec -T postgres \
    sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "$1"' sh "$1"
}
post "${sender_token}" "${run_id}-fund-sender" '{"amount":1000}' deposits >/dev/null
post "${receiver_token}" "${run_id}-fund-receiver" "{\"amount\":${maximum}}" deposits >/dev/null

response_file="$(mktemp)"
trap 'rm -f "${response_file}"' EXIT
request="{\"targetWalletId\":\"${target_id}\",\"amount\":100}"
http_status="$(curl --silent --show-error --output "${response_file}" --write-out '%{http_code}' \
  -H "Authorization: Bearer ${sender_token}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${run_id}-transfer" -H "X-Correlation-ID: ${run_id}" \
  -d "${request}" "${WALLET_URL}/api/v1/wallet/transfers")"
error_code="$(json_field code < "${response_file}")"
sender_after="$(wallet_get "${sender_token}" | json_field balance)"
receiver_after="$(wallet_get "${receiver_token}" | json_field balance)"
survivors="$(sql "SELECT
  (SELECT count(*) FROM wallet_transactions WHERE trace_id='${run_id}') +
  (SELECT count(*) FROM outbox_events WHERE trace_id='${run_id}') +
  (SELECT count(*) FROM consumed_events WHERE trace_id='${run_id}') +
  (SELECT count(*) FROM idempotency_records WHERE idempotency_key='${run_id}-transfer');")"
printf 'Rollback evidence (%s)\n  failure: HTTP %s / %s\n  balances: sender=%s receiver=%s\n  surviving transaction/event/idempotency records: %s\n' \
  "${run_id}" "${http_status}" "${error_code}" "${sender_after}" "${receiver_after}" "${survivors}"
if [[ "${http_status}" != 422 || "${error_code}" != AMOUNT_OVERFLOW ||
      "${sender_after}" != 1000 || "${receiver_after}" != "${maximum}" || "${survivors}" != 0 ]]; then
  echo 'FAIL: mid-transfer failure did not roll back completely' >&2
  exit 1
fi

# Make room, then retry exactly the same request/key and replay it once more.
post "${receiver_token}" "${run_id}-make-room" '{"amount":100}' withdrawals >/dev/null
transaction_id="$(post "${sender_token}" "${run_id}-transfer" "${request}" transfers | json_field transactionId)"
replay_id="$(post "${sender_token}" "${run_id}-transfer" "${request}" transfers | json_field transactionId)"
sender_final="$(wallet_get "${sender_token}" | json_field balance)"
receiver_final="$(wallet_get "${receiver_token}" | json_field balance)"
if [[ "${transaction_id}" != "${replay_id}" || "${sender_final}" != 900 || "${receiver_final}" != "${maximum}" ]]; then
  echo 'FAIL: retry/replay after rollback did not produce exactly one transfer' >&2
  exit 1
fi
printf '  retry and replay: one transaction, sender=%s receiver=%s\nPASS\n' "${sender_final}" "${receiver_final}"
