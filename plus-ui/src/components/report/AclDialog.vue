<template>
  <el-dialog :model-value="open" title="资源授权" width="min(760px, calc(100vw - 32px))" @update:model-value="emit('update:open', $event)">
    <el-alert title="配置当前资源的操作权限；关联报表、数据集和业务范围会分别校验。" type="info" :closable="false" class="mb-4" />
    <el-alert v-if="failed" title="授权载入失败，请重试后再修改。" type="error" :closable="false" class="mb-4" />
    <el-button v-if="failed" class="mb-4" @click="load">重试</el-button>
    <el-form :disabled="loading || saving || !loaded">
      <el-table v-loading="loading" :data="grants" border>
        <el-table-column label="主体" width="135"
          ><template #default="scope"
            ><el-select v-model="scope.row.subjectType"
              ><el-option label="用户" value="USER" /><el-option label="角色" value="ROLE" /><el-option
                label="部门"
                value="DEPT" /></el-select></template
        ></el-table-column>
        <el-table-column label="主体编号"
          ><template #default="scope"><el-input v-model="scope.row.subjectId" placeholder="输入当前租户主体 ID" /></template
        ></el-table-column>
        <el-table-column label="操作" width="150"
          ><template #default="scope"
            ><el-select v-model="scope.row.action"
              ><el-option v-for="action in actions" :key="action.value" :label="action.label" :value="action.value" /></el-select></template
        ></el-table-column>
        <el-table-column width="70"
          ><template #default="scope"
            ><el-button
              v-hasPermi="['report:acl:manage']"
              type="danger"
              link
              :disabled="loading || saving || !loaded"
              @click="grants.splice(scope.$index, 1)"
              >移除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-button
        v-hasPermi="['report:acl:manage']"
        class="mt-4"
        :disabled="loading || saving || !loaded"
        @click="grants.push({ subjectType: 'ROLE', subjectId: '', action: 'VIEW' })"
        >添加授权</el-button
      >
    </el-form>
    <template #footer
      ><el-button @click="emit('update:open', false)">取消</el-button
      ><el-button v-hasPermi="['report:acl:manage']" type="primary" :loading="saving" :disabled="loading || !loaded" @click="save"
        >保存授权</el-button
      ></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listGrants, saveGrants } from '@/api/report';
import type { ResourceGrant } from '@/types/report';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的props状态。
 */
const props = defineProps<{
  /** 保存当前 `open` 值。 */
  open: boolean;
  /** 保存当前 `resourceType` 值。 */
  resourceType: 'REPORT' | 'DATASET' | 'QUERY' | 'DASHBOARD' | 'SUBSCRIPTION' | 'ASSET' | 'BATCH';
  /** 保存当前 `resourceId` 值。 */
  resourceId: string;
}>();
/**
 * 保存当前页面或测试的emit状态。
 */
const emit = defineEmits<{
  /** 保存当前 `'update:open'` 值。 */
  'update:open': [value: boolean];
}>();
/**
 * 保存当前页面或测试的grants状态。
 */
const grants = ref<ResourceGrant[]>([]);
/**
 * 保存当前页面或测试的loading状态。
 */
const loading = ref(false);
/**
 * 保存当前页面或测试的saving状态。
 */
const saving = ref(false);
/**
 * 保存当前页面或测试的loaded状态。
 */
const loaded = ref(false);
/**
 * 保存当前页面或测试的failed状态。
 */
const failed = ref(false);
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
/**
 * 保存当前页面或测试的actions状态。
 */
const actions = [
  { value: 'VIEW', label: '查看' },
  { value: 'EDIT', label: '编辑' },
  { value: 'PUBLISH', label: '发布' },
  { value: 'EXPORT', label: '导出' },
  { value: 'MANAGE', label: '管理授权' }
];
/** 处理当前报表的 `load` 操作。 */
async function load() {
  const ticket = gate.begin();
  grants.value = [];
  loaded.value = failed.value = loading.value = saving.value = false;
  if (!props.open || !props.resourceId) return;
  const type = props.resourceType;
  const id = props.resourceId;
  loading.value = true;
  try {
    const response = await listGrants(type, id);
    if (gate.current(ticket)) {
      grants.value = response.data;
      loaded.value = true;
    }
  } catch {
    if (gate.current(ticket)) failed.value = true;
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
watch(() => [props.open, props.resourceType, props.resourceId], load, { immediate: true, flush: 'sync' });
/** 处理当前报表的 `save` 操作。 */
async function save(): Promise<boolean> {
  if (!props.open || !props.resourceId || !loaded.value || loading.value || saving.value) return false;
  if (grants.value.some((g) => !/^[1-9]\d{0,18}$/.test(g.subjectId))) {
    ElMessage.warning('请输入有效的主体编号');
    return false;
  }
  const ticket = gate.begin();
  const type = props.resourceType;
  const id = props.resourceId;
  const sent = grants.value.map((grant) => ({ ...grant }));
  saving.value = true;
  try {
    await saveGrants(type, id, sent);
    if (!gate.current(ticket)) return false;
    ElMessage.success('授权已保存');
    emit('update:open', false);
    return true;
  } catch {
    return false;
  } finally {
    if (gate.current(ticket)) saving.value = false;
  }
}
onUnmounted(() => gate.invalidate());
</script>
