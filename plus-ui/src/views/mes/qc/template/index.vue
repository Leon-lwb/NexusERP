<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="5">
          <el-form-item label="模板编码" prop="templateCode">
            <el-input v-model="queryParams.templateCode" placeholder="请输入模板编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="模板名称" prop="templateName">
            <el-input v-model="queryParams.templateName" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="类型" prop="templateType">
            <el-select v-model="queryParams.templateType" placeholder="全部类型" clearable style="width: 100%">
              <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 100%">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="启用" value="ACTIVE" />
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
        <el-button type="primary" icon="Plus" @click="handleAdd()" v-hasPermi="['mes:qc:template:add']">新增模板</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="模板名称" prop="templateName" min-width="160" />
      <el-table-column label="类型" prop="templateType" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.templateType === 'IQC' ? 'primary' : scope.row.templateType === 'IPQC' ? 'success' : 'warning'">
            {{ scope.row.templateType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="适用物料" min-width="160">
        <template #default="scope">
          {{ scope.row.materialId ? (scope.row.materialCode || '') + ' ' + (scope.row.materialName || '') : '通用' }}
        </template>
      </el-table-column>
      <el-table-column label="版本" prop="versionNo" align="center" width="60" />
      <el-table-column label="状态" prop="status" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '草稿' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="120" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="300" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:qc:template:edit']">编辑</el-button>
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleActivate(scope.row)"
            v-if="scope.row.status !== 'ACTIVE'"
            v-hasPermi="['mes:qc:template:edit']"
            >启用</el-button
          >
          <el-button
            link
            type="warning"
            icon="Switch"
            @click="handleDeactivate(scope.row)"
            v-if="scope.row.status === 'ACTIVE'"
            v-hasPermi="['mes:qc:template:edit']"
            >停用</el-button
          >
          <el-button link type="primary" icon="CopyDocument" @click="handleCopy(scope.row)" v-hasPermi="['mes:qc:template:copy']">复制版本</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:qc:template:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 模板编辑对话框(主子表) -->
    <el-dialog v-model="open" :title="title" width="1060px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模板编码" prop="templateCode">
              <el-input v-model="form.templateCode" placeholder="如 IQC-TPL-1001" :disabled="!!form.templateId" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="form.templateName" placeholder="请输入模板名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模板类型" prop="templateType">
              <el-select v-model="form.templateType" placeholder="选择类型" style="width: 100%" :disabled="!!form.templateId">
                <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="适用物料" prop="materialId">
              <el-select
                v-model="form.materialId"
                placeholder="不选=通用模板"
                filterable
                clearable
                style="width: 100%"
                @change="handleMaterialChange"
              >
                <el-option
                  v-for="m in materialOptions"
                  :key="m.materialId"
                  :label="(m.materialCode || '') + ' - ' + (m.materialName || '')"
                  :value="m.materialId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-tag :type="form.status === 'ACTIVE' ? 'success' : 'info'">{{ form.status === 'ACTIVE' ? '启用' : '草稿' }}</el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">检验项目</el-divider>
        <el-button type="primary" icon="Plus" size="small" @click="addItem" style="margin-bottom: 8px">添加项目</el-button>
        <el-table :data="form.items || []" border size="small" show-overflow-tooltip>
          <el-table-column label="序号" width="60" align="center">
            <template #default="scope">{{ scope.$index + 1 }}</template>
          </el-table-column>
          <el-table-column label="项目编码" width="110">
            <template #default="scope">
              <el-input v-model="scope.row.itemCode" placeholder="编码" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="项目名称" width="130">
            <template #default="scope">
              <el-input v-model="scope.row.itemName" placeholder="名称" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="数据类型" width="110">
            <template #default="scope">
              <el-select v-model="scope.row.dataType" size="small" @change="onDataTypeChange(scope.$index)">
                <el-option label="定量" value="QUANTITATIVE" />
                <el-option label="定性" value="QUALITATIVE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="标准类型" width="130">
            <template #default="scope">
              <el-select v-if="scope.row.dataType !== 'QUALITATIVE'" v-model="scope.row.standardType" size="small" placeholder="标准">
                <el-option label="下限+上限" value="LSL_USL" />
                <el-option label="仅下限" value="LSL_ONLY" />
                <el-option label="仅上限" value="USL_ONLY" />
                <el-option label="名义值±公差" value="NOMINAL" />
              </el-select>
              <span v-else>定性(OK/NG)</span>
            </template>
          </el-table-column>
          <el-table-column label="标准值" min-width="220">
            <template #default="scope">
              <template v-if="scope.row.dataType !== 'QUALITATIVE'">
                <div v-if="scope.row.standardType === 'NOMINAL'" class="std-inputs">
                  <el-input-number v-model="scope.row.nominalValue" placeholder="名义值" size="small" :controls="false" />
                  <span class="std-sep">±</span>
                  <el-input-number v-model="scope.row.tolUpper" placeholder="上公差" size="small" :controls="false" />
                  <span class="std-sep">/</span>
                  <el-input-number v-model="scope.row.tolLower" placeholder="下公差" size="small" :controls="false" />
                </div>
                <div v-else-if="scope.row.standardType === 'LSL_USL'" class="std-inputs">
                  <el-input-number v-model="scope.row.lslValue" placeholder="LSL" size="small" :controls="false" />
                  <span class="std-sep">~</span>
                  <el-input-number v-model="scope.row.uslValue" placeholder="USL" size="small" :controls="false" />
                </div>
                <div v-else-if="scope.row.standardType === 'LSL_ONLY'" class="std-inputs">
                  <span class="std-sep">≥</span>
                  <el-input-number v-model="scope.row.lslValue" placeholder="LSL" size="small" :controls="false" />
                </div>
                <div v-else-if="scope.row.standardType === 'USL_ONLY'" class="std-inputs">
                  <span class="std-sep">≤</span>
                  <el-input-number v-model="scope.row.uslValue" placeholder="USL" size="small" :controls="false" />
                </div>
                <span v-else class="std-hint">请选择标准类型</span>
              </template>
              <el-input v-else v-model="scope.row.remark" placeholder="合格描述填备注" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template #default="scope">
              <el-input v-model="scope.row.unit" placeholder="mm" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="关键度" width="110">
            <template #default="scope">
              <el-select v-model="scope.row.criticality" size="small">
                <el-option label="关键" value="CRITICAL" />
                <el-option label="重要" value="MAJOR" />
                <el-option label="次要" value="MINOR" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="60" align="center">
            <template #default="scope">
              <el-switch v-model="scope.row.enabled" active-value="1" inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="removeItem(scope.$index)" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesQcTemplate">
import {
  listTemplate,
  getTemplate,
  addTemplate,
  updateTemplate,
  delTemplate,
  activateTemplate,
  deactivateTemplate,
  copyTemplate
} from '@/api/mes/qc/template';
import type { QcInspectTemplateVO, QcInspectTemplateQuery, QcInspectTemplateForm } from '@/api/mes/qc/template';
import { listMaterial } from '@/api/erp/bd/material/index';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const formRef = ref<FormInstance>();

const typeOptions = [
  { label: 'IQC 来料检验', value: 'IQC' },
  { label: 'IPQC 过程检验', value: 'IPQC' },
  { label: 'OQC 出货检验', value: 'OQC' }
];

const queryParams = ref<QcInspectTemplateQuery>({ pageNum: 1, pageSize: 10, templateType: undefined, status: undefined });

const rules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  templateType: [{ required: true, message: '请选择模板类型', trigger: 'change' }]
};

const materialOptions = ref<any[]>([]);

const {
  loading,
  list: dataList,
  total,
  showSearch,
  open,
  title,
  form,
  getList,
  handleQuery,
  handleDelete,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud<QcInspectTemplateForm, QcInspectTemplateQuery>({
  apis: { list: listTemplate, get: getTemplate, add: addTemplate, update: updateTemplate, del: delTemplate },
  idField: 'templateId',
  entityName: '检验模板',
  formRef,
  queryParams,
  initForm: () => ({ templateCode: '', templateName: '', templateType: 'IQC', status: 'DRAFT', items: [] })
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 加载物料下拉选项 */
async function loadOptions() {
  if (materialOptions.value.length > 0) return;
  const res = await listMaterial({ pageNum: 1, pageSize: 1000 } as any);
  materialOptions.value = res.rows || [];
}

/** 新增按钮操作 */
async function handleAdd() {
  await loadOptions();
  rawHandleAdd(() => {
    form.value = { templateCode: '', templateName: '', templateType: 'IQC', status: 'DRAFT', versionNo: 1, items: [] };
  });
}

/** 编辑按钮操作 */
async function handleUpdate(row: QcInspectTemplateVO) {
  await loadOptions();
  return rawHandleUpdate(row);
}

/** 选择物料后冗余编码名称 */
function handleMaterialChange(materialId: number) {
  const material = materialOptions.value.find((m) => m.materialId === materialId);
  form.value.materialCode = material?.materialCode;
  form.value.materialName = material?.materialName;
}

/** 添加检验项目行 */
function addItem() {
  if (!form.value.items) form.value.items = [];
  form.value.items.push({
    itemCode: '',
    itemName: '',
    dataType: 'QUANTITATIVE',
    standardType: 'LSL_USL',
    criticality: 'MAJOR',
    enabled: '1',
    seqNum: form.value.items.length + 1
  });
}

/** 删除检验项目行 */
function removeItem(index: number) {
  form.value.items?.splice(index, 1);
}

/** 切换数据类型时清空不相关标准字段 */
function onDataTypeChange(index: number) {
  const item = form.value.items?.[index];
  if (!item) return;
  if (item.dataType === 'QUALITATIVE') {
    item.standardType = undefined;
    item.nominalValue = undefined;
    item.lslValue = undefined;
    item.uslValue = undefined;
    item.tolUpper = undefined;
    item.tolLower = undefined;
  } else if (!item.standardType) {
    item.standardType = 'LSL_USL';
  }
}

/** 提交表单: 行级校验后整体提交 */
function submitForm() {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    const items = form.value.items || [];
    if (items.length === 0) {
      proxy.$modal.msgError('检验模板至少需要一个检验项目');
      return;
    }
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (!item.itemCode) {
        proxy.$modal.msgError('第' + (i + 1) + '行: 请输入项目编码');
        return;
      }
      if (!item.itemName) {
        proxy.$modal.msgError('第' + (i + 1) + '行: 请输入项目名称');
        return;
      }
      if (item.dataType !== 'QUALITATIVE') {
        if (!item.standardType) {
          proxy.$modal.msgError('第' + (i + 1) + '行: 请选择标准类型');
          return;
        }
        const stdOk =
          (item.standardType === 'LSL_USL' && item.lslValue != null && item.uslValue != null) ||
          (item.standardType === 'LSL_ONLY' && item.lslValue != null) ||
          (item.standardType === 'USL_ONLY' && item.uslValue != null) ||
          (item.standardType === 'NOMINAL' && item.nominalValue != null && item.tolUpper != null && item.tolLower != null);
        if (!stdOk) {
          proxy.$modal.msgError('第' + (i + 1) + '行: 标准值填写不完整');
          return;
        }
      }
    }
    const submitData = { ...form.value } as QcInspectTemplateForm;
    submitData.items = (submitData.items || []).map((it, idx) => ({ ...it, seqNum: idx + 1 }));
    if (form.value.templateId) {
      await updateTemplate(submitData);
    } else {
      await addTemplate(submitData);
    }
    proxy.$modal.msgSuccess('保存成功');
    open.value = false;
    getList();
  });
}

/** 启用模板 */
async function handleActivate(row: QcInspectTemplateVO) {
  await proxy.$modal.confirm('确认启用此模板？启用后检验单创建时将按此模板生成检验明细。');
  await activateTemplate(row.templateId);
  proxy.$modal.msgSuccess('启用成功');
  getList();
}

/** 停用模板 */
async function handleDeactivate(row: QcInspectTemplateVO) {
  await proxy.$modal.confirm('确认停用此模板？停用后检验单创建时不再匹配。');
  await deactivateTemplate(row.templateId);
  proxy.$modal.msgSuccess('停用成功');
  getList();
}

/** 版本复制 */
async function handleCopy(row: QcInspectTemplateVO) {
  await proxy.$modal.confirm('确认复制为新版本草稿？将复制模板及全部检验项目。');
  await copyTemplate(row.templateId);
  proxy.$modal.msgSuccess('已生成新版本草稿');
  getList();
}

onMounted(() => {
  getList();
});
</script>

<style scoped>
.std-inputs {
  display: flex;
  align-items: center;
  gap: 4px;
}
.std-sep {
  color: var(--el-text-color-secondary);
}
.std-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
