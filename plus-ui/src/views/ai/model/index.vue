<template>
  <div class="p-2">
    <!-- 搜索栏 -->
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="所属厂商" prop="providerId">
              <el-select v-model="queryParams.providerId" placeholder="请选择厂商" clearable filterable style="width: 180px">
                <el-option v-for="item in providerList" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="queryParams.modelName" placeholder="请输入模型名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="模型类型" prop="modelType">
              <el-select v-model="queryParams.modelType" placeholder="请选择类型" clearable style="width: 160px">
                <el-option v-for="dict in ai_model_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="是否默认" prop="isDefault">
              <el-select v-model="queryParams.isDefault" placeholder="请选择" clearable style="width: 120px">
                <el-option v-for="dict in sys_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
                <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-table-column label="所属厂商" align="center" prop="providerName" min-width="120" :show-overflow-tooltip="true" />
        <el-table-column label="模型名称" align="center" prop="modelName" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="显示名称" align="center" prop="displayName" min-width="120" :show-overflow-tooltip="true" />
        <el-table-column label="类型" align="center" prop="modelType" width="90">
          <template #default="{ row }">
            <dict-tag :options="ai_model_type" :value="row.modelType" />
          </template>
        </el-table-column>
        <el-table-column label="能力(入→出)" align="center" width="170">
          <template #default="{ row }">
            <div class="cap-cells">
              <div class="cap-cell">
                <dict-tag v-for="cap in splitCapabilities(row.inputCapabilities)" :key="'in-' + cap" :options="ai_model_capability" :value="cap" />
              </div>
              <span class="cap-arrow">→</span>
              <div class="cap-cell">
                <dict-tag v-for="cap in splitCapabilities(row.outputCapabilities)" :key="'out-' + cap" :options="ai_model_capability" :value="cap" />
              </div>
            </div>
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
        <el-table-column label="温度" align="center" prop="temperature" width="70">
          <template #default="{ row }">
            {{ row.temperature != null ? row.temperature : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="默认" align="center" prop="isDefault" width="80">
          <template #default="{ row }">
            <dict-tag :options="sys_yes_no" :value="row.isDefault" />
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status" width="80">
          <template #default="{ row }">
            <dict-tag :options="sys_normal_disable" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="输入价格" align="center" prop="inputPricePerMillion" width="110">
          <template #default="{ row }">
            {{ row.inputPricePerMillion != null ? row.inputPricePerMillion + ' ' + currencyUnit(row) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="输出价格" align="center" prop="outputPricePerMillion" width="110">
          <template #default="{ row }">
            {{ row.outputPricePerMillion != null ? row.outputPricePerMillion + ' ' + currencyUnit(row) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="220" fixed="right" class-name="small-padding fixed-width">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" :disabled="row.isDefault === 'Y'" @click="handleSetDefault(row)">设为默认</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="820px" append-to-body @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <!-- 基础信息 -->
        <el-divider content-position="left">基础信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="所属厂商" prop="providerId">
              <el-select v-model="form.providerId" placeholder="请选择厂商" filterable style="width: 100%">
                <el-option v-for="item in providerList" :key="item.id" :label="item.name" :value="item.id">
                  <span class="provider-opt">
                    <dict-tag :options="ai_provider_type" :value="item.providerType" />
                    <span class="provider-opt-name">{{ item.name }}</span>
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模型名称（Model ID）" prop="modelName">
              <el-input v-model="form.modelName" placeholder="如 doubao-seed-1-6-250615" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="显示名称" prop="displayName">
              <el-input v-model="form.displayName" placeholder="请输入显示名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 运行配置 -->
        <el-divider content-position="left">运行配置</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模型类型" prop="modelType">
              <el-select v-model="form.modelType" placeholder="请选择模型类型" style="width: 100%" @change="handleModelTypeChange">
                <el-option v-for="dict in ai_model_type" :key="dict.value" :label="dict.label + '（' + dict.value + '）'" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item prop="apiProtocol">
              <template #label>
                <el-tooltip placement="top" content="留空时按厂商默认协议调用；厂商仅支持一种协议时不可修改">
                  <span
                    >API 协议 <el-icon class="align-middle"><QuestionFilled /></el-icon
                  ></span>
                </el-tooltip>
              </template>
              <el-select v-model="form.apiProtocol" placeholder="留空按厂商默认协议" clearable :disabled="protocolLocked" style="width: 100%">
                <el-option v-for="dict in filteredProtocolOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item prop="contextWindow">
              <template #label>
                <el-tooltip placement="top" content="模型上下文窗口大小（Token 数），由模型自身决定，通常远大于单次回复上限">
                  <span
                    >上下文窗口 <el-icon class="align-middle"><QuestionFilled /></el-icon
                  ></span>
                </el-tooltip>
              </template>
              <el-input-number v-model="form.contextWindow" :min="1" controls-position="right" placeholder="如 128000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col v-if="!templateFlags?.hideMaxTokens" :span="8">
            <el-form-item prop="maxTokens">
              <template #label>
                <el-tooltip
                  placement="top"
                  content="模型单次回复可生成的最大 Token 数（输出上限，对应接口参数 max_tokens）。此值限制的是「回复」长度，并非模型的上下文窗口大小；上下文窗口由模型自身决定，通常远大于此值。"
                >
                  <span
                    >最大输出Token <el-icon class="align-middle"><QuestionFilled /></el-icon
                  ></span>
                </el-tooltip>
              </template>
              <el-input-number
                v-model="form.maxTokens"
                :min="0"
                controls-position="right"
                placeholder="单次回复最大生成 Token 数"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="!templateFlags?.hideTemperature" :span="8">
            <el-form-item label="温度参数" prop="temperature">
              <el-input-number
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                :precision="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="templateFlags?.requiresEmbeddingDimension" :span="8">
            <el-form-item label="向量维度" prop="embeddingDimension" :required="!!templateFlags?.requiresEmbeddingDimension">
              <el-input-number v-model="form.embeddingDimension" :min="1" controls-position="right" placeholder="如 2048" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 模型能力 -->
        <el-divider content-position="left">模型能力</el-divider>
        <div class="cap-section">
          <div class="cap-group">
            <div class="cap-group-title">输入能力（模型接受的输入模态）</div>
            <el-checkbox-group v-model="inputCaps" :disabled="capabilityLocked">
              <el-checkbox v-for="dict in ai_model_capability" :key="'in-' + dict.value" :value="dict.value" border>
                {{ dict.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div class="cap-group">
            <div class="cap-group-title">输出能力（模型产出的输出模态）</div>
            <el-checkbox-group v-model="outputCaps" :disabled="capabilityLocked">
              <el-checkbox v-for="dict in ai_model_capability" :key="'out-' + dict.value" :value="dict.value" border>
                {{ dict.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div v-if="capabilityLocked" class="cap-lock-tip">EMBEDDING/RERANK 类型的输入输出能力由模型类型模板锁定，不可修改</div>
          <div v-else class="cap-lock-tip">未锁定类型至少选择一项输入与输出能力</div>
        </div>

        <!-- 定价配置 -->
        <el-divider content-position="left">定价配置</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="输入价格" prop="inputPricePerMillion">
              <el-input-number
                v-model="form.inputPricePerMillion"
                :min="0"
                :precision="6"
                controls-position="right"
                placeholder="每百万 Token 输入价格"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="输出价格" prop="outputPricePerMillion">
              <el-input-number
                v-model="form.outputPricePerMillion"
                :min="0"
                :precision="6"
                controls-position="right"
                placeholder="每百万 Token 输出价格"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
                <el-option v-for="dict in ai_model_currency" :key="dict.value" :label="dict.label + '（' + dict.value + '）'" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 默认与状态 -->
        <el-divider content-position="left">默认与状态</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="是否默认" prop="isDefault">
              <el-radio-group v-model="form.isDefault">
                <el-radio v-for="dict in sys_yes_no" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 连接测试结果（内联展示，不影响保存） -->
        <el-alert v-if="testResult" :type="testResult.success ? 'success' : 'error'" :closable="false" class="test-result">
          <template #title>
            {{ testResult.success ? '连接测试通过' : '连接测试失败' }}
          </template>
          <div class="test-result-body">
            <div>{{ testResult.message }}</div>
            <div class="test-meta">
              <span v-if="testResult.latencyMs != null">延迟 {{ testResult.latencyMs }}ms</span>
              <span v-if="testResult.apiProtocol">协议 {{ protocolLabel(testResult.apiProtocol) }}</span>
              <span v-if="testResult.detectedEmbeddingDimension != null">实测维度 {{ testResult.detectedEmbeddingDimension }}</span>
              <el-tag v-if="dimensionMismatch" type="danger" size="small">与配置维度 {{ form.embeddingDimension }} 不一致</el-tag>
            </div>
          </div>
        </el-alert>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="success" plain :loading="testing" @click="handleTestConnection">测试连接</el-button>
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listModel, getModel, addModel, updateModel, delModel, setDefaultModel, listProvider, getModelPolicy, testModel } from '@/api/ai/model';
import type { AiModel, AiModelPolicy, AiModelTestRequest, AiModelTestResult } from '@/api/ai/model';
import { applyModelTypeTemplate, joinCapabilities, normalizeCapabilityValues, splitCapabilities, type ModelTypeTemplate } from './model-config';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable, sys_yes_no, ai_provider_type, ai_model_type, ai_model_capability, ai_model_api_protocol, ai_model_currency } =
  toRefs<any>(
    proxy?.useDict(
      'sys_normal_disable',
      'sys_yes_no',
      'ai_provider_type',
      'ai_model_type',
      'ai_model_capability',
      'ai_model_api_protocol',
      'ai_model_currency'
    )
  );

const loading = ref(false);
const list = ref<AiModel[]>([]);
const total = ref(0);
const providerList = ref<any[]>([]);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const multiple = ref(true);

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
  contextWindow: undefined,
  inputCapabilities: '',
  outputCapabilities: '',
  apiProtocol: '',
  embeddingDimension: undefined,
  maxTokens: undefined,
  temperature: undefined,
  inputPricePerMillion: undefined,
  outputPricePerMillion: undefined,
  currency: 'CNY',
  isDefault: 'N',
  status: '0'
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
    modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
    embeddingDimension: [
      {
        validator: (_rule: any, _value: any, callback: (error?: Error) => void) => {
          if (templateFlags.value?.requiresEmbeddingDimension && !form.value.embeddingDimension) {
            callback(new Error('请输入向量维度'));
          } else {
            callback();
          }
        },
        trigger: 'blur'
      }
    ]
  }
});

const { queryParams, form, rules } = toRefs(data);

const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();

/** 能力勾选数组（与序列化的逗号分隔 API 字段分开存储，提交前再 join） */
const inputCaps = ref<string[]>([]);
const outputCaps = ref<string[]>([]);
/** 当前模型类型的能力模板开关（null = 存量未知类型，按未锁定可编辑表单展示） */
const templateFlags = ref<ModelTypeTemplate | null>(null);
/** 厂商协议策略（按 providerId 加载） */
const providerPolicy = ref<AiModelPolicy | null>(null);
/** 连接测试状态与结果（内联展示，不影响保存） */
const testing = ref(false);
const testResult = ref<AiModelTestResult | null>(null);

/** 能力勾选是否被类型模板锁定（EMBEDDING/RERANK） */
const capabilityLocked = computed(() => templateFlags.value?.lockCapabilities === true);

/** 协议下拉是否锁定：厂商策略已加载且仅允许一种协议 */
const protocolLocked = computed(() => !!providerPolicy.value && (providerPolicy.value.allowedProtocols?.length || 0) <= 1);

/** 按厂商策略过滤后的协议字典选项 */
const filteredProtocolOptions = computed(() => {
  const all = ai_model_api_protocol.value || [];
  const allowed = providerPolicy.value?.allowedProtocols;
  if (!allowed || !allowed.length) {
    return all;
  }
  return all.filter((dict: any) => allowed.includes(dict.value));
});

/** 实测向量维度与配置维度不一致提示 */
const dimensionMismatch = computed(() => {
  const detected = testResult.value?.detectedEmbeddingDimension;
  return detected != null && form.value.embeddingDimension != null && detected !== form.value.embeddingDimension;
});

/** 币种单位展示（CNY 用「元/M」，其余显示币种码） */
const currencyUnit = (row: AiModel) => (row.currency === 'CNY' ? '元/M' : `${row.currency || 'CNY'}/M`);

/** 协议码 → 字典标签 */
const protocolLabel = (code?: string) => {
  if (!code) return '-';
  const hit = (ai_model_api_protocol.value || []).find((dict: any) => dict.value === code);
  return hit ? hit.label : code;
};

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
  inputCaps.value = [];
  outputCaps.value = [];
  templateFlags.value = null;
  testResult.value = null;
  providerPolicy.value = null;
  // 不用 resetFields：element-plus 会把首次挂载时的字段值当初始值回填（首行编辑值可能预填到新增弹窗），
  // 整对象替换已重置取值，这里仅清理校验状态
  formRef.value?.clearValidate();
};

/** 弹窗关闭时清理测试结果 */
const handleDialogClose = () => {
  testResult.value = null;
};

/**
 * 打开编辑时套用类型模板（仅取显隐/锁定开关，不覆盖存量能力值）
 * <p>存量脏数据（未知/空模型类型）抛错时回退为「不锁定、能力留空」的可编辑表单，保证弹窗仍可打开。</p>
 */
const applyTemplateForOpen = () => {
  try {
    templateFlags.value = applyModelTypeTemplate(form.value.modelType);
  } catch {
    templateFlags.value = null;
    inputCaps.value = [];
    outputCaps.value = [];
    proxy?.$modal.msgWarning(`存量模型类型「${form.value.modelType || '空'}」不在字典 ai_model_type 中，请重新选择模型类型后再保存`);
  }
};

/** 模型类型切换：套用默认能力模板（深拷贝，可直接修改） */
const handleModelTypeChange = (value: string) => {
  try {
    const template = applyModelTypeTemplate(value);
    templateFlags.value = template;
    inputCaps.value = template.inputCapabilities;
    outputCaps.value = template.outputCapabilities;
  } catch {
    templateFlags.value = null;
  }
};

/** 厂商切换：加载协议策略并校正当前协议 */
watch(
  () => form.value.providerId,
  async (providerId) => {
    if (!providerId) return;
    const res = await getModelPolicy(providerId);
    // 等待响应期间厂商可能已再切换，丢弃过期策略避免回写错乱
    if (form.value.providerId !== providerId) return;
    providerPolicy.value = res.data;
    const policy = providerPolicy.value;
    if (!policy) return;
    if (!form.value.apiProtocol || !policy.allowedProtocols?.includes(form.value.apiProtocol)) {
      form.value.apiProtocol = policy.defaultProtocol;
    }
  }
);

/** 未锁定类型要求至少一项输入/输出能力（后端对空串按「未提供」保留旧值，需前端拦截空勾选） */
const validateCapabilities = (): boolean => {
  if (capabilityLocked.value) return true;
  if (!inputCaps.value.length) {
    proxy?.$modal.msgWarning('请至少选择一项输入能力');
    return false;
  }
  if (!outputCaps.value.length) {
    proxy?.$modal.msgWarning('请至少选择一项输出能力');
    return false;
  }
  return true;
};

/** 组装提交/测试载荷：能力数组序列化为逗号分隔串 */
const buildPayload = (): AiModel => ({
  ...form.value,
  inputCapabilities: joinCapabilities(inputCaps.value),
  outputCapabilities: joinCapabilities(outputCaps.value)
});

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
  inputCaps.value = normalizeCapabilityValues(splitCapabilities(form.value.inputCapabilities));
  outputCaps.value = normalizeCapabilityValues(splitCapabilities(form.value.outputCapabilities));
  applyTemplateForOpen();
  dialog.visible = true;
  dialog.title = '编辑模型';
};

/** 提交表单 */
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    if (!validateCapabilities()) return;
    const payload = buildPayload();
    form.value.id ? await updateModel(payload) : await addModel(payload);
    proxy?.$modal.msgSuccess('操作成功');
    dialog.visible = false;
    await getList();
  });
};

/** 弹窗内连接测试（草稿测试，不落库、不触发保存） */
const handleTestConnection = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    if (!validateCapabilities()) return;
    let confirmBillable = false;
    if (form.value.modelType === 'IMAGE') {
      try {
        await proxy?.$modal.confirm('将发起一次最小图片生成请求，可能产生模型费用，是否继续？');
        confirmBillable = true;
      } catch {
        return;
      }
    }
    testing.value = true;
    try {
      const payload: AiModelTestRequest = {
        ...buildPayload(),
        confirmBillableImageTest: confirmBillable || undefined
      };
      const res = await testModel(payload);
      testResult.value = res.data;
    } catch (e: any) {
      testResult.value = { success: false, message: e?.message || '连接测试失败' };
    } finally {
      testing.value = false;
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

<style scoped>
.provider-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.provider-opt-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cap-cells {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
}
.cap-cell {
  display: inline-flex;
  gap: 2px;
  flex-wrap: wrap;
  justify-content: center;
}
.cap-arrow {
  color: var(--el-text-color-secondary);
}
.cap-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 4px;
}
.cap-group-title {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}
.cap-lock-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.test-result {
  margin-top: 8px;
}
.test-result-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.test-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--el-text-color-secondary);
}
</style>
