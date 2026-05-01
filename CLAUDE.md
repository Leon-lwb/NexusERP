# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概述

RuoYi-Vue-Plus v5.6.0 — 一个多租户企业管理系统 (ERP)。基于 RuoYi-Vue 分支，并针对分布式/集群部署进行了大量重写。采用 MIT 许可证。

## 构建与运行

### 后端 (Java / Maven)

- **构建**: `mvn clean package` (默认跳过测试 — 根 pom 中 `skipTests=true`)
- **运行测试**: `mvn test -DskipTests=false`
- **本地运行**: `mvn spring-boot:run -P local` (或者将 `ruoyi-admin` 模块导入 IDE 并运行 `DromaraApplication`)
- **激活的环境**: `local`、`dev`、`prod` (通过 `<profiles.active>` 属性设置)
- 入口点: `ruoyi-admin/src/main/java/org/dromara/DromaraApplication.java`
- Web 服务器: Undertow，默认端口 8080，上下文路径 `/`

### 前端 (plus-ui / Vue 3)

- **开发服务器**: `npm run dev` (端口 80，代理 `/dev-api` → `http://localhost:8080`)
- **生产构建**: `npm run build:prod`
- **代码检查**: `npm run lint:eslint` (使用 `lint:eslint:fix` 自动修复)
- **代码格式化**: `npm run prettier`
- 要求 Node >= 20.19.0

### 数据库

初始化 SQL 脚本位于 `script/sql/` 目录:
- `ry_vue_5.X.sql` — 基础系统表
- `ry_job.sql` — 分布式任务调度表
- `ry_workflow.sql` — Flowable 工作流表
- `ry-vue-erp.sql` — ERP 业务表
- 同时包含 Oracle、PostgreSQL 和 SQLServer 版本

## 架构

### 后端模块结构图

```
ruoyi-admin/              — Spring Boot 入口 (DromaraApplication)。组装所有模块。
ruoyi-common/             — 共享库 (26 个子模块)。每个均为可自动配置的独立 jar。
  ruoyi-common-core/      —    基础层: 领域模型、枚举、异常、校验器、工具类
  ruoyi-common-web/       —    Web 层: 全局异常处理、响应包装、过滤器
  ruoyi-common-satoken/   —    认证 (Sa-Token + JWT)
  ruoyi-common-mybatis/   —    ORM 基础 (MyBatis-Plus，集成多租户、加密、脱敏等插件)
  ruoyi-common-redis/     —    Redis 客户端 (Redisson + Spring Cache 注解)
  ruoyi-common-tenant/    —    多租户插件
  ruoyi-common-sse/       —    用于推送通知的服务器发送事件 (Server-Sent Events)
  ruoyi-common-oss/       —    对象存储 (Minio / S3 协议)
  ruoyi-common-flowable/  —    Flowable 工作流引擎集成
  ... 以及更多 (任务、日志、短信、邮件、社交、Excel、加密等)
ruoyi-modules/            — 业务模块
  ruoyi-system/           —    系统管理 (用户、角色、菜单、部门、租户、客户端、字典等)
  ruoyi-erp/              —    ERP 业务逻辑 (主要业务模块)
  ruoyi-flowable/         —    Flowable 工作流 REST API
  ruoyi-generator/        —    代码生成器
  ruoyi-job/              —    分布式任务调度 (SnailJob)
  ruoyi-demo/             —    演示/示例代码
  ruoyi-online/           —    在线低代码表单构建器
ruoyi-extend/             — 独立服务
  ruoyi-monitor-admin/    —    Spring Boot Admin 监控服务端
  ruoyi-snailjob-server/  —    SnailJob 管理控制台
```

### 分层架构 (每个业务模块)

每个业务模块在 `org.dromara.<module>` 下均遵循相同结构:

