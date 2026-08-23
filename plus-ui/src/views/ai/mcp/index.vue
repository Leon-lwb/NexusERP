<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="info" plain icon="Refresh" @click="handleSync">同步内置工具</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增工具</el-button>
      </el-col>
      <right-toolbar :search="false" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list">
      <el-table-column label="工具名称" prop="toolName" min-width="150" />
      <el-table-column label="显示名称" prop="displayName" min-width="120" />
      <el-table-column label="描述" prop="description" min-width="250" show-overflow-tooltip />
      <el-table-column label="类型" prop="toolType" width="100">
        <template #default="{ row }">
          <el-tag :type="getAiOptionTagType(MCP_TOOL_TYPE_TAG_OPTIONS, row.toolType)">{{ row.toolType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100">
        <template #default="{ row }">
          <dict-tag :options="MCP_TOOL_STATUS_OPTIONS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="handleToggleStatus(row)">
            {{ row.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
          <el-button link type="info" @click="handleTest(row)" :disabled="row.toolType === 'BUILTIN'" :loading="testLoading[row.id]">
            测试
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)" :disabled="row.toolType === 'BUILTIN'"> 删除 </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="toolRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="工具名称" prop="toolName">
          <el-input v-model="form.toolName" placeholder="请输入工具名称（snake_case）" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入工具描述（AI 理解用途）" />
        </el-form-item>
        <el-form-item label="工具类型" prop="toolType">
          <el-select v-model="form.toolType" placeholder="请选择工具类型" style="width: 100%" :disabled="isEdit">
            <el-option v-for="item in MCP_TOOL_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.toolType === 'LOCAL'" label="配置 JSON" prop="configJson">
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="6"
            placeholder='本地工具配置，如：{"command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path"]}'
          />
        </el-form-item>
        <el-form-item v-if="form.toolType === 'REMOTE'" label="配置 JSON" prop="configJson">
          <el-input v-model="form.configJson" type="textarea" :rows="6" placeholder='远程工具配置，如：{"baseUrl": "http://localhost:3001/mcp"}' />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in MCP_TOOL_STATUS_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
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
  </div>
</template>

<script setup lang="ts">
import { listTool, getTool, addTool, updateTool, delTool, toggleToolStatus, syncBuiltinTools, testTool } from '@/api/ai/mcp';
import type { AiTool } from '@/api/ai/mcp';
import { useCrud } from '@/composables/useCrud';
import { MCP_TOOL_STATUS_OPTIONS, MCP_TOOL_TYPE_OPTIONS, MCP_TOOL_TYPE_TAG_OPTIONS, getAiOptionTagType } from '@/views/ai/constants';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const toolRef = ref<ElFormInstance>();
const testLoading = reactive<Record<number, boolean>>({});

const rules = {
  toolName: [
    { required: true, message: '请输入工具名称', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '工具名称格式不正确（小写字母开头，仅允许小写字母、数字、下划线）', trigger: 'blur' }
  ],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  toolType: [{ required: true, message: '请选择工具类型', trigger: 'change' }]
};

const {
  loading,
  list,
  open,
  isViewMode,
  title,
  form,
  getList,
  reset,
  submitForm: rawSubmitForm,
  cancel,
  handleAdd: rawHandleAdd
} = useCrud({
  apis: {
    // 列表接口返回全量数据（无分页），适配为 useCrud 需要的 rows/total 结构
    list: async () => {
      const res: any = await listTool();
      const rows: AiTool[] = res.data || res;
      return { rows, total: rows.length };
    },
    get: getTool,
    add: addTool,
    update: updateTool,
    del: delTool
  },
  idField: 'id',
  entityName: '工具',
  formRef: toolRef,
  queryParams: ref({ pageNum: 1, pageSize: 10 }),
  initForm: () => ({
    id: undefined,
    toolName: '',
    displayName: '',
    description: '',
    toolType: 'BUILTIN',
    configJson: '',
    status: 'ENABLED'
  })
});

/** 是否编辑态（工具名称/类型不可修改） */
const isEdit = computed(() => form.value.id != null);

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd();
}

/** 编辑（直接使用行数据回填，避免依赖 ai:mcp:query 权限） */
function handleEdit(row: AiTool) {
  reset();
  form.value = { ...row };
  open.value = true;
  title.value = '编辑工具';
}

/** 提交表单（先校验配置 JSON 格式） */
function submitForm() {
  if (form.value.configJson && form.value.toolType !== 'BUILTIN') {
    try {
      JSON.parse(form.value.configJson);
    } catch {
      proxy?.$modal.msgError('配置 JSON 格式不正确');
      return;
    }
  }
  return rawSubmitForm();
}

/** 删除（内置工具不允许删除） */
async function handleDelete(row: AiTool) {
  if (row.toolType === 'BUILTIN') {
    proxy?.$modal.msgWarning('内置工具不允许删除');
    return;
  }
  await proxy?.$modal.confirm('是否确认删除工具"' + row.displayName + '"？');
  await delTool([row.id!]);
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
}

/** 切换状态 */
async function handleToggleStatus(row: AiTool) {
  const newStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  const action = newStatus === 'ENABLED' ? '启用' : '停用';
  await proxy?.$modal.confirm(`是否确认${action}工具"${row.displayName}"？`);
  await toggleToolStatus(row.id!, newStatus);
  await getList();
  proxy?.$modal.msgSuccess(`${action}成功`);
}

/** 同步内置工具 */
async function handleSync() {
  await proxy?.$modal.confirm('是否确认同步内置工具到数据库？');
  await syncBuiltinTools();
  await getList();
  proxy?.$modal.msgSuccess('同步成功');
}

/** 测试工具连接 */
async function handleTest(row: AiTool) {
  if (row.toolType === 'BUILTIN') {
    proxy?.$modal.msgWarning('内置工具无需测试连接');
    return;
  }
  testLoading[row.id!] = true;
  try {
    const res = await testTool(row.id!);
    const result = res.data || res;
    if (result.success) {
      proxy?.$modal.msgSuccess(result.message || '连接测试成功');
    } else {
      proxy?.$modal.msgError(result.message || '连接测试失败');
    }
  } catch (e: any) {
    proxy?.$modal.msgError('测试失败: ' + (e.message || '未知错误'));
  } finally {
    testLoading[row.id!] = false;
  }
}

getList();
</script>
