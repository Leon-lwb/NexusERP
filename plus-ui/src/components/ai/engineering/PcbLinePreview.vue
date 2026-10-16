<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <el-empty v-if="!artifacts.length" description="尚无线路预览 PNG，已生成的其他产物仍可查看" />
    <template v-else>
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <el-select v-model="selectedId" placeholder="选择线路预览" style="width: 260px">
          <el-option v-for="item in artifacts" :key="item.artifactId" :value="item.artifactId" :label="item.fileName || item.relativePath" />
        </el-select>
        <el-button :disabled="!url" @click="zoom = Math.max(0.25, zoom - 0.25)">缩小</el-button>
        <el-button :disabled="!url" @click="zoom = Math.min(4, zoom + 0.25)">放大</el-button>
        <el-button :disabled="!url" @click="zoom = 1">适应窗口</el-button>
      </div>
      <div class="line-preview">
        <img v-if="url" :src="url" alt="PCB 线路预览" :style="{ width: `${zoom * 100}%` }" @error="error = 'PNG 图片解码失败，请下载原始文件核对'" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts" name="PcbLinePreview">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { EngineeringArtifact } from '@/api/ai/engineering';
import { getEngineeringArtifactContent } from '@/api/ai/engineering';
import { ArtifactBlobSlot, readArtifact, validatePng } from '@/views/ai/engineering/artifact-model';
import { engineeringError } from '@/views/ai/engineering/model';

const props = defineProps<{ artifacts: EngineeringArtifact[] }>();
const selectedId = ref('');
const url = ref('');
const zoom = ref(1);
const loading = ref(false);
const error = ref('');
const selected = computed(() => props.artifacts.find((item) => item.artifactId === selectedId.value));
const slot = new ArtifactBlobSlot();
let version = 0;
watch(
  () => props.artifacts.map((item) => item.artifactId).join(','),
  () => {
    if (!props.artifacts.some((item) => item.artifactId === selectedId.value)) selectedId.value = props.artifacts[0]?.artifactId || '';
  },
  { immediate: true }
);
/** 图片切换先释放旧资源，只提交当前选择的授权响应。 */
async function loadPreview() {
  const request = ++version;
  slot.dispose();
  url.value = '';
  zoom.value = 1;
  error.value = '';
  loading.value = !!selected.value;
  if (!selected.value) return;
  const artifact = selected.value;
  try {
    const next = await slot.load(async (signal) => {
      const blob = await readArtifact(artifact, getEngineeringArtifactContent, signal);
      await validatePng(blob);
      return blob;
    });
    if (request === version && next) url.value = next;
  } catch (cause) {
    if (request === version) error.value = engineeringError(cause).message;
  } finally {
    if (request === version) loading.value = false;
  }
}
watch(selected, loadPreview, { immediate: true });
onBeforeUnmount(() => {
  version++;
  slot.dispose();
});
</script>

<style scoped lang="scss">
.line-preview {
  min-height: 280px;
  max-height: 65vh;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  img {
    display: block;
    height: auto;
    margin: auto;
  }
}
</style>
