#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib.sh"
wait_for_services

run_id="event-recovery-$(date +%s)-$RANDOM"
registration="$(register_user "${run_id}")"
token="$(printf '%s' "${registration}" | json_field accessToken)"
consumer_started=false
cleanup() {
  if [[ "${consumer_started}" == false ]]; then
    docker compose -f "${REPO_DIR}/docker-compose.yml" start event-consumer >/dev/null
  fi
}
trap cleanup EXIT

docker compose -f "${REPO_DIR}/docker-compose.yml" stop event-consumer >/dev/null
for i in 1 2 3; do
  curl --fail --silent --show-error \
    -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${run_id}-${i}" -H "X-Correlation-ID: ${run_id}" \
    -d '{"amount":100}' "${WALLET_URL}/api/v1/wallet/deposits" >/dev/null
done

echo 'Consumer is stopped; waiting the required 30 seconds...'
sleep 30
docker compose -f "${REPO_DIR}/docker-compose.yml" start event-consumer >/dev/null
consumer_started=true

count=0
for _ in $(seq 1 30); do
  count="$(docker compose -f "${REPO_DIR}/docker-compose.yml" exec -T postgres \
    sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$1"' sh \
    "SELECT count(*) FROM consumed_events WHERE trace_id='${run_id}'")"
  [[ "${count}" == 3 ]] && break
  sleep 1
done

printf 'Event recovery evidence\n  events produced while consumer stopped: 3\n  events consumed after restart: %s\n' "${count}"
if [[ "${count}" != 3 ]]; then
  echo 'FAIL: not every event was eventually processed' >&2
  exit 1
fi
echo 'PASS (consumed_events.event_id primary key prevents duplicate effects)'
