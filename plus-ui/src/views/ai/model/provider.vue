<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="厂商名称" prop="name">
            <el-input v-model="queryParams.name" placeholder="请输入厂商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="厂商类型" prop="providerType">
            <el-select v-model="queryParams.providerType" placeholder="请选择厂商类型" clearable>
              <el-option v-for="item in AI_PROVIDER_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" border :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="厂商名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="API 地址" align="center" prop="apiHost" :show-overflow-tooltip="true" />
      <el-table-column label="API Key" align="center" prop="apiKey" :show-overflow-tooltip="true">
        <template #default="scope">
          <!-- 列表脱敏，完整 Key 仅在编辑弹窗可见 -->
          <span>{{ scope.row.apiKey ? 'sk-***' + String(scope.row.apiKey).slice(-4) : '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="厂商类型" align="center" prop="providerType">
        <template #default="scope">
          <el-tag :type="getAiOptionTagType(AI_PROVIDER_TYPE_OPTIONS, scope.row.providerType)">{{ scope.row.providerType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="AI_STATUS_OPTIONS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ proxy?.parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改厂商配置对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="providerRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="厂商名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入厂商名称" />
        </el-form-item>
        <el-form-item label="API 地址" prop="apiHost">
          <el-input v-model="form.apiHost" placeholder="请输入 API 地址" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" placeholder="请输入 API Key" show-password />
        </el-form-item>
        <el-form-item label="厂商类型" prop="providerType">
          <el-select v-model="form.providerType" placeholder="请选择厂商类型">
            <el-option v-for="item in AI_PROVIDER_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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

<script setup lang="ts" name="Provider">
import { listProvider, getProvider, addProvider, updateProvider, delProvider } from '@/api/ai/model';
import { useCrud } from '@/composables/useCrud';
import { AI_PROVIDER_TYPE_OPTIONS, AI_STATUS_OPTIONS, getAiOptionTagType } from '@/views/ai/constants';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const providerRef = ref<ElFormInstance>();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  name: null,
  providerType: null,
  status: null
});

const rules = {
  name: [{ required: true, message: '厂商名称不能为空', trigger: 'blur' }],
  apiHost: [{ required: true, message: 'API 地址不能为空', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择厂商类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
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
  apis: { list: listProvider, get: getProvider, add: addProvider, update: updateProvider, del: delProvider },
  idField: 'id',
  entityName: '厂商',
  formRef: providerRef,
  queryParams,
  initForm: () => ({
    id: undefined,
    name: '',
    apiHost: '',
    apiKey: '',
    providerType: '',
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
