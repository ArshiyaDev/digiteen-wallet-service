# Digiteen Wallet Service

Requires Docker Compose. **User Service must start first** for authentication.
Keep both repositories in the same parent folder. From this wallet repository,
start both services and their dependencies with one command:

```bash
docker compose -f ../digiteen-user-service/docker-compose.yml up -d --build --wait && docker compose up -d --build --wait
```

User Swagger: http://localhost:8080/swagger-ui.html

Wallet Swagger: http://localhost:8081/swagger-ui.html

Register/login in User Swagger, copy `accessToken`, then paste it into Wallet Swagger → **Authorize**.
Unit tests run during the builds.

Run evidence tests:

```bash
./scripts/acceptance-test.sh
./scripts/rollback-test.sh
./scripts/concurrency-test.sh
./scripts/idempotency-test.sh
./scripts/event-recovery-test.sh
./scripts/trace-test.sh
```

Concurrency: **33 succeed, 17 rejected, balance 1,000**.
Trace test checks event consumption within **2 seconds**. See [evidence](EVIDENCE.md).

Stop both (keeps data):

```bash
docker compose down && docker compose -f ../digiteen-user-service/docker-compose.yml down
```
