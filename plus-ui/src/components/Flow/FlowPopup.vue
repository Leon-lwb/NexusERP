<template>
  <div class="nm-popup">
    <!-- 显示框 -->
    <el-input v-model="displayValue" readonly :placeholder="nm.columnComment" @click="showPopup = true">
      <template #append>
        <el-button icon="Search" @click="showPopup = true" />
      </template>
    </el-input>

    <!-- 弹出选择窗口 -->
    <el-dialog v-model="showPopup" :title="`选择${nm.columnComment}`" width="60%" append-to-body>
      <!-- 搜索栏 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="searchKey" placeholder="输入关键词搜索" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table
        :data="tableData"
        highlight-current-row
        @row-click="handleRowClick"
        @selection-change="handleSelectionChange"
        :row-key="getRowKey"
        reserve-selection
      >
        <!-- 选择列放到最前面 -->
        <el-table-column width="55" v-if="!isMultiple">
          <template #default="{ row }">
            <el-radio v-model="selectedId" :label="row[keyField]">&nbsp;</el-radio>
          </template>
        </el-table-column>
        <el-table-column type="selection" width="55" v-if="isMultiple" />

        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" />
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />

      <!-- 添加弹窗底部按钮 -->
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="handleConfirm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, watch, onMounted, computed } from 'vue';
import { listPopupData, getPopupDetail } from '@/api/online';
import { ElMessage } from 'element-plus';

defineOptions({ name: 'FlowPopup' });

const props = defineProps({
  nm: {
    type: Object,
    required: true
  },
  value: [String, Number] // 绑定的主键值
});

const emit = defineEmits(['update:value', 'commonComponent']);

// 组件状态
const showPopup = ref(false);
const displayValue = ref('');
const tableData = ref([]);
const selectedId = ref(null);
const searchKey = ref('');
const tempSelectedRow = ref<any>(null); // 新增临时存储选中行

// 新增：多选相关状态
const selectedRows = ref<any[]>([]);
const isMultiple = computed(() => {
  try {
    // 优先从 mapping 中读取 multiple 配置
    const mapping = safeParseJSON(props.nm.mapping) || {};
    if (mapping.popupConfig && mapping.popupConfig.multiple !== undefined) {
      return mapping.popupConfig.multiple === true;
    }

    // 如果没有配置，默认为单选
    return false;
  } catch (e) {
    console.error('解析多选配置失败:', e);
    return false;
  }
});

// 分页参数
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 获取配置字段
const keyField = props.nm.keyField || 'id';
const displayField = props.nm.displayField || 'name';

// 获取行键值
const getRowKey = (row: any) => {
  return row[keyField];
};

// 动态columns配置
const columns = computed(() => {
  try {
    const fieldsConfig = JSON.parse(props.nm.dictFields.replace(/'/g, '"').replace(/"/g, '"').replace(/"/g, '"'));
    return Object.entries(fieldsConfig).map(([prop, label]) => ({
      prop,
      label: label as string
    }));
  } catch (e) {
    console.error('dictFields解析失败:', props.nm.dictFields);
    return [
      { prop: 'code', label: '编码' },
      { prop: 'name', label: '名称' }
    ];
  }
});

const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  params: {}
});

// 加载数据（请求序号防止快速翻页/连续搜索时旧响应覆盖新响应）
let loadSeq = 0;
const loadData = async () => {
  const seq = ++loadSeq;
  try {
    // 关键词过滤：后端按来源表全部文本列做 LIKE 匹配
    queryParams.params.keyword = searchKey.value?.trim() || undefined;
    const res = await listPopupData(props.nm.tableId, props.nm.dictType, queryParams);
    if (seq !== loadSeq) return;
    tableData.value = res.rows;
    total.value = res.total;
  } catch (error) {
    if (seq === loadSeq) {
      console.error('加载弹出数据失败:', error);
      ElMessage.error('数据加载失败');
    }
  }
};

// 搜索时重置到第一页
const handleSearch = () => {
  queryParams.pageNum = 1;
  loadData();
};

// runtime override for popup query linkage (from useOnlineRuntime.computeRuntimeLinkageState)
const runtimePopupParams = computed<Record<string, any>>(() => {
  const nm: any = props.nm as any;
  return nm && typeof nm.runtimePopupParams === 'object' && !Array.isArray(nm.runtimePopupParams) ? nm.runtimePopupParams : {};
});

watch(
  () => runtimePopupParams.value,
  (p) => {
    queryParams.pageNum = 1;
    queryParams.params = { ...(p || {}) };
    // don't block UI if backend is temporarily down
    loadData();
  },
  { deep: true, immediate: true }
);

// 行点击处理
const handleRowClick = (row: any) => {
  if (!isMultiple.value) {
    selectedId.value = row[keyField];
    tempSelectedRow.value = row; // 存储选中行数据
  }
};

// 处理多选变化
const handleSelectionChange = (selection: any[]) => {
  selectedRows.value = selection;
};

