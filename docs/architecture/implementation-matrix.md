# 实现真实性矩阵

| 能力 | 状态 | 真实实现与限制 |
|---|---|---|
| Zookeeper 注册/发现 | CONFIRMED | `/im/servers/{serverId}` + Gateway child watch；没有 Nacos。ephemeral 创建语义待确认。|
| Gateway 登录与轮询选节点 | CONFIRMED | Redis 帐号校验，配置实际是 `LoopHandle`。|
| 一致性哈希选节点 | CONFIG_ONLY | `TreeMapConsistentHash` 有实现（2 虚拟节点），但 `app.route.way` 指向 Loop。|
| TCP bind、心跳、掉线 | CONFIRMED | 30s server read idle、60s client write idle；重连固定 10s。|
| TCP 鉴权 / JWT | MISSING | LOGIN 仅将 userId/userName 绑定，无 token 校验。|
| 新单聊接收、持久化、ACK | CONFIRMED | CHAT→SETNX→JDBC→路由→Channel→client ACK；外部依赖未启动验证。|
| ACK 后端闭环 | PARTIAL | Pending 只在实际 local Channel write 成功时加入；跨节点 target 的 Pending 属于目标节点，源节点仍为 PENDING；客户端未去重且 ACK 无身份校验。|
| 重试与离线 | PARTIAL | 内存 `pending` 每 1s 扫描、3 次/5s；重启丢失。离线 ZSet 无 TTL/ACK 删除，pull 仅查询。|
| MySQL 历史 | CONFIRMED | JDBC `im_message` 主键与按时间查询；非 MyBatis-Plus。|
| RocketMQ 跨节点 | PARTIAL | 真实 producer/consumer 代码与 topic/tag 存在，但默认 `tim.mq.mode=local`；无 outbox/事务。|
| 旧 HTTP 跨节点 | CONFIRMED (LEGACY) | Gateway 通过目标节点 HTTP `/sendMsg` 直接 socket write。|
| 普通群写扩散 | PARTIAL | Redis Set 成员，阈值 <500 后逐成员 `accept`；只由 demo HTTP 调用，未处理 `GROUP_CHAT` TCP。|
| 超大群读扩散 | PARTIAL | 单份 Redis ZSet 与 pull；不推送、无成员可见性/持久化/过期。|
| 顺序 | MISSING | 无 conversation 分区、RocketMQ 顺序消息或客户端重排；同一毫秒 ZSet score 也不形成严格序列。|
| 限流/熔断/降级 | MISSING | 无 Sentinel/令牌桶/熔断规则；local MQ 是开发 fallback，不是运行时降级策略。|
| Spring Security / RBAC | MISSING | POM 与代码均未见接入。|
| Trace / metrics | MISSING | 仅 SLF4J 日志；无 traceId、Micrometer、Prometheus。|
