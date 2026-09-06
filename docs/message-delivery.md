# 单聊与可靠投递

客户端业务帧先经过已认证的 `ConnectionSession`。服务端以会话用户覆盖消息体 senderId，生成/校验全局 messageId，并由 `ReliableMessageService` 先做 Redis 快速去重，再由 MySQL 唯一索引和事务 outbox 做最终幂等与补发。

Outbox Relay 使用 `PROCESSING` 短租约认领事件，发送到 RocketMQ 后转 `SENT`；租约过期可被其他 Relay 重新认领，超过 `tim.outbox.max-retries` 转 `DEAD`。目标节点依据 Redis 路由查询本地 Channel。客户端以有界 `MessageDeduplicator` 按 messageId 去重，首次业务回调成功后才发送 ACK；重复投递只重新 ACK，不重复显示。服务端在 `message_delivery` 持久化 `PENDING/DELIVERING/OFFLINE/ACKED` 收件状态，并以 recipient 身份条件更新 ACK；当前 JVM Map 仍用于快速重试，数据库恢复扫描和租约领取仍是后续工作。目标不可达或离线时正文保存在 MySQL，近期索引投影到 Redis ZSet。

核心入口：`tim-server/.../reliable/ReliableMessageService.java`、`.../mq/RocketMqNodeMessageBus.java`、`.../handler/TIMServerHandle.java`、`.../route/RedisRouteService.java`。

群消息在正文、序号和成员 Inbox 事务中追加 `GROUP_MESSAGE_CREATED` Outbox；Relay 提交后才调用 RocketMQ 广播，重复事件由消息 ID 和本地客户端去重保护。
