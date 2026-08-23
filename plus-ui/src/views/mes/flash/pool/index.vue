<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="资源池名称" prop="poolName">
        <el-input v-model="queryParams.poolName" placeholder="请输入" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="资源类型" prop="resourceType">
        <el-select v-model="queryParams.resourceType" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="dict in resourceTypeOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:flash:pool:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="handleDelete()" v-hasPermi="['mes:flash:pool:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
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
      <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['mes:flash:pool:edit']">编辑</el-button>
          <el-button link type="success" icon="Upload" @click="handleImport(scope.row)" v-hasPermi="['mes:flash:pool:import']">导入资源</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:flash:pool:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="title" v-model="open" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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

    <!-- 导入资源弹窗 -->
    <el-dialog :title="'导入资源 - ' + (importPool.poolName || '')" v-model="importOpen" width="560px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="每行一个资源值(如MAC地址), 支持换行/逗号分隔, 池内自动去重"
      />
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="00:1A:2B:3C:4D:5E&#10;00:1A:2B:3C:4D:5F&#10;..." />
      <template #footer>
        <el-button type="primary" @click="submitImport">导 入</el-button>
        <el-button @click="importOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesFlashPool">
import {
  listPool,
  addPool,
  updatePool,
  delPool,
  importPoolItems,
  type MesResourcePoolVO,
  type MesResourcePoolForm,
  type MesResourcePoolQuery
} from '@/api/mes/flash/pool';

const { proxy } = getCurrentInstance() as any;
const { mes_resource_type } = toRefs<any>(proxy?.useDict('mes_resource_type'));
const resourceTypeOptions = computed(() => mes_resource_type.value || []);

const list = ref<MesResourcePoolVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const total = ref(0);
const open = ref(false);
const title = ref('');
const importOpen = ref(false);
const importText = ref('');
const importPool = ref<any>({});
const form = ref<MesResourcePoolForm>({ poolName: '', resourceType: 'MAC', source: 'INTERNAL', status: 'ENABLED' });
const queryParams = ref<MesResourcePoolQuery>({ pageNum: 1, pageSize: 10 });
const rules = {
  poolName: [{ required: true, message: '资源池名称不能为空', trigger: 'blur' }],
  resourceType: [{ required: true, message: '资源类型不能为空', trigger: 'change' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listPool(queryParams.value);
    list.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy?.resetForm('queryRef');
  handleQuery();
};
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((i) => i.poolId);
};
const handleAdd = () => {
  form.value = { poolName: '', resourceType: 'MAC', source: 'INTERNAL', status: 'ENABLED' };
  title.value = '新增资源池';
  open.value = true;
};
const handleEdit = (row: MesResourcePoolVO) => {
  form.value = {
    poolId: row.poolId,
    poolName: row.poolName,
    resourceType: row.resourceType,
    source: row.source,
    externalConfig: row.externalConfig,
    status: row.status,
    remark: row.remark
  };
  title.value = '编辑资源池';
  open.value = true;
};
const submitForm = () => {
  proxy?.$refs.formRef.validate(async (valid: boolean) => {
    if (!valid) return;
    if (form.value.poolId) {
      await updatePool(form.value);
      proxy?.$modal.msgSuccess('修改成功');
    } else {
      await addPool(form.value);
      proxy?.$modal.msgSuccess('新增成功');
    }
    open.value = false;
    getList();
  });
};
const cancel = () => {
  open.value = false;
};
const handleDelete = async (row?: MesResourcePoolVO) => {
  const delIds = row ? [row.poolId] : ids.value;
  await proxy?.$modal.confirm(row ? `确认删除「${row.poolName}」?` : `确认删除选中的 ${ids.value.length} 项?`);
  await delPool(delIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};
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

getList();
</script>
