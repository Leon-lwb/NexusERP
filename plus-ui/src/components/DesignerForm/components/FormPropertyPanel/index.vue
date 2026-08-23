<template>
  <div class="property-panel">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="字段属性" name="field">
        <!-- 字段属性配置 -->
        <el-form v-if="selectedComponent" label-width="100px" size="small">
          <el-form-item label="组件类型">
            <el-tag size="small" type="info">{{ localComponent.type }}</el-tag>
            <el-tag v-if="localComponent.dictType" size="small" type="success" style="margin-left: 5px"> 字典: {{ localComponent.dictType }} </el-tag>
          </el-form-item>
          <el-form-item label="组件ID">
            <el-input v-model="localComponent.id" />
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="localComponent.label" @input="updateComponent" />
          </el-form-item>
          <el-form-item label="字段名">
            <el-input v-model="localComponent.field" @input="updateComponent" />
          </el-form-item>
          <el-form-item label="必填">
            <el-switch v-model="localComponent.required" @change="updateComponent" />
          </el-form-item>
          <el-form-item label="占位符">
            <el-input v-model="localComponent.props!.placeholder" @input="updateComponent" />
          </el-form-item>
          <el-form-item label="默认值">
            <template v-if="localComponent.type === 'NcColorPicker'">
              <el-color-picker v-model="localComponent.props!.defaultValue" @change="updateComponent" />
            </template>
            <template v-else>
              <el-input v-model="localComponent.props!.defaultValue" @input="updateComponent" />
            </template>
          </el-form-item>
          <el-form-item label="禁用">
            <el-switch v-model="localComponent.props!.disabled" @change="updateComponent" />
          </el-form-item>
          <el-form-item label="可清空">
            <el-switch v-model="localComponent.props!.clearable" @change="updateComponent" />
          </el-form-item>
          <el-form-item label="数据表">
            <el-select v-model="localComponent.props!.dbField!.tableId" placeholder="请选择数据表" @change="handleTableChange">
              <el-option v-for="table in tables" :key="table.tableId" :label="table.tableComment" :value="table.tableId" />
            </el-select>
          </el-form-item>
          <el-form-item label="字段">
            <el-select v-model="localComponent.props!.dbField!.fieldName" placeholder="请选择字段" @change="handleFieldChange">
              <el-option v-for="column in currentTableColumns" :key="column.columnId" :label="column.columnComment" :value="column.columnName" />
            </el-select>
          </el-form-item>
          <el-form-item label="字段类型">
            <el-input v-model="localComponent.props!.dbField!.fieldType" disabled />
          </el-form-item>
          <el-form-item label="字段说明">
            <el-input v-model="localComponent.props!.dbField!.fieldComment" disabled />
          </el-form-item>
          <el-form-item label="是否必填">
            <el-switch v-model="localComponent.props!.dbField!.isRequired" disabled />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="localComponent.props!.dbField!.defaultValue" @input="updateComponent" />
          </el-form-item>
          <el-form-item label="验证规则">
            <el-select v-model="localComponent.props!.rules" multiple placeholder="请选择验证规则" @change="updateComponent">
              <el-option label="必填" value="required" />
              <el-option label="邮箱" value="email" />
              <el-option label="手机号" value="phone" />
              <el-option label="数字" value="number" />
              <el-option label="整数" value="integer" />
              <el-option label="URL" value="url" />
            </el-select>
          </el-form-item>
          <el-form-item label="自定义验证">
            <el-input
              v-model="localComponent.props!.customValidator"
              type="textarea"
              :rows="3"
              placeholder="请输入自定义验证函数"
              @input="updateComponent"
            />
          </el-form-item>
        </el-form>
        <div v-else class="empty-tip">请选择组件</div>
      </el-tab-pane>
      <el-tab-pane label="表单属性" name="form">
        <el-form label-width="100px" size="small">
          <el-form-item label="表单名称">
            <el-input v-model="formConfig.formName" placeholder="请输入表单名称" @change="updateFormConfig" />
          </el-form-item>
          <el-form-item label="表单描述">
            <el-input v-model="formConfig.formDesc" type="textarea" placeholder="请输入表单描述" @change="updateFormConfig" />
          </el-form-item>
          <el-form-item label="标签宽度">
            <el-input v-model="formConfig.labelWidth" placeholder="请输入标签宽度" @change="updateFormConfig" />
          </el-form-item>
          <el-form-item label="标签位置">
            <el-select v-model="formConfig.labelPosition" placeholder="请选择标签位置" @change="updateFormConfig">
              <el-option label="左对齐" value="left" />
              <el-option label="右对齐" value="right" />
              <el-option label="顶部对齐" value="top" />
            </el-select>
          </el-form-item>
          <el-form-item label="表单尺寸">
            <el-select v-model="formConfig.size" placeholder="请选择表单尺寸" @change="updateFormConfig">
              <el-option label="大" value="large" />
              <el-option label="默认" value="default" />
              <el-option label="小" value="small" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="字典配置" name="dict">
        <el-form label-width="100px" size="small">
          <el-form-item label="字典类型">
            <el-select v-model="localComponent.dictType" placeholder="请选择字典类型" clearable @change="handleDictTypeChange">
              <el-option v-for="item in dictTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="字典字段">
            <el-input
              v-model="localComponent.dictFields"
              type="textarea"
              :rows="3"
              placeholder='请输入字典字段映射，格式：{"dictValue":"value","dictLabel":"label"}'
              @change="handleDictFieldsChange"
            />
          </el-form-item>
          <el-form-item label="字段映射">
            <el-input
              v-model="localComponent.mapping"
              type="textarea"
              :rows="3"
              placeholder='请输入字段映射，格式：{"field":"value","fieldName":"label"}'
              @change="handleMappingChange"
            />
          </el-form-item>
          <el-form-item label="显示标签">
            <el-switch v-model="localComponent.showDisplay" @change="updateComponent" />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="联动规则" name="rules">
        <el-form label-width="100px" size="small">
          <el-form-item label="字段联动">
            <el-input
              v-model="localComponent.onChange"
              type="textarea"
              :rows="3"
              placeholder='请输入联动规则，格式：{"onChange":{"field":"targetField","value":"targetValue"}}'
              @change="handleOnChange"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue';
