<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="1000px"
    :before-close="handleClose"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="字典字段" name="dictFields">
        <div v-if="currentRow && currentRow.htmlType === 'popup'" class="popup-fields-config">
          <div v-if="!currentRow.dictType" style="color:#f56c6c;margin-bottom:10px;">
            请先在列表中为该字段选择对应的表（字典类型）。
          </div>
          <div v-else>
            <div class="popup-fields-scroll">
              <div
                v-for="col in popupColumns"
                :key="col.columnName"
                class="checkbox-item"
              >
                <el-checkbox 
                  v-model="selectedPopupFields[col.columnName]"
                  @change="updateJson"
                >
                  {{ col.columnName }}（{{ col.columnComment }}）
                </el-checkbox>
                <el-input 
                  v-if="selectedPopupFields[col.columnName]"
                  v-model="popupFieldTitles[col.columnName]"
                  placeholder="请输入标题，默认使用字段注释"
                  size="small"
                  style="width: 260px; margin-left: 10px;"
                  @input="updateJson"
                />
              </div>
            </div>
          </div>
        </div>
        
        <div v-else class="checkbox-list">
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.dictValue" 
              @change="updateJson"
            >
              dictValue（值字段）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.dictValue"
              v-model="fieldValues.dictValue" 
              placeholder="请输入字段名，如：value"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.dictLabel" 
              @change="updateJson"
            >
              dictLabel（显示字段）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.dictLabel"
              v-model="fieldValues.dictLabel" 
              placeholder="请输入字段名，如：label"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.orderField" 
              @change="updateJson"
            >
              orderField（排序字段）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.orderField"
              v-model="fieldValues.orderField" 
              placeholder="请输入字段名，如：orderNum"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.where" 
              @change="updateJson"
            >
              where（过滤条件）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.where"
              v-model="fieldValues.where" 
              placeholder="请输入条件，如：status = 1"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="字段映射" name="mapping">
        <div v-if="currentRow && currentRow.htmlType === 'popup'" class="mapping-table">
          <div class="mapping-header">
            <span>源字段（表字段）</span>
            <span>目标字段（当前表字段名）</span>
            <span>操作</span>
          </div>
          <div class="popup-mapping-scroll">
            <div
              v-for="(item, index) in popupMappingPairs"
              :key="index"
              class="mapping-item"
            >
              <el-select
                v-model="item.source"
                placeholder="选择表字段"
                filterable
                style="width: 220px;"
                @change="updateJson"
              >
                <el-option 
                  v-for="col in popupColumns" 
                  :key="col.columnName" 
                  :label="col.columnName + '（' + col.columnComment + '）'" 
                  :value="col.columnName" 
                />
              </el-select>
              <el-input
                v-model="item.target"
                placeholder="请输入当前表字段名"
                style="width: 220px;"
                @input="updateJson"
              />
              <el-button
                type="danger"
                size="small"
                :icon="Delete"
                @click="removePopupMappingPair(index)"
              />
            </div>
          </div>
          <el-button
            type="primary"
            size="small"
            :icon="Plus"
            @click="addPopupMappingPair"
            class="add-mapping-btn"
          >
            添加映射
          </el-button>

          <div class="popup-config">
            <div class="config-row">
              <label>显示模式：</label>
              <el-select
                v-model="popupDisplayMode"
                placeholder="选择显示模式"
                style="width: 200px;"
                @change="updateJson"
              >
                <el-option label="label" value="label" />
                <el-option label="value" value="value" />
                <el-option label="sub" value="sub" />
              </el-select>
            </div>
            <div class="config-row">
              <label>多选：</label>
              <el-switch v-model="popupMultiple" @change="updateJson" />
            </div>
          </div>
        </div>

        <div v-else class="checkbox-list">
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.field" 
              @change="updateJson"
            >
              field（字段映射）
            </el-checkbox>
            <div v-if="selectedOptions.field" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.fieldSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.fieldTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.fieldName" 
              @change="updateJson"
            >
              fieldName（字段名称映射）
            </el-checkbox>
            <div v-if="selectedOptions.fieldName" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.fieldNameSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.fieldNameTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.id" 
              @change="updateJson"
            >
              id（ID字段映射）
            </el-checkbox>
            <div v-if="selectedOptions.id" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.idSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.idTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.name" 
              @change="updateJson"
            >
              name（名称字段映射）
            </el-checkbox>
            <div v-if="selectedOptions.name" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.nameSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.nameTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="联动规则" name="onChange">
        <div class="onchange-editor">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="使用可视化配置生成 onChange JSON"
            class="mb-2"
          />
          <OnChangeRuleEditor
            v-if="currentRow"
            v-model="currentOnChange"
            :fields="whenFieldOptions"
            :targets="fieldOptions"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <div class="json-preview">
      <h4>JSON 预览</h4>
      <el-input
        v-model="jsonPreview"
        type="textarea"
        :rows="6"
        readonly
        class="json-textarea"
      />
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="FieldConfigDialog">
import { ref, computed, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import OnChangeRuleEditor from '@/views/online/edit/components/OnChangeRuleEditor.vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  configType: {
    type: String,
    default: 'dictFields'
  },
  currentRow: {
    type: Object,
    default: null
  },
  tables: {
    type: Array,
    default: () => []
  },
  columns: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:visible', 'update:config'])

