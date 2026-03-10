<template>
  <el-dialog :title="title" v-model="visible" width="80%" top="5vh" :before-close="handleClose">
    <!-- 单表预览 -->
    <div v-if="previewData.type === 'single'">
      <el-form label-width="120px">
        <el-row :gutter="20">
          <el-col
            v-for="field in previewData.mainFields"
            :key="field.columnName"
            :span="field.formSpan || 24"
            v-if="isMainVisible(field)"
          >
            <el-form-item :label="field.columnComment" :required="isMainRequired(field)">
              <component
                :is="getComponentType(field.htmlType)"
                v-model:value="previewData.mainForm[field.columnName]"
                :key="field.columnName"
                :nm="getMainRuntimeNm(field)"
                :disabled="isMainDisabled(field)"
                @common-component="(nm) => handleMainCommonComponent(nm, field)"
                @change="(val) => handleMainFieldChange(field, val)"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- 主子表预览 -->
    <div v-else-if="previewData.type === 'main-sub'">
      <!-- 主表部分 -->
      <el-card header="主表信息">
        <el-form label-width="120px">
          <el-row :gutter="20">
            <el-col
              v-for="field in previewData.mainFields"
              :key="field.columnName"
              :span="field.formSpan || 24"
              v-if="isMainVisible(field)"
            >
              <el-form-item :label="field.columnComment" :required="isMainRequired(field)">
                <component
                  :is="getComponentType(field.htmlType)"
                  v-model:value="previewData.mainForm[field.columnName]"
                  :key="field.columnName"
                  :nm="getMainRuntimeNm(field)"
                  :disabled="isMainDisabled(field)"
                  @common-component="(nm) => handleMainCommonComponent(nm, field)"
                  @change="(val) => handleMainFieldChange(field, val)"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- 子表部分 -->
      <el-card header="子表信息" class="mt-4">
        <div class="mb-4">
          <el-button type="primary" @click="addSubRow">+ 添加子项</el-button>
        </div>

        <el-table :data="previewData.subForms" border stripe class="sub-table">
          <el-table-column
            v-for="field in previewData.subFields"
            :key="field.columnName"
            :prop="field.columnName"
            :label="field.columnComment"
            min-width="180"
          >
            <template #default="{ row, $index }">
              <component
                v-if="isSubVisible(field, $index)"
                :is="getComponentType(field.htmlType)"
                v-model:value="row[field.columnName]"
                :nm="getSubRuntimeNm(field, row, $index)"
                :disabled="isSubDisabled(field, $index)"
                @common-component="(nm) => handleSubCommonComponent(nm, field, row)"
                @change="(val) => handleSubFieldChange(field, val, row)"
              />
              <span v-else>--</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" icon="Delete" @click="removeSubRow($index)" />
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <div v-else class="empty-state">
      <el-empty description="暂无表单数据" />
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useOnline } from '@/views/workflow/hooks/useOnline'
import {
  useOnlineRuntime,
  type UiState,
  type RuntimeLinkageState
} from '@/views/online/hooks/useOnlineRuntime'
import { addOrUpdateOnlineData } from '@/api/online'
import type { OnlineField, PreviewData } from './types'

const props = defineProps<{
  modelValue: boolean
  title?: string
  tableId: string | number
  tableName: string
  formData: any
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
  close: []
}>()

const { getComponentType } = useOnline()
const {
  safeParseJSON,
  applyCommonDataInfo,
  initAutoGenerateForForm,
  applyOnClear,
  applyOnSetValue,
  applyOnMainChange,
  handleMainToMain: handleMainToMainRuntime,
  applyOnRowAdd,
  computeUiStateWithEffects,
  validateBySchema,
  computeRuntimeLinkageState,
  applyOnSubChange,
  handleSubToSub: handleSubToSubRuntime,
  applyOnSubTrigger
} = useOnlineRuntime()

// 可见性
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const submitting = ref(false)

// 预览数据
const previewData = reactive<PreviewData>({
  type: null,
  mainFields: [],
  subFields: [],
  mainForm: {},
  subForms: [],
  foreignKey: '',
  mainTableId: null,
  mainTableName: '',
  subTableName: ''
})

// 运行时状态
const workflowCtx = computed<Record<string, any>>(() => ({}))

const emptyUiState = (): UiState => ({ visible: {}, disabled: {}, required: {} })
const emptyLinkageState = (): RuntimeLinkageState => ({
  dictOptions: {},
  popupParams: {},
  clearFields: []
})

const mainUiState = ref<UiState>(emptyUiState())
const mainLinkageState = ref<RuntimeLinkageState>(emptyLinkageState())
const subUiStates = ref<UiState[]>([])
const subLinkageStates = ref<RuntimeLinkageState[]>([])

