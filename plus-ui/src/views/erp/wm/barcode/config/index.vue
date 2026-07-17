<template>
  <div class="app-container">
    <!-- 搜索表单区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="规则编码" prop="configCode">
            <el-input v-model="queryParams.configCode" placeholder="请输入规则编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="规则名称" prop="configName">
            <el-input v-model="queryParams.configName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="业务类型" prop="bizType">
            <el-input v-model="queryParams.bizType" placeholder="请输入业务类型" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <!-- 技术债：启用状态暂无对应字典，使用硬编码选项，待补字典后改为 useDict + dict-tag -->
        <el-col :span="6">
          <el-form-item label="启用状态" prop="enableFlag">
            <el-select v-model="queryParams.enableFlag" placeholder="请选择" clearable style="width: 100%">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:barcodeConfig:add']"> 新增 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:barcodeConfig:edit']"> 修改 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:barcodeConfig:remove']">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:barcodeConfig:export']"> 导出 </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 数据表格区域 -->
    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="规则编码" prop="configCode" align="center" />
      <el-table-column label="规则名称" prop="configName" align="center" min-width="120" show-overflow-tooltip />
      <el-table-column label="前缀" prop="prefix" align="center" />
      <el-table-column label="日期格式" prop="datePattern" align="center" />
      <el-table-column label="流水长度" prop="sequenceLength" align="center" />
      <el-table-column label="业务类型" prop="bizType" align="center" />
      <!-- 技术债：启用状态暂无对应字典，使用本地 el-tag 渲染，待补字典后改为 dict-tag -->
      <el-table-column label="启用状态" prop="enableFlag" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.enableFlag === 1 ? 'success' : 'info'">
            {{ scope.row.enableFlag === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" align="center" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:barcodeConfig:edit']"> 修改 </el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:barcodeConfig:remove']"> 删除 </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 编辑弹窗 -->
    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="规则编码" prop="configCode">
          <el-input v-model="form.configCode" />
        </el-form-item>
        <el-form-item label="规则名称" prop="configName">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item label="前缀" prop="prefix">
          <el-input v-model="form.prefix" />
        </el-form-item>
        <el-form-item label="日期格式" prop="datePattern">
          <el-input v-model="form.datePattern" placeholder="yyyyMMdd" />
        </el-form-item>
        <el-form-item label="流水号长度" prop="sequenceLength">
          <el-input-number v-model="form.sequenceLength" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="校验位" prop="withCheckDigit">
          <el-switch v-model="form.withCheckDigit" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-input v-model="form.bizType" />
        </el-form-item>
        <el-form-item label="物料类别ID" prop="categoryId">
          <el-input-number v-model="form.categoryId" :min="0" />
        </el-form-item>
        <el-form-item label="启用状态" prop="enableFlag">
          <el-radio-group v-model="form.enableFlag">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BarcodeConfig">
// 导入条形码配置相关的API方法
import { addBarcodeConfig, delBarcodeConfig, getBarcodeConfig, listBarcodeConfig, updateBarcodeConfig } from '@/api/erp/wm/barcode';

// 获取当前实例的代理对象
const { proxy } = getCurrentInstance() as any;

// 定义响应式数据
const list = ref([]); // 条形码配置列表
const loading = ref(false); // 加载状态
const showSearch = ref(true); // 显示搜索区域
const ids = ref<any[]>([]); // 选中的ID数组
const single = ref(true); // 单选状态
const multiple = ref(true); // 多选状态
const open = ref(false); // 弹窗打开状态
const total = ref(0); // 总记录数
const title = ref(''); // 弹窗标题

// 定义表单和查询参数
const data = reactive({
  // 查询参数
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    configCode: null,
    configName: null,
    bizType: null,
    categoryId: null,
    enableFlag: null
  },
  // 表单数据
  form: {
    configId: undefined,
    configCode: '',
    configName: '',
    prefix: '',
    datePattern: 'yyyyMMdd',
    sequenceLength: 6,
    withCheckDigit: 0,
    bizType: '',
    categoryId: undefined,
    enableFlag: 1,
    remark: ''
  },
  // 表单验证规则
  rules: {
    configCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
    configName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }]
  }
});

// 解构响应式数据
const { queryParams, form, rules } = toRefs(data);

/**
 * 获取条形码配置列表
 */
function getList() {
  loading.value = true;
  listBarcodeConfig(queryParams.value)
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
  ids.value = selection.map((i) => i.configId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/**
 * 新增按钮操作
 */
function handleAdd() {
  form.value = {
    configId: undefined,
    configCode: '',
    configName: '',
    prefix: '',
    datePattern: 'yyyyMMdd',
    sequenceLength: 6,
    withCheckDigit: 0,
    bizType: '',
    categoryId: undefined,
    enableFlag: 1,
    remark: ''
  };
  open.value = true;
  title.value = '新增条码配置';
  nextTick(() => {
    proxy.$refs.formRef?.clearValidate();
  });
}

/**
 * 修改按钮操作
 */
function handleUpdate(row: any) {
  const id = row?.configId || ids.value[0];
  getBarcodeConfig(id).then((res: any) => {
    form.value = res.data;
    open.value = true;
    title.value = '修改条码配置';
    nextTick(() => {
      proxy.$refs.formRef?.clearValidate();
    });
  });
}

/**
 * 删除按钮操作
 */
function handleDelete(row: any) {
  const delIds = row?.configId || ids.value;
  proxy.$modal
    .confirm('是否确认删除条码配置？')
    .then(() => delBarcodeConfig(delIds))
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    });
}

/**
 * 导出按钮操作
 */
function handleExport() {
  proxy.download('/erp/wm/barcode/config/export', { ...queryParams.value }, `barcode_config_${Date.now()}.xlsx`);
}

/**
 * 提交表单
 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (!valid) return;
    const api = form.value.configId ? updateBarcodeConfig : addBarcodeConfig;
    api(form.value).then(() => {
      proxy.$modal.msgSuccess('保存成功');
      open.value = false;
      getList();
    });
  });
}

// 页面加载时获取数据
getList();
</script>

