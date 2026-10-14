<template>
  <div class="p-4 report-query">
    <el-alert title="通过批准的ERP逻辑目录取数。发布固定查询后，可在报表设计器选择生成的数据集。" type="info" :closable="false" class="mb-4" />
    <div class="query-grid">
      <el-card shadow="never"
        ><template #header
          ><div class="query-heading">
            <strong>受控查询</strong><el-button @click="startNew">新建</el-button><el-button :loading="loading" @click="load">刷新</el-button>
          </div></template
        >
        <el-alert v-if="failed" title="查询目录载入失败，请重试。" type="error" :closable="false" />
        <el-table :data="resources" highlight-current-row @row-click="edit"
          ><el-table-column prop="title" label="查询" /><el-table-column prop="status" label="状态" width="105"
        /></el-table>
      </el-card>
      <el-card shadow="never"
        ><template #header
          ><div class="query-heading">
            <el-button v-if="resource" v-hasPermi="['report:acl:manage']" :disabled="busy" @click="aclId = resource.id">资源授权</el-button
            ><strong>{{ resource ? '编辑查询' : '新建查询' }}</strong
            ><el-tag v-if="dirty" type="warning">未保存</el-tag>
            <el-button v-hasPermi="['report:query:edit']" :disabled="!entity || !model || busy" @click="preview">样例预览</el-button>
            <el-button
              v-hasPermi="[resource ? 'report:query:edit' : 'report:query:add']"
              :disabled="!entity || !model || busy"
              type="primary"
              @click="save"
              >保存草稿</el-button
            >
            <el-button v-if="resource" v-hasPermi="['report:query:publish']" :disabled="dirty || busy" type="success" @click="publish"
              >发布数据集</el-button
            >
          </div></template
        >
        <el-alert v-if="assembled.error" :title="assembled.error" type="error" :closable="false" />
        <el-form inline
          ><el-form-item label="编码"><el-input v-model="code" :disabled="!!resource || busy" /></el-form-item
          ><el-form-item label="标题"><el-input v-model="title" :disabled="busy" /></el-form-item>
          <el-form-item label="目录版本"
            ><el-select v-model="catalogVersion" :disabled="!!resource || busy" style="width: 110px" @change="resetEntity"
              ><el-option v-for="item in catalogs" :key="item.version" :label="String(item.version)" :value="item.version" /></el-select
          ></el-form-item>
          <el-form-item label="主实体"
            ><el-select v-model="entity" :disabled="busy" style="width: 180px" @change="resetColumns"
              ><el-option v-for="item in catalog?.entities" :key="item.id" :label="item.label" :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="批准关系"
            ><el-select v-model="joins" multiple :multiple-limit="2" :disabled="busy" style="width: 280px"
              ><el-option v-for="item in availableRelations" :key="item.id" :label="item.id + ' · ' + item.cardinality" :value="item.id" /></el-select
          ></el-form-item>
        </el-form>
        <el-divider content-position="left">输出字段（最多40列；数值汇总须保留单位和币种维度）</el-divider>
        <el-button :disabled="busy || columns.length >= 40" @click="addColumn">添加字段</el-button>
        <el-table :data="columns"
          ><el-table-column label="逻辑字段" min-width="190"
            ><template #default="scope"
              ><el-select v-model="scope.row.field" filterable :disabled="busy" @change="chooseField(scope.row)"
                ><el-option v-for="field in fields" :key="field.id" :label="field.label + ' · ' + field.id" :value="field.id" /></el-select></template
          ></el-table-column>
          <el-table-column label="输出别名" min-width="140"
            ><template #default="scope"><el-input v-model="scope.row.alias" :disabled="busy" /></template
          ></el-table-column>
          <el-table-column label="聚合" width="120"
            ><template #default="scope"
              ><el-select v-model="scope.row.aggregate" clearable :disabled="busy"
                ><el-option v-for="fn in aggregates" :key="fn" :label="fn" :value="fn" /></el-select></template
          ></el-table-column>
          <el-table-column label="分组" width="70"
            ><template #default="scope"><el-checkbox v-model="scope.row.group" :disabled="busy" /></template
          ></el-table-column>
          <el-table-column width="65"
            ><template #default="scope"
              ><el-button link type="danger" :disabled="busy" @click="columns.splice(scope.$index, 1)">删除</el-button></template
            ></el-table-column
          >
        </el-table>
        <el-divider content-position="left">筛选参数（最多20项，值统一绑定）</el-divider
        ><el-button :disabled="busy || filters.length >= 20" @click="addFilter">添加筛选</el-button>
        <el-table :data="filters"
          ><el-table-column label="字段" min-width="170"
            ><template #default="scope"
              ><el-select v-model="scope.row.field" filterable :disabled="busy" @change="chooseFilter(scope.row)"
                ><el-option v-for="field in fields" :key="field.id" :label="field.label" :value="field.id" /></el-select></template
          ></el-table-column>
          <el-table-column label="条件" width="115"
            ><template #default="scope"
              ><el-select v-model="scope.row.operator" :disabled="busy"
                ><el-option v-for="op in operators" :key="op" :label="op" :value="op" /></el-select></template
          ></el-table-column>
          <el-table-column label="参数名" min-width="140"
            ><template #default="scope"
              ><el-input v-model="scope.row.parameter" :disabled="busy || ['IS_NULL', 'NOT_NULL'].includes(scope.row.operator)" /></template
          ></el-table-column>
          <el-table-column label="必填" width="70"
            ><template #default="scope"><el-checkbox v-model="scope.row.required" :disabled="busy" /></template
          ></el-table-column>
          <el-table-column label="预览值" min-width="160"
            ><template #default="scope"
              ><el-input v-if="scope.row.parameter" v-model="parameters[scope.row.parameter]" :disabled="busy" placeholder="精确文本参数" /></template
          ></el-table-column>
          <el-table-column width="60"
            ><template #default="scope"
              ><el-button link type="danger" :disabled="busy" @click="filters.splice(scope.$index, 1)">删除</el-button></template
            ></el-table-column
          >
        </el-table>
        <template v-if="result"
          ><el-divider />
          <p>样例 {{ result.rows.length }} 行 / 总计 {{ result.total }} 行 · 数据时间 {{ result.dataTime }}</p>
          <TypedTable :columns="previewColumns" :rows="result.rows"
        /></template>
      </el-card>
    </div>
  </div>
  <AclDialog
    :open="!!aclId"
    resource-type="QUERY"
    :resource-id="aclId"
    @update:open="
      (value) => {
        if (!value) aclId = '';
      }
    "
  />
