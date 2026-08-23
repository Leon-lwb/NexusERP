<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ formTitle }}</span>
          <el-button-group>
            <el-button type="primary" @click="handleDesign">设计表单</el-button>
            <el-button type="success" @click="handlePreview">预览表单</el-button>
            <el-button type="warning" @click="handleSave">保存数据</el-button>
          </el-button-group>
        </div>
      </template>

      <!-- 表单设计器对话框 -->
      <el-dialog
        v-model="designerVisible"
        title="表单设计器"
        width="90%"
        fullscreen
        append-to-body
        :close-on-click-modal="false"
        :close-on-press-escape="false"
      >
        <form-designer ref="designerRef" :form-data="currentFormData" @save="handleDesignerSave" @close="handleDesignerClose" />
      </el-dialog>

      <!-- 表单预览对话框 -->
      <el-dialog v-model="previewVisible" title="表单预览" width="60%" append-to-body :close-on-click-modal="false">
        <FormPreview
          :form-content="currentFormContent"
          :model-value="previewVisible"
          @close="previewVisible = false"
          @update:model-value="previewVisible = $event"
        />
      </el-dialog>

      <!-- 表单录入区域 -->
      <div v-if="currentFormContent" class="form-container">
        <el-form :model="formData" :label-width="formConfig.labelWidth">
          <div v-for="(item, index) in formComponents" :key="index">
            <el-form-item :label="item.label" :required="item.required">
              <template v-if="item.type === 'input'">
                <el-input
                  v-model="formData[item.field]"
                  v-bind="item.props"
                  @input="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                />
              </template>
              <template v-else-if="item.type === 'textarea'">
                <el-input
                  type="textarea"
                  v-model="formData[item.field]"
                  v-bind="item.props"
                  @input="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                />
              </template>
              <template v-else-if="item.type === 'editor'">
                <rich-editor
                  v-model="formData[item.field]"
                  v-bind="item.props"
                  @update:modelValue="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                />
              </template>
              <template v-else-if="item.type === 'select'">
                <el-select
                  v-model="formData[item.field]"
                  v-bind="item.props"
                  :placeholder="item.props.placeholder || '请选择'"
                  @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                >
                  <el-option
                    v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </template>
              <template v-else-if="item.type === 'checkbox'">
                <el-checkbox
                  v-model="formData[item.field]"
                  v-bind="item.props"
                  @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                />
              </template>
              <template v-else-if="item.type === 'radio'">
                <el-radio-group
                  v-model="formData[item.field]"
                  v-bind="item.props"
                  @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                >
                  <el-radio
                    v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                    :key="option.value"
                    :label="option.value"
                  >
                    {{ option.label }}
                  </el-radio>
                </el-radio-group>
              </template>
              <template v-else-if="item.type === 'subtable'">
                <el-table :data="subTableData" border style="width: 100%">
                  <el-table-column
                    v-for="(column, colIndex) in item.subTableColumns"
                    :key="colIndex"
                    :prop="column.field as string"
                    :label="column.label"
                  >
                    <template #default="scope">
                      <template v-if="column.type === 'popup'">
                        <PopupTable
                          :modelValue="scope.row[String(column.field)]"
                          :nm="column"
                          :dictType="column.dictType || column.props.dictType"
                          :dictFields="column.props.dictFields || {}"
                          :mapping="column.props.mapping || {}"
                          :multiple="getMultipleMode(column.props)"
                          :displayMode="getDisplayMode(column.props)"
                          @common-component="(data) => handleSubTablePopupSelect(data, column, scope.row, item)"
                        />
                      </template>
                      <component
                        v-else
                        :is="getComponentByType(column.type)"
                        v-model="scope.row[String(column.field)]"
                        v-bind="getFilteredProps(column.props)"
                        @input="(value: any) => handleFieldLinkage(String(column.field), value, 'sub', scope.$index)"
                        @change="(value: any) => handleFieldLinkage(String(column.field), value, 'sub', scope.$index)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="150">
                    <template #default="scope">
                      <el-button size="small" type="danger" @click="handleDeleteSubTableRow(item, scope.$index)"> 删除 </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <div class="subtable-actions">
                  <el-button type="primary" size="small" @click="handleAddSubTableRow(item)"> 添加行 </el-button>
                </div>
              </template>
              <template v-else-if="item.type === 'dept-select'">
                <el-tree-select
                  v-model="formData[item.field]"
                  :data="deptOptions"
                  :props="{ label: 'deptName', value: 'deptId' } as any"
                  :placeholder="item.props.placeholder || '请选择部门'"
                  :multiple="item.props.multiple"
                  :clearable="item.props.clearable"
                  @change="(val: any) => handleDeptChange(val, item.field)"
                />
              </template>
              <template v-else-if="item.type === 'user-select'">
                <el-select
                  v-model="formData[item.field]"
                  :placeholder="item.props.placeholder || '请选择人员'"
                  :multiple="item.props.multiple"
                  :clearable="item.props.clearable"
                  @change="(val: any) => handleUserChange(val, item.field)"
                >
                  <el-option v-for="user in userOptions" :key="user.userId" :label="user.nickName" :value="user.userId" />
                </el-select>
              </template>
              <template v-else-if="item.type === 'popup'">
                <PopupTable
                  v-model="formData[item.field as string]"
                  :nm="item"
                  :dictType="item.dictType || item.props.dictType"
                  :dictFields="item.props.dictFields || {}"
                  :mapping="item.props.mapping || {}"
                  displayMode="label"
                  @common-component="(data) => handlePopupSelect(data, item)"
                />
              </template>
              <template v-else>
                <component
                  :is="getComponentByType(item.type)"
                  v-model="formData[item.field]"
                  v-bind="item.props"
                  @input="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                  @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
                />
              </template>
            </el-form-item>
          </div>
        </el-form>

        <!-- 表单操作按钮 -->
        <div class="form-actions">
          <el-button type="primary" @click="handleSubmit">提交表单</el-button>
          <el-button @click="handleReset">重置表单</el-button>
        </div>
      </div>
      <div v-else class="empty-tip">
        <el-empty description="请先设计或加载表单" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import FormDesigner from '@/components/DesignerForm/FormDesigner.vue';
