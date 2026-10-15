<template>
  <el-tag :type="presentation.type" effect="light" round size="small" class="report-status">{{ presentation.label }}</el-tag>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { statusPresentation } from '@/views/report/presentation';
/** 统一资源及任务的中文状态；不修改服务返回的真实状态。 */
const props = defineProps<{
  /** 服务器返回的资源或任务状态。 */
  status?: string | null;
}>();
/** 兼容已知和后续新增状态的展示信息。 */
const presentation = computed(() => statusPresentation(props.status));
</script>
