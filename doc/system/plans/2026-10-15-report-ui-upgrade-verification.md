# 报表前端界面升级验证记录

日期：2026-10-15。分支：codex/report-module-plan；基线：49778ea3。本记录覆盖报表前端升级与隔离验证；Git 提交与生产部署分别记录。

## 实施结果

覆盖报表中心、数据集、设计器、运行、导出、发布历史、受控查询、交叉报表、私有图片、批量单据、个人仪表盘、报表订阅和我的交付，共 13 个页面。

| 范围       | 实际改动                                                                                                                                                |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 统一工作台 | ReportPage 页头和操作插槽；报表限定样式；沿用 Element Plus、UnoCSS 和系统主题变量，无新增运行依赖                                                       |
| 报表中心   | 当前可见资源计数、状态卡、目录、组合搜索、TABLE / DOCUMENT / PIVOT 筛选、清空筛选、列表/卡片、更多操作菜单；快捷入口仅连接实际注册路由，排除404兜底匹配 |
| 设计器     | 字段标签/绑定名/类型检索、点击或键盘添加、画布元素键盘选择；配置与操作分区；窄屏草稿预览改为上下排列                                                    |
| 运行页     | 查询条件与结果分区、尚未运行和失败反馈、翻页独立加载、结果导出入口；保留参数失效、快照和精确值语义                                                      |
| 扩展页面   | 统一页头、中文资源/任务状态、表单及内容分节、目录载入失败和重试反馈、明确空列表文案；图片和批次分开输入与目录                                           |
| 仪表盘     | 图表随组件高度伸缩、空图表提示；主题切换重绘并清理观察器，不重新取数或计算业务指标                                                                      |
| 弹窗及主题 | 注册、模板包和ACL弹窗限制在视口范围；参数表单自然换行；深色面板标题、提示和图表使用对应主题                                                             |
| 注释和指南 | 新增展示契约、函数及状态使用中文 JSDoc；更新报表模块使用指南                                                                                            |

## 自动验证

以下命令在 D:/workspace/nexus-erp/plus-ui 执行。测试均针对本次工作区版本重新运行。

| 命令 / 检查                             | 结果                                                                                            |
| --------------------------------------- | ----------------------------------------------------------------------------------------------- |
| npm run test:report                     | 117 项通过，0 失败，0 跳过；相对基线新增 22 项                                                  |
| npm run test:unit                       | Node 测试运行器累计 1872 项通过，0 失败，0 跳过，包含上述报表专项；其余传统脚本也以成功状态结束 |
| npm run type-check                      | 通过，vue-tsc 无错误                                                                            |
| npm run build:prod                      | 通过，Vite 输出 built in 1m                                                                     |
| 对修改的 Vue / TS / MJS 执行 npx eslint | 通过                                                                                            |
| 对修改文件执行 npx prettier --check     | 通过                                                                                            |
| git diff --check                        | 通过                                                                                            |

新回归覆盖：组合筛选和清空、精确字符串ID、交叉设计路由、可见资源计数、字段搜索不改变草稿、定义/运行/翻页失败重试、迟到请求失效、目录请求错误恢复、未知状态兼容、实际注册路由与404兜底区分，以及六组菜单权限。

菜单权限测试编译实际 ReportActions 模板，使用仓库真实 checkPermi，仅替换合成用户存储和用于隔离弹层的组件。覆盖 edit + add 双权限、发布/下线、ACL/归档分离、无权限和超级管理员；浏览器夹具另使用真实 hasPermi 指令及合成角色复核可见入口。

构建仍出现原有 BPMN 样式的 Sass @import 弃用、Browserslist 数据时效、sax 的 stream 外置、权限 store 的动态/静态导入以及较大 vendor 分包提示；这些来源未在本次变更中修改，构建成功，不把提示描述成零警告。

## 视觉与交互验证

本地隔离 Vite 服务运行实际 Vue、Element Plus、UnoCSS 和 ECharts 组件；所有 API 被本地合成响应替换。未调用真实业务接口、写数据库或发送通知。截图明确标注“合成数据”。临时浏览器及端口18087预览服务已清理。

