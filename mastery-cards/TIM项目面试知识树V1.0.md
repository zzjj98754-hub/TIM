# TIM 项目面试知识树 V1.0

> 数据来源：`D:\DOWNLOAD\TIM\TIM` 真实代码（Spring Boot 2.1.7 多模块 Maven，Netty + ZK + Redis + Protostuff + OkHttp）。
> 原则：每个技术点都有代码证据；标注「代码未实现」的内容不是项目事实，面试时不要当功能讲。

## 0. 全局知识树

```text
TIM 项目（6 个 Maven 模块）
│
├─ tim-client    命令行客户端
│   ├─ HTTP 登录拿服务器地址 → Netty 建连 → 发送 LOGIN 注册
│   ├─ 心跳（15s 写空闲发 ping）→ 断线 10s 定时重连
│   ├─ 收到消息丢回调线程池 → 异步本地落盘（收发都记）
│   └─ 命令模式：延迟消息（时间轮）/ 前缀搜索（字典树）/ 历史查询
│
├─ tim-server    Netty 长连接服务端
│   ├─ 主从 Reactor：boss 负责 accept，worker 负责读写
│   ├─ 自定义协议：4 字节长度前缀 + Protostuff 序列化
│   ├─ SessionSocketHolder：userId → Channel 并发映射
│   ├─ 心跳：读空闲 20s 触发事件，30s 无读关闭连接
│   └─ 下线：channelInactive → HTTP 通知 Gateway 清路由
│
├─ tim-gateway   无状态 HTTP 路由网关
│   ├─ 登录校验 → 路由算法选 server → TCP 探活 → Redis 存路由
│   ├─ ZK 服务发现：临时节点 + watch → Guava 本地缓存
│   ├─ 群聊：SCAN 全量在线路由 → 按用户逐个 HTTP 扇出
│   ├─ 私聊：查单用户路由 → HTTP 推送到目标 server
│   └─ Redis：账号映射 / 路由表 / 在线状态 Set
│
├─ tim-common    公共库
│   ├─ 协议编解码 / ProtostuffUtil（schema 缓存）
│   ├─ 路由算法：一致哈希（TreeMap / SortArrayMap）、轮询、随机
│   ├─ 数据结构：RingBufferWheel 时间轮、TrieTree 字典树
│   └─ ProxyManager：JDK 动态代理把接口变成 HTTP 调用
│
└─ tim-gateway-api / tim-server-api
    └─ Feign 风格接口 + VO（供 ProxyManager 动态代理）
```

一句话架构：客户端先走 HTTP 到 Gateway 登录，Gateway 从 ZK 缓存的服务器列表里按路由算法挑一台 IM-Server，把 `ip:nettyPort:httpPort` 返回给客户端；客户端再用 Netty 与该 IM-Server 建立长连接。发消息时消息先到 IM-Server，再由 IM-Server 调 Gateway 的 `/groupRoute` 或 `/p2pRoute`，Gateway 查 Redis 路由表，最后用 HTTP 把消息推到目标用户的 IM-Server，目标 IM-Server 通过本地 Channel 映射写给客户端。

---

## 1. tim-server —— Netty 长连接服务端

### 1.1 主从 Reactor 模型

- 代码证据：[TIMServer.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/server/TIMServer.java)
- 实现：两个 `NioEventLoopGroup` 分别作为 boss 和 work；`ServerBootstrap.group(boss, work).channel(NioServerSocketChannel.class)`，开启 `SO_KEEPALIVE`；`@PostConstruct` 启动，`@PreDestroy` 里 `shutdownGracefully()` 优雅停机。
- 设计原因：把「接受连接」和「读写数据」分给两组线程，accept 的负载不会拖慢已有连接的 IO；Netty 保证同一个 Channel 的所有操作都在同一个 EventLoop 串行执行，业务代码无需加锁。

面试问题：

- Netty 的 Reactor 模型是什么？boss 和 worker 各干什么？
- 为什么默认线程数是 `2 * CPU`？
- 为什么说一个 Channel 的操作是线程安全的？跨线程 `writeAndFlush` 会发生什么？
- 优雅停机 `shutdownGracefully` 做了什么？

回答关键词：

