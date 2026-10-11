<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="派工单号" prop="dispatchNo">
            <el-input v-model="queryParams.dispatchNo" placeholder="请输入派工单号" clearable @keyup.enter="handleQuery()" />
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
              <el-option v-for="dict in mes_dispatch_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="success" plain icon="Aim" @click="handleOpenScan()" v-hasPermi="['mes:pro:dispatch:scan']">扫码执行</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="User" @click="handleOpenFaceLogin()">刷脸登录</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="派工单号" prop="dispatchNo" min-width="140" />
      <el-table-column label="工单编号" prop="woNo" min-width="140" />
      <el-table-column label="流转卡编号" prop="cardNo" min-width="140" />
      <el-table-column label="工序" prop="processName" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作员" prop="operatorName" min-width="90" />
      <el-table-column label="工作中心" prop="workcenterCode" min-width="110" />
      <el-table-column label="派工数量" prop="plannedQty" align="right" min-width="90" />
      <el-table-column label="已完成" prop="completedQty" align="right" min-width="80" />
      <el-table-column label="状态" prop="dispatchStatus" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_dispatch_status" :value="scope.row.dispatchStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
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

    <ScanExecute ref="scanRef" />
    <FaceLoginDrawer ref="faceLoginRef" @verified="onFaceVerified" />
  </div>
</template>

<script setup lang="ts" name="MesDispatch">
import { listDispatch, getDispatch, startDispatch, completeDispatch, cancelDispatch } from '@/api/mes/pro/dispatch';
import type { ProDispatchVO, ProDispatchQuery } from '@/api/mes/pro/dispatch';
import { listWorker } from '@/api/erp/sch/worker/index';
import type { WorkerVO } from '@/api/erp/sch/worker/types';
import ScanExecute from './components/ScanExecute.vue';
import FaceLoginDrawer from './components/FaceLoginDrawer.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_dispatch_status } = toRefs<any>(proxy.useDict('mes_dispatch_status'));
const router = useRouter();

const workerOptions = ref<WorkerVO[]>([]);
const scanRef = ref<InstanceType<typeof ScanExecute> | null>(null);
const faceLoginRef = ref<InstanceType<typeof FaceLoginDrawer> | null>(null);

const queryParams = ref<ProDispatchQuery>({
  pageNum: 1,
  pageSize: 10,
  dispatchNo: undefined,
  operatorId: undefined,
  dispatchStatus: undefined
});

// 派工单由流转卡派生，页面无新增/修改/删除入口（开工/完工/取消等业务操作走本地方法），
// add/update/del 占位仅为满足 useCrud 的 API 签名
const readonlyApi = () => Promise.resolve({ data: {} as any });

// 列表骨架（详情为路由页面，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud({
  apis: { list: listDispatch, get: getDispatch, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'dispatchId',
  entityName: '派工单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 详情按钮操作 */
function handleDetail(row: ProDispatchVO) {
  router.push(`/mes/pro/dispatch/detail/${row.dispatchId}`);
}

/** 打开扫码执行 */
function handleOpenScan() {
  scanRef.value?.open();
}

/** 操作台刷脸登录抽屉(1:N top-3 候选 + 1:1 确认,见方案 3.2.2) */
function handleOpenFaceLogin() {
  faceLoginRef.value?.open();
}

/** 人脸验证通过回调:记录当前操作员上下文(派工查询过滤等后续接入) */
function onFaceVerified(payload: { subject: string; workerId?: string | null; userName?: string; similarity: number }) {
  proxy.$modal.msgSuccess(`已验证 ${payload.userName || payload.subject},开始操作`);
}

/** 开工 */
async function handleStart(row: ProDispatchVO) {
  await proxy.$modal.confirm('确认开工派工单"' + row.dispatchNo + '"？');
  await startDispatch(row.dispatchId);
  proxy.$modal.msgSuccess('开工成功');
  getList();
}

/** 完工(依次录入良品/不良品/报废数量) */
async function handleComplete(row: ProDispatchVO) {
  const { value: goodQty } = await proxy.$modal.prompt('请输入良品数量:', '完工 - ' + row.dispatchNo);
  if (!goodQty || isNaN(Number(goodQty)) || Number(goodQty) <= 0) {
    proxy.$modal.msgWarning('请输入有效数量');
    return;
  }
  const { value: defectQty } = await proxy.$modal.prompt('请输入不良品数量（默认0）:', '完工 - ' + row.dispatchNo);
  const { value: scrapQty } = await proxy.$modal.prompt('请输入报废数量（默认0）:', '完工 - ' + row.dispatchNo);
  await completeDispatch(row.dispatchId, Number(goodQty), Number(defectQty || 0), Number(scrapQty || 0));
  proxy.$modal.msgSuccess('完工成功');
  getList();
}

/** 取消 */
async function handleCancel(row: ProDispatchVO) {
  await proxy.$modal.confirm('确认取消派工单"' + row.dispatchNo + '"？');
  await cancelDispatch(row.dispatchId);
  proxy.$modal.msgSuccess('取消成功');
  getList();
}

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