</template>
<script setup lang="ts">
import AclDialog from '@/components/report/AclDialog.vue';
/** 当前弹窗绑定的资源ID，关闭或切换时由弹窗请求围栏保护。 */
const aclId = ref('');
/** 原生查询构建页面，逻辑模型、参数文本与异步回包独立管理。 */
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  queryCatalogs,
  listQueries,
  createQuery,
  saveQuery,
  publishQuery,
  previewQuery,
  type QueryCatalog,
  type ReportResource,
  type QueryDefinition
} from '@/api/report/query';
import { queryModel, queryParameters, type QueryColumn, type QueryFilter } from './model';
import { RequestGate, normalStyle } from '@/views/report/model';
import TypedTable from '@/components/report/TypedTable.vue';
defineOptions({ name: 'ReportQuery' });
/**
 * 保存当前页面或测试的catalogs、resources、resource状态。
 */
const catalogs = ref<QueryCatalog[]>([]),
  resources = ref<ReportResource[]>([]),
  resource = ref<ReportResource>();
/**
 * 保存当前页面或测试的catalogVersion、entity、joins、columns、filters、parameters状态。
 */
const catalogVersion = ref(1),
  entity = ref(''),
  joins = ref<string[]>([]),
  columns = ref<QueryColumn[]>([]),
  filters = ref<QueryFilter[]>([]),
  parameters = ref<Record<string, string>>({});
/**
 * 保存当前页面或测试的code、title、baseline、loading、busy、failed状态。
 */
const code = ref(''),
  title = ref(''),
  baseline = ref(''),
  loading = ref(false),
  busy = ref(false),
  failed = ref(false);
/**
 * 保存当前页面或测试的result状态。
 */
