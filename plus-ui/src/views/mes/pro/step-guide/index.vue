<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工序名称" prop="processName">
            <el-input v-model="queryParams.processName" placeholder="请输入工序名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="指引类型" prop="guideType">
            <el-select v-model="queryParams.guideType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="dict in mes_guide_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="启用" prop="enabled">
            <el-select v-model="queryParams.enabled" placeholder="请选择" clearable style="width: 100%">
              <el-option label="启用" value="1" />
              <el-option label="停用" value="0" />
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
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:pro:step-guide:add']">新增指引</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:pro:step-guide:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['mes:pro:step-guide:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工序名称" prop="processName" min-width="130" />
      <el-table-column label="指引标题" prop="guideTitle" min-width="150" />
      <el-table-column label="指引类型" prop="guideType" align="center" width="110">
        <template #default="scope"><dict-tag :options="mes_guide_type" :value="scope.row.guideType" /></template>
      </el-table-column>
      <el-table-column label="关联SOP" prop="sopName" min-width="140">
        <template #default="scope">
          <span v-if="scope.row.sopName">{{ scope.row.sopName }}</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" align="center" width="70" />
      <el-table-column label="启用" prop="enabled" align="center" width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.enabled" :active-value="'1'" :inactive-value="'0'" disabled />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:pro:step-guide:edit']">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:pro:step-guide:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="guideFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工序" prop="processId">
          <el-input v-model="form.processName" placeholder="请选择工序" readonly>
            <template #append><el-button icon="Search" @click="openProcessDialog()" /></template>
          </el-input>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="指引类型" prop="guideType">
              <el-select v-model="form.guideType" placeholder="请选择类型" style="width: 100%" @change="handleGuideTypeChange">
                <el-option v-for="dict in mes_guide_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="指引标题" prop="guideTitle">
          <el-input v-model="form.guideTitle" placeholder="请输入指引标题" />
        </el-form-item>
        <el-form-item label="文本内容" v-if="form.guideType === 'TEXT'" prop="guideContent">
          <el-input v-model="form.guideContent" type="textarea" :rows="4" placeholder="请输入作业指导文本内容" />
        </el-form-item>
        <el-form-item label="关联SOP" v-if="form.guideType === 'SOP_LINK'" prop="sopId">
          <el-input v-model="form.sopName" placeholder="请选择SOP文档" readonly>
            <template #append><el-button icon="Search" @click="openSopDialog()" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" active-value="1" inactive-value="0" active-text="启用" />
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

    <!-- SOP选择对话框(仅已发布) -->
    <el-dialog v-model="sopDialog.visible" title="选择SOP" width="800px" append-to-body>
      <el-form :model="sopDialog.query" :inline="true">
        <el-form-item label="SOP编码"><el-input v-model="sopDialog.query.sopCode" clearable @keyup.enter="querySops" /></el-form-item>
        <el-form-item label="SOP名称"><el-input v-model="sopDialog.query.sopName" clearable @keyup.enter="querySops" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="querySops">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="sopDialog.list" height="380" @row-dblclick="selectSop">
        <el-table-column label="SOP编码" prop="sopCode" width="150" />
        <el-table-column label="SOP名称" prop="sopName" min-width="150" />
        <el-table-column label="类型" prop="sopType" width="100">
          <template #default="scope"><dict-tag :options="mes_sop_type" :value="scope.row.sopType" /></template>
        </el-table-column>
        <el-table-column label="当前版本" prop="currentVersion" width="90" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectSop(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="sopDialog.total > 0"
        :total="sopDialog.total"
        v-model:page="sopDialog.query.pageNum"
        v-model:limit="sopDialog.query.pageSize"
        @pagination="querySops"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ProStepGuide">
import { listStepGuide, getStepGuide, addStepGuide, updateStepGuide, delStepGuide } from '@/api/mes/pro/stepGuide';
import type { ProStepGuideVO, ProStepGuideQuery } from '@/api/mes/pro/stepGuide';
import { listProcess } from '@/api/erp/bd/process';
import { listDocument } from '@/api/mes/sop/document';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_guide_type, mes_sop_type } = toRefs<any>(proxy.useDict('mes_guide_type', 'mes_sop_type'));

const guideFormRef = ref<FormInstance>();

const queryParams = ref<ProStepGuideQuery>({
  pageNum: 1,
  pageSize: 10,
  processName: undefined,
  guideType: undefined,
  enabled: undefined
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
const sopDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, sopCode: '', sopName: '', status: 'PUBLISHED' }
});

const rules: ElFormRules = {
  processId: [{ required: true, message: '请选择工序', trigger: 'change' }],
  guideType: [{ required: true, message: '请选择指引类型', trigger: 'change' }],
  guideTitle: [{ required: true, message: '请输入指引标题', trigger: 'blur' }],
  guideContent: [{ required: true, message: '请输入文本内容', trigger: 'blur' }],
  sopId: [{ required: true, message: '请选择SOP文档', trigger: 'change' }]
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
  cancel
} = useCrud<ProStepGuideVO, ProStepGuideQuery>({
  apis: { list: listStepGuide, get: getStepGuide, add: addStepGuide, update: updateStepGuide, del: delStepGuide },
  idField: 'guideId',
  entityName: '工序指引',
  formRef: guideFormRef,
  queryParams,
  initForm: () => ({
    guideId: undefined,
    processId: 0,
    processCode: '',
    processName: '',
    guideType: 'TEXT',
    guideTitle: '',
    guideContent: '',
    sopId: undefined,
    sopName: '',
    sortOrder: undefined,
    enabled: '1'
  }),
  exportUrl: '/mes/pro/step-guide/export',
  exportFileName: '工序流程指引'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 指引类型切换:互斥清理文本内容/SOP关联 */
function handleGuideTypeChange(val: string) {
  if (val === 'TEXT') {
    form.value.sopId = undefined;
    form.value.sopName = '';
  } else {
    form.value.guideContent = '';
  }
}

/** 提交表单:本地保留按指引类型的条件必填校验(useCrud 标准提交不含指引特有校验) */
function submitForm() {
  guideFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    if (form.value.guideType === 'SOP_LINK' && !form.value.sopId) {
      proxy.$modal.msgWarning('SOP_LINK类型须选择关联SOP');
      return;
    }
    if (form.value.guideType === 'TEXT' && !form.value.guideContent) {
      proxy.$modal.msgWarning('TEXT类型须填写文本内容');
      return;
    }
    if (form.value.guideId) {
      await updateStepGuide(form.value as any);
      proxy.$modal.msgSuccess('修改成功');
    } else {
      await addStepGuide(form.value as any);
      proxy.$modal.msgSuccess('新增成功');
    }
    open.value = false;
    getList();
  });
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

// ===== SOP选择器 =====
function openSopDialog() {
  sopDialog.visible = true;
  querySops();
}
function querySops() {
  listDocument(sopDialog.query as any).then((res: any) => {
    sopDialog.list = res.rows || [];
    sopDialog.total = res.total || 0;
  });
}
function selectSop(row: any) {
  form.value.sopId = row.sopId;
  form.value.sopName = row.sopName;
  sopDialog.visible = false;
}

onMounted(() => getList());
</script>
