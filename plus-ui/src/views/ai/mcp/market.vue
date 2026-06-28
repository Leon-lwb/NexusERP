<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>MCP 市场管理</span>
          <el-button type="primary" @click="handleAdd">新增市场源</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="list">
        <el-table-column label="市场名称" prop="name" min-width="150" />
        <el-table-column label="市场 URL" prop="url" min-width="300" show-overflow-tooltip />
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" prop="status" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'success' : 'danger'">
              {{ row.status === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleTools(row)">工具列表</el-button>
            <el-button link type="success" @click="handleRefresh(row)" :loading="refreshLoading[row.id]">刷新</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑市场源弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
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
          <el-button type="primary" @click="handleRefresh(currentMarketRow)" :loading="refreshLoading[currentMarketRow?.id]"> 刷新工具 </el-button>
        </div>
      </div>
      <el-table v-loading="toolDrawer.loading" :data="toolDrawer.list" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column label="工具名称" prop="toolName" min-width="160" />
        <el-table-column label="版本" prop="toolVersion" width="100" />
        <el-table-column label="描述" prop="toolDescription" min-width="200" show-overflow-tooltip />
        <el-table-column label="已加载" prop="isLoaded" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isLoaded === '1' ? 'success' : 'info'">
              {{ row.isLoaded === '1' ? '是' : '否' }}
            </el-tag>
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

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const list = ref<AiMcpMarket[]>([]);
const refreshLoading = reactive<Record<number, boolean>>({});
const loadLoading = reactive<Record<number, boolean>>({});
const currentMarketRow = ref<AiMcpMarket | null>(null);

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const initFormData: AiMcpMarket = {
  id: undefined,
  name: '',
  url: '',
  description: '',
  status: '1'
};

const data = reactive<{ form: AiMcpMarket; rules: any }>({
  form: { ...initFormData },
  rules: {
    name: [{ required: true, message: '请输入市场名称', trigger: 'blur' }],
    url: [{ required: true, message: '请输入市场 URL', trigger: 'blur' }]
  }
});

const { form, rules } = toRefs(data);
const formRef = ref<ElFormInstance>();

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

/** 查询市场源列表 */
const getList = async () => {
  loading.value = true;
  try {
    const res = await listMarket();
    list.value = res.rows || res.data || [];
  } finally {
    loading.value = false;
  }
};

/** 取消 */
const cancel = () => {
  reset();
  dialog.visible = false;
};

/** 重置表单 */
const reset = () => {
  form.value = { ...initFormData };
  formRef.value?.resetFields();
};

/** 新增 */
const handleAdd = () => {
  reset();
  dialog.visible = true;
  dialog.title = '新增市场源';
};

/** 编辑 */
const handleEdit = async (row: AiMcpMarket) => {
  reset();
  const res = await getMarket(row.id!);
  Object.assign(form.value, res.data);
  dialog.visible = true;
  dialog.title = '编辑市场源';
};

/** 提交 */
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.id ? await updateMarket(form.value) : await addMarket(form.value);
      proxy?.$modal.msgSuccess('操作成功');
      dialog.visible = false;
      await getList();
    }
  });
};

/** 删除 */
const handleDelete = async (row: AiMcpMarket) => {
  await proxy?.$modal.confirm('是否确认删除市场源"' + row.name + '"？');
  await delMarket([row.id!]);
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
};

/** 刷新市场工具 */
const handleRefresh = async (row: AiMcpMarket) => {
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
};

/** 打开工具列表抽屉 */
const handleTools = async (row: AiMcpMarket) => {
  currentMarketRow.value = row;
  toolDrawer.marketName = row.name;
  toolDrawer.visible = true;
  await loadMarketTools(row.id!);
};

/** 加载市场工具列表 */
const loadMarketTools = async (marketId: number) => {
  toolDrawer.loading = true;
  try {
    const res = await listMarketTools(marketId);
    toolDrawer.list = res.rows || res.data || [];
  } finally {
    toolDrawer.loading = false;
  }
};

/** 一键加载单个工具 */
const handleLoadOne = async (row: AiMcpMarketTool) => {
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
};

/** 选择变化 */
const handleSelectionChange = (selection: AiMcpMarketTool[]) => {
  toolDrawer.selection = selection;
};

/** 批量加载 */
const handleBatchLoad = async () => {
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
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

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
