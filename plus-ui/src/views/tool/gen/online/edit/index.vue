<template>
  <el-card>
    <el-tabs v-model="activeName">
      <el-tab-pane label="基本信息" name="basic">
        <basic-info-form ref="basicInfo" :info="info" />
      </el-tab-pane>
      <el-tab-pane label="字段信息" name="columnInfo">
        <el-table ref="dragTable" :data="columns" row-key="columnId" :max-height="tableHeight">
          <el-table-column label="序号" type="index" min-width="60" />
          <el-table-column label="字段列名" prop="columnName" min-width="120" :show-overflow-tooltip="true" />
          <el-table-column label="字段描述" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.columnComment"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="物理类型" prop="columnType" min-width="120" :show-overflow-tooltip="true" />
          <el-table-column label="Java类型" min-width="150">
            <template #default="scope">
              <el-select v-model="scope.row.javaType">
                <el-option label="Long" value="Long" />
                <el-option label="String" value="String" />
                <el-option label="Integer" value="Integer" />
                <el-option label="Double" value="Double" />
                <el-option label="BigDecimal" value="BigDecimal" />
                <el-option label="Date" value="Date" />
                <el-option label="Boolean" value="Boolean" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="java属性" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.javaField"></el-input>
            </template>
          </el-table-column>

          <el-table-column label="插入" min-width="60">
            <template #default="scope">
              <el-checkbox true-label="1" false-label="0" v-model="scope.row.isInsert"></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="编辑" min-width="60">
            <template #default="scope">
              <el-checkbox true-label="1" false-label="0" v-model="scope.row.isEdit"></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="列表" min-width="60">
            <template #default="scope">
              <el-checkbox true-label="1" false-label="0" v-model="scope.row.isList"></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="查询" min-width="60">
            <template #default="scope">
              <el-checkbox true-label="1" false-label="0" v-model="scope.row.isQuery"></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="查询方式" min-width="150">
            <template #default="scope">
              <el-select v-model="scope.row.queryType">
                <el-option label="=" value="EQ" />
                <el-option label="!=" value="NE" />
                <el-option label=">" value="GT" />
                <el-option label=">=" value="GE" />
                <el-option label="<" value="LT" />
                <el-option label="<=" value="LE" />
                <el-option label="LIKE" value="LIKE" />
                <el-option label="BETWEEN" value="BETWEEN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="必填" min-width="60">
            <template #default="scope">
              <el-checkbox true-label="1" false-label="0" v-model="scope.row.isRequired"></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="显示类型" min-width="180">
            <template #default="scope">
              <el-select v-model="scope.row.htmlType">
                <el-option label="文本框" value="input" />
                <el-option label="文本域" value="textarea" />
                <el-option label="下拉框" value="select" />
                <el-option label="单选框" value="radio" />
                <el-option label="复选框" value="checkbox" />
                <el-option label="弹出窗口" value="popup" />
                <el-option label="日期时间" value="datetime" />
                <el-option label="日期控件" value="date" />
                <el-option label="时间控件" value="time" />
                <el-option label="图片上传" value="imageUpload" />
                <el-option label="文件上传" value="fileUpload" />
                <el-option label="富文本控件" value="editor" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="字典类型" min-width="160">
            <template #default="scope">
              <el-select
                v-model="scope.row.dictType"
                clearable
                filterable
                placeholder="请选择"
                @change="handleDictTypeChange(scope.row)"
              >
                <!-- 当显示类型为popup时显示表列表 -->
                <template v-if="scope.row.htmlType === 'popup'">
                  <el-option
                    v-for="table in tableOptions"
                    :key="table.tableName"
                    :label="table.tableComment"
                    :value="table.tableName"
                  >
                    <span style="float: left">{{ table.tableComment }}</span>
                    <span style="float: right; color: #8492a6; font-size: 13px">{{ table.tableName }}</span>
                  </el-option>
                </template>

                <!-- 其他显示类型显示字典 -->
                <template v-else>
                  <el-option
                    v-for="dict in dictOptions"
                    :key="dict.dictType"
                    :label="dict.dictName"
                    :value="dict.dictType"
                  >
                    <span style="float: left">{{ dict.dictName }}</span>
                    <span style="float: right; color: #8492a6; font-size: 13px">{{ dict.dictType }}</span>
                  </el-option>
                </template>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="字典字段" min-width="180">
            <template #default="scope">
              <div style="display:flex;align-items:center;gap:8px;">
                <el-input v-model="scope.row.dictFields" readonly placeholder="点击配置按钮进行设置" />
                <el-button 
                  size="small" 
                  type="primary" 
                  :icon="Edit"
                  @click="openJsonEditor('dictFields', scope.row)"
                >
                  配置
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="字段映射" min-width="180">
            <template #default="scope">
              <div style="display:flex;align-items:center;gap:8px;">
                <el-input v-model="scope.row.mapping" readonly placeholder="点击配置按钮进行设置" />
                <el-button 
                  size="small" 
                  type="primary" 
                  :icon="Edit"
                  @click="openJsonEditor('mapping', scope.row)"
                >
                  配置
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="联动规则" min-width="280">
            <template #default="scope">
              <div style="display:flex;align-items:center;gap:8px;">
                <el-input v-model="scope.row.onChange" readonly placeholder="点击配置按钮进行设置" />
                <el-button 
                  size="small" 
                  type="primary" 
                  :icon="Edit"
                  @click="openJsonEditor('onChange', scope.row)"
                >
                  配置
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="排序" min-width="80">
            <template #default="scope">
              <el-input v-model="scope.row.sort"></el-input>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="生成信息" name="genInfo">
        <gen-info-form ref="genInfo" :info="info" :tables="tables" />
      </el-tab-pane>
    </el-tabs>
    <el-form label-width="100px">
      <div style="text-align: center;margin-left:-100px;margin-top:10px;">
        <el-button type="primary" @click="submitForm()">提交</el-button>
        <el-button @click="close()">返回</el-button>
      </div>
    </el-form>

    <!-- JSON 配置对话框 -->
    <el-dialog
      v-model="jsonEditorVisible"
      :title="jsonEditorTitle"
      width="1000px"
      :before-close="handleJsonEditorClose"
      destroy-on-close
    >
      <!-- 字典字段配置 -->
      <div v-if="currentJsonType === 'dictFields'" class="json-editor-section">
        <h4>字典字段配置</h4>
        <p class="config-description">选择需要的字典字段配置项</p>
        
        <!-- 弹出窗口：按表字段生成 字段名 -> 标题 的映射 -->
        <div v-if="currentRow && currentRow.htmlType === 'popup'" class="checkbox-list">
          <div v-if="!currentRow.dictType" style="color:#f56c6c;">请先在列表中为该字段选择对应的表（字典类型）。</div>
          <div v-else>
            <div class="popup-fields-scroll">
              <div
                v-for="col in popupColumns"
                :key="col.columnName"
                class="checkbox-item"
              >
                <el-checkbox 
                  v-model="selectedPopupFields[col.columnName]"
                  @change="() => handlePopupFieldToggle(col)"
                >
                  {{ col.columnName }}（{{ col.columnComment }}）
                </el-checkbox>
                <el-input 
                  v-if="selectedPopupFields[col.columnName]"
                  v-model="popupFieldTitles[col.columnName]"
                  placeholder="请输入标题，默认使用字段注释"
                  size="small"
                  style="width: 260px; margin-left: 10px;"
                  @input="updateJson"
                />
              </div>
            </div>
          </div>
        </div>
        
        <!-- 非弹出窗口：保持原先的四项配置 -->
        <div v-else class="checkbox-list">
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.dictValue" 
              @change="updateJson"
            >
              dictValue（值字段）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.dictValue"
              v-model="fieldValues.dictValue" 
              placeholder="请输入字段名，如：value"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.dictLabel" 
              @change="updateJson"
            >
              dictLabel（显示字段）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.dictLabel"
              v-model="fieldValues.dictLabel" 
              placeholder="请输入字段名，如：label"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.orderField" 
              @change="updateJson"
            >
              orderField（排序字段）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.orderField"
              v-model="fieldValues.orderField" 
              placeholder="请输入字段名，如：orderNum"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.where" 
              @change="updateJson"
            >
              where（过滤条件）
            </el-checkbox>
            <el-input 
              v-if="selectedOptions.where"
              v-model="fieldValues.where" 
              placeholder="请输入条件，如：status = 1"
              size="small"
              style="width: 200px; margin-left: 10px;"
              @input="updateJson"
            />
          </div>
        </div>
      </div>

      <!-- 字段映射配置 -->
      <div v-if="currentJsonType === 'mapping'" class="json-editor-section">
        <h4>字段映射配置</h4>
        <p class="config-description">选择需要的字段映射配置项</p>
        
        <!-- 弹出窗口：表字段 -> 当前表字段 的映射 + 弹出配置 -->
        <div v-if="currentRow && currentRow.htmlType === 'popup'" class="mapping-table">
          <div class="mapping-header">
            <span>源字段（表字段）</span>
            <span>目标字段（当前表字段名）</span>
            <span>操作</span>
          </div>
          <div class="popup-mapping-scroll" :class="{ 'limit-height': popupMappingPairs.length > 3 }">
            <div
              v-for="(item, index) in popupMappingPairs"
              :key="index"
              class="mapping-item"
            >
              <el-select
                v-model="item.source"
                placeholder="选择表字段"
                filterable
                style="width: 220px;"
                @change="updateJson"
              >
                <el-option 
                  v-for="col in popupColumns" 
                  :key="col.columnName" 
                  :label="col.columnName + '（' + col.columnComment + '）'" 
                  :value="col.columnName" 
                />
              </el-select>
              <el-input
                v-model="item.target"
                placeholder="请输入当前表字段名"
                style="width: 220px;"
                @input="updateJson"
              />
              <el-button
                type="danger"
                size="small"
                :icon="Delete"
                @click="removePopupMappingPair(index)"
              />
            </div>
          </div>
          <el-button
            type="primary"
            size="small"
            :icon="Plus"
            @click="addPopupMappingPair"
            class="add-mapping-btn"
          >
            添加映射
          </el-button>

          <!-- 弹出配置 -->
          <div class="popup-config">
            <div class="config-row">
              <label>显示模式：</label>
              <el-select
                v-model="popupDisplayMode"
                placeholder="选择显示模式"
                style="width: 200px;"
                @change="updateJson"
              >
                <el-option label="label" value="label" />
                <el-option label="value" value="value" />
                <el-option label="sub" value="sub" />
              </el-select>
            </div>
            <div class="config-row">
              <label>多选：</label>
              <el-switch v-model="popupMultiple" @change="updateJson" />
            </div>
          </div>
        </div>

        <div v-else class="checkbox-list">
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.field" 
              @change="updateJson"
            >
              field（字段映射）
            </el-checkbox>
            <div v-if="selectedOptions.field" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.fieldSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.fieldTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.fieldName" 
              @change="updateJson"
            >
              fieldName（字段名称映射）
            </el-checkbox>
            <div v-if="selectedOptions.fieldName" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.fieldNameSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.fieldNameTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.id" 
              @change="updateJson"
            >
              id（ID字段映射）
            </el-checkbox>
            <div v-if="selectedOptions.id" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.idSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.idTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
          
          <div class="checkbox-item">
            <el-checkbox 
              v-model="selectedOptions.name" 
              @change="updateJson"
            >
              name（名称字段映射）
            </el-checkbox>
            <div v-if="selectedOptions.name" class="mapping-inputs">
              <el-input 
                v-model="fieldValues.nameSource" 
                placeholder="源字段名"
                size="small"
                style="width: 150px; margin-left: 10px;"
                @input="updateJson"
              />
              <span style="margin: 0 10px;">→</span>
              <el-input 
                v-model="fieldValues.nameTarget" 
                placeholder="目标字段名"
                size="small"
                style="width: 150px;"
                @input="updateJson"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 联动规则配置 -->
      <div v-if="currentJsonType === 'onChange'" class="json-editor-section">
        <h4>联动规则配置</h4>
        <p class="config-description">使用可视化配置生成 onChange JSON（保存前会做强校验）</p>

        <OnChangeRuleEditor
          v-if="currentRow"
          v-model="currentOnChange"
          :fields="whenFieldOptions"
          :targets="fieldOptions"
        />
      </div>

      <!-- JSON 预览 -->
      <div class="json-preview">
        <h4>JSON 预览</h4>
        <el-input
          v-model="jsonPreview"
          type="textarea"
          :rows="6"
          readonly
          class="json-textarea"
        />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleJsonEditorClose">取消</el-button>
          <el-button type="primary" @click="handleJsonEditorConfirm">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup name="OnlineEdit" lang="ts">
