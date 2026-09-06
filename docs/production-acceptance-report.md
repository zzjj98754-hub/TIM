# TIM 生产化改造验收报告

## 已取得证据

- `./mvnw.cmd -q test`：通过。
- `./mvnw.cmd -q -pl tim-server -am -Dtest=TIMServerHandleTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `docker compose config`：通过，包含 MySQL、Redis、ZooKeeper、RocketMQ NameServer、RocketMQ Broker、`tim-node-1`、`tim-node-2`。
- `git diff --check`：通过。
- `TIM_MYSQL_PORT=13306 ./scripts/smoke-test.ps1`：通过；双 TIM 节点和 Redis、MySQL、ZooKeeper、RocketMQ NameServer/Broker 健康检查均通过，两个 `/actuator/health` 均返回成功。

## 运行态边界

基础 Compose 启动和健康检查已通过。烟测使用 `TIM_MYSQL_PORT=13306` 避开宿主机 3306 占用；Flyway V1–V3 在空 MySQL 卷上成功执行。仍未将跨节点私聊、群广播业务消息、节点宕机恢复写成已通过，因为现有 smoke 脚本只验证基础设施和 Actuator：

```powershell
./scripts/smoke-test.ps1
```

该脚本会启动 Compose、检查两个节点的 `/actuator/health`，结束时关闭 Compose。中间件级跨节点消息、离线重连和故障恢复仍需在脚本基础上继续做端到端验证。

## 当前结论

核心代码链路、自动化测试和基础 Compose 运行态已验证；跨节点业务故障注入仍需补充专用验收脚本。
