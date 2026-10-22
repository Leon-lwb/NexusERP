<template>
  <div v-if="enabled && loading" class="ai-request-cancel">
    <el-button :disabled="!requestId || pending" @click="cancel">{{ pending ? '正在取消' : requestId ? '取消生成' : '等待请求回执' }}</el-button>
    <span v-if="pending" role="status" aria-live="polite">{{ unavailable ? '暂时无法确认停止状态，请稍后查看' : '等待服务端确认停止' }}</span>
  </div>
</template>
<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { cancelAiChatRequest, getAiChatCapabilities, getAiChatRequest } from '@/api/ai/chat';
import type { AiChatRequestReceipt } from '@/api/ai/chat';
const props = defineProps<{ requestId: string | null; loading: boolean; state?: string }>();
const emit = defineEmits<{ state: [value: AiChatRequestReceipt] }>();
const enabled = ref(false);
const pending = ref(false);
const unavailable = ref(false);
let epoch = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
let failures = 0;
function reset() {
  epoch++;
  if (timer) clearTimeout(timer);
  timer = undefined;
  pending.value = false;
  unavailable.value = false;
  failures = 0;
}
function observe(receipt: AiChatRequestReceipt, id: string, version: number) {
  if (version !== epoch || props.requestId !== id || !props.loading) return;
  emit('state', receipt);
  if (['SUCCEEDED', 'FAILED', 'CANCELLED', 'RECOVERY_REQUIRED'].includes(receipt.status)) {
    pending.value = false;
    return;
  }
  timer = setTimeout(() => void poll(id, version), 1000);
}
async function poll(id: string, version: number) {
  if (version !== epoch || props.requestId !== id || !props.loading) return;
  try {
    const receipt = await getAiChatRequest(id);
    failures = 0;
    observe(receipt, id, version);
  } catch {
    if (version !== epoch) return;
    unavailable.value = true;
    if (++failures < 5) timer = setTimeout(() => void poll(id, version), 5000);
  }
}
async function cancel() {
  const id = props.requestId;
  if (!id || pending.value) return;
  pending.value = true;
  unavailable.value = false;
  const version = ++epoch;
  try {
    observe(await cancelAiChatRequest(id), id, version);
  } catch {
    if (version === epoch) {
      pending.value = false;
      ElMessage.error('取消请求未确认，可稍后重试');
    }
  }
}
watch(() => props.requestId, reset);
watch(
  () => props.loading,
  (loading) => {
    if (!loading) reset();
  }
);
watch(
  () => props.state,
  (state) => {
    if (state === 'CANCEL_REQUESTED') pending.value = true;
  }
);
onMounted(() => {
  void getAiChatCapabilities()
    .then((value) => {
      enabled.value = value;
    })
    .catch(() => {
      enabled.value = false;
    });
});
onUnmounted(reset);
</script>
<style scoped>
.ai-request-cancel {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.ai-request-cancel span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
