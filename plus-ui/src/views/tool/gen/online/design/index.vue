<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div class="search" v-show="showSearch">
        <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
          <el-form-item label="数据源" prop="dataName">
            <el-select v-model="queryParams.dataName" filterable clearable placeholder="请选择/输入数据源名称" style="width: 200px">
              <el-option key="" label="全部" value="" />
              <el-option v-for="item in dataNameList" :key="item" :label="item" :value="item"> </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="表名称" prop="tableName">
            <el-input v-model="queryParams.tableName" placeholder="请输入表名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="表描述" prop="tableComment">
            <el-input v-model="queryParams.tableComment" placeholder="请输入表描述" clearable style="width: 200px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="创建时间" style="width: 308px">
            <el-date-picker
              v-model="dateRange"
              value-format="YYYY-MM-DD"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
            ></el-date-picker>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </transition>

    <el-card shadow="never">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Download" @click="handleGenTable()" v-hasPermi="['tool:gen:code']">生成</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" plain icon="Upload" @click="openImportTable" v-hasPermi="['tool:gen:import']">导入</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleEditTable()" v-hasPermi="['tool:gen:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['tool:gen:remove']">
              删除
            </el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="tableList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" align="center" width="55"></el-table-column>
        <el-table-column label="序号" type="index" width="50" align="center">
          <template #default="scope">
            <span>{{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据源" align="center" prop="dataName" :show-overflow-tooltip="true" />
        <el-table-column label="表名称" align="center" prop="tableName" :show-overflow-tooltip="true" />
        <el-table-column label="表描述" align="center" prop="tableComment" :show-overflow-tooltip="true" />
        <el-table-column label="实体" align="center" prop="className" :show-overflow-tooltip="true" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
        <el-table-column label="更新时间" align="center" prop="updateTime" width="160" />
        <el-table-column label="操作" align="center" width="330" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="界面预览" placement="top">
              <el-button link type="primary" icon="View" @click="handlePreview(scope.row)" v-hasPermi="['tool:gen:preview']"></el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top">
              <el-button link type="primary" icon="Edit" @click="handleEditTable(scope.row)" v-hasPermi="['tool:gen:edit']"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['tool:gen:remove']"></el-button>
            </el-tooltip>
            <el-tooltip content="测试" placement="top">
              <el-button link type="primary" icon="Open" @click="handleTest(scope.row)" v-hasPermi="['tool:gen:test']"></el-button>
            </el-tooltip>
            <el-tooltip content="同步" placement="top">
              <el-button link type="primary" icon="Refresh" @click="handleSynchDb(scope.row)" v-hasPermi="['tool:gen:edit']"></el-button>
            </el-tooltip>
            <el-tooltip content="代码预览" placement="top">
              <el-button link type="primary" icon="View" @click="handleCodePreview(scope.row)" v-hasPermi="['tool:gen:preview']"></el-button>
            </el-tooltip>
            <el-tooltip content="生成代码" placement="top">
              <el-button link type="primary" icon="Download" @click="handleGenTable(scope.row)" v-hasPermi="['tool:gen:code']"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 预览界面 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="80%" top="5vh">
      <!-- 单表预览 -->
      <div v-if="previewData.type === 'single'">
        <el-form label-width="120px">
          <el-row :gutter="20">
            <el-col v-for="field in previewData.mainFields" :key="field.columnName" :span="field.formSpan || 24" v-if="isMainVisible(field)">
              <el-form-item :label="field.columnComment" :required="isMainRequired(field)">
                <component
                  :is="getComponentType(field.htmlType)"
                  v-model:value="previewData.mainForm[field.columnName]"
                  :key="field.columnName"
                  :nm="getMainRuntimeNm(field)"
                  :disabled="isMainDisabled(field)"
                  @common-component="(nm) => handleMainCommonComponent(nm, field)"
                  @change="(val) => handleMainFieldChange(field, val)"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- 主子表预览 -->
      <div v-else-if="previewData.type === 'main-sub'">
        <!-- 主表部分 -->
        <el-card header="主表信息">
          <el-form label-width="120px">
            <el-row :gutter="20">
              <el-col v-for="field in previewData.mainFields" :key="field.columnName" :span="field.formSpan || 24" v-if="isMainVisible(field)">
                <el-form-item :label="field.columnComment" :required="isMainRequired(field)">
                  <component
                    :is="getComponentType(field.htmlType)"
                    v-model:value="previewData.mainForm[field.columnName]"
                    :key="field.columnName"
                    :nm="getMainRuntimeNm(field)"
                    :disabled="isMainDisabled(field)"
                    @common-component="(nm) => handleMainCommonComponent(nm, field)"
                    @change="(val) => handleMainFieldChange(field, val)"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <!-- 子表部分 -->
        <el-card header="子表信息" class="mt-4">
          <div class="mb-4">
            <el-button type="primary" @click="addSubRow">+ 添加子项</el-button>
          </div>

          <el-table :data="previewData.subForms" border stripe class="sub-table">
            <el-table-column
              v-for="field in previewData.subFields"
              :key="field.columnName"
              :prop="field.columnName"
              :label="field.columnComment"
              min-width="180"
            >
              <template #default="{ row, $index }">
                <component
                  v-if="isSubVisible(field, $index)"
                  :is="getComponentType(field.htmlType)"
                  v-model:value="row[field.columnName]"
                  :nm="getSubRuntimeNm(field, row, $index)"
                  :disabled="isSubDisabled(field, $index)"
                  @common-component="(nm) => handleSubCommonComponent(nm, field, row)"
                  @change="(val) => handleSubFieldChange(field, val, row)"
                />
                <span v-else>--</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" icon="Delete" @click="removeSubRow($index)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
    <!-- 代码预览界面 -->
    <el-dialog :title="dialog.title" v-model="codeDialog.visible" width="80%" top="5vh" append-to-body class="scrollbar">
      <el-tabs v-model="codePreview.activeName">
        <el-tab-pane
          v-for="(value, key) in codePreview.data"
          :label="(key as any).substring((key as any).lastIndexOf('/') + 1, (key as any).indexOf('.vm'))"
          :name="(key as any).substring((key as any).lastIndexOf('/') + 1, (key as any).indexOf('.vm'))"
          :key="value"
        >
          <el-link :underline="false" icon="DocumentCopy" v-copyText="value" v-copyText:callback="copyTextSuccess" style="float: right"
            >&nbsp;复制</el-link
          >
          <pre>{{ value }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
    <import-table ref="importRef" @ok="handleQuery" />
  </div>
</template>

<script setup name="online" lang="ts">
import { previewTable, delTable, genCode, synchDb } from '@/api/tool/gen';
import { list, getOnlineForm, getDataNames, addOrUpdateOnlineData } from '@/api/online';
import type { TableQuery, TableVO, OnlFieldVo } from '@/api/online/types';
import importTable from './importTable.vue';
import router from '@/router';
import { ComponentInternalInstance } from 'vue';
import { ElForm, DateModelType } from 'element-plus';
import { useOnline } from '@/views/workflow/hooks/useOnline';
import { useOnlineRuntime, type UiState, type RuntimeLinkageState } from '@/composables/online/useOnlineRuntime';

const route = useRoute();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { getComponentType } = useOnline();
const {
  safeParseJSON,
  applyCommonDataInfo,
  initAutoGenerateForForm,
  applyOnClear,
  applyOnSetValue,
  applyOnMainChange,
  handleMainToMain: handleMainToMainRuntime,
  applyOnRowAdd,
  computeUiStateWithEffects,
  validateBySchema,
  computeRuntimeLinkageState,
  applyOnSubChange,
  handleSubToSub: handleSubToSubRuntime,
  applyOnSubTrigger
} = useOnlineRuntime();

const tableList = ref<TableVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string | number>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const dateRange = ref<[DateModelType, DateModelType]>(['', '']);
const uniqueId = ref('');
const dataNameList = ref<Array<string>>([]);

const queryFormRef = ref(ElForm);
const importRef = ref(importTable);

const queryParams = reactive<TableQuery>({
  pageNum: 1,
  pageSize: 10,
  tableName: '',
  tableComment: '',
  dataName: ''
});

const onlPreview = reactive<any>({
  data: {}
});

type OnlineField = OnlFieldVo & Record<string, any>;

// 预览数据结构
interface PreviewData {
  type: 'single' | 'main-sub' | null;
  mainFields: OnlineField[];
  subFields: OnlineField[];
  mainForm: Record<string, any>;
  subForms: Record<string, any>[];
  foreignKey: string;
  mainTableId: string | number | null;
  mainTableName: string;
  subTableName: string;
}

const previewData = reactive<PreviewData>({
  type: null,
  mainFields: [],
  subFields: [],
  mainForm: {},
  subForms: [],
  foreignKey: '',
  mainTableId: null,
  mainTableName: '',
  subTableName: ''
});

// runtime state for "preview == OnlineTest" behaviors
const workflowCtx = computed<Record<string, any>>(() => {
  const q: any = route.query || {};
  return { ...q };
});

const emptyUiState = (): UiState => ({ visible: {}, disabled: {}, required: {} });
const emptyLinkageState = (): RuntimeLinkageState => ({ dictOptions: {}, popupParams: {}, clearFields: [] });

const mainUiState = ref<UiState>(emptyUiState());
const mainLinkageState = ref<RuntimeLinkageState>(emptyLinkageState());
const subUiStates = ref<UiState[]>([]);
const subLinkageStates = ref<RuntimeLinkageState[]>([]);

const refreshMainRuntime = () => {
  const ctx = { ...(previewData.mainForm || {}), workflow: workflowCtx.value };
  const { ui, effects } = computeUiStateWithEffects(previewData.mainFields, ctx);
  mainUiState.value = ui;
  effects.clearFields.forEach((k) => {
    if (k) (previewData.mainForm as any)[k] = null;
  });

  const linkage = computeRuntimeLinkageState(previewData.mainFields, ctx);
  mainLinkageState.value = linkage;
  linkage.clearFields.forEach((k) => {
    if (k) (previewData.mainForm as any)[k] = null;
  });
};

const refreshSubRuntime = (rowIndex: number) => {
  const row = previewData.subForms?.[rowIndex];
  if (!row) return;
  const ctx = { ...(previewData.mainForm || {}), ...(row || {}), workflow: workflowCtx.value };
  const { ui, effects } = computeUiStateWithEffects(previewData.subFields, ctx);
  subUiStates.value[rowIndex] = ui;
  effects.clearFields.forEach((k) => {
    if (k) (row as any)[k] = null;
  });

  const linkage = computeRuntimeLinkageState(previewData.subFields, ctx);
  subLinkageStates.value[rowIndex] = linkage;
  linkage.clearFields.forEach((k) => {
    if (k) (row as any)[k] = null;
  });
};

const refreshAllRuntime = () => {
  refreshMainRuntime();
  subUiStates.value = [];
  subLinkageStates.value = [];
  (previewData.subForms || []).forEach((_, idx) => refreshSubRuntime(idx));
};

const isMainVisible = (field: any) => {
  const k = field?.columnName;
  if (!k) return true;
  return mainUiState.value?.visible?.[k] !== false;
};

const shouldDisableField = (field: any) => {
  try {
    const cfg: any = safeParseJSON(field?.onChange || '{}');
    return !!cfg?.onCodeGenerate?.autoGenerateRule;
  } catch {
    return false;
  }
};

const isMainDisabled = (field: any) => {
  const k = field?.columnName;
  const runtimeDisabled = k ? mainUiState.value?.disabled?.[k] === true : false;
  return shouldDisableField(field) || runtimeDisabled;
};

const isMainRequired = (field: any) => {
  const k = field?.columnName;
  return k ? mainUiState.value?.required?.[k] === true : false;
};

const getMainRuntimeNm = (field: any) => {
  const k = field?.columnName;
  if (!k) return field;
  return {
    ...field,
    runtimeDictCode: mainLinkageState.value?.dictOptions?.[k],
    runtimePopupParams: mainLinkageState.value?.popupParams?.[k]
  };
};

const isSubVisible = (field: any, rowIndex: number) => {
  const k = field?.columnName;
  if (!k) return true;
  return subUiStates.value?.[rowIndex]?.visible?.[k] !== false;
};

const isSubDisabled = (field: any, rowIndex: number) => {
  const k = field?.columnName;
  return k ? subUiStates.value?.[rowIndex]?.disabled?.[k] === true : false;
};

const getSubRuntimeNm = (field: any, row: any, rowIndex: number) => {
  const k = field?.columnName;
  if (!k) return field;
  return {
    ...field,
    runtimeDictCode: subLinkageStates.value?.[rowIndex]?.dictOptions?.[k],
    runtimePopupParams: subLinkageStates.value?.[rowIndex]?.popupParams?.[k]
  };
};

const codePreview = ref<any>({
  data: {},
  activeName: 'domain.java'
});
const codeDialog = reactive<DialogOption>({
  visible: false,
  title: '代码预览'
});

const indexInner = ref(0);
const indexField = ref(0);
const olLabelCol = { xs: { span: 24 }, sm: { span: 5 } };
const olWrapperCol = { xs: { span: 24 }, sm: { span: 16 } };

const dialog = reactive<DialogOption>({
  visible: false,
  title: '界面预览'
});

const handleMainCommonComponent = (nm: any, field: OnlineField) => {
  applyCommonDataInfo(nm, previewData.mainForm);

  // preview == OnlineTest: clear/setValue -> onMainChange -> runtime refresh
  const ctx0 = { ...(previewData.mainForm || {}), workflow: workflowCtx.value };
  applyOnClear(field, ctx0, (k, v) => {
    (previewData.mainForm as any)[k] = v;
  });
  applyOnSetValue(field, { ...(previewData.mainForm || {}), workflow: workflowCtx.value }, (k, v) => {
    (previewData.mainForm as any)[k] = v;
  });
  applyOnMainChange(field, previewData.mainForm, (previewData.mainForm as any)?.[field?.columnName]);

  refreshAllRuntime();
  if (nm?.columnName) onlPreview.data[nm.columnName] = nm.commonDataInfo;
};

const handleSubCommonComponent = (nm: any, field: OnlineField, row: Record<string, any>) => {
  applyCommonDataInfo(nm, row);
  if (nm?.columnName) onlPreview.data[nm.columnName] = nm.commonDataInfo;

  const idx = (previewData.subForms || []).indexOf(row);
  const ctx0 = { ...(previewData.mainForm || {}), ...(row || {}), workflow: workflowCtx.value };
  applyOnClear(field, ctx0, (k, v) => {
    (row as any)[k] = v;
  });
  applyOnSetValue(field, ctx0, (k, v) => {
    (row as any)[k] = v;
  });

  applyOnSubChange(field, row, previewData.mainForm);
  applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm);
  if (idx >= 0) refreshSubRuntime(idx);
  refreshMainRuntime();
};

const handleMainFieldChange = (field: OnlineField, newValue: any) => {
  handleMainToMainRuntime(field, newValue, previewData.mainForm as any, workflowCtx.value);
  refreshAllRuntime();
};

const handleSubFieldChange = (field: OnlineField, newValue: any, row: Record<string, any>) => {
  const idx = (previewData.subForms || []).indexOf(row);
  handleSubToSubRuntime(field, newValue, row as any, previewData.mainForm as any, workflowCtx.value);
  applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm);
  if (idx >= 0) refreshSubRuntime(idx);
  refreshMainRuntime();
};

const submitOnlForm = () => {};
const resetOnlForm = () => {};

// 新建表设计器
const handleNewTable = () => {
  // 跳转到表设计器页面
  router.push('/online/db');
};

onActivated(() => {
  const time = route.query.t;
  if (time != null && time != uniqueId.value) {
    uniqueId.value = time as string;
    queryParams.pageNum = Number(route.query.pageNum);
    dateRange.value = ['', ''];
    queryFormRef.value.resetFields();
    getList();
  }
});

/** 查询多数据源名称 */
const getDataNameList = async () => {
  const res = await getDataNames();
  dataNameList.value = res.data;
};

/** 查询表集合 */
const getList = async () => {
  loading.value = true;
  const res = await list(proxy?.addDateRange(queryParams, dateRange.value));
  tableList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};
/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};
/** 重置按钮操作 */
const resetQuery = () => {
  dateRange.value = ['', ''];
  queryFormRef.value.resetFields();
  handleQuery();
};

/** 代码预览按钮 */
const handleCodePreview = async (row: TableVO) => {
  const res = await previewTable(row.tableId);
  codePreview.value.data = res.data;
  codeDialog.visible = true;
  codePreview.value.activeName = 'domain.java';
};

/** 生成代码操作 */
const handleGenTable = async (row?: TableVO) => {
  const tbIds = row?.tableId || ids.value;
  if (tbIds == '') {
    proxy?.$modal.msgError('请选择要生成的数据');
    return;
  }
  if (row?.genType === '1') {
    await genCode(row.tableId);
    proxy?.$modal.msgSuccess('成功生成到自定义路径：' + row.genPath);
  } else {
    proxy?.$download.zip('/tool/gen/batchGenCode?tableIdStr=' + tbIds, 'nbmade.zip');
  }
};
/** 同步数据库操作 */
const handleSynchDb = async (row: TableVO) => {
  const tableId = row.tableId;
  await proxy?.$modal.confirm('确认要强制同步"' + row.tableName + '"表结构吗？');
  await synchDb(tableId);
  proxy?.$modal.msgSuccess('同步成功');
};
/** 打开导入表弹窗 */
const openImportTable = () => {
  importRef.value.show(queryParams.dataName);
};

/** 预览按钮 */
const handlePreview = async (row: TableVO) => {
  try {
    // 清空旧数据
    previewData.type = null;
    previewData.mainForm = {};
    previewData.subForms = [];
    previewData.mainTableId = row.tableId;
    previewData.mainTableName = row.tableName;
    previewData.subTableName = '';
    previewData.foreignKey = '';

    // 获取表单结构
    const formRes = await getOnlineForm(row.tableId);
    const formData = formRes.data;

    // 判断表单类型
    if (formData.type === 'main-sub') {
      // 主子表处理
      previewData.type = 'main-sub';
      previewData.mainFields = formData.main.columnsVo || [];
      previewData.subFields = formData.sub.columnsVo || [];
      previewData.foreignKey = formData.foreignKey || '';
      previewData.subTableName = formData.sub?.tableName || '';

      // 初始化主表默认值
      previewData.mainFields.forEach((f) => {
        if (f?.columnName) previewData.mainForm[f.columnName] = previewData.mainForm[f.columnName] ?? null;
      });
      // 初始化子表至少一行
      addSubRow();
      // 编码产生：打开预览时自动填充（为空才填）
      initAutoGenerateForForm(previewData.mainFields, previewData.mainForm);
    } else if (formData.type === 'single') {
      // 单表处理
      previewData.type = 'single';
      previewData.mainFields = formData.data.columnsVo || [];

      previewData.mainFields.forEach((f) => {
        if (f?.columnName) previewData.mainForm[f.columnName] = previewData.mainForm[f.columnName] ?? null;
      });
      initAutoGenerateForForm(previewData.mainFields, previewData.mainForm);
    } else if (formData.type === 'sub') {
      proxy?.$modal.msgError('附子表单类型不能预览！');
      return;
    }

    refreshAllRuntime();
    dialog.visible = true;
  } catch (error) {
    proxy?.$modal.msgError('数据加载失败');
  }
};

// 添加子表行
const addSubRow = () => {
  const row = (previewData.subFields || []).reduce((acc: any, cur: any) => {
    if (cur?.columnName) acc[cur.columnName] = null;
    return acc;
  }, {});
  // apply row defaults (onChange.onRowAdd)
  applyOnRowAdd(previewData.subFields, row, previewData.mainForm as any, workflowCtx.value);
  previewData.subForms.push(row);
  applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm);
  refreshSubRuntime(previewData.subForms.length - 1);
  refreshMainRuntime();
};

