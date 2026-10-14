# 模块划分与功能描述（Phase 1）
| 模块 | 优先级 | 子功能 | 输入/输出 | 核心验收 |
|---|---|---|---|---|
| M01 设备档案 | P0 | 铭牌拍照、品牌型号序列号、身份核验、位置历史、服务历史 | 设备标识→Appliance ID | 不同住址保留历史快照 |
| M02 维修案件 | P0 | 故障描述、症状/错误码、Case/Visit、暂停恢复、结局分类 | 报修→Case/Visit | 多次上门独立记录 |
| M03 现场安全 | P0 | 危害识别、风险控制、静态/带电门禁、停工与复评 | Test requirement→clearance | 不满足门禁拒绝执行 |
| M04 诊断推理 | P0 | 假设、测试点、预期、实际、解释、下一步、反证 | Hypothesis→decision | 支持否定与重测 |
| M05 测量证据 | P0 | 数值/OL/不稳定/未测、单位、工具、照片视频、修订 | 测试→Evidence | 原始记录可追溯 |
| M06 维修操作 | P0 | 拆装、清洗、调整、零件更换、DOA、失败回退 | 授权→Action | 维修动作可追溯 |
| M07 修后验证 | P0 | 验证模板、功能测试、结果、失败返工、结案报告 | Repair→Verification | 未通过不可标修复成功 |
| M08 离线同步 | P0 | IndexedDB、Outbox、重试、幂等、冲突队列、媒体续传 | local events→server ack | 重放无重复数据 |
| M09 案例检索 | P0 | 按设备/症状/故障码/根因/部件过滤 | Query→Cases | 不把相似性当因果 |
| M10 知识候选 | P0 精简 | 候选规则、证据绑定、审核版本、反例 | Cases→Candidate | 未审核不得正式发布 |
| M11 客户及地址 | P1 必需 | 最少联系信息、现场地址、案件快照 | Customer→Case | 旧案件快照不可变 |
| M12 报价与授权 | P1 必需 | 诊断收费授权、报价版本、维修批准、完工确认 | Scope→Consent | 版本签署与拒绝可审计 |
| M13 零件与费用 | P1 精简 | 零件使用/来源/序列、成本、基础付款 | Repair→Parts/Cost | 无完整库存 ERP |
| M14 基础保修 | P1 精简 | 维修/零件范围、起止日、返修关联 | Repair→Warranty | 一案可多保修条款 |
| M15 账户与审计 | P1 必需 | 登录、角色、访问范围、审计事件 | User→Audit | 不能越权读客户数据 |

## 纵向切片（开发顺序）
- **Slice A — Diagnose**：设备→Case→Visit→安全→假设→测量→证据→根因（含离线）。
- **Slice B — Repair**：报价/授权→维修动作→零件→异常回退→验证。
- **Slice C — Close & Learn**：报告→保修→历史检索→知识候选审阅。

## Phase 2 候选（明确不做）
复杂预约派工、库存采购和供应商、OEM bulletin/召回自动化、零件替代与适配引擎、完整财务、成本利润分析、预测诊断 AI、多租户 SaaS、客户门户、翻新业务。


## v1.1 横向能力：双端读写（P0）
| 编号 | 功能 | Desktop Web | Mobile Web/PWA | 验收 |
|---|---|---|---|---|
| UI-01 | 设备、客户、Case 查询/创建/修改 | 完整 | 完整 | 双端独立建档 |
| UI-02 | 诊断假设、步骤、测量、媒体 | 多栏+键盘 | 分步+相机 | 双端完成闭环 |
| UI-03 | 维修动作、验证、结案 | 完整 | 完整 | 后端统一状态机 |
| UI-04 | 历史案例及知识检索 | 高密度筛选 | 响应式筛选 | 同一查询语义 |
| UI-05 | 报告查看、打印/分享 | 浏览器打印 | 移动打印/分享 | 统一报告模板 |
| UI-06 | 离线草稿/Outbox | 浏览器支持时启用 | 现场必需 | 失败可重试 |
| UI-07 | 多端并发冲突处理 | 冲突对比 | 冲突提示 | 不静默覆盖 |

前端为**一个 React 代码库**，通过响应式布局和适配组件分别优化电脑、手机；不得拆分两套业务规则。

## v1.3 CR-001 增补
- **M16 上门前收费告知、客户确认与轻量预约（P1）**：SMS 专属链接、费用/条款版本快照、客户主动确认、预约时段确认、拒绝/失效/重确认、不可篡改事件、双端状态显示；详细见 `11_CR001_Pre_Service_Agreement_and_Appointment.md`。
- M12 报价与授权新增约束：预授权仅覆盖披露的诊断服务，维修必须独立报价批准。


## v1.4 CR-002 extension
Multi-visit repairs, parts orders, deposits, receipts, refunds and invoice settlement: see `12_CR002_Multi_Visit_Parts_Deposit_and_Settlement.md`. Phase 1 P1; not completed by the existing baseline SQL.
