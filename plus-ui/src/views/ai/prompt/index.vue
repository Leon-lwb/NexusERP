<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="模板名称" prop="templateName">
            <el-input v-model="queryParams.templateName" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="模板标识" prop="templateKey">
            <el-input v-model="queryParams.templateKey" placeholder="请输入模板标识" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
              <el-option v-for="item in AI_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
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

    <!-- 操作区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:prompt:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['ai:prompt:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="模板名称" align="left" prop="templateName" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="模板标识" align="left" prop="templateKey" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="是否默认" align="center" prop="isDefault" width="90">
        <template #default="scope">
          <dict-tag :options="AI_YES_NO_OPTIONS" :value="scope.row.isDefault" />
        </template>
      </el-table-column>
      <el-table-column label="版本" align="center" prop="version" width="70" />
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <dict-tag :options="AI_STATUS_OPTIONS" :value="scope.row.status" />
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

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-form ref="promptRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
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
            <el-radio v-for="item in AI_YES_NO_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in AI_STATUS_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
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

<script setup name="AiPrompt" lang="ts">
import { listPromptTemplate, getPromptTemplate, addPromptTemplate, updatePromptTemplate, delPromptTemplate } from '@/api/ai/prompt';
import { useCrud } from '@/composables/useCrud';
import { AI_STATUS_OPTIONS, AI_YES_NO_OPTIONS } from '@/views/ai/constants';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const promptRef = ref<ElFormInstance>();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  templateName: null,
  templateKey: null,
  status: null
});

const rules = {
  templateName: [{ required: true, message: '模板名称不能为空', trigger: 'blur' }],
  templateKey: [{ required: true, message: '模板标识不能为空', trigger: 'blur' }],
  content: [{ required: true, message: '模板内容不能为空', trigger: 'blur' }]
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
  apis: { list: listPromptTemplate, get: getPromptTemplate, add: addPromptTemplate, update: updatePromptTemplate, del: delPromptTemplate },
  idField: 'id',
  entityName: 'Prompt 模板',
  formRef: promptRef,
  queryParams,
  initForm: () => ({
    id: undefined,
    templateName: undefined,
    templateKey: undefined,
    content: undefined,
    description: undefined,
    isDefault: '0',
    status: '1'
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
