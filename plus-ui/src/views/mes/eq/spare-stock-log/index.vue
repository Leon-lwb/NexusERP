<template>
  <div class="app-container">
    <el-alert
      class="mb8"
      type="info"
      show-icon
      :closable="false"
      title="本页记录 MES 库存变动：变动前后为 MES 派生缓存快照，不代表 ERP 已落账。请结合来源单据和 ERP 过账台账核对。"
    />
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="备件编码" prop="spareCode">
            <el-input v-model="queryParams.spareCode" placeholder="备件编码(精确)" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="流水类型" prop="transactionType">
            <el-select v-model="queryParams.transactionType" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="d in SPARE_TXN_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
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
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['mes:eq:spare:log:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="dataList"
      row-key="logId"
      show-overflow-tooltip
      empty-text="暂无库存流水，正常办理入库、出库、维修领用或退库后可在此追溯"
    >
      <el-table-column label="流水 ID" prop="logId" min-width="190" />
      <el-table-column label="备件编码" prop="spareCode" width="120" />
      <el-table-column label="备件名称" prop="spareName" min-width="120" show-overflow-tooltip />
      <el-table-column label="类型" prop="transactionType" align="center">
        <template #default="scope">
          <el-tag :type="txnTagType(scope.row.transactionType)" size="small">{{ txnTypeLabel(scope.row.transactionType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数量" prop="quantity" align="right" width="140">
        <template #default="scope">{{ formatQuantity(scope.row.quantity) }}</template>
      </el-table-column>
      <el-table-column label="MES 缓存变动前" prop="beforeQty" align="right" width="140">
        <template #default="scope">{{ formatQuantity(scope.row.beforeQty) }}</template>
      </el-table-column>
      <el-table-column label="MES 缓存变动后" prop="afterQty" align="right" width="140">
        <template #default="scope">{{ formatQuantity(scope.row.afterQty) }}</template>
      </el-table-column>
      <el-table-column label="来源" prop="refType" align="center" width="135">
        <template #default="scope">{{ refTypeLabel(scope.row.refType) }}</template>
      </el-table-column>
      <el-table-column label="来源单据 ID" prop="refId" min-width="190">
        <template #default="scope">{{ scope.row.refId || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作人" prop="operatorName" width="100">
        <template #default="scope">{{ scope.row.operatorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作时间" prop="operateTime" width="160" />
      <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.remark || '-' }}</template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MesEqSpareStockLog">
import { listStockLog } from '@/api/mes/eq/spare-part';
import type { EqSpareStockLogVO } from '@/api/mes/eq/spare-part';
import { formatQuantity, txnTypeLabel, txnTagType, refTypeLabel, SPARE_TXN_OPTIONS } from '@/utils/mesSpareDisplay';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

// 备件库存流水为只读列表页（无新增/修改/删除），以下占位仅为满足 useCrud 的 API 签名，不会被调用
const formRef = ref<FormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  spareCode: undefined as string | undefined,
  transactionType: undefined as string | undefined
});

const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList,
  handleQuery,
  handleExport
} = useCrud<EqSpareStockLogVO, any>({
  apis: { list: listStockLog, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'logId',
  entityName: '备件库存流水',
  formRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: '/mes/eq/spare-part/stock-log/export',
  exportFileName: 'spare_stock_log'
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
