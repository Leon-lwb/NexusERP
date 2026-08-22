<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <div class="form-container">
        <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="工单编号" prop="woNo">
                <el-input v-model="form.woNo" placeholder="请输入工单编号" :disabled="!!form.mesWoId" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="产品" prop="materialId">
                <el-select
                  v-model="form.materialId"
                  placeholder="请选择产品"
                  filterable
                  :disabled="!!form.mesWoId"
                  @change="handleMaterialChange"
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in materialOptions"
                    :key="item.materialId"
                    :label="item.materialCode + ' - ' + item.materialName"
                    :value="item.materialId"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="计划数量" prop="plannedQty">
                <el-input-number v-model="form.plannedQty" :min="0.0001" :precision="4" :step="1" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="优先级" prop="priority">
                <el-input-number v-model="form.priority" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">生产配置</el-divider>
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="BOM" prop="bomId">
                <el-select v-model="form.bomId" placeholder="请选择BOM" filterable clearable style="width: 100%">
                  <el-option
                    v-for="item in bomOptions"
                    :key="item.bomId"
                    :label="(item.parentMaterialCode || '') + ' - ' + (item.parentMaterialName || '') + ' v' + (item.bomVersion || '')"
                    :value="item.bomId"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="工艺路线" prop="routingId">
                <el-select v-model="form.routingId" placeholder="请选择工艺路线" filterable clearable style="width: 100%">
                  <el-option
                    v-for="item in routingOptions"
                    :key="item.routingId"
                    :label="item.routingCode + ' - ' + item.routingName + ' v' + (item.routingVersion || '')"
                    :value="item.routingId"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="关联设备">
                <equipment-select v-model="form.equipmentId" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="发布包" prop="releasePackageId">
                <el-select v-model="form.releasePackageId" placeholder="客户定制化配置(可选)" filterable clearable style="width: 100%">
                  <el-option
                    v-for="item in packageOptions"
                    :key="item.packageId"
                    :label="`${item.clientName} v${item.version}${item.customerProductCode ? ' / ' + item.customerProductCode : ''}${item.isDefault === '1' ? '(默认)' : ''}`"
                    :value="item.packageId"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert
            v-if="form.releasePackageId"
            type="info"
            :closable="false"
            show-icon
            title="已选定发布包: 工单下达时将按发布包覆写工序固件映射/BOM选配/刷写配置(在标准工艺路线基础上增量覆写)"
            style="margin-bottom: 12px"
          />

          <el-divider content-position="left">计划时间</el-divider>
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="计划开工" prop="planStartDate">
                <el-date-picker
                  v-model="form.planStartDate"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="请选择计划开工时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="6">
              <el-form-item label="计划完工" prop="planEndDate">
                <el-date-picker
                  v-model="form.planEndDate"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="请选择计划完工时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">其他信息</el-divider>
          <el-row :gutter="20">
            <el-col :xs="24" :sm="24" :md="16" :lg="12">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" :rows="3" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesWorkOrderForm">
import { getWorkOrder, addWorkOrder, updateWorkOrder } from '@/api/mes/pro/workorder';
import { listMaterial } from '@/api/erp/bd/material/index';
import { listBom } from '@/api/erp/bd/bom/index';
import { listRouting } from '@/api/erp/bd/routing/index';
import { listReleasePackage } from '@/api/mes/release/package';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';
import type { ProWorkOrderForm } from '@/api/mes/pro/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const title = ref('');
const formRef = ref();
const materialOptions = ref<any[]>([]);
const bomOptions = ref<any[]>([]);
const routingOptions = ref<any[]>([]);
/** 该物料已发布的客户定制化发布包(手动工单下达时按包覆写) */
const packageOptions = ref<any[]>([]);

const initForm: ProWorkOrderForm = {
  woNo: undefined,
  materialId: undefined,
  materialCode: undefined,
  materialName: undefined,
  plannedQty: undefined,
  bomId: undefined,
  routingId: undefined,
  equipmentId: undefined,
  priority: 0,
  planStartDate: undefined,
  planEndDate: undefined,
  releasePackageId: undefined,
  remark: undefined
};
const form = ref<ProWorkOrderForm>({ ...initForm });

const rules = {
  woNo: [{ required: true, message: '工单编号不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  plannedQty: [{ required: true, message: '计划数量不能为空', trigger: 'blur' }]
};

onMounted(async () => {
  await loadMaterials();
  const id = route.query.id as string;
  const action = route.query.action as string;

  if (id && action === 'edit') {
    title.value = '修改MES工单';
    loadWorkOrder(id);
  } else {
    title.value = '新增MES工单';
  }
});

const loadWorkOrder = async (id: string) => {
  const res = await getWorkOrder(id);
  const data = res.data;
  form.value = {
    mesWoId: data.mesWoId,
    woNo: data.woNo,
    materialId: data.materialId,
    materialCode: data.materialCode,
    materialName: data.materialName,
    plannedQty: data.plannedQty,
    bomId: data.bomId,
    routingId: data.routingId,
    equipmentId: data.equipmentId,
    priority: data.priority,
    planStartDate: data.planStartDate,
    planEndDate: data.planEndDate,
    releasePackageId: data.releasePackageId,
    remark: data.remark
  };
  loadPackages(data.materialId);
};

const loadMaterials = async () => {
  const [matRes, bomRes, routingRes] = await Promise.all([
    listMaterial({ pageNum: 1, pageSize: 1000 } as any),
    listBom({ status: 1, isCurrent: 1, pageNum: 1, pageSize: 1000 } as any),
    listRouting({ status: 1, pageNum: 1, pageSize: 1000 } as any)
  ]);
  materialOptions.value = matRes.rows || [];
  bomOptions.value = bomRes.rows || [];
  routingOptions.value = routingRes.rows || [];
};

const handleMaterialChange = (materialId: number) => {
  const m = materialOptions.value.find((item: any) => item.materialId === materialId);
  if (m) {
    form.value.materialCode = m.materialCode;
    form.value.materialName = m.materialName;
  }
  // 物料变更后已选发布包失效, 重新加载该物料的已发布包
  form.value.releasePackageId = undefined;
  loadPackages(materialId);
};

/** 加载该物料已发布的客户定制化发布包(手动工单可选定, 下达时按包覆写) */
const loadPackages = async (materialId?: number) => {
  if (!materialId) {
    packageOptions.value = [];
    return;
  }
  try {
    const res = await listReleasePackage({ materialId, status: 'RELEASED', pageNum: 1, pageSize: 100 });
    packageOptions.value = res.rows || [];
  } catch (e) {
    packageOptions.value = [];
  }
};

const submitForm = async () => {
  await formRef.value?.validate();
  if (form.value.mesWoId != null) {
    await updateWorkOrder(form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addWorkOrder(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  router.push('/mes/wo/workorder');
};

const cancel = () => {
  const obj = { path: '/mes/wo/workorder' };
  proxy?.$tab.closeOpenPage(obj);
};
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
.form-container {
  padding: 0 10px;
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