const activeTab = ref('dictFields')
const localDictFields = ref('')
const localMapping = ref('')

const dialogTitle = computed(() => {
  const titles = {
    dictFields: '字典字段配置',
    mapping: '字段映射配置',
    onChange: '联动规则配置'
  }
  return titles[props.configType]
})

const fieldOptions = computed(() =>
  (props.columns || [])
    .filter(c => c?.columnName)
    .map(c => ({
      label: `${c.columnComment || c.columnName}（${c.columnName}）`,
      value: String(c.columnName)
    }))
)

const whenFieldOptions = computed(() => [
  ...fieldOptions.value,
  { label: '流程：节点（workflow.nodeKey）', value: 'workflow.nodeKey' },
  { label: '流程：只读（workflow.readonly）', value: 'workflow.readonly' }
])

const currentOnChange = ref('{}')

const popupColumns = ref([])
const selectedPopupFields = ref({})
const popupFieldTitles = ref({})
const popupMappingPairs = ref([])
const popupDisplayMode = ref('label')
const popupMultiple = ref(false)

const selectedOptions = ref({})
const fieldValues = ref({})

const jsonPreview = computed(() => {
  try {
    if (props.configType === 'dictFields') {
      if (props.currentRow && props.currentRow.htmlType === 'popup') {
        const result = {}
        Object.keys(selectedPopupFields.value).forEach(field => {
          if (selectedPopupFields.value[field]) {
            result[field] = popupFieldTitles.value[field] || field
          }
        })
        return JSON.stringify(result, null, 2)
      }
      const result = {}
      if (selectedOptions.value.dictValue) {
        result.dictValue = fieldValues.value.dictValue
      }
      if (selectedOptions.value.dictLabel) {
        result.dictLabel = fieldValues.value.dictLabel
      }
      if (selectedOptions.value.orderField) {
        result.orderField = fieldValues.value.orderField
      }
      if (selectedOptions.value.where) {
        result.where = fieldValues.value.where
      }
      return JSON.stringify(result, null, 2)
    } else if (props.configType === 'mapping') {
      if (props.currentRow && props.currentRow.htmlType === 'popup') {
        const mapping = {}
        popupMappingPairs.value.forEach(pair => {
          if (pair.source && pair.target) {
            mapping[pair.source] = pair.target
          }
        })
        const result = {
          mapping,
          popupConfig: {
            displayMode: popupDisplayMode.value,
            multiple: popupMultiple.value
          }
        }
        return JSON.stringify(result, null, 2)
      }
      const result = {}
      if (selectedOptions.value.field) {
        result[fieldValues.value.fieldSource] = fieldValues.value.fieldTarget
      }
      if (selectedOptions.value.fieldName) {
        result[fieldValues.value.fieldNameSource] = fieldValues.value.fieldNameTarget
      }
      if (selectedOptions.value.id) {
        result[fieldValues.value.idSource] = fieldValues.value.idTarget
      }
      if (selectedOptions.value.name) {
        result[fieldValues.value.nameSource] = fieldValues.value.nameTarget
      }
      return JSON.stringify(result, null, 2)
    } else if (props.configType === 'onChange') {
      const s = currentOnChange.value
      if (typeof s !== 'string' || !s.trim()) return '{}'
      try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s }
    }
    return '{}'
  } catch (error) {
    return '{}'
  }
})

