# 单聊与可靠投递

客户端业务帧先经过已认证的 `ConnectionSession`。服务端以会话用户覆盖消息体 senderId，生成/校验全局 messageId，并由 MySQL 唯一索引和事务 outbox 做最终幂等与补发；Redis 去重键只在事务提交后作为缓存写入，不承担最终裁决。

Outbox Relay 使用 `PROCESSING` 短租约认领事件，发送到 RocketMQ 后转 `SENT`；租约过期可被其他 Relay 重新认领，超过 `tim.outbox.max-retries` 转 `DEAD`。目标节点依据 Redis 路由查询本地 Channel。客户端以有界 `MessageDeduplicator` 按 messageId 去重，首次业务回调成功后才发送 ACK；重复投递只重新 ACK，不重复显示。服务端在 `message_delivery` 持久化 `PENDING/DELIVERING/OFFLINE/ACKED` 收件状态，并以 recipient 身份条件更新 ACK；`im_message` 同样只允许对应接收者从 `DELIVERING/OFFLINE` 条件迁移到 `ACKED`，并写入 `acked_at`。两表更新在一个事务中。JVM Map 仅作加速，定时任务会以数据库条件更新领取到期任务，租约过期后可由其他节点恢复。恢复任务沿用数据库 `attempt_count`，达到在线重试上限即转离线，不会因节点重启无限重试。目标不可达或离线时正文保存在 MySQL，近期索引投影到 Redis ZSet。

核心入口：`tim-server/.../message/ReliableMessageService.java`、`.../message/DeliveryRepository.java`、`.../message/DeliveryStatus.java`、`.../mq/RocketMqNodeMessageBus.java`、`.../handle/TIMServerHandle.java`、`.../route/RedisRouteService.java`。

群消息在正文、序号和成员 Inbox 事务中追加 `GROUP_MESSAGE_CREATED` Outbox；Relay 提交后才调用 RocketMQ 广播，重复事件由消息 ID 和本地客户端去重保护。Redis 离线索引丢失时，定时任务按 MySQL `offline_message_index` 重建近期 ZSet 投影。

Actuator 暴露 `tim_delivery_pending`、`tim_delivery_oldest_seconds`、`tim_outbox_pending`、`tim_outbox_dead` 以及消息 accept/ACK/retry/offline/dead 计数；前四项直接读取持久化表，不以 JVM Map 作为积压事实来源。
