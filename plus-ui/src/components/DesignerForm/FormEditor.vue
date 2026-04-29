<template>
  <div class="form-editor">
    <el-form :model="formData" :label-width="formConfig.labelWidth">
      <div v-for="(item, index) in formComponents" :key="index">
        <el-form-item :label="item.label" :required="item.required">
          <template v-if="item.type === 'input'">
            <el-input v-model="formData[item.field]" v-bind="item.props" @change="val => handleFieldChange('main', item, val)" />
          </template>
          <template v-else-if="item.type === 'textarea'">
            <el-input type="textarea" v-model="formData[item.field]" v-bind="item.props" @change="val => handleFieldChange('main', item, val)" />
          </template>
          <template v-else-if="item.type === 'editor'">
            <rich-editor
              v-model="formData[item.field]"
              v-bind="item.props"
              :placeholder="item.props.placeholder"
              :height="item.props.height"
              :min-height="item.props.minHeight"
              @change="val => handleFieldChange('main', item, val)"
            />
          </template>
          <template v-else-if="item.type === 'select' && item.props.dictType">
            <el-select v-model="formData[item.field]" v-bind="item.props">
              <el-option
                v-for="option in getComponentOptions(item)"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </template>
          <template v-else-if="item.type === 'radio' && item.props.dictType">
            <el-radio-group v-model="formData[item.field]" v-bind="item.props">
              <el-radio
                v-for="option in getComponentOptions(item)"
                :key="option.value"
                :label="option.value"
              >
                {{ option.label }}
              </el-radio>
            </el-radio-group>
          </template>
          <template v-else-if="item.type === 'checkbox'">
            <el-checkbox-group v-model="formData[item.field]" v-bind="item.props" @change="val => handleFieldChange('main', item, val)">
              <el-checkbox
                v-for="option in getComponentOptions(item)"
                :key="option.value"
                :label="option.value"
              >
                {{ option.label }}
              </el-checkbox>
            </el-checkbox-group>
          </template>
          <template v-else-if="item.type === 'subtable'">
            <el-table :data="subTableData[item.field]" border style="width: 100%">
              <el-table-column
                v-for="(column, colIndex) in item.subTableColumns"
                :key="colIndex"
                :prop="column.field"
                :label="column.label"
              >
                <template #default="scope">
                  <component
                    :is="getComponentByType(column.type)"
                    v-model="scope.row[column.field]"
                    v-bind="column.props"
                    @change="val => handleFieldChange('sub', column, val, colIndex)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="scope">
                  <el-button
                    size="small"
                    type="danger"
                    @click="handleDeleteSubTableRow(item, scope.$index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="subtable-actions">
              <el-button
                type="primary"
                size="small"
                @click="handleAddSubTableRow(item)"
              >
                添加行
              </el-button>
            </div>
          </template>
          <template v-else-if="item.type === 'popup'">
            <component
              :is="getComponentByType(item.type)"
              v-model="formData[item.field]"
              :nm="item"
              @common-component="commonComponent"
            />
          </template>
          <template v-else>
            <component
              :is="getComponentByType(item.type)"
              v-model="formData[item.field]"
              v-bind="item.props"
              @change="val => handleFieldChange('main', item, val)"
            />
          </template>
        </el-form-item>
      </div>
    </el-form>
    <div class="form-actions">
      <el-button type="primary" @click="handleSubmit">提交</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, watch } from 'vue'
import RichEditor from './components/RichEditor.vue'
import PopupTable from './components/PopupTable.vue'

interface FormComponent {
  id: string
  type: string
  label: string
  field: string
  props: Record<string, any>
  required?: boolean
  subTableColumns?: any[]
}

interface FormConfig {
  labelWidth: string
  labelPosition: string
  size: string
  formName: string
  formDesc: string
}

interface Props {
  formContent: string
  initialData?: Record<string, any>
  dictOptions?: Record<string, any[]>
}

const props = defineProps<Props>()
const emit = defineEmits(['submit', 'reset'])

const formConfig = reactive<FormConfig>({
  labelWidth: '100px',
  labelPosition: 'right',
  size: 'default',
  formName: '',
  formDesc: ''
})

const formComponents = ref<FormComponent[]>([])
const formData = ref<Record<string, any>>({})
const subTableData = ref<Record<string, any[]>>({})

// 解析表单内容
const parseFormContent = () => {
  try {
    const content = JSON.parse(props.formContent)
    if (content.formConfig) {
      Object.assign(formConfig, content.formConfig)
    }
    if (content.formComponents) {
      formComponents.value = content.formComponents
      // 初始化表单数据
      formComponents.value.forEach(component => {
        if (component.type === 'subtable') {
          subTableData.value[component.field] = []
        } else {
          formData.value[component.field] = ''
        }
      })
    }
    if (content.masterSubTableData) {
      subTableData.value = content.masterSubTableData
    }
    // 如果有初始数据，则填充
    if (props.initialData) {
      Object.assign(formData.value, props.initialData)
    }
  } catch (error) {
    console.error('解析表单内容失败:', error)
  }
}

