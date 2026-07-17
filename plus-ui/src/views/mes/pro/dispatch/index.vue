<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="派工单号" prop="dispatchNo">
              <el-input v-model="queryParams.dispatchNo" placeholder="请输入派工单号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="操作员" prop="operatorId">
              <el-select v-model="queryParams.operatorId" placeholder="请选择操作员" clearable filterable style="width: 100%">
                <el-option
                  v-for="w in workerOptions"
                  :key="w.workerId"
                  :label="w.userName + (w.employeeId ? ' / ' + w.employeeId : '')"
                  :value="w.workerId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="dispatchStatus">
              <el-select v-model="queryParams.dispatchStatus" placeholder="请选择状态" clearable style="width: 100%">
                <el-option label="待开工" value="PENDING" />
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已完工" value="COMPLETED" />
                <el-option label="已取消" value="CANCELLED" />
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

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Aim" @click="handleOpenScan" v-hasPermi="['mes:pro:dispatch:scan']">扫码执行</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="派工单号" prop="dispatchNo" />
        <el-table-column label="工单编号" prop="woNo" />
        <el-table-column label="流转卡编号" prop="cardNo" />
        <el-table-column label="工序" prop="processName" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作员" prop="operatorName" />
        <el-table-column label="工作中心" prop="workcenterCode" />
        <el-table-column label="派工数量" prop="plannedQty" align="right" />
        <el-table-column label="已完成" prop="completedQty" align="right" />
        <el-table-column label="状态" prop="dispatchStatus" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.dispatchStatus)">{{ statusLabel(scope.row.dispatchStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button
              link
              type="primary"
              icon="Promotion"
              @click="handleStart(scope.row)"
              v-if="scope.row.dispatchStatus === 'PENDING'"
              v-hasPermi="['mes:pro:dispatch:start']"
              >开工</el-button
            >
            <el-button
              link
              type="success"
              icon="CircleCheck"
              @click="handleComplete(scope.row)"
              v-if="scope.row.dispatchStatus === 'IN_PROGRESS'"
              v-hasPermi="['mes:pro:dispatch:complete']"
              >完工</el-button
            >
            <el-button
              link
              type="danger"
              icon="Close"
              @click="handleCancel(scope.row)"
              v-if="scope.row.dispatchStatus === 'PENDING'"
              v-hasPermi="['mes:pro:dispatch:cancel']"
              >取消</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <ScanExecute ref="scanRef" />
  </div>
</template>

<script setup lang="ts" name="MesDispatch">
// 技术债: 缺 mes_dispatch_status 字典, 派工状态暂用硬编码 el-tag + 本地 map, 待补字典后改 dict-tag
import { listDispatch, startDispatch, completeDispatch, cancelDispatch } from '@/api/mes/pro/dispatch';
import type { ProDispatchVO, ProDispatchQuery } from '@/api/mes/pro/dispatch';
import { listWorker } from '@/api/erp/sch/worker/index';
import type { WorkerVO } from '@/api/erp/sch/worker/types';
import ScanExecute from './components/ScanExecute.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const router = useRouter();

const dataList = ref<ProDispatchVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const workerOptions = ref<WorkerVO[]>([]);
const scanRef = ref<InstanceType<typeof ScanExecute> | null>(null);

const queryParams = ref<ProDispatchQuery>({
  pageNum: 1,
  pageSize: 10,
  dispatchNo: undefined,
  operatorId: undefined,
  dispatchStatus: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listDispatch(queryParams.value);
    dataList.value = res.rows;
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

const handleDetail = (row: ProDispatchVO) => {
  router.push(`/mes/pro/dispatch/detail/${row.dispatchId}`);
};

const handleOpenScan = () => {
  scanRef.value?.open();
};

const handleStart = async (row: ProDispatchVO) => {
  await proxy?.$modal.confirm('确认开工派工单"' + row.dispatchNo + '"？');
  await startDispatch(row.dispatchId);
  proxy?.$modal.msgSuccess('开工成功');
  getList();
};

const handleComplete = async (row: ProDispatchVO) => {
  const { value: goodQty } = await proxy?.$modal.prompt('请输入良品数量:', '完工 - ' + row.dispatchNo);
  if (!goodQty || isNaN(Number(goodQty)) || Number(goodQty) <= 0) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  const { value: defectQty } = await proxy?.$modal.prompt('请输入不良品数量（默认0）:', '完工 - ' + row.dispatchNo);
  const { value: scrapQty } = await proxy?.$modal.prompt('请输入报废数量（默认0）:', '完工 - ' + row.dispatchNo);
  await completeDispatch(row.dispatchId, Number(goodQty), Number(defectQty || 0), Number(scrapQty || 0));
  proxy?.$modal.msgSuccess('完工成功');
  getList();
};

const handleCancel = async (row: ProDispatchVO) => {
  await proxy?.$modal.confirm('确认取消派工单"' + row.dispatchNo + '"？');
  await cancelDispatch(row.dispatchId);
  proxy?.$modal.msgSuccess('取消成功');
  getList();
};

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待开工',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完工',
    CANCELLED: '已取消'
  };
  return map[status] || status;
};
const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success',
    CANCELLED: 'danger'
  };
  return map[status] || 'info';
};

onMounted(async () => {
  try {
    const res = await listWorker({ pageNum: 1, pageSize: 1000 });
    workerOptions.value = res.rows;
  } catch (e) {
    workerOptions.value = [];
  }
  getList();
});
</script>

