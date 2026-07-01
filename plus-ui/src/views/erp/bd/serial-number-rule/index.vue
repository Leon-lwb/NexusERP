<template>
  <div class="app-container">
    <!-- 技术债：规则类型/状态下拉使用硬编码 el-option，缺对应字典，待补字典后改 useDict -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="规则编码" prop="ruleCode">
            <el-input v-model="queryParams.ruleCode" placeholder="请输入规则编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="queryParams.ruleName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="规则类型" prop="ruleType">
            <el-select v-model="queryParams.ruleType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option label="产品序列号" value="PRODUCT_SN" />
              <el-option label="条码" value="BARCODE" />
              <el-option label="批次号" value="BATCH_NO" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:serialNumberRule:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['bd:serialNumberRule:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:serialNumberRule:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['bd:serialNumberRule:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:serialNumberRule:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 技术债：状态列使用硬编码 el-tag，缺对应字典，待补字典后改 dict-tag -->
    <el-table v-loading="loading" :data="ruleList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="规则编码" align="center" prop="ruleCode" />
      <el-table-column label="规则名称" align="center" prop="ruleName" min-width="120" show-overflow-tooltip />
      <el-table-column label="规则类型" align="center" prop="ruleType">
        <template #default="scope">
          <dict-tag :options="rule_type_options" :value="scope.row.ruleType" />
        </template>
      </el-table-column>
      <el-table-column label="前缀" align="center" prop="prefix" />
      <el-table-column label="日期格式" align="center" prop="dateFormat" />
      <el-table-column label="流水号长度" align="center" prop="seqLength" />
      <el-table-column label="重置周期" align="center" prop="seqCycle">
        <template #default="scope">
          <dict-tag :options="seq_cycle_options" :value="scope.row.seqCycle" />
        </template>
      </el-table-column>
      <el-table-column label="当前流水号" align="center" prop="currentSeq" />
      <el-table-column label="校验位规则" align="center" prop="checkDigitRule">
        <template #default="scope">
          <dict-tag :options="check_digit_options" :value="scope.row.checkDigitRule" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:serialNumberRule:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:serialNumberRule:remove']">删除</el-button>
          <el-button link type="primary" icon="Key" @click="handleGenerate(scope.row)" v-hasPermi="['bd:serialNumberRule:generate']">生成</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改序列号规则对话框 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="ruleRef" :model="form" :rules="rules" label-width="110px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规则编码" prop="ruleCode">
              <el-input v-model="form.ruleCode" placeholder="请输入规则编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规则类型" prop="ruleType">
              <el-select v-model="form.ruleType" placeholder="请选择规则类型" style="width: 100%;">
                <el-option label="产品序列号" value="PRODUCT_SN" />
                <el-option label="条码" value="BARCODE" />
                <el-option label="批次号" value="BATCH_NO" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="前缀" prop="prefix">
              <el-input v-model="form.prefix" placeholder="如 SN/M/BN" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日期格式" prop="dateFormat">
              <el-input v-model="form.dateFormat" placeholder="如 yyyyMMdd" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="流水号长度" prop="seqLength">
              <el-input-number v-model="form.seqLength" :min="1" :max="10" placeholder="补零位数" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重置周期" prop="seqCycle">
              <el-select v-model="form.seqCycle" placeholder="请选择重置周期" style="width: 100%;">
                <el-option label="不重置" value="NONE" />
                <el-option label="每天" value="DAY" />
                <el-option label="每月" value="MONTH" />
                <el-option label="每年" value="YEAR" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="校验位规则" prop="checkDigitRule">
              <el-select v-model="form.checkDigitRule" placeholder="请选择校验位规则" style="width: 100%;">
                <el-option label="无校验位" value="NONE" />
                <el-option label="MOD10" value="MOD10" />
                <el-option label="MOD11" value="MOD11" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio label="ACTIVE">启用</el-radio>
                <el-radio label="INACTIVE">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 生成序列号结果对话框 -->
    <el-dialog title="生成序列号" v-model="genVisible" width="400px" append-to-body>
      <div style="text-align: center; padding: 20px 0;">
        <p style="margin-bottom: 10px; color: #909399;">规则: {{ genRuleCode }}</p>
        <p style="font-size: 24px; font-weight: bold; font-family: monospace; color: #303133;">{{ genResult }}</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="genVisible = false">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="SerialNumberRule">
import {
  listSerialNumberRule,
  getSerialNumberRule,
  delSerialNumberRule,
  addSerialNumberRule,
  updateSerialNumberRule,
  generateSerialNumber
} from '@/api/erp/bd/serial-number-rule';

const { proxy } = getCurrentInstance();

const ruleList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

const genVisible = ref(false);
const genResult = ref('');
const genRuleCode = ref('');

const seq_cycle_options = [
  { label: '不重置', value: 'NONE' },
  { label: '每天', value: 'DAY' },
  { label: '每月', value: 'MONTH' },
  { label: '每年', value: 'YEAR' }
];

const check_digit_options = [
  { label: '无', value: 'NONE' },
  { label: 'MOD10', value: 'MOD10' },
  { label: 'MOD11', value: 'MOD11' }
];

const rule_type_options = [
  { label: '产品序列号', value: 'PRODUCT_SN' },
  { label: '条码', value: 'BARCODE' },
  { label: '批次号', value: 'BATCH_NO' }
];

const data = reactive({
  form: {} as any,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    ruleCode: null,
    ruleName: null,
    ruleType: null,
    status: null
  },
  rules: {
    ruleCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
    ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
    ruleType: [{ required: true, message: '规则类型不能为空', trigger: 'change' }],
    status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listSerialNumberRule(queryParams.value).then((response) => {
    ruleList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function cancel() {
  open.value = false;
  reset();
}

function reset() {
  form.value = {
    ruleId: null,
    ruleCode: null,
    ruleName: null,
    ruleType: 'PRODUCT_SN',
    prefix: null,
    dateFormat: null,
    seqLength: 4,
    seqCycle: 'NONE',
    currentSeq: 0,
    checkDigitRule: 'NONE',
    status: 'ACTIVE',
    remark: null
  };
  proxy.resetForm('ruleRef');
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item) => item.ruleId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加序列号规则';
}

function handleUpdate(row: any) {
  reset();
  isViewMode.value = false;
  const _ruleId = row.ruleId || ids.value;
  getSerialNumberRule(_ruleId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '修改序列号规则';
  });
}

function handleView(row: any) {
  reset();
  isViewMode.value = true;
  const _ruleId = row.ruleId || ids.value;
  getSerialNumberRule(_ruleId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '查看序列号规则';
  });
}

function submitForm() {
  proxy.$refs['ruleRef'].validate((valid: boolean) => {
    if (valid) {
      if (form.value.ruleId != null) {
        updateSerialNumberRule(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addSerialNumberRule(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row: any) {
  const _ruleIds = row.ruleId || ids.value;
  proxy.$modal
    .confirm('是否确认删除序列号规则编号为"' + _ruleIds + '"的数据项？')
    .then(() => {
      return delSerialNumberRule(_ruleIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download(
    'bd/serial-number-rule/export',
    {
      ...queryParams.value
    },
    `serial-number-rule_${new Date().getTime()}.xlsx`
  );
}

function handleGenerate(row: any) {
  genRuleCode.value = row.ruleCode;
  genResult.value = '';
  generateSerialNumber(row.ruleCode).then((response) => {
    genResult.value = response.data;
    genVisible.value = true;
  });
}

getList();
</script>

<style scoped lang="scss">
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
