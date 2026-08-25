<template>
  <div class="form-render-wrapper">
    <div class="form-render-container">
      <!-- 表单内容 -->
      <el-form
        ref="formRef"
        :model="formData"
        :label-width="props.labelWidth || formConfig.labelWidth"
        :label-position="props.labelPosition || formConfig.labelPosition"
      >
        <div v-for="(item, index) in formComponents" :key="index">
          <!-- prop 必须绑定字段名：否则 required 只显示星号不产生校验 -->
          <el-form-item
            :label="item.type === 'subtable' ? '' : item.label"
            :prop="item.type === 'subtable' ? undefined : item.field"
            :required="item.required"
          >
            <template v-if="item.type === 'input'">
              <el-input
                v-model="formData[item.field]"
                v-bind="item.props"
                @input="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
              />
            </template>

            <template v-else-if="item.type === 'textarea'">
              <el-input
                v-model="formData[item.field]"
                type="textarea"
                v-bind="item.props"
                @input="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
              />
            </template>

            <template v-else-if="item.type === 'select'">
              <el-select
                v-model="formData[item.field]"
                v-bind="item.props"
                @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
              >
                <el-option v-for="option in getComponentOptions(item)" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </template>

            <template v-else-if="item.type === 'radio'">
              <el-radio-group
                v-model="formData[item.field]"
                v-bind="item.props"
                @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
              >
                <el-radio v-for="option in getComponentOptions(item)" :key="option.value" :label="option.value">
                  {{ option.label }}
                </el-radio>
              </el-radio-group>
            </template>

            <template v-else-if="item.type === 'editor'">
              <RichEditor
                v-model="formData[item.field]"
                v-bind="item.props"
                :placeholder="item.props?.placeholder || '请输入内容'"
                :height="item.props?.height || 300"
                :min-height="item.props?.minHeight || 200"
                @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
              />
            </template>

            <template v-else-if="item.type === 'popup'">
              <template v-if="!item.props?.disabled">
                <PopupTable
                  v-model="formData[item.field as string]"
                  :nm="item"
                  :dictType="item.dictType || item.props.dictType"
                  :dictFields="item.props.dictFields || {}"
                  :mapping="item.props.mapping || {}"
                  :multiple="getMultipleMode(item.props)"
                  :displayMode="getDisplayMode(item.props)"
                  @common-component="(data) => handlePopupSelect(data, item)"
                />
              </template>
              <template v-else>
                <span>{{ getMainPopupDisplay(item, formData[item.field as string]) }}</span>
              </template>
            </template>

            <template v-else-if="item.type === 'date'">
              <el-date-picker
                v-model="formData[item.field]"
                type="date"
                v-bind="item.props"
                @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
              />
            </template>

            <template v-else-if="item.type === 'datetime'">
              <el-date-picker
                v-model="formData[item.field]"
                type="datetime"
                v-bind="item.props"
                @change="(value: any) => handleFieldLinkage(String(item.field), value, 'main')"
              />
            </template>

            <template v-else-if="item.type === 'subtable'">
              <div class="subtable-container">
                <div class="subtable-header">
                  <span>{{ item.label }}</span>
                  <el-button type="primary" size="small" @click="handleAddSubTableRow(item)"> 添加行 </el-button>
                </div>
                <el-table :data="subTableData[item.field] || []" border class="subtable-full-width">
                  <el-table-column
                    v-for="column in item.subTableColumns"
                    :key="column.field"
                    :prop="column.field"
                    :label="column.label"
                    :width="(column as any).width || 120"
                  >
                    <template #default="{ row, $index }">
                      <PopupTable
                        v-if="column.type === 'popup'"
                        :modelValue="row[String(column.field)]"
                        :nm="column"
                        :dictType="column.dictType || column.props.dictType"
                        :dictFields="column.props.dictFields || {}"
                        :mapping="column.props.mapping || {}"
                        :multiple="getMultipleMode(column.props)"
                        :displayMode="getColumnDisplayMode(column) === 'sub' ? 'value' : getColumnDisplayMode(column)"
                        @common-component="
                          (data) => {
                            handleSubTablePopupSelect(data, column, row, item);
                          }
                        "
                      />
                      <el-input
                        v-else-if="column.type === 'input'"
                        v-model="row[column.field]"
                        v-bind="getFilteredProps(column.props)"
                        @input="(value: any) => handleSubTableFieldChange(item.field, column.field, value, $index)"
                      />
                      <el-select
                        v-else-if="column.type === 'select'"
                        v-model="row[column.field]"
                        v-bind="getFilteredProps(column.props)"
                        @change="(value: any) => handleSubTableFieldChange(item.field, column.field, value, $index)"
                      >
                        <el-option
                          v-for="option in getSubTableComponentOptions(column)"
                          :key="option.value"
                          :label="option.label"
                          :value="option.value"
                        />
                      </el-select>
                      <el-date-picker
                        v-else-if="column.type === 'date'"
                        v-model="row[column.field]"
                        type="date"
                        v-bind="getFilteredProps(column.props)"
                        @change="(value: any) => handleSubTableFieldChange(item.field, column.field, value, $index)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="80">
                    <template #default="{ $index }">
                      <el-button type="danger" size="small" @click="handleDeleteSubTableRow(item, $index)"> 删除 </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-form-item>
        </div>

        <!-- 如果没有子表组件，显示提示 -->
        <div
          v-if="formComponents.length > 0 && !hasSubTableInfo"
          style="background: #f9f9f9; padding: 15px; margin: 20px 0; border: 1px dashed #ddd; text-align: center; color: #666"
        >
          <p>当前表单只包含主表字段，没有子表字段。</p>
          <p>如需添加子表功能，请在表单设计器中添加子表组件。</p>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue';
