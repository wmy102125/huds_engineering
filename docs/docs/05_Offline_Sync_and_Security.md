# Offline-First 同步、安全与证据协议
## 1. 本地 IndexedDB Stores
`appliances`, `cases`, `visits`, `safety`, `hypotheses`, `steps`, `measurements`, `repairActions`, `verifications`, `mediaManifest`, `outbox`, `syncState`, `conflicts`。本地键 UUID，`outbox` 保存 `mutationId`, `entityType`, `entityId`, `operation`, `payload`, `baseVersion`, `dependsOn`, `clientCreatedAt`, `retryCount`, `state`。

## 2. Outbox 事务
用户提交时**同一 IndexedDB 事务**写业务草稿 + mutation；先显示“已保存在本机”，服务端 ACK 后显示“已同步”。同步顺序按依赖拓扑排序，允许 batch；网络恢复、应用进入前台、手动按钮触发，Background Sync 仅优化。

## 3. JSON 示例
```json
{
  "mutationId":"550e8400-e29b-41d4-a716-446655440000",
  "deviceId":"mobile-01",
  "entityType":"measurement_records",
  "entityId":"3c7c64d2-4cda-4d1f-91f2-9c59b86d1a15",
  "operation":"CREATE",
  "baseVersion":null,
  "dependsOn":["step-create-uuid"],
  "clientCreatedAt":"2026-10-07T21:00:00-03:00",
  "payload":{"readingKind":"OL","actualValue":null,"unit":"ohm"}
}
```

## 4. 服务端处理
1. 验证认证/权限、实体所有权、请求大小、Schema。
2. 以 `(actor_user_id, mutationId)` 唯一约束检查已处理请求；若重复，返回持久化 ACK。
3. 检查依赖、`baseVersion`、状态机与安全/授权门禁；失败返回结构化拒绝，不伪装成功。
4. 在**单一数据库事务**写领域记录、状态事件、审计、mutation 结果；提交后 ACK。
5. 客户端保存 ACK + serverVersion + serverTime；更新本地视图，清理已 ACK 的 outbox（业务草稿仍保留）。

## 5. 冲突策略矩阵
| 数据 | 策略 | 例子 |
|---|---|---|
| 测量/照片/诊断原始事件 | Client append-only + UUID 去重；不允许覆盖 | 离线电阻值同步后保存为新记录 |
| 客户/地址/预约等可变元数据 | 乐观锁 `baseVersion`，冲突交互合并 | 客户网页改地址，技师离线旧地址不得覆盖 |
| Case/Visit/Repair 状态 | 服务端合法状态转移，版本校验 | 客户取消后不能凭较晚同步恢复 |
| 报价/授权/付款 | 服务端验证，版本/范围/签署证据绑定；冲突人工处理 | 已撤销报价不得接受旧授权 |
| 知识候选 | 版本化审核 | 旧版本不能覆盖已审版本 |

**禁止**统一 Last-Write-Wins，禁止用客户端时钟决定权威状态。离线签名先是本地证据，服务端确认其关联版本、范围、身份和适用性后才标记 accepted；必要时阻止依赖动作直至核实。

## 6. 媒体协议
创建 manifest（UUID、SHA-256、mime、size、case/step）→服务器发限时上传凭证→分片/重试→完成回调→服务端检查哈希与对象属性→标记 VERIFIED。签名和敏感媒体不公开；对象 key 不可作为永久授权 URL。

## 7. 错误语义
`400 INVALID_PAYLOAD`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 NOT_FOUND`, `409 VERSION_CONFLICT/INVALID_TRANSITION`, `412 SAFETY_GATE_REQUIRED/AUTHORIZATION_REQUIRED`, `422 DOMAIN_RULE_VIOLATION`, `429 RATE_LIMITED`, `503 RETRY_LATER`；每个错误包含 `code`, `message`, `retryable`, `currentVersion`, `correlationId`（适用时）。

## 8. 安全设计
HTTPS；会话刷新与设备丢失撤销；最小权限；R2 私有桶；PII 最小化；审计日志不可写敏感签名原文；浏览器本地数据应提示设备丢失风险，必要时使用 OS 设备加密和应用锁。服务器不可相信客户端安全复选框，必须校验安全评估与实际测试类型；安全许可只说明软件流程满足条件，不代表现场一定安全。

## 9. 同步验收
网络抖动、双设备并发、同 mutation 10 次重放、App 强杀、时间偏差 24h、依赖乱序、媒体上传中断、认证过期、客户取消预约、报价版本过期、离线签名冲突、磁盘配额不足、存储被系统清理。


## v1.1 跨设备并发与离线边界
1. Desktop 与 Mobile 采用同一 `client_mutation_id`、`entity_id`、`expected_revision` 协议；每个设备各自 Outbox。
2. 对可变客户/设备资料使用 optimistic locking：`If-Match`/expected_revision 不匹配则 HTTP 412（或契约定义的 409），返回最新服务端快照及字段级差异。
3. 对证据（诊断步骤的已提交结果、测量、照片索引）使用追加写入；同一 mutation_id 重试返回原响应，不产生重复记录。
4. Case/Visit 状态与授权由服务器校验合法转换及有效版本；绝不以客户端时间或最后到达请求自动获胜。
5. 手机离线录入的数据同步后可能被接受为历史证据，但不得自动恢复已取消预约、撤销授权或已结案状态。
6. 本地浏览器清理、隐私模式、设备空间不足均可能丢失尚未同步的数据；界面明确显示同步状态，并允许人工重试/导出受控草稿（需脱敏与访问控制）。
7. 两设备先后操作时按 `server_recorded_at` 记录服务端事件顺序；保留 `client_observed_at` 供审计，但不作为最终冲突裁决依据。