import { TableDictVO } from '@/api/online/types';
import { getGenTable, updateGenTable } from '@/api/tool/gen';
import { DbColumnVO, DbTableVO } from '@/api/tool/gen/types';
import { optionselect as getDictOptionselect } from '@/api/system/dict/type';
import { DictTypeVO } from '@/api/system/dict/type/types';
import basicInfoForm from '../design/basicInfoForm.vue';
import genInfoForm from "../design/genInfoForm.vue";
import { ComponentInternalInstance, getCurrentInstance, ref, watch, computed } from "vue";
import { useRoute } from 'vue-router';
// Element Plus icon
import { Edit, Plus, Delete } from '@element-plus/icons-vue';
import { validateDictFields, validateMapping, validateOnChange } from '@/composables/online/fieldConfig'
import OnChangeRuleEditor from './components/OnChangeRuleEditor.vue'

const route = useRoute();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const activeName = ref('columnInfo');
const tableHeight = ref(document.documentElement.scrollHeight - 245 + 'px');
const tables = ref<DbTableVO[]>([]);
const columns = ref<DbColumnVO[]>([]);
const dictOptions = ref<DictTypeVO[]>([]);
const info = ref<Partial<DbTableVO>>({});

const basicInfo = ref<any>(basicInfoForm);
const genInfo = ref<any>(genInfoForm);
const tableOptions = ref<TableDictVO[]>([]);

