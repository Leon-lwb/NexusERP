<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">工艺路线</span>
          <div class="card-actions">
            <el-button type="primary" @click="handleSave" v-if="!isViewMode">保存</el-button>
            <el-button @click="handleClose">关闭</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="路线编码" prop="routingCode">
              <el-input v-model="form.routingCode" placeholder="请输入工艺路线编码" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="路线名称" prop="routingName">
              <el-input v-model="form.routingName" placeholder="请输入工艺路线名称" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本号" prop="routingVersion">
              <el-input v-model="form.routingVersion" placeholder="请输入版本号" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="适用物料" prop="materialId">
              <el-input v-model="form.materialName" placeholder="请选择适用物料" readonly>
                <template #append v-if="!isViewMode">
                  <el-button icon="Search" @click="openMaterialSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker
                v-model="form.effectiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择生效日期"
                style="width: 100%"
                :readonly="isViewMode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失效日期" prop="expiryDate">
              <el-date-picker
                v-model="form.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择失效日期"
                style="width: 100%"
                :readonly="isViewMode"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status" :disabled="isViewMode">
                <el-radio v-for="dict in sys_core_status" :key="dict.value" :label="parseInt(dict.value)">
                  {{ dict.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="描述" prop="routingDesc">
              <el-input v-model="form.routingDesc" type="textarea" placeholder="请输入工艺路线描述" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">工序步骤</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="addStepRow" v-if="!isViewMode">添加步骤</el-button>
          </el-col>
        </el-row>

        <el-table :data="stepItems" style="width: 100%" border>
          <el-table-column type="index" width="50" align="center" label="序号" />
          <el-table-column label="工序" align="center" min-width="180">
            <template #default="scope">
              <el-select
                v-model="scope.row.processId"
                placeholder="请选择工序"
                filterable
                :disabled="isViewMode"
                style="width: 100%"
                @change="(val) => handleProcessChange(val, scope.$index)"
              >
                <el-option v-for="p in processList" :key="p.processId" :label="p.processCode + ' - ' + p.processName" :value="p.processId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="步骤名称" align="center" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.stepName" placeholder="可自定义" :readonly="isViewMode" />
            </template>
          </el-table-column>
          <el-table-column label="工作中心" align="center" width="180">
            <template #default="scope">
              <el-select v-model="scope.row.workstationId" placeholder="覆盖默认" clearable filterable :disabled="isViewMode" style="width: 100%">
                <el-option
                  v-for="ws in workstationList"
                  :key="ws.workstationId"
                  :label="ws.workstationCode + ' - ' + ws.workstationName"
                  :value="ws.workstationId"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="标准工时(h/件)" align="center" width="130">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.standardHours"
                :min="0"
                :precision="4"
                controls-position="right"
                :disabled="isViewMode"
                size="small"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="准备工时(h)" align="center" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.setupHours"
                :min="0"
                :precision="4"
                controls-position="right"
                :disabled="isViewMode"
                size="small"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="关键" align="center" width="70">
            <template #default="scope">
              <el-switch v-model="scope.row.isKeyProcess" :active-value="1" :inactive-value="0" :disabled="isViewMode" />
            </template>
          </el-table-column>
          <el-table-column label="委外" align="center" width="70">
            <template #default="scope">
              <el-switch v-model="scope.row.isOutsource" :active-value="1" :inactive-value="0" :disabled="isViewMode" />
            </template>
          </el-table-column>
          <el-table-column label="质检" align="center" width="70">
            <template #default="scope">
              <el-switch v-model="scope.row.qualityRequired" :active-value="1" :inactive-value="0" :disabled="isViewMode" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="100" v-if="!isViewMode">
            <template #default="scope">
              <el-button link type="danger" @click="removeStepRow(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="RoutingForm">
import { getRouting, addRouting, updateRouting, getRoutingSteps } from '@/api/erp/bd/routing';
import { listProcess } from '@/api/erp/bd/process';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';
import { listWorkstation } from '@/api/erp/bd/workstation';
import { RoutingForm, RoutingStepForm } from '@/api/erp/bd/routing/types';

const router = useRouter();
const route = useRoute();
const { proxy } = getCurrentInstance() as any;
const { sys_core_status } = toRefs<any>(proxy.useDict('sys_core_status'));

const formRef = ref(null);
const isViewMode = ref(false);
const form = ref<RoutingForm>({
  routingCode: '',
  routingName: '',
  routingVersion: '1.0',
  status: 1
});

const rules = ref({
  routingCode: [{ required: true, message: '工艺路线编码不能为空', trigger: 'blur' }],
  routingName: [{ required: true, message: '工艺路线名称不能为空', trigger: 'blur' }]
});

// 工序步骤数据
const stepItems = ref<any[]>([]);
const processList = ref<any[]>([]);
const workstationList = ref<any[]>([]);

// 物料选择
const materialSelectDialogRef = ref<any>(null);

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action;
  isViewMode.value = action === 'view';
  loadProcessList();
  loadWorkstationList();
  if (id) {
    loadRoutingDetail(id as string);
  }
});