import FormPreview from '@/components/DesignerForm/FormPreview.vue';
import RichEditor from '@/components/DesignerForm/components/RichEditor.vue';
import PopupTable from '@/components/DesignerForm/components/PopupTable.vue';
import { getNform } from '@/api/workflow/nform';
import { listOnlineData, getOnlineData, delOnlineData, addOrUpdateOnlineDataByName } from '@/api/online';
import { getDicts } from '@/api/system/dict/data';
import { listDept } from '@/api/system/dept';
import { listUser } from '@/api/system/user';
import type { DictDataVO } from '@/api/system/dict/data/types';
import type { UserVO } from '@/api/system/user/types';
import type { DeptVO } from '@/api/system/dept/types';
import type { UserQuery } from '@/api/system/user/types';

// 定义表单组件类型
interface FormComponent {
  id: string;
  type: string;
  label: string;
  field: string;
  props: Record<string, any>;
  required?: boolean;
  subTableColumns?: any[];
  dictType?: string;
  subTableName?: string;
  subTableFkName?: string;
}

// 定义表单内容类型
interface FormContent {
  formComponents: FormComponent[];
  formConfig: {
    labelWidth: string;
    labelPosition: string;
    size: string;
    formName: string;
    formDesc: string;
  };
  masterSubTableData?: any;
}

const route = useRoute();

// 对话框显示状态
const designerVisible = ref(false);
const previewVisible = ref(false);

// 表单标题
const formTitle = ref('表单设计器测试');

// 当前表单数据
const currentFormData = ref({
  formName: '',
  formDesc: '',
  formContent: ''
});

// 当前表单内容
const currentFormContent = ref('');

// 表单配置
const formConfig = reactive({
  labelWidth: '120px',
  labelPosition: 'right',
  size: 'default',
  formName: '',
  formDesc: ''
});

// 表单组件
const formComponents = ref<FormComponent[]>([]);

// 表单数据
const formData = ref<Record<string, any>>({});

// 子表数据（直接使用数组，与online/test保持一致）
const subTableData = ref<any[]>([]);
// 子表配置（表名、外键名等配置）
const masterSubConfig = ref<any>(null);

// 字典数据
const dictOptions = ref<Record<string, any[]>>({});

// 部门数据
const deptOptions = ref<DeptVO[]>([]);

// 用户数据
const userOptions = ref<UserVO[]>([]);

// 设计器引用
const designerRef = ref();

// 格式化时间戳
const formatTimestamp = (date: Date, format: string) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  const milliseconds = String(date.getMilliseconds()).padStart(3, '0');

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
    .replace('SSS', milliseconds);
};

// 生成编码
const generateCode = (rule: string, params?: any) => {
  let result = rule;

  // 替换时间戳
  if (rule.includes('{timestamp}')) {
    const format = params?.format || 'YYYYMMDDHHmmssSSS';
    const timestamp = formatTimestamp(new Date(), format);
    result = result.replace('{timestamp}', timestamp);
  }

  // 替换日期
  if (rule.includes('{date}')) {
    const date = new Date().toISOString().split('T')[0].replace(/-/g, '');
    result = result.replace('{date}', date);
  }

  // 替换时间
  if (rule.includes('{time}')) {
    const time = new Date().toTimeString().split(' ')[0].replace(/:/g, '');
    result = result.replace('{time}', time);
  }

  // 替换随机数
  if (rule.includes('{random}')) {
    const random = Math.floor(Math.random() * 10000)
      .toString()
      .padStart(4, '0');
    result = result.replace('{random}', random);
  }

  return result;
};

