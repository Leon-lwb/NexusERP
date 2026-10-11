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

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleGenerate" v-hasPermi="['wm:barcode:add']"> 生成条码 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:barcode:edit']"> 修改 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:barcode:remove']"> 删除 </el-button>
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
      <el-table-column label="业务类型" prop="bizType" align="center" min-width="90" />
      <!-- 技术债：状态暂无对应字典，使用本地 el-tag 渲染，待补字典后改为 dict-tag -->
      <el-table-column label="状态" prop="status" align="center" min-width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 'ACTIVE'" type="success">可用</el-tag>
          <el-tag v-else-if="scope.row.status === 'USED'" type="info">已使用</el-tag>
          <el-tag v-else-if="scope.row.status === 'EXPIRED'" type="danger">已过期</el-tag>
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
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:barcode:remove']"> 删除 </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 查看条码详情对话框 -->
    <el-dialog title="条码详情" v-model="viewOpen" width="500px" append-to-body>
      <el-descriptions :column="2" border>
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
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="条码" prop="barcodeCode">
          <el-input v-model="form.barcodeCode" :disabled="!!form.barcodeId" placeholder="系统自动生成或手动输入" />
        </el-form-item>
        <el-form-item label="配置编码" prop="configCode">
          <el-select v-model="form.configCode" filterable placeholder="请选择配置编码" style="width: 100%" @change="onConfigChange">
            <el-option v-for="item in configOptions" :key="item.configId" :label="item.configCode" :value="item.configCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置ID" prop="configId">
          <el-input v-model="form.configId" disabled style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-input v-model="form.bizType" placeholder="请输入业务类型" />
        </el-form-item>
        <el-form-item label="业务ID" prop="bizId">
          <el-input v-model="form.bizId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务行ID" prop="bizLineId">
          <el-input v-model="form.bizLineId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="物料ID" prop="materialId">
          <el-input v-model="form.materialId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次ID" prop="batchId">
          <el-input v-model="form.batchId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存ID" prop="stockId">
          <el-input v-model="form.stockId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="可用" value="ACTIVE" />
            <el-option label="已使用" value="USED" />
            <el-option label="已过期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 生成条码对话框 -->
    <el-dialog title="生成条码" v-model="generateOpen" width="500px" append-to-body>
      <el-form ref="generateRef" :model="generateForm" :rules="generateRules" label-width="100px">
        <el-form-item label="配置编码" prop="configCode">
          <el-select v-model="generateForm.configCode" filterable placeholder="请选择配置编码" style="width: 100%" @change="onGenerateConfigChange">
            <el-option v-for="item in configOptions" :key="item.configId" :label="item.configCode" :value="item.configCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-input v-model="generateForm.bizType" placeholder="请输入业务类型" />
        </el-form-item>
        <el-form-item label="业务ID" prop="bizId">
          <el-input v-model="generateForm.bizId" style="width: 100%" />
        </el-form-item>
        <el-form-item label="物料ID" prop="materialId">
          <el-input v-model="generateForm.materialId" style="width: 100%" />
        </el-form-item>
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
import { addBarcode, delBarcode, generateBarcode, getBarcode, listBarcode, updateBarcode, listBarcodeConfig } from '@/api/erp/wm/barcode';
import type { BarcodeConfigVO } from '@/api/erp/wm/barcode/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

// 获取当前实例
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const formRef = ref<FormInstance>();
const generateRef = ref<FormInstance>();

const configOptions = ref<BarcodeConfigVO[]>([]); // 条码配置下拉选项

// 查看对话框
const viewOpen = ref(false);
const viewForm = ref({
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
const generateForm = ref({
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
  configCode: [{ required: true, message: '请选择配置编码', trigger: 'change' }]
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
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleView: _rawHandleView
} = useCrud({
  apis: { list: listBarcode, get: getBarcode, add: addBarcode, update: updateBarcode, del: delBarcode },
  idField: 'barcodeId',
  entityName: '条码',
  formRef: formRef,
  queryParams,
  initForm: () => ({
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
  exportUrl: '/erp/wm/barcode/export',
  exportFileName: 'barcode'
});

function handleUpdate(row?: any) {
  return rawHandleUpdate(row);
}

// 查看（使用独立对话框）
function handleView(row: any) {
  viewForm.value = { ...row };
  viewOpen.value = true;
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
  const selected = configOptions.value.find((item) => item.configCode === val);
  if (selected) {
    form.value.configId = selected.configId;
    if (selected.bizType) {
      form.value.bizType = selected.bizType;
    }
  }
}

/**
 * 配置编码变更（生成条码）
 */
function onGenerateConfigChange(val: string) {
  const selected = configOptions.value.find((item) => item.configCode === val);
  if (selected && selected.bizType) {
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
function submitGenerate() {
  generateRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    generateBarcode(generateForm.value).then((res: any) => {
      proxy.$modal.msgSuccess(`生成成功：${res.data}`);
      generateOpen.value = false;
      getList();
    });
  });
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
