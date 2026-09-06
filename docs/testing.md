# 测试与验收

当前可在无外部中间件环境执行：

```bash
./mvnw -q test
./mvnw -q verify -DskipTests
docker compose config
```

测试覆盖协议拆包/粘包、认证身份、旧连接保护、Redis/MySQL 幂等、ACK 重试、离线游标与裁剪、普通群写扩散和超大群读扩散。`scripts/smoke-test.ps1` 会启动两个 TIM 节点，检查 Actuator，并验证离线幂等与跨节点群消息持久化读取；它仍不等价于真实 TCP 客户端或故障注入验收。
