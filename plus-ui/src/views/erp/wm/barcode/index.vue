<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="条码" prop="barcodeCode">
            <el-input v-model="queryParams.barcodeCode" placeholder="请输入条码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="配置编码" prop="configCode">
            <el-input v-model="queryParams.configCode" placeholder="请输入配置编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="业务类型" prop="bizType">
            <el-input v-model="queryParams.bizType" clearable placeholder="请输入业务类型" @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <!-- 技术债：状态暂无对应字典，使用硬编码选项，待补字典后改为 useDict + dict-tag -->
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option label="可用" value="ACTIVE" />
              <el-option label="已使用" value="USED" />
              <el-option label="已过期" value="EXPIRED" />
              <el-option label="已停用" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-alert
      title="每个新条码必须指向一个已存在的SN、批次、物料或受支持单据。已创建的主码、主体和上下文不能重绑；停用仍保留原号码与映射；历史未声明类型的条码仅保留原映射。"
      type="info"
      :closable="false"
      show-icon
      class="mb8"
    />
    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:barcode:add']">手工登记</el-button></el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleGenerate" v-hasPermi="['wm:barcode:add']"> 生成条码 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:barcode:edit']"> 修改 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="CircleClose" :disabled="multiple" @click="handleDisable()" v-hasPermi="['wm:barcode:remove']">
          停用
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:barcode:export']"> 导出 </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="条码" prop="barcodeCode" align="center" min-width="180">
        <template #default="scope">
          <div class="barcode-cell">
            <span class="barcode-text">{{ scope.row.barcodeCode }}</span>
            <el-button
              link
              type="primary"
              icon="DocumentCopy"
              v-copyText="scope.row.barcodeCode"
              v-copyText:callback="onCopySuccess"
              class="copy-btn"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column label="配置编码" prop="configCode" align="center" min-width="140" />
      <el-table-column label="主对象" min-width="150"
        ><template #default="{ row }">{{ primaryLabel(row.primaryObjectType) }}</template></el-table-column
      >
      <el-table-column label="业务类型" prop="bizType" align="center" min-width="90" />
      <!-- 技术债：状态暂无对应字典，使用本地 el-tag 渲染，待补字典后改为 dict-tag -->
      <el-table-column label="状态" prop="status" align="center" min-width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 'ACTIVE'" type="success">可用</el-tag>
          <el-tag v-else-if="scope.row.status === 'USED'" type="info">已使用</el-tag>
          <el-tag v-else-if="scope.row.status === 'EXPIRED'" type="danger">已过期</el-tag>
          <el-tag v-else-if="scope.row.status === 'INACTIVE'" type="warning">已停用</el-tag>
          <el-tag v-else>{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生成时间" prop="generateTime" align="center" width="160">
        <template #default="scope">
          {{ parseTime(scope.row.generateTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="info" icon="View" @click="handleView(scope.row)" v-hasPermi="['wm:barcode:query']"> 查看 </el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:barcode:edit']"> 修改 </el-button>
          <el-button link type="danger" icon="CircleClose" @click="handleDisable(scope.row)" v-hasPermi="['wm:barcode:remove']"> 停用 </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 查看条码详情对话框 -->
    <el-dialog title="条码详情" v-model="viewOpen" width="500px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="主对象" :span="2">{{ primaryLabel(viewForm.primaryObjectType) }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ viewForm.expireTime || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="条码" :span="2">{{ viewForm.barcodeCode }}</el-descriptions-item>
        <el-descriptions-item label="配置编码">{{ viewForm.configCode }}</el-descriptions-item>
        <el-descriptions-item label="配置ID">{{ viewForm.configId }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ viewForm.bizType }}</el-descriptions-item>
        <el-descriptions-item label="业务ID">{{ viewForm.bizId }}</el-descriptions-item>
        <el-descriptions-item label="业务行ID">{{ viewForm.bizLineId }}</el-descriptions-item>
        <el-descriptions-item label="物料ID">{{ viewForm.materialId }}</el-descriptions-item>
        <el-descriptions-item label="批次ID">{{ viewForm.batchId }}</el-descriptions-item>
        <el-descriptions-item label="库存ID">{{ viewForm.stockId }}</el-descriptions-item>
        <el-descriptions-item label="序列号ID">{{ viewForm.snId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="viewForm.status === 'ACTIVE'" type="success">可用</el-tag>
          <el-tag v-else-if="viewForm.status === 'USED'" type="info">已使用</el-tag>
          <el-tag v-else-if="viewForm.status === 'EXPIRED'" type="danger">已过期</el-tag>
          <el-tag v-else-if="viewForm.status === 'INACTIVE'" type="warning">已停用</el-tag>
          <span v-else>{{ viewForm.status }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ parseTime(viewForm.generateTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewForm.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="viewOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 编辑/新增条码对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body :close-on-click-modal="!saving" :show-close="!saving">
      <el-alert
        v-if="form.barcodeId"
        :title="
          form.primaryObjectType == null
            ? '历史来源未声明主对象，保留原NULL映射；仅维护状态、有效期与备注。'
            : '原主码、主体及全部来源上下文已锁定；仅维护状态、有效期与备注。'
        "
        type="info"
        :closable="false"
        class="mb8"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" :disabled="saving">
        <el-form-item label="主对象" prop="primaryObjectType"
          ><el-select
            v-model="form.primaryObjectType"
            @change="onPrimaryChange"
            :disabled="!!form.barcodeId"
            :placeholder="form.barcodeId ? '历史来源（未声明）' : '请选择主对象（必选）'"
            style="width: 100%"
            ><el-option v-for="option in primaryOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select
        ></el-form-item>
        <el-form-item label="条码" prop="barcodeCode">
          <el-input v-model="form.barcodeCode" :disabled="!!form.barcodeId" placeholder="系统自动生成或手动输入" />
        </el-form-item>
        <el-form-item label="配置编码" prop="configCode">
          <el-select
            v-model="form.configCode"
            :disabled="!!form.barcodeId"
            filterable
            placeholder="请选择配置编码"
            style="width: 100%"
            @change="onConfigChange"
          >
            <el-option v-for="item in configOptions" :key="item.configId" :label="item.configCode" :value="item.configCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置ID" prop="configId">
          <el-input v-model="form.configId" disabled style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select
            v-if="form.primaryObjectType === 'DOCUMENT'"
            v-model="form.bizType"
            :disabled="!!form.barcodeId"
            placeholder="选择canonical单据类型"
            style="width: 100%"
            ><el-option v-for="value in BARCODE_DOCUMENT_TYPES" :key="value" :value="value" :label="value"
          /></el-select>
          <el-input v-else v-model="form.bizType" :disabled="!!form.barcodeId" placeholder="请输入业务类型" />
        </el-form-item>
        <el-form-item label="业务ID" prop="bizId">
          <el-input v-model="form.bizId" :disabled="!!form.barcodeId" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.primaryObjectType !== 'DOCUMENT' || !!form.barcodeId" label="业务行ID" prop="bizLineId">
          <el-input v-model="form.bizLineId" :disabled="!!form.barcodeId" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.primaryObjectType !== 'DOCUMENT' || !!form.barcodeId" label="物料ID" prop="materialId">
          <el-input v-model="form.materialId" :disabled="!!form.barcodeId" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.primaryObjectType !== 'DOCUMENT' || !!form.barcodeId" label="批次ID" prop="batchId">
          <el-input v-model="form.batchId" :disabled="!!form.barcodeId" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.primaryObjectType !== 'DOCUMENT' || !!form.barcodeId" label="库存ID" prop="stockId">
          <el-input v-model="form.stockId" :disabled="!!form.barcodeId" style="width: 100%" />
        </el-form-item>
        <el-form-item
          v-if="form.primaryObjectType !== 'DOCUMENT' || !!form.barcodeId"
          label="正式SN身份ID"
          prop="snId"
          :required="!form.barcodeId && form.primaryObjectType === 'SN'"
          ><el-input v-model="form.snId" :disabled="!!form.barcodeId" placeholder="已有ERP正式SN身份ID，保留完整字符串"
        /></el-form-item>
        <p v-if="!form.barcodeId">
          主对象需提供对应的已有ID；其余ID仅作上下文并由服务端核验。DOCUMENT仅接受六种canonical业务类型与单据头ID，不提交行ID或SN、批次、物料、库存上下文。
        </p>
        <el-form-item label="有效期" prop="expireTime"
          ><el-date-picker
            v-model="form.expireTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择有效期，可清空"
            clearable
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="可用" value="ACTIVE" />
            <el-option label="已使用" value="USED" />
            <el-option label="已过期" value="EXPIRED" />
            <el-option label="已停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
        <el-button :disabled="saving" @click="open = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 生成条码对话框 -->
    <el-dialog title="生成条码" v-model="generateOpen" width="500px" append-to-body>
      <el-alert
        title="生成接口按SN、批次、物料、单据由精到粗确定主对象，并核对其它上下文。单据主体只提交canonical业务类型及单据头ID。仅登记条码，不代表在库。"
        type="info"
        :closable="false"
        class="mb8"
      />
      <el-form ref="generateRef" :model="generateForm" :rules="generateRules" label-width="100px">
        <el-form-item label="配置编码" prop="configCode">
          <el-select v-model="generateForm.configCode" filterable placeholder="请选择配置编码" style="width: 100%" @change="onGenerateConfigChange">
            <el-option v-for="item in configOptions" :key="item.configId" :label="item.configCode" :value="item.configCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="generateForm.bizType" placeholder="可选canonical单据类型" clearable style="width: 100%"
            ><el-option v-for="value in BARCODE_DOCUMENT_TYPES" :key="value" :label="value" :value="value"
          /></el-select>
        </el-form-item>
        <el-form-item label="业务ID" prop="bizId">
          <el-input v-model="generateForm.bizId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="物料ID" prop="materialId">
          <el-input v-model="generateForm.materialId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次ID" prop="batchId"
          ><el-input v-model="generateForm.batchId" placeholder="已有批次ID，保留完整字符串"
        /></el-form-item>
        <el-form-item label="序列号ID" prop="snId">
          <el-input v-model="generateForm.snId" placeholder="序列号物料生成的条码绑定SN" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitGenerate">生 成</el-button>
        <el-button @click="generateOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Barcode">
// 导入条码相关的API方法
import {
  addBarcode,
  delBarcode,
  generateBarcode,
  getBarcode,
  listBarcode,
  updateBarcode,
  listBarcodeConfig,
  BARCODE_DOCUMENT_TYPES
} from '@/api/erp/wm/barcode';
import type { BarcodeConfigVO, BarcodeVO, BarcodeForm, BarcodePrimaryObjectType } from '@/api/erp/wm/barcode/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

// 获取当前实例
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const formRef = ref<FormInstance>();
const generateRef = ref<FormInstance>();
const originalBarcode = shallowRef<BarcodeVO>();
const saving = ref(false);
const primaryOptions: Array<{ value: BarcodePrimaryObjectType; label: string }> = [
  { value: 'SN', label: 'SN · 正式序列号' },
  { value: 'BATCH', label: 'BATCH · 批次' },
  { value: 'MATERIAL', label: 'MATERIAL · 物料' },
  { value: 'DOCUMENT', label: 'DOCUMENT · 单据' }
];
/** NULL历史只显示未声明，不根据附带ID自行推造主对象类型。 */
function primaryLabel(value?: BarcodePrimaryObjectType | null) {
  return primaryOptions.find((option) => option.value === value)?.label || '历史来源（未声明）';
}

const configOptions = ref<BarcodeConfigVO[]>([]); // 条码配置下拉选项

// 查看对话框
const viewOpen = ref(false);
const viewForm = ref<Partial<BarcodeVO>>({
  primaryObjectType: undefined,
  barcodeId: undefined,
  barcodeCode: '',
  configId: undefined,
  configCode: '',
  bizType: '',
  bizId: undefined,
  bizLineId: undefined,
  materialId: undefined,
  batchId: undefined,
  stockId: undefined,
  snId: undefined,
  status: 'ACTIVE',
  remark: '',
  generateTime: ''
});

// 生成条码对话框
const generateOpen = ref(false);
const generateForm = ref<{ configCode: string; bizType: string; bizId?: string; materialId?: string; batchId?: string; snId?: string }>({
  batchId: undefined,
  configCode: '',
  bizType: '',
  bizId: undefined,
  materialId: undefined,
  snId: undefined
});
const generateRules = {
  configCode: [{ required: true, message: '请选择配置编码', trigger: 'change' }]
};

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  barcodeCode: null,
  configCode: null,
  bizType: null,
  bizId: null,
  materialId: null,
  stockId: null,
  snId: null,
  status: null
});

const rules = {
  barcodeCode: [{ required: true, message: '条码不能为空', trigger: 'blur' }],
  configCode: [
    {
      validator: (_rule: unknown, value: unknown, done: (error?: Error) => void) =>
        done(!form.value.barcodeId && !value ? new Error('请选择配置编码') : undefined),
      trigger: 'change'
    }
  ],
  primaryObjectType: [
    {
      validator: (_rule: unknown, value: unknown, done: (error?: Error) => void) =>
        done(!form.value.barcodeId && !value ? new Error('请选择主对象类型') : undefined),
      trigger: 'change'
    }
  ]
};

const {
  loading,
  list,
  total,
  open,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleExport,
  handleSelectionChange,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleView: _rawHandleView
} = useCrud({
  apis: {
    list: listBarcode,
    get: getBarcode,
    add: addBarcode,
    update: (data: BarcodeForm) => updateBarcode(data, originalBarcode.value),
    del: delBarcode
  },
  idField: 'barcodeId',
  entityName: '条码',
  formRef: formRef,
  queryParams,
  initForm: () => ({
    primaryObjectType: undefined,
    snId: undefined,
    expireTime: undefined,
    barcodeId: undefined,
    barcodeCode: '',
    configId: undefined,
    configCode: '',
    bizType: '',
    bizId: undefined,
    bizLineId: undefined,
    materialId: undefined,
    batchId: undefined,
    stockId: undefined,
    status: 'ACTIVE',
    remark: ''
  }),
  afterLoad: (value: BarcodeVO) => {
    originalBarcode.value = JSON.parse(JSON.stringify(value));
  },
  exportUrl: '/erp/wm/barcode/export',
  exportFileName: 'barcode'
});

function handleUpdate(row?: any) {
  originalBarcode.value = undefined;
  return rawHandleUpdate(row).catch((cause: Error) => proxy.$modal.msgError(cause.message || '读取原条码失败'));
}
/** 切换为新单据主体时清空不受支持的上下文，历史编辑保持原映射。 */
function onPrimaryChange(value: BarcodePrimaryObjectType) {
  if (form.value.barcodeId || value !== 'DOCUMENT') return;
  for (const key of ['bizLineId', 'snId', 'batchId', 'materialId', 'stockId'] as const) form.value[key] = undefined;
  if (!(BARCODE_DOCUMENT_TYPES as readonly string[]).includes(form.value.bizType || '')) form.value.bizType = '';
}
/** 旧DELETE端点现在仅停用，不回收号码或清除原主体映射。 */
async function handleDisable(row?: BarcodeVO) {
  const selected = row?.barcodeId ?? ids.value;
  if (!selected || (Array.isArray(selected) && !selected.length)) return;
  try {
    await proxy.$modal.confirm('确认停用所选条码？原号码与主体映射永久保留，不能回收或重新绑定。');
    await delBarcode(selected);
    proxy.$modal.msgSuccess('条码已停用，原号码与映射保留');
    getList();
  } catch (cause) {
    if (cause instanceof Error) proxy.$modal.msgError(cause.message);
  }
}
function handleAdd() {
  originalBarcode.value = undefined;
  rawHandleAdd();
}
/** 原详情快照保护主体映射，提交只维护状态、有效期和备注。 */
async function submitForm() {
  if (!formRef.value || saving.value) return;
  try {
    await formRef.value.validate();
    saving.value = true;
    if (form.value.barcodeId) {
      if (!originalBarcode.value) throw new Error('缺少原条码详情，请重新读取');
      await updateBarcode(form.value as BarcodeForm, originalBarcode.value);
    } else await addBarcode(form.value as BarcodeForm);
    proxy.$modal.msgSuccess(form.value.barcodeId ? '条码维护成功' : '条码登记成功');
    open.value = false;
    getList();
  } catch (cause) {
    if (cause instanceof Error) proxy.$modal.msgError(cause.message);
  } finally {
    saving.value = false;
  }
}

// 查看（使用独立对话框）
async function handleView(row: BarcodeVO) {
  try {
    viewForm.value = (await getBarcode(row.barcodeId)).data;
    viewOpen.value = true;
  } catch (cause) {
    if (cause instanceof Error) proxy.$modal.msgError(cause.message);
  }
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/**
 * 获取条码配置下拉选项
 */
function loadConfigOptions() {
  listBarcodeConfig({ pageNum: 1, pageSize: 9999 }).then((res: any) => {
    configOptions.value = res.rows || [];
  });
}

/**
 * 配置编码变更（编辑/新增）
 */
function onConfigChange(val: string) {
  if (form.value.barcodeId) return;
  const selected = configOptions.value.find((item) => item.configCode === val);
  if (selected) {
    form.value.configId = selected.configId;
    if (
      selected.bizType &&
      (form.value.primaryObjectType !== 'DOCUMENT' || (BARCODE_DOCUMENT_TYPES as readonly string[]).includes(selected.bizType))
    ) {
      form.value.bizType = selected.bizType;
    }
  }
}

/**
 * 配置编码变更（生成条码）
 */
function onGenerateConfigChange(val: string) {
  const selected = configOptions.value.find((item) => item.configCode === val);
  if (selected?.bizType && (BARCODE_DOCUMENT_TYPES as readonly string[]).includes(selected.bizType)) {
    generateForm.value.bizType = selected.bizType;
  }
}

/**
 * 复制成功回调
 */
function onCopySuccess(val: string) {
  proxy.$modal.msgSuccess(`已复制：${val}`);
}

/**
 * 生成条码按钮操作
 */
function handleGenerate() {
  generateForm.value = {
    configCode: '',
    bizType: '',
    bizId: undefined,
    materialId: undefined,
    batchId: undefined,
    snId: undefined
  };
  generateOpen.value = true;
  nextTick(() => {
    generateRef.value?.clearValidate();
  });
}

/**
 * 提交生成条码
 */
async function submitGenerate() {
  if (!generateRef.value) return;
  try {
    await generateRef.value.validate();
    const res = await generateBarcode(generateForm.value);
    proxy.$modal.msgSuccess(`生成成功：${res.data}`);
    generateOpen.value = false;
    getList();
  } catch (cause) {
    if (cause instanceof Error) proxy.$modal.msgError(cause.message);
  }
}

// 初始化数据
loadConfigOptions();
getList();
</script>

<style scoped lang="scss">
.barcode-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.barcode-text {
  font-weight: 600;
  font-family: 'Courier New', Courier, monospace;
  color: var(--el-color-primary);
}
.copy-btn {
  padding: 2px;
}
</style>
