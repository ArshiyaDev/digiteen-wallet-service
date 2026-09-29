#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib.sh"
wait_for_services

run_id="idempotency-$(date +%s)-$RANDOM"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

sender_json="$(register_user "${run_id}-sender")"
receiver_json="$(register_user "${run_id}-receiver")"
sender_token="$(printf '%s' "${sender_json}" | json_field accessToken)"
receiver_token="$(printf '%s' "${receiver_json}" | json_field accessToken)"
receiver_wallet="$(wallet_get "${receiver_token}")"
target_wallet_id="$(printf '%s' "${receiver_wallet}" | json_field walletId)"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${sender_token}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${run_id}-deposit" -d '{"amount":10000}' \
  "${WALLET_URL}/api/v1/wallet/deposits" >/dev/null

pids=()
for i in $(seq 1 5); do
  curl --fail --silent --show-error \
    -H "Authorization: Bearer ${sender_token}" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${run_id}-same-transfer" \
    -d "{\"targetWalletId\":\"${target_wallet_id}\",\"amount\":1000}" \
    "${WALLET_URL}/api/v1/wallet/transfers" >"${work_dir}/response-${i}.json" &
  pids+=("$!")
done

request_failed=0
for pid in "${pids[@]}"; do
  if ! wait "${pid}"; then
    request_failed=1
  fi
done

if [[ "${request_failed}" != 0 ]]; then
  echo 'FAIL: at least one concurrent transfer request failed' >&2
  exit 1
fi

transaction_count="$(python3 - "${work_dir}" <<'PY'
import glob,json,sys
ids={json.load(open(path))["transactionId"] for path in glob.glob(sys.argv[1]+"/response-*.json")}
print(len(ids))
PY
)"
sender_balance="$(wallet_get "${sender_token}" | json_field balance)"
receiver_balance="$(wallet_get "${receiver_token}" | json_field balance)"

printf 'Idempotency evidence\n  requests: 5\n  unique transaction IDs: %s\n  sender balance: %s\n  receiver balance: %s\n' \
  "${transaction_count}" "${sender_balance}" "${receiver_balance}"

if [[ "${transaction_count}" != 1 || "${sender_balance}" != 9000 || "${receiver_balance}" != 1000 ]]; then
  echo 'FAIL: the transfer was not applied exactly once' >&2
  exit 1
fi
echo 'PASS'
