# 研发路线图与交付物
## 阶段 A：需求冻结与验证（1–2 周，估算）
确认业务术语、危险作业规则、报价/签名法律边界、报告样式、离线设备类型。产出审批 PRD、状态机图、用例与 UAT 场景。
## 阶段 B：核心诊断纵向切片（3–5 周，估算）
设备→Case→Visit→安全→Hypothesis→Step→Measurement→Evidence；本地 outbox 与服务端幂等；自动化集成测试。可在真实无网场景试用。
## 阶段 C：维修与验证（3–4 周，估算）
报价修订与授权→Repair Action→Part→Verification→结案；DOA/返工/拒绝/不确定根因。输出客户报告。
## 阶段 D：知识与上线（2–3 周，估算）
案例检索、候选规则审核、权限、监控、备份恢复、移动 E2E、试点 UAT。
> 时间仅为小团队初步估算，不是交付承诺；先完成 Slice A 后再校准。

## 职责建议
- Product Owner / Domain Expert：真实维修需求、测试步骤、现场验收、优先级。
- Software Developer：实际承担模块的代码、测试、PR、部署文档；面试展示应与实际贡献一致。
- Technical Reviewer：架构评审、威胁模型、代码审查、故障注入测试。

## Definition of Done
需求验收明确；OpenAPI/DDL/代码一致；单元与集成测试通过；权限与安全门禁负向测试通过；离线异常可恢复；变更文档与 ADR 更新；代码审查完成；可演示且有回滚计划。

## 交付里程碑
A0 PRD 审批 → A1 诊断闭环 Demo → A2 离线冲突演示 → B1 维修+验证 Demo → B2 报告/授权 → C1 试点与上线审查。


## v1.1 双端并行验收里程碑
- Sprint 0：确认桌面/手机浏览器矩阵、设计系统断点、版本字段迁移与并发冲突契约。
- Sprint 1：设备/Case/Visit 双端可创建与检索；电脑键盘录入和手机触控录入均通过。
- Sprint 2：安全、诊断、测量、媒体、维修动作和验证双端可用；手机断网保存。
- Sprint 3：双设备并发冲突、离线恢复、授权门禁、报告与真实维修回归。
- DoD：每个 P0 用户故事必须包含 Desktop 和 Mobile 的验收截图/自动化记录。

## v1.3 CR-001 排期门禁
将 M16 与 M12 报价/授权和发票模块一起交付，不单独开发完整预约派工。Sprint 0 冻结条款版本、费用快照、确认事件与 API 合同；客户/工单完成后接入短信链接和确认页；收费模块验收前完成 AC-01~AC-12。


## v1.4 milestone
Sprint 2 multi-visit; Sprint 4 parts orders; Sprint 5 estimate approval/deposit receipts/invoice settlement/refunds. Do not defer all money integrity testing to the end.