- 主从多 Reactor；NIO 多路复用（selector）；accept / IO 职责分离；EventLoop 串行化；任务队列提交；优雅停机。

潜在追问（真实代码弱点）：

- `TIMServerHandle.channelInactive()` 里同步调用 `RouteHandler.userOffLine()`，内部是 OkHttp 同步 HTTP 请求——这在 Netty 事件循环里是阻塞操作，会卡住该 EventLoop 上的所有连接。可以主动说：如果我来改进，会把下线通知放到业务线程池或异步客户端里。

### 1.2 自定义 TCP 协议（长度前缀 + Protostuff）

- 代码证据：[ObjEncoder.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/protocol/ObjEncoder.java)、[ObjDecoder.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/protocol/ObjDecoder.java)、[ProtostuffUtil.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/protocol/ProtostuffUtil.java)、[TIMReqMsg.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/protocol/TIMReqMsg.java)
- 实现：报文 = 4 字节 int 长度 + Protostuff 序列化字节；`ObjEncoder` 继承 `MessageToByteEncoder`，`ObjDecoder` 继承 `ByteToMessageDecoder`；半包时 `markReaderIndex/resetReaderIndex` 等待下次数据；`ProtostuffUtil` 用 `ConcurrentHashMap` 缓存 `RuntimeSchema`，避免每次序列化都反射。
- 消息体：`TIMReqMsg{requestId, reqMsg, type}`，type 只有三种：LOGIN=1、MSG=2、PING=3。
- 设计原因：IM 高频小消息，Protostuff 比 JSON 体积更小；长度前缀让接收端能确定一条完整消息的边界，解决粘包/半包。

面试问题：

- TCP 粘包、半包是什么？这个项目怎么解决的？
- 为什么用 Protostuff 而不是 JSON / Java 序列化？
- `ByteToMessageDecoder` 为什么能处理粘包？半包时做了什么？
- 编码器写长度用了什么字节序？如果恶意客户端声明一个超大长度会怎样？（代码里没有最大长度限制）

回答关键词：

- 粘包拆包；长度域；累积缓冲（cumulation）；半包等待；mark/reset；Protostuff；RuntimeSchema 缓存；体积小、吞吐高。

### 1.3 Channel 管理（SessionSocketHolder）

- 代码证据：[SessionSocketHolder.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/util/SessionSocketHolder.java)
- 实现：两个静态 `ConcurrentHashMap`——`userId → NioSocketChannel` 和 `userId → userName`；收到 LOGIN 消息时写入，`channelInactive` 或心跳超时时移除。
- 设计原因：Channel 映射会被 Netty 事件循环线程和 Tomcat HTTP 线程同时读写（推送入口是 HTTP `/sendMsg`），用 ConcurrentHashMap 免锁保证线程安全。

面试问题：

- 为什么用 ConcurrentHashMap 而不是 HashMap / Hashtable？
- 推送消息时怎么找到目标 Channel？找不到（离线）会怎样？
- `getUserId(channel)` 为什么是遍历？有什么问题？（O(n) 反查，可改用 `channel.attr()` 存 userId，代码里没有做）
- 一个 userId 能否多端登录？（Gateway 用 Redis Set 挡重复登录，代码里同一 userId 只维护一个 Channel）

回答关键词：

- userId→Channel 映射；ConcurrentHashMap 分段/无锁读；登录注册、下线移除；O(n) 反查；重复登录拦截。

### 1.4 心跳与半开连接检测

- 代码证据：[TIMServerInitializer.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/init/TIMServerInitializer.java)、[ServerHeartBeatHandlerImpl.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/kit/ServerHeartBeatHandlerImpl.java)、[NettyAttrUtil.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/util/NettyAttrUtil.java)
- 实现：服务端 `IdleStateHandler(20, 0, 0)`——20 秒没收到客户端任何数据触发 `READER_IDLE`；`userEventTriggered` 里检查 Channel attr 中记录的 `lastReadTime`，超过 `tim.heartbeat.time=30` 秒就 `userOffLine + close`。客户端收到 PING 时服务端回 pong 并更新读时间。
- 设计原因：TCP 本身感知不到对端断电/网络不可达（SO_KEEPALIVE 默认两小时才探测，太慢），必须应用层心跳；用 Channel 的 AttributeKey 存时间避免额外 Map。

