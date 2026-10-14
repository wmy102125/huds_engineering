# 待产品负责人确认的关键决策
| 编号 | 问题 | 建议默认值 | 状态 |
|---|---|---|---|
| ADR-001 | 首期支持哪些设备类型 | 洗衣机、干衣机、冰箱、炉灶、洗碗机；热泵清洗独立模板 | 待确认 |
| ADR-002 | 是否允许无客户签字执行诊断 | 记录拒绝/替代授权证据，受业务规则约束 | 待法律/业务审查 |
| ADR-003 | 离线签名是否可触发收费维修 | 默认不可自动生效，服务端审查/例外政策 | 待确认 |
| ADR-004 | 安全门禁与资质适用范围 | 按设备/测试类型定义，不提供自动危险操作指导 | 待专业审查 |
| ADR-005 | 数据保留期限和删除政策 | 暂不硬编码期限 | 待确认 |
| ADR-006 | 客户报告语言与格式 | 英文客户版、中文内部说明 | 待确认 |
| ADR-007 | PostgreSQL 17 vs 18 | 17.11 作为首期基线 | 建议 |
| ADR-008 | Spring Boot 4 扩展兼容 | 先做 spike；失败回退 Boot 3.5.x | 待验证 |
| ADR-009 | 设备跨地址/所有权变更 | 历史关系表+案件快照 | 建议 |
| ADR-010 | 保修范围与税务处理 | 配置化，不写死税率/法定期限 | 待确认 |
| ADR-011 | 个人数据保护/备份恢复 | 先做威胁模型和恢复演练 | 待确认 |
| ADR-012 | 单技术员首期部署/后续多用户 | 一期单组织，权限模型保留多用户 | 建议 |


## CR-002 (2026-10-08)
APPROVED DESIGN DIRECTION: multiple visits per case; distinct parts order, financial transactions/allocations, prepayment receipt and final invoice; v1.1 form draft. IMPLEMENTATION PENDING: authoritative ledger choice, migration/backfill, tax and refund policy.