// 解析表单内容
const parseFormContent = async () => {
  try {
    const content = JSON.parse(currentFormContent.value) as FormContent;

    if (content.formConfig) {
      Object.assign(formConfig, content.formConfig);
      formTitle.value = content.formConfig.formName || '表单设计器测试';
    }

    if (content.formComponents) {
      formComponents.value = content.formComponents;
      // 初始化表单数据
      formComponents.value.forEach((component) => {
        if (component.type === 'subtable') {
          subTableData.value[component.field] = [];
        } else if (component.type === 'checkbox') {
          formData.value[component.field] = [];
        } else if (component.type === 'dept-select' || component.type === 'user-select') {
          formData.value[component.field] = component.props.multiple ? [] : '';
        } else {
          formData.value[component.field] = '';

          // 检查是否有编码生成规则
          if (component.props?.onCodeGenerate) {
            const codeConfig = component.props.onCodeGenerate;
            if (codeConfig.autoGenerateRule) {
              const generatedCode = generateCode(codeConfig.autoGenerateRule, codeConfig.generateParams);
              formData.value[component.field] = generatedCode;
            }
            // 处理完编码生成后，从 props 中移除 onCodeGenerate，避免传递给组件
            delete component.props.onCodeGenerate;
          }
        }
      });

      // 收集所有需要加载的字典类型
      const dictTypes = new Set<string>();
      formComponents.value.forEach((component) => {
        if (['select', 'radio', 'checkbox'].includes(component.type) && component.props.dataSource === 'dict' && component.props.dictType) {
          dictTypes.add(component.props.dictType);
        }
      });

      // 并行加载所有字典数据
      if (dictTypes.size > 0) {
        await Promise.all(Array.from(dictTypes).map((dictType) => loadDictData(dictType)));
      }
    }

    // 检查是否有主子表结构
    if (content.masterSubTableData) {
      // 分析masterSubTableData的结构
      if (typeof content.masterSubTableData === 'object' && content.masterSubTableData !== null) {
        // 如果masterSubTableData是对象，尝试从中提取子表信息
        const keys = Object.keys(content.masterSubTableData);
        if (keys.length > 0) {
          // 第一个key通常是子表名
          const subTableName = keys[0];

          masterSubConfig.value = {
            subTableName: subTableName,
            subTableFkName: 'main_id' // 默认外键名，可以根据实际情况调整
          };
        }
      } else if (content.masterSubTableData.subTableName) {
        // 如果masterSubTableData有标准的subTableName字段
        masterSubConfig.value = {
          subTableName: content.masterSubTableData.subTableName,
          subTableFkName: content.masterSubTableData.subTableFkName
        };
      }
    } else {
      // 尝试从formComponents中查找子表信息
      const subTableComponent = content.formComponents?.find((c) => c.type === 'subtable');
      if (subTableComponent) {
        masterSubConfig.value = {
          subTableName: subTableComponent.subTableName || '',
          subTableFkName: subTableComponent.subTableFkName || ''
        };
      }
    }

    // 初始化子表数据数组
    formComponents.value.forEach((component) => {
      if (component.type === 'subtable') {
        // 初始化子表行容器为数组
        subTableData.value = [];
        // 绑定子表表名
        component.subTableName = masterSubConfig.value?.subTableName || '';
        // 设置子表列信息
        if (content.masterSubTableData?.subColumns) {
          component.subTableColumns = content.masterSubTableData.subColumns;
        } else if (component.subTableColumns) {
        } else {
          // 尝试从masterSubTableData中获取列信息
          if (content.masterSubTableData && typeof content.masterSubTableData === 'object') {
            const keys = Object.keys(content.masterSubTableData);
            if (keys.length > 0) {
              const subTableName = keys[0];
              const subTableData = content.masterSubTableData[subTableName];
              if (Array.isArray(subTableData) && subTableData.length > 0) {
                // 从第一行数据中提取列信息
                const firstRow = subTableData[0];
                const columns = Object.keys(firstRow)
                  .map((field) => {
                    // 过滤掉不需要的字段
                    if (
                      [
                        'id',
                        'main_id',
                        'tenant_id',
                        'create_by',
                        'create_dept',
                        'create_time',
                        'update_by',
                        'update_dept',
                        'update_time',
                        'del_flag'
                      ].includes(field)
                    ) {
                      return null;
                    }
                    return {
                      field: field,
                      label: field,
                      type: 'input'
                    };
                  })
                  .filter((col) => col !== null);
                component.subTableColumns = columns;
              }
            }
          }

          if (!component.subTableColumns) {
            console.warn('没有找到子表列信息');
          }
        }
      }
    });
  } catch (error) {
    console.error('解析表单内容失败:', error);
  }
};

