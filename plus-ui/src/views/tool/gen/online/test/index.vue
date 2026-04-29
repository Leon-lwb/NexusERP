<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div class="mb-[10px]" v-show="showSearch">
        <el-card shadow="hover">
          <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
            <!-- 循环遍历对象 -->
            <template v-for="(itemField, indexField) in onlineField" >
              <el-form-item  v-if="itemField && itemField.query"
              :label="itemField.columnComment"  :prop="itemField.columnName" >
                <template v-if="itemField.htmlType == 'input'">
                  <el-input v-model="queryParams.params[itemField.columnName]" :placeholder="itemField.columnComment" clearable style="width: 200px" @keyup.enter="handleQuery" />
                </template>
                <template v-if="itemField.commonDictCode != null && itemField.htmlType == 'select'">
                  <el-select v-model="queryParams.params[itemField.columnName]" :placeholder="itemField.columnComment" clearable style="width: 200px">
                    <el-option v-for="dict in itemField.commonDictCode" :key="dict.id" :label="dict.name" :value="dict.id" />
                  </el-select>
                </template>
              </el-form-item>
            </template>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>

    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['system:notice:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate(row)" v-hasPermi="['system:notice:edit']"
              >修改</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['system:notice:remove']">
              删除
            </el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="onlineList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <!-- 循环遍历对象 -->
          <template v-for="(itemField, indexField) in onlineField" :key="itemField.columnId || indexField">
                <el-table-column
                  v-if="itemField?.list"
                  :label="itemField.columnComment"
                  align="center"
                >
                  <template #default="scope">
                    <!-- 处理字典值 -->
                    <template v-if="itemField.commonDictCode && itemField.commonDictCode.length > 0">
                      <DispTag
                        :options="itemField.commonDictCode"
                        :value="String(scope.row[itemField.columnName] ?? '')"
                      />
                    </template>
                  <!-- popup 选择：显示名称（优先缓存映射，其次 *_Name 字段，最后回退原值） -->
                  <template v-else-if="itemField.htmlType === 'popup'">
                    {{ getPopupDisplay(scope.row, itemField) }}
                  </template>
                  <!-- 普通值 -->
                  <template v-else>
                    {{ scope.row[itemField.columnName] }}
                  </template>
                </template>
              </el-table-column>
            </template>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip content="修改" placement="top">
                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:notice:edit']"></el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:notice:remove']"></el-button>
              </el-tooltip>
            </template>
          </el-table-column>

      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
    <!-- 添加或修改在线表单对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" :width="dialogWidth" append-to-body>
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="200px">
        <!-- 单表模式 -->
        <template v-if="formType === 'single'">
          <el-row>
            <el-col :span="24" v-for="(itemField, indexField) in onlineField" :key="indexField">
              <el-form-item
                :label="itemField.columnComment"
                :required="isMainRequired(itemField)"
                v-if="(itemField.insert && itemField.edit) && !itemField.pk && isMainVisible(itemField)"
              >
                <component :is="getComponentType(itemField.htmlType)"
                  v-model:value="formData.single[itemField.columnName]"
                  :nm="getMainRuntimeNm(itemField)"
                  :disabled="isMainDisabled(itemField)"
                  @common-component="commonComponent"
                  @change="(val) => handleFieldChange('main', itemField, val)"/>
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- 主子表模式 -->
        <template v-else-if="formType === 'main-sub'">
          <!-- 主表字段 -->
          <el-row>
            <el-col :span="24" v-for="(itemField, indexField) in mainFields" :key="indexField">
              <el-form-item
                :label="itemField.columnComment"
                :required="isMainRequired(itemField)"
                v-if="(itemField.insert && itemField.edit) && !itemField.pk && isMainVisible(itemField)"
              >
                <component :is="getComponentType(itemField.htmlType)"
                  v-model:value="formData.main[itemField.columnName]"
                  :nm="getMainRuntimeNm(itemField)"
                  :disabled="isMainDisabled(itemField)"
                  @common-component="commonComponent"
                  @change="(val) => handleFieldChange('main', itemField, val)"/>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 单个子表区域 -->
          <el-card class="mb-4" shadow="never">
            <template #header>
              <div class="flex justify-between items-center">
                <span>{{ subTable.tableComment }}</span>
                <el-button type="primary" size="small" icon="Plus"
                  @click="handleAddSub">添加</el-button>
              </div>
            </template>

            <el-table :data="formData.sub" border>
              <el-table-column type="index" width="50" align="center" />
              <template v-for="(subField, subIndex) in subFields" :key="subIndex">
                <el-table-column :prop="subField.columnName" :label="subField.columnComment" align="center">
                  <template #default="{ row, $index }">
                    <component
                      v-if="isSubVisible(subField, $index)"
                      :is="getComponentType(subField.htmlType)"
                      v-model:value="row[subField.columnName]"
                      :nm="getSubRuntimeNm(subField, row, $index)"
                      :disabled="isSubDisabled(subField, $index)"
                      @common-component="(data) => handleSubComponent(data, $index)"
                      @change="(val) => handleFieldChange('sub', subField, val, $index)"
                      style="width: 100%"
                    />
                    <span v-else>--</span>
                  </template>
                </el-table-column>
              </template>
              <el-table-column label="操作" width="120" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" icon="Delete"
                    @click="handleDeleteSub($index)"></el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="OnlineTest" lang="ts">
import { listOnlineData, getOnlineData, delOnlineData, addOrUpdateOnlineData, getOnlineForm } from "@/api/online";
import DispTag from "../components/DispTag/index.vue";
import { useFlowable } from '@/views/workflow/hooks/useFlowable';
import { useOnline } from '@/views/workflow/hooks/useOnline'
import { ref, reactive, toRefs, onMounted, watch, computed, getCurrentInstance, ComponentInternalInstance } from 'vue'
import { useRoute } from 'vue-router'
import { listTableData } from "@/api/online";
import { useOnlineRuntime, type UiState, type RuntimeLinkageState } from '@/composables/online/useOnlineRuntime'

