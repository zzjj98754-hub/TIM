# TIM Modernization ExecPlan

## M0 Baseline

### Status
- In progress
- Git metadata is absent in this workspace, so a true repository baseline cannot be created here.

### Verified Facts
- Multi-module Maven project with `tim-common`, `tim-gateway-api`, `tim-server-api`, `tim-gateway`, `tim-server`, `tim-client`.
- Current parent POM uses Spring Boot `2.1.7.RELEASE` and Java 8 compiler settings.
- Runtime config is hard-coded to local Redis and Zookeeper endpoints in module `application.properties` files.
- No MySQL, Flyway, JWT, Docker Compose, or Maven Wrapper existed before this step.

### Validation Commands
- `java -version`
- `mvn -version`
- `mvn clean test`
- `mvn clean package -DskipTests`
- `docker compose up -d redis zookeeper`
- `docker compose ps`

### Known Risks
- Maven is not installed globally in this environment.
- Existing target directories may contain stale build output from prior runs.
- Application startup still depends on Redis and Zookeeper being reachable.

### Rollback
- Remove files added in M0 and restore the previous README/application properties state manually.
