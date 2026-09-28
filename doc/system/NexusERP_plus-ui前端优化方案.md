# NexusERP plus-ui 前端代码优化方案

> 文档编号: NexusERP-UI-2026-001
> 版本: v1.0
> 状态: 已发布（阶段 0-4 已完成并并入 `plus` 分支；推广批次 1-4 待开始）
> 创建日期: 2026-07-17
> 最后更新: 2026-07-17
> 作者: 王欢
> 关联文档: [../../AGENTS.md](../../AGENTS.md)（前端结构与技术栈）、[2026-09-06 全项目审查报告](./2026-09-06-全项目审查报告.md)（原代码审计报告已清理，本方案的历史前提不可直接视为当前缺陷）

---

## 一、背景与目标

`plus-ui` 是 Nexus-ERP 的前端（Vue 3.5 + Vite 7 + TypeScript + Pinia + Element Plus + UnoCSS），规模约 367 个 `.vue` + 343 个 `.ts`、12 万行代码。优化前经"工程化配置 / 业务页面模式 / API 与状态管理"三个维度系统审查，确认以下核心问题：

| 问题类别 | 现象 |
|---------|------|
| 死代码堆积 | 无引用文件/目录 10+ 处；431 处 `console.log`；`main.ts` 与 `plugins/index.ts` 重复注册同一批 globalProperties |
| 构建零优化 | `vite.config.ts` 无 build 分包配置；全量引入 Element Plus CSS 抵消按需加载 |
| 构建级炸弹 | `ProcessDesigner.vue` 引入未安装的 `codemirror-editor-vue3`，`build:prod` 大概率失败 |
| CRUD 样板大重复 | `getList`×148、`handleDelete`×145、`handleQuery`×141；已写好的 `useWmCrud.ts` 零采用；30 个页面内联同一套"自动生成编码"逻辑 |
| 冗余双写 | `settings.ts` 与 `theme.ts` 两个 store 对 8 个主题字段双写 localStorage；SSE/WebSocket 消息分发逻辑大段复制 |
| i18n 空转 | vue-i18n 仅 31 处 `$t`（login/register/Navbar），业务页 15860 处硬编码中文，仅面向中文市场 |

**决策范围**：死代码清理 + 构建优化 + 公共能力建设（**不含**大文件拆分、不含 TS 严格化）；i18n 仅中文市场，**移除** vue-i18n；策略为试点验证 + 渐进推广（wm 模块先行，不搞运动式重构）。

**目标**：降低首屏体积、消除构建炸弹、沉淀可复用 CRUD 公共能力、统一新页面开发范式，为后续业务页面推广建立标准。

---

## 二、优化总览

### 2.1 阶段全景

| 阶段 | 主题 | 状态 | 对应提交 |
|------|------|------|---------|
| 0 | 安全网（锁文件/构建基线/type-check 基线/冒烟清单） | ✅ | `ff58b24b` |
| 1A/1C | 死代码与未使用依赖清理 | ✅ | `4ecdd764` |
| 1B/1D | 工具注册点清理与 settings/theme 双 store 合并 | ✅ | `4568bfe7` |
| 2 | 构建与性能优化（分包/按需 CSS/依赖处置） | ✅ | `16f99579` |
| 3 | 公共能力建设与 wm 试点（useCrud/AutoGenCodeField） | ✅ | `5d8239f9` |
| 4 | 移除 vue-i18n 国际化 | ✅ | `a6b8459c` |
| —   | 收尾（useCrud 解构折行，prettier 合规） | ✅ | `cbedafb4` |
| 推广批次 1-4 | useCrud/AutoGenCodeField 全量推广 + vxe-table 移除 + 选择弹窗下沉 | ⬜ | 见第五节 |

分支 `feat/frontend-optimization` 共 6 个提交，全部 5 阶段构建通过，已并入 `plus`。

### 2.2 实际收益

| 指标 | 优化前 | 优化后 | 变化 |
|------|--------|--------|------|
| 入口 JS 体积 | 2.84 MB | 1.07 MB | **-62%** |
| CSS 总体积 | 1.4 MB | 1.0 MB | **-27%** |
| type-check 错误数 | 1609 | 1414 | -195（仅作"不增长"基线，本期不治理） |
| 净删除死代码 | — | 1541+ 行 | — |
| 移除依赖包 | — | 167+ 个 | — |