// 通过 props 接收路由参数
const props = defineProps<{
  tableId: string;
  tableName: string;
}>();

const route = useRoute();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { getFormComponent } = useFlowable();
const { getComponentType } = useOnline()
const {
  safeParseJSON,
  initAutoGenerateForForm,
  applyCommonDataInfo,
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
  applyOnSubTrigger,
} = useOnlineRuntime()

const onlineList = ref<any>([]);
const onlineField = ref<any>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string | number>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();


const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const queryParams = reactive<any>({
    pageNum: 1,
    pageSize: 10,
    params: {}
})

const form = reactive<any>({
  data: {}
});

const rules = reactive<any>({});

const tableName = ref('');
const tableId = ref(0);
// 在组件中新增主键字段名变量
const pkName = ref('');
const formType = ref<any>('');
const mainFields = ref<any[]>([])
const subFields = ref<any[]>([])
const subTable = ref<any>({})
const dialogWidth = computed(() => formType.value === 'main-sub' ? '1200px' : '880px');
// 表单数据结构
const formData = reactive({
  single: {} as Record<string, any>,    // 单表数据
  main: {} as Record<string, any>,      // 主表数据
  sub: [] as any[]                      // 子表数据
})

// workflow context (for linkage rules: workflow.xxx)
const workflowCtx = computed<Record<string, any>>(() => {
  const q: any = route.query || {}
  return { ...q }
})

const isWorkflowReadonly = computed(() => {
  const v: any = (route.query as any)?.readonly
  return v === '1' || v === 'true' || v === true
})

const emptyUiState = (): UiState => ({ visible: {}, disabled: {}, required: {} })
const emptyLinkageState = (): RuntimeLinkageState => ({ dictOptions: {}, popupParams: {}, clearFields: [] })

const mainUiState = ref<UiState>(emptyUiState())
const mainLinkageState = ref<RuntimeLinkageState>(emptyLinkageState())
const subUiStates = ref<UiState[]>([])
const subLinkageStates = ref<RuntimeLinkageState[]>([])

const getMainModel = () => (formType.value === 'single' ? formData.single : formData.main)
const getMainFieldsForRuntime = () => (formType.value === 'single' ? onlineField.value : mainFields.value)

const refreshMainRuntime = () => {
  const fields = getMainFieldsForRuntime()
  const model = getMainModel()
  const ctx = { ...(model || {}), workflow: workflowCtx.value }

  const { ui, effects } = computeUiStateWithEffects(fields, ctx)
  mainUiState.value = ui
  effects.clearFields.forEach((k) => { if (k && model) model[k] = null })

  const linkage = computeRuntimeLinkageState(fields, { ...(model || {}), workflow: workflowCtx.value })
  mainLinkageState.value = linkage
  linkage.clearFields.forEach((k) => { if (k && model) model[k] = null })
}

const refreshSubRuntime = (rowIndex: number) => {
  if (formType.value !== 'main-sub') return
  const row = formData.sub?.[rowIndex]
  if (!row) return
  const ctx = { ...(getMainModel() || {}), ...(row || {}), workflow: workflowCtx.value }

  const { ui, effects } = computeUiStateWithEffects(subFields.value, ctx)
  subUiStates.value[rowIndex] = ui
  effects.clearFields.forEach((k) => { if (k) row[k] = null })

  const linkage = computeRuntimeLinkageState(subFields.value, ctx)
  subLinkageStates.value[rowIndex] = linkage
  linkage.clearFields.forEach((k) => { if (k) row[k] = null })
}

const refreshAllRuntime = () => {
  refreshMainRuntime()
  if (formType.value === 'main-sub') {
    subUiStates.value = []
    subLinkageStates.value = []
    formData.sub.forEach((_, idx) => refreshSubRuntime(idx))
  }
}

const isMainVisible = (field: any) => {
  const k = field?.columnName
  if (!k) return true
  return mainUiState.value?.visible?.[k] !== false
}

const isMainDisabled = (field: any) => {
  const k = field?.columnName
  const runtimeDisabled = k ? mainUiState.value?.disabled?.[k] === true : false
  return isWorkflowReadonly.value || shouldDisableField(field) || runtimeDisabled
}

const isMainRequired = (field: any) => {
  const k = field?.columnName
  if (!k) return false
  return mainUiState.value?.required?.[k] === true
}

const getMainRuntimeNm = (field: any) => {
  const k = field?.columnName
  if (!k) return field
  return {
    ...field,
    // 优先使用运行时字典，如果没有则使用字段配置中的字典
    runtimeDictCode: mainLinkageState.value?.dictOptions?.[k] || field?.commonDictCode,
    runtimePopupParams: mainLinkageState.value?.popupParams?.[k],
  }
}

const isSubVisible = (field: any, rowIndex: number) => {
  const k = field?.columnName
  if (!k) return true
  return subUiStates.value?.[rowIndex]?.visible?.[k] !== false
}

const isSubDisabled = (field: any, rowIndex: number) => {
  const k = field?.columnName
  const runtimeDisabled = k ? subUiStates.value?.[rowIndex]?.disabled?.[k] === true : false
  return isWorkflowReadonly.value || runtimeDisabled
}

const getSubRuntimeNm = (field: any, row: any, rowIndex: number) => {
  const k = field?.columnName
  if (!k) return field
  return {
    ...field,
    // 优先使用运行时字典，如果没有则使用字段配置中的字典
    runtimeDictCode: subLinkageStates.value?.[rowIndex]?.dictOptions?.[k] || field?.commonDictCode,
    runtimePopupParams: subLinkageStates.value?.[rowIndex]?.popupParams?.[k],
  }
}

