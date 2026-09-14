<template>
  <div class="app-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">工艺路线</span>
          <div class="card-actions">
            <el-button type="primary" :loading="submitting" @click="handleSave" v-if="!isViewMode">保存</el-button>
            <el-button @click="handleClose">关闭</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="路线编码" prop="routingCode">
              <AutoGenCodeField
                ref="autoGenCodeRef"
                v-model="form.routingCode"
                :rule-code="ROUTING_CODE_RULE"
                placeholder="请输入或自动生成工艺路线编码"
                :disabled="isViewMode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="路线名称" prop="routingName">
              <el-input v-model="form.routingName" placeholder="请输入工艺路线名称" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本号" prop="routingVersion">
              <el-input v-model="form.routingVersion" placeholder="请输入版本号，如 1.0" :readonly="isViewMode" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="适用物料" prop="materialId">
              <el-input v-model="form.materialName" placeholder="请选择适用物料（留空表示通用路线）" readonly>
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
                @change="revalidateExpiryDate"
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
          <el-col :span="22" class="step-summary">
            <span>共 {{ stepItems.length }} 道工序</span>
            <el-divider direction="vertical" />
            <span
              >合计工时（标准+准备）<strong>{{ totalHoursText }}</strong> h</span
            >
            <el-tag v-if="duplicatedProcessCount > 0" type="warning" size="small" class="ml10"> {{ duplicatedProcessCount }} 组重复工序 </el-tag>
          </el-col>
        </el-row>

        <el-table :data="stepItems" style="width: 100%" border>
          <el-table-column type="index" width="50" align="center" label="序号" />
          <el-table-column label="工序" align="center" min-width="200">
            <template #default="scope">
              <el-select
                v-model="scope.row.processId"
                placeholder="请选择工序"
                filterable
                clearable
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
          <el-table-column label="操作" align="center" width="190" fixed="right" v-if="!isViewMode">
            <template #default="scope">
              <el-button link type="primary" icon="Top" :disabled="scope.$index === 0" @click="moveStepRow(scope.$index, -1)" />
              <el-button link type="primary" icon="Bottom" :disabled="scope.$index === stepItems.length - 1" @click="moveStepRow(scope.$index, 1)" />
              <el-button link type="primary" icon="CopyDocument" @click="copyStepRow(scope.$index)" />
              <el-button link type="danger" icon="Delete" @click="removeStepRow(scope.$index)" />
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
import { getRouting, addRouting, updateRouting, getRoutingSteps, listRouting } from '@/api/erp/bd/routing';
import { listProcess } from '@/api/erp/bd/process';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import AutoGenCodeField from '@/components/AutoGenCodeField/index.vue';
import { listWorkstation } from '@/api/erp/bd/workstation';
import type { RoutingForm } from '@/api/erp/bd/routing/types';
import type { FormInstance } from 'element-plus';

const route = useRoute();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_core_status } = toRefs<any>(proxy.useDict('sys_core_status'));

/** 工艺路线编码的自动生成规则码，与后端 BdRoutingServiceImpl.ROUTING_CODE_RULE 一致 */
const ROUTING_CODE_RULE = 'ROUTING_CODE';
/** 版本号缺省值，与 bd_routing.routing_version 的 DDL 默认值一致 */
const DEFAULT_ROUTING_VERSION = '1.0';
/** 唯一性预校验单次拉取条数：后端编码是 like 模糊查询，取回后再精确比对 */
const UNIQUE_CHECK_PAGE_SIZE = 100;

const formRef = ref<FormInstance>();
const autoGenCodeRef = ref<any>(null);
const isViewMode = ref(false);
const loading = ref(false);
const submitting = ref(false);
const form = ref<RoutingForm>(createEmptyForm());

function createEmptyForm(): RoutingForm {
  return {
    routingId: undefined,
    routingCode: '',
    routingName: '',
    routingVersion: DEFAULT_ROUTING_VERSION,
    status: 1
  };
}