// 获取组件类型
const getComponentByType = (type: string) => {
  switch (type) {
    case 'input':
      return 'el-input'
    case 'textarea':
      return 'el-input'
    case 'editor':
      return 'rich-editor'
    case 'select':
      return 'el-select'
    case 'checkbox':
      return 'el-checkbox'
    case 'radio':
      return 'el-radio'
    case 'date':
      return 'el-date-picker'
    case 'datetime':
      return 'el-date-picker'
    case 'time':
      return 'el-time-picker'
    case 'image-upload':
      return 'el-upload'
    case 'file-upload':
      return 'el-upload'
    case 'rate':
      return 'el-rate'
    case 'color-picker':
      return 'el-color-picker'
    case 'slider':
      return 'el-slider'
    case 'button':
      return 'el-button'
    case 'divider':
      return 'el-divider'
    case 'dept-select':
      return 'el-tree-select'
    case 'user-select':
      return 'el-select'
    case 'switch':
      return 'el-switch'
    default:
      return 'el-input'
  }
}

// 获取组件选项
const getComponentOptions = (component: FormComponent) => {
  if (component.props.dataSource === 'dict' && component.props.dictType) {
    return props.dictOptions?.[component.props.dictType] || []
  }
  return component.props.options || []
}

// 添加子表行
const handleAddSubTableRow = (component: FormComponent) => {
  if (!subTableData.value[component.field]) {
    subTableData.value[component.field] = []
  }
  
  const newRow: Record<string, any> = {}
  component.subTableColumns?.forEach(column => {
    newRow[column.field] = ''
  })
  
  subTableData.value[component.field].push(newRow)
}

// 删除子表行
const handleDeleteSubTableRow = (component: FormComponent, index: number) => {
  if (subTableData.value[component.field]) {
    subTableData.value[component.field].splice(index, 1)
  }
}

// 提交表单
const handleSubmit = () => {
  const submitData: any = { main: formData.value, sub: {} }

  // 只处理第一个子表
  const subTableField = Object.keys(subTableData.value)[0]
  if (subTableField) {
    // 找到子表的配置
    const subTableComponent = formComponents.value.find(c => c.field === subTableField)
    console.log("handleSubmit subTableComponent",subTableComponent)
    submitData.sub = {
      data: subTableData.value[subTableField],
      tableName: subTableComponent?.subTableName || ''
    }
  }

  emit('submit', submitData)
}

// 重置表单
const handleReset = () => {
  formData.value = {}
  subTableData.value = {}
  parseFormContent()
  emit('reset')
}

// 监听表单内容变化
watch(() => props.formContent, () => {
  parseFormContent()
}, { immediate: true })

// 监听初始数据变化
watch(() => props.initialData, () => {
  if (props.initialData) {
    Object.assign(formData.value, props.initialData)
  }
}, { deep: true })

// 字段联动处理
const handleFieldChange = (type: 'main' | 'sub', field: any, value: any, subIndex?: number) => {
  if (type === 'main') {
    handleMainToMain(field, value)
  }
  if (type === 'sub' && typeof subIndex === 'number') {
    handleSubToSub(field, value, subIndex)
    handleSubToMain()
  }
}

// 主表字段变化联动
const handleMainToMain = (changedField: any, newValue: any) => {
  try {
    if (!changedField?.onChange) return
    const onChange = typeof changedField.onChange === 'string' ? JSON.parse(changedField.onChange) : changedField.onChange
    if (!onChange?.onMainChange) return
    const onMainChange = onChange.onMainChange
    if (typeof onMainChange === 'function') {
      const updates = onMainChange(newValue, formData.value)
      Object.assign(formData.value, updates)
    }
    // 可扩展：支持表达式等
  } catch (e) {
    console.error('主表联动处理失败:', e)
  }
}

// 子表字段变化联动
const handleSubToSub = (changedField: any, newValue: any, subIndex: number) => {
  const subTableField = Object.keys(subTableData.value)[0]
  const row = subTableData.value[subTableField]?.[subIndex]
  try {
    if (!row || !changedField?.onChange) return
    const onChange = typeof changedField.onChange === 'string' ? JSON.parse(changedField.onChange) : changedField.onChange
    if (!onChange?.onSubChange) return
    const onSubChange = onChange.onSubChange
    if (typeof onSubChange === 'function') {
      const updates = onSubChange(newValue, row)
      Object.assign(row, updates)
      subTableData.value[subTableField].splice(subIndex, 1, { ...row })
    }
    // 可扩展：支持表达式等
  } catch (e) {
    console.error('子表联动处理失败:', e)
  }
}

// 子表变化影响主表（可根据需要扩展）
const handleSubToMain = () => {
  // 示例：可在此实现子表数据聚合到主表
}

// 弹窗选择回填
const commonComponent = (nm: any) => {
  // 这里nm为弹窗选择返回的数据对象
  if (!nm) return
  // 假设有dictFields、mapping、commonDataInfo等属性
  try {
    const dictFieldsObj = typeof nm.dictFields === 'string' ? JSON.parse(nm.dictFields) : nm.dictFields || {}
    const mappingObj = typeof nm.mapping === 'string' ? JSON.parse(nm.mapping) : nm.mapping || {}
    const commonDataInfo = nm.commonDataInfo || {}
    Object.entries(dictFieldsObj).forEach(([sourceField]) => {
      const targetField = mappingObj[sourceField] || sourceField
      if (commonDataInfo[sourceField] !== undefined) {
        formData.value[targetField] = commonDataInfo[sourceField]
      }
    })
  } catch (e) {
    console.error('popup字段映射错误:', e)
  }
}
</script>

<style lang="scss" scoped>
.form-editor {
  padding: 20px;
  
  .subtable-actions {
    margin-top: 10px;
    text-align: right;
  }
  
  .form-actions {
    margin-top: 20px;
    text-align: center;
  }
}
</style> 