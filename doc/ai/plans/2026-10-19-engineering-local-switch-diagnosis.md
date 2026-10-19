# 工程连接器 dev/local 开关诊断与验证

日期：2026-10-19；工作区：`E:/Nexus/nexus-erp`；分支：`plus`；基线：`ba0fdc79`。

## 目标与写入范围

启用开发前端和后端 dev/local 的工程入口、条件 Bean；生产仍需显式启用。
仅修改 `plus-ui/.env.development`、`ruoyi-admin/src/main/resources/application-dev.yml`、`ruoyi-admin/src/main/resources/application-local.yml` 和本文件。验证构建使用 `plus-ui/node_modules/.tmp/` 独立目录，不覆盖运行产物。

## 已确认原因

- 前端 `engineering-config.ts` 通过 `engineeringFeatureEnabled(import.meta.env?.VITE_APP_ENGINEERING_ENABLED)` 判断入口；缺失值按关闭处理。开发环境文件未设置该变量。
- `EngineeringProperties.enabled` 默认 `false`；工程控制器、服务、认证过滤器和存储适配器以 `nexus.ai.engineering.enabled=true` 为装配条件。
- 用户提供的现场证据：后端 `127.0.0.1:8080`，PID `30292`；2026-10-19 14:50:55 日志显示 active profile 为 `dev`；前端端口 `80`，开发代理前缀 `/dev-api`；配对接口返回 `R.code=404`。本任务不通过业务接口查询数据。
- 本地文件导入共享 `application-dev.yml`，但不激活 `dev`。共享文件原有 14 份 YAML 文档（含 sms/social 限定文档），不可直接在其无条件文档中开启工程功能。

## 最小补丁与执行计划

- [x] 开发前端追加 `VITE_APP_ENGINEERING_ENABLED = true`。
- [x] 共享 dev 文件追加以 `spring.config.activate.on-profile: dev` 限定的文档：`nexus.ai.engineering.enabled: true`，`oss-config-key: ${NEXUS_ENGINEERING_OSS_CONFIG_KEY:engineering-private}`。
- [x] local 的本地覆盖文档添加 `on-profile: local`，显式配置相同工程开关与私桶键；保留共享导入，不激活 dev。
- [x] 用已有 YAML 解析器检查重复键、多文档、dev/local/prod/无 profile/sms/social 场景，以及前端开发开关；断言仅输出安全结果。
- [x] 运行 `npm run test:engineering`、`npm run type-check`，记录非零测试数。
- [x] 以 development 模式构建至 `node_modules/.tmp/` 独立目录，防止覆盖运行文件；核对最终差异。

## 私桶与启动约束

`SysEngineeringStorageAdapter.verifyConfiguration()` 在 `@PostConstruct` 阶段执行 `ossConfigs.init()` 和 `client()`。配置键为空、不存在，或者访问策略不是 `AccessPolicyType.PRIVATE`，都会拒绝启动；`OssFactory` 不会隐式回落到默认桶。

`engineering-private` 只是无凭证的配置键契约，不会创建 OSS 配置或物理桶。启动前需要有效的专用 PRIVATE OSS 配置；真正上传还需要存储端、凭证、目标私桶和权限有效。源码中的 OSS 客户端构造不执行真实上传，本任务不会连接存储服务或验证现有配置。

## 验证记录

修改前内存检查：10 项，5 通过、5 失败，失败项覆盖 dev/local 开启、profile 限定、私桶配置键与开发前端开关。

配置修改后验证（前端命令在 `plus-ui/` 执行）：

| 命令/检查 | 结果 |
| --- | --- |
| `python -X utf8 -`，PyYAML 唯一键加载器与内存 unittest | 10 项通过，0 失败；dev 为 15 份文档、local 为 2 份 |
| `node --input-type=module -`，实际调用 Vite `loadEnv` | 2 项通过：development 开启，production 未开启 |
| `npm run test:engineering` | 81 项通过，0 失败，0 跳过 |
| `npm run type-check` | `vue-tsc --noEmit`，退出码 0 |
| `node --max-old-space-size=8192 --input-type=module -`，Vite API development 构建 | 退出码 0，生成 964 个输出；自动声明与 ESLint 辅助文件生成仅在内存中禁用 |
| `git diff --check -- <授权文件>` | 通过；仅有 Git 的 LF/CRLF 转换提示，无空白错误 |

配置矩阵覆盖 dev/local 生效、prod 导入共享 dev 仍关闭、prod 导入 local 仍关闭、无激活 profile 仍关闭、prod+sms+social 仍关闭、两份工程文档各自限定 profile、私桶键环境覆盖契约、local 不额外激活 dev、前端开发开关。检查工程段仅含 enabled 与 oss-config-key，不新增凭证。

`NEXUS_ENGINEERING_OSS_CONFIG_KEY` 可覆盖默认配置键 `engineering-private`。该值只标识已有配置，不含认证信息，也不证明该私桶已配置。外部私有配置和显式环境设置仍可覆盖仓库默认值。

为遵守写入范围，构建在内存中加载现有 `vite.config.ts` 与插件，将自动导入和组件插件的类型声明生成关闭，并关闭 ESLint 辅助文件生成；其他构建逻辑保持现有配置，缓存和产物定向到独立 `node_modules/.tmp/` 目录。该适配仅用于本次验证，不写入前端源码或 Vite 配置文件。

构建产物：`E:/Nexus/nexus-erp/plus-ui/node_modules/.tmp/engineering-local-switch-20261019-7f0371f8-3227-4ac0-a1cf-b7afdfe8ede9/dist`。

构建出现 Sass `@import` 弃用、`stream` 浏览器外置、静态与动态导入重叠及大分块警告，无构建错误。类型检查另有 npm 用户级 mirror 配置警告，未影响退出码。

差异复核：三个配置文件合计新增 22 行，无删除；前端源码、Vite 配置、自动声明和生产配置无新增改动。用户并行修改的连接器及其他既有未提交文件未纳入本次补丁。

## 生效操作与未验收范围

用户需自行重启本地后端以重新加载配置并装配工程 Bean。前端 Vite 通常会检测环境文件变化并重载；若仍显示旧提示，需自行重启开发前端并刷新页面。本任务不自动启停任何现有服务，不执行 SQL、不读取业务数据、不填写真实凭证，不提交或推送。

配置及构建验证不等于运行进程已加载、连接器配对成功、产物上传成功或生产已部署；真实联调需在用户重启后进行。


## 最终运行状态补充

主线程在2026-10-19 17:29:31 +08:00再次只读检查：8080工程pair的GET已能确认工程路由，前端80端口的实际Vite工程开关为true。现有本地入口与后端接口此时已生效，刷新页面即可继续配对。本次没有自动启停服务，也未推断该重载是由用户还是IDE触发；以上原“需要重启”的说明保留为配置生效原理及先前时点记录。真实产物上传/存储网络未联调。
