<template>
  <div class="p-2">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="mb-2">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="queryParams.templateName" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery" style="width: 200px" />
        </el-form-item>
        <el-form-item label="模板标识" prop="templateKey">
          <el-input v-model="queryParams.templateKey" placeholder="请输入模板标识" clearable @keyup.enter="handleQuery" style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="正常" value="1" />
            <el-option label="停用" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作区域 -->
    <el-card shadow="never">
      <template #header>
        <el-row :gutter="10">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:prompt:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['ai:prompt:remove']"
              >删除</el-button
            >
          </el-col>
        </el-row>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="templateList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="模板名称" align="left" prop="templateName" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="模板标识" align="left" prop="templateKey" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="是否默认" align="center" prop="isDefault" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.isDefault === '1' ? 'success' : 'info'">{{ scope.row.isDefault === '1' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" align="center" prop="version" width="70" />
        <el-table-column label="状态" align="center" prop="status" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'success' : 'danger'">{{ scope.row.status === '1' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" align="left" prop="description" min-width="200" :show-overflow-tooltip="true" />
        <el-table-column label="更新时间" align="center" prop="updateTime" width="160" />
        <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:prompt:edit']">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ai:prompt:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/修改对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="800px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="模板标识" prop="templateKey">
          <el-input v-model="form.templateKey" placeholder="如 erp_query_agent" :disabled="form.id != null" />
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="15" placeholder="支持 {{variable}} 占位符" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="是否默认" prop="isDefault">
          <el-radio-group v-model="form.isDefault">
            <el-radio value="1">是</el-radio>
            <el-radio value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="1">正常</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AiPrompt" lang="ts">
import { listPromptTemplate, getPromptTemplate, addPromptTemplate, updatePromptTemplate, delPromptTemplate } from '@/api/ai/prompt';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const templateList = ref<any[]>([]);
const loading = ref(true);
const multiple = ref(true);
const total = ref(0);
const ids = ref<Array<number>>([]);
const dialogVisible = ref(false);
const dialogTitle = ref('');

const queryParams = ref<any>({
  pageNum: 1,
  pageSize: 10,
  templateName: undefined,
  templateKey: undefined,
  status: undefined
});

const formRef = ref<ElFormInstance>();
const form = ref<any>({});
const rules = ref<any>({
  templateName: [{ required: true, message: '模板名称不能为空', trigger: 'blur' }],
  templateKey: [{ required: true, message: '模板标识不能为空', trigger: 'blur' }],
  content: [{ required: true, message: '模板内容不能为空', trigger: 'blur' }]
});

/** 查询列表 */
const getList = () => {
  loading.value = true;
  listPromptTemplate(queryParams.value).then((res: any) => {
    templateList.value = res.rows;
    total.value = res.total;
    loading.value = false;
  });
};

/** 搜索 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置 */
const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

/** 多选 */
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.id);
  multiple.value = !selection.length;
};

/** 重置表单 */
const reset = () => {
  form.value = {
    id: undefined,
    templateName: undefined,
    templateKey: undefined,
    content: undefined,
    description: undefined,
    isDefault: '0',
    status: '1'
  };
  proxy?.resetForm('formRef');
};

/** 新增 */
const handleAdd = () => {
  reset();
  dialogVisible.value = true;
  dialogTitle.value = '新增 Prompt 模板';
};

/** 修改 */
const handleUpdate = (row: any) => {
  reset();
  getPromptTemplate(row.id).then((res: any) => {
    form.value = res.data;
    dialogVisible.value = true;
    dialogTitle.value = '修改 Prompt 模板';
  });
};

/** 提交 */
const submitForm = () => {
  formRef.value?.validate((valid) => {
    if (valid) {
      if (form.value.id != null) {
        updatePromptTemplate(form.value).then(() => {
          proxy?.$modal.msgSuccess('修改成功');
          dialogVisible.value = false;
          getList();
        });
      } else {
        addPromptTemplate(form.value).then(() => {
          proxy?.$modal.msgSuccess('新增成功');
          dialogVisible.value = false;
          getList();
        });
      }
    }
  });
};

/** 删除 */
const handleDelete = (row?: any) => {
  const deleteIds = row?.id ? [row.id] : ids.value;
  proxy?.$modal
    .confirm('是否确认删除所选模板？')
    .then(() => {
      return delPromptTemplate(deleteIds);
    })
    .then(() => {
      getList();
      proxy?.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
};

/** 取消 */
const cancel = () => {
  dialogVisible.value = false;
  reset();
};

onMounted(() => {
  getList();
});
</script>
