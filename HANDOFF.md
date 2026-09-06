# TIM implementation handoff

## Current phase
Resume-alignment implementation and documentation complete locally; middleware smoke test attempted but blocked by Docker Hub image authorization/network access.

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
- Routed the CLI client's P2P/GROUP_CHAT commands through the authenticated Netty channel; legacy HTTP push now returns a deprecation response.
- Added cursored offline records, Netty-login replay, post-handler message/cursor ACKs, Redis/MySQL page de-duplication, and Redis fast dedup before persistence.
- Added focused tests for authenticated sender identity, replacement-session cleanup, retry/ACK behavior, group broadcast, offline trimming/fallback, and message IDs.
- Made local session maps use Netty `Channel`, fixed string message IDs in push request IDs, and made ZooKeeper root creation safe under concurrent node startup.
- Added GitHub Actions CI for Maven test/verify and Compose model validation.
- Replaced stale architecture status claims with current implementation evidence and added deployment, delivery, offline, group, testing, limitations, and acceptance-report documents.
- Replaced `CallerRunsPolicy` with an instrumented bounded-queue rejection policy so blocking work is not run on a Netty caller thread; exposed executor gauges and rejection counter through Micrometer.
- Added Outbox Relay short leases (`PROCESSING`), expired-lease reclaim, and configurable `DEAD` transition after retry exhaustion.
- Removed Gateway's pre-auth `pending/gateway-login` route write; only the authenticated Netty session now owns the unified Redis route lifecycle.
- Made route Hash, TTL, presence, and session/epoch conditional cleanup atomic with Redis Lua scripts.
- Added bounded client-side messageId/clientMessageId de-duplication; duplicate deliveries are ACKed without invoking the callback or displaying the message again.
- Changed group permission/size checks to use MySQL `group_member` as the source of truth and made group sequence allocation transactional with row locking for multi-node safety.
- Added `GroupFanoutStrategySelector` with a tested threshold boundary so write/read fanout selection has one explicit entry point.
- Tightened business-frame authentication to require both a session and the current `userId -> Channel` binding, rejecting replaced old Channels.
- Extracted and tested client reconnect backoff: 1/2/4/8/16/30 second exponential base, capped at 30 seconds with jitter.
- Hardened `ObjDecoder` to reject empty bodies and added EmbeddedChannel coverage for invalid magic/version, negative/oversized lengths, split frames, and coalesced frames.
- Added health checks for Redis, MySQL, ZooKeeper, RocketMQ NameServer/Broker and health-gated dependencies for both TIM nodes in Compose.
- Added configurable 64 KiB default business-content validation at the Netty handler before persistence, routing, or MQ work.
- Corrected TIM Compose health checks to use the `curl` binary installed by `Dockerfile.tim-server` instead of unavailable `wget`.

## Files changed in this phase
- `tim-server/pom.xml`
- `tim-server/src/main/resources/application.properties`
- `tim-server/src/main/java/com/tuling/tim/server/route/RedisRouteService.java`
- `tim-server/src/main/java/com/tuling/tim/server/message/ReliableMessageService.java`
- `docker-compose.yml`
- `Dockerfile.tim-server`
- `HANDOFF.md`

## Database and messaging status
- `im_message`, `outbox_event`, offline index, group, member, sequence, group-message, inbox, and member-cursor tables are defined in `schema.sql`, `script/init.sql`, and Flyway `V1__tim_core.sql`.
- `tim.mq.mode=rocketmq` uses a node-targeted RocketMQ topic and a separate `BROADCASTING` group topic so every active node receives group broadcasts; `local` remains the no-broker development fallback.
- Redis route Hash is `tim:route:user:{userId}`; offline ZSet is `im:offline:{userId}` with messageId members and per-user delivery cursors.

## Validation
- `./mvnw.cmd test`: latest run exited 0; surefire reports contain no non-zero failures/errors.
- `docker compose config`: latest run exited 0.
- `./mvnw.cmd -q verify -DskipTests`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-server -am -Dtest=BeanConfigTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-server -am -Dtest=ReliableMessageServiceTest,OfflineMessageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-gateway -am test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-client -am -Dtest=MessageDeduplicatorTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-server -am -Dtest=GroupChannelPushServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-server -am -Dtest=GroupFanoutStrategySelectorTest,GroupChannelPushServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-server -am -Dtest=TIMServerHandleTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-client -am -Dtest=ReconnectBackoffTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0.
- `./mvnw.cmd -q -pl tim-common -Dtest=TIMFrameCodecTest test`: latest run exited 0.
- `docker compose config`: latest run exited 0 after adding middleware health checks.
- `./mvnw.cmd -q -pl tim-server -am -Dtest=TIMServerHandleTest -Dsurefire.failIfNoSpecifiedTests=false test`: latest run exited 0 after content-limit coverage.
- `docker compose config`: latest run exited 0 after correcting the container healthcheck command.
- `.github/workflows/ci.yml`: added; it runs Maven test/verify and `docker compose config` on Ubuntu.
- `git diff --check`: latest run exited 0.
- `docker info`: Docker Desktop Linux daemon was available during the latest attempt.
- `scripts/smoke-test.ps1`: failed while building `tim-node-1`/`tim-node-2`; Docker could not fetch the Docker Hub OAuth token for `eclipse-temurin:17-jre-jammy` because the registry connection timed out.
- No TIM container health check or two-node runtime result was claimed; the smoke script's cleanup left the Compose project stopped.

## Known blockers / boundaries
- Existing working tree contains extensive user modifications; do not reset or discard them.
- The current project is Java 17/Spring Boot 3, not Java 21.
- Full runtime integration against Redis, MySQL, ZooKeeper, and RocketMQ remains unverified because the required base image could not be pulled from Docker Hub.
- The focused/unit tests prove local routing, persistence orchestration, cursor semantics, retry/ACK behavior, and broadcast fanout; they do not replace the pending two-node middleware smoke test.

## Next actions
1. In an environment with Docker Hub access or a locally cached `eclipse-temurin:17-jre-jammy`, run `./mvnw.cmd package` and `scripts/smoke-test.ps1`.
2. Verify two-node RocketMQ private delivery, broadcast fanout, Redis route ownership, MySQL Flyway startup, and offline replay against the Compose stack.
3. Do not claim the Docker smoke test passed until those runtime checks produce evidence.
