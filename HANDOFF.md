# TIM implementation handoff

## Current phase
Baseline audit and acceptance hardening.

## Completed in this phase
- Audited the existing multi-module Java 17 TIM project and preserved all pre-existing user changes.
- Confirmed the existing Maven test suite passes: 39 common, 1 server, 25 client, and 4 gateway tests.
- Confirmed `docker compose config` succeeds.
- Added Actuator health/metrics exposure to `tim-server`.
- Added two separately configured TIM service containers to Compose with distinct node IDs, HTTP ports, and Netty ports.
- Added a server image definition and container health checks.
- Added node-owned route cleanup guard to reduce stale-session deletion risk.
- Changed offline Redis index projection to use a per-user monotonic cursor and a messageId member with TTL.
- Unified the Gateway/Netty route namespace as `tim:route:user:{userId}` Redis Hash with node/session/epoch/route fields.
- Added clientMessageId idempotency, transactional-outbox tables/relay, MySQL offline index, group persistence tables, local group channel membership, and RocketMQ broadcast consumer.
- Bound message and Outbox insertion in `ReliableMessageService.accept` with Spring `@Transactional`; retired Gateway HTTP fanout endpoints in favor of authenticated Netty CHAT/GROUP_CHAT frames.
- Changed client reconnect scheduling to exponential backoff with jitter.

## Files changed in this phase
- `tim-server/pom.xml`
- `tim-server/src/main/resources/application.properties`
- `tim-server/src/main/java/com/tuling/tim/server/route/RedisRouteService.java`
- `tim-server/src/main/java/com/tuling/tim/server/message/ReliableMessageService.java`
- `docker-compose.yml`
- `Dockerfile.tim-server`
- `HANDOFF.md`

## Database and messaging status
- Existing code uses `im_message` and a local/optional RocketMQ node bus.
- The repository still needs a complete Flyway migration set and full transactional outbox tables before claiming production-grade delivery.

## Validation
- `./mvnw.cmd test`: latest run exited 0; surefire reports contain no non-zero failures/errors.
- `docker compose config`: latest run exited 0.
- `git diff --check`: latest run exited 0.
- `docker version`: Docker CLI could not connect to the Docker Desktop Linux daemon, so `scripts/smoke-test.ps1` was not claimed as passed.
- Docker Desktop executable was not present at the standard Windows installation path, so the daemon could not be started from this environment.
- Full Docker Compose smoke test is pending: Docker CLI is installed but the Docker Desktop Linux daemon is not running (`//./pipe/dockerDesktopLinuxEngine` unavailable).

## Known blockers / boundaries
- Existing working tree contains extensive user modifications; do not reset or discard them.
- The current project is Java 17/Spring Boot 3, not Java 21.
- Full resume-claim coverage (Flyway, transactional outbox, complete ACK state machine, hybrid group persistence, and integration tests against all middleware) remains to be audited and completed.

## Next actions
1. Run Maven tests and Compose config after the hardening changes.
2. Inspect failures and add focused unit tests for route ownership and offline cursor behavior.
3. Complete remaining schema/documentation evidence without overstating unverified capabilities.
4. Review secrets and diff, commit, inspect remote, then push without force.
