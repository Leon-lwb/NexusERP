<template>
  <div class="p-2">
    <!-- 搜索栏 -->
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="所属厂商" prop="providerId">
              <el-select v-model="queryParams.providerId" placeholder="请选择厂商" clearable style="width: 180px">
                <el-option v-for="item in providerList" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="queryParams.modelName" placeholder="请输入模型名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="模型类型" prop="modelType">
              <el-select v-model="queryParams.modelType" placeholder="请选择类型" clearable style="width: 160px">
                <el-option v-for="item in modelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="是否默认" prop="isDefault">
              <el-select v-model="queryParams.isDefault" placeholder="请选择" clearable style="width: 120px">
                <el-option label="是" value="1" />
                <el-option label="否" value="0" />
              </el-select>
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
      </div>
    </transition>

    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd">新增模型</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="所属厂商" align="center" prop="providerName" :show-overflow-tooltip="true" />
        <el-table-column label="模型名称" align="center" prop="modelName" :show-overflow-tooltip="true" />
        <el-table-column label="显示名称" align="center" prop="displayName" :show-overflow-tooltip="true" />
        <el-table-column label="类型" align="center" prop="modelType" width="110">
          <template #default="{ row }">
            <el-tag>{{ modelTypeMap[row.modelType] || row.modelType }}</el-tag>
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
            <el-tag :type="row.isDefault === '1' ? 'success' : 'info'">{{ row.isDefault === '1' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'success' : 'danger'">{{ row.status === '1' ? '正常' : '停用' }}</el-tag>
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
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" :disabled="row.isDefault === '1'" @click="handleSetDefault(row)">设为默认</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
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
            <el-option v-for="item in modelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
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
            <el-radio value="1">正常</el-radio>
            <el-radio value="0">停用</el-radio>
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
import { listModel, getModel, addModel, updateModel, delModel, setDefaultModel, listProvider } from '@/api/ai/model';
import type { AiModel } from '@/api/ai/model';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const list = ref<AiModel[]>([]);
const total = ref(0);
const providerList = ref<any[]>([]);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const multiple = ref(true);

/** 模型类型选项（搜索栏与新增/编辑弹窗共用） */
const modelTypeOptions = [
  { label: 'CHAT（对话）', value: 'CHAT' },
  { label: 'EMBEDDING（向量）', value: 'EMBEDDING' },
  { label: 'RERANK（重排）', value: 'RERANK' },
  { label: 'IMAGE（文生图）', value: 'IMAGE' }
];
/** 类型 code → 中文展示（列表 Tag） */
const modelTypeMap: Record<string, string> = {
  CHAT: '对话',
  EMBEDDING: '向量',
  RERANK: '重排',
  IMAGE: '文生图'
};

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

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
};

const data = reactive<PageData<AiModel, any>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    providerId: undefined,
    modelName: '',
    modelType: '',
    isDefault: '',
    status: ''
  },
  rules: {
    providerId: [{ required: true, message: '请选择所属厂商', trigger: 'change' }],
    modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
    modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();

/** 查询模型列表 */
const getList = async () => {
  loading.value = true;
  try {
    const res = await listModel(queryParams.value);
    list.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

/** 加载厂商下拉选项 */
const getProviderList = async () => {
  const res = await listProvider();
  providerList.value = res.rows || res.data || [];
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};

/** 多选框选中数据 */
const handleSelectionChange = (selection: AiModel[]) => {
  ids.value = selection.map((item) => item.id!);
  multiple.value = !selection.length;
};

/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
};

/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData };
  formRef.value?.resetFields();
};

/** 新增 */
const handleAdd = () => {
  reset();
  dialog.visible = true;
  dialog.title = '新增模型';
};

/** 编辑 */
const handleEdit = async (row: AiModel) => {
  reset();
  const res = await getModel(row.id!);
  Object.assign(form.value, res.data);
  dialog.visible = true;
  dialog.title = '编辑模型';
};

/** 提交表单 */
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.id ? await updateModel(form.value) : await addModel(form.value);
      proxy?.$modal.msgSuccess('操作成功');
      dialog.visible = false;
      await getList();
    }
  });
};

/** 删除（传 row 删单条，不传则按多选删除） */
const handleDelete = async (row?: AiModel) => {
  const deleteIds: Array<number | string> = row ? [row.id!] : ids.value;
  const tip = row ? `是否确认删除模型"${row.modelName}"？` : `是否确认删除选中的 ${ids.value.length} 个模型？`;
  await proxy?.$modal.confirm(tip);
  await delModel(deleteIds);
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
};

/** 设为默认 */
const handleSetDefault = async (row: AiModel) => {
  await setDefaultModel(row.id!);
  proxy?.$modal.msgSuccess('设置成功');
  await getList();
};

onMounted(() => {
  getProviderList();
  getList();
});
</script>
