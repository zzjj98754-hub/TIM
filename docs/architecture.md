# TIM 架构

当前实现是 Java 17、Spring Boot 3.5、Netty 的多模块 Maven 项目。`tim-server` 提供 TCP 长连接和本地 `userId -> Channel` 路由，`tim-gateway` 提供登录及节点发现入口，`tim-common` 保存协议和公共模型，`tim-client` 是 CLI 客户端。

运行时依赖 Redis、MySQL、ZooKeeper 和 RocketMQ。ZooKeeper 的 `/tim/nodes/{nodeId}` 为临时节点；Redis 路由使用 `tim:route:user:{userId}` Hash，离线索引使用 `im:offline:{userId}` ZSet；私聊节点投递使用 `TIM_NODE_MESSAGE`，群广播使用 `TIM_GROUP_BROADCAST`。

TCP Pipeline 依次包含 `IdleStateHandler`、`LengthFieldBasedFrameDecoder(1048576, 14, 4, 0, 0)`、对象编解码器和业务 Handler。数据库迁移位于 `tim-server/src/main/resources/db/migration/V1__tim_core.sql`。

完整 Compose 运行态烟测需要本机 Docker daemon；可先执行 `docker compose config` 做静态校验。当前仓库不把 Docker 未运行时的静态检查冒充为多节点运行验证。
