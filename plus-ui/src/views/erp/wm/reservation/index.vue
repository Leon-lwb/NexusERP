<template>
  <div class="app-container">
    <!-- 预留汇总表（在库/已预留/可用） -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="仓库" prop="warehouseCode">
            <el-input v-model="queryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="handleQuery" />
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
        <el-button type="primary" plain icon="Plus" @click="handleReserve()" v-hasPermi="['wm:reservation:add']">手动预留</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:reservation:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" @click="detailVisible = true" v-hasPermi="['wm:reservation:list']">预留明细</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="summaryList" show-overflow-tooltip>
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="单位" align="center" prop="baseUomName" />
      <el-table-column label="批次号" align="center" prop="batchCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="库位" align="center" prop="locationName" min-width="110" show-overflow-tooltip />
      <el-table-column label="在库数量" align="center" prop="stockQuantity" />
      <el-table-column label="已预留数量" align="center" prop="reservedQuantity">
        <template #default="scope">
          <el-tag :type="scope.row.reservedQuantity > 0 ? 'warning' : 'info'">{{ scope.row.reservedQuantity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可用数量" align="center" prop="availableQuantity">
        <template #default="scope">
          <el-tag :type="scope.row.availableQuantity > 0 ? 'success' : 'danger'">{{ scope.row.availableQuantity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联工单" align="center" prop="woNo" min-width="130" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="110" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-tooltip content="手动预留" placement="top">
            <el-button link type="primary" icon="Plus" @click="handleReserve(scope.row)" v-hasPermi="['wm:reservation:add']" />
          </el-tooltip>
          <el-tooltip content="检查可用" placement="top">
            <el-button link type="success" icon="Search" @click="handleCheckAvailable(scope.row)" v-hasPermi="['wm:reservation:query']" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 手动预留弹窗 -->
    <el-dialog :title="'手动预留'" v-model="reserveVisible" width="640px" :close-on-click-modal="false">
      <el-form ref="reserveFormRef" :model="reserveForm" :rules="reserveRules" label-width="110px">
        <el-form-item label="物料" prop="materialId">
          <el-input v-model="reserveForm.materialName" placeholder="请选择物料" readonly>
            <template #append>
              <el-button icon="Search" @click="openMaterialDialog" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-input v-model="reserveForm.warehouseName" placeholder="可选，不限仓库" readonly>
            <template #append>
              <el-button icon="Search" @click="openWarehouseDialog" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="预留数量" prop="reserveQty">
          <el-input-number v-model="reserveForm.reserveQty" :min="0.0001" :precision="4" style="width: 100%" placeholder="请输入预留数量" />
        </el-form-item>
        <el-form-item label="来源单据类型" prop="sourceDocType">
          <el-radio-group v-model="reserveForm.sourceDocType">
            <el-radio-button value="SALES_ORDER">销售订单</el-radio-button>
            <el-radio-button value="WORK_ORDER">生产工单</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="来源单据" prop="sourceDocId">
          <el-input v-model="sourceDocDisplay" placeholder="请选择销售订单 / 生产工单" readonly>
            <template #append>
              <el-button icon="Search" @click="openSourceDocDialog" />
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitReserve">确 定</el-button>
        <el-button @click="reserveVisible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 选择物料 -->
    <el-dialog title="选择物料" v-model="materialDialogOpen" width="960px" append-to-body>
      <el-form :model="materialQuery" inline>
        <el-form-item label="物料编码">
          <el-input v-model="materialQuery.materialCode" placeholder="请输入物料编码" clearable style="width: 160px" @keyup.enter="getMaterialList" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="materialQuery.materialName" placeholder="请输入物料名称" clearable style="width: 160px" @keyup.enter="getMaterialList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getMaterialList">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="materialList" @current-change="handleMaterialSelect" highlight-current-row max-height="420">
        <el-table-column label="物料编码" prop="materialCode" width="140" />
        <el-table-column label="物料名称" prop="materialName" width="180" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="materialSpec" width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="baseUomName" width="80" />
      </el-table>
      <template #footer>
        <el-button @click="materialDialogOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 选择仓库 -->
    <el-dialog title="选择仓库" v-model="warehouseDialogOpen" width="640px" append-to-body>
      <el-table :data="warehouseList" @current-change="handleWarehouseSelect" highlight-current-row max-height="420">
        <el-table-column label="仓库编码" prop="warehouseCode" width="140" />
        <el-table-column label="仓库名称" prop="warehouseName" />
        <el-table-column label="仓库地址" prop="warehouseAddress" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="warehouseDialogOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 选择来源单据（销售订单 / 生产工单） -->
    <el-dialog
      :title="'选择' + (reserveForm.sourceDocType === 'WORK_ORDER' ? '生产工单' : '销售订单')"
      v-model="sourceDocDialogOpen"
      width="960px"
      append-to-body
    >
      <el-form :model="sourceDocQuery" inline>
        <el-form-item v-if="reserveForm.sourceDocType === 'SALES_ORDER'" label="订单编号">
          <el-input v-model="sourceDocQuery.soNo" placeholder="请输入销售订单编号" clearable style="width: 170px" @keyup.enter="getSourceDocList" />
        </el-form-item>
        <el-form-item v-else label="工单编号">
          <el-input v-model="sourceDocQuery.woNo" placeholder="请输入工单编号" clearable style="width: 170px" @keyup.enter="getSourceDocList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getSourceDocList">搜索</el-button>
          <el-button icon="Refresh" @click="resetSourceDocQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="sourceDocList" @current-change="handleSourceDocSelect" highlight-current-row max-height="420">
        <template v-if="reserveForm.sourceDocType === 'SALES_ORDER'">
          <el-table-column label="订单编号" prop="soNo" width="160" />
          <el-table-column label="客户" prop="clientName" min-width="150" show-overflow-tooltip />
          <el-table-column label="订单状态" prop="orderStatus" width="100" />
          <el-table-column label="创建时间" prop="createTime" width="160" />
        </template>
        <template v-else>
          <el-table-column label="工单编号" prop="woNo" width="160" />
          <el-table-column label="物料" prop="materialName" min-width="150" show-overflow-tooltip />
          <el-table-column label="计划数量" prop="plannedQty" width="100" />
          <el-table-column label="工单状态" prop="woStatus" width="100" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="sourceDocDialogOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 预留明细弹窗 -->
    <el-dialog :title="'预留明细'" v-model="detailVisible" width="1200px">
      <el-form :model="detailQuery" inline class="mb8">
        <el-form-item label="物料编码">
          <el-input v-model="detailQuery.materialCode" placeholder="请输入物料编码" clearable style="width: 160px" @keyup.enter="getDetailList" />
        </el-form-item>
        <el-form-item label="单据编号">
          <el-input v-model="detailQuery.sourceDocCode" placeholder="请输入单据编号" clearable style="width: 160px" @keyup.enter="getDetailList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getDetailList">搜索</el-button>
          <el-button icon="Refresh" @click="resetDetailQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="detailLoading" :data="detailList" show-overflow-tooltip max-height="420">
        <el-table-column label="事务类型" align="center" prop="txType" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.txType === 'RESERVE' ? 'warning' : 'info'">{{ scope.row.txType === 'RESERVE' ? '预留' : '释放' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
        <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
        <el-table-column label="数量" align="center" prop="txQuantity" width="90" />
        <el-table-column label="仓库" align="center" prop="warehouseName" min-width="110" show-overflow-tooltip />
        <el-table-column label="来源单据类型" align="center" prop="sourceDocType" min-width="130" show-overflow-tooltip />
        <el-table-column label="单据编号" align="center" prop="sourceDocCode" min-width="150" show-overflow-tooltip />
        <el-table-column label="事务日期" align="center" prop="txDate" width="160" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="90" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-tooltip content="释放预留" placement="top" v-if="scope.row.txType === 'RESERVE'">
              <el-button link type="danger" icon="Delete" @click="handleUnreserve(scope.row)" v-hasPermi="['wm:reservation:edit']" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="detailTotal > 0"
        :total="detailTotal"
        v-model:page="detailQuery.pageNum"
        v-model:limit="detailQuery.pageSize"
        @pagination="getDetailList"
      />
    </el-dialog>
  </div>
</template>

<script setup name="WmReservation" lang="ts">
import { listReservationSummary, listReservation, reserveStock, unreserveStock, checkAvailable } from '@/api/erp/wm/reservation';
import { ReservationSummaryVO, ReservationTxVO, ReservationQuery, ReserveForm } from '@/api/erp/wm/reservation/types';
import { listMaterial } from '@/api/erp/bd/material';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { listOrderheader } from '@/api/erp/so/orderheader';
import { listWork } from '@/api/erp/prd/work';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

// 预留汇总为只读列表（无标准详情/新增/修改/删除接口），占位仅为满足 useCrud 的 API 签名，不会被调用
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<ReservationQuery>({ pageNum: 1, pageSize: 10 });

const {
  loading,
  list: summaryList,
  total,
  showSearch,
  getList,
  handleQuery,
  handleExport
} = useCrud({
  apis: { list: listReservationSummary, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'stockId',
  entityName: '库存预留',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  // 沿用原实现：预留汇总导出走库存导出接口
  exportUrl: 'erp/wm/stock/export',
  exportFileName: '库存预留'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// ---------- 手动预留 ----------
const reserveVisible = ref(false);
const reserveFormRef = ref<ElFormInstance>();
const reserveForm = ref<ReserveForm>({
  materialId: undefined,
  materialName: '',
  warehouseId: undefined,
  warehouseName: '',
  reserveQty: undefined,
  sourceDocType: 'SALES_ORDER',
  sourceDocId: undefined,
  sourceDocLineId: undefined
});
/** 来源单据展示文本（订单号/工单号） */
const sourceDocDisplay = computed(() => {
  if (reserveForm.value.sourceDocType === 'WORK_ORDER') return reserveForm.value.woNo ?? '';
  return reserveForm.value.soNo ?? '';
});

const reserveRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  reserveQty: [{ required: true, message: '请输入预留数量', trigger: 'blur' }],
  sourceDocId: [{ required: true, message: '请选择来源单据', trigger: 'change' }]
};

// 物料选择
const materialDialogOpen = ref(false);
const materialList = ref<any[]>([]);
const materialQuery = ref<{ materialCode?: string; materialName?: string }>({});

const openMaterialDialog = () => {
  materialDialogOpen.value = true;
  getMaterialList();
};
const getMaterialList = async () => {
  const res = await listMaterial({ ...materialQuery.value, pageNum: 1, pageSize: 20 } as any);
  materialList.value = res.rows;
};
const handleMaterialSelect = (row: any) => {
  if (!row) return;
  reserveForm.value.materialId = row.materialId;
  reserveForm.value.materialName = row.materialName + ' (' + row.materialCode + ')';
  materialDialogOpen.value = false;
};

// 仓库选择
const warehouseDialogOpen = ref(false);
const warehouseList = ref<any[]>([]);

const openWarehouseDialog = () => {
  warehouseDialogOpen.value = true;
  getWarehouseList();
};
const getWarehouseList = async () => {
  const res = await listWarehouse({ pageNum: 1, pageSize: 50 } as any);
  warehouseList.value = res.rows;
};
const handleWarehouseSelect = (row: any) => {
  if (!row) return;
  reserveForm.value.warehouseId = row.warehouseId;
  reserveForm.value.warehouseName = row.warehouseName;
  warehouseDialogOpen.value = false;
};

const handleReserve = (row?: ReservationSummaryVO) => {
  reserveForm.value = {
    materialId: row?.materialId,
    materialName: row ? row.materialName + ' (' + row.materialCode + ')' : '',
    warehouseId: row?.warehouseId,
    warehouseName: row?.warehouseName ?? '',
    reserveQty: undefined,
    sourceDocType: 'SALES_ORDER',
    sourceDocId: undefined,
    sourceDocLineId: undefined,
    soNo: undefined,
    woNo: undefined
  };
  reserveVisible.value = true;
};

const submitReserve = async () => {
  await reserveFormRef.value?.validate();
  const res: any = await reserveStock(reserveForm.value);
  proxy?.$modal.msgSuccess('预留成功，实际预留数量：' + res?.data);
  reserveVisible.value = false;
  getList();
};

// ---------- 来源单据选择 ----------
const sourceDocDialogOpen = ref(false);
const sourceDocList = ref<any[]>([]);
const sourceDocQuery = ref<{ soNo?: string; woNo?: string }>({});

const openSourceDocDialog = () => {
  sourceDocDialogOpen.value = true;
  getSourceDocList();
};
const getSourceDocList = async () => {
  if (reserveForm.value.sourceDocType === 'WORK_ORDER') {
    const res = await listWork({ ...sourceDocQuery.value, pageNum: 1, pageSize: 20 } as any);
    sourceDocList.value = res.rows;
  } else {
    const res = await listOrderheader({ ...sourceDocQuery.value, pageNum: 1, pageSize: 20 } as any);
    sourceDocList.value = res.rows;
  }
};
const resetSourceDocQuery = () => {
  sourceDocQuery.value = {};
  getSourceDocList();
};
const handleSourceDocSelect = (row: any) => {
  if (!row) return;
  if (reserveForm.value.sourceDocType === 'WORK_ORDER') {
    reserveForm.value.sourceDocId = row.woId;
    reserveForm.value.woNo = row.woNo;
    reserveForm.value.soNo = undefined;
  } else {
    reserveForm.value.sourceDocId = row.soId;
    reserveForm.value.soNo = row.soNo;
    reserveForm.value.woNo = undefined;
  }
  sourceDocDialogOpen.value = false;
};

// ---------- 释放预留 ----------
const handleUnreserve = async (row: ReservationTxVO) => {
  if (!row.sourceDocType || !row.sourceDocId) {
    proxy?.$modal.msgWarning('该预留流水未关联来源单据，无法按单据释放');
    return;
  }
  await proxy?.$modal.confirm(
    `是否确认释放该预留？\n物料：${row.materialName}，数量：${row.txQuantity}，单据：${row.sourceDocType}(${row.sourceDocId})`
  );
  await unreserveStock({ sourceDocType: row.sourceDocType, sourceDocId: row.sourceDocId, sourceDocLineId: row.sourceDocLineId ?? undefined });
  proxy?.$modal.msgSuccess('释放成功');
  getDetailList();
  getList();
};

// ---------- 检查可用 ----------
const handleCheckAvailable = async (row: ReservationSummaryVO) => {
  const res: any = await checkAvailable(row.materialId, row.warehouseId);
  proxy?.$modal.msgSuccess(`可用数量：${res?.data ?? 0}（在库 ${row.stockQuantity} - 预留 ${row.reservedQuantity}）`);
};

// ---------- 预留明细 ----------
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailList = ref<ReservationTxVO[]>([]);
const detailTotal = ref(0);
const detailQuery = ref<ReservationQuery>({ pageNum: 1, pageSize: 10 });

const getDetailList = async () => {
  detailLoading.value = true;
  const res = await listReservation(detailQuery.value);
  detailList.value = res.rows;
  detailTotal.value = res.total;
  detailLoading.value = false;
};
const resetDetailQuery = () => {
  detailQuery.value = { pageNum: 1, pageSize: 10 };
  getDetailList();
};

watch(detailVisible, (val) => {
  if (val) getDetailList();
});

onMounted(() => {
  getList();
});
</script>