/** 加载工艺路线详情 */
function loadRoutingDetail(id: string) {
  getRouting(id).then((response: any) => {
    form.value = response.data;
    // 加载步骤
    getRoutingSteps(Number(id)).then((stepResponse: any) => {
      stepItems.value = stepResponse.data || stepResponse.rows || [];
    });
  });
}

/** 加载工序列表 */
function loadProcessList() {
  listProcess({ status: 1 } as any).then((response: any) => {
    processList.value = response.rows || [];
  });
}

/** 加载工作中心列表 */
function loadWorkstationList() {
  listWorkstation({ status: 1 } as any).then((response: any) => {
    workstationList.value = response.rows || [];
  });
}

/** 工序选择变更 */
function handleProcessChange(processId: number, index: number) {
  const process = processList.value.find((p: any) => p.processId === processId);
  if (process) {
    stepItems.value[index].processCode = process.processCode;
    stepItems.value[index].processName = process.processName;
    stepItems.value[index].standardHours = process.standardHours;
    stepItems.value[index].setupHours = process.setupHours;
    stepItems.value[index].laborCount = process.laborCount;
    stepItems.value[index].machineCount = process.machineCount;
    stepItems.value[index].isKeyProcess = process.isKeyProcess;
    stepItems.value[index].isOutsource = process.isOutsource;
    stepItems.value[index].qualityRequired = process.qualityRequired;
    if (!stepItems.value[index].stepName) {
      stepItems.value[index].stepName = process.processName;
    }
  }
}

/** 添加步骤行 */
function addStepRow() {
  stepItems.value.push({
    stepId: null,
    routingId: form.value.routingId || null,
    sequenceNum: stepItems.value.length + 1,
    processId: null,
    processCode: '',
    processName: '',
    stepName: '',
    workstationId: null,
    workstationCode: '',
    workstationName: '',
    standardHours: null,
    setupHours: null,
    laborCount: null,
    machineCount: null,
    isKeyProcess: 0,
    isOutsource: 0,
    qualityRequired: 0,
    waitTime: 0,
    transferTime: 0,
    description: '',
    remark: ''
  });
}

/** 删除步骤行 */
function removeStepRow(index: number) {
  stepItems.value.splice(index, 1);
  // 重新排序序号
  stepItems.value.forEach((item, idx) => {
    item.sequenceNum = idx + 1;
  });
}

/** 保存 */
function handleSave() {
  formRef.value.validate((valid: boolean) => {
    if (valid) {
      // 计算总工序数和总工时
      form.value.totalSteps = stepItems.value.length;
      form.value.totalHours = stepItems.value.reduce((sum: number, item: any) => {
        return sum + (item.standardHours || 0) + (item.setupHours || 0);
      }, 0);

      if (form.value.routingId) {
        updateRouting(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          handleClose();
        });
      } else {
        addRouting(form.value).then((response: any) => {
          proxy.$modal.msgSuccess('新增成功');
          handleClose();
        });
      }
    }
  });
}

/** 关闭 */
function handleClose() {
  const obj = { path: '/erp/bd/routing' };
  proxy.$tab.closeOpenPage(obj);
}

/** 打开物料选择对话框 */
function openMaterialSelect() {
  materialSelectDialogRef.value?.open();
}

/** 物料选择回调 */
function handleMaterialSelected(row: any) {
  form.value.materialId = row.materialId;
  form.value.materialCode = row.materialCode;
  form.value.materialName = row.materialName;
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

.mb8 {
  margin-bottom: 8px;
}

:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
</style>
