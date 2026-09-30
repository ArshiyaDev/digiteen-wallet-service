#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib.sh"
wait_for_services

run_id="concurrency-$(date +%s)-$RANDOM"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

registration="$(register_user "${run_id}")"
token="$(printf '%s' "${registration}" | json_field accessToken)"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${run_id}-initial-deposit" -d '{"amount":100000}' \
  "${WALLET_URL}/api/v1/wallet/deposits" >/dev/null

pids=()
for i in $(seq 1 50); do
  curl --silent --show-error -o "${work_dir}/body-${i}.json" -w '%{http_code}' \
    -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${run_id}-withdraw-${i}" -d '{"amount":3000}' \
    "${WALLET_URL}/api/v1/wallet/withdrawals" >"${work_dir}/status-${i}.txt" &
  pids+=("$!")
done

transport_failed=0
for pid in "${pids[@]}"; do
  if ! wait "${pid}"; then
    transport_failed=1
  fi
done

success=0
rejected=0
unexpected=0
for status_file in "${work_dir}"/status-*.txt; do
  case "$(<"${status_file}")" in
    200) success=$((success + 1)) ;;
    422) rejected=$((rejected + 1)) ;;
    *) unexpected=$((unexpected + 1)) ;;
  esac
done
wallet="$(wallet_get "${token}")"
balance="$(printf '%s' "${wallet}" | json_field balance)"

printf 'Concurrency evidence\n  successful withdrawals: %s\n  insufficient-funds rejections: %s\n  unexpected responses: %s\n  final balance: %s\n' \
  "${success}" "${rejected}" "${unexpected}" "${balance}"

if [[ "${transport_failed}" != 0 || "${unexpected}" != 0 ||
      "${success}" != 33 || "${rejected}" != 17 || "${balance}" != 1000 ]]; then
  echo 'HTTP status counts:' >&2
  sort "${work_dir}"/status-*.txt | uniq -c >&2
  for status_file in "${work_dir}"/status-*.txt; do
    status="$(<"${status_file}")"
    if [[ "${status}" != 200 && "${status}" != 422 ]]; then
      number="${status_file##*-}"
      number="${number%.txt}"
      echo "First unexpected response (${status}):" >&2
      cat "${work_dir}/body-${number}.json" >&2
      echo >&2
      break
    fi
  done
  echo 'FAIL: expected exactly 33 successful, 17 rejected, and balance 1000' >&2
  exit 1
fi

# Read persisted transactions through the history API, independently of POST responses.
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${token}" \
  "${WALLET_URL}/api/v1/wallet/transactions?size=100" >"${work_dir}/history.json"
python3 - "${work_dir}/history.json" "${balance}" <<'PY'
import json
import sys

with open(sys.argv[1]) as history_file:
    history = json.load(history_file)
rows = history["content"]
balance = int(sys.argv[2])
deposits = [r for r in rows if r["type"] == "DEPOSIT" and r["status"] == "SUCCEEDED"]
withdrawals = [r for r in rows if r["type"] == "WITHDRAWAL" and r["status"] == "SUCCEEDED"]
rejected = [r for r in rows if r["type"] == "WITHDRAWAL" and r["status"] == "REJECTED"]
deposited = sum(r["amount"] for r in deposits)
withdrawn = sum(r["amount"] for r in withdrawals)
print(f"  persisted transactions: {len(rows)}")
print(f"  persisted successful withdrawals: {len(withdrawals)}, total: {withdrawn}")
print(f"  persisted insufficient-funds rejections: {len(rejected)}")
print(f"  ledger reconciliation: {deposited} - {withdrawn} = {balance}")
valid = (
    history["totalElements"] == len(rows) == 51
    and len({r["transactionId"] for r in rows}) == 51
    and len(deposits) == 1 and deposited == 100000
    and len(withdrawals) == 33 and withdrawn == 99000
    and len(rejected) == 17
    and all(r["amount"] == 3000 for r in withdrawals + rejected)
    and all(r["failureCode"] == "INSUFFICIENT_FUNDS" for r in rejected)
    and deposited - withdrawn == balance == 1000
)
if not valid:
    sys.exit("FAIL: persisted transaction totals/counts do not match the expected balance change")
PY
echo 'PASS'