**附带修复的活 bug**：
- `Sidebar/Logo.vue` 使用不存在的 `settingsStore.isDark`，暗色 Logo 永不生效 → 随 store 合并修复。
- `ProcessDesigner.vue` 引入未安装的 `codemirror-editor-vue3`（构建炸弹）→ 补装依赖修复。
- `main.ts` 与 `plugins/index.ts` 重复注册 globalProperties → 统一到 `plugins/index.ts`。
- `settings.ts`/`theme.ts` 双 store 对 8 个主题字段双写 localStorage → 合并为 `theme.ts` 单一来源。

**vendor 分包策略**：echarts / element-plus / three / bpmn 各自独立 chunk，配合路由懒加载按需加载，避免单路由依赖污染首屏。

---

## 三、已完成阶段详情

### 阶段 0：安全网 ✅
- 提交 `package-lock.json` 并将其移出 `.gitignore`；`.eslintrc-auto-import.json` 加入 `.gitignore` 并 `git rm --cached`。
- `package.json` 增加 `"type-check": "vue-tsc --noEmit"`，建立错误数观察基线（不治理）。
- 约定每阶段冒烟清单 8 页面：登录、首页、system/user、wm/warehouse、wm/storagelocation、workflow/model（流程设计器）、tool/gen、bd/material。

### 阶段 1：死代码与依赖清理 ✅
**1A 纯删除**：`tool/gen/online` 下重复 hooks、`utils/index.ts`（无 `@/utils` 引用）、`RuoYiDoc`/`RuoYiGit`/`iFrame` 组件、`components/Process/` 下 7 个仅自引用组件、`html/ie.html` + IE 判断脚本、注释死代码块（`erp/pu/order`、`erp/pu/requisition` 等）。
**1B 注册点清理**：globalProperties 去重，`resetForm` 注册挪入 `plugins/index.ts`；删除 `validUsername`/`sprintf` 等零调用工具；清扫 431 处 `console.log`（保留 warn/error，eslint 加 `no-console` 防回潮）；SSE/WebSocket 分发逻辑去重。
**1C 依赖清理**：移除 `hook-fetch`、`vue-element-plus-x`、`screenfull`、`vitest`、`lodash-es`（debounce 改用 `@vueuse/core` 的 `useDebounceFn`）；`@types/three` 移至 devDependencies。
**1D 双 store 合并**：8 个主题字段消费方迁移到 `theme.ts`；`settings.ts` 瘦身为仅 `title/setTitle/showSettings/dynamicTitle`；删除双写迁移逻辑，保留一次性旧 key 迁移。

### 阶段 2：构建与性能优化 ✅
落点 `vite.config.ts` 与样式入口：
- `manualChunks` 分包（vendor-vue / vendor-element / vendor-echarts / vendor-bpmn / vendor-three / vendor-editor / vendor-utils）。
- 移除全量 `element-plus/dist/index.css`，改为按需 CSS；**编程式组件**（ElMessage/ElNotification/ElLoading/ElMessageBox）样式在 `main.ts` 手动引入。
- 移除 `animate.css`（全项目 0 处 `animate__` 使用）。
- `.env.development` 增加 `VITE_APP_PROXY_TARGET`，代理目标环境化。
- `optimizeDeps` 清理无效 glob；`unplugin-icons` 的 `autoInstall` 改 `false`；`compression.ts` 保留（自包含、env 门控、gzip+brotli 双产物）。

### 阶段 3：公共能力建设 + wm 试点 ✅
- `src/hooks/useDialog.ts` → `src/composables/useDialog.ts`，删除 `src/hooks/` 目录。
- 基于 `useWmCrud.ts` 蓝本重写为泛型 `src/composables/useCrud.ts`（蓝本已删除）。
- 在 `src/components/AutoGenCodeField/index.vue` 实现"编码输入 + 自动生成开关"组件。
- 全局 `.list-search` 样式已在 `src/assets/styles/index.scss`，删除 95 个文件中逐字重复的 scoped 块。
- wm 试点 3 页面：`storagearea`、`warehouse`、`storagelocation`（含 3D 抽屉，最复杂）。

