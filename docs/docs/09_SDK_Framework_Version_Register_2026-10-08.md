# SDK / 框架 / 运行时版本登记册
**核对日期：2026-10-08。** “已核验”仅指官方发布页面/权威注册信息，不代表 HADS 组合已经集成测试。主流最新 ≠ 全部采用最新；本项目优先选择有支持期、可复现的兼容版本。精确补丁锁定需在 Sprint 0 完成。

| 分类 | 技术 | 2026-10-08 已知版本/主流线 | HADS 选定基线 | 状态/兼容说明 |
|---|---|---|---|---|
| JDK | Java | 21 LTS（成熟）；另有更新的 LTS | **21 LTS** | 选用稳定 LTS，发行商补丁待锁定 |
| Backend | Spring Boot | **4.1.1**（官方 2026-08-20） | **4.1.1** | 官方兼容 Java 17–26；与 Spring Framework 7.0.x |
| Core framework | Spring Framework | **7.0.x**（Boot 4.1 对应） | Boot BOM 管理 | 不独立覆盖 BOM |
| Security | Spring Security | **7.1.x**（Boot 4.1 代际） | Boot BOM 管理 | 认证方案 Sprint 0 ADR |
| Data | Spring Data | **2026.0.x**（Boot 4.1 代际） | Boot BOM 管理 | JPA 驱动版本随 BOM |
| Backend build | Maven | **3.9.x** | Maven Wrapper 固定 3.9.x patch | Boot 4 要求 Maven 3.6.3+ |
| DB | PostgreSQL | **18.6** 当前稳定；**17.11** 受支持 | **17.11** | 选择成熟 17 线；2029-11 支持截止 |
| UI | React | **19.3.0**（2026-09-09） | **19.3.0** | 官方版本页面核验 |
| UI language | TypeScript | 5.x 主流 | **5.x，Sprint 0 锁精确 patch** | 未核实最新补丁，不虚构 |
| Build | Vite | **8.3** 官方维护线 | **8.3.x** | 实际 npm 锁定具体 patch |
| Node runtime | Node.js | **24.21.0 LTS**（2026-09-08）；26.11.0 Current（2026-10-07） | **24.21.0 LTS** | 不用 Current 作生产默认 |
| Browser DB | Dexie.js | **4.4.6**（npm 注册信息） | **4.4.6** | IndexedDB；同步协议自行实现 |
| API schema | OpenAPI | 3.1 | **3.1** | OpenAPI YAML 需校验器验证 |
| JSON Schema | JSON Schema | Draft 2020-12 | **2020-12** | 客户端 DTO 和离线队列 |
| SQL migrations | Flyway | 与 Boot 4.1 BOM 兼容线 | **BOM 管理，Sprint 0 记录解析版本** | 需单独加入 PostgreSQL database 模块 |
| HTTP caching | TanStack Query | 5.x | **5.x，锁 patch** | React 19 兼容测试 |
| Desktop tables | TanStack Table | 8.x | **8.x，锁 patch** | 桌面大表格，移动卡片 |
| Forms | React Hook Form | 7.x | **7.x，锁 patch** | 需验证 React 19 |
| PWA | vite-plugin-pwa | 需核实支持 Vite 8 的版本 | **暂不锁定** | Sprint 0 做兼容 POC，不合适则替代 |
| Backend tests | JUnit / Testcontainers | Boot 4.1 BOM 管理 | **BOM 管理** | 注意 JUnit 5/6 生态变化，避免强制旧版 |
| E2E | Playwright | 1.x 稳定线 | **1.x，Sprint 0 锁 patch** | 与浏览器二进制同步锁定 |
| Containers | Docker Engine / Compose | 现行受支持版本 | **Sprint 0 锁版本** | 镜像使用 digest，不用 latest |
| CI | GitHub Actions | Actions 工作流 | **官方受支持 action 主版本 + SHA pin** | 供应链安全 |
| Object storage | Cloudflare R2 | S3 兼容 API | **AWS SDK for Java 2.x，Sprint 0 锁 patch** | 仅签名 URL / 私有 bucket；验证校验和行为 |
| Monitoring | Micrometer / OpenTelemetry | Boot 4.1 Micrometer 1.17.x | **BOM 管理** | 追踪敏感数据脱敏 |

## 版本决策规则
- 版本状态：**Verified upstream**（官方或注册源核验）、**Proposed**（拟选）、**Integration verified**（真实仓库测试通过）、**Frozen**（锁文件和镜像 digest 冻结）。本表没有任何条目标记为 Integration verified。
- Spring 生态由 BOM 控制；前端使用 pnpm-lock.yaml 或 package-lock.json 精确锁定；容器按 digest；CI 使用 SHA pin。
- Sprint 0 执行：`java -version`、`./mvnw -version`、`./mvnw dependency:tree`、`node --version`、`npm ls --all`、`npm audit`、`docker compose version`，记录原始输出到 `qa/version-verification/`。
- 升级策略：安全补丁优先，次版本先做回归；主版本需 ADR、兼容矩阵、迁移和回滚计划。

## 官方/权威版本来源（核对日期 2026-10-08）
- Spring Boot: https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/
- Spring Boot 系统要求: https://docs.spring.io/spring-boot/system-requirements.html
- Spring 代际: https://spring.io/projects/generations/
- React: https://react.dev/versions
- Vite: https://vite.dev/releases
- Node.js: https://github.com/nodejs/node/releases
- PostgreSQL: https://www.postgresql.org/support/versioning/
- Dexie: https://www.npmjs.com/package/dexie
