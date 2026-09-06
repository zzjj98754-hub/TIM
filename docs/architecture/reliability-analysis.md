# 可靠性与故障分析

## 单聊实际链路

`TIMClient#sendChat` JSON → Netty `CHAT` → `TIMServerHandle#channelRead0` → `ReliableMessageService#accept`：生成 Snowflake ID（若缺失）→ Redis `SETNX im:dedup:{messageId}` TTL 24h → JDBC `INSERT ... ON DUPLICATE KEY` 状态 PENDING → Redis `im:route:{toUserId}` → 同节点写 Channel，或 `NodeMessageBus#forward` → 目标节点本地 Channel → client `TIMClientHandle` 收到 CHAT 并发送 ACK → target `ReliableMessageService#acknowledge` 删内存 Pending、更新 MySQL ACKED。

含义严格区分：进入 `accept` 是**服务器接收**；JDBC 返回才是**历史表写入**；RocketMQ `producer.send` 成功仅是**broker 接收**；`writeAndFlush` 被调用也不是客户端读取；仅对端 client 发送 ACK 才能证明客户端代码观察到该帧。ACK 不等于用户已读。

## 语义与一致性

目标语义是“**至少一次尝试投递 + 最终存储防重**”，不是 exactly-once：重试会重复写同一 payload，客户端无 `messageId` 去重；Redis SETNX 成功后 JDBC 失败会永久吞掉 24h 内同 ID 重试；JDBC 成功后 MQ send 失败无 outbox；跨节点 ACK 只更新目标节点的 Pending/状态；ACK 未绑定 channel/user，任意连接知晓 ID 可确认。数据库主键能避免历史重复行，但不能阻止重复客户端显示。

`pending` 为 `ConcurrentHashMap`，服务重启即丢；延迟为 5 秒、最大重试计数 3（首次推送不计 attempt，最多可产生首次+3 次 retry），超限写离线。MQ consumer 是并发消费，未指定按会话 key 的顺序消息。故可声明“局部 socket 写入顺序尽量保持”，不可声明全局、会话或端到端严格有序。

## 离线与冷热

离线键 `im:offline:{userId}`，member 是完整 JSON，score 是 `createdAt`；最多 1000 项，删除最低 score，无 TTL。`pullOffline` 用 `cursor+1` 作 score 下界，再用 MySQL `created_at` 补量；不删除 ZSet、没有已读 ACK/游标持久化，且同毫秒 score 会造成游标歧义。因此 Redis+MySQL **并存但未形成严格冷热分层闭环**。

## 节点失效与路由

Gateway 旧 `route:{userId}` 存地址且无 TTL；新 `im:route:{userId}` 存 serverId（24h TTL）。两套键互不协调。节点注册明确使用 ZK ephemeral node，宕机断会话后 child watch 可刷新 Gateway 节点列表；旧路由并未主动清理，新路由等待 TTL 或 client 失联后的 `channelInactive` 清理。客户端重连先调 Gateway `/offLine`（清旧键/登录集合），再 login；固定 10s 无指数退避/jitter。消息投到失效目标：local bus 会离线保存，RocketMQ `forward` send 失败直接抛异常且未捕获转离线。

## 风险优先级

1. 两套路由与登录状态键并存，故障时可能向不一致节点投递。
2. EventLoop 内存在 Redis/JDBC/MQ/JSON 阻塞调用，慢依赖可拖慢连接 I/O；`TIMServer#sendMsg` 还同步等待 5s。
3. ACK 缺少身份/接收方校验，Pending 不能跨节点/重启恢复。
4. 无 TCP 鉴权、限流、熔断、度量和追踪。
5. ZK 注册在 Netty bind 后异步发生，注册失败不影响已启动 TCP；销毁与 ephemeral 语义待确认。
