<template>
  <div class="p-2 autocode-page">
    <el-row :gutter="16" class="autocode-grid">
      <!-- 编码规则 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="autocode-card">
          <template #header>
            <div class="autocode-card__header">
              <div class="autocode-card__title">编码规则</div>
              <right-toolbar v-model:show-search="showTypeSearch" @query-table="getTypeList" />
            </div>
          </template>

          <div v-show="showTypeSearch" class="autocode-form-scroll">
            <el-form ref="typeQueryFormRef" :model="typeQueryParams" :inline="true">
              <el-form-item label="规则编码" prop="ruleCode">
                <el-input v-model="typeQueryParams.ruleCode" placeholder="请输入规则编码" clearable @keyup.enter="handleTypeQuery" />
              </el-form-item>
              <el-form-item label="规则名称" prop="ruleName">
                <el-input v-model="typeQueryParams.ruleName" placeholder="请输入规则名称" clearable @keyup.enter="handleTypeQuery" />
              </el-form-item>
              <el-form-item label="启用状态" prop="enableFlag">
                <el-select v-model="typeQueryParams.enableFlag" placeholder="启用状态" clearable>
                  <el-option v-for="dict in sys_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="Search" @click="handleTypeQuery">搜索</el-button>
                <el-button icon="Refresh" @click="handleTypeResetQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="autocode-actions">
            <el-button v-hasPermi="['system:autocode:rule:add']" type="primary" plain icon="Plus" @click="handleTypeAdd">新增</el-button>
            <el-button v-hasPermi="['system:autocode:rule:edit']" type="success" plain icon="Edit" :disabled="typeSingle" @click="handleTypeUpdate()"
              >修改</el-button
            >
            <el-button
              v-hasPermi="['system:autocode:rule:remove']"
              type="danger"
              plain
              icon="Delete"
              :disabled="typeMultiple"
              @click="handleTypeDelete()"
              >删除</el-button
            >
          </div>

          <div class="autocode-table-wrap">
            <el-table
              ref="typeTableRef"
              v-loading="typeLoading"
              border
              :data="typeList"
              highlight-current-row
              @row-click="handleTypeRowClick"
              @selection-change="handleTypeSelectionChange"
            >
              <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="规则编码" align="center" prop="ruleCode" width="210" :show-overflow-tooltip="true">
                <template #default="scope">
                  <span class="link-type" @click.stop="handleTypeRowClick(scope.row)">{{ scope.row.ruleCode }}</span>
                </template>
              </el-table-column>
              <el-table-column label="规则名称" align="center" prop="ruleName" width="140" :show-overflow-tooltip="true" />
              <el-table-column label="描述" align="center" prop="ruleDesc" :show-overflow-tooltip="true" />
              <el-table-column label="是否可用" align="center" prop="enableFlag" width="80">
                <template #default="scope">
                  <dict-tag :options="sys_yes_no" :value="scope.row.enableFlag" />
                </template>
              </el-table-column>
              <el-table-column label="操作" fixed="right" align="center" width="120" class-name="small-padding fixed-width">
                <template #default="scope">
                  <el-tooltip content="修改" placement="top">
                    <el-button
                      v-hasPermi="['system:autocode:rule:edit']"
                      link
                      type="primary"
                      icon="Edit"
                      @click="handleTypeUpdate(scope.row)"
                    ></el-button>
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button
                      v-hasPermi="['system:autocode:rule:remove']"
                      link
                      type="primary"
                      icon="Delete"
                      @click="handleTypeDelete(scope.row)"
                    ></el-button>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <pagination
            v-show="typeTotal > 0"
            v-model:page="typeQueryParams.pageNum"
            v-model:limit="typeQueryParams.pageSize"
            :total="typeTotal"
            @pagination="getTypeList"
          />
        </el-card>
      </el-col>

      <!-- 规则组成 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="autocode-card">
          <template #header>
            <div class="autocode-card__header">
              <div class="autocode-card__title">
                规则组成
                <span class="autocode-card__subtitle">{{ currentRuleLabel }}</span>
              </div>
              <right-toolbar v-model:show-search="showDataSearch" @query-table="getDataList" />
            </div>
          </template>

          <div v-show="showDataSearch" class="autocode-form-scroll">
            <el-form ref="dataQueryFormRef" :model="dataQueryParams" :inline="true">
              <el-form-item label="组成编码" prop="partCode">
                <el-input
                  v-model="dataQueryParams.partCode"
                  placeholder="请输入组成编码"
                  clearable
                  :disabled="!hasCurrentRule"
                  @keyup.enter="handleDataQuery"
                />
              </el-form-item>
              <el-form-item label="组成名称" prop="partName">
                <el-input
                  v-model="dataQueryParams.partName"
                  placeholder="请输入组成名称"
                  clearable
                  :disabled="!hasCurrentRule"
                  @keyup.enter="handleDataQuery"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="Search" :disabled="!hasCurrentRule" @click="handleDataQuery">搜索</el-button>
                <el-button icon="Refresh" :disabled="!hasCurrentRule" @click="handleDataResetQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="autocode-actions">
            <el-button v-hasPermi="['system:autocode:part:add']" type="primary" plain icon="Plus" :disabled="!hasCurrentRule" @click="handleDataAdd"
              >新增</el-button
            >
            <el-button
              v-hasPermi="['system:autocode:part:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="dataSingle || !hasCurrentRule"
              @click="handleDataUpdate()"
              >修改</el-button
            >
            <el-button
              v-hasPermi="['system:autocode:part:remove']"
              type="danger"
              plain
              icon="Delete"
              :disabled="dataMultiple || !hasCurrentRule"
              @click="handleDataDelete()"
              >删除</el-button
            >
          </div>

          <div class="autocode-table-wrap">
            <el-table v-loading="dataLoading" border :data="dataList" @selection-change="handleDataSelectionChange">
              <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="组成编码" align="center" prop="partCode" />
              <el-table-column label="组成名称" align="center" prop="partName" />
              <el-table-column label="分段序号" align="center" prop="partIndex" width="80" />
              <el-table-column label="分段类型" align="center" prop="partType" width="100">
                <template #default="scope">
                  <dict-tag :options="sys_autocode_parttype" :value="scope.row.partType" />
                </template>
              </el-table-column>
              <el-table-column label="分段长度" align="center" prop="partLength" width="80" />
              <el-table-column label="操作" fixed="right" align="center" width="120" class-name="small-padding fixed-width">
                <template #default="scope">
                  <el-tooltip content="修改" placement="top">
                    <el-button
                      v-hasPermi="['system:autocode:part:edit']"
                      link
                      type="primary"
                      icon="Edit"
                      @click="handleDataUpdate(scope.row)"
                    ></el-button>
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button
                      v-hasPermi="['system:autocode:part:remove']"
                      link
                      type="primary"
                      icon="Delete"
                      @click="handleDataDelete(scope.row)"
                    ></el-button>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <pagination
            v-show="dataTotal > 0"
            v-model:page="dataQueryParams.pageNum"
            v-model:limit="dataQueryParams.pageSize"
            :total="dataTotal"
            @pagination="getDataList"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 编码规则对话框 -->
    <el-dialog v-model="typeDialog.visible" :title="typeDialog.title" width="500px" append-to-body>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="80px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="typeForm.ruleCode" placeholder="请输入规则编码" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="typeForm.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="描述" prop="ruleDesc">
          <el-input v-model="typeForm.ruleDesc" placeholder="请输入描述信息" />
        </el-form-item>
        <el-form-item label="最大长度" prop="maxLength">
          <el-input-number v-model="typeForm.maxLength" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="是否补齐" prop="isPadded">
          <el-radio-group v-model="typeForm.isPadded">
            <el-radio label="Y">是</el-radio>
            <el-radio label="N">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="补齐字符" prop="paddedChar" v-if="typeForm.isPadded == 'Y'">
          <el-input v-model="typeForm.paddedChar" placeholder="请输入补齐字符" />
        </el-form-item>
        <el-form-item label="补齐方式" prop="paddedMethod" v-if="typeForm.isPadded == 'Y'">
          <el-radio-group v-model="typeForm.paddedMethod">
            <el-radio label="L">左补齐</el-radio>
            <el-radio label="R">右补齐</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否启用" prop="enableFlag">
          <el-radio-group v-model="typeForm.enableFlag">
            <el-radio v-for="dict in sys_yes_no" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="typeForm.remark" type="textarea" placeholder="请输入内容"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitTypeForm">确 定</el-button>
          <el-button @click="cancelType">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 规则组成对话框 -->
    <el-dialog v-model="dataDialog.visible" :title="dataDialog.title" width="960px" append-to-body>
      <el-form ref="dataFormRef" :model="dataForm" :inline="true" :rules="dataRules" label-width="110px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="分段编码" prop="partCode">
              <el-input v-model="dataForm.partCode" placeholder="请输入分段编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分段名称" prop="partName">
              <el-input v-model="dataForm.partName" placeholder="请输入分段名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="分段序号" prop="partIndex">
              <el-input-number v-model="dataForm.partIndex" placeholder="请输入分段序号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分段长度" prop="partLength">
              <el-input-number v-model="dataForm.partLength" placeholder="请输入分段长度" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="分段类型" prop="partType">
              <el-select v-model="dataForm.partType">
                <el-option v-for="item in sys_autocode_parttype" :key="item.value" :label="item.label" :value="item.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日期时间格式" prop="dateFormat" v-if="dataForm.partType == 'NOWDATE'">
              <el-input v-model="dataForm.dateFormat" placeholder="请输入日期时间格式" />
            </el-form-item>
            <el-form-item label="输入字符" prop="inputCharacter" v-if="dataForm.partType == 'INPUTCHAR'">
              <el-input v-model="dataForm.inputCharacter" placeholder="请填写输入字符" />
            </el-form-item>
            <el-form-item label="固定字符" prop="fixCharacter" v-if="dataForm.partType == 'FIXCHAR'">
              <el-input v-model="dataForm.fixCharacter" placeholder="请填写固定字符" />
            </el-form-item>
            <el-form-item label="起始流水号" prop="seriaStartNo" v-if="dataForm.partType == 'SERIALNO'">
              <el-input-number v-model="dataForm.seriaStartNo" placeholder="请填写起始流水号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="流水号步长" prop="seriaStep" v-if="dataForm.partType == 'SERIALNO'">
              <el-input-number v-model="dataForm.seriaStep" placeholder="请填写流水号步长" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否循环" prop="cycleFlag" v-if="dataForm.partType == 'SERIALNO'">
              <el-radio-group v-model="dataForm.cycleFlag">
                <el-radio label="Y">是</el-radio>
                <el-radio label="N">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="循环方式" prop="cycleMethod" v-if="dataForm.partType == 'SERIALNO' && dataForm.cycleFlag == 'Y'">
              <el-select v-model="dataForm.cycleMethod">
                <el-option v-for="item in sys_autocode_cyclemethod" :key="item.value" :label="item.label" :value="item.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="dataForm.remark" type="textarea" placeholder="请输入内容"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitDataForm">确 定</el-button>
          <el-button @click="cancelData">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AutoCode" lang="ts">