const seqCache = new Map()

const popupDictState = reactive<Record<string, Record<string, string>>>({})

const ensureDictLoaded = async (dictTable: string, valueKey: string, labelKey: string) => {
  const cacheKey = `${dictTable}:${valueKey}:${labelKey}`
  if (popupDictState[cacheKey]) return popupDictState[cacheKey]
  try {
    const res = await listTableData(dictTable, { pageNum: 1, pageSize: 1000, params: {} } as any)
    const map: Record<string, string> = {}
    const rows: any[] = (res as any).rows || []
    rows.forEach((row: any) => {
      if (row?.[valueKey] != null) map[String(row[valueKey])] = String(row[labelKey] ?? '')
    })
    popupDictState[cacheKey] = map
    return map
  } catch {
    return {}
  }
}

const getFromRow = (row: any, key?: string) => {
  if (!row || !key) return undefined
  if (row[key] !== undefined && row[key] !== null && row[key] !== '') return row[key]
  if (row.main && row.main[key] !== undefined && row.main[key] !== null && row.main[key] !== '') return row.main[key]
  if (row.data && row.data[key] !== undefined && row.data[key] !== null && row.data[key] !== '') return row.data[key]
  return undefined
}

const getPopupDisplay = (row: any, itemField: any) => {
  // 安全取值（兼容 main/sub）
  const valOf = (r: any, k?: string) => {
    if (!r || !k) return undefined
    if (r[k] !== undefined && r[k] !== null && r[k] !== '') return r[k]
    if (r.main && r.main[k] !== undefined && r.main[k] !== null && r.main[k] !== '') return r.main[k]
    return undefined
  }

  // 解析 mapping（支持字符串/对象，以及 {mapping:{...}} 结构）
  let mappingCfg: any = itemField?.mapping
  if (typeof mappingCfg === 'string') {
    try { mappingCfg = JSON.parse(mappingCfg) } catch { mappingCfg = {} }
  }
  const innerMap: any = (mappingCfg && typeof mappingCfg === 'object') ? (mappingCfg.mapping || mappingCfg) : {}
  const nameTarget: string | undefined = typeof innerMap?.name === 'string' ? innerMap.name : undefined
  const idTarget: string | undefined = typeof innerMap?.id === 'string' ? innerMap.id : undefined

  // 1) 显示名称：优先使用 mapping.name（如 customerName）
  const byMappingName = valOf(row, nameTarget)
  if (byMappingName !== undefined) return byMappingName

  // 2) 次之：使用 `${columnName}Name`（如 customer_idName）
  const columnNameName = `${itemField?.columnName || ''}Name`
  const byColumnNameName = valOf(row, columnNameName)
  if (byColumnNameName !== undefined) return byColumnNameName

  // 3) 再次回退：mapping.id 对应字段
  const byMappingId = valOf(row, idTarget)
  if (byMappingId !== undefined) return byMappingId

  // 4) 最终回退：原始 columnName 对应 id
  return valOf(row, itemField?.columnName) ?? ''
}

const parseDictFieldKeys = (dictFields: any): { valueKey: string, labelKey: string } => {
  try {
    const obj = typeof dictFields === 'string' ? JSON.parse(dictFields) : (dictFields || {})
    const keys = Object.keys(obj)
    return { valueKey: keys[0] || 'id', labelKey: keys[1] || 'name' }
  } catch {
    return { valueKey: 'id', labelKey: 'name' }
  }
}

const injectPopupLabels = async (rows: any[], cols: any[]) => {
  if (!Array.isArray(rows) || !Array.isArray(cols)) return
  const popupCols = cols.filter((c: any) => c?.htmlType === 'popup' && c?.dictType)
  for (const col of popupCols) {
    // 解析映射与字典源字段
    let mappingCfg: any = col?.mapping
    if (typeof mappingCfg === 'string') { try { mappingCfg = JSON.parse(mappingCfg) } catch { mappingCfg = {} } }
    const innerMap: any = (mappingCfg && typeof mappingCfg === 'object') ? (mappingCfg.mapping || mappingCfg) : {}
    const nameTarget: string | undefined = typeof innerMap?.name === 'string' ? innerMap.name : undefined

    const dictMap = await fetchDictMap(col.dictType, col.dictFields)
    const idKey = col?.columnName || col?.javaField
    const labelKeyFallback = `${col?.columnName || ''}Name`

    for (const row of rows) {
      const idVal = (row && idKey) ? (row[idKey] ?? (row.main ? row.main[idKey] : undefined)) : undefined
      if (idVal !== undefined && idVal !== null && idVal !== '') {
        const nameVal = dictMap[String(idVal)]
        if (nameVal !== undefined) {
          if (nameTarget) row[nameTarget] = nameVal
          if (labelKeyFallback) row[labelKeyFallback] = nameVal
        }
      }
    }
  }
}

// 从列配置提取字典源字段键（优先 mapping.id/name，其次 dictFields 的两个键）
const getValueLabelKeysFromCol = (col: any): { valueKey: string, labelKey: string } => {
  try {
    // 优先 mapping
    let mappingCfg: any = col?.mapping
    if (typeof mappingCfg === 'string') { try { mappingCfg = JSON.parse(mappingCfg) } catch { mappingCfg = {} } }
    const innerMap: any = (mappingCfg && typeof mappingCfg === 'object') ? (mappingCfg.mapping || mappingCfg) : {}
    const idKey = typeof innerMap?.id === 'string' ? innerMap.id : undefined
    const nameKey = typeof innerMap?.name === 'string' ? innerMap.name : undefined
    if (idKey && nameKey) return { valueKey: idKey, labelKey: nameKey }

    // 其次 dictFields（对象键）
    const obj = typeof col?.dictFields === 'string' ? JSON.parse(col.dictFields) : (col?.dictFields || {})
    const keys = Object.keys(obj)
    if (keys.length >= 2) return { valueKey: keys[0], labelKey: keys[1] }
  } catch {}
  return { valueKey: 'id', labelKey: 'name' }
}

