# TIM 架构证据索引

审计基线：2026-08-31 当前工作树（包含用户未提交的可靠消息新增代码）；`git status` 在审计前已显示大量既有修改，本文不将其归因于本次工作。状态仅表示代码接入程度，不代表已经端到端运行过。

| ID | 证据位置 | 关键符号 / 事实 | 状态 |
|---|---|---|---|
| E-BOOT | `tim-server/.../server/TIMServer.java` | `@PostConstruct start()` 绑定 Netty；`@PreDestroy destroy()` 关闭 boss/worker | CONFIRMED |
| E-ZK | `tim-server/.../TIMServerApplication.java`, `kit/RegistryZK.java`; `tim-gateway/.../kit/ZKit.java` | CommandLineRunner 创建 `/im/servers/{serverId}`；Gateway 订阅 children | CONFIRMED |
| E-LOGIN | `tim-gateway/.../controller/RouteController.java#login`; `service/impl/AccountServiceRedisImpl.java` | HTTP 登录校验 Redis 帐号、选节点、写旧 `route:{userId}` | CONFIRMED |
| E-BIND | `tim-server/.../handle/TIMServerHandle.java#channelRead0`; `route/RedisRouteService.java` | TCP LOGIN 写 `SessionSocketHolder`，写新 `im:route:{userId}` / `im:online:{userId}`（24h TTL） | CONFIRMED |
| E-FRAME | `tim-common/.../protocol/ObjEncoder.java`, `ObjDecoder.java`; `TIMFrameCodecTest.java` | TIM1 / version 1 / 18-byte header；LengthField 参数验证测试通过 | CONFIRMED |
| E-HEART | server/client `*Initializer.java`, `*Handle.java` | server read-idle 30s；client write-idle 60s；PING/PONG | CONFIRMED |
| E-LEGACY | Gateway `RouteController#p2pRoute/groupRoute`; `AccountServiceRedisImpl#pushMsg`; server `TIMServer#sendMsg` | 旧 HTTP 路由 + 目标节点本地 Channel push，未进入 ACK/持久化 | CONFIRMED (LEGACY 路径) |
| E-RMS | `tim-server/.../message/ReliableMessageService.java` | CHAT 接入、SETNX 去重、存储、路由、Pending、定时重试、离线 | CONFIRMED |
| E-HISTORY | `message/MessageHistoryRepository.java`; `resources/schema.sql` | JDBC 写 `im_message`，`message_id` 主键，状态更新/历史查询 | CONFIRMED |
| E-MQ | `mq/RocketMqNodeMessageBus.java`, `LocalNodeMessageBus.java`; properties `tim.mq.mode=local` | RocketMQ 实现存在但默认 local；topic `TIM_NODE_MESSAGE`、tag=serverId | PARTIAL |
| E-GROUP | `group/GroupMessageService.java`; `controller/DemoMessageController.java` | <500 成员调用 `messages.accept`；大群仅 ZSet+pull；HTTP 演示入口 | PARTIAL |
| E-CHASH | `tim-common/.../consistenthash/*`; Gateway `BeanConfig#buildRouteHandle`; gateway properties | 实现有 2 虚拟节点；实际配置 `LoopHandle`，故登录未用一致性哈希 | CONFIG_ONLY |
| E-SEC | root/server/gateway POM 与全仓检索 | 未找到 Spring Security、JWT、Sentinel、Nacos、MyBatis-Plus、指标/trace 接入 | MISSING |

图表节点中的 `E-*` 链接均可回溯到本页。路径中的 `...` 代表 `src/main/java/com/tuling/tim/...`。
