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
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="序列号" prop="snCode">
              <el-input v-model="form.snCode" placeholder="请输入序列号" />
            </el-form-item>
          </el-col>
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
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批次号" prop="batchCode">
              <el-input v-model="form.batchCode" placeholder="请输入批次号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in wm_sn_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">库存信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择仓库" clearable style="width: 100%">
                <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位编码" prop="locationCode">
              <el-input v-model="form.locationCode" placeholder="请输入库位编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库存ID" prop="stockId">
              <el-input v-model="form.stockId" placeholder="请输入库存ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">来源信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="来源单号" prop="sourceDocNo">
              <el-input v-model="form.sourceDocNo" placeholder="请输入来源单号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源类型" prop="sourceDocType">
              <el-input v-model="form.sourceDocType" placeholder="请输入来源类型" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规则ID" prop="ruleId">
              <el-input v-model="form.ruleId" placeholder="请输入规则ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">日期信息</el-divider>
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
        </el-row>

        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="WmSnForm">
import { getSn, addSn, updateSn } from '@/api/erp/wm/sn';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { useRoute, useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as any;
const { wm_sn_status } = toRefs<any>(proxy.useDict('wm_sn_status'));

const route = useRoute();
const router = useRouter();

const title = ref('');
const isViewMode = ref(false);
const warehouseOptions = ref<any[]>([]);

const form = ref<any>({
  snId: null,
  snCode: null,
  materialId: null,
  materialCode: null,
  materialName: null,
  materialSpec: null,
  batchId: null,
  batchCode: null,
  warehouseId: null,
  warehouseCode: null,
  warehouseName: null,
  locationId: null,
  locationCode: null,
  stockId: null,
  status: null,
  sourceDocType: null,
  sourceDocId: null,
  sourceDocNo: null,
  ruleId: null,
  productionDate: null,
  expiryDate: null,
  remark: null
});

const rules = {
  snCode: [{ required: true, message: '序列号不能为空', trigger: 'blur' }]
};

onMounted(() => {
  const snId = route.query.snId as any;
  const action = route.query.action as string;
  const isView = route.query.isView as string;

  isViewMode.value = action === 'view' || isView === 'true';
  title.value = action === 'add' ? '新增序列号' : action === 'edit' ? '修改序列号' : '查看序列号';

  loadWarehouseOptions();

  if (action === 'add') {
    reset();
  } else if (snId) {
    getInfo(snId);
  }
});

function loadWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((res: any) => {
    warehouseOptions.value = res.rows || [];
  });
}

function getInfo(id: any) {
  getSn(id).then((res: any) => {
    form.value = res.data;
  });
}

function reset() {
  form.value = {
    snId: null,
    snCode: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    locationId: null,
    locationCode: null,
    stockId: null,
    status: null,
    sourceDocType: null,
    sourceDocId: null,
    sourceDocNo: null,
    ruleId: null,
    productionDate: null,
    expiryDate: null,
    remark: null
  };
}

function submitForm() {
  const formRef = proxy.$refs['formRef'];
  formRef.validate((valid: boolean) => {
    if (valid) {
      const isEdit = route.query.action === 'edit' || form.value.snId != null;
      (isEdit ? updateSn(form.value) : addSn(form.value)).then(() => {
        proxy.$modal.msgSuccess('保存成功');
        router.push('/erp/wm/sn');
      });
    }
  });
}

function cancel() {
  router.push('/erp/wm/sn');
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
