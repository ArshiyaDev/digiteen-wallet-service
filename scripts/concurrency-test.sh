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
echo 'PASS'
