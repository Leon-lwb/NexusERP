<template>
  <div class="app-container cascade-layout">
    <!-- 左侧：单位类别 -->
    <div class="cascade-sidebar category-panel">
      <div class="panel-header">
        <span class="panel-title">
          <el-icon><Folder /></el-icon>
          单位类别
        </span>
      </div>
      <div class="panel-body">
        <div v-for="cat in categories" :key="cat" class="category-item" :class="{ active: selectedCategory === cat }" @click="selectCategory(cat)">
          <span class="category-name">{{ cat }}</span>
          <el-tag size="small" type="info" class="count-tag">{{ baseUnitCountMap[cat] || 0 }}</el-tag>
        </div>
        <el-empty v-if="categories.length === 0" description="暂无类别" :image-size="60" />
      </div>
    </div>

    <!-- 中间：基本单位 -->
    <div class="cascade-sidebar baseunit-panel">
      <div class="panel-header">
        <span class="panel-title">
          <el-icon><ScaleToOriginal /></el-icon>
          基本单位
        </span>
        <el-button v-hasPermi="['bd:measure:add']" link type="primary" icon="Plus" size="small" @click="handleAddBase">新增</el-button>
      </div>
      <div class="panel-body" v-loading="baseUnitLoading">
        <div
          v-for="unit in baseUnitList"
          :key="unit.uomId"
          class="baseunit-card"
          :class="{ active: selectedBaseUnit?.uomId === unit.uomId }"
          @click="selectBaseUnit(unit)"
        >
          <div class="baseunit-main">
            <span class="baseunit-symbol">{{ unit.uomSymbol }}</span>
            <div class="baseunit-info">
              <div class="baseunit-name">{{ unit.uomName }}</div>
              <div class="baseunit-code">{{ unit.uomCode }}</div>
            </div>
          </div>
          <div class="baseunit-actions">
            <el-button v-hasPermi="['bd:measure:edit']" link type="primary" icon="Edit" size="small" @click.stop="handleUpdate(unit)" />
            <el-button v-hasPermi="['bd:measure:remove']" link type="danger" icon="Delete" size="small" @click.stop="handleDelete(unit)" />
          </div>
        </div>
        <el-empty v-if="!baseUnitLoading && baseUnitList.length === 0" description="暂无基本单位" :image-size="60" />
      </div>
    </div>

    <!-- 右侧：衍生单位管理 -->
    <div class="cascade-main">
      <div v-if="selectedBaseUnit" class="main-content">
        <!-- 基本单位信息 -->
        <el-card class="baseunit-detail-card" shadow="never">
          <template #header>
            <div class="detail-header">
              <span class="detail-title">
                <el-icon><Collection /></el-icon>
                基本单位信息
              </span>
              <el-tag type="success">{{ selectedBaseUnit.category }}</el-tag>
            </div>
          </template>
          <el-descriptions :column="4" border>
            <el-descriptions-item label="单位编码">{{ selectedBaseUnit.uomCode }}</el-descriptions-item>
            <el-descriptions-item label="单位名称">{{ selectedBaseUnit.uomName }}</el-descriptions-item>
            <el-descriptions-item label="单位符号">{{ selectedBaseUnit.uomSymbol }}</el-descriptions-item>
            <el-descriptions-item label="排序">{{ selectedBaseUnit.sortOrder }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 衍生单位表格 -->
        <el-card class="derived-unit-card" shadow="never">
          <template #header>
            <div class="detail-header">
              <span class="detail-title">
                <el-icon><Connection /></el-icon>
                衍生单位列表
                <el-tag size="small" type="info" class="count-badge">{{ derivedUnitList.length }}</el-tag>
              </span>
              <div class="header-actions">
                <el-button type="warning" plain icon="Download" size="small" @click="handleExport" v-hasPermi="['bd:measure:export']">导出</el-button>
                <el-button v-hasPermi="['bd:measure:add']" type="primary" plain icon="Plus" size="small" @click="handleAddDerived"
                  >新增衍生单位</el-button
                >
              </div>
            </div>
          </template>

          <el-table v-loading="derivedUnitLoading" :data="derivedUnitList" stripeshow-overflow-tooltip>
            <el-table-column label="排序" align="center" prop="sortOrder" />
            <el-table-column label="单位编码" align="center" prop="uomCode" />
            <el-table-column label="单位名称" align="center" prop="uomName" min-width="120" show-overflow-tooltip />
            <el-table-column label="单位符号" align="center" prop="uomSymbol" />
            <el-table-column label="转换率" align="center" prop="conversionRate" min-width="180">
              <template #default="scope">
                <span style="font-weight: 600; color: #409eff">
                  1 {{ scope.row.uomSymbol }} = {{ scope.row.conversionRate }} {{ selectedBaseUnit.uomSymbol }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" prop="status">
              <template #default="scope">
                <dict-tag :options="sys_core_status" :value="scope.row.status" />
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
              <template #default="scope">
                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:measure:edit']">修改</el-button>
                <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:measure:remove']">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!derivedUnitLoading && derivedUnitList.length === 0" description="暂无衍生单位，点击右上角新增" :image-size="80" />
        </el-card>
      </div>

      <div v-else class="empty-placeholder">
        <el-empty description="请先选择左侧的基本单位">
          <template #image>
            <el-icon :size="80" color="#dcdfe6"><Collection /></el-icon>
          </template>
        </el-empty>
      </div>
    </div>

    <!-- 添加或修改计量单位对话框（保留原有弹窗） -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="measureRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单位编码" prop="uomCode">
              <el-input v-model="form.uomCode" placeholder="请输入单位编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位名称" prop="uomName">
              <el-input v-model="form.uomName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单位符号" prop="uomSymbol">
              <el-input v-model="form.uomSymbol" placeholder="请输入单位符号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位类别" prop="category">
              <el-select v-model="form.category" placeholder="请选择单位类别" clearable style="width: 100%"
                ><el-option v-for="item in categories" :key="item" :label="item" :value="item"
              /></el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="基本单位" prop="isBase">
              <el-select v-model="form.isBase" placeholder="是/否" style="width: 100%" @change="handleBaseUnitChange">
                <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-show="form.isBase === '0'">
            <el-form-item label="基本单位" prop="primaryName">
              <el-input v-model="form.primaryName" readonly placeholder="请选择基本单位">
                <template #append>
                  <el-button icon="Search" @click="openBaseUnitSelect"></el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-show="form.isBase === '0'">
          <el-col :span="12">
            <el-form-item label="转换率" prop="conversionRate">
              <el-input v-model="form.conversionRate" placeholder="相对于基本单位" />
            </el-form-item>
          </el-col>
          <el-col :span="12"></el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" placeholder="请输入排序" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div v-if="isViewMode && form.isBase === '1'" style="margin-top: 20px; border-top: 1px solid #e8e8e8; padding-top: 16px">
        <h4 style="margin: 0 0 12px 0; font-size: 14px; color: #333">引用该基本单位的衍生单位</h4>
        <el-table v-loading="derivedUnitLoading" :data="derivedUnitList" max-height="300" bordershow-overflow-tooltip>
          <el-table-column label="单位编码" align="center" prop="uomCode" />
          <el-table-column label="单位名称" align="center" prop="uomName" />
          <el-table-column label="单位符号" align="center" prop="uomSymbol" />
          <el-table-column label="转换率" align="center" prop="conversionRate" />
        </el-table>
        <div v-if="!derivedUnitLoading && derivedUnitList.length === 0" style="text-align: center; color: #999; padding: 20px">
          暂无引用该基本单位的衍生单位
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 基本单位选择对话框 -->
    <el-dialog title="选择基本单位" v-model="baseUnitVisible" width="800px" append-to-body>
      <el-form :model="baseUnitQueryParams" ref="baseUnitQueryRef" :inline="true">
        <el-form-item label="单位编码" prop="uomCode">
          <el-input v-model="baseUnitQueryParams.uomCode" placeholder="请输入单位编码" clearable @keyup.enter="getBaseUnitList" />
        </el-form-item>
        <el-form-item label="单位名称" prop="uomName">
          <el-input v-model="baseUnitQueryParams.uomName" placeholder="请输入单位名称" clearable @keyup.enter="getBaseUnitList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getBaseUnitList">搜索</el-button>
          <el-button icon="Refresh" @click="resetBaseUnitQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="baseUnitLoading" :data="baseUnitListDialog" @row-dblclick="selectBaseUnit" show-overflow-tooltip>
        <el-table-column label="单位编码" align="center" prop="uomCode" />
        <el-table-column label="单位名称" align="center" prop="uomName" />
        <el-table-column label="单位符号" align="center" prop="uomSymbol" />
        <el-table-column label="单位类别" align="center" prop="category" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="pickBaseUnit(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="baseUnitTotal > 0"
        :total="baseUnitTotal"
        v-model:page="baseUnitQueryParams.pageNum"
        v-model:limit="baseUnitQueryParams.pageSize"
        @pagination="getBaseUnitList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="baseUnitVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Measure">
import { listMeasure, getMeasure, delMeasure, addMeasure, updateMeasure, listByPrimary, listCategories } from '@/api/erp/bd/measure';
import type { MeasureForm, MeasureQuery } from '@/api/erp/bd/measure/types';
import { Folder, ScaleToOriginal, Collection, Connection } from '@element-plus/icons-vue';
import type { FormInstance } from 'element-plus';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_true_or_false, sys_core_status } = toRefs<any>(proxy.useDict('sys_true_or_false', 'sys_core_status'));

// ==================== 级联布局数据 ====================
const categories = ref([]);
const baseUnitList = ref([]);
const baseUnitCountMap = ref<Record<string, number>>({});
const derivedUnitList = ref([]);
const baseUnitLoading = ref(false);
const derivedUnitLoading = ref(false);
const selectedCategory = ref('');
const selectedBaseUnit = ref<any>(null);

// ==================== 原有弹窗数据 ====================
const open = ref(false);
const loading = ref(false);
const title = ref('');
const isViewMode = ref(false);
const baseUnitVisible = ref(false);
const baseUnitListDialog = ref([]);
const baseUnitTotal = ref(0);
const baseUnitName = ref('');

const baseUnitQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  uomCode: undefined,
  uomName: undefined,
  isBase: '1',
  excludeId: undefined
});