面试问题：

- 为什么需要应用层心跳？TCP keepalive 不够吗？
- `IdleStateHandler(20, 0, 0)` 三个参数含义？
- 为什么「20s 空闲事件 + 30s 超时阈值」两层判断？
- 心跳包算不算“读数据”？会不会自己触发空闲事件？

回答关键词：

- 半开连接；TCP keepalive 默认 2h 不可靠；读空闲；双阈值；AttributeKey；防误杀。

### 1.5 下线清理链路

- 代码证据：[TIMServerHandle.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/handle/TIMServerHandle.java)、[RouteHandler.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/kit/RouteHandler.java)
- 实现：连接断开 → `channelInactive` → 从 SessionSocketHolder 找到 userId → `userOffLine`：删本地 session、通过 `ProxyManager` 调 Gateway `/offLine` 清 Redis 路由和在线状态、最后移除 Channel 映射；注释里明确提到“可能出现业务判断离线后再次触发 channelInactive”，所以先判 `userInfo != null`。
- 设计原因：谁持有连接谁负责感知断线；路由表的权威数据在 Redis，由 Gateway 统一清理，避免每个 server 直接操作 Redis 路由键。

面试问题：

- 客户端拔网线，服务端多久才能发现？（约 30s 级，依赖心跳超时）
- 为什么 server 不直接删 Redis，而要 HTTP 通知 Gateway？
- `channelInactive` 可能重复触发吗？代码怎么防？
- 这个链路里最明显的性能问题是什么？（同步 HTTP 阻塞 EventLoop）

回答关键词：

- 断连感知；路由清理；HTTP 通知；幂等判断；阻塞隐患。

### 1.6 HTTP 推送入口

- 代码证据：[IndexController.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/controller/IndexController.java)、`TIMServer.sendMsg()`
- 实现：Gateway 通过 HTTP POST `/sendMsg` 调进来，`TIMServer` 查 Channel 映射，`writeAndFlush(new TIMReqMsg(..., MSG))` 推给客户端；`ChannelFutureListener` 只打日志，不做重试。

面试问题：

- Tomcat 线程往 Netty Channel 写数据安全吗？为什么？
- 如果目标用户离线，推送会怎样？（打日志直接 return）
- 推送失败会重发吗？（不会，代码未实现重试/ACK）

回答关键词：

- writeAndFlush 线程安全；EventLoop 串行执行；离线忽略；无 ACK 无重发。

---

## 2. tim-gateway —— 无状态路由网关

### 2.1 登录与服务器分配

- 代码证据：[RouteController.java](D:/DOWNLOAD/TIM/TIM/tim-gateway/src/main/java/com/tuling/tim/gateway/controller/RouteController.java)、[AccountServiceRedisImpl.java](D:/DOWNLOAD/TIM/TIM/tim-gateway/src/main/java/com/tuling/tim/gateway/service/impl/AccountServiceRedisImpl.java)
- 实现：`/login` 流程 = Redis 校验账号密码（只有 userName 比对）→ `routeHandle.routeServer(serverCache.getServerList(), userId)` 选一台 server → `checkServerAvailable` 用原始 Socket TCP 连接探测 1s → 把 `ip:port:httpPort` 写入 Redis 路由表 → 返回给客户端。
- 设计原因：Gateway 只做“指路”，长连接下沉到 IM-Server，Gateway 自身无状态，可以 Nginx 负载均衡水平扩展。

面试问题：

- 为什么登录要返回服务器地址，而不是 Gateway 一直中转消息？
- 「无状态」指什么？多部署几台 Gateway 会有什么问题？
- 选完服务器为什么还要 TCP 探活？探活失败做了什么？（重建缓存 + 抛 SERVER_NOT_AVAILABLE）

回答关键词：

- 长连接下沉；地址下发；无状态水平扩展；探活兜底；缓存重建。

### 2.2 可插拔路由算法