const safeParseObject = (input) => {
  if (input == null) return {}
  if (typeof input === 'object' && !Array.isArray(input)) return input
  if (typeof input !== 'string') return {}
  const s = input.trim()
  if (!s) return {}
  try {
    const obj = JSON.parse(s)
    return (obj && typeof obj === 'object' && !Array.isArray(obj)) ? obj : {}
  } catch {
    return {}
  }
}

const initData = () => {
  if (props.configType === 'dictFields') {
    localDictFields.value = props.currentRow?.dictFields || '{}'
    if (props.currentRow && props.currentRow.htmlType === 'popup' && props.currentRow.dictType) {
      const selectedTable = props.tables.find(t => t.tableName === props.currentRow.dictType)
      popupColumns.value = (selectedTable?.columns || []).map(c => ({
        columnName: c.columnName,
        columnComment: c.columnComment
      }))
      selectedPopupFields.value = {}
      popupFieldTitles.value = {}
      let mapping = {}
      try { mapping = localDictFields.value ? JSON.parse(localDictFields.value) : {} } catch { mapping = {} }
      if (mapping && typeof mapping === 'object') {
        Object.keys(mapping).forEach(field => {
          selectedPopupFields.value[field] = true
          popupFieldTitles.value[field] = mapping[field]
        })
      }
    } else {
      let dictFields = {}
      try { dictFields = localDictFields.value ? JSON.parse(localDictFields.value) : {} } catch { dictFields = {} }
      selectedOptions.value = {
        dictValue: !!dictFields.dictValue,
        dictLabel: !!dictFields.dictLabel,
        orderField: !!dictFields.orderField,
        where: !!dictFields.where
      }
      fieldValues.value = {
        dictValue: dictFields.dictValue || 'value',
        dictLabel: dictFields.dictLabel || 'label',
        orderField: dictFields.orderField || 'orderNum',
        where: dictFields.where || 'status = 1'
      }
    }
  } else if (props.configType === 'mapping') {
    localMapping.value = props.currentRow?.mapping || '{}'
    if (props.currentRow && props.currentRow.htmlType === 'popup' && props.currentRow.dictType) {
      const selectedTable = props.tables.find(t => t.tableName === props.currentRow.dictType)
      popupColumns.value = (selectedTable?.columns || []).map(c => ({
        columnName: c.columnName,
        columnComment: c.columnComment
      }))
      popupMappingPairs.value = []
      popupDisplayMode.value = 'label'
      popupMultiple.value = false
      let mappingObj = {}
      try { mappingObj = localMapping.value ? JSON.parse(localMapping.value) : {} } catch { mappingObj = {} }
      if (mappingObj && typeof mappingObj === 'object') {
        if (mappingObj.mapping && typeof mappingObj.mapping === 'object') {
          Object.keys(mappingObj.mapping).forEach(key => {
            popupMappingPairs.value.push({ source: key, target: mappingObj.mapping[key] })
          })
        }
        if (mappingObj.popupConfig) {
          popupDisplayMode.value = mappingObj.popupConfig.displayMode || 'label'
          popupMultiple.value = !!mappingObj.popupConfig.multiple
        }
      }
      if (popupMappingPairs.value.length === 0) {
        popupMappingPairs.value.push({ source: '', target: '' })
      }
    } else {
      let mapping = {}
      try { mapping = localMapping.value ? JSON.parse(localMapping.value) : {} } catch { mapping = {} }
      selectedOptions.value = {
        field: !!mapping.field,
        fieldName: !!mapping.fieldName,
        id: !!mapping.id,
        name: !!mapping.name
      }
      fieldValues.value = {
        fieldSource: mapping.field || 'field',
        fieldTarget: mapping.field || 'field',
        fieldNameSource: mapping.fieldName || 'fieldName',
        fieldNameTarget: mapping.fieldName || 'fieldName',
        idSource: mapping.id || 'id',
        idTarget: mapping.id || 'id',
        nameSource: mapping.name || 'name',
        nameTarget: mapping.name || 'name'
      }
    }
  } else if (props.configType === 'onChange') {
    try {
      if (!props.currentRow.onChange || !String(props.currentRow.onChange).trim()) {
        currentOnChange.value = '{}'
      } else {
        currentOnChange.value = JSON.stringify(JSON.parse(props.currentRow.onChange), null, 2)
      }
    } catch {
      currentOnChange.value = '{}'
    }
  }
}