import { ref, reactive, onMounted, toRefs, computed, nextTick, getCurrentInstance } from 'vue';
import { listRule, getRule, delRule, addRule, updateRule } from '@/api/system/autocode/rule';
import { listPart, getPart, delPart, addPart, updatePart } from '@/api/system/autocode/part';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_yes_no, sys_autocode_parttype, sys_autocode_cyclemethod } = toRefs<any>(
  proxy.useDict('sys_yes_no', 'sys_autocode_parttype', 'sys_autocode_cyclemethod')
);

// ========== 编码规则相关 ==========
const typeList = ref<any[]>([]);
const typeLoading = ref(true);
const showTypeSearch = ref(true);
const typeIds = ref<Array<number | string>>([]);
const typeSingle = ref(true);
const typeMultiple = ref(true);
const typeTotal = ref(0);
const typeTableRef = ref<any>();
const typeQueryFormRef = ref<any>();
const typeFormRef = ref<any>();

const typeDialog = reactive({
  visible: false,
  title: ''
});

const typeInitFormData = {
  ruleId: undefined,
  ruleCode: '',
  ruleName: '',
  ruleDesc: '',
  maxLength: undefined,
  isPadded: 'N',
  paddedChar: '',
  paddedMethod: 'L',
  enableFlag: 'Y',
  remark: ''
};

