# 离线消息

`im:offline:{userId}` 的 member 是 messageId，score 是接收用户维度单调递增的 deliveryCursor。正文和历史游标以 MySQL 为事实来源，Redis 只保留近期索引，并按最大条数和 TTL 裁剪。

客户端登录时携带上次确认游标，服务端按游标升序分页读取并在成功处理后接收 `OFFLINE:{cursor}` 确认；确认采用单调更新，Redis 缓存不足时回退 MySQL。相关实现位于 `OfflineMessageService`、`ReliableMessageService` 和 `OfflineCursorStore`。