import { safeArithmeticEval } from '@/utils/safeExpression';
import PopupTable from './components/PopupTable.vue';
import RichEditor from './components/RichEditor.vue';
import type { FormComponent, FormConfig } from './types';
import { listTableData } from '@/api/online';

const props = defineProps<{
  formContent: string;
  modelValue?: boolean;
  initialData?: Record<string, any>;
  dictOptions?: Record<string, any[]>;
  deptOptions?: any[];
  userOptions?: any[];
  labelWidth?: string;
  labelPosition?: 'left' | 'right' | 'top';
}>();

const emit = defineEmits(['submit', 'reset', 'update:modelValue']);

// 调试模式（默认关闭，仅保留变量以便需要时启用）
const debugMode = ref(false);

const formConfig = reactive<FormConfig>({
  labelWidth: '100px',
  labelPosition: 'right',
  size: 'default',
  formName: '',
  formDesc: ''
});

const formComponents = ref<FormComponent[]>([]);
const formData = ref<Record<string, any>>({});
const subTableData = ref<Record<string, any[]>>({});
const formRef = ref();
const dictOptionsLocal = ref<Record<string, any[]>>({});
const parsedContent = ref<any>({});
const popupDictCache: Record<string, Record<string, string>> = reactive({});

// 同步外部传入的字典到内部使用（解决只读场景不显示标签的问题）
watch(
  () => props.dictOptions,
  (val) => {
    dictOptionsLocal.value = val || {};
  },
  { immediate: true, deep: true }
);

// 添加 tableInfo 定义
const tableInfo = ref<any>({});

// 计算是否有子表信息
const hasSubTableInfo = computed(() => {
  const hasMasterSubTableData = !!parsedContent.value?.masterSubTableData;
  const hasSubTableInConfig = !!parsedContent.value?.formConfig?.tableInfo?.subTable;
  const hasSubTableComponent = formComponents.value.some((c) => c.type === 'subtable');

  return hasMasterSubTableData || hasSubTableInConfig || hasSubTableComponent;
});

// 从列信息生成表单组件
const generateFormComponentsFromColumns = (columns: any[]) => {
  if (!columns || columns.length === 0) {
    return [];
  }

  const components: FormComponent[] = [];

  columns.forEach((column, index) => {
    // 跳过系统字段
    if (['id', 'tenant_id', 'create_by', 'create_dept', 'create_time', 'update_by', 'update_time', 'update_dept'].includes(column.columnName)) {
      return;
    }

    const component: FormComponent = {
      id: `field_${Date.now()}_${index}`,
      type: getComponentTypeFromColumn(column),
      label: column.columnComment || column.columnName,
      field: column.columnName,
      props: getComponentPropsFromColumn(column),
      required: column.isRequired === '1' || column.isRequired === true || column.isNullable === 'NO',
      dictType: column.dictType
    };

    components.push(component);
  });

  return components;
};

// 根据列信息确定组件类型
const getComponentTypeFromColumn = (column: any) => {
  const columnType = column.columnType?.toLowerCase() || '';
  const htmlType = column.htmlType?.toLowerCase() || '';

  // 优先使用 htmlType
  if (htmlType) {
    switch (htmlType) {
      case 'input':
        return 'input';
      case 'textarea':
        return 'textarea';
      case 'select':
        return 'select';
      case 'radio':
        return 'radio';
      case 'checkbox':
        return 'checkbox';
      case 'date':
        return 'date';
      case 'datetime':
        return 'datetime';
      case 'time':
        return 'time';
      case 'switch':
        return 'switch';
      case 'rate':
        return 'rate';
      case 'slider':
        return 'slider';
      case 'color-picker':
        return 'color-picker';
      case 'popup':
        return 'popup';
      case 'dept-select':
        return 'dept-select';
      case 'user-select':
        return 'user-select';
      default:
        return 'input';
    }
  }

  // 根据数据库字段类型推断
  if (columnType.includes('text') || columnType.includes('longtext')) {
    return 'textarea';
  } else if (columnType.includes('date') && !columnType.includes('datetime')) {
    return 'date';
  } else if (columnType.includes('datetime') || columnType.includes('timestamp')) {
    return 'datetime';
  } else if (columnType.includes('time')) {
    return 'time';
  } else if (columnType.includes('int') || columnType.includes('decimal') || columnType.includes('float')) {
    return 'input';
  } else if (columnType.includes('varchar') && columnType.includes('1')) {
    return 'switch';
  } else {
    return 'input';
  }
};

