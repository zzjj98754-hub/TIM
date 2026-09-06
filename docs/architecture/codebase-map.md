# TIM 代码地图

## 模块与当前职责

| 模块 | 入口/核心类 | 当前结论 |
|---|---|---|
| `tim-common` | `ObjEncoder`, `ObjDecoder`, `ProtostuffUtil`, 路由算法 | 帧协议与工具；一致性哈希实现可用但默认未被选择。|
| `tim-gateway` | `GatewayApplication`, `RouteController`, `ServerCache` | HTTP 登录/旧路由控制面；从 ZooKeeper 监听节点。|
| `tim-server` | `TIMServerApplication`, `TIMServer`, `TIMServerHandle` | Netty 长连端点；含新可靠消息、组消息、MQ 适配层。|
| `tim-client` | `TIMClient`, `TIMClientHandle` | CLI 客户端，先 HTTP login，再 TCP LOGIN；接收 CHAT 自动 ACK。|
| `*-api` | `RouteApi` / `ServerApi` 与 VO | HTTP 契约，非 RPC 框架。|

## 两条并存链路

1. **旧路径（CONFIRMED，作为 legacy）**：客户端命令/HTTP Gateway → `RouteController#p2pRoute` 或 `groupRoute` → `AccountServiceRedisImpl#pushMsg` HTTP 调用目标 server `/sendMsg` → `TIMServer#sendMsg` → `SessionSocketHolder` socket write。它把 socket write 成功当作成功，不持久化、不 ACK、不重试。
2. **新路径（CONFIRMED，但接入面不统一）**：客户端 `TIMClient#sendChat` 的 TCP `CHAT` → `TIMServerHandle` → `ReliableMessageService#accept`。`DemoMessageController` 也可直接调用 `accept`。它不经过 Gateway 旧路由 API。

因此不能把二者合并描述为同一个“主链路”。详见 [实现矩阵](implementation-matrix.md) 和 [可靠性分析](reliability-analysis.md)。

## 启动与节点

`TIMServer` 是 Spring Bean，`@PostConstruct` 先 bind Netty（默认 TCP 9002），而 `TIMServerApplication#run` 随后另起 `registry-zk` 线程注册 ZK。`NioEventLoopGroup()` 未显式配置线程数，Netty 使用其默认值；没有独立业务线程池。`ZKit#createNode(path,data)` 使用 `zkClient.createEphemeral`，所以会话断开时 ZK 会自动移除节点（CONFIRMED）；`@PreDestroy` 仅关闭 EventLoop，未见显式主动删除节点。

Gateway 通过 `ServerListListener` 注册 child listener，`ServerCache#updateCache` 读取 znode JSON 数据取得 `host:tcpPort:httpPort`。控制面是 ZooKeeper，不是 Nacos（E-ZK）。

## 线程与连接

| 位置 | 线程/执行面 | 风险 |
|---|---|---|
| server boss / worker | `NioEventLoopGroup()` 默认线程数 | 未配置、无度量；`channelRead0` 直接 JSON、Redis/JDBC/MQ 调用，可能阻塞 EventLoop。|
| Spring `@Scheduled` | Spring 默认调度器（未显式 Bean） | 扫描 `pending`，内存状态重启丢失。|
| client callback | 固定 core=max=`tim.callback.thread.pool.size`，有界队列 | 默认拒绝策略 AbortPolicy；满时异常回到 EventLoop。|
| client reconnect | 1 线程 `ScheduledThreadPoolExecutor` | 固定 10 秒、无退避和 jitter；可能形成惊群。|

`SessionSocketHolder` 用两个 `ConcurrentHashMap` 保存 `userId→NioSocketChannel` 与用户名。重复 TCP LOGIN 直接覆写；旧连接 `channelInactive` 会按 userId 删除新映射，故存在旧连接误删新会话风险（E-BIND）。
