# TIM 生产化改造验收报告

## 已取得证据

- `./mvnw.cmd -q test`：通过。
- `./mvnw.cmd -q -pl tim-server -am -Dtest=TIMServerHandleTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `docker compose config`：通过，包含 MySQL、Redis、ZooKeeper、RocketMQ NameServer、RocketMQ Broker、`tim-node-1`、`tim-node-2`。
- `git diff --check`：通过。

## 运行态边界

本环境 Docker CLI 存在，但 Docker Linux daemon 的 named pipe 不可连接；因此没有将双节点启动、Actuator、Redis、MySQL、ZooKeeper、RocketMQ、跨节点私聊或群广播写成“已通过”。可用 Docker 环境执行：

```powershell
./scripts/smoke-test.ps1
```

该脚本会启动 Compose、检查两个节点的 `/actuator/health`，结束时关闭 Compose。中间件级跨节点消息、离线重连和故障恢复仍需在脚本基础上继续做端到端验证。

## 当前结论

核心代码链路和自动化单元测试已落地，配置与文档已对齐；最终的多节点运行态验收受 Docker daemon 环境阻塞，不能替代性宣称完成。