- 1440 × 1000 桌面视口、390 × 844 手机视口检查全部13个页面，页面 scrollWidth 未超出 clientWidth；垂直滚动条使实际内容宽度分别出现1425/1440和375/390。复杂表格在自身容器内滚动。
- 1024 × 900 与手机宽度复核草稿预览：内容网格为单列，两块面板上下排列；手机保留目录选择和带原权限的新建目录入口。
- 实际点击验证列表/卡片切换、搜索、更多菜单、字段检索、草稿预览、运行结果、仪表盘选择、订阅编辑及弹窗打开。合成大数9007199254740993.005完整显示。
- 额外检查深色主题、空目录、筛选无结果、加载中、请求失败及最小权限角色。仪表盘图表随主题重绘，面板标题对比度已复核。
- 手机注册与模板导入弹窗宽度约358px，未超过390px视口；仅打开检查，没有提交注册、导入或授权。

主要视觉证据：

| 场景           | 截图                                                                                                                                                                                                                                                                                    |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 报表中心桌面   | [列表](../assets/report-ui/center-desktop-synthetic.jpg) / [卡片](../assets/report-ui/center-cards-synthetic.jpg)                                                                                                                                                                       |
| 手机与深色     | [手机](../assets/report-ui/center-mobile-synthetic.jpg) / [深色](../assets/report-ui/center-dark-synthetic.jpg)                                                                                                                                                                         |
| 权限、空与失败 | [最小权限菜单](../assets/report-ui/center-permissions-synthetic.jpg) / [空目录](../assets/report-ui/center-empty-synthetic.jpg) / [失败](../assets/report-ui/center-error-synthetic.jpg) / [加载](../assets/report-ui/center-loading-synthetic.jpg)                                     |
| 设计器         | [字段搜索](../assets/report-ui/designer-desktop-synthetic.jpg) / [预览](../assets/report-ui/designer-preview-synthetic.jpg) / [手机](../assets/report-ui/designer-mobile-synthetic.jpg) / [深色](../assets/report-ui/designer-dark-synthetic.jpg)                                       |
| 运行与仪表盘   | [运行](../assets/report-ui/viewer-desktop-synthetic.jpg) / [仪表盘](../assets/report-ui/dashboard-desktop-synthetic.jpg) / [手机](../assets/report-ui/dashboard-mobile-synthetic.jpg) / [深色](../assets/report-ui/dashboard-dark-synthetic.jpg)                                        |
| 扩展与弹窗     | [受控查询](../assets/report-ui/query-desktop-synthetic.jpg) / [订阅编辑](../assets/report-ui/subscription-editor-mobile-synthetic.jpg) / [注册弹窗](../assets/report-ui/dataset-dialog-mobile-synthetic.jpg) / [模板导入弹窗](../assets/report-ui/template-dialog-mobile-synthetic.jpg) |

## 审查及边界

只读代码审查发现并修复了三个本次回归：下拉项Fragment导致权限指令失效、预览媒体查询指向旧容器、隐藏侧栏时丢失新建目录。复核确认三项消除，未发现新的确定问题；随后自查补充未安装扩展菜单时的快捷入口保护及回归用例。

后端API、SQL、数据源、租户/资源ACL规则、发布版本和修订规则均未修改。未迁移现有开发库或生产库，未开启订阅调度、真实通知或独立查询账户。本次证据属于前端自动测试与隔离合成视觉验证；真实账号、真实业务数据联调及生产部署验收仍需在目标环境进行。

代码入口见 plus-ui/src/components/report/ReportPage.vue、ReportStatus.vue、ReportActions.vue、report-ui.scss 和 plus-ui/src/views/report/presentation.ts；页面按既有 report 子目录组织。实施过程与范围见[实施计划](2026-10-15-report-ui-upgrade-plan.md)，操作说明见[使用指南](../guides/NexusERP_自研报表模块使用指南.md)。
