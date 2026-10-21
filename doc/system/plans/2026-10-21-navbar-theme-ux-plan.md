# 导航栏与主题设置 Implementation Plan

> 执行方式：在当前会话逐项实施已确认方案；完成后进行代码审查。用户已授权最终提交 PR。

**Goal:** 改善导航栏空间分配、小屏幕功能可达性，以及主题和布局设置的一致性。

**Architecture:** Navbar 负责入口与会话操作，ThemeSettings 负责统一设置；Pinia 继续作为主题唯一状态来源。Layout 统一响应导航模式和设备变化，TopBar 使用组件原生溢出处理。

**Tech Stack:** Vue 3、TypeScript、Pinia、Element Plus、SCSS、SVG sprite、Node test runner。

---

### Task 1: 回归与交互测试

- [x] 新建 plus-ui/scripts/test-navbar-theme.mjs，编译实际 Vue 组件，在离线 Vue renderer 中验证状态同步、抽屉分组、移动端入口与管理员 ID；仅替换 API、浏览器和全局 Store 边界。
- [x] 运行 node --test scripts/test-navbar-theme.mjs，记录功能缺失导致的失败。

### Task 2: 统一主题设置

- [x] 修改 ThemeSettings/index.vue：使用外观/布局/偏好分组、原生选择按钮、关闭入口、自动保存说明、响应式宽度和固定底部操作。
- [x] 将颜色等控件直接绑定 themeStore，补齐组件尺寸和动态标题。恢复默认调用 resetConfig、恢复应用尺寸与动态标题，不重载页面。
- [x] 修改 layout/index.vue 和 components/index.ts，移除重复 Settings 抽屉与导出。统一导航/设备变化时的侧栏和菜单处理。

### Task 3: 导航栏与图标

- [x] 改造 Navbar.vue 的伸缩区域、原生工具按钮、更多菜单、租户选择弹窗和头像菜单，保留会话和通知逻辑。
- [x] 在 assets/icons/svg 下添加 nav- 前缀线性图标，仅用于本次组件。
- [x] TopBar/index.vue 使用 Element Plus 原生 ellipsis 处理实际容器溢出，移除基于窗口宽度的手工裁剪。

### Task 4: 验证与交付

- [x] npm run test:layout（必须有非零用例）；定向 prettier --check 和 eslint；npm run type-check；npm run test:unit；npm run build:prod。
- [x] 使用真实组件与合成状态进行浏览器视觉及交互检查，覆盖桌面、375px、深色和高对比度。
- [x] 审查差异并修复范围内问题；更新此计划的执行结果。
- [x] 通过用户配置的 gitee-wzqaqa SSH 别名推送 origin/codex/wzq-dev；提交身份已按用户提供的账号邮箱修正。
- [ ] 创建面向 plus 的 PR：创建页要求浏览器登录，已准备标题与说明，等待用户在内置浏览器登录 WZQAQA。

实施证据：[验证报告](2026-10-21-navbar-theme-ux-verification-report.md)。全量单测存在 5 项可在未修改基线复现的 SQL 断言失败，不将其记为全部通过。
