<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="序列号" prop="snCode">
            <el-input v-model="queryParams.snCode" placeholder="请输入序列号" clearable @keyup.enter="handleQuery" />
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
              <el-option v-for="dict in wm_sn_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="批次号" prop="batchCode">
            <el-input v-model="queryParams.batchCode" placeholder="请输入批次号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['wm:sn:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wm:sn:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:sn:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="List" @click="handleBatchGenerate()" v-hasPermi="['wm:sn:add']">批量生成</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="snList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" width="60" />
      <el-table-column label="序列号" align="center" prop="snCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="批次号" align="center" prop="batchCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="库位" align="center" prop="locationCode" min-width="100" />
      <el-table-column label="状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="wm_sn_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="来源单号" align="center" prop="sourceDocNo" min-width="120" show-overflow-tooltip />
      <el-table-column label="生产日期" align="center" prop="productionDate" width="110" />
      <el-table-column label="有效期至" align="center" prop="expiryDate" width="110" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-tooltip content="编辑" placement="top">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:sn:edit']" />
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:sn:remove']" />
          </el-tooltip>
          <el-tooltip content="绑定库存" placement="top" v-if="!scope.row.stockId">
            <el-button link type="success" icon="Link" @click="handleBind(scope.row)" v-hasPermi="['wm:sn:edit']" />
          </el-tooltip>
          <el-tooltip content="解绑库存" placement="top" v-if="scope.row.stockId">
            <el-button link type="warning" icon="Disconnect" @click="handleUnbind(scope.row)" v-hasPermi="['wm:sn:edit']" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 批量生成对话框 -->
    <el-dialog title="批量生成序列号" v-model="batchGenerateVisible" width="500px" append-to-body>
      <el-form ref="batchGenerateFormRef" :model="batchGenerateForm" :rules="batchGenerateRules" label-width="100px">
        <el-form-item label="物料ID" prop="materialId">
          <el-input v-model="batchGenerateForm.materialId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生成数量" prop="quantity">
          <el-input-number v-model="batchGenerateForm.quantity" :min="1" :max="1000" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联批次" prop="batchId">
          <el-select v-model="batchGenerateForm.batchId" placeholder="请选择批次（可空）" clearable filterable style="width: 100%">
            <el-option
              v-for="item in batchOptions"
              :key="item.batchId"
              :label="item.batchCode + '（' + (item.materialName || item.materialCode || '') + '）'"
              :value="item.batchId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitBatchGenerate">确 定</el-button>
          <el-button @click="batchGenerateVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 绑定库存对话框 -->
    <el-dialog title="绑定库存" v-model="bindVisible" width="500px" append-to-body>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="100px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="bindForm.warehouseId" placeholder="请选择仓库" clearable style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位" prop="locationId">
          <el-input v-model="bindForm.locationId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存ID" prop="stockId">
          <el-input v-model="bindForm.stockId" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitBind">确 定</el-button>
          <el-button @click="bindVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="WmSn">
import { listSn, getSn, addSn, updateSn, delSn, batchGenerateSn, bindStock, unbindStock } from '@/api/erp/wm/sn';
import { listBatch } from '@/api/erp/wm/batch';
import { WmSnVO, WmSnQuery } from '@/api/erp/wm/sn/types';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_sn_status } = toRefs<any>(proxy.useDict('wm_sn_status'));

const router = useRouter();

const queryParams = ref<WmSnQuery>({
  pageNum: 1,
  pageSize: 10,
  snCode: undefined,
  materialCode: undefined,
  materialName: undefined,
  status: undefined,
  batchCode: undefined
});

// 列表骨架（新增/修改为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: snList,
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
  apis: { list: listSn, get: getSn, add: addSn, update: updateSn, del: delSn },
  idField: 'snId',
  entityName: '序列号',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/wm/sn/export',
  exportFileName: '序列号'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({ path: '/erp/wm/sn/form', query: { action: 'add' } });
}

/** 修改按钮操作 */
function handleUpdate(row: WmSnVO) {
  const snId = row.snId || ids.value[0];
  router.push({ path: '/erp/wm/sn/form', query: { snId: snId as string, action: 'edit' } });
}

// ---------- 批量生成 ----------
const batchGenerateVisible = ref(false);
const batchGenerateFormRef = ref<ElFormInstance>();
const batchGenerateForm = ref({
  materialId: undefined as number | undefined,
  quantity: 1,
  batchId: undefined as number | undefined
});
const batchGenerateRules = {
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  quantity: [{ required: true, message: '生成数量不能为空', trigger: 'blur' }]
};

const batchOptions = ref<any[]>([]);

const handleBatchGenerate = () => {
  batchGenerateForm.value = { materialId: undefined, quantity: 1, batchId: undefined };
  batchOptions.value = [];
  batchGenerateVisible.value = true;
};

const loadBatchOptions = (materialId?: number) => {
  listBatch({ pageNum: 1, pageSize: 200, materialId }).then((res: any) => {
    batchOptions.value = res.rows || [];
  });
};

// 物料ID变化时刷新批次下拉；有物料ID才加载
watch(
  () => batchGenerateForm.value.materialId,
  (val) => {
    if (val) loadBatchOptions(val);
    else batchOptions.value = [];
  }
);

const submitBatchGenerate = async () => {
  batchGenerateFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      await batchGenerateSn(batchGenerateForm.value);
      proxy?.$modal.msgSuccess('批量生成成功');
      batchGenerateVisible.value = false;
      getList();
    }
  });
};

// ---------- 绑定库存 ----------
const bindVisible = ref(false);
const bindFormRef = ref<ElFormInstance>();
const bindForm = ref({
  snId: undefined as number | undefined,
  stockId: undefined as number | undefined,
  warehouseId: undefined as number | undefined,
  locationId: undefined as number | undefined
});
const bindRules = {
  warehouseId: [{ required: true, message: '仓库不能为空', trigger: 'change' }]
};
const warehouseOptions = ref<any[]>([]);

const handleBind = (row: WmSnVO) => {
  bindForm.value = {
    snId: row.snId,
    stockId: undefined,
    warehouseId: undefined,
    locationId: undefined
  };
  bindVisible.value = true;
  loadWarehouseOptions();
};

const submitBind = async () => {
  bindFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      await bindStock(bindForm.value);
      proxy?.$modal.msgSuccess('绑定成功');
      bindVisible.value = false;
      getList();
    }
  });
};

// ---------- 解绑库存 ----------
const handleUnbind = async (row: WmSnVO) => {
  await proxy?.$modal.confirm('是否确认解绑序列号"' + row.snCode + '"的库存？');
  await unbindStock(row.snId);
  proxy?.$modal.msgSuccess('解绑成功');
  getList();
};

const loadWarehouseOptions = () => {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((res: any) => {
    warehouseOptions.value = res.rows || [];
  });
};

onActivated(() => {
  getList();
});
</script>
