<template>
  <section class="selection-inspector" aria-label="选中对象属性">
    <h3>{{ heading }}</h3>
    <el-empty v-if="!current" description="选择画布中的列、固定格或媒体" :image-size="48" />
    <el-form v-else label-position="top" size="small" :disabled="disabled" @submit.prevent>
      <template v-if="current.type === 'column'">
        <el-form-item label="列标题"
          ><el-input
            v-model="inputs.label"
            aria-label="列标题"
            @blur="commitColumn('label')"
            @keydown.enter="commitOnEnter($event, () => commitColumn('label'))"
        /></el-form-item>
        <el-form-item label="宽度（mm）"
          ><el-input-number
            v-model="inputs.width"
            aria-label="列宽度（mm）"
            :min="0"
            :precision="2"
            @change="commitDimension('width')"
            @blur="commitDimension('width')"
            @keydown.enter="commitOnEnter($event, () => commitDimension('width'))"
        /></el-form-item>
        <el-form-item label="小数位"
          ><el-input-number
            v-model="inputs.scale"
            aria-label="小数位"
            :min="0"
            :max="12"
            @change="commitColumn('scale')"
            @blur="commitColumn('scale')"
            @keydown.enter="commitOnEnter($event, () => commitColumn('scale'))"
        /></el-form-item>
        <el-form-item label="计算表达式"
          ><el-input
            v-model="inputs.expression"
            aria-label="计算表达式"
            type="textarea"
            :rows="3"
            placeholder="例如 ROUND(quantity * unitPrice, 2)"
            @blur="commitColumn('expression')"
            @keydown.ctrl.enter="commitOnEnter($event, () => commitColumn('expression'))"
        /></el-form-item>
        <p class="inspector-help">支持十进制运算、IF、ROUND、COALESCE、SUM 和 COUNT。</p>
        <el-button type="danger" link :disabled="modelValue.columns.length === 1" @click="removeSelected">移除列</el-button>
      </template>
      <template v-if="current.type === 'cell'">
        <el-form-item label="固定行高度（mm）"
          ><el-input-number
            v-model="inputs.rowHeight"
            aria-label="固定行高度（mm）"
            :min="0"
            :precision="2"
            @change="commitDimension('rowHeight')"
            @blur="commitDimension('rowHeight')"
            @keydown.enter="commitOnEnter($event, () => commitDimension('rowHeight'))"
        /></el-form-item>
        <p class="inspector-help">当前跨 {{ current.cell.colSpan }} 列、{{ current.cell.rowSpan }} 行。</p>
        <el-form-item label="文本与绑定"
          ><el-input
            v-model="inputs.text"
            aria-label="固定格文本"
            type="textarea"
            :rows="3"
            placeholder="可输入单号绑定和页码占位符"
            @blur="commitCellText"
            @keydown.ctrl.enter="commitOnEnter($event, commitCellText)"
        /></el-form-item>
        <el-form-item label="字段绑定"
          ><el-select :model-value="current.cell.field" aria-label="固定格字段绑定" clearable @change="setField"
            ><el-option v-for="field in fields" :key="field.name" :label="field.label" :value="field.name" /></el-select
        ></el-form-item>
      </template>
      <p v-if="current.type === 'range'" class="inspector-help">
        同区矩形：{{ current.range.bottom - current.range.top + 1 }} 行 ×
        {{ current.range.right - current.range.left + 1 }} 列。样式应用于整个有效选区。
      </p>
      <template v-if="current.type === 'media'">
        <template v-if="current.media.kind === 'IMAGE'">
          <p v-if="assetState.status === 'loading'" role="status">正在读取固定图片属性…</p>
          <div v-else-if="assetState.status === 'ready'" class="asset-metadata" role="status">
            <strong>{{ assetState.metadata!.name }}</strong
            ><span>{{ assetState.metadata!.width }} × {{ assetState.metadata!.height }} px</span>
          </div>
          <el-alert v-else title="固定图片不可访问或已下线；原版本已保留，请重新选择。" type="warning" :closable="false" />
          <p class="asset-version">固定版本：{{ current.media.assetVersionId || '未绑定' }}</p>
          <el-button v-hasPermi="['report:asset:list']" aria-label="替换图片资产" @click="openPicker">更换图片</el-button>
          <el-button link :disabled="assetState.status === 'loading'" @click="loadMetadata">刷新图片属性</el-button>
        </template>
        <el-form-item v-else label="批准业务编码"
          ><el-select :model-value="current.media.field" aria-label="媒体绑定字段" placeholder="选择批准业务编码" @change="setField"
            ><el-option v-for="field in allowedBarcodeFields" :key="field.name" :label="field.label" :value="field.name" /></el-select
        ></el-form-item>
        <div class="media-dimensions">
          <el-form-item v-for="dimension in mediaDimensions" :key="dimension.key" :label="dimension.label + '（mm）'"
            ><el-input-number
              v-model="inputs[dimension.key]"
              :aria-label="dimension.label + '（mm）'"
              :min="0"
              :precision="2"
              :step="0.5"
              @change="commitDimension(dimension.key)"
              @blur="commitDimension(dimension.key)"
              @keydown.enter="commitOnEnter($event, () => commitDimension(dimension.key))"
          /></el-form-item>
        </div>
        <p v-if="current.media.kind === 'QR'" class="inspector-help">二维码宽高保持相等，并限制在可打印区域内。</p>
        <el-checkbox :model-value="current.media.repeat" @change="setRepeat(!!$event)">每页重复</el-checkbox>
        <el-button link type="danger" aria-label="删除选中媒体" @click="removeSelected">删除媒体</el-button>
      </template>
      <template v-if="selectedStyle">
        <el-divider />
        <el-form-item label="字号"
          ><el-input-number
            v-model="inputs.fontSize"
            aria-label="字号"
            :min="6"
            :max="32"
            @change="commitStyle({ fontSize: inputs.fontSize })"
            @blur="commitStyle({ fontSize: inputs.fontSize })"
            @keydown.enter="commitOnEnter($event, () => commitStyle({ fontSize: inputs.fontSize }))"
        /></el-form-item>
        <el-form-item label="对齐"
          ><el-radio-group :model-value="selectedStyle.align" aria-label="对象对齐" @change="commitStyle({ align: $event as ReportStyle['align'] })"
            ><el-radio-button value="LEFT">左</el-radio-button><el-radio-button value="CENTER">中</el-radio-button
            ><el-radio-button value="RIGHT">右</el-radio-button></el-radio-group
          ></el-form-item
        >
        <el-checkbox :model-value="selectedStyle.bold" @change="commitStyle({ bold: !!$event })">加粗</el-checkbox>
        <el-form-item label="文字颜色"
          ><el-color-picker :model-value="selectedStyle.color" aria-label="对象文字颜色" @change="(value) => value && commitStyle({ color: value })"
        /></el-form-item>
        <el-form-item label="背景颜色"
          ><el-color-picker
            :model-value="selectedStyle.background"
            aria-label="对象背景颜色"
            @change="(value) => value && commitStyle({ background: value })"
        /></el-form-item>
      </template>
    </el-form>
    <AssetPicker v-model="pickerOpen" :context-key="contextKey" @select="selectAsset" />
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { assetMetadata, type ReportAssetMetadata } from '@/api/report/assets';
import type { DatasetField, ReportDsl, ReportStyle } from '@/types/report';
import { removeColumn } from '@/views/report/model';
import {
  barcodeFields,
  bindMediaField,
  mmToPt,
  ptToMm,
  positionMedia,
  resizeBandRow,
  resizeColumn,
  styleCells
} from '@/views/report/designer/gridCommands';
import { resolveEditorSelection, type EditorSelection } from './editorSelection';
import AssetPicker from './AssetPicker.vue';

