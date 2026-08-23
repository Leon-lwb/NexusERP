<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">批次信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="批次编码" prop="batchCode">
              <el-input v-model="form.batchCode" placeholder="请输入批次编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in wm_batch_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="源单号" prop="sourceDocNo">
              <el-input v-model="form.sourceDocNo" placeholder="请输入源单号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">物料信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入物料编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">供应商与仓库</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择仓库" clearable style="width: 100%">
                <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">日期与数量</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生产日期" prop="productionDate">
              <el-date-picker v-model="form.productionDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择生产日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="有效期至" prop="expiryDate">
              <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择有效期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="已收数量" prop="receivedQty">
              <el-input-number v-model="form.receivedQty" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="已发数量" prop="issuedQty">
              <el-input-number v-model="form.issuedQty" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="WmBatchForm">
import { getBatch, addBatch, updateBatch } from '@/api/erp/wm/batch';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { useRoute, useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_batch_status } = toRefs<any>(proxy.useDict('wm_batch_status'));

const route = useRoute();
const router = useRouter();

const props = defineProps<{
  batchId?: string;
  isView?: string;
}>();

const title = ref('');
const isViewMode = ref(false);
const warehouseOptions = ref<any[]>([]);

const form = ref<any>({
  batchId: null,
  batchCode: null,
  materialId: null,
  materialCode: null,
  materialName: null,
  materialSpec: null,
  vendorId: null,
  vendorCode: null,
  vendorName: null,
  warehouseId: null,
  warehouseCode: null,
  warehouseName: null,
  productionDate: null,
  expiryDate: null,
  quantity: 0,
  receivedQty: 0,
  issuedQty: 0,
  status: 'ACTIVE',
  sourceDocType: null,
  sourceDocId: null,
  sourceDocNo: null,
  ruleId: null,
  remark: null
});

const rules = {
  batchCode: [{ required: true, message: '批次编码不能为空', trigger: 'blur' }],
  materialCode: [{ required: true, message: '物料编码不能为空', trigger: 'blur' }],
  materialName: [{ required: true, message: '物料名称不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
};

onMounted(() => {
  const id = props.batchId || (route.query.batchId as string);
  const viewFlag = props.isView || (route.query.isView as string);
  const action = route.query.action as string;

  isViewMode.value = viewFlag === 'true' || viewFlag === '1' || action === 'view';

  if (action === 'add') {
    title.value = '新增批次';
    reset();
  } else if (id) {
    title.value = isViewMode.value ? '查看批次' : '编辑批次';
    getInfo(id);
  } else {
    title.value = '新增批次';
    reset();
  }

  loadWarehouseOptions();
});

function loadWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((res: any) => {
    warehouseOptions.value = res.rows || [];
  });
}

function getInfo(id: any) {
  getBatch(id).then((res: any) => {
    form.value = res.data;
  });
}

function reset() {
  form.value = {
    batchId: null,
    batchCode: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    productionDate: null,
    expiryDate: null,
    quantity: 0,
    receivedQty: 0,
    issuedQty: 0,
    status: 'ACTIVE',
    sourceDocType: null,
    sourceDocId: null,
    sourceDocNo: null,
    ruleId: null,
    remark: null
  };
  proxy.resetForm('formRef');
}

function submitForm() {
  (proxy.$refs['formRef'] as any).validate((valid: boolean) => {
    if (valid) {
      // 同步仓库信息
      const warehouse = warehouseOptions.value.find((w: any) => w.warehouseId === form.value.warehouseId);
      if (warehouse) {
        form.value.warehouseCode = warehouse.warehouseCode;
        form.value.warehouseName = warehouse.warehouseName;
      }
      if (form.value.batchId != null) {
        updateBatch(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/barcode/batch');
        });
      } else {
        addBatch(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/barcode/batch');
        });
      }
    }
  });
}

function cancel() {
  router.push('/erp/barcode/batch');
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.card-actions {
  display: flex;
  gap: 10px;
}
:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>
