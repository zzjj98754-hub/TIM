# 部署与启动

静态检查：

```bash
./mvnw test
./mvnw verify -DskipTests
docker compose config
```

运行完整环境：

```bash
docker compose up -d --build
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
docker compose down
```

Compose 包含 MySQL、Redis、ZooKeeper、RocketMQ NameServer、RocketMQ Broker 和两个 TIM 节点。Redis、MySQL、ZooKeeper、NameServer、Broker 和 TIM 节点均配置健康检查，TIM 节点只在依赖服务健康后启动。必须先确认 Docker daemon 正常；本仓库当前已记录的环境事实是 Docker Linux daemon 不可用，因此运行态命令尚未取得通过证据。
