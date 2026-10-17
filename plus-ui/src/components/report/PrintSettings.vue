<template>
  <div class="print-settings">
    <el-alert v-if="!compact" title="固定媒体可在纸张画布选择、拖动，并在属性中编辑毫米尺寸与业务绑定。" type="info" :closable="false" class="mb-3" />
    <el-button v-if="!compact && !modelValue.print" :disabled="disabled || modelValue.kind !== 'DOCUMENT'" @click="enable">启用第二版打印</el-button>
    <el-form v-if="!compact && modelValue.print" :disabled="disabled" label-position="top">
      <el-form-item label="分组字段"
        ><el-select
          :model-value="modelValue.print.groupBy"
          multiple
          :multiple-limit="3"
          aria-label="打印分组字段"
          @change="patch({ groupBy: $event })"
          ><el-option v-for="field in fields" :key="field.name" :label="field.label" :value="field.name" /></el-select
      ></el-form-item>
      <el-form-item label="重复页脚"
        ><el-switch :model-value="modelValue.print.repeatFooter" @change="patch({ repeatFooter: !!$event })"
      /></el-form-item>
    </el-form>
    <div class="media-actions" role="group" aria-label="添加固定媒体">
      <el-button :disabled="disabled || mediaLimit || !allowedFields.length" size="small" @click="add('QR')">添加 QR</el-button>
      <el-button :disabled="disabled || mediaLimit || !allowedFields.length" size="small" @click="add('CODE128')">添加 Code128</el-button>
      <el-button v-hasPermi="['report:asset:list']" :disabled="disabled || mediaLimit" size="small" @click="openPicker">添加私有图片</el-button>
    </div>
    <template v-if="!compact">
      <p v-if="!allowedFields.length" class="report-secondary">当前契约没有可用于条码的批准编码字段。</p>
      <div class="media-list">
        <el-button
          v-for="(media, index) in modelValue.print?.media || []"
          :key="index"
          :type="selection?.type === 'media' && selection.index === index ? 'primary' : 'default'"
          plain
          :aria-label="'编辑' + mediaLabel(media.kind) + ' ' + (index + 1)"
          @click="emit('select', { type: 'media', index })"
          >{{ mediaLabel(media.kind) }} {{ index + 1 }}</el-button
        >
      </div>
      <SelectionInspector
        v-if="selection?.type === 'media'"
        :model-value="modelValue"
        :selection="selection"
        :fields="fields"
        :disabled="disabled"
        :context-key="contextKey"
        @update:model-value="emit('update:modelValue', $event)"
        @select="emit('select', $event)"
      />
    </template>
    <AssetPicker v-model="pickerOpen" :context-key="contextKey" @select="selectAsset" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { ReportDsl, PrintDefinition, DatasetField } from '@/types/report';
import { enablePrint, effectivePaper, positionMedia, barcodeFields } from '@/views/report/designer/gridCommands';
import type { EditorSelection } from './designer/editorSelection';
import AssetPicker from './designer/AssetPicker.vue';
import SelectionInspector from './designer/SelectionInspector.vue';
/** 添加入口与新版打印配置共用固定版本语义，媒体属性交给统一面板。 */
const props = defineProps<{
  modelValue: ReportDsl;
  fields: DatasetField[];
  selection?: EditorSelection;
  disabled?: boolean;
  contextKey?: string;
  compact?: boolean;
}>();
/** 每次添加只提交一个独立草稿，选区与定义分开传递。 */
const emit = defineEmits<{ 'update:modelValue': [ReportDsl]; select: [EditorSelection | undefined] }>();
/** 图库选择只能作用于打开时的编辑会话。 */
const pickerOpen = ref(false);
let pickerSession = '';
/** 当前契约批准的条码字段与媒体数量边界。 */
const allowedFields = computed(() => barcodeFields(props.fields));
const mediaLimit = computed(() => (props.modelValue.print?.media.length || 0) >= 6);
/** 显示名称不包含资产存储引用。 */
function mediaLabel(kind: string): string {
  return kind === 'IMAGE' ? '图片' : kind === 'QR' ? '二维码' : '条形码';
}
/** 捕获当前模板与身份，拒绝迟到图库回调。 */
function session(): string {
  return JSON.stringify([props.contextKey, props.modelValue.code, props.modelValue.datasetVersionId]);
}
/** 提交受控草稿并保留非法操作前的状态。 */
function apply(action: () => ReportDsl) {
  if (props.disabled) return;
  try {
    emit('update:modelValue', action());
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 显式升级草稿格式并保持原纸张方向。 */
function enable() {
  apply(() => enablePrint(props.modelValue));
}
/** 分组字段只接受当前契约白名单。 */
function patch(value: Partial<PrintDefinition>) {
  apply(() => {
    if (value.groupBy?.some((name) => !props.fields.some((field) => field.name === name))) throw Error('请选择批准的分组字段');
    const next = enablePrint(props.modelValue);
    Object.assign(next.print!, value);
    return next;
  });
}
/** 新媒体仍由画布共用命令检查边界与二维码正方形。 */
function withMedia(kind: 'IMAGE' | 'QR' | 'CODE128', assetVersionId: string | null = null): ReportDsl {
  if (mediaLimit.value) throw Error('最多配置6个媒体');
  if (kind !== 'IMAGE' && !allowedFields.value.length) throw Error('请选择批准的业务编码字段');
  const next = enablePrint(props.modelValue),
    page = effectivePaper(next);
  next.print!.media.push({
    kind,
    assetVersionId,
    field: kind === 'IMAGE' ? null : allowedFields.value[0].name,
    x: page.marginLeft,
    y: page.marginTop + 40,
    width: kind === 'CODE128' ? 180 : 72,
    height: kind === 'CODE128' ? 48 : 72,
    repeat: false
  });
  return positionMedia(next, next.print!.media.length - 1, {});
}
/** 添加成功后直接选择新媒体，不把选区写入DSL。 */
function add(kind: 'QR' | 'CODE128') {
  apply(() => {
    const next = withMedia(kind);
    emit('select', { type: 'media', index: next.print!.media.length - 1 });
    return next;
  });
}
/** 打开图库前固定当前会话。 */
function openPicker() {
  if (props.disabled || mediaLimit.value) return;
  pickerSession = session();
  pickerOpen.value = true;
}
/** 固定图片只绑定用户本次显式选择的不可变版本。 */
function selectAsset(asset: { versionId: string; title: string }) {
  if (!pickerOpen.value || props.disabled || pickerSession !== session()) return;
  apply(() => {
    const next = withMedia('IMAGE', asset.versionId);
    emit('select', { type: 'media', index: next.print!.media.length - 1 });
    return next;
  });
  pickerOpen.value = false;
}
watch(
  () => [props.contextKey, props.modelValue.code, props.modelValue.datasetVersionId, props.disabled],
  () => {
    pickerOpen.value = false;
    pickerSession = '';
  },
  { flush: 'sync' }
);
</script>
<style scoped>
.print-settings {
  min-width: 0;
}
.media-actions,
.media-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.media-list {
  margin: 14px 0;
}
.media-actions :deep(.el-button),
.media-list :deep(.el-button) {
  margin-left: 0;
}
</style>
