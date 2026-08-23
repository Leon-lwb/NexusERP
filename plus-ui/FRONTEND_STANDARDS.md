# plus-ui 前端开发规范

> 2026-08 前端治理后确立的标准。新页面必须遵循；存量页面在触碰时应顺手对齐。
> 门禁：`npm run type-check`（vue-tsc 全量，基线 0 错误）、`npm run lint:eslint`（基线 0 error）。

## 技术栈约定

| 关注点 | 约定 |
|---|---|
| 语言 | 全 TypeScript：`<script setup lang="ts" name="XxxYyy">`（name 供 keep-alive） |
| 表格 | 统一 el-table（vxe-table 已移除） |
| 消息提示 | `proxy?.$modal.msgSuccess/msgError/msgWarning/msgInfo`；确认框 `proxy?.$modal.confirm(content, title?)`；输入框 `proxy?.$modal.prompt(content, title?, options?)` |
| 上传 | OSS 上传用 `<file-upload>` / `<image-upload>`；自定义端点上传（视觉服务 multipart、固件、头像等）允许裸 el-upload + `:http-request` |
| API | 一律走 `src/api/**`（index.ts + types.ts 三件套：VO/Query/Form），视图禁止直接 `import request` |
| 雪花 ID | 前端按字符串处理，类型统一 `number | string`，禁止 `Number(id)` 截断 |
| 实例 | `const { proxy } = getCurrentInstance() as ComponentInternalInstance;` |
| 表单 ref | `const xxxRef = ref<ElFormInstance>()` + `xxxRef.value?.validate()`（勿用 `proxy.$refs['xxx'].validate`） |

## 列表页标准结构（对照典范 `src/views/erp/bd/workshop/index.vue`）

```vue
<el-form :model="queryParams" ref="queryRef" v-show="showSearch" class="list-search">
  <el-row>
    <el-col :span="6"><el-form-item label="名称" prop="name"><el-input ... /></el-form-item></el-col>
    <!-- 更多查询字段 :span="6" 一行四个 -->
    <el-col :span="6" class="search-actions">
      <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
      <el-button icon="Refresh" @click="resetQuery">重置</el-button>
    </el-col>
  </el-row>
</el-form>
<el-card shadow="never">
  <template #header>
    <el-row :gutter="10" class="mb8">
      <!-- 操作按钮（v-hasPermi 按后端菜单权限） -->
      <right-toolbar v-model:show-search="showSearch" @query-table="getList" />
    </el-row>
  </template>
  <el-table v-loading="loading" :data="list" @selection-change="...">
    <el-table-column type="selection" width="55" align="center" />
    <!-- <dict-tag :options="xxxDict" :value="row.status" /> 渲染字典列 -->
  </el-table>
  <pagination v-show="total > 0" v-model:total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
</el-card>
```

## CRUD 逻辑：useCrud（`src/composables/useCrud.ts`）

列表页增删改查统一用 useCrud，页面提供 `apis / idField / entityName / initForm / formRef`，
业务特有逻辑经 `beforeSubmit / afterLoad` 等钩子介入；文档上传轮询、连通性测试等
非标准功能保留本地函数。

## 弹窗规范

- 编辑/新增：el-dialog（宽档 500 / 640 / 800 / 960 / 1200）
- 复杂详情展示：可用 el-drawer
- 查看模式：复用编辑表单 + `isViewMode` 禁用（useCrud 内置）

## 字典与枚举

- 后端有 sys_dict 数据 → `proxy.useDict('dict_type')` + `<dict-tag>`
- 纯前端枚举 → 统一收敛到常量文件（如 `src/views/ai/constants.ts`），禁止页面内散落 `value="1">正常` 硬编码

## 类型定义

- API 三件套：`XxxVO`（含全字段）/ `XxxQuery extends PageQuery` / `XxxForm`（提交字段，可选 ID）
- 后端实际返回/提交而接口缺字段 → 补 `?:` 可选字段，不用 any 硬压
- 模板 el-tag type 函数返回值标注 `ElTagType`；树组件 `:props` 内联对象提取为脚本常量
  （element-plus `TreeOptionProps` 未收录 `value` 键）

## 已知保留项（勿"顺手统一"）

- BPMN 流程设计器（src/package、src/modules）：第三方移植代码，保持 JS，不迁移 TS
- 跨页勾选表格：用 row-key + toggleRowSelection 模拟（vxe-table reserve 的等价替代，见 RoleSelect/UserSelect）
- vue-flow Edge 类型深层泛型：遍历处经 any 中转断言避免 TS2589（见 useFlowStore 注释）