// JSON 编辑器相关
const jsonEditorVisible = ref(false)
const currentJsonType = ref<'dictFields' | 'mapping' | 'onChange'>('dictFields')
const currentRow = ref<any>(null)

// fields/targets options for OnChangeRuleEditor
const fieldOptions = computed(() =>
  (columns.value || [])
    .filter((c: any) => c?.columnName)
    .map((c: any) => ({
      label: `${c.columnComment || c.columnName}（${c.columnName}）`,
      value: String(c.columnName),
    }))
)

// when 条件里允许引用 workflow 上下文
const whenFieldOptions = computed(() => [
  ...fieldOptions.value,
  { label: '流程：节点（workflow.nodeKey）', value: 'workflow.nodeKey' },
  { label: '流程：只读（workflow.readonly）', value: 'workflow.readonly' },
])

const currentOnChange = computed<string>({
  get: () => {
    const v = currentRow.value?.onChange
    return (typeof v === 'string' && v.trim()) ? v : '{}'
  },
  set: (v) => {
    if (!currentRow.value) return
    currentRow.value.onChange = v
  },
})

// 选中的配置项
const selectedOptions = ref<Record<string, boolean>>({})
// 字段值
const fieldValues = ref<Record<string, any>>({})
// 联动规则
const rules = ref<Array<{triggerField: string, targetField: string, type: string}>>([])

