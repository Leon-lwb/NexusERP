<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="条码" prop="barcodeCode">
        <el-input 
          v-model="queryParams.barcodeCode" 
          placeholder="请输入条码" 
          clearable 
          @keyup.enter="handleQuery" 
        />
      </el-form-item>
      <el-form-item label="配置ID" prop="configId">
        <el-input-number 
          v-model="queryParams.configId" 
          :min="0" 
        />
      </el-form-item>
      <el-form-item label="业务类型" prop="bizType">
        <el-input 
          v-model="queryParams.bizType" 
          clearable 
          placeholder="请输入业务类型" 
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 160px">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="USED" value="USED" />
          <el-option label="EXPIRED" value="EXPIRED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button 
          type="primary" 
          plain 
          icon="Plus" 
          @click="handleGenerate" 
          v-hasPermi="['wm:barcode:add']"
        >
          生成条码
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="success" 
          plain 
          icon="Edit" 
          :disabled="single" 
          @click="handleUpdate" 
          v-hasPermi="['wm:barcode:edit']"
        >
          修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="danger" 
          plain 
          icon="Delete" 
          :disabled="multiple" 
          @click="handleDelete" 
          v-hasPermi="['wm:barcode:remove']"
        >
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="warning" 
          plain 
          icon="Download" 
          @click="handleExport" 
          v-hasPermi="['wm:barcode:export']"
        >
          导出
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="条码" prop="barcodeCode" align="center" />
      <el-table-column label="配置编码" prop="configCode" align="center" />
      <el-table-column label="业务类型" prop="bizType" align="center" />
      <el-table-column label="物料ID" prop="materialId" align="center" />
      <el-table-column label="库存ID" prop="stockId" align="center" />
      <el-table-column label="状态" prop="status" align="center" />
      <el-table-column label="生成时间" prop="generateTime" align="center" width="180">
        <template #default="scope">
          {{ parseTime(scope.row.generateTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="180">
        <template #default="scope">
          <el-button 
            link 
            type="primary" 
            icon="Edit" 
            @click="handleUpdate(scope.row)" 
            v-hasPermi="['wm:barcode:edit']"
          >
            修改
          </el-button>
          <el-button 
            link 
            type="primary" 
            icon="Delete" 
            @click="handleDelete(scope.row)" 
            v-hasPermi="['wm:barcode:remove']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination 
      v-show="total > 0" 
      :total="total" 
      v-model:page="queryParams.pageNum" 
      v-model:limit="queryParams.pageSize" 
      @pagination="getList" 
    />

    <!-- 编辑/新增条码对话框 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="条码" prop="barcodeCode">
          <el-input v-model="form.barcodeCode" />
        </el-form-item>
        <el-form-item label="配置ID" prop="configId">
          <el-input-number v-model="form.configId" :min="0" />
        </el-form-item>
        <el-form-item label="配置编码" prop="configCode">
          <el-input v-model="form.configCode" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-input v-model="form.bizType" />
        </el-form-item>
        <el-form-item label="业务ID" prop="bizId">
          <el-input-number v-model="form.bizId" :min="0" />
        </el-form-item>
        <el-form-item label="业务行ID" prop="bizLineId">
          <el-input-number v-model="form.bizLineId" :min="0" />
        </el-form-item>
        <el-form-item label="物料ID" prop="materialId">
          <el-input-number v-model="form.materialId" :min="0" />
        </el-form-item>
        <el-form-item label="批次ID" prop="batchId">
          <el-input-number v-model="form.batchId" :min="0" />
        </el-form-item>
        <el-form-item label="库存ID" prop="stockId">
          <el-input-number v-model="form.stockId" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-input v-model="form.status" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 生成条码对话框 -->
    <el-dialog title="生成条码" v-model="generateOpen" width="520px" append-to-body>
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="配置编码">
          <el-input 
            v-model="generateForm.configCode" 
            placeholder="WM_RECEIVE_DEFAULT" 
          />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input 
            v-model="generateForm.bizType" 
            placeholder="RECEIVE_IN" 
          />
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input-number 
            v-model="generateForm.bizId" 
            :min="0" 
          />
        </el-form-item>
        <el-form-item label="物料ID">
          <el-input-number 
            v-model="generateForm.materialId" 
            :min="0" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitGenerate">生成</el-button>
        <el-button @click="generateOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Barcode">
// 导入条码相关的API方法
import { 
  addBarcode, 
  delBarcode, 
  generateBarcode, 
  getBarcode, 
  listBarcode, 
  updateBarcode 
} from '@/api/erp/wm/barcode'

// 获取当前实例
const { proxy } = getCurrentInstance() as any

// 定义响应式数据
const list = ref([])                 // 条码列表
const loading = ref(false)           // 加载状态
const showSearch = ref(true)         // 显示搜索区域
const ids = ref<any[]>([])          // 选中的ID数组
const single = ref(true)             // 单选状态
const multiple = ref(true)           // 多选状态
const total = ref(0)                 // 总记录数
const open = ref(false)              // 编辑对话框显示状态
const generateOpen = ref(false)      // 生成对话框显示状态
const title = ref('')                // 对话框标题

// 定义页面数据
const data = reactive({
  // 查询参数
  queryParams: { 
    pageNum: 1, 
    pageSize: 10, 
    barcodeCode: null, 
    configId: null, 
    bizType: null, 
    bizId: null, 
    materialId: null, 
    stockId: null, 
    status: null 
  },
  // 表单数据
  form: { 
    barcodeId: undefined, 
    barcodeCode: '', 
    configId: undefined, 
    configCode: '', 
    bizType: '', 
    bizId: undefined, 
    bizLineId: undefined, 
    materialId: undefined, 
    batchId: undefined, 
    stockId: undefined, 
    status: 'ACTIVE', 
    remark: '' 
  },
  // 生成条码表单数据
  generateForm: { 
    configCode: 'WM_RECEIVE_DEFAULT', 
    bizType: 'RECEIVE_IN', 
    bizId: undefined, 
    materialId: undefined 
  },
  // 表单验证规则
  rules: { 
    barcodeCode: [{ required: true, message: '条码不能为空', trigger: 'blur' }] 
  }
})

// 解构数据
const { queryParams, form, rules, generateForm } = toRefs(data)

/**
 * 获取条码列表
 */
function getList(){
  loading.value = true
  listBarcode(queryParams.value).then((res: any) => {
    list.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

/**
 * 搜索按钮操作
 */
function handleQuery(){
  queryParams.value.pageNum = 1
  getList()
}

/**
 * 重置按钮操作
 */
function resetQuery(){
  proxy.resetForm('queryRef')
  handleQuery()
}

/**
 * 多选框选中数据
 */
function handleSelectionChange(selection: any[]){
  ids.value = selection.map(i => i.barcodeId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/**
 * 生成条码按钮操作
 */
function handleGenerate(){
  generateOpen.value = true
}

/**
 * 提交生成条码
 */
function submitGenerate(){
  generateBarcode(generateForm.value).then((res: any) => {
    proxy.$modal.msgSuccess(`生成成功：${res.data}`)
    generateOpen.value = false
    getList()
  })
}

/**
 * 修改按钮操作
 * @param row 选中的条码数据
 */
function handleUpdate(row: any){
  const id = row?.barcodeId || ids.value[0]
  getBarcode(id).then((res: any) => {
    form.value = res.data
    open.value = true
    title.value = '修改条码实例'
  })
}

/**
 * 删除按钮操作
 * @param row 选中的条码数据
 */
function handleDelete(row: any){
  const delIds = row?.barcodeId || ids.value
  proxy.$modal.confirm('是否确认删除条码实例？').then(() =>
    delBarcode(delIds)
  ).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  })
}

/**
 * 导出按钮操作
 */
function handleExport(){
  proxy.download('/erp/wm/barcode/export', { ...queryParams.value }, `barcode_${Date.now()}.xlsx`)
}

/**
 * 提交表单
 */
function submitForm(){
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (!valid) return
    const api = form.value.barcodeId ? updateBarcode : addBarcode
    api(form.value).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      open.value = false
      getList()
    })
  })
}

// 初始化数据
getList()
</script>