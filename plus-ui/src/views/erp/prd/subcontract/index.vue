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
  submitApproval
} from '@/api/erp/prd/subcontract';
import SubcontractClosureDetail from './detail.vue';
import { useRouter } from 'vue-router';
import type { PrdSubcontractVO, PrdSubcontractQuery } from '@/api/erp/prd/subcontract/types';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_subcontract_status } = toRefs<any>(proxy.useDict('prd_subcontract_status'));

const queryParams = ref<PrdSubcontractQuery>({ pageNum: 1, pageSize: 10 });

const closureRow = ref<any>(null);

/** 打开 PCBA 闭环详情抽屉 */
const handleClosure = (row: PrdSubcontractVO) => {
  closureRow.value = row;
};
const subcontractRef = ref<any>();

const {
  loading,
  list: subcontractList,
  total,
  ids,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  // get/add/update 仅供 useCrud 签名使用; 新增/编辑为路由表单页 form.vue, 列表页不调用
  apis: { list: listSubcontract, get: getSubcontract, add: addSubcontract, update: updateSubcontract, del: delSubcontract },
  idField: 'subcontractId',
  entityName: '委外订单',
  formRef: subcontractRef,
  initForm: () => ({}),
  queryParams,
  exportUrl: 'erp/prd/subcontract/export',
  exportFileName: '委外订单'
});

const router = useRouter();

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作（跳转路由表单页, 单号留空由后端自动生成） */
function handleAdd() {
  router.push({ path: '/erp/prdSub/subcontract/form', query: { action: 'add' } });
}

/** 修改按钮操作（跳转路由表单页） */
function handleUpdate(row?: PrdSubcontractVO) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    row = subcontractList.value.find((item: any) => item.subcontractId == ids.value[0]);
    if (!row) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
  }
  if (row.status !== 'DRAFT') {
    proxy.$modal.msgWarning('仅草稿状态可修改');
    return;
  }
  router.push({ path: '/erp/prdSub/subcontract/form', query: { subcontractId: String(row.subcontractId) } });
}

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
