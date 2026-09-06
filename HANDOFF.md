# TIM implementation handoff

## Current phase
Enterprise reliability v2 is in progress on `codex/enterprise-reliability-v2`; authentication, route renewal, durable delivery records, and continuous offline cursors are implemented and tested locally.

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
- Added short-lived HMAC-SHA256 Gateway-to-Netty connect tokens bound to user, serverId, expiry, and nonce.
- Added durable `message_delivery` records and recipient-scoped conditional ACK updates; the JVM pending map remains only an acceleration cache.
- Added authenticated-session checks for ACK frames and atomic route renewal on heartbeat outside the Netty EventLoop.
- Added durable offline cursor reuse and a client continuous-prefix cursor store with optional atomic local-file persistence.
- Changed group broadcast publication to `GROUP_MESSAGE_CREATED` through the transactional Outbox; Relay broadcasts only after commit.
- Added scheduled MySQL-to-Redis offline projection rebuild, bounded by `tim.offline.max-size`, for Redis cache loss/recovery.
- Fixed Compose runtime validation: ZooKeeper/RocketMQ health checks no longer depend on missing `nc`; MySQL host port is configurable with `TIM_MYSQL_PORT`; Flyway is the Compose schema owner; and RocketMQ Bus initialization no longer forms a Spring circular dependency.

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
- `scripts/smoke-test.ps1`: latest run passed with `TIM_MYSQL_PORT=13306`; both TIM nodes and all middleware became healthy, and the script's durable offline idempotency and cross-node group persistence assertions passed.

## Known blockers / boundaries
- Existing working tree contains extensive user modifications; do not reset or discard them.
- The current project is Java 17/Spring Boot 3, not Java 21.
- Real TCP client-to-client delivery, node-kill recovery, Redis/RocketMQ fault injection, and online RocketMQ broadcast consumption remain unverified.
- The HTTP smoke assertions prove shared durable persistence paths, but do not replace the pending TCP/middleware failure scenarios.

## Next actions
1. Run a real two-client Netty acceptance against the Compose nodes.
2. Verify node-kill recovery, ACK loss/retry, Redis route loss, RocketMQ duplicate delivery, and concurrent group sequence behavior.
3. Keep the current smoke result scoped to health, durable offline idempotency, and cross-node group persistence.

## Enterprise reliability v2 continuation
- Commits: `cf19271`, `f48832b`, `1313c7d`, `f38cd25`, `beb875f`, `0bf868a`, `1fee929`.
- Latest full Maven test, verify, Compose config, and diff-check passed after these changes.
- Added database conditional lease claiming/recovery for due `message_delivery` rows and persisted server-side offline ACK upper-bound state in migration `V3__offline_ack_cursor.sql`.
- Latest runtime smoke passed with `TIM_MYSQL_PORT=13306`: all middleware health checks and both TIM `/actuator/health` endpoints passed. Full cross-node message/failure-injection scenarios remain to be exercised.
