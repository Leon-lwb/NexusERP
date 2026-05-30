<template>
  <div class="app-container">
    <el-form :inline="true" class="mb8">
      <el-form-item label="工单号">
        <el-select v-model="selectedWoId" filterable remote :remote-method="searchWo" placeholder="请选择工单" @change="loadResources" clearable style="width: 300px;">
          <el-option v-for="wo in woOptions" :key="wo.woId" :label="`${wo.woNo} - ${wo.materialName || ''}`" :value="wo.woId" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAddRow" :disabled="!selectedWoId">添加资源</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Check" @click="handleSave" :disabled="!selectedWoId || resourceList.length === 0">保存</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" :disabled="!selectedWoId">导出</el-button>
      </el-col>
    </el-row>

    <el-table :data="resourceList" border v-loading="loading">
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="资源类型" prop="resourceType" width="140">
        <template #default="scope">
          <el-select v-model="scope.row.resourceType" placeholder="请选择" style="width: 100%">
            <el-option v-for="(label, key) in RESOURCE_TYPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="资源编码" prop="resourceCode" width="150">
        <template #default="scope">
          <el-input v-model="scope.row.resourceCode" placeholder="请输入编码" />
        </template>
      </el-table-column>
      <el-table-column label="资源名称" prop="resourceName" min-width="180">
        <template #default="scope">
          <el-input v-model="scope.row.resourceName" placeholder="请输入名称" />
        </template>
      </el-table-column>
      <el-table-column label="计划开始" prop="planStartTime" width="180">
        <template #default="scope">
          <el-date-picker v-model="scope.row.planStartTime" type="datetime" placeholder="选择时间" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </template>
      </el-table-column>
      <el-table-column label="计划结束" prop="planEndTime" width="180">
        <template #default="scope">
          <el-date-picker v-model="scope.row.planEndTime" type="datetime" placeholder="选择时间" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : scope.row.status === 'PROCESSING' ? 'primary' : 'info'" size="small">
            {{ RESOURCE_STATUS_LABEL[scope.row.status as keyof typeof RESOURCE_STATUS_LABEL] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" width="150">
        <template #default="scope">
          <el-input v-model="scope.row.remark" placeholder="请输入备注" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="removeRow(scope.$index)" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts" name="WorkOrderResource">
import { ref, onMounted } from 'vue';
import { getWorkOrderResourceByWoId, saveWorkOrderResourceBatch, delWorkOrderResource, RESOURCE_TYPE_LABEL, RESOURCE_STATUS_LABEL } from '@/api/erp/prd/work-order-resource';
import { listWork } from '@/api/erp/prd/work';
import { WorkOrderResourceForm } from '@/api/erp/prd/work-order-resource/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const selectedWoId = ref<number>();
const woOptions = ref<any[]>([]);
const resourceList = ref<WorkOrderResourceForm[]>([]);
const loading = ref(false);

const searchWo = async (query: string) => {
  try {
    const res = await listWork({ woNo: query || undefined, pageNum: 1, pageSize: 20 });
    woOptions.value = res.rows || [];
  } catch { woOptions.value = []; }
};

const loadResources = async () => {
  if (!selectedWoId.value) {
    resourceList.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await getWorkOrderResourceByWoId(selectedWoId.value);
    resourceList.value = (res.data || []).map((item: any) => ({ ...item }));
  } catch {
    resourceList.value = [];
  } finally {
    loading.value = false;
  }
};

const handleAddRow = () => {
  resourceList.value.push({
    woId: selectedWoId.value,
    resourceType: undefined,
    resourceCode: undefined,
    resourceName: undefined,
    planStartTime: undefined,
    planEndTime: undefined,
    status: 'PENDING',
    remark: undefined
  });
};

const removeRow = (index: number) => {
  const row = resourceList.value[index];
  if (row.id) {
    proxy?.$modal.confirm('是否确认删除该资源？').then(async () => {
      await delWorkOrderResource(row.id!);
      resourceList.value.splice(index, 1);
      proxy?.$modal.msgSuccess('删除成功');
    });
  } else {
    resourceList.value.splice(index, 1);
  }
};

const handleSave = async () => {
  if (!selectedWoId.value) return;
  for (const row of resourceList.value) {
    row.woId = selectedWoId.value;
  }
  await saveWorkOrderResourceBatch(selectedWoId.value, resourceList.value);
  proxy?.$modal.msgSuccess('保存成功');
  loadResources();
};

const handleExport = () => {
  proxy?.download('erp/prd/workOrderResource/export', { woId: selectedWoId.value }, `工单资源分配_${new Date().getTime()}.xlsx`);
};

onMounted(() => {
  searchWo('');
});
</script>
