<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="委外单号" prop="subcontractNo">
        <el-input v-model="queryParams.subcontractNo" placeholder="请输入委外单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商名称" prop="vendorName">
        <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="物料编码" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="委外状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择委外状态" clearable>
          <el-option v-for="dict in prd_subcontract_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:subcontract:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['prd:subcontract:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['prd:subcontract:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="subcontractList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="委外单号" align="center" prop="subcontractNo" width="150" />
      <el-table-column label="供应商" align="center" prop="vendorName" min-width="150" show-overflow-tooltip />
      <el-table-column label="物料编码" align="center" prop="materialCode" width="120" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="计划数量" align="center" prop="plannedQty" width="100" />
      <el-table-column label="单价" align="center" prop="unitPrice" width="100" />
      <el-table-column label="总金额" align="center" prop="totalAmount" width="120" />
      <el-table-column label="计划开始" align="center" prop="planStartDate" width="120" />
      <el-table-column label="计划结束" align="center" prop="planEndDate" width="120" />
      <el-table-column label="关联工单" align="center" prop="woNo" width="150" />
      <el-table-column label="委外状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="prd_subcontract_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="250">
        <template #default="scope">
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

    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="subcontractRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="委外单号" prop="subcontractNo">
              <el-input v-model="form.subcontractNo" placeholder="请输入委外单号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商ID" prop="vendorId">
              <el-input-number v-model="form.vendorId" placeholder="请输入供应商ID" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料ID" prop="materialId">
              <el-input-number v-model="form.materialId" placeholder="请输入物料ID" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入物料编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单位" prop="baseUomName">
              <el-input v-model="form.baseUomName" placeholder="请输入单位" />
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
            <el-form-item label="单价" prop="unitPrice">
              <el-input-number v-model="form.unitPrice" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始日期" prop="planStartDate">
              <el-date-picker v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择计划开始日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
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
  </div>
</template>

<script setup name="PrdSubcontract" lang="ts">
import { listSubcontract, getSubcontract, addSubcontract, updateSubcontract, delSubcontract, releaseSubcontract, completeSubcontract } from '@/api/erp/prd/subcontract';
import { PrdSubcontractVO, PrdSubcontractQuery, PrdSubcontractForm } from '@/api/erp/prd/subcontract/types';

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

const rules = {
  subcontractNo: [{ required: true, message: '委外单号不能为空', trigger: 'blur' }],
  vendorId: [{ required: true, message: '供应商ID不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  plannedQty: [{ required: true, message: '计划数量不能为空', trigger: 'blur' }],
};

const getList = async () => {
  loading.value = true;
  const res = await listSubcontract(queryParams.value);
  subcontractList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

const handleQuery = () => { queryParams.value.pageNum = 1; getList(); };
const resetQuery = () => { queryRef.value?.resetFields(); handleQuery(); };
const handleSelectionChange = (selection: PrdSubcontractVO[]) => {
  ids.value = selection.map(item => item.subcontractId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const reset = () => {
  form.value = {};
  subcontractRef.value?.resetFields();
};

const handleAdd = () => { reset(); open.value = true; title.value = '添加委外订单'; };

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

const cancel = () => { open.value = false; reset(); };

const handleExport = () => { proxy?.download('erp/prd/subcontract/export', { ...queryParams.value }, `委外订单_${new Date().getTime()}.xlsx`); };

onMounted(() => { getList(); });
</script>
