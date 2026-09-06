# 群聊扩散

发送群消息前校验 `group_member`。成员数不超过 `tim.group.write-fanout-limit` 时写入一份正文并生成成员 inbox 索引；超过阈值时只保存 `group_message` 正文和 `group_member_cursor`，成员按 groupSequence 增量读取。

群广播使用 RocketMQ `BROADCASTING` 消费模式，每个 TIM 节点独立接收 `TIM_GROUP_BROADCAST`，只遍历本节点维护的 `groupId -> Channel` 集合，不逐用户查询 Redis。策略和持久化入口位于 `GroupMessageService`、`WriteFanoutStrategy`、`ReadFanoutStrategy`。
