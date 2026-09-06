# 离线消息

`im:offline:{userId}` 的 member 是 messageId，score 是接收用户维度单调递增的 deliveryCursor。`offline_cursor_sequence` 在数据库事务中按用户加行锁分配游标，`offline_message_index` 的 `(user_id,message_id)` 唯一约束确保重复消息复用原 cursor；正文和历史游标以 MySQL 为事实来源，Redis 只保留近期索引，并按最大条数和 TTL 裁剪。Redis 写入失败不会回滚已提交的 MySQL 离线索引。

客户端登录时携带上次确认游标，服务端按游标升序分页读取并在成功处理后接收 `OFFLINE:{cursor}` 确认；确认采用单调更新，Redis 缓存不足时回退 MySQL。相关实现位于 `MessageHistoryRepository`、`ReliableMessageService` 和客户端 `OfflineCursorStore`。