watch(() => props.visible, (v) => {
  if (v) {
    activeTab.value = props.configType
    initData()
  }
})

const updateJson = () => {
  if (props.configType === 'dictFields') {
    if (props.currentRow && props.currentRow.htmlType === 'popup') {
      const result = {}
      Object.keys(selectedPopupFields.value).forEach(field => {
        if (selectedPopupFields.value[field]) {
          result[field] = popupFieldTitles.value[field] || field
        }
      })
      localDictFields.value = JSON.stringify(result)
    } else {
      const result = {}
      if (selectedOptions.value.dictValue) {
        result.dictValue = fieldValues.value.dictValue
      }
      if (selectedOptions.value.dictLabel) {
        result.dictLabel = fieldValues.value.dictLabel
      }
      if (selectedOptions.value.orderField) {
        result.orderField = fieldValues.value.orderField
      }
      if (selectedOptions.value.where) {
        result.where = fieldValues.value.where
      }
      localDictFields.value = JSON.stringify(result)
    }
  } else if (props.configType === 'mapping') {
    if (props.currentRow && props.currentRow.htmlType === 'popup') {
      const mapping = {}
      popupMappingPairs.value.forEach(pair => {
        if (pair.source && pair.target) {
          mapping[pair.source] = pair.target
        }
      })
      const result = {
        mapping,
        popupConfig: {
          displayMode: popupDisplayMode.value,
          multiple: popupMultiple.value
        }
      }
      localMapping.value = JSON.stringify(result)
    } else {
      const result = {}
      if (selectedOptions.value.field) {
        result[fieldValues.value.fieldSource] = fieldValues.value.fieldTarget
      }
      if (selectedOptions.value.fieldName) {
        result[fieldValues.value.fieldNameSource] = fieldValues.value.fieldNameTarget
      }
      if (selectedOptions.value.id) {
        result[fieldValues.value.idSource] = fieldValues.value.idTarget
      }
      if (selectedOptions.value.name) {
        result[fieldValues.value.nameSource] = fieldValues.value.nameTarget
      }
      localMapping.value = JSON.stringify(result)
    }
  } else if (props.configType === 'onChange') {
    if (props.currentRow) {
      currentOnChange.value = currentOnChange.value
    }
  }
}

const addPopupMappingPair = () => {
  popupMappingPairs.value.push({ source: '', target: '' })
}

const removePopupMappingPair = (index) => {
  popupMappingPairs.value.splice(index, 1)
}

const handleClose = () => {
  emit('update:visible', false)
}

const handleConfirm = () => {
  const data = { ...props.currentRow }
  if (props.configType === 'dictFields') {
    data.dictFields = localDictFields.value
  } else if (props.configType === 'mapping') {
    data.mapping = localMapping.value
  } else if (props.configType === 'onChange') {
    data.onChange = currentOnChange.value
  }
  emit('update:config', {
    type: props.configType,
    data
  })
  emit('update:visible', false)
}
</script>

<style scoped>
.popup-fields-config {
  padding: 12px;
}

.popup-fields-scroll {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #dcdfe6;
  padding: 12px;
  border-radius: 4px;
}

.mapping-table {
  padding: 12px;
}

.mapping-header {
  display: flex;
  gap: 20px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 10px;
  font-weight: bold;
}

.mapping-item {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  align-items: center;
}

.popup-mapping-scroll {
  max-height: 300px;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  margin-bottom: 10px;
}

.add-mapping-btn {
  margin-top: 10px;
}

.popup-config {
  margin-top: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.config-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.config-row label {
  width: 100px;
  font-weight: bold;
}

.checkbox-list {
  padding: 12px;
}

.checkbox-item {
  margin-bottom: 15px;
  display: flex;
  align-items: center;
}

.mapping-inputs {
  display: flex;
  align-items: center;
}

.onchange-editor {
  padding: 12px;
}

.json-preview {
  padding: 12px;
  border-top: 1px solid #dcdfe6;
  margin-top: 20px;
}

.json-preview h4 {
  margin: 0 0 10px 0;
  font-size: 14px;
  font-weight: bold;
}

.json-textarea {
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.mb-2 {
  margin-bottom: 10px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
