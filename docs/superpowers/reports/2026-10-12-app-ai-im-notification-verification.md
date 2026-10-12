# App AI、IM 与通知修复验证记录

日期：2026-10-12。后端位于 `E:/Nexus/nexus-erp`，分支 `plus`；App 位于 `E:/Nexus/Nexus-App`，分支 `main`。本次开发遵循用户授权，在各自主分支提交任务改动，不推送。新增注释使用中文。

## 修复结果

- AI 会话、消息和用户 ID 保持字符串，完成后的历史回填不再因雪花 ID 精度丢失误报内部错误；回填失败静默保留成功回答。处理 SSE 握手、HTTP 返回与完成事件竞态、断线代次以及唯一完成/错误终态。H5 使用 EventSource，App 使用原生流式请求。后端检查会话用户和租户、拒绝空白或超长输入，对用户返回固定业务提示。
- IM 消息 Tab 每次展示刷新，合并并发初始化，避免重复 WebSocket 和旧回调。群聊 @ 的用户 ID、光标插入、文本修改和历史提及保持一致。修复已读、离线重发、跨账号缓存、超过五批的增量同步、好友申请更新以及通知点击后的消息高亮参数。
- 后端好友申请处理及撤销先验证归属和消息关联，事务提交后推送。群已读按当前收件人的通知状态计算，删除仅隐藏本人的消息，保留共享记录；同步返回个人隐藏 ID，其他设备清理相应缓存。引用与提及受会话边界和长度限制。
- 系统公告经所属租户的启用用户通知，移除全局文本广播。WebSocket 支持同用户多连接，旧连接关闭只删除自己；WS/SSE 定向空收件人不能误广播。SSE 通过 Redis 发布，单个连接异常不阻断其他设备，握手日志不输出登录凭据。
- App 原生通知启用 Push 模块、Android 通知权限与渠道及 iOS 本地权限入口。USER_NOTIFICATION 统一去重、遵守免打扰与会话静音；通知点击只接受业务白名单和匹配账号/租户，冷启动待身份和页面就绪后跳转。已读操作使在途旧计数失效。权限拒绝时通知中心仍可使用。
- HBuilderX 的正式项目根配置及验证脚本均使用配套 Vue shared/Pinia，修复运行库混用造成的 H5 白屏；工作台等入口直接导入 IM Store，消除分块重导出的循环依赖提示。

## 最新实跑结果

| 验证 | 结果 | 本地证据 |
| --- | --- | --- |
| 后端相关模块及依赖联合测试 | 2254 个用例，0 失败、0 错误、0 跳过；12 个模块实际运行非零测试 | `.codex/app-repair-20261012/backend-latest-tests.log` |
| 后端完整 package | 54 个聚合项全部成功；测试此前单独执行，打包使用 skipTests | `.codex/app-repair-20261012/backend-latest-package.log` |
| facade 纯任务候选独立编译及测试 | 物理排除已有 bulk 类及其两份测试，51 个用例通过 | `.codex/app-repair-20261012/facade-commit-validation.log` |
| App 全量 Vitest | 39 个文件、521 个用例通过 | `.codex/app-repair-20261012/app-latest-tests.log` |
| App 改动文件 ESLint / Prettier | 全部通过；没有全仓格式化 | `.codex/app-repair-20261012/app-latest-eslint.log`、`app-latest-format.log` |
| HBuilderX 5.15 正式根配置 H5 / App 编译 | 均返回 Build complete；App 为原生源码编译，未安装 APK/IPA | `.codex/app-repair-20261012/app-latest-h5-build.log`、`app-latest-native-build.log` |
| 最终 H5 页面流程 | 6 项通过，页面及控制台错误均为 0 | `.codex/app-repair-20261012/app-smoke.json`、`app-smoke.png` |
| 真实浏览器 EventSource 协议 | 连接后发送、中文增量和完成事件通过 | `.codex/app-repair-20261012/ai-latest-eventsource.log` |
| ADB 设备检查 | List of devices attached 下无设备 | `.codex/app-repair-20261012/adb-final-devices.log` |

H5 页面测试运行实际编译页面，在 HTTP/WS/SSE 传输边界使用受控响应；覆盖消息 Tab、群聊 @ 长 ID、服务端已读请求、Tab 返回刷新、单个 WS 连接，以及 AI DONE 后精确历史回填。它不写现有业务数据库，也不代表生产联调。EventSource smoke 使用实际浏览器和本地 SSE 服务。

## 复核命令

后端在 `E:/Nexus/nexus-erp` 执行：

```powershell
mvn -pl ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-im,ruoyi-modules/ruoyi-system,ruoyi-common/ruoyi-common-websocket,ruoyi-common/ruoyi-common-sse -am test '-DskipTests=false' '-Dprofiles.active=' '-Dgroups=' '-DexcludedGroups=integration,exclude' '-Dim.excludedGroups=integration' '-Dflatten.skip=true' '-Djacoco.skip=true' '-Dstyle.color=never'
mvn package '-DskipTests=true' '-Dflatten.skip=true' '-Djacoco.skip=true' '-Dstyle.color=never'
```

App 在 `E:/Nexus/Nexus-App` 执行：

```powershell
npm test -- --maxWorkers=2 --minWorkers=1
node scripts/verify-build.mjs --hbuilderx E:/HBuilderX --platform h5 --output E:/Nexus/nexus-erp/.codex/app-repair-20261012/h5-e2e
node scripts/verify-build.mjs --hbuilderx E:/HBuilderX --platform app --output E:/Nexus/nexus-erp/.codex/app-repair-20261012/final-app
node tests/ai-sse-browser-smoke.mjs
```

## 尚未取得验收证据

截至本次设备检查，没有连接 Android 真机或模拟器，也没有 iOS 真机。本次已完成原生通知相关代码、自动化行为验证及原生源码编译；**手机通知栏实际显示、系统权限弹窗、通知点击和真实冷启动仍待真机验收**。没有用 Mock、编译成功或 H5 结果代替这部分验收。

验收范围按用户确认为 App 前台、后台且进程存活。现方案在 WebSocket/JS 仍可运行时创建本地通知；操作系统挂起 JS、断网或冻结后台连接时，进程存在本身不能保证新通知到达。远程离线推送不在本次范围。真机需按 App 仓库通知交付文档逐项记录设备型号、系统、授权状态、应用状态与展示/点击结果。

App 现有后端地址仍使用 HTTP，已检查的 HTTPS 入口不可用，未直接切换地址以免中断当前服务。TLS 部署和真实环境加密传输仍需服务器侧处理，不能据此宣称全部系统漏洞清零。测试命令显式排除了 integration/exclude 标记；没有执行真实数据库集成写入或数据库迁移。编译保留既有 Sass 弃用及动态/静态导入的分块提示，未发现它们导致本次页面错误。

## 工作区和产物保护

只提交本任务文件，保留任务前的 PC 前端、CV/PDA、SQL、文档及 App Markdown 改动。facade 的本任务版本通过独立 Git blob 暂存，工作区原有 bulk 逻辑和相关未跟踪文件保留；没有导入初始化 SQL、执行迁移、推送或部署生产。

本轮生成的 `E:/Nexus/Nexus-App/dist` 已确认是构建产物并排除提交；自动执行审查拒绝删除，返回 `blocked by policy`，所以目录保留。本地验证日志与产物位于 `.codex/app-repair-20261012`，不随源码提交。验证结束后只停止本轮启动的 9092/9093 静态服务。
