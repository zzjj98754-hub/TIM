# TIM Modernization ExecPlan

## M0 Baseline

### Status
- Complete
- New Git repository initialized and baseline committed as `ae7c27b` (`chore: establish TIM modernization baseline`).

### Verified Facts
- Multi-module Maven project with `tim-common`, `tim-gateway-api`, `tim-server-api`, `tim-gateway`, `tim-server`, `tim-client`.
- Current parent POM uses Spring Boot `2.1.7.RELEASE` and Java 8 compiler settings.
- Runtime config targets local Redis and Zookeeper endpoints in module `application.properties` files.
- Parent POM compiles for Java 8; this workstation uses JDK `17.0.19` and Maven Wrapper `3.9.12`.
- Redis and Zookeeper Compose definitions, Maven Wrapper, `.env.example`, and `.gitignore` are present.
- Service ports: Gateway HTTP `8090`, Server HTTP `8082`, Server Netty `9002`, Client HTTP `8003`, Redis `6379`, Zookeeper `2181`.

### Validation Commands
- `java -version`
- `./mvnw.cmd -version`
- `./mvnw.cmd clean compile`
- `./mvnw.cmd clean test`
- `./mvnw.cmd clean package -DskipTests`
- `docker compose up -d redis zookeeper`
- `docker compose ps`

### Known Risks
- `clean compile` passed for all seven reactor projects.
- `clean package -DskipTests` passed for all seven reactor projects.
- `clean test` failed in `tim-client`: twelve tests cannot load the Spring context because `RouteRequestImpl` requires `tim.gateway.url` without a test value. This is the first M1 fix.
- Docker Compose configuration is valid, but infrastructure startup is blocked because Docker Desktop's Linux daemon is not running on this workstation.
- Application startup remains unverified until Redis and Zookeeper are available.

### Rollback
- Restore the baseline with `git reset --hard ae7c27b` only when explicitly approved, or create a new commit that reverts a later milestone.

### M0 Result
- M0 is complete with reproducible build results and documented environmental blockers.
- Do not begin M1 until explicitly approved.

## M1 Deterministic Correctness Fixes

### Status
- Complete

### Changes
- HTTP callers now treat missing, non-success, and non-success business responses as failures and close responses.
- Gateway validation and exception responses are active; empty online-user results use empty collections.
- Consistent-hash rebuild, configured ZooKeeper root, cache snapshot replacement, Redis scan resource cleanup, and frame-size validation are covered by the M1 baseline code.
- Client tests provide an isolated Gateway URL and do not auto-start a network client.
- Server push waits for the Netty write result and reports offline, failed, or timed-out writes instead of returning a false success.
- Gateway tests use mocks instead of requiring live Redis/Zookeeper instances.

### Verification
- `./mvnw.cmd clean test` passed on 2026-08-26.
- `./mvnw.cmd clean verify` passed on 2026-08-26 after upgrading Maven Surefire to `3.2.5`, which fixes its JDK 17 fork-process compatibility issue.

### Remaining Risks
- The current M1 protocol has frame bounds but does not yet implement M4-level ACK, message IDs, persistence, or offline delivery.
- Docker Desktop remains unavailable on this workstation, so external-service integration remains an M6/Testcontainers concern.
