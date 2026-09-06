# 简历描述与代码证据

| 描述 | 真实入口/证据 |
|---|---|
| Netty自定义协议、粘包半包 | `tim-common/.../ObjEncoder.java`、`ObjDecoder.java`、`TIMFrameCodecTest.java` |
| 认证Channel与旧连接保护 | `tim-server/.../TIMServerHandle.java`、`SessionSocketHolder.java`、`ConnectionSession.java` |
| ZooKeeper注册发现 | `RegistryZK.java`、`ZKit.java`、Gateway `ZKit.java` |
| 统一Redis路由 | `RedisRouteService.java`、Gateway `AccountServiceRedisImpl.java`，Key为 `tim:route:user:{id}`；Lua 原子维护 route/presence 与 session/epoch 清理 |
| 幂等与可靠投递 | `MessageHistoryRepository.java`、`OutboxRepository.java`、`ReliableMessageService.java` |
| 离线消息 | `offline_message_index`、`tim:offline:{userId}`，正文由 `im_message` 保存 |
| 群写/读扩散 | `GroupMessageService.java`、`im_group`、`group_message_inbox`、`group_member_cursor` |
| 节点群广播 | `RocketMqNodeMessageBus.java`、`GroupChannelPushService.java`、Topic `TIM_GROUP_BROADCAST` |
| 多节点部署 | `docker-compose.yml`、`Dockerfile.tim-server`、`scripts/smoke-test.*` |

边界：Docker Desktop daemon未运行，完整容器烟测尚未取得运行证据；当前项目仍使用Java 17，RocketMQ和MySQL集成需在Compose环境验证。