const fetchDictMap = async (dictType: string, dictFields: any) => {
  try {
    const obj = typeof dictFields === 'string' ? JSON.parse(dictFields) : (dictFields || {})
    const keys = Object.keys(obj)
    const valueKey = keys[0] || 'id'
    const labelKey = keys[1] || 'name'
    const res = await listTableData(dictType, { pageNum: 1, pageSize: 1000, params: {} } as any)
    const map: Record<string, string> = {}
    const rows: any[] = (res as any)?.rows || []
    rows.forEach((r: any) => {
      if (r?.[valueKey] != null) map[String(r[valueKey])] = String(r[labelKey] ?? '')
    })
    return map
  } catch {
    return {}
  }
}

/** 查询online列表 */
const getList = async () => {
  loading.value = true;
  const res: any = await listOnlineData(tableId.value, tableName.value, queryParams);
  formType.value = res.map.type;

  // 预加载 popup 字段的字典映射，确保首屏显示名称
  const preloadPopupDicts = async (cols: any[]) => {
    const popupCols = (cols || []).filter((c: any) => c?.htmlType === 'popup' && c?.dictType)
    for (const col of popupCols) {
      const { valueKey, labelKey } = parseDictFieldKeys(col.dictFields)
      await ensureDictLoaded(col.dictType, valueKey, labelKey)
    }
  }

  if (formType.value === 'single' ) {
    // 列与行先做安全清理
    const safeColumns = sanitizeColumns(res.map?.data?.columnsVo || []);
    const safeRows = (res.rows || []).map(sanitizeRow);

    // 名称注入后再渲染
    await applyPopupLabelMapping(safeRows, safeColumns);

    // 按原始返回顺序渲染字段
    onlineField.value = safeColumns.map((item: any) => {
      const newItem = reactive({ ...item });
      if (newItem.htmlType === 'popup') {
        newItem.showDisplay = true;
      }
      return newItem;
    });

    onlineList.value = safeRows;
    formData.single = safeRows[0] || {}
  }
  else if (formType.value === 'main-sub') {
    // 列与行先做安全清理
    const safeMainColumns = sanitizeColumns(res.map.main.columnsVo || []);
    const safeSubColumns = sanitizeColumns(res.map.sub.columnsVo || []);
    const safeRows = (res.rows || []).map(sanitizeRow);

    // 名称注入（主表列）
    await applyPopupLabelMapping(safeRows, safeMainColumns)

    onlineField.value = safeMainColumns;
    onlineList.value = safeRows;
    mainFields.value = safeMainColumns;
    //处理是否显示名称
    mainFields.value = safeMainColumns.map((item: any) => {
      const newItem = reactive({ ...item });
      if (newItem.htmlType === 'popup') {
        newItem.showDisplay = true;
      }
      return newItem;
    });
    console.log("getList mainFields.value",mainFields.value)
    //处理是否显示名称
    subFields.value = safeSubColumns.map((item: any) => {
      const newItem = reactive({ ...item });
      if (newItem.htmlType === 'popup') {
        newItem.showDisplay = false; // 直接操作响应式对象
      }
      return newItem;
    });
    console.log("getList subFields.value",subFields.value)
    subTable.value = { ...res.map.sub, columnsVo: safeSubColumns }
    formData.main = safeRows[0]?.main || {}
    formData.sub = safeRows[0]?.[subTable.value.tableName] || []
  }
  // 查找主键字段
  const pkField = onlineField.value.find(field => field.pk)?.columnName;
  pkName.value = pkField || 'id'; // 默认使用'id'作为主键名
  total.value = res.total;
  loading.value = false;
  enhanceFields();
  injectPopupLabels(onlineList.value, onlineField.value);
}

