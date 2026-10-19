<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm" :disabled="reading" :loading="saving" v-if="!isViewMode">保存</el-button>
            <el-button :disabled="saving" @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="创建后原批次码、主体、来源仓库和累计数量锁定；仅维护状态、有效期和备注。来源仓库与当前库存分开核对，累计量只能由真实业务过账产生。"
        type="info"
        :closable="false"
        show-icon
        class="mb8"
      />
      <el-alert v-if="error" :title="error" type="warning" :closable="false" class="mb8" />
      <el-descriptions v-if="existing" :column="2" border class="mb8"
        ><el-descriptions-item label="原批次ID">{{ form.batchId }}</el-descriptions-item
        ><el-descriptions-item label="原物料ID">{{ form.materialId }}</el-descriptions-item
        ><el-descriptions-item label="来源类型">{{ form.sourceDocType || '未提供' }}</el-descriptions-item
        ><el-descriptions-item label="来源ID">{{ form.sourceDocId || '未提供' }}</el-descriptions-item
        ><el-descriptions-item label="原编号规则">{{ form.ruleId || '未提供' }}</el-descriptions-item></el-descriptions
      >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode || reading || saving">
        <el-divider content-position="left">批次信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="批次编码" prop="batchCode">
              <el-input v-model="form.batchCode" :disabled="existing" placeholder="请输入批次编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in batchStatusOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="源单号" prop="sourceDocNo">
              <el-input v-model="form.sourceDocNo" :disabled="existing" placeholder="请输入源单号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">物料信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" readonly placeholder="选择已有物料"
                ><template #append><el-button :disabled="existing" @click="materialRef?.open()">选择物料</el-button></template></el-input
              >
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" :disabled="existing" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" :disabled="existing" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">供应商与来源仓库</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" :disabled="existing" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" :disabled="existing" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源仓库" prop="warehouseId">
              <el-input v-if="existing" v-model="form.warehouseName" readonly /><el-select
                v-else
                v-model="form.warehouseId"
                placeholder="请选择仓库"
                clearable
                style="width: 100%"
              >
                <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">日期与数量</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生产日期" prop="productionDate">
              <el-date-picker
                v-model="form.productionDate"
                :disabled="existing"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择生产日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="有效期至" prop="expiryDate">
              <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择有效期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数量" prop="quantity">
              <el-input :model-value="form.quantity ?? '0'" readonly disabled style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="已收数量" prop="receivedQty">
              <el-input :model-value="form.receivedQty ?? '0'" readonly disabled style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="已发数量" prop="issuedQty">
              <el-input :model-value="form.issuedQty ?? '0'" readonly disabled style="width: 100%" />
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
    <MaterialSelectDialog ref="materialRef" @select="selectMaterial" />
  </div>
</template>

<script setup lang="ts" name="WmBatchForm">
import { getBatch, addBatch, updateBatch } from '@/api/erp/wm/batch';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import type { WmBatchVO } from '@/api/erp/wm/batch/types';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { identityText } from '@/components/material-trace/model';
import { useRoute, useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_batch_status } = toRefs<any>(proxy.useDict('wm_batch_status'));
const batchStatusOptions = computed(() => [
  ...(wm_batch_status.value || []),
  ...((wm_batch_status.value || []).some((item: any) => item.value === 'INACTIVE') ? [] : [{ label: '已停用', value: 'INACTIVE' }])
]);

const route = useRoute();
const router = useRouter();

const props = defineProps<{
  batchId?: string;
  isView?: string;
}>();

const title = ref('');
const isViewMode = ref(false);
const warehouseOptions = ref<any[]>([]);
const reading = ref(false),
  saving = ref(false),
  error = ref('');
const original = shallowRef<WmBatchVO>();
const existing = computed(() => Boolean(props.batchId || route.query.batchId || form.value.batchId));
const materialRef = ref<InstanceType<typeof MaterialSelectDialog>>();
function selectMaterial(value: any) {
  if (existing.value) return;
  const materialId = identityText(value.materialId);
  if (!materialId) {
    error.value = '缺少已有物料身份';
    return;
  }
  Object.assign(form.value, { materialId, materialCode: value.materialCode, materialName: value.materialName, materialSpec: value.materialSpec });
}

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
  quantity: '0',
  receivedQty: '0',
  issuedQty: '0',
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

  if (!existing.value) loadWarehouseOptions();
});

function loadWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((res: any) => {
    warehouseOptions.value = res.rows || [];
  });
}

async function getInfo(id: string) {
  reading.value = true;
  error.value = '';
  original.value = undefined;
  try {
    const res = await getBatch(id);
    if (identityText(res.data.batchId) !== id) throw new Error('原批次详情身份不一致');
    original.value = JSON.parse(JSON.stringify(res.data));
    form.value = res.data;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取原批次失败';
  } finally {
    reading.value = false;
  }
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
    quantity: '0',
    receivedQty: '0',
    issuedQty: '0',
    status: 'ACTIVE',
    sourceDocType: null,
    sourceDocId: null,
    sourceDocNo: null,
    ruleId: null,
    remark: null
  };
  proxy.resetForm('formRef');
}

/** 编辑只维护原详情的白名单元数据，不用当前仓库主数据覆盖原来源快照。 */
async function submitForm() {
  if (reading.value || saving.value || isViewMode.value) return;
  try {
    await (proxy.$refs['formRef'] as any).validate();
    saving.value = true;
    error.value = '';
    if (existing.value) {
      if (!original.value) throw new Error('缺少原批次详情，不能修改');
      await updateBatch(form.value, original.value);
    } else {
      const warehouse = warehouseOptions.value.find((row: any) => identityText(row.warehouseId) === identityText(form.value.warehouseId));
      if (warehouse) Object.assign(form.value, { warehouseCode: warehouse.warehouseCode, warehouseName: warehouse.warehouseName });
      await addBatch({ ...form.value, quantity: '0', receivedQty: '0', issuedQty: '0' });
    }
    proxy.$modal.msgSuccess(existing.value ? '批次元数据维护成功' : '批次登记成功，库存由真实单据产生');
    router.push('/erp/barcode/batch');
  } catch (cause) {
    if (cause instanceof Error) error.value = cause.message;
  } finally {
    saving.value = false;
  }
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
