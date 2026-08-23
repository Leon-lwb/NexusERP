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
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:subcontract:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['prd:subcontract:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['prd:subcontract:export']">导出</el-button>
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
          <el-tooltip content="提交审批" placement="top" v-if="scope.row.status === 'DRAFT'">
            <el-button link type="primary" icon="Finished" @click="handleSubmitApproval(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="下达" placement="top" v-if="scope.row.status === 'DRAFT' || scope.row.status === 'APPROVED'">
            <el-button link type="success" icon="Promotion" @click="handleRelease(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="完工" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
            <el-button link type="primary" icon="Select" @click="handleComplete(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="委外发料" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
            <el-button link type="warning" icon="Van" @click="handleSubcontractIssue(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
          </el-tooltip>
          <el-tooltip content="委外收货" placement="top" v-if="scope.row.status === 'IN_PROGRESS'">
            <el-button link type="success" icon="Box" @click="handleSubcontractReceive(scope.row)" v-hasPermi="['prd:subcontract:edit']" />
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
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="委外订单详情" v-model="detailOpen" width="800px" append-to-body>
      <el-tabs v-model="detailActiveTab">
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="委外单号">{{ detailData.subcontractNo }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ detailData.vendorName }}</el-descriptions-item>
            <el-descriptions-item label="物料编码">{{ detailData.materialCode }}</el-descriptions-item>
            <el-descriptions-item label="物料名称">{{ detailData.materialName }}</el-descriptions-item>
            <el-descriptions-item label="规格型号">{{ detailData.materialSpec }}</el-descriptions-item>
            <el-descriptions-item label="单位">{{ detailData.baseUomName }}</el-descriptions-item>
            <el-descriptions-item label="计划数量">{{ detailData.plannedQty }}</el-descriptions-item>
            <el-descriptions-item label="实际数量">{{ detailData.actualQty }}</el-descriptions-item>
            <el-descriptions-item label="单价">{{ detailData.unitPrice }}</el-descriptions-item>
            <el-descriptions-item label="总金额">{{ detailData.totalAmount }}</el-descriptions-item>
            <el-descriptions-item label="计划开始日期">{{ detailData.planStartDate }}</el-descriptions-item>
            <el-descriptions-item label="计划结束日期">{{ detailData.planEndDate }}</el-descriptions-item>
            <el-descriptions-item label="关联工单">{{ detailData.woNo }}</el-descriptions-item>
            <el-descriptions-item label="委外状态">
              <dict-tag :options="prd_subcontract_status" :value="detailData.status" />
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detailData.remark }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="操作日志" name="logs">
          <el-table v-loading="logsLoading" :data="operationLogs" border style="width: 100%" show-overflow-tooltip>
            <el-table-column label="操作类型" align="center" prop="operationType" width="120" />
            <el-table-column label="操作描述" align="center" prop="operationDesc" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作人" align="center" prop="operatorName" width="120" />
            <el-table-column label="操作时间" align="center" prop="operationTime" width="180" />
          </el-table>
          <el-empty v-if="!logsLoading && operationLogs.length === 0" description="暂无操作日志" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="detailOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <el-dialog title="委外发料" v-model="issueOpen" width="500px" append-to-body>
      <el-form ref="issueRef" :model="issueForm" :rules="issueRules" label-width="100px">
        <el-form-item label="委外单号">
          <el-input :model-value="issueForm.subcontractNo" disabled />
        </el-form-item>
        <el-form-item label="物料">
          <el-input :model-value="issueForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input :model-value="issueForm.plannedQty" disabled />
        </el-form-item>
        <el-form-item label="发料数量" prop="quantity">
          <el-input-number
            v-model="issueForm.quantity"
            :precision="4"
            :min="0"
            :max="issueForm.plannedQty"
            style="width: 100%"
            placeholder="请输入发料数量"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="issueForm.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitIssue">确 定</el-button>
        <el-button @click="issueOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="委外收货" v-model="receiveOpen" width="500px" append-to-body>
      <el-form ref="receiveRef" :model="receiveForm" :rules="receiveRules" label-width="100px">
        <el-form-item label="委外单号">
          <el-input :model-value="receiveForm.subcontractNo" disabled />
        </el-form-item>
        <el-form-item label="物料">
          <el-input :model-value="receiveForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input :model-value="receiveForm.plannedQty" disabled />
        </el-form-item>
        <el-form-item label="收货数量" prop="quantity">
          <el-input-number
            v-model="receiveForm.quantity"
            :precision="4"
            :min="0"
            :max="receiveForm.plannedQty"
            style="width: 100%"
            placeholder="请输入收货数量"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="receiveForm.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitReceive">确 定</el-button>
        <el-button @click="receiveOpen = false">取 消</el-button>
      </template>
    </el-dialog>
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
  issueSubcontractMaterial,
  receiveSubcontractMaterial,
  submitApproval,
  getOperationLogs
} from '@/api/erp/prd/subcontract';
import { PrdSubcontractVO, PrdSubcontractQuery, PrdSubcontractForm } from '@/api/erp/prd/subcontract/types';
import { listVendor } from '@/api/erp/bd/vendor';
import { listMaterial } from '@/api/erp/bd/material';
import { listWork } from '@/api/erp/prd/work';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_subcontract_status } = toRefs<any>(proxy?.useDict('prd_subcontract_status'));

