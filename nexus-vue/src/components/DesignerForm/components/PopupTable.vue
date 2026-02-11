<template>
  <div class="popup-table-container">
    <el-input
      v-model="displayLabel"
      :placeholder="props.placeholder || '请选择'"
      readonly
      class="popup-input"
    >
      <template #append>
        <el-button @click="handleSearch" :icon="SearchIcon" />
      </template>
    </el-input>
    <el-dialog 
      v-model="dialogVisible" 
      :title="props.nm?.label || '选择数据'" 
      width="70%"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      append-to-body
    >
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
        <el-table-column v-if="props.multiple" type="selection" width="55" />
        <!-- 单选模式：显示单选按钮 -->
        <el-table-column v-else type="radio" width="55">
          <template #default="{ row }">
            <el-radio 
              v-model="selectedRadioValue" 
              :label="getRowKey(row)"
              @change="handleRadioChange(row)"
            >
              &nbsp;
            </el-radio>
          </template>
        </el-table-column>
        <el-table-column
          v-for="(label, field) in dictFields"
          :key="field"
          :prop="field"
          :label="label"
        >
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
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, watch, nextTick } from 'vue'
import type { PropType } from 'vue'
import { listTableData } from "@/api/online";
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  nm: Object,
  dictType: String,
  dictFields: {
    type: Object,
    default: () => ({})
  },
  mapping: {
    type: Object,
    default: () => ({})
  },
  placeholder: String,
  multiple: {
    type: Boolean,
    default: false
  },
  // 控制展示模式：main(显示名称) / sub(显示值)
  displayMode: {
    type: String as PropType<'label' | 'value' | 'auto'>,
    default: 'auto'
  }
})

const emit = defineEmits(['update:modelValue', 'common-component'])

const dialogVisible = ref(false)
const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const tableRef = ref();
const selectedRadioValue = ref<string | number>(''); // 单选模式下的选中值
const userSelectedValue = ref<string | number>('')  // 用户选择的值的缓存
const getRowKey = (row: any) => {
  return row[getValueSourceKey()];
};

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
})
const selectedRows = ref<any[]>([])
const displayLabel = ref('')
const SearchIcon = Search

// 从 mapping 与 dictFields 推断当前组件对应的源字段键
function getMappingObject(): Record<string, string> | null {
  try {
    if (!props.mapping) return null
    if (typeof props.mapping === 'string') return JSON.parse(props.mapping)
    if (typeof props.mapping === 'object') return props.mapping as Record<string, string>
  } catch {}
  return null
}

function getDictFieldKeys(): { valueKey: string, labelKey: string } {
  const keys = Object.keys(props.dictFields || {})
  const valueKey = keys[0]
  const labelKey = keys[1] || valueKey
  return { valueKey, labelKey }
}

function getValueSourceKey(): string {
  const mapping = getMappingObject()
  const currentTarget = (props.nm as any)?.field as string || ''
  if (mapping && currentTarget) {
    for (const [src, target] of Object.entries(mapping)) {
      if (String(target) === currentTarget) {
        return src
      }
    }
  }
  return getDictFieldKeys().valueKey
}

function getLabelSourceKey(): string {
  const mapping = getMappingObject()
  const currentTarget = (props.nm as any)?.field as string || ''
  if (mapping) {
    // 优先找 target 以 name 结尾的映射，或 src 包含 name/label 的映射，且不等于当前目标字段
    const preferred = Object.entries(mapping).find(([src, target]) => {
      return String(target) !== currentTarget && (String(target).toLowerCase().endsWith('name') || src.toLowerCase().includes('name') || src.toLowerCase().includes('label'))
    })
    if (preferred) return preferred[0]
  }
  return getDictFieldKeys().labelKey
}

const getLabelByValue = (value: string | number | undefined | null) => {
  if (value === undefined || value === null || value === '') return ''
  
  try {
    // sub 表：指定 value 模式则直接返回值
    if (props.displayMode === 'value') {
      return String(value)
    }

    // 如果数据列表为空，直接返回 value
    if (dataList.value.length === 0) {
      return String(value)
    }
    
    const key = getValueSourceKey()
    const labelKey = getLabelSourceKey()
    const row = dataList.value.find(item => item[key] == value)
    
    // 如果找到了行数据
    if (row) {
      // main 表：指定 label 模式优先返回 label
      if (props.displayMode === 'label') {
        return row[labelKey] || String(value)
      }
      // auto：默认按 label 返回
      return row[labelKey] || String(value)
    }
    
    // 如果没找到，直接返回 value
    return String(value)
  } catch (error) {
    console.error('Error in getLabelByValue:', error)
    return String(value || '')
  }
}

watch(() => props.modelValue, (val) => {
  try {
    displayLabel.value = getLabelByValue(val)
  } catch (error) {
    console.error('Error in watch callback:', error)
    displayLabel.value = ''
  }
}, { immediate: true })

const handleSearch = async () => {
  dialogVisible.value = true
  await getList()
}

