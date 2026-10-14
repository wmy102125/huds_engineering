> **v1.2 提示：版本信息以 `09_SDK_Framework_Version_Register_2026-10-08.md` 为最新登记册；本文件保留 v1.1 技术决策背景。**

# 软件选型、匹配版本与架构
**冻结日期：2026-10-07；以下为建议兼容基线，不代表每个依赖的最新补丁均已实时核验。** 项目开始时执行 `mvn dependency:tree`、`npm audit`、官方兼容矩阵及安全公告核对，锁定 lockfile 与容器 digest。

| 层 | 建议版本基线 | 选择原因/兼容策略 |
|---|---|---|
| OS / 开发 | macOS / Windows 11 / Ubuntu 24.04 LTS | Docker 统一开发环境 |
| Java | **21 LTS** | 稳定、长期支持、招聘面试价值；避免追逐最新 JDK |
| Spring Boot | **4.1.x（首选 4.1.1）** | 官方支持 Java 17+，Java 21 兼容；采用 Spring Framework 7；插件须单独检查 Boot 4 兼容 |
| Build | Maven **3.9.x** | Spring Boot 4 支持；使用 Maven Wrapper 锁版本 |
| Database | PostgreSQL **17.11** | 17 主线成熟，官方支持至 2029；18.6 可作为升级候选，不必一期追新 |
| DB migrations | Flyway **Boot 4 BOM 管理的兼容版本** | 避免手写固定版本与 BOM 冲突；PostgreSQL 模块按 Boot 4 指南加入 |
| Frontend | React **19.3.x** | 组件生态成熟；版本发布已核验 |
| Language | TypeScript **5.x（按 Vite 支持矩阵锁定）** | 类型约束和共享 DTO |
| Bundler | Vite **8.3.x** | 官方当前维护线；Node 版本按 Vite 官方要求 |
| Node.js | **24 LTS**（部署前核对维护状态） | 与 Vite 8 的运行时要求核对，使用 `.nvmrc` 固定 |
| PWA | vite-plugin-pwa（与 Vite 8 兼容的已验证版本） | 缓存应用壳；不依赖浏览器后台同步作为唯一手段 |
| Offline DB | Dexie.js **4.x** | IndexedDB 封装、事务与迁移；锁定具体 patch |
| API | REST/JSON + OpenAPI **3.1** | 合同优先、前后端解耦 |
| Validation | Jakarta Validation / JSON Schema **2020-12** | 服务端权威校验、客户端提前提示 |
| Security | Spring Security **Boot BOM 管理** | 会话/Token、角色权限、CSRF/CORS 依认证方案设计 |
| Tests | JUnit 5、Testcontainers（BOM 管理）、Playwright（锁版本） | 真实 PG 集成测试 + 浏览器离线 E2E |
| Object Storage | Cloudflare R2（S3 API） | 私有媒体存储；上传校验 SHA-256；生命周期管理 |
| Deploy | Docker Engine / Compose v2、GitHub Actions | 可复现、自动化；生产数据库独立备份 |
| Logging | SLF4J + Micrometer/OpenTelemetry（兼容版本） | 关联 trace/request/mutation IDs；禁止记录敏感签名内容 |

## 选型决定
**模块化单体**（Spring Boot）+ React PWA + PostgreSQL + 私有对象存储。初期单个开发者/小团队更易测试、部署、回滚。暂不引入 Kafka、Kubernetes、微服务、Graph DB、Redis（除非性能证据证明必要）。

## 环境矩阵
| 环境 | API | DB | 对象存储 | 测试 |
|---|---|---|---|---|
| Local | localhost / Docker | PG 17 容器 | R2 测试桶或 S3 兼容模拟 | 单测/集成 |
| Staging | HTTPS | 独立 PG | 私有测试桶 | E2E/迁移/恢复 |
| Production | HTTPS | 受控 PG + PITR/备份 | 私有生产桶 | 健康检查/告警 |

## 官方核验资料（检索于 2026-10-07）
- Spring Boot 系统要求：https://docs.spring.io/spring-boot/system-requirements.html
- React 版本：https://react.dev/versions
- PostgreSQL 版本与支持：https://www.postgresql.org/support/versioning/
- Vite 发布与维护：https://vite.dev/releases

## 重要风险
Boot 4 生态扩展依赖可能尚未全面兼容；若插件不满足，采用经过验证的 Boot 3.5.x 兼容线作为备用并记录 ADR。**不要**在未核验前把所有建议版本直接复制进生产配置。


## v1.1 桌面 + 手机适配基线
| 类别 | 建议方案 | 版本策略 |
|---|---|---|
| 响应式布局 | CSS Grid / Flexbox + CSS Media Queries | 浏览器原生标准，无独立版本 |
| 桌面数据表格 | TanStack Table | 8.x，实施时锁定已验证 patch |
| 表单 | React Hook Form | 7.x，实施时核验 React 19 兼容性 |
| 数据请求与缓存 | TanStack Query | 5.x，实施时锁定已验证 patch |
| UI 自动化 | Playwright | 与锁定 Chromium/Firefox/WebKit 浏览器包配套 |
| 端侧存储 | Dexie.js 4.x | 每设备独立 IndexedDB，非服务端主数据 |

不得把 CSS 断点误认为独立权限或独立 API；双端业务能力相同，UI 布局不同。建议验收 Chrome/Edge 桌面、Safari 桌面、Chrome Android、Safari iOS 的受支持版本；具体最低版本在 Sprint 0 根据设备覆盖率冻结。此表为建议兼容基线，不表示依赖版本已在真实项目完成集成验证。