// 在获取字段配置后处理联动规则（mainFields和subFields的加工处理）
const enhanceFields = () => {
  // 主表字段增强
  mainFields.value = mainFields.value.map(field => {
    const rules: any = {}

    // 汇率变化时更新总金额
    if (field.columnName === 'rate') {
      rules.onMainChange = (newValue: number, mainData: any) => {
        return {
          total: mainData.amount * newValue
        }
      }
    }

    // 金额变化时更新总金额（需要汇率）
    if (field.columnName === 'amount') {
      rules.onMainChange = (newValue: number, mainData: any) => {
        return {
          total: newValue * (mainData.rate || 1)
        }
      }
    }

    return {...field, ...rules}
  })

  // 子表字段增强
  subFields.value = subFields.value.map(field => {
    const rules: any = {}

    // 单价变化时更新小计
    if (field.columnName === 'price') {
      rules.onSubChange = (newValue: number, subData: any) => {
        return {
          subtotal: newValue * (subData.quantity || 1)
        }
      }
    }

    // 数量变化时更新小计
    if (field.columnName === 'quantity') {
      rules.onSubChange = (newValue: number, subData: any) => {
        return {
          subtotal: (subData.price || 0) * newValue
        }
      }
    }

    return {...field, ...rules}
  })
}
/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
}
/** 表单重置 */
const reset = () => {
  formRef.value?.resetFields()
  if (formType.value === 'single') {
    formData.single = {}
  } else {
    formData.main = {}
    formData.sub = []
  }
}
/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNum = 1;
  console.log("handleQuery queryParams",queryParams)
  getList();
}
/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
}
/** 多选框选中数据 */
const handleSelectionChange = (selection: any) => {
  ids.value = selection.map(item => item[pkName.value]); // 使用动态主键字段
  single.value = selection.length !== 1;
  multiple.value = selection.length === 0;
}
/** 新增按钮操作 */
const handleAdd = async () => {
  reset();
  
  // 如果字段配置未加载，先获取字段配置
  if (!onlineField.value || onlineField.value.length === 0) {
    console.log("handleAdd - 字段配置未加载，先获取字段配置")
    const formRes = await getOnlineForm(tableId.value)
    console.log("handleAdd - getOnlineForm res", formRes)
    
    if (formRes.data && formRes.data.map) {
      const map = formRes.data.map
      formType.value = map.type || 'single'
      
      if (formType.value === 'single') {
        const safeColumns = sanitizeColumns(map.data?.columnsVo || [])
        onlineField.value = safeColumns
        mainFields.value = safeColumns
      } else if (formType.value === 'main-sub') {
        const safeMainColumns = sanitizeColumns(map.main?.columnsVo || [])
        const safeSubColumns = sanitizeColumns(map.sub?.columnsVo || [])
        onlineField.value = safeMainColumns
        mainFields.value = safeMainColumns
        subFields.value = safeSubColumns
        subTable.value = { ...map.sub, columnsVo: safeSubColumns }
      }
      
      // 查找主键字段
      const pkFieldFromConfig = onlineField.value.find((field: any) => field.pk)?.columnName
      pkName.value = pkFieldFromConfig || 'id'
      console.log("handleAdd - 从配置中获取的主键字段:", pkName.value)
    }
  }
  
  dialog.visible = true;
  dialog.title = "添加";
  // 自动生成业务编号（按当前表单模式）
  initAutoGenerateForForm(getMainFieldsForRuntime(), getMainModel())
  refreshAllRuntime()
}

/** 判断是否禁用字段 */
const shouldDisableField = (field: any) => {
  try {
    const config = safeParseJSON(field.onChange || '{}')
    if (!config.onCodeGenerate) return false
    const onCodeGenerate = config.onCodeGenerate
    return !!onCodeGenerate.autoGenerateRule
  } catch {
    return false
  }
}

// generateBusinessNo 已抽到 useOnlineRuntime（此处无需重复实现）

/**修改按钮操作 */
const handleUpdate = async (row?: any) => {
  const pkField = pkName.value
  console.log("handleUpdate row",row)
  console.log("handleUpdate pkField",pkField)
  const onlineId = row ? row[pkField] : ids.value[0]

  // 如果字段配置未加载，先获取字段配置
  if (!onlineField.value || onlineField.value.length === 0) {
    console.log("handleUpdate - 字段配置未加载，先获取字段配置")
    const formRes = await getOnlineForm(tableId.value)
    console.log("handleUpdate - getOnlineForm res", formRes)
    
    if (formRes.data && formRes.data.map) {
      const map = formRes.data.map
      formType.value = map.type || 'single'
      
      if (formType.value === 'single') {
        const safeColumns = sanitizeColumns(map.data?.columnsVo || [])
        onlineField.value = safeColumns
        mainFields.value = safeColumns
      } else if (formType.value === 'main-sub') {
        const safeMainColumns = sanitizeColumns(map.main?.columnsVo || [])
        const safeSubColumns = sanitizeColumns(map.sub?.columnsVo || [])
        onlineField.value = safeMainColumns
        mainFields.value = safeMainColumns
        subFields.value = safeSubColumns
        subTable.value = { ...map.sub, columnsVo: safeSubColumns }
      }
      
      // 查找主键字段
      const pkFieldFromConfig = onlineField.value.find((field: any) => field.pk)?.columnName
      pkName.value = pkFieldFromConfig || 'id'
      console.log("handleUpdate - 从配置中获取的主键字段:", pkName.value)
    }
  }

  const { data } = await getOnlineData(tableId.value, tableName.value, onlineId)

  console.log("handleUpdate data",data)
  if (formType.value === 'single') {
    Object.assign(formData.single, data)
  }
  else if (formType.value === 'main-sub') {
    Object.assign(formData.main, data.main)
    formData.sub = data[subTable.value.tableName] || []
  }

  refreshAllRuntime()
  dialog.visible = true
  dialog.title = "修改"
}

// 驼峰转下划线函数
const camelToSnake = (str: string): string => {
  if (!str) return '';
  return str
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1_$2')
    .toLowerCase();
};

