# Digiteen Wallet Service

## Run with Docker

Start `digiteen-user-service` first, then run here:

```bash
docker compose up -d --build
```

Check:

```bash
docker compose ps
curl http://localhost:8081/actuator/health
```

Swagger:

```text
http://localhost:8081/swagger-ui.html
```

Register/login through User Service Swagger at
`http://localhost:8080/swagger-ui.html`. Copy `accessToken`, click **Authorize** in
Wallet Swagger, and paste the token.

## Test

```bash
./scripts/concurrency-test.sh
./scripts/idempotency-test.sh
./scripts/event-recovery-test.sh
./scripts/trace-test.sh
```

Expected concurrency result:

```text
33 successful
17 insufficient funds
1000 final balance
```

## Stop

```bash
docker compose down
```

Delete all Wallet Service data and start clean:

```bash
docker compose down --volumes
docker compose up -d --build
```
