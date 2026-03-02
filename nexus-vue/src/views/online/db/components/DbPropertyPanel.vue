<template>
  <div class="property-panel">
    <div class="panel-header">
      <h3>属性设置</h3>
    </div>
    
    <div class="panel-content">
      <!-- 表属性 -->
      <div v-if="selectedTable && !selectedField" class="table-properties">
        <h4>表属性</h4>
        <el-form :model="tableForm" label-width="80px">
          <el-form-item label="表名">
            <el-input v-model="tableForm.name" @blur="handleTableUpdate" />
          </el-form-item>
          <el-form-item label="注释">
            <el-input v-model="tableForm.comment" @blur="handleTableUpdate" />
          </el-form-item>
          <el-form-item label="引擎">
            <el-select v-model="tableForm.engine" @change="handleTableUpdate">
              <el-option label="InnoDB" value="InnoDB" />
              <el-option label="MyISAM" value="MyISAM" />
            </el-select>
          </el-form-item>
          <el-form-item label="字符集">
            <el-select v-model="tableForm.charset" @change="handleTableUpdate">
              <el-option label="utf8mb4" value="utf8mb4" />
              <el-option label="utf8" value="utf8" />
              <el-option label="latin1" value="latin1" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      
      <!-- 字段属性 -->
      <div v-else-if="selectedField" class="field-properties">
        <h4>字段属性</h4>
        <el-form :model="fieldForm" label-width="80px">
          <el-form-item label="字段名">
            <el-input v-model="fieldForm.name" @blur="handleFieldUpdate" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="fieldForm.type" @change="handleFieldUpdate">
              <el-option label="varchar" value="varchar" />
              <el-option label="int" value="int" />
              <el-option label="bigint" value="bigint" />
              <el-option label="decimal" value="decimal" />
              <el-option label="datetime" value="datetime" />
              <el-option label="text" value="text" />
            </el-select>
          </el-form-item>
          <el-form-item label="长度" v-if="showLength">
            <el-input-number v-model="fieldForm.length" @change="handleFieldUpdate" />
          </el-form-item>
          <el-form-item label="精度" v-if="showPrecision">
            <el-input-number v-model="fieldForm.precision" @change="handleFieldUpdate" />
          </el-form-item>
          <el-form-item label="小数位" v-if="showScale">
            <el-input-number v-model="fieldForm.scale" @change="handleFieldUpdate" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="fieldForm.defaultValue" @blur="handleFieldUpdate" />
          </el-form-item>
          <el-form-item label="注释">
            <el-input v-model="fieldForm.comment" @blur="handleFieldUpdate" />
          </el-form-item>
          <el-form-item label="约束">
            <el-checkbox v-model="fieldForm.isPrimaryKey" @change="handleFieldUpdate">
              主键
            </el-checkbox>
            <el-checkbox v-model="fieldForm.isNotNull" @change="handleFieldUpdate">
              非空
            </el-checkbox>
            <el-checkbox v-model="fieldForm.isUnique" @change="handleFieldUpdate">
              唯一
            </el-checkbox>
          </el-form-item>
        </el-form>
      </div>
      
      <!-- 空状态 -->
      <div v-else class="empty-state">
        <el-empty description="请选择一个表或字段" />
      </div>
    </div>
  </div>
</template>

<script setup name="DbPropertyPanel" lang="ts">
import { ref, watch, computed } from 'vue'

interface Table {
  id: string
  name: string
  comment: string
  engine: string
  charset: string
  fields: Field[]
}

interface Field {
  id: string
  name: string
  type: string
  length?: number
  precision?: number
  scale?: number
  defaultValue?: string
  comment: string
  isPrimaryKey: boolean
  isNotNull: boolean
  isUnique: boolean
}

const props = defineProps<{
  selectedTable?: Table | null
  selectedField?: Field | null
}>()

const emit = defineEmits<{
  updateTable: [tableId: string, updates: Partial<Table>]
  updateField: [fieldId: string, updates: Partial<Field>]
}>()

const tableForm = ref({
  name: '',
  comment: '',
  engine: 'InnoDB',
  charset: 'utf8mb4'
})

const fieldForm = ref({
  name: '',
  type: 'varchar',
  length: 255,
  precision: 10,
  scale: 2,
  defaultValue: '',
  comment: '',
  isPrimaryKey: false,
  isNotNull: false,
  isUnique: false
})

// 是否显示长度字段
const showLength = computed(() => {
  return ['varchar', 'char', 'text'].includes(fieldForm.value.type)
})

// 是否显示精度字段
const showPrecision = computed(() => {
  return ['decimal', 'float', 'double'].includes(fieldForm.value.type)
})

// 是否显示小数位字段
const showScale = computed(() => {
  return ['decimal'].includes(fieldForm.value.type)
})

// 监听选中的表变化
watch(() => props.selectedTable, (newTable) => {
  if (newTable) {
    tableForm.value = {
      name: newTable.name,
      comment: newTable.comment,
      engine: newTable.engine,
      charset: newTable.charset
    }
  }
}, { immediate: true })

// 监听选中的字段变化
watch(() => props.selectedField, (newField) => {
  if (newField) {
    fieldForm.value = {
      name: newField.name,
      type: newField.type,
      length: newField.length || 255,
      precision: newField.precision || 10,
      scale: newField.scale || 2,
      defaultValue: newField.defaultValue || '',
      comment: newField.comment,
      isPrimaryKey: newField.isPrimaryKey,
      isNotNull: newField.isNotNull,
      isUnique: newField.isUnique
    }
  }
}, { immediate: true })

// 更新表属性
const handleTableUpdate = () => {
  if (props.selectedTable) {
    emit('updateTable', props.selectedTable.id, { ...tableForm.value })
  }
}

// 更新字段属性
const handleFieldUpdate = () => {
  if (props.selectedField) {
    emit('updateField', props.selectedField.id, { ...fieldForm.value })
  }
}
</script>

<style lang="scss" scoped>
.property-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 500;
  }
}

.panel-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.table-properties,
.field-properties {
  h4 {
    margin: 0 0 16px 0;
    font-size: 14px;
    font-weight: 500;
    color: #606266;
  }
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

:deep(.el-form) {
  .el-form-item {
    margin-bottom: 16px;
  }
  
  .el-form-item__label {
    font-size: 12px;
    color: #606266;
  }
  
  .el-checkbox {
    margin-right: 16px;
  }
}
</style>