// 获取组件类型
const getComponentByType = (type: string) => {
  switch (type) {
    case 'input':
      return 'el-input';
    case 'textarea':
      return 'el-input';
    case 'editor':
      return 'rich-editor';
    case 'select':
      return 'el-select';
    case 'checkbox':
      return 'el-checkbox';
    case 'radio':
      return 'el-radio';
    case 'date':
      return 'el-date-picker';
    case 'datetime':
      return 'el-date-picker';
    case 'time':
      return 'el-time-picker';
    case 'image-upload':
      return 'el-upload';
    case 'file-upload':
      return 'el-upload';
    case 'rate':
      return 'el-rate';
    case 'color-picker':
      return 'el-color-picker';
    case 'slider':
      return 'el-slider';
    case 'button':
      return 'el-button';
    case 'divider':
      return 'el-divider';
    case 'dept-select':
      return 'el-tree-select';
    case 'user-select':
      return 'el-select';
    case 'switch':
      return 'el-switch';
    case 'popup':
      return 'popup-table';
    default:
      return 'el-input';
  }
};

// 添加子表行
const handleAddSubTableRow = (component: FormComponent) => {
  const newRow: Record<string, any> = {};
  if (component.subTableColumns && Array.isArray(component.subTableColumns)) {
    component.subTableColumns.forEach((column) => {
      // 只添加业务字段，过滤掉系统字段
      if (
        !['id', 'main_id', 'tenant_id', 'create_by', 'create_dept', 'create_time', 'update_by', 'update_dept', 'update_time', 'del_flag'].includes(
          column.field
        )
      ) {
        newRow[column.field] = '';
      }
    });
  } else {
    console.warn('子表列信息不存在:', component.subTableColumns);
  }

  subTableData.value.push(newRow);
};

// 删除子表行
const handleDeleteSubTableRow = (component: FormComponent, index: number) => {
  subTableData.value.splice(index, 1);
};

// 加载字典数据
const loadDictData = async (dictType: string) => {
  if (!dictType) return;
  try {
    const res = await getDicts(dictType);
    if (res.code === 200) {
      const options = (res.data as DictDataVO[]).map((item) => ({
        label: item.dictLabel,
        value: item.dictValue
      }));
      dictOptions.value[dictType] = options;
    }
  } catch (error) {
    console.error('获取字典数据失败:', error);
  }
};

// 加载部门数据
const loadDeptOptions = async () => {
  try {
    const res = await listDept();
    if (res.code === 200) {
      deptOptions.value = res.data as DeptVO[];
    }
  } catch (error) {
    console.error('获取部门列表失败:', error);
  }
};

// 加载用户数据
const loadUserOptions = async () => {
  try {
    const query: UserQuery = {
      pageNum: 1,
      pageSize: 100
    };
    const res = await listUser(query);
    if (res.code === 200) {
      userOptions.value = res.rows as UserVO[];
    }
  } catch (error) {
    console.error('获取用户列表失败:', error);
  }
};

// 部门选择变化处理
const handleDeptChange = (value: any, field: string) => {
  formData.value[field] = value;
};

// 用户选择变化处理
const handleUserChange = (value: any, field: string) => {
  formData.value[field] = value;
};

// 处理设计表单
const handleDesign = () => {
  designerVisible.value = true;
};

// 处理预览表单
const handlePreview = () => {
  if (!currentFormContent.value) {
    ElMessage.warning('请先设计表单');
    return;
  }
  previewVisible.value = true;
};

// 处理设计器保存
const handleDesignerSave = (formData: any) => {
  currentFormData.value = formData;
  currentFormContent.value = formData.formContent;
  designerVisible.value = false;
  ElMessage.success('表单保存成功');
  // 解析表单内容并加载字典数据
  parseFormContent();
};

// 处理设计器关闭
const handleDesignerClose = () => {
  designerVisible.value = false;
};

