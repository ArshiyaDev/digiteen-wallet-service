#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
USER_REPO_DIR="${USER_REPO_DIR:-${REPO_DIR}/../digiteen-user-service}"
source "${SCRIPT_DIR}/lib.sh"
wait_for_services

run_id="acceptance-$(date +%s)-$RANDOM"
owner_email="${run_id}-owner@example.test"
other_email="${run_id}-other@example.test"

register_user "${run_id}-owner" >/dev/null
other_registration="$(register_user "${run_id}-other")"
owner_login="$(login_user "${owner_email}")"
owner_token="$(printf '%s' "${owner_login}" | json_field accessToken)"
other_token="$(printf '%s' "${other_registration}" | json_field accessToken)"

unauthorized_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  "${WALLET_URL}/api/v1/wallet")"
owner_wallet="$(wallet_get "${owner_token}")"
other_wallet="$(wallet_get "${other_token}")"
owner_wallet_id="$(printf '%s' "${owner_wallet}" | json_field walletId)"
other_wallet_id="$(printf '%s' "${other_wallet}" | json_field walletId)"

attempted_switch="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${other_token}" \
  "${WALLET_URL}/api/v1/wallet?walletId=${owner_wallet_id}")"
attempted_wallet_id="$(printf '%s' "${attempted_switch}" | json_field walletId)"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${owner_token}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${run_id}-deposit" -d '{"amount":500}' \
  "${WALLET_URL}/api/v1/wallet/deposits" >/dev/null

other_history="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${other_token}" \
  "${WALLET_URL}/api/v1/wallet/transactions")"
other_history_count="$(printf '%s' "${other_history}" | json_field totalElements)"

failed_transfer_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  -H "Authorization: Bearer ${owner_token}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${run_id}-rejected-transfer" \
  -d "{\"targetWalletId\":\"${other_wallet_id}\",\"amount\":1000}" \
  "${WALLET_URL}/api/v1/wallet/transfers")"
owner_after_failure="$(wallet_get "${owner_token}" | json_field balance)"
other_after_failure="$(wallet_get "${other_token}" | json_field balance)"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${owner_token}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${run_id}-successful-transfer" \
  -d "{\"targetWalletId\":\"${other_wallet_id}\",\"amount\":200}" \
  "${WALLET_URL}/api/v1/wallet/transfers" >/dev/null
owner_final="$(wallet_get "${owner_token}" | json_field balance)"
other_final="$(wallet_get "${other_token}" | json_field balance)"

password_hash="$(docker compose -f "${USER_REPO_DIR}/docker-compose.yml" exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$1"' sh \
  "SELECT password_hash FROM users WHERE email='${owner_email}'")"

printf 'Acceptance evidence\n'
printf '  registration and login: PASS\n'
printf '  stored password is BCrypt: %s\n' "$([[ "${password_hash}" == \$2* && "${password_hash}" != "${EVIDENCE_PASSWORD}" ]] && echo PASS || echo FAIL)"
printf '  unauthenticated wallet status: %s\n' "${unauthorized_status}"
printf '  owner wallet isolation: %s\n' "$([[ "${owner_wallet_id}" != "${other_wallet_id}" && "${attempted_wallet_id}" == "${other_wallet_id}" && "${other_history_count}" == 0 ]] && echo PASS || echo FAIL)"
printf '  rejected transfer balances: owner=%s other=%s\n' "${owner_after_failure}" "${other_after_failure}"
printf '  successful atomic transfer balances: owner=%s other=%s\n' "${owner_final}" "${other_final}"

if [[ "${password_hash}" != \$2* || "${password_hash}" == "${EVIDENCE_PASSWORD}" ||
      "${unauthorized_status}" != 401 || "${owner_wallet_id}" == "${other_wallet_id}" ||
      "${attempted_wallet_id}" != "${other_wallet_id}" || "${other_history_count}" != 0 ||
      "${failed_transfer_status}" != 422 || "${owner_after_failure}" != 500 ||
      "${other_after_failure}" != 0 || "${owner_final}" != 300 || "${other_final}" != 200 ]]; then
  echo 'FAIL: one or more authentication, ownership, or atomic-transfer checks failed' >&2
  exit 1
fi
echo 'PASS'