const result = ref<Awaited<ReturnType<typeof previewQuery>>['data']>();
/**
 * 保存当前页面或测试的gate、editorGate、previewGate、switchGate状态。
 */
const gate = new RequestGate(),
  editorGate = new RequestGate(),
  previewGate = new RequestGate(),
  switchGate = new RequestGate();
/**
 * 保存当前页面或测试的catalog状态。
 */
const catalog = computed(() => catalogs.value.find((c) => c.version === catalogVersion.value));
/**
 * 保存当前页面或测试的joinedEntities状态。
 */
const joinedEntities = computed(() => [
  entity.value,
  ...joins.value.map((id) => catalog.value?.relations.find((r) => r.id === id)?.to).filter(Boolean)
]);
/**
 * 保存当前页面或测试的fields状态。
 */
const fields = computed(() => catalog.value?.entities.filter((e) => joinedEntities.value.includes(e.id)).flatMap((e) => e.fields) || []);
/**
 * 保存当前页面或测试的availableRelations状态。
 */
const availableRelations = computed(() => catalog.value?.relations.filter((r) => joinedEntities.value.includes(r.from)) || []);
/**
 * 保存当前页面或测试的aggregates、operators状态。
 */
const aggregates = ['SUM', 'COUNT', 'COUNT_ROWS', 'MIN', 'MAX', 'AVG'],
  operators = ['EQ', 'NE', 'GT', 'GE', 'LT', 'LE', 'LIKE', 'IS_NULL', 'NOT_NULL'];
/**
 * 保存当前页面或测试的assembled状态。
 */
const assembled = computed(() => {
  try {
    if (
      columns.value.some((c) => !fields.value.some((f) => f.id === c.field)) ||
      filters.value.some((c) => !fields.value.some((f) => f.id === c.field))
    )
      throw new Error('关系变化后存在未在当前实体登记的字段，请删除或重新选择');
    return {
      model: queryModel(
        catalogVersion.value,
        entity.value,
        joins.value,
        columns.value,
        filters.value.map((f) => ({ ...f, parameter: ['IS_NULL', 'NOT_NULL'].includes(f.operator) ? null : f.parameter })),
        []
      ),
      error: ''
    };
  } catch (error) {
    return { model: undefined, error: (error as Error).message };
  }
});
/**
 * 保存当前页面或测试的model状态。
 */
const model = computed(() => assembled.value.model);
/**
 * 保存当前页面或测试的dirty状态。
 */
const dirty = computed(() => entity.value && JSON.stringify({ code: code.value, title: title.value, model: model.value }) !== baseline.value);
/**
 * 保存当前页面或测试的previewColumns状态。
 */
