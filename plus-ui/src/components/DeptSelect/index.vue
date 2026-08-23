<template>
  <el-tree-select
    v-model="selectedDeptId"
    :data="deptOptions"
    :props="deptTreeProps"
    value-key="deptId"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    check-strictly
    filterable
    :style="{ width: width }"
    @node-click="handleNodeClick"
    @clear="handleClear"
  />
</template>

<script setup lang="ts" name="DeptSelect">
import type { TreeOptionProps } from 'element-plus';
import { listDept } from '@/api/system/dept';
import type { DeptVO } from '@/api/system/dept/types';

const { proxy } = getCurrentInstance() as any;

interface Props {
  /** 绑定值（deptId） */
  modelValue?: string | number;
  /** 按部门类别过滤（dept_category 字典值，如 PRODUCTION/SALES/PURCHASE/WAREHOUSE 等）。不传则展示全部部门 */
  category?: string;
  disabled?: boolean;
  clearable?: boolean;
  placeholder?: string;
  width?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  category: undefined,
  disabled: false,
  clearable: true,
  placeholder: '请选择部门',
  width: '100%'
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number | undefined): void;
  (e: 'change', dept: DeptVO | undefined): void;
}>();

const selectedDeptId = ref<string | number | undefined>(props.modelValue);
// 树节点字段映射（element-plus TreeOptionProps 未收录 value 键，扩展声明）
const deptTreeProps: TreeOptionProps & { value?: string } = { value: 'deptId', label: 'deptName', children: 'children' };
const deptOptions = ref<any[]>([]);

// 同步外部传入的 modelValue
watch(
  () => props.modelValue,
  (val) => {
    selectedDeptId.value = val;
  }
);

/** 加载部门树（可选按 category 过滤） */
async function getDeptTree() {
  const params: any = {};
  if (props.category) {
    params.deptCategory = props.category;
  }
  const res = await listDept(params);
  deptOptions.value = proxy?.handleTree(res.data, 'deptId');
}

/** 选择节点：emit deptId 和整个 dept 节点（便于父组件回填 deptName） */
function handleNodeClick(data: any) {
  emit('update:modelValue', data.deptId);
  emit('change', data);
}

function handleClear() {
  emit('update:modelValue', undefined);
  emit('change', undefined);
}

onMounted(() => {
  getDeptTree();
});
</script>
