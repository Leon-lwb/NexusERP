# NexusERP-plus 前端文件命名规范

> 适用范围：`plus-ui` 项目（Vue 3 + TypeScript）
>
> 目标：消除命名混用，提高代码可维护性，降低跨平台文件系统风险

## 1. 总则

1. **统一优先于完美**：本规范以"最小改动成本 + 最大统一收益"为原则制定
2. **禁止混用**：同一业务模块、同一目录层级下，禁止出现两种及以上命名风格
3. **新文件严格执行**：存量文件在重构或变更时逐步迁移，新创建文件必须 100% 遵守本规范
4. **大小写敏感提示**：Windows 不区分大小写，但 Linux/macOS 区分；统一规范可避免 CI/CD 构建失败

## 2. 命名风格定义

| 风格 | 示例 | 适用场景 |
|-----|------|---------|
| **kebab-case** | `employee-schedule`, `serial-number-rule` | 目录、URL 路由、样式文件、静态资源 |
| **camelCase** | `useDialog`, `fieldConfig`, `authUtils` | JS/TS 函数文件、Hook 文件、工具类文件 |
| **PascalCase** | `FormDesigner`, `EmployeeSchedule` | Vue 单文件组件（SFC）、枚举/常量文件 |
| **全小写** | `index`, `form`, `detail`, `bom`, `erp` | 约定俗成的页面入口文件、业务缩写模块 |

## 3. 目录命名规范

### 3.1 页面目录（`src/views/**`）

- **规范**：使用 **kebab-case** 或 **全小写**
- **规则**：
  - 多单词目录必须使用 kebab-case（短横线连接）
  - 单单词或业务缩写保持全小写
  - **禁止 camelCase**（如 `employeeSchedule` 必须改为 `employee-schedule`）

**示例**：

```
views/
├── erp/
│   ├── bd/
│   │   ├── bom/                    ✅ 全小写（业务缩写）
│   │   ├── serial-number-rule/     ✅ kebab-case（原 serialNumberRule）
│   │   └── workstation/            ✅ 全小写
│   ├── prd/
│   │   ├── prd-plan/               ✅ kebab-case（原 prdplan）
│   │   ├── prd-work/               ✅ kebab-case（原 prdwork）
│   │   └── capacity-calendar/      ✅ kebab-case（原 capacityCalendar）
```

### 3.2 API 目录（`src/api/**`）

- **规范**：与对应 `views` 目录保持**完全一致**
- 保证路由模块、API 模块、页面模块三者路径一一对应

### 3.3 公共组件目录（`src/components/**`）

- **规范**：使用 **PascalCase**
- **原因**：与组件在模板中的使用方式保持一致（`<FormDesigner />`）

```
components/
├── DictTag/           ✅
├── ProcessDesigner/   ✅
└── TableSelector/     ✅
```

### 3.4 布局组件目录（`src/layout/components/**`）

- **规范**：使用 **PascalCase**

### 3.5 其他源码目录

| 目录 | 规范 | 说明 |
|-----|------|------|
| `src/composables/` | camelCase | 组合式函数 |
| `src/directive/` | camelCase | 指令文件 |
| `src/enums/` | PascalCase | 枚举定义 |
| `src/hooks/` | camelCase | Vue Hooks |
| `src/modules/` | PascalCase / camelCase | 复杂模块用大驼峰，简单模块用小驼峰 |
| `src/plugins/` | camelCase | 插件 |
| `src/router/` | camelCase | 路由配置 |
| `src/store/` | camelCase | 状态管理 |
| `src/types/` | camelCase | 类型定义 |
| `src/utils/` | camelCase | 工具函数 |

## 4. 文件命名规范

### 4.1 Vue 单文件组件（`.vue`）

#### 公共/全局组件

- **规范**：**PascalCase**
- **示例**：`FormDesigner.vue`, `TableSelector.vue`, `ShiftManage.vue`
- **禁止**：`basicInfoForm.vue`, `approvalButton.vue`

#### 页面级组件

- **规范**：约定文件名使用**全小写**，业务扩展组件使用 **PascalCase**
- **约定文件名清单**（白名单，允许全小写）：
  - `index.vue` —— 页面入口
  - `form.vue` —— 表单页
  - `detail.vue` —— 详情页
  - `list.vue` —— 列表页
  - `config.vue` —— 配置页
  - `part.vue` —— 局部片段

