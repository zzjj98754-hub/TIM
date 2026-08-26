# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules
mvn clean install -DskipTests

# Build a single module
mvn clean install -pl tim-common -DskipTests

# Run tests
mvn test
mvn test -pl tim-common -Dtest=TrieTreeTest

# Start IM-Server (requires Zookeeper + Redis running)
mvn spring-boot:run -pl tim-server

# Start Gateway (requires Zookeeper + Redis running)
mvn spring-boot:run -pl tim-gateway

# Start Client (requires Gateway running, configure tim.user.id in application.properties first)
mvn spring-boot:run -pl tim-client
```

**Prerequisites:** JDK 8, Maven 3.x, Zookeeper (default port 2181), Redis (default port 6379).

## Architecture

Multi-module Maven project (`tim` parent POM). Six modules, four deployable:

```
tim-client ──Netty──▶ tim-server ──HTTP──▶ tim-gateway ──▶ Zookeeper (service discovery)
                           │                               │
                           └──────────── Redis ─────────────┘ (routing, accounts, online status)
```

### Modules

| Module | Role |
|--------|------|
| `tim-server` | Netty-based IM server. Maintains long connections, pushes messages to clients. Registers itself in ZK on startup. |
| `tim-gateway` | HTTP routing gateway. Handles login, assigns an IM-Server to each client, forwards group/P2P messages. Stateless, can be Nginx-load-balanced. |
| `tim-client` | Command-line IM client. Connects to Gateway for login, then establishes a Netty long connection to the assigned IM-Server. |
| `tim-common` | Shared code: custom protocol (Protostuff serialization), consistent-hash routing algorithms, data structures (RingBufferWheel, TrieTree, SortArrayMap), utility classes. |
| `tim-gateway-api` | Feign-style interfaces for Gateway HTTP endpoints. |
| `tim-server-api` | Feign-style interfaces for IM-Server HTTP endpoints. |

### Message flow

**Login:**
Client → Gateway `/login` → authenticates via Redis → picks an IM-Server via consistent hash → returns server address to client → Client opens Netty connection to that IM-Server.

**Group chat:**
Client sends msg via Netty → IM-Server → Gateway `/groupRoute` → scans Redis for all online users' routing info → HTTP-pushes to each user's IM-Server → each IM-Server writes to the target Netty Channel.

**P2P chat:**
Same as group chat but Gateway looks up a single userId's route in Redis (`/p2pRoute`).

### Key design decisions

- **Protostuff over JSON**: Smaller payload for high-frequency IM messages; Schema caching in `ConcurrentHashMap` avoids repeated reflection.
- **Consistent hash over modulo**: When IM-Server nodes join/leave, only adjacent hash-interval data remaps, not the entire key space.
- **Zookeeper ephemeral nodes + Watch**: Gateway subscribes to `/route` children changes; dead IM-Server nodes are auto-removed, Gateway refreshes its local cache within seconds.
- **Redis for routing, not MQ**: Routing table is key-value by nature (userId → serverAddress); Redis SET for online status; SCAN for batch operations.
- **Boss + Worker EventLoopGroups**: boss accepts connections, workers handle I/O; blocking business logic is offloaded to a separate thread pool to avoid stalling EventLoops.
