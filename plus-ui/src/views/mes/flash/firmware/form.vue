<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'IN_REVIEW' || form.status === 'RELEASED'">查看流程</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="120px" :disabled="formDisabled">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="固件编码" prop="firmwareCode">
              <el-input v-model="form.firmwareCode" placeholder="-" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="固件名称" prop="firmwareName">
              <el-input v-model="form.firmwareName" placeholder="-" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="-" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="关联物料">
              <el-input :model-value="(form.materialCode || '') + ' - ' + (form.materialName || '')" placeholder="-" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="构建号">
              <el-input v-model="form.buildNo" placeholder="-" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="状态">
              <el-tag v-if="form.status" :type="statusTagType">{{ getDictLabel(mes_fw_status, form.status) || form.status }}</el-tag>
              <span v-else>-</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="默认版本">
              <el-tag v-if="form.isDefault === '1'" type="success">默认</el-tag>
              <span v-else>-</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="创建信息">
              <span>{{ form.createByName || form.createBy || '-' }} / {{ form.createTime || '-' }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">文件信息</el-divider>
        <el-row :gutter="20">
          <el-col :xs="24" :sm="24" :md="16">
            <el-form-item label="固件文件">
              <el-link v-if="form.fileUrl" :href="form.fileUrl" type="primary" target="_blank">
                下载固件文件（{{ form.fileMd5 ? 'MD5: ' + form.fileMd5 : '—' }}）
              </el-link>
              <span v-else>-</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="文件大小">
              <span>{{ formatFileSize(form.fileSize) }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">发布信息</el-divider>
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="发布人">
              <span>{{ form.publishedByName || form.publishedBy || '-' }}</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="发布时间">
              <span>{{ form.publishTime || '-' }}</span>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="审批流程">
              <span>{{ form.selectFlowId ? '流程#' + form.selectFlowId : '-' }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">其他信息</el-divider>
        <el-row :gutter="20">
          <el-col :xs="24" :sm="24" :md="16">
            <el-form-item label="发布说明" prop="releaseNote">
              <el-input v-model="form.releaseNote" type="textarea" :rows="3" placeholder="-" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="8">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="-" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="FwFirmwareForm">
import { getFirmware, getFirmwareProcId } from '@/api/mes/flash/firmware';
import type { FwFirmwareVO } from '@/api/mes/flash/firmware';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_fw_status } = toRefs<any>(proxy?.useDict('mes_fw_status'));
const router = useRouter();

/** 审批详情 / HistoricDetail 嵌入时传入(同 ERP 表单契约) */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

const title = ref('');
const isViewMode = ref(false);
const form = ref<Partial<FwFirmwareVO>>({});
const formDisabled = computed(() => isViewMode.value || props.disabled);

/** 流程详情嵌入时: 父组件注入 customFormData(含 firmwareId), 按 ID 拉取固件详情 */
watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).firmwareId != null) {
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '固件档案（流程）' : '固件档案';
      getFirmware((val as any).firmwareId).then((res: any) => {
        form.value = res.data || {};
      });
    }
  },
  { deep: true, immediate: true }
);

/** 查流程实例并跳转流程详情 */
function onViewFlow() {
  if (!form.value.firmwareId) return;
  getFirmwareProcId(String(form.value.firmwareId)).then((res: any) => {
    const procInsId = res.data;
    if (!procInsId) {
      proxy?.$modal.msgWarning('暂无流程信息');
      return;
    }
    router.push({ path: '/workflow/process/detail/' + procInsId, query: { processed: 'false' } });
  });
}

/** 字典标签 */
function getDictLabel(dictOptions: any, value: any) {
  const item = (dictOptions || []).find((d: any) => d.value === value);
  return item ? item.label : value;
}

/** 状态标签颜色 */
function statusTagType() {
  switch (form.value.status) {
    case 'DRAFT':
      return 'info';
    case 'IN_REVIEW':
      return 'warning';
    case 'RELEASED':
      return 'success';
    case 'ARCHIVED':
      return 'info';
    default:
      return 'info';
  }
}

/** 文件大小格式化 (字节 -> B/KB/MB/GB) */
function formatFileSize(size?: number) {
  if (size == null) return '-';
  if (size < 1024) return size + ' B';
  const units = ['KB', 'MB', 'GB'];
  let value = size;
  let unit = 'B';
  for (const u of units) {
    if (value < 1024) break;
    value = value / 1024;
    unit = u;
  }
  return value.toFixed(value >= 100 ? 0 : 1) + ' ' + unit;
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
