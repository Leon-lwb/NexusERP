<template>
  <el-dialog
    v-model="visible"
    title="选择数据表"
    width="70%"
    height="600px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <el-steps :active="activeStep" finish-status="success" align-center>
      <el-step title="选择数据表" />
      <el-step title="预览表结构" />
      <el-step title="生成表单" />
    </el-steps>

    <!-- 步骤1：选择数据表 -->
    <div v-if="activeStep === 0" class="step-content">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
        <el-form-item label="表名称" prop="tableName">
          <el-input
            v-model="queryParams.tableName"
            placeholder="请输入表名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="表描述" prop="tableComment">
          <el-input
            v-model="queryParams.tableComment"
            placeholder="请输入表描述"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-container">
        <el-table 
          v-loading="loading" 
          :data="tables" 
          ref="tableRef"
          @row-click="(row) => { selectedTableName = row.tableName }"
        >
          <el-table-column label="" width="55" align="center">
            <template #default="scope">
              <el-radio v-model="selectedTableName" :label="scope.row.tableName">{{ scope.$index + 1 }}</el-radio>
            </template>
          </el-table-column>
          <el-table-column label="表名称" align="center" prop="tableName" />
          <el-table-column label="表描述" align="center" prop="tableComment" />
          <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        </el-table>

        <div class="pagination-container">
          <pagination
            v-show="total > 0"
            :total="total"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            @pagination="getList"
          />
        </div>
      </div>
    </div>

    <!-- 步骤2：预览表结构 -->
    <div v-if="activeStep === 1" class="step-content">
      <div class="table-preview" v-if="selectedTable">
        <!-- 主表信息 -->
        <div class="table-section">
          <h3>主表：{{ selectedTable.tableName }}</h3>
          <el-table :data="selectedTable.columnsVo" style="width: 100%">
            <el-table-column type="selection" width="55" align="center">
              <template #default="{ row }">
                <el-checkbox 
                  v-model="selectedFields[row.columnName]"
                  @change="() => handleFieldSelect(row.columnName)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="columnName" label="字段名" />
            <el-table-column prop="columnType" label="类型" />
            <el-table-column prop="columnComment" label="说明" />
            <el-table-column prop="isNullable" label="是否可空" />
            <el-table-column label="组件类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.htmlType }}</el-tag>
                <el-tag v-if="row.dictType" size="small" type="success" style="margin-left: 5px">
                  {{ row.dictType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="是否插入" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox 
                  v-model="row.insert"
                  true-value="true"
                  false-value="false"
                  :disabled="!selectedFields[row.columnName]"
                />
              </template>
            </el-table-column>
            <el-table-column label="是否编辑" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox 
                  v-model="row.edit"
                  true-value="true"
                  false-value="false"
                  :disabled="!selectedFields[row.columnName]"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 子表信息 -->
        <div class="table-section" v-if="selectedTable.subTableName && selectedTable.subColumnsVo">
          <h3>子表：{{ selectedTable.subTableName }}</h3>
          <el-table :data="selectedTable.subColumnsVo" style="width: 100%">
            <el-table-column type="selection" width="55" align="center">
              <template #default="{ row }">
                <el-checkbox 
                  v-model="selectedSubFields[row.columnName]"
                  @change="() => handleFieldSelect(row.columnName, true)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="columnName" label="字段名" />
            <el-table-column prop="columnType" label="类型" />
            <el-table-column prop="columnComment" label="说明" />
            <el-table-column prop="isNullable" label="是否可空" />
            <el-table-column label="组件类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.htmlType }}</el-tag>
                <el-tag v-if="row.dictType" size="small" type="success" style="margin-left: 5px">
                  {{ row.dictType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="是否插入" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox 
                  v-model="row.insert"
                  true-value="true"
                  false-value="false"
                  :disabled="!selectedSubFields[row.columnName]"
                />
              </template>
            </el-table-column>
            <el-table-column label="是否编辑" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox 
                  v-model="row.edit"
                  true-value="true"
                  false-value="false"
                  :disabled="!selectedSubFields[row.columnName]"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 步骤3：生成表单 -->
    <div v-if="activeStep === 2" class="step-content">
      <div class="form-preview" v-if="selectedTable">
        <!-- 主表表单预览 -->
        <div class="form-section">
          <h3>主表：{{ selectedTable.tableName }}</h3>
          <el-form :model="formData" label-width="100px">
            <template v-for="(column, index) in selectedTable.columnsVo" :key="index">
              <el-form-item 
                v-if="selectedFields[column.columnName] && column.insert && column.edit && column.isPk != '1'"
                :label="column.columnComment || column.columnName"
              >
                <!-- 添加组件类型显示 -->
                <div class="component-type">
                  <el-tag size="small" type="info">{{ column.htmlType }}</el-tag>
                  <el-tag v-if="column.dictType" size="small" type="success" style="margin-left: 5px">
                    字典: {{ column.dictType }}
                  </el-tag>
                </div>

                <!-- 输入框 -->
                <template v-if="column.htmlType === 'input'">
                  <el-input 
                    v-if="!column.columnType.includes('blob') && !column.columnType.includes('longblob')"
                    v-model="formData[column.columnName]" 
                    :placeholder="`请输入${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                  <el-input
                    v-else
                    v-model="formData[column.columnName]"
                    type="textarea"
                    :rows="4"
                    :placeholder="`请输入${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                </template>

                <!-- 文本域 -->
                <template v-else-if="column.htmlType === 'textarea'">
                  <el-input 
                    v-if="!column.columnType.includes('blob') && !column.columnType.includes('longblob')"
                    v-model="formData[column.columnName]" 
                    type="textarea"
                    :rows="4"
                    :placeholder="`请输入${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                  <el-input
                    v-else
                    v-model="formData[column.columnName]"
                    type="textarea"
                    :rows="4"
                    :placeholder="`请输入${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                </template>

                <!-- 下拉选择 -->
                <template v-else-if="column.htmlType === 'select'">
                  <el-select 
                    v-model="formData[column.columnName]"
                    :placeholder="`请选择${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  >
                    <el-option
                      v-for="dict in dictOptions[column.dictType] || []"
                      :key="dict.value"
                      :label="dict.label"
                      :value="dict.value"
                    />
                  </el-select>
                  <span v-if="column.showDisplay" class="dict-label">
                    {{ getDisplayValue(column) }}
                  </span>
                </template>

                <!-- 单选框组 -->
                <template v-else-if="column.htmlType === 'radio'">
                  <el-radio-group 
                    v-model="formData[column.columnName]"
                    @change="(value) => handleFieldChange(value, column)"
                  >
                    <el-radio
                      v-for="dict in dictOptions[column.dictType] || []"
                      :key="dict.value"
                      :label="dict.value"
                    >
                      {{ dict.label }}
                    </el-radio>
                  </el-radio-group>
                </template>

                <!-- 复选框组 -->
                <template v-else-if="column.htmlType === 'checkbox'">
                  <el-checkbox-group 
                    v-model="formData[column.columnName]"
                    @change="(value) => handleFieldChange(value, column)"
                  >
                    <el-checkbox
                      v-for="dict in dictOptions[column.dictType] || []"
                      :key="dict.value"
                      :label="dict.value"
                    >
                      {{ dict.label }}
                    </el-checkbox>
                  </el-checkbox-group>
                </template>

                <!-- 日期时间选择器 -->
                <template v-else-if="column.htmlType === 'datetime'">
                  <el-date-picker
                    v-model="formData[column.columnName]"
                    type="datetime"
                    :placeholder="`请选择${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                </template>

                <!-- 日期选择器 -->
                <template v-else-if="column.htmlType === 'date'">
                  <el-date-picker
                    v-model="formData[column.columnName]"
                    type="date"
                    :placeholder="`请选择${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                </template>

                <!-- 时间选择器 -->
                <template v-else-if="column.htmlType === 'time'">
                  <el-time-picker
                    v-model="formData[column.columnName]"
                    type="time"
                    :placeholder="`请选择${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                </template>

                 <!-- popup选择器 -->
                 <template v-else-if="column.htmlType === 'popup'">
                  <popup-table
                    v-model="formData[column.columnName]"
                    type="time"
                    :placeholder="`请选择${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                </template>

                <!-- 默认使用输入框 -->
                <template v-else>
                  <el-input 
                    v-if="!column.columnType.includes('blob') && !column.columnType.includes('longblob')"
                    v-model="formData[column.columnName]" 
                    :placeholder="`请输入${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                  <el-input
                    v-else
                    v-model="formData[column.columnName]"
                    type="textarea"
                    :rows="4"
                    :placeholder="`请输入${column.columnComment || column.columnName}`"
                    @change="(value) => handleFieldChange(value, column)"
                  />
                </template>
              </el-form-item>
            </template>
          </el-form>
        </div>

        <!-- 子表表格预览 -->
        <div class="form-section" v-if="selectedTable.subTableName && selectedTable.subColumnsVo">
          <h3>子表：{{ selectedTable.subTableName }}</h3>
          <el-table :data="subTableData" style="width: 100%" border>
            <template v-for="(column, index) in selectedTable.subColumnsVo" :key="index">
              <el-table-column
                v-if="selectedSubFields[column.columnName] && column.insert && column.edit && column.isPk !== '1' && !column.columnName.includes(selectedTable.subTableFkName || '')"
                :prop="column.columnName"
                :label="column.columnComment || column.columnName"
              >
                <template #default="scope">
                  <!-- 添加组件类型显示 -->
                  <div class="component-type">
                    <el-tag size="small" type="info">{{ column.htmlType }}</el-tag>
                    <el-tag v-if="column.dictType" size="small" type="success" style="margin-left: 5px">
                      字典: {{ column.dictType }}
                    </el-tag>
                  </div>

                  <!-- 输入框 -->
                  <template v-if="column.htmlType === 'input'">
                    <el-input 
                      v-if="!column.columnType.includes('blob') && !column.columnType.includes('longblob')"
                      v-model="scope.row[column.columnName]" 
                      :placeholder="`请输入${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                    <el-input
                      v-else
                      v-model="scope.row[column.columnName]"
                      type="textarea"
                      :rows="4"
                      :placeholder="`请输入${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                  </template>

                  <!-- 文本域 -->
                  <template v-else-if="column.htmlType === 'textarea'">
                    <el-input 
                      v-if="!column.columnType.includes('blob') && !column.columnType.includes('longblob')"
                      v-model="scope.row[column.columnName]" 
                      type="textarea"
                      :rows="4"
                      :placeholder="`请输入${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                    <el-input
                      v-else
                      v-model="scope.row[column.columnName]"
                      type="textarea"
                      :rows="4"
                      :placeholder="`请输入${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                  </template>

                  <!-- 下拉选择 -->
                  <template v-else-if="column.htmlType === 'select'">
                    <el-select 
                      v-model="scope.row[column.columnName]"
                      :placeholder="`请选择${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    >
                      <el-option
                        v-for="dict in dictOptions[column.dictType] || []"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                      />
                    </el-select>
                    <span v-if="column.showDisplay" class="dict-label">
                      {{ getDisplayValue(column) }}
                    </span>
                  </template>

                  <!-- 单选框组 -->
                  <template v-else-if="column.htmlType === 'radio'">
                    <el-radio-group 
                      v-model="scope.row[column.columnName]"
                      @change="(value) => handleFieldChange(value, column)"
                    >
                      <el-radio
                        v-for="dict in dictOptions[column.dictType] || []"
                        :key="dict.value"
                        :label="dict.value"
                      >
                        {{ dict.label }}
                      </el-radio>
                    </el-radio-group>
                  </template>

                  <!-- 复选框组 -->
                  <template v-else-if="column.htmlType === 'checkbox'">
                    <el-checkbox-group 
                      v-model="scope.row[column.columnName]"
                      @change="(value) => handleFieldChange(value, column)"
                    >
                      <el-checkbox
                        v-for="dict in dictOptions[column.dictType] || []"
                        :key="dict.value"
                        :label="dict.value"
                      >
                        {{ dict.label }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </template>

                  <!-- 日期时间选择器 -->
                  <template v-else-if="column.htmlType === 'datetime'">
                    <el-date-picker
                      v-model="scope.row[column.columnName]"
                      type="datetime"
                      :placeholder="`请选择${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                  </template>

                  <!-- 日期选择器 -->
                  <template v-else-if="column.htmlType === 'date'">
                    <el-date-picker
                      v-model="scope.row[column.columnName]"
                      type="date"
                      :placeholder="`请选择${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                  </template>

                  <!-- 时间选择器 -->
                  <template v-else-if="column.htmlType === 'time'">
                    <el-time-picker
                      v-model="scope.row[column.columnName]"
                      type="date"
                      :placeholder="`请选择${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                  </template>

                  <!-- popup选择器 -->
                  <template v-else-if="column.htmlType === 'popup'">
                    <popup-table
                      v-model="scope.row[column.columnName]"
                      type="date"
                      :placeholder="`请选择${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                  </template>

                  <!-- 默认使用输入框 -->
                  <template v-else>
                    <el-input 
                      v-if="!column.columnType.includes('blob') && !column.columnType.includes('longblob')"
                      v-model="scope.row[column.columnName]" 
                      :placeholder="`请输入${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                    <el-input
                      v-else
                      v-model="scope.row[column.columnName]"
                      type="textarea"
                      :rows="4"
                      :placeholder="`请输入${column.columnComment || column.columnName}`"
                      @change="(value) => handleFieldChange(value, column)"
                    />
                  </template>
                </template>
              </el-table-column>
            </template>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="scope">
                <el-button type="danger" size="small" @click="handleDeleteSubTableRow(scope.$index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="subtable-actions">
            <el-button type="primary" size="small" @click="handleAddSubTableRow">
              添加行
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleCancel">取 消</el-button>
        <el-button type="primary" @click="handlePrev" v-if="activeStep > 0">上一步</el-button>
        <el-button type="primary" @click="handleNext" v-if="activeStep < 2">下一步</el-button>
        <el-button type="primary" @click="handleGenerate" v-if="activeStep === 2">生成表单</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, reactive, watch, onMounted, computed } from 'vue'

// 定义组件名称
defineOptions({
  name: 'TableSelector'
})
import { ElMessage } from 'element-plus'
import { list } from '@/api/online'
import { getTableColumns } from '@/api/tool/gen'
import { TableQuery, TableVO as BaseTableVO, DbColumnVO } from '@/api/tool/gen/types'
import { getDicts } from '@/api/system/dict/data'
import type { FormComponent, SubTableColumn } from './types'
import type { FormConfig } from '@/types/table'
import RichEditor from './components/RichEditor.vue'
import PopupTable from './components/PopupTable.vue'

interface DictOption {
  label: string
  value: string | number
}

interface ColumnInfo {
  columnName: string
  columnType: string
  columnComment: string
  isNullable: string
  isPk: string
  insert: boolean
  edit: boolean
  dictType?: string
  dictFields?: string
  mapping?: string
  htmlType: string
  showDisplay?: boolean
  onChange?: string
}

type ColumnVO = DbColumnVO & ColumnInfo

interface ExtendedTableVO extends Omit<BaseTableVO, 'tplCategory'> {
  columnsVo: ColumnVO[]
  subTableName?: string
  subTableFkName?: string
  subColumnsVo?: ColumnVO[]
  tplCategory: string
}

const props = defineProps<{
  total?: number
}>()

const visible = ref(false)
const tables = ref<ExtendedTableVO[]>([])
const total = ref(0)
const selectedTable = ref<ExtendedTableVO | null>(null)
let selectedTableName = ref<string>('')
const loading = ref(false)

const queryParams = ref<TableQuery & { total?: number }>({
  pageNum: 1,
  pageSize: 10,
  tableName: '',
  tableComment: '',
  dataName: '', // 保留这个字段，因为 TableQuery 接口需要
  total: 0
})

const activeStep = ref(0)
const formData = ref<Record<string, any>>({})

// 添加字段选择状态
const selectedFields = ref<Record<string, boolean>>({})
const selectedSubFields = ref<Record<string, boolean>>({})

// 添加插入和编辑状态
const insertFields = ref<Record<string, boolean>>({})
const editFields = ref<Record<string, boolean>>({})
const insertSubFields = ref<Record<string, boolean>>({})
const editSubFields = ref<Record<string, boolean>>({})

const emit = defineEmits(['generate', 'table-select'])

const tableRef = ref()

// 添加子表数据
const subTableData = ref<any[]>([])

// 字典数据缓存
const dictCache = ref<Record<string, DictOption[]>>({})

// 添加字典选项的响应式引用
const dictOptions = ref<Record<string, DictOption[]>>({})

// 获取字典选项
const getDictOptions = async (dictType: string): Promise<DictOption[]> => {
  if (!dictType) return []
  
  if (dictCache.value[dictType]) {
    return dictCache.value[dictType]
  }

  try {
    const res = await getDicts(dictType)
    if (res.code === 200) {
      const options = res.data.map((item: any) => ({
        label: item.dictLabel,
        value: item.dictValue
      }))
      dictCache.value[dictType] = options
      return options
    }
    return []
  } catch (error) {
    console.error('获取字典数据失败:', error)
    return []
  }
}

// 处理字段变化
const handleFieldChange = (value: string | number | boolean | Date | null, column: ColumnVO) => {
  formData.value[column.columnName] = value
  
  // 处理联动规则
  if (column.onChange) {
    try {
      const rules = JSON.parse(column.onChange)
      if (rules.onChange) {
        const { field, value: targetValue } = rules.onChange
        if (typeof field === 'string') {
          formData.value[field] = targetValue
        }
      }
    } catch (error) {
      console.error('处理联动规则失败:', error)
    }
  }
  
  // 处理字段映射
  if (column.mapping) {
    try {
      const mapping = JSON.parse(column.mapping)
      if (mapping[column.columnName]) {
        const targetField = mapping[column.columnName]
        formData.value[targetField] = value
      }
    } catch (error) {
      console.error('处理字段映射失败:', error)
    }
  }
}

// 获取显示值
const getDisplayValue = (column: ColumnVO): string => {
  if (!column.dictType || !column.showDisplay) return ''
  const value = formData.value[column.columnName]
  const options = dictCache.value[column.dictType] || []
  const option = options.find(opt => String(opt.value) === String(value))
  return option ? option.label : String(value || '')
}

// 获取字典字段配置
const getDictFields = (column: ColumnVO) => {
  try {
    return JSON.parse(column.dictFields || '{}')
  } catch {
    return { dictValue: 'value', dictLabel: 'label' }
  }
}

// 获取字段映射
const getMapping = (column: ColumnVO) => {
  try {
    return JSON.parse(column.mapping || '{}')
  } catch {
    return { [column.columnName]: column.columnName }
  }
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.value.pageNum = 1
  loadTables()
}

// 重置按钮操作
const resetQuery = () => {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    tableName: '',
    tableComment: '',
    dataName: '',
    total: 0
  }
  handleQuery()
}

// 获取列表数据
const getList = () => {
  loadTables()
}

// 处理字段信息
const processColumn = (column: any): ColumnVO => {
  const processedColumn = {
    ...column,
    columnName: column.columnName || '',
    columnType: column.columnType || '',
    columnComment: column.columnComment || '',
    isNullable: column.isNullable || 'NO',
    isPk: column.isPk || 'NO',
    insert: column.insert || false,
    edit: column.edit || false,
    htmlType: column.htmlType || 'input',
    dictType: column.dictType || '',
    dictFields: column.dictFields || '',
    mapping: column.mapping || '',
    onChange: column.onChange || '',
    showDisplay: false
  }

  // 根据htmlType和dictType设置显示类型和字典相关配置
  if (processedColumn.htmlType === 'select' && processedColumn.dictType) {
    processedColumn.showDisplay = true
  } else if (processedColumn.htmlType === 'radio' && processedColumn.dictType) {
    processedColumn.showDisplay = true
  } else if (processedColumn.htmlType === 'checkbox' && processedColumn.dictType) {
    processedColumn.showDisplay = true
  } else if (processedColumn.htmlType === 'switch' && processedColumn.dictType) {
    processedColumn.showDisplay = true
  } else if (processedColumn.htmlType === 'textarea') {
    processedColumn.showDisplay = true
  } else if (processedColumn.htmlType === 'date') {
    processedColumn.showDisplay = true
  } else if (processedColumn.htmlType === 'datetime') {
    processedColumn.showDisplay = true
  } else if (processedColumn.htmlType === 'popup') {
    // 保证 dictType 一定有值
    if (!processedColumn.dictType) {
      processedColumn.dictType = processedColumn.columnName || 'popup'
    }
    processedColumn.showDisplay = true
  } else {
    // 默认使用输入框
    processedColumn.htmlType = 'input'
    if (processedColumn.columnType.includes('int') || processedColumn.columnType.includes('decimal')) {
      processedColumn.props = {
        ...processedColumn.props,
        type: 'number'
      }
    }
    if (processedColumn.columnType.includes('longtext')){
      processedColumn.props = {
        ...processedColumn.props,
        type: 'textarea',
        rows: 4
      }
      processedColumn.htmlType = 'textarea'
    }
    if (processedColumn.columnType.includes('text')){
      processedColumn.props = {
        ...processedColumn.props,
        type: 'textarea',
        rows: 4
      }
      processedColumn.htmlType = 'textarea'
    }
    if (processedColumn.columnType.includes('blob')){
      processedColumn.props = {
        ...processedColumn.props,
        type: 'editor'
      }
      processedColumn.htmlType = 'editor'
    }
    if (processedColumn.columnType.includes('longblob')){
      processedColumn.props = {
        ...processedColumn.props,
        type: 'editor'
      }
      processedColumn.htmlType = 'editor'
    }
  }

  // 如果有字典类型，设置字典相关配置
  if (processedColumn.dictType) {
    processedColumn.showDisplay = true
    processedColumn.props = {
      ...processedColumn.props,
      dictType: processedColumn.dictType
    }
    // 如果有字典字段配置，解析并设置
    if (processedColumn.dictFields) {
      try {
        const dictFields = JSON.parse(processedColumn.dictFields)
        processedColumn.props.dictFields = dictFields
      } catch (error) {
        console.error('解析字典字段配置失败:', error)
      }
    }
    // 如果有字段映射配置，解析并设置
    if (processedColumn.mapping) {
      try {
        const mapping = JSON.parse(processedColumn.mapping)
        processedColumn.props.mapping = mapping
      } catch (error) {
        console.error('解析字段映射配置失败:', error)
      }
    }
    // 如果有联动规则配置，解析并设置
    if (processedColumn.onChange) {
      try {
        const onChange = JSON.parse(processedColumn.onChange)
        processedColumn.props.onChange = onChange
      } catch (error) {
        console.error('解析联动规则配置失败:', error)
      }
    }
  }

  return processedColumn
}

// 监听单选项变化，更新选中表
watch(selectedTableName, (name) => {
  const table = tables.value.find(t => t.tableName === name) || null
  if (table) handleTableSelect(table as ExtendedTableVO)
  else selectedTable.value = null
})

// 获取表字段信息时添加字典类型判断
const handleTableSelect = async (table: ExtendedTableVO) => {
  if (!table) return
  
  try {
    const res = await getTableColumns(table.tableName)
    if (res) {
      const columnsData = Array.isArray(res) ? res : []
      table.columnsVo = columnsData.map(column => processColumn(column))
      
      // 加载字典数据
      for (const column of table.columnsVo) {
        if (column.dictType) {
          await loadDictData(column)
        }
      }
      
      if (table.tplCategory === 'main' && table.subTableName) {
        const subRes = await getTableColumns(table.subTableName)
        
        if (subRes) {
          const subColumnsData = Array.isArray(subRes) ? subRes : []
          table.subColumnsVo = subColumnsData.map(column => processColumn(column))
          
          // 加载子表字典数据
          for (const column of table.subColumnsVo) {
            if (column.dictType) {
              await loadDictData(column)
            }
          }
        }
      }
      
      selectedTable.value = table
      initFieldStates(table)
    } else {
      ElMessage.error('获取表字段信息失败')
    }
  } catch (error) {
    console.error('获取表字段信息失败:', error)
    ElMessage.error('获取表字段信息失败')
  }
}

// 修改初始化字段状态函数
const initFieldStates = (table: ExtendedTableVO) => {
  if (!table) return
  
  // 初始化主表字段状态
  table.columnsVo.forEach(column => {
    const fieldName = column.columnName
    // 默认选中所有字段
    selectedFields.value[fieldName] = true
    // 初始化表单数据
    formData.value[fieldName] = ''
  })
  
  // 初始化子表字段状态
  if (table.subColumnsVo) {
    table.subColumnsVo.forEach(column => {
      const fieldName = column.columnName
      // 默认选中所有字段
      selectedSubFields.value[fieldName] = true
    })
    // 初始化子表数据
    initSubTableData()
  }
}

// 修改字段选择处理方法
const handleFieldSelect = (field: string, isSubTable: boolean = false) => {
  if (isSubTable) {
    const newValue = !selectedSubFields.value[field]
    selectedSubFields.value[field] = newValue
    // 找到对应的行数据
    const row = selectedTable.value?.subColumnsVo?.find(col => col.columnName === field)
    if (row) {
      // 如果选中，则设置insert和edit为true
      if (newValue) {
        row.insert = true
        row.edit = true
      }
    }
  } else {
    const newValue = !selectedFields.value[field]
    selectedFields.value[field] = newValue
    // 找到对应的行数据
    const row = selectedTable.value?.columnsVo?.find(col => col.columnName === field)
    if (row) {
      // 如果选中，则设置insert和edit为true
      if (newValue) {
        row.insert = true
        row.edit = true
      }
    }
  }
  console.log('主表选中字段:', selectedFields.value)
  console.log('子表选中字段:', selectedSubFields.value)
}

const loadTables = async () => {
  loading.value = true
  try {
    const res = await list(queryParams.value)
    
    if (res && res.rows) {
      tables.value = res.rows.map((table: any) => ({
        ...table,
        columnsVo: [],
        tplCategory: table.tplCategory || 'single',
        subTableName: table.subTableName || '',
        subTableFkName: table.subTableFkName || '',
        subColumnsVo: []
      }))
      total.value = res.total
      queryParams.value.total = res.total
    } else {
      tables.value = []
      total.value = 0
      queryParams.value.total = 0
    }
  } catch (error) {
    console.error('加载表列表失败:', error)
    ElMessage.error('加载表列表失败')
    tables.value = []
    total.value = 0
    queryParams.value.total = 0
  } finally {
    loading.value = false
  }
}

const handleNext = () => {
  console.log("handleNext selectedTable.value",selectedTable.value)
  if (activeStep.value === 0 && !selectedTable.value) {
    ElMessage.warning('请先选择数据表')
    return
  }
  activeStep.value++
}

const handlePrev = () => {
  activeStep.value--
}

const handleGenerate = () => {
  if (!selectedTable.value) {
    ElMessage.warning('请选择数据表')
    return
  }

  const table = selectedTable.value

  // 构建表单配置
  const formConfig: FormConfig = {
    labelWidth: '100px',
    labelPosition: 'right',
    size: 'default',
    formName: table.tableComment || table.tableName,
    formDesc: table.tableComment || '通过表单设计器生成的自定义表单',
    tableInfo: {
      mainTable: {
        tableName: table.tableName,
        tableComment: table.tableComment,
        columns: table.columnsVo.map(col => ({
          columnName: col.columnName,
          columnType: col.columnType,
          columnComment: col.columnComment,
          isNullable: col.isNullable,
          isPk: col.isPk,
          dictType: col.dictType,
          htmlType: col.htmlType
        }))
      }
    }
  }

  // 如果是主子表，添加子表信息
  if (table.subTableName && table.subColumnsVo) {
    formConfig.tableInfo.subTable = {
      tableName: table.subTableName,
      tableComment: table.tableComment || table.subTableName,
      fkName: table.subTableFkName,
      columns: table.subColumnsVo.map(col => ({
        columnName: col.columnName,
        columnType: col.columnType,
        columnComment: col.columnComment,
        isNullable: col.isNullable,
        isPk: col.isPk,
        dictType: col.dictType,
        htmlType: col.htmlType
      }))
    }
  }

  // 构建表单内容
  const formContent = {
    formConfig,
    formComponents: [] as FormComponent[],
    masterSubTableData: {}
  }

  // 判断是单表还是主子表
  if (table.subTableName) {
    // 主子表处理
    const mainComponents = table.columnsVo
      .filter((column: ColumnVO) => {
        return selectedFields.value[column.columnName] && column.insert && column.edit && column.isPk != '1'
      })
      .map((column: ColumnVO) => {
        let type = 'input'
        let props: any = {
          placeholder: `请输入${column.columnComment || column.columnName}`
        }

        // popup优先判断
        if (column.htmlType === 'popup') {
          type = 'popup'
          props = {
            ...props,
            dictType: column.dictType || column.columnName || 'popup',
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined,
            placeholder: `请选择${column.columnComment || column.columnName}`
          }
        } else if (column.htmlType === 'select' && column.dictType) {
          type = 'select'
          props = {
            ...props,
            placeholder: `请选择${column.columnComment || column.columnName}`,
            dataSource: 'dict',
            dictType: column.dictType,
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined
          }
        } else if (column.htmlType === 'radio' && column.dictType) {
          type = 'radio'
          props = {
            ...props,
            dataSource: 'dict',
            dictType: column.dictType,
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined
          }
        } else if (column.htmlType === 'checkbox' && column.dictType) {
          type = 'checkbox'
          props = {
            ...props,
            dataSource: 'dict',
            dictType: column.dictType,
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined
          }
        } else if (column.htmlType === 'switch' && column.dictType) {
          type = 'switch'
          props = {
            activeText: '是',
            inactiveText: '否',
            activeValue: true,
            inactiveValue: false
          }
        } else if (column.htmlType === 'textarea') {
          type = 'textarea'
          props = {
            ...props,
            rows: 4
          }
        } else if (column.htmlType === 'date') {
          type = 'date'
          props = {
            ...props,
            type: 'date',
            format: 'YYYY-MM-DD',
            valueFormat: 'YYYY-MM-DD'
          }
        } else if (column.htmlType === 'datetime') {
          type = 'datetime'
          props = {
            ...props,
            type: 'datetime',
            format: 'YYYY-MM-DD HH:mm:ss',
            valueFormat: 'YYYY-MM-DD HH:mm:ss'
          }
        } else {
          // 默认使用输入框
          type = 'input'
          if (column.columnType.includes('int') || column.columnType.includes('decimal')) {
            props.type = 'number'
          }
          if (column.columnType.includes('longtext')){
            props.type = 'textarea'
            props.rows = 4
            type = 'textarea'
          }
          if (column.columnType.includes('text')){
            props.type = 'textarea'
            props.rows = 4
            type = 'textarea'
          }
          if (column.columnType.includes('blob')){
            props.type = 'editor'
            type = 'editor'
          }
          if (column.columnType.includes('longblob')){
            props.type = 'editor'
            type = 'editor'
          }
        }

        // 从数据库的 onChange 字段动态获取联动规则配置
        if (column.onChange) {
          try {
            const onChangeConfig = JSON.parse(column.onChange)
            
            // 检查是否包含编码生成规则
            if (onChangeConfig.onCodeGenerate) {
              props = {
                ...props,
                onCodeGenerate: onChangeConfig.onCodeGenerate
              }
            }
            
            // 检查其他联动规则配置
            if (onChangeConfig.type === 'summary') {
              // 汇总类型配置
              props = {
                ...props,
                summary: onChangeConfig.summary || {},
                onChange: onChangeConfig
              }
            } else if (onChangeConfig.type === 'calculate') {
              // 计算类型配置
              props = {
                ...props,
                onChange: onChangeConfig
              }
            } else if (onChangeConfig.type) {
              // 其他类型配置
              props = {
                ...props,
                onChange: onChangeConfig
              }
            }
          } catch (e) {
            console.error(`解析主表字段 ${column.columnName} 的 onChange 配置失败:`, e)
          }
        }

        const component: FormComponent = {
          id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          label: column.columnComment || column.columnName,
          field: column.columnName,
          required: column.isNullable === 'NO',
          type,
          props,
          dbInfo: {
            tableName: table.tableName,
            columnName: column.columnName,
            columnType: column.columnType,
            columnComment: column.columnComment,
            isNullable: column.isNullable
          },
          isMaster: true,
          masterField: '',
          subTableName: table.subTableName,
          subTableColumns: [],
          dictType: column.dictType,
          dictFields: column.dictFields,
          mapping: column.mapping,
          showDisplay: !!column.dictType
        }

        return component
      })

    formContent.formComponents = [...mainComponents]

    // 如果有子表，添加子表组件
    if (table.subColumnsVo && table.subColumnsVo.length > 0) {
      const subTableComponent: FormComponent = {
        id: `subtable_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        type: 'subtable',
        label: table.subTableName || '子表',
        field: table.subTableName || '',
        props: {
          placeholder: '子表'
        },
        dbInfo: {
          tableName: table.subTableName || '',
          columnName: '',
          columnType: '',
          columnComment: '',
          isNullable: 'YES'
        },
        required: false,
        isMaster: false,
        masterField: '',
        subTableName: table.subTableName || '',
        subTableColumns: table.subColumnsVo
          .filter(column => {
            const fkName = table.subTableFkName || ''
            return selectedSubFields.value[column.columnName] && 
                   column.insert && 
                   column.edit && 
                   column.isPk != '1' && 
                   !column.columnName.includes(fkName)
          })
          .map((column: ColumnVO) => {
            let type = 'input'
            let props: any = {
              placeholder: `请输入${column.columnComment || column.columnName}`
            }

            // popup优先判断
            if (column.htmlType === 'popup') {
              type = 'popup'
              props = {
                ...props,
                dictType: column.dictType || column.columnName || 'popup',
                dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
                mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
                onChange: column.onChange ? JSON.parse(column.onChange) : undefined,
                placeholder: `请选择${column.columnComment || column.columnName}`
              }
            } else if (column.htmlType === 'select' && column.dictType) {
              type = 'select'
              props = {
                ...props,
                placeholder: `请选择${column.columnComment || column.columnName}`,
                dataSource: 'dict',
                dictType: column.dictType,
                dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
                mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
                onChange: column.onChange ? JSON.parse(column.onChange) : undefined
              }
            } else if (column.htmlType === 'radio' && column.dictType) {
              type = 'radio'
              props = {
                ...props,
                dataSource: 'dict',
                dictType: column.dictType,
                dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
                mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
                onChange: column.onChange ? JSON.parse(column.onChange) : undefined
              }
            } else if (column.htmlType === 'checkbox' && column.dictType) {
              type = 'checkbox'
              props = {
                ...props,
                dataSource: 'dict',
                dictType: column.dictType,
                dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
                mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
                onChange: column.onChange ? JSON.parse(column.onChange) : undefined
              }
            } else if (column.htmlType === 'switch' && column.dictType) {
              type = 'switch'
              props = {
                activeText: '是',
                inactiveText: '否',
                activeValue: true,
                inactiveValue: false
              }
            } else if (column.htmlType === 'textarea') {
              type = 'textarea'
              props = {
                ...props,
                rows: 4
              }
            } else if (column.htmlType === 'datetime') {
              type = 'datetime'
              props = {
                ...props,
                type: 'datetime',
                format: 'YYYY-MM-DD HH:mm:ss',
                valueFormat: 'YYYY-MM-DD HH:mm:ss'
              }
            } else {
              // 默认使用输入框
              type = 'input'
              if (column.columnType.includes('int') || column.columnType.includes('decimal')) {
                props.type = 'number'
              }
              if (column.columnType.includes('longtext')){
                props.type = 'textarea'
                props.rows = 4
                type = 'textarea'
              }
              if (column.columnType.includes('text')){
                props.type = 'textarea'
                props.rows = 4
                type = 'textarea'
              }
              if (column.columnType.includes('blob')){
                props.type = 'editor'
                type = 'editor'
              }
              if (column.columnType.includes('longblob')){
                props.type = 'editor'
                type = 'editor'
              }
            }

            const subComponent: SubTableColumn = {
              id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
              label: column.columnComment || column.columnName,
              field: column.columnName,
              type,
              props: {},  // 初始化为空对象
              required: column.isNullable === 'NO',
              dbInfo: {
                tableName: table.subTableName || '',
                columnName: column.columnName,
                columnType: column.columnType,
                columnComment: column.columnComment,
                isNullable: column.isNullable
              },
              dictType: column.dictType,
              dictFields: column.dictFields,
              mapping: column.mapping,
              showDisplay: !!column.dictType
            }
            
            // 设置子表组件的 props，包含所有必要的配置
            subComponent.props = {
              placeholder: `请输入${column.columnComment || column.columnName}`,
              // 如果是 popup 类型，添加 popup 相关配置
              ...(type === 'popup' ? {
                dictFields: column.dictFields ? JSON.parse(column.dictFields) : {},
                mapping: column.mapping ? JSON.parse(column.mapping) : {},
                multiple: true  // 子表 popup 默认启用多选模式
              } : {}),
              // 添加联动规则配置（从 onChange 字段获取）
              onSubChange: column.onChange ? JSON.parse(column.onChange) : undefined,
              // 添加其他可能的配置
              onChange: column.onChange ? JSON.parse(column.onChange) : undefined,
              // 临时硬编码测试：为 price 字段添加联动配置
              ...(column.columnName === 'price' ? {
                onSubChange: {
                  type: "calculate",
                  formula: "{{price}} * {{num}}",
                  targetFields: ["zong_price"]
                }
              } : {})
            }
            
            // 调试信息：检查 onChange 配置
            console.log(`子表字段 ${column.columnName} 的配置:`, {
              columnName: column.columnName,
              onChange: column.onChange,
              props: subComponent.props
            })
            
            // 特别检查 price 字段的配置传递
            if (column.columnName === 'price') {
              console.log('=== PRICE 字段配置传递检查 ===')
              console.log('原始 column 对象:', column)
              console.log('subComponent.props 设置前:', subComponent.props)
              console.log('硬编码配置:', {
                type: "calculate",
                formula: "{{price}} * {{num}}",
                targetFields: ["zong_price"]
              })
              console.log('最终 subComponent.props:', subComponent.props)
              console.log('=== PRICE 字段配置传递检查结束 ===')
            }
            


            return subComponent
          })
      }

      formContent.formComponents.push(subTableComponent)
    }
  } else {
    // 单表处理
    const components = table.columnsVo
      .filter((column: ColumnVO) => {
        return selectedFields.value[column.columnName] && column.insert && column.edit && column.isPk != '1'
      })
      .map((column: ColumnVO) => {
        let type = 'input'
        let props: any = {
          placeholder: `请输入${column.columnComment || column.columnName}`
        }

        // popup优先判断
        if (column.htmlType === 'popup') {
          type = 'popup'
          props = {
            ...props,
            dictType: column.dictType || column.columnName || 'popup',
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined,
            placeholder: `请选择${column.columnComment || column.columnName}`
          }
        } else if (column.htmlType === 'select' && column.dictType) {
          type = 'select'
          props = {
            ...props,
            placeholder: `请选择${column.columnComment || column.columnName}`,
            dataSource: 'dict',
            dictType: column.dictType,
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined
          }
        } else if (column.htmlType === 'radio' && column.dictType) {
          type = 'radio'
          props = {
            ...props,
            dataSource: 'dict',
            dictType: column.dictType,
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined
          }
        } else if (column.htmlType === 'checkbox' && column.dictType) {
          type = 'checkbox'
          props = {
            ...props,
            dataSource: 'dict',
            dictType: column.dictType,
            dictFields: column.dictFields ? JSON.parse(column.dictFields) : undefined,
            mapping: column.mapping ? JSON.parse(column.mapping) : undefined,
            onChange: column.onChange ? JSON.parse(column.onChange) : undefined
          }
        } else if (column.htmlType === 'switch' && column.dictType) {
          type = 'switch'
          props = {
            activeText: '是',
            inactiveText: '否',
            activeValue: true,
            inactiveValue: false
          }
        } else if (column.htmlType === 'textarea') {
          type = 'textarea'
          props = {
            ...props,
            rows: 4
          }
        } else if (column.htmlType === 'date') {
          type = 'date'
          props = {
            ...props,
            type: 'date',
            format: 'YYYY-MM-DD',
            valueFormat: 'YYYY-MM-DD'
          }
        } else if (column.htmlType === 'datetime') {
          type = 'datetime'
          props = {
            ...props,
            type: 'datetime',
            format: 'YYYY-MM-DD HH:mm:ss',
            valueFormat: 'YYYY-MM-DD HH:mm:ss'
          }
        } else {
          // 默认使用输入框
          type = 'input'
          if (column.columnType.includes('int') || column.columnType.includes('decimal')) {
            props.type = 'number'
          }
          if (column.columnType.includes('longtext')){
            props.type = 'textarea'
            props.rows = 4
            type = 'textarea'
          }
          if (column.columnType.includes('text')){
            props.type = 'textarea'
            props.rows = 4
            type = 'textarea'
          }
          if (column.columnType.includes('blob')){
            props.type = 'editor'
            type = 'editor'
          }
          if (column.columnType.includes('longblob')){
            props.type = 'editor'
            type = 'editor'
          }
        }

        const component: FormComponent = {
          id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          label: column.columnComment || column.columnName,
          field: column.columnName,
          required: column.isNullable === 'NO',
          type,
          props,
          dbInfo: {
            tableName: table.tableName,
            columnName: column.columnName,
            columnType: column.columnType,
            columnComment: column.columnComment,
            isNullable: column.isNullable
          },
          isMaster: true,
          masterField: '',
          subTableName: '',
          subTableColumns: [],
          dictType: column.dictType,
          dictFields: column.dictFields,
          mapping: column.mapping,
          showDisplay: !!column.dictType
        }

        return component
      })

    formContent.formComponents.push(...components)
  }

  // 设置表单数据
  const formData = {
    formName: formConfig.formName,
    formDesc: formConfig.formDesc,
    formContent: JSON.stringify(formContent),
    formType: 'PC'
  }

  console.log('生成的表单配置:', formConfig) // 添加日志
  console.log('生成的表单内容:', formContent) // 添加日志

  emit('generate', formData)
  visible.value = false
}

// 修改 handleCancel 方法
const handleCancel = () => {
  visible.value = false
  activeStep.value = 0
  selectedTable.value = null
  formData.value = {}
  selectedTableName.value = ''
}

// 打开对话框时重置所有状态，确保每次新建从头开始
const resetAllState = () => {
  activeStep.value = 0
  selectedTable.value = null
  selectedTableName.value = ''
  formData.value = {}
  subTableData.value = []
  selectedFields.value = {}
  selectedSubFields.value = {}
}

watch(visible, (v) => {
  if (v) {
    resetAllState()
    loadTables()
  }
})

// 修改添加子表行方法
const handleAddSubTableRow = () => {
  if (!selectedTable.value?.subColumnsVo) return
  
  const newRow: Record<string, any> = {}
    selectedTable.value.subColumnsVo.forEach(column => {
      if (selectedSubFields.value[column.columnName]) {
        newRow[column.columnName] = ''
      }
    })
  subTableData.value = [...subTableData.value, newRow]
}

// 删除子表行
const handleDeleteSubTableRow = (index: number) => {
  subTableData.value = subTableData.value.filter((_, i) => i !== index)
}

// 初始化子表数据
const initSubTableData = () => {
  if (!selectedTable.value?.subColumnsVo) return
  
  const newRow: Record<string, any> = {}
  selectedTable.value.subColumnsVo.forEach(column => {
    if (selectedSubFields.value[column.columnName]) {
      newRow[column.columnName] = ''
    }
  })
  subTableData.value = [newRow]
}

// 监听选中字段变化
watch([selectedSubFields], () => {
  if (selectedTable.value?.subColumnsVo) {
    initSubTableData()
}
}, { deep: true })

// 监听选中表变化
watch(() => selectedTable.value, (newVal) => {
  if (newVal?.subColumnsVo) {
    initFieldStates(newVal)
  }
}, { deep: true })

// 在组件挂载时初始化
onMounted(() => {
  loadTables()
})

// 添加加载字典数据的方法
const loadDictData = async (column: ColumnVO) => {
  if (column.dictType && !dictOptions.value[column.dictType]) {
    const options = await getDictOptions(column.dictType)
    dictOptions.value[column.dictType] = options
  }
}

function getDefaultOptions(type: string) {
  if (type === 'select' || type === 'radio') {
    return [
      { label: '选项1', value: '1' },
      { label: '选项2', value: '2' }
    ]
  }
  if (type === 'checkbox') {
    return [
      { label: '选项1', value: '1' },
      { label: '选项2', value: '2' },
      { label: '选项3', value: '3' }
    ]
  }
  return []
}

const getColumnType = (columnType: string): string => {
  if (columnType.includes('text') || columnType.includes('varchar')) {
    return 'input'
  } else if (columnType.includes('int') || columnType.includes('decimal')) {
    return 'number'
  } else if (columnType.includes('tinyint(1)')) {
    return 'checkbox'
  } else if (columnType.includes('datetime') || columnType.includes('date')) {
    return 'date'
  } else if (columnType.includes('longblob')) {
    return 'editor'
  }
  return 'input'
}

defineExpose({
  visible,
  tables,
  loadTables
})
</script>

<style lang="scss" scoped>
.step-content {
  margin-top: 20px;
  height: 500px; // 设置固定高度
  overflow: auto; // 添加滚动条
  display: flex;
  flex-direction: column;
}

.table-preview, .form-preview {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  height: 100%; // 占满父容器高度
  overflow: auto; // 添加滚动条
  
  h3 {
    margin-bottom: 20px;
    font-size: 16px;
    font-weight: 500;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

// 修改表格容器样式
.table-container {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  margin-bottom: 20px;

  .el-table {
    flex: 1;
    overflow: auto;
    height: calc(100% - 50px); // 减去分页容器的高度
  }

  .pagination-container {
    margin-top: 10px;
    padding: 10px 0;
    display: flex;
    justify-content: flex-end;
    height: 50px; // 设置固定高度
    background-color: #fff; // 确保背景色为白色
    position: relative; // 确保分页始终在底部
    z-index: 1; // 确保分页显示在最上层
  }
}

// 添加表单预览样式
.form-preview {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  height: 100%;
  overflow: auto;
  
  .form-section {
    margin-bottom: 30px;
    
    &:last-child {
      margin-bottom: 0;
    }
    
    h3 {
      margin-bottom: 20px;
      font-size: 16px;
      font-weight: 500;
      color: #303133;
      padding-bottom: 10px;
      border-bottom: 1px solid #dcdfe6;
    }
  }
}

.subtable-actions {
  margin-top: 15px;
  text-align: right;
}

.el-table {
  margin-bottom: 15px;
}

.el-table {
  .el-checkbox {
    margin-right: 0;
  }
}

.el-checkbox.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dict-label {
  margin-left: 10px;
  color: #606266;
}

.component-type {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 5px;
  
  .el-tag {
    font-size: 12px;
  }
}

.option-form-item .el-form-item__label {
  display: block;
  width: 100%;
  text-align: left;
  margin-bottom: 2px;
}
</style> 