// 根据列信息生成组件属性
const getComponentPropsFromColumn = (column: any) => {
  const props: Record<string, any> = {
    placeholder: `请输入${column.columnComment || column.columnName}`,
    clearable: true
  };

  // 根据字段类型设置特定属性
  const columnType = column.columnType?.toLowerCase() || '';
  if (columnType.includes('int') || columnType.includes('decimal') || columnType.includes('float')) {
    props.type = 'number';
  }

  // 设置字典相关属性
  if (column.dictType) {
    props.dataSource = 'dict';
    props.dictType = column.dictType;
  }

  // 设置选项数据
  if (column.options) {
    props.options = column.options;
  }

  // 设置默认值
  if (column.defaultValue !== undefined && column.defaultValue !== null) {
    props.defaultValue = column.defaultValue;
  }

  // 设置长度限制
  if (column.columnLength) {
    props.maxlength = parseInt(column.columnLength);
  }

  return props;
};

// 时间格式化 - 参考 FormPreview.vue
const formatTimestamp = (date: Date, format: string) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  const milliseconds = String(date.getMilliseconds()).padStart(3, '0');

  return format
    .replace('YYYY', year.toString())
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
    .replace('SSS', milliseconds);
};

// 生成编码 - 参考 FormPreview.vue
const generateCode = (rule: string, params?: any) => {
  // 添加类型检查
  if (typeof rule !== 'string') {
    console.warn('[FormRender] generateCode: rule 不是字符串类型:', rule);
    return '';
  }

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
    const length = params?.randomLength || 4;
    const random = Math.random().toString().substr(2, length);
    result = result.replace('{random}', random);
  }

  return result;
};

// 解析表单内容
const parseFormContent = () => {
  try {
    if (!props.formContent) {
      return;
    }

    const parsed = JSON.parse(props.formContent);
    parsedContent.value = parsed;

    // formConfig 是 reactive 对象（非 ref）：此前误写成 formConfig.value 赋值，解析出的表单配置从未生效，现改为合并赋值
    Object.assign(formConfig, parsed.formConfig || {});
    formComponents.value = parsed.formComponents || [];

    // 初始化表单/子表数据
    formComponents.value.forEach((component) => {
      if (component.type === 'subtable') {
        if (!subTableData.value[component.field]) {
          subTableData.value[component.field] = [];
        }
      } else if (component.type === 'checkbox') {
        if (formData.value[component.field] === undefined) {
          formData.value[component.field] = [];
        }
      } else if (component.type === 'dept-select' || component.type === 'user-select') {
        if (formData.value[component.field] === undefined) {
          formData.value[component.field] = component.props?.multiple ? [] : '';
        }
      } else {
        if (formData.value[component.field] === undefined) {
          formData.value[component.field] = '';
        }
      }

      // 处理编码生成（仅当当前值为空时生成，避免覆盖回填值）
      if (component.props?.onCodeGenerate) {
        const currentVal = formData.value[component.field];
        if (currentVal === undefined || currentVal === null || currentVal === '') {
          const codeRule = component.props.onCodeGenerate;
          // 如果 codeRule 是对象，尝试获取其中的规则字符串
          let ruleString = '';
          if (typeof codeRule === 'string') {
            ruleString = codeRule;
          } else if (typeof codeRule === 'object' && codeRule.rule) {
            ruleString = codeRule.rule;
          } else if (typeof codeRule === 'object' && codeRule.autoGenerateRule) {
            ruleString = codeRule.autoGenerateRule;
          } else {
            console.warn('[FormRender] 无法识别的编码规则格式:', codeRule);
            return;
          }

          const generatedCode = generateCode(ruleString, component.props);
          formData.value[component.field] = generatedCode;
        }

        // 避免将 onCodeGenerate 作为事件传递给原生组件
        if (component.props && 'onCodeGenerate' in component.props) {
          delete component.props.onCodeGenerate;
        }
      }
    });

    // 若父组件传入 initialData，这里再次合并，确保不被上面的默认值覆盖
    if (props.initialData && typeof props.initialData === 'object') {
      Object.assign(formData.value, props.initialData);
    }

    // 预加载主表 popup 的名称字典（用于 label 显示）
    const popupItems = formComponents.value.filter((c) => c.type === 'popup' && (c as any)?.props?.dictType);
    popupItems.forEach(async (it: any) => {
      try {
        const mappingCfg = normalizeMapping(it?.props?.mapping);
        const dictType = it?.props?.dictType;
        // 优先使用 mapping.id/name，其次 dictFields 两个键
        let valueKey = typeof mappingCfg?.id === 'string' ? mappingCfg.id : undefined;
        let labelKey = typeof mappingCfg?.name === 'string' ? mappingCfg.name : undefined;
        if (!valueKey || !labelKey) {
          const keys = parseDictFieldKeys(it?.props?.dictFields);
          valueKey = valueKey || keys.valueKey;
          labelKey = labelKey || keys.labelKey;
        }
        await ensurePopupDictLoaded(dictType, String(valueKey), String(labelKey));
      } catch (e) {
        console.warn('[FormRender] 预加载 popup 字典失败:', e);
      }
    });

    // 将 masterSubTableData 注入 subTableData（用于只读回显子表）
    if (parsed?.masterSubTableData && typeof parsed.masterSubTableData === 'object') {
      Object.keys(parsed.masterSubTableData).forEach((key) => {
        const rows = parsed.masterSubTableData[key];
        if (Array.isArray(rows)) {
          subTableData.value[key] = rows;
        }
      });
    }
  } catch (error) {
    console.error('解析表单内容失败:', error);
  }
};

