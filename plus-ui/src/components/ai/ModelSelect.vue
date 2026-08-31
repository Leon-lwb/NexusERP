<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled || loading"
    :clearable="clearable"
    filterable
    size="small"
    style="width: 100%"
    @update:model-value="onChange"
  >
    <el-option v-for="item in options" :key="item.id" :label="item.displayName || item.modelName" :value="item[valueField]">
      <span class="model-opt">
        <el-tag size="small" effect="plain" :type="providerTagType(item.providerType)">
          {{ providerTagText(item.providerType) }}
        </el-tag>
        <span class="model-opt-name">{{ item.displayName || item.modelName }}</span>
        <el-tag v-if="item.isDefault === 'Y'" size="small" type="success" effect="plain">默认</el-tag>
      </span>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { listModelOptions, type AiModel } from '@/api/ai/model';

/**
 * AI 模型选择器（按 modelType 分类加载启用模型）
 * <p>对话页用 valueField="modelName"（后端按名解析），文生图页用 valueField="id"（按 id 解析）。
 * modelType 支持逗号分隔多类型（如 "TEXT,VISION"，后端 in 查询）。
 * 可选传入 requiredInputCapability/requiredOutputCapability 按能力过滤（如知识库选模型要求输出含 VECTOR）。
 * 无选中值时自动预选 isDefault=Y 的模型。</p>
 */
interface Props {
  /** 模型分类：CHAT/EMBEDDING/RERANK/IMAGE */
  modelType: string;
  /** 选中值（modelName 字符串 或 id 数字） */
  modelValue?: string | number;
  /** 绑定值取哪个字段：对话用 modelName，文生图用 id */
  valueField?: 'modelName' | 'id';
  /** 要求输入能力包含该值（字典 ai_model_capability，如 TEXT），可选 */
  requiredInputCapability?: string;
  /** 要求输出能力包含该值（字典 ai_model_capability，如 VECTOR），可选 */
  requiredOutputCapability?: string;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  valueField: 'modelName',
  placeholder: '请选择模型',
  disabled: false,
  clearable: false
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number | undefined): void;
  (e: 'change', model: AiModel | undefined): void;
}>();

const options = ref<AiModel[]>([]);
const loading = ref(false);

async function loadOptions() {
  loading.value = true;
  try {
    const res: any = await listModelOptions(props.modelType, props.requiredInputCapability, props.requiredOutputCapability);
    options.value = res.data || [];
    // 无选中值时预选默认模型（首屏即显示将使用的模型）
    if (props.modelValue === undefined || props.modelValue === null || props.modelValue === '') {
      if (options.value.length) {
        const def = options.value.find((m) => m.isDefault === 'Y') || options.value[0];
        emit('update:modelValue', def[props.valueField]);
        emit('change', def);
      }
    }
  } finally {
    loading.value = false;
  }
}

function onChange(val: string | number | undefined) {
  emit('update:modelValue', val);
  const matched = options.value.find((m) => m[props.valueField] === val);
  emit('change', matched);
}

/** 厂商类型标签样式：本地(Ollama)用 warning 提示，云上厂商用 info */
function providerTagType(providerType?: string): any {
  if (providerType === 'OLLAMA') return 'warning';
  return 'info';
}

/** 厂商类型标签文本 */
function providerTagText(providerType?: string): string {
  return providerType || '未知';
}

onMounted(loadOptions);
</script>

<style scoped>
.model-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.model-opt-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
