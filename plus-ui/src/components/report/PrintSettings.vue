<template>
  <div>
    <el-alert title="第二版打印绑定固定图片版本与业务条码；媒体也可在纸张画布上拖动和缩放。" type="info" :closable="false" class="mb-3" />
    <el-button v-if="!modelValue.print" :disabled="disabled || modelValue.kind !== 'DOCUMENT'" @click="enable">启用第二版打印</el-button>
    <el-form v-else :disabled="disabled" label-width="100px">
      <el-form-item label="分组字段"
        ><el-select :model-value="modelValue.print.groupBy" multiple :multiple-limit="3" @change="patch({ groupBy: $event })"
          ><el-option v-for="field in fields" :key="field.name" :label="field.label" :value="field.name" /></el-select
      ></el-form-item>
      <el-form-item label="重复页脚"
        ><el-switch :model-value="modelValue.print.repeatFooter" @change="patch({ repeatFooter: !!$event })"
      /></el-form-item>
      <el-form-item label="固定媒体"
        ><el-button :disabled="modelValue.print.media.length >= 6" @click="add('QR')">添加 QR</el-button
        ><el-button :disabled="modelValue.print.media.length >= 6" @click="add('CODE128')">添加 Code128</el-button
        ><el-button v-hasPermi="['report:asset:list']" :disabled="modelValue.print.media.length >= 6" @click="openPicker(-1)"
          >添加私有图片</el-button
        ></el-form-item
      >
      <el-card v-for="(media, index) in modelValue.print.media" :key="index" class="mb-3">
        <div class="media-heading">
          <strong>{{ media.kind === 'IMAGE' ? '私有图片' : media.kind === 'QR' ? '二维码' : '条形码' }}</strong
          ><el-button link type="danger" @click="remove(index)">删除</el-button>
        </div>
        <div class="media-fields">
          <template v-if="media.kind === 'IMAGE'"
            ><span>{{ assetNames[media.assetVersionId!] || '已绑定固定图片版本' }}</span
            ><el-button v-hasPermi="['report:asset:list']" @click="openPicker(index)">更换图片</el-button></template
          >
          <el-select v-else :model-value="media.field" placeholder="批准业务编码" @change="setField(index, $event)"
            ><el-option v-for="field in barcodeFields" :key="field.name" :value="field.name" :label="field.label"
          /></el-select>
          <label v-for="coordinate in coordinates" :key="coordinate.key"
            >{{ coordinate.label }}（mm）<el-input-number
              :model-value="ptToMm(media[coordinate.key])"
              :min="0"
              :max="600"
              :precision="2"
              @change="(value) => setDimension(index, coordinate.key, value)"
          /></label>
          <el-checkbox :model-value="media.repeat" @change="(value) => setRepeat(index, !!value)">每页重复</el-checkbox>
        </div>
      </el-card>
    </el-form>
    <AssetPicker v-model="pickerOpen" @select="selectAsset" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { ReportDsl, PrintDefinition, DatasetField } from '@/types/report';
import { enablePrint, effectivePaper, mmToPt, ptToMm, positionMedia } from '@/views/report/designer/gridCommands';
import AssetPicker from '@/components/report/designer/AssetPicker.vue';
/** 媒体设置仅提交草稿副本，权限由服务端再次检查。 */
const props = defineProps<{ modelValue: ReportDsl; fields: DatasetField[]; disabled?: boolean }>();
/** 原子编辑事件。 */
const emit = defineEmits<{ 'update:modelValue': [ReportDsl] }>();
/** 图片选择会话与当前媒体位置。 */
const pickerOpen = ref(false),
  pickerIndex = ref(-1),
  assetNames = ref<Record<string, string>>({});
/** 允许进入条码的固定业务编码字段。 */
const barcodeFields = computed(() =>
  props.fields.filter((field) => ['STRING', 'ID'].includes(field.type) && /(code|no|number|id|sn)$/i.test(field.name))
);
/** 布局维度使用毫米展示。 */
const coordinates = [
  { key: 'x', label: '横坐标' },
  { key: 'y', label: '纵坐标' },
  { key: 'width', label: '宽度' },
  { key: 'height', label: '高度' }
] as const;
/** 提交一次受控操作并报告不可容纳的媒体。 */
function apply(action: () => ReportDsl) {
  if (props.disabled) return;
  try {
    emit('update:modelValue', action());
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 显式升级草稿格式且保持原纸张方向。 */
function enable() {
  apply(() => enablePrint(props.modelValue));
}
/** 只修改分组和页脚，不在此重复修改纸张方向。 */
function patch(value: Partial<PrintDefinition>) {
  apply(() => {
    const next = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
    Object.assign(next.print!, value);
    return next;
  });
}
/** 新媒体以当前可打印区域为边界。 */
function withMedia(kind: 'IMAGE' | 'QR' | 'CODE128', assetVersionId: string | null = null): ReportDsl {
  const next = enablePrint(props.modelValue),
    page = effectivePaper(next);
  if (next.print!.media.length >= 6) throw Error('最多配置6个媒体');
  next.print!.media.push({
    kind,
    assetVersionId,
    field: kind === 'IMAGE' ? null : barcodeFields.value[0]?.name || '',
    x: page.marginLeft,
    y: page.marginTop + 40,
    width: kind === 'CODE128' ? 180 : 72,
    height: kind === 'CODE128' ? 48 : 72,
    repeat: false
  });
  return positionMedia(next, next.print!.media.length - 1, {});
}
/** 添加服务器支持的条码种类。 */
function add(kind: 'QR' | 'CODE128') {
  apply(() => withMedia(kind));
}
/** 打开图库前固定当前编辑会话。 */
function openPicker(index: number) {
  if (!props.disabled) {
    pickerIndex.value = index;
    pickerOpen.value = true;
  }
}
/** 将选择结果绑定为不可变版本。 */
function selectAsset(asset: { versionId: string; title: string }) {
  if (!pickerOpen.value || props.disabled) return;
  assetNames.value[asset.versionId] = asset.title;
  apply(() => {
    if (pickerIndex.value < 0) return withMedia('IMAGE', asset.versionId);
    const next = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
    const media = next.print?.media[pickerIndex.value];
    if (!media || media.kind !== 'IMAGE') throw Error('当前图片已移除，请重新选择');
    media.assetVersionId = asset.versionId;
    return next;
  });
}
/** 绑定批准业务字段。 */
function setField(index: number, value: string) {
  apply(() => {
    const next = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
    next.print!.media[index].field = value;
    return next;
  });
}
/** 调整毫米尺寸时复用画布同一边界与二维码约束。 */
function setDimension(index: number, key: 'x' | 'y' | 'width' | 'height', value: number | undefined) {
  if (value !== undefined) apply(() => positionMedia(props.modelValue, index, { [key]: mmToPt(value) }));
}
/** 修改每页重复设置。 */
function setRepeat(index: number, value: boolean) {
  apply(() => {
    const next = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
    next.print!.media[index].repeat = value;
    return next;
  });
}
/** 删除媒体并关闭旧选择会话。 */
function remove(index: number) {
  pickerOpen.value = false;
  apply(() => {
    const next = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
    next.print!.media.splice(index, 1);
    return next;
  });
}
watch(
  () => [props.modelValue.code, props.modelValue.datasetVersionId, props.disabled],
  () => {
    pickerOpen.value = false;
  }
);
</script>
<style scoped>
.media-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.media-fields {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
  align-items: center;
}
.media-fields label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