// 弹出窗口字典字段配置（字段名 -> 标题）相关
const popupColumns = ref<Array<{ columnName: string; columnComment: string }>>([])
const selectedPopupFields = ref<Record<string, boolean>>({})
const popupFieldTitles = ref<Record<string, string>>({})

// 弹出窗口映射配置
const popupMappingPairs = ref<Array<{ source: string; target: string }>>([])
const popupDisplayMode = ref<'label' | 'value' | 'sub'>('label')
const popupMultiple = ref<boolean>(false)

// 计算属性
const jsonEditorTitle = computed(() => {
  const titles = {
    dictFields: '字典字段配置',
    mapping: '字段映射配置',
    onChange: '联动规则配置'
  }
  return titles[currentJsonType.value]
})

const jsonPreview = computed(() => {
  try {
    if (currentJsonType.value === 'dictFields') {
      // 弹出窗口：输出 { fieldName: title }
      if (currentRow.value && currentRow.value.htmlType === 'popup') {
        const result: Record<string, string> = {}
        Object.keys(selectedPopupFields.value).forEach((field) => {
          if (selectedPopupFields.value[field]) {
            result[field] = popupFieldTitles.value[field] || field
          }
        })
        return JSON.stringify(result, null, 2)
      }
      // 非弹出窗口：沿用原结构
      const result: any = {}
      if (selectedOptions.value.dictValue) {
        result.dictValue = fieldValues.value.dictValue
      }
      if (selectedOptions.value.dictLabel) {
        result.dictLabel = fieldValues.value.dictLabel
      }
      if (selectedOptions.value.orderField) {
        result.orderField = fieldValues.value.orderField
      }
      if (selectedOptions.value.where) {
        result.where = fieldValues.value.where
      }
      return JSON.stringify(result, null, 2)
    } else if (currentJsonType.value === 'mapping') {
      // 弹出窗口：输出 { mapping: {source: target}, popupConfig: {displayMode, multiple} }
      if (currentRow.value && currentRow.value.htmlType === 'popup') {
        const mapping: Record<string, string> = {}
        popupMappingPairs.value.forEach(pair => {
          if (pair.source && pair.target) {
            mapping[pair.source] = pair.target
          }
        })
        const result = {
          mapping,
          popupConfig: {
            displayMode: popupDisplayMode.value,
            multiple: popupMultiple.value
          }
        }
        return JSON.stringify(result, null, 2)
      }
      const result: any = {}
      if (selectedOptions.value.field) {
        result[fieldValues.value.fieldSource] = fieldValues.value.fieldTarget
      }
      if (selectedOptions.value.fieldName) {
        result[fieldValues.value.fieldNameSource] = fieldValues.value.fieldNameTarget
      }
      if (selectedOptions.value.id) {
        result[fieldValues.value.idSource] = fieldValues.value.idTarget
      }
      if (selectedOptions.value.name) {
        result[fieldValues.value.nameSource] = fieldValues.value.nameTarget
      }
      return JSON.stringify(result, null, 2)
    } else if (currentJsonType.value === 'onChange') {
      // onChange 由 OnChangeRuleEditor 负责生成/维护
      const s = currentRow.value?.onChange
      if (typeof s !== 'string' || !s.trim()) return '{}'
      try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s }
    }
    return '{}'
  } catch (error) {
    return '{}'
  }
})

