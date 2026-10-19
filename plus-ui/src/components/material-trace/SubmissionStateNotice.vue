<template>
  <el-alert v-if="snapshot" :title="notice.message" :type="notice.type" :closable="false" show-icon class="mb-2">
    <template #default>
      <p>原提交命令：{{ notice.commandId }}；原过账组：{{ notice.postingGroupIds.join('、') }}</p>
      <p>此状态来自该次提交的快照凭据；刷新详情可读取新的跨模块确认状态。</p>
    </template>
  </el-alert>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { SubmissionMetadata } from '@/api/erp/wm/identity/types';
import { submissionNotice } from './model';
const props = defineProps<{ snapshot?: SubmissionMetadata }>();
const notice = computed(() => submissionNotice(props.snapshot));
</script>