- 代码证据：[RouteHandle.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/route/algorithm/RouteHandle.java)、[LoopHandle.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/route/algorithm/loop/LoopHandle.java)、[RandomHandle.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/route/algorithm/random/RandomHandle.java)、[AbstractConsistentHash.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/route/algorithm/consistenthash/AbstractConsistentHash.java)、[TreeMapConsistentHash.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/route/algorithm/consistenthash/TreeMapConsistentHash.java)
- 实现：`RouteHandle` 接口 + 三种实现；`BeanConfig` 通过反射按配置 `app.route.way` 创建，一致性哈希实现还能再配置 TreeMap 版或 SortArrayMap 版；轮询用 `AtomicLong` 保证线程安全，随机用 `ThreadLocalRandom`；一致性哈希用 MD5 截断成 long 做节点 hash，2 个虚拟节点，`TreeMap.tailMap(hash)` 顺时针找第一个节点，环尾回绕到第一个。
- 重要事实：`application.properties` 默认配置是 **LoopHandle 轮询**，一致性哈希是可选实现——面试要如实说。

面试问题：

- 轮询 / 随机 / 一致性哈希各自的适用场景？
- 一致性哈希原理？虚拟节点解决什么问题？
- 为什么不用取模？节点增删时取模和一致性哈希的区别？
- TreeMap 实现和 SortArrayMap 实现有什么不同？
- 为什么这里要用 `AtomicLong` / `ThreadLocalRandom`？

回答关键词：

- 哈希环；顺时针查找；虚拟节点均衡；最小重映射；MD5 32 位；tailMap；配置驱动可插拔。

### 2.3 ZK 服务注册与发现

- 代码证据：[RegistryZK.java](D:/DOWNLOAD/TIM/TIM/tim-server/src/main/java/com/tuling/tim/server/kit/RegistryZK.java)、[ZKit.java](D:/DOWNLOAD/TIM/TIM/tim-gateway/src/main/java/com/tuling/tim/gateway/kit/ZKit.java)、[ServerCache.java](D:/DOWNLOAD/TIM/TIM/tim-gateway/src/main/java/com/tuling/tim/gateway/cache/ServerCache.java)
- 实现：server 启动时建持久节点 `/route`，再注册**临时节点** `/route/ip-ip:9002:8082`（可配置 zkSwitch 关闭）；Gateway 启动线程 `subscribeChildChanges` 监听 `/route` 子节点变化，回调里 `cache.invalidateAll()` 全清后重放；列表为空时懒加载 `getAllNode`。
- 设计原因：临时节点随会话失效自动删除，server 宕机不用人工清理；watch 让 Gateway 秒级刷新本地缓存，避免每次登录都查 ZK。

面试问题：

- 为什么用临时节点而不是持久节点？
- 临时节点什么时候被删除？（会话超时/断开）
- watch 是一次性的吗？事件丢失怎么办？（真实代码没有重新拉取补偿，可作边界回答）
- Gateway 本地缓存为什么“先全删再全加”？为什么不直接增量更新？

回答关键词：

- 临时节点；会话（session）失效；watch 通知；本地缓存；弱一致；无补偿机制（真实边界）。

### 2.4 Redis 路由表与在线状态

- 代码证据：`AccountServiceRedisImpl`、[UserInfoCacheServiceImpl.java](D:/DOWNLOAD/TIM/TIM/tim-gateway/src/main/java/com/tuling/tim/gateway/service/impl/UserInfoCacheServiceImpl.java)、[Constant.java](D:/DOWNLOAD/TIM/TIM/tim-gateway/src/main/java/com/tuling/tim/gateway/constant/Constant.java)
- Key 设计（真实）：
  - `tim-account:{userId}` → userName（账号反查）
  - `userName` → `tim-account:{userId}`（用户名反查 userId，注册时避免重名）
  - `tim-route:{userId}` → `ip:nettyPort:httpPort`（路由表）
  - Set `login-status` → 在线 userId 集合
- 实现：`saveAndCheckUserLoginStatus` 用 `SADD` 返回值判断重复登录（返回 0 = 已存在 = REPEAT_LOGIN）；群聊 `loadRouteRelated` 用 `SCAN` 游标扫 `tim-route:*`（避免 KEYS 阻塞）；`UserInfoCacheServiceImpl` 有一层本地 `ConcurrentHashMap` 缓存用户名（注释明确：防内存撑爆，后续可换 LRU）。
- 真实边界：`offLine()` 里“删路由 + 删在线状态”是两次 Redis 操作，代码注释 `TODO 这里需要用lua保证原子性`——非原子。

面试问题：