// 处理表单提交
const handleSubmit = async () => {
  try {
    const formId = route.query.formId as string;
    if (!formId) {
      ElMessage.error('表单ID不存在');
      return;
    }

    // 解析表单内容获取表名
    const formContent = JSON.parse(currentFormContent.value);

    // 尝试从不同位置获取表名
    let tableName = '';
    if (formContent.formConfig?.tableInfo?.mainTable?.tableName) {
      tableName = formContent.formConfig.tableInfo.mainTable.tableName;
    } else if (formContent.formComponents && formContent.formComponents.length > 0) {
      // 从第一个组件的dbInfo中获取表名
      const firstComponent = formContent.formComponents[0];
      if (firstComponent.dbInfo?.tableName) {
        tableName = firstComponent.dbInfo.tableName;
      }
    }

    if (!tableName) {
      ElMessage.error('无法获取表名信息');
      return;
    }

    // 构建提交数据格式
    let submitData: any = {};

    // 检查是否有子表数据（必须同时满足：子表表名存在且有行数据）
    const subTableComponent = formComponents.value.find((c) => c.type === 'subtable');
    const subTableName = subTableComponent?.subTableName || masterSubConfig.value?.subTableName || '';

    if (subTableName && Array.isArray(subTableData.value) && subTableData.value.length > 0) {
      // 主子表结构 - 需要清理子表数据，只保留有效的业务字段
      const cleanedSubTableData = subTableData.value.map((row) => {
        const cleanedRow: Record<string, any> = {};
        // 只保留业务字段，过滤掉系统字段和驼峰格式字段
        Object.keys(row).forEach((key) => {
          // 过滤掉系统字段
          if (
            ['id', 'main_id', 'tenant_id', 'create_by', 'create_dept', 'create_time', 'update_by', 'update_dept', 'update_time', 'del_flag'].includes(
              key
            )
          ) {
            return;
          }

          // 过滤掉驼峰格式字段，只保留下划线格式字段
          if (key.includes('_') || /^[a-z]+$/.test(key)) {
            // 下划线格式字段（如 good_code, good_name）或全小写字段（如 price, num）
            cleanedRow[key] = row[key];
          }
          // 驼峰格式字段（如 goodCode, goodName）会被过滤掉
        });
        return cleanedRow;
      });

      submitData = {
        main: JSON.parse(JSON.stringify(formData.value)),
        sub: {
          tableName: subTableName,
          data: cleanedSubTableData
        }
      };
    } else {
      // 单表结构
      submitData = JSON.parse(JSON.stringify(formData.value));
    }

    // 清理空字符串的日期字段，将其设置为 null
    const cleanEmptyDateFields = (data: any) => {
      const cleaned = { ...data };
      Object.keys(cleaned).forEach((key) => {
        if (cleaned[key] === '' && (key.includes('date') || key.includes('time'))) {
          cleaned[key] = null;
        }
      });
      return cleaned;
    };

    // 清理主表数据中的空日期字段
    if (submitData.main) {
      submitData.main = cleanEmptyDateFields(submitData.main);
    } else {
      submitData = cleanEmptyDateFields(submitData);
    }

    const res = await addOrUpdateOnlineDataByName(tableName, submitData);
    if (res.code === 200) {
      ElMessage.success('表单提交成功');
      // 清空表单数据
      formData.value = {};
      subTableData.value = [];
      // 重新初始化表单
      parseFormContent();
    } else {
      ElMessage.error(res.msg || '表单提交失败');
    }
  } catch (error) {
    console.error('提交表单失败:', error);
    ElMessage.error('提交表单失败');
  }
};

// 处理保存数据
const handleSave = async () => {
  await handleSubmit();
};

// 处理重置表单
const handleReset = () => {
  formData.value = {};
  subTableData.value = [];
  parseFormContent();
  ElMessage.success('表单已重置');
};

// 获取显示模式
const getDisplayMode = (props: any): 'label' | 'value' => {
  try {
    const mapping = typeof props.mapping === 'string' ? JSON.parse(props.mapping) : props.mapping;

    if (mapping && mapping.popupConfig && mapping.popupConfig.displayMode) {
      const mode = mapping.popupConfig.displayMode;
      if (mode === 'label') return 'label';
      if (mode === 'value') return 'value';
      if (mode === 'sub') return 'value';
    }

    return 'value';
  } catch (error) {
    console.error('解析显示模式失败:', error);
    return 'value';
  }
};

// 获取多选模式
const getMultipleMode = (props: any): boolean => {
  try {
    const mapping = typeof props.mapping === 'string' ? JSON.parse(props.mapping) : props.mapping;

    if (mapping && mapping.popupConfig && mapping.popupConfig.multiple !== undefined) {
      return mapping.popupConfig.multiple === true;
    }

    return false;
  } catch (error) {
    console.error('解析多选模式失败:', error);
    return false;
  }
};

// 获取列的显示模式
const getColumnDisplayMode = (column: any): 'label' | 'value' | 'sub' | null => {
  try {
    const mapping = typeof column.props.mapping === 'string' ? JSON.parse(column.props.mapping) : column.props.mapping;

    if (mapping && mapping.popupConfig && mapping.popupConfig.displayMode) {
      return mapping.popupConfig.displayMode;
    }

    return null; // 没有配置时返回 null，让原有逻辑处理
  } catch (error) {
    console.error('解析列显示模式失败:', error);
    return null;
  }
};

