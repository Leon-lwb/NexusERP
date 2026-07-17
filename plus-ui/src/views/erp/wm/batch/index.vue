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
              <el-option v-for="dict in wm_batch_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:batch:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:batch:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:batch:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Timer" @click="handleCheckExpiry" v-hasPermi="['wm:batch:edit']">检查过期</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="batchList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="批次编码" align="center" prop="batchCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料编码" align="center" prop="materialCode" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="供应商" align="center" prop="vendorName" min-width="120" show-overflow-tooltip />
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="生产日期" align="center" prop="productionDate" />
      <el-table-column label="有效期至" align="center" prop="expiryDate" />
      <el-table-column label="数量" align="center" prop="quantity" />
      <el-table-column label="已收数量" align="center" prop="receivedQty" />
      <el-table-column label="已发数量" align="center" prop="issuedQty" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_batch_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="源单号" align="center" prop="sourceDocNo" min-width="120" show-overflow-tooltip />
      <el-table-column label="创建时间" align="center" prop="createTime" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:batch:edit']">编辑</el-button>
          <el-button link type="primary" icon="Switch" @click="handleChangeStatus(scope.row)" v-hasPermi="['wm:batch:edit']">状态</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:batch:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 变更状态对话框 -->
    <el-dialog title="变更状态" v-model="statusDialogVisible" width="400px" append-to-body>
      <el-form ref="statusFormRef" :model="statusForm" label-width="80px">
        <el-form-item label="当前状态">
          <dict-tag :options="wm_batch_status" :value="statusForm.currentStatus" />
        </el-form-item>
        <el-form-item label="新状态" prop="newStatus">
          <el-select v-model="statusForm.newStatus" placeholder="请选择新状态" style="width: 100%">
            <el-option v-for="dict in wm_batch_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitChangeStatus">确 定</el-button>
          <el-button @click="statusDialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="WmBatch">
import { listBatch, delBatch, changeStatus, checkExpiry } from '@/api/erp/wm/batch';
import { WmBatchVO, WmBatchQuery } from '@/api/erp/wm/batch/types';
import { useRouter } from 'vue-router';

const router = useRouter();
const { proxy } = getCurrentInstance() as any;
const { wm_batch_status } = toRefs<any>(proxy.useDict('wm_batch_status'));

const batchList = ref<WmBatchVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const statusDialogVisible = ref(false);
const statusForm = ref({ batchId: null as number | null, currentStatus: '', newStatus: '' });

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    batchCode: null,
    materialCode: null,
    materialName: null,
    vendorCode: null,
    warehouseId: null,
    status: null
  } as WmBatchQuery
});

const { queryParams } = toRefs(data);

function getList() {
  loading.value = true;
  listBatch(queryParams.value)
    .then((response: any) => {
      batchList.value = response.rows;
      total.value = response.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection: WmBatchVO[]) {
  ids.value = selection.map((item: WmBatchVO) => item.batchId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  router.push({ path: '/erp/wm/batch/form', query: { action: 'add' } });
}

function handleUpdate(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }
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

function handleDelete(row: any) {
  const _ids = row?.batchId || ids.value;
  proxy.$modal
    .confirm('是否确认删除批次编号为"' + _ids + '"的数据项？')
    .then(function () {
      return delBatch(_ids);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download('/erp/wm/batch/export', { ...queryParams.value }, `批次管理_${new Date().getTime()}.xlsx`);
}

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