const typeState = reactive({
  form: { ...typeInitFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    ruleCode: undefined,
    ruleName: undefined,
    enableFlag: undefined
  },
  rules: {
    ruleCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
    ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
    remark: [{ max: 250, message: '长度必须小于250个字符', trigger: 'blur' }]
  }
});

const { queryParams: typeQueryParams, form: typeForm, rules: typeRules } = toRefs(typeState);

// ========== 规则组成相关 ==========
const currentRule = ref<any>(null);
const hasCurrentRule = computed(() => !!currentRule.value);
const currentRuleLabel = computed(() => {
  if (!currentRule.value) return '请先选择编码规则';
  return `${currentRule.value.ruleName} / ${currentRule.value.ruleCode}`;
});

const dataList = ref<any[]>([]);
const dataLoading = ref(false);
const showDataSearch = ref(true);
const dataIds = ref<Array<number | string>>([]);
const dataSingle = ref(true);
const dataMultiple = ref(true);
const dataTotal = ref(0);
const dataQueryFormRef = ref<any>();
const dataFormRef = ref<any>();

const dataDialog = reactive({
  visible: false,
  title: ''
});

const dataInitFormData = {
  partId: undefined,
  ruleId: undefined,
  partCode: '',
  partName: '',
  partIndex: 0,
  partLength: 0,
  partType: '',
  dateFormat: '',
  inputCharacter: '',
  fixCharacter: '',
  seriaStartNo: undefined,
  seriaStep: undefined,
  cycleFlag: 'N',
  cycleMethod: '',
  remark: ''
};

