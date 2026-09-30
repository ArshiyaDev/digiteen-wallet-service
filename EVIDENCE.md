# Verification — 2026-09-30

Executed against the running local Docker services. Each API script creates fresh test users/wallets; their test data remains in the local databases.

| Check | Observed result |
| --- | --- |
| Wallet Maven `clean verify` | BUILD SUCCESS; 8 tests, 0 failures/errors/skips |
| User Maven `clean verify` | BUILD SUCCESS; 8 tests, 0 failures/errors/skips |
| Mid-transfer rollback | HTTP 422 / AMOUNT_OVERFLOW after source debit; sender remains 1000, receiver remains 9223372036854775807 |
| Rollback persistence | Zero surviving transaction, outbox, consumed-event or idempotency rows for the failed operation |
| Retry after rollback | Same request/key succeeds after making room; replay returns the same transaction; balances 900 / 9223372036854775807 |
| Event timing | Published in 69 ms; consumed in 72 ms; both below 2000 ms |
| Trace propagation | Transaction, publication and consumption have the same trace ID |
| Acceptance | Registration/login, BCrypt storage, unauthenticated 401, wallet isolation, insufficient funds and successful transfer all PASS |

Rollback run: `rollback-1790748923-26050`.
Trace run: `trace-evidence-1790748925-25084`.
Trace transaction: `5c92b4bb-7e68-4597-a950-074840bafb75`.
Trace event: `a44cf50e-a145-4c38-a591-b2ecd7605a09`.

The rollback scenario uses real balance overflow in `target.credit()` after `source.debit()`, through the HTTP API and Spring transaction. It does not inject a process crash or broker outage. Timing is a measured normal-operation sample, not a guarantee under every load; the script fails if a rerun exceeds the limit. Timing starts at the transaction's recorded occurrence time and ends at the consumer timestamp (a conservative interval that includes transaction work).

Run with both Compose stacks up:

```bash
./scripts/rollback-test.sh
./scripts/trace-test.sh
./scripts/acceptance-test.sh
mvn -B -ntp clean verify
```

Run `mvn -B -ntp clean verify` inside the user repository too. For this run, Maven 3.9.11 at `/private/tmp/apache-maven-3.9.11/bin/mvn` was used with `-Dmaven.repo.local=/private/tmp/digiteen-m2`. Surefire reports are under each repository's `target/surefire-reports/`.