- 为什么路由表用 Redis 而不是数据库 / MQ？
- `SCAN` 和 `KEYS` 区别？为什么群聊用 SCAN？
- 防重复登录为什么用 Set 的 SADD 就能做到原子？
- 下线为什么需要 Lua 保证原子性？不原子会怎样？（可能路由删了但在线状态还在，或反之）
- 本地缓存为什么用 ConcurrentHashMap？为什么不一直缓存？（内存上限，无 LRU）

回答关键词：

- KV 天然匹配路由；在线状态 Set；SADD 原子判定；SCAN 非阻塞；Lua 原子性；缓存淘汰缺失。

### 2.5 群聊 / 私聊扇出

- 代码证据：`RouteController.groupRoute / p2pRoute`、`AccountServiceRedisImpl.pushMsg`
- 实现：群聊 = `loadRouteRelated()` SCAN 出所有在线用户路由 → for 循环里逐个 `pushMsg`，过滤发送者自己，每个用户一次 HTTP POST 到其所在 server 的 `/sendMsg`；私聊 = 查单个 userId 路由，离线抛 `OFF_LINE`，然后把消息推过去。
- 设计原因：群聊没有群成员表，直接按“所有在线用户的路由”广播，实现最简单；server 收到 HTTP 后通过本地 Channel 映射写 Netty。

面试问题：

- 群聊消息完整路径是什么？（画一遍：Client → Netty → IM-Server → HTTP → Gateway → SCAN → 每个用户 HTTP → 目标 IM-Server → 写 Channel）
- 为什么按“用户”逐个推，而不是按“服务器”聚合推？有什么问题？（同一台 server 的多个用户会被重复请求；串行 HTTP 慢；失败只打日志不重试）
- 私聊目标离线返回什么？（OFF_LINE=7000）
- 如果用户量很大，这段代码的瓶颈在哪？（N 次 HTTP、SCAN 全量、无聚合）

回答关键词：

- 扇出；路由表全量扫描；按用户粒度推送；N 次 HTTP；无聚合无重试；离线码。

### 2.6 注册账号与统一异常

- 代码证据：`RouteController.registerAccount`、[ExceptionHandlingController.java](D:/DOWNLOAD/TIM/TIM/tim-gateway/src/main/java/com/tuling/tim/gateway/exception/ExceptionHandlingController.java)、[BaseResponse.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/res/BaseResponse.java)
- 实现：注册时 `userId = System.currentTimeMillis()`，用户名已存在则返回已有 userId；所有接口返回统一 `BaseResponse{code, message, reqNo, dataBody}`；`@ControllerAdvice + @ExceptionHandler` 把 `TIMException` 转成统一响应。

面试问题：

- 时间戳当 userId 有什么问题？（并发注册可能碰撞；多实例部署不唯一；代码未实现分布式 ID）
- 统一响应 + 全局异常处理的好处？

回答关键词：

- 高并发碰撞；集群唯一性；雪花 ID（讨论方向，非项目实现）；统一返回结构；ControllerAdvice。

---

## 3. tim-client —— 客户端

### 3.1 启动：先 HTTP 登录，再 Netty 建连

- 代码证据：[TIMClient.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/client/TIMClient.java)
- 实现：`start()` = `userLogin()`（HTTP 调 Gateway `/login` 拿 server 地址）→ `startClient()`（Bootstrap 连接 `ip:nettyPort`，`NioEventLoopGroup(1)` 单线程）→ `loginTIMServer()`（Netty 写 LOGIN 消息，服务端才登记 Channel）；连接/登录失败计数达到 `errorCount`（默认 5）就关闭客户端。
- 设计原因：客户端必须知道“去哪台 server”，所以先走 Gateway；建连后再发 LOGIN，让 server 把 userId 和 Channel 绑定。

面试问题：

- 为什么登录和建连分成两步？
- 为什么客户端用单线程 EventLoopGroup？
- 失败重试策略是什么？（计数，达到上限退出）

回答关键词：

- 先路由后建连；LOGIN 注册绑定；单 EventLoop；失败计数上限。

### 3.2 客户端心跳