// 打开 JSON 编辑器
const openJsonEditor = (type: 'dictFields' | 'mapping' | 'onChange', row: any) => {
  currentJsonType.value = type
  currentRow.value = row
  
  // 初始化数据
  if (type === 'dictFields') {
    // 如果是弹出窗口，按表加载字段
    if (row && row.htmlType === 'popup' && row.dictType) {
      const selectedTable = tables.value.find((t: any) => t.tableName === row.dictType)
      popupColumns.value = (selectedTable?.columns || []).map((c: any) => ({
        columnName: c.columnName,
        columnComment: c.columnComment
      }))
      // 初始化勾选与标题
      selectedPopupFields.value = {}
      popupFieldTitles.value = {}
      let mapping: any = {}
      try { mapping = row.dictFields ? JSON.parse(row.dictFields) : {} } catch { mapping = {} }
      if (mapping && typeof mapping === 'object') {
        Object.keys(mapping).forEach((field) => {
          selectedPopupFields.value[field] = true
          popupFieldTitles.value[field] = mapping[field]
        })
      }
    } else {
      // 非弹出窗口，沿用原有四项
      let dictFields: any = {}
      try { dictFields = row.dictFields ? JSON.parse(row.dictFields) : {} } catch { dictFields = {} }
      selectedOptions.value = {
        dictValue: !!dictFields.dictValue,
        dictLabel: !!dictFields.dictLabel,
        orderField: !!dictFields.orderField,
        where: !!dictFields.where
      }
      fieldValues.value = {
        dictValue: dictFields.dictValue || 'value',
        dictLabel: dictFields.dictLabel || 'label',
        orderField: dictFields.orderField || 'orderNum',
        where: dictFields.where || 'status = 1'
      }
    }
  } else if (type === 'mapping') {
    // 弹出窗口初始化
    if (row && row.htmlType === 'popup' && row.dictType) {
      const selectedTable = tables.value.find((t: any) => t.tableName === row.dictType)
      popupColumns.value = (selectedTable?.columns || []).map((c: any) => ({
        columnName: c.columnName,
        columnComment: c.columnComment
      }))
      popupMappingPairs.value = []
      popupDisplayMode.value = 'label'
      popupMultiple.value = false
      let mappingObj: any = {}
      try { mappingObj = row.mapping ? JSON.parse(row.mapping) : {} } catch { mappingObj = {} }
      if (mappingObj && typeof mappingObj === 'object') {
        if (mappingObj.mapping && typeof mappingObj.mapping === 'object') {
          Object.keys(mappingObj.mapping).forEach((key) => {
            popupMappingPairs.value.push({ source: key, target: mappingObj.mapping[key] })
          })
        }
        if (mappingObj.popupConfig) {
          popupDisplayMode.value = mappingObj.popupConfig.displayMode || 'label'
          popupMultiple.value = !!mappingObj.popupConfig.multiple
        }
      }
      if (popupMappingPairs.value.length === 0) {
        popupMappingPairs.value.push({ source: '', target: '' })
      }
    } else {
      let mapping: any = {}
      try { mapping = row.mapping ? JSON.parse(row.mapping) : {} } catch { mapping = {} }
      selectedOptions.value = {
        field: !!mapping.field,
        fieldName: !!mapping.fieldName,
        id: !!mapping.id,
        name: !!mapping.name
      }
      fieldValues.value = {
        fieldSource: mapping.field || 'field',
        fieldTarget: mapping.field || 'field',
        fieldNameSource: mapping.fieldName || 'fieldName',
        fieldNameTarget: mapping.fieldName || 'fieldName',
        idSource: mapping.id || 'id',
        idTarget: mapping.id || 'id',
        nameSource: mapping.name || 'name',
        nameTarget: mapping.name || 'name'
      }
    }
  } else if (type === 'onChange') {
    // onChange 改为可视化编辑器（避免 JSON.parse 崩溃）
    try {
      if (!row.onChange || !String(row.onChange).trim()) {
        row.onChange = '{}'
      } else {
        // normalize to object json
        row.onChange = JSON.stringify(JSON.parse(row.onChange), null, 2)
      }
    } catch {
      row.onChange = '{}'
    }
    // 清理旧版 onChange 编辑器状态，避免干扰 jsonPreview
    selectedOptions.value = {}
    fieldValues.value = {}
  }
  
  jsonEditorVisible.value = true
}

