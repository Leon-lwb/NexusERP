<template>
  <div class="app-container">
    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增测试</el-button>
      </el-col>
      <right-toolbar :search="false" @queryTable="getList" />
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list">
      <el-table-column label="测试名称" prop="testName" min-width="150" show-overflow-tooltip />
      <el-table-column label="模型A" prop="modelAName" width="150" show-overflow-tooltip />
      <el-table-column label="模型B" prop="modelBName" width="150" show-overflow-tooltip />
      <el-table-column label="问题数" width="80" align="center">
        <template #default="scope">
          {{ parseQuestions(scope.row.testQuestions).length }}
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template #default="scope">
          <dict-tag :options="ai_ab_test_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="250" align="center" fixed="right">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'PENDING'" link type="success" @click="handleExecute(scope.row)">执行</el-button>
          <el-button link type="primary" @click="handleViewResults(scope.row)">查看结果</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-if="scope.row.status !== 'RUNNING'">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-if="scope.row.status !== 'RUNNING'">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="abTestRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="测试名称" prop="testName">
          <el-input v-model="form.testName" placeholder="请输入测试名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="模型A" prop="modelAId">
          <el-input v-model="form.modelAId" placeholder="模型ID" style="width: 100%" />
        </el-form-item>
        <el-form-item label="模型B" prop="modelBId">
          <el-input v-model="form.modelBId" placeholder="模型ID" style="width: 100%" />
        </el-form-item>
        <el-form-item label="测试问题" prop="testQuestions">
          <el-input v-model="form.testQuestions" type="textarea" :rows="6" placeholder='JSON数组，如：["查询库存","销售趋势"]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 结果对比对话框 -->
    <el-dialog title="测试结果对比" v-model="resultDialogVisible" width="960px" append-to-body>
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
import { listAbTest, getAbTest, addAbTest, updateAbTest, delAbTest, executeAbTest, getAbTestResults, scoreAbTestResult } from '@/api/ai/abTest';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_ab_test_status } = toRefs<any>(proxy?.useDict('ai_ab_test_status'));

const abTestRef = ref<ElFormInstance>();
const resultDialogVisible = ref(false);
const resultList = ref<any[]>([]);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
});

const rules = {
  testName: [{ required: true, message: '测试名称不能为空', trigger: 'blur' }],
  modelAId: [{ required: true, message: '请输入模型A的ID', trigger: 'blur' }],
  modelBId: [{ required: true, message: '请输入模型B的ID', trigger: 'blur' }],
  testQuestions: [{ required: true, message: '测试问题不能为空', trigger: 'blur' }]
};

const {
  loading,
  list,
  total,
  open,
  isViewMode,
  title,
  form,
  getList,
  handleDelete,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate
} = useCrud({
  apis: { list: listAbTest, get: getAbTest, add: addAbTest, update: updateAbTest, del: delAbTest },
  idField: 'id',
  entityName: 'A/B 测试',
  formRef: abTestRef,
  queryParams,
  initForm: () => ({
    id: undefined,
    testName: '',
    description: '',
    modelAId: undefined,
    modelBId: undefined,
    testQuestions: ''
  })
});

function parseQuestions(json: string) {
  try {
    return JSON.parse(json || '[]');
  } catch {
    return [];
  }
}

const resultGroups = computed(() => {
  const map = new Map<string, any[]>();
  for (const r of resultList.value) {
    if (!map.has(r.question)) map.set(r.question, []);
    map.get(r.question)!.push(r);
  }
  return Array.from(map.entries()).map(([question, results]) => ({ question, results }));
});

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd();
}

/** 执行测试 */
function handleExecute(row: any) {
  proxy?.$modal
    .confirm('确认执行该 A/B 测试？执行过程可能需要几分钟。')
    .then(() => {
      executeAbTest(row.id).then(() => {
        proxy?.$modal.msgSuccess('测试已开始执行');
        getList();
      });
    })
    .catch(() => {});
}

/** 查看结果对比 */
function handleViewResults(row: any) {
  getAbTestResults(row.id).then((res: any) => {
    resultList.value = res.data || [];
    resultDialogVisible.value = true;
  });
}

/** 人工评分 */
function handleScore(resultId: number, score: number) {
  scoreAbTestResult(resultId, score).then(() => {
    proxy?.$modal.msgSuccess('评分已保存');
  });
}

getList();
</script>

<style scoped>
.result-group {
  margin-bottom: 20px;
}
.result-group h4 {
  margin-bottom: 10px;
  color: #303133;
}
.result-card {
  margin-bottom: 10px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.response-text {
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
}
.ml5 {
  margin-left: 5px;
}
</style>
