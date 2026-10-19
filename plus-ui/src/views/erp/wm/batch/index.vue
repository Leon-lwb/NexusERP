<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="批次编码" prop="batchCode">
            <el-input v-model="queryParams.batchCode" placeholder="请输入批次编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
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
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in batchStatusOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
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

    <el-alert
      title="批次主体、原码、来源和累计数量为只读事实；仅维护状态、有效期与备注。来源仓库不代表当前库存，差异须通过原业务单据核对更正。停用保留原批次及映射。"
      type="info"
      :closable="false"
      show-icon
      class="mb8"
    />
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['wm:batch:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="CircleClose" :disabled="multiple" @click="handleDisable()" v-hasPermi="['wm:batch:edit']"
          >停用</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:batch:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Timer" @click="handleCheckExpiry()" v-hasPermi="['wm:batch:edit']">检查过期</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="batchList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="批次编码" align="center" prop="batchCode" min-width="150" show-overflow-tooltip />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="140" show-overflow-tooltip />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="供应商" align="center" prop="vendorName" min-width="120" show-overflow-tooltip />
      <el-table-column label="来源仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="生产日期" align="center" prop="productionDate" width="110" />
      <el-table-column label="有效期至" align="center" prop="expiryDate" width="110" />
      <el-table-column label="数量" align="center" prop="quantity" min-width="80" />
      <el-table-column label="已收数量" align="center" prop="receivedQty" min-width="90" />
      <el-table-column label="已发数量" align="center" prop="issuedQty" min-width="90" />
      <el-table-column label="状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 'INACTIVE'" type="warning">已停用</el-tag
          ><dict-tag v-else :options="wm_batch_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="源单号" align="center" prop="sourceDocNo" min-width="140" show-overflow-tooltip />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:batch:edit']">编辑</el-button>
          <el-button link type="primary" icon="Switch" @click="handleChangeStatus(scope.row)" v-hasPermi="['wm:batch:edit']">状态</el-button>
          <el-button link type="primary" icon="CircleClose" @click="handleDisable(scope.row)" v-hasPermi="['wm:batch:edit']">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 变更状态对话框 -->
    <el-dialog title="变更状态" v-model="statusDialogVisible" width="500px" append-to-body>
      <el-form ref="statusFormRef" :model="statusForm" label-width="80px">
        <el-form-item label="当前状态">
          <dict-tag :options="wm_batch_status" :value="statusForm.currentStatus" />
        </el-form-item>
        <el-form-item label="新状态" prop="newStatus">
          <el-select v-model="statusForm.newStatus" placeholder="请选择新状态" style="width: 100%">
            <el-option v-for="dict in batchStatusOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitChangeStatus()">确 定</el-button>
          <el-button @click="statusDialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="WmBatch">
import { listBatch, changeStatus, checkExpiry } from '@/api/erp/wm/batch';
import { WmBatchVO, WmBatchQuery } from '@/api/erp/wm/batch/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useRouter } from 'vue-router';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_batch_status } = toRefs<any>(proxy.useDict('wm_batch_status'));
const batchStatusOptions = computed(() => [
  ...(wm_batch_status.value || []),
  ...((wm_batch_status.value || []).some((item: any) => item.value === 'INACTIVE') ? [] : [{ label: '已停用', value: 'INACTIVE' }])
]);

const statusDialogVisible = ref(false);
const statusForm = ref({ batchId: null as string | null, currentStatus: '', newStatus: '' });

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  batchCode: null,
  materialCode: null,
  materialName: null,
  vendorCode: null,
  warehouseId: null,
  status: null
} as WmBatchQuery);

// 列表骨架（新增/修改为路由表单页 form.vue，保留本地 router.push 跳转逻辑；
// 批次事实不在列表页直接新增、修改或删除）
const {
  loading,
  list: batchList,
  total,
  ids,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listBatch },
  idField: 'batchId',
  entityName: '批次',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/wm/batch/export',
  exportFileName: '批次管理'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作（跳转路由表单页） */
function handleAdd() {
  router.push({ path: '/erp/wm/batch/form', query: { action: 'add' } });
}

/** 修改按钮操作（跳转路由表单页） */
function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = batchList.value.find((item: any) => item.batchId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/wm/batch/form', query: { batchId: row.batchId, action: 'edit' } });
}

/** 通过原状态业务入口停用，保留批次来源和累计记录，不调用物理删除接口。 */
async function handleDisable(row?: WmBatchVO) {
  const targets = row ? [row.batchId] : ids.value;
  if (!targets.length) return;
  try {
    await proxy.$modal.confirm('确认停用所选批次？原批次码、来源及累计过账记录永久保留，不能回收或重新绑定。');
    for (const batchId of targets) await changeStatus(batchId, 'INACTIVE');
    proxy.$modal.msgSuccess('批次已停用，原事实保留');
  } catch (cause) {
    if (cause instanceof Error) proxy.$modal.msgError(cause.message);
  } finally {
    getList();
  }
}
/** 检查过期 */
function handleCheckExpiry() {
  proxy.$modal
    .confirm('确认执行过期批次检查吗？')
    .then(function () {
      return checkExpiry();
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('过期检查完成');
    })
    .catch(() => {});
}

/** 变更状态 */
function handleChangeStatus(row: WmBatchVO) {
  statusForm.value.batchId = row.batchId;
  statusForm.value.currentStatus = row.status;
  statusForm.value.newStatus = '';
  statusDialogVisible.value = true;
}

function submitChangeStatus() {
  if (!statusForm.value.newStatus) {
    proxy.$modal.msgWarning('请选择新状态');
    return;
  }
  changeStatus(statusForm.value.batchId!, statusForm.value.newStatus).then(() => {
    proxy.$modal.msgSuccess('状态变更成功');
    statusDialogVisible.value = false;
    getList();
  });
}

onActivated(() => {
  getList();
});
</script>
