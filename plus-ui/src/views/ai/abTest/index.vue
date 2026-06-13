<template>
  <div class="app-container">
    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain :icon="Plus" @click="handleAdd">新增测试</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="测试名称" prop="testName" min-width="150" />
      <el-table-column label="模型A" prop="modelAName" width="150" />
      <el-table-column label="模型B" prop="modelBName" width="150" />
      <el-table-column label="问题数" width="80" align="center">
        <template #default="scope">
          {{ parseQuestions(scope.row.testQuestions).length }}
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="250" align="center">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'PENDING'" link type="success" @click="handleExecute(scope.row)">执行</el-button>
          <el-button link type="primary" @click="handleViewResults(scope.row)">查看结果</el-button>
          <el-button link type="primary" :icon="Edit" @click="handleUpdate(scope.row)" v-if="scope.row.status !== 'RUNNING'">修改</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(scope.row)" v-if="scope.row.status !== 'RUNNING'">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="测试名称" prop="testName">
          <el-input v-model="form.testName" placeholder="请输入测试名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="模型A" prop="modelAId">
          <el-input-number v-model="form.modelAId" :controls="false" placeholder="模型ID" style="width: 100%" />
        </el-form-item>
        <el-form-item label="模型B" prop="modelBId">
          <el-input-number v-model="form.modelBId" :controls="false" placeholder="模型ID" style="width: 100%" />
        </el-form-item>
        <el-form-item label="测试问题" prop="testQuestions">
          <el-input v-model="form.testQuestions" type="textarea" :rows="6" placeholder='JSON数组，如：["查询库存","销售趋势"]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 结果对比对话框 -->
    <el-dialog title="测试结果对比" v-model="resultDialogVisible" width="900px" append-to-body>
      <div v-for="(group, idx) in resultGroups" :key="idx" class="result-group">
        <h4>问题 {{ idx + 1 }}：{{ group.question }}</h4>
        <el-row :gutter="20">
          <el-col :span="12" v-for="r in group.results" :key="r.id">
            <el-card shadow="hover" class="result-card">
              <template #header>
                <div class="card-header">
                  <span>{{ r.modelName }}</span>
                  <div>
                    <el-tag size="small" type="info">{{ r.latencyMs }}ms</el-tag>
                    <el-tag size="small" type="warning" class="ml5">{{ r.tokenUsage }} tokens</el-tag>
                    <el-rate v-model="r.score" :max="5" @change="(val: number) => handleScore(r.id, val)" class="ml5" />
                  </div>
                </div>
              </template>
              <div class="response-text">{{ r.response }}</div>
            </el-card>
          </el-col>
        </el-row>
      </div>
      <el-empty v-if="resultGroups.length === 0" description="暂无测试结果" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="AiAbTest">
import { ref, reactive, computed } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAbTest, getAbTest, addAbTest, updateAbTest, delAbTest, executeAbTest, getAbTestResults, scoreAbTestResult } from '@/api/ai/abTest'

defineOptions({ name: 'AiAbTest' })

const loading = ref(false)
const showSearch = ref(true)
const dataList = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const resultDialogVisible = ref(false)
const resultList = ref<any[]>([])

const queryParams = reactive({ pageNum: 1, pageSize: 10 })

const form = reactive({
  id: undefined as number | undefined,
  testName: '',
  description: '',
  modelAId: undefined as number | undefined,
  modelBId: undefined as number | undefined,
  testQuestions: ''
})

const rules = {
  testName: [{ required: true, message: '测试名称不能为空', trigger: 'blur' }],
  modelAId: [{ required: true, message: '请输入模型A的ID', trigger: 'blur' }],
  modelBId: [{ required: true, message: '请输入模型B的ID', trigger: 'blur' }],
  testQuestions: [{ required: true, message: '测试问题不能为空', trigger: 'blur' }]
}

const formRef = ref()

function statusTagType(status: string) {
  return { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success' }[status] || 'info'
}
function statusLabel(status: string) {
  return { PENDING: '待执行', RUNNING: '执行中', COMPLETED: '已完成' }[status] || status
}
function parseQuestions(json: string) {
  try { return JSON.parse(json || '[]') } catch { return [] }
}

const resultGroups = computed(() => {
  const map = new Map<string, any[]>()
  for (const r of resultList.value) {
    if (!map.has(r.question)) map.set(r.question, [])
    map.get(r.question)!.push(r)
  }
  return Array.from(map.entries()).map(([question, results]) => ({ question, results }))
})

function getList() {
  loading.value = true
  listAbTest(queryParams).then((res: any) => {
    dataList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function resetForm() {
  form.id = undefined
  form.testName = ''
  form.description = ''
  form.modelAId = undefined
  form.modelBId = undefined
  form.testQuestions = ''
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增 A/B 测试'
  dialogVisible.value = true
}

function handleUpdate(row: any) {
  resetForm()
  getAbTest(row.id).then((res: any) => {
    Object.assign(form, res.data)
    dialogTitle.value = '修改 A/B 测试'
    dialogVisible.value = true
  })
}

function submitForm() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    if (form.id) {
      updateAbTest(form).then(() => { ElMessage.success('修改成功'); dialogVisible.value = false; getList() })
    } else {
      addAbTest(form).then(() => { ElMessage.success('新增成功'); dialogVisible.value = false; getList() })
    }
  })
}

function handleExecute(row: any) {
  ElMessageBox.confirm('确认执行该 A/B 测试？执行过程可能需要几分钟。', '提示', { type: 'warning' }).then(() => {
    executeAbTest(row.id).then(() => { ElMessage.success('测试已开始执行'); getList() })
  }).catch(() => {})
}

function handleViewResults(row: any) {
  getAbTestResults(row.id).then((res: any) => {
    resultList.value = res.data || []
    resultDialogVisible.value = true
  })
}

function handleScore(resultId: number, score: number) {
  scoreAbTestResult(resultId, score).then(() => { ElMessage.success('评分已保存') })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该测试？', '提示', { type: 'warning' }).then(() => {
    delAbTest(row.id).then(() => { ElMessage.success('删除成功'); getList() })
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.result-group { margin-bottom: 20px; }
.result-group h4 { margin-bottom: 10px; color: #303133; }
.result-card { margin-bottom: 10px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.response-text { max-height: 200px; overflow-y: auto; white-space: pre-wrap; font-size: 13px; line-height: 1.6; }
.ml5 { margin-left: 5px; }
</style>
