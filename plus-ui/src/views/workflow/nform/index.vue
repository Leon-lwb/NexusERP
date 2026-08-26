<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div class="search" v-show="showSearch">
        <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
          <el-form-item label="表单名称" prop="formName">
            <el-input v-model="queryParams.formName" placeholder="请输入表单名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="表单描述" prop="formDesc">
            <el-input v-model="queryParams.formDesc" placeholder="请输入表单描述" clearable style="width: 240px" @keyup.enter="handleQuery" />
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
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['demo:nform:add']">新增 </el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['demo:nform:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['demo:nform:remove']"
              >删除</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['demo:nform:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="nformList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="表单ID" align="center" prop="formId" v-if="true" />
        <el-table-column label="表单名称" align="center" prop="formName" />
        <el-table-column label="表单描述" align="center" prop="formDesc" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button link type="primary" icon="View" @click="handleDetail(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="修改" placement="top">
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['demo:nform:edit']"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['demo:nform:remove']"></el-button>
            </el-tooltip>
            <el-tooltip content="测试" placement="top">
              <el-button link type="primary" icon="Connection" @click="handleTest(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
    <!-- 添加或修改设计器单对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="960px" append-to-body>
      <el-form ref="nformFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="表单名称" prop="formName">
          <el-input v-model="form.formName" placeholder="请输入表单名称" />
        </el-form-item>
        <el-form-item label="表单描述" prop="formDesc">
          <el-input v-model="form.formDesc" placeholder="请输入表单描述" />
        </el-form-item>
        <el-form-item label="表单内容">
          <editor v-model="form.formContent" :min-height="192" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 动态生成弹窗选择组件 -->
    <!-- 主表弹窗 -->
    <PopupSelect
      v-for="column in mainColumns.filter((c) => c.htmlType === 'popup')"
      :key="'main_' + column.javaField"
      v-model:visible="popupVisible[column.javaField]"
      :dict-type="column.dictType"
      :dict-fields="getDictFields(column)"
      :mapping="getMainMapping(column)"
      :selected-values="getMainSelectedValues(column)"
      :multiple="getPopupMultipleFromForm(column)"
      :title="`选择${column.columnComment}`"
      @select="(data) => handleMainSelect(data, column)"
    />
    <!-- 表单设计器对话框 -->
    <el-dialog
      v-model="designerVisible"
      :title="designerTitle"
      width="100%"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="true"
      @close="handleDesignerClose"
      class="form-designer-dialog"
      :fullscreen="true"
    >
      <FormDesigner
        v-if="designerVisible"
        :form-data="currentFormData"
        :table-selector-ref="tableSelectorRef"
        @save="handleDesignerSave"
        @close="handleDesignerClose"
      />
    </el-dialog>
    <!-- 表单详情对话框 -->
    <el-dialog v-model="detailVisible" title="表单详情" width="60%" append-to-body :close-on-click-modal="false">
      <div class="form-detail">
        <div class="form-info">
          <h3>{{ currentForm.formName }}</h3>
        </div>
        <div class="form-preview">
          <FormPreview
            :form-content="currentForm.formContent"
            :model-value="detailVisible"
            @close="detailVisible = false"
            @update:model-value="detailVisible = $event"
          />
        </div>
      </div>
    </el-dialog>
    <!-- 分步处理对话框 -->
    <el-dialog v-model="stepDialog.visible" :title="stepDialog.title" width="80%" append-to-body :close-on-click-modal="false">
      <el-steps :active="stepDialog.activeStep" finish-status="success" simple style="margin-bottom: 20px">
        <el-step title="选择数据表" />
        <el-step title="预览表结构" />
        <el-step title="设计表单" />
      </el-steps>

      <!-- 步骤1：选择数据表 -->
      <div v-if="stepDialog.activeStep === 0">
        <el-table v-loading="loading" :data="tableList" highlight-current-row @current-change="handleTableSelect" row-key="tableName">
          <el-table-column label="" width="60" align="center">
            <template #default="scope">
              <el-radio v-model="selectedTableName" :label="scope.row.tableName"></el-radio>
            </template>
          </el-table-column>
          <el-table-column label="表名称" align="center" prop="tableName" />
          <el-table-column label="表描述" align="center" prop="tableComment" />
          <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        </el-table>
      </div>

      <!-- 步骤2：预览表结构 -->
      <div v-if="stepDialog.activeStep === 1">
        <el-table :data="columnList" border>
          <el-table-column label="字段名称" align="center" prop="columnName" />
          <el-table-column label="字段描述" align="center" prop="columnComment" />
          <el-table-column label="字段类型" align="center" prop="columnType" />
          <el-table-column label="是否必填" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.isNullable === 'NO' ? 'danger' : 'info'">
                {{ scope.row.isNullable === 'NO' ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="是否列表" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.isList ? 'success' : 'info'">
                {{ scope.row.isList ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="是否查询" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.isQuery ? 'warning' : 'info'">
                {{ scope.row.isQuery ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancelStep">取 消</el-button>
          <el-button type="primary" @click="prevStep" v-if="stepDialog.activeStep > 0">上一步</el-button>
          <el-button type="primary" @click="nextStep" v-if="stepDialog.activeStep === 0">下一步</el-button>
          <el-button type="primary" @click="enterDesigner" v-if="stepDialog.activeStep === 1">下一步</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 表选择器组件 -->
    <TableSelector ref="tableSelectorRef" @generate="handleTableSelectorGenerate" />
  </div>
</template>

<script setup name="Nform" lang="ts">
import {
  ref,
  watch,
  computed,
  triggerRef,
  getCurrentInstance,
  defineAsyncComponent,
  ComponentInternalInstance,
  reactive,
  toRefs,
  onMounted,
  nextTick
} from 'vue';
import dayjs from 'dayjs';
import { listNform, getNform, delNform, addNform, updateNform } from '@/api/workflow/nform';
import { NformVO, NformQuery, NformForm } from '@/api/workflow/nform/types';
import { listTableData } from '@/api/online';
import { getTableColumns } from '@/api/tool/gen';
import { useRouter, useRoute } from 'vue-router';
import FormDesigner from '@/components/DesignerForm/FormDesigner.vue';
import FormPreview from '@/components/DesignerForm/FormPreview.vue';
import TableSelector from '@/components/DesignerForm/TableSelector.vue';
import { list, getDataNames } from '@/api/online';
import { TableQuery, TableVO } from '@/api/online/types';
import type { FormInstance } from 'element-plus';

// 定义接口
interface DialogOption {
  visible: boolean;
  title: string;
}

interface PageData<T, Q> {
  form: T;
  queryParams: Q;
  rules: Record<string, any>;
}

interface Column {
  columnName: string;
  columnType: string;
  columnComment: string;
  javaField: string;
  isRequired: string;
  isList: string;
  isQuery: string;
  dictType?: string;
  dictFields?: string;
  mapping?: string;
}

// PopupSelect组件引入
const PopupSelect = defineAsyncComponent(() => import('@/components/PopupSelect/index.vue'));

const router = useRouter();
const route = useRoute();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const nformList = ref<NformVO[]>([]);
const buttonLoading = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string | number>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const queryFormRef = ref<FormInstance>();
const nformFormRef = ref<FormInstance>();

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const stepDialog = reactive({
  visible: false,
  title: '新增表单',
  activeStep: 0
});

const tableList = ref<any[]>([]);
const selectedTable = ref<any>(null);
const selectedTableName = ref<string>('');
const columnList = ref<any[]>([]);
const selectedFields = ref<Record<string, boolean>>({});
const selectedSubFields = ref<Record<string, boolean>>({});

const initFormData: NformForm = {
  formName: undefined,
  formDesc: undefined,
  formContent: undefined
};

const data = reactive<PageData<NformForm, NformQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    formName: undefined,
    formDesc: undefined,
    formContent: undefined,
    params: {}
  },
  rules: {
    formName: [{ required: true, message: '表单名称不能为空', trigger: 'blur' }],
    formDesc: [{ required: true, message: '表单描述不能为空', trigger: 'blur' }],
    formContent: [{ required: true, message: '表单内容不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

// 主表和子表列数据分开管理
const mainColumns = ref<any[]>([]);
// 弹窗可见状态分开管理
const popupVisible = reactive<Record<string, boolean>>({}); // 主表
const subPopupVisible = reactive<Record<string, boolean>>({}); // 子表
const currentSubRowIndex = ref(-1); // 当前操作的子表行索引
//修改时候显示名称用
const relationDict = ref<Record<string, Record<string, string>>>({});

// 添加表单设计器相关状态
const designerVisible = ref(false);
const designerTitle = ref('');
const designerRef = ref();
const currentFormData = ref<any>(null);

// 详情相关
const detailVisible = ref(false);
const currentForm = ref<NformVO>({
  formId: '',
  formName: '',
  formDesc: '',
  formContent: ''
});

// 添加TableSelector引用
const tableSelectorRef = ref();

// 添加表格选择相关状态
const tableSelection = ref<any[]>([]);

/** 查询设计器单列表 */
const getList = async () => {
  loading.value = true;
  const res = await listNform(queryParams.value);
  nformList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
};

/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData };
  nformFormRef.value?.resetFields();
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};

/** 多选框选中数据 */
const handleSelectionChange = (selection: NformVO[]) => {
  ids.value = selection.map((item) => item.formId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
};

// 动态获取字段配置
const getDictFields = (column: any) => {
  try {
    return JSON.parse(column.dictFields || '{}');
  } catch {
    return { id: 'ID', name: '名称' };
  }
};

// 改进的getMainMapping方法（支持复杂映射）
const getMainMapping = (column: any) => {
  try {
    const defaultMapping = { id: column.javaField, name: `${column.javaField}Name` };
    const customMapping = JSON.parse(column.mapping || '{}');

    // 合并默认映射和自定义映射
    return { ...defaultMapping, ...customMapping };
  } catch {
    return { id: column.javaField, name: `${column.javaField}Name` };
  }
};

// 获取 popup 的多选配置
const getPopupMultiple = (column: any): boolean => {
  try {
    // 从数据库表字段的 mapping 中获取
    if (column.mapping && typeof column.mapping === 'string') {
      const mappingConfig = JSON.parse(column.mapping);
      if (mappingConfig.popupConfig && typeof mappingConfig.popupConfig.multiple === 'boolean') {
        return mappingConfig.popupConfig.multiple;
      }
    }

    return false;
  } catch (error) {
    console.error('getPopupMultiple error:', error);
    return false;
  }
};

// 从表单组件配置中获取 popup 的 multiple 设置
const getPopupMultipleFromForm = (column: any): boolean => {
  try {
    // 如果当前有表单数据，尝试从表单组件配置中获取
    if (currentFormData.value && currentFormData.value.formContent) {
      const formContent = JSON.parse(currentFormData.value.formContent);
      const formComponents = formContent.formComponents || [];

      // 查找对应的表单组件
      const formComponent = formComponents.find((comp: any) => comp.field === column.columnName || comp.dbInfo?.columnName === column.columnName);

      if (formComponent && formComponent.mapping) {
        const mappingConfig = JSON.parse(formComponent.mapping);
        if (mappingConfig.popupConfig && typeof mappingConfig.popupConfig.multiple === 'boolean') {
          return mappingConfig.popupConfig.multiple;
        }
      }
    } else {
    }

    // 如果没有找到，回退到数据库表字段配置
    const result = getPopupMultiple(column);
    return result;
  } catch (error) {
    console.error('getPopupMultipleFromForm error:', error);
    const result = getPopupMultiple(column);
    return result;
  }
};

// 添加类型定义
interface FormComponent {
  id: string;
  type: string;
  label: string;
  field: string;
  required: boolean;
  props: Record<string, any>;
  dbInfo: {
    tableName: string;
    columnName: string;
    columnType: string;
    columnComment: string;
    isNullable: string;
  };
  isMaster: boolean;
  masterField: string;
  subTableName: string;
  subTableColumns: any[];
  dictType?: string;
  dictFields?: string;
  mapping?: string;
  showDisplay?: boolean;
}

interface SubTableColumn extends Omit<FormComponent, 'isMaster' | 'masterField' | 'subTableName' | 'subTableColumns'> {}

// 修改 getDictTypeByColumn 方法的参数类型
const getDictTypeByColumn = (column: { columnName: string; columnComment: string }): string => {
  const columnName = column.columnName.toLowerCase();
  const columnComment = column.columnComment.toLowerCase();

  // 根据字段名和注释判断字典类型
  if (columnName.includes('notice_type') || columnComment.includes('通知类型')) {
    return 'sys_notice_type';
  } else if (columnName.includes('status') || columnComment.includes('状态')) {
    return 'sys_normal_disable';
  } else if (columnName.includes('sex') || columnComment.includes('性别')) {
    return 'sys_user_sex';
  } else if (columnName.includes('menu_type') || columnComment.includes('菜单类型')) {
    return 'sys_menu_type';
  } else if (columnName.includes('sys_yes_no') || columnComment.includes('是否')) {
    return 'sys_yes_no';
  } else if (columnName.includes('job_status') || columnComment.includes('任务状态')) {
    return 'sys_job_status';
  } else if (columnName.includes('job_group') || columnComment.includes('任务组名')) {
    return 'sys_job_group';
  } else if (columnName.includes('sys_oper_type') || columnComment.includes('操作类型')) {
    return 'sys_oper_type';
  } else if (columnName.includes('sys_common_status') || columnComment.includes('状态')) {
    return 'sys_common_status';
  }

  return '';
};

// 修改 handleMainSelect 方法
const handleMainSelect = (data: Record<string, unknown> | Record<string, unknown>[], column: any) => {
  const mapping = getMainMapping(column);
  const isMultiple = getPopupMultipleFromForm(column);

  if (form.value) {
    if (isMultiple && Array.isArray(data)) {
      // 多选模式：处理数组数据
      data.forEach((item, index) => {
        Object.entries(mapping).forEach(([source, target]) => {
          if (item[source] !== undefined && typeof target === 'string') {
            if (index === 0) {
              // 第一个值直接赋值
              (form.value as Record<string, unknown>)[target] = item[source];
            } else {
              // 后续值追加到带序号的字段，避免覆盖前面的选中项
              const fieldName = `${target}${index + 1}`;
              (form.value as Record<string, unknown>)[fieldName] = item[source];
            }
          }
        });
      });
    } else if (!isMultiple && !Array.isArray(data)) {
      // 单选模式：处理单个对象
      Object.entries(mapping).forEach(([source, target]) => {
        if (data[source] !== undefined && typeof target === 'string') {
          (form.value as Record<string, unknown>)[target] = data[source];
        }
      });
    }
  }
  triggerRef(form);
  popupVisible[column.javaField] = false;
};

// 自动生成编号逻辑（增强版）
const generateBusinessNo = (column: any) => {
  try {
    const ruleConfig = JSON.parse(column.onChange || '{}').onCodeGenerate;
    if (!ruleConfig?.autoGenerateRule) return;

    let result = ruleConfig.autoGenerateRule;
    const params = ruleConfig.generateParams || {};

    // 替换时间戳
    if (result.includes('{timestamp}')) {
      const timestamp = params.format ? dayjs().format(params.format) : Date.now().toString();
      result = result.replace(/{timestamp}/g, timestamp);
    }

    // 替换序列号
    if (result.includes('{sequence}')) {
      const sequence = generateSequence(column.javaField);
      result = result.replace(/{sequence}/g, sequence);
    }

    return result;
  } catch (e) {
    console.error('编号生成失败:', e);
  }
};

//获取表popup字段的字典信息
const getDictTypeData = async (table: string) => {
  const res = await listTableData(table, queryParams.value);

  // 为当前列创建独立存储空间
  relationDict.value[table] = res.rows.reduce(
    (dict: Record<string, string>, item: any) => {
      dict[item.id] = item.name;
      return dict;
    },
    {} as Record<string, string>
  );
};

// 获取列配置
const getColumns = async () => {
  const res = await getTableColumns('wf_nform');
  mainColumns.value = res.data || [];

  // 加载所有popup字段的字典数据
  mainColumns.value.filter((col) => col.htmlType === 'popup' && col.dictType).forEach((col) => getDictTypeData(col.dictType));
};

/** 新增按钮操作 */
const handleAdd = () => {
  tableSelectorRef.value.visible = true;
};

/** 处理表选择器生成表单 */
const handleTableSelectorGenerate = (formData: any) => {
  // 设置表单设计器数据
  currentFormData.value = {
    formName: formData.formName,
    formDesc: formData.formDesc,
    formContent: formData.formContent
  };
  designerTitle.value = '设计表单';
  designerVisible.value = true;
};

/** 处理表选择 */
const handleTableSelect = async (table: any) => {
  if (!table) return;

  try {
    // 先设置选中的表
    selectedTable.value = table;

    // 打印原始表数据

    // 获取表字段数据
    const res = await getTableColumns(table.tableName);
    if (res.data) {
      const columnsData = Array.isArray(res.data) ? res.data : [];

      // 处理字段数据，过滤掉主键字段
      const processedColumns = columnsData
        .filter((column) => column.isPk !== '1') // 过滤掉主键字段
        .map(processColumn);

      // 判断是否是主子表
      if (table.tplCategory === 'main' && table.subTableName) {
        const subRes = await getTableColumns(table.subTableName);
        if (subRes.data) {
          const subColumnsData = Array.isArray(subRes.data) ? subRes.data : [];
          // 子表也过滤掉主键字段
          table.subColumnsVo = subColumnsData.filter((column) => column.isPk !== '1').map(processColumn);
        } else {
        }
      } else {
        // 单表处理
        table.tplCategory = 'single';
        table.subTableName = '';
        table.subTableFkName = '';
        table.subColumnsVo = [];
      }

      // 更新表数据
      table.columnsVo = processedColumns;
      selectedTable.value = table;

      // 初始化字段状态
      initFieldStates(table);

      // 设置列列表
      columnList.value = processedColumns;

      // 显示步骤对话框
      stepDialog.visible = true;
      stepDialog.activeStep = 1;

      // 打印调试信息
    }
  } catch (error) {
    console.error('获取表字段信息失败:', error);
    proxy?.$modal.msgError('获取表字段信息失败');
  }
};

/** 获取字段类型 */
const getColumnType = (columnType: string): string => {
  if (columnType.includes('text') || (columnType.includes('varchar') && columnType.includes('(500)'))) {
    return 'textarea';
  } else if (columnType.includes('int') || columnType.includes('decimal')) {
    return 'input';
  } else if (columnType.includes('tinyint(1)')) {
    return 'checkbox';
  } else if (columnType.includes('datetime')) {
    return 'datetime';
  } else if (columnType.includes('date')) {
    return 'date';
  } else if (columnType.includes('time')) {
    return 'time';
  }
  return 'input';
};

/** 进入表单设计器 */
const enterDesigner = () => {
  if (!selectedTable.value) return;

  // 构建表信息
  const tableInfo = {
    mainTable: {
      tableName: selectedTable.value.tableName,
      tableComment: selectedTable.value.tableComment,
      columns: columnList.value
        .filter((col: any) => selectedFields.value[col.columnName])
        .map((col: any) => ({
          columnName: col.columnName,
          columnType: col.columnType,
          columnComment: col.columnComment,
          isNullable: col.isNullable,
          isRequired: col.isNullable === 'NO' ? '1' : '0',
          dictType: col.dictType,
          htmlType: getColumnType(col.columnType),
          dictFields: col.dictFields,
          mapping: col.mapping
        }))
    },
    subTable: undefined as
      | undefined
      | {
          tableName?: string;
          tableComment?: string;
          fkName?: string;
          columns?: any[];
        }
  };

  // 如果有子表信息，添加到 tableInfo 中
  if (selectedTable.value.subTableName && selectedTable.value.subColumnsVo) {
    tableInfo.subTable = {
      tableName: selectedTable.value.subTableName,
      tableComment: selectedTable.value.subTableComment || '',
      fkName: selectedTable.value.subTableFkName || 'main_id',
      columns:
        selectedTable.value.subColumnsVo?.map((col: any) => ({
          columnName: col.columnName,
          columnType: col.columnType,
          columnComment: col.columnComment,
          isNullable: col.isNullable,
          isRequired: col.isNullable === 'NO' ? '1' : '0',
          dictType: col.dictType,
          htmlType: getColumnType(col.columnType),
          dictFields: col.dictFields,
          mapping: col.mapping
        })) || []
    };
  } else {
  }

  // 构建表单数据
  const formContent = {
    formConfig: {
      labelWidth: '100px',
      labelPosition: 'right',
      size: 'default',
      formName: selectedTable.value.tableComment || selectedTable.value.tableName,
      formDesc: `基于${selectedTable.value.tableComment || selectedTable.value.tableName}生成的表单`,
      tableInfo: tableInfo
    },
    formComponents: columnList.value
      .filter((col: any) => selectedFields.value[col.columnName])
      .map((col: any) => {
        let type = 'input';
        let props: any = {
          placeholder: `请输入${col.columnComment || col.columnName}`
        };

        // 根据字段类型和字典类型选择对应的表单组件
        if (col.dictType) {
          // 如果有字典类型，设置为 select 类型
          type = 'select';
          props = {
            ...props,
            options: [] // 字典数据会在预览时动态加载
          };
        } else if (col.columnType.includes('text') || (col.columnType.includes('varchar') && col.columnType.includes('(500)'))) {
          type = 'textarea';
        } else if (col.columnType.includes('int') || col.columnType.includes('decimal')) {
          type = 'input';
          props.type = 'number';
        } else if (col.columnType.includes('tinyint(1)')) {
          type = 'checkbox';
          props = {
            ...props,
            trueValue: 1,
            falseValue: 0,
            label: col.columnComment || col.columnName
          };
        } else if (col.columnType.includes('datetime') || col.columnType.includes('date')) {
          type = 'date-picker';
          props = {
            ...props,
            type: 'datetime'
          };
        }

        // 根据字段名和注释判断是否需要使用字典
        let dictType = col.dictType;
        let dictFields = col.dictFields;
        let mapping = col.mapping;

        if (
          !dictType &&
          (col.columnName.includes('type') ||
            col.columnName.includes('status') ||
            col.columnComment.includes('类型') ||
            col.columnComment.includes('状态'))
        ) {
          // 设置字典类型
          dictType = getDictTypeByColumn(col);
          // 设置字典字段映射
          dictFields = JSON.stringify({
            dictValue: col.columnName,
            dictLabel: `${col.columnName}Name`
          });
          // 设置字段映射
          mapping = JSON.stringify({
            [col.columnName]: col.columnName,
            [`${col.columnName}Name`]: `${col.columnName}Name`
          });
        }

        return {
          id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          type,
          label: col.columnComment || col.columnName,
          field: col.columnName,
          required: col.isNullable === 'NO',
          props,
          dbInfo: {
            tableName: selectedTable.value.tableName,
            columnName: col.columnName,
            columnType: col.columnType,
            columnComment: col.columnComment,
            isNullable: col.isNullable,
            tableInfo: tableInfo
          },
          isMaster: true,
          masterField: '',
          subTableName: '',
          subTableColumns: [],
          dictType,
          dictFields,
          mapping,
          showDisplay: !!dictType
        };
      }),
    masterSubTableData:
      selectedTable.value.subTableName && selectedTable.value.subColumnsVo
        ? {
            subTableName: selectedTable.value.subTableName,
            subTableFkName: selectedTable.value.subTableFkName,
            subColumns:
              selectedTable.value.subColumnsVo?.map((col: any) => {
                let type = 'input';
                let props: any = {
                  placeholder: `请输入${col.columnComment || col.columnName}`
                };

                // 根据字段类型和字典类型选择对应的表单组件
                if (col.dictType) {
                  type = 'select';
                  props = {
                    ...props,
                    options: []
                  };
                } else if (col.columnType.includes('text') || (col.columnType.includes('varchar') && col.columnType.includes('(500)'))) {
                  type = 'textarea';
                } else if (col.columnType.includes('int') || col.columnType.includes('decimal')) {
                  type = 'input';
                  props.type = 'number';
                } else if (col.columnType.includes('tinyint(1)')) {
                  type = 'checkbox';
                  props = {
                    ...props,
                    trueValue: 1,
                    falseValue: 0,
                    label: col.columnComment || col.columnName
                  };
                } else if (col.columnType.includes('datetime') || col.columnType.includes('date')) {
                  type = 'date-picker';
                  props = {
                    ...props,
                    type: 'datetime'
                  };
                }

                // 根据字段名和注释判断是否需要使用字典
                let dictType = col.dictType;
                let dictFields = col.dictFields;
                let mapping = col.mapping;

                if (
                  !dictType &&
                  (col.columnName.includes('type') ||
                    col.columnName.includes('status') ||
                    col.columnComment.includes('类型') ||
                    col.columnComment.includes('状态'))
                ) {
                  // 设置字典类型
                  dictType = getDictTypeByColumn(col);
                  // 设置字典字段映射
                  dictFields = JSON.stringify({
                    dictValue: col.columnName,
                    dictLabel: `${col.columnName}Name`
                  });
                  // 设置字段映射
                  mapping = JSON.stringify({
                    [col.columnName]: col.columnName,
                    [`${col.columnName}Name`]: `${col.columnName}Name`
                  });
                }

                return {
                  id: `component_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
                  type,
                  label: col.columnComment || col.columnName,
                  field: col.columnName,
                  required: col.isNullable === 'NO',
                  props,
                  dbInfo: {
                    tableName: selectedTable.value.subTableName || '',
                    columnName: col.columnName,
                    columnType: col.columnType,
                    columnComment: col.columnComment,
                    isNullable: col.isNullable,
                    tableInfo: tableInfo
                  },
                  dictType,
                  dictFields,
                  mapping,
                  showDisplay: !!dictType
                };
              }) || []
          }
        : {}
  };

  // 设置当前表单数据
  currentFormData.value = {
    formName: selectedTable.value.tableComment || selectedTable.value.tableName,
    formDesc: `基于${selectedTable.value.tableComment || selectedTable.value.tableName}生成的表单`,
    formContent: JSON.stringify(formContent),
    formType: 'PC'
  };

  // 进入表单设计器
  designerVisible.value = true;
};

/** 上一步 */
const prevStep = () => {
  stepDialog.activeStep--;
};

/** 下一步 */
const nextStep = async () => {
  if (!selectedTable.value) {
    proxy?.$modal.msgWarning('请先选择数据表');
    return;
  }

  try {
    const res = await getTableColumns(selectedTable.value.tableName);
    if (res.data) {
      const columnsData = Array.isArray(res.data) ? res.data : [];
      // 处理字段数据，过滤掉主键字段
      const processedColumns = columnsData
        .filter((column) => column.isPk !== '1') // 过滤掉主键字段
        .map(processColumn);

      // 更新选中表的字段数据
      selectedTable.value.columnsVo = processedColumns;

      // 设置列列表
      columnList.value = processedColumns;

      // 初始化字段选择状态
      columnList.value.forEach((col) => {
        selectedFields.value[col.columnName] = true;
      });

      stepDialog.activeStep = 1;

      // 打印调试信息
    }
  } catch (error) {
    console.error('获取表字段失败:', error);
    proxy?.$modal.msgError('获取表字段失败');
  }
};

/** 取消步骤 */
const cancelStep = () => {
  stepDialog.visible = false;
  selectedTable.value = null;
  columnList.value = [];
  selectedFields.value = {};
  stepDialog.activeStep = 0;
};

// 生成动态display计算属性（主表）
const displayComputed = computed(() => {
  return mainColumns.value
    .filter((col) => col.htmlType === 'popup')
    .reduce(
      (acc, col) => {
        const displayKey = `display${col.javaField}`;
        acc[displayKey] = col.dictType
          ? relationDict.value[col.dictType][(form.value as any)[col.javaField]]
          : (form.value as any)[`${col.javaField}Name`];
        return acc;
      },
      {} as Record<string, string>
    );
});

/** 修改按钮操作 */
const handleUpdate = async (row?: NformVO) => {
  const _formId = row?.formId || ids.value[0];
  try {
    const res = await getNform(_formId);
    currentFormData.value = res.data;
    designerTitle.value = '修改表单';
    designerVisible.value = true;
  } catch (error) {
    console.error('获取表单数据失败:', error);
    proxy?.$modal.msgError('获取表单数据失败');
  }
};

/** 处理表单设计器保存 */
const handleDesignerSave = async (formData: any) => {
  try {
    // 确保表单数据包含必要的字段
    if (!formData.formName || !formData.formDesc) {
      throw new Error('表单名称和描述不能为空');
    }

    if (formData.formId) {
      // 编辑模式
      const res = await updateNform(formData);
      if (res.code === 200) {
        proxy?.$modal.msgSuccess('修改成功');
      } else {
        throw new Error(res.msg || '更新表单失败');
      }
    } else {
      // 新建模式
      const res = await addNform(formData);
      if (res.code === 200) {
        proxy?.$modal.msgSuccess('新增成功');
      } else {
        throw new Error(res.msg || '新增表单失败');
      }
    }
    designerVisible.value = false;
    getList();
  } catch (error) {
    console.error('保存表单失败:', error);
    proxy?.$modal.msgError('保存表单失败: ' + (error as Error).message);
  }
};

/** 处理表单设计器关闭 */
const handleDesignerClose = () => {
  designerVisible.value = false;
};

/** 删除按钮操作 */
const handleDelete = async (row?: NformVO) => {
  const _formIds = row?.formId || ids.value;
  await proxy?.$modal.confirm('是否确认删除设计器单编号为"' + _formIds + '"的数据项？').finally(() => (loading.value = false));
  await delNform(_formIds);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

/** 导出按钮操作 */
const handleExport = () => {
  proxy?.download(
    'demo/nform/export',
    {
      ...queryParams.value
    },
    `nform_${new Date().getTime()}.xlsx`
  );
};

const handleDetail = (row: NformVO) => {
  currentForm.value = { ...row };
  detailVisible.value = true;
};

const handleTest = (row: NformVO) => {
  // 跳转到同目录下的test.vue页面
  router
    .push({
      path: '/workflow/nform/test',
      query: {
        formId: row.formId,
        formName: row.formName
      }
    })
    .then(() => {})
    .catch((error) => {
      console.error('跳转到测试页面失败:', error);
      proxy?.$modal.msgError('跳转失败');
    });
};

// 生成序列号
const generateSequence = (field: string): string => {
  const timestamp = Date.now().toString();
  const random = Math.floor(Math.random() * 1000)
    .toString()
    .padStart(3, '0');
  return `${field}_${timestamp}_${random}`;
};

onMounted(() => {
  getColumns();
  getList();
});

// 监听路由变化，确保每次进入页面时都能正确初始化
watch(
  () => route.path,
  (newPath, oldPath) => {
    if (newPath === '/demo/nform') {
      // 延迟执行，确保组件完全挂载
      nextTick(() => {
        getColumns();
        getList();
      });
    }
  },
  { immediate: true }
);

// 当 radio 选择变化时，同步选中表对象
watch(selectedTableName, (name) => {
  if (!name) return;
  const hit = tableList.value.find((t) => t.tableName === name);
  if (hit) {
    selectedTable.value = hit;
  }
});

/** 提交按钮 */
const submitForm = async () => {
  nformFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      buttonLoading.value = true;
      try {
        if ((form.value as any).formId) {
          await updateNform(form.value);
          proxy?.$modal.msgSuccess('修改成功');
        } else {
          await addNform(form.value);
          proxy?.$modal.msgSuccess('新增成功');
        }
        dialog.visible = false;
        getList();
      } catch (error) {
        console.error('提交表单失败:', error);
      } finally {
        buttonLoading.value = false;
      }
    }
  });
};

// 修改 getMainSelectedValues 方法
const getMainSelectedValues = (column: any): any[] => {
  if (!form.value) return [];
  const mapping = getMainMapping(column);
  const isMultiple = getPopupMultipleFromForm(column);

  if (isMultiple) {
    // 多选模式：收集所有相关字段的值
    const values: any[] = [];
    const baseFields = Object.values(mapping).filter((v) => typeof v === 'string') as string[];

    // 查找主字段和后续字段（如 field1, field2 等）
    baseFields.forEach((baseField) => {
      const mainValue = (form.value as Record<string, unknown>)[baseField];
      if (mainValue !== undefined) {
        values.push(mainValue);
      }

      // 查找后续字段
      let index = 1;
      while (true) {
        const nextField = `${baseField}${index + 1}`;
        const nextValue = (form.value as Record<string, unknown>)[nextField];
        if (nextValue !== undefined) {
          values.push(nextValue);
          index++;
        } else {
          break;
        }
      }
    });

    return values;
  } else {
    // 单选模式：返回单个值数组
    const result: any[] = [];
    Object.entries(mapping).forEach(([source, target]) => {
      if (typeof target === 'string' && (form.value as Record<string, unknown>)[target] !== undefined) {
        result.push((form.value as Record<string, unknown>)[target]);
      }
    });
    return result;
  }
};

/** 处理字段类型 */
const processColumn = (column: any) => {
  // 打印原始字段数据

  const processedColumn = {
    ...column,
    columnName: column.columnName || '',
    columnType: column.columnType || '',
    columnComment: column.columnComment || '',
    isNullable: column.isNullable || 'NO',
    isPk: column.isPk || 'NO',
    isList: column.isList || false,
    isQuery: column.isQuery || false,
    insert: column.insert || false,
    edit: column.edit || false,
    // 添加额外的字段属性
    isMaster: true,
    masterField: '',
    subTableName: '',
    subTableColumns: []
  };

  // 打印处理后的字段数据

  return processedColumn;
};

/** 初始化字段状态 */
const initFieldStates = (table: any) => {
  if (!table) return;

  // 初始化主表字段状态
  if (table.columnsVo) {
    table.columnsVo.forEach((column: any) => {
      selectedFields.value[column.columnName] = true;
    });
  }

  // 初始化子表字段状态
  if (table.subColumnsVo) {
    table.subColumnsVo.forEach((column: any) => {
      selectedFields.value[column.columnName] = true;
    });
  }
};

/** 处理字段选择 */
const handleFieldSelect = (field: string, isSubTable: boolean = false) => {
  if (isSubTable) {
    selectedFields.value[field] = !selectedFields.value[field];
  } else {
    selectedFields.value[field] = !selectedFields.value[field];
  }
};
</script>

<style scoped>
.form-designer-dialog {
  max-width: 100%;
}
.form-detail {
  padding: 20px;
}
.form-info {
  margin-bottom: 20px;
}
.form-preview {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 20px;
}
</style>
