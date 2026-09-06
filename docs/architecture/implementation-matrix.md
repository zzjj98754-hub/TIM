# 当前实现矩阵

| 能力 | 状态 | 代码证据与边界 |
|---|---|---|
| Netty 长连接、协议、心跳 | IMPLEMENTED | `TIMServer`, `TIMServerInitializer`, `ObjDecoder/ObjEncoder`, `IdleStateHandler`; EmbeddedChannel 覆盖半包/粘包、非法魔数/版本、负/超大长度和空体 |
| 客户端重连退避 | IMPLEMENTED | `ReconnectBackoff` 提供 1/2/4/8/16/30 秒指数基线和抖动，`ReConnectManager` 调度执行 |
| 认证身份与旧连接保护 | IMPLEMENTED | Gateway 签发 HMAC-SHA256 短期 connectToken；`TIMServerHandle` 校验 token/user/server/expiry，并同时校验当前 Channel；ACK 也要求认证会话 |
| ZooKeeper 注册/Watch | IMPLEMENTED | `RegistryZK`, `ZKit`, Gateway `ServerCache`; `/tim/nodes/{nodeId}` 临时节点，根节点并发创建有保护 |
| Redis 用户路由 | IMPLEMENTED | Gateway 登录只返回候选节点；仅已认证 Netty Session 通过 Lua 原子写入 route Hash/TTL/presence，断开按 session/epoch 原子匹配删除 |
| 单聊可靠投递 | PARTIAL-IMPLEMENTED | `ReliableMessageService`、MySQL `im_message/outbox_event/message_delivery`、RocketMQ node topic、目标节点本地 Channel、ACK/重试；持久化 Delivery 的数据库租约恢复仍待完成 |
| Outbox 并发 Relay | IMPLEMENTED | `OutboxRepository.claimPending` 使用短租约 `PROCESSING`；过期重试，超限进入 `DEAD` |
| 客户端重复投递去重 | IMPLEMENTED | `MessageDeduplicator` 有界按 messageId/clientMessageId 去重；首次回调成功后 ACK，重复帧只 ACK |
| 服务端幂等 | IMPLEMENTED | Redis 快速去重 + MySQL `message_id` 与 `(from_user_id, client_message_id)` 唯一约束 |
| 离线消息 | IMPLEMENTED-LOCALLY | MySQL 正文/索引/确认游标 + `im:offline:{userId}` ZSet；重复 messageId 复用游标，客户端连续游标和分页已覆盖，定时任务可从 MySQL 重建 Redis 投影；双节点运行态仍未验证 |
| 普通群写扩散 | IMPLEMENTED | `WriteFanoutStrategy`, `group_message_inbox`；成员索引唯一，正文不按成员复制 |
| 超大群读扩散 | IMPLEMENTED | `ReadFanoutStrategy`, `group_member_cursor`；单份正文与成员读取游标 |
| 群策略、成员权限与序号 | IMPLEMENTED | `GroupFanoutStrategySelector` 统一阈值选择；`GroupMessageService` 以 MySQL `group_member` 校验权限/规模，事务锁定 `group_sequence` 生成多节点安全序号 |
| 群节点广播 | IMPLEMENTED | `TIM_GROUP_BROADCAST` + RocketMQ `BROADCASTING` + `GroupChannelPushService` 本地集合 |
| 业务线程池隔离与指标 | IMPLEMENTED | `BeanConfig` 有界 `ArrayBlockingQueue`、显式拒绝策略；Actuator 暴露 active/queue/pool/completed/rejected |
| 业务正文长度限制 | IMPLEMENTED | `TIMServerHandle` 使用 `tim.message.max-content-length`（默认 64 KiB）拒绝空正文和超限正文 |
| 双节点 Compose | RUNTIME-CONFIRMED (限定范围) | `scripts/smoke-test.ps1` 已验证两个 TIM 节点、Redis/MySQL/ZooKeeper/RocketMQ 健康启动、Flyway、离线幂等和跨节点群持久化读取；真实 TCP 客户端和故障注入仍未验证 |
| CI | IMPLEMENTED | `.github/workflows/ci.yml` 执行 Maven test/verify 与 Compose 静态校验 |
| 完整运行态故障注入 | PENDING | 需要可用 Docker 环境验证 Redis/MQ/ZK 故障和双节点端到端链路 |
