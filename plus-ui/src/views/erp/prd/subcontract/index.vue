<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="委外单号" prop="subcontractNo">
            <el-input v-model="queryParams.subcontractNo" placeholder="请输入委外单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商名称" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="委外状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择委外状态" clearable style="width: 100%">
              <el-option v-for="dict in prd_subcontract_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['prd:subcontract:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['prd:subcontract:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['prd:subcontract:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="subcontractList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="委外单号" align="center" prop="subcontractNo" />
      <el-table-column label="供应商" align="center" prop="vendorName" min-width="150" show-overflow-tooltip />
      <el-table-column label="物料编码" align="center" prop="materialCode" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="计划数量" align="center" prop="plannedQty" />
      <el-table-column label="单价" align="center" prop="unitPrice" />
      <el-table-column label="总金额" align="center" prop="totalAmount" />
      <el-table-column label="计划开始" align="center" prop="planStartDate" />
      <el-table-column label="计划结束" align="center" prop="planEndDate" />
      <el-table-column label="关联工单" align="center" prop="woNo" />
      <el-table-column label="收货进度" align="center" width="130">
        <template #default="scope">
          <el-progress :percentage="progressOf(scope.row)" :stroke-width="8" />
        </template>
      </el-table-column>
      <el-table-column label="委外状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="prd_subcontract_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-tooltip content="查看" placement="top">
            <el-button link type="info" icon="View" @click="handleView(scope.row)" v-hasPermi="['prd:subcontract:query']" />
          </el-tooltip>
          <el-tooltip content="委外闭环详情" placement="top">
            <el-button link type="primary" icon="Operation" @click="handleClosure(scope.row)" v-hasPermi="['prd:subcontract:query']" />
          </el-tooltip>
          <el-tooltip content="提交审批" placement="top" v-if="scope.row.status === 'DRAFT'">
            <el-button link type="primary" icon="Finished" @click="handleSubmitApproval(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="下达" placement="top" v-if="scope.row.status === 'DRAFT' || scope.row.status === 'APPROVED'">
            <el-button link type="success" icon="Promotion" @click="handleRelease(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="完工" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
            <el-button link type="primary" icon="Select" @click="handleComplete(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="修改" placement="top" v-if="scope.row.status === 'DRAFT'">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="删除" placement="top" v-if="scope.row.status === 'DRAFT'">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:subcontract:remove']" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="subcontractRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="委外单号" prop="subcontractNo">
              <el-input v-model="form.subcontractNo" placeholder="请输入委外单号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="vendorId">
              <el-select
                v-model="form.vendorId"
                filterable
                remote
                :remote-method="searchVendor"
                placeholder="请选择供应商"
                @change="onVendorChange"
                style="width: 100%"
              >
                <el-option v-for="v in vendorOptions" :key="v.vendorId" :label="v.vendorName" :value="v.vendorId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料" prop="materialId">
              <el-select
                v-model="form.materialId"
                filterable
                remote
                :remote-method="searchMaterial"
                placeholder="请选择物料"
                @change="onMaterialChange"
                style="width: 100%"
              >
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode} - ${m.materialName}`" :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价" prop="unitPrice">
              <el-input-number v-model="form.unitPrice" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联工单" prop="woId">
              <el-select v-model="form.woId" filterable remote :remote-method="searchWorkOrder" placeholder="请选择关联工单" style="width: 100%">
                <el-option v-for="wo in woOptions" :key="wo.woId" :label="`${wo.woNo} - ${wo.materialName || ''}`" :value="wo.woId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="form.plannedQty" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联BOM" prop="bomId">
              <el-select v-model="form.bomId" placeholder="选择物料后带出当前生效BOM" clearable style="width: 100%" @change="onBomChange">
                <el-option v-for="b in bomOptions" :key="b.bomId" :label="`${b.bomName || 'BOM'} V${b.bomVersion || '-'}`" :value="b.bomId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺要求" prop="processRequirement">
              <el-input v-model="form.processRequirement" type="textarea" :rows="2" placeholder="质量标准/特殊工艺/AOI/FCT等(询价与发料资料包随附)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划开始日期" prop="planStartDate">
              <el-date-picker
                v-model="form.planStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择计划开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束日期" prop="planEndDate">
              <el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择计划结束日期" style="width: 100%" />
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

    <!-- PCBA 委外闭环详情抽屉 -->
    <SubcontractClosureDetail :row="closureRow" @close="closureRow = null" v-if="closureRow" />
  </div>
</template>

<script setup name="PrdSubcontract" lang="ts">
import {
  listSubcontract,
  getSubcontract,
  addSubcontract,
  updateSubcontract,
  delSubcontract,
  releaseSubcontract,
  completeSubcontract,
  submitApproval,
  getMaterialInfo
} from '@/api/erp/prd/subcontract';
import SubcontractClosureDetail from './detail.vue';
import type { PrdSubcontractVO, PrdSubcontractQuery } from '@/api/erp/prd/subcontract/types';
import { listVendor } from '@/api/erp/bd/vendor';
import { listMaterial } from '@/api/erp/bd/material';
import { listWork } from '@/api/erp/prd/work';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_subcontract_status } = toRefs<any>(proxy.useDict('prd_subcontract_status'));

const subcontractRef = ref<FormInstance>();

const queryParams = ref<PrdSubcontractQuery>({ pageNum: 1, pageSize: 10 });

const vendorOptions = ref<any[]>([]);
const materialOptions = ref<any[]>([]);
const bomOptions = ref<any[]>([]);
const closureRow = ref<any>(null);

/** 打开 PCBA 闭环详情抽屉 */
const handleClosure = (row: PrdSubcontractVO) => {
  closureRow.value = row;
};
const woOptions = ref<any[]>([]);

const rules = {
  subcontractNo: [{ required: true, message: '委外单号不能为空', trigger: 'blur' }],
  vendorId: [{ required: true, message: '供应商ID不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  plannedQty: [{ required: true, message: '计划数量不能为空', trigger: 'blur' }]
};

const {
  loading,
  list: subcontractList,
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
  apis: { list: listSubcontract, get: getSubcontract, add: addSubcontract, update: updateSubcontract, del: delSubcontract },
  idField: 'subcontractId',
  entityName: '委外订单',
  formRef: subcontractRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: 'erp/prd/subcontract/export',
  exportFileName: '委外订单'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 预载表单下拉选项（供应商/物料/工单） */
function loadFormOptions() {
  searchVendor('');
  searchMaterial('');
  searchWorkOrder('');
}

/** 新增按钮操作 */
function handleAdd() {
  rawHandleAdd(loadFormOptions);
}

/** 修改按钮操作 */
function handleUpdate(row?: PrdSubcontractVO) {
  return rawHandleUpdate(row, loadFormOptions);
}

/** 远程搜索供应商 */
const searchVendor = async (query: string) => {
  if (!query && query !== '') return;
  try {
    const res = await listVendor({ vendorName: query || undefined, pageNum: 1, pageSize: 20 });
    vendorOptions.value = res.rows || [];
  } catch {
    vendorOptions.value = [];
  }
};

/** 远程搜索物料 */
const searchMaterial = async (query: string) => {
  if (!query && query !== '') return;
  try {
    const res = await listMaterial({ materialName: query || undefined, pageNum: 1, pageSize: 20 });
    materialOptions.value = res.rows || [];
  } catch {
    materialOptions.value = [];
  }
};

/** 远程搜索关联工单 */
const searchWorkOrder = async (query: string) => {
  try {
    const res = await listWork({ woNo: query || undefined, pageNum: 1, pageSize: 20 });
    woOptions.value = res.rows || [];
  } catch {
    woOptions.value = [];
  }
};

/** 选中供应商后回填编码/名称 */
const onVendorChange = (vendorId: number) => {
  const v = vendorOptions.value.find((i: any) => i.vendorId === vendorId);
  if (v) {
    form.value.vendorCode = v.vendorCode;
    form.value.vendorName = v.vendorName;
  }
};

/** 选中物料后回填物料信息 + 带出当前生效 BOM(PCBA 闭环) */
const onMaterialChange = (materialId: number) => {
  const m = materialOptions.value.find((i: any) => i.materialId === materialId);
  if (m) {
    form.value.materialCode = m.materialCode;
    form.value.materialName = m.materialName;
    form.value.materialSpec = m.materialSpec;
    form.value.baseUomName = m.baseUomName;
  }
  bomOptions.value = [];
  form.value.bomId = null;
  form.value.routingId = null;
  if (materialId) {
    getMaterialInfo(materialId)
      .then((res: any) => {
        const data = res.data || {};
        bomOptions.value = data.bom ? [data.bom] : [];
        if (data.bom) {
          form.value.bomId = data.bom.bomId;
          form.value.routingId = data.bom.routingId || null;
        }
      })
      .catch(() => {});
  }
};

/** 选中 BOM 后带出工艺路线 */
const onBomChange = (bomId: number) => {
  const b = bomOptions.value.find((i: any) => i.bomId === bomId);
  form.value.routingId = b?.routingId || null;
};

/** 查看委外订单详情（含操作日志） */
/** 查看: 直接打开 PCBA 闭环详情抽屉(基本信息/日志/业务页签合一) */
const handleView = (row: PrdSubcontractVO) => {
  closureRow.value = row;
};

/** 收货进度百分比 */
const progressOf = (row: PrdSubcontractVO): number => {
  const planned = Number(row.plannedQty || 0);
  if (planned <= 0) return 0;
  return Math.min(100, Math.round((Number(row.actualQty || 0) / planned) * 100));
};

/** 提交审批 */
function handleSubmitApproval(row: PrdSubcontractVO) {
  proxy.$modal
    .confirm('是否确认提交审批委外订单"' + row.subcontractNo + '"？')
    .then(() => submitApproval(row.subcontractId))
    .then(() => {
      proxy.$modal.msgSuccess('提交审批成功');
      getList();
    })
    .catch(() => {});
}

/** 下达 */
function handleRelease(row: PrdSubcontractVO) {
  proxy.$modal
    .confirm('是否确认下达委外订单"' + row.subcontractNo + '"？')
    .then(() => releaseSubcontract(row.subcontractId))
    .then(() => {
      proxy.$modal.msgSuccess('下达成功');
      getList();
    })
    .catch(() => {});
}

/** 完工 */
function handleComplete(row: PrdSubcontractVO) {
  proxy.$modal
    .confirm('是否确认完工委外订单"' + row.subcontractNo + '"？')
    .then(() => completeSubcontract(row.subcontractId))
    .then(() => {
      proxy.$modal.msgSuccess('完工成功');
      getList();
    })
    .catch(() => {});
}

getList();
</script>
