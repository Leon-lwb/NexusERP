<template>
  <el-dialog
    :model-value="visible"
    title="选择数据表"
    width="70%"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form :model="queryParams" :inline="true" size="small">
      <el-form-item label="表名称">
        <el-input v-model="queryParams.tableName" placeholder="请输入表名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="表描述">
        <el-input v-model="queryParams.tableComment" placeholder="请输入表描述" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">搜索</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="tableData"
      highlight-current-row
      @current-change="handleCurrentChange"
      @selection-change="handleSelectionChange"
      style="width: 100%"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="表名称" prop="tableName" :show-overflow-tooltip="true" />
      <el-table-column label="表描述" prop="tableComment" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" prop="createTime" width="180" />
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <div v-if="selectedTable && columns.length > 0" style="margin-top: 16px">
      <el-divider>字段选择</el-divider>
      <el-checkbox v-model="checkAll" :indeterminate="isIndeterminate" @change="handleCheckAllChange">
        全选
      </el-checkbox>
      <el-checkbox-group v-model="selectedFields" style="margin-top: 8px">
        <el-checkbox
          v-for="col in columns"
          :key="col.columnName"
          :label="col.columnName"
        >
          {{ col.columnComment || col.columnName }}
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <template #footer>
      <el-button type="primary" @click="handleGenerate" :disabled="!selectedTable || selectedFields.length === 0">
        生成表单
      </el-button>
      <el-button @click="handleClose">取消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { list } from '@/api/online'
import { getTableColumns } from '@/api/tool/gen'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'TableSelector' })

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'generate'])

const loading = ref(false)
const total = ref(0)
const tableData = ref<any[]>([])
const selectedTable = ref<any>(null)
const selectedRows = ref<any[]>([])
const columns = ref<any[]>([])
const selectedFields = ref<string[]>([])
const checkAll = ref(false)
const isIndeterminate = ref(false)
const tableRef = ref()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  tableName: '',
  tableComment: '',
  dataName: ''
})

const getList = async () => {
  loading.value = true
  try {
    const res = await list(queryParams)
    tableData.value = res.rows || []
    total.value = res.total || 0
  } catch (error) {
    console.error('获取表列表失败:', error)
    ElMessage.error('获取表列表失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  queryParams.tableName = ''
  queryParams.tableComment = ''
  queryParams.pageNum = 1
  handleQuery()
}

const handleCurrentChange = (row: any) => {
  if (row) {
    selectedTable.value = row
    loadColumns(row.tableName)
  }
}

const handleSelectionChange = (rows: any[]) => {
  selectedRows.value = rows
  if (rows.length === 1) {
    selectedTable.value = rows[0]
    loadColumns(rows[0].tableName)
  }
}

const loadColumns = async (tableName: string) => {
  try {
    const res = await getTableColumns(tableName)
    if (res && res.data && Array.isArray(res.data)) {
      columns.value = res.data
      selectedFields.value = res.data.map((c: any) => c.columnName)
      checkAll.value = true
      isIndeterminate.value = false
    } else if (Array.isArray(res)) {
      columns.value = res
      selectedFields.value = res.map((c: any) => c.columnName)
      checkAll.value = true
      isIndeterminate.value = false
    } else {
      columns.value = []
      selectedFields.value = []
    }
  } catch (error) {
    console.error('获取表字段失败:', error)
    columns.value = []
    selectedFields.value = []
  }
}

const handleCheckAllChange = (val: boolean) => {
  selectedFields.value = val ? columns.value.map((c: any) => c.columnName) : []
  isIndeterminate.value = false
}

watch(selectedFields, (val) => {
  if (columns.value.length === 0) return
  checkAll.value = val.length === columns.value.length
  isIndeterminate.value = val.length > 0 && val.length < columns.value.length
})

const handleGenerate = () => {
  if (!selectedTable.value || selectedFields.value.length === 0) {
    ElMessage.warning('请选择数据表和字段')
    return
  }

  const selectedCols = columns.value.filter((c: any) => selectedFields.value.includes(c.columnName))
  const formComponents = selectedCols.map((col: any) => ({
    id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    type: 'input',
    label: col.columnComment || col.columnName,
    field: col.columnName,
    required: col.isNullable === 'NO',
    props: { placeholder: `请输入${col.columnComment || col.columnName}`, clearable: true },
    dbInfo: {
      tableName: selectedTable.value.tableName,
      columnName: col.columnName,
      columnType: col.columnType,
      columnComment: col.columnComment,
      isNullable: col.isNullable
    },
    isMaster: true,
    masterField: '',
    subTableName: '',
    subTableColumns: [],
    dictType: '',
    dictFields: '',
    mapping: '',
    showDisplay: false
  }))

  const formContent = {
    formConfig: {
      labelWidth: '100px',
      labelPosition: 'right',
      size: 'default',
      formName: selectedTable.value.tableComment || selectedTable.value.tableName,
      formDesc: `基于${selectedTable.value.tableComment || selectedTable.value.tableName}生成的表单`,
      tableInfo: {
        mainTable: {
          tableName: selectedTable.value.tableName,
          tableComment: selectedTable.value.tableComment,
          columns: selectedCols.map((col: any) => ({
            columnName: col.columnName,
            columnType: col.columnType,
            columnComment: col.columnComment,
            isNullable: col.isNullable,
            isRequired: col.isNullable === 'NO' ? '1' : '0',
            htmlType: 'input'
          }))
        }
      }
    },
    formComponents,
    masterSubTableData: {}
  }

  const formData = {
    formName: selectedTable.value.tableComment || selectedTable.value.tableName,
    formDesc: `基于${selectedTable.value.tableComment || selectedTable.value.tableName}生成的表单`,
    formContent: JSON.stringify(formContent)
  }

  emit('generate', formData)
  emit('update:visible', false)
}

const handleClose = () => {
  emit('update:visible', false)
}

watch(() => props.visible, (val) => {
  if (val) {
    selectedTable.value = null
    selectedRows.value = []
    columns.value = []
    selectedFields.value = []
    checkAll.value = false
    isIndeterminate.value = false
    queryParams.pageNum = 1
    getList()
  }
})
</script>