const dataState = reactive({
  form: { ...dataInitFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    ruleId: undefined,
    partCode: undefined,
    partName: undefined
  },
  rules: {
    partCode: [{ required: true, message: '组成编码不能为空', trigger: 'blur' }],
    partName: [{ required: true, message: '组成名称不能为空', trigger: 'blur' }],
    partIndex: [{ required: true, message: '组成序号不能为空', trigger: 'blur' }],
    remark: [{ max: 250, message: '长度必须小于250个字符', trigger: 'blur' }]
  }
});

const { queryParams: dataQueryParams, form: dataForm, rules: dataRules } = toRefs(dataState);

// ========== 编码规则方法 ==========
const getTypeList = () => {
  typeLoading.value = true;
  listRule(proxy.addDateRange(typeQueryParams.value, [])).then((response: any) => {
    typeList.value = response.rows;
    typeTotal.value = response.total;
    typeLoading.value = false;
    ensureCurrentType();
  });
};

const ensureCurrentType = () => {
  if (!typeList.value.length) {
    currentRule.value = null;
    dataQueryParams.value.ruleId = undefined;
    dataList.value = [];
    dataTotal.value = 0;
    return;
  }
  const current = currentRule.value && typeList.value.find((item: any) => item.ruleId === currentRule.value?.ruleId);
  const nextRow = current || typeList.value[0];
  setCurrentType(nextRow);
};

const setCurrentType = (row: any) => {
  currentRule.value = row;
  dataQueryParams.value.ruleId = row.ruleId;
  dataQueryParams.value.pageNum = 1;
  dataQueryParams.value.partCode = undefined;
  dataQueryParams.value.partName = undefined;
  getDataList();
  nextTick(() => typeTableRef.value?.setCurrentRow(row));
};

const handleTypeRowClick = (row: any) => {
  setCurrentType(row);
};

const handleTypeQuery = () => {
  typeQueryParams.value.pageNum = 1;
  getTypeList();
};

const handleTypeResetQuery = () => {
  typeQueryFormRef.value?.resetFields();
  handleTypeQuery();
};

const handleTypeAdd = () => {
  resetTypeForm();
  typeDialog.visible = true;
  typeDialog.title = '添加编码规则';
};

const handleTypeSelectionChange = (selection: any[]) => {
  typeIds.value = selection.map((item) => item.ruleId);
  typeSingle.value = selection.length != 1;
  typeMultiple.value = !selection.length;
};

const handleTypeUpdate = async (row?: any) => {
  resetTypeForm();
  const ruleId = row?.ruleId || typeIds.value[0];
  const res = await getRule(ruleId);
  Object.assign(typeForm.value, res.data);
  typeDialog.visible = true;
  typeDialog.title = '修改编码规则';
};