// 使用完整 mapping 更新行数据（sub 模式专用）
const updateRowDataWithFullMapping = (row: any, column: any, data: any) => {
  try {
    const mapping = typeof column.props.mapping === 'string' ? JSON.parse(column.props.mapping) : column.props.mapping;

    if (mapping && mapping.mapping) {
      // 使用 mapping.mapping 中的所有字段映射
      Object.entries(mapping.mapping).forEach(([srcField, targetField]) => {
        if (typeof targetField === 'string') {
          // 从 originalData 中获取源字段值
          let sourceValue = null;
          if (data.originalData && data.originalData[srcField] !== undefined) {
            sourceValue = data.originalData[srcField];
          } else if (data[srcField] !== undefined) {
            sourceValue = data[srcField];
          }

          if (sourceValue !== null) {
            row[targetField] = sourceValue;
            // 同时设置下划线版本
            const underscoreField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase();
            if (underscoreField !== targetField) {
              row[underscoreField] = sourceValue;
            }
          } else {
          }
        }
      });
    } else {
    }
  } catch (error) {
    console.error('sub模式更新行数据失败:', error);
  }
};

// 处理popup选择
const handlePopupSelect = (data: any, item: FormComponent) => {
  if (data && data.value !== undefined) {
    const field = String(item.field);
    formData.value[field] = data.value;

    // 如果有映射字段，也更新映射字段的值
    if (item.props.mapping) {
      try {
        const mapping = typeof item.props.mapping === 'string' ? JSON.parse(item.props.mapping) : item.props.mapping;

        Object.entries(mapping).forEach(([srcField, targetField]) => {
          if (data[srcField] !== undefined && typeof targetField === 'string') {
            formData.value[targetField] = data[srcField];
          }
        });
      } catch (error) {
        console.error('解析字段映射失败:', error);
      }
    }
  }
};

// 处理子表popup选择
const handleSubTablePopupSelect = (data: any, column: any, row: any, subTableComponent?: any) => {
  // 获取当前列的显示模式
  const currentDisplayMode = getColumnDisplayMode(column);

  // 检查是否为多选模式
  const isMultiple = getMultipleMode(column.props || {});

  // 只有明确配置了 sub 模式才使用新的逻辑
  if (currentDisplayMode === 'sub' && Array.isArray(data)) {
    if (subTableComponent && subTableComponent.field) {
      const subTableField = subTableComponent.field;

      // 为每个选中的数据创建新行，每行填入完整的 mapping 数据
      data.forEach((item, index) => {
        if (index === 0) {
          // 第一项更新当前行
          updateRowDataWithFullMapping(row, column, item);
        } else {
          // 其他项创建新行
          const newRow = createNewRow(subTableComponent);
          updateRowDataWithFullMapping(newRow, column, item);
          subTableData.value.push(newRow);
        }
      });

      return;
    }
  }

  // 检查是否为多选模式（非 sub 模式的多选）
  if (isMultiple && Array.isArray(data) && data.length > 1) {
    // 多选模式：插入多条记录

    if (subTableComponent && subTableComponent.field) {
      const subTableField = subTableComponent.field;

      // 为每个选中的商品创建新行
      data.forEach((item, index) => {
        if (index === 0) {
          // 第一项更新当前行
          updateRowData(row, column, item);
        } else {
          // 其他项创建新行
          const newRow = createNewRow(subTableComponent);
          updateRowData(newRow, column, item);
          subTableData.value.push(newRow);
        }
      });
    }
  } else if (data && data.value !== undefined) {
    // 单选模式：更新当前行（保持原有逻辑）
    updateRowData(row, column, data);
  }
};

// 更新行数据的辅助函数
const updateRowData = (row: any, column: any, data: any) => {
  const field = String(column.field);
  row[field] = data.value;

  // 直接使用 PopupTable 传递的映射后数据
  // PopupTable 已经完成了字段映射，我们只需要直接赋值
  if (column.props.mapping) {
    try {
      const mapping = typeof column.props.mapping === 'string' ? JSON.parse(column.props.mapping) : column.props.mapping;

      // 遍历映射配置，直接使用映射后的字段值
      Object.entries(mapping).forEach(([srcField, targetField]) => {
        if (typeof targetField === 'string') {
          // 从映射后的数据中获取值
          const mappedValue = data[targetField];
          if (mappedValue !== undefined) {
            row[targetField] = mappedValue;
          }

          // 同时设置下划线格式的字段名
          const underscoreField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase();
          if (underscoreField !== targetField) {
            const underscoreValue = data[underscoreField];
            if (underscoreValue !== undefined) {
              row[underscoreField] = underscoreValue;
            }
          }
        }
      });

      // 强制触发响应式更新
      nextTick(() => {
        // 确保所有字段都被正确设置
        Object.entries(mapping).forEach(([srcField, targetField]) => {
          if (typeof targetField === 'string') {
            const mappedValue = data[targetField];
            if (mappedValue !== undefined) {
              row[targetField] = mappedValue;

              const underscoreField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase();
              if (underscoreField !== targetField) {
                const underscoreValue = data[underscoreField];
                if (underscoreValue !== undefined) {
                  row[underscoreField] = underscoreValue;
                }
              }
            }
          }
        });
      });
    } catch (error) {
      console.error('解析字段映射失败:', error);
    }
  }

  // 详细检查每个字段的值
  if (column.props.mapping) {
    try {
      const mapping = typeof column.props.mapping === 'string' ? JSON.parse(column.props.mapping) : column.props.mapping;

      Object.entries(mapping).forEach(([srcField, targetField]) => {
        if (typeof targetField === 'string') {
          const underscoreField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase();
        }
      });
    } catch (error) {
      console.error('字段值检查失败:', error);
    }
  }
};

