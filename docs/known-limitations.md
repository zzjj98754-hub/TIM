# 已知限制

- 当前环境 Docker CLI 可用但 Docker Linux daemon 不可连接，因此 MySQL、Redis、ZooKeeper、RocketMQ 和双节点 Compose 运行态尚未验证。
- `tim.mq.mode=local` 是无中间件开发回退；跨节点 RocketMQ 验收必须使用 `rocketmq` 模式并在 Compose 中验证。
- 群广播采用每节点广播消费，节点数量很大时会产生无效消费；这是当前实现边界。
- 端到端跨两个真实 TCP 客户端、MQ 重复消费和故障注入仍需在可用 Docker 环境执行，不能由单元测试替代。
