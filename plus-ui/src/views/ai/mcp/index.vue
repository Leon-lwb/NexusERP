<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>MCP 工具管理</span>
          <div>
            <el-button type="info" @click="handleSync">同步内置工具</el-button>
            <el-button type="primary" @click="handleAdd">新增工具</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="list">
        <el-table-column label="工具名称" prop="toolName" min-width="150" />
        <el-table-column label="显示名称" prop="displayName" min-width="120" />
        <el-table-column label="描述" prop="description" min-width="250" show-overflow-tooltip />
        <el-table-column label="类型" prop="toolType" width="100">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.toolType)">{{ row.toolType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'ENABLED' ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button
              link
              type="danger"
              @click="handleDelete(row)"
              :disabled="row.toolType === 'BUILTIN'"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工具名称" prop="toolName">
          <el-input v-model="form.toolName" placeholder="请输入工具名称（snake_case）" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入工具描述（AI 理解用途）"
          />
        </el-form-item>
        <el-form-item label="工具类型" prop="toolType">
          <el-select v-model="form.toolType" placeholder="请选择工具类型" style="width: 100%" :disabled="isEdit">
            <el-option label="BUILTIN（内置工具）" value="BUILTIN" />
            <el-option label="MCP（MCP 服务工具）" value="MCP" />
            <el-option label="REMOTE（远程 HTTP 工具）" value="REMOTE" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.toolType !== 'BUILTIN'"
          label="配置 JSON"
          prop="configJson"
        >
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="6"
            placeholder='请输入配置 JSON，如：{"url": "http://example.com/mcp", "apiKey": "xxx"}'
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
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
import { listTool, addTool, updateTool, delTool, toggleToolStatus, syncBuiltinTools } from '@/api/ai/mcp'
import type { AiTool } from '@/api/ai/mcp'

const { proxy } = getCurrentInstance() as ComponentInternalInstance

const loading = ref(false)
const list = ref<AiTool[]>([])
const isEdit = ref(false)

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
})

const initFormData: AiTool = {
  id: undefined,
  toolName: '',
  displayName: '',
  description: '',
  toolType: 'BUILTIN',
  configJson: '',
  status: 'ENABLED'
}

const data = reactive<{ form: AiTool; rules: any }>({
  form: { ...initFormData },
  rules: {
    toolName: [
      { required: true, message: '请输入工具名称', trigger: 'blur' },
      { pattern: /^[a-z][a-z0-9_]*$/, message: '工具名称格式不正确（小写字母开头，仅允许小写字母、数字、下划线）', trigger: 'blur' }
    ],
    displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
    toolType: [{ required: true, message: '请选择工具类型', trigger: 'change' }]
  }
})

const { form, rules } = toRefs(data)

const formRef = ref<ElFormInstance>()

/** 获取类型标签颜色 */
const getTypeTagType = (type: string) => {
  const map: Record<string, string> = {
    BUILTIN: 'primary',
    MCP: 'success',
    REMOTE: 'warning'
  }
  return map[type] || 'info'
}

/** 查询工具列表 */
const getList = async () => {
  loading.value = true
  try {
    const res = await listTool()
    list.value = res.data || res
  } finally {
    loading.value = false
  }
}

/** 取消按钮 */
const cancel = () => {
  reset()
  dialog.visible = false
}

/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData }
  isEdit.value = false
  formRef.value?.resetFields()
}

/** 新增 */
const handleAdd = () => {
  reset()
  dialog.visible = true
  dialog.title = '新增工具'
}

/** 编辑 */
const handleEdit = (row: AiTool) => {
  reset()
  Object.assign(form.value, row)
  isEdit.value = true
  dialog.visible = true
  dialog.title = '编辑工具'
}

/** 提交表单 */
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      // 验证 JSON 格式
      if (form.value.configJson && form.value.toolType !== 'BUILTIN') {
        try {
          JSON.parse(form.value.configJson)
        } catch {
          proxy?.$modal.msgError('配置 JSON 格式不正确')
          return
        }
      }
      form.value.id ? await updateTool(form.value) : await addTool(form.value)
      proxy?.$modal.msgSuccess('操作成功')
      dialog.visible = false
      await getList()
    }
  })
}

/** 删除 */
const handleDelete = async (row: AiTool) => {
  if (row.toolType === 'BUILTIN') {
    proxy?.$modal.msgWarning('内置工具不允许删除')
    return
  }
  await proxy?.$modal.confirm('是否确认删除工具"' + row.displayName + '"？')
  await delTool([row.id!])
  await getList()
  proxy?.$modal.msgSuccess('删除成功')
}

/** 切换状态 */
const handleToggleStatus = async (row: AiTool) => {
  const newStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = newStatus === 'ENABLED' ? '启用' : '停用'
  await proxy?.$modal.confirm(`是否确认${action}工具"${row.displayName}"？`)
  await toggleToolStatus(row.id!, newStatus)
  await getList()
  proxy?.$modal.msgSuccess(`${action}成功`)
}

/** 同步内置工具 */
const handleSync = async () => {
  await proxy?.$modal.confirm('是否确认同步内置工具到数据库？')
  await syncBuiltinTools()
  await getList()
  proxy?.$modal.msgSuccess('同步成功')
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