- **非约定的业务组件**：必须使用 PascalCase
  - ❌ `basicInfoForm.vue` → ✅ `BasicInfoForm.vue`
  - ❌ `genInfoForm.vue` → ✅ `GenInfoForm.vue`
  - ❌ `authRole.vue` → ✅ `AuthRole.vue`

```
views/erp/sch/plan/
├── index.vue              ✅ 约定入口文件
├── ShiftManage.vue        ✅ PascalCase 业务组件
├── TeamManage.vue         ✅ PascalCase 业务组件
└── TeamScheduleManage.vue ✅ PascalCase 业务组件
```

### 4.2 TypeScript / JavaScript 文件

| 文件类型 | 规范 | 示例 |
|---------|------|------|
| 工具函数 | camelCase | `request.ts`, `auth.ts`, `validate.ts` |
| Hook 文件 | camelCase | `useDialog.ts`, `useFlowable.ts` |
| 常量/枚举 | PascalCase | `LanguageEnum.ts`, `ThemeEnum.ts` |
| API 入口 | camelCase | `index.ts`, `chat.ts`, `notification.ts` |
| 类型定义 | camelCase | `types.ts`, `global.d.ts` |

### 4.3 样式文件（`.scss` / `.css`）

- **规范**：**kebab-case** 或 **全小写**
- **示例**：`element-ui.scss`, `variables.module.scss`, `theme.scss`

### 4.4 静态资源（图片、SVG、字体）

- **规范**：**kebab-case**
- **示例**：`login-background.jpg`, `eye-open.svg`, `process-definition.svg`
- **禁止**：大写字母、下划线（已有资源除外）

## 5. 特殊约定

### 5.1 业务缩写处理

ERP 系统中大量业务缩写保持全小写，不进行驼峰转换：

| 缩写 | 含义 | 正确示例 | 错误示例 |
|-----|------|---------|---------|
| `erp` | 企业资源计划 | `erp/` | `ERP/` |
| `bd` | 基础数据 | `bd/` | `bD/`, `Bd/` |
| `prd` | 生产 | `prd-plan/` | `prdPlan/` |
| `pu` | 采购 | `pu/` | `PU/` |
| `sch` | 排程 | `sch/` | `Sch/` |
| `so` | 销售 | `so/` | `SO/` |
| `wm` | 仓库 | `wm/` | `WM/` |

### 5.2 语言文件例外

国际化语言文件允许使用下划线：
- `zh_CN.ts` ✅
- `en_US.ts` ✅

### 5.3 自动导入相关文件

`.eslintrc-auto-import.json` 等框架生成的配置文件不受本规范约束。

## 6. 禁止项清单

以下行为在代码审查中必须打回：

1. ❌ 目录使用 camelCase（如 `employeeSchedule/`, `serialNumberRule/`）
2. ❌ Vue 组件文件使用 camelCase（如 `basicInfoForm.vue`）
3. ❌ 同一目录下同时存在 `PascalCase.vue` 和 `camelCase.vue`
4. ❌ API 目录与 Views 目录命名不一致（如 `views/erp/prd/prd-plan/` 对应 `api/erp/prd/prdplan/`）
5. ❌ 新文件使用非规范命名风格

## 7. 存量迁移优先级

建议按以下顺序逐步整改：

1. **P0 - 核心模块统一**：选择 1-2 个最常改动的模块（如 `sch/`, `prd/`），将目录和文件命名一次性整改到位
2. **P1 - 新功能严格把关**：新需求开发时，关联的新旧文件按规范命名
3. **P2 - 公共组件整改**：`src/components/` 下的 camelCase 文件改为 PascalCase
4. **P3 - 全局扫描**：使用 `scripts/lint-naming.js` 扫描并清理剩余问题

## 8. 配套工具

项目根目录已提供命名扫描脚本：

```bash
# 扫描所有命名问题
node scripts/lint-naming.js

# 输出 JSON 报告（供 CI 使用）
node scripts/lint-naming.js --json > naming-report.json

# 仅扫描 views 目录
node scripts/lint-naming.js --scope views

# 仅扫描 api 目录
node scripts/lint-naming.js --scope api
```

建议在代码提交前或 CI 流程中集成此扫描。
