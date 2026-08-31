<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增市场源</el-button>
      </el-col>
      <right-toolbar :search="false" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list">
      <el-table-column label="市场名称" prop="name" min-width="150" />
      <el-table-column label="市场 URL" prop="url" min-width="300" show-overflow-tooltip />
      <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" prop="status" width="90">
        <template #default="{ row }">
          <dict-tag :options="sys_normal_disable" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleTools(row)">工具列表</el-button>
          <el-button link type="success" @click="handleRefresh(row)" :loading="refreshLoading[row.id]">刷新</el-button>
          <el-button link type="primary" @click="handleUpdate(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑市场源弹窗 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="marketRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="市场名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入市场名称" />
        </el-form-item>
        <el-form-item label="市场 URL" prop="url">
          <el-input v-model="form.url" placeholder="返回工具列表 JSON 的市场地址" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="市场描述（可选）" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 市场工具抽屉 -->
    <el-drawer v-model="toolDrawer.visible" :title="`${toolDrawer.marketName} - 工具列表`" size="700px" append-to-body>
      <div class="tool-drawer-header">
        <span class="tool-drawer-tip">刷新市场工具列表后，可一键加载到本地 ai_tool（加载后默认停用，需在工具管理页手动启用）</span>
        <div>
          <el-button type="success" @click="handleBatchLoad" :disabled="toolDrawer.selection.length === 0">
            批量加载（{{ toolDrawer.selection.length }}）
          </el-button>
          <el-button type="primary" @click="handleRefresh(currentMarketRow!)" :loading="refreshLoading[currentMarketRow?.id!]"> 刷新工具 </el-button>
        </div>
      </div>
      <el-table v-loading="toolDrawer.loading" :data="toolDrawer.list" @selection-change="handleToolSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column label="工具名称" prop="toolName" min-width="160" />
        <el-table-column label="版本" prop="toolVersion" width="100" />
        <el-table-column label="描述" prop="toolDescription" min-width="200" show-overflow-tooltip />
        <el-table-column label="已加载" prop="isLoaded" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isLoaded === '1' ? 'success' : 'info'">{{ row.isLoaded === '1' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.isLoaded !== '1'" link type="primary" @click="handleLoadOne(row)" :loading="loadLoading[row.id]"> 加载 </el-button>
            <span v-else class="loaded-text">已加载（ID: {{ row.localToolId }}）</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import {
  listMarket,
  getMarket,
  addMarket,
  updateMarket,
  delMarket,
  listMarketTools,
  refreshMarketTools,
  loadToolToLocal,
  batchLoadTools,
  type AiMcpMarket,
  type AiMcpMarketTool
} from '@/api/ai/market';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const marketRef = ref<ElFormInstance>();
const refreshLoading = reactive<Record<number, boolean>>({});
const loadLoading = reactive<Record<number, boolean>>({});
const currentMarketRow = ref<AiMcpMarket | null>(null);

const rules = {
  name: [{ required: true, message: '请输入市场名称', trigger: 'blur' }],
  url: [{ required: true, message: '请输入市场 URL', trigger: 'blur' }]
};

const {
  loading,
  list,
  open,
  isViewMode,
  title,
  form,
  getList,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate,
  handleDelete
} = useCrud({
  apis: {
    // 列表无分页展示需求，全量取回（保持原行为：不带查询参数）
    list: async () => {
      const res: any = await listMarket();
      const rows: AiMcpMarket[] = res.rows || res.data || [];
      return { rows, total: rows.length };
    },
    get: getMarket,
    add: addMarket,
    update: updateMarket,
    del: delMarket
  },
  idField: 'id',
  entityName: '市场源',
  formRef: marketRef,
  queryParams: ref({ pageNum: 1, pageSize: 10 }),
  initForm: () => ({
    id: undefined,
    name: '',
    url: '',
    description: '',
    status: '0'
  })
});

/** 市场工具抽屉 */
const toolDrawer = reactive<{
  visible: boolean;
  marketName: string;
  loading: boolean;
  list: AiMcpMarketTool[];
  selection: AiMcpMarketTool[];
}>({
  visible: false,
  marketName: '',
  loading: false,
  list: [],
  selection: []
});

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd();
}

/** 刷新市场工具 */
async function handleRefresh(row: AiMcpMarket) {
  refreshLoading[row.id!] = true;
  try {
    const res = await refreshMarketTools(row.id!);
    const result = res.data || res;
    proxy?.$modal.msgSuccess(`刷新成功：新增 ${result.addedCount}，更新 ${result.updatedCount}`);
    if (toolDrawer.visible && currentMarketRow.value?.id === row.id) {
      await loadMarketTools(row.id!);
    }
  } catch (e: any) {
    proxy?.$modal.msgError('刷新失败: ' + (e.message || '未知错误'));
  } finally {
    refreshLoading[row.id!] = false;
  }
}

/** 打开工具列表抽屉 */
async function handleTools(row: AiMcpMarket) {
  currentMarketRow.value = row;
  toolDrawer.marketName = row.name;
  toolDrawer.visible = true;
  await loadMarketTools(row.id!);
}

/** 加载市场工具列表 */
async function loadMarketTools(marketId: number) {
  toolDrawer.loading = true;
  try {
    const res = await listMarketTools(marketId);
    toolDrawer.list = res.rows || res.data || [];
  } finally {
    toolDrawer.loading = false;
  }
}

/** 一键加载单个工具 */
async function handleLoadOne(row: AiMcpMarketTool) {
  loadLoading[row.id!] = true;
  try {
    const res = await loadToolToLocal(row.id!);
    const toolId = res.data || res;
    proxy?.$modal.msgSuccess(`加载成功，已创建本地工具（ID: ${toolId}），请到工具管理页启用`);
    await loadMarketTools(row.marketId);
  } catch (e: any) {
    proxy?.$modal.msgError('加载失败: ' + (e.message || '未知错误'));
  } finally {
    loadLoading[row.id!] = false;
  }
}

/** 抽屉多选变化 */
function handleToolSelectionChange(selection: AiMcpMarketTool[]) {
  toolDrawer.selection = selection;
}

/** 批量加载 */
async function handleBatchLoad() {
  const ids = toolDrawer.selection.filter((t) => t.isLoaded !== '1').map((t) => t.id!);
  if (ids.length === 0) {
    proxy?.$modal.msgWarning('请选择未加载的工具');
    return;
  }
  try {
    const res = await batchLoadTools(ids);
    const loadedIds = res.data || res || [];
    proxy?.$modal.msgSuccess(`成功加载 ${loadedIds.length} 个工具，请到工具管理页启用`);
    if (currentMarketRow.value) {
      await loadMarketTools(currentMarketRow.value.id!);
    }
  } catch (e: any) {
    proxy?.$modal.msgError('批量加载失败: ' + (e.message || '未知错误'));
  }
}

getList();
</script>

<style scoped>
.tool-drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
}

.tool-drawer-tip {
  font-size: 13px;
  color: #909399;
  flex: 1;
}

.loaded-text {
  font-size: 12px;
  color: #909399;
}
</style>