- 代码证据：[TIMClientHandleInitializer.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/init/TIMClientHandleInitializer.java)、[TIMClientHandle.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/handle/TIMClientHandle.java)
- 实现：客户端 `IdleStateHandler(0, 15, 0)`，15 秒**写空闲**就主动发一条 PING；收到服务端 pong（type 也是 PING）时更新 readerTime；发心跳失败则关闭 Channel 触发重连。
- 设计原因：客户端负责“主动保活”，服务端只负责“读超时判断”，15s < 20s（服务端读空闲阈值），保证服务端总能收到数据。

面试问题：

- 为什么客户端用写空闲、服务端用读空闲？
- 心跳频率怎么和服务端配合？（15s 发送 vs 20s 触发 vs 30s 判定）

回答关键词：

- 写空闲定时发送；读空闲超时检测；频率匹配；避免误判离线。

### 3.3 断线重连

- 代码证据：[ReConnectManager.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/service/ReConnectManager.java)、[ReConnectJob.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/thread/ReConnectJob.java)、[ClientHeartBeatHandlerImpl.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/service/impl/ClientHeartBeatHandlerImpl.java)
- 实现：`channelInactive`（非主动退出）→ `ReConnectManager.reConnect()` 用 `ScheduledThreadPoolExecutor(1)` 每 10 秒执行一次重连任务 → `TIMClient.reconnect()`：先判断 Channel 是否已恢复；未恢复则调 Gateway `/offLine` 清旧路由 → 重新走 `start()` 登录建连 → 成功则 `shutdown()` 停止重连任务；`ContextHolder` 用 ThreadLocal 标记“重连中”，重连失败时不退出程序而是继续重试。
- 设计原因：服务器宕机/网络抖动后客户端必须重新登录才能拿到新 server 地址；旧路由必须先清掉，否则 Gateway 还会往死 server 推消息。

面试问题：

- 重连流程是什么？为什么重连前要调 offLine？
- 为什么用 `scheduleAtFixedRate` 固定 10s？
- 重连成功为什么 shutdown？任务停止后还能再重连吗？（代码里 `buildExecutor` 会判断 isShutdown 重建）
- ThreadLocal 在这里的作用？

回答关键词：

- 定时重连；先清路由；重新登录；任务终止；ThreadLocal 重连状态；防递归退出。

### 3.4 消息回调与业务线程池

- 代码证据：`TIMClientHandle.callBackMsg`、[BeanConfig.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/config/BeanConfig.java)、[MsgCallBackListener.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/service/impl/MsgCallBackListener.java)
- 实现：收到非心跳消息 → `callBackThreadPool.execute(...)` 异步回调（线程池：2 线程 + 有界队列 2，daemon 线程）→ 回调里把消息写本地日志；终端展示用 EmojiParser 转 Unicode。
- 设计原因：业务回调（落盘、格式化）不能在 Netty EventLoop 里做，避免阻塞后续消息读写。

面试问题：

- 为什么收到消息要丢线程池？直接在 EventLoop 里做会怎样？
- 线程池参数为什么这么小？队列满了会怎样？（有界 LinkedBlockingQueue，未配拒绝策略，默认 AbortPolicy 抛异常）

回答关键词：

- EventLoop 不阻塞；业务隔离；有界队列；daemon 线程；拒绝策略。

### 3.5 本地消息日志（异步落盘）

- 代码证据：[AsyncMsgLogger.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/service/impl/AsyncMsgLogger.java)
- 实现：`ArrayBlockingQueue(16)` + 单 daemon 消费线程；生产者 `put`，消费者 `take` 后按天写 `yyyyMMdd.log`（APPEND）；自己发的消息在 Scan 里 log，收到的消息在 MsgCallBackListener 里 log；`:q 关键字` 查询时同步遍历目录下所有文件做 contains 匹配。
- 设计原因：磁盘 IO 慢，异步队列削峰，不让消息处理线程等磁盘；按天分文件便于归档。

面试问题：

- 异步日志怎么实现？队列满时 `put` 会阻塞谁？
- 这个日志能当“消息可靠性”讲吗？（只能算本地历史记录，不是服务端可靠投递）
- 查询实现有什么问题？（同步读全部文件、内存全量 contains，数据量大时慢）

回答关键词：

- 生产者消费者；有界队列；daemon worker；按天分文件；全量扫描查询。

### 3.6 命令模式 + 数据结构应用

