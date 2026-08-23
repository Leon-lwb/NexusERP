<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="所属厂商" prop="providerId">
            <el-select v-model="queryParams.providerId" placeholder="请选择厂商" clearable>
              <el-option v-for="item in providerList" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="模型名称" prop="modelName">
            <el-input v-model="queryParams.modelName" placeholder="请输入模型名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="模型类型" prop="modelType">
            <el-select v-model="queryParams.modelType" placeholder="请选择类型" clearable>
              <el-option v-for="item in AI_MODEL_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否默认" prop="isDefault">
            <el-select v-model="queryParams.isDefault" placeholder="请选择" clearable>
              <el-option v-for="item in AI_YES_NO_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增模型</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" border :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="所属厂商" align="center" prop="providerName" :show-overflow-tooltip="true" />
      <el-table-column label="模型名称" align="center" prop="modelName" :show-overflow-tooltip="true" />
      <el-table-column label="显示名称" align="center" prop="displayName" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" prop="modelType" width="110">
        <template #default="{ row }">
          <el-tag :type="getAiOptionTagType(AI_MODEL_TYPE_OPTIONS, row.modelType)">{{
            getAiOptionTagLabel(AI_MODEL_TYPE_OPTIONS, row.modelType)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" prop="maxTokens" width="140">
        <template #header>
          <el-tooltip
            placement="top"
            content="模型单次回复可生成的最大 Token 数（输出上限，对应接口参数 max_tokens）。此值限制的是「回复」长度，并非模型的上下文窗口大小；上下文窗口由模型自身决定，通常远大于此值。"
          >
            <span
              >最大输出Token <el-icon class="align-middle"><QuestionFilled /></el-icon
            ></span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          {{ row.maxTokens != null ? row.maxTokens : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="温度" align="center" prop="temperature" width="80" />
      <el-table-column label="默认" align="center" prop="isDefault" width="80">
        <template #default="{ row }">
          <dict-tag :options="AI_YES_NO_OPTIONS" :value="row.isDefault" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="{ row }">
          <dict-tag :options="AI_STATUS_OPTIONS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="输入价格" align="center" prop="inputPricePerMillion" width="110">
        <template #default="{ row }">
          {{ row.inputPricePerMillion ? row.inputPricePerMillion + ' 元/M' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="输出价格" align="center" prop="outputPricePerMillion" width="110">
        <template #default="{ row }">
          {{ row.outputPricePerMillion ? row.outputPricePerMillion + ' 元/M' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleUpdate(row)">编辑</el-button>
          <el-button link type="primary" :disabled="row.isDefault === '1'" @click="handleSetDefault(row)">设为默认</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="modelRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
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
            <el-option v-for="item in AI_MODEL_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item prop="maxTokens">
          <template #label>
            <el-tooltip
              placement="top"
              content="模型单次回复可生成的最大 Token 数（输出上限，对应接口参数 max_tokens）。此值限制的是「回复」长度，并非上下文窗口大小；上下文窗口由模型自身决定，通常远大于此值。"
            >
              <span
                >最大输出Token <el-icon class="align-middle"><QuestionFilled /></el-icon
              ></span>
            </el-tooltip>
          </template>
          <el-input-number v-model="form.maxTokens" :min="0" controls-position="right" placeholder="单次回复最大生成 Token 数" style="width: 100%" />
        </el-form-item>
        <el-form-item label="温度参数" prop="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="是否默认" prop="isDefault">
          <el-switch v-model="form.isDefault" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in AI_STATUS_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-divider content-position="left">定价配置</el-divider>
        <el-form-item label="输入价格" prop="inputPricePerMillion">
          <el-input-number
            v-model="form.inputPricePerMillion"
            :min="0"
            :precision="6"
            controls-position="right"
            placeholder="元/百万Token"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="输出价格" prop="outputPricePerMillion">
          <el-input-number
            v-model="form.outputPricePerMillion"
            :min="0"
            :precision="6"
            controls-position="right"
            placeholder="元/百万Token"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="币种" prop="currency">
          <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
            <el-option v-for="item in AI_CURRENCY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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

<script setup lang="ts" name="AiModel">
import { listModel, getModel, addModel, updateModel, delModel, setDefaultModel, listProvider } from '@/api/ai/model';
import type { AiModel } from '@/api/ai/model';
import { useCrud } from '@/composables/useCrud';
import {
  AI_CURRENCY_OPTIONS,
  AI_MODEL_TYPE_OPTIONS,
  AI_STATUS_OPTIONS,
  AI_YES_NO_OPTIONS,
  getAiOptionTagLabel,
  getAiOptionTagType
} from '@/views/ai/constants';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const modelRef = ref<ElFormInstance>();

/** 厂商下拉选项（查询与表单共用） */
const providerList = ref<any[]>([]);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  providerId: null,
  modelName: null,
  modelType: null,
  isDefault: null,
  status: null
});

const rules = {
  providerId: [{ required: true, message: '请选择所属厂商', trigger: 'change' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }]
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
  apis: { list: listModel, get: getModel, add: addModel, update: updateModel, del: delModel },
  idField: 'id',
  entityName: '模型',
  formRef: modelRef,
  queryParams,
  initForm: () => ({
    id: undefined,
    providerId: undefined,
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

/** 设为默认模型 */
async function handleSetDefault(row: AiModel) {
  await setDefaultModel(row.id!);
  proxy?.$modal.msgSuccess('设置成功');
  await getList();
}

/** 加载厂商下拉选项 */
async function getProviderList() {
  const res = await listProvider();
  providerList.value = res.rows || res.data || [];
}

getProviderList();
getList();
</script>
