# TIM 真实架构审计

一句话：TIM 是一个 Spring Boot 多模块教学型 IM 原型，使用 **Gateway HTTP 控制面 + Netty TCP 连接面 + ZooKeeper 节点发现 + Redis 路由/状态**；当前工作树新增了与旧 HTTP 推送并存的可靠消息演示链路。

## 阅读顺序

1. [系统拓扑图](diagrams/system-topology.html)、[协议 Pipeline](diagrams/protocol-pipeline.html)、[单聊时序图](diagrams/single-message-sequence.html)
2. [代码地图](codebase-map.md) 与 [证据索引](evidence-index.md)
3. [实现矩阵](implementation-matrix.md)、[可靠性分析](reliability-analysis.md)、[协议规范](protocol-spec.md)
4. [连接生命周期](diagrams/connection-lifecycle.html)、[跨节点路由](diagrams/cross-node-routing.html)、[ACK 重试](diagrams/ack-retry-lifecycle.html)、[离线数据流](diagrams/offline-storage-dataflow.html)、[群扩散](diagrams/group-dissemination.html)、[节点恢复](diagrams/node-failure-recovery.html)、[线程背压](diagrams/thread-pool-backpressure.html)。

## 面划分

控制面：Gateway 登录和 ZooKeeper 节点监听。连接面：client/server Netty pipeline、心跳、Channel 映射。消息面：旧 HTTP push 与新 `ReliableMessageService`/NodeMessageBus。存储面：Redis 帐号/两套路由/离线 ZSet，JDBC `im_message`，可选 RocketMQ。

## 十二条流程结论（摘要）

启动注册：Netty bind 后异步注册 ZooKeeper；连接绑定：TCP LOGIN 无 token，写本地映射和新 Redis key；协议：TIM1 18-byte header + Protostuff；心跳：30s server read idle / 60s client write idle；单聊：新 CHAT 走可靠服务，旧聊天仍走 Gateway HTTP；ACK：客户端自动 ACK，但跨节点/重启不闭环；顺序：未实现严格保证；离线：ZSet+JDBC 查询但无确认清理；故障：ZK 能刷新节点列表，路由脏数据靠 TTL/下线处理；群：仅 demo 服务的阈值式 partial 实现；线程：业务阻塞可能发生 EventLoop；安全稳定：无 JWT/Sentinel/Security/metrics。

## 真正生效的方案

注册中心是 **ZooKeeper**，不是 Nacos。默认登录路由是 **LoopHandle**，一致性哈希只是 CONFIG_ONLY。RocketMQ 代码在 `tim.mq.mode=rocketmq` 才装配，默认 local，故为 PARTIAL。新单聊有 ACK/重试/去重/离线的片段，但因内存 Pending、无客户端去重、无事务 outbox 与不一致路由，不构成生产级可靠性闭环。群聊读/写扩散有 demo 代码，但未接 TCP `GROUP_CHAT`，为 PARTIAL。

## 建议重点阅读的 15 个类

`TIMServer`, `TIMServerInitializer`, `TIMServerHandle`, `TIMClient`, `TIMClientHandle`, `ObjEncoder`, `ObjDecoder`, `RouteController`, `AccountServiceRedisImpl`, `ServerCache`, `RegistryZK`, `ReliableMessageService`, `RedisRouteService`, `RocketMqNodeMessageBus`, `GroupMessageService`。

面试建议顺序：先讲原始 Gateway/ZK/Redis/Netty 架构，再主动说明新可靠消息演示是并行补充；随后分层描述协议、连接、路由、存储；最后诚实讲清 ACK/顺序/大群/安全/观测的边界和改进路径。不要声称 Nacos、Sentinel、JWT、严格顺序或完整冷热分层已实现。

## 工具与验证

CodeBoarding 命令与 npm 包均不可用；Archify 仅发现无关 npm 同名包，未安装以免伪造工具使用。本交付用源码审计生成可点击的静态/交互 HTML-SVG 等价图。已执行 `./mvnw.cmd -pl tim-common test`：39 tests passed。未启动任何外部服务，未修改业务代码/配置。