// 删除子表行
const removeSubRow = (index: number) => {
  if (previewData.subForms.length > 1) {
    previewData.subForms.splice(index, 1);
    applyOnSubTrigger(previewData.mainFields, previewData.subForms, previewData.mainForm);
    refreshAllRuntime();
  } else {
    proxy?.$modal.msgWarning('至少需要保留一个子项');
  }
};

// 提交处理
const handleSubmit = async () => {
  try {
    if (!previewData.mainTableId || !previewData.mainTableName) {
      proxy?.$modal.msgError('缺少表信息，无法提交');
      return;
    }

    refreshAllRuntime();

    const mainErrors = validateBySchema(previewData.mainFields, { ...(previewData.mainForm || {}), workflow: workflowCtx.value }, mainUiState.value);
    if (mainErrors.length) {
      proxy?.$modal.msgError(mainErrors[0].message);
      return;
    }

    if (previewData.type === 'main-sub') {
      // 过滤空子项
      const validSubs = previewData.subForms.filter((sub) => Object.values(sub).some((v) => v !== null && v !== undefined && v !== ''));

      // 子表校验（仅校验非空行）
      for (let i = 0; i < previewData.subForms.length; i++) {
        const row = previewData.subForms[i];
        const isEmptyRow = !Object.values(row || {}).some((v) => v !== null && v !== undefined && v !== '');
        if (isEmptyRow) continue;
        const ui = subUiStates.value[i] || emptyUiState();
        const rowErrors = validateBySchema(
          previewData.subFields,
          { ...(previewData.mainForm || {}), ...(row || {}), workflow: workflowCtx.value },
          ui
        );
        if (rowErrors.length) {
          proxy?.$modal.msgError(`子表第${i + 1}行：${rowErrors[0].message}`);
          return;
        }
      }

      const payload = {
        main: previewData.mainForm,
        sub: {
          tableName: previewData.subTableName,
          data: validSubs
        }
      };
      await addOrUpdateOnlineData(previewData.mainTableId, previewData.mainTableName, payload);
    } else if (previewData.type === 'single') {
      await addOrUpdateOnlineData(previewData.mainTableId, previewData.mainTableName, previewData.mainForm);
    } else {
      proxy?.$modal.msgError('未知表单类型，无法提交');
      return;
    }

    proxy?.$modal.msgSuccess('操作成功');
    dialog.visible = false;
    getList();
  } catch (error) {
    proxy?.$modal.msgError('操作失败');
  }
};
/** 复制代码成功 */
const copyTextSuccess = () => {
  proxy?.$modal.msgSuccess('复制成功');
};
// 多选框选中数据
const handleSelectionChange = (selection: TableVO[]) => {
  ids.value = selection.map((item) => item.tableId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
};
/** 修改按钮操作 */
const handleEditTable = (row?: TableVO) => {
  const tableId = row?.tableId || ids.value[0];
  router.push({ path: '/online/edit/' + tableId, query: { pageNum: queryParams.pageNum } });
};
/** 删除按钮操作 */
const handleDelete = async (row?: TableVO) => {
  const tableIds = row?.tableId || ids.value;
  await proxy?.$modal.confirm('是否确认删除表编号为"' + tableIds + '"的数据项？');
  await delTable(tableIds);
  getList();
  proxy?.$modal.msgSuccess('删除成功');
};

/** online表单功能测试按钮操作 */
const handleTest = (row?: TableVO) => {
  if (row?.tplCategory === 'sub') {
    proxy?.$modal.msgError('附子表不支持在线表单功能测试！');
    return;
  }
  const tableId = row?.tableId;
  const tableName = row?.tableName;
  // 直接使用路径跳转，绕过命名路由匹配
  const pathTarget = {
    path: `/online/test/${String(tableId || '')}/${encodeURIComponent(String(tableName || ''))}`,
    query: { t: Date.now() }
  } as const;
  router
    .push({
      path: `/redirect${pathTarget.path}`,
      query: pathTarget.query
    })
    .catch((e) => {
      console.error('redirect 跳转失败，执行硬跳转兜底:', e);
      const href = router.resolve(pathTarget).href;
      window.location.href = href;
    });
};

onMounted(() => {
  getList();
  getDataNameList();
});
</script>
<style lang="scss" scoped>
.olFromItem {
  width: 100%;
}
.sub-item {
  position: relative;
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  transition: all 0.3s;

  &:hover {
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }
}

.float-right {
  position: absolute;
  right: 20px;
  top: 20px;
}

.mt-4 {
  margin-top: 1rem;
}

.el-form-item.is-required .el-form-item__label::before {
  content: '*';
  color: #f56c6c;
  margin-right: 4px;
}

.sub-table {
  margin-top: 15px;

  .el-table__cell {
    padding: 8px 0;
  }

  .cell {
    padding: 0 8px;
  }
}

// 调整组件内部间距
.el-input,
.el-select {
  width: 100%;
}
</style>