// 添加/删除 弹出窗口映射行
const addPopupMappingPair = () => {
  popupMappingPairs.value.push({ source: '', target: '' })
  updateJson()
}
const removePopupMappingPair = (index: number) => {
  popupMappingPairs.value.splice(index, 1)
  updateJson()
}

// 更新 JSON
const updateJson = () => {
  // 触发计算属性更新
}

// 弹出窗口：勾选行为时填充默认标题
const handlePopupFieldToggle = (col: { columnName: string; columnComment: string }) => {
  if (selectedPopupFields.value[col.columnName]) {
    if (!popupFieldTitles.value[col.columnName]) {
      popupFieldTitles.value[col.columnName] = col.columnComment || col.columnName
    }
  }
  updateJson()
}

// 添加规则
const addRule = () => {
  rules.value.push({ triggerField: '', targetField: '', type: 'clear' })
  updateJson()
}

// 删除规则
const removeRule = (index: number) => {
  rules.value.splice(index, 1)
  updateJson()
}

// 关闭 JSON 编辑器
const handleJsonEditorClose = () => {
  jsonEditorVisible.value = false
  currentRow.value = null
  selectedOptions.value = {}
  fieldValues.value = {}
  rules.value = []
  popupColumns.value = []
  selectedPopupFields.value = {}
  popupFieldTitles.value = {}
  popupMappingPairs.value = []
  popupDisplayMode.value = 'label'
  popupMultiple.value = false
}

// 确认 JSON 编辑器
const handleJsonEditorConfirm = () => {
  if (currentRow.value) {
    const jsonString = jsonPreview.value

    // 保存前做一次强校验（后端也会校验，这里是为了更早给用户友好提示）
    let vr: any = { ok: true }
    if (currentJsonType.value === 'dictFields') {
      vr = validateDictFields(jsonString)
    } else if (currentJsonType.value === 'mapping') {
      vr = validateMapping(jsonString)
    } else if (currentJsonType.value === 'onChange') {
      vr = validateOnChange(jsonString)
    }
    if (vr && vr.ok === false) {
      proxy?.$modal.msgError((vr.errors || []).join('；'))
      return
    }
    
    if (currentJsonType.value === 'dictFields') {
      currentRow.value.dictFields = jsonString
    } else if (currentJsonType.value === 'mapping') {
      currentRow.value.mapping = jsonString
    } else if (currentJsonType.value === 'onChange') {
      currentRow.value.onChange = jsonString
    }
  }
  
  handleJsonEditorClose()
}

