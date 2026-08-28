<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="分类" prop="category">
            <el-select v-model="queryParams.category" placeholder="请选择分类" clearable>
              <el-option v-for="dict in ai_sql_example_category" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="问题" prop="question">
            <el-input v-model="queryParams.question" placeholder="请输入问题关键词" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="排序号" prop="sortOrder" width="80" align="center" />
      <el-table-column label="分类" prop="category" width="100" align="center">
        <template #default="scope">
          <dict-tag :options="ai_sql_example_category" :value="scope.row.category" />
        </template>
      </el-table-column>
      <el-table-column label="问题" prop="question" min-width="200" show-overflow-tooltip />
      <el-table-column label="SQL 模板" prop="sqlTemplate" min-width="300" show-overflow-tooltip>
        <template #default="scope">
          <code class="sql-preview">{{ scope.row.sqlTemplate }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="sqlExampleRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="问题" prop="question">
          <el-input v-model="form.question" placeholder="请输入用户可能问的问题" />
        </el-form-item>
        <el-form-item label="SQL 模板" prop="sqlTemplate">
          <el-input v-model="form.sqlTemplate" type="textarea" :rows="8" placeholder="请输入 SQL 查询模板" style="font-family: monospace" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类">
            <el-option v-for="dict in ai_sql_example_category" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选：示例说明" />
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

<script setup lang="ts" name="AiSqlExample">
import { listSqlExample, getSqlExample, addSqlExample, updateSqlExample, delSqlExample } from '@/api/ai/sqlExample';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_sql_example_category, sys_normal_disable } = toRefs<any>(proxy?.useDict('ai_sql_example_category', 'sys_normal_disable'));

const sqlExampleRef = ref<ElFormInstance>();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  category: null,
  question: null
});

const rules = {
  question: [{ required: true, message: '问题不能为空', trigger: 'blur' }],
  sqlTemplate: [{ required: true, message: 'SQL 模板不能为空', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
};

const {
  loading,
  list,
  total,
  open,
  isViewMode,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate
} = useCrud({
  apis: { list: listSqlExample, get: getSqlExample, add: addSqlExample, update: updateSqlExample, del: delSqlExample },
  idField: 'id',
  entityName: 'SQL 示例',
  formRef: sqlExampleRef,
  queryParams,
  initForm: () => ({
    id: undefined,
    question: '',
    sqlTemplate: '',
    category: 'INVENTORY',
    sortOrder: 0,
    status: '0',
    description: ''
  })
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd();
}

getList();
</script>

<style scoped>
.sql-preview {
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
}
</style>