```
controller/               — REST 控制器
domain/                   — 实体类 (MyBatis-Plus 模型)
domain/bo/                — 业务对象 (请求 DTO)
domain/vo/                — 视图对象 (响应 DTO)
mapper/                   — MyBatis-Plus 映射器接口
service/                  — 服务接口
service/impl/             — 服务实现
```

模块通过 Spring Boot 自动配置 (`spring.factories` / `AutoConfiguration.imports`) 进行注册，而非组件扫描。

### ERP 模块子包

ERP 模块 (`ruoyi-erp`) 按业务领域组织:

| 包 | 领域 | 描述 |
|---------|--------|-------------|
| `bd` | 基础数据 | 物料、BOM、客户、供应商、车间、工位、计量单位、类别 |
| `prd` | 生产管理 | 生产计划、工单、报工、物料需求 |
| `so` | 销售管理 | 客户订单、订单头 |
| `sch` | 排产调度 | 班次、班组、工人、考勤、假期、生产调度 |
| `wm` | 仓储管理 | 仓库、库区/储位、库存、收货、发货、到货通知 |

### 前端结构 (plus-ui)

```
src/api/                  — API 层 (对应后端模块: system, erp/bd, erp/prd, erp/sch, erp/so, erp/wm, workflow)
src/views/                — 页面组件 (结构对应)
src/router/               — Vue Router 配置 (index.ts)
src/store/modules/        — Pinia 状态库 (app, user, permission, dict, tagsView, settings, notice)
src/components/           — 共享组件
src/layout/               — 布局框架 (侧边栏、顶栏、标签页)
src/utils/                — Axios 拦截器、认证工具等
src/package/              — BPMN 流程设计器 (Flowable 集成)
```

使用 UnoCSS 作为工具样式库，Element Plus 作为 UI 组件库，`vxe-table` 作为数据表格组件。

### 关键技术栈

| 关注点 | 技术 |
|---------|------------|
| 认证 | Sa-Token + JWT (无状态)，密钥: `Authorization` 请求头 |
| ORM | MyBatis-Plus 3.5.x，ID 策略: ASSIGN_ID (雪花算法) |
| 数据库连接池 | HikariCP |
| 缓存 | Redis (通过 Redisson 和 Spring Cache 注解) |
| 多租户 | 自定义 MyBatis-Plus 多租户插件 |
| 多数据源 | dynamic-datasource |
| 分布式锁 | Lock4j (基于 Redisson) |
| 任务调度 | SnailJob |
| 工作流 | Flowable 7.0 |
| 对象映射 | MapStruct Plus |
| API 文档 | SpringDoc (基于 javadoc，无需额外注解) |
| 序列化 | Jackson (内部工具使用 fastjson2) |
| Excel | FastExcel (阿里巴巴 EasyExcel 分支) |
| API 加密 | 动态 AES + RSA (每次请求生成唯一 AES 密钥，用 RSA 加密) |
| XSS 防护 | 自定义过滤器 (可配置排除 URL) |
| 前端技术栈 | Vue 3.5 + Vite 7 + TypeScript + Pinia + Element Plus + UnoCSS |

### 配置注意事项

- `application.yml` 使用 Maven 资源过滤 (`@property@` 语法) 来设置环境相关值
- 多租户默认开启 (`tenant.enable: true`)，特定表被排除 (例如 `sys_menu`、`sys_tenant`)
- API 加密默认开启 — 如果不需要，必须前后端同时禁用
- 默认通知机制为 SSE 推送；WebSocket 默认禁用
- Flowable 异步执行器和 IDM 引擎已禁用 — 仅运行核心流程引擎
- Logback 配置: `logback-plus.xml`，日志写入 `./logs/` 目录
- 敏感凭证位于 `application-dev.yml` / `application-prod.yml` 中 (不在基础 `application.yml` 中)

### 国际化 (i18n)

资源文件位于 `ruoyi-admin/src/main/resources/i18n/messages*.properties`。通过请求头进行语言切换。