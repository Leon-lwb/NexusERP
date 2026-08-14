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
          <el-input v-model="form.categoryId" />
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
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

// 获取当前实例的代理对象
const { proxy } = getCurrentInstance() as any;

const formRef = ref<FormInstance>();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  configCode: null,
  configName: null,
  bizType: null,
  categoryId: null,
  enableFlag: null
});

const rules = {
  configCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
  configName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }]
};

const {
  loading, list, total, open, title, ids, single, multiple, showSearch, form,
  getList, handleQuery, handleDelete, handleExport, handleSelectionChange, submitForm, cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listBarcodeConfig, get: getBarcodeConfig, add: addBarcodeConfig, update: updateBarcodeConfig, del: delBarcodeConfig },
  idField: 'configId',
  entityName: '条码配置',
  formRef: formRef,
  queryParams,
  initForm: () => ({
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
  }),
  exportUrl: '/erp/wm/barcode/config/export',
  exportFileName: 'barcode_config'
});

function handleAdd() {
  rawHandleAdd();
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row);
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 页面加载时获取数据
getList();
</script>
