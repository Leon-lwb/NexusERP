<template>
  <div class="app-container dict-page">
    <el-row :gutter="16" class="dict-grid">
      <!-- 字典类型 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="dict-card">
          <template #header>
            <div class="dict-card__header">
              <div class="dict-card__title">字典管理</div>
              <right-toolbar v-model:show-search="showTypeSearch" @query-table="getTypeList" />
            </div>
          </template>

          <el-form ref="typeQueryRef" :model="typeQueryParams" v-show="showTypeSearch" label-width="80px" class="list-search">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="字典名称" prop="dictName">
                  <el-input v-model="typeQueryParams.dictName" placeholder="请输入字典名称" clearable @keyup.enter="handleTypeQuery" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="字典类型" prop="dictType">
                  <el-input v-model="typeQueryParams.dictType" placeholder="请输入字典类型" clearable @keyup.enter="handleTypeQuery" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="创建时间">
                  <el-date-picker
                    v-model="dateRange"
                    style="width: 100%"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    type="daterange"
                    range-separator="-"
                    start-placeholder="开始日期"
                    end-placeholder="结束日期"
                    :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
                  ></el-date-picker>
                </el-form-item>
              </el-col>
              <el-col :span="12" class="search-actions">
                <el-form-item>
                  <el-button type="primary" icon="Search" @click="handleTypeQuery">搜索</el-button>
                  <el-button icon="Refresh" @click="handleTypeResetQuery">重置</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <div class="dict-actions">
            <el-button v-hasPermi="['system:dict:add']" type="primary" plain icon="Plus" @click="handleTypeAdd()">新增</el-button>
            <el-button v-hasPermi="['system:dict:edit']" type="success" plain icon="Edit" :disabled="typeSingle" @click="handleTypeUpdate()"
              >修改</el-button
            >
            <el-button v-hasPermi="['system:dict:remove']" type="danger" plain icon="Delete" :disabled="typeMultiple" @click="handleTypeDelete()"
              >删除</el-button
            >
            <el-button v-hasPermi="['system:dict:export']" type="warning" plain icon="Download" @click="handleTypeExport">导出</el-button>
            <el-button v-hasPermi="['system:dict:remove']" type="danger" plain icon="Refresh" @click="handleRefreshCache">刷新缓存</el-button>
          </div>

          <div class="dict-table-wrap">
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
              <el-table-column label="字典名称" align="center" prop="dictName" width="120" />
              <el-table-column label="字典类型" align="center" prop="dictType" width="160">
                <template #default="scope">
                  <span class="link-type" @click.stop="handleTypeRowClick(scope.row)">{{ scope.row.dictType }}</span>
                </template>
              </el-table-column>
              <el-table-column label="备注" align="center" prop="remark" width="160" />
              <el-table-column label="创建时间" align="center" prop="createTime" width="180">
                <template #default="scope">
                  <span>{{ proxy?.parseTime(scope.row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" fixed="right" align="center" width="120" class-name="small-padding fixed-width">
                <template #default="scope">
                  <el-tooltip content="修改" placement="top">
                    <el-button v-hasPermi="['system:dict:edit']" link type="primary" icon="Edit" @click="handleTypeUpdate(scope.row)"></el-button>
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button v-hasPermi="['system:dict:remove']" link type="primary" icon="Delete" @click="handleTypeDelete(scope.row)"></el-button>
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

      <!-- 字典数据 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="dict-card">
          <template #header>
            <div class="dict-card__header">
              <div class="dict-card__title">
                字典数据
                <span class="dict-card__subtitle">{{ currentDictLabel }}</span>
              </div>
              <right-toolbar v-model:show-search="showDataSearch" @query-table="getDataList" />
            </div>
          </template>

          <el-form ref="dataQueryRef" :model="dataQueryParams" v-show="showDataSearch" label-width="80px" class="list-search">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="字典标签" prop="dictLabel">
                  <el-input
                    v-model="dataQueryParams.dictLabel"
                    placeholder="请输入字典标签"
                    clearable
                    :disabled="!hasCurrentDict"
                    @keyup.enter="handleDataQuery"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12" class="search-actions">
                <el-form-item>
                  <el-button type="primary" icon="Search" :disabled="!hasCurrentDict" @click="handleDataQuery">搜索</el-button>
                  <el-button icon="Refresh" :disabled="!hasCurrentDict" @click="handleDataResetQuery">重置</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <div class="dict-actions">
            <el-button v-hasPermi="['system:dict:add']" type="primary" plain icon="Plus" :disabled="!hasCurrentDict" @click="handleDataAdd()"
              >新增</el-button
            >
            <el-button
              v-hasPermi="['system:dict:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="dataSingle || !hasCurrentDict"
              @click="handleDataUpdate()"
              >修改</el-button
            >
            <el-button
              v-hasPermi="['system:dict:remove']"
              type="danger"
              plain
              icon="Delete"
              :disabled="dataMultiple || !hasCurrentDict"
              @click="handleDataDelete()"
              >删除</el-button
            >
            <el-button v-hasPermi="['system:dict:export']" type="warning" plain icon="Download" :disabled="!hasCurrentDict" @click="handleDataExport"
              >导出</el-button
            >
          </div>

          <div class="dict-table-wrap">
            <el-table v-loading="dataLoading" border :data="dataList" @selection-change="handleDataSelectionChange">
              <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="字典标签" align="center" prop="dictLabel" width="80">
                <template #default="scope">
                  <span
                    v-if="
                      (scope.row.listClass === '' || scope.row.listClass === 'default') && (scope.row.cssClass === '' || scope.row.cssClass == null)
                    "
                    >{{ scope.row.dictLabel }}</span
                  >
                  <el-tag
                    v-else
                    :type="scope.row.listClass === 'primary' || scope.row.listClass === 'default' ? 'primary' : scope.row.listClass"
                    :class="scope.row.cssClass"
                    >{{ scope.row.dictLabel }}</el-tag
                  >
                </template>
              </el-table-column>
              <el-table-column label="字典键值" align="center" prop="dictValue" width="80" />
              <el-table-column label="字典排序" align="center" prop="dictSort" width="80" />
              <el-table-column label="备注" align="center" prop="remark" width="100" />
              <el-table-column label="创建时间" align="center" prop="createTime" width="180">
                <template #default="scope">
                  <span>{{ proxy?.parseTime(scope.row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" fixed="right" align="center" width="120" class-name="small-padding fixed-width">
                <template #default="scope">
                  <el-tooltip content="修改" placement="top">
                    <el-button v-hasPermi="['system:dict:edit']" link type="primary" icon="Edit" @click="handleDataUpdate(scope.row)"></el-button>
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button v-hasPermi="['system:dict:remove']" link type="primary" icon="Delete" @click="handleDataDelete(scope.row)"></el-button>
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

    <!-- 字典类型对话框 -->
    <el-dialog v-model="typeOpen" :title="typeTitle" width="500px" append-to-body>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item prop="dictType">
          <el-input v-model="typeForm.dictType" placeholder="请输入字典类型" maxlength="100" />
          <template v-slot:label>
            <span>
              <el-tooltip content="数据存储中的Key值，如：sys_user_sex" placement="top">
                <i class="el-icon-question"></i>
              </el-tooltip>
              字典类型
            </span>
          </template>
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

    <!-- 字典数据对话框 -->
    <el-dialog v-model="dataOpen" :title="dataTitle" width="500px" append-to-body>
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="80px">
        <el-form-item label="字典类型">
          <el-input v-model="dataForm.dictType" :disabled="true" />
        </el-form-item>
        <el-form-item label="数据标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="请输入数据标签" />
        </el-form-item>
        <el-form-item label="数据键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="请输入数据键值" />
        </el-form-item>
        <el-form-item label="样式属性" prop="cssClass">
          <el-input v-model="dataForm.cssClass" placeholder="请输入样式属性" />
        </el-form-item>
        <el-form-item label="显示排序" prop="dictSort">
          <el-input-number v-model="dataForm.dictSort" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="回显样式" prop="listClass">
          <el-select v-model="dataForm.listClass">
            <el-option
              v-for="item in listClassOptions"
              :key="item.value"
              :label="item.label + '(' + item.value + ')'"
              :value="item.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dataForm.remark" type="textarea" placeholder="请输入内容"></el-input>
        </el-form-item>
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

<script setup name="Dict" lang="ts">
import { useDictStore } from '@/store/modules/dict';
import { listType, getType, delType, addType, updateType, refreshCache } from '@/api/system/dict/type';
import { listData, getData, delData, addData, updateData } from '@/api/system/dict/data';
import { DictTypeForm, DictTypeQuery, DictTypeVO } from '@/api/system/dict/type/types';
import { DictDataForm, DictDataQuery, DictDataVO } from '@/api/system/dict/data/types';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dateRange = ref<[string, string]>(['', '']);
const typeTableRef = ref<ElTableInstance>();
const typeFormRef = ref<ElFormInstance>();
const dataFormRef = ref<ElFormInstance>();

const currentDict = ref<DictTypeVO | null>(null);
const hasCurrentDict = computed(() => !!currentDict.value);
const currentDictLabel = computed(() => {
  if (!currentDict.value) return '请先选择字典';
  return `${currentDict.value.dictName} / ${currentDict.value.dictType}`;
});

const listClassOptions = ref<Array<{ value: string; label: string }>>([
  { value: 'default', label: '默认' },
  { value: 'primary', label: '主要' },
  { value: 'success', label: '成功' },
  { value: 'info', label: '信息' },
  { value: 'warning', label: '警告' },
  { value: 'danger', label: '危险' }
]);

const typeQueryParams = ref<DictTypeQuery>({
  pageNum: 1,
  pageSize: 10,
  dictName: '',
  dictType: ''
});

const typeRules = {
  dictName: [{ required: true, message: '字典名称不能为空', trigger: 'blur' }],
  dictType: [{ required: true, message: '字典类型不能为空', trigger: 'blur' }]
};

const typeInitFormData: DictTypeForm = {
  dictId: undefined,
  dictName: '',
  dictType: '',
  remark: ''
};

const dataQueryParams = ref<DictDataQuery>({
  pageNum: 1,
  pageSize: 10,
  dictName: '',
  dictType: '',
  dictLabel: ''
});

const dataRules = {
  dictLabel: [{ required: true, message: '数据标签不能为空', trigger: 'blur' }],
  dictValue: [{ required: true, message: '数据键值不能为空', trigger: 'blur' }],
  dictSort: [{ required: true, message: '数据顺序不能为空', trigger: 'blur' }]
};

const dataInitFormData: DictDataForm = {
  dictCode: undefined,
  dictLabel: '',
  dictValue: '',
  cssClass: '',
  listClass: 'primary',
  dictSort: 0,
  remark: ''
};

/** 字典数据行类型（dictType 为表单冗余字段，用于新增/修改时标识所属类型） */
type DictDataRow = DictDataVO & Pick<DictDataForm, 'dictType'>;

/** 字典数据变更后清理对应字典的本地缓存 */
function invalidateDictCache<T>(res: T): T {
  useDictStore().removeDict(dataQueryParams.value.dictType);
  return res;
}

// ---------------- 字典类型（主表） ----------------
const {
  loading: typeLoading,
  list: typeList,
  total: typeTotal,
  open: typeOpen,
  title: typeTitle,
  single: typeSingle,
  multiple: typeMultiple,
  showSearch: showTypeSearch,
  form: typeForm,
  getList: getTypeList,
  handleQuery: handleTypeQuery,
  handleDelete: handleTypeDelete,
  handleExport: handleTypeExport,
  handleSelectionChange: handleTypeSelectionChange,
  submitForm: submitTypeForm,
  cancel: cancelType,
  handleAdd: handleTypeAdd,
  handleUpdate: handleTypeUpdate
} = useCrud<DictTypeVO, DictTypeQuery>({
  apis: {
    // 查询时附加日期范围；每次刷新列表后校验当前选中类型仍有效（主子联动）
    list: (query) =>
      listType(proxy?.addDateRange(query, dateRange.value)).then((res) => {
        ensureCurrentType();
        return res;
      }),
    get: getType,
    add: addType,
    update: updateType,
    del: delType
  },
  idField: 'dictId',
  entityName: '字典类型',
  formRef: typeFormRef,
  queryParams: typeQueryParams,
  initForm: () => ({ ...typeInitFormData }),
  exportUrl: 'system/dict/type/export',
  exportFileName: 'dict'
});

// ---------------- 字典数据（子表，随选中类型联动） ----------------
const {
  loading: dataLoading,
  list: dataList,
  total: dataTotal,
  open: dataOpen,
  title: dataTitle,
  single: dataSingle,
  multiple: dataMultiple,
  showSearch: showDataSearch,
  form: dataForm,
  getList: getDataList,
  handleDelete: rawDataHandleDelete,
  handleExport: rawDataHandleExport,
  handleSelectionChange: handleDataSelectionChange,
  submitForm: submitDataForm,
  cancel: cancelData,
  handleAdd: rawDataHandleAdd,
  handleUpdate: rawDataHandleUpdate
} = useCrud<DictDataRow, DictDataQuery>({
  apis: {
    // 未选中字典类型时不请求数据
    list: (query) => (currentDict.value ? listData(query) : Promise.resolve({ rows: [], total: 0 })),
    get: getData,
    // 数据变更后同步清理对应字典缓存
    add: (data) => addData(data as DictDataForm).then(invalidateDictCache),
    update: (data) => updateData(data as DictDataForm).then(invalidateDictCache),
    del: (ids) => delData(ids).then(invalidateDictCache)
  },
  idField: 'dictCode',
  entityName: '字典数据',
  formRef: dataFormRef,
  queryParams: dataQueryParams,
  initForm: () => ({ ...dataInitFormData }),
  exportUrl: 'system/dict/data/export',
  exportFileName: 'dict_data'
});

/** 类型列表刷新后确保仍有选中的字典类型（无则取第一行） */
function ensureCurrentType() {
  if (!typeList.value.length) {
    currentDict.value = null;
    dataQueryParams.value.dictType = '';
    dataList.value = [];
    dataTotal.value = 0;
    return;
  }

  const current = currentDict.value && typeList.value.find((item) => item.dictId === currentDict.value?.dictId);
  const nextRow = current || typeList.value[0];
  setCurrentType(nextRow);
}

/** 选中字典类型并加载对应数据 */
function setCurrentType(row: DictTypeVO) {
  currentDict.value = row;
  dataQueryParams.value.dictType = row.dictType;
  dataQueryParams.value.pageNum = 1;
  dataQueryParams.value.dictLabel = '';
  getDataList();
  nextTick(() => typeTableRef.value?.setCurrentRow(row));
}

function handleTypeRowClick(row: DictTypeVO) {
  setCurrentType(row);
}

/** 类型搜索重置按钮操作 */
function handleTypeResetQuery() {
  dateRange.value = ['', ''];
  proxy?.resetForm('typeQueryRef');
  handleTypeQuery();
}

/** 数据搜索按钮操作 */
function handleDataQuery() {
  if (!currentDict.value) return;
  dataQueryParams.value.pageNum = 1;
  return getDataList();
}

/** 数据搜索重置按钮操作 */
function handleDataResetQuery() {
  proxy?.resetForm('dataQueryRef');
  dataQueryParams.value.dictLabel = '';
  handleDataQuery();
}

/** 数据新增按钮操作（挂接当前选中类型） */
function handleDataAdd() {
  if (!currentDict.value) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  rawDataHandleAdd(() => {
    dataForm.value.dictType = currentDict.value!.dictType;
  });
}

/** 数据修改按钮操作 */
function handleDataUpdate(row?: DictDataVO) {
  if (!currentDict.value) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  return rawDataHandleUpdate(row);
}

/** 数据删除按钮操作 */
function handleDataDelete(row?: DictDataVO) {
  if (!currentDict.value) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  return rawDataHandleDelete(row);
}

/** 数据导出按钮操作 */
function handleDataExport() {
  if (!currentDict.value) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  rawDataHandleExport();
}

/** 刷新缓存按钮操作 */
async function handleRefreshCache() {
  await refreshCache();
  proxy?.$modal.msgSuccess('刷新成功');
  useDictStore().cleanDict();
}

getTypeList();
</script>

<style lang="scss" scoped>
.dict-grid {
  row-gap: 16px;
}

.dict-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.dict-card__title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-weight: 600;
}

.dict-card__subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dict-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 8px 0 12px;
}

.dict-actions :deep(.el-button) {
  height: 32px;
  padding: 0 14px;
}

.dict-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.dict-table-wrap {
  overflow-x: auto;
  flex: 1;
}

.dict-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
}

.dict-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}
</style>
