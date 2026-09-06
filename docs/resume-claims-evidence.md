# 简历描述与代码证据

| 描述 | 真实入口/证据 |
|---|---|
| Netty自定义协议、粘包半包 | `tim-common/.../ObjEncoder.java`、`ObjDecoder.java`、`TIMFrameCodecTest.java` |
| 认证Channel与旧连接保护 | `tim-server/.../TIMServerHandle.java`、`SessionSocketHolder.java`、`ConnectionSession.java` |
| ZooKeeper注册发现 | `RegistryZK.java`、`ZKit.java`、Gateway `ZKit.java` |
| 统一Redis路由 | `RedisRouteService.java`、Gateway `AccountServiceRedisImpl.java`，Key为 `tim:route:user:{id}`；Lua 原子维护 route/presence 与 session/epoch 清理 |
| 幂等与可靠投递 | `MessageHistoryRepository.java`、`DeliveryRepository.java`、`DeliveryStatus.java`、`OutboxRepository.java`、`ReliableMessageService.java`；MySQL 唯一约束负责最终幂等，投递状态有合法迁移规则 |
| 离线消息 | `offline_message_index`、`tim:offline:{userId}`，正文由 `im_message` 保存 |
| 群写/读扩散 | `GroupMessageService.java`、`im_group`、`group_message_inbox`、`group_member_cursor` |
| 节点群广播 | `RocketMqNodeMessageBus.java`、`GroupChannelPushService.java`、Topic `TIM_GROUP_BROADCAST` |
| 多节点部署 | `docker-compose.yml`、`Dockerfile.tim-server`、`scripts/smoke-test.*`；已实测健康启动、离线幂等、跨节点群持久化和节点重启后持久化消息可读 |

边界：上述 smoke 不等价于真实 TCP 双客户端在线投递、未 ACK 租约恢复、RocketMQ 在线广播消费或 Redis/RocketMQ 故障注入；当前项目使用 Java 17，RocketMQ 和 MySQL 的基础 Compose 集成已有运行证据。