const subcontractList = ref<PrdSubcontractVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const open = ref(false);

const queryRef = ref<ElFormInstance>();
const subcontractRef = ref<ElFormInstance>();
const queryParams = ref<PrdSubcontractQuery>({ pageNum: 1, pageSize: 10 });
const form = ref<PrdSubcontractForm>({});

const vendorOptions = ref<any[]>([]);
const materialOptions = ref<any[]>([]);
const woOptions = ref<any[]>([]);

const detailOpen = ref(false);
const detailActiveTab = ref('info');
const detailData = ref<PrdSubcontractVO>({} as PrdSubcontractVO);
const operationLogs = ref<any[]>([]);
const logsLoading = ref(false);

const issueOpen = ref(false);
const issueRef = ref<ElFormInstance>();
const issueForm = ref<{ subcontractId: number; subcontractNo: string; materialName: string; plannedQty: number; quantity: number; remark: string }>(
  {} as any
);
const issueRules = {
  quantity: [{ required: true, message: '发料数量不能为空', trigger: 'blur' }]
};

const receiveOpen = ref(false);
const receiveRef = ref<ElFormInstance>();
const receiveForm = ref<{ subcontractId: number; subcontractNo: string; materialName: string; plannedQty: number; quantity: number; remark: string }>(
  {} as any
);
const receiveRules = {
  quantity: [{ required: true, message: '收货数量不能为空', trigger: 'blur' }]
};

const searchVendor = async (query: string) => {
  if (!query && query !== '') return;
  try {
    const res = await listVendor({ vendorName: query || undefined, pageNum: 1, pageSize: 20 });
    vendorOptions.value = res.rows || [];
  } catch {
    vendorOptions.value = [];
  }
};

const searchMaterial = async (query: string) => {
  if (!query && query !== '') return;
  try {
    const res = await listMaterial({ materialName: query || undefined, pageNum: 1, pageSize: 20 });
    materialOptions.value = res.rows || [];
  } catch {
    materialOptions.value = [];
  }
};

const searchWorkOrder = async (query: string) => {
  try {
    const res = await listWork({ woNo: query || undefined, pageNum: 1, pageSize: 20 });
    woOptions.value = res.rows || [];
  } catch {
    woOptions.value = [];
  }
};

const onVendorChange = (vendorId: number) => {
  const v = vendorOptions.value.find((i: any) => i.vendorId === vendorId);
  if (v) {
    form.value.vendorCode = v.vendorCode;
    form.value.vendorName = v.vendorName;
  }
};

const onMaterialChange = (materialId: number) => {
  const m = materialOptions.value.find((i: any) => i.materialId === materialId);
  if (m) {
    form.value.materialCode = m.materialCode;
    form.value.materialName = m.materialName;
    form.value.materialSpec = m.materialSpec;
    form.value.baseUomName = m.baseUomName;
  }
};

