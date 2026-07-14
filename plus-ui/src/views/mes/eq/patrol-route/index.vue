<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6"
            ><el-form-item label="路线名称" prop="routeName"
              ><el-input v-model="queryParams.routeName" placeholder="请输入" clearable @keyup.enter="handleQuery" /></el-form-item
          ></el-col>
          <el-col :span="6"
            ><el-form-item label="状态" prop="status"
              ><el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%"
                ><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select></el-form-item
          ></el-col>
          <el-col :span="6" class="search-actions"
            ><el-form-item
              ><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button
              ><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item
            ></el-col
          >
        </el-row>
      </el-form>
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5"
          ><el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:eq:patrol:route:add']">新增路线</el-button></el-col
        >
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="路线名称" prop="routeName" min-width="120" />
        <el-table-column label="描述" prop="description" min-width="120" show-overflow-tooltip />
        <el-table-column label="频率" prop="frequency" align="center" width="80" />
        <el-table-column label="状态" prop="status" align="center" width="80">
          <template #default="s"
            ><el-tag :type="s.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{
              s.row.status === 'ACTIVE' ? '启用' : '停用'
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="操作" align="center" width="160" :show-overflow-tooltip="false">
          <template #default="s">
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(s.row)" v-hasPermi="['mes:eq:patrol:route:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(s.row)" v-hasPermi="['mes:eq:patrol:route:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
    <el-dialog v-model="dialog.visible" :title="form.routeId ? '编辑路线' : '新增路线'" width="600px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-form-item label="路线名称" prop="routeName"><el-input v-model="form.routeName" placeholder="路线名称" clearable /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="设备ID列表"><el-input v-model="form.equipmentIds" placeholder="JSON数组,如 [1,2,3]" clearable /></el-form-item>
        <el-form-item label="检查项"
          ><el-input v-model="form.checkItems" placeholder='JSON数组,如 ["温度","油位"]' type="textarea" :rows="2"
        /></el-form-item>
        <el-form-item label="频率"
          ><el-select v-model="form.frequency" style="width: 100%"
            ><el-option label="每日" value="DAILY" /><el-option label="每周" value="WEEKLY" /><el-option label="每月" value="MONTHLY" /></el-select
        ></el-form-item>
        <el-form-item label="状态"
          ><el-select v-model="form.status" style="width: 100%"
            ><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select
        ></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" @click="submitForm">确认</el-button></template
      >
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="MesEqPatrolRoute">
import { listPatrolRoute, getPatrolRoute, addPatrolRoute, updatePatrolRoute, delPatrolRoute } from '@/api/mes/eq/patrol';
import type { EqPatrolRouteVO, EqPatrolRouteQuery, EqPatrolRouteForm } from '@/api/mes/eq/patrol';
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const dataList = ref<EqPatrolRouteVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const queryParams = ref<EqPatrolRouteQuery>({ pageNum: 1, pageSize: 10, routeName: undefined, status: undefined });
const dialog = reactive({ visible: false });
const formRef = ref();
const formRules = { routeName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }] };
const form = ref<EqPatrolRouteForm>({} as EqPatrolRouteForm);
const getList = async () => {
  loading.value = true;
  try {
    const r = await listPatrolRoute(queryParams.value);
    dataList.value = r.rows;
    total.value = r.total;
  } finally {
    loading.value = false;
  }
};
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};
const resetForm = () => {
  form.value = { routeName: '', description: '', equipmentIds: '', checkItems: '', frequency: 'DAILY', status: 'ACTIVE', remark: '' };
};
const handleOpenAdd = () => {
  resetForm();
  dialog.visible = true;
};
const handleOpenEdit = async (row: EqPatrolRouteVO) => {
  const r = await getPatrolRoute(row.routeId);
  form.value = { ...r.data };
  dialog.visible = true;
};
const submitForm = async () => {
  await formRef.value?.validate();
  form.value.routeId ? await updatePatrolRoute(form.value) : await addPatrolRoute(form.value);
  proxy?.$modal.msgSuccess(form.value.routeId ? '编辑成功' : '新增成功');
  dialog.visible = false;
  getList();
};
const handleDelete = async (row: EqPatrolRouteVO) => {
  await proxy?.$modal.confirm('确认删除路线"' + row.routeName + '"？');
  await delPatrolRoute([row.routeId]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};
onMounted(() => getList());
</script>
<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