/** 属性修改只提交独立草稿，候选输入与私有元数据留在编辑会话。 */
const props = defineProps<{ modelValue: ReportDsl; selection?: EditorSelection; fields: DatasetField[]; disabled?: boolean; contextKey?: string }>();
/** 每次确认输入至多产生一个历史状态，删除后显式清空父级选区。 */
const emit = defineEmits<{ 'update:modelValue': [ReportDsl]; select: [EditorSelection | undefined] }>();
/** 对象解析始终基于父级当前草稿。 */
const current = computed(() => resolveEditorSelection(props.modelValue, props.selection));
/** 属性区标题具有稳定可访问文案。 */
const heading = computed(() =>
  current.value?.type === 'media'
    ? current.value.media.kind === 'IMAGE'
      ? '图片属性'
      : current.value.media.kind === 'QR'
        ? '二维码属性'
        : '条形码属性'
    : current.value?.type === 'column'
      ? '列属性'
      : current.value?.type === 'range'
        ? '选区属性'
        : '属性'
);
/** 批量样式只读取有效固定区左上格。 */
const selectedStyle = computed(() => {
  const selected = current.value;
  if (selected?.type === 'column') return selected.column.style;
  if (selected?.type === 'cell') return selected.cell.style;
  if (selected?.type === 'range')
    return props.modelValue[selected.range.band][selected.range.top].cells.find((cell) => cell.column === selected.range.left)?.style;
  return undefined;
});
/** 条码候选由批准的数据集字段推导。 */
const allowedBarcodeFields = computed(() => barcodeFields(props.fields));
/** 布局属性统一以毫米编辑，保留内部点数精度。 */
const mediaDimensions = [
  { key: 'x', label: '横坐标' },
  { key: 'y', label: '纵坐标' },
  { key: 'width', label: '宽度' },
  { key: 'height', label: '高度' }
] as const;
/** 普通键入保留候选，数字步进或Enter、失焦确认后才修改DSL。 */
const inputs = ref({ label: '', expression: '', text: '', scale: 0, width: 0, height: 0, x: 0, y: 0, rowHeight: 0, fontSize: 10 });
/** 未编辑的显示数值不会因舍入或重复失焦产生历史。 */
let inputBaseline = JSON.stringify(inputs.value),
  lastSubmitted = '';
