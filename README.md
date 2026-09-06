## 介绍

`TIM(TULING-IM)` 一款面向开发者的 `IM(即时通讯)`系统；同时提供了一些组件帮助开发者构建一款属于自己可水平扩展的 `IM` 。

借助 `TIM` 你可以实现以下需求：

- `IM` 即时通讯系统。
- 适用于 `APP` 的消息推送中间件。

## 系统架构

![](http://assets.processon.com/chart_image/611bb33a1efad412479f7157.png)

- `TIM` 中的各个组件均采用 `SpringBoot` 构建。
- 采用 `Netty` 构建底层通信。
- `Redis` 存放账号、在线状态、统一用户路由、幂等键和近期离线索引；用户路由只由认证后的 Netty Session 写入。
- `Zookeeper` 用于 `IM-server` 服务的注册与发现。
- 认证这块逻辑只做了简单实现

### tim-server

`IM` 服务端；用于接收 `client` 连接、消息透传、消息推送等功能。

**支持集群部署。**

### tim-gateway

消息路由网关；用于处理消息路由、消息转发、用户登录、用户下线以及一些运营工具（获取在线用户数等）。

### tim-client

`IM` 客户端；给用户使用的消息终端，一个命令即可启动并向其他人发起通讯（群聊、私聊）。

## 流程图

![](http://assets.processon.com/chart_image/611bb33a1efad412479f7157.png)

- 客户端向 `gateway` 发起登录。
- 登录成功从 `Zookeeper` 中选择可用 `IM-server` 返回给客户端；客户端随后建立 TCP LOGIN，只有认证成功的 Netty Session 才会把用户路由写入 Redis。
- 客户端向 `IM-server` 发起长连接，成功后保持心跳。
- 客户端下线时通过 `gateway` 清除状态信息。

## 快速启动

当前仓库已将可靠单聊、离线消息和群扩散接入统一 TCP 主链路，并提供双节点 Compose 验收基线。

### 基础设施

推荐直接启动完整依赖和两个 TIM 节点：

```bash
docker compose up -d --build
```

默认端口如下：

- `Redis`: `6379`
- `Zookeeper`: `2181`
- `tim-server` HTTP: `8082`
- `tim-server` Netty: `9002`
- `tim-gateway` HTTP: `8090`
- `tim-client` HTTP: `8003`

如果宿主机 3306 已被占用，可将 MySQL 发布端口改为备用端口，例如
PowerShell 中设置 `$env:TIM_MYSQL_PORT='13306'`；Compose 内部连接仍使用
`mysql:3306`。

### 本地运行

如果需要本地调试单个模块，先确保 Redis、MySQL、ZooKeeper 和 RocketMQ 可用，再按以下顺序启动：

```bash
./mvnw spring-boot:run -pl tim-server
./mvnw spring-boot:run -pl tim-gateway
./mvnw spring-boot:run -pl tim-client
```

Windows PowerShell 使用 `./mvnw.cmd`。启动客户端前需要在
`tim-client/src/main/resources/application.properties` 设置演示用户信息。

### 部署 IM-server(tim-server)

直接运行TIMServerApplication.java

### 部署网关服务器(tim-gateway)

直接运行GatewayApplication.java
> tim-gateway 本身就是无状态，可以部署多台；使用 Nginx 代理即可。

### 启动客户端(tim-client)

直接运行TIMClientApplication.java

### 接口

#### 注册账号

```shell
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{
  "reqNo": "13766668890",
  "timeStamp": 0,
  "userName": "wangwu"
}' 'http://路由服务器:8090/registerAccount'
```

从返回结果中获取 `userId`,写入客户端项目tim-client的配置文件里

```json
{
  "code": "9000",
  "message": "成功",
  "reqNo": null,
  "dataBody": {
    "userId": 1633614560039,
    "userName": "wangwu"
  }
}
```

## 客户端内置命令

| 命令 | 描述|
| ------ | ------ | 
| `:q!` | 退出客户端| 
| `:olu` | 获取所有在线用户信息 | 
| `:all` | 获取所有命令 | 
| `:q [option]` | 【:q 关键字】查询聊天记录 | 
| `:ai` | 开启 AI 模式 | 
| `:qai` | 关闭 AI 模式 | 
| `:pu` | 模糊匹配用户 | 
| `:info` | 获取客户端信息 |

### 聊天记录查询

使用命令 `:q 关键字` 即可查询与个人相关的聊天记录。

> 客户端聊天记录默认存放在 `/opt/logs/tim/`，所以需要这个目录的写入权限。也可在启动命令中加入 `--tim.msg.logger.path = /自定义` 参数自定义目录。

### 前缀匹配用户名

使用命令 `:pu prefix` 可以按照前缀的方式搜索用户信息。

> 该功能主要用于在移动端中的输入框中搜索用户。

### 群聊/私聊

#### 群聊

群聊通过认证后的 TCP `GROUP_CHAT` 帧发送；普通群使用写扩散，超大群使用读扩散，在线成员由各节点本地 Channel 集合批量推送。

#### 私聊

私聊首先需要知道对方的 `userID` 才能进行。

输入命令 `:olu` 可列出所有在线用户。

接着使用 `userId::消息内容` 的格式即可发送私聊消息。

对方客户端会在成功处理后发送 ACK；未 ACK 时服务端按退避策略重试，客户端按 messageId 去重。

## 分布式可靠消息 Demo

新增的学习主链路为：

```text
TCP CHAT -> Redis 路由 -> RocketMQ（或本地降级总线）-> 目标节点 Channel
         -> 客户端 ACK -> 服务端更新 MySQL 状态
```

帧格式为 `magic(4) + version(1) + type(1) + requestId(8) + bodyLength(4) + body`。`LengthFieldBasedFrameDecoder` 负责处理粘包、半包，消息体仍使用 Protostuff。客户端收到 `CHAT` 自动 ACK；服务端以 Redis `SETNX` 快速去重、MySQL `im_message.message_id` 唯一键最终防重，超时最多重试 3 次，随后进入 Redis ZSet 离线缓存。

### 启动两个节点

```powershell
docker compose up -d redis zookeeper mysql rocketmq-namesrv rocketmq-broker
./mvnw.cmd spring-boot:run -pl tim-gateway
$env:TIM_SERVER_ID='im-server-1'; $env:TIM_NODE_ID='1'; $env:TIM_SERVER_HTTP_PORT='8081'; $env:TIM_SERVER_NETTY_PORT='9001'; $env:TIM_MQ_MODE='rocketmq'; ./mvnw.cmd spring-boot:run -pl tim-server
# 在第二个终端运行：
$env:TIM_SERVER_ID='im-server-2'; $env:TIM_NODE_ID='2'; $env:TIM_SERVER_HTTP_PORT='8082'; $env:TIM_SERVER_NETTY_PORT='9002'; $env:TIM_MQ_MODE='rocketmq'; ./mvnw.cmd spring-boot:run -pl tim-server
```

没有 RocketMQ 时将 `TIM_MQ_MODE` 设为 `local`。它保持相同投递接口，适合单 JVM 学习 ACK、重试及离线流程。MQ 模式使用 `TIM_NODE_MESSAGE` topic，并以目标 `serverId` 作为 tag 消费。

### HTTP 演示

先使用原有 `/registerAccount` 注册两个用户，并让两个客户端登录。然后可执行：

```powershell
Invoke-RestMethod http://localhost:8081/demo/messages -Method Post -ContentType application/json -Body '{"fromUserId":1001,"toUserId":1002,"content":"hello"}'
Invoke-RestMethod 'http://localhost:8082/demo/offline/1002?cursor=0&limit=20'
Invoke-RestMethod 'http://localhost:8082/demo/offline/1002/ack?cursor=20' -Method Post
Invoke-RestMethod http://localhost:8081/demo/groups/9/members/1002 -Method Put
Invoke-RestMethod http://localhost:8081/demo/groups/9/messages -Method Post -ContentType application/json -Body '{"fromUserId":1001,"content":"group hello"}'
```

成员少于 `tim.group.write-fanout-limit`（默认 500）时，群聊按成员写扩散；超过阈值时只追加群消息 ZSet，使用 `/demo/groups/{groupId}/messages/{userId}` 按游标读取。离线缓存容量由 `tim.offline.max-size` 控制（默认 1000）。

离线接口返回 `messageId`、`deliveryCursor` 和 `body`。客户端处理完本页中最大的连续游标后，调用 `/demo/offline/{userId}/ack?cursor={deliveryCursor}`，服务端才裁剪 Redis 离线索引；Redis 索引缺失时会按同一游标从 MySQL 回退读取。

推荐阅读顺序：`ObjEncoder/ObjDecoder` → `TIMServerHandle` → `ReliableMessageService` → `RedisRouteService` / `RocketMqNodeMessageBus` → `GroupMessageService`。Netty 负责连接、协议和心跳；ZooKeeper 注册/发现节点；Redis 保存路由、在线、去重和近期离线数据；RocketMQ 转发跨节点消息；MySQL 保存历史及最终幂等约束。

### 当前改造后的权威链路

认证后的 TCP Channel 绑定用户、sessionId 和 epoch；路由统一保存于
`tim:route:user:{userId}` Hash。CHAT 会覆盖客户端伪造的 senderId，先写
`im_message` 和 `outbox_event`，再由定时 Relay 投递到目标节点。离线正文只以
MySQL 为事实来源，Redis `tim:offline:{userId}` 只保存 messageId 和用户递增游标。
群消息使用 `im_group`、`group_member`、`group_message` 和
`group_message_inbox`；RocketMQ 的 `TIM_GROUP_BROADCAST` 使用广播消费模式，
节点只推送本地 `groupId → Channel集合`。

Gateway 的 `/p2pRoute` 和 `/groupRoute` 仅保留为明确返回弃用提示的兼容接口，
不会再执行逐用户 HTTP 推送；单聊和群聊必须通过认证后的 Netty `CHAT`/
`GROUP_CHAT` 帧进入服务端消息链路。

### 验收

先执行 `./mvnw.cmd package`，再执行 `scripts/smoke-test.ps1`（Linux 使用
`bash scripts/smoke-test.sh`）。Smoke脚本会检查Compose服务、构建两个TIM节点并
访问 `/actuator/health`，并验证离线消息幂等和跨节点群消息持久化读取。如果Docker daemon未启动，只能执行 `docker compose config`
和 Maven 测试，不能宣称容器验收通过。