// 统一解析 mapping 配置（兼容字符串/对象，且支持 { mapping: {...} } 包裹）
const normalizeMapping = (mapping: any) => {
  try {
    if (typeof mapping === 'string') {
      const obj = JSON.parse(mapping);
      return obj?.mapping || obj || {};
    }
    return mapping?.mapping || mapping || {};
  } catch {
    return {};
  }
};

// 解析 dictFields 的两个键（值键、名键）
const parseDictFieldKeys = (dictFields: any): { valueKey: string; labelKey: string } => {
  try {
    const obj = typeof dictFields === 'string' ? JSON.parse(dictFields) : dictFields || {};
    // 优先使用显式的 id/name 键名
    const explicitValue = Object.prototype.hasOwnProperty.call(obj, 'id') ? 'id' : undefined;
    const explicitLabel = Object.prototype.hasOwnProperty.call(obj, 'name') ? 'name' : undefined;
    if (explicitValue && explicitLabel) {
      return { valueKey: explicitValue, labelKey: explicitLabel };
    }
    const keys = Object.keys(obj);
    return { valueKey: explicitValue || keys[0] || 'id', labelKey: explicitLabel || keys[1] || 'name' };
  } catch {
    return { valueKey: 'id', labelKey: 'name' };
  }
};

// 加载 popup 字典映射缓存
const ensurePopupDictLoaded = async (dictType: string, valueKey: string, labelKey: string) => {
  const cacheKey = `${dictType}:${valueKey}:${labelKey}`;
  if (popupDictCache[cacheKey]) return popupDictCache[cacheKey];
  try {
    const res: any = await listTableData(dictType, { pageNum: 1, pageSize: 1000, params: {} } as any);
    const rows: any[] = res?.rows || [];
    const map: Record<string, string> = {};
    rows.forEach((r: any) => {
      if (r?.[valueKey] != null) map[String(r[valueKey])] = String(r[labelKey] ?? '');
    });
    popupDictCache[cacheKey] = map;
    return map;
  } catch {
    return {};
  }
};

// 字典选项
const getComponentOptions = (item: any) => {
  // 优先使用字典
  const dictTypeFromProps = item?.props?.dictType;
  const dictTypeFromTop = item?.dictType;
  const dictType = dictTypeFromProps || dictTypeFromTop;
  const dataSource = item?.props?.dataSource || (dictType ? 'dict' : undefined);
  if (dataSource === 'dict' && dictType) {
    return dictOptionsLocal.value[dictType] || [];
  }
  // 否则使用静态 options
  return item?.props?.options || [];
};

const getSubTableComponentOptions = (column: any) => {
  if (column.props?.dataSource === 'dict' && column.props?.dictType) {
    return dictOptionsLocal.value[column.props.dictType] || [];
  }
  return column.props?.options || [];
};

// popup 显示模式与多选模式
const getDisplayMode = (props: any) => {
  try {
    if (typeof props?.mapping === 'string') {
      const parsed = JSON.parse(props.mapping);
      return parsed?.popupConfig?.displayMode || 'label';
    }
    return props?.mapping?.popupConfig?.displayMode || 'label';
  } catch {
    return 'label';
  }
};

