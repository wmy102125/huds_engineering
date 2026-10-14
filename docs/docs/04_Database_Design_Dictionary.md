# PostgreSQL 数据库设计与数据字典
**对应脚本**：`db/migrations/V001__hads_phase1_baseline.sql`。UUID 由客户端离线生成或服务端 `gen_random_uuid()` 生成；所有业务主键 UUID，事件时间 `timestamptz`，财务金额 `numeric(12,2)`，计量数值 `numeric(18,6)`；无证据级联删除。

## 1. 核心拓扑
`customers → service_addresses`；`appliances ↔ appliance_location_history`；`appliances → diagnostic_cases → service_visits`；`diagnostic_cases → hypotheses → steps → measurements`；`diagnostic_cases → repair_actions → installed_parts`；`diagnostic_cases → verifications`；`diagnostic_cases → estimate_revisions → authorizations`；`diagnostic_cases → knowledge_candidates → knowledge_evidence`。

## 2. 数据字典（四列表格）
| 领域模块 | 物理数据表名 | 主键生成策略 | 核心职责与关键约束 |
|---|---|---|---|
| 身份 | sys_users | UUID | 技师、审阅人；角色和启用状态 |
| 客户 | customers | UUID | 联系方式，软删除 |
| 地址 | service_addresses | UUID | 客户关联、地址历史来源 |
| 设备 | appliances | UUID | 型号/序列号可空，身份可信度 |
| 设备 | appliance_location_history | UUID | 生效区间，不永久绑定客户地址 |
| 案件 | diagnostic_cases | UUID | 设备、现场快照、状态/结局分离、row_version |
| 案件 | case_state_events | UUID | 状态变更追加日志 |
| 案件 | service_visits | UUID | 一案多次上门、暂停/结束 |
| 安全 | safety_assessments | UUID | 每次 Visit 的模式/风险/停工 |
| 安全 | safety_hazards | UUID | 危害项、缓解措施、是否可控 |
| 诊断 | diagnostic_hypotheses | UUID | 假设、状态、优先级 |
| 诊断 | diagnostic_steps | UUID | 假设、预期、测试方式、所需安全模式 |
| 证据 | measurement_records | UUID | 测量值可空、非数值状态、单位/仪表 |
| 证据 | evidence_amendments | UUID | 原记录更正、原因、替代载荷 |
| 证据 | diagnostic_media | UUID | 媒体哈希、私有对象 key、上传状态 |
| 工具 | tool_assets | UUID | 仪表识别、校准/检查信息 |
| 根因 | root_cause_findings | UUID | 结论级别、支持/反对证据 |
| 维修 | repair_actions | UUID | 动作状态、故障/DOA、操作记录 |
| 维修 | installed_parts | UUID | 部件安装、来源、批次与成本 |
| 验证 | verification_templates | UUID | 检查模板、设备类型 |
| 验证 | repair_verifications | UUID | 验证轮次、总体结果 |
| 验证 | verification_checks | UUID | 逐项结果、实际与期望 |
| 商务 | estimate_revisions | UUID | 不可覆盖的报价版本 |
| 商务 | estimate_items | UUID | 报价行项目 |
| 商务 | authorizations | UUID | 阶段授权、报价版本、摘要、确认方式 |
| 商务 | payment_records | UUID | 基础付款记录，不代替会计系统 |
| 保修 | warranties | UUID | 一案多条保修、覆盖范围 |
| 知识 | knowledge_candidates | UUID | 人工候选、审核状态、版本 |
| 知识 | knowledge_evidence | UUID | 候选证据支持/反证 |
| 离线 | sync_mutations | UUID | 设备 mutation 去重、响应回放 |
| 审计 | audit_events | UUID | 追加审计、actor、动作、引用 |

## 3. 关键约束
- `measurement_records`: `reading_kind='NUMERIC'` 才允许且要求 `actual_value` 非空；其他类型必须为空。
- `diagnostic_cases`: workflow_status 与 disposition 独立；关闭时需结局；状态变更由服务端事务和规则控制，SQL CHECK 不等于状态机。
- `estimate_revisions`: `(case_id, revision_no)` 唯一；已发布报价及授权内容必须在应用层/数据库权限层阻止改写。
- `authorizations`: `estimate_revision_id` 可空（诊断收费、完工确认不一定绑定报价）；维修报价授权必须绑定版本并核验 hash。
- `sync_mutations`: `(actor_user_id, client_mutation_id)` 唯一；重复提交返回已保存结果。
- `knowledge_evidence`: 明确 SUPPORTS / CONTRADICTS，候选规则需人工审查。
- 生产禁止使用 `ON DELETE CASCADE` 清除案件证据；使用软删除/受控留存策略。

## 4. 数据库层未覆盖的规则（应用必须实现）
服务端状态机校验、授权适用范围、签名真实性验证、实际安全门禁有效期、证据防篡改访问权限、知识发布审批、媒体上传对象校验、保留/销毁审批。仅建表不等于满足这些要求。

## 5. 索引与迁移
SQL 建立常用 case、appliance、step、media、mutation 索引。以后迁移使用 Flyway V002+，不得直接修改已经部署的 V001；大表索引与归档策略上线前压测。

## 6. 备份与恢复
每日加密备份、定期恢复演练、明确 RPO/RTO（待确定）；数据库备份不包含 R2 对象字节，需协调对象存储生命周期和恢复演练。


## v1.1 跨设备数据设计评审
- **不新增 desktop_cases/mobile_cases 等平台分表**：两端操作同一组实体。
- 服务端为每个可变聚合维护版本号（`record_revision` 或现有等价 `version` 字段）；更新使用预期版本条件写入。若旧 DDL 尚无版本字段，必须在后续迁移增加，不能仅靠 UI 检查。
- append-only 诊断证据以 UUID + mutation/idempotency key 去重，冲突不得覆盖证据。
- 对 `service_visits`、`diagnostic_cases` 等业务聚合应检查实际 SQL 是否具备 revision、事件记录和唯一约束；本说明不声称原 DDL 已全部实现。
- 本次 v1.1 不重写 `V001` 历史基线，增量迁移见 `db/migrations/V002__cross_device_revision.sql`。


## v1.4 CR-002 extension
See `14_CR002_Data_Dictionary_and_Integrity.md` and `db/migrations/V004__parts_deposits_multi_visit_DESIGN_DRAFT.sql`. Existing `payment_records` cannot be treated as an authoritative deposit/refund allocation ledger without migration decision.
