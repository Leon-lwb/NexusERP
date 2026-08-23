<template>
  <div class="form-preview-wrapper">
    <div class="form-preview-container">
      <div class="form-preview">
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
              <template v-else-if="item.type === 'subtable'">
                <el-table :data="subTableData[item.field]" border style="width: 100%">
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
                  :props="{ label: 'deptName', value: 'deptId' }"
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
                <template v-if="item.type === 'select'">
                  <el-select v-model="formData[item.field]" v-bind="item.props" :placeholder="item.props.placeholder || '请选择'">
                    <el-option
                      v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </template>
                <template v-else-if="item.type === 'radio'">
                  <el-radio-group v-model="formData[item.field]" v-bind="item.props">
                    <el-radio
                      v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                      :key="option.value"
                      :label="option.value"
                    >
                      {{ option.label }}
                    </el-radio>
                  </el-radio-group>
                </template>
                <template v-else-if="item.type === 'checkbox'">
                  <el-checkbox-group v-model="formData[item.field]" v-bind="item.props">
                    <el-checkbox
                      v-for="option in item.props.dataSource === 'dict' ? dictOptions[item.props.dictType] || [] : item.props.options"
                      :key="option.value"
                      :label="option.value"
                    >
                      {{ option.label }}
                    </el-checkbox>
                  </el-checkbox-group>
                </template>
                <template v-else>
                  <component :is="getComponentByType(item.type)" v-model="formData[item.field]" v-bind="item.props" />
                </template>
              </template>
            </el-form-item>
          </div>
        </el-form>
      </div>
    </div>
    <div class="form-preview-footer">
      <el-button @click="handleClose">关闭</el-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, watch, onMounted } from 'vue';

// 定义组件名称
defineOptions({
  name: 'FormPreview'
});
import { ElMessage } from 'element-plus';
import { Close, Search } from '@element-plus/icons-vue';
import { listDept } from '@/api/system/dept';
import { listUser } from '@/api/system/user';
import { getDicts } from '@/api/system/dict/data';
import type { DictDataVO } from '@/api/system/dict/data/types';
import type { UserVO } from '@/api/system/user/types';
import type { DeptVO } from '@/api/system/dept/types';
import type { UserQuery } from '@/api/system/user/types';
import RichEditor from './components/RichEditor.vue';
import PopupTable from './components/PopupTable.vue';

interface FormComponent {
  id: string;
  type: string;
  label: string;
  field: string;
  props: Record<string, any>;
  required?: boolean;
  subTableColumns?: any[];
  dictType?: string;
}

interface FormConfig {
  labelWidth: string;
  labelPosition: string;
  size: string;
  formName: string;
  formDesc: string;
}

interface Props {
  formContent: string;
  modelValue: boolean;
}

const props = defineProps<Props>();

const formConfig = reactive<FormConfig>({
  labelWidth: '120px',
  labelPosition: 'right',
  size: 'default',
  formName: '',
  formDesc: ''
});

const formComponents = ref<FormComponent[]>([]);
const formData = ref<Record<string, any>>({});
const subTableData = ref<Record<string, any[]>>({});
const dictOptions = ref<Record<string, any[]>>({});
const deptOptions = ref<DeptVO[]>([]);
const userOptions = ref<UserVO[]>([]);
const showPopup = ref(false);
const currentPopupField = ref('');

// 定义 emits
const emit = defineEmits(['update:modelValue', 'close']);

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
    const content = JSON.parse(props.formContent);
    if (content.formConfig) {
      Object.assign(formConfig, content.formConfig);
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
    if (content.masterSubTableData) {
      subTableData.value = content.masterSubTableData;
    }
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
  if (!subTableData.value[component.field]) {
    subTableData.value[component.field] = [];
  }

  const newRow: Record<string, any> = {};
  component.subTableColumns?.forEach((column) => {
    newRow[column.field] = '';
  });

  subTableData.value[component.field].push(newRow);
};

// 删除子表行
const handleDeleteSubTableRow = (component: FormComponent, index: number) => {
  if (subTableData.value[component.field]) {
    subTableData.value[component.field].splice(index, 1);
  }
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

// 处理关闭事件
const handleClose = () => {
  emit('close');
  emit('update:modelValue', false);
};

// 监听表单内容变化
watch(
  () => props.formContent,
  async (newVal) => {
    if (newVal) {
      await parseFormContent();
    }
  },
  { immediate: true }
);

// 组件挂载时加载数据
onMounted(async () => {
  await loadDeptOptions();
  await loadUserOptions();
});

const handlePopupClick = (item: FormComponent) => {
  showPopup.value = true;
  currentPopupField.value = item.field;
};

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
  showPopup.value = false;
  currentPopupField.value = '';
};

