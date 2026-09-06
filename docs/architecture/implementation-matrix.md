# 当前实现矩阵

| 能力 | 状态 | 代码证据与边界 |
|---|---|---|
| Netty 长连接、协议、心跳 | IMPLEMENTED | `TIMServer`, `TIMServerInitializer`, `ObjDecoder/ObjEncoder`, `IdleStateHandler`; 连接和中间件运行态待 Compose 验证 |
| 认证身份与旧连接保护 | IMPLEMENTED | `TIMServerHandle` 同时校验会话和当前 Channel，`SessionSocketHolder`/`RedisRouteService` 按 session/epoch 清理；旧 Channel 不能继续发业务帧 |
| ZooKeeper 注册/Watch | IMPLEMENTED | `RegistryZK`, `ZKit`, Gateway `ServerCache`; `/tim/nodes/{nodeId}` 临时节点，根节点并发创建有保护 |
| Redis 用户路由 | IMPLEMENTED | Gateway 登录只返回候选节点；仅已认证 Netty Session 通过 Lua 原子写入 route Hash/TTL/presence，断开按 session/epoch 原子匹配删除 |
| 单聊可靠投递 | IMPLEMENTED | `ReliableMessageService`、MySQL `im_message/outbox_event`、RocketMQ node topic、目标节点本地 Channel、ACK/重试 |
| Outbox 并发 Relay | IMPLEMENTED | `OutboxRepository.claimPending` 使用短租约 `PROCESSING`；过期重试，超限进入 `DEAD` |
| 客户端重复投递去重 | IMPLEMENTED | `MessageDeduplicator` 有界按 messageId/clientMessageId 去重；首次回调成功后 ACK，重复帧只 ACK |
| 服务端幂等 | IMPLEMENTED | Redis 快速去重 + MySQL `message_id` 与 `(from_user_id, client_message_id)` 唯一约束 |
| 离线消息 | IMPLEMENTED | MySQL 正文/索引 + `im:offline:{userId}` ZSet；游标分页、ACK 后推进、容量/TTL、MySQL 回退 |
| 普通群写扩散 | IMPLEMENTED | `WriteFanoutStrategy`, `group_message_inbox`；成员索引唯一，正文不按成员复制 |
| 超大群读扩散 | IMPLEMENTED | `ReadFanoutStrategy`, `group_member_cursor`；单份正文与成员读取游标 |
| 群策略、成员权限与序号 | IMPLEMENTED | `GroupFanoutStrategySelector` 统一阈值选择；`GroupMessageService` 以 MySQL `group_member` 校验权限/规模，事务锁定 `group_sequence` 生成多节点安全序号 |
| 群节点广播 | IMPLEMENTED | `TIM_GROUP_BROADCAST` + RocketMQ `BROADCASTING` + `GroupChannelPushService` 本地集合 |
| 业务线程池隔离与指标 | IMPLEMENTED | `BeanConfig` 有界 `ArrayBlockingQueue`、显式拒绝策略；Actuator 暴露 active/queue/pool/completed/rejected |
| 双节点 Compose | DEFINED | `docker-compose.yml` 含依赖、健康检查和两个节点；本环境 Docker daemon 不可用，运行态未验证 |
| CI | IMPLEMENTED | `.github/workflows/ci.yml` 执行 Maven test/verify 与 Compose 静态校验 |
| 完整运行态故障注入 | PENDING | 需要可用 Docker 环境验证 Redis/MQ/ZK 故障和双节点端到端链路 |
