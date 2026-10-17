<template>
  <el-form inline label-position="top" :disabled="disabled">
    <el-form-item label="纸张预设"
      ><el-select :model-value="preset" style="width: 140px" @change="choosePreset">
        <el-option label="A4 · 210 × 297 mm" value="A4" /><el-option label="A5 · 148 × 210 mm" value="A5" /><el-option
          label="自定义"
          value="CUSTOM"
        /> </el-select
    ></el-form-item>
    <el-form-item label="方向"
      ><el-radio-group :model-value="orientation" @change="chooseOrientation"
        ><el-radio-button value="PORTRAIT">纵向</el-radio-button><el-radio-button value="LANDSCAPE">横向</el-radio-button></el-radio-group
      ></el-form-item
    >
    <el-form-item v-for="setting in settings" :key="setting.key" :label="setting.label + '（mm）'">
      <el-input-number
        :model-value="ptToMm(paper[setting.key])"
        :min="0"
        :max="600"
        :precision="2"
        :step="1"
        @change="(value) => dimension(setting.key, value)"
      />
    </el-form-item>
    <el-form-item label="续页列头"
      ><el-switch :model-value="modelValue.repeatHeader" @change="(value) => emit('update:modelValue', { ...modelValue, repeatHeader: !!value })"
    /></el-form-item>
  </el-form>
  <el-alert title="尺寸以毫米设置；正式分页、换行和字体测量以服务端固定计划为准。" type="info" :closable="false" />
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { ElMessage } from 'element-plus';
import type { ReportDsl, ReportPage } from '@/types/report';
import { effectivePaper, mmToPt, ptToMm, setPaper, updatePaper, type PaperOrientation, type PaperPreset } from '@/views/report/designer/gridCommands';
/** 纸张设置只向父级提交已校验的独立草稿。 */
const props = defineProps<{ modelValue: ReportDsl; disabled?: boolean }>();
/** 一次设置变更对应一条编辑历史。 */
const emit = defineEmits<{ 'update:modelValue': [ReportDsl] }>();
/** 实际纸张包含第二版方向变换。 */
const paper = computed(() => effectivePaper(props.modelValue));
/** 旧定义直接按实际宽高判断方向。 */
const orientation = computed<PaperOrientation>(() => (paper.value.width > paper.value.height ? 'LANDSCAPE' : 'PORTRAIT'));
/** 按物理尺寸识别预设，不在DSL写入额外标志。 */
const preset = computed<PaperPreset>(() => {
  const dimensions = [ptToMm(paper.value.width), ptToMm(paper.value.height)].sort((left, right) => left - right);
  if (Math.abs(dimensions[0] - 210) < 0.05 && Math.abs(dimensions[1] - 297) < 0.05) return 'A4';
  if (Math.abs(dimensions[0] - 148) < 0.05 && Math.abs(dimensions[1] - 210) < 0.05) return 'A5';
  return 'CUSTOM';
});
/** 可编辑尺寸与中文标签。 */
const settings: { key: keyof ReportPage; label: string }[] = [
  { key: 'width', label: '纸张宽度' },
  { key: 'height', label: '纸张高度' },
  { key: 'marginTop', label: '上边距' },
  { key: 'marginRight', label: '右边距' },
  { key: 'marginBottom', label: '下边距' },
  { key: 'marginLeft', label: '左边距' },
  { key: 'rowHeight', label: '明细行高' }
];
/** 集中报告尺寸冲突并保留旧草稿。 */
function apply(action: () => ReportDsl) {
  if (props.disabled) return;
  try {
    emit('update:modelValue', action());
  } catch (error) {
    ElMessage.warning((error as Error).message);
  }
}
/** 切换标准纸张不会重复交换方向。 */
function choosePreset(value: PaperPreset) {
  apply(() => setPaper(props.modelValue, value, orientation.value));
}
/** 保留当前纸张规格切换方向。 */
function chooseOrientation(value: string | number | boolean | undefined) {
  apply(() => setPaper(props.modelValue, preset.value, value as PaperOrientation));
}
/** 将毫米输入转换为既有精确布局单位。 */
function dimension(key: keyof ReportPage, value: number | undefined) {
  if (value !== undefined) apply(() => updatePaper(props.modelValue, { [key]: mmToPt(value) }));
}
</script>