/** 当前图库会话与固定版本元数据。 */
const pickerOpen = ref(false);
const assetState = ref<{ status: 'idle' | 'loading' | 'ready' | 'unavailable'; metadata?: ReportAssetMetadata }>({ status: 'idle' });
/** 迟到请求和旧图库选择不能跨编辑会话生效。 */
let metadataGeneration = 0,
  pickerSession = '';

/** 创建不共享引用的下一份草稿。 */
function clone(): ReportDsl {
  return JSON.parse(JSON.stringify(props.modelValue));
}
/** 记录当前会话与媒体位置，防止替换到切换后的对象。 */
function session(): string {
  return JSON.stringify([props.contextKey, props.modelValue.code, props.modelValue.datasetVersionId, props.selection]);
}
/** 数字change、Enter与紧随其后的失焦对相同结果只提交一次。 */
function apply(action: () => ReportDsl) {
  if (props.disabled || !current.value) return;
  try {
    const next = action(),
      serialized = JSON.stringify(next);
    if (serialized === JSON.stringify(props.modelValue)) {
      resetInputs();
      return;
    }
    if (serialized === lastSubmitted) return;
    lastSubmitted = serialized;
    emit('update:modelValue', next);
  } catch (error) {
    ElMessage.warning((error as Error).message);
    resetInputs();
  }
}
/** 外部草稿、对象或身份切换后丢弃未确认输入和图库会话。 */
function resetInputs() {
  const selected = current.value;
  inputs.value = {
    label: '',
    expression: '',
    text: '',
    scale: 0,
    width: 0,
    height: 0,
    x: 0,
    y: 0,
    rowHeight: 0,
    fontSize: selectedStyle.value?.fontSize || 10
  };
  if (selected?.type === 'column')
    Object.assign(inputs.value, {
      label: selected.column.label,
      expression: selected.column.expression || '',
      scale: selected.column.scale,
      width: ptToMm(selected.column.width)
    });
  if (selected?.type === 'cell')
    Object.assign(inputs.value, { text: selected.cell.text || '', rowHeight: ptToMm(props.modelValue[selected.band][selected.row].height) });
  if (selected?.type === 'media') for (const dimension of mediaDimensions) inputs.value[dimension.key] = ptToMm(selected.media[dimension.key]);
  inputBaseline = JSON.stringify(inputs.value);
  lastSubmitted = '';
  pickerOpen.value = false;
  pickerSession = '';
}
/** 组合输入的Enter交给输入法；其余Enter确认当前候选。 */
function commitOnEnter(event: Event, action: () => void) {
  const keyboardEvent = event as KeyboardEvent;
  if (keyboardEvent.isComposing || keyboardEvent.keyCode === 229) return;
  event.preventDefault();
  event.stopPropagation();
  action();
}
/** 列、固定行与媒体尺寸复用画布的守恒和边界命令。 */
function commitDimension(key: 'x' | 'y' | 'width' | 'height' | 'rowHeight') {
  const selected = current.value,
    value = inputs.value[key];
  if (value === JSON.parse(inputBaseline)[key]) return;
  apply(() => {
    if (!Number.isFinite(value)) throw Error('请输入有效尺寸');
    if (selected?.type === 'column' && key === 'width') return resizeColumn(props.modelValue, selected.index, mmToPt(value));
    if (selected?.type === 'cell' && key === 'rowHeight')
      return resizeBandRow(props.modelValue, selected.band, selected.row, mmToPt(value) - props.modelValue[selected.band][selected.row].height);
    if (selected?.type === 'media' && key !== 'rowHeight') return positionMedia(props.modelValue, selected.index, { [key]: mmToPt(value) });
    return clone();
  });
}
/** 列输入在确认时整体提交，不直接绑定持久化对象。 */
function commitColumn(key: 'label' | 'expression' | 'scale') {
  const selected = current.value;
  if (selected?.type !== 'column') return;
  apply(() => {
    const next = clone(),
      column = next.columns[selected.index];
    if (key === 'scale') {
      if (!Number.isInteger(inputs.value.scale) || inputs.value.scale < 0 || inputs.value.scale > 12) throw Error('请输入0到12的小数位');
      column.scale = inputs.value.scale;
    } else if (key === 'expression') column.expression = inputs.value.expression || null;
    else column.label = inputs.value.label;
    return next;
  });
}
/** 固定格文本总是通过当前逻辑坐标重新解析。 */
function commitCellText() {
  const selected = current.value;
  if (selected?.type !== 'cell') return;
  apply(() => {
    const next = clone();
    next[selected.band][selected.row].cells.find((cell) => cell.column === selected.cell.column)!.text = inputs.value.text;
    return next;
  });
}
/** 固定格和媒体绑定分别使用自身批准字段规则。 */
function setField(value: string | undefined) {
  const selected = current.value;
  apply(() => {
    if (selected?.type === 'media') return bindMediaField(props.modelValue, selected.index, value || '', props.fields);
    const next = clone();
    if (selected?.type === 'cell') {
      if (value && !props.fields.some((field) => field.name === value)) throw Error('请选择批准的字段');
      next[selected.band][selected.row].cells.find((cell) => cell.column === selected.cell.column)!.field = value || null;
    }
    return next;
  });
}
/** 选区样式只影响当前列或完整固定区矩形。 */
function commitStyle(style: Partial<ReportStyle>) {
  const selected = current.value;
  apply(() => {
    if ('fontSize' in style && (!Number.isFinite(style.fontSize) || style.fontSize! < 6 || style.fontSize! > 32)) throw Error('请输入6到32的字号');
    if (selected?.type === 'cell' || selected?.type === 'range') return styleCells(props.modelValue, selected.range, style);
    const next = clone();
    if (selected?.type === 'column') Object.assign(next.columns[selected.index].style, style);
    return next;
  });
}
/** 媒体重复选项不改变绑定版本。 */
function setRepeat(value: boolean) {
  const selected = current.value;
  if (selected?.type === 'media')
    apply(() => {
      const next = clone();
      next.print!.media[selected.index].repeat = value;
      return next;
    });
}
/** 删除对象成功后立即清空选区，避免数组移位选中其他媒体。 */
function removeSelected() {
  const selected = current.value;
  if (!selected || props.disabled || (selected.type === 'column' && props.modelValue.columns.length === 1)) return;
  apply(() => {
    if (selected.type === 'column') return removeColumn(props.modelValue, selected.index);
    const next = clone();
    if (selected.type === 'media') next.print!.media.splice(selected.index, 1);
    return next;
  });
  if (selected.type === 'column' || selected.type === 'media') emit('select', undefined);
}
/** 私有元数据只请求绑定版本，失败保持原版本并显示不可访问状态。 */
async function loadMetadata() {
  const generation = ++metadataGeneration,
    selected = current.value;
  assetState.value = { status: 'idle' };
  if (selected?.type !== 'media' || selected.media.kind !== 'IMAGE') return;
  const version = selected.media.assetVersionId;
  if (!version) {
    assetState.value = { status: 'unavailable' };
    return;
  }
  assetState.value = { status: 'loading' };
  try {
    const response = await assetMetadata(version);
    if (generation !== metadataGeneration) return;
    if (
      response.data.versionId !== version ||
      !response.data.name ||
      !Number.isFinite(response.data.width) ||
      !Number.isFinite(response.data.height) ||
      response.data.width <= 0 ||
      response.data.height <= 0
    )
      throw Error('固定版本属性不可用');
    assetState.value = { status: 'ready', metadata: response.data };
  } catch {
    if (generation === metadataGeneration) assetState.value = { status: 'unavailable' };
  }
}
/** 更换图片时固定当前对象和身份会话。 */
function openPicker() {
  if (props.disabled || current.value?.type !== 'media' || current.value.media.kind !== 'IMAGE') return;
  pickerSession = session();
  pickerOpen.value = true;
}
/** 图库仅接受当前会话显式选择的不可变版本。 */
function selectAsset(asset: { versionId: string; title: string }) {
  const selected = current.value;
  if (!pickerOpen.value || pickerSession !== session() || selected?.type !== 'media' || selected.media.kind !== 'IMAGE') return;
  apply(() => {
    const next = clone();
    next.print!.media[selected.index].assetVersionId = asset.versionId;
    return next;
  });
  pickerOpen.value = false;
}
watch(() => [props.modelValue, props.selection, props.contextKey, props.disabled], resetInputs, { deep: true, immediate: true, flush: 'sync' });
watch(
  () =>
    JSON.stringify([
      props.contextKey,
      props.modelValue.code,
      props.modelValue.datasetVersionId,
      current.value?.type === 'media' && current.value.media.kind === 'IMAGE' ? current.value.media.assetVersionId : null
    ]),
  loadMetadata,
  { immediate: true, flush: 'sync' }
);
onUnmounted(() => {
  metadataGeneration++;
});
</script>
<style scoped>
.selection-inspector {
  min-width: 0;
}
.selection-inspector h3 {
  margin: 0 0 14px;
  font-size: 15px;
}
.selection-inspector :deep(.el-select),
.selection-inspector :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}
.inspector-help,
.asset-version {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.asset-metadata {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-wrap: anywhere;
}
.media-dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 10px;
  margin-top: 12px;
}
</style>