const rules = ref({
  routingCode: [
    { required: true, message: '工艺路线编码不能为空', trigger: 'blur' },
    { max: 64, message: '工艺路线编码长度不能超过 64 个字符', trigger: 'blur' },
    { validator: validateCodeVersionUnique, trigger: 'blur' }
  ],
  routingName: [
    { required: true, message: '工艺路线名称不能为空', trigger: 'blur' },
    { max: 128, message: '工艺路线名称长度不能超过 128 个字符', trigger: 'blur' }
  ],
  routingVersion: [
    { required: true, message: '版本号不能为空', trigger: 'blur' },
    { max: 20, message: '版本号长度不能超过 20 个字符', trigger: 'blur' },
    { validator: validateCodeVersionUnique, trigger: 'blur' }
  ],
  routingDesc: [{ max: 500, message: '描述长度不能超过 500 个字符', trigger: 'blur' }],
  expiryDate: [{ validator: validateExpiryDate, trigger: 'change' }]
});

// 工序步骤数据
const stepItems = ref<any[]>([]);
const processList = ref<any[]>([]);
const workstationList = ref<any[]>([]);

// 物料选择
const materialSelectDialogRef = ref<any>(null);

/** 合计工时（标准+准备），与后端 computeTotals 口径一致 */
const totalHours = computed(() =>
  stepItems.value.reduce((sum: number, item: any) => sum + (Number(item.standardHours) || 0) + (Number(item.setupHours) || 0), 0)
);
const totalHoursText = computed(() => (totalHours.value ? String(Number(totalHours.value.toFixed(4))) : '0'));

/** 重复工序组数（工序允许重复使用，如 检验→加工→检验，因此只提示不拦截） */
const duplicatedProcessCount = computed(() => {
  const counter = new Map<any, number>();
  stepItems.value.forEach((row: any) => {
    if (row.processId) counter.set(row.processId, (counter.get(row.processId) || 0) + 1);
  });
  let groups = 0;
  counter.forEach((count) => {
    if (count > 1) groups += 1;
  });
  return groups;
});

// 表单页被 keep-alive 缓存（路由 name=RoutingForm），在表单页已打开时从列表点另一行的
// 「修改/查看」不会重新触发 onMounted。若不监听 query，会一直显示上一条数据且模式不切换，
// 因此这里改为监听 route.query 并立即执行一次。
watch(
  () => route.query,
  () => initPage(),
  { immediate: true }
);

/** 初始化页面（新增/修改/查看共用） */
function initPage() {
  const id = route.query.id as string | undefined;
  isViewMode.value = route.query.action === 'view';
  // 复位自动生成开关与表单，避免复用组件时残留上一条记录（含 routingId）
  autoGenCodeRef.value?.reset();
  form.value = createEmptyForm();
  stepItems.value = [];
  formRef.value?.clearValidate();
  if (id) {
    loadRoutingDetail(id);
  }
}

