# 单聊与可靠投递

客户端业务帧先经过已认证的 `ConnectionSession`。服务端以会话用户覆盖消息体 senderId，生成/校验全局 messageId，并由 `ReliableMessageService` 先做 Redis 快速去重，再由 MySQL 唯一索引和事务 outbox 做最终幂等与补发。

Outbox Relay 使用 `PROCESSING` 短租约认领事件，发送到 RocketMQ 后转 `SENT`；租约过期可被其他 Relay 重新认领，超过 `tim.outbox.max-retries` 转 `DEAD`。目标节点依据 Redis 路由查询本地 Channel。客户端处理成功后发送 ACK，服务端维护待确认状态并按退避策略重试；目标不可达或离线时正文保存在 MySQL，近期索引投影到 Redis ZSet。

核心入口：`tim-server/.../reliable/ReliableMessageService.java`、`.../mq/RocketMqNodeMessageBus.java`、`.../handler/TIMServerHandle.java`、`.../route/RedisRouteService.java`。
