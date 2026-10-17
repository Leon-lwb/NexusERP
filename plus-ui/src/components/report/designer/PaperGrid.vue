<template>
  <section class="paper-editor" @pointermove="moveDrag" @pointerup="finishDrag" @pointercancel="cancelDrag" @keydown.esc="cancelDrag">
    <div class="grid-toolbar" @keydown.stop>
      <el-button size="small" @click="fit">适应宽度</el-button>
      <el-select v-model="zoom" size="small" style="width: 105px"
        ><el-option v-if="![0.25, 0.5, 0.75, 1, 1.25, 1.5, 2].includes(zoom)" :value="zoom" :label="Math.round(zoom * 100) + '%'" />
        <el-option v-for="scale in [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2]" :key="scale" :label="Math.round(scale * 100) + '%'" :value="scale"
      /></el-select>
      <el-button size="small" :disabled="disabled" @click="emit('add-band', 'headerRows')">添加页眉行</el-button
      ><el-button size="small" :disabled="disabled" @click="emit('add-band', 'footerRows')">添加页脚行</el-button>
      <el-button size="small" :disabled="disabled || !rectangle" @click="mergeSelection">合并选区</el-button
      ><el-button size="small" :disabled="disabled || !rectangle" @click="splitSelection">拆分选区</el-button>
      <el-button size="small" :disabled="disabled || !rectangle" @click="styleSelection({ bold: true })">加粗选区</el-button
      ><el-button size="small" :disabled="disabled || !rectangle" @click="styleSelection({ bold: false })">常规</el-button>
      <el-button
        v-for="align in ['LEFT', 'CENTER', 'RIGHT'] as const"
        :key="align"
        size="small"
        :disabled="disabled || !rectangle"
        @click="styleSelection({ align })"
        >{{ align === 'LEFT' ? '左对齐' : align === 'CENTER' ? '居中' : '右对齐' }}</el-button
      >
      <el-color-picker
        size="small"
        :model-value="selectionColor"
        :disabled="disabled || !rectangle"
        @change="(value) => value && styleSelection({ color: value })"
      />
      <el-color-picker
        size="small"
        :model-value="selectionBackground"
        :disabled="disabled || !rectangle"
        @change="(value) => value && styleSelection({ background: value })"
      />
    </div>
    <p class="grid-help">Shift + 点击选择同一区域矩形；拖动列边界、固定行底边或媒体调节柄。合并保留左上格内容，可撤销。</p>
    <div ref="viewport" class="grid-viewport">
      <div class="paper-frame" :style="{ width: paper.width * ratio * zoom + 52 + 'px', height: paper.height * ratio * zoom + 52 + 'px' }">
        <div class="ruler ruler-x" :style="{ width: paper.width * ratio + 'px', transform: 'scale(' + zoom + ')' }">
          <span v-for="tick in ticks(paper.width)" :key="tick" :style="{ left: mmToPt(tick) * ratio + 'px' }">{{ tick }}</span>
        </div>
        <div class="ruler ruler-y" :style="{ height: paper.height * ratio + 'px', transform: 'scale(' + zoom + ')' }">
          <span v-for="tick in ticks(paper.height)" :key="tick" :style="{ top: mmToPt(tick) * ratio + 'px' }">{{ tick }}</span>
        </div>
        <div class="grid-paper" :style="{ width: paper.width * ratio + 'px', height: paper.height * ratio + 'px', transform: 'scale(' + zoom + ')' }">
          <div
            class="printable-boundary"
            :style="{
              left: paper.marginLeft * ratio + 'px',
              top: paper.marginTop * ratio + 'px',
              right: paper.marginRight * ratio + 'px',
              bottom: paper.marginBottom * ratio + 'px'
            }"
          />
          <div
            class="grid-content"
            :style="{
              left: paper.marginLeft * ratio + 'px',
              top: paper.marginTop * ratio + 'px',
              width: (paper.width - paper.marginLeft - paper.marginRight) * ratio + 'px'
            }"
          >
            <div class="document-title">{{ design.title }}</div>
            <template v-for="band in bands" :key="band.key">
              <div v-if="band.key === 'footerRows'" class="detail-area">
                <div class="detail-header" :style="columnsStyle">
                  <div
                    v-for="(column, index) in design.columns"
                    :key="column.field"
                    class="detail-column"
                    :class="{ 'active-cell': selection?.type === 'column' && selection.index === index }"
                    :style="textStyle(column.style)"
                    draggable="true"
                    @dragstart="(event) => event.dataTransfer?.setData('report-column', String(index))"
                    @dragover.prevent
                    @drop="(event) => dropColumn(event, index)"
                    @click="emit('select', { type: 'column', index })"
                  >
                    {{ column.label }}
                    <span
                      v-if="index < design.columns.length - 1"
                      class="column-resizer"
                      role="separator"
                      aria-label="拖动调整相邻列宽"
                      @pointerdown="(event) => startColumnDrag(event, index)"
                    />
                  </div>
                </div>
                <div v-for="row in 3" :key="row" class="detail-row" :style="{ ...columnsStyle, height: paper.rowHeight * ratio + 'px' }">
                  <div v-for="column in design.columns" :key="column.field" :style="textStyle(column.style)">
                    {{ row === 1 ? bindingLabel(column.field) : '…' }}
                  </div>
                </div>
                <div class="growth-placeholder">可增长明细区 · 实际换行与分页请查看服务端预览</div>
              </div>
              <div v-if="design[band.key].length" class="fixed-band" :data-band="band.key" :style="bandStyle(band.key)">
                <template v-for="(row, rowIndex) in design[band.key]" :key="rowIndex">
                  <button
                    v-for="(cell, cellIndex) in row.cells"
                    :key="rowIndex + '-' + cellIndex"
                    type="button"
                    class="fixed-cell"
                    :disabled="disabled"
                    :class="{ 'active-cell': inSelection(band.key, rowIndex, cell) }"
                    :style="{
                      ...textStyle(cell.style),
                      gridColumn: cell.column + 1 + ' / span ' + cell.colSpan,
                      gridRow: rowIndex + 1 + ' / span ' + cell.rowSpan
                    }"
                    @click="(event) => selectCell(band.key, rowIndex, cellIndex, event)"
                  >
                    {{ cell.text || (cell.field ? bindingLabel(cell.field) : '空白') }}
                  </button>
                  <span
                    class="row-resizer"
                    role="separator"
                    :aria-label="'调整' + band.label + '第' + (rowIndex + 1) + '行高度'"
                    :style="{ top: rowBottom(band.key, rowIndex) * ratio - 3 + 'px' }"
                    @pointerdown="(event) => startRowDrag(event, band.key, rowIndex)"
                  />
                </template>
              </div>
              <div v-else class="empty-band">{{ band.label }} · 可添加固定行</div>
            </template>
          </div>
          <div
            v-for="(media, index) in design.print?.media || []"
            :key="index"
            class="paper-media"
            :class="{ 'active-media': selectedMedia === index }"
            :style="{
              left: media.x * ratio + 'px',
              top: media.y * ratio + 'px',
              width: media.width * ratio + 'px',
              height: media.height * ratio + 'px'
            }"
            :title="'横坐标 ' + ptToMm(media.x).toFixed(1) + ' mm；纵坐标 ' + ptToMm(media.y).toFixed(1) + ' mm'"
            @pointerdown="(event) => startMediaDrag(event, index, false)"
          >
            <img
              v-if="media.kind === 'IMAGE' && images[media.assetVersionId!]"
              :src="images[media.assetVersionId!]"
              alt="私有打印图片"
              draggable="false"
            />
            <span v-else>{{ media.kind === 'IMAGE' ? '私有图片' : media.kind + ' · ' + media.field }}</span>
            <span
              class="media-resizer"
              role="separator"
              aria-label="拖动缩放图片或条码"
              @pointerdown="(event) => startMediaDrag(event, index, true)"
            />
          </div>
        </div>
      </div>
    </div>
    <div class="row-tools">
      <template v-for="band in bands" :key="band.key"
        ><el-button
          v-for="(_, index) in design[band.key]"
          :key="band.key + index"
          link
          type="danger"
          size="small"
          :disabled="disabled"
          @click="deleteRow(band.key, index)"
          >删除{{ band.label }}第 {{ index + 1 }} 行</el-button
        ></template
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import type { CSSProperties } from 'vue';
import type { BandCell, ReportDsl, ReportStyle } from '@/types/report';
import { moveColumn } from '@/views/report/model';
import { assetImage } from '@/api/report/assets';
import {
  effectivePaper,
  mmToPt,
  ptToMm,
  resizeColumnBoundary,
  resizeBandRow,
  mergeCells,
  splitCells,
  styleCells,
  positionMedia,
  type FixedBand,
  type GridSelection
} from '@/views/report/designer/gridCommands';
/** 属性面板使用的单个固定格或明细列位置。 */
type EditorSelection = { type: 'column'; index: number } | { type: 'cell'; band: FixedBand; row: number; cell: number };
/** 纸张画布只编辑布局，不计算业务数值。 */
const props = defineProps<{ modelValue: ReportDsl; disabled?: boolean; selection?: EditorSelection }>();
/** 一次手势只提交一次草稿，辅助状态留在组件内。 */
const emit = defineEmits<{
  'update:modelValue': [ReportDsl];
  select: [EditorSelection | undefined];
  'add-band': [FixedBand];
  'field-drop': [DragEvent];
  gesture: [boolean];
}>();
/** 屏幕CSS像素与印刷点数的比例。 */
const ratio = 4 / 3;
/** 缩放与视口引用。 */
const zoom = ref(0.75),
  viewport = ref<HTMLElement>();
