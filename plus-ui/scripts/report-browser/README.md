# 隔离报表浏览器回归

在 `plus-ui/` 执行 `npm run test:report:browser`。使用现有 Vite、Vue、Element Plus 和 Node 原生 WebSocket，不安装额外依赖；Node 必须提供全局 `WebSocket`（验证环境为 v24.21.0）。默认探测本机 Edge/Chrome，也可通过 `REPORT_TEST_BROWSER` 指定可执行文件的完整路径；显式指定无效路径时直接非零退出。

HTTP 服务以 Vite 中间件模式监听 `127.0.0.1:0`，CDP 通过独立 profile 的 `DevToolsActivePort` 获取操作系统分配的端口。不加载项目 Vite 配置、环境文件或代理；真实报表 API、权限检查和组件保留，只替换身份来源与 HTTP 传输层。Mock 校验 code、完整字符串 ID、固定版本、权限、租户及幂等内容；意外接口或后端网络请求均使回归失败。独立浏览器禁止下载到用户目录。

每次运行的 profile 和 Vite cache 位于 `node_modules/.tmp/report-browser/run-*/`，成功、断言失败、拒绝或可处理的中断都走 `finally`，只关闭本次浏览器进程树、Vite 及端口，再移除整个运行目录。不会按浏览器进程名清理，不接触用户服务或现有浏览器。

截图与 `verification.json` 保留在独立的 `node_modules/.tmp/report-browser/artifacts-run-*/`，终端会输出目录路径。JSON 含实际完成的场景、请求记录、异常、浏览器 PID、动态端口及清理结果。失败时另存当前页面截图和焦点诊断。所有内容均为合成 UI 证据，不能作为真实后端取数、PDF 生成、生产部署或打印机验收结论；这些忽略目录不得加入版本管理。

截图前逐帧等待字体、图片、活动过渡/动画、加载层及离场菜单结束，并要求布局和计算颜色连续稳定；正常打开的Drawer和PDF弹窗保留。12秒仍未稳定则失败，不禁用动画或过滤页面异常。每张截图在JSON中记录等待时间、曾观察到的动画/遮罩和最终计算样式。深色截图要求可见卡片背景亮度低于0.18、普通标题文字对比度至少4.5:1；业务抽屉同时验证共享`--report-*`变量、零padding和`report-section-heading`的flex排版。

`scenarios.mjs` 覆盖基础组件，`business-scenarios.mjs` 覆盖真实单张/批次入口与恢复，`designer-scenarios.mjs` 覆盖真实设计器整页、键盘、媒体属性及窄屏侧栏。390px以独立文档首次加载，专注编辑的布局切换仍在同一真实画布上检查。每个场景检查脚本异常、未处理拒绝和页面横向溢出，不能通过过滤或清空异常来放行。

`navigation-scenarios.mjs` 使用正式 `AppMain`、命名路由包装器及标签缓存，覆盖图片、查询、仪表盘、订阅、批次分别切到导出中心并返回。缓存开启/关闭与随机动画开启/关闭共20个场景，要求页面实际可见、导出列表完成请求、Vue组件警告为零。可设置 `REPORT_TEST_SUITE=navigation` 单独运行；默认 `all` 也执行。只替换身份和接口，保留正式布局的 `Transition mode="out-in"` 与 `KeepAlive`，防止只测独立页面遗漏跨菜单空白问题。

默认all最后的`lifecycle-scenario.mjs`沿同一模板实际编辑并点击“保存并发布”，核对真实PUT `{ revision, dsl }`与POST `{ revision }`各一次、草稿修订递增及旧发布快照不变；随后目录、单张run和多选批次全部使用新不可变versionId，并各自成功领取带PDF签名的合成Blob。JSON的`lifecycle`记录版本、修订、run/batch与关闭后Blob回收证据。此文件为Mock生成的PDF，只验证UI协议和预览生命周期。

并行调整设计器时可临时设置 `REPORT_TEST_SUITE=business` 仅跑业务入口。JSON会明确记录此范围及实际执行数；默认 `all` 才覆盖全部回归。业务范围还验证清空或超过50项勾选后恢复原批次，以及已缓存PDF在逐项刷新为BLOCKED后立即关闭、回收URL和隐藏原单据标签。PDF弹窗覆盖刷新按钮时使用真实按钮事件模拟异步刷新，不直接修改组件状态。

可用 `REPORT_TEST_FAULT` 注入以下负向情况；每种命令均应返回非零并保存清理证据：

- `exception`：浏览器脚本抛错。
- `rejection`：浏览器未处理 Promise 拒绝。
- `network`：外部网络和真实后端路径请求，分别由 CSP 和 CDP 阻断。
- `overflow`：页面意外横向溢出。
- `node-rejection`：驱动进程未处理 Promise 拒绝。
- `interrupt`：驱动的可处理 SIGINT 中断路径。
- `cdp-disconnect`：意外断开CDP，拒绝所有等待命令并按自有PID清理浏览器进程树。

例如 PowerShell：

```powershell
$env:REPORT_TEST_FAULT = 'network'
npm run test:report:browser
Remove-Item Env:REPORT_TEST_FAULT
```

浏览器或操作系统强制终止 Node 进程不属于可处理信号，无法由 JavaScript 的 `finally` 提供清理保证。
