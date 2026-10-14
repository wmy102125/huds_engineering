# HADS Phase 1 Engineering Package v1.4 — CURRENT DESIGN BASELINE

**New in v1.4:** CR-002 multi-visit repairs, parts deposits, supplier orders, refunds, receipts, final invoice settlement, printable v1.1 draft. See `CHANGELOG_v1.4.md` and `docs/12_CR002_Multi_Visit_Parts_Deposit_and_Settlement.md`.

**Important:** V004 SQL is a DESIGN DRAFT, not an approved Flyway migration; new API endpoints are contracts only; form v1.1 is a print-friendly DRAFT, not an exact redesign of original v1.0 PDF. CR-001 from v1.3 remains in force.

# HADS Phase 1 工程项目包 v1.3
HUGO’S Appliance Diagnostic System | Repair First · Desktop + Mobile · Offline-First · Evidence-Driven

## 交付定位
这是一套**开发规范与数据库脚本工程文档包**，不是可直接启动的成品应用。沿用 v1.1 全量文档/SQL/API 草案，并增加多 AI 协作总体计划和截至 2026-10-08 的主流 SDK/框架版本登记册。

## 优先阅读顺序
1. `docs/01_HADS_PRD_Phase1_v1.1.md` — 产品需求原始基线
2. `docs/10_Cross_Device_Requirements_Amendment.md` — v1.2 P0 跨设备补充
3. `docs/08_Software_Development_Master_Plan.md` — 开发计划和质量门禁
4. `AI_TEAM_RULES.md` — 三 AI 协作协议
5. `docs/09_SDK_Framework_Version_Register_2026-10-08.md` — 版本与来源
6. `docs/02_Modules_Functions_Backlog.md` — 模块/功能
7. `docs/04_Database_Design_Dictionary.md` + `db/migrations/` — 数据库
8. `contracts/openapi_phase1_skeleton.yaml` — API 草案，**尚非完整契约**
9. `docs/05_Offline_Sync_and_Security.md`、`qa/Acceptance_Test_Cases.md` — 同步/验收

## 关键工程提醒
- Spring Boot 4.1.1 / React 19.3.0 / Node 24.21.0 LTS / Vite 8.3.x / PostgreSQL 17.11 / Dexie 4.4.6 是**拟选组合**；每个精确依赖补丁与兼容性需 Sprint 0 验证后冻结。
- `db/migrations/V001...sql` 和 `V002...sql` 是设计稿，未在本次交付中启动 PostgreSQL 运行迁移。不要把文件存在视为执行通过。
- 技术负责人 GPT-6；实施 Claude Code；独立审查 Gemini；业务验收由负责人决定；所有协作依赖共享 Git 和正式记录。
- 首期维修流程优先；电脑和手机功能一致、布局优化；离线不等于服务端授权有效。

参见 `CHANGELOG_v1.2.md` 与 `SHA256_CHECKSUMS.txt`。

## v1.3 新增（必读）
- `docs/11_CR001_Pre_Service_Agreement_and_Appointment.md` — 收费披露/短信客户确认/预约确认业务规则和 AC-01~AC-12
- `db/migrations/V003__pre_service_agreement_DESIGN_DRAFT.sql` — 数据库设计草案（**未经迁移验证**）
- `contracts/CR001_API_EXTENSION_DRAFT.md` — API 设计草案
- `CHANGELOG_v1.3.md` — 变更清单
注意：**约定上门时间本身不能代替客户对收费条款的主动同意；维修另行报价授权。**
