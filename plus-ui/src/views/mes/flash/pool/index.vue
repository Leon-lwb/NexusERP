<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="资源池名称" prop="poolName">
            <el-input v-model="queryParams.poolName" placeholder="请输入" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="资源类型" prop="resourceType">
            <el-select v-model="queryParams.resourceType" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="dict in resourceTypeOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:flash:pool:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['mes:flash:pool:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:flash:pool:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="资源池编码" align="center" prop="poolCode" min-width="150" />
      <el-table-column label="名称" align="center" prop="poolName" min-width="140" />
      <el-table-column label="资源类型" align="center" prop="resourceType" width="100">
        <template #default="scope">
          <dict-tag :options="resourceTypeOptions" :value="scope.row.resourceType" />
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" prop="source" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.source === 'EXTERNAL' ? 'warning' : 'success'">{{
            scope.row.source === 'EXTERNAL' ? '外部REST' : '内部导入'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="总数/已用" align="center" width="110">
        <template #default="scope">
          <span>{{ scope.row.usedCount || 0 }} / {{ scope.row.totalCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ENABLED' ? 'success' : 'info'">{{ scope.row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="310" class-name="small-padding fixed-width" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:flash:pool:edit']">编辑</el-button>
          <el-button link type="success" icon="Upload" @click="handleImport(scope.row)" v-hasPermi="['mes:flash:pool:import']">导入资源</el-button>
          <el-button link type="warning" icon="Connection" @click="handleAllocate(scope.row)" v-hasPermi="['mes:flash:pool:edit']"
            >手工分配</el-button
          >
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:flash:pool:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加/修改资源池对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="poolRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="资源池名称" prop="poolName">
          <el-input v-model="form.poolName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="资源类型" prop="resourceType">
          <el-select v-model="form.resourceType" placeholder="请选择" style="width: 100%">
            <el-option v-for="dict in resourceTypeOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-radio-group v-model="form.source">
            <el-radio value="INTERNAL">内部导入</el-radio>
            <el-radio value="EXTERNAL">外部REST</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="外部配置" v-if="form.source === 'EXTERNAL'">
          <el-input v-model="form.externalConfig" type="textarea" :rows="3" placeholder='JSON: {"url":"http://...","token":"..."}' />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 导入资源弹窗(批量导入资源项, 特殊功能本地保留) -->
    <el-dialog :title="'导入资源 - ' + (importPool.poolName || '')" v-model="importOpen" width="500px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="每行一个资源值(如MAC地址), 支持换行/逗号分隔, 池内自动去重"
      />
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="00:1A:2B:3C:4D:5E&#10;00:1A:2B:3C:4D:5F&#10;..." />
      <template #footer>
        <el-button type="primary" @click="submitImport()">导 入</el-button>
        <el-button @click="importOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 手工分配资源项(MES-TEST-05: 池自动分配取错号 / 需指定某号时人工补分配) -->
    <el-dialog :title="'手工分配资源 - ' + (allocatePool.poolName || '')" v-model="allocateOpen" width="520px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="从池内取一个可用资源项分配给指定 SN(资源类型需与刷写类型匹配, 如 MAC 池配 MAC 类型)"
      />
      <el-form label-width="90px">
        <el-form-item label="SN" required>
          <el-select
            v-model="allocateForm.snId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchSn"
            :loading="snLoading"
            placeholder="输入 SN 编码搜索"
            style="width: 100%"
            @change="handleSnChange"
          >
            <el-option
              v-for="sn in snOptions"
              :key="sn.snId"
              :label="sn.snCode + (sn.materialName ? ' / ' + sn.materialName : '')"
              :value="sn.snId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="allocateResult" :column="1" border size="small">
        <el-descriptions-item label="资源值">{{ allocateResult.resourceValue }}</el-descriptions-item>
        <el-descriptions-item label="资源状态">{{ allocateResult.status }}</el-descriptions-item>
        <el-descriptions-item label="绑定SN ID">{{ allocateResult.boundSnId ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" :loading="allocateLoading" @click="submitAllocate()">分 配</el-button>
        <el-button @click="allocateOpen = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesFlashPool">
import { listPool, getPool, addPool, updatePool, delPool, importPoolItems, allocatePoolResource } from '@/api/mes/flash/pool';
import type { MesResourcePoolVO, MesResourcePoolForm, MesResourcePoolQuery, MesResourceItemVO } from '@/api/mes/flash/pool';
import { listSnOptions } from '@/api/mes/flash/flash';
import type { SnOptionVO } from '@/api/mes/flash/flash';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_resource_type } = toRefs<any>(proxy?.useDict('mes_resource_type'));
const resourceTypeOptions = computed(() => mes_resource_type.value || []);

const poolRef = ref<FormInstance>();

const queryParams = ref<MesResourcePoolQuery>({
  pageNum: 1,
  pageSize: 10,
  poolName: undefined,
  resourceType: undefined,
  status: undefined
});

const rules: ElFormRules = {
  poolName: [{ required: true, message: '资源池名称不能为空', trigger: 'blur' }],
  resourceType: [{ required: true, message: '资源类型不能为空', trigger: 'change' }]
};

const {
  loading,
  list,
  total,
  open,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listPool, get: getPool, add: addPool, update: updatePool, del: delPool },
  idField: 'poolId',
  entityName: '资源池',
  formRef: poolRef,
  queryParams,
  initForm: () =>
    ({
      poolId: undefined,
      poolName: '',
      resourceType: 'MAC',
      source: 'INTERNAL',
      externalConfig: undefined,
      status: 'ENABLED',
      remark: undefined
    }) as MesResourcePoolForm
});

function handleAdd() {
  rawHandleAdd();
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row);
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

// ============ 导入资源(批量导入资源项) ============
const importOpen = ref(false);
const importText = ref('');
const importPool = ref<any>({});

const handleImport = (row: MesResourcePoolVO) => {
  importPool.value = row;
  importText.value = '';
  importOpen.value = true;
};
const submitImport = async () => {
  if (!importText.value.trim()) {
    proxy?.$modal.msgError('导入内容不能为空');
    return;
  }
  const count = await importPoolItems(importPool.value.poolId, importText.value);
  proxy?.$modal.msgSuccess(`导入成功 ${count} 条`);
  importOpen.value = false;
  getList();
};

// ============ 手工分配资源项(MES-TEST-05) ============
const allocateOpen = ref(false);
const allocatePool = ref<any>({});
const allocateForm = reactive<{ snId?: number | string; snCode?: string }>({ snId: undefined, snCode: undefined });
const allocateResult = ref<MesResourceItemVO | null>(null);
const allocateLoading = ref(false);
const snLoading = ref(false);
const snOptions = ref<SnOptionVO[]>([]);

const handleAllocate = (row: MesResourcePoolVO) => {
  allocatePool.value = row;
  allocateForm.snId = undefined;
  allocateForm.snCode = undefined;
  allocateResult.value = null;
  snOptions.value = [];
  allocateOpen.value = true;
};

const searchSn = async (query: string) => {
  if (!query) {
    snOptions.value = [];
    return;
  }
  snLoading.value = true;
  try {
    const res = await listSnOptions(query);
    snOptions.value = (res.data as SnOptionVO[]) || [];
  } finally {
    snLoading.value = false;
  }
};

const handleSnChange = (snId: number | string) => {
  allocateForm.snCode = snOptions.value.find((s) => String(s.snId) === String(snId))?.snCode;
};

const submitAllocate = async () => {
  if (allocateForm.snId == null || allocateForm.snId === '') {
    proxy?.$modal.msgError('请选择 SN');
    return;
  }
  allocateLoading.value = true;
  try {
    const res = await allocatePoolResource(allocatePool.value.poolId, allocateForm.snId, allocateForm.snCode);
    allocateResult.value = (res.data as MesResourceItemVO) || null;
    proxy?.$modal.msgSuccess('分配成功');
    getList();
  } catch (e) {
    proxy?.$modal.msgError('分配失败: ' + (e as Error).message);
  } finally {
    allocateLoading.value = false;
  }
};

getList();
</script>
