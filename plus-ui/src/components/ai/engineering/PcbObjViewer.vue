<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <el-empty v-if="!models.length" description="OBJ 模型尚未上传，可查看线路预览及其他产物" />
    <template v-else>
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <el-select v-model="selectedId" placeholder="选择模型" style="width: 260px">
          <el-option v-for="item in models" :key="item.artifactId" :value="item.artifactId" :label="item.fileName || item.relativePath" />
        </el-select>
        <el-button :disabled="!ready" @click="viewer?.fit()">适应窗口</el-button>
        <el-button :disabled="!ready" @click="viewer?.reset()">重置视角</el-button>
        <el-button v-if="error" @click="loadModel">重新加载</el-button>
        <el-tag v-if="ready && !hasMaterials" type="warning">{{ materialMissing ? '材质缺失，使用统一材质' : '无材质，使用统一材质' }}</el-tag>
      </div>
      <div ref="container" class="obj-viewport" />
      <p class="text-sm text-gray-500">拖动旋转，滚轮缩放，右键拖动平移。模型仅供草稿查看，不作为工程尺寸认证。</p>
    </template>
  </div>
</template>

<script setup lang="ts" name="PcbObjViewer">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { getEngineeringArtifactContent, type EngineeringManifest } from '@/api/ai/engineering';
import { artifactsOfType, readArtifact } from '@/views/ai/engineering/artifact-model';
import { engineeringError } from '@/views/ai/engineering/model';
import { EngineeringModelResources } from './model-resources';
import { assertModelTextBudget, readModelText } from './model-text';
import { createEngineeringViewer, disposeEngineeringObject, parseEngineeringObj } from './obj-viewer';
const props = defineProps<{ manifest: EngineeringManifest }>();
const container = ref<HTMLElement>();
const selectedId = ref('');
const loading = ref(false);
const error = ref('');
const ready = ref(false);
const hasMaterials = ref(false);
const materialMissing = ref(false);
const models = computed(() => artifactsOfType(props.manifest, 'PCB_MODEL_OBJ'));
let version = 0;
let controller: AbortController | undefined;
let resources: EngineeringModelResources | undefined;
let viewer: ReturnType<typeof createEngineeringViewer> | undefined;
let parsed: ReturnType<typeof parseEngineeringObj> | undefined;
/** 销毁顺序覆盖在途内容读取、纹理、几何、控件、观察器、帧循环及 WebGL。 */
function release() {
  version++;
  controller?.abort();
  if (viewer) viewer.dispose();
  else if (parsed) disposeEngineeringObject(parsed.object);
  viewer = undefined;
  parsed = undefined;
  resources?.dispose();
  resources = undefined;
  ready.value = false;
  loading.value = false;
}
/** 仅解析当前组清单中的模型、材质和纹理，任何引用失败均停止加载。 */
async function loadModel() {
  release();
  const request = version;
  error.value = '';
  hasMaterials.value = false;
  materialMissing.value = false;
  const artifact = models.value.find((item) => item.artifactId === selectedId.value);
  if (!artifact || document.hidden) return;
  const current = new AbortController();
  controller = current;
  loading.value = true;
  try {
    const pool = new EngineeringModelResources(props.manifest, artifact, (item, signal) => readArtifact(item, getEngineeringArtifactContent, signal));
    resources = pool;
    assertModelTextBudget(artifact.size, 'OBJ');
    const objText = await readModelText(await readArtifact(artifact, getEngineeringArtifactContent, current.signal), 'OBJ', current.signal);
    current.signal.throwIfAborted();
    const materialTexts: Record<string, string> = {};
    for (const material of pool.materialArtifacts(objText)) {
      assertModelTextBudget(material.size, 'MTL');
      materialTexts[material.relativePath] = await readModelText(
        await readArtifact(material, getEngineeringArtifactContent, current.signal),
        'MTL',
        current.signal
      );
    }
    const mtlText = await pool.prepare(objText, Object.keys(materialTexts).length ? materialTexts : undefined);
    current.signal.throwIfAborted();
    const result = parseEngineeringObj(objText, mtlText, pool.urlModifier);
    parsed = result;
    await result.ready;
    current.signal.throwIfAborted();
    await nextTick();
    if (request !== version || !container.value) return;
    try {
      viewer = createEngineeringViewer(container.value, result, pool);
    } catch {
      throw new Error('浏览器无法启动 WebGL 三维预览，请下载 OBJ 后在本地查看');
    }
    hasMaterials.value = result.hasMaterials;
    materialMissing.value = pool.materialMissing;
    ready.value = true;
  } catch (cause) {
    if (request === version && !current.signal.aborted) {
      const message = engineeringError(cause).message;
      release();
      error.value = message;
    }
  } finally {
    if (request === version) loading.value = false;
  }
}
watch(
  () =>
    [props.manifest.taskId, ...props.manifest.artifacts.map((item) => item.artifactId), JSON.stringify(props.manifest.modelGroups || [])].join(':'),
  () => {
    if (!models.value.some((item) => item.artifactId === selectedId.value)) selectedId.value = models.value[0]?.artifactId || '';
    else if (container.value) void loadModel();
  },
  { immediate: true, flush: 'post' }
);
// 新产物到达时先让条件模板创建 viewport/ref，再开始加载；清空选择同样释放旧资源。
watch(selectedId, () => void loadModel(), { flush: 'post' });
/** 浏览器隐藏时释放 GPU，恢复可见后重新获取当前任务。 */
function visibility() {
  if (document.hidden) release();
  else void loadModel();
}
onMounted(() => {
  document.addEventListener('visibilitychange', visibility);
  void loadModel();
});
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', visibility);
  release();
});
</script>

<style scoped>
.obj-viewport {
  width: 100%;
  height: 480px;
  min-height: 280px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}
</style>