const submitTypeForm = () => {
  typeFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      typeForm.value.ruleId ? await updateRule(typeForm.value) : await addRule(typeForm.value);
      proxy?.$modal.msgSuccess('操作成功');
      typeDialog.visible = false;
      getTypeList();
    }
  });
};

const handleTypeDelete = async (row?: any) => {
  const ruleIds = row?.ruleId || typeIds.value;
  await proxy?.$modal.confirm('是否确认删除编码规则为"' + ruleIds + '"的数据项？');
  await delRule(ruleIds);
  getTypeList();
  proxy?.$modal.msgSuccess('删除成功');
};

const cancelType = () => {
  resetTypeForm();
  typeDialog.visible = false;
};

const resetTypeForm = () => {
  typeForm.value = { ...typeInitFormData };
  typeFormRef.value?.resetFields();
};

// ========== 规则组成方法 ==========
const getDataList = () => {
  if (!currentRule.value) {
    dataList.value = [];
    dataTotal.value = 0;
    dataLoading.value = false;
    return;
  }
  dataLoading.value = true;
  listPart(dataQueryParams.value).then((response: any) => {
    dataList.value = response.rows;
    dataTotal.value = response.total;
    dataLoading.value = false;
  });
};

const handleDataQuery = () => {
  if (!currentRule.value) return;
  dataQueryParams.value.pageNum = 1;
  getDataList();
};

const handleDataResetQuery = () => {
  dataQueryFormRef.value?.resetFields();
  dataQueryParams.value.partCode = undefined;
  dataQueryParams.value.partName = undefined;
  handleDataQuery();
};

const handleDataAdd = () => {
  if (!currentRule.value) {
    proxy?.$modal.msgWarning('请先选择编码规则');
    return;
  }
  resetDataForm();
  dataForm.value.ruleId = currentRule.value.ruleId;
  dataDialog.visible = true;
  dataDialog.title = '添加规则组成';
};

const handleDataSelectionChange = (selection: any[]) => {
  dataIds.value = selection.map((item) => item.partId);
  dataSingle.value = selection.length != 1;
  dataMultiple.value = !selection.length;
};

const handleDataUpdate = async (row?: any) => {
  if (!currentRule.value) {
    proxy?.$modal.msgWarning('请先选择编码规则');
    return;
  }
  resetDataForm();
  const partId = row?.partId || dataIds.value[0];
  const res = await getPart(partId);
  Object.assign(dataForm.value, res.data);
  dataDialog.visible = true;
  dataDialog.title = '修改规则组成';
};

const submitDataForm = () => {
  dataFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      dataForm.value.partId ? await updatePart(dataForm.value) : await addPart(dataForm.value);
      proxy?.$modal.msgSuccess('操作成功');
      dataDialog.visible = false;
      await getDataList();
    }
  });
};

const handleDataDelete = async (row?: any) => {
  if (!currentRule.value) {
    proxy?.$modal.msgWarning('请先选择编码规则');
    return;
  }
  const partIds = row?.partId || dataIds.value;
  await proxy?.$modal.confirm('是否确认删除规则组成ID为"' + partIds + '"的数据项？');
  await delPart(partIds);
  await getDataList();
  proxy?.$modal.msgSuccess('删除成功');
};

const cancelData = () => {
  dataDialog.visible = false;
  resetDataForm();
};

const resetDataForm = () => {
  dataForm.value = { ...dataInitFormData };
  dataFormRef.value?.resetFields();
};

onMounted(() => {
  getTypeList();
});
</script>

<style lang="scss" scoped>
.autocode-grid {
  row-gap: 16px;
}

.autocode-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.autocode-card__title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-weight: 600;
}

.autocode-card__subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.autocode-form-scroll {
  max-height: 200px;
  overflow: auto;
  margin-bottom: 12px;
  padding-right: 6px;
  padding-bottom: 4px;
}

.autocode-form-scroll :deep(.el-form) {
  display: flex;
  flex-wrap: wrap;
  column-gap: 12px;
  row-gap: 10px;
}

.autocode-form-scroll :deep(.el-form-item) {
  margin: 0;
}

.autocode-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 8px 0 12px;
}

.autocode-actions :deep(.el-button) {
  height: 32px;
  padding: 0 14px;
}

.autocode-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.autocode-table-wrap {
  overflow-x: auto;
  flex: 1;
}

.autocode-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
}

.autocode-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}
</style>
