<template>
  <el-dialog
    :model-value="modelValue"
    title="选择私有打印图片"
    width="720px"
    append-to-body
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="asset-search">
      <el-input v-model="search" clearable placeholder="搜索图片名称或编码" /><el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <div v-loading="loading" class="asset-grid">
      <button
        v-for="asset in visible"
        :key="asset.id"
        class="asset-choice"
        type="button"
        :disabled="!images[asset.publishedVersion!]"
        @click="choose(asset)"
      >
        <img
          v-if="images[asset.publishedVersion!]"
          :src="images[asset.publishedVersion!]"
          alt="私有图片预览"
          @load="imageSize(asset.publishedVersion!, $event)"
        />
        <span v-else class="asset-placeholder">{{ failures.has(asset.publishedVersion!) ? '预览不可用，请刷新重试' : '读取中…' }}</span>
        <strong>{{ asset.title }}</strong
        ><small>{{ dimensions[asset.publishedVersion!] || 'PNG / JPEG' }}</small>
      </button>
    </div>
    <el-empty v-if="!loading && !visible.length" description="没有可选择的已发布图片" />
    <p class="report-secondary">仅显示当前可访问的已发布图片，每次展示前 24 项；可搜索缩小范围。</p>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue';
import { listAssets, assetImage } from '@/api/report/assets';
import type { ReportResource } from '@/api/report/query';
import { RequestGate } from '@/views/report/model';
/** 图库开关由打印控件管理。 */
const props = defineProps<{ modelValue: boolean }>();
/** 仅提交不可变图片版本及显示名称。 */
const emit = defineEmits<{ 'update:modelValue': [boolean]; select: [{ versionId: string; title: string }] }>();
/** 当前用户可见图片目录。 */
const assets = ref<ReportResource[]>([]);
/** 名称与编码搜索。 */
const search = ref('');
/** 请求状态与可重试提示。 */
const loading = ref(false),
  error = ref('');
/** 仅本弹窗生命周期持有的私有Blob地址。 */
const images = ref<Record<string, string>>({}),
  dimensions = ref<Record<string, string>>({});
/** 当前读取失败的版本。 */
const failures = ref(new Set<string>());
/** 列表与缩略图使用独立请求围栏。 */
const listGate = new RequestGate(),
  imageGate = new RequestGate();
/** 搜索限定预览数量，避免整目录图片同时物化。 */
const visible = computed(() =>
  assets.value
    .filter(
      (asset) =>
        asset.title.toLowerCase().includes(search.value.trim().toLowerCase()) || asset.code.toLowerCase().includes(search.value.trim().toLowerCase())
    )
    .slice(0, 24)
);
/** 撤销窗口拥有的全部图片地址。 */
function release() {
  imageGate.invalidate();
  Object.values(images.value).forEach((url) => URL.revokeObjectURL(url));
  images.value = {};
  dimensions.value = {};
  failures.value = new Set();
}
/** 读取仍有权限且上线的图片，拒绝迟到目录响应。 */
async function load() {
  const ticket = listGate.begin();
  loading.value = true;
  error.value = '';
  release();
  try {
    const response = await listAssets();
    if (listGate.current(ticket) && props.modelValue)
      assets.value = response.data.filter((asset) => asset.status === 'PUBLISHED' && asset.publishedVersion);
  } catch {
    if (listGate.current(ticket)) error.value = '图片目录读取失败，请确认访问权限后刷新。';
  } finally {
    if (listGate.current(ticket)) loading.value = false;
  }
}
/** 分组读取缩略图，晚响应不创建可遗留的私有地址。 */
async function thumbnails() {
  release();
  const ticket = imageGate.begin();
  const pending = [...visible.value];
  for (let index = 0; index < pending.length; index += 4) {
    if (!imageGate.current(ticket) || !props.modelValue) return;
    await Promise.all(
      pending.slice(index, index + 4).map(async (asset) => {
        const id = asset.publishedVersion!;
        try {
          const blob = await assetImage(id);
          if (!blob.type.startsWith('image/')) throw Error('图片不可用');
          if (imageGate.current(ticket) && props.modelValue) images.value[id] = URL.createObjectURL(blob);
        } catch {
          if (imageGate.current(ticket)) failures.value.add(id);
        }
      })
    );
  }
}
/** 图片尺寸由已归一化私有内容读取，不使用任意外部链接。 */
function imageSize(id: string, event: Event) {
  const image = event.target as HTMLImageElement;
  dimensions.value[id] = image.naturalWidth + ' × ' + image.naturalHeight + ' px';
}
/** 选中当前预览可用的固定版本。 */
function choose(asset: ReportResource) {
  if (!asset.publishedVersion || !images.value[asset.publishedVersion]) return;
  emit('select', { versionId: asset.publishedVersion, title: asset.title });
  emit('update:modelValue', false);
}
watch(visible, () => {
  if (props.modelValue) void thumbnails();
});
watch(
  () => props.modelValue,
  (value) => {
    if (value) void load();
    else {
      listGate.invalidate();
      loading.value = false;
      release();
      assets.value = [];
    }
  },
  { immediate: true }
);
onUnmounted(() => {
  listGate.invalidate();
  release();
});
</script>
<style scoped>
.asset-search {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
  max-height: 440px;
  overflow: auto;
}
.asset-choice {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
}
.asset-choice img,
.asset-placeholder {
  height: 100px;
  max-width: 100%;
  object-fit: contain;
}
.asset-choice small {
  color: var(--el-text-color-secondary);
}
.asset-choice:disabled {
  cursor: default;
}
</style>