- 代码证据：[InnerCommandContext.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/service/InnerCommandContext.java)、[SystemCommandEnum.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/enums/SystemCommandEnum.java)、[DelayMsgCommand.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/service/impl/command/DelayMsgCommand.java)、[PrefixSearchCommand.java](D:/DOWNLOAD/TIM/TIM/tim-client/src/main/java/com/tuling/tim/client/service/impl/command/PrefixSearchCommand.java)
- 实现：`:命令` → 枚举映射到实现类名 → `SpringBeanFactory.getBean` 拿到命令对象执行；`:delay [msg] [秒]` 用 RingBufferWheel 延迟发送；`:pu 前缀` 把在线用户插入 TrieTree 做前缀搜索；`:q` 查历史日志。

面试问题：

- 命令模式的好处？（解耦、可扩展、统一入口）
- 延迟消息为什么用时间轮？简述 RingBufferWheel 内部机制。
- TrieTree 前缀匹配和遍历所有用户名 contains 比，优势在哪？

回答关键词：

- 开闭原则；枚举→Class→Spring Bean；时间轮；字典树共享前缀。

---

## 4. tim-common —— 公共组件深挖

### 4.1 RingBufferWheel 时间轮

- 代码证据：[RingBufferWheel.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/data/construct/RingBufferWheel.java)
- 实现细节（真实）：
  - 环形数组默认 64 槽，槽数必须是 2 的幂（构造时校验）；
  - `index = (delay + tick) & (size - 1)` 位运算取模；
  - `cycleNum = delay >> bitCount(size-1)` 计算跨几轮；
  - 一个后台 `TriggerJob` 线程每秒 tick+1，扫到 cycleNum=0 的任务丢业务线程池执行，否则 cycleNum-1 放回；
  - `addTask/cancel` 用 `ReentrantLock` 保护，`taskMap` 用 ConcurrentHashMap，`start` 用 CAS + AtomicBoolean 保证只启动一次；
  - `stop(false)` 会等队列清空（Condition await/signal）。

面试问题：

- 时间轮原理？为什么槽数要求 2 的幂？
- 延迟 100 秒的任务，64 槽时放在哪？cycleNum 是多少？
- 添加任务、取消任务复杂度？和 ScheduledThreadPoolExecutor 相比？
- 多个线程同时 addTask 安全吗？为什么？

回答关键词：

- 环形数组；位运算取模；跨轮计数；单消费者 tick；O(1) 添加；锁 + CAS + Condition。

### 4.2 TrieTree 字典树

- 代码证据：[TrieTree.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/data/construct/TrieTree.java)
- 实现：每个节点 52 个子节点（26 大写 + 26 小写），`isEnd` 标记单词结尾，insert / prefixSearch / 深度遍历 all；最大字符长度 16。

面试问题：

- 前缀搜索原理？为什么用 26*2 数组而不是 Map？
- 查询复杂度？空间开销？

回答关键词：

- 共享前缀；数组索引 char 偏移；isEnd；查询 O(长度)。

### 4.3 SortArrayMap（自定义有序 Map）

- 代码证据：[SortArrayMap.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/data/construct/SortArrayMap.java)
- 实现：Node 数组，扩容 1.5 倍，`sort()` 排序后 `firstNodeValue` 线性找第一个 `key >= hash` 的节点（代码是线性遍历，不是二分）。

面试问题：

- 它和 TreeMap 实现一致哈希的差异？（红黑树 logN vs 数组排序 + 线性查找）
- 这段代码哪里可以优化？（firstNodeValue 可改二分）

回答关键词：

- 有序数组；1.5 倍扩容；线性查找；TreeMap 红黑树对比。

### 4.4 ProxyManager 动态代理

- 代码证据：[ProxyManager.java](D:/DOWNLOAD/TIM/TIM/tim-common/src/main/java/com/tuling/tim/common/core/proxy/ProxyManager.java)
- 实现：JDK `Proxy.newProxyInstance` + `InvocationHandler`；调用接口方法时用反射把参数对象的字段塞进 JSON，OkHttp POST 到 `url + "/" + methodName`；约定：方法名 = HTTP 路径，只支持 0/1 个参数。
- 应用：server 调 Gateway（RouteApi）、Gateway 调 server（ServerApi）、client 调 Gateway（RouteApi），三处共用。

