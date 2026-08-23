<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工序名称" prop="processName">
            <el-input v-model="queryParams.processName" placeholder="请输入工序名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="校验类型" prop="checkType">
            <el-select v-model="queryParams.checkType" placeholder="请选择校验类型" clearable style="width: 100%">
              <el-option v-for="dict in mes_check_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="校验阶段" prop="checkStage">
            <el-select v-model="queryParams.checkStage" placeholder="请选择阶段" clearable style="width: 100%">
              <el-option v-for="dict in mes_check_stage" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:pro:step-check-rule:add']">新增规则</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:pro:step-check-rule:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['mes:pro:step-check-rule:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工序编码" prop="processCode" min-width="130" />
      <el-table-column label="工序名称" prop="processName" min-width="130" />
      <el-table-column label="校验类型" prop="checkType" min-width="160">
        <template #default="scope"><dict-tag :options="mes_check_type" :value="scope.row.checkType" /></template>
      </el-table-column>
      <el-table-column label="校验阶段" prop="checkStage" align="center" width="100">
        <template #default="scope"><dict-tag :options="mes_check_stage" :value="scope.row.checkStage" /></template>
      </el-table-column>
      <el-table-column label="失败提示" prop="errorMsg" min-width="200" show-overflow-tooltip />
      <el-table-column label="排序" prop="sortOrder" align="center" width="70" />
      <el-table-column label="启用" prop="enabled" align="center" width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.enabled" :active-value="'1'" :inactive-value="'0'" disabled />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:pro:step-check-rule:edit']">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:pro:step-check-rule:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="ruleFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工序" prop="processId">
          <el-input v-model="form.processName" placeholder="请选择工序" readonly>
            <template #append><el-button icon="Search" @click="openProcessDialog()" /></template>
          </el-input>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="校验类型" prop="checkType">
              <el-select v-model="form.checkType" placeholder="请选择校验类型" style="width: 100%">
                <el-option v-for="dict in mes_check_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="校验阶段" prop="checkStage">
              <el-select v-model="form.checkStage" placeholder="请选择校验阶段" style="width: 100%">
                <el-option v-for="dict in mes_check_stage" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" active-value="1" inactive-value="0" active-text="启用" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="失败提示">
          <el-input v-model="form.errorMsg" type="textarea" :rows="2" placeholder="校验失败时前端展示的提示信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 工序选择对话框(ERP bd_process) -->
    <el-dialog v-model="processDialog.visible" title="选择工序" width="800px" append-to-body>
      <el-form :model="processDialog.query" :inline="true">
        <el-form-item label="工序编码"><el-input v-model="processDialog.query.processCode" clearable @keyup.enter="queryProcesses" /></el-form-item>
        <el-form-item label="工序名称"><el-input v-model="processDialog.query.processName" clearable @keyup.enter="queryProcesses" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryProcesses">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="processDialog.list" height="380" @row-dblclick="selectProcess">
        <el-table-column label="工序编码" prop="processCode" width="140" />
        <el-table-column label="工序名称" prop="processName" min-width="140" />
        <el-table-column label="类别" prop="processCategory" width="100">
          <template #default="scope">{{ categoryLabel(scope.row.processCategory) }}</template>
        </el-table-column>
        <el-table-column label="默认工位" prop="workstationName" min-width="120" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectProcess(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="processDialog.total > 0"
        :total="processDialog.total"
        v-model:page="processDialog.query.pageNum"
        v-model:limit="processDialog.query.pageSize"
        @pagination="queryProcesses"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ProStepCheckRule">
import { listStepCheckRule, getStepCheckRule, addStepCheckRule, updateStepCheckRule, delStepCheckRule } from '@/api/mes/pro/stepCheckRule';
import type { ProStepCheckRuleVO, ProStepCheckRuleQuery } from '@/api/mes/pro/stepCheckRule';
import { listProcess } from '@/api/erp/bd/process';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_check_type, mes_check_stage } = toRefs<any>(proxy.useDict('mes_check_type', 'mes_check_stage'));

const ruleFormRef = ref<FormInstance>();

const queryParams = ref<ProStepCheckRuleQuery>({
  pageNum: 1,
  pageSize: 10,
  processName: undefined,
  checkType: undefined,
  checkStage: undefined
});

// 工序类别本地映射
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger';
const categoryMap: Record<string, { label: string; tag: TagType }> = {
  PROCESSING: { label: '加工', tag: 'primary' },
  ASSEMBLY: { label: '装配', tag: 'success' },
  INSPECTION: { label: '检验', tag: 'warning' },
  PACKAGING: { label: '包装', tag: 'info' }
};
const categoryLabel = (v?: string) => (v && categoryMap[v] ? categoryMap[v].label : v || '-');

const processDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, processCode: '', processName: '' }
});

const rules: ElFormRules = {
  processId: [{ required: true, message: '请选择工序', trigger: 'change' }],
  checkType: [{ required: true, message: '请选择校验类型', trigger: 'change' }],
  checkStage: [{ required: true, message: '请选择校验阶段', trigger: 'change' }]
};

const {
  loading,
  list: dataList,
  total,
  multiple,
  showSearch,
  open,
  title,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel
} = useCrud<ProStepCheckRuleVO, ProStepCheckRuleQuery>({
  apis: { list: listStepCheckRule, get: getStepCheckRule, add: addStepCheckRule, update: updateStepCheckRule, del: delStepCheckRule },
  idField: 'ruleId',
  entityName: '工序校验规则',
  formRef: ruleFormRef,
  queryParams,
  initForm: () => ({
    ruleId: undefined,
    processId: 0,
    processCode: '',
    processName: '',
    checkType: '',
    checkStage: 'ENTRY',
    enabled: '1',
    errorMsg: '',
    sortOrder: undefined
  }),
  exportUrl: '/mes/pro/step-check-rule/export',
  exportFileName: '工序校验规则'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// ===== 工序选择器 =====
function openProcessDialog() {
  processDialog.visible = true;
  queryProcesses();
}
function queryProcesses() {
  listProcess(processDialog.query as any).then((res: any) => {
    processDialog.list = res.rows || [];
    processDialog.total = res.total || 0;
  });
}
function selectProcess(row: any) {
  form.value.processId = row.processId;
  form.value.processCode = row.processCode;
  form.value.processName = row.processName;
  processDialog.visible = false;
}

onMounted(() => getList());
</script>