// 获取显示模式
const getDisplayMode = (props: any): 'label' | 'value' => {
  try {
    const mapping = typeof props.mapping === 'string' ? JSON.parse(props.mapping) : props.mapping;

    if (mapping && mapping.popupConfig && mapping.popupConfig.displayMode) {
      const mode = mapping.popupConfig.displayMode;
      if (mode === 'label') return 'label';
      if (mode === 'value') return 'value';
      // sub 模式下，单元格显示值
      if (mode === 'sub') return 'value';
    }

    // 默认显示值
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

    // 默认单选
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

// 处理子表 popup 点击
const handleSubTablePopupClick = (column: any, row: any) => {
  showPopup.value = true;
  currentPopupField.value = column.field;
};

// 处理子表 popup 选择
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
          subTableData.value[subTableField].push(newRow);
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
          subTableData.value[subTableField].push(newRow);
        }
      });
    }
  } else if (data && data.value !== undefined) {
    // 单选模式：更新当前行（保持原有逻辑）
    updateRowData(row, column, data);
  }

  showPopup.value = false;
  currentPopupField.value = '';
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

// 处理字段联动规则
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
      const subTableField = component.field;

      // 安全检查：确保 subTableData 和对应的子表数据存在
      if (!subTableData.value || !subTableData.value[subTableField]) {
        console.warn(`子表数据不存在: ${subTableField}`);
        return;
      }

      const currentSubTableData = subTableData.value[subTableField];

      if (Array.isArray(currentSubTableData)) {
        currentSubTableData.forEach((row, index) => {
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
    }
  });
};

// 处理子表字段联动
const handleSubTableLinkage = (field: string, value: any, rowIndex: number) => {
  // 子表内部联动（如数量×价格=总价）
  if (rowIndex !== undefined) {
    // 查找当前行所属的子表组件
    formComponents.value.forEach((component) => {
      if (component.type === 'subtable') {
        const subTableField = component.field;

        // 安全检查：确保 subTableData 和对应的子表数据存在
        if (!subTableData.value || !subTableData.value[subTableField]) {
          console.warn(`子表数据不存在: ${subTableField}`);
          return;
        }

        const currentSubTableData = subTableData.value[subTableField];

        if (Array.isArray(currentSubTableData) && currentSubTableData[rowIndex]) {
          const row = currentSubTableData[rowIndex];

          // 检查子表中具体字段的计算规则（支持 onSubChange 格式）
          component.subTableColumns?.forEach((column) => {
            if (column.props?.onSubChange) {
              const onSubChange = column.props.onSubChange;
              if (onSubChange.type === 'calculate' && onSubChange.formula && onSubChange.targetFields) {
                // 处理 {{field}} 格式的公式
                const formula = onSubChange.formula.replace(/\{\{(\w+)\}\}/g, '$1');
                const calculatedValue = calculateFormula(formula, row);

                // 更新所有目标字段
                onSubChange.targetFields.forEach((targetField: string) => {
                  row[targetField] = calculatedValue;
                });
              }
            }
          });

          // 子表数据变化后，更新主表汇总字段
          updateMainTableSummary(component);

          // 兼容原有的 calculations 格式
          if (component.props?.calculations) {
            Object.entries(component.props.calculations).forEach(([targetField, formula]) => {
              const calculatedValue = calculateFormula(String(formula), row);
              row[String(targetField)] = calculatedValue;
            });
          }

          // 检查是否有字段联动规则
          component.subTableColumns?.forEach((column) => {
            if (column.props?.linkage && column.props.linkage[field]) {
              const targetField = column.props.linkage[field];
              const newValue = calculateLinkageValue(field, value, column.props.linkage);
              row[targetField] = newValue;
            }
          });
        }
      }
    });
  }

  // 子表字段变化，更新主表相关字段
  updateMainTableFromSubTable(field, value);
};

// 更新主表字段（基于子表数据）
const updateMainTableFromSubTable = (field: string, value: any) => {
  // 遍历所有子表，汇总数据更新主表
  formComponents.value.forEach((component) => {
    if (component.type === 'subtable') {
      const subTableField = component.field;

      // 安全检查：确保 subTableData 和对应的子表数据存在
      if (!subTableData.value || !subTableData.value[subTableField]) {
        console.warn(`子表数据不存在: ${subTableField}`);
        return;
      }

      const currentSubTableData = subTableData.value[subTableField];

      if (Array.isArray(currentSubTableData)) {
        // 检查是否有汇总规则
        if (component.props?.summary) {
          Object.entries(component.props.summary).forEach(([targetField, summaryRule]) => {
            const summaryValue = calculateSummary(summaryRule, currentSubTableData);
            formData.value[targetField] = summaryValue;
          });
        }
      }
    }
  });
};

