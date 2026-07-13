<template>
  <el-select
    v-model="selectedId"
    filterable
    remote
    reserve-keyword
    clearable
    :remote-method="handleSearch"
    :loading="loading"
    placeholder="请输入设备名称搜索"
    style="width: 100%"
    @change="handleChange"
  >
    <el-option v-for="item in options" :key="item.equipmentId" :label="item.equipmentCode + ' - ' + item.equipmentName" :value="item.equipmentId" />
  </el-select>
</template>

<script setup lang="ts" name="EquipmentSelect">
import { listEquipment, getEquipment } from '@/api/mes/eq/equipment';
import type { EqEquipmentVO } from '@/api/mes/eq/equipment';

const props = defineProps<{ modelValue?: number | null }>();
const emit = defineEmits<{
  (e: 'update:modelValue', val: number | null | undefined): void;
  (e: 'change', val: number | null | undefined, item?: EqEquipmentVO): void;
}>();

const selectedId = ref<number | null | undefined>(props.modelValue);
const options = ref<EqEquipmentVO[]>([]);
const loading = ref(false);

watch(
  () => props.modelValue,
  (val) => {
    selectedId.value = val;
  }
);

const handleSearch = async (query: string) => {
  if (!query) {
    options.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listEquipment({ pageNum: 1, pageSize: 50, equipmentName: query } as any);
    options.value = res.rows || [];
  } finally {
    loading.value = false;
  }
};

const handleChange = (val: number | null | undefined) => {
  emit('update:modelValue', val);
  const item = options.value.find((i) => i.equipmentId === val);
  emit('change', val, item);
};

// 初始回显: modelValue 有值时加载该设备
onMounted(async () => {
  if (props.modelValue) {
    try {
      const res = await getEquipment(props.modelValue);
      options.value = res.data ? [res.data] : [];
    } catch {
      /* ignore */
    }
  }
});
</script>