// 创建新行的辅助函数
const createNewRow = (subTableComponent: any) => {
  const newRow: Record<string, any> = {};
  subTableComponent.subTableColumns?.forEach((column: any) => {
    newRow[column.field] = '';
  });
  return newRow;
};

// 处理字段联动
const handleFieldLinkage = (field: string, value: any, source: 'main' | 'sub', rowIndex?: number) => {
  if (source === 'main') {
    // 主表字段变化，更新子表相关字段
    handleMainTableLinkage(field, value);
  } else if (source === 'sub' && rowIndex !== undefined) {
    // 子表字段变化，更新主表或子表其他字段
    handleSubTableLinkage(field, value, rowIndex);
  }
};

// 处理主表字段联动
const handleMainTableLinkage = (field: string, value: any) => {
  // 遍历所有子表，查找需要联动的字段
  formComponents.value.forEach((component) => {
    if (component.type === 'subtable') {
      if (!subTableData.value || !Array.isArray(subTableData.value)) {
        return;
      }

      subTableData.value.forEach((row, index) => {
        // 检查子表列是否有联动规则
        component.subTableColumns?.forEach((column) => {
          if (column.props?.linkage && column.props.linkage[field]) {
            const targetField = column.props.linkage[field];
            const newValue = calculateLinkageValue(field, value, column.props.linkage);
            row[targetField] = newValue;
          }
        });
      });
    }
  });
};

// 处理子表字段联动
const handleSubTableLinkage = (field: string, value: any, rowIndex: number) => {
  // 子表内部联动
  if (rowIndex !== undefined) {
    formComponents.value.forEach((component) => {
      if (component.type === 'subtable') {
        if (!subTableData.value || !Array.isArray(subTableData.value) || !subTableData.value[rowIndex]) {
          return;
        }

        const row = subTableData.value[rowIndex];

        // 检查子表中具体字段的计算规则
        component.subTableColumns?.forEach((column) => {
          if (column.props?.onSubChange) {
            const onSubChange = column.props.onSubChange;
            if (onSubChange.type === 'calculate' && onSubChange.formula && onSubChange.targetFields) {
              const formula = onSubChange.formula.replace(/\{\{(\w+)\}\}/g, '$1');
              const calculatedValue = calculateFormula(formula, row);

              onSubChange.targetFields.forEach((targetField: string) => {
                row[targetField] = calculatedValue;
              });
            }
          }
        });

        // 子表数据变化后，更新主表汇总字段
        updateMainTableSummary(component);
      }
    });
  }

  // 子表字段变化，更新主表相关字段
  updateMainTableFromSubTable(field, value);
};

// 更新主表字段（基于子表数据）
const updateMainTableFromSubTable = (field: string, value: any) => {
  formComponents.value.forEach((component) => {
    if (component.type === 'subtable') {
      if (!subTableData.value || !Array.isArray(subTableData.value)) {
        return;
      }

      // 检查是否有汇总规则
      if (component.props?.summary) {
        Object.entries(component.props.summary).forEach(([targetField, summaryRule]) => {
          const summaryValue = calculateSummary(summaryRule, subTableData.value);
          formData.value[targetField] = summaryValue;
        });
      }
    }
  });
};

// 更新主表汇总字段
const updateMainTableSummary = (subTableComponent: any) => {
  if (!Array.isArray(subTableData.value)) {
    return;
  }

  // 汇总子表的所有 zong_price 字段
  const totalZongPrice = subTableData.value.reduce((sum, row) => {
    const zongPrice = parseFloat(row.zong_price) || 0;
    return sum + zongPrice;
  }, 0);

  // 更新主表的总价字段
  formComponents.value.forEach((component) => {
    if (component.type !== 'subtable') {
      const isTotalPriceField = ['money', 'total_price', 'zong_price', 'totalPrice', 'totalAmount'].includes(component.field);

      if (isTotalPriceField) {
        formData.value[component.field] = totalZongPrice.toFixed(2);
      }
    }
  });
};