// 更新主表汇总字段
const updateMainTableSummary = (subTableComponent: any) => {
  const subTableField = subTableComponent.field;
  const currentSubTableData = subTableData.value[subTableField];

  if (!Array.isArray(currentSubTableData)) {
    return;
  }

  // 汇总子表的所有 zong_price 字段
  const totalZongPrice = currentSubTableData.reduce((sum, row) => {
    const zongPrice = parseFloat(row.zong_price) || 0;
    return sum + zongPrice;
  }, 0);

  // 更新主表的总价字段
  formComponents.value.forEach((component) => {
    if (component.type !== 'subtable') {
      // 检查是否是主表的总价字段（常见的总价字段名）
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
    // 直接映射
    return sourceValue;
  } else if (typeof linkageRule === 'function') {
    // 函数计算
    return linkageRule(sourceValue);
  } else if (typeof linkageRule === 'object') {
    // 复杂规则
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
    // 替换字段名为实际值
    let calculatedFormula = formula;

    // 处理 {{field}} 格式和普通 field 格式
    Object.keys(rowData).forEach((field) => {
      const value = rowData[field];
      if (value !== undefined && value !== null && value !== '') {
        // 替换 {{field}} 格式
        const curlyRegex = new RegExp(`\\{\\{${field}\\}\\}`, 'g');
        calculatedFormula = calculatedFormula.replace(curlyRegex, String(value));

        // 替换普通 field 格式
        const plainRegex = new RegExp(`\\b${field}\\b`, 'g');
        calculatedFormula = calculatedFormula.replace(plainRegex, String(value));
      } else {
        // 如果字段值为空，替换为 0
        const curlyRegex = new RegExp(`\\{\\{${field}\\}\\}`, 'g');
        calculatedFormula = calculatedFormula.replace(curlyRegex, '0');

        const plainRegex = new RegExp(`\\b${field}\\b`, 'g');
        calculatedFormula = calculatedFormula.replace(plainRegex, '0');
      }
    });

    // 安全计算
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
    // 简单字段汇总
    return subTableData.reduce((sum, row) => {
      return sum + (parseFloat(row[summaryRule]) || 0);
    }, 0);
  } else if (typeof summaryRule === 'object') {
    if (summaryRule.type === 'sum') {
      // 求和
      return subTableData.reduce((sum, row) => {
        return sum + (parseFloat(row[summaryRule.field]) || 0);
      }, 0);
    } else if (summaryRule.type === 'count') {
      // 计数
      return subTableData.length;
    } else if (summaryRule.type === 'average') {
      // 平均值
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
  // 移除不应该传递给组件的配置
  delete filteredProps.onSubChange;
  delete filteredProps.onChange;
  delete filteredProps.onCodeGenerate;
  delete filteredProps.summary;
  delete filteredProps.calculations;
  return filteredProps;
};
</script>

<style lang="scss" scoped>
.form-preview-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 60vh;
  background-color: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.form-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid #dcdfe6;
  background-color: #fff;

  .title {
    font-size: 16px;
    font-weight: 500;
    color: #303133;
  }
}

.form-preview-container {
  flex: 1;
  overflow: hidden;
  min-height: 60vh;
}

.form-preview {
  height: 100%;
  padding: 20px;
  max-height: 70vh;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background-color: #dcdfe6;
    border-radius: 3px;
  }

  &::-webkit-scrollbar-track {
    background-color: #f5f7fa;
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
}

.form-preview-footer {
  padding: 15px 20px;
  border-top: 1px solid #dcdfe6;
  text-align: right;
  background-color: #fff;
}

.popup-container {
  display: flex;
  align-items: center;
  gap: 10px;

  .popup-input {
    width: 100%;

    :deep(.el-input-group__append) {
      padding: 0;

      .el-button {
        margin: 0;
        border: none;
        padding: 8px 15px;

        &:hover {
          background-color: #f5f7fa;
        }
      }
    }
  }
}

// 添加子表中 popup 的样式
.el-table {
  .popup-container {
    margin: 0;

    .popup-input {
      :deep(.el-input__wrapper) {
        box-shadow: none;
      }

      :deep(.el-input-group__append) {
        background-color: transparent;
      }
    }
  }
}
</style>
