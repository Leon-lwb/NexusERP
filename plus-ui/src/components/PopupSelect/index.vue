<template>
  <el-dialog v-model="visibleLocal" :title="title" width="70%">
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="dataList"
      @selection-change="handleSelectionChange"
      @row-click="handleRowClick"
      style="width: 100%"
      :row-key="getRowKey"
      reserve-selection
      highlight-current-row
    >
      <!-- 多选模式：显示选择框 -->
      <el-table-column v-if="multiple" type="selection" width="55" />
      <!-- 单选模式：显示单选按钮 -->
      <el-table-column v-else type="radio" width="55">
        <template #default="{ row }">
          <el-radio v-model="selectedRadioValue" :label="getRowKey(row)" @change="handleRadioChange(row)"> &nbsp; </el-radio>
        </template>
      </el-table-column>
      <el-table-column v-for="(label, field) in dictFields" :key="field" :prop="field" :label="label">
        <template #default="{ row }">
          {{ row[field] || '-' }}
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="handlePageChange"
    />

    <template #footer>
      <el-button @click="visibleLocal = false">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { listTableData } from '@/api/online';

interface Mapping {
  [key: string]: string;
}

const props = defineProps({
  visible: Boolean,
  title: String,
  selectedValues: {
    type: Array as PropType<any[]>, // 明确数组类型
    default: () => []
  },
  dictType: {
    type: String,
    required: true
  },
  dictFields: {
    type: Object,
    default: () => ({})
  },
  mapping: {
    type: Object,
    default: () => ({})
  },
  multiple: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:visible', 'select']);

const visibleLocal = ref(false);
const loading = ref(false);
const dataList = ref<any[]>([]);
const total = ref(0);
const tableRef = ref();
const selectedRadioValue = ref<string | number>(''); // 单选模式下的选中值
const getRowKey = (row: any) => {
  return row[Object.keys(props.dictFields)[0]];
};

watch(
  () => props.selectedValues,
  (newVal) => {
    nextTick(() => {
      if (props.multiple) {
        // 多选模式：处理多选框
        dataList.value.forEach((row) => {
          const key = getRowKey(row);
          const isSelected = newVal.includes(key);
          tableRef.value?.toggleRowSelection(row, isSelected);
        });
      } else {
        // 单选模式：处理单选按钮
        if (newVal.length > 0) {
          selectedRadioValue.value = newVal[0];
          // 找到对应的行并设置为选中状态
          const selectedRow = dataList.value.find((row) => getRowKey(row) === newVal[0]);
          if (selectedRow) {
            selectedRows.value = [selectedRow];
          }
        }
      }
    });
  },
  { immediate: true, deep: true }
);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
});
const selectedRows = ref<any[]>([]);

//同步内部状态到父组件
watch(visibleLocal, (newVal) => {
  emit('update:visible', newVal);
});

watch(
  () => props.visible,
  (val) => {
    visibleLocal.value = val;
    if (val) {
      resetQuery();
      getList();
    }
  }
);

const getList = async () => {
  try {
    loading.value = true;
    const res = await listTableData(props.dictType, queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handlePageChange = (pagination: any) => {
  queryParams.value.pageNum = pagination.pageNum;
  queryParams.value.pageSize = pagination.pageSize;
  getList();
};

const handleSelectionChange = (selection: any[]) => {
  selectedRows.value = selection;
};

const handleRadioChange = (row: any) => {
  // 单选模式下，直接设置选中的行
  selectedRows.value = [row];
};

const handleRowClick = (row: any) => {
  if (!props.multiple) {
    // 单选模式：设置单选按钮的值并选中行
    selectedRadioValue.value = getRowKey(row);
    selectedRows.value = [row];
  }
};

const handleConfirm = () => {
  if (selectedRows.value.length === 0) return;
  const mappedData = selectedRows.value.map((row) => {
    const result: any = {};
    Object.entries(props.mapping).forEach(([srcField, targetField]) => {
      result[targetField] = row[srcField];
    });
    return result;
  });

  // 根据 multiple 属性决定返回格式
  if (props.multiple) {
    emit('select', mappedData);
  } else {
    emit('select', mappedData[0]);
  }
  visibleLocal.value = false;
};

const resetQuery = () => {
  queryParams.value.pageNum = 1;
  queryParams.value.pageSize = 10;
  // 重置选中状态
  selectedRows.value = [];
  if (!props.multiple) {
    selectedRadioValue.value = '';
  }
};
</script>