const getMultipleMode = (props: any) => {
  try {
    if (typeof props?.mapping === 'string') {
      const parsed = JSON.parse(props.mapping);
      return parsed?.popupConfig?.multiple === true;
    }
    return props?.mapping?.popupConfig?.multiple === true;
  } catch {
    return false;
  }
};

// 读取列显示模式（便于与 FormPreview 对齐）
const getColumnDisplayMode = (column: any) => {
  return getDisplayMode(column?.props || {});
};

// 基于子表组件结构创建新行
const createNewRow = (subTableComponent: any) => {
  const newRow: Record<string, any> = {};
  (subTableComponent?.subTableColumns || []).forEach((column: any) => {
    newRow[column.field] = '';
  });
  return newRow;
};

// 将选择的数据按 mapping 写入行（普通模式）
const updateRowData = (row: any, column: any, data: any) => {
  const field = String(column.field);

  // 首先设置当前字段的值
  if (data && data.value !== undefined) {
    row[field] = data.value;
  }

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
    } catch (error) {
      console.error('解析字段映射失败:', error);
    }
  }
};

// 将选择的数据按 mapping 全量写入（sub 显示模式）
const updateRowDataWithFullMapping = (row: any, column: any, data: any) => {
  try {
    // 首先设置当前字段的值
    const field = String(column.field);
    if (data && data.value !== undefined) {
      row[field] = data.value;
    }

    const mapping = typeof column.props.mapping === 'string' ? JSON.parse(column.props.mapping) : column.props.mapping;

    if (mapping && mapping.mapping) {
      Object.entries(mapping.mapping).forEach(([srcField, targetField]) => {
        if (typeof targetField === 'string') {
          let sourceValue = null;
          if (data.originalData && data.originalData[srcField] !== undefined) {
            sourceValue = data.originalData[srcField];
          } else if (data[srcField] !== undefined) {
            sourceValue = data[srcField];
          }

          if (sourceValue !== null) {
            row[targetField] = sourceValue;
            const underscoreField = targetField.replace(/([A-Z])/g, '_$1').toLowerCase();
            if (underscoreField !== targetField) {
              row[underscoreField] = sourceValue;
            }
          } else {
          }
        }
      });
    }
  } catch (error) {
    console.error('updateRowDataWithFullMapping 失败:', error);
  }
};

// 子表行新增
const handleAddSubTableRow = (component: FormComponent) => {
  if (!subTableData.value[component.field]) {
    subTableData.value[component.field] = [];
  }
  const newRow: Record<string, any> = {};
  const columns = (component as any).subTableColumns || [];
  columns.forEach((column: any) => {
    newRow[column.field] = column?.props?.defaultValue ?? '';
  });
  subTableData.value[component.field].push(newRow);
  // 触发响应式
  subTableData.value[component.field] = [...subTableData.value[component.field]];
};

// 子表行删除
const handleDeleteSubTableRow = (component: FormComponent, index: number) => {
  if (!subTableData.value[component.field]) return;
  subTableData.value[component.field].splice(index, 1);
  subTableData.value[component.field] = [...subTableData.value[component.field]];
};

// 子表单元格变更
const handleSubTableFieldChange = (subTableField: string, columnField: string, value: any, index: number) => {
  if (!subTableData.value[subTableField]) return;
  const row = subTableData.value[subTableField][index];
  if (!row) return;
  row[columnField] = value;
  // 触发联动
  handleFieldLinkage(columnField, value, 'sub', index);
  // 触发响应式
  subTableData.value[subTableField] = [...subTableData.value[subTableField]];
};

