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
      <el-table-column label="配置编码" prop="configCode" align="center" />
      <el-table-column label="业务类型" prop="bizType" align="center" />
      <!-- 技术债：状态暂无对应字典，使用本地 el-tag 渲染，待补字典后改为 dict-tag -->
      <el-table-column label="状态" prop="status" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 'ACTIVE'" type="success">可用</el-tag>
          <el-tag v-else-if="scope.row.status === 'USED'" type="info">已使用</el-tag>
          <el-tag v-else-if="scope.row.status === 'EXPIRED'" type="danger">已过期</el-tag>
          <el-tag v-else>{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生成时间" prop="generateTime" align="center">
        <template #default="scope">
          {{ parseTime(scope.row.generateTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
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
    <el-dialog title="条码详情" v-model="viewOpen" width="520px" append-to-body>
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
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
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
          <el-input-number v-model="form.configId" :min="0" disabled style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-input v-model="form.bizType" placeholder="请输入业务类型" />
        </el-form-item>
        <el-form-item label="业务ID" prop="bizId">
          <el-input-number v-model="form.bizId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务行ID" prop="bizLineId">
          <el-input-number v-model="form.bizLineId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="物料ID" prop="materialId">
          <el-input-number v-model="form.materialId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次ID" prop="batchId">
          <el-input-number v-model="form.batchId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存ID" prop="stockId">
          <el-input-number v-model="form.stockId" :min="0" style="width: 100%" />
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
    <el-dialog title="生成条码" v-model="generateOpen" width="520px" append-to-body>
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
          <el-input-number v-model="generateForm.bizId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="物料ID" prop="materialId">
          <el-input-number v-model="generateForm.materialId" :min="0" style="width: 100%" />
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

// 获取当前实例
const { proxy } = getCurrentInstance() as any;

// 定义响应式数据
const list = ref([]); // 条码列表
const loading = ref(false); // 加载状态
const showSearch = ref(true); // 显示搜索区域
const ids = ref<any[]>([]); // 选中的ID数组
const single = ref(true); // 单选状态
const multiple = ref(true); // 多选状态
const total = ref(0); // 总记录数
const open = ref(false); // 编辑对话框显示状态
const generateOpen = ref(false); // 生成对话框显示状态
const viewOpen = ref(false); // 查看对话框显示状态
const title = ref(''); // 对话框标题
const configOptions = ref<BarcodeConfigVO[]>([]); // 条码配置下拉选项

// 定义页面数据
const data = reactive({
  // 查询参数
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    barcodeCode: null,
    configCode: null,
    bizType: null,
    bizId: null,
    materialId: null,
    stockId: null,
    status: null
  },
  // 表单数据
  form: {
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
  },
  // 查看表单数据
  viewForm: {
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
    remark: '',
    generateTime: ''
  },
  // 生成条码表单数据
  generateForm: {
    configCode: '',
    bizType: '',
    bizId: undefined,
    materialId: undefined
  },
  // 表单验证规则
  rules: {
    barcodeCode: [{ required: true, message: '条码不能为空', trigger: 'blur' }],
    configCode: [{ required: true, message: '请选择配置编码', trigger: 'change' }]
  },
  // 生成表单验证规则
  generateRules: {
    configCode: [{ required: true, message: '请选择配置编码', trigger: 'change' }]
  }
});

// 解构数据
const { queryParams, form, viewForm, rules, generateForm, generateRules } = toRefs(data);

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
 * 获取条码列表
 */
function getList() {
  loading.value = true;
  listBarcode(queryParams.value)
    .then((res: any) => {
      list.value = res.rows;
      total.value = res.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

/**
 * 搜索按钮操作
 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/**
 * 重置按钮操作
 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/**
 * 多选框选中数据
 */
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((i) => i.barcodeId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/**
 * 查看按钮操作
 */
function handleView(row: any) {
  viewForm.value = { ...row };
  viewOpen.value = true;
}

/**
 * 生成条码按钮操作
 */
function handleGenerate() {
  generateForm.value = {
    configCode: '',
    bizType: '',
    bizId: undefined,
    materialId: undefined
  };
  generateOpen.value = true;
  nextTick(() => {
    proxy.$refs.generateRef?.clearValidate();
  });
}

/**
 * 提交生成条码
 */
function submitGenerate() {
  proxy.$refs['generateRef'].validate((valid: boolean) => {
    if (!valid) return;
    generateBarcode(generateForm.value).then((res: any) => {
      proxy.$modal.msgSuccess(`生成成功：${res.data}`);
      generateOpen.value = false;
      getList();
    });
  });
}

/**
 * 修改按钮操作
 * @param row 选中的条码数据
 */
function handleUpdate(row: any) {
  const id = row?.barcodeId || ids.value[0];
  getBarcode(id).then((res: any) => {
    form.value = res.data;
    open.value = true;
    title.value = '修改条码实例';
    nextTick(() => {
      proxy.$refs.formRef?.clearValidate();
    });
  });
}

/**
 * 删除按钮操作
 * @param row 选中的条码数据
 */
function handleDelete(row: any) {
  const delIds = row?.barcodeId || ids.value;
  proxy.$modal
    .confirm('是否确认删除条码实例？')
    .then(() => delBarcode(delIds))
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    });
}

/**
 * 导出按钮操作
 */
function handleExport() {
  proxy.download('/erp/wm/barcode/export', { ...queryParams.value }, `barcode_${Date.now()}.xlsx`);
}

/**
 * 提交表单
 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (!valid) return;
    const api = form.value.barcodeId ? updateBarcode : addBarcode;
    api(form.value).then(() => {
      proxy.$modal.msgSuccess('保存成功');
      open.value = false;
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
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