// 修改子表处理逻辑
const handleSubComponent = (data: any, index: number) => {
  try {
    console.log("=== handleSubComponent 开始 ===")
    console.log("完整接收数据:", data)
    console.log("data.mappedData:", data.mappedData)
    console.log("data.mapping:", data.mapping)
    console.log("data.popupConfig:", data.popupConfig)
    
    // 检查是否为多选模式
    const isMultiple = data.popupConfig?.multiple === true || 
                      (data.mapping && safeParseJSON(data.mapping).popupConfig?.multiple === true)
    
    console.log("是否为多选模式:", isMultiple)
    
    if (isMultiple && Array.isArray(data.mappedData) && data.mappedData.length > 1) {
      // 多选模式：为每个选中的数据创建新行
      console.log(`多选模式 - 选中 ${data.mappedData.length} 条记录`)
      
      // 先删除当前行（因为会被第一条记录替换）
      formData.sub.splice(index, 1)
      
      // 为每条选中的记录创建对应的子表行
      data.mappedData.forEach((mappedItem: any, itemIndex: number) => {
        console.log(`处理第 ${itemIndex + 1} 条记录:`, mappedItem)
        
        // 创建新行，初始化所有字段为 null
        const newRow = subFields.value.reduce((acc: any, cur) => {
          acc[cur.columnName] = null
          return acc
        }, {})
        
        console.log(`第 ${itemIndex + 1} 行初始化:`, newRow)
        
        // 使用映射数据填充新行
        updateRowWithMappedData(newRow, mappedItem, data.mapping)
        
        console.log(`第 ${itemIndex + 1} 行填充后:`, newRow)
        
        // 将新行插入到子表中
        if (itemIndex === 0) {
          // 第一条记录插入到原来的位置
          formData.sub.splice(index, 0, newRow)
        } else {
          // 其他记录追加到末尾
          formData.sub.push(newRow)
        }
      })
      
      console.log(`多选模式 - 成功创建了 ${data.mappedData.length} 行记录`)
      console.log("最终子表数据:", formData.sub)
      return
    }
    
    // 单选模式或单条数据
    const newRow = formData.sub[index]
    if (!newRow) return
    
    if (data.popupConfig?.displayMode === 'sub' && data.mappedData) {
      // sub 模式：使用 mappedData 中的完整映射数据
      const mappedItem = Array.isArray(data.mappedData) ? data.mappedData[0] : data.mappedData
      console.log("单选模式 - mappedItem:", mappedItem)
      updateRowWithMappedData(newRow, mappedItem, data.mapping)
    } else {
      // 原有逻辑：使用 dictFields 和 mapping
      if (!data.dictFields || !data.mapping) return
      
      Object.entries(safeParseJSON(data.dictFields)).forEach(([sourceField]) => {
        const targetField = safeParseJSON(data.mapping)[sourceField] || sourceField
        newRow[camelToSnake(targetField)] = convertValueType(
          data.commonDataInfo?.[targetField],
          subFields.value.find(f => f.columnName === targetField)?.javaType
        )
      })
    }

    // 更新数组（触发响应式）
    formData.sub.splice(index, 1, newRow)
    console.log("=== handleSubComponent 结束 ===")
  } catch (e) {
    console.error('子表处理异常:', e)
    proxy?.$modal.msgError(`字段映射失败: ${e.message}`)
  }
}

// 更新行数据的辅助函数
const updateRowWithMappedData = (row: any, mappedItem: any, mapping: any) => {
  console.log('=== updateRowWithMappedData 开始 ===')
  console.log('目标行:', row)
  console.log('映射数据:', mappedItem)
  console.log('字段映射配置:', mapping)
  
  // 解析 mapping 配置
  let mappingConfig = mapping
  let popupConfig = {}
  
  if (typeof mapping === 'string') {
    try {
      mappingConfig = safeParseJSON(mapping)
    } catch (e) {
      console.error('解析 mapping 字符串失败:', e)
      return
    }
  }
  
  // 处理嵌套的 mapping 结构
  if (mappingConfig.mapping) {
    popupConfig = mappingConfig.popupConfig || {}
    mappingConfig = mappingConfig.mapping
  }
  
  console.log('解析后的 mapping 配置:', mappingConfig)
  console.log('解析后的 popup 配置:', popupConfig)
  
  // 根据 displayMode 处理数据
  const displayMode = popupConfig.displayMode || 'value'
  console.log('显示模式:', displayMode)
  
  if (displayMode === 'sub') {
    // sub 模式：基于 mapping 构建完整映射数据
    console.log('使用 sub 模式处理数据')
    
    Object.entries(mappingConfig).forEach(([sourceField, targetField]) => {
      console.log(`映射字段: ${sourceField} -> ${targetField}`)
      
      // 检查 mappedItem 中是否有这个字段
      if (mappedItem.hasOwnProperty(sourceField)) {
        const value = mappedItem[sourceField]
        console.log(`源字段 ${sourceField} 的值:`, value)
        
        // 只设置下划线格式字段（数据库表中的实际字段）
        const snakeField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase()
        row[snakeField] = value
        console.log(`设置下划线字段 ${snakeField} = ${value}`)
        
        // 不再设置驼峰格式字段，避免数据库字段错误
        // row[targetField] = value
      } else {
        console.log(`源字段 ${sourceField} 不存在于 mappedItem 中，mappedItem 的字段:`, Object.keys(mappedItem))
      }
    })
  } else {
    // 其他模式：直接使用 mappedItem 中的数据
    console.log('使用直接映射模式处理数据')
    
    Object.entries(mappedItem).forEach(([field, value]) => {
      console.log(`直接设置字段: ${field} = ${value}`)
      row[field] = value
    })
  }
  
  console.log('更新后的行数据:', row)
  console.log('=== updateRowWithMappedData 结束 ===')
}

// 类型转换辅助方法
const convertValueType = (value: any, javaType: string) => {
  if (value === null || value === undefined) return null

  switch(javaType) {
    case 'Long':
    case 'Integer':
      return Number(value) || 0
    case 'BigDecimal':
    case 'Double':
      return parseFloat(value) || 0
    default:
      return String(value)
  }
}

// safeParseJSON 已由 useOnlineRuntime 提供（此处无需重复实现）

// 修改添加子表行逻辑
const handleAddSub = () => {
  const newRow = subFields.value.reduce((acc: any, cur) => {
    acc[cur.columnName] = null
    return acc
  }, {})
  // apply row defaults (onChange.onRowAdd)
  applyOnRowAdd(subFields.value, newRow, getMainModel(), workflowCtx.value)
  formData.sub.push(newRow)
  refreshSubRuntime(formData.sub.length - 1)
  // adding a row may affect main aggregates
  handleSubToMain()
  refreshMainRuntime()
}

// 删除子表行
const handleDeleteSub = (index: number) => {
  formData.sub.splice(index, 1)
  handleSubToMain()
  refreshAllRuntime()
}