// 计算联动值
const calculateLinkageValue = (sourceField: string, sourceValue: any, linkageRule: any) => {
  if (typeof linkageRule === 'string') {
    return sourceValue;
  } else if (typeof linkageRule === 'function') {
    return linkageRule(sourceValue);
  } else if (typeof linkageRule === 'object') {
    if (linkageRule.type === 'multiply') {
      return sourceValue * (linkageRule.factor || 1);
    } else if (linkageRule.type === 'divide') {
      return sourceValue / (linkageRule.divisor || 1);
    } else if (linkageRule.type === 'add') {
      return sourceValue + (linkageRule.addend || 0);
    } else if (linkageRule.type === 'subtract') {
      return sourceValue - (linkageRule.subtrahend || 0);
    }
  }
  return sourceValue;
};

// 计算公式
const calculateFormula = (formula: string, rowData: any) => {
  try {
    let calculatedFormula = formula;

    Object.keys(rowData).forEach((field) => {
      const value = rowData[field];
      if (value !== undefined && value !== null && value !== '') {
        const curlyRegex = new RegExp(`\\{\\{${field}\\}\\}`, 'g');
        calculatedFormula = calculatedFormula.replace(curlyRegex, String(value));

        const plainRegex = new RegExp(`\\b${field}\\b`, 'g');
        calculatedFormula = calculatedFormula.replace(plainRegex, String(value));
      } else {
        const curlyRegex = new RegExp(`\\{\\{${field}\\}\\}`, 'g');
        calculatedFormula = calculatedFormula.replace(curlyRegex, '0');

        const plainRegex = new RegExp(`\\b${field}\\b`, 'g');
        calculatedFormula = calculatedFormula.replace(plainRegex, '0');
      }
    });

    const result = eval(calculatedFormula);
    return result;
  } catch (error) {
    console.error('公式计算失败:', error, { formula, rowData });
    return 0;
  }
};

// 计算汇总值
const calculateSummary = (summaryRule: any, subTableData: any[]) => {
  if (typeof summaryRule === 'string') {
    return subTableData.reduce((sum, row) => {
      return sum + (parseFloat(row[summaryRule]) || 0);
    }, 0);
  } else if (typeof summaryRule === 'object') {
    if (summaryRule.type === 'sum') {
      return subTableData.reduce((sum, row) => {
        return sum + (parseFloat(row[summaryRule.field]) || 0);
      }, 0);
    } else if (summaryRule.type === 'count') {
      return subTableData.length;
    } else if (summaryRule.type === 'average') {
      const sum = subTableData.reduce((sum, row) => {
        return sum + (parseFloat(row[summaryRule.field]) || 0);
      }, 0);
      return subTableData.length > 0 ? sum / subTableData.length : 0;
    }
  }
  return 0;
};

// 过滤掉不应该传递给组件的配置
const getFilteredProps = (props: any) => {
  const filteredProps = { ...props };
  delete filteredProps.onSubChange;
  delete filteredProps.onChange;
  delete filteredProps.onCodeGenerate;
  delete filteredProps.summary;
  delete filteredProps.calculations;
  return filteredProps;
};

// 加载表单数据
const loadFormData = async () => {
  const formId = route.query.formId as string;
  if (formId) {
    try {
      const res = await getNform(formId);
      if (res.code === 200) {
        currentFormData.value = res.data;
        currentFormContent.value = res.data.formContent;
        ElMessage.success('表单数据加载成功');
        // 解析表单内容并加载字典数据
        await parseFormContent();
      } else {
        ElMessage.error(res.msg || '加载表单数据失败');
      }
    } catch (error) {
      console.error('加载表单数据失败:', error);
      ElMessage.error('加载表单数据失败');
    }
  }
};

onMounted(async () => {
  await loadDeptOptions();
  await loadUserOptions();
  await loadFormData();
});

// 监听表单内容变化
watch(
  () => currentFormContent.value,
  async (newVal) => {
    if (newVal) {
      await parseFormContent();
    }
  },
  { immediate: true }
);
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-container {
  margin-top: 20px;
  padding: 20px;
  background-color: #fff;
  border-radius: 4px;
}

.empty-tip {
  margin-top: 20px;
  text-align: center;
}

.form-actions {
  margin-top: 20px;
  text-align: center;
  padding: 20px 0;
  border-top: 1px solid #eee;
}

.subtable-actions {
  margin-top: 10px;
  text-align: right;
}

// 优化子表样式
.el-table {
  width: 100%;

  .el-table__header-wrapper {
    th {
      padding: 12px 0;
      font-weight: 600;
    }
  }

  .el-table__body-wrapper {
    td {
      padding: 12px 0;
    }
  }
}
</style>