import type { FormComponent, Table, TableColumn, FormConfig } from '../../types';
import { ElMessage } from 'element-plus';

const props = defineProps({
  selectedComponent: {
    type: Object as PropType<FormComponent | null>,
    default: null
  },
  tables: {
    type: Array as PropType<Table[]>,
    default: () => []
  },
  currentTableColumns: {
    type: Array as PropType<TableColumn[]>,
    default: () => []
  },
  formConfig: {
    type: Object as PropType<FormConfig>,
    required: true
  }
});

const emit = defineEmits(['update:selectedComponent', 'update:formConfig', 'columnChange']);

const activeTab = ref('field');
const localComponent = ref<FormComponent>({
  id: '',
  type: '',
  label: '',
  field: '',
  required: false,
  props: {
    placeholder: '',
    defaultValue: '',
    disabled: false,
    clearable: false,
    rules: [],
    customValidator: '',
    labelWidth: '100px',
    labelPosition: 'right',
    size: 'default',
    dbField: {
      tableId: 0,
      tableName: '',
      fieldName: '',
      fieldType: '',
      fieldComment: '',
      isRequired: false,
      defaultValue: ''
    }
  }
});

// 表单配置
const formConfig = ref<FormConfig>({
  formName: '',
  formDesc: '',
  labelWidth: '100px',
  labelPosition: 'right',
  size: 'default'
});

// 添加字典类型选项
const dictTypeOptions = ref([
  { label: '通知类型', value: 'sys_notice_type' },
  { label: '状态', value: 'sys_normal_disable' },
  { label: '性别', value: 'sys_user_sex' },
  { label: '菜单类型', value: 'sys_menu_type' },
  { label: '是否', value: 'sys_yes_no' },
  { label: '任务状态', value: 'sys_job_status' },
  { label: '任务组名', value: 'sys_job_group' },
  { label: '操作类型', value: 'sys_oper_type' },
  { label: '通用状态', value: 'sys_common_status' }
]);

// 监听组件变化，更新本地数据
watch(
  () => props.selectedComponent,
  (newVal) => {
    if (newVal) {
      // 确保保留所有必要的属性结构
      const updatedComponent: FormComponent = {
        id: newVal.id,
        type: newVal.type,
        label: newVal.label || '',
        field: newVal.field || '',
        required: newVal.required || false,
        props: {
          placeholder: newVal.props?.placeholder || '',
          defaultValue: newVal.props?.defaultValue || '',
          disabled: newVal.props?.disabled || false,
          clearable: newVal.props?.clearable || false,
          rules: newVal.props?.rules || [],
          customValidator: newVal.props?.customValidator || '',
          labelWidth: newVal.props?.labelWidth || '100px',
          labelPosition: newVal.props?.labelPosition || 'right',
          size: newVal.props?.size || 'default',
          dbField: newVal.props?.dbField || {
            tableId: 0,
            tableName: '',
            fieldName: '',
            fieldType: '',
            fieldComment: '',
            isRequired: false,
            defaultValue: ''
          }
        }
      };
      localComponent.value = updatedComponent;
    } else {
      // 如果没有选中组件，重置为初始状态
      localComponent.value = {
        id: '',
        type: '',
        label: '',
        field: '',
        required: false,
        props: {
          placeholder: '',
          defaultValue: '',
          disabled: false,
          clearable: false,
          rules: [],
          customValidator: '',
          labelWidth: '100px',
          labelPosition: 'right',
          size: 'default',
          dbField: {
            tableId: 0,
            tableName: '',
            fieldName: '',
            fieldType: '',
            fieldComment: '',
            isRequired: false,
            defaultValue: ''
          }
        }
      };
    }
  },
  { immediate: true }
);

