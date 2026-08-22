<template>
  <el-dialog v-model="visible" :title="`选择固件版本 - ${step?.processName || ''}`" width="760px" append-to-body>
    <el-alert
      v-if="materialCode"
      :title="`当前物料: ${materialCode} ${materialName ? '/ ' + materialName : ''} (仅显示已发布固件)`"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 10px"
    />
    <el-table v-loading="loading" :data="firmwareList" highlight-current-row border height="360">
      <el-table-column label="固件编码" prop="firmwareCode" width="160" />
      <el-table-column label="固件名称" prop="firmwareName" min-width="150" show-overflow-tooltip />
      <el-table-column label="版本" prop="version" width="100" align="center">
        <template #default="scope">
          <span>v{{ scope.row.version }}</span>
          <el-tag v-if="scope.row.isDefault === '1'" type="success" size="small" style="margin-left: 4px">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" prop="publishTime" width="160" />
      <el-table-column label="文件MD5" prop="fileMd5" width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleSelect(scope.row)">选择</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="FirmwareSelectDialog">
import { listFirmware } from '@/api/mes/flash/firmware';
import type { FwFirmwareVO } from '@/api/mes/flash/firmware';
import type { ProWorkOrderStepVO } from '@/api/mes/pro/types';

const emit = defineEmits<{ select: [firmware: FwFirmwareVO] }>();

const visible = ref(false);
const loading = ref(false);
const firmwareList = ref<FwFirmwareVO[]>([]);
const step = ref<ProWorkOrderStepVO | null>(null);
const materialId = ref<number>();
const materialCode = ref('');
const materialName = ref('');

/**
 * 打开弹窗: 按工单物料加载已发布(RELEASED)固件列表
 */
const open = (stepRow: ProWorkOrderStepVO, woMaterialId?: number, woMaterialCode?: string, woMaterialName?: string) => {
  step.value = stepRow;
  materialId.value = woMaterialId;
  materialCode.value = woMaterialCode || '';
  materialName.value = woMaterialName || '';
  visible.value = true;
  loadList();
};
defineExpose({ open });

const loadList = async () => {
  loading.value = true;
  try {
    const res = await listFirmware({ materialId: materialId.value, status: 'RELEASED', pageNum: 1, pageSize: 100 });
    firmwareList.value = res.rows || [];
  } finally {
    loading.value = false;
  }
};

const handleSelect = (row: FwFirmwareVO) => {
  emit('select', row);
  visible.value = false;
};
</script>