/** 提交按钮 */
const submitForm = async () => {
  refreshAllRuntime()

  const model = getMainModel()
  const fields = getMainFieldsForRuntime()
  const ctx = { ...(model || {}), workflow: workflowCtx.value }

  const mainErrors = validateBySchema(fields, ctx, mainUiState.value)
  if (mainErrors.length) {
    proxy?.$modal.msgError(mainErrors[0].message)
    return
  }

  if (formType.value === 'main-sub') {
    for (let i = 0; i < formData.sub.length; i++) {
      const row = formData.sub[i]
      const ui = subUiStates.value[i] || emptyUiState()
      const rowErrors = validateBySchema(subFields.value, { ...(model || {}), ...(row || {}), workflow: workflowCtx.value }, ui)
      if (rowErrors.length) {
        proxy?.$modal.msgError(`子表第${i + 1}行：${rowErrors[0].message}`)
        return
      }
    }
  }

  let submitData: any
  if (formType.value === 'single') {
    submitData = formData.single
  } else if (formType.value === 'main-sub') {
    submitData = {
      main: formData.main,
      sub: {
        tableName: subTable.value.tableName,
        data: formData.sub
      }
    }
  }

  await addOrUpdateOnlineData(tableId.value, tableName.value, submitData)
  proxy?.$modal.msgSuccess("操作成功")
  dialog.visible = false
  await getList()
}

/** 删除按钮操作 */
const handleDelete = async (row?: any) => {
    // 获取主键字段名（假设在getList中已获取并存储在pkName.value中）
    const pkField = pkName.value;
    // 获取要删除的ID数组
    const deleteIds = row ? [row[pkField]] : ids.value;
    if (deleteIds.length === 0) {
      proxy?.$modal.msgWarning('请选择要删除的数据');
      return;
    }
    try {
      await proxy?.$modal.confirm('是否确认删除选中的数据？');
      // 调用删除API，传递表ID、表名和要删除的ID数组
      console.log("handleDelete deleteIds",deleteIds)
      await delOnlineData(tableId.value, tableName.value, deleteIds);
      proxy?.$modal.msgSuccess('删除成功');
      await getList(); // 刷新列表
    } catch (error) {
      // 用户取消删除或删除失败
      console.error('删除失败:', error);
    }
}

const commonComponent = (nm) => {
  // 保持一致：直接把 commonDataInfo merge 进当前主表模型（single/main-sub）
  const model = getMainModel()
  applyCommonDataInfo(nm, model)

  // 对当前字段做一次运行时联动（不依赖 @change 的场景：比如 popup 选择回填多个字段）
  const col = nm?.columnName
  if (col) {
    const fields = getMainFieldsForRuntime()
    const field = fields.find((f: any) => f?.columnName === col)
    if (field) {
      const ctx = { ...(model || {}), workflow: workflowCtx.value }
      applyOnClear(field, ctx, (k, v) => { (model as any)[k] = v })
      applyOnSetValue(field, { ...(model || {}), workflow: workflowCtx.value }, (k, v) => { (model as any)[k] = v })
      applyOnMainChange(field, model, (model as any)[col])
    }
  }
  refreshAllRuntime()
}

// 新增方法 - 处理字段变化事件
const handleFieldChange = (type: 'main'|'sub', field: any, value: any, subIndex?: number) => {
  // 主表字段变化
  if (type === 'main') {
    // 处理主表内部联动
    handleMainToMain(field, value)
  }
  // 子表字段变化
  if (type === 'sub' && typeof subIndex === 'number') {
    // 处理子表内部联动
    handleSubToSub(field, value, subIndex)
    // 处理子表到主表的联动
    handleSubToMain()
    refreshAllRuntime()
  }
}

// 主表字段变化处理（安全增强版）
const handleMainToMain = (changedField: any, newValue: any) => {
  try {
    const model = getMainModel()
    handleMainToMainRuntime(changedField, newValue, model as any, workflowCtx.value)
    // main change may affect ui/linkage and also sub-row when/filters
    refreshAllRuntime()
  } catch (e) {
    console.error('主表联动处理失败:', e)
    proxy?.$modal.msgError('主表数据联动异常')
  }
}

// 子表字段变化处理（安全增强版）
const handleSubToSub = (changedField: any, newValue: any, subIndex: number) => {
  const row = formData.sub[subIndex]
  if (!row) return
  try {
    handleSubToSubRuntime(changedField, newValue, row, getMainModel(), workflowCtx.value)
    // 触发视图更新
    formData.sub.splice(subIndex, 1, { ...row })
    refreshSubRuntime(subIndex)
  } catch (e) {
    console.error('子表联动处理失败:', e)
    proxy?.$modal.msgError('子表数据联动异常')
  }
}
// 子表变化触发主表更新
const handleSubToMain = () => {
  if (formType.value !== 'main-sub') return
  try {
    applyOnSubTrigger(mainFields.value, formData.sub, formData.main)
    refreshMainRuntime()
  } catch (e) {
    console.error('子表到主表联动处理失败:', e)
    proxy?.$modal.msgError('数据联动计算发生错误')
  }
}

// —— popup 映射辅助：按 mapping(id/name) 和 dict_fields 从 dict_type 表取名称 ——
const getColValueLabelKeys = (col: any): { valueKey: string; labelKey: string } | null => {
  try {
    let mappingCfg: any = col?.mapping;
    if (typeof mappingCfg === 'string') { try { mappingCfg = JSON.parse(mappingCfg) } catch { mappingCfg = {} } }
    const inner = mappingCfg?.mapping || mappingCfg || {};
    const idKey = typeof inner?.id === 'string' ? inner.id : undefined;
    const nameKey = typeof inner?.name === 'string' ? inner.name : undefined;
    if (idKey && nameKey) return { valueKey: idKey, labelKey: nameKey };
    const obj = typeof col?.dictFields === 'string' ? JSON.parse(col.dictFields) : (col?.dictFields || {});
    const keys = Object.keys(obj);
    if (keys.length >= 2) return { valueKey: keys[0], labelKey: keys[1] };
  } catch {}
  // 没有提供足够的信息（无 mapping.id/name 且无 dictFields），判定为系统字典或未知，返回 null 让上层跳过
  return null;
};