// 监听 formData 变化，初始化预览数据
watch(
  () => props.formData,
  (formData) => {
    if (!formData) return

    // 清空旧数据
    previewData.type = null
    previewData.mainForm = {}
    previewData.subForms = []
    previewData.mainTableId = props.tableId
    previewData.mainTableName = props.tableName
    previewData.subTableName = ''
    previewData.foreignKey = ''

    // 判断表单类型
    if (formData.type === 'main-sub') {
      previewData.type = 'main-sub'
      previewData.mainFields = formData.main?.columnsVo || []
      previewData.subFields = formData.sub?.columnsVo || []
      previewData.foreignKey = formData.foreignKey || ''
      previewData.subTableName = formData.sub?.tableName || ''

      previewData.mainFields.forEach((f: OnlineField) => {
        if (f?.columnName) previewData.mainForm[f.columnName] = null
      })
      addSubRow()
      initAutoGenerateForForm(previewData.mainFields, previewData.mainForm)
    } else if (formData.type === 'single') {
      previewData.type = 'single'
      previewData.mainFields = formData.data?.columnsVo || []

      previewData.mainFields.forEach((f: OnlineField) => {
        if (f?.columnName) previewData.mainForm[f.columnName] = null
      })
      initAutoGenerateForForm(previewData.mainFields, previewData.mainForm)
    }

    refreshAllRuntime()
  },
  { immediate: true }
)

// 运行时刷新方法
const refreshMainRuntime = () => {
  const ctx = { ...(previewData.mainForm || {}), workflow: workflowCtx.value }
  const { ui, effects } = computeUiStateWithEffects(previewData.mainFields, ctx)
  mainUiState.value = ui
  effects.clearFields.forEach((k) => {
    if (k) (previewData.mainForm as any)[k] = null
  })

  const linkage = computeRuntimeLinkageState(previewData.mainFields, ctx)
  mainLinkageState.value = linkage
  linkage.clearFields.forEach((k) => {
    if (k) (previewData.mainForm as any)[k] = null
  })
}

const refreshSubRuntime = (rowIndex: number) => {
  const row = previewData.subForms?.[rowIndex]
  if (!row) return
  const ctx = { ...(previewData.mainForm || {}), ...(row || {}), workflow: workflowCtx.value }
  const { ui, effects } = computeUiStateWithEffects(previewData.subFields, ctx)
  subUiStates.value[rowIndex] = ui
  effects.clearFields.forEach((k) => {
    if (k) (row as any)[k] = null
  })

  const linkage = computeRuntimeLinkageState(previewData.subFields, ctx)
  subLinkageStates.value[rowIndex] = linkage
  linkage.clearFields.forEach((k) => {
    if (k) (row as any)[k] = null
  })
}

const refreshAllRuntime = () => {
  refreshMainRuntime()
  subUiStates.value = []
  subLinkageStates.value = []
  previewData.subForms.forEach((_, idx) => refreshSubRuntime(idx))
}

// 可见性/禁用/必填判断
const isMainVisible = (field: OnlineField) => {
  const k = field?.columnName
  if (!k) return true
  return mainUiState.value?.visible?.[k] !== false
}

const shouldDisableField = (field: OnlineField) => {
  try {
    const cfg: any = safeParseJSON(field?.onChange || '{}')
    return !!cfg?.onCodeGenerate?.autoGenerateRule
  } catch {
    return false
  }
}

const isMainDisabled = (field: OnlineField) => {
  const k = field?.columnName
  const runtimeDisabled = k ? mainUiState.value?.disabled?.[k] === true : false
  return shouldDisableField(field) || runtimeDisabled
}

const isMainRequired = (field: OnlineField) => {
  const k = field?.columnName
  return k ? mainUiState.value?.required?.[k] === true : false
}

const getMainRuntimeNm = (field: OnlineField) => {
  const k = field?.columnName
  if (!k) return field
  return {
    ...field,
    runtimeDictCode: mainLinkageState.value?.dictOptions?.[k],
    runtimePopupParams: mainLinkageState.value?.popupParams?.[k]
  }
}

const isSubVisible = (field: OnlineField, rowIndex: number) => {
  const k = field?.columnName
  if (!k) return true
  return subUiStates.value?.[rowIndex]?.visible?.[k] !== false
}

const isSubDisabled = (field: OnlineField, rowIndex: number) => {
  const k = field?.columnName
  return k ? subUiStates.value?.[rowIndex]?.disabled?.[k] === true : false
}

const getSubRuntimeNm = (field: OnlineField, row: any, rowIndex: number) => {
  const k = field?.columnName
  if (!k) return field
  return {
    ...field,
    runtimeDictCode: subLinkageStates.value?.[rowIndex]?.dictOptions?.[k],
    runtimePopupParams: subLinkageStates.value?.[rowIndex]?.popupParams?.[k]
  }
}