// 日期格式化
const formatDate = (date: Date, format: string) => {
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

// 弹窗选择处理
const handlePopupSelect = (data: any, component: FormComponent) => {
  try {
    const dictFields =
      typeof (component as any).props.dictFields === 'string'
        ? JSON.parse((component as any).props.dictFields)
        : (component as any).props.dictFields || {};
    const mapping =
      typeof (component as any).props.mapping === 'string' ? JSON.parse((component as any).props.mapping) : (component as any).props.mapping || {};

    Object.entries(dictFields).forEach(([sourceField, _label]: [string, any]) => {
      const targetField = (mapping as any)[sourceField] || sourceField;
      if ((data as any)[sourceField] !== undefined) {
        formData.value[targetField as string] = (data as any)[sourceField];
      }
    });
  } catch (error) {
    console.error('弹窗选择处理失败:', error);
  }
};

// 子表弹窗选择处理（支持多选插入多行，以及 sub 显示模式）
const handleSubTablePopupSelect = (data: any, column: any, row: any, subTableComponent: FormComponent) => {
  try {
    const isMultiple = getMultipleMode(column?.props || {});
    const displayMode = getColumnDisplayMode(column);

    // sub 显示模式且多选：每条数据生成一行（第一条填充当前行，其余 push 新行）
    if (displayMode === 'sub' && Array.isArray(data)) {
      const subTableField = (subTableComponent as any)?.field;

      if (!subTableField) return;

      data.forEach((item: any, index: number) => {
        if (index === 0) {
          updateRowDataWithFullMapping(row, column, item);

          // 强制触发第一行的响应式更新
          const rowIndex = subTableData.value[subTableField].indexOf(row);
          if (rowIndex !== -1) {
            subTableData.value[subTableField].splice(rowIndex, 1, { ...row });
          }
        } else {
          const newRow = createNewRow(subTableComponent);
          updateRowDataWithFullMapping(newRow, column, item);
          if (!subTableData.value[subTableField]) subTableData.value[subTableField] = [];
          subTableData.value[subTableField].push(newRow);
        }
      });

      // 触发响应式
      subTableData.value[(subTableComponent as any).field] = [...subTableData.value[(subTableComponent as any).field]];
      return;
    }

    // 非 sub 模式，如为多选也按多行插入
    if (isMultiple && Array.isArray(data)) {
      const subTableField = (subTableComponent as any)?.field;
      if (!subTableField) return;
      data.forEach((item: any, index: number) => {
        if (index === 0) {
          updateRowData(row, column, item);
        } else {
          const newRow = createNewRow(subTableComponent);
          updateRowData(newRow, column, item);
          if (!subTableData.value[subTableField]) subTableData.value[subTableField] = [];
          subTableData.value[subTableField].push(newRow);
        }
      });
      subTableData.value[(subTableComponent as any).field] = [...subTableData.value[(subTableComponent as any).field]];
      return;
    }

    // 单选：更新当前行
    updateRowData(row, column, data);
    const fieldKey = (subTableComponent as any)?.field;
    if (fieldKey && subTableData.value[fieldKey]) {
      subTableData.value[fieldKey] = [...subTableData.value[fieldKey]];
    }
  } catch (error) {
    console.error('子表弹窗选择处理失败:', error);
  }
};

// 监听表单内容变化
watch(
  () => props.formContent,
  (newVal, oldVal) => {
    parseFormContent();
  },
  { immediate: true }
);

// 监听初始数据变化
watch(
  () => props.initialData,
  () => {
    if (props.initialData) {
      Object.assign(formData.value, props.initialData);
    }
  },
  { deep: true }
);

// 监听字典选项变化
watch(
  () => props.dictOptions,
  () => {
    Object.assign(dictOptionsLocal.value, props.dictOptions);
  },
  { deep: true }
);

// 暴露方法给父组件
defineExpose({
  formData,
  subTableData,
  getFormData: () => ({
    main: formData.value,
    sub: subTableData.value
  }),
  /** 校验必填项（el-form-item 绑定 prop 后 required 才会真正校验） */
  validate: async () => {
    if (!formRef.value) return true;
    return await formRef.value.validate().catch(() => false);
  }
});

// 字段联动入口（主表/子表）
const handleFieldLinkage = (fieldName: string, value: any, type: 'main' | 'sub', subIndex?: number) => {
  if (type === 'main') {
    handleMainFieldLinkage(fieldName, value);
  } else if (type === 'sub' && typeof subIndex === 'number') {
    handleSubFieldLinkage(fieldName, value, subIndex);
  }
  emit('update:modelValue', { main: formData.value, sub: subTableData.value });
};

// 主表字段联动
const handleMainFieldLinkage = (fieldName: string, value: any) => {
  const component = formComponents.value.find((c) => c.field === fieldName);
  const rawOnChange = (component as any)?.props?.onChange;
  if (!rawOnChange) return;
  try {
    const onChange = typeof rawOnChange === 'string' ? JSON.parse(rawOnChange) : rawOnChange;
    if (onChange.onCodeGenerate) {
      handleCodeGenerate(onChange.onCodeGenerate, value);
    }
    if (onChange.onMainChange) {
      handleMainChange(onChange.onMainChange, value);
    }
  } catch (e) {
    console.error('主表字段联动处理失败:', e);
  }
};

// 子表字段联动
const handleSubFieldLinkage = (fieldName: string, value: any, subIndex: number) => {
  const subTableField = Object.keys(subTableData.value)[0];
  const row = subTableData.value[subTableField]?.[subIndex];
  if (!row) return;
  const column = formComponents.value.find((c) => c.type === 'subtable')?.subTableColumns?.find((col) => col.field === fieldName);
  const rawOnChange = (column as any)?.props?.onChange;
  if (!rawOnChange) return;
  try {
    const onChange = typeof rawOnChange === 'string' ? JSON.parse(rawOnChange) : rawOnChange;
    if (onChange.onSubChange) {
      handleSubChange(onChange.onSubChange, value, row, subIndex);
    }
    if (onChange.onSubTrigger) {
      handleSubTrigger(onChange.onSubTrigger, value, row);
    }
  } catch (e) {
    console.error('子表字段联动处理失败:', e);
  }
};

// 编码生成
const handleCodeGenerate = (config: any, value: any) => {
  if (!config?.autoGenerateRule) return;
  const generatedCode = generateCode(config.autoGenerateRule, config.generateParams);
  const targetField = config.targetField || 'code';

  // 更新目标字段
  if (targetField && formData.value.hasOwnProperty(targetField)) {
    formData.value[targetField] = generatedCode;
  }
};

// 主表变化规则
const handleMainChange = (config: any, value: any) => {
  if (!config?.formula || !config?.targetFields) return;
  const result = evaluateFormula(config.formula, formData.value);
  if (Array.isArray(config.targetFields)) {
    (config.targetFields as string[]).forEach((field: string) => {
      formData.value[field] = result;
    });
  } else {
    formData.value[config.targetFields as string] = result;
  }
};

// 子表变化规则
const handleSubChange = (config: any, value: any, row: any, subIndex: number) => {
  if (!config?.formula || !config?.targetFields) return;
  const result = evaluateFormula(config.formula, row);
  if (Array.isArray(config.targetFields)) {
    (config.targetFields as string[]).forEach((field: string) => {
      row[field] = result;
    });
  } else {
    row[config.targetFields as string] = result;
  }
  const subTableField = Object.keys(subTableData.value)[0];
  subTableData.value[subTableField].splice(subIndex, 1, { ...row });

  // 子表数据变化后，更新主表汇总字段
  const subTableComponent = formComponents.value.find((c) => c.type === 'subtable');
  if (subTableComponent) updateMainTableSummary(subTableComponent);
};

// 子表触发（如聚合）
const handleSubTrigger = (config: any, value: any, row: any) => {
  if (config?.triggerType !== 'aggregate' || !config?.formula || !config?.targetFields) return;
  const subTableField = Object.keys(subTableData.value)[0];
  const subData = subTableData.value[subTableField] || [];
  let result = 0;
  subData.forEach((item: any) => {
    result += parseFloat(evaluateFormula(config.formula, item)) || 0;
  });
  if (Array.isArray(config.targetFields)) {
    (config.targetFields as string[]).forEach((field: string) => {
      formData.value[field] = result;
    });
  } else {
    formData.value[config.targetFields as string] = result;
  }

  // 触发聚合后，同步更新主表其它汇总字段（如有）
  const subTableComponent = formComponents.value.find((c) => c.type === 'subtable');
  if (subTableComponent) updateMainTableSummary(subTableComponent);
};

// 公式计算
const evaluateFormula = (formula: string, data: Record<string, any>) => {
  try {
    let result = String(formula);
    const matches = result.match(/\{\{([^}]+)\}\}/g);
    if (matches) {
      matches.forEach((match: string) => {
        const field = match.replace(/\{\{|\}\}/g, '');
        const value = data[field] ?? 0;
        result = result.replace(match, String(value));
      });
    }
    // 仅做基本四则运算（安全求值，禁止 eval —— 公式来自表单定义 JSON，存在代码注入风险）
    return safeArithmeticEval(result) || 0;
  } catch (e) {
    console.error('公式计算失败:', e);
    return 0;
  }
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

// 更新主表汇总字段（参考 FormPreview.vue）
const updateMainTableSummary = (subTableComponent: any) => {
  const subTableField = subTableComponent.field;
  const currentSubTableData = subTableData.value[subTableField];
  if (!Array.isArray(currentSubTableData)) return;

  // 如果有 summary 配置，则按配置汇总
  if (subTableComponent.props?.summary) {
    Object.entries(subTableComponent.props.summary as Record<string, any>).forEach(([targetField, summaryRule]) => {
      // 支持 sum: 'zong_price' 或表达式
      let summaryValue = 0;
      if (typeof summaryRule === 'string') {
        // 简单字段求和
        summaryValue = currentSubTableData.reduce((sum, row) => sum + (parseFloat(row[String(summaryRule)]) || 0), 0);
      } else if (summaryRule && typeof summaryRule === 'object' && (summaryRule as any).type === 'sum' && (summaryRule as any).field) {
        summaryValue = currentSubTableData.reduce((sum, row) => sum + (parseFloat(row[String((summaryRule as any).field)]) || 0), 0);
      } else if (summaryRule && typeof summaryRule === 'object' && (summaryRule as any).formula) {
        // 复杂公式：对每行按公式计算后再累加
        summaryValue = currentSubTableData.reduce(
          (sum, row) => sum + (parseFloat(evaluateFormula(String((summaryRule as any).formula), row)) || 0),
          0
        );
      }
      formData.value[String(targetField)] = Number.isFinite(summaryValue) ? Number(summaryValue.toFixed(2)) : 0;
    });
    return;
  }

  // 无显式配置时，默认按常见总价字段名汇总 zong_price 到主表常见字段
  const totalZongPrice = currentSubTableData.reduce((sum, row) => sum + (parseFloat(row.zong_price) || 0), 0);
  formComponents.value.forEach((component) => {
    if (component.type !== 'subtable') {
      const isTotalPriceField = ['money', 'total_price', 'zong_price', 'totalPrice', 'totalAmount'].includes(component.field);
      if (isTotalPriceField) {
        formData.value[component.field] = Number(totalZongPrice.toFixed(2));
      }
    }
  });
};

// 主表 popup 显示文本计算（只读）
const getMainPopupDisplay = (item: any, currentValue: any) => {
  try {
    const props = item?.props || {};
    const displayMode = getDisplayMode(props);
    if (displayMode === 'sub') {
      // sub 模式用于子表，这里回退为 label 以便展示
      return resolvePopupLabel(item, currentValue);
    }
    if (displayMode === 'value') {
      return resolvePopupValue(item, currentValue);
    }
    // label
    return resolvePopupLabel(item, currentValue);
  } catch {
    return String(currentValue ?? '');
  }
};

const resolvePopupValue = (item: any, currentValue: any) => {
  const mappingCfg = normalizeMapping(item?.props?.mapping);
  const idTarget: string | undefined = typeof mappingCfg?.id === 'string' ? mappingCfg.id : undefined;
  if (idTarget && formData.value[idTarget] != null && formData.value[idTarget] !== '') {
    return String(formData.value[idTarget]);
  }
  return String(currentValue ?? '');
};

const resolvePopupLabel = (item: any, currentValue: any) => {
  const field = item?.field || '';
  const mappingCfg = normalizeMapping(item?.props?.mapping);
  const nameTarget: string | undefined = typeof mappingCfg?.name === 'string' ? mappingCfg.name : undefined;
  // 1) 优先映射名称字段
  if (nameTarget && formData.value[nameTarget] != null && formData.value[nameTarget] !== '') {
    return String(formData.value[nameTarget]);
  }
  // 2) 其次 字段名+Name
  const fallback = `${field}Name`;
  if (formData.value[fallback] != null && formData.value[fallback] !== '') {
    return String(formData.value[fallback]);
  }
  // 3) 尝试通过 dictType 将 id 转换为名称（若缓存未就绪则触发加载并等待下一次渲染命中）
  const dictType = item?.props?.dictType;
  if (dictType) {
    const idKey = typeof mappingCfg?.id === 'string' ? mappingCfg.id : field;
    const idVal = formData.value[idKey] ?? currentValue;
    if (idVal !== undefined && idVal !== null && idVal !== '') {
      const keys = parseDictFieldKeys(item?.props?.dictFields);
      const cacheKey = `${dictType}:${keys.valueKey}:${keys.labelKey}`;
      const map = popupDictCache[cacheKey];
      if (map && map[String(idVal)] !== undefined) {
        return String(map[String(idVal)]);
      }
      // 懒加载缓存，加载完成后因依赖被追踪会二次渲染
      ensurePopupDictLoaded(dictType, keys.valueKey, keys.labelKey);
    }
  }
  // 4) 最后回退 id 值
  return resolvePopupValue(item, currentValue);
};
</script>

<style lang="scss" scoped>
.form-render-wrapper {
  .form-render-container {
    padding: 20px;

    .subtable-container {
      width: 100%;
      margin-left: calc(-1 * var(--el-form-label-width, 100px));
      width: calc(100% + var(--el-form-label-width, 100px));

      .subtable-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 10px;
        padding: 8px 0;
        border-bottom: 1px solid #e4e7ed;

        span {
          font-weight: 500;
          font-size: 14px;
          color: #303133;
          flex: 1;
        }

        .el-button {
          margin-left: 16px;
          flex-shrink: 0;
        }
      }
    }

    .subtable-actions {
      margin-top: 10px;
      text-align: right;
    }
  }
}

// 确保子表宽度与主表一致
:deep(.subtable-full-width) {
  width: 100% !important;
  table-layout: fixed;
}

:deep(.subtable-full-width .el-table__body-wrapper) {
  width: 100% !important;
}

:deep(.subtable-full-width .el-table__header-wrapper) {
  width: 100% !important;
}

:deep(.subtable-full-width .el-table__header) {
  width: 100% !important;
}

:deep(.subtable-full-width .el-table__body) {
  width: 100% !important;
}
</style>
