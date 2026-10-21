# 导航栏与主题设置优化验证报告

日期：2026-10-21。环境：Windows、本地独立克隆 D:/workspace/wzq；分支 codex/wzq-dev，基线 2537fadb。本文仅记录本地验证，不代表生产部署或真实业务验收。

## 实施结果

- Navbar 使用统一尺寸的原生按钮和 19 个 nav- 前缀 SVG；低频操作收进更多菜单，移动端保留搜索、通知、主题及完整工具入口。
- 合并 Settings 与 ThemeSettings，提供外观、布局、偏好三个分组。直接读取 Pinia，配置即时生效并自动保存；支持关闭、完成、恢复默认和取消恢复。
- 保留头像 URL 安全与失败回退、通知未读和跳转、租户切换与清空、个人中心、退出等行为。兼容字符串超级管理员 ID；普通用户不加载租户管理接口；请求失败恢复最后成功的租户选择。
- 导航按实际容器宽度折叠。手机端顶部和混合布局使用完整侧栏。针对 Element Plus 动态省略菜单补齐 Tab、Enter、方向键、Escape 和焦点归还；每层子菜单提供与实际 index 一致的 DOM 映射。
- 原有主题存储键和后端接口保持兼容，无后端或数据库修改，无新增运行时依赖。
- 按用户明确要求，导航栏 Gitee 点击入口继续使用 https://gitee.com/NexusERP/nexus-erp；开发远程 origin 仍使用 WZQAQA/nexus-erp。

## 自动检查

所有命令在 plus-ui 下运行。

| 检查                                                                                                                                                                          | 结果                                                                                                 |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| npm run test:layout                                                                                                                                                           | 10/10 通过；覆盖同步、移动端、设置恢复/取消、完整菜单、租户权限和失败回滚                            |
| node --test tests/ai-knowledge-mcp/display-usability.test.mjs scripts/test-navbar-theme.mjs                                                                                   | 33/33 通过，含 23 项原有头像/显示回归                                                                |
| 定向 prettier --check、eslint                                                                                                                                                 | 通过，未批量格式化无关文件                                                                           |
| npm run type-check                                                                                                                                                            | 通过，构建生成自动导入声明后无类型错误                                                               |
| npm run build:prod                                                                                                                                                            | 通过                                                                                                 |
| npm run test:unit                                                                                                                                                             | 未全部通过：到仓储规则套件的 436 项中 434 通过，2 项 SQL 菜单种子断言失败；此前 93 项和 127 项均通过 |
| npm run test:competition-demo（单独补跑）                                                                                                                                     | 999/999 通过                                                                                         |
| node --test --test-concurrency=1 scripts/test-mes-spare-management.mjs scripts/test-mes-tooling-reference.mjs scripts/test-list-initialization.mjs scripts/test-cv-defect.mjs | 91/91 通过                                                                                           |
| npm run test:report（单独补跑）                                                                                                                                               | 335 项中 332 通过，3 项 SQL 基线断言失败                                                             |

初次新克隆缺少被忽略的自动导入声明，直接 type-check 出现大量全局名称未定义；完成正常构建生成声明后重新检查通过。此环境准备过程没有修改业务代码以绕开类型检查。

新增组件测试初次运行 5 项全部因预期行为缺失失败，实施后通过；手机混合导航另有一个先失败后修复的运行时回归。

## 基线失败确认

在未修改的 D:/workspace/nexus-erp/plus-ui（同为基线 2537fadb）复现：

1. node --test scripts/test-wm-rule-import-casing.mjs：6 项中 4 通过，2 失败，缺少主安装 SQL 中的菜单 2621 和 2616。
2. node --test scripts/test-report-schema.mjs：12 项中 9 通过，3 失败，涉及核心表约束、种子 INSERT 列名和 E2–E7 报表表结构断言。

这些失败均来自本次未修改的 SQL/静态基线，未运行数据库导入或迁移。全量单测的 && 链会在仓储断言处停止，不能据此声称整个 test:unit 通过。

## 浏览器与审查

使用实际 Layout、Navbar、ThemeSettings、Sidebar、TopNav、TopBar 和安装的 Element Plus，外部 API/用户/业务页面替换为合成数据边界，未访问真实业务数据。

- 1440px、1024px 桌面与 375px 手机宽度；手机工具按钮全部位于屏幕内，无横向溢出。
- 浅色、深色、高对比度；设置抽屉适应宽度，内容滚动与底部操作分离。
- 两个入口、三个分组、关闭/焦点归还、恢复默认确认、即时显示和取消恢复。
- 手机顶部布局可展开完整菜单；手机混合导航使用完整路由的运行时回归通过。
- 顶部及混合导航：Tab 到省略入口、Enter 展开、方向键切换、Escape 关闭；鼠标展开后 Escape 亦关闭并归还焦点。
- 独立审查发现的省略入口焦点问题已修复；按具体弹层的 ARIA 关联定位菜单 owner，避免沿用历史键盘索引。最终静态复核未发现有明确证据的剩余 P1/P2 问题。

未验证：真实后端在线联调、真实账号/租户切换和生产部署。

## PR 准备

建议标题：feat(ui): 优化导航栏与主题布局设置

说明：窄屏原本隐藏大部分工具，主题与布局分散在两个面板且存在状态镜像。此次统一工具栏与设置入口，增加响应式布局、SVG 图标和键盘操作，直接绑定主题状态并保持自动保存。验证结果和基线失败见本报告。

开发远程为 origin（WZQAQA/nexus-erp），PR 目标为 NexusERP/nexus-erp 的 plus。最初 HTTPS 推送因凭据过期返回 403；后续用户已配置 gitee-wzqaqa SSH 别名并提供账号提交邮箱。2026-10-21 已仅在本仓库修正两次未推送提交的身份，校验代码树完全一致后成功推送，功能与链接修正提交分别为 aa332824、46437d32。用户完成浏览器登录后，已于 2026-10-21 创建 PR [!2 优化导航栏与主题布局设置](https://gitee.com/NexusERP/nexus-erp/pulls/2)，从 WZQAQA/nexus-erp 的 codex/wzq-dev 合入 NexusERP/nexus-erp 的 plus。PR 处于开启状态，等待维护者审查/测试，尚未合并或部署。
