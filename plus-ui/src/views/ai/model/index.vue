<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AI 模型配置</span>
          <el-button type="primary" @click="handleAdd">新增模型</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="list">
        <el-table-column label="所属厂商" prop="providerName" />
        <el-table-column label="模型名称" prop="modelName" />
        <el-table-column label="显示名称" prop="displayName" />
        <el-table-column label="类型" prop="modelType" />
        <el-table-column label="最大Token" prop="maxTokens" />
        <el-table-column label="温度" prop="temperature" />
        <el-table-column label="默认" prop="isDefault">
          <template #default="{ row }">
            <el-tag :type="row.isDefault === '1' ? 'success' : 'info'">{{ row.isDefault === '1' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'success' : 'danger'">{{ row.status === '1' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="输入价格" prop="inputPricePerMillion" width="120">
          <template #default="{ row }">
            {{ row.inputPricePerMillion ? row.inputPricePerMillion + ' 元/M' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="输出价格" prop="outputPricePerMillion" width="120">
          <template #default="{ row }">
            {{ row.outputPricePerMillion ? row.outputPricePerMillion + ' 元/M' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="handleSetDefault(row)" :disabled="row.isDefault === '1'">设为默认</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属厂商" prop="providerId">
          <el-select v-model="form.providerId" placeholder="请选择厂商" style="width: 100%">
            <el-option v-for="item in providerList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" placeholder="请选择模型类型" style="width: 100%">
            <el-option label="CHAT" value="CHAT" />
            <el-option label="EMBEDDING" value="EMBEDDING" />
            <el-option label="RERANK" value="RERANK" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大Token" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="温度参数" prop="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="是否默认" prop="isDefault">
          <el-switch v-model="form.isDefault" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="1">正常</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-divider content-position="left">定价配置</el-divider>
        <el-form-item label="输入价格" prop="inputPricePerMillion">
          <el-input-number v-model="form.inputPricePerMillion" :min="0" :precision="6" controls-position="right" placeholder="元/百万Token" style="width: 100%" />
        </el-form-item>
        <el-form-item label="输出价格" prop="outputPricePerMillion">
          <el-input-number v-model="form.outputPricePerMillion" :min="0" :precision="6" controls-position="right" placeholder="元/百万Token" style="width: 100%" />
        </el-form-item>
        <el-form-item label="币种" prop="currency">
          <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
            <el-option label="人民币（CNY）" value="CNY" />
            <el-option label="美元（USD）" value="USD" />
          </el-select>
        </el-form-item>
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

<script setup lang="ts">
import { listModel, getModel, addModel, updateModel, delModel, setDefaultModel, listProvider } from '@/api/ai/model'
import type { AiModel } from '@/api/ai/model'

const { proxy } = getCurrentInstance() as ComponentInternalInstance

const loading = ref(false)
const list = ref<AiModel[]>([])
const total = ref(0)
const providerList = ref<any[]>([])

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
})

const initFormData: AiModel = {
  id: undefined,
  providerId: undefined as unknown as number,
  modelName: '',
  displayName: '',
  modelType: '',
  maxTokens: undefined,
  temperature: undefined,
  isDefault: '0',
  status: '1',
  inputPricePerMillion: undefined,
  outputPricePerMillion: undefined,
  currency: 'CNY'
}

const data = reactive<PageData<AiModel, any>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10
  },
  rules: {
    providerId: [{ required: true, message: '请选择所属厂商', trigger: 'change' }],
    modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
    modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

const formRef = ref<ElFormInstance>()

/** 查询模型列表 */
const getList = async () => {
  loading.value = true
  try {
    const res = await listModel(queryParams.value)
    list.value = res.rows
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 加载厂商下拉选项 */
const getProviderList = async () => {
  const res = await listProvider()
  providerList.value = res.rows || res.data || []
}

/** 取消按钮 */
const cancel = () => {
  reset()
  dialog.visible = false
}

/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData }
  formRef.value?.resetFields()
}

/** 新增 */
const handleAdd = () => {
  reset()
  dialog.visible = true
  dialog.title = '新增模型'
}

/** 编辑 */
const handleEdit = async (row: AiModel) => {
  reset()
  const res = await getModel(row.id!)
  Object.assign(form.value, res.data)
  dialog.visible = true
  dialog.title = '编辑模型'
}

/** 提交表单 */
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.id ? await updateModel(form.value) : await addModel(form.value)
      proxy?.$modal.msgSuccess('操作成功')
      dialog.visible = false
      await getList()
    }
  })
}

/** 删除 */
const handleDelete = async (row: AiModel) => {
  await proxy?.$modal.confirm('是否确认删除模型"' + row.modelName + '"？')
  await delModel([row.id!])
  await getList()
  proxy?.$modal.msgSuccess('删除成功')
}

/** 设为默认 */
const handleSetDefault = async (row: AiModel) => {
  await setDefaultModel(row.id!)
  proxy?.$modal.msgSuccess('设置成功')
  await getList()
}

/** 弹窗打开时加载厂商列表 */
watch(() => dialog.visible, (val) => {
  if (val) {
    getProviderList()
  }
})

onMounted(() => {
  getList()
})
</script>