const measureRef = ref<FormInstance>();
const data = reactive<{ form: MeasureForm; queryParams: MeasureQuery }>({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    uomCode: null,
    uomName: null,
    uomSymbol: null,
    category: null,
    isBase: null,
    status: null
  }
});

const { queryParams, form } = toRefs(data);

const rules = computed(() => ({
  uomCode: [{ required: true, message: '单位编码不能为空', trigger: 'blur' }],
  uomName: [{ required: true, message: '单位名称不能为空', trigger: 'blur' }],
  uomSymbol: [{ required: true, message: '单位符号不能为空', trigger: 'blur' }],
  category: [{ required: true, message: '单位类别不能为空', trigger: 'blur' }],
  isBase: [{ required: true, message: '是否基本单位不能为空', trigger: 'change' }],
  conversionRate: [{ required: form.value.isBase === '0', message: '转换率不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
}));

// ==================== 初始化 ====================
onMounted(() => {
  loadCategories();
  loadAllBaseUnits();
});

/** 加载类别列表 */
function loadCategories() {
  listCategories().then((response) => {
    categories.value = response.data || [];
    if (categories.value.length > 0 && !selectedCategory.value) {
      selectCategory(categories.value[0]);
    }
  });
}

/** 加载所有基本单位用于统计 */
function loadAllBaseUnits() {
  listMeasure({ pageNum: 1, pageSize: 9999, isBase: '1' }).then((res: any) => {
    const units = res.rows || [];
    const countMap: Record<string, number> = {};
    units.forEach((u: any) => {
      if (u.category) countMap[u.category] = (countMap[u.category] || 0) + 1;
    });
    baseUnitCountMap.value = countMap;
  });
}

// ==================== 级联选择 ====================
function selectCategory(category: string) {
  selectedCategory.value = category;
  selectedBaseUnit.value = null;
  derivedUnitList.value = [];
  loadBaseUnitsByCategory(category);
}

function loadBaseUnitsByCategory(category: string) {
  baseUnitLoading.value = true;
  listMeasure({ pageNum: 1, pageSize: 9999, category, isBase: '1' })
    .then((res: any) => {
      baseUnitList.value = res.rows || [];
      baseUnitLoading.value = false;
      if (baseUnitList.value.length > 0) {
        selectBaseUnit(baseUnitList.value[0]);
      }
    })
    .catch(() => {
      baseUnitLoading.value = false;
    });
}

function selectBaseUnit(unit: any) {
  selectedBaseUnit.value = unit;
  loadDerivedUnits(unit.uomId);
}

function loadDerivedUnits(primaryId: number) {
  derivedUnitLoading.value = true;
  listByPrimary(primaryId)
    .then((res: any) => {
      derivedUnitList.value = res.data || [];
      derivedUnitLoading.value = false;
    })
    .catch(() => {
      derivedUnitLoading.value = false;
    });
}

// ==================== 弹窗操作 ====================
function cancel() {
  open.value = false;
  reset();
}

function reset() {
  form.value = {
    uomId: null,
    uomCode: null,
    uomName: null,
    uomSymbol: null,
    category: null,
    isBase: null,
    primaryId: null,
    primaryName: null,
    conversionRate: null,
    status: null,
    sortOrder: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  baseUnitName.value = '';
  proxy.resetForm('measureRef');
}

function normalizeFormData(data: any) {
  data.isBase = String(data.isBase);
  data.status = String(data.status);
  return data;
}

/** 新增基本单位 */
function handleAddBase() {
  reset();
  isViewMode.value = false;
  form.value.category = selectedCategory.value || categories.value[0] || '';
  form.value.isBase = '1';
  form.value.status = '1';
  open.value = true;
  title.value = '新增基本单位';
}

/** 新增衍生单位 */
function handleAddDerived() {
  if (!selectedBaseUnit.value) return;
  reset();
  isViewMode.value = false;
  form.value.category = selectedBaseUnit.value.category;
  form.value.isBase = '0';
  form.value.status = '1';
  form.value.primaryId = selectedBaseUnit.value.uomId;
  form.value.primaryName = selectedBaseUnit.value.uomName;
  open.value = true;
  title.value = `新增「${selectedBaseUnit.value.uomName}」的衍生单位`;
}

/** 修改按钮操作 */
function handleUpdate(row: any) {
  reset();
  isViewMode.value = false;
  getMeasure(row.uomId).then((response) => {
    const data = normalizeFormData(response.data);
    form.value = data;
    if (data.primaryId) {
      loadPrimaryUnitName(data.primaryId);
    }
    open.value = true;
    title.value = '修改计量单位';
  });
}

/** 查看按钮操作 */
function handleView(row: any) {
  reset();
  isViewMode.value = true;
  getMeasure(row.uomId).then((response) => {
    const data = normalizeFormData(response.data);
    form.value = data;
    if (data.primaryId) {
      loadPrimaryUnitName(data.primaryId);
    }
    if (data.isBase === '1') {
      loadDerivedUnits(data.uomId);
    }
    open.value = true;
    title.value = '查看计量单位';
  });
}

function loadPrimaryUnitName(primaryId: number) {
  if (primaryId) {
    getMeasure(primaryId).then((response) => {
      form.value.primaryName = response.data.uomName;
      baseUnitName.value = response.data.uomName;
    });
  }
}

/** 提交按钮 */
function submitForm() {
  measureRef.value?.validate((valid) => {
    if (valid) {
      const submitForm = { ...form.value };
      if (submitForm.isBase === '1') {
        submitForm.primaryId = null;
        submitForm.primaryName = null;
      }

      submitForm.isBase = parseInt(String(submitForm.isBase));
      submitForm.status = parseInt(String(submitForm.status));

      if (submitForm.uomId != null) {
        updateMeasure(submitForm).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          refreshData();
        });
      } else {
        addMeasure(submitForm).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          refreshData();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row: any) {
  const _uomIds = row.uomId;
  proxy.$modal
    .confirm('是否确认删除计量单位「' + row.uomName + '」？')
    .then(function () {
      return delMeasure(_uomIds);
    })
    .then(() => {
      proxy.$modal.msgSuccess('删除成功');
      refreshData();
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  if (!selectedBaseUnit.value) return;
  proxy.download(
    'bd/measure/export',
    {
      primaryId: selectedBaseUnit.value.uomId
    },
    `measure_${selectedBaseUnit.value.uomCode}_${new Date().getTime()}.xlsx`
  );
}

function handleBaseUnitChange(value: string) {
  if (value === '1') {
    form.value.primaryId = null;
    baseUnitName.value = '';
  }
}

function openBaseUnitSelect() {
  if (form.value.isBase !== '0') {
    proxy.$modal.msgWarning("请先选择'基本单位'为'否'");
    return;
  }
  baseUnitVisible.value = true;
  baseUnitQueryParams.excludeId = form.value.uomId || undefined;
  getBaseUnitList();
}

function getBaseUnitList() {
  baseUnitLoading.value = true;
  baseUnitQueryParams.isBase = '1';
  listMeasure(baseUnitQueryParams).then((response) => {
    baseUnitListDialog.value = response.rows;
    baseUnitTotal.value = response.total;
    baseUnitLoading.value = false;
  });
}

function pickBaseUnit(row: any) {
  form.value.primaryId = row.uomId;
  form.value.primaryName = row.uomName;
  baseUnitName.value = row.uomName;
  baseUnitVisible.value = false;
}

function resetBaseUnitQuery() {
  baseUnitQueryParams.uomCode = undefined;
  baseUnitQueryParams.uomName = undefined;
  getBaseUnitList();
}

/** 刷新数据 */
function refreshData() {
  loadAllBaseUnits();
  if (selectedCategory.value) {
    loadBaseUnitsByCategory(selectedCategory.value);
  } else {
    loadCategories();
  }
}
</script>

<style scoped lang="scss">
.cascade-layout {
  display: flex;
  height: calc(100vh - 120px);
  gap: 12px;
  padding: 0;
  background: #f5f7fa;
}

.cascade-sidebar {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 16px;
    border-bottom: 1px solid #ebeef5;
    font-weight: 600;
    font-size: 15px;
    color: #303133;

    .panel-title {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  .panel-body {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }
}

.category-panel {
  width: 200px;
  min-width: 180px;

  .category-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    margin-bottom: 4px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s;
    color: #606266;

    &:hover {
      background: #f5f7fa;
      color: #409eff;
    }

    &.active {
      background: #ecf5ff;
      color: #409eff;
      font-weight: 600;
    }

    .category-name {
      flex: 1;
      font-size: 14px;
    }

    .count-tag {
      transform: scale(0.85);
    }
  }
}

.baseunit-panel {
  width: 300px;
  min-width: 260px;

  .baseunit-card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px;
    margin-bottom: 8px;
    border-radius: 8px;
    border: 1px solid #ebeef5;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: #c6e2ff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
    }

    &.active {
      border-color: #409eff;
      background: #f0f9ff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
    }

    .baseunit-main {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .baseunit-symbol {
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      background: #ecf5ff;
      color: #409eff;
      font-size: 16px;
      font-weight: 700;
    }

    .baseunit-info {
      .baseunit-name {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
      }
      .baseunit-code {
        font-size: 12px;
        color: #909399;
        margin-top: 2px;
      }
    }

    .baseunit-actions {
      display: flex;
      gap: 4px;
      opacity: 0;
      transition: opacity 0.2s;
    }

    &:hover .baseunit-actions {
      opacity: 1;
    }
  }
}

.cascade-main {
  flex: 1;
  min-width: 500px;
  overflow-y: auto;

  .main-content {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .empty-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    background: #fff;
    border-radius: 8px;
  }
}

.baseunit-detail-card {
  :deep(.el-card__header) {
    padding: 12px 16px;
  }
}

.derived-unit-card {
  flex: 1;

  :deep(.el-card__header) {
    padding: 12px 16px;
  }

  :deep(.el-card__body) {
    padding: 12px;
  }
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .detail-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
    font-size: 15px;
    color: #303133;
  }

  .count-badge {
    margin-left: 4px;
  }

  .header-actions {
    display: flex;
    gap: 8px;
  }
}
</style>
