<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
      <el-form-item label="分类" prop="category">
        <el-select v-model="queryParams.category" placeholder="请选择分类" clearable style="width: 150px">
          <el-option label="库存" value="INVENTORY" />
          <el-option label="销售" value="SALES" />
          <el-option label="采购" value="PURCHASE" />
          <el-option label="生产" value="PRODUCTION" />
        </el-select>
      </el-form-item>
      <el-form-item label="问题" prop="question">
        <el-input v-model="queryParams.question" placeholder="请输入问题关键词" clearable @keyup.enter="handleQuery" style="width: 200px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain :icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain :icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="排序号" prop="sortOrder" width="80" align="center" />
      <el-table-column label="分类" prop="category" width="100" align="center">
        <template #default="scope">
          <el-tag :type="categoryTagType(scope.row.category)">{{ scope.row.category }}</el-tag>
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
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'">
            {{ scope.row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template #default="scope">
          <el-button link type="primary" :icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="700px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="问题" prop="question">
          <el-input v-model="form.question" placeholder="请输入用户可能问的问题" />
        </el-form-item>
        <el-form-item label="SQL 模板" prop="sqlTemplate">
          <el-input v-model="form.sqlTemplate" type="textarea" :rows="8" placeholder="请输入 SQL 查询模板" style="font-family: monospace" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类">
            <el-option label="库存" value="INVENTORY" />
            <el-option label="销售" value="SALES" />
            <el-option label="采购" value="PURCHASE" />
            <el-option label="生产" value="PRODUCTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选：示例说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="AiSqlExample">
import { ref, reactive } from 'vue';
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listSqlExample, getSqlExample, addSqlExample, updateSqlExample, delSqlExample } from '@/api/ai/sqlExample';

defineOptions({ name: 'AiSqlExample' });

const loading = ref(false);
const showSearch = ref(true);
const dataList = ref<any[]>([]);
const total = ref(0);
const ids = ref<number[]>([]);
const multiple = ref(true);
const dialogVisible = ref(false);
const dialogTitle = ref('');

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  category: undefined as string | undefined,
  question: undefined as string | undefined
});

const form = reactive({
  id: undefined as number | undefined,
  question: '',
  sqlTemplate: '',
  category: 'INVENTORY',
  sortOrder: 0,
  status: '0',
  description: ''
});

const rules = {
  question: [{ required: true, message: '问题不能为空', trigger: 'blur' }],
  sqlTemplate: [{ required: true, message: 'SQL 模板不能为空', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
};

const queryFormRef = ref();
const formRef = ref();

/** 分类标签颜色 */
function categoryTagType(category: string) {
  const map: Record<string, string> = {
    INVENTORY: 'warning',
    SALES: 'success',
    PURCHASE: 'primary',
    PRODUCTION: 'danger'
  };
  return map[category] || 'info';
}

/** 查询列表 */
function getList() {
  loading.value = true;
  listSqlExample(queryParams).then((res: any) => {
    dataList.value = res.rows;
    total.value = res.total;
    loading.value = false;
  });
}

/** 搜索 */
function handleQuery() {
  queryParams.pageNum = 1;
  getList();
}

/** 重置 */
function resetQuery() {
  queryFormRef.value?.resetFields();
  handleQuery();
}

/** 多选 */
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item) => item.id);
  multiple.value = !selection.length;
}

/** 重置表单 */
function resetForm() {
  form.id = undefined;
  form.question = '';
  form.sqlTemplate = '';
  form.category = 'INVENTORY';
  form.sortOrder = 0;
  form.status = '0';
  form.description = '';
}

/** 新增 */
function handleAdd() {
  resetForm();
  dialogTitle.value = '新增 SQL 示例';
  dialogVisible.value = true;
}

/** 修改 */
function handleUpdate(row: any) {
  resetForm();
  const exampleId = row.id || ids.value[0];
  getSqlExample(exampleId).then((res: any) => {
    Object.assign(form, res.data);
    dialogTitle.value = '修改 SQL 示例';
    dialogVisible.value = true;
  });
}

/** 提交 */
function submitForm() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    if (form.id) {
      updateSqlExample(form).then(() => {
        ElMessage.success('修改成功');
        dialogVisible.value = false;
        getList();
      });
    } else {
      addSqlExample(form).then(() => {
        ElMessage.success('新增成功');
        dialogVisible.value = false;
        getList();
      });
    }
  });
}

/** 删除 */
function handleDelete(row?: any) {
  const deleteIds = row?.id ? [row.id] : ids.value;
  ElMessageBox.confirm('是否确认删除选中的数据？', '提示', { type: 'warning' })
    .then(() => {
      delSqlExample(deleteIds).then(() => {
        ElMessage.success('删除成功');
        getList();
      });
    })
    .catch(() => {});
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