/** 加载工艺路线详情 */
function loadRoutingDetail(id: string) {
  loading.value = true;
  getRouting(id)
    .then((response: any) => {
      form.value = response.data;
      // 加载步骤（雪花ID不能 Number() 截断，字符串透传）
      return getRoutingSteps(id);
    })
    .then((stepResponse: any) => {
      stepItems.value = (stepResponse?.data || stepResponse?.rows || []).map((step: any) => ({ ...step }));
      renumberSteps();
    })
    .finally(() => {
      loading.value = false;
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

/** 工序选择变更：带出工序默认参数 */
function handleProcessChange(processId: any, index: number) {
  const row = stepItems.value[index];
  const process = processList.value.find((p: any) => p.processId === processId);
  if (!process) {
    return;
  }
  row.processCode = process.processCode;
  row.processName = process.processName;
  row.standardHours = process.standardHours;
  row.setupHours = process.setupHours;
  row.laborCount = process.laborCount;
  row.machineCount = process.machineCount;
  row.isKeyProcess = process.isKeyProcess;
  row.isOutsource = process.isOutsource;
  row.qualityRequired = process.qualityRequired;
  if (!row.stepName) {
    row.stepName = process.processName;
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

/** 复制步骤行（沿用工序与工时，清空主键，紧跟在原行后插入） */
function copyStepRow(index: number) {
  const source = stepItems.value[index];
  const copy = { ...source, stepId: null };
  copy.stepName = source.stepName ? `${source.stepName}(副本)` : source.processName || '';
  stepItems.value.splice(index + 1, 0, copy);
  renumberSteps();
}

/** 上移/下移步骤行 */
function moveStepRow(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= stepItems.value.length) {
    return;
  }
  const rows = stepItems.value;
  [rows[index], rows[target]] = [rows[target], rows[index]];
  renumberSteps();
}

/** 删除步骤行（二次确认，避免误删） */
function removeStepRow(index: number) {
  const row = stepItems.value[index];
  const label = row.processName || row.stepName || `第 ${index + 1} 行`;
  proxy.$modal
    .confirm(`确认移除工序「${label}」？`)
    .then(() => {
      stepItems.value.splice(index, 1);
      renumberSteps();
    })
    .catch(() => {});
}

/** 重排序号，保证 sequenceNum 连续（后端 verifyRouting 会校验序号连续性） */
function renumberSteps() {
  stepItems.value.forEach((item: any, idx: number) => {
    item.sequenceNum = idx + 1;
  });
}

/** 生效日期变更时联动校验失效日期区间 */
function revalidateExpiryDate() {
  if (!form.value.expiryDate) {
    return;
  }
  formRef.value?.validateField('expiryDate');
}

/** 失效日期不能早于生效日期 */
function validateExpiryDate(_rule: any, value: any, callback: (error?: Error) => void) {
  const { effectiveDate, expiryDate } = form.value;
  if (effectiveDate && expiryDate && new Date(expiryDate).getTime() < new Date(effectiveDate).getTime()) {
    callback(new Error('失效日期不能早于生效日期'));
    return;
  }
  callback();
}

/**
 * 编码 + 版本唯一性预校验。
 * <p>后端 list 接口对 routingCode 用的是 like 模糊查询，因此取回结果后必须按
 * code + version 再做一次精确比对；编辑态需排除自身 routingId。
 * 这里只是提交前的即时反馈，权威判定仍在后端 BdRoutingServiceImpl（并含唯一索引兜底）。</p>
 */
async function validateCodeVersionUnique(_rule: any, value: any, callback: (error?: Error) => void) {
  const code = String(value || '').trim();
  const version = String(form.value.routingVersion || '').trim();
  if (!code || !version || isViewMode.value) {
    callback();
    return;
  }
  try {
    const response: any = await listRouting({ pageNum: 1, pageSize: UNIQUE_CHECK_PAGE_SIZE, routingCode: code });
    const hit = (response.rows || []).some(
      (row: any) => row.routingCode === code && row.routingVersion === version && String(row.routingId) !== String(form.value.routingId ?? '')
    );
    callback(hit ? new Error(`编码[${code}] + 版本号[${version}] 已存在`) : undefined);
  } catch (error) {
    // 预校验网络异常不应阻塞提交，交由后端最终校验
    callback();
  }
}

/** 保存 */
async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (stepItems.value.length === 0) {
    proxy.$modal.msgWarning('请至少添加一道工序步骤');
    return;
  }
  const blankRowIndex = stepItems.value.findIndex((row: any) => !row.processId);
  if (blankRowIndex >= 0) {
    proxy.$modal.msgWarning(`第 ${blankRowIndex + 1} 行未选择工序`);
    return;
  }
  // 工序允许重复（如 检验→加工→检验），仅提示不拦截
  if (duplicatedProcessCount.value > 0) {
    try {
      await proxy.$modal.confirm(`检测到 ${duplicatedProcessCount.value} 组重复工序，确认继续保存？`);
    } catch {
      return;
    }
  }

  submitting.value = true;
  try {
    // 自动生成开关打开时清空编码，交由后端按规则生成，避免预览消耗流水号导致跳号
    autoGenCodeRef.value?.applyAutoGen();
    form.value.totalSteps = stepItems.value.length;
    form.value.totalHours = totalHours.value;

    // 工序步骤随主表一并提交（后端 insert/update 落库 steps）
    const submitData = { ...form.value, steps: stepItems.value };
    if (form.value.routingId) {
      await updateRouting(submitData);
      proxy.$modal.msgSuccess('修改成功');
    } else {
      await addRouting(submitData);
      proxy.$modal.msgSuccess('新增成功');
    }
    handleClose();
  } catch (error) {
    // 错误提示由 axios 拦截器统一弹出（含后端「编码+版本已存在」的业务异常），此处只保证按钮恢复可用
  } finally {
    submitting.value = false;
  }
}

/** 关闭 */
function handleClose() {
  const obj = { path: '/erp/tech/routing' };
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

loadProcessList();
loadWorkstationList();
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

.ml10 {
  margin-left: 10px;
}

.step-summary {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  color: #606266;
  line-height: 32px;
}

.step-summary strong {
  color: #409eff;
  margin: 0 2px;
}

:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
</style>
