# TIM 代码地图

| 模块 | 入口/核心类 | 职责 |
|---|---|---|
| `tim-common` | `ObjEncoder`, `ObjDecoder`, `ProtostuffUtil` | 自定义帧协议、序列化和公共模型 |
| `tim-gateway` | `RouteController`, `ServerCache`, `ZKit` | 登录、节点选择和 ZooKeeper watch；旧推送接口仅返回弃用响应 |
| `tim-server` | `TIMServer`, `TIMServerHandle`, `SessionSocketHolder` | Netty Reactor、认证会话、本地用户/群 Channel 映射 |
| `tim-server` | `ReliableMessageService`, `OutboxRepository`, `RocketMqNodeMessageBus` | 幂等、历史消息、Outbox、MQ 转发、ACK/重试 |
| `tim-server` | `OfflineMessageService`, `GroupMessageService` | 离线游标、MySQL 回退、群写扩散和读扩散 |
| `tim-client` | `TIMClient`, `TIMClientHandle`, `ReConnectManager` | 登录、心跳、ACK、离线游标和指数退避重连 |
| `*-api` | `RouteApi`, `ServerApi` | Gateway/server HTTP 契约 |

## 真实数据面

`TIMClient` 的 TCP `CHAT/GROUP_CHAT` → `TIMServerHandle` → 认证会话身份覆盖 senderId → `ReliableMessageService` → MySQL + Outbox → 本地 Channel 或 `RocketMqNodeMessageBus` → 目标节点本地 Channel → 客户端成功处理后 ACK。旧 HTTP `/p2pRoute`、`/groupRoute`、`/sendMsg` 已不再调用 socket push。

## 线程与连接

Netty boss/worker 负责网络 I/O；`BeanConfig` 提供有界 `ArrayBlockingQueue` 业务线程池，Redis/JDBC/MQ 工作在业务线程。队列满时使用显式拒绝策略并计数，不使用 `CallerRunsPolicy` 将阻塞任务带回 EventLoop。Actuator/Micrometer 暴露线程池 active、queue、pool、completed、rejected 指标。

`SessionSocketHolder` 使用线程安全的 `userId -> Channel`、`Channel -> ConnectionSession` 和 `groupId -> Channel集合`；新连接替换旧连接，断开清理使用比较删除和 session/epoch 匹配，避免旧连接误删新路由。

## 验证边界

协议、会话、幂等、ACK、离线和群策略有自动化测试；Redis、MySQL、ZooKeeper、RocketMQ 的双节点运行态需要 Docker daemon 可用后由 `scripts/smoke-test.*` 和端到端客户端继续验证。