const buildDictMapForCol = async (col: any): Promise<Record<string, string>> => {
  try {
    const keys = getColValueLabelKeys(col);
    if (!keys) return {};
    const { valueKey, labelKey } = keys;
    const res: any = await listTableData(col.dictType, { pageNum: 1, pageSize: 1000, params: {} } as any);
    const rows: any[] = res?.rows || [];
    const map: Record<string, string> = {};
    rows.forEach((r: any) => { if (r?.[valueKey] != null) map[String(r[valueKey])] = String(r[labelKey] ?? '') });
    return map;
  } catch { return {} }
};

const applyPopupLabelMapping = async (rows: any[], cols: any[]): Promise<void> => {
  if (!Array.isArray(rows) || !Array.isArray(cols)) return;
  const popupCols = cols.filter((c: any) => c?.htmlType === 'popup' && c?.dictType);
  for (const col of popupCols) {
    // 系统字典：无 id/name 线索则跳过，由 disp-tag 负责展示
    const keys = getColValueLabelKeys(col);
    if (!keys) continue;

    let mappingCfg: any = col?.mapping;
    if (typeof mappingCfg === 'string') { try { mappingCfg = JSON.parse(mappingCfg) } catch { mappingCfg = {} } }
    const inner = mappingCfg?.mapping || mappingCfg || {};
    const nameTarget: string | undefined = typeof inner?.name === 'string' ? inner.name : undefined;

    const dictMap = await buildDictMapForCol(col);
    const idKey = col?.columnName || col?.javaField;
    const labelKeyFallback = `${col?.columnName || ''}Name`;

    for (const row of rows) {
      const idVal = idKey ? (row[idKey] ?? (row.main ? row.main[idKey] : undefined)) : undefined;
      if (idVal !== undefined && idVal !== null && idVal !== '') {
        const nameVal = dictMap[String(idVal)];
        if (nameVal !== undefined) {
          if (nameTarget) row[nameTarget] = nameVal;
          if (labelKeyFallback) row[labelKeyFallback] = nameVal;
        }
      }
    }
  }
};

// —— 渲染安全防护：移除行数据/列定义中的非法属性名，避免 setAttribute 报错 ——
const hasIllegalAttrName = (key: string): boolean => /[{}\s]/.test(key);
const sanitizeRow = (row: any): any => {
  if (!row || typeof row !== 'object') return row;
  Object.keys(row).forEach(k => {
    if (hasIllegalAttrName(k)) delete row[k];
  });
  if (row.main && typeof row.main === 'object') {
    Object.keys(row.main).forEach(k => { if (hasIllegalAttrName(k)) delete row.main[k] })
  }
  return row;
};
const sanitizeColumns = (cols: any[]): any[] => {
  if (!Array.isArray(cols)) return cols;
  return cols.map(col => {
    const c = { ...col };
    if (typeof c.columnName === 'string' && hasIllegalAttrName(c.columnName)) c.columnName = (c.columnName || '').replace(/[{}\s]/g, '');
    if (typeof c.javaField === 'string' && hasIllegalAttrName(c.javaField)) c.javaField = (c.javaField || '').replace(/[{}\s]/g, '');
    // 将后端字段映射为前端使用的字段（同时保留原始字段用于兼容）
    c.list = c.isList === '1' || c.isList === true || c.isList === 'true';  // 是否列表显示
    c.query = c.isQuery === '1' || c.isQuery === true || c.isQuery === 'true'; // 是否查询字段
    c.edit = c.isEdit === '1' || c.isEdit === true || c.isEdit === 'true';   // 是否编辑字段
    c.insert = c.isInsert === '1' || c.isInsert === true || c.isInsert === 'true'; // 是否插入字段
    c.required = c.isRequired === '1' || c.isRequired === true || c.isRequired === 'true'; // 是否必填
    c.pk = c.isPk === '1' || c.isPk === true || c.isPk === 'true'; // 是否主键
    c.readonly = c.isReadonly === '1' || c.isReadonly === true || c.isReadonly === 'true'; // 是否只读
    return c;
  })
};

onMounted(() => {
  // 优先使用 props，其次使用 route.query，最后使用 route.params
  tableId.value = props.tableId 
    || (route.query && route.query.tableId as string) 
    || (route.params && route.params.tableId as string);
  tableName.value = props.tableName 
    || (route.query && route.query.tableName as string)
    || (route.params && route.params.tableName as string);
  
  if (tableId.value && tableName.value) {
    getList();
  } else {
    console.error('OnlineTest onMounted - 缺少必要的参数，无法初始化');
  }
})

// 监听路由参数变化，确保当路由参数变化时能正确响应
watch(
  () => [props.tableId, props.tableName],
  ([newTableId, newTableName]) => {
    if (newTableId && newTableName) {
      tableId.value = newTableId;
      tableName.value = newTableName;
      // 重置查询参数
      queryParams.pageNum = 1;
      queryParams.params = {};
      // 重新获取数据
      getList();
    }
  },
  { immediate: false }
)

// 同时监听 route.query 作为备用
watch(
  () => [route.query.tableId, route.query.tableName],
  ([newTableId, newTableName]) => {
    if (newTableId && newTableName && (!props.tableId || !props.tableName)) {
      tableId.value = newTableId as string;
      tableName.value = newTableName as string;
      // 重置查询参数
      queryParams.pageNum = 1;
      queryParams.params = {};
      // 重新获取数据
      getList();
    }
  },
  { immediate: false }
)
</script>
