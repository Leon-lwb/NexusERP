<template>
  <el-dropdown
    v-hasPermi="['report:runtime:preview']"
    :disabled="!recordId"
    trigger="click"
    @visible-change="(value) => value && load()"
    @command="open"
    ><el-button link type="primary">报表预览/打印</el-button
    ><template #dropdown
      ><el-dropdown-menu
        ><el-dropdown-item v-for="report in reports" :key="report.id" :command="report.id">{{ report.name }}</el-dropdown-item
        ><el-dropdown-item v-if="!reports.length" disabled>{{ loading ? '加载中' : '没有可用的已发布模板' }}</el-dropdown-item></el-dropdown-menu
      ></template
    ></el-dropdown
  >
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { businessReportTemplates } from '@/api/report';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的props状态。
 */
const props = defineProps<{
  /** 保存当前 `datasetCode` 值。 */
  datasetCode: string;
  /** 保存当前 `parameter` 值。 */
  parameter: string;
  /** 保存当前 `recordId` 值。 */
  recordId: string | number | undefined;
}>();
/**
 * 保存当前页面或测试的router状态。
 */
const router = useRouter();
/**
 * 保存当前页面或测试的reports状态。
 */
const reports = ref<
  {
    /** 保存资源标识。 */
    id: string;
    /** 保存当前 `name` 值。 */
    name: string;
  }[]
>([]);
/**
 * 保存当前页面或测试的loading状态。
 */
const loading = ref(false);
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
/** 处理当前报表的 `load` 操作。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  try {
    const response = await businessReportTemplates(props.datasetCode);
    if (gate.current(ticket)) reports.value = response.data;
  } catch {
    if (gate.current(ticket)) reports.value = [];
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 处理当前报表的 `open` 操作。 */
function open(id: string) {
  if (props.recordId == null) return;
  router.push({ path: '/report/viewer', query: { id, [props.parameter]: String(props.recordId) } });
}
watch(
  () => props.datasetCode,
  () => {
    gate.invalidate();
    reports.value = [];
  }
);
onUnmounted(() => gate.invalidate());
</script>