const previewColumns = computed(
  () =>
    result.value?.fields.map((field) => ({
      field: field.name,
      label: field.label,
      width: 140,
      scale: field.type === 'DECIMAL' ? 4 : 0,
      expression: null,
      style: normalStyle()
    })) || []
);
/** 载入目录及有权限资源，旧回包不能替换新刷新。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  try {
    const [a, b] = await Promise.all([queryCatalogs(), listQueries()]);
    if (gate.current(ticket)) {
      catalogs.value = a.data;
      resources.value = b.data;
      failed.value = false;
    }
  } catch {
    if (gate.current(ticket)) failed.value = true;
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 开始新草稿并使上个编辑会话失效。 */
function newQuery() {
  editorGate.invalidate();
  previewGate.invalidate();
  busy.value = false;
  resource.value = undefined;
  code.value = title.value = '';
  entity.value = '';
  joins.value = [];
  columns.value = [];
  filters.value = [];
  parameters.value = {};
  result.value = undefined;
  baseline.value = '';
}
/** 新建草稿前确认尚未保存的编辑，迟到确认不覆盖新会话。 */
async function startNew() {
  const ticket = switchGate.begin();
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的查询设计，确认新建？', '新建查询');
    } catch {
      return;
    }
  }
  if (switchGate.current(ticket)) newQuery();
}
/** 读取固定目录模型，旧契约不自动跟随当前目录。 */
async function edit(row: ReportResource) {
  const ticket = switchGate.begin();
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的查询设计，确认切换？', '切换查询');
    } catch {
      return;
    }
  }
  if (!switchGate.current(ticket)) return;
  newQuery();
  resource.value = row;
  code.value = row.code;
  title.value = row.title;
  const value = JSON.parse(row.json) as QueryDefinition;
  catalogVersion.value = value.catalogVersion;
  entity.value = value.entity;
  joins.value = value.joins;
  columns.value = value.fields.map((column) => ({ ...column, group: value.groupBy.includes(column.field) }));
  filters.value = value.filters.map((filter) => {
    const parameter = value.parameters.find((p) => p.name === filter.parameter);
    return {
      ...filter,
      type: parameter?.type || fields.value.find((f) => f.id === filter.field)?.type || 'STRING',
      required: parameter?.required || false
    };
  });
  baseline.value = JSON.stringify({ code: code.value, title: title.value, model: model.value });
}
/** 目录变化必须显式重建实体选择。 */
function resetEntity() {
  entity.value = '';
  resetColumns();
}
/** 变更主实体时清空不兼容关系、字段及筛选。 */
function resetColumns() {
  joins.value = [];
  columns.value = [];
  filters.value = [];
  parameters.value = {};
  addColumn();
}
/** 添加可信逻辑字段，并生成可修改的合法别名。 */
function addColumn() {
  const field = fields.value[0];
  if (field) columns.value.push({ field: field.id, alias: field.id.replace('.', '_') + '_' + columns.value.length, aggregate: null, group: false });
}
/** 字段选择变化不保留不匹配的聚合和分组。 */
function chooseField(column: QueryColumn) {
  column.aggregate = null;
  column.group = false;
  column.alias = column.field.replace('.', '_');
}
/** 添加参数化筛选，不接受原始SQL。 */
function addFilter() {
  const field = fields.value[0];
  if (field) filters.value.push({ field: field.id, operator: 'EQ', parameter: 'filter' + filters.value.length, type: field.type, required: false });
}
/** 参数类型始终来自可信目录字段。 */
function chooseFilter(filter: QueryFilter) {
  filter.type = fields.value.find((f) => f.id === filter.field)?.type || 'STRING';
}
/** 校验并保存当前会话，迟到请求不影响新编辑。 */
async function save() {
  const input = model.value;
  if (busy.value || !input) return;
  const ticket = editorGate.begin();
  const sent = JSON.stringify({ code: code.value, title: title.value, model: model.value });
  busy.value = true;
  try {
    const response = resource.value ? await saveQuery(resource.value, title.value, input) : await createQuery(code.value, title.value, input);
    if (!editorGate.current(ticket)) return;
    resource.value = response.data;
    baseline.value = sent;
    ElMessage.success('查询草稿已保存');
    await load();
  } catch {
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 发布已经保存的固定修订，不自动发布未保存编辑。 */
async function publish() {
  if (!resource.value || dirty.value || busy.value) return;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    const response = await publishQuery(resource.value);
    if (!editorGate.current(ticket)) return;
    ElMessage.success('数据集 ' + response.data.dataset.code + ' 已发布，可前往报表设计器使用');
    await load();
    const current = resources.value.find((r) => r.id === resource.value?.id);
    if (current) resource.value = current;
  } catch {
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 参数以文本传输，结果使用服务器精确类型。 */
async function preview() {
  const input = model.value;
  if (!input) return;
  const ticket = previewGate.begin();
  try {
    const response = await previewQuery(input, queryParameters(parameters.value));
    if (previewGate.current(ticket)) result.value = response.data;
  } catch {}
}
watch(
  [model, parameters],
  () => {
    previewGate.invalidate();
    result.value = undefined;
  },
  { deep: true, flush: 'sync' }
);
onBeforeRouteLeave(async () => {
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm('有未保存的查询设计，确认离开？', '离开查询');
    return true;
  } catch {
    return false;
  }
});
onMounted(load);
onUnmounted(() => {
  gate.invalidate();
  editorGate.invalidate();
  previewGate.invalidate();
  switchGate.invalidate();
});
</script>
<style scoped>
.query-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}
.query-heading {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
@media (max-width: 1050px) {
  .query-grid {
    grid-template-columns: 1fr;
  }
}
</style>
