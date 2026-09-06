# TIM 当前架构

TIM 是 Java 17、Spring Boot 3、Netty 的多模块分布式 IM。Gateway 负责登录和 ZooKeeper 节点控制面；Netty server 负责 TCP 连接面；可靠消息服务负责 MySQL、Redis、Outbox、RocketMQ 和 ACK；群服务负责写扩散/读扩散与节点广播。

## 阅读顺序

先看 [系统架构](../architecture.md)、[代码地图](codebase-map.md)、[实现矩阵](implementation-matrix.md)，再看 [协议规范](protocol-spec.md)、[可靠性分析](reliability-analysis.md) 和各流程图。

## 唯一业务链路

客户端通过 Gateway 登录后建立 TCP 长连接。登录成功的 Channel 绑定 `ConnectionSession`；`CHAT/GROUP_CHAT` 必须通过认证校验。单聊进入 `ReliableMessageService`，经 Redis 快速去重、MySQL 消息+Outbox、RocketMQ 节点转发、目标节点本地 Channel、客户端 ACK 和超时重试。旧 Gateway `/p2pRoute`、`/groupRoute` 与 server `/sendMsg` 仅保留弃用兼容响应，不再执行 HTTP 推送。

## 关键状态

- ZooKeeper：`/im/servers/{serverId}` 临时节点，Gateway child watch 刷新节点快照。
- Redis 路由：`tim:route:user:{userId}` Hash，包含 node/session/epoch/route；离线索引为 `im:offline:{userId}` ZSet。
- RocketMQ：私聊 `TIM_NODE_MESSAGE` 按目标 serverId tag 投递；群消息 `TIM_GROUP_BROADCAST` 使用 `BROADCASTING`，每个 TIM 节点各自消费。
- 数据库：Flyway `V1__tim_core.sql` 与 Compose `script/init.sql` 提供消息、Outbox、离线索引、群正文、成员 inbox 和游标表。

## 验证边界

单元测试和 Maven 构建已通过，`docker compose config` 已通过；双节点中间件运行态尚未验证，因为当前环境 Docker daemon 不可连接。不要将静态配置检查写成端到端通过。