面试问题：

- 为什么定义接口就能发 HTTP？动态代理原理？
- 它和 Feign 有什么相似和不同？（Feign 思路的简化版，无负载均衡/熔断）
- 为什么限制单参数？

回答关键词：

- JDK 动态代理；InvocationHandler；反射字段转 JSON；约定路径；Feign 风格。

---

## 5. 高可用与消息可靠性（真实边界）

### 代码里已实现的可靠性手段

| 手段 | 代码证据 | 面试一句话 |
| --- | --- | --- |
| 服务注册发现 | ZK 临时节点 + Gateway watch | server 宕机，临时节点自动消失，Gateway 秒级更新本地缓存 |
| 服务不可用兜底 | `NetAddressIsReachable` TCP 探活 | 登录前 1s 探活，失败重建缓存并快速失败 |
| 连接保活 | 心跳 15s / 空闲 20s / 判定 30s | 半开连接能被回收，避免僵尸连接占资源 |
| 客户端自动重连 | ReConnectManager 10s 定时任务 | 断线后先清路由，再重新登录拿新地址 |
| 防重复登录 | Redis Set SADD 原子判定 | 同一 userId 只能一个在线会话 |
| 本地消息落盘 | AsyncMsgLogger | 收发消息异步写本地文件，支持关键字历史查询 |

### 代码里没有实现的功能（面试不要虚构）

- 消息 ACK / 失败重发 / 幂等去重：推送只加 `ChannelFutureListener` 打日志，无确认、无重试、无消息序号；
- 离线消息存储与补推：私聊目标离线直接返回 OFF_LINE，没有离线消息；
- 服务端消息持久化：只有客户端本地日志，服务端不落库；
- 下线两步删除原子性：`offLine()` 删路由 + 删在线状态非原子，代码 TODO 注明需 Lua；
- 限流：`StatusEnum` 里有 REQUEST_LIMIT 枚举，但没有限流实现代码；
- 全局唯一 ID：注册 userId 用 `System.currentTimeMillis()`，并发/多实例下有碰撞风险；
- ZK watch 事件丢失补偿、Redis 故障降级：均无代码。

### 面试话术模板

“TIM 是一套教学级分布式 IM：服务发现、路由、心跳、重连、本地日志这些可靠性手段是真实实现的；但消息 ACK、离线消息、原子下线这些没有做。如果面试官问怎么演进，我会先加消息序号 + ACK 重发，再用 Lua 保证下线清理原子性，最后做离线消息存储。”

---

## 6. 一条串讲链路（面试时按这个顺序讲）

1. 客户端启动 → HTTP 调 Gateway `/login`；
2. Gateway 用 Redis 校验账号，从 ZK 缓存列表选 server（默认轮询）→ TCP 探活 → 写 `tim-route:{userId}` → 返回地址；
3. 客户端 Netty 建连 → 发 LOGIN → server 把 userId→Channel 放进 SessionSocketHolder；
4. 客户端 15s 发一次心跳，server 20s 检查读空闲、30s 判定超时回收连接；
5. 发消息：私聊走 `/p2pRoute` 查单个路由；群聊走 `/groupRoute` SCAN 全量路由逐个 HTTP 扇出；
6. 目标 server 收到 `/sendMsg` → 本地查 Channel → writeAndFlush；
7. server 宕机 → ZK 临时节点删除 → Gateway 缓存刷新；客户端断线 → 10s 定时重连，先 offLine 清路由再重新登录；
8. 主动下线 / 心跳超时 → server 通知 Gateway 清 Redis 路由和在线状态。

## 7. 最容易被追问的 5 个真实弱点（提前准备好）

1. `userOffLine` 在 Netty 事件循环里同步发 HTTP —— 阻塞 EventLoop；
2. 群聊按用户逐个串行 HTTP 扇出 —— 同服务器重复请求、延迟放大；
3. `offLine` 两步 Redis 操作非原子 —— 代码 TODO 已自认需要 Lua；
4. `userId = System.currentTimeMillis()` —— 并发/集群碰撞；
5. ZK watch 无事件丢失补偿、默认路由是轮询而非一致性哈希 —— 配置与文档要区分清楚。
