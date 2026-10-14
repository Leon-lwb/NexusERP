<template>
  <div>
    <el-alert
      title="第二版打印使用固定图片版本与服务器条码。保存和发布后形成新版本，历史运行继续使用原计划。"
      type="info"
      :closable="false"
      class="mb-3"
    /><el-button v-if="!modelValue.print" :disabled="disabled || modelValue.kind !== 'DOCUMENT'" @click="enable">启用第二版打印</el-button>
    <el-form v-else :disabled="disabled" label-width="100px"
      ><el-form-item label="纸张方向"
        ><el-select :model-value="modelValue.print.orientation" @change="patch({ orientation: $event })"
          ><el-option label="纵向" value="PORTRAIT" /><el-option label="横向" value="LANDSCAPE" /></el-select></el-form-item
      ><el-form-item label="分组字段"
        ><el-select :model-value="modelValue.print.groupBy" multiple :multiple-limit="3" @change="patch({ groupBy: $event })"
          ><el-option v-for="field in fields" :key="field.name" :label="field.label" :value="field.name" /></el-select></el-form-item
      ><el-form-item label="重复页脚"
        ><el-switch :model-value="modelValue.print.repeatFooter" @change="patch({ repeatFooter: !!$event })" /><span class="ml-2"
          >使用末页固定区，保留原始明细行数</span
        ></el-form-item
      >
      <el-form-item label="固定媒体"
        ><el-button :disabled="modelValue.print.media.length >= 6" @click="add('QR')">添加QR</el-button
        ><el-button :disabled="modelValue.print.media.length >= 6" @click="add('CODE128')">添加Code128</el-button
        ><el-button :disabled="modelValue.print.media.length >= 6" @click="add('IMAGE')">添加私有图片</el-button></el-form-item
      >
      <el-card v-for="(media, index) in modelValue.print.media" :key="index" class="mb-3"
        ><div class="flex justify-between">
          <strong>{{ media.kind }}</strong
          ><el-button link type="danger" @click="remove(index)">删除</el-button>
        </div>
        <div class="media-fields">
          <el-input
            v-if="media.kind === 'IMAGE'"
            :model-value="media.assetVersionId || ''"
            placeholder="从私有资产目录复制版本ID"
            @update:model-value="set(index, 'assetVersionId', $event)"
          /><el-select v-else :model-value="media.field" placeholder="批准业务编码" @change="set(index, 'field', $event)"
            ><el-option
              v-for="field in fields.filter((f) => ['STRING', 'ID'].includes(f.type) && /(code|no|number|id|sn)$/i.test(f.name))"
              :key="field.name"
              :value="field.name"
              :label="field.label" /></el-select
          ><label v-for="key in coordinates" :key="key"
            >{{ key }}（pt）<el-input-number
              :model-value="media[key]"
              :min="0"
              :max="2000"
              @update:model-value="set(index, key, $event || 0)" /></label
          ><el-checkbox :model-value="media.repeat" @change="set(index, 'repeat', !!$event)">每页重复</el-checkbox>
        </div></el-card
      >
    </el-form>
  </div>
</template>
<script setup lang="ts">
/** 第二版打印控件只修改草稿，私有资产由服务端固定和鉴权。 */
import type { ReportDsl, PrintDefinition, DatasetField } from '@/types/report';
/**
 * 保存当前页面或测试的props、emit状态。
 */
const props = defineProps<{
    /**
     * 当前契约的modelValue配置或状态
     */
    modelValue: ReportDsl; /**
     * 当前契约的fields配置或状态
     */
    fields: DatasetField[]; /**
     * 当前契约的disabled配置或状态
     */
    disabled?: boolean;
  }>(),
  emit = defineEmits<{
    /**
     * 当前契约的'update:modelValue'配置或状态
     */
    'update:modelValue': [ReportDsl];
  }>();
/**
 * 保存当前页面或测试的coordinates状态。
 */
const coordinates = ['x', 'y', 'width', 'height'] as const;
/** 创建独立草稿副本，避免直接修改父组件状态。 */
function change(action: (dsl: ReportDsl) => void) {
  const next = JSON.parse(JSON.stringify(props.modelValue)) as ReportDsl;
  action(next);
  emit('update:modelValue', next);
}
/** 明确升级草稿格式，保留旧发布版本。 */
function enable() {
  change((dsl) => {
    dsl.schemaVersion = 2;
    dsl.print = { orientation: 'PORTRAIT', groupBy: [], repeatFooter: false, media: [] };
  });
}
/** 修改方向、分组或重复固定区。 */
function patch(value: Partial<PrintDefinition>) {
  change((dsl) => Object.assign(dsl.print!, value));
}
/** 添加一个受支持媒体，字段与版本随后明确选择。 */
function add(kind: 'IMAGE' | 'QR' | 'CODE128') {
  change((dsl) =>
    dsl.print!.media.push({
      kind,
      assetVersionId: kind === 'IMAGE' ? '' : null,
      field: kind === 'IMAGE' ? null : props.fields.find((f) => /(code|no|number|id|sn)$/i.test(f.name))?.name || '',
      x: dsl.page.marginLeft,
      y: dsl.page.marginTop + 40,
      width: kind === 'CODE128' ? 180 : 72,
      height: kind === 'CODE128' ? 48 : 72,
      repeat: false
    })
  );
}
/** 修改媒体的一个受控属性。 */
function set(index: number, key: string, value: unknown) {
  change((dsl) => Object.assign(dsl.print!.media[index], { [key]: value }));
}
/** 删除当前草稿媒体，不改写任何发布资源。 */
function remove(index: number) {
  change((dsl) => dsl.print!.media.splice(index, 1));
}
</script>
<style scoped>
.media-fields {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.media-fields label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