// 监听props.formConfig的变化
watch(
  () => props.formConfig,
  (newVal) => {
    if (newVal) {
      formConfig.value = { ...newVal };
    }
  },
  { immediate: true }
);

// 更新组件
const updateComponent = () => {
  // 确保更新时保留所有属性
  emit('update:selectedComponent', JSON.parse(JSON.stringify(localComponent.value)));
};

// 更新表单配置
const updateFormConfig = () => {
  emit('update:formConfig', formConfig.value);
};

// 处理数据表变更
const handleTableChange = (tableId: number) => {
  const table = props.tables.find((t) => t.tableId === tableId);
  if (table && localComponent.value.props) {
    if (!localComponent.value.props.dbField) {
      localComponent.value.props.dbField = {
        tableId: 0,
        tableName: '',
        fieldName: '',
        fieldType: '',
        fieldComment: '',
        isRequired: false,
        defaultValue: ''
      };
    }
    const currentDbField = localComponent.value.props.dbField;
    currentDbField.tableId = tableId;
    currentDbField.tableName = table.tableName;
    currentDbField.fieldName = '';
    currentDbField.fieldType = '';
    currentDbField.fieldComment = '';
    currentDbField.isRequired = false;
    currentDbField.defaultValue = '';
    // 调用columnChange事件，触发父组件获取表字段列表
    emit('columnChange', table.tableName);
  }
};

// 处理字段变更
const handleFieldChange = (fieldName: string) => {
  const column = props.currentTableColumns.find((col) => col.columnName === fieldName);
  if (column && localComponent.value.props) {
    if (!localComponent.value.props.dbField) {
      localComponent.value.props.dbField = {
        tableId: 0,
        tableName: '',
        fieldName: '',
        fieldType: '',
        fieldComment: '',
        isRequired: false,
        defaultValue: ''
      };
    }
    const currentDbField = localComponent.value.props.dbField;
    currentDbField.fieldName = fieldName;
    currentDbField.fieldType = column.columnType;
    currentDbField.fieldComment = column.columnComment;
    currentDbField.isRequired = column.isRequired === '1';
    currentDbField.defaultValue = '';

    // 同步更新组件的标签和字段名
    localComponent.value.label = column.columnComment || fieldName;
    localComponent.value.field = fieldName;

    updateComponent();
  }
};

// 处理字典类型变化
const handleDictTypeChange = (value: string) => {
  if (value) {
    // 如果选择了字典类型，自动设置为 select 类型
    localComponent.value.type = 'select';
    // 设置默认的字典字段映射
    if (!localComponent.value.dictFields) {
      localComponent.value.dictFields = JSON.stringify({
        dictValue: localComponent.value.field,
        dictLabel: `${localComponent.value.field}Name`
      });
    }
    // 设置默认的字段映射
    if (!localComponent.value.mapping) {
      localComponent.value.mapping = JSON.stringify({
        [localComponent.value.field]: localComponent.value.field,
        [`${localComponent.value.field}Name`]: `${localComponent.value.field}Name`
      });
    }
  }
  updateComponent();
};

// 处理字典字段变化
const handleDictFieldsChange = () => {
  try {
    // 验证 JSON 格式
    JSON.parse(localComponent.value.dictFields || '{}');
    updateComponent();
  } catch (error) {
    ElMessage.error('字典字段映射格式不正确');
  }
};

// 处理字段映射变化
const handleMappingChange = () => {
  try {
    // 验证 JSON 格式
    JSON.parse(localComponent.value.mapping || '{}');
    updateComponent();
  } catch (error) {
    ElMessage.error('字段映射格式不正确');
  }
};

// 处理联动规则变化
const handleOnChange = () => {
  try {
    // 验证 JSON 格式
    JSON.parse(localComponent.value.onChange || '{}');
    updateComponent();
  } catch (error) {
    ElMessage.error('联动规则格式不正确');
  }
};
</script>

<style lang="scss" scoped>
.property-panel {
  height: 100%;
  overflow-y: auto;
  padding: 16px;

  .empty-tip {
    text-align: center;
    color: #909399;
    padding: 20px 0;
  }

  .el-form {
    .el-form-item {
      margin-bottom: 16px;
    }
  }
}
</style>