### 阶段 4：移除 vue-i18n ✅
- `login.vue`/`register.vue`/`Navbar.vue` 的 31 处 `$t` → 硬编码中文（取值自原 `lang/zh_CN.ts`）。
- 删除 `components/LangSelect/`、`src/lang/`、`enums/LanguageEnum.ts`、`store/modules/app.ts` 中 language 相关逻辑、`types/module.d.ts` 的 `$t` 声明。
- `App.vue` 改为直接 `import zhCN from 'element-plus/es/locale/lang/zh-cn'`。
- `main.ts` 删 i18n import 与 `app.use(i18n)`；`package.json` 卸载 vue-i18n；`optimizeDeps` 删 `'vue-i18n'`。

---

## 四、新建公共能力与使用规范（后续开发必读）

> **新页面开发强制采用以下范式**。旧页面保持现状、渐进迁移，不搞运动式重构。

### 4.1 useCrud —— 通用 CRUD Composable

**位置**：`src/composables/useCrud.ts`

封装列表页标准增删改查（列表/分页/新增/修改/查看/删除/导出/多选），消除各页面重复样板。页面提供 API、主键字段、实体名、表单 ref、查询参数与初始化表单，业务特有逻辑通过钩子介入。

**配置项**（`UseCrudOptions<T, Q>`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `apis` | `CrudApis<T, Q>` | `{ list, get, add, update, del }`，对应页面 API 层 |
| `idField` | `keyof T & string` | 主键字段名 |
| `entityName` | `string` | 实体中文名（标题/确认文案） |
| `formRef` | `Ref<FormInstance>` | 表单 ref，页面 `ref<FormInstance>()` 直接传入（消灭 `proxy.$refs[字符串]`） |
| `queryParams` | `Ref<Q>` | 查询参数（含分页，归页面自持） |
| `initForm` | `() => Partial<T>` | 表单初始化函数 |
| `exportUrl` / `exportFileName` | `string?` | 导出 URL 与文件名前缀（默认用 entityName） |
| `beforeList` | `(query: Q) => void?` | 列表查询前钩子（路由参数预填、默认过滤） |
| `onReset` | `() => void?` | 表单重置钩子（清理级联、文件列表等） |
| `beforeSubmit` | `(form) => void?` | 提交前钩子（自动生成编码清空等） |
| `afterLoad` | `(data: T) => void?` | 详情加载后钩子（回填级联数据） |

**返回**：`loading / list / total / open / isViewMode / title / ids / single / multiple / showSearch / form / getList / handleQuery / reset / cancel / handleAdd / handleUpdate / handleView / submitForm / handleDelete / handleExport / handleSelectionChange`。

- `handleAdd(row?, extraInit?)` / `handleUpdate` / `handleView` 均支持 `extraInit` 回调（加载下拉选项、预填字段）。
- 一期**不接管** `resetQuery`（页面差异大，归页面自持）。
- 导出直接 `import { download } from '@/utils/request'`；消息显式 import `ElMessage`/`ElMessageBox`。

**接入示例**（摘自 `views/erp/wm/warehouse/index.vue`）：

```ts
const { loading, list, total, open, title, form, isViewMode,
  getList, handleQuery, handleAdd, handleUpdate, handleView,
  submitForm, handleDelete, handleExport, handleSelectionChange } = useCrud<WarehouseVO, WarehouseQuery>({
  apis: { list: listWarehouse, get: getWarehouse, add: addWarehouse, update: updateWarehouse, del: delWarehouse },
  idField: 'warehouseId',
  entityName: '仓库',
  formRef,
  queryParams,
  initForm: () => ({ /* 空表单 */ }),
  exportUrl: '/erp/wm/warehouse/export',
  beforeList: (q) => { /* 路由预填等 */ },
  afterLoad: (data) => { /* 级联回填 */ },
  beforeSubmit: () => codeRef.value?.applyAutoGen(),   // 与 AutoGenCodeField 协同
});
```

### 4.2 AutoGenCodeField —— 编码输入 + 自动生成开关

**位置**：`src/components/AutoGenCodeField/index.vue`

统一封装"编码输入框 + 自动生成开关"，替代 30+ 页面内联的 `autoGenFlag` 逻辑。开关打开时调 `previewCode(ruleCode)` 预览编码并禁用输入；提交前调 `applyAutoGen()` 清空编码，由后端 `isBlank` 兜底生成，**避免预览消耗流水号**。

