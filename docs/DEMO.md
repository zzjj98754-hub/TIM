# TIM 浏览器聊天演示

要求：JDK 8、Maven、Docker Desktop、Node.js 18+。

## 启动

```powershell
docker compose up -d redis zookeeper mysql rocketmq-namesrv rocketmq-broker
$env:TIM_SERVER_ID='im-server-1'; $env:TIM_NODE_ID='1'; $env:TIM_SERVER_HTTP_PORT='8081'; $env:TIM_SERVER_NETTY_PORT='9001'; $env:TIM_MQ_MODE='rocketmq'; ./mvnw.cmd spring-boot:run -pl tim-server
# 第二个终端
$env:TIM_SERVER_ID='im-server-2'; $env:TIM_NODE_ID='2'; $env:TIM_SERVER_HTTP_PORT='8082'; $env:TIM_SERVER_NETTY_PORT='9002'; $env:TIM_MQ_MODE='rocketmq'; ./mvnw.cmd spring-boot:run -pl tim-server
```

另开终端运行 `cd tim-web; npm install; npm run dev`，访问 http://localhost:5173。窗口 A 用 1001，窗口 B 用 1002；分别选择节点 1/2 即可演示同节点和跨节点单聊。发送后服务端返回 `MESSAGE_ACCEPTED`，接收端按真实 `messageId` 去重并回 ACK；断开 B 后发送，B 重连会收到离线消息。

检查：MySQL 查询 `im_message`、`message_delivery`；Redis 查看 `tim:route:user:<id>` 和 `im:offline:<id>`；RocketMQ topic 为 `TIM_NODE_MESSAGE`；节点日志分别来自两个 Maven 进程。停止并保留数据：`docker compose stop`。

验证命令：`./mvnw.cmd test`、`./mvnw.cmd package -DskipTests`、`docker compose config`。若 Docker 未运行，只能将 Compose 配置检查和 Maven 结果作为验证，不能宣称容器验收通过。