const getList = async () => {
  loading.value = true
  try {
    // 确保 dictType 是有效的表名
    if (!props.dictType || typeof props.dictType !== 'string') {
      console.error('无效的表名:', props.dictType)
      return
    }
    
    const res = await listTableData(props.dictType, queryParams.value)
    if (res.code === 200) {
      dataList.value = res.rows
      total.value = res.total
      
      // 如果有已选值，设置选中状态
      if (props.modelValue) {
        nextTick(() => {
          const row = dataList.value.find(item => item[getValueSourceKey()] == props.modelValue)
          if (row) {
            if (props.multiple) {
              // 多选模式：设置多选框选中状态
              tableRef.value?.toggleRowSelection(row, true)
            } else {
              // 单选模式：设置单选按钮选中状态
              selectedRadioValue.value = getRowKey(row);
              selectedRows.value = [row];
            }
          }
          // 载入数据后根据已选值刷新显示
          try {
            displayLabel.value = getLabelByValue(props.modelValue as any)
          } catch {}
        })
      }
    }
  } catch (error) {
    console.error('获取数据列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSelectionChange = (selection: any[]) => {
  selectedRows.value = selection
}

const handleRowClick = (row: any) => {
  if (!props.multiple) {
    // 单选模式：设置单选按钮的值并选中行
    selectedRadioValue.value = getRowKey(row);
    selectedRows.value = [row];
  }
};

const handleRadioChange = (row: any) => {
  if (!props.multiple) {
    // 单选模式：设置选中的行
    selectedRows.value = [row];
  }
};

const handleConfirm = () => {
  if (selectedRows.value.length === 0) return
  
  if (props.multiple) {
    // 多选模式：处理多条数据
    const key = getValueSourceKey()
    const labelKey = getLabelSourceKey()
    
    // 为每行数据创建映射后的数据
    const allMappedData = selectedRows.value.map(row => {
      const mappedData: any = {}
      
      // 应用字段映射
      if (props.mapping) {
        try {
          const mapping = typeof props.mapping === 'string' 
            ? JSON.parse(props.mapping) 
            : props.mapping
          
          Object.entries(mapping).forEach(([srcField, targetField]) => {
            if (typeof targetField === 'string' && row[srcField] !== undefined) {
              // 设置驼峰格式字段
              mappedData[targetField] = row[srcField]
              
              // 同时生成下划线格式字段名，用于表单显示
              const underscoreField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase()
              if (underscoreField !== targetField) {
                mappedData[underscoreField] = row[srcField]
              }
            }
          })
        } catch (error) {
          console.error('解析字段映射失败:', error)
        }
      }
      
      return {
        value: row[key],
        label: row[labelKey],
        ...mappedData,
        originalData: row
      }
    })
    
    
    // 发送多选数据
    emit('common-component', allMappedData)
    
    // 显示第一个选中项：依据显示模式
    if (allMappedData.length > 0) {
      displayLabel.value = props.displayMode === 'value' ? String(allMappedData[0].value) : String(allMappedData[0].label)
      userSelectedValue.value = allMappedData[0].value
    }
    
  } else {
    // 单选模式：处理单条数据（保持原有逻辑）
  const row = selectedRows.value[0]
  const key = getValueSourceKey()
  const labelKey = getLabelSourceKey()
  
    // 创建映射后的数据对象
    const mappedData: any = {}
    
    // 应用字段映射
  if (props.mapping) {
      try {
        const mapping = typeof props.mapping === 'string' 
          ? JSON.parse(props.mapping) 
          : props.mapping
        
        Object.entries(mapping).forEach(([srcField, targetField]) => {
          if (typeof targetField === 'string' && row[srcField] !== undefined) {
            // 设置驼峰格式字段
      mappedData[targetField] = row[srcField]
            
            // 同时生成下划线格式字段名，用于表单显示
            const underscoreField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase()
            if (underscoreField !== targetField) {
              mappedData[underscoreField] = row[srcField]
            }
          }
    })
      } catch (error) {
        console.error('解析字段映射失败:', error)
      }
    }
    
  
  emit('update:modelValue', row[key])
    // 传递映射后的数据，确保字段映射正确
    emit('common-component', { 
      value: row[key], 
      label: row[labelKey], 
      ...mappedData,  // 只包含映射后的字段
      // 添加原始数据，供调试使用
      originalData: row
    })
    
    // 依据显示模式设置展示值
    displayLabel.value = props.displayMode === 'value' ? String(row[key]) : String(row[labelKey])
    
    // 缓存用户选择的值，防止 watch 重新触发时覆盖
    userSelectedValue.value = row[key]
  }
  
  dialogVisible.value = false
}

const handlePageChange = (pagination: any) => {
  queryParams.value.pageNum = pagination.pageNum
  queryParams.value.pageSize = pagination.pageSize
  getList()
}
</script>

<style lang="scss" scoped>
.popup-table-container {
  width: 100%;
  
  .popup-input {
    width: 100%;
    
    :deep(.el-input-group__append) {
      padding: 0;
      
      .el-button {
        margin: 0;
        border: none;
        padding: 8px 15px;
        
        &:hover {
          background-color: #f5f7fa;
        }
      }
    }
  }
}

:deep(.el-dialog__body) {
  padding: 10px 20px;
}
</style>