const rules = {
  subcontractNo: [{ required: true, message: '委外单号不能为空', trigger: 'blur' }],
  vendorId: [{ required: true, message: '供应商ID不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  plannedQty: [{ required: true, message: '计划数量不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  const res = await listSubcontract(queryParams.value);
  subcontractList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  queryRef.value?.resetFields();
  handleQuery();
};
const handleSelectionChange = (selection: PrdSubcontractVO[]) => {
  ids.value = selection.map((item) => item.subcontractId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const reset = () => {
  form.value = {};
  subcontractRef.value?.resetFields();
};

const handleAdd = () => {
  reset();
  open.value = true;
  title.value = '添加委外订单';
};

const handleUpdate = async (row: PrdSubcontractVO) => {
  reset();
  const res = await getSubcontract(row.subcontractId);
  form.value = res.data;
  open.value = true;
  title.value = '修改委外订单';
};

const submitForm = () => {
  subcontractRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      if (form.value.subcontractId) {
        await updateSubcontract(form.value);
        proxy?.$modal.msgSuccess('修改成功');
      } else {
        await addSubcontract(form.value);
        proxy?.$modal.msgSuccess('新增成功');
      }
      open.value = false;
      getList();
    }
  });
};

const handleDelete = async (row?: PrdSubcontractVO) => {
  const subcontractIds = row?.subcontractId || ids.value;
  await proxy?.$modal.confirm('是否确认删除编号为"' + subcontractIds + '"的数据项？');
  await delSubcontract(subcontractIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleRelease = async (row: PrdSubcontractVO) => {
  await proxy?.$modal.confirm('是否确认下达委外订单"' + row.subcontractNo + '"？');
  await releaseSubcontract(row.subcontractId);
  proxy?.$modal.msgSuccess('下达成功');
  getList();
};

const handleComplete = async (row: PrdSubcontractVO) => {
  await proxy?.$modal.confirm('是否确认完工委外订单"' + row.subcontractNo + '"？');
  await completeSubcontract(row.subcontractId);
  proxy?.$modal.msgSuccess('完工成功');
  getList();
};

const handleSubcontractIssue = (row: PrdSubcontractVO) => {
  issueForm.value = {
    subcontractId: row.subcontractId,
    subcontractNo: row.subcontractNo,
    materialName: row.materialName,
    plannedQty: row.plannedQty,
    quantity: row.plannedQty,
    remark: ''
  };
  issueOpen.value = true;
};

const submitIssue = () => {
  issueRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      await issueSubcontractMaterial(issueForm.value.subcontractId);
      proxy?.$modal.msgSuccess('发料成功');
      issueOpen.value = false;
      getList();
    }
  });
};

const handleSubcontractReceive = (row: PrdSubcontractVO) => {
  receiveForm.value = {
    subcontractId: row.subcontractId,
    subcontractNo: row.subcontractNo,
    materialName: row.materialName,
    plannedQty: row.plannedQty,
    quantity: row.plannedQty,
    remark: ''
  };
  receiveOpen.value = true;
};

const submitReceive = () => {
  receiveRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      await receiveSubcontractMaterial(receiveForm.value.subcontractId);
      proxy?.$modal.msgSuccess('收货成功');
      receiveOpen.value = false;
      getList();
    }
  });
};

const cancel = () => {
  open.value = false;
  reset();
};

const handleView = async (row: PrdSubcontractVO) => {
  const res = await getSubcontract(row.subcontractId);
  detailData.value = res.data;
  detailActiveTab.value = 'info';
  operationLogs.value = [];
  detailOpen.value = true;
};

const loadOperationLogs = async (subcontractId: number) => {
  logsLoading.value = true;
  try {
    const res = await getOperationLogs(subcontractId);
    operationLogs.value = res.data || [];
  } catch {
    operationLogs.value = [];
  } finally {
    logsLoading.value = false;
  }
};

watch(detailActiveTab, (val) => {
  if (val === 'logs' && detailData.value.subcontractId) {
    loadOperationLogs(detailData.value.subcontractId);
  }
});

const handleSubmitApproval = async (row: PrdSubcontractVO) => {
  await proxy?.$modal.confirm('是否确认提交审批委外订单"' + row.subcontractNo + '"？');
  await submitApproval(row.subcontractId);
  proxy?.$modal.msgSuccess('提交审批成功');
  getList();
};

const handleExport = () => {
  proxy?.download('erp/prd/subcontract/export', { ...queryParams.value }, `委外订单_${new Date().getTime()}.xlsx`);
};

onMounted(() => {
  getList();
});
</script>
