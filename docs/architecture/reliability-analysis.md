# 可靠性与故障边界

## 单聊链路

`TIMClient` TCP `CHAT` → `TIMServerHandle` → 会话身份校验 → `ReliableMessageService.accept` → MySQL 消息、Delivery 和 Outbox 事务 → 事务提交后写 Redis 快速缓存 → RocketMQ 节点 topic（同节点可直接本地投递）→ 目标节点本地 Channel → 客户端处理成功后 ACK → 服务端更新投递状态；未 ACK 的消息由定时器有限次退避重试，最终进入离线索引。数据库租约是重启恢复依据，JVM Map 仅加速当前节点。

MySQL 是正文和最终幂等事实来源；Redis 只负责快速去重、路由和近期离线索引。Outbox Relay 负责补偿“数据库提交后、MQ 发送前”的窗口。重复 MQ 消费由消息/收件箱唯一约束防止重复落库。

## 离线语义

`im:offline:{userId}` member 是 messageId，score 是用户维度单调 deliveryCursor。客户端登录携带游标，服务端按游标升序分页读取正文；客户端处理成功后确认最大连续 cursor，服务端再删除/推进索引。Redis 缓存不足时从 MySQL 索引和正文回退，容量与 TTL 不影响历史正文。

## 路由与连接故障

路由唯一使用 `tim:route:user:{userId}` Hash，包含 nodeId/sessionId/epoch/route 并带 TTL。断线删除使用 Lua/条件匹配语义；旧 Channel 的断开事件不能删除新 Channel 的路由。ZooKeeper 临时节点消失后 Gateway 刷新节点快照；基础 Compose 健康检查已通过，Redis/MQ/ZK 故障注入和业务消息恢复仍需专用验收脚本验证。

## 群消息

普通群写入一份正文并批量生成唯一 inbox 索引；超大群仅保存正文和 groupSequence，成员通过 `group_member_cursor` 增量读取。群序号由数据库行锁串行分配，正文/Inbox/Outbox 同事务；Redis 大群索引失败不影响 MySQL 权威拉取。群广播使用 RocketMQ `BROADCASTING`，每个节点只处理自己的本地 Channel 集合。

## 已验证与未验证

源码级链路、单元测试、Maven 构建、Compose 静态配置及基础双节点健康检查已验证。跨节点私聊、广播消费、节点宕机恢复和故障注入尚未由自动化脚本证明。
