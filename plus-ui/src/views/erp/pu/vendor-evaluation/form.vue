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

      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorId">
              <el-select v-model="form.vendorId" filterable placeholder="请选择供应商" @change="handleVendorChange">
                <el-option v-for="item in vendorList" :key="item.vendorId" :label="item.vendorName" :value="item.vendorId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商编码">
              <el-input v-model="form.vendorCode" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="评估周期" prop="evalPeriod">
              <el-date-picker v-model="form.evalPeriod" type="month" value-format="YYYY-MM" placeholder="请选择评估周期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">评估结果</el-divider>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="交货及时率">
              <el-input v-model="form.deliveryRate" disabled>
                <template #append>%</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="质量合格率">
              <el-input v-model="form.qualityRate" disabled>
                <template #append>%</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="价格竞争力">
              <el-input v-model="form.priceScore" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="综合评分">
              <el-input v-model="form.totalScore" disabled>
                <template #prepend>
                  <el-tag :type="getEvalLevelType(form.evalLevel)" size="small">{{ form.evalLevel || '-' }}</el-tag>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="总订单数">
              <el-input-number v-model="form.totalOrders" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="按时交货数">
              <el-input-number v-model="form.onTimeOrders" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="总收货数量">
              <el-input-number v-model="form.totalReceivedQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="退货数量">
              <el-input-number v-model="form.returnQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="评估日期" prop="evalDate">
              <el-date-picker v-model="form.evalDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择评估日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="VendorEvaluationForm">
import { addVendorEvaluation, getVendorEvaluation, updateVendorEvaluation, calculateVendorEvaluation } from '@/api/erp/pu/vendor-evaluation';
import { listVendor } from '@/api/erp/bd/vendor';
import { useRoute, useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as any;
const { pu_vendor_eval_level } = toRefs<any>(proxy.useDict('pu_vendor_eval_level'));

const route = useRoute();
const router = useRouter();

const title = ref('');
const isViewMode = ref(false);
const vendorList = ref<any[]>([]);

const form = ref<any>({
  evaluationId: null,
  vendorId: null,
  vendorCode: null,
  vendorName: null,
  evalPeriod: null,
  deliveryRate: null,
  qualityRate: null,
  priceScore: null,
  totalScore: null,
  evalLevel: null,
  totalOrders: 0,
  onTimeOrders: 0,
  totalReceivedQty: 0,
  returnQty: 0,
  evalDate: null,
  remark: null
});

const rules = {
  vendorId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  evalPeriod: [{ required: true, message: '请选择评估周期', trigger: 'change' }]
};

onMounted(() => {
  const id = route.query.id as any;
  const action = route.query.action as string;

  isViewMode.value = action === 'view';
  title.value = action === 'add' ? '新增供应商评估' : action === 'edit' ? '修改供应商评估' : '查看供应商评估';

  loadVendors();

  if (action === 'add') {
    reset();
  } else if (id) {
    getInfo(id);
  }
});

function loadVendors() {
  listVendor({ pageNum: 1, pageSize: 999 }).then((res: any) => {
    vendorList.value = res.rows || res.data || [];
  });
}

function getInfo(id: any) {
  getVendorEvaluation(id).then((res: any) => {
    form.value = res.data;
  });
}

function reset() {
  form.value = {
    evaluationId: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    evalPeriod: null,
    deliveryRate: null,
    qualityRate: null,
    priceScore: null,
    totalScore: null,
    evalLevel: null,
    totalOrders: 0,
    onTimeOrders: 0,
    totalReceivedQty: 0,
    returnQty: 0,
    evalDate: null,
    remark: null
  };
}

function handleVendorChange(vendorId: number) {
  const vendor = vendorList.value.find((v: any) => v.vendorId === vendorId);
  if (vendor) {
    form.value.vendorCode = vendor.vendorCode;
    form.value.vendorName = vendor.vendorName;
  }
}

function getEvalLevelType(level: string) {
  const map: Record<string, string> = { 'A': 'success', 'B': 'primary', 'C': 'warning', 'D': 'danger' };
  return map[level] || 'info';
}

function submitForm() {
  const isEdit = route.query.action === 'edit' || form.value.evaluationId != null;
  (isEdit ? updateVendorEvaluation(form.value) : addVendorEvaluation(form.value)).then(() => {
    proxy.$modal.msgSuccess('保存成功');
    router.push('/erp/puBiz/vendorEvaluation');
  });
}

function cancel() {
  router.push('/erp/puBiz/vendorEvaluation');
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
:deep(.el-form-item__label) {
  font-weight: 500;
}
</style>
