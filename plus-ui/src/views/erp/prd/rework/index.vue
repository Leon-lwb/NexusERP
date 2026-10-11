<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="返工单号" prop="reworkNo">
            <el-input v-model="queryParams.reworkNo" placeholder="请输入返工单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="来源工单" prop="sourceWoNo">
            <el-input v-model="queryParams.sourceWoNo" placeholder="请输入来源工单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="返工状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择返工状态" clearable style="width: 100%">
              <el-option v-for="dict in prd_rework_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['prd:rework:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['prd:rework:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['prd:rework:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="reworkList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="返工单号" align="center" prop="reworkNo" min-width="140" />
      <el-table-column label="来源工单" align="center" prop="sourceWoNo" min-width="140" />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="140" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="单位" align="center" prop="baseUomName" min-width="80" />
      <el-table-column label="返工数量" align="center" prop="reworkQty" min-width="90" />
      <el-table-column label="返工原因" align="center" prop="reworkReason" min-width="90">
        <template #default="scope">
          <dict-tag :options="prd_rework_reason" :value="scope.row.reworkReason" />
        </template>
      </el-table-column>
      <el-table-column label="实际数量" align="center" prop="actualQty" min-width="90" />
      <el-table-column label="报废数量" align="center" prop="scrapQty" min-width="90" />
      <el-table-column label="返工状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="prd_rework_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-tooltip content="开始" placement="top" v-if="scope.row.status === 'DRAFT'">
            <el-button link type="success" icon="VideoPlay" @click="handleStart(scope.row)" v-hasPermi="['prd:rework:edit']" />
          </el-tooltip>
          <el-tooltip content="完工" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
            <el-button link type="primary" icon="Select" @click="handleComplete(scope.row)" v-hasPermi="['prd:rework:edit']" />
          </el-tooltip>
          <el-tooltip content="返工出库" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
            <el-button link type="warning" icon="Van" @click="handleIssue(scope.row)" v-hasPermi="['prd:rework:issue']" />
          </el-tooltip>
          <el-tooltip content="返工入库" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
            <el-button link type="success" icon="Box" @click="handleReceive(scope.row)" v-hasPermi="['prd:rework:receive']" />
          </el-tooltip>
          <el-tooltip content="修改" placement="top" v-if="scope.row.status === 'DRAFT'">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:rework:edit']" />
          </el-tooltip>
          <el-tooltip content="删除" placement="top" v-if="scope.row.status === 'DRAFT'">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:rework:remove']" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="reworkRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="返工单号" prop="reworkNo">
              <el-input v-model="form.reworkNo" placeholder="请输入返工单号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源工单" prop="sourceWoId">
              <el-select
                v-model="form.sourceWoId"
                filterable
                remote
                :remote-method="searchWorkOrder"
                placeholder="请选择来源工单"
                @change="onSourceWoChange"
                style="width: 100%"
              >
                <el-option v-for="wo in woOptions" :key="wo.woId" :label="`${wo.woNo} - ${wo.materialName || ''}`" :value="wo.woId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="返工数量" prop="reworkQty">
              <el-input-number v-model="form.reworkQty" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="返工原因" prop="reworkReason">
              <el-select v-model="form.reworkReason" placeholder="请选择返工原因" style="width: 100%">
                <el-option v-for="dict in prd_rework_reason" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实际数量" prop="actualQty">
              <el-input-number v-model="form.actualQty" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报废数量" prop="scrapQty">
              <el-input-number v-model="form.scrapQty" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm()">确 定</el-button>
        <el-button @click="cancel()">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PrdRework" lang="ts">
import {
  listReworkOrder,
  getReworkOrder,
  addReworkOrder,
  updateReworkOrder,
  delReworkOrder,
  startReworkOrder,
  completeReworkOrder,
  issueReworkMaterial,
  receiveReworkMaterial
} from '@/api/erp/prd/rework';
import type { PrdReworkOrderVO, PrdReworkOrderQuery } from '@/api/erp/prd/rework/types';
import { listWork } from '@/api/erp/prd/work';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_rework_status, prd_rework_reason } = toRefs<any>(proxy.useDict('prd_rework_status', 'prd_rework_reason'));

const reworkRef = ref<FormInstance>();
const woOptions = ref<any[]>([]);

const queryParams = ref<PrdReworkOrderQuery>({ pageNum: 1, pageSize: 10 });

const rules = {
  reworkNo: [{ required: true, message: '返工单号不能为空', trigger: 'blur' }],
  sourceWoId: [{ required: true, message: '来源工单ID不能为空', trigger: 'blur' }],
  reworkQty: [{ required: true, message: '返工数量不能为空', trigger: 'blur' }]
};

const {
  loading,
  list: reworkList,
  total,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listReworkOrder, get: getReworkOrder, add: addReworkOrder, update: updateReworkOrder, del: delReworkOrder },
  idField: 'reworkId',
  entityName: '返工订单',
  formRef: reworkRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: 'erp/prd/rework/export',
  exportFileName: '返工订单'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作（预载来源工单选项） */
function handleAdd() {
  rawHandleAdd(() => {
    searchWorkOrder('');
  });
}

/** 修改按钮操作（预载来源工单选项） */
function handleUpdate(row?: PrdReworkOrderVO) {
  return rawHandleUpdate(row, () => {
    searchWorkOrder('');
  });
}

/** 远程搜索来源工单 */
async function searchWorkOrder(query: string) {
  try {
    const res = await listWork({ woNo: query || undefined, pageNum: 1, pageSize: 20 });
    woOptions.value = res.rows || [];
  } catch {
    woOptions.value = [];
  }
}

/** 选中来源工单后回填物料信息 */
const onSourceWoChange = (woId: number) => {
  const wo = woOptions.value.find((i: any) => i.woId === woId);
  if (wo) {
    form.value.sourceWoNo = wo.woNo;
    form.value.materialId = wo.materialId;
    form.value.materialCode = wo.materialCode;
    form.value.materialName = wo.materialName;
    form.value.materialSpec = wo.materialSpec;
    form.value.baseUomName = wo.baseUomName;
  }
};

/** 开始返工 */
function handleStart(row: PrdReworkOrderVO) {
  proxy.$modal
    .confirm('是否确认开始返工订单"' + row.reworkNo + '"？')
    .then(() => startReworkOrder(row.reworkId))
    .then(() => {
      proxy.$modal.msgSuccess('开始成功');
      getList();
    })
    .catch(() => {});
}

/** 完工返工 */
function handleComplete(row: PrdReworkOrderVO) {
  proxy.$modal
    .confirm('是否确认完工返工订单"' + row.reworkNo + '"？')
    .then(() => completeReworkOrder(row.reworkId))
    .then(() => {
      proxy.$modal.msgSuccess('完工成功');
      getList();
    })
    .catch(() => {});
}

/** 返工出库 */
function handleIssue(row: PrdReworkOrderVO) {
  proxy.$modal
    .confirm('是否确认对返工订单"' + row.reworkNo + '"执行出库？')
    .then(() => issueReworkMaterial(row.reworkId))
    .then(() => {
      proxy.$modal.msgSuccess('出库成功');
      getList();
    })
    .catch(() => {});
}

/** 返工入库 */
function handleReceive(row: PrdReworkOrderVO) {
  proxy.$modal
    .confirm('是否确认对返工订单"' + row.reworkNo + '"执行入库？')
    .then(() => receiveReworkMaterial(row.reworkId))
    .then(() => {
      proxy.$modal.msgSuccess('入库成功');
      getList();
    })
    .catch(() => {});
}

getList();
</script>
