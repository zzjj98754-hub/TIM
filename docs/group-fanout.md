# 群聊扩散

发送群消息前以 MySQL `group_member` 为权限和成员规模事实来源。`GroupFanoutStrategySelector` 统一选择策略：成员数小于 `tim.group.write-fanout-limit` 时写入一份正文并生成成员 inbox 索引；达到或超过阈值时只保存 `group_message` 正文和 `group_member_cursor`，成员按 groupSequence 增量读取。序号在事务内锁定 `group_sequence` 行，多节点不会依赖 JVM 锁。

群广播使用 RocketMQ `BROADCASTING` 消费模式，每个 TIM 节点独立接收 `TIM_GROUP_BROADCAST`，只遍历本节点维护的 `groupId -> Channel` 集合，不逐用户查询 Redis。策略和持久化入口位于 `GroupMessageService`、`WriteFanoutStrategy`、`ReadFanoutStrategy`。