// 修改确认选择处理逻辑
const handleConfirm = () => {
  if (isMultiple.value) {
    // 多选模式
    if (selectedRows.value.length === 0) {
      ElMessage.warning('请先选择记录');
      return;
    }

    // 处理多选数据 - 修复数据结构
    const mappedDataArray = selectedRows.value.map((row) => {
      // 直接返回行数据，不包装额外的配置信息
      return row;
    });

    const returnData = {
      id: selectedRows.value.map((row) => row[keyField]),
      display: selectedRows.value.map((row) => row[displayField]).join(', '),
      dictFields: safeParseJSON(props.nm.dictFields) || {},
      mapping: safeParseJSON(props.nm.mapping) || {},
      popupConfig: safeParseJSON(props.nm.popupConfig) || {},
      commonDataInfo: {},
      mappedData: mappedDataArray // 这里直接是行数据数组
    };

    emit('update:value', returnData.id);
    emit('commonComponent', returnData);
  } else {
    // 单选模式（保持原有逻辑）
    if (!tempSelectedRow.value) {
      ElMessage.warning('请先选择一条记录');
      return;
    }

    const returnData = processRowData(tempSelectedRow.value);
    emit('update:value', selectedId.value);
    emit('commonComponent', returnData);
  }

  showPopup.value = false;
};

// 新增：处理行数据的通用方法
const processRowData = (row: any) => {
  const dictFields: Record<string, any> = safeParseJSON(props.nm.dictFields) || {};
  let mapping: Record<string, any> = safeParseJSON(props.nm.mapping) || {};
  let popupConfig: Record<string, any> = safeParseJSON(props.nm.popupConfig) || {};

  // 处理嵌套的 mapping 结构
  if (mapping.mapping) {
    popupConfig = mapping.popupConfig || popupConfig;
    mapping = mapping.mapping;
  }

  const displayMode = popupConfig.displayMode || 'value';

  // 构建返回数据（sub 模式下会追加 mappedData 字段）
  const returnData: Record<string, any> = {
    id: row[keyField],
    display: row[displayField],
    dictFields: dictFields,
    mapping: mapping,
    popupConfig: popupConfig,
    commonDataInfo: {}
  };

  // 根据 displayMode 处理数据
  if (displayMode === 'sub') {
    // sub 模式：基于 mapping 构建完整映射数据
    const mappedData = {};
    Object.entries(mapping).forEach(([sourceField, targetField]) => {
      if (row[sourceField] !== undefined) {
        mappedData[targetField] = convertValueType(row[sourceField], props.nm.javaType);
        // 只设置下划线版本（避免数据库字段错误）
        const snakeField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase();
        if (snakeField !== targetField) {
          mappedData[snakeField] = mappedData[targetField];
        }
      }
    });

    returnData.mappedData = [mappedData];
    returnData.commonDataInfo = mappedData;
  } else {
    // 原有逻辑：基于 dictFields 构建 commonDataInfo
    returnData.commonDataInfo = Object.keys(dictFields).reduce(
      (acc, sourceField) => {
        const targetField = mapping[sourceField] || sourceField;
        acc[targetField] = convertValueType(row[sourceField], props.nm.javaType);
        return acc;
      },
      {} as Record<string, any>
    );
  }

  return returnData;
};

// 添加通用JSON解析方法
const safeParseJSON = (str: any) => {
  if (typeof str !== 'string') return str;
  try {
    return JSON.parse(str.replace(/[""]/g, '"').replace(/'/g, '"').replace(/\\/g, '\\\\'));
  } catch (e) {
    console.error('JSON解析失败，原始字符串:', str);
    return {};
  }
};

// 类型转换辅助方法
const convertValueType = (value: any, javaType: string) => {
  if (value === null || value === undefined) return null;

  switch (javaType) {
    case 'Long':
    case 'Integer':
      return Number(value) || 0;
    case 'BigDecimal':
    case 'Double':
      return parseFloat(value) || 0;
    default:
      return String(value);
  }
};

// 取消选择
const handleCancel = () => {
  if (isMultiple.value) {
    selectedRows.value = [];
  } else {
    selectedId.value = null;
    tempSelectedRow.value = null;
  }
  showPopup.value = false;
};

// 初始化加载显示值
const loadDisplayValue = async () => {
  if (!props.value) {
    displayValue.value = '';
    return;
  }

  // 根据配置决定显示方式
  if (props.nm.showDisplay) {
    try {
      const res = await getPopupDetail(props.nm.tableId, props.nm.dictType, props.value);
      displayValue.value = res.data[displayField];
    } catch (error) {
      console.error('加载显示值失败:', error);
      ElMessage.error('显示值加载失败');
    }
  } else {
    displayValue.value = props.value.toString();
  }
  selectedId.value = props.value;
};

// 监听value变化（用于编辑时回显）
watch(
  () => props.value,
  (newVal) => {
    if (newVal) {
      loadDisplayValue();
    } else {
      displayValue.value = '';
    }
  }
);

// 初始化
onMounted(() => {
  if (props.value) {
    loadDisplayValue();
  }
});
</script>

<style scoped>
.nm-popup :deep(.el-dialog__body) {
  padding: 10px 20px;
}
.search-form {
  margin-bottom: 15px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
