# 报表菜单切换空白修复与验证

- 日期：2026-10-18，Asia/Shanghai。
- 工作分支：`plus`；修改基于 `7cb7ec57`，验证工作区变更。
- 范围：前端页面根节点结构、正式布局菜单回归及使用指南。

## 原因与复现

正式 `AppMain.vue` 用 `Transition mode="out-in"` 包裹 `KeepAlive`。私有图片、受控查询、个人仪表盘、订阅、批次五个页面把 `ReportPage` 和 `AclDialog` 放在并列根节点，最终得到 Fragment。切走此类页面时，过渡进入等待离场完成的状态，而 Fragment 无法完成元素离场过渡，目标页面被阻止挂载。

隔离 Edge 回归复用正式 `AppMain`、`createCustomNameComponent`、标签缓存及真实 Vue Router。修复前从私有图片切到导出中心，路由链接已选中导出中心，主体空白，只有 `GET /report/assets`，没有导出列表请求，页面脚本异常为空。测试因12秒仍未出现导出列表而失败，与用户报告一致。此回归不加载 `permission.ts`，证明 `next()` 弃用警告不是复现此故障的必要条件。

修复前证据保留于本机忽略目录 `plus-ui/node_modules/.tmp/report-browser/artifacts-run-nbUcLu/verification.json` 及同目录失败截图。该目录不提交；修复前后命令和行为可用已跟踪回归重新检查。

## 修复

五个页面的 `AclDialog` 移到现有 `ReportPage` 内，保留原权限、弹窗参数和回调，页面最终以同一个 `section` 为根。未增加额外路由层或更改正式缓存、动画及登录鉴权逻辑。

新增菜单浏览器回归：五个来源菜单分别切到导出中心并返回，缓存开启/关闭、随机动画开启/关闭共20个场景。断言真实页面内容、目标列表请求、零组件警告，并继续使用既有脚本异常、网络及横向溢出防线。

## 验证记录

| 命令                                                                                  | 结果                                                           |
| ------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `$env:REPORT_TEST_SUITE='navigation'; node scripts/test-report-browser.mjs`（修复前） | 按预期失败：目标导出列表未挂载，无目标接口请求                 |
| 同命令（修复后）                                                                      | 20通过，0失败，0跳过；`artifacts-run-bUxsCE/verification.json` |
| `npm run test:report`                                                                 | 228通过，0失败，0跳过                                          |
| `npm run test:report:browser`（默认all）                                              | 56通过，0失败，0跳过；`artifacts-run-EuSNTx/verification.json` |
| `npm run type-check`                                                                  | 通过，退出码0                                                  |
| `npm run build:prod`                                                                  | 通过，退出码0；Vite 1m26s，含压缩产物                          |
| 修改文件定向 ESLint、Prettier、`git diff --check`                                     | 均通过，ESLint使用`--max-warnings 0`                           |

构建日志位于本机忽略目录 `plus-ui/node_modules/.tmp/report-navigation-build.log`。本次构建仍输出 Sass旧语法、`sax`的`stream`浏览器外置、store混合静态/动态导入及大分块提示；没有构建错误。本次未调整相关依赖或构建规则。

仅使用合成数据与自有回环端口、独立浏览器 profile。浏览器、Vite及暂存目录由回归脚本清理，不涉及数据库、后端部署或真实打印机验收。两份既有未跟踪 engineering-demo-import 文档保留不动。