// 事件处理
const handleMainCommonComponent = (nm: any, field: OnlineField) => {
  applyCommonDataInfo(nm, previewData.mainForm)

  const ctx0 = { ...(previewData.mainForm || {}), workflow: workflowCtx.value }
  applyOnClear(field, ctx0, (k, v) => {
    (previewData.mainForm as any)[k] = v
  })
  applyOnSetValue(
    field,
    { ...(previewData.mainForm || {}), workflow: workflowCtx.value },
    (k, v) => {
      (previewData.mainForm as any)[k] = v
    }
  )
  applyOnMainChange(field, previewData.mainForm, (previewData.mainForm as any)?.[field?.columnName])

  refreshAllRuntime()
}

const handleSubCommonComponent = (nm: any, field: OnlineField, row: Record<string, any>) => {
  applyCommonDataInfo(nm, row)

  const idx = previewData.subForms.indexOf(row)
  const ctx0 = { ...(previewData.mainForm || {}), ...(row || {}), workflow: workflowCtx.value }
  applyOnClear(field, ctx0, (k, v) => {
    (row as any)[k] = v
  })
  applyOnSetValue(field, ctx0, (k, v) => {
    (row as any)[k] = v
  })

  applyOnSubChange(field, row, previewData.mainForm)
  applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm)
  if (idx >= 0) refreshSubRuntime(idx)
  refreshMainRuntime()
}

const handleMainFieldChange = (field: OnlineField, newValue: any) => {
  handleMainToMainRuntime(field, newValue, previewData.mainForm as any, workflowCtx.value)
  refreshAllRuntime()
}

const handleSubFieldChange = (field: OnlineField, newValue: any, row: Record<string, any>) => {
  const idx = previewData.subForms.indexOf(row)
  handleSubToSubRuntime(field, newValue, row as any, previewData.mainForm as any, workflowCtx.value)
  applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm)
  if (idx >= 0) refreshSubRuntime(idx)
  refreshMainRuntime()
}

// 子表行操作
const addSubRow = () => {
  const row = previewData.subFields.reduce((acc: any, cur: OnlineField) => {
    if (cur?.columnName) acc[cur.columnName] = null
    return acc
  }, {})
  applyOnRowAdd(previewData.subFields, row, previewData.mainForm as any, workflowCtx.value)
  previewData.subForms.push(row)
  applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm)
  refreshSubRuntime(previewData.subForms.length - 1)
  refreshMainRuntime()
}

const removeSubRow = (index: number) => {
  if (previewData.subForms.length > 1) {
    previewData.subForms.splice(index, 1)
    applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm)
    refreshAllRuntime()
  } else {
    ElMessage.warning('至少需要保留一个子项')
  }
}

// 提交
const handleSubmit = async () => {
  if (!props.tableId || !props.tableName) {
    ElMessage.error('缺少表信息，无法提交')
    return
  }

  submitting.value = true
  try {
    refreshAllRuntime()

    const mainErrors = validateBySchema(
      previewData.mainFields,
      { ...(previewData.mainForm || {}), workflow: workflowCtx.value },
      mainUiState.value
    )
    if (mainErrors.length) {
      ElMessage.error(mainErrors[0].message)
      return
    }

    if (previewData.type === 'main-sub') {
      const validSubs = previewData.subForms.filter((sub) =>
        Object.values(sub).some((v) => v !== null && v !== undefined && v !== '')
      )

      for (let i = 0; i < previewData.subForms.length; i++) {
        const row = previewData.subForms[i]
        const isEmptyRow = !Object.values(row || {}).some((v) => v !== null && v !== undefined && v !== '')
        if (isEmptyRow) continue
        const ui = subUiStates.value[i] || emptyUiState()
        const rowErrors = validateBySchema(
          previewData.subFields,
          { ...(previewData.mainForm || {}), ...(row || {}), workflow: workflowCtx.value },
          ui
        )
        if (rowErrors.length) {
          ElMessage.error(`子表第${i + 1}行：${rowErrors[0].message}`)
          return
        }
      }

      const payload = {
        main: previewData.mainForm,
        sub: {
          tableName: previewData.subTableName,
          data: validSubs
        }
      }
      await addOrUpdateOnlineData(props.tableId, props.tableName, payload)
    } else if (previewData.type === 'single') {
      await addOrUpdateOnlineData(props.tableId, props.tableName, previewData.mainForm)
    } else {
      ElMessage.error('未知表单类型，无法提交')
      return
    }

    ElMessage.success('操作成功')
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  visible.value = false
  emit('close')
}
</script>

<style lang="scss" scoped>
.mt-4 {
  margin-top: 1rem;
}

.mb-4 {
  margin-bottom: 1rem;
}

.sub-table {
  margin-top: 15px;

  :deep(.el-table__cell) {
    padding: 8px 0;
  }

  :deep(.cell) {
    padding: 0 8px;
  }
}

.empty-state {
  padding: 40px 0;
}
</style>
