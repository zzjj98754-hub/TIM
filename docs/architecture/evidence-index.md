# TIM 架构证据索引

状态表示源码接入程度，不代表外部中间件已经在本机端到端运行。

| ID | 证据位置 | 关键事实 | 状态 |
|---|---|---|---|
| E-BOOT | `tim-server/.../server/TIMServer.java` | Netty boss/worker bind 与优雅关闭 | CONFIRMED |
| E-ZK | `tim-server/.../kit/RegistryZK.java`, Gateway `ZKit` | `/im/servers/{serverId}` 临时节点与 child watch | CONFIRMED |
| E-LOGIN | Gateway `RouteController`, `AccountServiceRedisImpl` | 登录校验账号、选择节点、返回 TCP 地址；不提前写用户路由 | CONFIRMED |
| E-BIND | `TIMServerHandle`, `SessionSocketHolder`, `RedisRouteService` | 会话绑定本地 Channel，统一写入 route Hash，旧连接比较清理 | CONFIRMED |
| E-FRAME | `tim-common/.../ObjEncoder.java`, `ObjDecoder.java` | TIM1、18-byte header、LengthField 参数和拆包测试 | CONFIRMED |
| E-HEART | server/client `*Initializer.java`, `*Handle.java` | IdleStateHandler、PING/PONG、断开清理 | CONFIRMED |
| E-RMS | `ReliableMessageService`, `MessageHistoryRepository` | Redis 快速去重、MySQL 唯一约束、Outbox、投递和 ACK 重试 | CONFIRMED |
| E-MQ | `RocketMqNodeMessageBus`, `OutboxRelay` | 私聊节点 topic/tag；群 topic 使用 `BROADCASTING` | CODE-CONFIRMED; runtime pending |
| E-OFFLINE | `OfflineMessageService`, `OfflineCursorStore` | ZSet member=messageId、score=用户 cursor、分页/ACK/容量/回退 | CONFIRMED |
| E-GROUP | `GroupMessageService`, `WriteFanoutStrategy`, `ReadFanoutStrategy` | 成员权限、写扩散、读扩散和成员游标 | CONFIRMED |
| E-BROADCAST | `GroupChannelPushService`, `SessionSocketHolder` | 节点只遍历本地 groupId→Channel 集合 | CODE-CONFIRMED; runtime pending |
| E-POOL | `BeanConfig`, `InstrumentedRejectedExecutionHandler` | 有界业务池、显式拒绝和 Micrometer 指标 | CONFIRMED |
| E-COMPOSE | `docker-compose.yml`, `scripts/smoke-test.*` | 两 TIM 节点及 MySQL/Redis/ZK/RocketMQ 服务 | STATIC-CONFIRMED; runtime pending |