/** 提交按钮 */
const submitForm = () => {
  const basicForm = (basicInfo as any).value.$refs.basicInfoForm;
  const genForm = (genInfo as any).value.$refs.genInfoForm;

  Promise.all([basicForm, genForm].map(getFormPromise)).then(async res => {
    const validateResult = res.every(item => !!item);
    if (validateResult) {
      const genTable: any = Object.assign({}, info.value);
      genTable.columns = columns.value;
      genTable.params = {
        treeCode: info.value?.treeCode,
        treeName: info.value.treeName,
        treeParentCode: info.value.treeParentCode,
        parentMenuId: info.value.parentMenuId
      };
      const response = await updateGenTable(genTable);
      proxy?.$modal.msgSuccess(response.msg);
      if (response.code === 200) {
        close();
      }
    } else {
      proxy?.$modal.msgError("表单校验未通过，请重新检查提交内容");
    }
  });
}

const getFormPromise = (form: any) => {
  return new Promise(resolve => {
    form.validate((res: any) => {
      resolve(res);
    });
  });
}

const close = () => {
  const obj = {path: "/onlineform/design", query: {t: Date.now(), pageNum: route.query.pageNum}};
  proxy?.$tab.closeOpenPage(obj);
}

const handleDictTypeChange = (row: any) => {
  if (row.htmlType === 'popup') {
    const selectedTable = tableOptions.value.find((t: TableDictVO) => t.tableName === row.dictType);
    if (selectedTable) {
      // 这里可以自动设置字段关联配置
    }
  }
}

(async () => {
  const tableId = route.params && route.params.tableId as string;
  if (tableId) {
    const res = await getGenTable(tableId);
    columns.value = res.data.rows as DbColumnVO[];
    info.value = res.data.info;
    tables.value = res.data.tables as DbTableVO[];
    tableOptions.value = tables.value.map((item: DbTableVO) => ({
      tableName: item.tableName,
      tableComment: item.tableComment
    }));
    const response = await getDictOptionselect();
    dictOptions.value = response.data;
  }
})();

watch(() => columns.value, (newVal: DbColumnVO[]) => {
  newVal.forEach((row: DbColumnVO) => {
    if (row.htmlType !== 'popup' && row.dictType && row.dictType.startsWith('sys_')) {
      // 保留原有字典逻辑
    } else if (row.htmlType === 'popup') {
      if (!tableOptions.value.some((t: TableDictVO) => t.tableName === row.dictType)) {
        row.dictType = '' as any;
      }
    }
  });
}, { deep: true });
</script>

<style scoped>
.json-editor-section {
  margin-bottom: 20px;
}

.json-editor-section h4 {
  margin: 0 0 15px 0;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.config-description {
  color: #606266;
  font-size: 14px;
  margin-bottom: 20px;
}

.checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.checkbox-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background-color: #fafafa;
  transition: all 0.2s;
}

.checkbox-item:hover {
  background-color: #f0f9ff;
  border-color: #409eff;
}

.mapping-inputs {
  display: flex;
  align-items: center;
  margin-left: 10px;
}

.rule-config {
  margin-left: 10px;
  width: 100%;
  padding: 15px;
  background-color: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  margin-top: 10px;
}

.config-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.config-row:last-child {
  margin-bottom: 0;
}

.config-row label {
  min-width: 100px;
  font-weight: 500;
  color: #495057;
  font-size: 14px;
}

.json-preview {
  margin-top: 20px;
  padding: 15px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background-color: #f8f9fa;
}

.json-preview h4 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.json-textarea {
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.dialog-footer {
  text-align: right;
}
.popup-fields-scroll {
  max-height: 360px;
  overflow: auto;
  padding-right: 8px;
}

.mapping-table {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}

.mapping-header {
  display: grid;
  grid-template-columns: 1fr 1fr 80px;
  gap: 12px;
  padding: 8px 12px;
  font-weight: 600;
  color: #303133;
  background-color: #f0f9eb;
}
.mapping-item {
  display: grid;
  grid-template-columns: 1fr 1fr 80px;
  gap: 12px;
  padding: 8px 12px;
  align-items: center;
  background-color: #f9fafc;
}
.popup-config {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background-color: #fafafa;
}

.popup-mapping-scroll {
  overflow: hidden;
}
.popup-mapping-scroll.limit-height {
  max-height: 150px; /* 约等于3行的高度，超过3行即出现滚动条 */
  overflow: auto;
  padding-right: 8px;
}
</style>