| Props | 类型 | 说明 |
|-------|------|------|
| `modelValue` | `string \| null \| undefined` | 编码值（v-model） |
| `ruleCode` | `string`（必填） | 编码规则码，对应 `previewCode` 的 ruleCode |
| `disabled` | `boolean?` | 禁用（查看模式） |
| `placeholder` | `string?` | 默认"请输入编码" |

| Emits | 说明 |
|-------|------|
| `update:modelValue` | 编码值变更 |
| `auto-gen-change(flag)` | 开关变更 |

| Expose | 说明 |
|--------|------|
| `applyAutoGen()` | 提交前调用：开关开则清空编码交后端兜底 |
| `reset()` | 复位开关（对话框关闭/新增时调用） |
| `autoGenFlag` | 开关状态 |

**接入示例**（摘自 `views/erp/wm/warehouse/index.vue`）：

```vue
<auto-gen-code-field
  ref="codeRef"
  v-model="form.warehouseCode"
  rule-code="WAREHOUSE_CODE"
  :disabled="isViewMode"
  placeholder="请输入仓库编码"
/>
```

```ts
const codeRef = ref();
// useCrud 配置中：
beforeSubmit: () => codeRef.value?.applyAutoGen(),
```

### 4.3 .list-search 全局样式

全局样式已存在于 `src/assets/styles/index.scss`。新列表页搜索栏直接用 `class="list-search"`，**勿再写 scoped 重复块**。试点范例：`views/erp/wm/storagearea/index.vue`、`views/erp/wm/warehouse/index.vue`。

### 4.4 hooks → composables 迁移

`src/hooks/` 目录已删除，`useDialog` 等迁入 `src/composables/`。新增组合式函数统一放 `src/composables/`，不要再建 `src/hooks/`。

### 4.5 编程式组件 CSS 引入约定

Element Plus 已移除全量 `index.css`，改为按需 CSS。**编程式组件**（`ElMessage`/`ElNotification`/`ElLoading`/`ElMessageBox`）样式在 `main.ts` 手动引入。**新增编程式组件调用时，若样式丢失，需在 `main.ts` 补对应 `element-plus/theme-chalk/xxx.css`。**

### 4.6 settings/theme 单 store 约定

主题相关字段（明暗、品牌色、页签模式、固定头部等）统一在 `store/modules/theme.ts`，`store/modules/settings.ts` 仅保留 `title/setTitle/showSettings/dynamicTitle`。**新增主题字段写入 `theme.ts`，勿再向 `settings.ts` 添加。**

---

## 五、后续推广路线（⬜ 待开始）

> 不在本期实施，按批次渐进推广。每批次独立验证：`npm run build:prod` 成功 + 冒烟 8 页面 + type-check 错误数不增。

### 批次 1：wm/bd 列表页接入 + 选择弹窗下沉
- wm 剩余约 20 个列表页 + bd 5 页接入 `useCrud` + `AutoGenCodeField`（参照 `warehouse`/`storagearea` 范例，每页预期净减 100-150 行）。
- `storagelocation`（含 3D 抽屉，最复杂）作为边界容纳力验证页。
- 抽取 `VendorSelectDialog`/`ClientSelectDialog`，参照 `MaterialSelectDialog` 范式，替换约 15 处内联供应商/客户选择弹窗。

### 批次 2：pu/so/prd/sch 接入
- pu/so/prd/sch 约 40 页接入 `useCrud`。
- 评估 `useCrud` 支持"表单页"变体（非弹窗式 CRUD）。

### 批次 3：system/mes 接入 + 页面模板规范
- system/mes 接入 `useCrud`。
- 沉淀页面模板规范：新页面统一 plus-ui 卡片风，旧页面不动。