/** 本地拖动预览不会触发父级历史记录。 */
const candidate = ref<ReportDsl>();
/** 编辑态纸张取本地候选，否则取父级已提交草稿。 */
const design = computed(() => candidate.value || props.modelValue);
/** 与服务端一致的方向变换。 */
const paper = computed(() => effectivePaper(design.value));
/** 固定区顺序。 */
const bands: { key: FixedBand; label: string }[] = [
  { key: 'headerRows', label: '页眉' },
  { key: 'footerRows', label: '页脚' }
];
/** 矩形选区与锚点仅用于编辑。 */
const rectangle = ref<GridSelection>(),
  anchor = ref<{ band: FixedBand; row: number; column: number }>();
/** 当前媒体和私有缩略图。 */
const selectedMedia = ref(-1),
  images = ref<Record<string, string>>({});
/** 缩略图请求代数。 */
let imageGeneration = 0;
/** 布局手势持有源快照和指针捕获。 */
let drag:
  | {
      kind: 'column' | 'row' | 'media' | 'media-size';
      source: ReportDsl;
      baseline: string;
      index: number;
      band?: FixedBand;
      startX: number;
      startY: number;
      pointerId: number;
      target: HTMLElement;
    }
  | undefined;
/** 视口尺寸变化观察器。 */
let observer: ResizeObserver | undefined;
/** 明细列按真实pt宽度绘制。 */
const columnsStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: design.value.columns.map((column) => column.width * ratio + 'px').join(' ')
}));
/** 当前矩形左上格样式仅用于控件初始显示。 */
const selectionStyle = computed(() => {
  const range = rectangle.value;
  return range ? design.value[range.band]?.[range.top]?.cells.find((cell) => cell.column === range.left)?.style : undefined;
});
/** 当前选区的文字和背景颜色。 */
const selectionColor = computed(() => selectionStyle.value?.color || '#111111'),
  selectionBackground = computed(() => selectionStyle.value?.background || '#ffffff');
