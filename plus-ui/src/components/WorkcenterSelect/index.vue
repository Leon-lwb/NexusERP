<template>
  <el-select
    v-model="selectedId"
    filterable
    remote
    reserve-keyword
    clearable
    :remote-method="handleSearch"
    :loading="loading"
    placeholder="请输入工作中心搜索"
    style="width: 100%"
    @change="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.workstation_id"
      :label="item.workstation_code + ' - ' + item.workstation_name"
      :value="item.workstation_id"
    />
  </el-select>
</template>

<script setup lang="ts" name="WorkcenterSelect">
import { listWorkcenters } from '@/api/mes/eq/equipment';

/** ERP 工作中心选项(字段对齐 ErpWorkstation VO) */
interface WorkcenterOption {
  workstation_id: string | number;
  workstation_code: string;
  workstation_name: string;
}

const props = defineProps<{ modelValue?: string | number | null }>();
const emit = defineEmits<{
  (e: 'update:modelValue', val: string | number | null | undefined): void;
  (e: 'change', val: string | number | null | undefined, item?: WorkcenterOption): void;
}>();

const selectedId = ref<string | number | null | undefined>(props.modelValue);
const options = ref<WorkcenterOption[]>([]);
const loading = ref(false);

/** 拉取工作中心列表并定位当前值(编辑回显场景, 工作中心无 get-by-id 接口) */
const loadAndFind = async () => {
  loading.value = true;
  try {
    const res = await listWorkcenters('');
    options.value = (res.data || []) as WorkcenterOption[];
  } finally {
    loading.value = false;
  }
};

// 外部 modelValue 变化时, 若新值不在当前选项中则拉全量回显(编辑弹窗复用场景)
watch(
  () => props.modelValue,
  (val) => {
    selectedId.value = val;
    if (val && !options.value.some((i) => i.workstation_id === val)) {
      loadAndFind();
    }
  }
);

const handleSearch = async (query: string) => {
  if (!query) {
    options.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listWorkcenters(query);
    options.value = (res.data || []) as WorkcenterOption[];
  } finally {
    loading.value = false;
  }
};

const handleChange = (val: number | null | undefined) => {
  emit('update:modelValue', val);
  const item = options.value.find((i) => i.workstation_id === val);
  emit('change', val, item);
};

// 初始回显: modelValue 有值时拉取列表定位
onMounted(async () => {
  if (props.modelValue) {
    await loadAndFind();
  }
});
</script>
