# 自定义 TCP 协议规范（代码实证）

状态：CONFIRMED。来源：[E-FRAME](evidence-index.md#e-frame)。

| 顺序 | 字段 | 宽度 | 编码/含义 |
|---:|---|---:|---|
| 1 | `magic` | 4 B | big-endian int `0x54494D31`（`TIM1`）|
| 2 | `version` | 1 B | `1` |
| 3 | `type` | 1 B | `LOGIN=1`, `MSG=2`, `PING=3`, `CHAT=4`, `ACK=5`, `OFFLINE_PULL=6`, `GROUP_CHAT=7` |
| 4 | `requestId` | 8 B | long；CHAT 出站实际塞 `Long.parseLong(messageId)`；ACK 本身 `0`，正文携带 messageId |
| 5 | `bodyLength` | 4 B | 仅 body 长度 |
| 6 | `body` | N B | Protostuff 序列化的 `TIMReqMsg`；其 `reqMsg` 内又可能是 JSON `ChatMessage` |

Header 固定 **18 B**。Pipeline 顺序：`IdleStateHandler` → `LengthFieldBasedFrameDecoder(1048576,14,4,0,0)` → `ObjEncoder` → `ObjDecoder` → 业务 Handler。长度字段偏移 14，表示 body；decoder 包含整帧（18+N）。编码/解码都以 Netty 默认大端序运行。

`ObjDecoder` 对 magic、version、负值/超过 1 MiB、以及剩余字节不等于 bodyLength 的帧关闭连接。它没有独立的“未知 type”拒绝分支，未知 type 会被反序列化后静默落到业务 Handler；`OFFLINE_PULL` 和 `GROUP_CHAT` 虽定义常量但 server Handler 未处理，故为 PARTIAL。帧的半包重组由前置 LengthField decoder 负责，`TIMFrameCodecTest` 已验证一次拆帧用例。

注意：`requestId` 不是统一的 messageId。LOGIN 把它当 userId；CHAT 的真实幂等/ACK 主键是 JSON `ChatMessage.messageId`；ACK 以 `reqMsg` 字符串传回该 ID。