### 批次 4：vxe-table 移除 + autoGen 全量替换 + eslint 固化
- **vxe-table 移除**（当前 `package.json` 仍为 `4.18.1`，残留在 5 文件）：
  - `views/erp/wm/productionissue/form.vue`（25 处 vxe 引用，核心业务表单）—— 先改 `el-table` 再卸载依赖。
  - `components/UserSelect/index.vue`（14 处）、`components/RoleSelect/index.vue`（13 处）—— 改 `el-table`（checkbox 选择 + 分页，各约 30 行模板）。
  - `assets/styles/variables.module.scss`（12 处）、`main.ts`（5 处：`VXETable` import / `setConfig` / `app.use` / css）。
  - 改造完成后 `main.ts` 删 VXETable 注册与 css，`npm uninstall vxe-table`（省 500KB+ vendor）。

  > ⚠️ **方向冲突提示**：[../PLANS.md](../PLANS.md) 的 P2-14 曾计划"MES el-table -> vxe-table 迁移"，与本方案"移除 vxe-table"方向相反。本方案以**移除 vxe-table** 为准（统一技术栈为 el-table + vxe-table 仅省 500KB+ vendor、且全局仅 5 文件残留），推广前需与 MES 团队对齐、废止 P2-14。
- `autoGenFlag` 剩余内联页面全量替换为 `AutoGenCodeField`。
- eslint 规则固化（`no-console` 等防回潮规则入配置）。

### 推广前需"先核实再动手"事项

| 事项 | 核实方法 | 默认动作 |
|------|---------|---------|
| `demo` 目录删除 | 后端查 `SELECT path, component FROM sys_menu WHERE component LIKE '%demo%'` | `sys_menu` 有 `menu_id` 1500/1506/20201 引用，删页面需同步清菜单 |
| vxe-table 改造量 | 评估 `productionissue/form.vue` 25 处改 `el-table` 的工作量 | 推荐改 `el-table` 后移除依赖，保底方案为 manualChunks 独立 `vendor-vxe` |

---

## 六、关键文件索引

| 文件 | 作用 |
|------|------|
| `plus-ui/src/composables/useCrud.ts` | 通用 CRUD Composable（新页面必用） |
| `plus-ui/src/components/AutoGenCodeField/index.vue` | 编码输入 + 自动生成开关组件 |
| `plus-ui/src/assets/styles/index.scss` | `.list-search` 全局样式（约 228 行起） |
| `plus-ui/src/store/modules/theme.ts` | 主题字段单一来源（settings/theme 合并主战场） |
| `plus-ui/src/store/modules/settings.ts` | 仅保留 title/showSettings/dynamicTitle |
| `plus-ui/src/main.ts` | globalProperties 注册点、编程式组件 CSS、vxe-table 注册（待移除）交汇处 |
| `plus-ui/vite.config.ts` | 阶段 2 全部构建配置落点（manualChunks/optimizeDeps） |
| `plus-ui/src/views/erp/wm/warehouse/index.vue` | wm 试点主页面、useCrud + AutoGenCodeField 接入范例 |
| `plus-ui/src/views/erp/wm/storagearea/index.vue` | 试点范例（含 beforeList 路由预填） |
| `plus-ui/src/views/erp/wm/storagelocation/index.vue` | 最复杂试点（route.query 预填、仓库树级联、3D 抽屉） |
| `plus-ui/src/package/designer/ProcessDesigner.vue` | codemirror 构建炸弹现场（已修复） |

> 路径均相对项目根；本文档位于 `doc/system/`，从本文档到 `plus-ui/` 为 `../../plus-ui/`。

---

## 七、验证方式

1. **每阶段/每批次结束**：`npm run build:prod` 成功 + `npm run dev` 启动正常 + `npm run type-check` 错误数 ≤ 基线（1414）。
2. **冒烟清单 8 页面**（功能与视觉走查）：登录、首页、system/user、wm/warehouse、wm/storagelocation、workflow/model（流程设计器，验证 bpmn）、tool/gen、bd/material。
3. **构建产物**：`vite preview` 验证分包产物无循环引用报错；产物体积对比基线达标（入口 JS ≤ 1.07 MB）。
4. **推广批次专项**：接入 `useCrud` 的页面全功能回归（增删改查/查看模式/导出/自动编码开关/级联）；记录每页净减行数。

---

## 附：决策与约束

- **范围边界**：不含大文件拆分、不含 TS 严格化（type-check 仅作"错误数不增长"观察基线）。
- **i18n**：仅中文市场，vue-i18n 已移除；如需恢复多语言需重新评估。
- **RSA 私钥仍在 `.env`**：RuoYi 既定设计，仅做混淆，非本方案范围。
- **渐进推广**：旧页面保持现状，按批次迁移，不搞运动式重构；新页面强制采用第四节范式。
