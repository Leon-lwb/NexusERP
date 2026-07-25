<template>
  <div class="p-2">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="工单编号" prop="woNo">
              <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="产品名称" prop="materialName">
              <el-input v-model="queryParams.materialName" placeholder="请输入产品名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="woStatus">
              <el-select v-model="queryParams.woStatus" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="dict in prd_wo_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 操作栏 -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:pro:work:add']">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:pro:work:remove']"
            >删除</el-button
          >
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="workOrderList" @selection-change="handleSelectionChange" show-overflow-tooltip>
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="工单编号" prop="woNo" />
        <el-table-column label="产品编码" prop="materialCode" />
        <el-table-column label="产品名称" prop="materialName" min-width="120" show-overflow-tooltip />
        <el-table-column label="计划数量" prop="plannedQty" align="right" />
        <el-table-column label="车间" prop="workshopName" min-width="120" show-overflow-tooltip />
        <el-table-column label="计划开工" prop="planStartDate" />
        <el-table-column label="状态" prop="woStatus" align="center">
          <template #default="scope">
            <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button
              link
              type="primary"
              icon="Edit"
              @click="handleUpdate(scope.row)"
              v-if="scope.row.woStatus === 'CREATED'"
              v-hasPermi="['mes:pro:work:edit']"
              >修改</el-button
            >
            <el-button
              link
              type="primary"
              icon="Promotion"
              @click="handleRelease(scope.row)"
              v-if="scope.row.woStatus === 'CREATED'"
              v-hasPermi="['mes:pro:work:edit']"
              >下达</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesWorkOrder">
import { listWorkOrder, delWorkOrder, releaseWorkOrder } from '@/api/mes/pro/workorder';
import type { ProWorkOrderVO, ProWorkOrderQuery } from '@/api/mes/pro/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_wo_status } = toRefs<any>(proxy?.useDict('prd_wo_status'));
const router = useRouter();

const workOrderList = ref<ProWorkOrderVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const queryParams = ref<ProWorkOrderQuery>({
  pageNum: 1,
  pageSize: 10,
  woNo: undefined,
  materialName: undefined,
  woStatus: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listWorkOrder(queryParams.value);
    workOrderList.value = res.rows;
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
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

const handleSelectionChange = (selection: ProWorkOrderVO[]) => {
  ids.value = selection.map((item) => item.mesWoId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const handleDetail = (row: ProWorkOrderVO) => {
  router.push(`/mes/pro/workorder/detail/${row.mesWoId}`);
};

const handleAdd = () => {
  router.push({ path: '/mes/pro/workorder/form', query: { action: 'add' } });
};
const handleUpdate = (row: ProWorkOrderVO) => {
  router.push({ path: '/mes/pro/workorder/form', query: { id: row.mesWoId, action: 'edit' } });
};

const handleRelease = async (row: ProWorkOrderVO) => {
  await proxy?.$modal.confirm('确认下达工单"' + row.woNo + '"？');
  await releaseWorkOrder(row.mesWoId);
  proxy?.$modal.msgSuccess('下达成功');
  getList();
};

const handleDelete = async () => {
  await proxy?.$modal.confirm('确认删除选中的工单？');
  await delWorkOrder(ids.value);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

onActivated(() => {
  getList();
});
</script>