/** 按10毫米生成辅助标尺。 */
function ticks(size: number) {
  return Array.from({ length: Math.floor(ptToMm(size) / 10) + 1 }, (_, index) => index * 10);
}
/** 绑定占位符在脚本中生成，避免与Vue插值结束符冲突。 */
function bindingLabel(field: string) {
  return '{{' + field + '}}';
}
/** 适应视口时保留可滚动纸张边界。 */
function fit() {
  const width = viewport.value?.clientWidth;
  if (width) zoom.value = Math.max(0.15, Math.min(2, (width - 64) / (paper.value.width * ratio)));
}
/** 将固定样式映射到画布，不改变存储值。 */
function textStyle(style: ReportStyle): CSSProperties {
  return {
    fontSize: style.fontSize * ratio + 'px',
    fontWeight: style.bold ? '700' : '400',
    textAlign: style.align.toLowerCase() as 'left' | 'center' | 'right',
    color: style.color,
    backgroundColor: style.background
  };
}
/** 固定行网格与列边界共用一套尺寸。 */
function bandStyle(band: FixedBand) {
  return { ...columnsStyle.value, gridTemplateRows: design.value[band].map((row) => row.height * ratio + 'px').join(' ') };
}
/** 获取固定行底边的位置。 */
function rowBottom(band: FixedBand, row: number) {
  return design.value[band].slice(0, row + 1).reduce((height, item) => height + item.height, 0);
}
/** 选区高亮覆盖合并格。 */
function inSelection(band: FixedBand, row: number, cell: BandCell) {
  const range = rectangle.value;
  return (
    !!range &&
    range.band === band &&
    row <= range.bottom &&
    row + cell.rowSpan - 1 >= range.top &&
    cell.column <= range.right &&
    cell.column + cell.colSpan - 1 >= range.left
  );
}
/** 通过坐标读取当前锚点，合并、拆分及撤销后不保留旧单元格对象。 */
function currentAnchor() {
  const position = anchor.value;
  if (!position) return;
  const rows = design.value[position.band];
  for (let row = 0; row <= position.row && row < rows.length; row++) {
    const cell = rows[row].cells.find(
      (cell) => cell.column <= position.column && cell.column + cell.colSpan > position.column && row + cell.rowSpan > position.row
    );
    if (cell) return { row, cell };
  }
}
/** Shift选择同一区域的完整矩形，跨区域点击重新建立锚点。 */
function selectCell(band: FixedBand, row: number, index: number, event: MouseEvent) {
  if (props.disabled) return;
  const cell = design.value[band][row].cells[index];
  let start = event.shiftKey && anchor.value?.band === band ? currentAnchor() : undefined;
  if (!start) {
    anchor.value = { band, row, column: cell.column };
    start = { row, cell };
  }
  rectangle.value = {
    band,
    top: Math.min(start.row, row),
    bottom: Math.max(start.row + start.cell.rowSpan - 1, row + cell.rowSpan - 1),
    left: Math.min(start.cell.column, cell.column),
    right: Math.max(start.cell.column + start.cell.colSpan - 1, cell.column + cell.colSpan - 1)
  };
  emit('select', { type: 'cell', band, row, cell: index });
}
/** 统一提交菜单动作并保留非法操作前的草稿。 */
function apply(action: () => ReportDsl) {
  if (props.disabled || drag) return;
  try {
    emit('update:modelValue', action());
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 合并矩形并重新绑定左上格属性。 */
function mergeSelection() {
  if (rectangle.value)
    apply(() => {
      const range = rectangle.value!,
        next = mergeCells(props.modelValue, range);
      emit('select', {
        type: 'cell',
        band: range.band,
        row: range.top,
        cell: next[range.band][range.top].cells.findIndex((cell) => cell.column === range.left)
      });
      return next;
    });
}
/** 拆分完整矩形中的合并格。 */
function splitSelection() {
  if (rectangle.value) apply(() => splitCells(props.modelValue, rectangle.value!));
}
/** 为固定矩形应用批量样式。 */
function styleSelection(style: Partial<ReportStyle>) {
  if (rectangle.value) apply(() => styleCells(props.modelValue, rectangle.value!, style));
}
/** 删除固定行前拒绝截断跨行合并。 */
function deleteRow(band: FixedBand, index: number) {
  apply(() => {
    if (
      props.modelValue[band].some((row, rowIndex) =>
        row.cells.some((cell) => cell.rowSpan > 1 && rowIndex <= index && rowIndex + cell.rowSpan > index)
      )
    )
      throw Error('请先拆分涉及该行的跨行合并');
    const next = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
    next[band].splice(index, 1);
    rectangle.value = undefined;
    anchor.value = undefined;
    emit('select', undefined);
    return next;
  });
}
/** 列排序与拖入字段分别处理。 */
function dropColumn(event: DragEvent, index: number) {
  event.preventDefault();
  event.stopPropagation();
  const source = event.dataTransfer?.getData('report-column');
  if (source !== undefined && /^\d+$/.test(source)) apply(() => moveColumn(props.modelValue, Number(source), index));
  else emit('field-drop', event);
}
/** 开始手势时固定源对象和屏幕比例。 */
function beginDrag(event: PointerEvent, kind: 'column' | 'row' | 'media' | 'media-size', index: number, band?: FixedBand) {
  if (props.disabled || event.button !== 0) return;
  event.preventDefault();
  event.stopPropagation();
  cancelDrag();
  drag = {
    kind,
    index,
    band,
    source: props.modelValue,
    baseline: JSON.stringify(props.modelValue),
    startX: event.clientX,
    startY: event.clientY,
    pointerId: event.pointerId,
    target: event.currentTarget as HTMLElement
  };
  candidate.value = JSON.parse(drag.baseline);
  try {
    drag.target.setPointerCapture(event.pointerId);
  } catch {}
  emit('gesture', true);
}
/** 相邻明细列边界拖动入口。 */
function startColumnDrag(event: PointerEvent, index: number) {
  beginDrag(event, 'column', index);
}
/** 固定行高度拖动入口。 */
function startRowDrag(event: PointerEvent, band: FixedBand, row: number) {
  beginDrag(event, 'row', row, band);
}
/** 私有图片或条码移动和缩放入口。 */
function startMediaDrag(event: PointerEvent, index: number, resizing: boolean) {
  selectedMedia.value = index;
  beginDrag(event, resizing ? 'media-size' : 'media', index);
}
/** 指针移动只修改本地候选，保持一次拖动一条历史。 */
function moveDrag(event: PointerEvent) {
  if (!drag || drag.pointerId !== event.pointerId) return;
  const horizontal = (event.clientX - drag.startX) / (ratio * zoom.value),
    vertical = (event.clientY - drag.startY) / (ratio * zoom.value);
  try {
    if (drag.kind === 'column') candidate.value = resizeColumnBoundary(drag.source, drag.index, horizontal);
    else if (drag.kind === 'row') candidate.value = resizeBandRow(drag.source, drag.band!, drag.index, vertical);
    else {
      const media = drag.source.print!.media[drag.index];
      candidate.value = positionMedia(
        drag.source,
        drag.index,
        drag.kind === 'media'
          ? { x: media.x + horizontal, y: media.y + vertical }
          : { width: media.width + horizontal, height: media.height + vertical }
      );
    }
  } catch (error) {
    ElMessage.warning((error as Error).message);
    cancelDrag();
  }
}
/** 松开时提交一次，源草稿已变化则丢弃旧候选。 */
function finishDrag(event: PointerEvent) {
  if (!drag || event.pointerId !== drag.pointerId) return;
  moveDrag(event);
  if (!drag) return;
  const result = candidate.value,
    baseline = drag.baseline;
  const valid = !props.disabled && baseline === JSON.stringify(props.modelValue);
  cancelDrag();
  if (valid && result && JSON.stringify(result) !== baseline) emit('update:modelValue', result);
}
/** 取消时释放指针并丢弃未提交布局。 */
function cancelDrag() {
  if (drag) {
    try {
      drag.target.releasePointerCapture(drag.pointerId);
    } catch {}
    drag = undefined;
    emit('gesture', false);
  }
  candidate.value = undefined;
}
/** 捕获阶段取消活动手势，工具栏或外部控件焦点不会截断Escape。 */
function cancelWithEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !drag) return;
  event.preventDefault();
  cancelDrag();
}
/** 回收当前组件创建的所有私有图片地址。 */
function releaseImages() {
  imageGeneration++;
  Object.values(images.value).forEach((url) => URL.revokeObjectURL(url));
  images.value = {};
}
/** 只读取当前画布依赖的固定图片版本。 */
async function loadImages() {
  releaseImages();
  const generation = imageGeneration;
  const versions = [
    ...new Set(
      (props.modelValue.print?.media || []).filter((media) => media.kind === 'IMAGE' && media.assetVersionId).map((media) => media.assetVersionId!)
    )
  ];
  await Promise.all(
    versions.map(async (version) => {
      try {
        const blob = await assetImage(version);
        if (generation === imageGeneration && blob.type.startsWith('image/')) images.value[version] = URL.createObjectURL(blob);
      } catch {}
    })
  );
}
watch(
  () => props.modelValue,
  () => cancelDrag(),
  { deep: true }
);
watch(
  () => props.disabled,
  (value) => {
    if (value) cancelDrag();
  }
);
watch(
  () => JSON.stringify([props.modelValue.code, props.modelValue.datasetVersionId, props.modelValue.columns.map((column) => column.field)]),
  () => {
    rectangle.value = undefined;
    anchor.value = undefined;
  }
);
watch(() => JSON.stringify([props.modelValue.code, props.modelValue.print?.media.map((media) => media.assetVersionId)]), loadImages, {
  immediate: true
});
onMounted(() => {
  fit();
  if (typeof ResizeObserver !== 'undefined' && viewport.value) {
    observer = new ResizeObserver(() => fit());
    observer.observe(viewport.value);
  }
  if (typeof window !== 'undefined') {
    window.addEventListener('blur', cancelDrag);
    window.addEventListener('keydown', cancelWithEscape, true);
  }
});
onUnmounted(() => {
  cancelDrag();
  observer?.disconnect();
  releaseImages();
  if (typeof window !== 'undefined') {
    window.removeEventListener('blur', cancelDrag);
    window.removeEventListener('keydown', cancelWithEscape, true);
  }
});
</script>
<style scoped>
.paper-editor {
  min-width: 0;
}
.grid-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.grid-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.grid-help {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.grid-viewport {
  overflow: auto;
  max-height: 76vh;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
.paper-frame {
  position: relative;
  margin: 12px auto;
}
.grid-paper {
  position: absolute;
  left: 26px;
  top: 26px;
  transform-origin: top left;
  background: #fff;
  color: #111;
  box-shadow: 0 2px 10px #0002;
}
.printable-boundary {
  position: absolute;
  pointer-events: none;
  border: 1px dashed #aab8ca;
}
.grid-content {
  position: absolute;
}
.document-title {
  min-height: 36px;
  text-align: center;
  font-size: 21.33px;
  line-height: 1.2;
  font-weight: 700;
  padding: 5.33px;
}
.fixed-band {
  position: relative;
}
.fixed-cell,
.detail-column,
.detail-row > div {
  min-width: 0;
  overflow: hidden;
  padding: 5.33px;
  box-sizing: border-box;
  border: 0.5px solid #d2d8e0;
  font-family: SimHei, 'Noto Sans CJK SC', sans-serif;
  line-height: 1.2;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.fixed-cell {
  cursor: pointer;
  border-radius: 0;
}
.active-cell {
  outline: 2px solid #409eff;
  outline-offset: -2px;
}
.detail-column {
  min-height: 32px;
  position: relative;
  cursor: grab;
  font-weight: 700;
}
.column-resizer {
  position: absolute;
  top: 0;
  bottom: 0;
  right: -1px;
  width: 7px;
  z-index: 3;
  cursor: col-resize;
  touch-action: none;
}
.row-resizer {
  position: absolute;
  left: 0;
  width: 100%;
  height: 7px;
  z-index: 3;
  cursor: row-resize;
  touch-action: none;
}
.column-resizer:hover,
.row-resizer:hover {
  background: #409eff66;
}
.growth-placeholder {
  text-align: center;
  padding: 20px 4px;
  color: #7a8897;
  font-size: 12px;
  border: 1px dashed #d2d8e0;
}
.empty-band {
  font-size: 10px;
  color: #8792a2;
  border: 1px dashed #d2d8e0;
  min-height: 18px;
}
.ruler {
  position: absolute;
  color: var(--el-text-color-secondary);
  font-size: 10px;
  transform-origin: top left;
  pointer-events: none;
}
.ruler-x {
  top: 6px;
  left: 26px;
  height: 20px;
  border-bottom: 1px solid var(--el-border-color);
}
.ruler-x span {
  position: absolute;
  border-left: 1px solid var(--el-border-color);
  padding-left: 2px;
  height: 20px;
}
.ruler-y {
  left: 2px;
  top: 26px;
  width: 24px;
  border-right: 1px solid var(--el-border-color);
}
.ruler-y span {
  position: absolute;
  border-top: 1px solid var(--el-border-color);
  width: 24px;
}
.paper-media {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #409eff;
  cursor: move;
  touch-action: none;
  font-size: 12px;
  color: #31577f;
  overflow-wrap: anywhere;
  background: #eaf4ffcc;
}
.paper-media img {
  width: 100%;
  height: 100%;
  object-fit: fill;
  pointer-events: none;
}
.active-media {
  outline: 2px solid #409eff;
}
.media-resizer {
  position: absolute;
  bottom: -3px;
  right: -3px;
  width: 10px;
  height: 10px;
  background: #409eff;
  cursor: nwse-resize;
  touch-action: none;
}
.row-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 10px;
}
</style>
